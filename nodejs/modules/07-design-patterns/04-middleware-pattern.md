# 7.4 — Middleware Pattern

## Concept

The middleware pattern is one of the most powerful architectural patterns in Node.js. Express uses it. Koa uses it. Many CLI tools use it. It's a chain of functions where each function receives context, does work, and optionally passes control to the next function. Implementing it from scratch demystifies what every Node.js framework does under the hood.

---

## Deep Dive

### Implementing a Middleware Chain from Scratch

```typescript
type Middleware<T> = (ctx: T, next: () => Promise<void>) => Promise<void>;

class Pipeline<T> {
  private middlewares: Middleware<T>[] = [];

  use(fn: Middleware<T>): this {
    this.middlewares.push(fn);
    return this;
  }

  async run(ctx: T): Promise<void> {
    const dispatch = async (index: number): Promise<void> => {
      if (index >= this.middlewares.length) return; // end of chain
      const fn = this.middlewares[index];
      await fn(ctx, () => dispatch(index + 1));
    };
    await dispatch(0);
  }
}
```

### Using the Pipeline

```typescript
interface StepContext {
  jobId: string;
  stepName: string;
  input: unknown;
  output?: unknown;
  startedAt?: Date;
  duration?: number;
}

const pipeline = new Pipeline<StepContext>();

// Timing middleware
pipeline.use(async (ctx, next) => {
  ctx.startedAt = new Date();
  await next();
  ctx.duration = Date.now() - ctx.startedAt.getTime();
  console.log(`${ctx.stepName} took ${ctx.duration}ms`);
});

// Logging middleware
pipeline.use(async (ctx, next) => {
  console.log(`Starting step: ${ctx.stepName}`);
  try {
    await next();
    console.log(`Step done: ${ctx.stepName}`);
  } catch (err) {
    console.error(`Step failed: ${ctx.stepName}`, err);
    throw err;
  }
});

// The actual step handler
pipeline.use(async (ctx) => {
  ctx.output = await executeStep(ctx.stepName, ctx.input);
});

// Run
await pipeline.run({ jobId: 'abc', stepName: 'extract', input: null });
```

### Koa's "Onion" Model

Koa's middleware runs "in and out" — before next() and after next() are both executed:

```
Middleware 1 (before)
  Middleware 2 (before)
    Middleware 3 (before)
    Handler
    Middleware 3 (after) ← runs on the way out
  Middleware 2 (after)
Middleware 1 (after)
```

This is why response timing, compression, and caching middleware work in Koa without callbacks.

---

## Try It Yourself

**Exercise:** Add a `retry` middleware to the step pipeline that retries on error up to N times:

```typescript
function retryMiddleware(maxAttempts: number): Middleware<StepContext> {
  return async (ctx, next) => {
    // TODO: retry next() up to maxAttempts times with 1s delay between
  };
}
```

<details>
<summary>Show solution</summary>

```typescript
function retryMiddleware(maxAttempts: number): Middleware<StepContext> {
  return async (ctx, next) => {
    let lastError: Error | undefined;
    for (let attempt = 1; attempt <= maxAttempts; attempt++) {
      try {
        await next();
        return; // success — done
      } catch (err) {
        lastError = err as Error;
        if (attempt < maxAttempts) {
          console.warn(`Step ${ctx.stepName} failed (attempt ${attempt}), retrying...`);
          await new Promise((r) => setTimeout(r, 1000 * attempt));
        }
      }
    }
    throw lastError!;
  };
}
```

</details>

---

## Capstone Connection

PipeForge's `PipelineEngine` uses a middleware pipeline for step execution — every step runs through: `retryMiddleware(3)` → `timingMiddleware` → `loggingMiddleware` → actual step handler. Adding a new cross-cutting concern (e.g., tracing) is a one-line `pipeline.use(tracingMiddleware)` addition.
