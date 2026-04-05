# 9.4 — Mocking

## Concept

Mocks replace real dependencies — database, file system, HTTP clients, timers — with controlled fakes. Good mocking strategy: mock at the boundary of your system (external I/O), not inside it. Over-mocking leads to tests that pass but don't catch real bugs.

---

## Deep Dive

### `vi.fn()` — Function Stubs

```typescript
import { it, expect, vi } from 'vitest';
import { processJob } from './engine.js';

it('calls onComplete when job finishes', async () => {
  const onComplete = vi.fn();
  const job = { id: 'job_1', steps: [] };

  await processJob(job, { onComplete });

  expect(onComplete).toHaveBeenCalledOnce();
  expect(onComplete).toHaveBeenCalledWith(expect.objectContaining({ id: 'job_1' }));
});
```

### `vi.mock()` — Module Mocking

```typescript
// Mock the entire db module
import { vi, describe, it, expect, beforeEach } from 'vitest';

vi.mock('../db/client.js', () => ({
  db: {
    pipeline: {
      findMany: vi.fn(),
      create: vi.fn(),
    },
    job: {
      findFirst: vi.fn(),
    },
  },
}));

import { db } from '../db/client.js'; // now returns the mock
import { getPipelines } from './pipeline-service.js';

describe('getPipelines', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('returns pipelines from db', async () => {
    const fakeData = [{ id: '1', name: 'ETL', status: 'ACTIVE' }];
    vi.mocked(db.pipeline.findMany).mockResolvedValueOnce(fakeData as any);

    const result = await getPipelines('user_1');
    expect(result).toEqual(fakeData);
    expect(db.pipeline.findMany).toHaveBeenCalledWith(
      expect.objectContaining({ where: { ownerId: 'user_1' } })
    );
  });
});
```

### `vi.spyOn()` — Wrap Real Implementation

```typescript
import { vi, it, expect } from 'vitest';
import * as fs from 'node:fs/promises';

it('calls fs.writeFile with correct path', async () => {
  const spy = vi.spyOn(fs, 'writeFile').mockResolvedValueOnce();
  
  await saveConfig({ port: 3000 });

  expect(spy).toHaveBeenCalledWith(
    expect.stringContaining('config.json'),
    expect.any(String),
    expect.objectContaining({ encoding: 'utf-8' })
  );

  spy.mockRestore(); // clean up spy
});
```

### Mocking `fetch`

```typescript
it('calls the webhook URL', async () => {
  const mockFetch = vi.fn().mockResolvedValueOnce(
    new Response(null, { status: 200 })
  );

  await sendWebhook('https://hook.example.com', { event: 'job.done' }, { fetch: mockFetch });

  expect(mockFetch).toHaveBeenCalledWith(
    'https://hook.example.com',
    expect.objectContaining({
      method: 'POST',
      headers: expect.objectContaining({ 'content-type': 'application/json' }),
    })
  );
});
```

### Timer Mocking

```typescript
import { vi, it, expect, beforeEach, afterEach } from 'vitest';

beforeEach(() => { vi.useFakeTimers(); });
afterEach(() => { vi.useRealTimers(); });

it('retries after delay', async () => {
  const fn = vi.fn().mockRejectedValueOnce(new Error('fail')).mockResolvedValueOnce('ok');
  
  const promise = retryWithDelay(fn, { delay: 5000 });
  
  // Fast-forward time without actually waiting
  await vi.advanceTimersByTimeAsync(5000);
  
  const result = await promise;
  expect(result).toBe('ok');
  expect(fn).toHaveBeenCalledTimes(2);
});
```

---

## Try It Yourself

**Exercise:** Mock the `PluginRegistry` to test that the engine calls `registry.get(stepType)` for each step:

<details>
<summary>Show solution</summary>

```typescript
import { vi, describe, it, expect } from 'vitest';
import { PipelineEngine } from './engine.js';

it('looks up plugin for each step type', async () => {
  const mockHandler = vi.fn().mockResolvedValue({ success: true });
  const mockRegistry = {
    get: vi.fn().mockReturnValue({ handler: mockHandler }),
  };

  const engine = new PipelineEngine({ registry: mockRegistry as any });
  const pipeline = {
    steps: [
      { type: 'CSV_IMPORT', config: {} },
      { type: 'DB_INSERT', config: {} },
    ],
  };

  await engine.runPipeline(pipeline as any, 'job_1');

  expect(mockRegistry.get).toHaveBeenCalledWith('CSV_IMPORT');
  expect(mockRegistry.get).toHaveBeenCalledWith('DB_INSERT');
  expect(mockHandler).toHaveBeenCalledTimes(2);
});
```

</details>

---

## Capstone Connection

PipeForge's test suite mocks three external boundaries: `db` (Prisma client), `fetch` (external webhooks), and `fs` (file system imports). Internal classes like `PluginRegistry`, `CircuitBreaker`, and `Pipeline<T>` are tested with real implementations, not mocks, to catch logic errors.
