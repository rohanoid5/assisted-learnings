# 6.2 — BullMQ Deep Dive

## Concept

BullMQ is a Redis-backed job queue library for Node.js. It uses Redis sorted sets, lists, and pub/sub to implement reliable job scheduling, concurrency, rate limiting, and progress reporting. Understanding BullMQ's internals helps you tune it for throughput and debug jobs when they fail.

---

## Deep Dive

### BullMQ's Redis Data Structures

```
  Four sorted sets and two lists represent job lifecycle:
  
  wait      LIST        Jobs waiting to be claimed by a worker
  active    SORTED SET  Jobs currently being processed (score = lock expiry)
  delayed   SORTED SET  Jobs scheduled for future time (score = run-at time)
  completed SORTED SET  Successfully processed jobs (trimmed by count)
  failed    SORTED SET  Jobs that exhausted all retries (trimmed by count)
  
  Job state transitions:
  
    ┌──────────┐
    │ enqueue  │
    └────┬─────┘
         │
         ▼
  ┌─────────────┐  worker polls  ┌────────────┐
  │   waiting   │ ──────────────►│   active   │
  └─────────────┘                └─────┬──────┘
                                       │
                    ┌──────────────────┼──────────────────┐
                    ▼                  ▼                   ▼
             ┌──────────┐       ┌──────────┐       ┌──────────┐
             │ completed│       │  failed  │       │ delayed  │
             └──────────┘       └──────────┘       └──────────┘
                                     │                    │
                             final failure           backoff wait
                           (no more retries)         → back to wait
```

### Job Locking and Stall Detection

```
  BullMQ uses optimistic locking to prevent duplicate processing:
  
  1. Worker acquires lock: SET bullmq:active:<jobId> 1 PX 30000
     (30s lock TTL = stalledInterval)
     
  2. Worker processing:
     Every 15s (lockRenewTime = stalledInterval/2), worker renews lock
     
  3. Worker crashes:
     Lock expires after 30s → other workers detect stalled job
     Job moved back to 'waiting' queue → another worker picks it up
     
  4. Worker finishes:
     LREM bullmq:wait... + ZADD bullmq:completed...
     Lock released automatically
     
  Setting stalledInterval too low: healthy workers falsely marked stalled
  Setting stalledInterval too high: crashed workers' jobs are stuck longer
  Default 30s is appropriate for most workloads.
```

### Concurrency Model

```
  BullMQ concurrency = number of jobs processed simultaneously per worker:
  
  worker = new Worker('notifications', processor, { concurrency: 5 });
  
  With concurrency: 5 and 3 worker processes:
    Total parallel jobs = 5 × 3 = 15 simultaneous jobs
    
  When to lower concurrency:
    - CPU-intensive processing (image resize, PDF gen)
      → match to CPU cores
      → concurrency: os.cpus().length
      
  When to raise concurrency:
    - I/O-bound processing (HTTP requests, email sending)
      → most time waiting for network
      → concurrency: 20-50 (limited by downstream rate limits)
      
  BullMQ vs node-cluster:
    node-cluster = multiple OS processes (no shared memory)
    BullMQ concurrency = multiple async tasks within ONE process
    Use both: cluster for CPU isolation, concurrency for I/O
```

### Rate Limiting

```
  BullMQ can rate-limit job consumption per second:
  
  const worker = new Worker('notifications', processor, {
    limiter: { max: 100, duration: 1000 }  // 100 jobs/second max
  });
  
  When limit is hit:
    Worker pauses, no new jobs claimed until window resets
    Jobs stay in 'waiting' — not dropped, not failed
    
  Critical for email providers:
    SendGrid: 100 emails/second (free), 600/s (paid)
    Mailgun: 200/minute free tier
    Without rate limiting: all jobs hit provider → 429 → all fail → all retry
    With rate limiting: jobs flow through steadily → no 429s
```

---

## Code Examples

### Email Worker

