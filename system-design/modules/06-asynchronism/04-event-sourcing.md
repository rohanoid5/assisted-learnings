# 6.4 — Event Sourcing

## Concept

Event sourcing is a storage pattern where application state is derived from an immutable, append-only log of events rather than storing only the current state. Instead of "URL abc has target X", you store "URL abc was created with target A", then "URL abc target was changed to X". The current state is computed by replaying all events. This gives you a complete audit trail, the ability to time-travel to any past state, and a natural event stream for building read models.

---

## Deep Dive

### Traditional State vs. Event Sourcing

```
  Traditional (mutable state):
  
    Table: urls
    │ code │ target_url         │ click_count │ updated_at          │
    │ abc  │ https://new.com    │ 1247        │ 2024-01-15 14:30:00 │
    
    After update: row is overwritten. History is gone.
    Question: "What was the target URL 3 days ago?" → No answer.
    Question: "Who changed the target URL?" → No audit trail.
  
  Event Sourcing:
  
    Table: url_events (append-only)
    │ id │ code │ event_type      │ payload                        │ created_at  │
    │ 1  │ abc  │ url_created     │ {target: "https://orig.com"}   │ Jan 10 09:00│
    │ 2  │ abc  │ url_updated     │ {target: "https://mid.com"}    │ Jan 12 11:30│
    │ 3  │ abc  │ click_recorded  │ {country: "US"}                │ Jan 12 15:00│
    │ 4  │ abc  │ url_updated     │ {target: "https://new.com"}    │ Jan 15 14:30│
    │ 5  │ abc  │ click_recorded  │ {country: "DE"}                │ Jan 15 16:00│
    
    Current state: replay events → target = "https://new.com", clicks = 2
    State at Jan 13: replay events 1-2 → target = "https://mid.com"
    Audit trail: built-in ✓
```

### Event Sourcing + CQRS

```
  CQRS = Command Query Responsibility Segregation
  Separate read and write models:
  
    ┌──────────────────────────────────────────────────────┐
    │                    Write Side                        │
    │  POST /urls  ──►  Command Handler                    │
    │                       │                             │
    │                       ▼                             │
    │               url_events table (append-only)        │
    │                       │                             │
    │                       ▼  (async projection)         │
    └───────────────────────┼─────────────────────────────┘
                            │
    ┌───────────────────────▼─────────────────────────────┐
    │                    Read Side                         │
    │     urls_view (materialized view / Redis cache)      │
    │       Optimized for fast reads                       │
    │       Updated by projection worker                   │
    │                                                      │
    │  GET /urls/abc  ──►  Query Handler                   │
    │                      ↓ read urls_view                │
    └──────────────────────────────────────────────────────┘
    
  Benefits:
    - Write side: simple, append-only (no UPDATE contention)
    - Read side: can be shaped exactly for the query (denormalized)
    - Projection can be recomputed from event log (self-healing)
```

### When to Use Event Sourcing

```
  Good fit:
    - Audit requirements (compliance, legal)
    - Time-travel debugging ("what was the state when this happened?")
    - Complex domain with multiple transition paths
    - Building analytics on top of the event stream
    - Undo/redo functionality
    
  Poor fit:
    - Simple CRUD with no history requirements
    - Teams unfamiliar with the pattern (high complexity overhead)
    - Very high write volume (>10k events/s) — event log grows without limit
    
  FlowForge's notification delivery audit log is a natural fit:
    - Regulation: proof that notification was sent and received
    - Retry audit: every attempt tracked with outcome
    - Debugging: "why did this notification fail?" → event replay
```

---

## Code Examples

### Event Store for FlowForge Notifications

```typescript
// src/events/notification-events.ts — event type definitions

export type NotificationEventType =
  | 'notification_created'
  | 'notification_dispatched'
  | 'delivery_attempted'
  | 'delivery_succeeded'
  | 'delivery_failed'
  | 'notification_cancelled';

export interface NotificationEvent {
  eventId: string;
  notificationId: string;
  eventType: NotificationEventType;
  channel?: 'email' | 'webhook';
  payload: Record<string, unknown>;
  createdAt: Date;
}

// Database schema:
// CREATE TABLE notification_events (
//   event_id        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
//   notification_id UUID NOT NULL,
//   event_type      TEXT NOT NULL,
//   channel         TEXT,
//   payload         JSONB NOT NULL DEFAULT '{}',
//   created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
// );
// CREATE INDEX idx_notification_events_id ON notification_events (notification_id, created_at);
```

