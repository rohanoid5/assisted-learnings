# 5.6 — stdin, stdout, and Terminal UX

## Concept

Command-line tools live and die by their terminal UX. Reading interactive input with `readline`, showing progress spinners with `ora`, applying colour with `chalk`, and writing structured output — these transform a script into a tool people actually enjoy using.

---

## Deep Dive

### Reading from stdin

```typescript
import { createInterface } from 'node:readline';

// Interactive prompt
async function prompt(question: string): Promise<string> {
  const rl = createInterface({
    input: process.stdin,
    output: process.stdout,
  });

  return new Promise<string>((resolve) => {
    rl.question(question, (answer) => {
      rl.close();
      resolve(answer.trim());
    });
  });
}

const name = await prompt('Pipeline name: ');
const confirm = await prompt(`Create pipeline "${name}"? [y/N] `);
if (confirm.toLowerCase() !== 'y') process.exit(0);
```

### Reading Lines from stdin (piped input)

```typescript
import { createInterface } from 'node:readline';

// pipeforge import < pipelines.csv
async function readStdin(): Promise<string[]> {
  const rl = createInterface({ input: process.stdin });
  const lines: string[] = [];
  for await (const line of rl) {
    lines.push(line);
  }
  return lines;
}

// Detection: is stdin a TTY (interactive) or a pipe?
if (process.stdin.isTTY) {
  // Interactive mode
} else {
  // Piped / redirected input
  const lines = await readStdin();
}
```

### Progress Spinners with `ora`

```typescript
import ora from 'ora';

const spinner = ora('Running pipeline...').start();

try {
  await runPipeline(pipelineId);
  spinner.succeed('Pipeline completed'); // ✔ Pipeline completed
} catch (err) {
  spinner.fail(`Pipeline failed: ${err.message}`); // ✖ Pipeline failed: ...
}

// Dynamic text updates
spinner.text = 'Connecting to database...';
await db.$connect();
spinner.text = 'Executing step 1 of 3...';
await runStep(1);
```

### Colour with `chalk`

```typescript
import chalk from 'chalk';

// Status colours
const STATUS_COLOUR = {
  RUNNING: chalk.blue,
  DONE:    chalk.green,
  FAILED:  chalk.red,
  PENDING: chalk.yellow,
} as const;

function printStatus(status: keyof typeof STATUS_COLOUR): void {
  console.log(STATUS_COLOUR[status](status));
}

// Structured error output
console.error(chalk.red.bold('Error:'), chalk.red(err.message));
if (verbose) {
  console.error(chalk.dim(err.stack));
}
```

### Detecting Output Context

```typescript
// Disable colour when output is piped or redirected
const useColour = process.stdout.isTTY; // false when piped

// chalk respects NO_COLOR env var and FORCE_COLOR automatically
// For JSON output flag:
if (program.opts<{ json: boolean }>().json) {
  process.stdout.write(JSON.stringify(result, null, 2) + '\n');
} else {
  printTable(result);
}
```

---

## Try It Yourself

**Exercise:** Implement `pipeforge init` which interactively prompts for pipeline name and steps, writes a `pipeline.json` file, and shows a spinner while calling the API:

```typescript
async function initCommand(): Promise<void> {
  const name = await prompt(chalk.cyan('Pipeline name: '));
  const stepsInput = await prompt(chalk.cyan('Step names (comma-separated): '));
  const steps = stepsInput.split(',').map((s) => s.trim()).filter(Boolean);

  const spinner = ora('Creating pipeline...').start();

  try {
    const res = await fetch(`${apiUrl}/api/pipelines`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ name, steps }),
    });
    const pipeline = await res.json();
    // TODO: write pipeline.json to CWD, show success
  } catch (err) {
    // TODO: spinner.fail(...)
  }
}
```

<details>
<summary>Show solution</summary>

```typescript
import { writeFile } from 'node:fs/promises';
import { join } from 'node:path';
import ora from 'ora';
import chalk from 'chalk';

async function initCommand(): Promise<void> {
  const name = await prompt(chalk.cyan('Pipeline name: '));
  const stepsInput = await prompt(chalk.cyan('Step names (comma-separated): '));
  const steps = stepsInput.split(',').map((s) => s.trim()).filter(Boolean);

  const spinner = ora('Creating pipeline...').start();

  try {
    const res = await fetch(`${apiUrl}/api/pipelines`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ name, steps }),
    });

    if (!res.ok) throw new Error(`API error: ${res.status}`);
    const pipeline = await res.json() as { id: string };

    await writeFile(
      join(process.cwd(), 'pipeline.json'),
      JSON.stringify({ id: pipeline.id, name, steps }, null, 2),
    );

    spinner.succeed(chalk.green(`Pipeline "${name}" created (${pipeline.id})`));
  } catch (err) {
    spinner.fail(chalk.red((err as Error).message));
    process.exit(1);
  }
}
```

</details>

---

## Capstone Connection

Every PipeForge CLI command uses this pattern: `ora` for progress while waiting for the API, `chalk` for status colouring, and JSON mode for scripted/CI usage. The `pipeforge logs --follow` command streams NDJSON from the API and writes lines to stdout in real time.
