# 6.5 — Backpressure & Flow Control

## Concept

Backpressure is the mechanism by which a slow consumer signals to a fast producer to slow down. Without backpressure, a fast producer overwhelms a slow consumer — the queue grows unboundedly, memory fills up, and the system crashes. Backpressure converts that crash into controlled slowdown.

---

## Deep Dive

### The Overflow Problem

```
  Production rate > Consumption rate → Queue grows forever
  
  Scenario: viral link launches, 10k clicks/sec hit ScaleForge
  Click events queued to BullMQ: 10k jobs/sec
  Click processor throughput: 500 jobs/sec
  
  t=0:    Queue depth = 0
  t=1s:   Queue depth = 9,500  (+9.5k)
  t=10s:  Queue depth = 95,000
  t=60s:  Queue depth = 570,000
  
  Redis memory: 570k jobs × 200 bytes/job = 114 MB
  At 500k jobs: Redis OOM → eviction → job loss → counter drift
  
  Without flow control:
    - Queue grows until OOM or Redis eviction
    - Latency for all OTHER queues in the same Redis grows
    - Stats drift as evicted jobs are never processed
  
  With backpressure:
    - Producer is slowed down: accept 500 req/s, queue 500 jobs/s
    - Extra 9.5k req/s get 429 Too Many Requests
    - Queue depth stays bounded: ~0 at steady state
    - Clients can retry after backoff
```

### Backpressure Strategies

```
  1. Rate Limiting (Token Bucket / Leaky Bucket):
     Producer side: limit how fast jobs are created.
     See Module 05 Redis rate limiter (Lua script with ZREMRANGEBYSCORE).
  
  2. Queue Depth Check at Enqueue Time:
     If queue.waiting > MAX_QUEUE_DEPTH → reject new job → return 429.
     Simple and effective: protects Redis from unbounded growth.
     
  3. BullMQ built-in rate limiting:
     Worker-side: limiter: { max: 100, duration: 1000 }
     Slows consumption (useful for API rate limits, not queue depth).
     Does NOT limit production — queue still grows.
  
  4. Graceful Degradation (Shedding):
     Under extreme load, drop low-priority work and keep critical work.
     Priority queues: critical=1, normal=5, low=10 (BullMQ uses 1=highest).
```

### Queue Depth Monitoring

```
  Healthy queue behavior:
  
  Queue depth over time (ideal):
    ▲
    │
  50│   ████
    │  ██  ██
    │ ██    ██
    │ █      █████
    └──────────────► time
    
    Short-lived spikes, quickly drained.
    
  Unhealthy queue behavior (backpressure needed):
  
    ▲
 50k│               ████████████████
    │          █████
  5k│      ████
    │  █████
    └──────────────► time
    
    Queue growing monotonically → OOM imminent.
    Alert threshold: waiting > 1000 for > 30s.
```

---

## Code Examples

### Queue Depth Guard at Enqueue Time

```typescript
// src/queues/queue-guard.ts — check queue depth before accepting new jobs

import type { Queue } from 'bullmq';

const MAX_WAITING_JOBS = 5000; // alert if queue exceeds this
const REJECT_AFTER = 10000;   // hard reject after this depth

export class QueueDepthGuard {
  constructor(private readonly queue: Queue) {}

  async canEnqueue(): Promise<{ allowed: boolean; depth: number }> {
    const counts = await this.queue.getJobCounts('waiting', 'delayed');
    const depth = counts.waiting + counts.delayed;
    return {
      allowed: depth < REJECT_AFTER,
      depth,
    };
  }
}

// In the API route:
const guard = new QueueDepthGuard(notificationQueue);

router.post('/', async (req, res) => {
  const { allowed, depth } = await guard.canEnqueue();
  
  if (!allowed) {
    // Return 503 with Retry-After — client should back off and retry
    res.status(503)
      .set('Retry-After', '30')
      .json({
        error: 'Queue at capacity. Retry after 30 seconds.',
        queueDepth: depth,
      });
    return;
  }

  // ... proceed with enqueue
});
```

### Priority Queue: Critical Before Normal

