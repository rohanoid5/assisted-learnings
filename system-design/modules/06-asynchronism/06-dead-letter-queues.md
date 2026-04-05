# 6.6 — Dead Letter Queues

## Concept

A dead letter queue (DLQ) is a special queue that receives messages which have failed all retry attempts and can no longer be processed normally. Instead of silently dropping poison messages, the DLQ holds them for investigation, manual reprocessing, or alerting. Every production queue system needs a DLQ strategy — eventually, every system encounters a message it cannot process.

---

## Deep Dive

### What Is a Poison Message?

```
  A poison message is a job that always causes the worker to fail:
  
  Examples:
    - Malformed payload: { recipient: null } → nodemailer throws on null recipient
    - Downstream bug: webhook URL returns 500 for a specific payload shape
    - Environmental: SMTP credentials changed → all email jobs fail
    - Data corrupted: JSON.parse fails on a truncated message body
    - Logic bug: division by zero for a specific URL click count value
    
  Without DLQ:
    Poison message retried 5 times → removed from queue (removeOnFail limit)
    → silently lost → no alert → customer never receives notification
    
  With DLQ:
    Poison message retried 5 times → moved to 'failed' queue (BullMQ default)
    → DLQ monitor checks failed queue → fires alert → engineer investigates
    → fix deployed → jobs replayed from DLQ → notifications delivered
```

### DLQ Monitoring Lifecycle

```
  Job creation
       │
       ▼
  ┌─────────────┐
  │   waiting   │
  └──────┬──────┘
         │ worker picks up
         ▼
  ┌─────────────┐
  │   active    │ ◄── attempt 1: throws Error
  └──────┬──────┘
         │ backoff 1s
         ▼
  ┌─────────────┐
  │   delayed   │ ◄── wait 1 second
  └──────┬──────┘
         │
         ▼
  ┌─────────────┐
  │   active    │ ◄── attempt 2: throws Error
  └──────┬──────┘
         │ backoff 2s (exponential)
         │ ...
         │ attempt 5: throws Error (final)
         ▼
  ┌─────────────┐
  │   failed    │ ◄── DEAD LETTER QUEUE
  └──────┬──────┘
         │
         ├──► Alert fired (PagerDuty / Slack)
         ├──► Job preserved for investigation
         └──► Manual replay possible after fix
```

### DLQ Strategies Compared

```
  Strategy             Description                          When to Use
  ─────────────────    ──────────────────────────────────   ──────────────────────────
  Separate DLQ topic   Failed jobs moved to separate queue  Complex systems with multiple
                       with different processing logic      queues needing different DLQ policies
                       
  Failed queue inspect BullMQ's built-in 'failed' queue     Most applications — simple and
  (used here)          removeOnFail: { count: N } controls  built into BullMQ
                       retention
                       
  Alert + discard      Fire alert, log full job payload,    Non-recoverable errors where
                       then delete job                      replay would not help
                       
  Manual replay UI     Ops team reviews DLQ in Bull Board   Any — Bull Board provides this
                       and requeues individual jobs         UI out of the box
```

---

## Code Examples

### BullMQ Failed Queue — Already Your DLQ

```typescript
// src/monitoring/dlq-monitor.ts
// BullMQ stores failed jobs in the 'failed' state automatically.
// This monitor runs periodically to alert on DLQ growth.

import { Queue } from 'bullmq';
import { redisConnection } from '../config/redis.config.js';
import type { NotificationJob } from '../queues/notification.queue.js';
import pino from 'pino';

const log = pino({ name: 'dlq-monitor' });

const ALERT_THRESHOLD = 10; // alert if more than 10 failed jobs

export async function checkDLQ(queue: Queue<NotificationJob>): Promise<void> {
  const counts = await queue.getJobCounts('failed');
  
  if (counts.failed > ALERT_THRESHOLD) {
    log.error(
      { failedCount: counts.failed, queue: queue.name },
      'DLQ threshold exceeded — manual investigation required'
    );
    // In production: send PagerDuty alert, Slack webhook, or email
    await sendDLQAlert(queue.name, counts.failed);
  }

  // Log details of the most recent failed jobs
  const recentFailed = await queue.getFailed(0, 4); // last 5 failed jobs
  for (const job of recentFailed) {
    log.warn({
      jobId: job.id,
      notificationId: job.data.notificationId,
      channel: job.data.channel,
      attempts: job.attemptsMade,
      failedReason: job.failedReason,
      timestamp: job.timestamp ? new Date(job.timestamp).toISOString() : undefined,
    }, 'DLQ job details');
  }
}

async function sendDLQAlert(queueName: string, failedCount: number): Promise<void> {
  // MailHog doesn't need auth — for production, use your alerting system
  log.error({ queueName, failedCount }, 'ALERT: DLQ threshold exceeded');
  // TODO: Integrate with your alert provider (OpsGenie, PagerDuty, etc.)
}

// Run DLQ monitor every 60 seconds
export function startDLQMonitor(queue: Queue<NotificationJob>): NodeJS.Timeout {
  return setInterval(() => checkDLQ(queue), 60_000);
}
```

