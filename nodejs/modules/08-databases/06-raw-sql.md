# 8.6 — Raw SQL with Prisma

## Concept

Sometimes an ORM query can't express what you need — complex window functions, CTEs, UPSERT with conflict resolution, or a query that Prisma generates inefficiently. Prisma's `$queryRaw` and `$executeRaw` escape hatch lets you write raw SQL while staying type-safe.

---

## Deep Dive

### `$queryRaw` — Returns Records

```typescript
import { Prisma } from '@prisma/client';

// Template literal syntax — AUTOMATICALLY PARAMETERIZED (SQL injection safe!)
const jobId = req.params.id;
const results = await db.$queryRaw<{ step: string; count: bigint }[]>`
  SELECT step_name as step, COUNT(*) as count
  FROM job_log
  WHERE job_id = ${jobId}          -- ${jobId} becomes $1 parameter
    AND level = 'ERROR'
  GROUP BY step_name
  ORDER BY count DESC
`;

// Always cast results to a typed array
console.log(results.map((r) => ({ step: r.step, count: Number(r.count) })));
```

### `$executeRaw` — DDL / No Return Value

```typescript
// Refresh a materialized view
await db.$executeRaw`REFRESH MATERIALIZED VIEW CONCURRENTLY pipeline_stats`;

// Update with complex logic
await db.$executeRaw`
  UPDATE job
  SET status = 'TIMED_OUT', finished_at = NOW()
  WHERE status = 'RUNNING'
    AND started_at < NOW() - INTERVAL '1 hour'
`;
```

### Dynamic Queries (Use `Prisma.sql` carefully)

```typescript
// ⚠️ For dynamic field names (ORDER BY, column names) — canNOT be parameterized
// Use Prisma.sql to compose safe fragments

const orderColumn = req.query.sort === 'name'
  ? Prisma.sql`name`
  : Prisma.sql`created_at`;

const rows = await db.$queryRaw`
  SELECT id, name, status FROM pipeline
  ORDER BY ${orderColumn} DESC
  LIMIT ${20}
`;
```

### SQL Injection Prevention

```typescript
// ✅ SAFE — template literal auto-parameterizes
const id = req.params.id; // could be anything
const row = await db.$queryRaw`SELECT * FROM pipeline WHERE id = ${id}`;
// Generates: SELECT * FROM pipeline WHERE id = $1 with id as parameter

// ❌ NEVER DO THIS — string concatenation bypasses parameterization
const UNSAFE = await db.$queryRaw(
  Prisma.raw(`SELECT * FROM pipeline WHERE id = '${id}'`) // SQL injection!
);
```

---

## Try It Yourself

**Exercise:** Write a raw SQL query that returns the top 5 pipelines by total job count in the last 30 days:

<details>
<summary>Show solution</summary>

```typescript
const top5 = await db.$queryRaw<{ pipeline_id: string; name: string; job_count: bigint }[]>`
  SELECT p.id as pipeline_id, p.name, COUNT(j.id) as job_count
  FROM pipeline p
  JOIN job j ON j.pipeline_id = p.id
  WHERE j.started_at >= NOW() - INTERVAL '30 days'
  GROUP BY p.id, p.name
  ORDER BY job_count DESC
  LIMIT 5
`;

return top5.map((r) => ({ ...r, job_count: Number(r.job_count) }));
```

</details>

---

## Capstone Connection

PipeForge's analytics endpoint (`GET /api/v1/analytics/pipeline-stats`) uses `$queryRaw` to run a window function query that computes moving averages of job duration — a query that Prisma's query builder can't express in a single roundtrip.
