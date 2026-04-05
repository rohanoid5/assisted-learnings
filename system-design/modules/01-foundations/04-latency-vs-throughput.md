# 1.4 — Latency vs. Throughput

## Concept

Latency is the time it takes to process **one** request; throughput is how many requests the system can process **per second**. They often trade off against each other — batching improves throughput but increases latency; prioritising speed for individual requests can limit throughput. Understanding both metrics and how to measure them prevents optimising the wrong thing.

---

## Deep Dive

### Definitions

```
Latency  = time from request sent → response received (per request)
           Measured in: ms, µs
           Reported as: p50, p95, p99, p99.9 (percentiles)

Throughput = number of requests completed per unit of time
             Measured in: req/s, messages/s, MB/s
             Reported as: average over a time window
```

### Why Percentiles Matter More Than Averages

Average latency hides tail latency — the slow requests that real users experience:

```
10 requests with latencies: [1, 1, 1, 1, 1, 1, 1, 1, 1, 1000] ms

Mean:    100.9ms  ← disguises the outlier completely
Median:    1ms    ← p50
p95:       1ms    ← 95% of users are fine
p99:    1000ms    ← 1 in 100 users waits 1 second

If you SLA on p99, you catch the tail.
If you SLA on median, you ignore it.
```

**Rule of thumb**: Use p50 for "typical", p95 for "most" users, p99 for SLA commitments, p99.9 for "near-zero tolerance" systems (payment processing, authentication).

### The Latency–Throughput Tradeoff

```
  High throughput ◀──────────────────────────▶ Low latency
  
  Strategy A: Batching
    Collect 100 requests → process together → send 100 responses
    Throughput: HIGH (process 100 at once)
    Latency:    HIGH (last request waits for batch to fill)
    Use case: Analytics ingestion, DB bulk writes
  
  Strategy B: Streaming
    Process each request immediately as it arrives
    Throughput: LOWER (processing overhead per request)
    Latency:    LOW (no waiting)
    Use case: Real-time redirects, API responses
  
  The balance point: accept some batching at the ASYNC layer
  (click tracking queue) while keeping the critical path (redirect) streaming.
```

### Little's Law — Connecting Latency and Throughput

For any stable system: $L = \lambda \cdot W$

- $L$ = average number of requests in the system (concurrency)
- $\lambda$ = throughput (requests per second)  
- $W$ = average latency (seconds per request)

**ScaleForge example:**
- Throughput: 11,574 req/s (peak)
- Target latency: 0.020s (20ms p99)
- Required concurrency: $L = 11,574 × 0.020 = 231$ simultaneous in-flight requests

This tells you: your event loop, connection pool, and Redis need to handle **231 concurrent requests** at peak. If PostgreSQL connection pool is capped at 20, you need Redis to absorb 98%+ of traffic.

---

## Code Examples

### Measuring Latency Percentiles in TypeScript

```typescript
// src/telemetry/latency.ts — latency measurement utilities

export class LatencyHistogram {
  private samples: number[] = [];

  record(ms: number): void {
    this.samples.push(ms);
  }

  percentile(p: number): number {
    if (this.samples.length === 0) return 0;
    const sorted = [...this.samples].sort((a, b) => a - b);
    const index = Math.ceil((p / 100) * sorted.length) - 1;
    return sorted[Math.max(0, index)];
  }

  report(): Record<string, number> {
    return {
      p50: this.percentile(50),
      p95: this.percentile(95),
      p99: this.percentile(99),
      p999: this.percentile(99.9),
      mean: this.samples.reduce((a, b) => a + b, 0) / this.samples.length,
      count: this.samples.length,
    };
  }

  reset(): void {
    this.samples = [];
  }
}

// Usage in Express middleware:
import { Router } from 'express';

const redirectHistogram = new LatencyHistogram();

export function latencyMiddleware(router: Router) {
  router.use((req, res, next) => {
    const start = performance.now();
    res.on('finish', () => {
      redirectHistogram.record(performance.now() - start);
    });
    next();
  });
}

// Every 60 seconds, log the latency report
setInterval(() => {
  const report = redirectHistogram.report();
  console.log('Redirect latency (ms):', report);
  redirectHistogram.reset();
}, 60_000);
```

### Throughput Test — Measuring Max Sustainable QPS