### Manual Replay: Reprocess All Failed Jobs

```typescript
// src/monitoring/replay.ts — manually replay failed jobs after fixing the root cause
// Called via admin API or CLI script after investigating and fixing the bug

import { Queue } from 'bullmq';
import { redisConnection } from '../config/redis.config.js';
import pino from 'pino';

const log = pino({ name: 'dlq-replay' });

export async function replayFailedJobs(queueName: string): Promise<{ replayed: number }> {
  const queue = new Queue(queueName, { connection: redisConnection });
  
  const failedJobs = await queue.getFailed(0, -1); // get ALL failed jobs
  log.info({ count: failedJobs.length }, 'Starting replay of failed jobs');
  
  let replayed = 0;
  for (const job of failedJobs) {
    try {
      // retry() moves the job from 'failed' back to 'waiting'
      await job.retry('failed');
      replayed++;
      log.debug({ jobId: job.id }, 'Job queued for retry');
    } catch (err) {
      log.error({ jobId: job.id, err }, 'Failed to retry job');
    }
  }

  await queue.close();
  return { replayed };
}

// Admin endpoint: POST /admin/queues/:name/replay
// app.post('/admin/queues/:name/replay', async (req, res) => {
//   const result = await replayFailedJobs(req.params.name);
//   res.json(result);
// });
```

### Classifying Failures: Retryable vs. Non-Retryable

```typescript
// src/workers/email.worker.ts — smart error classification
// Some errors should exhaust retries immediately (non-retryable),
// while others should always retry (transient).

import { UnrecoverableError } from 'bullmq';

async function processEmailJob(job: Job<NotificationJob>): Promise<void> {
  const { recipient, subject, body } = job.data;
  
  // Validate before sending — fail fast on permanent errors
  if (!recipient || !recipient.includes('@')) {
    // UnrecoverableError tells BullMQ: skip remaining retries, go straight to failed
    throw new UnrecoverableError(`Invalid recipient address: "${recipient}"`);
  }

  try {
    await transporter.sendMail({ from: '...', to: recipient, subject, text: body });
  } catch (err) {
    const error = err as NodeJS.ErrnoException;
    
    // SMTP auth failure: retrying won't help until credentials are fixed
    if (error.code === 'EAUTH') {
      throw new UnrecoverableError('SMTP authentication failed — check credentials');
    }
    
    // Connection error: transient, should retry
    // Returning a normal Error lets BullMQ use the retry config
    throw new Error(`SMTP error: ${error.message}`);
  }
}
```

---

## Try It Yourself

**Exercise:** Force a job into the DLQ and then replay it.

```typescript
// dlq-test.exercise.ts

// Step 1: Create a job guaranteed to fail all retries.
// Set an invalid recipient that will cause UnrecoverableError.

// TODO:
// 1. Comment out the UnrecoverableError check in email.worker.ts temporarily
// 2. Enqueue a notification with recipient: "not-an-email"
//    The worker will throw on every attempt and eventually exhaust retries
//
// 3. Check the DLQ:
//    const failedJobs = await emailQueue.getFailed();
//    console.log('Failed jobs:', failedJobs.length);
//    console.log('Reason:', failedJobs[0]?.failedReason);
//
// 4. Fix the issue (restore the check), then replay:
//    await replayFailedJobs('email');
//    Verify the job moves from 'failed' back to 'waiting', then 'failed' again
//    (because the address is still invalid — this is expected!)
//
// 5. Try with a VALID recipient address: verify replay succeeds and job completes.
```

<details>
<summary>Show solution steps</summary>

```typescript
import assert from 'node:assert/strict';
import { emailQueue } from './src/queues/dispatcher.js';
import { replayFailedJobs } from './src/monitoring/replay.js';

// 1. Check failed queue
const failedBefore = await emailQueue.getFailed();
console.log(`Failed jobs in DLQ: ${failedBefore.length}`);

if (failedBefore.length > 0) {
  const job = failedBefore[0]!;
  console.log('Job ID:        ', job.id);
  console.log('Attempts made: ', job.attemptsMade);
  console.log('Failed reason: ', job.failedReason);
  console.log('Job data:      ', JSON.stringify(job.data, null, 2));
}

// 2. Replay
const { replayed } = await replayFailedJobs('email');
console.log(`Replayed ${replayed} jobs`);

// 3. Wait a moment and verify
await new Promise(r => setTimeout(r, 2000));
const counts = await emailQueue.getJobCounts('waiting', 'active', 'completed', 'failed');
console.log('Queue counts after replay:', counts);

// For invalid address: should be back in 'failed' (replay just moved to 'waiting', ran, failed again)
// For valid address:   should be in 'completed'
```

</details>

---

## Capstone Connection

FlowForge's DLQ monitor (`startDLQMonitor`) runs in the worker process and checks the failed queue every 60 seconds. If notifications start failing (e.g., MailHog stopped, SMTP credentials rotated), the DLQ depth grows and an alert fires within 60 seconds. After fixing the root cause, `replayFailedJobs('email')` recovers all undelivered notifications without any manual job reconstruction — because the full job payload is preserved in Redis.
