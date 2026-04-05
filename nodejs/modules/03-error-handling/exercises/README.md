# Module 3 — Exercises

## Overview

These exercises build PipeForge's complete error infrastructure: error classes, Express middleware, process-level handlers, and graceful shutdown.

---

## Exercise 1 — Implement the Error Hierarchy

**Goal:** Complete `src/errors/index.ts` with the full PipeForge error hierarchy.

Based on the design in Topic 3.2, ensure you have:
- `PipeForgeError` base class with `code`, `statusCode`, `context`, `isOperational`
- `NotFoundError` (404)
- `ValidationError` (422) with `fields`
- `UnauthorizedError` (401)
- `ForbiddenError` (403)
- `ConflictError` (409)
- `JobError` (500) with `jobId`, `stepId`

Verify each class serializes correctly:
```typescript
const err = new NotFoundError('Pipeline', 'pipe-123');
console.log(err.name);        // 'NotFoundError'
console.log(err.statusCode);  // 404
console.log(err.code);        // 'NOT_FOUND'
console.log(err.message);     // "Pipeline with id 'pipe-123' not found"
console.log(err instanceof PipeForgeError); // true
console.log(err instanceof NotFoundError);  // true
```

---

## Exercise 2 — asyncHandler + Error Middleware

**Goal:** Wire error handling into the Express server.

1. Add `asyncHandler` wrapper to `src/api/server.ts`
2. Create `src/api/middleware/error-handler.ts` using the template from Topic 3.4
3. Update the health route and add a test route that throws:

```typescript
app.get('/api/test-error', asyncHandler(async (req, res) => {
  const type = req.query.type as string;

  if (type === 'not-found') throw new NotFoundError('TestResource', 'abc');
  if (type === 'validation') throw new ValidationError('Bad input', { name: ['required'] });
  if (type === 'unexpected') throw new Error('Something totally unexpected');

  res.json({ ok: true });
}));
```

Verify each error response shape:
```bash
curl "http://localhost:3000/api/test-error?type=not-found"
# {"error":{"code":"NOT_FOUND","message":"TestResource with id 'abc' not found","details":{"resource":"TestResource","id":"abc"}}}

curl "http://localhost:3000/api/test-error?type=unexpected"
# {"error":{"code":"INTERNAL_ERROR","message":"An unexpected error occurred"}}
# Stack trace should NOT appear in response (logged internally only)
```

---

## Exercise 3 — Process-Level Error Handlers

**Goal:** Register all five error channels in `src/api/server.ts`.

```typescript
// Register these handlers before starting the server:

// 1. Uncaught exceptions
process.on('uncaughtException', (err) => { /* log and exit */ });

// 2. Unhandled rejections
process.on('unhandledRejection', (reason) => { /* log and exit */ });

// 3. Test the uncaught exception handler works:
//    Temporarily add this AFTER the handler registration:
//    setTimeout(() => { throw new Error('test uncaught'); }, 2000);
//    Start the server, observe the log output, verify process exits with code 1
```

---

## Exercise 4 — Graceful Shutdown

**Goal:** Implement the shutdown sequence from Topic 3.5.

1. Add `cancelAll()` and `waitForAll()` methods to `PipelineEngine`
2. Create the `createGracefulShutdown` helper
3. Call it after starting the HTTP server in `src/api/server.ts`

Test it:
```bash
npm run dev &
sleep 2
# Start a long-running job (if you have one)
kill -TERM %1
# Observe: server logs "Shutdown signal received", then jobs finish, then exits 0
```

---

## Capstone Checkpoint ✅

Before moving to Module 4, verify:

- [ ] All five error channels are handled in PipeForge's server setup
- [ ] Custom error hierarchy is implemented with correct `name`, `statusCode`, `code`, `instanceof` behavior
- [ ] Express error middleware returns consistent JSON for all error types
- [ ] Stack traces never appear in HTTP responses (only in server logs)
- [ ] Graceful shutdown correctly waits for in-flight requests before exiting
- [ ] `kill -TERM` on the process results in exit code 0, not 1
