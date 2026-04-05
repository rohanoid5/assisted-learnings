# 6.3 — Pub/Sub Patterns

## Concept

In the publish-subscribe pattern, producers publish messages to a topic without knowing who will receive them. Multiple subscribers receive a copy of each message independently. Unlike point-to-point queues (where one consumer claims each job), pub/sub creates a one-to-many delivery fan-out. Use pub/sub when the same event must trigger multiple independent actions.

---

## Deep Dive

### Pub/Sub vs. Point-to-Point

```
  Point-to-point (work queue):
  
    Producer ──► [Queue] ──► Worker A claims job
    
    One message, one consumer. Purpose: distribute work.
    Example: "process this image" — only one worker resizes it.
  
  Publish-Subscribe:
  
    Publisher ──► [Topic] ──┬──► Subscriber A (email)
                            ├──► Subscriber B (push notification)
                            ├──► Subscriber C (analytics)
                            └──► Subscriber D (webhook)
    
    One message, MANY consumers. Purpose: broadcast event.
    Example: "user signed up" — triggers welcome email, analytics event,
             Slack alert, CRM update all simultaneously.
```

### Fan-Out in BullMQ

```
  BullMQ doesn't have native topics. Implement fan-out with a dispatcher:
  
  Strategy A — Dispatcher job:
    1. Enqueue "dispatch" job with event payload
    2. Dispatcher worker receives it
    3. Dispatcher enqueues one child job per subscriber
       → email-queue, webhook-queue, analytics-queue
    
    Advantages:
      - Each subscriber queue has its own retry policy
      - One subscriber failing doesn't affect others
      - Subscribers can be added/removed without changing producer
    
  Strategy B — BullMQ Flows (parent/child jobs):
    1. Producer creates a Flow with parent + N children
    2. Parent job only marks complete when ALL children complete
    3. Built-in for "fan-out and wait for all" semantics
    
  FlowForge uses: Strategy A (Dispatcher job)
  BullMQ Flows: covered in bonus section below
```

### Redis Pub/Sub vs. BullMQ

```
  Redis PUBLISH/SUBSCRIBE:
    - Fire-and-forget: message lost if no subscriber is connected
    - Sub-millisecond delivery to all connected clients
    - No persistence, no retries, no job tracking
    - Use for: real-time in-memory events (cache invalidation, live dashboards)
    
  BullMQ (Redis-backed queue):
    - Durable: jobs persist in Redis even if workers are down
    - Retries with backoff, progress tracking, dead-letter queue
    - Use for: reliable task delivery (email, webhook, payments)
    
  FlowForge uses BOTH:
    Redis pub/sub → notify workers that a new job is available (internal signal)
    BullMQ queue  → durable job storage and delivery (application workload)
```

---

## Code Examples

### Dispatcher Pattern: One Job → Multiple Queues

```typescript
// src/queues/dispatcher.ts — fan-out a notification event to all relevant channels

import { Queue, Worker, type Job } from 'bullmq';
import { redisConnection } from '../config/redis.config.js';
import type { NotificationJob } from './notification.queue.js';
import pino from 'pino';

const log = pino({ name: 'dispatcher' });

// Separate queues per channel — each can have its own retry policy,
// rate limiting, concurrency, and dead-letter queue
export const emailQueue = new Queue<NotificationJob>('email', {
  connection: redisConnection,
  defaultJobOptions: {
    attempts: 5,
    backoff: { type: 'exponential', delay: 1000 },
  },
});

export const webhookQueue = new Queue<NotificationJob>('webhook', {
  connection: redisConnection,
  defaultJobOptions: {
    attempts: 3,       // fewer retries for webhooks (caller-controlled endpoints)
    backoff: { type: 'exponential', delay: 2000 },
  },
});

export const analyticsQueue = new Queue<NotificationJob>('analytics', {
  connection: redisConnection,
  defaultJobOptions: {
    attempts: 1,       // analytics are best-effort — don't retry
  },
});

// Maps channel to their respective queue
const channelQueues: Record<string, Queue<NotificationJob>> = {
  email: emailQueue,
  webhook: webhookQueue,
  analytics: analyticsQueue,
};

// Dispatcher worker: receives from the main 'notifications' queue,
// routes to the correct channel queue
export function createDispatcherWorker() {
  return new Worker<NotificationJob>(
    'notifications',
    async (job: Job<NotificationJob>) => {
      const { channel, notificationId } = job.data;
      const targetQueue = channelQueues[channel];

      if (!targetQueue) {
        log.warn({ channel, notificationId }, 'Unknown channel — dropping job');
        return;
      }

      await targetQueue.add(channel, job.data, {
        jobId: notificationId, // idempotency: same notification won't re-enqueue
      });

      log.debug({ notificationId, channel }, 'Dispatched to channel queue');
    },
    { connection: redisConnection, concurrency: 50 } // dispatching is fast, high concurrency
  );
}
```

### Fan-Out: One Event to All Channels

