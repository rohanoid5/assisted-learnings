# Module 2 — Asynchronous Programming

## Overview

Async programming is Node.js's core value proposition. But it's also the source of most subtle bugs: race conditions, unhandled rejections, improper cancellation, and misused concurrency primitives. This module takes you from "I know how to `await`" to "I understand what happens under the hood and can build reliable concurrent systems."

You'll work through the full evolution: callbacks → Promises → async/await → advanced patterns — with an emphasis on real-world failure modes and how to avoid them.

---

## Learning Objectives

By the end of this module you will be able to:

- [ ] Explain why callbacks were the original async mechanism and their key limitations
- [ ] Describe the **Promise state machine** (pending → fulfilled/rejected) and how chaining works
- [ ] Use all **Promise combinators** (`all`, `allSettled`, `race`, `any`) and know when to choose each
- [ ] Write clean **async/await** code that handles errors correctly and avoids starvation
- [ ] Implement **cancellation** using `AbortController` and `AbortSignal`
- [ ] Choose between sequential, parallel, and batched async execution appropriately
- [ ] Use **async generators** and `for await...of` to process async sequences

---

## Topics

| # | File | Concept |
|---|------|---------|
| 1 | [01-callbacks.md](01-callbacks.md) | Callback pattern, error-first convention, callback hell |
| 2 | [02-promises.md](02-promises.md) | Promise state machine, chaining, error propagation |
| 3 | [03-async-await.md](03-async-await.md) | async/await, try/catch patterns, common mistakes |
| 4 | [04-promise-combinators.md](04-promise-combinators.md) | all, allSettled, race, any — when and why |
| 5 | [05-async-iteration.md](05-async-iteration.md) | Async generators, for await...of, async iterables |
| 6 | [06-cancellation.md](06-cancellation.md) | AbortController, AbortSignal, timeout patterns |

---

## Estimated Time

**5–7 hours** (including exercises)

---

## Prerequisites

- Module 01 — Node.js Internals (especially Event Loop)
- JavaScript Promises and basic async/await

---

## Capstone Milestone

By the end of this module you will implement PipeForge's **job execution engine**:

- A `JobRunner` class that executes pipeline steps sequentially with async/await
- Parallel step execution for independent steps using `Promise.allSettled`
- Job cancellation via `AbortController` propagated through all step executions
- A retry mechanism using Promise composition

See [exercises/README.md](exercises/README.md) for the step-by-step tasks.
