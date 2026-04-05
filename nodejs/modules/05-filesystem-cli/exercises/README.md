# Module 5 — Exercises

## Overview

These exercises build PipeForge's complete CLI (`pipeforge`) and its environment configuration module.

---

## Exercise 1 — Environment Configuration

**Goal:** Implement the Zod-validated environment module.

Create `src/config/env.ts`:

```typescript
import 'dotenv/config';
import { z } from 'zod';

const envSchema = z.object({
  NODE_ENV: z.enum(['development', 'staging', 'production']).default('development'),
  PORT: z.coerce.number().int().min(1024).max(65535).default(3000),
  DATABASE_URL: z.string().url(),
  JWT_SECRET: z.string().min(32, 'JWT_SECRET must be at least 32 characters'),
  WORKER_POOL_SIZE: z.coerce.number().int().min(1).max(32).default(4),
  LOG_LEVEL: z.enum(['fatal', 'error', 'warn', 'info', 'debug', 'trace']).default('info'),
});

function parseEnv() {
  const result = envSchema.safeParse(process.env);
  if (!result.success) {
    // print formatted errors and exit(1)
  }
  return result.data;
}

export const env = parseEnv();
```

Test: copy `.env.example` to `.env`, deliberately remove `JWT_SECRET`, run `pipeforge` — verify it prints a clear error and exits 1.

---

## Exercise 2 — Commander CLI Skeleton

**Goal:** Wire up all six subcommands in `src/cli/index.ts`.

Required subcommands:
- `run <pipelineId>` with `--env` and `--dry-run` options
- `list` with `--status` filter
- `logs <jobId>` with `--follow` flag
- `status <jobId>`
- `import <file>` (uploads a pipeline definition JSON file)
- `init` (interactive pipeline creator from Topic 5.6)

Test:
```bash
node --loader ts-node/esm src/cli/index.ts --help
```

---

## Exercise 3 — `pipeforge import` File Command

**Goal:** Implement file walking for the import command.

```typescript
// pipeforge import ./pipelines-dir/
// Discovers all *.json files, validates each, POSTs to API

async function importCommand(pathArg: string) {
  const resolved = resolve(process.cwd(), pathArg);
  const stat = await fsStat(resolved);

  if (stat.isDirectory()) {
    // walk the directory, collect all .json files
  } else {
    // single file import
  }
}
```

---

## Exercise 4 — `pipeforge logs --follow`

**Goal:** Stream NDJSON from the API and print logs in real time.

```typescript
// GET /api/jobs/:id/logs returns NDJSON
// Parse line-by-line using readline on the response body
async function logsCommand({ jobId, follow }: { jobId: string; follow: boolean }) {
  const res = await fetch(`${apiUrl}/api/jobs/${jobId}/logs`);
  const rl = createInterface({ input: Readable.fromWeb(res.body!) });
  for await (const line of rl) {
    const log = JSON.parse(line);
    const colour = log.level === 'ERROR' ? chalk.red : chalk.white;
    console.log(`${chalk.dim(log.timestamp)}  ${colour(log.message)}`);
  }
}
```

---

## Capstone Checkpoint ✅

Before moving to Module 6, verify:

- [ ] `src/config/env.ts` validates all required env vars at startup and exits 1 with a clear message if any are missing or invalid
- [ ] `pipeforge --help` shows all six subcommands with descriptions
- [ ] `pipeforge run <id>` triggers a job and shows a spinner
- [ ] `pipeforge list` prints a table of pipelines
- [ ] `pipeforge logs <id>` streams NDJSON and prints formatted log lines
- [ ] `pipeforge import ./pipelines/` walks a directory and imports all JSON files
