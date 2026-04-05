# Module 10 — Exercises

## Overview

These exercises make PipeForge production-grade: a worker pool for job execution, a live metrics endpoint, and cluster-mode startup.

---

## Exercise 1 — Implement the WorkerPool

**Goal:** Wire up `src/engine/worker-pool.ts` (from the topic) to the `PipelineEngine`.

```typescript
// src/engine/index.ts
const pool = new WorkerPool(
  parseInt(process.env.WORKER_POOL_SIZE ?? '4'),
  new URL('../workers/job-runner.js', import.meta.url).pathname
);

// Jobs are dispatched via pool.run(jobId, steps) instead of running inline
```

Verify: trigger two jobs simultaneously via API — both should execute concurrently.

---

## Exercise 2 — Metrics Endpoint

**Goal:** Implement `GET /api/v1/metrics` (admin-only) returning:

```json
{
  "eventLoop": { "meanMs": "1.23", "p99Ms": "4.56" },
  "memory": { "heapUsedMb": "87.2", "rssMb": "142.0" },
  "workerPool": { "queueDepth": 0, "idleWorkers": 4 },
  "uptime": 3600,
  "pid": 12345
}
```

---

## Exercise 3 — Cluster Mode Startup

**Goal:** Write `src/cluster.ts` that:
1. Forks `availableParallelism()` workers (capped at 4 for dev)
2. Restarts dead workers
3. Sends `SIGUSR2` → rolling restart (new worker up before old one exits)
4. Only starts the `WorkerPool` in the first cluster worker

---

## Exercise 4 — Memory Leak Guard

**Goal:** Add `MemoryMonitor` to each worker thread in `src/workers/job-runner.ts`:

```typescript
const monitor = new MemoryMonitor();
monitor.on('heap-critical', (snapshot) => {
  parentPort!.postMessage({ type: 'MEMORY_CRITICAL', ...snapshot });
  // After current job finishes, exit — cluster primary will spawn a fresh worker
});
monitor.start(30_000);
```

---

## Capstone Checkpoint ✅

Before moving to Module 11, verify:

- [ ] `POST /api/v1/pipelines/:id/jobs` executes jobs in worker threads (not the main thread)
- [ ] Two simultaneous jobs run concurrently (check logs for interleaved step messages)
- [ ] `GET /api/v1/metrics` returns event loop lag + memory + worker pool stats
- [ ] `node src/cluster.js` spawns N workers, each logging their PID
- [ ] Sending SIGUSR2 triggers a rolling restart without downtime (test with `autocannon`)
