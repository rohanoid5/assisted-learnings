# 7.2 — Singleton Pattern in ESM

## Concept

The Singleton pattern ensures a class has exactly one instance. In Node.js with ESM, the module cache already enforces this — any exported value is initialized once and shared across all imports. Understanding this makes Singletons trivial to implement correctly and avoids Common pitfalls.

---

## Deep Dive

### ESM Module Cache = Free Singleton

```typescript
// src/db/client.ts
import { PrismaClient } from '@prisma/client';

// This code runs exactly once — the module cache returns the same instance
// for all subsequent imports of this file
export const db = new PrismaClient({
  log: process.env.NODE_ENV === 'development'
    ? ['query', 'warn', 'error']
    : ['warn', 'error'],
});

// Any file that does `import { db } from './db/client.js'` gets THE SAME instance
```

### When You Need Explicit Singleton Logic

For lazy initialization or conditional creation:

```typescript
// src/core/pipeline-engine.ts
import { EventEmitter } from 'node:events';

class PipelineEngine extends EventEmitter {
  private static instance: PipelineEngine | null = null;

  private constructor() { super(); }

  static getInstance(): PipelineEngine {
    if (!PipelineEngine.instance) {
      PipelineEngine.instance = new PipelineEngine();
    }
    return PipelineEngine.instance;
  }

  // ... methods
}

export const engine = PipelineEngine.getInstance();
// Or just: export const engine = new PipelineEngine();
// Both work — ESM guarantees one instance.
```

### Testing Singletons (The Real Challenge)

The problem with singletons is test isolation — one test's state leaks into the next:

```typescript
// ⚠️ Problem: db is shared across all tests
// State created in test A is visible in test B

// ✅ Solution 1: Use transactions that rollback
beforeEach(async () => {
  await db.$executeRaw`BEGIN`;
});
afterEach(async () => {
  await db.$executeRaw`ROLLBACK`;
});

// ✅ Solution 2: Truncate tables in afterEach
afterEach(async () => {
  await db.$executeRaw`TRUNCATE pipeline, job, job_log, "user" CASCADE`;
});

// ✅ Solution 3: Dependency injection — pass db, engine as arguments (Topic 7.6)
// This way tests can pass different instances
```

---

## Try It Yourself

**Exercise:** Implement a `Logger` singleton that reads `LOG_LEVEL` from config once and exposes `log(level, message)`:

```typescript
// src/lib/logger.ts
// Should return the same Logger instance from any import
```

<details>
<summary>Show solution</summary>

```typescript
// src/lib/logger.ts
import { env } from '../config/env.js';

type Level = 'fatal' | 'error' | 'warn' | 'info' | 'debug' | 'trace';
const LEVELS: Level[] = ['fatal', 'error', 'warn', 'info', 'debug', 'trace'];

function isEnabled(level: Level): boolean {
  return LEVELS.indexOf(level) <= LEVELS.indexOf(env.LOG_LEVEL);
}

export const logger = {
  log(level: Level, message: string, data?: unknown): void {
    if (!isEnabled(level)) return;
    const entry = { ts: new Date().toISOString(), level, message, ...(data ? { data } : {}) };
    process.stdout.write(JSON.stringify(entry) + '\n');
  },
  info: (msg: string, data?: unknown) => logger.log('info', msg, data),
  error: (msg: string, data?: unknown) => logger.log('error', msg, data),
  debug: (msg: string, data?: unknown) => logger.log('debug', msg, data),
};
```

</details>

---

## Capstone Connection

PipeForge has three singletons courtesy of ESM module caching: `db` (PrismaClient), `engine` (PipelineEngine), and `logger`. The key design decision is that they're created at module level — which means they initialize eagerly at server startup. The Zod env validation (Module 05) ensures that startup fails fast if any config is missing.
