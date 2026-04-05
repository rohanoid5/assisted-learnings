# 8.1 — Node.js Performance Profiling

## Concept

Node.js performance problems fall into two categories: event loop blocking (synchronous CPU work that prevents I/O callbacks from running) and memory bloat (objects that are never garbage collected). Both manifest as latency spikes or throughput collapse — but they require completely different fixes. You find them by profiling, not by reading code.

---

## Deep Dive

### The Event Loop and Latency

```
  Node.js processes one thing at a time on the event loop.
  A 100ms synchronous operation blocks ALL requests for 100ms:

  Timeline (without blocking):

  t=0ms   t=5ms   t=10ms  t=15ms  t=20ms
    │req1    │req2    │req3    │req4    │req5
    │►Redis  │►Redis  │►Redis  │►Redis  │►Redis
    │◄302    │◄302    │◄302    │◄302    │◄302
  
  All 5 requests complete in ~5ms each. ✓

  ─────────────────────────────────────────────────────────

  Timeline (with 50ms CPU-blocking operation, e.g. JSON.parse on 5MB body):

  t=0ms                           t=50ms  t=55ms  t=60ms
    │req1  ██████████████████████        │req2    │req3
    │JSON.parse(5MB)                     │►Redis  │►Redis
    │►Redis ◄302                         │◄302    │◄302
  
  req2 and req3 waited 50ms even though Redis answered in <1ms.
  p99 latency = 50ms × (max concurrent requests that arrived during block).
```

### What Tools Show What

```
  Symptom                    Tool                   What you see
  ─────────────────────────  ─────────────────────  ─────────────────────────────
  High p99, occasional       clinic doctor           "Event loop delay detected"
  latency spikes             (event loop monitor)    Points to blocking calls
  
  CPU usage is high          0x + clinic flame       Flame graph: widest bars =
  but throughput is low      (CPU profiling)         hottest functions → optimize those
  
  Memory grows over time     clinic heapprof         Heap snapshot: find objects
  (never GCed)               (heap profiler)         not getting released
  
  Throughput ceiling at      autocannon + htop       Thread saturation vs I/O wait —
  N req/s regardless of      (load testing)          guides cluster vs worker_threads
  concurrency increase
```

### Reading a Flame Graph

```
  Each bar = a function call. Width = time spent (inclusive of callees).
  Colour = file source (node_modules = red, your code = blue).

  ┌────────────────────────────────────────────────────────────────┐
  │  ████ JSON.stringify (35% of CPU time)                         │
  │  ██████████████ handleRedirect (55%)                           │
  │      ████ pg.query (10%)                                       │
  │    ██████ ioredis.get (20%)                                    │
  │  ██████████████████████████████ express.router (75%)           │
  └────────────────────────────────────────────────────────────────┘

  Problem: JSON.stringify is consuming 35% of CPU time.
  Root cause: You're probably calling JSON.stringify on a 
  large object you're logging on every redirect.
  Fix: Use pino's object serialization (which avoids a
  separate JSON.stringify call) or reduce the logged object size.
```

---

## Code Examples

### Detecting Event Loop Lag

```typescript
// src/monitoring/event-loop-lag.ts
// Measures how long a setImmediate callback is delayed.
// Under load, this reveals if synchronous work is blocking the loop.

import { createGauge } from 'prom-client';  // optional: export to Prometheus
import { metricsRegistry } from '../metrics/registry.js';

const eventLoopLagGauge = new (await import('prom-client')).Gauge({
  name: 'nodejs_event_loop_lag_seconds',
  help: 'Node.js event loop lag in seconds',
  registers: [metricsRegistry],
});

export function startEventLoopMonitor(intervalMs = 1000): NodeJS.Timeout {
  return setInterval(() => {
    const start = performance.now();
    setImmediate(() => {
      const lag = (performance.now() - start) / 1000;
      eventLoopLagGauge.set(lag);

      if (lag > 0.1) {
        // pino logger not imported here to avoid circular deps
        console.warn({ lag }, 'Event loop lag >100ms — check for blocking code');
      }
    });
  }, intervalMs);
}
```

### Profiling with `clinic doctor`

