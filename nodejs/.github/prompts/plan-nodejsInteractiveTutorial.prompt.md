# Plan: Node.js Advanced Interactive Tutorial (PipeForge)

Build a modular, markdown-based advanced Node.js tutorial in `/nodejs/` following the exact structure of the Spring Boot and PostgreSQL tutorials. 11 modules covering every topic on the roadmap, with a **"PipeForge"** (data pipeline & job processing system) capstone built incrementally. Foundational + deep-dive approach — covers each topic from fundamentals but always digs into the "why" and internals.

---

## Capstone Project: PipeForge

A data pipeline & job processing system exercising every advanced Node.js concept — built incrementally as each module is completed.

### Domain Model

```
User ──creates──▶ Pipeline ──has──▶ Step (ordered)
                    │
                    └──triggers──▶ Job ──produces──▶ JobLog
```

### Entities

| Entity | Key Fields |
|--------|-----------|
| `User` | id, username, email, passwordHash, role (ADMIN/USER) |
| `Pipeline` | id, name, description, steps config, ownerId, status (ACTIVE/PAUSED/ARCHIVED) |
| `Step` | id, pipelineId, name, type (TRANSFORM/FILTER/AGGREGATE/CUSTOM), config (JSON), order |
| `Job` | id, pipelineId, status (PENDING/RUNNING/COMPLETED/FAILED), input, output, progress, startedAt, completedAt, error |
| `JobLog` | id, jobId, level (INFO/WARN/ERROR), message, timestamp, metadata (JSON) |

### What Gets Built Module-by-Module

| Module | What Gets Added to PipeForge |
|--------|-------------------------------|
| 01 | Project scaffolding, ESM setup, npm workspace, npm scripts |
| 02 | Async pipeline engine core, EventEmitter-based job lifecycle |
| 03 | Custom error hierarchy, error handling middleware, debugging config |
| 04 | Stream-based file reading/transformation/writing pipeline |
| 05 | CLI tool (`pipeforge` CLI), fs-based pipeline config loading |
| 06 | Express REST API, WebSocket job progress, JWT authentication |
| 07 | Plugin system, middleware chain, DI container, strategy pattern for step types |
| 08 | PostgreSQL + Prisma persistence, job history, migrations |
| 09 | Unit, integration, E2E tests for all layers |
| 10 | Worker thread pool, PM2 config, logging, performance profiling |
| 11 | Docker, security hardening, production deployment checklist |

---

## Module Structure

### Module 01 — Node.js Internals & Architecture
**Est. Time:** 4–6 hours | **Capstone Milestone:** PipeForge project scaffolded with ESM, npm scripts, TypeScript config

| # | File | Concept |
|---|------|---------|
| 1 | 01-what-is-nodejs.md | V8 engine, libuv, single-threaded architecture, non-blocking I/O |
| 2 | 02-nodejs-vs-browser.md | Global objects, APIs, security model, module differences |
| 3 | 03-event-loop.md | Event loop phases (timers, pending, idle/prepare, poll, check, close), microtasks vs macrotasks, starvation |
| 4 | 04-module-systems.md | CommonJS internals (require algorithm, caching, circular deps), ESM (static analysis, top-level await, interop) |
| 5 | 05-npm-advanced.md | Semantic versioning, lockfiles, npm workspaces, creating packages, npx |
| 6 | 06-built-in-modules.md | Overview of key built-in modules (os, util, url, crypto, zlib, dns) |

### Module 02 — Async Programming Mastery
**Est. Time:** 5–7 hours | **Capstone Milestone:** Async pipeline execution engine with EventEmitter-based lifecycle

| # | File | Concept |
|---|------|---------|
| 1 | 01-callbacks.md | Error-first convention, callback hell, control flow patterns |
| 2 | 02-promises.md | Promise internals, microtask queue, chaining, combinators (all, allSettled, race, any) |
| 3 | 03-async-await.md | Async function internals, error propagation, sequential vs parallel, top-level await |
| 4 | 04-event-emitter.md | EventEmitter internals, memory leaks, patterns (once, removeListener, error event) |
| 5 | 05-timers.md | setTimeout, setInterval, setImmediate, process.nextTick — execution order & use cases |
| 6 | 06-concurrency-patterns.md | Throttling, batching, semaphores, async queues, backoff strategies |

