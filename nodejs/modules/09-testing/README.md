# Module 9 — Testing

## Overview

Production Node.js applications require a multi-layered testing strategy: fast unit tests for pure logic, integration tests for database and HTTP interactions, and end-to-end tests for critical user journeys. This module uses Node.js's built-in test runner (`node:test`) alongside Vitest for watch mode and snapshot testing, plus Supertest for HTTP testing without starting a real server.

---

## Learning Objectives

By the end of this module you will be able to:

- [ ] Write **unit tests** for pure functions and classes using `node:test` or Vitest
- [ ] Write **integration tests** for Express routes using Supertest
- [ ] **Mock external dependencies** (database, fetch, EventEmitter) using `vi.mock` and `vi.spyOn`
- [ ] Test **async code** correctly (promises, streams, EventEmitter events)
- [ ] Measure and enforce **test coverage** with `--experimental-coverage`
- [ ] Set up **test database isolation** using transactions or schema reset

---

## Topics

| # | File | Concept |
|---|------|---------|
| 1 | [01-testing-fundamentals.md](01-testing-fundamentals.md) | node:test, Vitest, test structure |
| 2 | [02-unit-testing.md](02-unit-testing.md) | Pure functions, classes, error cases |
| 3 | [03-integration-testing.md](03-integration-testing.md) | Supertest, database integration |
| 4 | [04-mocking.md](04-mocking.md) | vi.mock, vi.spyOn, stub patterns |
| 5 | [05-test-coverage.md](05-test-coverage.md) | Coverage reports, thresholds, CI |

---

## Estimated Time

**5–6 hours** (including exercises)

---

## Prerequisites

- Module 03 — Error Handling (testing error paths)
- Module 06 — Networking & HTTP (API to test)
- Module 08 — Databases (test database setup)

---

## Capstone Milestone

By the end of this module:

- Unit tests for `PipelineEngine`, error hierarchy, and retry logic
- Integration tests for all API routes using Supertest + test database
- Mocked external services (webhooks, email) in integration tests
- Coverage ≥ 70% enforced in CI

See [exercises/README.md](exercises/README.md) for the step-by-step tasks.
