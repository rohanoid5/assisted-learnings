# 3.4 — Express Error Handling

## Concept

Express has a specific error handling API: 4-argument middleware `(err, req, res, next)`. If you don't implement it correctly, errors either leak stack traces to clients or crash the server. A proper error middleware converts all errors — operational and unexpected — into well-structured JSON responses.

---

## Deep Dive

### The Error Middleware Signature

```typescript
import type { Request, Response, NextFunction, ErrorRequestHandler } from 'express';

// MUST have exactly 4 parameters — express detects this signature
const errorHandler: ErrorRequestHandler = (err, req, res, next) => {
  // ...
};

// Register LAST, after all routes
app.use(errorHandler);
```

### Triggering Error Middleware

```typescript
// Method 1: Call next(err) — works in sync and async middleware
app.get('/jobs/:id', (req, res, next) => {
  try {
    const job = getJob(req.params.id);
    res.json(job);
  } catch (err) {
    next(err); // passes to error middleware
  }
});

// Method 2: Throw inside async route (requires try/catch or wrapper)
// Express 5 handles async throws natively, but Express 4 needs a wrapper:
function asyncHandler(fn: (req: Request, res: Response, next: NextFunction) => Promise<void>) {
  return (req: Request, res: Response, next: NextFunction) => {
    Promise.resolve(fn(req, res, next)).catch(next);
  };
}

app.get('/jobs/:id', asyncHandler(async (req, res) => {
  const job = await db.job.findUniqueOrThrow({ where: { id: req.params.id } });
  res.json(job);
  // If findUniqueOrThrow throws, the wrapper catches it and calls next(err)
}));
```

### Production Error Middleware

```typescript
// src/api/middleware/error-handler.ts
import type { ErrorRequestHandler } from 'express';
import { PipeForgeError } from '../../errors/index.js';
import { logger } from '../../lib/logger.js';

export const errorHandler: ErrorRequestHandler = (err, req, res, _next) => {
  // Known operational error — predictable, client-friendly response
  if (err instanceof PipeForgeError) {
    if (err.statusCode >= 500) {
      logger.error({ err, reqId: req.id }, 'Server error');
    }

    return res.status(err.statusCode).json({
      error: {
        code: err.code,
        message: err.message,
        ...(err.context && { details: err.context }),
      },
    });
  }

  // Zod validation error (from request body parsing)
  if (err.name === 'ZodError') {
    return res.status(422).json({
      error: {
        code: 'VALIDATION_ERROR',
        message: 'Request validation failed',
        details: { fields: err.flatten().fieldErrors },
      },
    });
  }

  // Unknown error — don't expose internals
  logger.error({ err, reqId: req.id }, 'Unexpected error');
  res.status(500).json({
    error: {
      code: 'INTERNAL_ERROR',
      message: 'An unexpected error occurred',
    },
  });
};
```

---

## Try It Yourself

**Exercise:** Implement the `asyncHandler` wrapper and apply it to all PipeForge routes.

Update `src/api/server.ts` to:
1. Create an `asyncHandler` wrapper for async route handlers
2. Wrap all existing routes
3. Register the error middleware from `02-custom-errors.md`

<details>
<summary>Show solution</summary>

```typescript
type AsyncHandler = (req: Request, res: Response, next: NextFunction) => Promise<void>;

export const asyncHandler = (fn: AsyncHandler) =>
  (req: Request, res: Response, next: NextFunction) =>
    Promise.resolve(fn(req, res, next)).catch(next);

// Apply to routes:
app.get('/api/jobs/:id', asyncHandler(async (req, res) => {
  const job = await db.job.findUnique({ where: { id: req.params.id } });
  if (!job) throw new NotFoundError('Job', req.params.id);
  res.json(job);
}));
```

</details>

---

## Capstone Connection

All PipeForge API routes use `asyncHandler`. The centralized error middleware converts every `NotFoundError`, `ValidationError`, and unexpected error into a consistent `{ error: { code, message, details? } }` JSON shape — API clients can always rely on this structure.
