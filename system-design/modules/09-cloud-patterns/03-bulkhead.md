# 9.3 — Bulkhead Pattern

## Concept

The bulkhead pattern isolates failures by partitioning resources — connection pools, worker threads, or concurrency slots — so that a misbehaving subsystem exhausts only its own allocation, not the entire system's. Named after ship bulkheads that contain flooding to a single compartment, the pattern ensures one slow database query or a runaway external call cannot starve the rest of the application.

---

## Deep Dive

### The Problem Without Bulkheads

```
  ScaleForge has one Express server handling two types of traffic:
  
    Path A: GET /:code (redirects) — fast, cached, high volume
    Path B: POST /api/v1/admin/bulk-import (slow, DB-heavy, low volume)
  
  Without bulkheads: shared connection pool (max=10)
  
    admin user starts a bulk import of 100,000 URLs
    → all 10 pool connections are consumed by INSERT queries
    → redirect requests arrive, need DB connections → WAIT
    → redirect p99 latency spikes from 5ms to 2000ms+
    → SLO violated for ALL users
  
  The admin bulk import killed the redirect path.
  
  ─────────────────────────────────────────────────────────
  
  With bulkheads: two separate pools
  
    redirectPool:   max=8   (high priority, redirect path)
    adminPool:      max=2   (low priority, admin operations)
  
    bulk import consumes adminPool (max 2 connections)
    redirects use redirectPool (8 connections, unaffected)
    bulk import is slower — but admin accepts that trade-off
    redirect SLO is preserved
```

### Types of Bulkheads

```
  1. Connection pool bulkhead (Postgres, Redis)
     Separate pool per workload type
     
  2. Worker process bulkhead (Node.js worker_threads or child_process)
     CPU-intensive work runs in a separate thread pool
     Never blocks the main event loop
     
  3. Concurrency limiter bulkhead (async semaphore)
     Limit how many concurrent calls to a single downstream service
     Other calls get immediate 503 instead of queueing indefinitely
     
  4. BullMQ worker bulkhead
     Separate workers with separate concurrency for each channel
     Email worker backlog can't block webhook workers
```

---

## Code Examples

### Separate Connection Pools per Workload

```typescript
// src/db/pools.ts
import pg from 'pg';

// Hot path pool: used by redirect handler and URL lookup
// Tuned for high-concurrency, short queries
export const redirectPool = new pg.Pool({
  connectionString: process.env.DATABASE_REPLICA_URL ?? process.env.DATABASE_URL,
  max: 8,
  min: 4,
  idleTimeoutMillis: 30_000,
  connectionTimeoutMillis: 2_000,   // fail fast — redirect SLO is tight
  statement_timeout: 5_000,
});

// Write pool: used for URL creation, analytics writes
export const writePool = new pg.Pool({
  connectionString: process.env.DATABASE_URL,  // must be primary (not replica)
  max: 5,
  min: 2,
  idleTimeoutMillis: 30_000,
  connectionTimeoutMillis: 5_000,
  statement_timeout: 15_000,
});

// Admin pool: used for bulk operations, migrations check endpoints
// Capped low so bulk operations can't starve the hot paths
export const adminPool = new pg.Pool({
  connectionString: process.env.DATABASE_URL,
  max: 2,
  min: 0,                            // don't keep idle connections for rare operations
  idleTimeoutMillis: 10_000,
  connectionTimeoutMillis: 30_000,   // admin can wait longer
  statement_timeout: 60_000,
});
```

### Async Semaphore for Concurrency Limiting

```typescript
// src/resilience/semaphore.ts
// Limits concurrent access to a resource.
// Callers that can't acquire immediately fail with 503 (not queue indefinitely).

export class Semaphore {
  private current = 0;
  private readonly max: number;

  constructor(maxConcurrency: number) {
    this.max = maxConcurrency;
  }

  tryAcquire(): boolean {
    if (this.current < this.max) {
      this.current++;
      return true;
    }
    return false;
  }

  release(): void {
    if (this.current > 0) this.current--;
  }

  // Wrap a function: acquire slot or throw if none available
  async run<T>(fn: () => Promise<T>): Promise<T> {
    if (!this.tryAcquire()) {
      throw new ConcurrencyLimitError(this.max);
    }
    try {
      return await fn();
    } finally {
      this.release();
    }
  }

  get available(): number {
    return this.max - this.current;
  }
}

export class ConcurrencyLimitError extends Error {
  constructor(limit: number) {
    super(`Concurrency limit of ${limit} exceeded`);
    this.name = 'ConcurrencyLimitError';
  }
}
```

