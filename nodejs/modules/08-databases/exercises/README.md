# Module 8 — Exercises

## Overview

These exercises complete PipeForge's data layer — migrations, optimized queries, and transactions.

---

## Exercise 1 — Run Initial Migration

**Goal:** Initialize the database schema from the Prisma schema.

```bash
# Copy .env.example to .env and set DATABASE_URL
cp .env.example .env

# Create the database
createdb pipeforge

# Apply the initial migration
npx prisma migrate dev --name initial_schema

# Seed the admin user
npx prisma db seed

# Open Prisma Studio to explore the data
npx prisma studio
```

---

## Exercise 2 — Fix N+1 in Pipeline List

**Goal:** Profile and fix the N+1 query in the pipeline list endpoint.

1. Enable query logging in `db/client.ts`
2. Call `GET /api/v1/pipelines` and count the queries
3. Rewrite using `include` + `select` to reduce to 1–2 queries
4. Verify the response shape is unchanged

---

## Exercise 3 — Implement createJob Transaction

**Goal:** Implement `src/db/job-repo.ts:createJob()` as a transaction:

```typescript
export async function createJob(pipelineId: string, triggeredById: string) {
  return db.$transaction(async (tx) => {
    // 1. Create the Job record
    // 2. Create the initial JobLog entry ('Job queued')
    // 3. Update pipeline.lastRunAt to now()
    // Return the created job
  });
}
```

---

## Exercise 4 — Soft Delete Pipeline

**Goal:** Implement `softDeletePipeline(pipelineId)` (from the transactions topic exercise) and wire it to `DELETE /api/v1/pipelines/:id`.

---

## Capstone Checkpoint ✅

Before moving to Module 9, verify:

- [ ] All migrations applied; `npx prisma migrate status` shows "Database schema is up to date"
- [ ] `GET /api/v1/pipelines` generates ≤ 2 SQL queries (verified via query log)
- [ ] `POST /api/v1/pipelines/:id/jobs` uses a transaction to create job + log + update pipeline
- [ ] `DELETE /api/v1/pipelines/:id` soft-deletes and cancels running jobs atomically
- [ ] Raw SQL analytics endpoint returns the top 5 pipelines by job count
