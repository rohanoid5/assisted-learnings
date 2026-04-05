# Module 8 — Databases with Node.js

## Overview

This module covers the full database interaction stack in Node.js: raw SQL with `pg`, query building with Knex, and production ORM usage with Prisma. You'll understand connection pooling, transactions, migrations, N+1 query problems, and database-level optimizations — not just "how to call the ORM".

---

## Learning Objectives

By the end of this module you will be able to:

- [ ] Use Prisma for all CRUD operations, relations, and complex filters
- [ ] Write and manage **database migrations** with Prisma Migrate
- [ ] Identify and fix **N+1 query problems** using eager loading and query logging
- [ ] Use **database transactions** correctly (including nested transactions with savepoints)
- [ ] Configure and tune **connection pooling** (PgBouncer, Prisma's pooling)
- [ ] Use **raw SQL** via Prisma's `$queryRaw` for performance-critical queries

---

## Topics

| # | File | Concept |
|---|------|---------|
| 1 | [01-prisma-orm.md](01-prisma-orm.md) | Prisma client, CRUD, relations, filters |
| 2 | [02-migrations.md](02-migrations.md) | Prisma Migrate, schema evolution |
| 3 | [03-n-plus-one.md](03-n-plus-one.md) | N+1 problem, eager loading, query logging |
| 4 | [04-transactions.md](04-transactions.md) | Transactions, nested transactions, savepoints |
| 5 | [05-connection-pooling.md](05-connection-pooling.md) | PgBouncer, Prisma Data Platform, pool sizing |
| 6 | [06-raw-sql.md](06-raw-sql.md) | $queryRaw, $executeRaw, SQL injection prevention |

---

## Estimated Time

**5–7 hours** (including exercises)

---

## Prerequisites

- Module 05 — File System & CLI (env config for DATABASE_URL)
- Familiarity with SQL (PostgreSQL module in this workspace)

---

## Capstone Milestone

By the end of this module:

- All PipeForge data operations use Prisma with correct transaction handling
- Migrations are tracked in version control
- N+1 queries eliminated in `GET /api/v1/pipelines` (which includes steps)
- Connection pool correctly sized for the worker pool

See [exercises/README.md](exercises/README.md) for the step-by-step tasks.
