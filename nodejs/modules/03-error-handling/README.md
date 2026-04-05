# Module 3 — Error Handling

## Overview

Error handling is the gap between code that works in demos and code that works in production. Node.js has multiple error channels (thrown exceptions, rejected Promises, emitted error events, operating system signals) and most developers only handle one or two of them. This module covers the complete error taxonomy, structured error hierarchies, shutdown patterns, and production-grade error logging.

---

## Learning Objectives

By the end of this module you will be able to:

- [ ] Name all error channels in Node.js and handle each correctly
- [ ] Design a structured **error hierarchy** with custom error classes carrying metadata
- [ ] Distinguish operational errors from programmer errors and handle them differently
- [ ] Write **Express error middleware** that converts errors to well-formed HTTP responses
- [ ] Implement **graceful shutdown** that drains in-flight requests before exiting
- [ ] Configure structured logging with context (correlation IDs, user IDs)

---

## Topics

| # | File | Concept |
|---|------|---------|
| 1 | [01-error-taxonomy.md](01-error-taxonomy.md) | Error channels, operational vs programmer errors |
| 2 | [02-custom-errors.md](02-custom-errors.md) | Custom error classes, error hierarchies, metadata |
| 3 | [03-async-errors.md](03-async-errors.md) | Unhandled rejections, try/catch in async code |
| 4 | [04-express-error-handling.md](04-express-error-handling.md) | Error middleware, HTTP error mapping, validation |
| 5 | [05-graceful-shutdown.md](05-graceful-shutdown.md) | SIGTERM, connection draining, cleanup ordering |

---

## Estimated Time

**4–5 hours** (including exercises)

---

## Prerequisites

- Module 01 — Node.js Internals
- Module 02 — Async Programming

---

## Capstone Milestone

By the end of this module you will implement PipeForge's **error infrastructure**:

- A custom error hierarchy (`PipeForgeError → NotFoundError, ValidationError, JobError`)
- Express error middleware that formats all errors as consistent JSON responses
- Graceful shutdown handler that waits for running jobs to finish before exiting
- Structured error logging with job and request context

See [exercises/README.md](exercises/README.md) for the step-by-step tasks.
