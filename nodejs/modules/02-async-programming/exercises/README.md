# Module 2 — Exercises

## Overview

These exercises build PipeForge's core job execution engine. By the end, you'll have a working `JobRunner` that executes pipeline steps with proper async control flow, error handling, parallelism, and cancellation.

---

## Exercise 1 — Sequential Job Step Executor

**Goal:** Implement step-by-step job execution using async/await.

```typescript
// src/core/job-runner.ts

import type { Job, Step } from '@prisma/client';

interface StepResult {
  stepId: string;
  output: Record<string, unknown>;
  durationMs: number;
}

export class JobRunner {
  // Execute steps sequentially, passing each step's output as the next step's input
  async runStepsSequentially(
    steps: Step[],
    signal: AbortSignal,
  ): Promise<StepResult[]> {
    const results: StepResult[] = [];
    let context: Record<string, unknown> = {};

    for (const step of steps) {
      if (signal.aborted) throw new Error('Job cancelled');

      const start = Date.now();
      const output = await this.executeStep(step, context, signal);
      context = { ...context, ...output };

      results.push({
        stepId: step.id,
        output,
        durationMs: Date.now() - start,
      });
    }

    return results;
  }

  private async executeStep(
    step: Step,
    context: Record<string, unknown>,
    signal: AbortSignal,
  ): Promise<Record<string, unknown>> {
    // TODO: implement based on step.type
    // For now, simulate with a sleep
    await new Promise<void>((resolve, reject) => {
      const timer = setTimeout(resolve, 100);
      signal.addEventListener('abort', () => {
        clearTimeout(timer);
        reject(new Error('Step cancelled'));
      });
    });
    return { [`${step.name}_result`]: 'ok' };
  }
}
```

Test it:
```bash
node --loader ts-node/esm -e "
import { JobRunner } from './src/core/job-runner.js';
const runner = new JobRunner();
const controller = new AbortController();
const results = await runner.runStepsSequentially(mockSteps, controller.signal);
console.log(results);
"
```

---

## Exercise 2 — Retry Mechanism

**Goal:** Add retry logic with exponential backoff.

```typescript
// Add to JobRunner class:

async withRetry<T>(
  fn: () => Promise<T>,
  maxAttempts: number,
  baseDelayMs: number,
  signal: AbortSignal,
): Promise<T> {
  // TODO: retry up to maxAttempts times
  // Delay between attempts: baseDelayMs * 2^(attempt - 1) (exponential backoff)
  // Respect the AbortSignal — stop retrying if aborted
  // Re-throw the final error if all attempts fail
}
```

<details>
<summary>Show solution</summary>

```typescript
async withRetry<T>(
  fn: () => Promise<T>,
  maxAttempts: number,
  baseDelayMs: number,
  signal: AbortSignal,
): Promise<T> {
  let lastError: Error;

  for (let attempt = 1; attempt <= maxAttempts; attempt++) {
    if (signal.aborted) throw new Error('Retry cancelled');

    try {
      return await fn();
    } catch (err) {
      lastError = err as Error;

      if (attempt < maxAttempts) {
        const delayMs = baseDelayMs * Math.pow(2, attempt - 1);
        await new Promise<void>((resolve, reject) => {
          const timer = setTimeout(resolve, delayMs);
          signal.addEventListener('abort', () => {
            clearTimeout(timer);
            reject(new Error('Retry sleep cancelled'));
          });
        });
      }
    }
  }

  throw lastError!;
}
```

</details>

---

## Exercise 3 — Parallel Stage Execution

**Goal:** Run independent steps in parallel using `Promise.allSettled`.

Add a method that groups steps by their `order` field and runs each group in parallel:

```typescript
// Steps with the same 'order' value can run in parallel
async runStagesParallel(steps: Step[], signal: AbortSignal): Promise<StepResult[]> {
  // TODO:
  // 1. Group steps by step.order
  // 2. For each group (in order), run all steps with Promise.allSettled
  // 3. Collect results — failed steps should not stop the other steps in the same group
  // 4. Return all results in order
}
```

<details>
<summary>Show solution</summary>

```typescript
async runStagesParallel(steps: Step[], signal: AbortSignal): Promise<StepResult[]> {
  // Group by order
  const groups = new Map<number, Step[]>();
  for (const step of steps) {
    const order = step.order ?? 0;
    if (!groups.has(order)) groups.set(order, []);
    groups.get(order)!.push(step);
  }

  const allResults: StepResult[] = [];
  const orders = [...groups.keys()].sort((a, b) => a - b);

  for (const order of orders) {
    if (signal.aborted) throw new Error('Job cancelled');

    const groupSteps = groups.get(order)!;
    const settled = await Promise.allSettled(
      groupSteps.map((step) => this.executeStep(step, {}, signal).then(
        (output) => ({ stepId: step.id, output, durationMs: 0 } as StepResult)
      ))
    );

    for (const result of settled) {
      if (result.status === 'fulfilled') allResults.push(result.value);
      else console.error('Step failed:', result.reason);
    }
  }

  return allResults;
}
```

</details>

---

## Exercise 4 — Job Cancellation via AbortController

**Goal:** Implement the `cancelJob` API endpoint and the engine's cancellation plumbing.

Update `src/core/pipeline-engine.ts`:
```typescript
// Add a Map to track running jobs' AbortControllers
// Add a cancelJob(jobId) method
// Modify runJob to register/deregister the controller
```

Then update `src/api/server.ts`:
```typescript
// Add: DELETE /api/jobs/:id
// Call engine.cancelJob(req.params.id)
// Return 200 if cancelled, 404 if not found
```

---

## Capstone Checkpoint ✅

Before moving to Module 3, verify:

- [ ] Can explain why `Promise.all` fails fast but `Promise.allSettled` doesn't
- [ ] Can identify and fix accidentally sequential code (awaiting inside a loop)
- [ ] Has implemented the `JobRunner` with sequential execution, retry, and parallel stages
- [ ] Can use `AbortController` to cancel running async operations
- [ ] Understands `for await...of` and async generators
- [ ] The `DELETE /api/jobs/:id` endpoint correctly cancels a running job
