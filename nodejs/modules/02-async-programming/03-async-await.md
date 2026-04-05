# 2.3 — async/await

## Concept

`async/await` is syntactic sugar for Promises. It lets you write asynchronous code that looks synchronous, making it easier to reason about, debug, and test. But it has non-obvious failure modes: swallowed errors, accidentally sequential code where parallel was intended, and `await` inside loops.

---

## Deep Dive

### How async/await Desugars

```typescript
// async/await version
async function fetchUser(id: string) {
  const res = await fetch(`/api/users/${id}`);
  return res.json();
}

// Equivalent Promise version (what the runtime actually executes)
function fetchUser(id: string) {
  return fetch(`/api/users/${id}`).then((res) => res.json());
}
```

An `async` function **always returns a Promise**, even if you return a plain value. The `await` keyword **pauses execution of the async function** (not the event loop!) until the Promise settles.

### Error Handling Patterns

```typescript
// ✅ Pattern 1: try/catch (most idiomatic)
async function loadJob(id: string) {
  try {
    const job = await db.job.findUniqueOrThrow({ where: { id } });
    return job;
  } catch (err) {
    if (err instanceof Prisma.NotFoundError) {
      return null; // handle specific error
    }
    throw err; // re-throw unexpected errors
  }
}

// ✅ Pattern 2: .catch() on the awaited Promise
async function loadJob(id: string) {
  const job = await db.job.findUnique({ where: { id } }).catch(() => null);
  return job;
}

// ❌ Anti-pattern: unhandled rejection
async function loadJob(id: string) {
  const job = await db.job.findUniqueOrThrow({ where: { id } }); // throws silently!
  return job;
}
```

### Sequential vs Parallel: The Most Common async/await Mistake

```typescript
// ❌ Accidentally sequential — each waits for the previous to finish
async function getJobsSequential(ids: string[]) {
  const results = [];
  for (const id of ids) {
    const job = await db.job.findUnique({ where: { id } }); // sequential!
    results.push(job);
  }
  return results; // takes ids.length × queryTime
}

// ✅ Parallel with Promise.all
async function getJobsParallel(ids: string[]) {
  return Promise.all(
    ids.map((id) => db.job.findUnique({ where: { id } }))
  ); // takes max(queryTime) — much faster!
}

// ✅ Parallel with batching (when you have 1000s of items)
async function getJobsBatched(ids: string[], batchSize = 10) {
  const results = [];
  for (let i = 0; i < ids.length; i += batchSize) {
    const batch = ids.slice(i, i + batchSize);
    const batchResults = await Promise.all(
      batch.map((id) => db.job.findUnique({ where: { id } }))
    );
    results.push(...batchResults);
  }
  return results;
}
```

### async/await in Different Contexts

```typescript
// Class methods
class PipelineEngine {
  async runJob(jobId: string): Promise<void> {
    const job = await this.db.job.findUniqueOrThrow({ where: { id: jobId } });
    // ...
  }
}

// Arrow functions
const runJob = async (jobId: string): Promise<void> => {
  // ...
};

// IIFE (especially useful in ESM for top-level await workarounds)
(async () => {
  const db = await connectDatabase();
  const app = createServer(db);
  app.listen(3000);
})();

// But in ESM, just use top-level await:
const db = await connectDatabase();
const app = createServer(db);
app.listen(3000);
```

### The Forgotten return await

```typescript
// ⚠️ Subtle — the try/catch doesn't catch errors from the return
async function riskyOperation() {
  try {
    return doSomethingAsync(); // ← NOT awaited!
  } catch (err) {
    console.error('This never runs for async errors!');
  }
}

// ✅ Correct — await inside try/catch so errors are caught
async function riskyOperation() {
  try {
    return await doSomethingAsync(); // ← awaited
  } catch (err) {
    console.error('This catches errors correctly');
  }
}
```

---

## Code Examples

### Controlled Concurrency

```typescript
// Run up to N promises concurrently using a semaphore
async function withConcurrency<T>(
  items: T[],
  concurrency: number,
  fn: (item: T) => Promise<unknown>,
): Promise<void> {
  const executing = new Set<Promise<unknown>>();

  for (const item of items) {
    const p = fn(item).finally(() => executing.delete(p));
    executing.add(p);

    if (executing.size >= concurrency) {
      await Promise.race(executing); // wait for one to finish
    }
  }

  await Promise.all(executing); // wait for all remaining
}

// Process 100 jobs, 5 at a time
await withConcurrency(jobIds, 5, async (id) => {
  await processJob(id);
});
```

---

## Try It Yourself

**Exercise:** Fix the concurrency bug in this code.

```typescript
// This code works but is 3x slower than it needs to be.
// Refactor it to execute the three fetches in parallel.

async function getDashboardData(userId: string) {
  const user = await db.user.findUnique({ where: { id: userId } });
  const pipelines = await db.pipeline.findMany({ where: { userId } });
  const recentJobs = await db.job.findMany({
    where: { pipeline: { userId } },
    take: 10,
    orderBy: { startedAt: 'desc' },
  });

  return { user, pipelines, recentJobs };
}
```

<details>
<summary>Show solution</summary>

```typescript
async function getDashboardData(userId: string) {
  const [user, pipelines, recentJobs] = await Promise.all([
    db.user.findUnique({ where: { id: userId } }),
    db.pipeline.findMany({ where: { userId } }),
    db.job.findMany({
      where: { pipeline: { userId } },
      take: 10,
      orderBy: { startedAt: 'desc' },
    }),
  ]);

  return { user, pipelines, recentJobs };
}
```

All three queries are independent — they can run at the same time. `Promise.all` fans them out concurrently and waits for all to complete.

</details>

---

## Capstone Connection

The PipeForge job runner uses these exact patterns:
- **Sequential execution**: pipeline steps that depend on previous step output run sequentially with `for...of` + `await`
- **Parallel execution**: independent steps within the same pipeline stage run with `Promise.all`
- **Controlled concurrency**: the job queue runs at most `WORKER_POOL_SIZE` jobs concurrently using the semaphore pattern
- **`return await`**: all async operations inside `try/catch` blocks in PipeForge correctly use `return await` to ensure errors are caught