```typescript
// src/workers/email.worker.ts

import { Worker, type Job } from 'bullmq';
import nodemailer from 'nodemailer';
import { redisConnection } from '../config/redis.config.js';
import type { NotificationJob } from '../queues/notification.queue.js';
import { recordDelivery } from '../db/delivery.repository.js';
import pino from 'pino';

const log = pino({ name: 'email-worker' });

// MailHog local SMTP (no credentials required)
const transporter = nodemailer.createTransport({
  host: process.env.SMTP_HOST ?? 'localhost',
  port: parseInt(process.env.SMTP_PORT ?? '1025'),
  secure: false,
});

async function processEmailJob(job: Job<NotificationJob>): Promise<void> {
  const { notificationId, recipient, subject, body } = job.data;
  
  log.info({ notificationId, recipient, attempt: job.attemptsMade + 1 }, 'Sending email');

  // Record attempt before sending — idempotency check
  const alreadySent = await recordDelivery(notificationId, 'email', 'attempting');
  if (alreadySent === 'duplicate') {
    log.warn({ notificationId }, 'Duplicate job — skipping (idempotency check)');
    return;
  }

  // Update progress so Bull Board UI shows live status
  await job.updateProgress(30);

  await transporter.sendMail({
    from: 'noreply@flowforge.local',
    to: recipient,
    subject: subject ?? '(No subject)',
    text: body,
  });

  await job.updateProgress(100);
  await recordDelivery(notificationId, 'email', 'delivered');
  
  log.info({ notificationId }, 'Email delivered');
}

export const emailWorker = new Worker<NotificationJob>(
  'notifications',
  processEmailJob,
  {
    connection: redisConnection,
    concurrency: 10,         // 10 simultaneous email sends per worker
    limiter: {
      max: 50,               // max 50 emails/second (MailHog: unlimited, but good practice)
      duration: 1000,
    },
  }
);

// Attach event listeners for observability
emailWorker.on('completed', (job) => {
  log.debug({ jobId: job.id }, 'Email job completed');
});

emailWorker.on('failed', (job, err) => {
  log.error({ jobId: job?.id, err, attempts: job?.attemptsMade }, 'Email job failed');
});

emailWorker.on('stalled', (jobId) => {
  log.warn({ jobId }, 'Email job stalled — will be retried');
});
```

### Webhook Worker

```typescript
// src/workers/webhook.worker.ts

import { Worker, type Job } from 'bullmq';
import axios, { type AxiosError } from 'axios';
import { redisConnection } from '../config/redis.config.js';
import type { NotificationJob } from '../queues/notification.queue.js';
import { recordDelivery } from '../db/delivery.repository.js';
import pino from 'pino';

const log = pino({ name: 'webhook-worker' });

async function processWebhookJob(job: Job<NotificationJob>): Promise<void> {
  const { notificationId, recipient: webhookUrl, body, metadata } = job.data;
  
  log.info({ notificationId, webhookUrl }, 'Sending webhook');

  try {
    const response = await axios.post(
      webhookUrl,
      { notificationId, body, metadata },
      {
        timeout: 5000,        // 5s timeout — don't wait for slow endpoints
        headers: {
          'Content-Type': 'application/json',
          'X-FlowForge-Delivery-ID': notificationId,
        },
        // Only 2xx responses are considered success
        validateStatus: (status) => status >= 200 && status < 300,
      }
    );

    await recordDelivery(notificationId, 'webhook', 'delivered', {
      statusCode: response.status,
    });
    log.info({ notificationId, statusCode: response.status }, 'Webhook delivered');
  } catch (err) {
    const axiosErr = err as AxiosError;
    const statusCode = axiosErr.response?.status;
    
    await recordDelivery(notificationId, 'webhook', 'failed', { statusCode });

    // Throw to trigger BullMQ retry with exponential backoff
    throw new Error(`Webhook delivery failed: HTTP ${statusCode ?? 'no response'}`);
  }
}

export const webhookWorker = new Worker<NotificationJob>(
  'notifications',
  processWebhookJob,
  {
    connection: redisConnection,
    concurrency: 20,         // webhooks are I/O bound — high concurrency is fine
  }
);
```

### Progress and Monitoring

