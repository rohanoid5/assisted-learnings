# 10.3 — Cluster

## Concept

Cluster forks multiple Node.js processes (workers) from a primary process, all sharing the same TCP port. The OS distributes incoming connections across workers via round-robin. Each worker is a full copy of the application — providing fault isolation and true multi-core parallelism for I/O-bound workloads.

---

## Deep Dive

### Basic Cluster Setup

```typescript
// src/cluster.ts
import cluster, { type Worker } from 'node:cluster';
import { availableParallelism } from 'node:os';
import process from 'node:process';

const NUM_WORKERS = parseInt(process.env.WEB_CONCURRENCY ?? String(availableParallelism()));

if (cluster.isPrimary) {
  console.log(`Primary ${process.pid} — forking ${NUM_WORKERS} workers`);

  for (let i = 0; i < NUM_WORKERS; i++) {
    cluster.fork();
  }

  // Respawn dead workers
  cluster.on('exit', (worker, code, signal) => {
    console.warn(`Worker ${worker.process.pid} died (${signal || code}). Restarting...`);
    cluster.fork();
  });

} else {
  // Worker process — start the HTTP server
  const { startServer } = await import('./api/server.js');
  await startServer();
  console.log(`Worker ${process.pid} started`);
}
```

### Graceful Rolling Restart (Zero-Downtime Deploy)

```typescript
// Primary sends SIGUSR2 → workers drain connections before dying
if (cluster.isPrimary) {
  process.on('SIGUSR2', async () => {
    const workers = Object.values(cluster.workers!);
    console.log('Rolling restart of', workers.length, 'workers');

    for (const worker of workers as Worker[]) {
      worker.send('shutdown');           // worker starts refusing new connections
      await new Promise<void>((res) => setTimeout(res, 1000)); // small stagger
      cluster.fork();                    // new worker up
      await new Promise<void>((res) => worker.once('exit', res)); // old worker gone
    }
    console.log('Rolling restart complete');
  });
}

// Worker handles the shutdown message
if (cluster.isWorker) {
  process.on('message', (msg) => {
    if (msg === 'shutdown') {
      server.close(() => process.exit(0));  // stop accepting, let existing finish
    }
  });
}
```

### When NOT to Use Cluster

- If you already use a process manager (PM2 with `instances: 'max'`): PM2 handles clustering
- For job workers (`WorkerPool`): use `worker_threads` instead of cluster to share memory
- Serverless/container environments: horizontal scaling is done by the orchestrator (K8s)

---

## Try It Yourself

**Exercise:** Modify the cluster setup to log the worker PID on each HTTP request, proving that the OS distributes requests across workers:

<details>
<summary>Show solution</summary>

```typescript
// In server.ts (worker), add a middleware:
app.use((_req, res, next) => {
  res.setHeader('X-Worker-PID', String(process.pid));
  next();
});

// Then run:
// node src/cluster.js
// for i in {1..10}; do curl -si http://localhost:3000/health | grep X-Worker-PID; done
// You'll see different PIDs — confirming round-robin distribution
```

</details>

---

## Capstone Connection

PipeForge's production startup script (`src/cluster.ts`) forks `availableParallelism()` workers. The API server (HTTP) runs in cluster workers; the `WorkerPool` (job execution) runs only in worker 1 to avoid duplicate job execution across workers. This is controlled by `cluster.worker?.id === 1`.