```typescript
// experiments/throughput-test.ts
// Find the maximum sustainable throughput before latency degrades

import { performance } from 'node:perf_hooks';

async function measureThroughputAtConcurrency(
  concurrency: number,
  totalRequests: number,
  handler: () => Promise<void>
): Promise<{ throughput: number; p99Ms: number }> {
  const latencies: number[] = [];
  const queue = Array.from({ length: totalRequests }, () => handler);

  let index = 0;
  const start = performance.now();

  await Promise.all(
    Array.from({ length: concurrency }, async () => {
      while (index < queue.length) {
        const fn = queue[index++];
        if (!fn) break;
        const reqStart = performance.now();
        await fn();
        latencies.push(performance.now() - reqStart);
      }
    })
  );

  const elapsed = (performance.now() - start) / 1000; // seconds
  const throughput = totalRequests / elapsed;
  const sorted = latencies.sort((a, b) => a - b);
  const p99 = sorted[Math.ceil(0.99 * sorted.length) - 1] ?? 0;

  return { throughput, p99Ms: p99 };
}

// Simulate URL lookup with Redis cache (0.3ms) + 10% miss rate to Postgres (3ms)
const simulateLookup = async () => {
  const isCacheHit = Math.random() > 0.1;
  await new Promise(r => setTimeout(r, isCacheHit ? 0.3 : 3));
};

console.log('Concurrency | Throughput (req/s) | p99 latency (ms)');
console.log('─────────────────────────────────────────────────────');

for (const concurrency of [1, 10, 50, 100, 500]) {
  const result = await measureThroughputAtConcurrency(concurrency, 1000, simulateLookup);
  console.log(
    `${String(concurrency).padEnd(11)} | ${result.throughput.toFixed(0).padEnd(18)} | ${result.p99Ms.toFixed(2)}`
  );
}

// Expected pattern:
// Low concurrency:  low throughput, low latency (not saturated)
// Optimal point:    high throughput, latency still within SLA
// Over-saturation:  throughput plateaus, latency spikes (queueing theory)
```

---

## Try It Yourself

**Exercise:** Implement a sliding-window rate limiter that enforces throughput limits per user.

```typescript
// src/resilience/rate-limiter.ts

export class SlidingWindowRateLimiter {
  // Tracks request timestamps per key in a sliding window
  private windows = new Map<string, number[]>();

  constructor(
    private readonly maxRequests: number,
    private readonly windowMs: number
  ) {}

  /**
   * Returns true if the request is allowed, false if rate-limited.
   * TODO: implement sliding window logic
   */
  isAllowed(key: string): boolean {
    const now = Date.now();
    // TODO:
    // 1. Get or create the window array for this key
    // 2. Remove timestamps older than (now - windowMs)
    // 3. Check if remaining count < maxRequests
    // 4. If allowed, add current timestamp and return true
    // 5. If not allowed, return false
    throw new Error('Not implemented');
  }
}

// Test it:
const limiter = new SlidingWindowRateLimiter(5, 1000); // 5 req/sec
const key = 'user:123';
for (let i = 0; i < 8; i++) {
  console.log(`Request ${i + 1}: ${limiter.isAllowed(key) ? 'ALLOWED' : 'BLOCKED'}`);
}
// Expected: ALLOWED ×5, then BLOCKED ×3
```

<details>
<summary>Show solution</summary>

```typescript
isAllowed(key: string): boolean {
  const now = Date.now();
  const cutoff = now - this.windowMs;

  // Get or initialize the timestamp window
  let timestamps = this.windows.get(key) ?? [];

  // Evict old timestamps outside the window
  timestamps = timestamps.filter(t => t > cutoff);

  if (timestamps.length >= this.maxRequests) {
    this.windows.set(key, timestamps);
    return false;
  }

  timestamps.push(now);
  this.windows.set(key, timestamps);
  return true;
}
```

**Note**: This in-memory implementation works in a single process but not across multiple ScaleForge replicas (Module 03). In Module 10, we replace it with a Redis-backed sliding window that all replicas share.

</details>

---

## Capstone Connection

The ScaleForge target of "redirect p99 < 20ms" will be validated with the `LatencyHistogram` class from this topic, wired into Prometheus in Module 08. The throughput target of 10k req/s drives the horizontal scaling design in Module 03 — but only after this module makes clear that a single Node.js process, even clustered, cannot achieve 10k req/s if every request hits PostgreSQL (each query = 3-5ms, max parallelism = connection pool size ~100 = ~25k req/s theoretical, but with GC and connection overhead closer to 5-8k in practice at p99 < 20ms).