```typescript
// src/queues/fan-out.ts — when a single event needs ALL channels triggered

import { randomUUID } from 'node:crypto';
import { emailQueue, webhookQueue, analyticsQueue } from './dispatcher.js';
import type { NotificationJob } from './notification.queue.js';
import pino from 'pino';

const log = pino({ name: 'fan-out' });

// Dispatches the same event to all subscribed channels simultaneously
export async function fanOutEvent(
  event: Omit<NotificationJob, 'notificationId' | 'channel'>
): Promise<void> {
  const correlationId = randomUUID(); // same ID across all fan-out branches

  const jobs: Array<[Queue: typeof emailQueue, channel: string]> = [
    [emailQueue, 'email'],
    [webhookQueue, 'webhook'],
    [analyticsQueue, 'analytics'],
  ];

  // All three enqueue atomically (as close to simultaneous as Node.js allows)
  await Promise.all(
    jobs.map(([queue, channel]) =>
      queue.add(
        channel,
        { ...event, channel: channel as NotificationJob['channel'], notificationId: `${correlationId}-${channel}` },
        { jobId: `${correlationId}-${channel}` }
      )
    )
  );

  log.info({ correlationId }, 'Fan-out dispatched to all channels');
}
```

### Redis Pub/Sub for Real-Time Cache Invalidation (Cross-Instance)

```typescript
// src/cache/pubsub-invalidation.ts
// When one instance updates a URL, all instances must invalidate their
// in-process LRU. Redis pub/sub provides sub-ms broadcast.

import Redis from 'ioredis';
import { urlLRU } from './in-process-cache.js';
import pino from 'pino';

const log = pino({ name: 'pubsub-invalidation' });

const CHANNEL = 'url:invalidated';

// Separate publisher connection (ioredis docs: SUBSCRIBE mode blocks other commands)
const publisher = new Redis(process.env.REDIS_URL!);
const subscriber = new Redis(process.env.REDIS_URL!);

export async function publishInvalidation(code: string): Promise<void> {
  await publisher.publish(CHANNEL, code);
}

export async function subscribeToInvalidations(): Promise<void> {
  await subscriber.subscribe(CHANNEL);

  subscriber.on('message', (channel, code) => {
    if (channel !== CHANNEL) return;
    log.debug({ code }, 'Received invalidation broadcast');
    urlLRU.del(code); // invalidate this worker's in-process LRU
  });

  log.info('Subscribed to URL invalidation channel');
}

// In url-cache.ts updateTarget():
// await primaryPool.query(...update...);
// await redisClient.del(`url:${code}`);      // Redis shared cache
// await publishInvalidation(code);           // broadcast to all workers' LRU
```

---

## Try It Yourself

**Exercise:** Implement and verify the fan-out.

```typescript
// fanout-test.exercise.ts

// TODO:
// 1. Call fanOutEvent() with a test notification body
// 2. Check all three queues (emailQueue, webhookQueue, analyticsQueue)
//    to verify each has exactly one job with the correlation ID

import { emailQueue, webhookQueue, analyticsQueue } from './src/queues/dispatcher.js';
import { fanOutEvent } from './src/queues/fan-out.js';

// TODO: call fanOutEvent with sample data
// TODO: check job counts and search for the jobs by their correlation prefix
// Expected: emailQueue has 1 job, webhookQueue has 1 job, analyticsQueue has 1 job
// Each job's notificationId should share the same prefix (correlationId)
```

<details>
<summary>Show solution</summary>

```typescript
import assert from 'node:assert/strict';
import { emailQueue, webhookQueue, analyticsQueue } from './src/queues/dispatcher.js';
import { fanOutEvent } from './src/queues/fan-out.js';

await fanOutEvent({
  recipient: 'test@example.com',
  subject: 'Fan-out test',
  body: 'Testing pub/sub fan-out',
  metadata: { test: true },
});

// Give BullMQ a moment to persist
await new Promise(r => setTimeout(r, 200));

const emailCounts = await emailQueue.getJobCounts('waiting');
const webhookCounts = await webhookQueue.getJobCounts('waiting');
const analyticsCounts = await analyticsQueue.getJobCounts('waiting');

assert.ok(emailCounts.waiting >= 1, 'Email queue should have a job');
assert.ok(webhookCounts.waiting >= 1, 'Webhook queue should have a job');
assert.ok(analyticsCounts.waiting >= 1, 'Analytics queue should have a job');

console.log('Fan-out verified ✓');
console.log('Email waiting:    ', emailCounts.waiting);
console.log('Webhook waiting:  ', webhookCounts.waiting);
console.log('Analytics waiting:', analyticsCounts.waiting);
```

</details>

---

## Capstone Connection

FlowForge's `fanOutEvent()` is the backbone of the notification pipeline. When a future feature adds SMS support, you add `smsQueue` to `channelQueues` and `fanOutEvent` automatically routes to it — no changes to the API or existing workers. This is the open/closed principle applied to async systems: open for extension, closed for modification.
