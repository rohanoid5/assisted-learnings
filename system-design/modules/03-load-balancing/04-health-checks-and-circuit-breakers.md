# 3.4 — Health Checks and Circuit Breakers

## Concept

Health checks prevent traffic from reaching broken servers. Circuit breakers prevent cascading failures when a downstream service is degraded. Together they make distributed systems self-healing — bad instances are automatically removed from rotation, and failing dependencies are short-circuited before they exhaust your connection pool.

---

## Deep Dive

### Liveness vs Readiness

```
Two distinct questions load balancers/orchestrators ask:

  "Is this process alive?"          → Liveness probe → /health
  "Can this process handle traffic?" → Readiness probe → /ready

  LIVENESS FAILURE:                 READINESS FAILURE:
  Something is catastrophically     Temporary: DB restarting, warm-up
  wrong with the process itself.    in progress, graceful shutdown.

  Action: RESTART the pod/process   Action: REMOVE from LB pool.
                                     Do NOT restart — that would
                                     break the running instance.

  If Kubernetes restarts your pod when the DB is down,
  you now have a crash-loop instead of graceful degradation.
```

### Health Check Anatomy

```
                 Load Balancer Poll every 5s
                        │
            ┌───────────┼────────────┐
            ▼           ▼            ▼
         /health      /ready      /metrics
         ──────       ──────      ────────
         200 OK        200 OK      counter data
         always        iff:        for Prometheus
         (if alive)    - DB ping OK   scraping
                       - Redis OK
                       - Queue OK
                       
  Nginx passive health check (no polling):
    max_fails=3 fail_timeout=30s
    → 3 consecutive TCP errors or 5xx responses in 30s
    → server marked down for 30s
    → Nginx retries connections after 30s
```

### Circuit Breaker State Machine

```
           ┌──────────────────────────────────────┐
           │                                      │
           ▼                                      │
    ┌─────────────┐  failure rate    ┌────────────────┐
    │    CLOSED   │  exceeds         │     OPEN       │
    │  (normal)   │────threshold────►│  (fail fast)   │
    │  pass-thru  │                  │  skip calls    │
    └─────────────┘                  └────────────────┘
           ▲                                 │
           │ success                         │ after timeout
           │                                 ▼
    ┌─────────────────────────────────────────────────┐
    │                    HALF-OPEN                     │
    │       (probe: allow 1 test request through)      │
    └─────────────────────────────────────────────────┘

  CLOSED → OPEN:    After N failures in a time window
  OPEN → HALF-OPEN: After cooldown period (e.g., 30s)
  HALF-OPEN → CLOSED: Probe request succeeds
  HALF-OPEN → OPEN:   Probe request fails → reset cooldown

  Why this prevents cascading failures:
    Without CB: app waits 30s for each timed-out DB call
               100 concurrent requests × 30s = thread starvation
    With CB:    after 5 failures, all calls fail in <1ms
               DB gets time to recover without more load
```

### Passive vs Active Health Checks

| | Passive | Active |
|--|--|--|
| How | Observe real traffic failures | Send dedicated probe requests |
| Speed | Slow — needs failed user requests to detect | Fast — catches failures before users hit them |
| Nginx config | `max_fails` / `fail_timeout` | `health_check` directive (Nginx Plus) |
| Use case | Good enough for most cases | When zero false-negatives matter |

---

## Code Examples

### Health and Readiness Endpoints

```typescript
// src/routes/health.router.ts
import { Router, type Request, type Response } from 'express';
import { pool } from '../db/pool.js';
import { redisClient } from '../cache/redis.client.js';

export const healthRouter = Router();

// Simple liveness — just prove the event loop is responding
healthRouter.get('/health', (_req: Request, res: Response) => {
  res.json({ status: 'ok', uptime: Math.floor(process.uptime()) });
});

// Deep readiness — check every dependency
healthRouter.get('/ready', async (_req: Request, res: Response): Promise<void> => {
  const checks: Record<string, boolean | string> = {};
  const deadline = 2000; // 2 second max for all checks

  const withTimeout = <T>(promise: Promise<T>, name: string): Promise<T> =>
    Promise.race([
      promise,
      new Promise<T>((_, reject) =>
        setTimeout(() => reject(new Error(`${name} timeout`)), deadline)
      ),
    ]);

  await Promise.allSettled([
    withTimeout(pool.query('SELECT 1'), 'postgres').then(
      () => { checks.postgres = true; },
      (e) => { checks.postgres = e.message; }
    ),
    withTimeout(redisClient.ping(), 'redis').then(
      () => { checks.redis = true; },
      (e) => { checks.redis = e.message; }
    ),
  ]);

  const allReady = Object.values(checks).every((v) => v === true);
  res.status(allReady ? 200 : 503).json({
    status: allReady ? 'ready' : 'not_ready',
    checks,
  });
});
```

### Graceful Shutdown

