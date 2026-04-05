# 7.5 — Distributed Transactions

## Concept

When a single user action spans multiple services, you need all steps to either fully succeed or fully roll back — but there is no shared database transaction across service boundaries. The saga pattern solves this by chaining local transactions, where each step publishes a compensating action that undoes its work if a later step fails.

---

## Deep Dive

### Why Two-Phase Commit (2PC) Doesn't Scale

```
  2PC requires a coordinator that holds a distributed lock for the
  entire transaction duration:

    Coordinator                Service A          Service B
        │                         │                   │
        ├─── Phase 1: Prepare ───►│                   │
        ├─────────────────────────┴─── Prepare ──────►│
        │                                              │
        │◄── A: ready ────────────────────────────────┤
        │◄──────────────── B: ready ───────────────── ┤
        │                                              │
        ├─── Phase 2: Commit ─────────────────────────►│
        │                                              │
  
  Problems:
    - Coordinator crashes between Phase 1 and Phase 2 → both services
      hold locks indefinitely (blocking reads AND writes)
    - Network partition during Phase 2 → A commits, B doesn't → inconsistent
    - Latency: 2 round trips minimum before any write is visible
    - Every service must implement the 2PC protocol
```

### The Saga Pattern

```
  Key insight: replace 1 distributed transaction with N local transactions
  + N compensating transactions to undo completed steps on failure.

  ┌─────────────────────────────────────────────────────────────────────┐
  │  Happy path: create URL + send welcome notification                 │
  │                                                                     │
  │  Step 1: INSERT url (ScaleForge DB)                                 │
  │     ↓                                                               │
  │  Step 2: enqueue notification (FlowForge via BullMQ)                │
  │     ↓                                                               │
  │  Step 3: respond 201 Created                                        │
  └─────────────────────────────────────────────────────────────────────┘

  ┌─────────────────────────────────────────────────────────────────────┐
  │  Failure: Step 2 fails (FlowForge down / queue full)                │
  │                                                                     │
  │  Step 1: INSERT url ✓ (committed)                                   │
  │     ↓                                                               │
  │  Step 2: enqueue notification ✗ (fails)                             │
  │     ↓                                                               │
  │  Compensate Step 1: DELETE url (undo the committed insert)          │
  │     ↓                                                               │
  │  Respond 503                                                        │
  └─────────────────────────────────────────────────────────────────────┘
```

### Choreography vs. Orchestration

```
  Choreography (event-driven):
  
    ScaleForge inserts URL → emits "url.created" event
    FlowForge hears "url.created" → enqueues notification
    If FlowForge fails → emits "notification.failed" event
    ScaleForge hears "notification.failed" → deletes URL
  
    Pros: services are loosely coupled
    Cons: hard to trace the full saga flow; debugging is complex
  
  ─────────────────────────────────────────────────────────────────────

  Orchestration (central coordinator in one service):
  
    ScaleForge.createUrlAndNotify():
      1. INSERT url
      2. call FlowForge.enqueue()
      3. if step 2 throws → DELETE url
  
    Pros: full saga logic visible in one function; easy to trace
    Cons: ScaleForge is coupled to FlowForge — knows its steps
  
  For ScaleForge + FlowForge: use orchestration.
  With 5+ services interacting: switch to choreography.
```

### Idempotency in Each Step

```
  If the saga coordinator crashes mid-way and retries, steps already
  completed must be safe to repeat (idempotent):

  Step 1 — URL insert: use ON CONFLICT DO NOTHING with a stable key
  Step 2 — Notification enqueue: BullMQ jobId = notificationId (dedup)
  Compensate — URL delete: DELETE WHERE id = ? is always safe to repeat
```

---

## Code Examples

### Orchestration Saga: `createUrlAndNotify`