### Module 03 — Error Handling & Debugging
**Est. Time:** 3–5 hours | **Capstone Milestone:** Custom error hierarchy for PipeForge, global error handling, debug configuration

| # | File | Concept |
|---|------|---------|
| 1 | 01-error-types.md | System errors, user errors, assertion errors, operational vs programmer errors |
| 2 | 02-error-strategies.md | Try/catch patterns, error propagation, error boundaries, graceful degradation |
| 3 | 03-async-errors.md | Unhandled rejections, uncaught exceptions, domain alternatives, AbortController |
| 4 | 04-callstack-traces.md | V8 stack traces, Error.captureStackTrace, async stack traces, source maps |
| 5 | 05-debugging.md | node --inspect, Chrome DevTools, VS Code debugger, conditional breakpoints, logpoints |

### Module 04 — Streams & Buffers
**Est. Time:** 4–6 hours | **Capstone Milestone:** Stream-based file processing pipeline (read → transform → write)

| # | File | Concept |
|---|------|---------|
| 1 | 01-buffers.md | Buffer API, encoding, ArrayBuffer, TypedArrays, binary data handling |
| 2 | 02-readable-streams.md | Readable streams, flowing vs paused mode, async iteration, highWaterMark |
| 3 | 03-writable-streams.md | Writable streams, drain event, cork/uncork, finish vs close |
| 4 | 04-transform-duplex.md | Transform streams, Duplex streams, PassThrough, object mode |
| 5 | 05-backpressure.md | Backpressure mechanics, pipeline(), stream.pipeline(), error handling in pipelines |
| 6 | 06-stream-patterns.md | Composition, multiplexing, forking, real-world patterns (CSV parsing, compression, encryption) |

### Module 05 — File System & CLI Apps
**Est. Time:** 3–5 hours | **Capstone Milestone:** `pipeforge` CLI with pipeline CRUD, file-based config, environment management

| # | File | Concept |
|---|------|---------|
| 1 | 01-fs-module.md | fs promises API, sync vs async, recursive operations, watchers (fs.watch, chokidar) |
| 2 | 02-path-module.md | Path resolution, cross-platform, __dirname/__filename in ESM, import.meta.url |
| 3 | 03-glob-patterns.md | glob, globby, file matching patterns, ignoring patterns |
| 4 | 04-cli-fundamentals.md | process.argv, commander, input (inquirer/prompts), output (chalk, ora), exit codes |
| 5 | 05-environment.md | process.env, dotenv, config management patterns, .env files, validation (envalid) |

### Module 06 — Networking & HTTP
**Est. Time:** 5–7 hours | **Capstone Milestone:** Express REST API for PipeForge with CRUD, WebSocket job progress, JWT auth

| # | File | Concept |
|---|------|---------|
| 1 | 01-net-http-internals.md | TCP with net module, http module internals, request/response lifecycle |
| 2 | 02-express-deep-dive.md | Express architecture, middleware stack, routing, request lifecycle |
| 3 | 03-rest-api-patterns.md | RESTful design, validation (Zod/Joi), DTOs, pagination, error responses |
| 4 | 04-making-api-calls.md | fetch (native), axios, got — patterns, retries, timeouts, interceptors |
| 5 | 05-websockets.md | ws module, Socket.IO, real-time patterns, heartbeat, reconnection |
| 6 | 06-authentication.md | JWT (jsonwebtoken), Passport.js, session vs token auth, refresh tokens, bcrypt |

### Module 07 — Design Patterns
**Est. Time:** 5–7 hours | **Capstone Milestone:** Plugin system, middleware chain, DI container, strategy pattern for step processors

| # | File | Concept |
|---|------|---------|
| 1 | 01-creational-patterns.md | Factory, Abstract Factory, Builder, Singleton (module-level), Dependency Injection |
| 2 | 02-structural-patterns.md | Proxy, Decorator, Adapter, Facade, Composite |
| 3 | 03-behavioral-patterns.md | Observer, Strategy, Chain of Responsibility, Iterator, State, Command |
| 4 | 04-middleware-pattern.md | Express-style middleware, Koa-style onion, generic middleware engines |
| 5 | 05-plugin-architecture.md | Plugin loading, hooks, lifecycle, Fastify-style plugin system, tapable |
| 6 | 06-dependency-injection.md | Manual DI, DI containers (tsyringe, awilix), composition root, testing benefits |

