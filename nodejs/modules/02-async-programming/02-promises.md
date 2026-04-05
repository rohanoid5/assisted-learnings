# 2.2 — Promises

## Concept

A Promise represents a value that will be available at some point in the future — or never, if it rejects. Understanding the Promise state machine, how `.then()` chains work, and how errors propagate through chains is essential. Async/await is just syntax sugar on top of Promises, so if you misunderstand Promises you'll write buggy async/await code without knowing why.

---

## Deep Dive

### The Promise State Machine

A Promise is always in one of three states:

```
              ┌─────────────────────────────┐
              │          PENDING            │
              │  (initial state — neither   │
              │   fulfilled nor rejected)   │
              └───────────┬─────────────────┘
                          │
          ┌───────────────┴────────────────┐
          │ resolve(value)                  │ reject(reason)
          ▼                                 ▼
   ┌──────────────┐                  ┌──────────────┐
   │  FULFILLED   │                  │   REJECTED   │
   │  (immutable) │                  │  (immutable) │
   └──────────────┘                  └──────────────┘
```

Once a Promise is settled (fulfilled or rejected), it **cannot change**. The value/reason is immutable and permanently associated with that Promise.

### Promise Chaining

`.then()` always returns a **new Promise**. The return value of the `.then()` handler becomes the resolved value of that new Promise:

```typescript
const result = Promise.resolve(1)          // Promise<1>
  .then((x) => x + 1)                     // Promise<2>
  .then((x) => String(x))                 // Promise<'2'>
  .then((x) => ({ value: x }));           // Promise<{value: '2'}>

console.log(await result); // { value: '2' }
```

If a handler returns a Promise, the chain **waits** for that inner Promise:

```typescript
Promise.resolve('config.json')
  .then((path) => readFile(path, 'utf8'))  // returns a Promise — chain waits
  .then((data) => JSON.parse(data))        // receives resolved string data
  .then((config) => console.log(config));
```

### Error Propagation

Errors skip `.then()` handlers and flow to the nearest `.catch()`:

```typescript
Promise.resolve('bad-path.json')
  .then((path) => readFile(path, 'utf8'))   // throws ENOENT
  .then((data) => JSON.parse(data))          // ← SKIPPED
  .then((config) => console.log(config))    // ← SKIPPED
  .catch((err) => {
    console.error('Caught:', err.message);   // ← catches ENOENT
    // If you return here (or don't throw), the chain CONTINUES as fulfilled
    return defaultConfig;
  })
  .then((config) => console.log('Using:', config)); // ← runs with defaultConfig!
```

### Creating Promises

```typescript
// From a value (already resolved)
Promise.resolve(42);
Promise.reject(new Error('Something went wrong'));

// From async work
new Promise<string>((resolve, reject) => {
  setTimeout(() => {
    if (Math.random() > 0.5) resolve('success');
    else reject(new Error('failed'));
  }, 1000);
});

// thenable — any object with a .then method
const thenable = {
  then(resolve: (v: string) => void, reject: (e: Error) => void) {
    resolve('from thenable');
  }
};
await Promise.resolve(thenable); // 'from thenable'
```

### Unhandled Promise Rejections

Always handle rejections. In Node.js 15+, unhandled rejections crash the process:

```typescript
// ⚠️ Dangerous — rejection is not handled
fetch('https://bad-url.invalid').then((res) => res.json());

// ✅ Always attach .catch() or use try/catch with await
fetch('https://bad-url.invalid')
  .then((res) => res.json())
  .catch((err) => console.error('Request failed:', err));

// Global handler (last resort logging, not recovery)
process.on('unhandledRejection', (reason, promise) => {
  console.error('Unhandled rejection:', reason);
});
```

---

## Code Examples

### Promise-Based Retry

```typescript
async function withRetry<T>(
  fn: () => Promise<T>,
  maxAttempts: number,
  delayMs: number,
): Promise<T> {
  let lastError: Error;

  for (let attempt = 1; attempt <= maxAttempts; attempt++) {
    try {
      return await fn();
    } catch (err) {
      lastError = err as Error;
      if (attempt < maxAttempts) {
        await new Promise((resolve) => setTimeout(resolve, delayMs * attempt));
      }
    }
  }
  throw lastError!;
}

// Usage
const data = await withRetry(
  () => fetch('https://api.pipeforge.dev/jobs').then((r) => r.json()),
  3,
  500,
);
```

---

## Try It Yourself

**Exercise:** Implement a `promiseChain` function that runs an array of async transforms sequentially:

```typescript
// Each transform receives the output of the previous one
async function promiseChain<T>(
  initial: T,
  transforms: Array<(input: T) => Promise<T>>,
): Promise<T> {
  // TODO: run transforms sequentially, passing each result to the next
}

// Test:
const result = await promiseChain(
  1,
  [
    async (n) => n * 2,    // 2
    async (n) => n + 10,   // 12
    async (n) => n ** 2,   // 144
  ]
);
console.log(result); // 144
```

<details>
<summary>Show solution</summary>

```typescript
async function promiseChain<T>(
  initial: T,
  transforms: Array<(input: T) => Promise<T>>,
): Promise<T> {
  return transforms.reduce(
    (acc, transform) => acc.then(transform),
    Promise.resolve(initial),
  );
}

// Alternative with for await:
async function promiseChain<T>(
  initial: T,
  transforms: Array<(input: T) => Promise<T>>,
): Promise<T> {
  let value = initial;
  for (const transform of transforms) {
    value = await transform(value);
  }
  return value;
}
```

</details>

---

## Capstone Connection

PipeForge's pipeline model is directly mapped to Promise semantics:
- A **Job** mirrors a Promise — it starts pending, becomes running (like a pending Promise with progress), then either completes (fulfilled) or fails (rejected)
- **Pipeline Steps** are executed as a Promise chain — sequential steps pass data from one to the next
- The retry mechanism in `src/core/pipeline-engine.ts` uses the `withRetry` pattern shown above, configured per step in the `Step.retryPolicy` column
