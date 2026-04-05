# 3.2 — Custom Error Classes

## Concept

Custom error classes give you typed, structured errors that carry metadata — HTTP status codes, error codes, contextual data — without resorting to parsing error message strings. They make error handling code explicit and testable.

---

## Deep Dive

### Extending Error Correctly

```typescript
// The correct way to extend Error in TypeScript with ES2022+
export class AppError extends Error {
  constructor(
    message: string,
    public readonly code: string,
    public readonly statusCode: number,
    public readonly context?: Record<string, unknown>,
  ) {
    super(message);
    this.name = this.constructor.name;  // ← important for err.name to reflect the subclass
    // stack trace cleanup: remove this constructor from the stack
    if (Error.captureStackTrace) {
      Error.captureStackTrace(this, this.constructor);
    }
  }
}
```

**Why `this.name = this.constructor.name`?** Without it, `err.name` is always `'Error'` for all subclasses. With it, you get `'NotFoundError'`, `'ValidationError'`, etc. — useful in logs and error serialization.

### A Complete Error Hierarchy for PipeForge

```typescript
// src/errors/index.ts

export class PipeForgeError extends Error {
  constructor(
    message: string,
    public readonly code: string,
    public readonly statusCode: number,
    public readonly context?: Record<string, unknown>,
    public readonly isOperational: boolean = true,
  ) {
    super(message);
    this.name = this.constructor.name;
    if (Error.captureStackTrace) {
      Error.captureStackTrace(this, this.constructor);
    }
  }
}

// 4xx — Client errors (operational)
export class NotFoundError extends PipeForgeError {
  constructor(resource: string, id?: string) {
    super(
      id ? `${resource} with id '${id}' not found` : `${resource} not found`,
      'NOT_FOUND',
      404,
      { resource, id },
    );
  }
}

export class ValidationError extends PipeForgeError {
  constructor(
    message: string,
    public readonly fields?: Record<string, string[]>,
  ) {
    super(message, 'VALIDATION_ERROR', 422, { fields });
  }
}

export class UnauthorizedError extends PipeForgeError {
  constructor(message = 'Authentication required') {
    super(message, 'UNAUTHORIZED', 401);
  }
}

export class ForbiddenError extends PipeForgeError {
  constructor(message = 'Insufficient permissions') {
    super(message, 'FORBIDDEN', 403);
  }
}

export class ConflictError extends PipeForgeError {
  constructor(resource: string, message?: string) {
    super(
      message ?? `${resource} already exists`,
      'CONFLICT',
      409,
      { resource },
    );
  }
}

// 5xx — Server errors
export class JobError extends PipeForgeError {
  constructor(
    message: string,
    public readonly jobId: string,
    public readonly stepId?: string,
    cause?: Error,
  ) {
    super(message, 'JOB_ERROR', 500, { jobId, stepId }, true);
    if (cause) this.cause = cause;
  }
}

export class DatabaseError extends PipeForgeError {
  constructor(message: string, cause?: Error) {
    super(message, 'DATABASE_ERROR', 503, undefined, true);
    if (cause) this.cause = cause;
    this.isOperational = false; // DB errors may indicate programmer error
  }
}
```

### Using Type Guards for Error Handling

```typescript
// Type guard — narrows the type for TypeScript
export function isPipeForgeError(err: unknown): err is PipeForgeError {
  return err instanceof PipeForgeError;
}

export function isNotFoundError(err: unknown): err is NotFoundError {
  return err instanceof NotFoundError;
}

// Usage in route handlers
async function getPipelineHandler(req: Request, res: Response) {
  try {
    const pipeline = await db.pipeline.findUniqueOrThrow({
      where: { id: req.params.id }
    });
    res.json(pipeline);
  } catch (err) {
    if (err instanceof Prisma.NotFoundError) {
      throw new NotFoundError('Pipeline', req.params.id); // convert to our type
    }
    throw err; // unexpected — let error middleware handle it
  }
}
```

---

## Try It Yourself

**Exercise:** Add a `RateLimitError` and a `StepTimeoutError` to the PipeForge error hierarchy.

```typescript
// RateLimitError: 429 Too Many Requests
// Should include: retryAfterSeconds (how long to wait before retrying)

// StepTimeoutError: subclass of JobError
// Should include: jobId, stepId, timeoutMs
```

<details>
<summary>Show solution</summary>

```typescript
export class RateLimitError extends PipeForgeError {
  constructor(public readonly retryAfterSeconds: number) {
    super(
      `Rate limit exceeded. Retry after ${retryAfterSeconds} seconds`,
      'RATE_LIMIT_EXCEEDED',
      429,
      { retryAfterSeconds },
    );
  }
}

export class StepTimeoutError extends JobError {
  constructor(jobId: string, stepId: string, timeoutMs: number) {
    super(
      `Step timed out after ${timeoutMs}ms`,
      jobId,
      stepId,
    );
    Object.assign(this.context ?? {}, { timeoutMs });
    this.code = 'STEP_TIMEOUT'; // override parent code
  }
}
```

</details>

---

## Capstone Connection

The PipeForge error hierarchy in `src/errors/index.ts` is the single source of truth for all error types. Every API route, job runner, and plugin uses these classes — the Express error middleware inspects `statusCode` and `code` for HTTP responses, the logger uses `context` for structured fields, and the job runner uses `isOperational` to decide whether to retry or fail fast.
