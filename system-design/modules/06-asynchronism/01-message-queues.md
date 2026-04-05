# 6.1 — Message Queues

## Concept

A message queue is a buffer that holds messages produced by one component until a consumer is ready to process them. Producers and consumers are decoupled — they don't need to be running simultaneously, don't need to know about each other, and can operate at different rates. This enables reliable async communication, load leveling, and retry-on-failure.

---

## Deep Dive

### Producer-Consumer Decoupling

```
  Without a queue (tight coupling):
  
    ┌─────────┐  direct call  ┌──────────────┐
    │  API    │ ────────────► │ Email Service │
    └─────────┘               └──────────────┘
    
    - If email service is slow (800ms) → API call is slow (800ms)
    - If email service is down → API returns 500 to user
    - If API sends 10k req/s but email service handles 100/s → overload
  
  With a queue (loose coupling):
  
    ┌─────────┐  enqueue  ┌───────┐  dequeue  ┌──────────────┐
    │  API    │ ─────────►│ Queue │ ──────────►│ Email Worker │
    └─────────┘           └───────┘            └──────────────┘
    
    - API enqueues in <5ms → user gets 202 Accepted immediately
    - Email service down → job stays in queue → retried automatically
    - API sends 10k/s → queue absorbs burst → worker processes at 100/s
    - Worker crashes → job returns to queue → processed by next worker
```

### Queue Delivery Semantics

```
  At-most-once (fire and forget):
    Job sent → consumer processes ONCE
    If consumer crashes mid-process → job is LOST
    Use for: non-critical events, metrics, logs
    
  At-least-once (durable + retry):
    Job sent → consumer processes → ACK required
    If no ACK within timeout → job REQUEUED → processed again
    Consumer must be IDEMPOTENT (safe to process same job twice)
    Use for: emails, webhooks, payments (with idempotency keys)
    
  Exactly-once (guaranteed single delivery):
    Requires distributed transaction (2PC) between queue and consumer
    Very expensive → rarely needed in practice
    Use only when: duplicate causes irreversible harm (e.g., double-charge)
    
  FlowForge uses: At-least-once (default in BullMQ)
  Idempotency key = job.data.notificationId (UUID from API)
```

### Message Queue Topology Patterns

```
  1. Point-to-Point (Single Consumer):
  
     Producer ──► [Queue] ──► Worker
     
     Each message processed by exactly ONE worker.
     Horizontally scalable: add more workers for throughput.
     BullMQ: multiple workers compete for jobs (first to lock wins).
  
  2. Publish-Subscribe (Fan-Out):
  
     Producer ──► [Topic] ──┬──► Email Worker
                            ├──► Webhook Worker
                            └──► Analytics Worker
     
     Each subscriber gets a COPY of every message.
     Workers can fail independently without affecting others.
     BullMQ: use multiple queues + a dispatcher job.
  
  3. Competing Consumers (Work Queue):
  
     Producer ──► [Queue] ──┬──► Worker 1
                            ├──► Worker 2
                            └──► Worker 3
     
     Jobs distributed across workers (load balancing).
     More workers = higher throughput.
     BullMQ: natural behavior when multiple workers call queue.process().
```

### Trade-offs: Queue Systems Compared

```
  System     Transport   Ordering    Throughput    Persistence  Use Case
  ─────────  ──────────  ──────────  ────────────  ───────────  ────────────────
  BullMQ     Redis       Per-queue   50k jobs/s    Yes (AOF)    Background jobs
  RabbitMQ   TCP/AMQP    Per-queue   20k msgs/s    Yes (disk)   Microservices
  Kafka      TCP         Per-partition 1M msgs/s   Yes (log)    Event streaming
  SQS        HTTP        Best-effort 3k-10k msg/s  Yes          Cloud workloads
  
  FlowForge uses BullMQ because:
    - Redis is already a dependency (caching)
    - Simple API, no separate broker to manage
    - UI dashboard (Bull Board) included
    - Jobs at 50k/s exceeds FlowForge's notification volume
```

---

## Code Examples

### FlowForge: Defining Job Types

