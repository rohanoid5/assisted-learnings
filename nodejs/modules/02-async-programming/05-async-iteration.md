# 2.5 — Async Iteration

## Concept

Async generators and `for await...of` let you work with sequences of values that arrive over time — database cursor results, paginated API responses, file lines, event streams. They give you the elegance of `for...of` without loading everything into memory at once.

---

## Deep Dive

### Async Generators

An async generator is a function declared with `async function*` that uses both `await` and `yield`:

```typescript
async function* generateJobLogs(jobId: string) {
  let cursor: string | undefined;

  while (true) {
    const page = await db.jobLog.findMany({
      where: { jobId },
      take: 100,
      ...(cursor ? { cursor: { id: cursor }, skip: 1 } : {}),
      orderBy: { timestamp: 'asc' },
    });

    if (page.length === 0) break;

    for (const log of page) {
      yield log; // ← pause here, hand one log to the consumer
    }

    cursor = page[page.length - 1].id;
  }
}

// Consumer: processes one log at a time, never loads all into memory
for await (const log of generateJobLogs(jobId)) {
  console.log(`[${log.level}] ${log.message}`);
}
```

### The AsyncIterable Protocol

An async iterable is any object with a `[Symbol.asyncIterator]()` method:

```typescript
class JobLogStream implements AsyncIterable<JobLog> {
  constructor(private readonly jobId: string) {}

  async *[Symbol.asyncIterator](): AsyncGenerator<JobLog> {
    yield* generateJobLogs(this.jobId);
  }
}

// Usage is identical to above
const stream = new JobLogStream(jobId);
for await (const log of stream) {
  process(log);
}
```

### Transforming Async Streams

```typescript
// map: transform each item
async function* map<T, U>(
  iterable: AsyncIterable<T>,
  fn: (item: T) => Promise<U> | U,
): AsyncGenerator<U> {
  for await (const item of iterable) {
    yield await fn(item);
  }
}

// filter: skip items
async function* filter<T>(
  iterable: AsyncIterable<T>,
  predicate: (item: T) => boolean | Promise<boolean>,
): AsyncGenerator<T> {
  for await (const item of iterable) {
    if (await predicate(item)) yield item;
  }
}

// take: limit to N items
async function* take<T>(
  iterable: AsyncIterable<T>,
  n: number,
): AsyncGenerator<T> {
  let count = 0;
  for await (const item of iterable) {
    if (++count > n) break;
    yield item;
  }
}

// Usage:
const errorLogs = filter(generateJobLogs(jobId), (log) => log.level === 'ERROR');
const first10Errors = take(errorLogs, 10);

for await (const log of first10Errors) {
  console.error(log.message);
}
```

### Converting Node.js Streams to AsyncIterables

Node.js Readable streams are already async iterables since Node.js 12:

```typescript
import { createReadStream } from 'node:fs';
import { createInterface } from 'node:readline';

// Read a file line by line, without loading it into memory
const fileStream = createReadStream('large-pipeline.csv');
const rl = createInterface({ input: fileStream });

for await (const line of rl) {
  const [id, name, status] = line.split(',');
  await processPipelineRow({ id, name, status });
}
```

---

## Try It Yourself

**Exercise:** Implement a `paginate` async generator that fetches paginated API data.

```typescript
interface PagedResponse<T> {
  data: T[];
  nextPage: number | null;
}

async function* paginate<T>(
  fetchPage: (page: number) => Promise<PagedResponse<T>>,
): AsyncGenerator<T> {
  // TODO: yield items from each page, follow pagination until nextPage is null
}

// Test:
for await (const job of paginate((page) => fetchJobs({ page, limit: 20 }))) {
  console.log(job.id);
}
```

<details>
<summary>Show solution</summary>

```typescript
async function* paginate<T>(
  fetchPage: (page: number) => Promise<PagedResponse<T>>,
): AsyncGenerator<T> {
  let page = 1;

  while (true) {
    const { data, nextPage } = await fetchPage(page);

    for (const item of data) {
      yield item;
    }

    if (nextPage === null) break;
    page = nextPage;
  }
}
```

</details>

---

## Capstone Connection

PipeForge uses async iteration throughout:
- **Job log streaming** (Module 06): The WebSocket layer streams job logs to the client using an async generator that pages through `JobLog` records as they're written
- **Pipeline import** (Module 05): The CLI reads large YAML pipeline definition files line-by-line using `readline` async iteration
- **Database cursors** (Module 08): Large result sets use Prisma cursor pagination wrapped in async generators to avoid OOM errors when exporting thousands of job records
