# 10.6 — Event Loop Monitoring

## Concept

Event loop lag is the delay between when a callback is scheduled and when it actually runs. Under load, high lag (> 100ms) means the event loop is overloaded — requests are queuing, timeouts fire late, and users experience slow responses. Measuring lag is essential for SLA monitoring.

---

## Deep Dive

### `perf_hooks.monitorEventLoopDelay`

```typescript
import { monitorEventLoopDelay } from 'node:perf_hooks';

// Creates a histogram that samples event loop delay every resolution ms
const histogram = monitorEventLoopDelay({ resolution: 20 }); // sample every 20ms
histogram.enable();

// Read metrics periodically
setInterval(() => {
  const lagMs = histogram.mean / 1e6; // nanoseconds → milliseconds
  const p99Ms = histogram.percentile(99) / 1e6;

  console.log(`Event loop lag — mean: ${lagMs.toFixed(2)}ms, p99: ${p99Ms.toFixed(2)}ms`);

  histogram.reset(); // reset histogram for next interval
}, 5000);
```

### libuv Threadpool

Node.js uses a threadpool (default 4 threads) for:
- DNS lookups (`dns.lookup`)
- Crypto (hashing, key generation)
- File I/O (on some OS configurations)
- Any `uv_` call that blocks

```bash
# Increase thread pool size if CPU-bound I/O is queuing
UV_THREADPOOL_SIZE=16 node src/api/server.js
```

```typescript
// Detect threadpool saturation — high lag on specific operations only
// vs. event loop saturation — high lag everywhere
async function measureDnsLag() {
  const start = performance.now();
  await dns.promises.lookup('localhost'); // exercises threadpool
  return performance.now() - start;
}
```

### Metrics Endpoint

```typescript
// src/api/routes/metrics.route.ts
import { monitorEventLoopDelay, type IntervalHistogram } from 'node:perf_hooks';

let histogram: IntervalHistogram;

export function startEventLoopMonitoring() {
  histogram = monitorEventLoopDelay({ resolution: 20 });
  histogram.enable();
}

router.get('/', (req, res) => {
  const { heapUsed, heapTotal, rss } = process.memoryUsage();

  res.json({
    eventLoop: {
      meanMs: histogram ? (histogram.mean / 1e6).toFixed(2) : null,
      p99Ms: histogram ? (histogram.percentile(99) / 1e6).toFixed(2) : null,
    },
    memory: {
      heapUsedMb: (heapUsed / 1024 / 1024).toFixed(1),
      heapTotalMb: (heapTotal / 1024 / 1024).toFixed(1),
      rssMb: (rss / 1024 / 1024).toFixed(1),
    },
    workerPool: {
      queueDepth: pool.queueDepth,
      idleWorkers: pool.idleCount,
    },
    uptime: process.uptime(),
    pid: process.pid,
  });
});
```

### Alerting on High Lag

```typescript
// Emit an alert if event loop lag exceeds threshold
const LAG_WARN_MS = 100;
const LAG_CRIT_MS = 500;

setInterval(() => {
  const p99 = histogram.percentile(99) / 1e6;
  histogram.reset();

  if (p99 > LAG_CRIT_MS) {
    logger.error('Event loop critically overloaded', { p99Ms: p99 });
    // In production: send alert, consider restarting
  } else if (p99 > LAG_WARN_MS) {
    logger.warn('Event loop lag elevated', { p99Ms: p99 });
  }
}, 10_000);
```

---

## Try It Yourself

**Exercise:** Simulate event loop blocking and observe the lag meter spike:

```typescript
// Add this temporary route to your Express app
app.get('/block', (_req, res) => {
  // Block the event loop for 300ms (CPU work)
  const start = Date.now();
  while (Date.now() - start < 300) {} // NEVER do this in production!
  res.json({ blocked: '300ms' });
});
```

1. Start the metrics collection
2. Hit `/block` with curl
3. Immediately check `/api/v1/metrics` — observe the elevated `meanMs` and `p99Ms`
4. Remove the blocking route

<details>
<summary>What you should observe</summary>

The mean and p99 event loop delay will spike to 300+ms immediately after the `/block` call. Concurrent requests to other endpoints will have been delayed by the same amount. This demonstrates why blocking the event loop is catastrophic for throughput.

</details>

---

## Capstone Connection

PipeForge's `GET /api/v1/metrics` endpoint (accessible only to `ADMIN` role) exposes event loop lag alongside memory and worker pool stats. In a production setup, Prometheus scrapes this endpoint and Grafana dashboards alert when p99 lag exceeds 100ms.
