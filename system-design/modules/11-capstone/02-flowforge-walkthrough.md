# 11.2 — FlowForge: Full Source Walkthrough

## Overview

FlowForge is the notification-dispatcher capstone. It receives job requests from ScaleForge and other services, queues them in BullMQ, and dispatches them via email and webhook channels. This file annotates the complete request lifecycle for job submission and delivery.

---

## Project Structure

```
flowforge/
├── src/
│   ├── app.ts                    — Express setup + middleware
│   ├── server.ts                 — HTTP server + graceful shutdown
│   ├── config.ts                 — Environment validation (Zod)
│   ├── db/
│   │   └── pool.ts               — Single pg pool (audit log and job state)
│   ├── cache/
│   │   └── redis.ts              — ioredis (BullMQ backing store)
│   ├── queues/
│   │   ├── notification.queue.ts — BullMQ Queue definition (Module 06)
│   │   └── notification.worker.ts — BullMQ Worker, typed processor (Module 06)
│   ├── processors/
│   │   ├── email.processor.ts    — Dispatches one email job
│   │   └── webhook.processor.ts  — Dispatches one webhook job
│   ├── routes/
│   │   ├── notification.routes.ts — POST /api/v1/notifications
│   │   └── health.routes.ts
│   ├── middleware/
│   │   ├── correlation-id.middleware.ts
│   │   ├── deadline.middleware.ts
│   │   └── metrics.middleware.ts
│   └── observability/
│       ├── logger.ts
│       └── metrics.ts
└── prometheus/
```

---

## Request Lifecycle: POST /api/v1/notifications

```
  ScaleForge calls:
  POST /api/v1/notifications
  { "channel": "email", "to": "user@example.com", "subject": "...", "body": "..." }
  Headers: X-Request-Deadline: 1715001234567
           X-Correlation-ID: abc-123
       │
       ▼
  Express: correlationId → deadline → metrics → body parse
       │
       ▼
  notificationRouter POST handler
       │
       ├─ Validate body (Zod schema: channel enum, to/subject/body required)
       │
       ├─ Try Tier 0: INSERT into notifications table (audit log)
       │       On failure → log warn, continue without audit (Tier 1 fallback)
       │
       ├─ Enqueue to BullMQ
       │       notificationQueue.add(channel, payload, {
       │         attempts: 5,
       │         backoff: { type: 'exponential', delay: 1000 },
       │         removeOnComplete: { count: 1000 },
       │         removeOnFail:     { count: 5000 },
       │       })
       │
       ├─ Return 201 { jobId, status: 'queued' }
       │      or 202 { jobId, status: 'queued_no_audit' } if DB was unavailable
       │
       └─ Return 503 with Retry-After if BullMQ also unavailable
```

---

## Job Processor Lifecycle

```
  BullMQ Worker picks up a job:
  { channel: 'email', to: 'user@example.com', subject: '...', body: '...' }
       │
       ▼
  emailProcessor(job)
       │
       ├─ Build payload: { from, to, subject, html }
       │
       ├─ Call SMTP client with 10s timeout (AbortSignal.timeout(10_000))
       │
       ├─ On success:
       │     UPDATE notifications SET status='delivered', delivered_at=now()
       │     Return { delivered: true, messageId: '...' }
       │
       └─ On failure:
             If attempt < maxAttempts:
               throw error — BullMQ retries with exponential backoff
             If attempt === maxAttempts:
               UPDATE notifications SET status='failed'
               Return { delivered: false, error: 'SMTP_TIMEOUT' }
               (BullMQ marks job as 'failed', moves to failed queue)
```

---

## BullMQ Queue and Worker Setup

```typescript
// src/queues/notification.queue.ts
import { Queue } from 'bullmq';
import { redis } from '../cache/redis.js';
import type { NotificationJobInput } from '../types.js';

export const notificationQueue = new Queue<NotificationJobInput>('notifications', {
  connection: redis,
  defaultJobOptions: {
    attempts:  5,
    backoff:   { type: 'exponential', delay: 1000 },
    removeOnComplete: { count: 1000 },
    removeOnFail:     { count: 5000 },
  },
});


// src/queues/notification.worker.ts
import { Worker } from 'bullmq';
import { redis }       from '../cache/redis.js';
import { emailProcessor }   from '../processors/email.processor.js';
import { webhookProcessor } from '../processors/webhook.processor.js';
import type { NotificationJobInput } from '../types.js';
import { activeWorkersGauge } from '../observability/metrics.js';

export const notificationWorker = new Worker<NotificationJobInput>(
  'notifications',
  async (job) => {
    switch (job.data.channel) {
      case 'email':   return emailProcessor(job);
      case 'webhook': return webhookProcessor(job);
      default:
        throw new Error(`Unknown channel: ${String((job.data as { channel: unknown }).channel)}`);
    }
  },
  {
    connection:  redis,
    concurrency: 10,   // bulkhead: max 10 concurrent notification workers (Module 09.3)
    limiter: {
      max:      100,   // max 100 jobs per ...
      duration: 1000,  // ... 1 second (rate limit worker throughput)
    },
  },
);

// Instrument worker event lifecycle for Prometheus
notificationWorker.on('active',    () => activeWorkersGauge.inc());
notificationWorker.on('completed', () => activeWorkersGauge.dec());
notificationWorker.on('failed',    () => activeWorkersGauge.dec());
```

