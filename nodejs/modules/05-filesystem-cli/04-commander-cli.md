# 5.4 — Commander.js CLI

## Concept

Commander.js is the most widely used CLI framework in the Node.js ecosystem. It handles argument parsing, subcommands, option defaults, and help generation. The goal is to build a `pipeforge` CLI that feels like a professional tool — clear help text, informative errors, and shell tab completion.

---

## Deep Dive

### Basic Structure

```typescript
#!/usr/bin/env node
import { Command } from 'commander';

const program = new Command();

program
  .name('pipeforge')
  .description('Data pipeline & job management CLI')
  .version('1.0.0');

program.parse(); // process.argv
```

### Global Options

```typescript
program
  .option('--api-url <url>', 'PipeForge API base URL', 'http://localhost:3000')
  .option('--json', 'Output raw JSON instead of formatted tables')
  .option('-v, --verbose', 'Enable verbose logging');

// Access parsed options
const opts = program.opts<{ apiUrl: string; json: boolean; verbose: boolean }>();
```

### Subcommands

```typescript
// pipeforge run <pipeline-id> [--env staging]
program
  .command('run <pipelineId>')
  .description('Trigger a pipeline run')
  .option('-e, --env <environment>', 'Target environment', 'production')
  .option('--dry-run', 'Validate pipeline without executing')
  .action(async (pipelineId: string, opts: { env: string; dryRun: boolean }) => {
    const { apiUrl, json } = program.opts<{ apiUrl: string; json: boolean }>();
    await runCommand({ pipelineId, env: opts.env, dryRun: opts.dryRun, apiUrl, json });
  });

// pipeforge list [--status running]
program
  .command('list')
  .description('List pipelines')
  .option('-s, --status <status>', 'Filter by status (ACTIVE|INACTIVE)')
  .action(async (opts: { status?: string }) => {
    await listCommand(opts);
  });

// pipeforge logs <jobId> [--follow]
program
  .command('logs <jobId>')
  .description('Stream logs for a job')
  .option('-f, --follow', 'Keep streaming new log entries')
  .action(async (jobId: string, opts: { follow: boolean }) => {
    await logsCommand({ jobId, follow: opts.follow });
  });
```

### Input Validation in Commands

```typescript
import { z } from 'zod';

const runOptionsSchema = z.object({
  pipelineId: z.string().uuid('Pipeline ID must be a UUID'),
  env: z.enum(['production', 'staging', 'development']),
});

async function runCommand(rawOpts: unknown) {
  const result = runOptionsSchema.safeParse(rawOpts);
  if (!result.success) {
    console.error('Invalid options:');
    result.error.errors.forEach((e) => console.error(`  ${e.path.join('.')}: ${e.message}`));
    process.exit(1);
  }
  const opts = result.data;
  // ... proceed with validated opts
}
```

### Custom Output Formatting

```typescript
import chalk from 'chalk';

function printTable(headers: string[], rows: string[][]): void {
  const widths = headers.map((h, i) =>
    Math.max(h.length, ...rows.map((r) => (r[i] ?? '').length)),
  );

  const fmt = (row: string[]) =>
    row.map((cell, i) => cell.padEnd(widths[i])).join('  ');

  console.log(chalk.bold(fmt(headers)));
  console.log(widths.map((w) => '─'.repeat(w)).join('──'));
  rows.forEach((r) => console.log(fmt(r)));
}

// pipeforge list output:
// ID                                    NAME         STATUS
// ──────────────────────────────────────────────────────────
// 3f2504e0-4f89-11d3-9a0c-0305e82c3301  ETL Daily    ACTIVE
```

---

## Try It Yourself

**Exercise:** Add a `pipeforge status <jobId>` subcommand that:
1. Fetches `GET /api/jobs/:id` from the API
2. Prints `STATUS: RUNNING | DONE | FAILED` in colour (green/blue/red)
3. Exits with code `1` if the job failed

```typescript
program
  .command('status <jobId>')
  .description('Check the status of a job')
  .action(async (jobId: string) => {
    // TODO
  });
```

<details>
<summary>Show solution</summary>

```typescript
import chalk from 'chalk';

program
  .command('status <jobId>')
  .description('Check the status of a job')
  .action(async (jobId: string) => {
    const { apiUrl } = program.opts<{ apiUrl: string }>();

    const res = await fetch(`${apiUrl}/api/jobs/${jobId}`);
    if (!res.ok) {
      console.error(chalk.red(`Error: ${res.status} ${res.statusText}`));
      process.exit(1);
    }

    const job = await res.json() as { id: string; status: string };
    const colour =
      job.status === 'RUNNING' ? chalk.blue :
      job.status === 'DONE'    ? chalk.green :
                                 chalk.red;

    console.log(`Job ${job.id}: ${colour(job.status)}`);

    if (job.status === 'FAILED') process.exit(1);
  });
```

</details>

---

## Capstone Connection

The full PipeForge CLI lives in `src/cli/index.ts`. By the end of this module it has six subcommands: `run`, `list`, `logs`, `status`, `import`, and `init`. The CLI talks to the Express API (Module 06) using the built-in `fetch` API.