### Module 08 — Working with Databases
**Est. Time:** 4–6 hours | **Capstone Milestone:** Full PostgreSQL persistence with Prisma, migrations, job history queries

| # | File | Concept |
|---|------|---------|
| 1 | 01-native-drivers.md | pg (PostgreSQL), mongodb native driver — connection, queries, prepared statements |
| 2 | 02-query-builders.md | Knex.js — schema building, migrations, query construction, transactions |
| 3 | 03-prisma.md | Prisma schema, client generation, queries, relations, transactions, raw SQL |
| 4 | 04-drizzle-typeorm.md | Drizzle ORM, TypeORM — comparison, when to use which |
| 5 | 05-connection-pooling.md | Pool configuration, connection lifecycle, pg-pool, Prisma pool, health checks |
| 6 | 06-migrations-seeding.md | Migration strategies, Prisma Migrate, Knex migrations, seed data patterns |

### Module 09 — Testing
**Est. Time:** 3–5 hours | **Capstone Milestone:** Full test suite — unit, integration, E2E — for PipeForge

| # | File | Concept |
|---|------|---------|
| 1 | 01-node-test-runner.md | Built-in node:test, describe/it, assertions, test runner CLI, reporters |
| 2 | 02-vitest-jest.md | Vitest & Jest setup, matchers, snapshots, coverage, watch mode |
| 3 | 03-mocking-patterns.md | Module mocking, dependency injection for testability, spies, stubs, fakes |
| 4 | 04-integration-testing.md | Testing HTTP APIs (supertest), database testing (test containers, transactions), fixture management |
| 5 | 05-e2e-testing.md | Playwright & Cypress overview, API E2E testing, test environments, CI integration |

### Module 10 — Process Management & Performance
**Est. Time:** 5–7 hours | **Capstone Milestone:** Worker thread pool, PM2 config, Winston logging, performance profiling

| # | File | Concept |
|---|------|---------|
| 1 | 01-child-processes.md | spawn, exec, execFile, fork — IPC, stdio, signals, use cases |
| 2 | 02-worker-threads.md | Worker API, SharedArrayBuffer, Atomics, MessageChannel, thread pool pattern |
| 3 | 03-cluster-module.md | Cluster for multi-core, sticky sessions, graceful restart, zero-downtime deploys |
| 4 | 04-logging.md | Winston, Pino, Morgan — structured logging, log levels, transports, request ID tracing |
| 5 | 05-pm2.md | PM2 ecosystem, process management, cluster mode, monitoring, log management |
| 6 | 06-memory-gc.md | V8 garbage collection (Scavenge, Mark-Sweep, Mark-Compact), memory leaks, heap snapshots |
| 7 | 07-profiling-apm.md | CPU profiling, flame graphs, --inspect profiling, clinic.js, APM tools (Datadog, New Relic) |

### Module 11 — Capstone Integration
**Est. Time:** 3–5 hours | **Capstone Milestone:** Production-ready PipeForge with Docker, security, deployment checklist

README.md only — ties everything together:
- Dockerfile (multi-stage build)
- Docker Compose (app + PostgreSQL + Redis optional)
- Security hardening (helmet, rate limiting, input sanitization, CORS)
- Graceful shutdown
- Health checks
- CI/CD pipeline (GitHub Actions)
- Production deployment checklist
- Final verification

---

## Directory Structure

