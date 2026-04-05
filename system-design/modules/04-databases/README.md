# Module 04 — Databases & Storage

## Overview

Databases are the state layer of every distributed system. Good database design determines whether ScaleForge can serve 10,000 redirects per second or grind to a halt under load. This module covers the full spectrum — from choosing the right storage technology, to query optimization, to scaling beyond a single Postgres instance.

## Learning Objectives

By the end of this module you will be able to:

- [ ] Explain the differences between relational, document, key-value, and time-series databases and choose appropriately
- [ ] Write indexes in PostgreSQL and validate they are used with `EXPLAIN ANALYZE`
- [ ] Identify and fix N+1 query problems
- [ ] Implement a read replica routing strategy in the connection pool
- [ ] Describe horizontal sharding strategies and their tradeoffs
- [ ] Use migrations for zero-downtime schema changes
- [ ] Set up PgBouncer to handle connection pressure from scaled horizontally app

## Topics

| # | Topic | Est. Time |
|---|-------|-----------|
| 01 | [Relational vs NoSQL vs NewSQL](01-relational-vs-nosql.md) | 45 min |
| 02 | [Indexes and Query Optimization](02-indexes-and-query-optimization.md) | 60 min |
| 03 | [Replication and Read Replicas](03-replication-and-read-replicas.md) | 45 min |
| 04 | [Sharding Strategies](04-sharding-strategies.md) | 45 min |
| 05 | [Schema Migrations](05-schema-migrations.md) | 30 min |
| 06 | [Connection Pooling with PgBouncer](06-connection-pooling-pgbouncer.md) | 30 min |
| Exercises | [Hands-on exercises](exercises/README.md) | 3–4 hrs |

**Total estimated time:** 5–7 hours

## Prerequisites

- Module 01 — Foundations (consistency, CAP theorem)
- Module 03 — Load Balancing (connection pool behaviour under replica scaling)

## Capstone Milestone

By the end of Module 04, ScaleForge's database layer will be production-grade:

```
                        ┌─────────────────┐
                        │  App Replicas   │
                        │  (×3 or more)   │
                        └────────┬────────┘
                                 │ max 10 conn per replica
                                 ▼
                        ┌─────────────────┐
                        │   PgBouncer     │  ← connection pooler
                        │  (transaction   │     multiplexes 30 app
                        │   mode)         │     connections into 10
                        └────────┬────────┘     Postgres connections
                                 │
               ┌─────────────────┼─────────────────┐
               │                 │                 │
               ▼                 ▼                 ▼
     ┌──────────────────┐        │       ┌──────────────────┐
     │  Postgres Primary│        │       │  Postgres Replica│
     │  (writes)        │────────┘       │  (reads)         │
     └──────────────────┘  streaming     └──────────────────┘
                           replication
                           
App routing:
  Reads (GET /api/v1/urls, stats):  → replica pool
  Writes (POST /api/v1/urls, PATCH): → primary pool
```

Validations:
- `EXPLAIN ANALYZE` confirms index scan (not seq scan) for code lookups
- Zero N+1 queries in URL listing endpoint
- Schema migration applied with zero downtime (additive-only changes)
- PgBouncer in transaction mode → 3 app replicas × 10 connections → 10 Postgres connections
