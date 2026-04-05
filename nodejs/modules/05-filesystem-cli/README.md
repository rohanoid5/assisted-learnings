# Module 5 — File System & CLI

## Overview

Node.js provides a rich file system API and is an excellent platform for building CLI tools. This module covers the full `fs/promises` API (read, write, watch, traverse), building production-quality CLIs with Commander.js, managing environment configuration with dotenv and Zod, and working with `stdin`/`stdout` for interactive terminal programs.

---

## Learning Objectives

By the end of this module you will be able to:

- [ ] Use the full `fs/promises` API for common file operations (read, write, stat, walk, watch)
- [ ] Implement **recursive directory traversal** efficiently using async generators
- [ ] Build a production-quality **CLI with Commander.js** (subcommands, options, validation)
- [ ] Manage **environment configuration** safely using dotenv + Zod schema validation
- [ ] Handle **`stdin`/`stdout`** for interactive prompts and progress reporting
- [ ] Use `path` module correctly for **cross-platform path resolution**

---

## Topics

| # | File | Concept |
|---|------|---------|
| 1 | [01-fs-promises.md](01-fs-promises.md) | fs/promises — reading, writing, stat, walking |
| 2 | [02-file-watching.md](02-file-watching.md) | fs.watch, chokidar, responding to file changes |
| 3 | [03-path-resolution.md](03-path-resolution.md) | path, import.meta.url, cross-platform gotchas |
| 4 | [04-commander-cli.md](04-commander-cli.md) | Commander.js — subcommands, options, validation |
| 5 | [05-env-config.md](05-env-config.md) | dotenv, Zod schema, environment validation |
| 6 | [06-stdin-stdout.md](06-stdin-stdout.md) | readline, ora progress, chalk styling |

---

## Estimated Time

**4–6 hours** (including exercises)

---

## Prerequisites

- Module 01 — Node.js Internals (module system, built-in modules)
- Module 04 — Streams & Buffers (for streaming file operations)

---

## Capstone Milestone

By the end of this module you will have:

- A complete `pipeforge` CLI with `run`, `list`, `logs`, `import`, and `status` subcommands
- A Zod-validated environment configuration module that validates `DATABASE_URL`, `JWT_SECRET`, `PORT`, and `WORKER_POOL_SIZE` on startup
- A `--watch` mode that monitors a pipeline definition directory and re-runs pipelines on change

See [exercises/README.md](exercises/README.md) for the step-by-step tasks.
