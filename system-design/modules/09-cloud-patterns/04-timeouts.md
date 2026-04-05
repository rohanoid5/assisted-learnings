# 9.4 — Timeout & Deadline Propagation

## Concept

A timeout is the maximum time a caller is willing to wait for a response. Without timeouts, a single slow downstream dependency causes requests to queue indefinitely, exhausting connection pools and crashing the caller. Deadline propagation takes this further: when a client sets a deadline for an entire request, every service involved in serving that request must know the remaining budget and refuse to start work it can't complete in time.

---

## Deep Dive

### The Cost of No Timeouts

```
  ScaleForge calls FlowForge with no timeout.
  FlowForge's database starts running a slow query (5 minutes).
  
  t=0:    1 ScaleForge connection waiting for FlowForge
  t=10s:  20 connections waiting (more requests arrived)
  t=60s:  200 connections waiting — pool exhausted
  t=60s:  Redirect requests need DB connection → pool full → 503
  t=5min: FlowForge query finishes, connections release
  t=5min: 200 queued requests get a connection SIMULTANEOUSLY
          → thundering herd on DB
  
  Resolution: set a timeout on every network call. Always.
```

### Timeout Layers

```
  ┌────────────────────────────────────────────────────────────┐
  │  Layer           Default  ScaleForge setting               │
  │                                                            │
  │  DNS resolution  none     5s (set via environment)         │
  │  TCP connect     none     3s (AbortController timeout)     │
  │  HTTP headers    none     5s (included in total timeout)   │
  │  HTTP body read  none     10s (included in total timeout)  │
  │  DB query        none     10s (statement_timeout)          │
  │  Redis command   none     3s (ioredis commandTimeout)      │
  │  Total request   none     30s (Nginx proxy_read_timeout)   │
  └────────────────────────────────────────────────────────────┘
  
  Key: set timeout at EVERY layer independently.
  "Total request" timeout at Nginx is the outer bound.
  The inner timeouts must all be shorter than the outer bound.
```

### Deadline Propagation

```
  Client sets an overall deadline: this request must finish in 2000ms.
  
  Client → ScaleForge             deadline: 2000ms remaining
              │ ─── call FlowForge: wait max 1800ms (200ms budget for self)
              │     FlowForge ─── call SMTP: wait max 1500ms
              │                   SMTP: takes 600ms ✓
              │◄─── FlowForge responds in 700ms total ✓
              │
              ScaleForge total: 900ms ✓ (within 2000ms)
  
  If SMTP takes 2000ms:
    FlowForge kills the SMTP call at 1500ms (SMTP budget exceeded)
    FlowForge returns error to ScaleForge
    ScaleForge uses remaining 300ms of its budget to log + respond
    Client gets error before original 2000ms deadline expires
  
  Implementation: pass remaining deadline via header
    Outgoing headers: X-Request-Deadline: <epoch timestamp ms>
    Each service checks on receipt: if Date.now() > deadline → 408 immediately
```

---

## Code Examples

### AbortController-Based Timeout for `fetch`

```typescript
// src/resilience/timeout.ts
// Creates an AbortSignal that fires after `ms` milliseconds.
// Use this for every outbound fetch call.

export function timeoutSignal(ms: number): AbortSignal {
  return AbortSignal.timeout(ms);  // Node.js 18+
  // Pre-18 fallback: use AbortController + setTimeout
}

// Combined abort: cancel if either the timeout fires OR the parent is aborted
export function combinedSignal(timeoutMs: number, parentSignal?: AbortSignal): AbortSignal {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(new Error(`Timeout after ${timeoutMs}ms`)), timeoutMs);

  parentSignal?.addEventListener('abort', () => {
    clearTimeout(timer);
    controller.abort(parentSignal.reason);
  });

  return controller.signal;
}
```

### Deadline Propagation Middleware

```typescript
// src/middleware/deadline.middleware.ts
// Reads the X-Request-Deadline header from incoming requests.
// Attaches an AbortSignal to res.locals for route handlers to use.
// Downstream calls use combinedSignal(budget, res.locals.signal).

import type { Request, Response, NextFunction } from 'express';
import { combinedSignal } from '../resilience/timeout.js';

declare global {
  // eslint-disable-next-line @typescript-eslint/no-namespace
  namespace Express {
    interface Locals {
      signal: AbortSignal;
      remainingMs: () => number;
      deadlineMs: number;
    }
  }
}

export function deadlineMiddleware(req: Request, res: Response, next: NextFunction): void {
  const deadlineHeader = req.headers['x-request-deadline'];
  const now = Date.now();

  let deadlineMs: number;

  if (deadlineHeader && !Array.isArray(deadlineHeader)) {
    deadlineMs = Number(deadlineHeader);
    if (deadlineMs <= now) {
      // Request already past deadline before processing starts
      res.status(408).json({ error: 'Request deadline exceeded', code: 'DEADLINE_EXCEEDED' });
      return;
    }
  } else {
    // No deadline from client — set a default ceiling
    deadlineMs = now + 30_000;
  }

  const remainingMs = deadlineMs - now;
  res.locals.deadlineMs = deadlineMs;
  res.locals.remainingMs = () => deadlineMs - Date.now();
  res.locals.signal = combinedSignal(remainingMs);

  next();
}
```

