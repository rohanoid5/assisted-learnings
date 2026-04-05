# 10.4 — Memory Management

## Concept

Node.js applications can suffer from memory leaks: long-lived event listener references, accumulating caches, and retained closures. This topic covers the V8 heap model, measuring memory, and diagnosing leaks.

---

## Deep Dive

### V8 Heap Structure

```
process.memoryUsage() returns:
┌─────────────────────────────────────────────────────────┐
│ rss          — Resident Set Size: total memory held by  │
│                the OS for this process (includes heap + │
│                stack + code)                            │
│ heapTotal    — total allocated heap (from V8)           │
│ heapUsed     — actual JS objects on the heap            │
│ external     — C++ objects (Buffers, native handles)    │
│ arrayBuffers — SharedArrayBuffers + ArrayBuffers        │
└─────────────────────────────────────────────────────────┘

Normal: heapUsed grows gradually, drops after GC
Leak:   heapUsed grows monotonically, never drops
```

### Tracking Memory Over Time

```typescript
// src/lib/memory-monitor.ts
import { EventEmitter } from 'node:events';

interface MemorySnapshot {
  heapUsedMb: number;
  rssMb: number;
  timestamp: number;
}

export class MemoryMonitor extends EventEmitter {
  private timer?: NodeJS.Timeout;

  start(intervalMs = 30_000) {
    this.timer = setInterval(() => {
      const { heapUsed, rss } = process.memoryUsage();
      const snapshot: MemorySnapshot = {
        heapUsedMb: Math.round(heapUsed / 1024 / 1024),
        rssMb: Math.round(rss / 1024 / 1024),
        timestamp: Date.now(),
      };

      if (snapshot.heapUsedMb > 512) {
        this.emit('heap-critical', snapshot); // trigger worker restart
      }

      this.emit('snapshot', snapshot);
    }, intervalMs);

    this.timer.unref(); // don't block process exit
  }

  stop() { clearInterval(this.timer); }
}
```

### Common Leak Patterns and Fixes

**Leak: Unbounded EventEmitter listeners**
```typescript
// ⚠️ Leak: new listener added every request, old ones never removed
app.get('/stream', (req, res) => {
  engine.on('job:done', (job) => res.write(`data: ${JSON.stringify(job)}\n\n`));
  // listener is never removed when client disconnects!
});

// ✅ Fix: remove listener on disconnect
app.get('/stream', (req, res) => {
  const send = (job: Job) => res.write(`data: ${JSON.stringify(job)}\n\n`);
  engine.on('job:done', send);
  req.on('close', () => engine.off('job:done', send)); // cleanup!
});
```

**Leak: Growing Map/Set without eviction**
```typescript
// ⚠️ Leak: cache grows forever
const cache = new Map<string, Job[]>();

// ✅ Fix: use a size-bounded LRU cache (lru-cache package)
import { LRUCache } from 'lru-cache';
const cache = new LRUCache<string, Job[]>({ max: 500 });
```

### Taking a Heap Snapshot

```bash
# Start with inspector
node --inspect src/api/server.js

# In Chrome:  chrome://inspect → Open dedicated DevTools for Node
# Memory tab → Profiling → Take heap snapshot
# Compare two snapshots to find what grew
```

---

## Try It Yourself

**Exercise:** Add a `/metrics/memory` endpoint to PipeForge that returns `heapUsedMb`, `heapTotalMb`, `rssMb`, and `externalMb`:

<details>
<summary>Show solution</summary>

```typescript
// In src/api/routes/metrics.route.ts
router.get('/memory', (_req, res) => {
  const { heapUsed, heapTotal, rss, external } = process.memoryUsage();
  res.json({
    heapUsedMb: (heapUsed / 1024 / 1024).toFixed(1),
    heapTotalMb: (heapTotal / 1024 / 1024).toFixed(1),
    rssMb: (rss / 1024 / 1024).toFixed(1),
    externalMb: (external / 1024 / 1024).toFixed(1),
  });
});
```

</details>

---

## Capstone Connection

PipeForge's `MemoryMonitor` runs in each worker thread and cluster worker. When `heapUsedMb > 512`, the monitor emits `heap-critical`, which is logged and causes the cluster primary to spawn a fresh replacement worker before the leaking one exits gracefully.