```typescript
// src/routes/job-status.route.ts — check job status from API

import { Router } from 'express';
import { notificationQueue } from '../queues/notification.queue.js';

const router = Router();

// GET /api/v1/notifications/:notificationId/status
router.get('/:notificationId/status', async (req, res) => {
  const { notificationId } = req.params;
  
  // Jobs are stored by notificationId (set as jobId during enqueue)
  const job = await notificationQueue.getJob(notificationId);
  
  if (!job) {
    res.status(404).json({ error: 'Notification not found' });
    return;
  }

  const state = await job.getState();
  
  res.json({
    notificationId,
    state,                                // 'waiting' | 'active' | 'completed' | 'failed'
    attempts: job.attemptsMade,
    progress: job.progress,
    failedReason: job.failedReason ?? undefined,
    completedAt: job.finishedOn
      ? new Date(job.finishedOn).toISOString()
      : undefined,
  });
});

export default router;
```

---

## Try It Yourself

**Exercise:** Observe the full job lifecycle: enqueue → active → completed/failed.

```typescript
// bullmq-lifecycle.exercise.ts

// TODO:
// 1. Register a Worker with a processor that:
//    - Reports progress at 0%, 50%, 100%
//    - Throws on the first attempt (to trigger a retry)
//    - Succeeds on the second attempt

import { Queue, Worker, type Job } from 'bullmq';
import { redisConnection } from './src/config/redis.config.js';

interface MyJob { message: string; }

const queue = new Queue<MyJob>('lifecycle-test', { connection: redisConnection });
let attemptCount = 0;

// TODO: create a Worker for 'lifecycle-test' that:
// - logs "Processing attempt N for message: ..."
// - calls job.updateProgress at 0, 50, 100
// - throws Error('simulated failure') if attemptCount === 1
// - returns normally on subsequent attempts

const worker = new Worker<MyJob>('lifecycle-test', async (job: Job<MyJob>) => {
  // YOUR CODE HERE
}, {
  connection: redisConnection,
});

// 2. Enqueue a job
const job = await queue.add('test', { message: 'Hello BullMQ' }, { attempts: 3 });
console.log('Enqueued job:', job.id);

// 3. Poll job status until it reaches 'completed' or 'failed'
// YOUR CODE HERE — poll every 500ms and log state transitions

// 4. Expected output:
// Processing attempt 1 for message: Hello BullMQ → throws
// Processing attempt 2 for message: Hello BullMQ → succeeds
// Final state: completed
```

<details>
<summary>Show solution</summary>

```typescript
import { Queue, Worker, type Job } from 'bullmq';
import { redisConnection } from './src/config/redis.config.js';

interface MyJob { message: string; }

const queue = new Queue<MyJob>('lifecycle-test', { connection: redisConnection });
let attemptCount = 0;

const worker = new Worker<MyJob>(
  'lifecycle-test',
  async (job: Job<MyJob>) => {
    attemptCount++;
    console.log(`Processing attempt ${attemptCount} for: ${job.data.message}`);
    await job.updateProgress(0);
    await new Promise(r => setTimeout(r, 100));
    await job.updateProgress(50);

    if (attemptCount === 1) throw new Error('simulated failure');

    await job.updateProgress(100);
    console.log('Success!');
  },
  { connection: redisConnection }
);

const job = await queue.add('test', { message: 'Hello BullMQ' }, {
  attempts: 3,
  backoff: { type: 'fixed', delay: 500 },
});

console.log('Enqueued job:', job.id);

// Poll until done
while (true) {
  await new Promise(r => setTimeout(r, 300));
  const fresh = await queue.getJob(job.id!);
  const state = await fresh?.getState();
  console.log('State:', state, '| Progress:', fresh?.progress);
  if (state === 'completed' || state === 'failed') break;
}

await worker.close();
await queue.close();
```

</details>

---

## Capstone Connection

The retry configuration `{ attempts: 5, backoff: { type: 'exponential', delay: 1000 } }` in FlowForge means a temporary MailHog outage won't lose any notifications. The first retry fires in 1s, then 2s, 4s, 8s, 16s. If MailHog is down for 30 seconds and recovers, all queued notifications deliver successfully — the user who trigged the notification never knows there was a blip.
