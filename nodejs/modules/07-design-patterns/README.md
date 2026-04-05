# Module 7 — Design Patterns in Node.js

## Overview

Design patterns are reusable solutions to common software design problems. In Node.js, many patterns from the Gang of Four (GoF) book manifest differently due to the dynamic nature of JavaScript — and some patterns (middleware chain, plugin architecture, event-based observer) are so idiomatic to Node.js that they've become invisible parts of the ecosystem. This module makes those implicit patterns explicit.

---

## Learning Objectives

By the end of this module you will be able to:

- [ ] Apply the **Factory** and **Builder** patterns to create complex objects cleanly
- [ ] Implement the **Singleton** pattern correctly in ESM (module caching)
- [ ] Use the **Observer/EventEmitter** pattern for decoupled event-driven architecture
- [ ] Build a **Plugin architecture** with a hook system for extensible applications
- [ ] Implement the **Middleware pattern** from scratch (the same way Express does it)
- [ ] Apply **Dependency Injection** in a Node.js context for testability

---

## Topics

| # | File | Concept |
|---|------|---------|
| 1 | [01-factory-builder.md](01-factory-builder.md) | Factory functions, Builder pattern |
| 2 | [02-singleton.md](02-singleton.md) | Singleton via ESM module caching |
| 3 | [03-observer-eventemitter.md](03-observer-eventemitter.md) | EventEmitter, typed events, custom emitters |
| 4 | [04-middleware-pattern.md](04-middleware-pattern.md) | Middleware chain from scratch |
| 5 | [05-plugin-architecture.md](05-plugin-architecture.md) | Plugin registry, hooks, lifecycle |
| 6 | [06-dependency-injection.md](06-dependency-injection.md) | Manual DI container, testability |

---

## Estimated Time

**5–7 hours** (including exercises)

---

## Prerequisites

- Module 02 — Async Programming (async middleware chains)
- Module 06 — Networking & HTTP (Express middleware as an example)

---

## Capstone Milestone

By the end of this module you will have refactored PipeForge to:

- Use a typed EventEmitter for the `PipelineEngine` (replacing loose `engine.on()` calls)
- Implement a Plugin system where pipeline step types (`CSV_IMPORT`, `WEBHOOK`, etc.) are registered as plugins
- Apply dependency injection to the engine and router for testability

See [exercises/README.md](exercises/README.md) for the step-by-step tasks.