---

## Email Processor

```typescript
// src/processors/email.processor.ts
import type { Job } from 'bullmq';
import type { NotificationJobInput } from '../types.js';
import { logger } from '../observability/logger.js';
import { notificationDeliveredCounter, notificationFailedCounter } from '../observability/metrics.js';

export async function emailProcessor(job: Job<NotificationJobInput>): Promise<{ delivered: boolean }> {
  const childLogger = logger.child({
    jobId: job.id,
    attempt: job.attemptsMade,
    channel: 'email',
  });

  childLogger.info({ to: job.data.to }, 'Processing email notification');

  try {
    // Send via SMTP (nodemailer or similar)
    const result = await sendEmail({
      to:      job.data.to,
      subject: job.data.subject ?? 'Notification',
      html:    job.data.body,
    });

    childLogger.info({ messageId: result.messageId }, 'Email delivered');
    notificationDeliveredCounter.inc({ channel: 'email' });
    return { delivered: true };

  } catch (err) {
    childLogger.warn({ err, attemptsRemaining: job.opts.attempts! - job.attemptsMade }, 'Email delivery failed');
    notificationFailedCounter.inc({ channel: 'email' });

    // Throw — BullMQ will retry with exponential backoff
    throw err;
  }
}

// sendEmail stub — replace with nodemailer/Resend/SendGrid SDK
async function sendEmail(options: { to: string; subject: string; html: string }) {
  const res = await fetch(process.env['SMTP_RELAY_URL']!, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(options),
    signal: AbortSignal.timeout(10_000),  // 10s SMTP timeout (Module 09.4)
  });
  if (!res.ok) throw new Error(`SMTP relay error: ${res.status}`);
  return res.json() as Promise<{ messageId: string }>;
}
```

---

## Metrics

```typescript
// src/observability/metrics.ts
import { Counter, Gauge, Histogram, register } from 'prom-client';

// Queue metrics (polled every 30s — see Module 08.4)
export const queueDepthGauge = new Gauge({
  name:       'flowforge_queue_depth',
  help:       'Number of jobs waiting in the BullMQ queue',
  labelNames: ['state'],   // waiting, active, delayed, failed
});

// Worker concurrency gauge
export const activeWorkersGauge = new Gauge({
  name: 'flowforge_active_workers',
  help: 'Number of currently executing notification jobs',
});

// Delivery counters
export const notificationDeliveredCounter = new Counter({
  name:       'flowforge_notifications_delivered_total',
  help:       'Notifications successfully delivered',
  labelNames: ['channel'],
});

export const notificationFailedCounter = new Counter({
  name:       'flowforge_notifications_failed_total',
  help:       'Notifications that exhausted all retries',
  labelNames: ['channel'],
});

// API latency histogram
export const httpRequestDurationSeconds = new Histogram({
  name:       'http_request_duration_seconds',
  help:       'HTTP request duration',
  labelNames: ['method', 'handler', 'status'],
  buckets:    [0.01, 0.05, 0.1, 0.25, 0.5, 1, 2.5, 5],
});
```

---

## Capstone Connection

FlowForge handles all async communication that falls outside ScaleForge's synchronous request path. The clean boundary — ScaleForge enqueues, FlowForge processes — means that a slow email provider never slows down URL creation. The BullMQ retry logic (exponential backoff, 5 attempts) is the reliability layer for delivery. The circuit breaker in ScaleForge's `FlowForgeClient` is the reliability layer for enqueuing. Together they ensure that you never lose a notification: if the queue is unavailable, ScaleForge compensates (saga) and informs the caller; if the email provider is flaky, FlowForge retries until delivery succeeds or the job is moved to the dead-letter queue for manual review.
