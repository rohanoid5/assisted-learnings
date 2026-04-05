# 8.3 — The N+1 Problem

## Concept

The N+1 problem is **the most common performance killer in ORM usage**. It occurs when you load N parent records and then issue N separate queries to load their children. It can make a fast query into one that fires hundreds of SQL statements.

---

## Deep Dive

### What N+1 Looks Like

```typescript
// ⚠️ N+1: 1 query for pipelines + N queries for steps (one per pipeline)
const pipelines = await db.pipeline.findMany(); // Query 1: SELECT * FROM pipeline

for (const pipeline of pipelines) {
  const steps = await db.step.findMany({        // Query 2, 3, 4... N+1!
    where: { pipelineId: pipeline.id },
  });
  pipeline.steps = steps;
}

// With 100 pipelines: 101 SQL queries!
```

### Fix: Eager Loading with `include`

```typescript
// ✅ 1 query (or 2 with Prisma's JOIN strategy)
const pipelines = await db.pipeline.findMany({
  include: { steps: { orderBy: { order: 'asc' } } },
});

// Prisma generates:
// SELECT * FROM pipeline
// SELECT * FROM step WHERE pipeline_id IN (1, 2, 3, ...) -- single batched query
```

### Enable Query Logging to Spot N+1

```typescript
const db = new PrismaClient({
  log: [
    { emit: 'event', level: 'query' },
  ],
});

db.$on('query', (e) => {
  console.log(`[${e.duration}ms] ${e.query}`);
  // Watch for repeating queries with different parameters!
});
```

### Selecting Only What You Need

```typescript
// ⚠️ Over-fetching: loads every column including large fields
const pipelines = await db.pipeline.findMany({ include: { steps: true } });

// ✅ Right-sized: only the columns the API response needs
const pipelines = await db.pipeline.findMany({
  select: {
    id: true,
    name: true,
    status: true,
    _count: { select: { jobs: true } },   // count without fetching jobs
    steps: { select: { id: true, name: true, type: true }, orderBy: { order: 'asc' } },
  },
});
```

---

## Try It Yourself

**Exercise:** The following code has an N+1 problem. Fix it:

```typescript
const jobs = await db.job.findMany({ where: { status: 'DONE' } });
const results = jobs.map(async (job) => ({
  ...job,
  stepCount: await db.step.count({ where: { pipelineId: job.pipelineId } }),
}));
```

<details>
<summary>Show solution</summary>

```typescript
// ✅ Option 1: Use _count in the query
const jobs = await db.job.findMany({
  where: { status: 'DONE' },
  include: {
    pipeline: {
      select: { _count: { select: { steps: true } } },
    },
  },
});
// Access: job.pipeline._count.steps

// ✅ Option 2: Group count in a single query
const stepCounts = await db.step.groupBy({
  by: ['pipelineId'],
  _count: { id: true },
  where: { pipelineId: { in: jobs.map((j) => j.pipelineId) } },
});
```

</details>

---

## Capstone Connection

PipeForge's `GET /api/v1/pipelines` initially had an N+1 bug — it loaded pipelines then queried steps separately in a loop. After this module, it uses `include: { steps: true, _count: { select: { jobs: true } } }` to load everything in one roundtrip.
