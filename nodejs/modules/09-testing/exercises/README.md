# Module 9 — Exercises

## Overview

These exercises build out PipeForge's test suite — unit tests for core logic, integration tests for the API, and CI coverage enforcement.

---

## Exercise 1 — Unit Test the Circuit Breaker

**Goal:** Achieve 100% branch coverage on `CircuitBreaker`.

Write tests for all three states: `CLOSED` (passes calls through), `OPEN` (rejects immediately), `HALF_OPEN` (allows one probe, reopens on failure, closes on success).

```typescript
// src/domain/circuit-breaker.test.ts
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { CircuitBreaker } from './circuit-breaker.js';

describe('CircuitBreaker', () => {
  // Your tests here
});
```

---

## Exercise 2 — Integration Test: Pipeline CRUD

**Goal:** Write integration tests covering the full pipeline lifecycle.

```typescript
// src/api/routes/pipelines.route.test.ts

// Test cases:
// - POST /pipelines → 201 with valid body
// - POST /pipelines → 401 without auth token
// - POST /pipelines → 422 with invalid body
// - GET  /pipelines → 200, returns only caller's pipelines
// - GET  /pipelines/:id → 404 for non-existent ID
// - DELETE /pipelines/:id → 204, re-GET returns 404
```

---

## Exercise 3 — Mock the DB for Service Unit Tests

**Goal:** Test `PipelineService.listPipelines()` without hitting the database.

```typescript
// Mock db.pipeline.findMany
// Assert pagination parameters are passed correctly
// Assert cursor-based pagination applies the correct where clause when cursor is provided
```

---

## Exercise 4 — Enforce 70% Coverage in CI

**Goal:** Ensure the CI pipeline fails when coverage drops below threshold.

1. Set thresholds in `vitest.config.ts` (`lines: 70, branches: 70, functions: 70, statements: 70`)
2. Run `npx vitest run --coverage` — it should pass
3. Add an empty `/* istanbul ignore */` to a critical function and verify coverage drops
4. Fix it by removing the ignore comment

---

## Capstone Checkpoint ✅

Before moving to Module 10, verify:

- [ ] `CircuitBreaker` has 100% branch coverage
- [ ] Pipeline CRUD endpoints covered by integration tests
- [ ] At least 3 service functions covered with mocked DB
- [ ] `npx vitest run --coverage` exits with code 0 (thresholds met)
- [ ] HTML coverage report opens at `coverage/index.html`
