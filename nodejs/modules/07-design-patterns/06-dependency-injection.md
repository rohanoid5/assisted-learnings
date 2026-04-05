# 7.6 — Dependency Injection

## Concept

Dependency Injection (DI) means providing a component's dependencies from the outside rather than letting the component create them. In Node.js, manual DI via constructor arguments or factory parameters is usually sufficient — no framework needed. DI makes code testable, flexible, and explicit about its dependencies.

---

## Deep Dive

### The Problem Without DI

```typescript
// ⚠️ Hard to test — directly imports singletons
export class JobController {
  async triggerJob(req: Request, res: Response) {
    const job = await db.job.create({ ... });    // db imported directly
    engine.start(job.id);                         // engine imported directly
    broadcastToJob(job.id, { type: 'started' }); // ws imported directly
    res.json({ jobId: job.id });
  }
}
// To test this, you need a real database and a running engine.
```

### Constructor Injection

```typescript
// ✅ Dependencies injected — easy to test with fakes
interface JobDeps {
  db: PrismaClient;
  engine: PipelineEngine;
  broadcast: (jobId: string, msg: unknown) => void;
}

export class JobController {
  constructor(private readonly deps: JobDeps) {}

  async triggerJob(req: Request, res: Response) {
    const job = await this.deps.db.job.create({ ... });
    this.deps.engine.start(job.id);
    this.deps.broadcast(job.id, { type: 'started' });
    res.json({ jobId: job.id });
  }
}

// Production wiring:
const controller = new JobController({ db, engine, broadcast: broadcastToJob });

// Test wiring:
const fakeDb = { job: { create: vi.fn().mockResolvedValue({ id: 'test-id' }) } };
const fakeEngine = { start: vi.fn() };
const fakeBroadcast = vi.fn();
const controller = new JobController({ db: fakeDb, engine: fakeEngine, broadcast: fakeBroadcast });
```

### Manual DI Container

```typescript
// src/container.ts — wire up the entire app
import { db } from './db/client.js';
import { PipelineEngine } from './core/pipeline-engine.js';
import { broadcastToJob } from './api/ws.js';
import { JobController } from './api/controllers/job.js';
import { PipelineController } from './api/controllers/pipeline.js';

export function createContainer() {
  const engine = new PipelineEngine();

  return {
    engine,
    db,
    controllers: {
      job: new JobController({ db, engine, broadcast: broadcastToJob }),
      pipeline: new PipelineController({ db }),
    },
  };
}

export type AppContainer = ReturnType<typeof createContainer>;
```

---

## Try It Yourself

**Exercise:** Refactor `PipelineController.listPipelines` to use constructor DI:

```typescript
class PipelineController {
  constructor(private readonly db: { pipeline: { findMany: (...args: any[]) => Promise<any[]> } }) {}

  async listPipelines(req: Request, res: Response): Promise<void> {
    // TODO: use this.db (not the global singleton)
  }
}
```

<details>
<summary>Show solution</summary>

```typescript
class PipelineController {
  constructor(private readonly db: PrismaClient) {}

  async listPipelines(req: Request, res: Response): Promise<void> {
    const { cursor, limit = 20 } = req.query as { cursor?: string; limit?: number };
    const items = await this.db.pipeline.findMany({
      take: Number(limit) + 1,
      ...(cursor ? { cursor: { id: cursor }, skip: 1 } : {}),
      orderBy: { createdAt: 'desc' },
    });
    const hasNext = items.length > limit;
    const data = hasNext ? items.slice(0, limit) : items;
    res.json({ data, meta: { hasNext, nextCursor: hasNext ? data.at(-1)?.id : undefined } });
  }
}
```

</details>

---

## Capstone Connection

After this module, PipeForge's controllers all use constructor DI. The integration tests (Module 09) inject fake/stub implementations of `db` and `engine` — making those tests fast (no database needed) and deterministic (no race conditions).