```typescript
// src/queues/priority-notification.queue.ts
// Lower BullMQ priority number = processed first (1 = highest)

import { Queue } from 'bullmq';
import { redisConnection } from '../config/redis.config.js';
import type { NotificationJob } from './notification.queue.js';

export type NotificationPriority = 'critical' | 'normal' | 'low';

const PRIORITY_MAP: Record<NotificationPriority, number> = {
  critical: 1,  // BullMQ: processed first (e.g., password reset email)
  normal: 5,    // (e.g., weekly digest)
  low: 10,      // (e.g., marketing, can be dropped under load)
};

export const priorityQueue = new Queue<NotificationJob>('notifications-priority', {
  connection: redisConnection,
});

export async function enqueueWithPriority(
  job: NotificationJob,
  priority: NotificationPriority
): Promise<string> {
  const added = await priorityQueue.add(
    job.channel,
    job,
    {
      jobId: job.notificationId,
      priority: PRIORITY_MAP[priority],
      // Low-priority jobs: remove if still waiting after 1 hour (graceful shedding)
      ...(priority === 'low' ? { delay: 0, removeOnFail: { age: 3600 } } : {}),
    }
  );
  return added.id!;
}
```

### Adaptive Workers: Scale Concurrency with Queue Depth

```typescript
// src/workers/adaptive-email.worker.ts
// Increase worker concurrency when queue is deep, decrease when shallow
// (Useful when running on infrastructure with spare capacity)

import { Worker, type Job } from 'bullmq';
import { redisConnection } from '../config/redis.config.js';
import { notificationQueue } from '../queues/notification.queue.js';
import type { NotificationJob } from '../queues/notification.queue.js';

const MIN_CONCURRENCY = 5;
const MAX_CONCURRENCY = 50;

let worker: Worker<NotificationJob>;

async function adjustConcurrency(): Promise<void> {
  const counts = await notificationQueue.getJobCounts('waiting');
  const waiting = counts.waiting;

  // Linear scale: 0 waiting = 5 concurrency, 5000 waiting = 50 concurrency
  const target = Math.min(
    MAX_CONCURRENCY,
    Math.max(MIN_CONCURRENCY, Math.floor(waiting / 100))
  );

  // BullMQ Workers don't expose concurrency adjustment — restart with new config
  // In practice, use Kubernetes HPA (Horizontal Pod Autoscaler) to scale worker pods
  // This is a simplified demonstration of the concept
  console.log(`Queue depth: ${waiting}. Recommended concurrency: ${target}`);
}

// In production: expose queue depth as a Prometheus metric, then
// configure HPA to scale worker replicas based on that metric
setInterval(adjustConcurrency, 10_000); // check every 10s
```

---

## Try It Yourself

**Exercise:** Observe queue growth vs. bounded queue behavior.

```typescript
// backpressure.exercise.ts

// TODO:
// 1. Start FlowForge WITHOUT the QueueDepthGuard
// 2. Enqueue 5000 jobs rapidly:

import { notificationQueue } from './src/queues/notification.queue.js';
async function flood() {
  const promises = Array.from({ length: 5000 }, (_, i) =>
    notificationQueue.add('email', {
      notificationId: `flood-${i}`,
      channel: 'email',
      recipient: 'test@example.com',
      subject: 'Flood test',
      body: `Job ${i}`,
    })
  );
  await Promise.all(promises);
  const counts = await notificationQueue.getJobCounts('waiting');
  console.log('Queue depth after flood:', counts.waiting);
}

// TODO: Run flood(), then check Redis memory:
// redis-cli INFO memory | grep used_memory_human

// 3. Restart workers and observe queue drain time
// 4. Add QueueDepthGuard, repeat flood(), verify 429s are returned after depth=10000
```

<details>
<summary>Show expected outcome</summary>

```
Without QueueDepthGuard:
  Queue depth after flood: 5000
  Redis memory: ~5 MB (small jobs)
  Workers drain: ~5000 / 10 concurrency = 500s at 10 jobs/s throughput
  No protection against even larger floods

With QueueDepthGuard (REJECT_AFTER = 10000):
  First 10000 jobs: accepted
  Job 10001+: returns 503 with Retry-After: 30
  Queue depth: capped at 10000
  Redis memory: bounded

Key insight: the guard doesn't prevent processing delay, but it
prevents unbounded memory growth and gives callers a clear signal
to back off instead of silently dropping events.
```

</details>

---

## Capstone Connection

ScaleForge's click tracking queue needs backpressure on viral links. Without it, a single YouTube video linking to a short URL could flood the click queue with millions of jobs within minutes. The `QueueDepthGuard` converts overflow into 429 responses — the client (browser redirect) doesn't need to retry click tracking, so the 429 is acceptable. Critical paths like `GET /abc` (the redirect itself) are never throttled — only the async click recording is.
