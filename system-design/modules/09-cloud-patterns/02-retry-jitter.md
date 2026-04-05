# 9.2 — Retry with Jitter

## Concept

Retrying a failed call increases reliability, but naive retries (immediate, uniform) can make failures worse: when a service struggles and many clients retry at the same time, the retry thundering herd amplifies the load exactly when the service is most fragile. Adding jitter — random variation to retry delays — spreads the load, and exponential backoff ensures retries slow down as errors persist.

---

## Deep Dive

### The Thundering Herd Problem

```
  Scenario: Redis is momentarily unavailable for 200ms (e.g., failover).
  1000 clients are waiting. Redis recovers.
  ALL 1000 clients retry at the same instant.

  Without jitter:
    t=200ms: Redis recovers
    t=200ms: 1000 simultaneous connections hit Redis
    Redis connection limit = 500 → 500 connections dropped
    500 clients get another error → retry AGAIN at t=600ms
    Thundering herd repeats until all connections drain.

  With full jitter:
    t=200ms: Redis recovers
    Each client waits random(0, 400ms) before retrying
    1000 clients spread over 400ms = 2.5 connections/ms
    Redis handles it with no stress.
```

### Backoff Formulas

```
  Fixed delay: always wait 1000ms
    retry 1: 1000ms
    retry 2: 1000ms   ← doesn't slow down under sustained failure
  
  Exponential backoff: delay = base × 2^attempt
    base=100ms:
    retry 1: 100ms
    retry 2: 200ms
    retry 3: 400ms
    retry 4: 800ms
    retry 5: 1600ms   ← but all clients synchronize at the same delay!
  
  Exponential backoff + full jitter: delay = random(0, base × 2^attempt)
    base=100ms, max=10000ms:
    retry 1: random(0, 100ms)
    retry 2: random(0, 200ms)
    retry 3: random(0, 400ms)    ← spread becomes wider with each attempt
    retry 4: random(0, 800ms)    
    retry 5: random(0, 1600ms)   ← capped at max
  
  Decorrelated jitter (AWS recommendation):
    delay = min(max, random(base, prev_delay × 3))
    Produces excellent spread with no synchronization.
```

### What to Retry vs. What Not to Retry

```
  RETRY:
    Network timeouts          — transient, likely resolved on retry
    503 Service Unavailable   — server overloaded, retry after Retry-After header
    429 Too Many Requests      — wait for rate limit window to reset
    500 Internal Server Error — might be transient (GC pause, restart)
  
  DO NOT RETRY:
    400 Bad Request           — your payload is wrong; retrying wastes resources
    401 Unauthorized           — token is invalid; retry won't fix auth
    403 Forbidden              — permission denied; must change authorization
    404 Not Found             — the resource doesn't exist
    422 Unprocessable Entity  — validation error; fix the payload first
  
  Rule: Only retry errors that are (a) transient and (b) idempotent.
  "Idempotent" = retrying produces the same result as calling once.
  POST /notifications with jobId header = idempotent (dedup by jobId).
  POST /charge-card = NOT idempotent (would charge twice).
```

---

## Code Examples

### Retry with Exponential Backoff + Full Jitter

```typescript
// src/resilience/retry.ts

interface RetryOptions {
  maxAttempts: number;
  baseDelayMs: number;
  maxDelayMs: number;
  retryableStatuses?: number[];   // HTTP status codes to retry
  onRetry?: (attempt: number, error: Error, delayMs: number) => void;
}

const DEFAULT_RETRYABLE_STATUSES = [429, 500, 502, 503, 504];

export async function withRetry<T>(
  fn: () => Promise<T>,
  opts: RetryOptions,
): Promise<T> {
  const { maxAttempts, baseDelayMs, maxDelayMs, onRetry } = opts;
  const retryable = opts.retryableStatuses ?? DEFAULT_RETRYABLE_STATUSES;

  let lastError: Error;

  for (let attempt = 1; attempt <= maxAttempts; attempt++) {
    try {
      return await fn();
    } catch (err) {
      lastError = err as Error;

      // Don't retry non-retryable HTTP errors
      const status = (err as { status?: number }).status;
      if (status !== undefined && !retryable.includes(status)) {
        throw err;
      }

      if (attempt === maxAttempts) break;

      // Full jitter: random(0, min(maxDelay, base * 2^attempt))
      const cap = Math.min(maxDelayMs, baseDelayMs * Math.pow(2, attempt));
      const delayMs = Math.random() * cap;

      onRetry?.(attempt, lastError, delayMs);
      await sleep(delayMs);
    }
  }

  throw lastError!;
}

function sleep(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
}
```

### Using Retry in FlowForgeClient

