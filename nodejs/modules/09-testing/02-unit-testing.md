# 9.2 — Unit Testing

## Concept

A unit test verifies a single function or class in isolation — with all external dependencies replaced by fakes. Unit tests are fast (< 1ms each), deterministic, and pinpoint exactly which logic broke.

---

## Deep Dive

### Pure Function Testing

```typescript
// src/lib/cursor.ts
export function encodeCursor(id: string, date: Date): string {
  return Buffer.from(JSON.stringify({ id, date: date.toISOString() })).toString('base64url');
}

export function decodeCursor(cursor: string): { id: string; date: Date } {
  const raw = JSON.parse(Buffer.from(cursor, 'base64url').toString('utf-8'));
  return { id: raw.id, date: new Date(raw.date) };
}
```

```typescript
// src/lib/cursor.test.ts
import { describe, it, expect } from 'vitest';
import { encodeCursor, decodeCursor } from './cursor.js';

describe('cursor encoding', () => {
  it('is reversible', () => {
    const id = 'pipe_123';
    const date = new Date('2024-06-01T00:00:00Z');

    const cursor = encodeCursor(id, date);
    const decoded = decodeCursor(cursor);

    expect(decoded.id).toBe(id);
    expect(decoded.date.toISOString()).toBe(date.toISOString());
  });
});
```

### Class Testing

```typescript
// src/domain/circuit-breaker.test.ts
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { CircuitBreaker } from './circuit-breaker.js';

describe('CircuitBreaker', () => {
  let cb: CircuitBreaker;

  beforeEach(() => {
    cb = new CircuitBreaker({ threshold: 2, timeout: 100 });
  });

  it('starts CLOSED and passes calls through', async () => {
    const result = await cb.call(async () => 'ok');
    expect(result).toBe('ok');
    expect(cb.state).toBe('CLOSED');
  });

  it('opens after threshold failures', async () => {
    const fail = () => Promise.reject(new Error('down'));
    await expect(cb.call(fail)).rejects.toThrow();
    await expect(cb.call(fail)).rejects.toThrow();
    expect(cb.state).toBe('OPEN');
  });

  it('rejects immediately when OPEN', async () => {
    // Force open state
    const fail = () => Promise.reject(new Error('down'));
    await cb.call(fail).catch(() => {});
    await cb.call(fail).catch(() => {});

    await expect(cb.call(async () => 'ok')).rejects.toThrow('Circuit open');
  });
});
```

### Testing Async Error Paths

```typescript
// Always test the error case, not just the happy path
it('rejects with ValidationError for invalid config', async () => {
  const builder = new PipelineBuilder().name('X').ownerId('user_1');

  await expect(builder.withConfig({ invalid: true }).build())
    .rejects.toBeInstanceOf(ValidationError);
});

// Testing that an async function calls a callback on error
it('calls onError handler when step fails', async () => {
  const onError = vi.fn();
  const engine = new PipelineEngine({ onError });

  await engine.run(failingPipeline);

  expect(onError).toHaveBeenCalledOnce();
  expect(onError).toHaveBeenCalledWith(expect.any(StepError));
});
```

---

## Try It Yourself

**Exercise:** Write unit tests for the `fetchWithRetry` function from Module 06:
- Happy path: succeeds on first attempt
- Retry path: fails twice, succeeds on third attempt
- Max retries exceeded: throws after N+1 failures

<details>
<summary>Show solution</summary>

```typescript
import { describe, it, expect, vi } from 'vitest';
import { fetchWithRetry } from './http-client.js';

describe('fetchWithRetry', () => {
  it('returns response on first success', async () => {
    const mockFetch = vi.fn().mockResolvedValueOnce(new Response('ok', { status: 200 }));
    const res = await fetchWithRetry('https://api.example.com', {}, { fetch: mockFetch });
    expect(mockFetch).toHaveBeenCalledOnce();
    expect(res.ok).toBe(true);
  });

  it('retries on failure and succeeds', async () => {
    const mockFetch = vi.fn()
      .mockRejectedValueOnce(new Error('timeout'))
      .mockRejectedValueOnce(new Error('timeout'))
      .mockResolvedValueOnce(new Response('ok', { status: 200 }));

    const res = await fetchWithRetry('https://api.example.com', {}, { fetch: mockFetch, retries: 3 });
    expect(mockFetch).toHaveBeenCalledTimes(3);
    expect(res.ok).toBe(true);
  });

  it('throws after max retries', async () => {
    const mockFetch = vi.fn().mockRejectedValue(new Error('network error'));
    await expect(
      fetchWithRetry('https://api.example.com', {}, { fetch: mockFetch, retries: 2 })
    ).rejects.toThrow('network error');
    expect(mockFetch).toHaveBeenCalledTimes(3); // initial + 2 retries
  });
});
```

</details>

---

## Capstone Connection

PipeForge unit tests live alongside source files (`*.test.ts` colocated). The `CircuitBreaker`, `cursor`, `formatDuration`, and `PluginRegistry` classes are all covered at the unit level. Dependencies like `fetch` and the DB are always injected so they can be swapped for test doubles.
