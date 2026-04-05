# 10.2 — Worker Threads

## Concept

Worker threads run JavaScript in parallel — each with its own V8 heap, event loop, and memory. Unlike child processes, workers share the same process and can transfer ownership of `SharedArrayBuffer` for zero-copy data exchange. Use worker threads for CPU-bound work that would otherwise block the event loop.

---

## Deep Dive

### Basic Worker

```typescript
// src/workers/job-runner.ts  (this file runs inside the worker)
import { workerData, parentPort } from 'node:worker_threads';

interface WorkerInput { jobId: string; steps: StepConfig[] }
interface WorkerOutput { jobId: string; duration: number; status: 'DONE' | 'FAILED' }

const { jobId, steps } = workerData as WorkerInput;

try {
  const start = Date.now();
  for (const step of steps) {
    await executeStep(step); // CPU-bound processing
    parentPort!.postMessage({ type: 'STEP_DONE', stepName: step.name });
  }
  parentPort!.postMessage({ type: 'DONE', jobId, duration: Date.now() - start } satisfies WorkerOutput);
} catch (err) {
  parentPort!.postMessage({ type: 'ERROR', jobId, message: String(err) });
}
```

```typescript
// src/engine/pool.ts  (main thread)
import { Worker } from 'node:worker_threads';
import { fileURLToPath } from 'node:url';
import { join, dirname } from 'node:path';

const WORKER_PATH = join(dirname(fileURLToPath(import.meta.url)), '../workers/job-runner.js');

function runJob(jobId: string, steps: StepConfig[]): Promise<void> {
  return new Promise((resolve, reject) => {
    const worker = new Worker(WORKER_PATH, { workerData: { jobId, steps } });

    worker.on('message', (msg) => {
      if (msg.type === 'STEP_DONE') engine.emit('step:done', msg.stepName);
      if (msg.type === 'DONE') resolve();
      if (msg.type === 'ERROR') reject(new Error(msg.message));
    });
    worker.on('error', reject);
    worker.on('exit', (code) => {
      if (code !== 0) reject(new Error(`Worker exited with code ${code}`));
    });
  });
}
```

### Worker Pool

```typescript
// src/engine/worker-pool.ts
import { Worker } from 'node:worker_threads';
import { EventEmitter } from 'node:events';

interface Task { jobId: string; steps: StepConfig[]; resolve: () => void; reject: (e: Error) => void }

export class WorkerPool extends EventEmitter {
  private queue: Task[] = [];
  private workers: Worker[] = [];
  private idle: Worker[] = [];

  constructor(private size: number, private workerPath: string) {
    super();
    for (let i = 0; i < size; i++) {
      const w = this.createWorker();
      this.idle.push(w);
    }
  }

  run(jobId: string, steps: StepConfig[]): Promise<void> {
    return new Promise((resolve, reject) => {
      const task: Task = { jobId, steps, resolve, reject };
      const worker = this.idle.pop();
      if (worker) {
        this.dispatch(worker, task);
      } else {
        this.queue.push(task); // all workers busy, enqueue
      }
    });
  }

  private dispatch(worker: Worker, task: Task) {
    worker.postMessage({ jobId: task.jobId, steps: task.steps });
    worker.once('message', (msg) => {
      if (msg.type === 'DONE') task.resolve();
      else task.reject(new Error(msg.message));

      // Pick up next queued task, or return to idle
      const next = this.queue.shift();
      if (next) this.dispatch(worker, next);
      else this.idle.push(worker);
    });
  }

  private createWorker() {
    const w = new Worker(this.workerPath);
    this.workers.push(w);
    return w;
  }

  get queueDepth() { return this.queue.length; }
  get idleCount() { return this.idle.length; }
}
```

### `SharedArrayBuffer` — Zero-Copy Data Transfer

```typescript
// For large shared state between threads (e.g., progress counter)
const sharedBuffer = new SharedArrayBuffer(4); // 4 bytes = 1 Int32
const counter = new Int32Array(sharedBuffer);

// In worker:
Atomics.add(counter, 0, 1); // atomic increment
```

---

## Try It Yourself

**Exercise:** Add a `shutdown()` method to `WorkerPool` that:
1. Waits for all in-progress tasks to complete (drains the queue)
2. Terminates all workers with `worker.terminate()`

<details>
<summary>Show solution</summary>

```typescript
async shutdown(): Promise<void> {
  // Wait until all workers are idle (queue is empty and all workers idle)
  await new Promise<void>((resolve) => {
    const check = () => {
      if (this.queue.length === 0 && this.idle.length === this.size) resolve();
      else setTimeout(check, 50);
    };
    check();
  });

  await Promise.all(this.workers.map((w) => w.terminate()));
}
```

</details>

---

## Capstone Connection

`WorkerPool` in `src/engine/worker-pool.ts` is the heart of PipeForge's job execution. The metrics endpoint exposes `pool.queueDepth` and `pool.idleCount`. On `SIGTERM`, the graceful shutdown waits for `pool.shutdown()` before exiting — ensuring no job is left mid-execution.