```bash
# Install clinic and autocannon globally
npm install -g clinic autocannon

# Profile ScaleForge for 30 seconds under load
# clinic doctor runs your app, collects data, then generates an HTML report

clinic doctor -- node dist/server.js &
SERVER_PID=$!

# Hit it with load
autocannon -c 200 -d 30 http://localhost:3001/abc123

# Kill and generate report
kill $SERVER_PID
# Opens: .clinic/12345.clinic-doctor/index.html
```

### Flame Graph with `0x`

```bash
# 0x instruments V8's CPU profiler, then converts the output to a flame graph

npm install -g 0x

# Run server under 0x profiler
0x dist/server.js &

# Apply load
autocannon -c 100 -d 15 http://localhost:3001/abc123

# ctrl+c the server — 0x generates the flame graph
# Opens: 12345.0x/flamegraph.html

# Reading the flame graph:
# 1. Look for WIDE bars near the TOP of a call stack
# 2. Hover to see function name and % of total CPU time
# 3. Click to zoom into that call tree
```

### Finding Synchronous Hot Spots in Code

```typescript
// src/routes/redirect.router.ts
// BAD: performing synchronous work on every redirect

import express from 'express';

const router = express.Router();

router.get('/:code', async (req, res) => {
  const target = await getTarget(req.params.code);
  if (!target) { res.sendStatus(404); return; }

  // ❌ DO NOT LOG HUGE OBJECTS — JSON.stringify is synchronous and SLOW
  // This serializes the entire Express request object on every redirect.
  // logger.info({ req }, 'Redirect hit');

  // ✅ Only log what you need — small, known-size objects
  logger.info({ code: req.params.code, target: target.originalUrl }, 'Redirect hit');

  // ❌ DO NOT do this — synchronous regex on every hit
  // const clean = target.originalUrl.replace(/(https?:\/\/)?(www\.)?/, '');

  res.redirect(302, target.originalUrl);
});
```

---

## Try It Yourself

**Exercise:** Find the event loop blocker and fix it.

```typescript
// profiling.exercise.ts

// This route has a performance bug. Find it using clinic doctor.
import express from 'express';
import fs from 'node:fs';

const app = express();

app.get('/slow', (_req, res) => {
  // TODO: Why does this route make ALL other routes slow under
  //       concurrency, even though it looks innocent?
  //       Use clinic doctor to confirm your hypothesis.
  const data = fs.readFileSync('./big-file.json', 'utf-8');   // <--- ?
  const parsed = JSON.parse(data);   // <--- ?
  res.json({ count: parsed.items.length });
});

app.get('/fast', (_req, res) => {
  res.json({ status: 'ok' });
});

// Steps:
// 1. Create big-file.json with 50,000 items:
//    node -e "fs.writeFileSync('big-file.json', JSON.stringify({items: Array(50000).fill({id:1})}))"
//
// 2. Load test both routes simultaneously:
//    autocannon -c 100 -d 10 "http://localhost:3000/slow" &
//    autocannon -c 100 -d 10 "http://localhost:3000/fast"
//
// 3. Observe: /fast latency is terrible even though it does nothing.
//
// 4. Fix it (hint: readFile + JSON.parse → use streaming or cache)
```

<details>
<summary>Show solution</summary>

```typescript
// Fix 1: cache the parsed result (read once at startup)
import fs from 'node:fs';

let cachedData: { items: unknown[] } | null = null;

async function getJsonData() {
  if (cachedData) return cachedData;
  const raw = await fs.promises.readFile('./big-file.json', 'utf-8');
  cachedData = JSON.parse(raw);
  return cachedData!;
}

app.get('/slow', async (_req, res) => {
  const data = await getJsonData();  // async read (only on first call)
  res.json({ count: data.items.length });
});

// Fix 2: Don't parse the file on every request at all.
// Store the count in Redis/Postgres when data changes.
// GET /count → Redis GET count (sub-1ms, no file I/O).
```

</details>

---

## Capstone Connection

The redirect handler in ScaleForge is the hottest path in the system. A flame graph after a load test will show you exactly where CPU time goes — whether that's ioredis serialization overhead, Express router traversal, or an accidental `JSON.stringify` inside a log statement. The event loop lag monitor exports a Prometheus gauge that you can alert on: "if event loop lag p99 > 50ms for 2 minutes, page on-call."
