# Module 7 — Exercises

## Overview

These exercises refactor PipeForge to use the patterns from this module.

---

## Exercise 1 — Typed EventEmitter

**Goal:** Add typed `emit`/`on`/`once` overloads to `PipelineEngine`.

```typescript
// src/core/pipeline-engine.ts
interface EngineEvents {
  'job:started':  [jobId: string];
  'job:progress': [jobId: string, step: string, percent: number];
  'job:done':     [jobId: string, result: Record<string, unknown>];
  'job:failed':   [jobId: string, error: Error];
}

class PipelineEngine extends EventEmitter {
  // Override emit, on, once with typed signatures
}
```

Verify: TypeScript should error if you try to `engine.emit('job:done', 'id', 123)` (wrong arg type).

---

## Exercise 2 — Plugin Registry

**Goal:** Implement the plugin registry and wire it into the engine.

1. Create `src/plugins/registry.ts`
2. Create `src/plugins/built-in/csv-import.ts`
3. Create `src/plugins/built-in/webhook.ts`
4. Create `src/plugins/built-in/delay.ts`
5. Import all plugins in `src/plugins/index.ts`
6. Update `PipelineEngine.executeStep` to use `registry.get(step.type).handler(step.config)`

---

## Exercise 3 — Step Middleware Pipeline

**Goal:** Add timing and retry middleware to step execution.

```typescript
// In PipelineEngine.executeJob:
const stepPipeline = new Pipeline<StepContext>()
  .use(retryMiddleware(3))
  .use(timingMiddleware)
  .use(loggingMiddleware)
  .use(pluginMiddleware(registry)); // calls the plugin handler

await stepPipeline.run({ jobId, stepName, input });
```

---

## Exercise 4 — DI Refactor

**Goal:** Refactor `JobController` and `PipelineController` to use constructor DI.

Update `src/container.ts` to wire up the controllers with injected dependencies. Verify the code still works for the API routes.

---

## Capstone Checkpoint ✅

Before moving to Module 8, verify:

- [ ] `PipelineEngine` emits typed events — TypeScript rejects wrong argument types
- [ ] Plugin registry has at least 3 step types registered: `CSV_IMPORT`, `WEBHOOK`, `DELAY`
- [ ] Adding a new step type requires zero changes to the engine
- [ ] Step execution runs through timing and logging middleware
- [ ] Controllers use constructor DI (no direct singleton imports)
