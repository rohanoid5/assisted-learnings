# Node.js Advanced Interactive Tutorial

A hands-on, modular Node.js learning guide for experienced **JavaScript / TypeScript / Node.js** developers who want to go deeper — understanding internals, design patterns, and production-grade techniques. Every concept is taught with a "you've used this — here's how it actually works" approach, digging into V8, libuv, and the event loop rather than surface-level syntax.

---

## How to Use This Tutorial

1. Work through modules **in order** — each builds on the previous.
2. After each concept, **apply it to the capstone project** (PipeForge) using the _Capstone Connection_ section at the bottom of every topic file.
3. Complete the **exercises** before moving to the next module.
4. The `capstone/pipeforge/` folder holds your working project — built incrementally.

> You already know Node.js. This tutorial is about understanding *why* it works the way it does and *how* to use it at a professional level.

---

## Prerequisites

| Requirement    | Version | Notes                                                             |
|----------------|---------|-------------------------------------------------------------------|
| Node.js        | 20+     | LTS; use [nvm](https://github.com/nvm-sh/nvm) to manage versions |
| npm            | 10+     | Bundled with Node.js                                              |
| TypeScript     | 5+      | `npm install -g typescript`                                       |
| PostgreSQL     | 15+     | Needed from Module 8 onwards; Docker recommended                  |
| Docker         | Latest  | For PostgreSQL, test containers, and Module 11 deployment         |
| VS Code        | Latest  | With the Node.js debugger configured                              |

> **Assumed knowledge:** ES2020+, TypeScript basics (types, interfaces, generics), async/await, Express.js fundamentals, npm. If you're comfortable building REST APIs in Node.js, you're ready.

---

## Learning Path

| Module | Topic | Est. Time | Capstone Milestone |
|--------|-------|-----------|-------------------|
| [01 — Node.js Internals](modules/01-internals/) | V8, libuv, event loop, module systems, npm | 4–6 hrs | PipeForge project scaffolded |
| [02 — Async Programming](modules/02-async-programming/) | Callbacks, Promises, async/await, EventEmitter, concurrency | 5–7 hrs | Async pipeline execution engine |
| [03 — Error Handling & Debugging](modules/03-error-handling/) | Error types, strategies, async errors, stack traces, debugging | 3–5 hrs | Custom error hierarchy + debug config |
| [04 — Streams & Buffers](modules/04-streams-buffers/) | Buffers, Readable/Writable/Transform streams, backpressure | 4–6 hrs | Stream-based file processing pipeline |
| [05 — File System & CLI](modules/05-filesystem-cli/) | fs, path, glob, commander, environment variables | 3–5 hrs | `pipeforge` CLI tool |
| [06 — Networking & HTTP](modules/06-networking-http/) | http internals, Express deep dive, REST, WebSockets, JWT auth | 5–7 hrs | REST API + WebSocket job progress |
| [07 — Design Patterns](modules/07-design-patterns/) | GoF patterns, middleware, plugin architecture, DI containers | 5–7 hrs | Plugin system + strategy pattern |
| [08 — Databases](modules/08-databases/) | Native drivers, Knex, Prisma, Drizzle, connection pooling | 4–6 hrs | PostgreSQL persistence + migrations |
| [09 — Testing](modules/09-testing/) | node:test, Vitest/Jest, mocking, integration, E2E | 3–5 hrs | Full test suite for PipeForge |
| [10 — Process & Performance](modules/10-process-performance/) | Child processes, worker threads, cluster, logging, GC, profiling | 5–7 hrs | Worker thread pool + PM2 + profiling |
| [11 — Capstone Integration](modules/11-capstone-integration/) | Docker, security hardening, CI/CD, production deployment | 3–5 hrs | Production-ready PipeForge |

**Total estimated time: 44–66 hours**

---

## Capstone Project: PipeForge

PipeForge is a **data pipeline & job processing system** — a backend platform for defining processing pipelines (sequences of transformation steps), triggering jobs against them, and streaming real-time progress. It exercises every advanced Node.js concept in the tutorial.

### Domain Model

```
User ──creates──▶ Pipeline ──has──▶ Step (ordered)
                    │
                    └──triggers──▶ Job ──produces──▶ JobLog
                                   │
                                   └──status: PENDING → RUNNING → COMPLETED/FAILED
```

### Entities

| Entity     | Key Fields                                                                             |
|------------|----------------------------------------------------------------------------------------|
| `User`     | id, username, email, passwordHash, role (`ADMIN`/`USER`), createdAt                   |
| `Pipeline` | id, name, description, ownerId, status (`ACTIVE`/`PAUSED`/`ARCHIVED`), createdAt      |
| `Step`     | id, pipelineId, name, type (`TRANSFORM`/`FILTER`/`AGGREGATE`/`CUSTOM`), config (JSON), order |
| `Job`      | id, pipelineId, status (`PENDING`/`RUNNING`/`COMPLETED`/`FAILED`), input, output, progress, startedAt, completedAt, error |
| `JobLog`   | id, jobId, level (`INFO`/`WARN`/`ERROR`), message, timestamp, metadata (JSON)         |

### What Gets Built Module-by-Module

| Module | What Gets Added to PipeForge |
|--------|-------------------------------|
| 01     | Project scaffolding, TypeScript + ESM config, npm scripts, workspace layout |
| 02     | Async pipeline execution engine, EventEmitter-based job lifecycle |
| 03     | Custom error hierarchy (`PipeForgeError`, `JobError`, `ValidationError`), global handler |
| 04     | Stream-based file processing step (read → transform → write with backpressure) |
| 05     | `pipeforge` CLI (`create`, `run`, `status`, `logs` commands), dotenv config loading |
| 06     | Express REST API (pipelines/jobs CRUD), WebSocket job progress, JWT authentication |
| 07     | Step type plugin system, DI container, middleware chain for request processing |
| 08     | PostgreSQL + Prisma schema, migrations, job history, audit queries |
| 09     | Unit tests (core engine), integration tests (API + DB), E2E tests (CLI) |
| 10     | Worker thread pool for parallel job execution, PM2 config, Pino structured logging |
| 11     | Multi-stage Dockerfile, Docker Compose, security hardening, GitHub Actions CI |

---

## Project Structure

```
nodejs/
├── README.md                               ← You are here
├── roadmap.png                             ← Visual learning roadmap
├── .github/
│   └── prompts/
│       └── plan-nodejsInteractiveTutorial.prompt.md
├── capstone/
│   └── pipeforge/                          ← Your working project
│       ├── package.json
│       ├── tsconfig.json
│       ├── .env.example
│       ├── src/
│       │   ├── cli/                        ← CLI entry point (Module 05)
│       │   ├── api/                        ← Express API (Module 06)
│       │   ├── core/                       ← Pipeline engine (Module 02)
│       │   ├── errors/                     ← Error hierarchy (Module 03)
│       │   ├── plugins/                    ← Plugin system (Module 07)
│       │   ├── db/                         ← Prisma schema + client (Module 08)
│       │   └── workers/                    ← Worker thread scripts (Module 10)
│       └── tests/
│           ├── unit/
│           ├── integration/
│           └── e2e/
└── modules/
    ├── 01-internals/
    ├── 02-async-programming/
    ├── 03-error-handling/
    ├── 04-streams-buffers/
    ├── 05-filesystem-cli/
    ├── 06-networking-http/
    ├── 07-design-patterns/
    ├── 08-databases/
    ├── 09-testing/
    ├── 10-process-performance/
    └── 11-capstone-integration/
```

---

## Quick Start

```bash
# Clone / navigate to the nodejs folder
cd nodejs/capstone/pipeforge

# Install dependencies
npm install

# Copy environment config
cp .env.example .env

# Run in development mode
npm run dev

# Run the CLI
npm run cli -- --help
```

---

## Node.js Version Reference

This tutorial uses **Node.js 20 LTS** (Iron). Features assumed throughout:

| Feature | Available Since |
|---------|----------------|
| Built-in `fetch` | Node.js 18 |
| Built-in `node:test` runner | Node.js 18 |
| `--watch` mode | Node.js 18 |
| Web Streams API | Node.js 18 |
| Top-level `await` in ESM | Node.js 14.8+ |
| Worker Threads stable | Node.js 12+ |
| `fs/promises` stable | Node.js 14+ |
| `AbortController` | Node.js 15+ |

---

## Related Tutorials in This Workspace

| Tutorial | Relevance |
|----------|-----------|
| [Spring Boot](../spring-boot/) | Parallel Java ecosystem learning — DI, IoC, MVC patterns map to Node.js equivalents covered here |
| [PostgreSQL](../postgres/) | Database module (08-databases) uses PostgreSQL and Prisma; prior PostgreSQL knowledge helps |
