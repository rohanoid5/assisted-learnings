# 9.3 — Integration Testing

## Concept

Integration tests verify that multiple components work correctly together — your Express routes, Prisma, and middleware all connected. They hit a real (test) database and make real HTTP requests via `supertest`. The key challenge is isolation: each test must leave the DB clean for the next.

---

## Deep Dive

### Supertest Setup

```typescript
// src/api/pipelines.route.test.ts
import { describe, it, expect, beforeAll, afterAll, beforeEach } from 'vitest';
import request from 'supertest';
import { app } from '../api/app.js';
import { db } from '../db/client.js';
import { seedTestUser, clearTables } from '../test/helpers.js';

let authToken: string;
let userId: string;

beforeAll(async () => {
  const user = await seedTestUser();
  userId = user.id;
  authToken = signJwt({ sub: user.id, role: user.role });
});

afterAll(async () => {
  await clearTables(['job_log', 'job', 'step', 'pipeline', 'user']);
  await db.$disconnect();
});

beforeEach(async () => {
  // Clear pipelines before each test to avoid test order coupling
  await db.step.deleteMany();
  await db.pipeline.deleteMany({ where: { ownerId: userId } });
});

describe('POST /api/v1/pipelines', () => {
  it('creates a pipeline with steps', async () => {
    const res = await request(app)
      .post('/api/v1/pipelines')
      .set('Authorization', `Bearer ${authToken}`)
      .send({
        name: 'Test ETL',
        steps: [
          { name: 'Ingest', type: 'CSV_IMPORT', order: 0, config: { path: 'data.csv' } },
        ],
      });

    expect(res.status).toBe(201);
    expect(res.body.data.name).toBe('Test ETL');
    expect(res.body.data.steps).toHaveLength(1);
  });

  it('returns 422 for invalid body', async () => {
    const res = await request(app)
      .post('/api/v1/pipelines')
      .set('Authorization', `Bearer ${authToken}`)
      .send({ name: '' }); // empty name fails validation

    expect(res.status).toBe(422);
    expect(res.body.errors).toBeDefined();
  });
});
```

### Test Helpers

```typescript
// src/test/helpers.ts
import { db } from '../db/client.js';
import bcrypt from 'bcryptjs';

export async function seedTestUser(overrides = {}) {
  return db.user.create({
    data: {
      email: `test+${Date.now()}@pipeforge.io`,
      name: 'Test User',
      role: 'USER',
      passwordHash: await bcrypt.hash('test-password', 1), // rounds=1 for speed
      ...overrides,
    },
  });
}

export async function clearTables(tables: string[]) {
  // Delete in correct order to respect FK constraints
  for (const table of tables) {
    await db.$executeRawUnsafe(`TRUNCATE TABLE "${table}" RESTART IDENTITY CASCADE`);
  }
}
```

### Transaction Isolation Strategy

For faster tests that don't need full truncation:

```typescript
// src/test/setup.ts
import { db } from '../db/client.js';

// Wrap each test in a transaction and roll it back after
export function withTransaction(fn: () => Promise<void>) {
  return async () => {
    await db.$executeRaw`SAVEPOINT test_start`;
    try {
      await fn();
    } finally {
      await db.$executeRaw`ROLLBACK TO SAVEPOINT test_start`;
    }
  };
}
```

---

## Try It Yourself

**Exercise:** Write an integration test for `POST /api/v1/pipelines/:id/jobs` that:
1. Creates a pipeline first (setup)
2. POSTs to trigger a job
3. Asserts `status: 201`, `job.status: 'PENDING'`
4. Asserts the job appears in `GET /api/v1/pipelines/:id/jobs`

<details>
<summary>Show solution</summary>

```typescript
it('triggers and lists a job', async () => {
  // Setup: create a pipeline
  const { body: { data: pipeline } } = await request(app)
    .post('/api/v1/pipelines')
    .set('Authorization', `Bearer ${authToken}`)
    .send({ name: 'Job Test Pipeline', steps: [] });

  // Trigger job
  const triggerRes = await request(app)
    .post(`/api/v1/pipelines/${pipeline.id}/jobs`)
    .set('Authorization', `Bearer ${authToken}`);

  expect(triggerRes.status).toBe(201);
  expect(triggerRes.body.data.status).toBe('PENDING');

  // Verify it appears in job list
  const listRes = await request(app)
    .get(`/api/v1/pipelines/${pipeline.id}/jobs`)
    .set('Authorization', `Bearer ${authToken}`);

  expect(listRes.status).toBe(200);
  expect(listRes.body.data.some((j: { id: string }) => j.id === triggerRes.body.data.id)).toBe(true);
});
```

</details>

---

## Capstone Connection

PipeForge's integration tests use a dedicated `DATABASE_TEST_URL` (separate `pipeforge_test` database). The `vitest.config.ts` sets `process.env.DATABASE_URL = process.env.DATABASE_TEST_URL` in `globalSetup.ts` before any tests run, ensuring the test suite never touches the dev database.
