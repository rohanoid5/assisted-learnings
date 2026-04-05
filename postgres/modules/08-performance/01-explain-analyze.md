# EXPLAIN and ANALYZE

## Concept

`EXPLAIN` shows the execution plan the query planner chose without running the query. `EXPLAIN ANALYZE` actually runs the query and shows both estimated and actual statistics. Reading these plans is the single most important performance skill in PostgreSQL — every optimisation decision should be driven by plan evidence, not assumptions.

---

## Basic EXPLAIN

```sql
-- Show the plan (estimated only, no execution):
EXPLAIN
SELECT p.name, p.price
FROM product p
WHERE p.category_id = 3 AND p.is_active;

-- Typical output:
-- Seq Scan on product  (cost=0.00..4.50 rows=8 width=36)
--   Filter: (is_active AND (category_id = 3))

-- cost=startup..total  : estimated cost units (not milliseconds)
-- rows                 : estimated row count
-- width                : estimated average row width in bytes
```

---

## EXPLAIN ANALYZE

```sql
-- Run the query and compare estimates vs actuals:
EXPLAIN (ANALYZE, BUFFERS, FORMAT TEXT)
SELECT p.name, p.price
FROM product p
WHERE p.category_id = 3 AND p.is_active;

-- Typical output:
-- Seq Scan on product  (cost=0.00..4.50 rows=8 width=36)
--                      (actual time=0.015..0.022 rows=6 loops=1)
--   Filter: (is_active AND (category_id = 3))
--   Rows Removed by Filter: 44
--   Buffers: shared hit=2
-- Planning Time: 0.3 ms
-- Execution Time: 0.1 ms

-- Key fields:
-- actual time=x..y : startup ms .. total ms
-- rows             : how many rows were actually returned
-- loops            : how many times this node ran (>1 with nested loops)
-- Buffers shared hit : pages from shared_buffers (fast)
-- Buffers shared read: pages read from disk (slow)
```

---

## Scan Types

```sql
-- Sequential Scan: reads every row on every page.
-- Good for: small tables or fetching >10% of rows.
-- Bad for: large tables with selective filters.

EXPLAIN SELECT * FROM product;
-- Seq Scan on product (cost=0.00..2.50 rows=50 width=...)

-- Index Scan: traverses a B-tree index, then fetches heap pages.
-- Good for: highly selective queries (few rows returned).
EXPLAIN SELECT * FROM product WHERE id = 1;
-- Index Scan using product_pkey on product (cost=0.14..8.16 rows=1 ...)

-- Index Only Scan: all needed columns are in the index (covering index).
-- No heap fetch at all — fastest for covered queries.
EXPLAIN SELECT id FROM product WHERE is_active ORDER BY id;
-- Index Only Scan using product_pkey ...

-- Bitmap Index Scan + Bitmap Heap Scan:
-- Builds a bitmap of matching pages, then fetches in page order.
-- Good for: medium selectivity, multiple indexes combined (BitmapAnd/BitmapOr).
EXPLAIN SELECT * FROM product WHERE category_id = 3 AND price < 100;
```

---

## Join Types

```sql
-- Nested Loop: for small outer sets + indexed inner lookups.
EXPLAIN ANALYZE
SELECT o.id, c.name
FROM "order" o JOIN customer c ON c.id = o.customer_id
WHERE o.status = 'pending' AND o.total_amount > 500;

-- Hash Join: builds a hash table on the smaller relation.
-- Common when no useful index exists on the join column.
EXPLAIN ANALYZE
SELECT p.name, SUM(oi.quantity) AS sold
FROM product p JOIN order_item oi ON oi.product_id = p.id
GROUP BY p.id, p.name
ORDER BY sold DESC;

-- Merge Join: requires both sides sorted or sort nodes inserted.
-- Good for large, already-sorted datasets.

-- Reading the indentation:
-- Each level of indentation is a child node feeding the parent.
-- Total execution time = sum of all leaf node actual times (considering loops).
```

---

## EXPLAIN Options

```sql
-- Most useful combination:
EXPLAIN (ANALYZE, BUFFERS, VERBOSE, FORMAT JSON)
SELECT ...;

-- FORMAT JSON: machine-readable, paste into https://explain.dalibo.com or pganalyze
-- BUFFERS: shows cache hits vs disk reads (needs ANALYZE)
-- VERBOSE: shows output columns and schema-qualified names
-- TIMING OFF: removes per-node timing overhead for very fast queries
-- SUMMARY: shows Planning + Execution summary line (default on in ANALYZE)

-- Quick shorthand:
EXPLAIN (ANALYZE, BUFFERS)
SELECT ...;
```