```typescript
// src/queues/notification.queue.ts

import { Queue } from 'bullmq';
import { redisConnection } from '../config/redis.config.js';

// All job types accepted by the notification queue
export type NotificationChannel = 'email' | 'webhook';

export interface NotificationJob {
  notificationId: string;  // UUID — used as idempotency key
  channel: NotificationChannel;
  recipient: string;        // email address or webhook URL
  subject?: string;         // email only
  body: string;
  metadata?: Record<string, unknown>;
}

// BullMQ default options applied to every job
const DEFAULT_JOB_OPTIONS = {
  attempts: 5,                    // retry up to 5 times on failure
  backoff: {
    type: 'exponential' as const,
    delay: 1000,                  // 1s, 2s, 4s, 8s, 16s
  },
  removeOnComplete: { count: 100 }, // keep last 100 completed jobs for UI
  removeOnFail: { count: 200 },     // keep last 200 failed jobs for debugging
};

export const notificationQueue = new Queue<NotificationJob>('notifications', {
  connection: redisConnection,
  defaultJobOptions: DEFAULT_JOB_OPTIONS,
});

// Helper to enqueue a notification
export async function enqueueNotification(job: NotificationJob): Promise<string> {
  const addedJob = await notificationQueue.add(job.channel, job, {
    jobId: job.notificationId, // deduplication: same UUID → not re-added
  });
  return addedJob.id!;
}
```

### FlowForge: API Endpoint

```typescript
// src/routes/notifications.route.ts

import { Router } from 'express';
import { z } from 'zod';
import { randomUUID } from 'node:crypto';
import { enqueueNotification } from '../queues/notification.queue.js';
import pino from 'pino';

const router = Router();
const log = pino({ name: 'notifications-api' });

const SendNotificationSchema = z.object({
  channel: z.enum(['email', 'webhook']),
  recipient: z.string().min(1),
  subject: z.string().optional(),
  body: z.string().min(1),
  metadata: z.record(z.unknown()).optional(),
});

// POST /api/v1/notifications
router.post('/', async (req, res) => {
  const parse = SendNotificationSchema.safeParse(req.body);
  if (!parse.success) {
    res.status(400).json({ error: parse.error.flatten() });
    return;
  }

  const notificationId = randomUUID();
  const jobId = await enqueueNotification({
    notificationId,
    ...parse.data,
  });

  log.info({ notificationId, jobId, channel: parse.data.channel }, 'Notification enqueued');

  // 202 Accepted — request received but not yet processed
  res.status(202).json({
    notificationId,
    jobId,
    status: 'queued',
  });
});

export default router;
```

---

## Try It Yourself

**Exercise:** Enqueue and then inspect a job.

```typescript
// queue-inspection.exercise.ts

// TODO:
// 1. Start the FlowForge API server
// 2. Send a POST to enqueue a notification:
//    curl -X POST http://localhost:3002/api/v1/notifications \
//      -H 'Content-Type: application/json' \
//      -d '{"channel":"email","recipient":"test@example.com","subject":"Hello","body":"Test message"}'
//    → Record the notificationId and jobId returned
//
// 3. Use BullMQ's Queue API to inspect the job directly:
import { notificationQueue } from './src/queues/notification.queue.js';

// TODO: get job by ID and print its status and data
const jobId = 'PASTE_JOB_ID_HERE';
// const job = await notificationQueue.getJob(jobId);
// console.log('Job status:', await job?.getState());
// console.log('Job data:', job?.data);
// console.log('Job attempts:', job?.attemptsMade);
//
// 4. Check queue counts:
// const counts = await notificationQueue.getJobCounts('waiting', 'active', 'completed');
// console.log('Queue counts:', counts);
```

<details>
<summary>Show solution</summary>

```typescript
import { notificationQueue } from './src/queues/notification.queue.js';

const jobId = process.argv[2];
if (!jobId) throw new Error('Usage: npx ts-node-esm solution.ts <jobId>');

const job = await notificationQueue.getJob(jobId);
if (!job) {
  console.log(`Job ${jobId} not found (may have been cleaned up)`);
  process.exit(1);
}

console.log('Job ID:      ', job.id);
console.log('Job Name:    ', job.name);
console.log('Status:      ', await job.getState());
console.log('Attempts:    ', job.attemptsMade, '/', job.opts.attempts);
console.log('Data:        ', JSON.stringify(job.data, null, 2));
console.log('Created:     ', new Date(job.timestamp).toISOString());
if (job.finishedOn) {
  console.log('Completed:   ', new Date(job.finishedOn).toISOString());
}

const counts = await notificationQueue.getJobCounts('waiting', 'active', 'completed', 'failed');
console.log('\nQueue totals:', counts);

await notificationQueue.close();
```

</details>

---

## Capstone Connection

When FlowForge's API returns `202 Accepted`, it means the notification has been durably persisted to Redis. Even if all three app workers crash at this exact moment, the job will be processed as soon as any worker comes back online. This is the contract that message queues provide — and it's why asynchronous notification delivery is always more reliable than synchronous.
