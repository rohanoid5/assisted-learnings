# Module 10 — Capstone Integration

## Overview

You've built StoreForge incrementally across nine modules. This final module ties everything together. You'll review the complete schema, run a production readiness checklist, document the operational runbook, and verify that all pieces work end-to-end.

This module is primarily practical — it's a guided review, not new content. Use the reference files in `capstone/storeforge/` as a completed baseline to compare against your work.

---

## Learning Objectives

By the end of this module, you should be able to:

- [ ] Run the complete StoreForge schema from scratch with a single SQL file
- [ ] Populate it with seed data and verify all constraints hold
- [ ] Run all stored functions and triggers and verify their behavior
- [ ] Confirm all security roles and RLS policies work as intended
- [ ] Verify the index strategy using `EXPLAIN (ANALYZE, BUFFERS)` on key queries
- [ ] Execute a full backup and restore cycle
- [ ] Document a production deployment checklist specific to StoreForge

---

## Capstone Reference Files

The complete StoreForge implementation is in [`../../capstone/storeforge/`](../../capstone/storeforge/):

| File | Contents |
|------|----------|
| [`schema.sql`](../../capstone/storeforge/schema.sql) | Complete DDL — all tables, types, constraints, triggers, views |
| [`seed.sql`](../../capstone/storeforge/seed.sql) | Realistic sample data — 5 categories, 50 products, 100 customers, 200+ orders |
| [`functions.sql`](../../capstone/storeforge/functions.sql) | All stored functions, procedures, and trigger functions |
| [`security.sql`](../../capstone/storeforge/security.sql) | Roles, grants, RLS policies |
| [`indexes.sql`](../../capstone/storeforge/indexes.sql) | Complete optimized index set with documentation |

---

## Production Readiness Checklist

Work through each item and verify it against your StoreForge database:

### Schema & Data Integrity
- [ ] All foreign keys defined and constraints verified with test data
- [ ] `NOT NULL` on all required columns
- [ ] `CHECK` constraints on status enums and numeric ranges
- [ ] `UNIQUE` constraints on natural keys (e.g., `customer.email`, `product.slug`)
- [ ] `audit_log` table receiving entries on product changes
- [ ] Inventory trigger preventing overselling

### Security
- [ ] No application connection uses the `postgres` superuser
- [ ] RLS policies tested with `SET ROLE` to confirm customers only see their own orders/reviews
- [ ] `pg_hba.conf` requires `scram-sha-256` for all remote connections
- [ ] SSL enforced (`ssl = on`, `sslmode=require` in connection string)
- [ ] Passwords not hardcoded (use environment variables in connection strings)

### Performance
- [ ] All foreign key columns indexed
- [ ] Full-text search using GIN on `product.search_vector`
- [ ] JSONB attributes indexed with GIN for attribute filtering
- [ ] `order` table partitioned by month
- [ ] `EXPLAIN (ANALYZE, BUFFERS)` shows no sequential scans on large tables for key queries
- [ ] `shared_buffers` set to 25% of server RAM
- [ ] `autovacuum` enabled (default) with reasonable thresholds

### Operations
- [ ] `pg_dump` backup script in place, tested restore to a fresh container
- [ ] Streaming replica running, lag monitored via `pg_stat_replication`
- [ ] PgBouncer in front, `max_client_conn` > `max_connections` on Postgres side
- [ ] `pg_stat_statements` extension enabled
- [ ] Slow query threshold (`log_min_duration_statement`) set to log queries > 1s
- [ ] `pg_stat_user_tables` shows no tables with extremely high `n_dead_tup`

---

## Final Architecture Diagram

```
                    ┌─────────────────────────────────┐
                    │         Applications             │
                    │  (API server, analytics, admin)  │
                    └──────────────┬──────────────────┘
                                   │ TCP :6432
                    ┌──────────────▼──────────────────┐
                    │           PgBouncer              │
                    │    (transaction pool mode)       │
                    └──────────────┬──────────────────┘
                                   │ TCP :5432
          ┌────────────────────────▼────────────────────────┐
          │              PostgreSQL Primary                   │
          │        storeforge_dev  (15+)                     │
          │  ┌──────────────────────────────────────────┐   │
          │  │  storeforge schema:                      │   │
          │  │  customer, category, product, address,   │   │
          │  │  order, order_item, review, audit_log    │   │
          │  └──────────────────────────────────────────┘   │
          └────────────────────────┬────────────────────────┘
                     WAL streaming │
          ┌────────────────────────▼────────────────────────┐
          │              PostgreSQL Replica                   │
          │          (hot standby — read queries)            │
          └─────────────────────────────────────────────────┘
```

---

## Next Steps

Congratulations on completing the PostgreSQL Interactive Tutorial. Here are natural next steps:

| Topic | Resource |
|-------|---------|
| **Advanced query analysis** | [explain.dalibo.com](https://explain.dalibo.com) — paste EXPLAIN output for visualization |
| **pgBadger** | Log analysis tool — generates detailed reports from PostgreSQL logs |
| **TimescaleDB** | PostgreSQL extension for time-series data (excellent for the analytics module) |
| **PostGIS** | PostgreSQL extension for geographic data — adds geometry types and spatial indexes |
| **Citus** | Distributed PostgreSQL — horizontal sharding for multi-tenant workloads |
| **Flyway / Liquibase** | Database migration tools for managing schema changes in application codebases |
| **pgvector** | Vector similarity search extension — growing fast with AI/embedding workloads |

Related roadmaps: [roadmap.sh/postgresql-dba](https://roadmap.sh/postgresql-dba) | [roadmap.sh/backend](https://roadmap.sh/backend)
