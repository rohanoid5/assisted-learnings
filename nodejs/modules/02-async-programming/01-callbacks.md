# 2.1 — Callbacks

## Concept

Before Promises, Node.js async was done entirely with callbacks. Understanding the callback pattern — and its failure modes — explains *why* Promises and async/await were invented. Even today, you'll encounter callback APIs in legacy code, streams, and event emitters. Knowing the pattern well means you can `promisify` them correctly.

---

## Deep Dive

### Error-First Callbacks

Node.js established the **error-first callback convention**: the first argument is always `null` or an `Error`, and subsequent arguments are the success values.

```typescript
// fs.readFile follows error-first convention
import { readFile } from 'node:fs';

readFile('data.json', 'utf8', (err, data) => {
  if (err) {
    console.error('Failed to read file:', err.message);
    return; // ← Critical: return prevents executing success path
  }
  console.log('File contents:', data);
});
```

This convention is the basis of `util.promisify`. Any function following this pattern can be automatically wrapped into a Promise.

### Why Callbacks Fall Apart: Callback Hell

When async operations depend on each other, nesting becomes unmanageable:

```typescript
readFile('config.json', 'utf8', (err, configData) => {
  if (err) return handleError(err);

  parseConfig(configData, (err, config) => {
    if (err) return handleError(err);

    connectToDatabase(config.dbUrl, (err, db) => {
      if (err) return handleError(err);

      db.query('SELECT * FROM jobs', (err, jobs) => {
        if (err) return handleError(err);

        // ... now we have data, 4 levels deep
        // Error handling is duplicated at every level
        // Can't use return, try/catch, or any sync-style control flow
      });
    });
  });
});
```

Problems with deeply nested callbacks:
- **Error handling** must be done manually at every level
- **Can't use `try/catch`** — errors thrown inside callbacks don't propagate
- **Hard to reason about** execution order
- **No return values** — data must be passed through callback parameters
- **No composition** — you can't easily reuse or test individual steps

### Promisifying Callbacks

```typescript
import { promisify } from 'node:util';
import { readFile, writeFile } from 'node:fs';

const readFileAsync = promisify(readFile);
const writeFileAsync = promisify(writeFile);

// Now usable with async/await
const data = await readFileAsync('config.json', 'utf8');
```

For the modern `node:fs` module, `fs/promises` is already promisified:

```typescript
import { readFile, writeFile } from 'node:fs/promises'; // ← prefer this
```

### When Callbacks Are Still Appropriate

Callbacks remain the right choice for:

1. **Event listeners** — `emitter.on('data', handler)` — fires multiple times
2. **Stream handler hooks** — Transform stream `_transform(chunk, encoding, callback)`
3. **Low-level performance-critical code** — Promises add a microtask queue flush overhead

---

## Code Examples

### Converting a Callback API to Promise

```typescript
import { createConnection } from 'node:net';
import { promisify } from 'node:util';

// Some legacy packages don't use fs/promises style — wrap them
function connectAsync(host: string, port: number): Promise<import('node:net').Socket> {
  return new Promise((resolve, reject) => {
    const socket = createConnection({ host, port }, () => resolve(socket));
    socket.on('error', reject);
  });
}

const socket = await connectAsync('localhost', 5432);
```

### Parallel Callback Operations (The Hard Way)

```typescript
// Without Promises, parallelism requires manual counting
function readAllFiles(paths: string[], callback: (err: Error | null, results?: string[]) => void) {
  const results: string[] = new Array(paths.length);
  let completed = 0;

  paths.forEach((path, i) => {
    readFile(path, 'utf8', (err, data) => {
      if (err) return callback(err);
      results[i] = data;
      if (++completed === paths.length) callback(null, results);
    });
  });
}
// With Promise.all, this is one line. That's why Promises won.
```

---

## Try It Yourself

**Exercise:** Wrap the `setTimeout` function in a Promise-based `sleep` utility.

```typescript
// sleep.ts
// Create a sleep(ms: number): Promise<void> function
// that resolves after the given number of milliseconds.
// Do NOT use util.promisify — implement it manually.

export function sleep(ms: number): Promise<void> {
  // TODO: return a new Promise that resolves after 'ms' milliseconds
}

// Test:
console.log('before');
await sleep(1000);
console.log('after (1 second later)');
```

<details>
<summary>Show solution</summary>

```typescript
export function sleep(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
}
```

This is the canonical pattern for turning any time-based callback into a Promise. It's used throughout PipeForge for retry delays in Module 03.

</details>

---

## Capstone Connection

PipeForge uses callbacks in specific, appropriate places:
- `EventEmitter.on('step:complete', handler)` in the pipeline engine — events fire multiple times, callbacks are correct
- Transform stream `_transform()` method callbacks in Module 04 — the stream API requires the callback pattern to signal backpressure
- `process.on('SIGTERM', handler)` for signal handling in Module 11

For all other async operations (database queries, HTTP requests, job execution), PipeForge uses async/await.