### Bulkhead Middleware for Express

```typescript
// src/middleware/bulkhead.middleware.ts
// Apply a concurrency limit to a specific route or group of routes.

import type { Request, Response, NextFunction } from 'express';
import { Semaphore, ConcurrencyLimitError } from '../resilience/semaphore.js';

export function bulkhead(semaphore: Semaphore) {
  return (req: Request, res: Response, next: NextFunction): void => {
    if (!semaphore.tryAcquire()) {
      res.setHeader('Retry-After', '5');
      res.status(503).json({
        error: 'Too many concurrent requests, please retry',
        code: 'CONCURRENCY_LIMIT',
      });
      return;
    }

    // Release the slot when the response finishes (success or error)
    res.on('finish', () => semaphore.release());
    res.on('close', () => semaphore.release());

    next();
  };
}

// Usage:
// const bulkImportSemaphore = new Semaphore(2);
// adminRouter.post('/bulk-import', bulkhead(bulkImportSemaphore), bulkImportHandler);
```

### BullMQ Worker Bulkhead

```typescript
// src/workers/index.ts
// Separate workers for each notification channel.
// Email worker being slow/blocked does NOT affect webhook delivery.

import { Worker } from 'bullmq';
import { redis } from '../cache/redis.js';

// Email worker — limited concurrency (SMTP can't handle too many parallel sends)
const emailWorker = new Worker('emails', processEmail, {
  connection: redis,
  concurrency: 10,   // max 10 parallel email sends
  limiter: {
    max: 50,         // max 50 emails per 10 seconds (rate limit)
    duration: 10_000,
  },
});

// Webhook worker — can run more concurrent calls (HTTP is faster than SMTP)
const webhookWorker = new Worker('webhooks', processWebhook, {
  connection: redis,
  concurrency: 50,   // max 50 parallel webhook calls
});

// Analytics worker — lowest priority, can be slow
const analyticsWorker = new Worker('analytics', processAnalytics, {
  connection: redis,
  concurrency: 5,
});

// If emailWorker stalls (SMTP is down), webhooks and analytics keep processing.
// Bulkhead: each queue is entirely independent.
```

---

## Try It Yourself

**Exercise:** Observe bulkhead protecting the redirect path from an admin operation.

```bash
# Setup: ScaleForge with two separate pools (redirectPool + adminPool)

# Terminal 1: Continuous redirect load
autocannon -c 50 -d 60 http://localhost:3001/abc123 &

# Terminal 2: Watch redirect p99 latency in real time
watch -n 2 'curl -s http://localhost:3001/metrics | grep "http_request_duration"'

# Terminal 3: Start a bulk admin operation that hammers the DB
curl -X POST http://localhost:3001/api/v1/admin/bulk-import \
  -H "Content-Type: application/json" \
  -d '{"count": 10000}'

# Expected WITH bulkhead:
#   redirect p99 stays flat during the bulk import

# Expected WITHOUT bulkhead (shared pool):
#   redirect p99 spikes to 500ms+ during the bulk import
```

```typescript
// bulkhead.exercise.ts
// TODO:
// 1. Currently all routes share primaryPool. Split into redirectPool + adminPool.
// 2. Observe redirect latency with and without the split under concurrent admin load.
// 3. Add a Prometheus gauge for each pool's waiting count:
//    pg_redirect_pool_waiting_requests
//    pg_admin_pool_waiting_requests
// 4. The admin pool should NEVER show nonzero in the redirect pool gauge.
```

<details>
<summary>Show how to wire pools to routers</summary>

```typescript
// src/routes/redirect.router.ts — uses redirectPool
import { redirectPool } from '../db/pools.js';
// ...
const result = await redirectPool.query(
  'SELECT original_url FROM urls WHERE short_code = $1 AND is_active = true',
  [code],
);

// src/routes/admin.router.ts — uses adminPool (capped at 2 connections)
import { adminPool } from '../db/pools.js';
// ...
const client = await adminPool.connect();
// Bulk operation ...
client.release();
```

</details>

---

## Capstone Connection

ScaleForge has two distinct traffic classes: the high-priority, latency-sensitive redirect path (which must stay below 50ms p99) and the lower-priority administrative operations (bulk import, analytics export). The bulkhead pattern ensures they never compete for the same resources. The redirect path gets a dedicated pool with a tight `connectionTimeoutMillis` (fail fast), while admin operations get a smaller pool with a longer timeout. When a user's bulk import takes 5 minutes, no other user notices.