```
nodejs/
├── README.md                            # Overview, prerequisites, learning path
├── .github/
│   └── prompts/
│       └── plan-nodejsInteractiveTutorial.prompt.md
├── capstone/
│   └── pipeforge/                       # Working project (built module-by-module)
│       ├── package.json
│       ├── tsconfig.json
│       ├── src/
│       │   ├── cli/                     # CLI entry point
│       │   ├── api/                     # Express API
│       │   ├── core/                    # Pipeline engine
│       │   ├── plugins/                 # Plugin system
│       │   ├── db/                      # Prisma schema & migrations
│       │   └── workers/                 # Worker thread scripts
│       └── tests/
├── modules/
│   ├── 01-internals/
│   │   ├── README.md
│   │   ├── 01-what-is-nodejs.md ... 06-built-in-modules.md
│   │   └── exercises/README.md
│   ├── 02-async-programming/
│   ├── 03-error-handling/
│   ├── 04-streams-buffers/
│   ├── 05-filesystem-cli/
│   ├── 06-networking-http/
│   ├── 07-design-patterns/
│   ├── 08-databases/
│   ├── 09-testing/
│   ├── 10-process-performance/
│   └── 11-capstone-integration/
```

---

## Tutorial Format (per topic file)

Each `.md` file follows this consistent 5-part structure (matching Spring Boot & PostgreSQL patterns):

1. **Concept** — What it is, why it matters, real-world context
2. **Deep Dive** — Technical internals, diagrams (ASCII), comparison tables
3. **Code Examples** — Annotated, runnable TypeScript/JavaScript snippets
4. **Try It Yourself** — Mini-exercise with collapsible `<details>` solution
5. **Capstone Connection** — How to apply this concept in PipeForge

### Markdown Conventions
- Headings: max 3 levels (`#`, `##`, `###`)
- Code blocks: language-labeled (```typescript, ```bash, ```json)
- Solutions: `<details><summary>Show solution</summary>` blocks
- Tables: Topics, comparisons, API references
- ASCII diagrams: architecture, data flow, event loop phases
- Cross-references: relative links to other modules/files
- Checkboxes: `- [ ]` for learning objectives and checkpoints
- Exercise README: 5-6 exercises + `## Capstone Checkpoint ✅` with verification checklist

---

## Pedagogical Approach

- **Audience:** Experienced JS/TS/Node.js developer wanting depth
- **Style:** Foundational coverage + deep dive into internals ("you've used this — here's how it actually works")
- **Analogies:** Compare Node.js internals with browser JS, compare with Go/Java concurrency models, reference Spring Boot patterns where relevant (since user is learning that in parallel)
- **Ratio:** ~30% conceptual / ~70% hands-on code & exercises
- **Progression:** Internals → async mastery → I/O → networking → patterns → data → testing → production

---

## Steps

| Phase | Steps | Dependency |
|-------|-------|------------|
| **1. Scaffold** | Create README.md, plan prompt, all directories, capstone skeleton | Independent |
| **2. Foundation** | Modules 01 → 02 → 03 → 04 → 05 (sequential) | Phase 1 |
| **3. Application** | Modules 06 → 07, 08 (07/08 parallel after 06) → 09 → 10 | Phase 2 |
| **4. Integration** | Module 11 + final capstone skeleton | All above |

## Verification

1. All relative links in README files resolve correctly
2. Every module README has all 6 sections (Overview, Objectives, Topics, Time, Prerequisites, Capstone Milestone)
3. Every topic file has all 5 sections (Concept, Deep Dive, Code Examples, Try It Yourself, Capstone Connection)
4. Every exercise README has solutions in `<details>` blocks + Capstone Checkpoint
5. No forward references to unexplained concepts
6. Code examples are runnable with Node.js 20+ and TypeScript 5+
7. Module progression follows dependency order

## Decisions

- **Node.js 20+** (LTS), **TypeScript** throughout, **ESM** (modern module system)
- **Express.js** as primary HTTP framework (Fastify mentioned for comparison)
- **Prisma** as primary ORM (Drizzle, Knex, TypeORM covered for awareness)
- **PostgreSQL** for database (consistency with postgres tutorial)
- **PipeForge** capstone — data pipeline & job processing system
- Markdown with embedded code blocks (not separate `.ts` files per example)
- Total estimated time: **44–66 hours** across all modules

## Further Considerations

1. **Capstone project skeleton:** Provide a starter skeleton with `package.json` + `tsconfig.json`, let you build along module-by-module.
2. **Module 07 (Design Patterns):** Covers both GoF patterns and Node.js-specific patterns (middleware, plugin architecture, module-level singleton).
3. **Any specific topics you want more/less depth on?** e.g., go deeper on V8 internals, skip template engines, add GraphQL, etc.
