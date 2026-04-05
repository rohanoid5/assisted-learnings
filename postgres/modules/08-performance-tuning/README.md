# Module 08 — Performance Tuning & Indexing

## Overview

PostgreSQL is fast by default, but "fast by default" is not the same as "fast under your specific workload." This module teaches you to measure before you optimize, read query plans, choose the right index type, tune memory parameters, avoid bloat, and partition large tables.

The workflow is always: measure → understand → change → measure again. Never guess; always `EXPLAIN`.

---

## Learning Objectives

By the end of this module, you should be able to:

- [ ] Tune key `postgresql.conf` parameters for your server's memory and workload
- [ ] Read `EXPLAIN` and `EXPLAIN (ANALYZE, BUFFERS)` output and identify bottlenecks
- [ ] Choose between B-Tree, Hash, GiST, GIN, SP-GiST, and BRIN indexes for different use cases
- [ ] Create partial indexes, expression indexes, and covering indexes
- [ ] Configure autovacuum and understand dead tuple accumulation and table bloat
- [ ] Rewrite slow queries by avoiding common anti-patterns
- [ ] Partition a large table by range, list, or hash and verify partition pruning is working

---

## Topics

| # | File | Concept |
|---|------|---------|
| 1 | [01-postgresql-conf.md](01-postgresql-conf.md) | Key parameters, where to find the file, reload vs restart, `pg_settings` |
| 2 | [02-resource-usage.md](02-resource-usage.md) | `shared_buffers`, `work_mem`, `effective_cache_size`, `maintenance_work_mem`, `max_connections` |
| 3 | [03-explain-analyze.md](03-explain-analyze.md) | Reading query plans: nodes, cost, rows, actual time, buffers; common patterns and what they mean |
| 4 | [04-indexes.md](04-indexes.md) | B-Tree, Hash, GIN (JSONB/arrays/full-text), GiST (ranges/geometry), BRIN (time-series), SP-GiST |
| 5 | [05-vacuums.md](05-vacuums.md) | MVCC dead tuples, autovacuum, `pg_stat_user_tables`, bloat detection and remediation |
| 6 | [06-query-optimization.md](06-query-optimization.md) | Anti-patterns (SELECT *, functions in WHERE, implicit casts), rewriting strategies |
| 7 | [07-partitioning.md](07-partitioning.md) | Declarative partitioning: RANGE, LIST, HASH; partition pruning; partition-wise operations |

---

## Estimated Time

**5–7 hours** (including exercises)

---

## Prerequisites

- Modules 03–05 completed — full StoreForge schema with meaningful data volume

---

## Capstone Milestone

By the end of this module you should have:

1. **Tuned `postgresql.conf`** for a development machine (set `shared_buffers`, `work_mem`, `effective_cache_size` appropriately)
2. **Analyzed** the top-5 slowest StoreForge queries with `EXPLAIN (ANALYZE, BUFFERS)` and documented the bottleneck in each
3. **Created indexes** with verified improvement (before/after actual time comparison):
   - B-Tree on `order.customer_id`, `order_item.order_id`, `order_item.product_id`
   - GIN index on `product.attributes` (JSONB) for attribute filtering
   - GIN index on `product.search_vector` for full-text search
   - Partial index on `order.status` for pending orders only
4. **Partitioned `order`** by range on `created_at` (monthly partitions for the last 2 years)
5. **Verified partition pruning** with `EXPLAIN` showing only relevant partitions scanned
