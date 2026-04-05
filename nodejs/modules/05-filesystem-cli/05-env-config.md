# 5.5 — Environment & Configuration Management

## Concept

Applications need configuration that varies by environment (dev/staging/prod). The twelve-factor app pattern says: store config in environment variables. But raw `process.env` access is stringly-typed and fails silently. Combine `dotenv` (loading `.env` files) with a Zod schema (parsing + validation) for a robust, type-safe config layer.

---

## Deep Dive

### The Problem with Raw `process.env`

```typescript
// ⚠️ Problems with this approach:
const port = Number(process.env.PORT);       // NaN if missing
const dbUrl = process.env.DATABASE_URL;      // string | undefined — no guarantee
const pool = parseInt(process.env.POOL_SIZE); // NaN, no min/max validation

// Fails silently — app starts but crashes later when it tries to connect
```

### Zod + dotenv Configuration Module

```typescript
// src/config/env.ts
import 'dotenv/config';
import { z } from 'zod';

const envSchema = z.object({
  NODE_ENV: z.enum(['development', 'staging', 'production']).default('development'),
  PORT: z.coerce.number().int().min(1).max(65535).default(3000),
  DATABASE_URL: z.string().url('DATABASE_URL must be a valid PostgreSQL URL'),
  JWT_SECRET: z.string().min(32, 'JWT_SECRET must be at least 32 characters'),
  WORKER_POOL_SIZE: z.coerce.number().int().min(1).max(32).default(4),
  LOG_LEVEL: z.enum(['fatal', 'error', 'warn', 'info', 'debug', 'trace']).default('info'),
});

// Parse and validate — throws with clear error if invalid
function parseEnv() {
  const result = envSchema.safeParse(process.env);
  if (!result.success) {
    const issues = result.error.issues
      .map((i) => `  ${i.path.join('.')}: ${i.message}`)
      .join('\n');
    console.error(`[config] Invalid environment variables:\n${issues}`);
    process.exit(1); // fail-fast at startup
  }
  return result.data;
}

export const env = parseEnv(); // singleton — parse once at module load

// Type: { NODE_ENV: 'development'|'staging'|'production', PORT: number, ... }
```

### Using the Config

```typescript
// src/server.ts
import { env } from './config/env.js';

app.listen(env.PORT, () => {
  console.log(`PipeForge API running on port ${env.PORT} [${env.NODE_ENV}]`);
});
```

### Validating in Tests

```typescript
// Override env vars for tests
process.env.DATABASE_URL = 'postgresql://localhost/pipeforge_test';
process.env.JWT_SECRET = 'a'.repeat(32);
// Importing the module again picks up the overrides
// Use vi.resetModules() in Vitest to re-parse config between tests
```

### Loading `.env` Files Correctly

```typescript
// dotenv/config auto-loads .env in CWD
// For custom paths:
import { config } from 'dotenv';
config({ path: '.env.test' }); // load before importing env.ts

// For production (Docker, Kubernetes):
// DON'T use dotenv — inject env vars via the container runtime
// dotenv is a dev-only convenience
```

---

## Try It Yourself

**Exercise:** Add a `REDIS_URL` optional config value and an `EMAIL_FROM` that is required only in production:

```typescript
const envSchema = z.object({
  // ... existing fields ...
  REDIS_URL: z.string().url().optional(),
  EMAIL_FROM: z.string().email().optional(), // required in production!
  // Hint: use .superRefine() for cross-field validation
});
```

<details>
<summary>Show solution</summary>

```typescript
const envSchema = z
  .object({
    NODE_ENV: z.enum(['development', 'staging', 'production']).default('development'),
    PORT: z.coerce.number().int().min(1).max(65535).default(3000),
    DATABASE_URL: z.string().url(),
    JWT_SECRET: z.string().min(32),
    WORKER_POOL_SIZE: z.coerce.number().int().min(1).max(32).default(4),
    REDIS_URL: z.string().url().optional(),
    EMAIL_FROM: z.string().email().optional(),
  })
  .superRefine((data, ctx) => {
    if (data.NODE_ENV === 'production' && !data.EMAIL_FROM) {
      ctx.addIssue({
        code: z.ZodIssueCode.custom,
        path: ['EMAIL_FROM'],
        message: 'EMAIL_FROM is required in production',
      });
    }
  });
```

</details>

---

## Capstone Connection

PipeForge's `src/config/env.ts` is imported by `src/api/server.ts`, `src/db/client.ts`, and `src/workers/job-runner.ts`. The validated env object ensures every subsystem starts with known-good configuration — no integration test failures due to a missing `DATABASE_URL`.