---

## Common Red Flags in Plans

| Warning Sign | Meaning | Fix |
|---|---|---|
| `Seq Scan` on large table | Missing or unused index | Add appropriate index |
| Estimated rows ≪ actual rows | Stale statistics | `ANALYZE tablename` |
| `rows=1000` actual=`50000` | Poor estimate → wrong join type | `ANALYZE`; increase `default_statistics_target` |
| `Nested Loop` with large outer | Missing index on inner join column | Add FK index |
| `Sort` on large row count | Missing index for ORDER BY | Add index on sort column |
| `Hash Batches > 1` | Hash table spilled to disk | Increase `work_mem` |
| High `shared read` | Data not in cache | Check `effective_cache_size`; add indexes |

---

## Forcing and Comparing Plans

```sql
-- Temporarily disable a scan type to compare alternatives:
SET enable_seqscan = OFF;
EXPLAIN SELECT * FROM product WHERE category_id = 3;
-- Forces an index scan (if index exists) — compare cost with seq scan
RESET enable_seqscan;

SET enable_hashjoin = OFF;
EXPLAIN ANALYZE SELECT ...;  -- forces merge join
RESET enable_hashjoin;

-- Never disable these in production — only in EXPLAIN sessions.
```

---

## Try It Yourself

```sql
-- 1. Run EXPLAIN (ANALYZE, BUFFERS) on:
--    SELECT * FROM product WHERE category_id = 3 AND price < 100;
--    Note: is it a Seq Scan or Index Scan? Note the actual vs estimated row counts.

-- 2. Run EXPLAIN ANALYZE on a join:
--    SELECT c.name, SUM(o.total_amount)
--    FROM customer c JOIN "order" o ON o.customer_id = c.id
--    WHERE o.status = 'delivered'
--    GROUP BY c.id, c.name ORDER BY 2 DESC LIMIT 10;
--    Identify the join type used (Nested Loop / Hash / Merge).

-- 3. Check BUFFERS output. How many shared hits vs shared reads?
--    Run the same query twice — does the second run have fewer reads?
--    (Second run should be faster as data is now in shared_buffers.)
```

<details>
<summary>Show solutions</summary>

```sql
-- 1. Scan type on product filter:
EXPLAIN (ANALYZE, BUFFERS)
SELECT * FROM product WHERE category_id = 3 AND price < 100;
-- Likely Seq Scan (no index on category_id yet — Module 08-02 adds indexes)
-- Actual rows may differ from estimated if ANALYZE hasn't run recently

-- Force analysis on table to update statistics:
ANALYZE product;
EXPLAIN (ANALYZE, BUFFERS) SELECT * FROM product WHERE category_id = 3 AND price < 100;

-- 2. Join plan for top customers:
EXPLAIN (ANALYZE, BUFFERS)
SELECT c.name, SUM(o.total_amount) AS total
FROM customer c JOIN "order" o ON o.customer_id = c.id
WHERE o.status = 'delivered'
GROUP BY c.id, c.name
ORDER BY total DESC
LIMIT 10;
-- Typical: Hash Join (customer hash built, order scanned)
-- Or Nested Loop if order has good index and customer is small

-- 3. Buffers comparison:
-- First run (cold):
EXPLAIN (ANALYZE, BUFFERS)
SELECT * FROM order_item WHERE order_id BETWEEN 1 AND 100;

-- Second run (warm cache):
EXPLAIN (ANALYZE, BUFFERS)
SELECT * FROM order_item WHERE order_id BETWEEN 1 AND 100;
-- shared read should drop to 0, all shared hit
```

</details>

---

## Capstone Connection

EXPLAIN-driven optimisation in StoreForge:
1. **`place_order()` inner SELECT** — use `EXPLAIN ANALYZE` after each migration to ensure locking queries use index scans (not seq scans) on `product.id`
2. **Revenue aggregation** — the monthly sales report was moved to a materialized view after `EXPLAIN` revealed a 500ms hash join on unindexed columns
3. **`pg_stat_statements`** — tracks cumulative stats for every normalised query; the top 10 by `total_exec_time` are reviewed weekly and each investigated with `EXPLAIN ANALYZE`
