# 7.3 — Observer Pattern & EventEmitter

## Concept

Node.js's `EventEmitter` is the Observer pattern built into the platform. Understanding how to use it — and how to type it safely — is essential for building decoupled, event-driven systems. This topic covers the EventEmitter API deeply and shows how to create typed custom emitters.

---

## Deep Dive

### EventEmitter Fundamentals

```typescript
import { EventEmitter } from 'node:events';

const emitter = new EventEmitter();

// Register listeners
emitter.on('data', (chunk: Buffer) => console.log('chunk:', chunk.length));
emitter.once('end', () => console.log('stream ended')); // fires only once

// Emit events
emitter.emit('data', Buffer.from('hello'));
emitter.emit('end');

// Listener management
emitter.off('data', handler);          // remove specific listener
emitter.removeAllListeners('data');    // remove all for event

// Default limit: 10 listeners per event (memory leak warning)
emitter.setMaxListeners(20);           // increase if needed
EventEmitter.defaultMaxListeners = 20; // global default
```

### Typed EventEmitter

```typescript
// Node.js 20 way: use TypedEventEmitter interface
interface EngineEvents {
  'job:started':  [jobId: string];
  'job:progress': [jobId: string, step: string, percent: number];
  'job:done':     [jobId: string, result: JobResult];
  'job:failed':   [jobId: string, error: Error];
  'step:started': [jobId: string, stepName: string];
  'step:done':    [jobId: string, stepName: string, durationMs: number];
}

class PipelineEngine extends EventEmitter {
  emit<K extends keyof EngineEvents>(event: K, ...args: EngineEvents[K]): boolean {
    return super.emit(event, ...args);
  }

  on<K extends keyof EngineEvents>(event: K, listener: (...args: EngineEvents[K]) => void): this {
    return super.on(event, listener as (...args: unknown[]) => void);
  }

  once<K extends keyof EngineEvents>(event: K, listener: (...args: EngineEvents[K]) => void): this {
    return super.once(event, listener as (...args: unknown[]) => void);
  }
}

// Now listeners are fully typed!
engine.on('job:progress', (jobId, step, percent) => {
  //                       ^^^^^^  ^^^^  ^^^^^^^  All inferred correctly!
  broadcastToJob(jobId, { step, percent });
});
```

### Waiting for Events (Async)

```typescript
import { once } from 'node:events';

// Wait for a single event as a Promise
const [result] = await once(engine, 'job:done');

// Wait with timeout
const [result] = await Promise.race([
  once(engine, 'job:done'),
  new Promise<never>((_, reject) =>
    setTimeout(() => reject(new Error('Timeout')), 30_000),
  ),
]);
```

---

## Try It Yourself

**Exercise:** Implement a `PipelineEngine.waitForJob(jobId)` method that returns a Promise that resolves when the job completes (done or failed):

```typescript
async waitForJob(jobId: string): Promise<JobResult> {
  // TODO: listen for job:done and job:failed, whichever comes first
  // Hint: use Promise.race and once()
}
```

<details>
<summary>Show solution</summary>

```typescript
async waitForJob(jobId: string): Promise<JobResult> {
  return new Promise<JobResult>((resolve, reject) => {
    const onDone = (id: string, result: JobResult) => {
      if (id !== jobId) return;
      this.off('job:done', onDone);
      this.off('job:failed', onFailed);
      resolve(result);
    };
    const onFailed = (id: string, err: Error) => {
      if (id !== jobId) return;
      this.off('job:done', onDone);
      this.off('job:failed', onFailed);
      reject(err);
    };
    this.on('job:done', onDone);
    this.on('job:failed', onFailed);
  });
}
```

</details>

---

## Capstone Connection

`PipelineEngine` emits lifecycle events that three different consumers listen to: the WebSocket broadcaster (Module 06), the database logger (writes to `JobLog`), and the CLI progress reporter. EventEmitter decouples these three consumers — adding a fourth consumer (e.g., sending an email on failure) requires zero changes to the engine.
