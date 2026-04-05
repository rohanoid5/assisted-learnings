# 2.4 — Promise Combinators

## Concept

When you need to run multiple Promises at once, you have four tools: `Promise.all`, `Promise.allSettled`, `Promise.race`, and `Promise.any`. They have very different failure semantics. Using the wrong one leads to silent errors or wasted work.

---

## Deep Dive

### `Promise.all` — All or Nothing

Runs all Promises in parallel. Resolves when **all** settle successfully. **Rejects immediately** if any one rejects (short-circuits).

```
Input:  [P1(1s), P2(2s), P3(3s)]
Output: [v1,     v2,     v3    ]   after 3s (parallel)

If P2 rejects at 2s:
Output: ❌ rejected with P2's error (P3 still running but result is discarded!)
```

```typescript
const [user, pipelines, jobs] = await Promise.all([
  fetchUser(userId),
  fetchPipelines(userId),
  fetchRecentJobs(userId),
]);
// ALL succeed → we have all three
// ANY fails → error thrown, others discarded
```

**Use when:** You need all results and any failure means you can't proceed.

### `Promise.allSettled` — Wait for All, Collect Results

Runs all Promises in parallel. **Always resolves** (never rejects). Returns an array of result objects: `{ status: 'fulfilled', value }` or `{ status: 'rejected', reason }`.

```typescript
const results = await Promise.allSettled([
  processStep(step1),
  processStep(step2),
  processStep(step3),
]);

for (const result of results) {
  if (result.status === 'fulfilled') {
    console.log('Step succeeded:', result.value);
  } else {
    console.error('Step failed:', result.reason);
  }
}
```

**Use when:** You want all to run regardless of failures, and you need to handle partial success.

### `Promise.race` — First Settled Wins

Resolves or rejects with the **first** Promise to settle (either way). Remaining Promises continue running but their results are ignored.

```typescript
// Timeout pattern: reject if operation takes too long
function withTimeout<T>(promise: Promise<T>, ms: number): Promise<T> {
  const timeout = new Promise<never>((_, reject) =>
    setTimeout(() => reject(new Error(`Timeout after ${ms}ms`)), ms)
  );
  return Promise.race([promise, timeout]);
}

const result = await withTimeout(fetchExternalData(), 5000);
```

**Use when:** You need a timeout or want the fastest of N equivalent sources.

### `Promise.any` — First Success Wins

Resolves with the **first fulfilled** Promise. Only rejects (with `AggregateError`) if **all** reject.

```typescript
// Try multiple CDN mirrors — use whichever responds first
const data = await Promise.any([
  fetch('https://cdn1.pipeforge.dev/plugin.js'),
  fetch('https://cdn2.pipeforge.dev/plugin.js'),
  fetch('https://cdn-fallback.pipeforge.dev/plugin.js'),
]).then((res) => res.text());
```

**Use when:** You have multiple equivalent sources and want the first success.

### Comparison Table

| Combinator | Runs | Resolves | Rejects |
|-----------|------|---------|---------|
| `Promise.all` | All in parallel | When all succeed | When first fails |
| `Promise.allSettled` | All in parallel | Always (after all settle) | Never |
| `Promise.race` | All in parallel | When first settles | When first settles |
| `Promise.any` | All in parallel | When first succeeds | When all fail |

---

## Code Examples

### Batched allSettled with Error Reporting

```typescript
async function runPipelineSteps(steps: Step[]): Promise<StepResult[]> {
  const results = await Promise.allSettled(
    steps.map((step) => executeStep(step))
  );

  return results.map((result, i) => {
    if (result.status === 'fulfilled') {
      return { step: steps[i], status: 'success', output: result.value };
    } else {
      return { step: steps[i], status: 'failed', error: result.reason };
    }
  });
}
```

---

## Try It Yourself

**Exercise:** Implement a `promiseHash` function similar to Promise.all but for objects:

```typescript
// type PromiseValues<T> = { [K in keyof T]: Awaited<T[K]> };
async function promiseHash<T extends Record<string, Promise<unknown>>>(
  obj: T
): Promise<{ [K in keyof T]: Awaited<T[K]> }> {
  // TODO: resolve all values in parallel, return an object with the same keys
}

// Usage:
const data = await promiseHash({
  user: fetchUser('123'),
  jobs: fetchJobs('123'),
  metrics: fetchMetrics('123'),
});
// data.user, data.jobs, data.metrics all typed correctly
```

<details>
<summary>Show solution</summary>

```typescript
async function promiseHash<T extends Record<string, Promise<unknown>>>(
  obj: T
): Promise<{ [K in keyof T]: Awaited<T[K]> }> {
  const keys = Object.keys(obj) as (keyof T)[];
  const values = await Promise.all(keys.map((k) => obj[k]));
  return Object.fromEntries(keys.map((k, i) => [k, values[i]])) as { [K in keyof T]: Awaited<T[K]> };
}
```

</details>

---

## Capstone Connection

PipeForge uses all four combinators in specific places:
- **`Promise.all`**: Fetching user + pipeline + step config when starting a job (all required — any failure aborts)
- **`Promise.allSettled`**: Running independent pipeline stages in parallel — partial failure is recorded, not fatal
- **`Promise.race`**: HTTP request timeout in the API client (Module 06)
- **`Promise.any`**: Plugin loading from multiple search paths (Module 07)
