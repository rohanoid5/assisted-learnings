# 9.1 — Testing Fundamentals

## Concept

Node.js 20+ ships `node:test` — a built-in test runner that handles test suites, assertions, mocking, and coverage without extra dependencies. For PipeForge we use **Vitest** instead, which is built on the same API surface but adds TypeScript support, ES Module compatibility, and a watch mode that's dramatically faster than Jest.

---

## Deep Dive

### node:test vs Vitest

| Feature | `node:test` | Vitest |
|---------|-------------|--------|
| Built-in | ✅ | No (package) |
| TypeScript | Manual transform | ✅ Native |
| ESM | Partial | ✅ Full |
| Watch mode | Basic | ✅ Smart |
| Globals (describe/it) | opt-in | opt-in |
| Compatible API | — | ✅ Same describe/it/expect |
| Coverage | `--experimental-coverage` | v8 or istanbul |

### Vitest Config

```typescript
// vitest.config.ts
import { defineConfig } from 'vitest/config';

export default defineConfig({
  test: {
    globals: false,           // explicit imports > globals
    environment: 'node',
    coverage: {
      provider: 'v8',
      reporter: ['text', 'lcov', 'html'],
      thresholds: { lines: 70, branches: 70, functions: 70, statements: 70 },
      exclude: ['prisma/**', 'dist/**', '**/*.d.ts'],
    },
  },
});
```

### First Test

```typescript
// src/lib/pipeline-builder.test.ts
import { describe, it, expect } from 'vitest';
import { PipelineBuilder } from './pipeline-builder.js';

describe('PipelineBuilder', () => {
  it('builds a pipeline with required fields', () => {
    const pipeline = new PipelineBuilder()
      .name('Daily ETL')
      .ownerId('user_123')
      .build();

    expect(pipeline.name).toBe('Daily ETL');
    expect(pipeline.ownerId).toBe('user_123');
    expect(pipeline.status).toBe('ACTIVE'); // default
  });

  it('throws if name is missing', () => {
    expect(() => new PipelineBuilder().build()).toThrow('name is required');
  });
});
```

### Running Tests

```bash
# Run tests once
npx vitest run

# Watch mode (re-runs affected tests on save)
npx vitest

# With coverage
npx vitest run --coverage
```

---

## Try It Yourself

**Exercise:** Write two tests for a `formatDuration(ms: number): string` utility that:
- Returns `"42ms"` for values < 1000
- Returns `"1.5s"` for 1500ms

<details>
<summary>Show solution</summary>

```typescript
import { describe, it, expect } from 'vitest';
import { formatDuration } from './format.js';

describe('formatDuration', () => {
  it('returns milliseconds for < 1s', () => {
    expect(formatDuration(42)).toBe('42ms');
    expect(formatDuration(999)).toBe('999ms');
  });

  it('returns seconds for >= 1s', () => {
    expect(formatDuration(1500)).toBe('1.5s');
    expect(formatDuration(2000)).toBe('2.0s');
  });
});
```

</details>

---

## Capstone Connection

PipeForge uses Vitest with `v8` coverage. The `package.json` `scripts.test` runs `vitest run` and `scripts.test:watch` runs `vitest`. Coverage is enforced in CI via `--coverage --reporter=junit`.