```typescript
// src/services/url-saga.service.ts
import { pool } from '../db/pool.js';
import { FlowForgeClient, ServiceUnavailableError } from '../clients/flowforge.client.js';
import { logger } from '../logger.js';
import { randomUUID } from 'node:crypto';
import { nanoid } from 'nanoid';

interface CreateUrlInput {
  originalUrl: string;
  userId: string;
  notifyOnCreate: boolean;
}

interface CreateUrlResult {
  shortCode: string;
  shortUrl: string;
}

export async function createUrlAndNotify(
  input: CreateUrlInput,
  flowforge: FlowForgeClient,
): Promise<CreateUrlResult> {
  const shortCode = nanoid(6);
  const notificationId = randomUUID();

  // ── Step 1: insert URL (local transaction) ────────────────────────
  await pool.query(
    `INSERT INTO urls (short_code, original_url, user_id, created_at)
     VALUES ($1, $2, $3, NOW())
     ON CONFLICT (short_code) DO NOTHING`,
    [shortCode, input.originalUrl, input.userId],
  );
  logger.info({ shortCode }, 'Step 1 complete: URL inserted');

  // ── Step 2: enqueue welcome notification ──────────────────────────
  if (input.notifyOnCreate) {
    try {
      await flowforge.enqueueNotification({
        notificationId,
        userId: input.userId,
        channel: 'email',
        type: 'url_created',
        payload: { shortCode, originalUrl: input.originalUrl },
      });
      logger.info({ notificationId }, 'Step 2 complete: notification enqueued');
    } catch (err) {
      // ── Compensate Step 1: delete URL we just created ─────────────
      logger.warn({ shortCode, err }, 'Step 2 failed — running compensation');
      await compensateUrlInsert(shortCode);

      if (err instanceof ServiceUnavailableError) {
        throw err; // caller turns this into 503
      }
      throw err;
    }
  }

  return {
    shortCode,
    shortUrl: `${process.env.BASE_URL}/${shortCode}`,
  };
}

async function compensateUrlInsert(shortCode: string): Promise<void> {
  // Safe to call multiple times — idempotent delete
  await pool.query('DELETE FROM urls WHERE short_code = $1', [shortCode]);
  logger.info({ shortCode }, 'Compensation complete: URL deleted');
}
```

### Route Handler with Saga

```typescript
// src/routes/urls.router.ts
import { Router } from 'express';
import { z } from 'zod';
import { createUrlAndNotify } from '../services/url-saga.service.js';
import { ServiceUnavailableError } from '../clients/flowforge.client.js';
import { getServiceUrls } from '../config/services.js';
import { FlowForgeClient } from '../clients/flowforge.client.js';

export const urlsRouter = Router();

const CreateUrlSchema = z.object({
  url: z.string().url(),
  notifyOnCreate: z.boolean().default(false),
});

const { flowforge: flowforgeCfg } = getServiceUrls();
const flowforgeClient = new FlowForgeClient(flowforgeCfg.baseUrl, flowforgeCfg.timeoutMs);

urlsRouter.post('/', async (req, res) => {
  const parsed = CreateUrlSchema.safeParse(req.body);
  if (!parsed.success) {
    res.status(400).json({ error: parsed.error.flatten() });
    return;
  }

  const userId = req.headers['x-user-id'] as string;  // injected by gateway

  try {
    const result = await createUrlAndNotify(
      {
        originalUrl: parsed.data.url,
        userId,
        notifyOnCreate: parsed.data.notifyOnCreate,
      },
      flowforgeClient,
    );
    res.status(201).json(result);
  } catch (err) {
    if (err instanceof ServiceUnavailableError) {
      res.setHeader('Retry-After', '30');
      res.status(503).json({ error: 'Notification service unavailable. URL was not created.' });
      return;
    }
    throw err;  // unhandled errors → global error middleware
  }
});
```

---

## Try It Yourself

**Exercise:** Force the compensation path and verify the URL is cleaned up.

```typescript
// saga-compensation.exercise.ts

// TODO:
// 1. Temporarily modify FlowForgeClient.enqueueNotification() to always
//    throw a ServiceUnavailableError (simulate step 2 failure).
//
// 2. Call POST /api/v1/urls with { url: "https://example.com", notifyOnCreate: true }
//
// 3. Verify:
//    - HTTP response is 503 with "Retry-After" header
//    - The url does NOT exist in the database:
//      SELECT * FROM urls WHERE short_code = '<code that would have been created>'
//
// 4. Add logging to both createUrlAndNotify() and compensateUrlInsert()
//    and verify the log sequence shows:
//      "Step 1 complete: URL inserted"
//      "Step 2 failed — running compensation"
//      "Compensation complete: URL deleted"
//
// Bonus: What happens if compensateUrlInsert() itself throws?
// Add a try/catch around it that logs the failure and alerts,
// but still propagates the original error.
```

<details>
<summary>Show solution for the bonus</summary>

```typescript
async function safeCompensate(shortCode: string, originalError: unknown): Promise<void> {
  try {
    await compensateUrlInsert(shortCode);
  } catch (compensationError) {
    // Compensation failed — URL is now orphaned.
    // Log at FATAL level and alert on-call — this needs manual cleanup.
    logger.fatal({ shortCode, compensationError, originalError }, 
      'CRITICAL: Compensation failed — orphaned URL in database');
    // In production: page on-call, write to incidents table, etc.
  }
  // Always throw the original error (not the compensation error)
  throw originalError;
}

// Updated step 2 catch block:
// } catch (err) {
//   await safeCompensate(shortCode, err);
// }
```

</details>

---

## Capstone Connection

The saga pattern is the correct way to implement "create a short URL AND send a welcome notification" in ScaleForge. Using a single database transaction is impossible (the two operations touch different services). Using 2PC would block both services under failure. The saga gives a clean rollback path: if FlowForge is down, the URL never appears in the system — the user gets a clear error and no dangling state is left behind.
