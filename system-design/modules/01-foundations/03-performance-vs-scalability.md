# 1.3 — Performance vs. Scalability

## Concept

Performance answers "how fast is this *one* request?"; scalability answers "how does the system behave as load increases?". They are related but distinct — a system can be fast at low load and completely fall apart at 10× load, or be slow per request but handle millions of concurrent users gracefully. Knowing which problem you have determines whether you need to optimize code, add hardware, or redesign the architecture.

---

## Deep Dive

### Performance: The Single-Request View

Performance is measured from the user's perspective: latency, and whether the system returns a correct result. It depends on:

- **Algorithm efficiency** — O(n²) vs O(log n)
- **I/O efficiency** — number of DB queries, query plans
- **Memory allocation** — GC pressure in Node.js
- **CPU utilization** — blocking the event loop

```
  Client request ─────────────────────────────────────────▶ Response
  
  Timeline:
  │─ Network (5ms) ─│─ App (2ms) ─│─ DB query (8ms) ─│─ Network (5ms) ─│
                                                         
  Total: 20ms latency
  
  To improve performance:
  - Reduce DB query time (index, cache)  ← biggest win
  - Reduce app processing time           ← usually small
  - Reduce network hops (CDN, locality)  ← sometimes large
```

### Scalability: The Increasing-Load View

A system is **scalable** if adding resources (CPU, memory, servers) results in proportional throughput increase. There are two strategies:

```
Vertical Scaling (Scale Up):
  ┌──────────┐            ┌──────────────────┐
  │ Server   │  upgrade   │ Bigger Server    │
  │ 4 CPU    │  ────────▶ │ 32 CPU           │
  │ 16 GB    │            │ 256 GB           │
  └──────────┘            └──────────────────┘
  Pro: Simple, no code changes
  Con: Hardware ceiling; single point of failure; expensive

Horizontal Scaling (Scale Out):
  ┌──────────┐            ┌──────────┐  ┌──────────┐  ┌──────────┐
  │ Server   │  replicate │ Server 1 │  │ Server 2 │  │ Server N │
  │ 4 CPU    │  ────────▶ │ 4 CPU    │  │ 4 CPU    │  │ 4 CPU    │
  │ 16 GB    │            └──────────┘  └──────────┘  └──────────┘
  └──────────┘                              ▲
  Pro: Linear scaling; fault tolerant  Load Balancer
  Con: Requires stateless services; adds distribution complexity
```

### The Key Requirement for Horizontal Scaling: Statelessness

Horizontal scaling only works if requests can be routed to **any** replica. This means servers must NOT store state locally:

| State type | Problem | Solution |
|------------|---------|---------|
| User sessions | Session stored in-memory on one server | Move to Redis (shared) |
| Uploaded files | File on local disk of one server | Move to object storage (S3 / MinIO) |
| Scheduled jobs | Cron runs on one specific server | Use distributed job scheduler (BullMQ) |
| In-memory caches | Each server has stale copy | Use Redis as shared cache |

### Measuring Scalability

Use **Amdahl's Law** to understand the theoretical ceiling:

$$S(n) = \frac{1}{(1 - p) + \frac{p}{n}}$$

where $p$ is the fraction of parallelizable work and $n$ is number of processors/nodes.

If 95% of your work is parallelizable (p = 0.95):
- 2 nodes → 1.9× speedup  
- 10 nodes → 6.7× speedup  
- 100 nodes → 17× speedup (not 100×!)

The **serial fraction** (5% in this example) always creates a ceiling. In practice, coordination overhead, DB contention, and shared state are the serial fractions.

---

## Code Examples

### Demonstrating the Performance vs. Scalability Distinction

