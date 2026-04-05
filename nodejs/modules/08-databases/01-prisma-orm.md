# 8.1 — Prisma ORM

## Concept

Prisma is a type-safe ORM that generates a fully-typed client from your schema. This topic covers CRUD operations, relation queries, filtering, sorting, and the `select`/`include` distinction — the most common source of N+1 bugs.

---

## Deep Dive

### CRUD Operations

```typescript
import { db } from '../db/client.js';

// Create
const pipeline = await db.pipeline.create({
  data: {
    name: 'Daily ETL',
    ownerId: userId,
    steps: {
      create: [
        { name: 'Extract', type: 'CSV_IMPORT', order: 0, config: {} },
        { name: 'Load',    type: 'DB_INSERT',  order: 1, config: {} },
      ],
    },
  },
  include: { steps: true }, // return with steps
});

// Read with filters
const pipelines = await db.pipeline.findMany({
  where: {
    status: 'ACTIVE',
    ownerId: userId,
    name: { contains: 'ETL', mode: 'insensitive' },
    createdAt: { gte: new Date('2024-01-01') },
  },
  orderBy: { createdAt: 'desc' },
  take: 20,
  skip: 40,
  include: { steps: { orderBy: { order: 'asc' } } },
});

// Update (partial)
const updated = await db.pipeline.update({
  where: { id: pipelineId },
  data: { name: 'Updated ETL', status: 'INACTIVE' },
});

// Upsert
const user = await db.user.upsert({
  where: { email: 'alice@pipeforge.io' },
  create: { email: 'alice@pipeforge.io', name: 'Alice', role: 'ADMIN' },
  update: { lastLoginAt: new Date() },
});

// Delete
await db.pipeline.delete({ where: { id: pipelineId } });
```

### select vs include

```typescript
// include: eagerly loads a relation (fetches related records)
const pipeline = await db.pipeline.findUnique({
  where: { id: pipelineId },
  include: { steps: true, jobs: { take: 5 } },
});

// select: pick specific scalar fields only
const pipelineNames = await db.pipeline.findMany({
  select: { id: true, name: true, status: true },
  // steps NOT included — only 3 scalar fields fetched
});

// ⚠️ Cannot use select and include together at the top level
```

### Many-to-Many with explicit join table

```typescript
// In schema.prisma, PipelineTag is an explicit join model
const tagged = await db.pipeline.findMany({
  where: {
    tags: { some: { tag: { name: 'daily' } } },
  },
});
```

---

## Try It Yourself

**Exercise:** Write a query that returns all jobs for a pipeline, including their logs, ordered by most recent first, limited to 10 jobs with at most 100 logs each:

<details>
<summary>Show solution</summary>

```typescript
const jobs = await db.job.findMany({
  where: { pipelineId },
  orderBy: { startedAt: 'desc' },
  take: 10,
  include: {
    logs: {
      orderBy: { timestamp: 'asc' },
      take: 100,
    },
  },
});
```

</details>

---

## Capstone Connection

This query pattern is used in PipeForge's `GET /api/v1/pipelines/:id/jobs` — returning job history with their logs for the pipeline detail view.
