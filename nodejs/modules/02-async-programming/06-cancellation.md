# 2.6 — Cancellation with AbortController

## Concept

Async operations should be cancellable. HTTP requests, database queries, and long-running jobs shouldn't run forever just because the client disconnected. `AbortController` and `AbortSignal` are the standard way to propagate cancellation through async operations in modern Node.js.

---

## Deep Dive

### AbortController Basics

```typescript
// Create a controller
const controller = new AbortController();
const { signal } = controller;

// Start an operation that accepts a signal
const fetchPromise = fetch('https://api.pipeforge.dev/jobs', { signal });

// Cancel it after 5 seconds
setTimeout(() => controller.abort(), 5000);

try {
  const data = await fetchPromise;
} catch (err) {
  if (err instanceof Error && err.name === 'AbortError') {
    console.log('Request was cancelled');
  } else {
    throw err; // re-throw unexpected errors
  }
}
```

### Checking Signal State

```typescript
async function processJob(jobId: string, signal: AbortSignal): Promise<void> {
  // Check before starting expensive work
  if (signal.aborted) {
    throw new DOMException('Job cancelled before starting', 'AbortError');
  }

  // Listen for cancellation during processing
  signal.addEventListener('abort', () => {
    console.log('Cancellation requested during job processing');
    // Clean up resources, release locks, etc.
  });

  for (const step of steps) {
    // Check between steps
    if (signal.aborted) {
      throw new DOMException('Job cancelled between steps', 'AbortError');
    }
    await executeStep(step, signal); // propagate signal downward
  }
}
```

### Timeout Signals

```typescript
// Node.js 17.3+ — create a signal that auto-aborts after a timeout
const signal = AbortSignal.timeout(5000); // 5 second timeout

const response = await fetch('https://api.pipeforge.dev/data', { signal });
// Throws 'TimeoutError' after 5 seconds if not resolved
```

### Combining Signals

```typescript
// Abort if EITHER the user cancels OR the timeout fires
function anySignal(signals: AbortSignal[]): AbortSignal {
  const controller = new AbortController();

  function onAbort(this: AbortSignal) {
    controller.abort(this.reason);
    cleanup();
  }

  function cleanup() {
    for (const signal of signals) {
      signal.removeEventListener('abort', onAbort);
    }
  }

  for (const signal of signals) {
    if (signal.aborted) {
      controller.abort(signal.reason);
      return controller.signal;
    }
    signal.addEventListener('abort', onAbort);
  }

  return controller.signal;
}

const userCancelSignal = getUserCancelSignal(jobId);
const timeoutSignal = AbortSignal.timeout(30_000);
const signal = anySignal([userCancelSignal, timeoutSignal]);

await processJob(jobId, signal);
```

### Built-in Support

Many Node.js APIs accept signals natively:

```typescript
// fetch
await fetch(url, { signal });

// node:fs
import { readFile } from 'node:fs/promises';
await readFile('large-file.bin', { signal });

// node:timers/promises
import { setTimeout as delay, setInterval as interval } from 'node:timers/promises';
await delay(5000, undefined, { signal }); // cancellable sleep!

// node:child_process (Node 20+)
import { execFile } from 'node:child_process';
import { promisify } from 'node:util';
await promisify(execFile)('some-command', ['arg'], { signal });
```

---

## Code Examples

### Cancellable Job Runner

```typescript
// src/core/pipeline-engine.ts
export class PipelineEngine extends EventEmitter {
  private readonly cancellers = new Map<string, AbortController>();

  async runJob(jobId: string): Promise<void> {
    const controller = new AbortController();
    this.cancellers.set(jobId, controller);

    try {
      await this.executeJob(jobId, controller.signal);
    } finally {
      this.cancellers.delete(jobId);
    }
  }

  cancelJob(jobId: string): boolean {
    const controller = this.cancellers.get(jobId);
    if (!controller) return false;
    controller.abort(new Error(`Job ${jobId} cancelled by user`));
    return true;
  }

  private async executeJob(jobId: string, signal: AbortSignal): Promise<void> {
    const job = await db.job.findUniqueOrThrow({ where: { id: jobId } });

    for (const step of job.pipeline.steps) {
      if (signal.aborted) throw signal.reason;
      await this.executeStep(step, signal);
    }
  }
}
```

---

## Try It Yourself

**Exercise:** Implement a `withTimeout` function using `AbortSignal.timeout`:

```typescript
// Wraps any async function with a timeout.
// If the function doesn't complete within 'ms', it should throw a TimeoutError.
async function withTimeout<T>(
  fn: (signal: AbortSignal) => Promise<T>,
  ms: number,
): Promise<T> {
  // TODO: use AbortSignal.timeout(ms) and pass the signal to fn
}

// Usage:
const result = await withTimeout(
  (signal) => processHeavyJob(jobId, signal),
  30_000
);
```

<details>
<summary>Show solution</summary>

```typescript
async function withTimeout<T>(
  fn: (signal: AbortSignal) => Promise<T>,
  ms: number,
): Promise<T> {
  const signal = AbortSignal.timeout(ms);
  try {
    return await fn(signal);
  } catch (err) {
    if (err instanceof DOMException && err.name === 'TimeoutError') {
      throw new Error(`Operation timed out after ${ms}ms`);
    }
    throw err;
  }
}
```

</details>

---

## Capstone Connection

PipeForge's `PipelineEngine` uses `AbortController` as the primary cancellation mechanism:
- Every job execution receives an `AbortSignal` — cancellation propagates through all steps
- The `DELETE /jobs/:id` API endpoint calls `engine.cancelJob(id)` which triggers the abort
- `AbortSignal.timeout(step.timeoutMs)` enforces per-step time limits defined in the database
- The WebSocket disconnect handler (Module 06) cancels long-polling log streams when the client disconnects