### Redis Client with Timeout

```typescript
// src/cache/redis.ts
import Redis from 'ioredis';

export const redis = new Redis(process.env.REDIS_URL ?? 'redis://localhost:6379', {
  // Maximum time to wait for a command response
  commandTimeout: 3000,

  // Connection timeout
  connectTimeout: 5000,

  // Reconnect on failure with bounded retries
  retryStrategy: (times) => {
    if (times > 5) return null;  // stop retrying after 5 attempts
    return Math.min(times * 200, 2000);  // 200ms, 400ms, 800ms, 1600ms, 2000ms
  },

  // Enable offline queue: commands won't throw while reconnecting
  // (they wait up to lazyConnect timeout)
  enableOfflineQueue: true,
  maxRetriesPerRequest: 2,
});

redis.on('error', (err) => {
  // ioredis emits 'error' on connection issues — MUST handle to prevent
  // unhandled rejection crash
  console.error({ err }, 'Redis error');
});
```

### Passing Deadline Downstream

```typescript
// src/clients/flowforge.client.ts — deadline-aware version

async enqueueNotification(
  job: NotificationJobInput,
  parentSignal?: AbortSignal,
): Promise<string> {
  // Give ourselves at most 80% of the remaining budget for this call
  // The other 20% is reserved for error handling and response writing
  return await this.breaker.call(async () => {
    const signal = parentSignal
      ? combinedSignal(this.timeoutMs, parentSignal)
      : AbortSignal.timeout(this.timeoutMs);

    const res = await fetch(`${this.baseUrl}/api/v1/notifications`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        // Propagate deadline to FlowForge
        'X-Request-Deadline': String(Date.now() + this.timeoutMs),
      },
      body: JSON.stringify(job),
      signal,
    });

    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    return ((await res.json()) as { jobId: string }).jobId;
  });
}

// Usage in route handler:
// await flowforgeClient.enqueueNotification(job, res.locals.signal);
```

---

## Try It Yourself

**Exercise:** Simulate a slow downstream and verify the timeout fires.

```typescript
// timeout.exercise.ts

// 1. Create a mock "slow" server
// import express from 'express';
// const mockServer = express();
// mockServer.post('/slow', async (_req, res) => {
//   await new Promise(r => setTimeout(r, 5000)); // 5 second delay
//   res.json({ ok: true });
// });
// mockServer.listen(4000);

// 2. Call it with a 1000ms timeout and verify:
//    - Fetch throws "AbortError" after 1000ms (not after 5000ms)
//    - Total execution time is < 1200ms (1000ms + some overhead)

// TODO: Implement this test

// 3. Verify deadline propagation:
//    - Make a request to ScaleForge with X-Request-Deadline: (now + 100ms)
//    - Expected: ScaleForge returns 408 immediately (no DB call, no Redis call)
//    - Confirm in logs: "Request deadline exceeded"
```

<details>
<summary>Show the test implementation</summary>

```typescript
import assert from 'node:assert/strict';

async function testTimeout() {
  const start = Date.now();

  try {
    await fetch('http://localhost:4000/slow', {
      signal: AbortSignal.timeout(1000),
    });
    assert.fail('Should have thrown');
  } catch (err) {
    assert.equal((err as Error).name, 'AbortError');
    const elapsed = Date.now() - start;
    assert.ok(elapsed < 1500, `Took ${elapsed}ms, expected < 1500ms`);
    console.log(`✓ Timeout fired after ${elapsed}ms (expected < 1100ms)`);
  }
}

testTimeout();
```

</details>

---

## Capstone Connection

Every RPC call in ScaleForge has a timeout — Redis gets 3s, Postgres gets 10s per query, FlowForge gets 5s per call. These aren't arbitrary numbers: they're derived from the end-to-end latency budget. If a user expects a redirect to complete in under 100ms, every step of the pipeline must finish in much less than that. Deadline propagation ensures FlowForge doesn't start a database query it can't finish before the overall request expires, preventing wasted compute and held connections.