```typescript
// experiments/performance-vs-scalability.ts
//
// Two implementations of the same URL lookup.
// Neither is "wrong" — they optimize for different things.

import { performance } from 'node:perf_hooks';

// ── Simulated dependencies ─────────────────────────────────────

const fakeRedis = {
  async get(key: string): Promise<string | null> {
    // Redis in production: ~0.2ms
    await new Promise(r => setTimeout(r, 0.2));
    return key === 'abc123' ? 'https://example.com/very-long-url' : null;
  }
};

const fakePostgres = {
  async query(code: string): Promise<string | null> {
    // PostgreSQL indexed lookup: ~3ms
    await new Promise(r => setTimeout(r, 3));
    return code === 'abc123' ? 'https://example.com/very-long-url' : null;
  }
};

// ── Implementation A: Optimized for single-request performance ──

async function lookupOptimizedPerformance(code: string): Promise<string | null> {
  // Check Redis first (< 1ms), PostgreSQL only on miss
  const cached = await fakeRedis.get(code);
  if (cached) return cached;
  return fakePostgres.query(code);
}

// ── Implementation B: Naive (no cache) ──────────────────────────

async function lookupNaive(code: string): Promise<string | null> {
  // Always hits PostgreSQL (~3ms per request)
  return fakePostgres.query(code);
}

// ── Benchmark: single-request latency ───────────────────────────

async function benchmarkSingle() {
  const start = performance.now();
  await lookupOptimizedPerformance('abc123');
  const optimizedMs = performance.now() - start;

  const start2 = performance.now();
  await lookupNaive('abc123');
  const naiveMs = performance.now() - start2;

  console.log(`Optimized (Redis): ${optimizedMs.toFixed(2)}ms`);
  console.log(`Naive (Postgres):  ${naiveMs.toFixed(2)}ms`);
  // Optimized (Redis): 0.45ms
  // Naive (Postgres):  3.12ms
}

// ── Benchmark: concurrent load (scalability view) ───────────────

async function benchmarkConcurrent(concurrency: number) {
  const requests = Array.from({ length: concurrency }, (_, i) =>
    i % 2 === 0 ? 'abc123' : 'missing'
  );

  const start = performance.now();
  await Promise.all(requests.map(code => lookupOptimizedPerformance(code)));
  const optimizedMs = performance.now() - start;

  const start2 = performance.now();
  await Promise.all(requests.map(code => lookupNaive(code)));
  const naiveMs = performance.now() - start2;

  console.log(`\n=== ${concurrency} concurrent requests ===`);
  console.log(`Optimized: ${optimizedMs.toFixed(0)}ms total`);
  console.log(`Naive:     ${naiveMs.toFixed(0)}ms total`);
}

await benchmarkSingle();
await benchmarkConcurrent(10);
await benchmarkConcurrent(100);
await benchmarkConcurrent(1000);

// At 1000 concurrent:
// Optimized: ~1ms  (Redis handles concurrent, each still ~0.2ms)
// Naive:     ~3ms  (Postgres handles concurrent too, but if it saturates...)
//
// In production, Postgres has connection pool limits (100 default).
// At 1000 concurrent: naive implementation starts queueing connections.
// Optimized version: Redis serves 95%+ of traffic before touching Postgres.
```

### Identifying the Bottleneck: Node.js Cluster vs. Single Process

```typescript
// Compare: single-process vs. clustered Node.js
// Shows when vertical optimization stops helping

import cluster from 'node:cluster';
import { cpus } from 'node:os';

if (cluster.isPrimary) {
  const numCPUs = cpus().length;
  console.log(`Master PID ${process.pid} — forking ${numCPUs} workers`);

  for (let i = 0; i < numCPUs; i++) {
    cluster.fork();
  }

  cluster.on('exit', (worker) => {
    console.log(`Worker ${worker.process.pid} died — restarting`);
    cluster.fork();
  });
} else {
  // Each worker is an independent Express server
  // Load balancer (OS-level round-robin) distributes connections
  const express = (await import('express')).default;
  const app = express();

  // CPU-bound work (hashing) benefits from clustering
  // I/O-bound work (DB queries) benefits less — already async
  app.get('/hash', (_req, res) => {
    const { createHash } = await import('node:crypto');
    const hash = createHash('sha256').update('scaleforge').digest('hex');
    res.json({ hash, pid: process.pid });
  });

  app.listen(3001, () => {
    console.log(`Worker ${process.pid} ready`);
  });
}
```

---

## Try It Yourself

**Exercise:** Find the bottleneck in ScaleForge's redirect path.

```typescript
// experiments/find-bottleneck.ts
// Profile where time is spent in a redirect

import { performance } from 'node:perf_hooks';

async function simulateRedirect(code: string) {
  const timings: Record<string, number> = {};

  // TODO: add performance.now() measurements around each step:
  // 1. Check JWT auth  (if present)
  // 2. Redis cache lookup
  // 3. PostgreSQL lookup (on miss)
  // 4. Write click event to queue
  // 5. Send HTTP redirect response

  // Then calculate: what % of total time is each step?
  // Which step should you optimize first?
}

await simulateRedirect('abc123');
```

<details>
<summary>Show expected findings and reasoning</summary>

```
Typical measurements:
  JWT verify:          ~0.1ms   (crypto is fast)
  Redis GET:           ~0.3ms   (< 1ms always)
  PostgreSQL (on hit): 0ms      (Redis handled it)
  PostgreSQL (on miss): ~4ms    (index lookup)
  Queue push (async):  ~0.2ms   (fire and forget)
  Total (cache hit):   ~0.6ms   ← target achieved
  Total (cache miss):  ~4.5ms   ← still under 20ms

Optimization priority:
  1. Cache hit rate  — if most requests are cache misses, fix TTL or cache strategy
  2. PostgreSQL       — if cache miss latency is too high, add read replica
  3. Auth             — almost never the bottleneck for redirect

The bottleneck is almost never the Node.js application code.
It's almost always the I/O (DB, cache, network).
```

</details>

---

## Capstone Connection

ScaleForge's critical performance requirement is "redirect p99 < 20ms". The decision to use Redis cache-aside (Module 05) is driven entirely by this NFR — without it, a PostgreSQL-only architecture maxes out at ~300-500 req/s before query latency degrades. The decision to process clicks asynchronously via BullMQ (Module 06) is driven by scalability — synchronous writes at 100M/day would triple the redirect latency and create a DB write bottleneck.