```typescript
// src/events/event-store.ts — append-only write + full event history reads

import { primaryPool } from '../db/pool.js';
import type { NotificationEvent, NotificationEventType } from './notification-events.js';
import pino from 'pino';

const log = pino({ name: 'event-store' });

// Append a new event — never UPDATE or DELETE rows in this table
export async function appendEvent(
  notificationId: string,
  eventType: NotificationEventType,
  payload: Record<string, unknown> = {},
  channel?: 'email' | 'webhook'
): Promise<void> {
  await primaryPool.query(
    `INSERT INTO notification_events (notification_id, event_type, channel, payload)
     VALUES ($1, $2, $3, $4)`,
    [notificationId, eventType, channel ?? null, payload]
  );
  log.debug({ notificationId, eventType }, 'Event appended');
}

// Replay all events for a notification — used to reconstruct current state
export async function getEventHistory(notificationId: string): Promise<NotificationEvent[]> {
  const result = await primaryPool.query<NotificationEvent>(
    `SELECT event_id, notification_id, event_type, channel, payload, created_at
     FROM notification_events
     WHERE notification_id = $1
     ORDER BY created_at ASC`,
    [notificationId]
  );
  return result.rows;
}

// Derive current status from event history (projection)
export interface NotificationStatus {
  notificationId: string;
  status: 'queued' | 'delivering' | 'delivered' | 'failed' | 'cancelled';
  deliveryAttempts: number;
  lastError?: string;
  createdAt: Date;
  deliveredAt?: Date;
}

export function projectStatus(events: NotificationEvent[]): NotificationStatus | null {
  if (events.length === 0) return null;

  const first = events[0]!;
  let status: NotificationStatus = {
    notificationId: first.notificationId,
    status: 'queued',
    deliveryAttempts: 0,
    createdAt: first.createdAt,
  };

  // Replay events to compute current state
  for (const event of events) {
    switch (event.eventType) {
      case 'notification_created':
        status.status = 'queued';
        break;
      case 'notification_dispatched':
        status.status = 'delivering';
        break;
      case 'delivery_attempted':
        status.deliveryAttempts++;
        break;
      case 'delivery_succeeded':
        status.status = 'delivered';
        status.deliveredAt = event.createdAt;
        break;
      case 'delivery_failed':
        status.status = 'failed';
        status.lastError = event.payload['error'] as string;
        break;
      case 'notification_cancelled':
        status.status = 'cancelled';
        break;
    }
  }

  return status;
}
```

### Integrating Events into the Worker

```typescript
// In email.worker.ts — emit events at each lifecycle point

import { appendEvent } from '../events/event-store.js';

async function processEmailJob(job: Job<NotificationJob>): Promise<void> {
  const { notificationId, recipient, subject, body } = job.data;

  await appendEvent(notificationId, 'delivery_attempted', {
    attempt: job.attemptsMade + 1,
    recipient,
  }, 'email');

  try {
    await transporter.sendMail({ from: '...', to: recipient, subject, text: body });
    await appendEvent(notificationId, 'delivery_succeeded', {}, 'email');
  } catch (err) {
    await appendEvent(notificationId, 'delivery_failed', {
      error: (err as Error).message,
      attempt: job.attemptsMade + 1,
    }, 'email');
    throw err; // rethrow to trigger BullMQ retry
  }
}
```

---

## Try It Yourself

**Exercise:** Replay event history to reconstruct notification status.

```typescript
// event-replay.exercise.ts

import { getEventHistory, projectStatus } from './src/events/event-store.js';

// TODO:
// 1. Send a notification via POST /api/v1/notifications
//    Record the notificationId
//
// 2. Fetch its event history:
//    const events = await getEventHistory('<notificationId>');
//    console.log('Events:', JSON.stringify(events, null, 2));
//
// 3. Project the current status:
//    const status = projectStatus(events);
//    console.log('Current status:', status);
//
// 4. If delivery failed (stop MailHog to force failure):
//    Verify that delivery_attempted events accumulate (one per retry)
//    Verify failedReason is populated in the final status
//
// Expected output for delivered notification:
// {
//   notificationId: '...',
//   status: 'delivered',
//   deliveryAttempts: 1,
//   createdAt: '...',
//   deliveredAt: '...'
// }
```

<details>
<summary>Show solution</summary>

```typescript
import { getEventHistory, projectStatus } from './src/events/event-store.js';
import { primaryPool } from './src/db/pool.js';

const notificationId = process.argv[2];
if (!notificationId) {
  console.error('Usage: npx ts-node-esm solution.ts <notificationId>');
  process.exit(1);
}

const events = await getEventHistory(notificationId);

if (events.length === 0) {
  console.log('No events found for notification:', notificationId);
} else {
  console.log(`Found ${events.length} events:\n`);
  for (const event of events) {
    console.log(`  [${event.createdAt.toISOString()}] ${event.eventType}`, 
                event.channel ? `(${event.channel})` : '',
                Object.keys(event.payload).length ? JSON.stringify(event.payload) : '');
  }
  
  const status = projectStatus(events);
  console.log('\nProjected status:', JSON.stringify(status, null, 2));
}

await primaryPool.end();
```

</details>

---

## Capstone Connection

`notification_events` is the single source of truth for FlowForge's delivery pipeline. When a customer asks "was my notification delivered?", `getEventHistory(notificationId)` gives the complete delivery journal — every attempt, every failure reason, the exact timestamp of delivery. No mutable state to question, no logs to grep. The event log is the audit trail.