```typescript
// src/server.ts — mark not-ready before stopping, drain connections, then exit

let isReady = true; // module-level flag

export function setNotReady(): void {
  isReady = false;
}

// Expose readiness state to the router
healthRouter.get('/ready', async (_req, res) => {
  if (!isReady) {
    // Immediately signal "not ready" — Nginx stops sending new connections
    res.status(503).json({ status: 'not_ready', checks: { shutting_down: true } });
    return;
  }
  // ... dependency checks from above
});

// Graceful shutdown handler
async function gracefulShutdown(signal: string): Promise<void> {
  console.log(`Received ${signal}, starting graceful shutdown`);

  // Step 1: Mark not ready → Nginx stops routing here within one health check interval
  setNotReady();

  // Step 2: Wait for in-flight requests to complete (connection drain)
  // Give Nginx time to notice our /ready returned 503
  await new Promise(resolve => setTimeout(resolve, 5000));

  // Step 3: Close server (stop accepting new connections)
  server.close(async () => {
    console.log('HTTP server closed');

    // Step 4: Close DB/Redis connections
    await pool.end();
    redisClient.disconnect();

    console.log('Shutdown complete');
    process.exit(0);
  });

  // Hard stop if graceful fails within 30s
  setTimeout(() => {
    console.error('Graceful shutdown timeout — forcing exit');
    process.exit(1);
  }, 30_000).unref();
}

process.on('SIGTERM', () => gracefulShutdown('SIGTERM'));
process.on('SIGINT',  () => gracefulShutdown('SIGINT'));
```

### Circuit Breaker

```typescript
// src/circuit-breaker.ts — a minimal but correct implementation

type BreakerState = 'CLOSED' | 'OPEN' | 'HALF_OPEN';

export class CircuitBreaker<T> {
  private state: BreakerState = 'CLOSED';
  private failureCount: number = 0;
  private lastFailureTime: number = 0;

  constructor(
    private readonly fn: () => Promise<T>,
    private readonly options: {
      failureThreshold: number;   // Open after N consecutive failures
      cooldownMs: number;          // Try again after this much time in OPEN state
    }
  ) {}

  async call(): Promise<T> {
    if (this.state === 'OPEN') {
      const elapsed = Date.now() - this.lastFailureTime;
      if (elapsed < this.options.cooldownMs) {
        // Fail immediately — don't even try
        throw new Error(
          `Circuit breaker OPEN. Retry in ${Math.ceil((this.options.cooldownMs - elapsed) / 1000)}s`
        );
      }
      // Cooldown elapsed — allow one probe request
      this.state = 'HALF_OPEN';
    }

    try {
      const result = await this.fn();
      this.onSuccess();
      return result;
    } catch (err) {
      this.onFailure();
      throw err;
    }
  }

  private onSuccess(): void {
    this.failureCount = 0;
    this.state = 'CLOSED'; // Probe succeeded — fully close the circuit
  }

  private onFailure(): void {
    this.failureCount++;
    this.lastFailureTime = Date.now();

    if (
      this.state === 'HALF_OPEN' ||
      this.failureCount >= this.options.failureThreshold
    ) {
      this.state = 'OPEN';
    }
  }

  getState(): BreakerState {
    return this.state;
  }
}

// Usage in click-tracking worker:
const dbBreaker = new CircuitBreaker(
  () => pool.query('UPDATE urls SET click_count = click_count + 1 WHERE code = $1', ['abc']),
  { failureThreshold: 5, cooldownMs: 30_000 }
);

try {
  await dbBreaker.call();
} catch (err) {
  // Either DB error OR circuit open — log and drop the increment
  // The short URL redirect already succeeded; analytics drop is acceptable
}
```

---

## Try It Yourself

**Exercise:** Make the readiness probe detect a degraded connection pool.

```typescript
// A pool under heavy load may exhaust its connections.
// Add a "pool_pressure" check to the /ready endpoint.

// TODO:
// 1. Import pg Pool type and access pool.totalCount and pool.waitingCount
// 2. Add a check: if waitingCount > 5, mark ready as false with reason "pool_pressure"
// 3. Test by temporarily setting pool max to 1 and firing 10 concurrent requests
//    Expected: /ready returns 503 with checks.pool_pressure = "waiting:X"
//
// Hint: pool.totalCount = connections created
//       pool.idleCount = connections not in use
//       pool.waitingCount = queries waiting for a connection
```

<details>
<summary>Show solution</summary>

```typescript
healthRouter.get('/ready', async (_req: Request, res: Response): Promise<void> => {
  const checks: Record<string, boolean | string> = {};

  // Sync check — no async needed
  const waiting = pool.waitingCount;
  const total   = pool.totalCount;
  if (waiting > 5) {
    checks.pool_pressure = `waiting:${waiting}/total:${total}`;
  } else {
    checks.pool_pressure = true;
  }

  // Async dependency checks
  await Promise.allSettled([
    pool.query('SELECT 1').then(
      () => { checks.postgres = true; },
      (e: Error) => { checks.postgres = e.message; }
    ),
    redisClient.ping().then(
      () => { checks.redis = true; },
      (e: Error) => { checks.redis = e.message; }
    ),
  ]);

  const allReady = Object.values(checks).every((v) => v === true);
  res.status(allReady ? 200 : 503).json({
    status: allReady ? 'ready' : 'not_ready',
    checks,
  });
});
```

</details>

---

## Capstone Connection

The graceful shutdown pattern here is critical for ScaleForge in production: when you deploy a new version, Kubernetes sends `SIGTERM` to old pods. Without graceful shutdown, in-flight requests receive TCP RST. With it, in-flight requests complete, the LB drains the pod, then the process exits cleanly.

The circuit breaker wraps the `click_count` update in the async worker — this is OK because click analytics are "best effort." If the DB is down, we drop click events (acceptable per the requirements) rather than blocking the redirect. In Module 10 (Reliability Patterns), you'll chain the circuit breaker with a dead-letter queue so dropped events can be replayed when the DB recovers.