```typescript
// src/clients/flowforge.client.ts
import { withRetry } from '../resilience/retry.js';
import { logger } from '../logger.js';

// Inside FlowForgeClient — wrap the fetch call with retry
async enqueueNotification(job: NotificationJobInput): Promise<string> {
  return await this.breaker.call(async () => {
    return await withRetry(
      async () => {
        const controller = new AbortController();
        const timer = setTimeout(() => controller.abort(), this.timeoutMs);
        try {
          const res = await fetch(`${this.baseUrl}/api/v1/notifications`, {
            method: 'POST',
            headers: {
              'Content-Type': 'application/json',
              // jobId in body provides idempotency at the FlowForge side
            },
            body: JSON.stringify(job),
            signal: controller.signal,
          });

          if (res.status === 429) {
            // Respect server's Retry-After header if present
            const retryAfter = res.headers.get('Retry-After');
            const err = Object.assign(new Error('Rate limited'), {
              status: 429,
              retryAfterMs: retryAfter ? Number(retryAfter) * 1000 : undefined,
            });
            throw err;
          }

          if (!res.ok) {
            const err = Object.assign(new Error(`HTTP ${res.status}`), { status: res.status });
            throw err;
          }

          return ((await res.json()) as { jobId: string }).jobId;
        } finally {
          clearTimeout(timer);
        }
      },
      {
        maxAttempts: 3,
        baseDelayMs: 100,
        maxDelayMs: 2000,
        onRetry: (attempt, err, delayMs) => {
          logger.warn({ attempt, errMsg: err.message, delayMs: Math.round(delayMs) },
            'FlowForge call failed, retrying');
        },
      },
    );
  });
}
```

### BullMQ Retry (Recap from Module 06)

```typescript
// BullMQ has built-in retry with exponential backoff.
// This is the preferred mechanism for async job retries.

import { Queue } from 'bullmq';

const notificationQueue = new Queue('notifications', {
  defaultJobOptions: {
    attempts: 5,
    backoff: {
      type: 'exponential',
      delay: 1000,  // base delay in ms
      // Retry delays: 1s, 2s, 4s, 8s, 16s
      // BullMQ adds jitter automatically in newer versions
    },
    removeOnComplete: { count: 100 },
    removeOnFail: { count: 200 },
  },
});
```

---

## Try It Yourself

**Exercise:** Verify jitter prevents synchronized retry storms.

```typescript
// retry-storm.exercise.ts
import { withRetry } from './src/resilience/retry.js';

// Simulate N clients all retrying a failing service simultaneously
async function simulateRetryStorm(clientCount: number, withJitter: boolean): Promise<void> {
  let callTimestamps: number[] = [];
  let attempt = 0;

  const failingService = async (): Promise<void> => {
    attempt++;
    if (attempt <= clientCount * 2) throw new Error('Service unavailable');
    // "Recovers" after enough attempts
  };

  const retryWithoutJitter = async (): Promise<void> => {
    let a = 0;
    while (true) {
      try {
        return await failingService();
      } catch {
        a++;
        if (a >= 3) throw new Error('Max retries');
        callTimestamps.push(Date.now());
        await new Promise((r) => setTimeout(r, 1000));  // fixed 1s delay
      }
    }
  };

  // TODO:
  // 1. Launch `clientCount` concurrent clients, all calling failingService()
  //    via withRetry (jitter) or retryWithoutJitter (no jitter)
  //
  // 2. Record call timestamps for each retry attempt
  //
  // 3. Bucket timestamps by 100ms windows and print a histogram:
  //    t=0ms:    ████████████████████████████ 28 calls (thundering herd!)
  //    t=100ms:  ████ 4 calls
  //    ...
  //
  //    vs. with jitter:
  //    t=0ms:    ██ 2 calls
  //    t=100ms:  ███ 3 calls
  //    t=200ms:  ███ 3 calls
  //    ...
}

simulateRetryStorm(50, false); // thundering herd
simulateRetryStorm(50, true);  // spread
```

<details>
<summary>Show histogram implementation</summary>

```typescript
function printTimestampHistogram(timestamps: number[], bucketMs = 100): void {
  if (timestamps.length === 0) return;
  const min = Math.min(...timestamps);
  const max = Math.max(...timestamps);
  const buckets: Record<number, number> = {};

  for (const t of timestamps) {
    const bucket = Math.floor((t - min) / bucketMs) * bucketMs;
    buckets[bucket] = (buckets[bucket] ?? 0) + 1;
  }

  for (let t = 0; t <= max - min; t += bucketMs) {
    const count = buckets[t] ?? 0;
    const bar = '█'.repeat(count);
    console.log(`t+${String(t).padStart(5, '0')}ms: ${bar} ${count}`);
  }
}
```

</details>

---

## Capstone Connection

FlowForge's email worker retries via BullMQ's built-in exponential backoff. ScaleForge's HTTP calls to FlowForge retry via `withRetry`. Both have jitter. This matters most during a FlowForge deployment (restart takes 3 seconds): without jitter, every in-flight request retries simultaneously on startup, potentially overwhelming FlowForge's connection pool before it fully initializes. With jitter, the retry load is smeared over several seconds — FlowForge warms up gracefully.
