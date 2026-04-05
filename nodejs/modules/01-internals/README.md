# Module 1 — Node.js Internals & Architecture

## Overview

Before diving into async patterns, streams, or design patterns, you need to understand **what Node.js actually is under the hood** — not just "JavaScript on the server." This module examines V8, libuv, the event loop, and the module system. Everything in later modules depends on these mental models.

If you've been writing Node.js for years without thinking about event loop phases, microtask queues, or CommonJS caching — this module will reframe how you reason about your code.

---

## Learning Objectives

By the end of this module you will be able to:

- [ ] Explain the role of **V8** and **libuv** in Node.js, and how they interact
- [ ] Describe **non-blocking I/O** and why Node.js can handle concurrency with a single thread
- [ ] Trace a piece of code through the **event loop phases** (timers, poll, check, close), including where microtasks fit
- [ ] Explain the difference between **CommonJS** (`require`) and **ESM** (`import`), and the tradeoffs of each
- [ ] Describe how CommonJS **module caching** and **circular dependency** resolution work
- [ ] Use **npm workspaces**, semantic versioning, and understand lockfile mechanics
- [ ] Know when and how to reach for key **built-in modules** (os, util, crypto, url, zlib)

---

## Topics

| # | File | Concept |
|---|------|---------|
| 1 | [01-what-is-nodejs.md](01-what-is-nodejs.md) | V8 engine, libuv, single-threaded architecture, non-blocking I/O |
| 2 | [02-nodejs-vs-browser.md](02-nodejs-vs-browser.md) | Global objects, Node.js-specific APIs, security model differences |
| 3 | [03-event-loop.md](03-event-loop.md) | Event loop phases, microtasks vs macrotasks, starvation |
| 4 | [04-module-systems.md](04-module-systems.md) | CommonJS internals, ESM static analysis, interop between the two |
| 5 | [05-npm-advanced.md](05-npm-advanced.md) | Semantic versioning, lockfiles, workspaces, creating & publishing packages |
| 6 | [06-built-in-modules.md](06-built-in-modules.md) | os, util, url, crypto, zlib, dns — practical reference |

---

## Estimated Time

**4–6 hours** (including exercises)

---

## Prerequisites

- JavaScript ES2020+ (destructuring, optional chaining, nullish coalescing, Promise combinators)
- TypeScript basics (types, interfaces, generics)
- Basic command-line familiarity

---

## Capstone Milestone

By the end of this module you will **bootstrap the PipeForge project**:

- Initialize the project with ESM support (`"type": "module"` in `package.json`)
- Configure TypeScript (`tsconfig.json`) for modern Node.js
- Set up npm scripts for development, build, and test
- Scaffold the directory structure using knowledge of Node.js module resolution
- Create a `src/core/pipeline-engine.ts` stub using the module system correctly

See [exercises/README.md](exercises/README.md) for the step-by-step tasks.
