# 8.4 — Transactions

## Concept

A transaction groups multiple database operations into an atomic unit — either all succeed or all fail, leaving the database in a consistent state. Prisma supports interactive transactions (fine-grained control) and sequential operations (simpler but less powerful).

---

## Deep Dive

### Basic Interactive Transaction

```typescript
// All operations inside run in a single transaction.
// If any throws, all changes are rolled back.
const result = await db.$transaction(async (tx) => {
  // tx is a PrismaClient scoped to this transaction
  const pipeline = await tx.pipeline.create({
    data: { name: 'ETL', ownerId: userId },
  });

  const steps = await tx.step.createMany({
    data: stepConfigs.map((s, i) => ({
      ...s,
      pipelineId: pipeline.id,
      order: i,
    })),
  });

  return { pipeline, stepsCreated: steps.count };
});
```

### Transaction Options

```typescript
await db.$transaction(async (tx) => {
  // ...
}, {
  maxWait: 5000,   // max time to wait for transaction slot (ms)
  timeout: 10_000, // max transaction duration before auto-rollback (ms)
  isolationLevel: 'Serializable', // strictest isolation
});
```

### Job Lifecycle Transaction

```typescript
// Atomically: create job record + first log entry + update pipeline.lastRunAt
async function createJob(pipelineId: string, userId: string) {
  return db.$transaction(async (tx) => {
    const job = await tx.job.create({
      data: { pipelineId, triggeredById: userId, status: 'PENDING' },
    });

    await tx.jobLog.create({
      data: { jobId: job.id, level: 'INFO', message: 'Job created', stepName: 'system' },
    });

    await tx.pipeline.update({
      where: { id: pipelineId },
      data: { lastRunAt: new Date() },
    });

    return job;
  });
}
```

### Sequential (Batch) Transactions

```typescript
// For independent operations — Prisma batches them into one roundtrip
const [userCount, pipelineCount, jobCount] = await db.$transaction([
  db.user.count(),
  db.pipeline.count({ where: { status: 'ACTIVE' } }),
  db.job.count({ where: { status: 'RUNNING' } }),
]);
```

---

## Try It Yourself

**Exercise:** Implement a "soft delete" transaction that:
1. Sets `pipeline.status = 'INACTIVE'`
2. Cancels all RUNNING jobs for that pipeline (sets status = 'CANCELLED')
3. Creates an audit log entry in `JobLog` for each cancelled job

<details>
<summary>Show solution</summary>

```typescript
async function softDeletePipeline(pipelineId: string) {
  return db.$transaction(async (tx) => {
    const runningJobs = await tx.job.findMany({
      where: { pipelineId, status: 'RUNNING' },
      select: { id: true },
    });

    await tx.pipeline.update({
      where: { id: pipelineId },
      data: { status: 'INACTIVE' },
    });

    if (runningJobs.length > 0) {
      await tx.job.updateMany({
        where: { id: { in: runningJobs.map((j) => j.id) } },
        data: { status: 'CANCELLED', finishedAt: new Date() },
      });

      await tx.jobLog.createMany({
        data: runningJobs.map((job) => ({
          jobId: job.id,
          level: 'WARN',
          message: 'Job cancelled due to pipeline deactivation',
          stepName: 'system',
        })),
      });
    }

    return { cancelled: runningJobs.length };
  });
}
```

</details>

---

## Capstone Connection

PipeForge uses transactions in three places: job creation (create job + initial log + update pipeline), job completion (update job status + create summary log + update pipeline stats), and pipeline deletion (soft delete + cancel running jobs). The transaction guarantees no torn state if the server crashes mid-operation.
