# Module 08 Exercises — Performance Tuning

## Setup

Connect to your StoreForge database:
```bash
psql -h localhost -U storeforge -d storeforge_dev
```

Ensure `pg_stat_statements` is available:
```sql
CREATE EXTENSION IF NOT EXISTS pg_stat_statements;
CREATE EXTENSION IF NOT EXISTS pgstattuple;
```

---

## Exercise 1 — EXPLAIN ANALYZE and Scan Types

**Goal:** Use `EXPLAIN ANALYZE` to understand how PostgreSQL executes queries and identify plan inefficiencies.

```sql
-- Step 1: Run EXPLAIN ANALYZE on a product name search:
EXPLAIN (ANALYZE, BUFFERS, FORMAT TEXT)
SELECT id, name, price
FROM product
WHERE name ILIKE '%running%'
  AND is_active = TRUE;

-- Step 2: Run EXPLAIN ANALYZE on a customer order summary:
EXPLAIN (ANALYZE, BUFFERS)
SELECT c.name, COUNT(o.id) AS order_count, SUM(o.total_amount) AS lifetime_value
FROM customer c
JOIN "order" o ON o.customer_id = c.id
WHERE o.status = 'delivered'
GROUP BY c.id, c.name
ORDER BY lifetime_value DESC
LIMIT 10;

-- Step 3: Compare scan types
-- a) Disable sequential scans and see if a different plan emerges:
SET enable_seqscan = OFF;
EXPLAIN ANALYZE SELECT * FROM product WHERE is_active = TRUE;
RESET enable_seqscan;

-- Answer these questions:
-- - What scan type(s) does each query use?
-- - In query 2, what join type does PostgreSQL choose? Why?
-- - What does a high "Buffers: shared read" number indicate?
-- - Are the estimated row counts close to actual row counts?
```

<details>
<summary>Show solutions and discussion</summary>

```sql
-- 1. Product ILIKE search:
EXPLAIN (ANALYZE, BUFFERS)
SELECT id, name, price FROM product WHERE name ILIKE '%running%' AND is_active = TRUE;

-- Expected: Seq Scan on product (ILIKE '%...%' cannot use a standard B-tree index)
-- Fix: Add a pg_trgm GIN index to enable fast ILIKE:
CREATE INDEX CONCURRENTLY ON product USING GIN (name gin_trgm_ops);
-- After the index, re-run EXPLAIN — it will switch to a Bitmap Index Scan.

-- 2. Customer order summary:
EXPLAIN (ANALYZE, BUFFERS)
SELECT c.name, COUNT(o.id), SUM(o.total_amount)
FROM customer c
JOIN "order" o ON o.customer_id = c.id
WHERE o.status = 'delivered'
GROUP BY c.id, c.name
ORDER BY SUM(o.total_amount) DESC LIMIT 10;

-- Expected: Hash Join or Merge Join (large table join)
-- Hash Join preferred when one side fits in work_mem.
-- Customer is the build side (smaller), order is the probe side.

-- 3. Answers to questions:
-- - High "Buffers: shared read" = reading from disk (not cache) — cold cache or too little shared_buffers.
-- - If estimated rows >> actual rows: planner may over-allocate memory, choose wrong join strategy.
-- - If estimated rows << actual rows: planner may under-allocate (hash batches spill to disk).
```

</details>

---

## Exercise 2 — Index Audit

**Goal:** Identify missing, unused, and duplicate indexes on the StoreForge schema.

```sql
-- Step 1: Find tables with missing FK indexes:
-- Every foreign key column that can appear in a JOIN should have an index.
-- Check which FK columns lack indexes:
SELECT
    tc.table_name,
    kcu.column_name,
    tc.constraint_name
FROM information_schema.table_constraints tc
JOIN information_schema.key_column_usage kcu
    ON tc.constraint_name = kcu.constraint_name
WHERE tc.constraint_type = 'FOREIGN KEY'
  AND NOT EXISTS (
      SELECT 1
      FROM pg_indexes i
      WHERE i.tablename = tc.table_name
        AND i.indexdef LIKE '%(' || kcu.column_name || ')%'
  )
ORDER BY tc.table_name;

-- Step 2: Check for unused indexes:
SELECT relname AS table, indexrelname AS index, idx_scan
FROM pg_stat_user_indexes
WHERE idx_scan < 10
  AND relname NOT LIKE 'pg_%'
ORDER BY idx_scan ASC;

-- Step 3: Create any missing FK indexes you found.

-- Step 4: After adding indexes, run EXPLAIN ANALYZE on a JOIN query and verify
--         it switched from a Seq Scan to an Index Scan.
```

<details>
<summary>Show solutions</summary>

```sql
-- Step 1: Common missing FK indexes in StoreForge:
-- address.customer_id  → needs index (used in JOIN customer)
-- order.shipping_address_id → needs index
-- order_item.order_id  → needs index (critical: every order lookup)
-- order_item.product_id → needs index
-- review.product_id    → needs index
-- review.customer_id   → needs index

-- Step 3: Create missing FK indexes:
CREATE INDEX CONCURRENTLY ON address (customer_id);
CREATE INDEX CONCURRENTLY ON "order" (shipping_address_id);
CREATE INDEX CONCURRENTLY ON order_item (order_id);
CREATE INDEX CONCURRENTLY ON order_item (product_id);
CREATE INDEX CONCURRENTLY ON review (product_id);
CREATE INDEX CONCURRENTLY ON review (customer_id);

-- Step 4: Verify improvement on order lookup:
EXPLAIN ANALYZE
SELECT o.id, o.status, oi.quantity, p.name
FROM "order" o
JOIN order_item oi ON oi.order_id = o.id
JOIN product p ON p.id = oi.product_id
WHERE o.customer_id = 42;
-- Before indexes: Seq Scan on order_item with filter
-- After indexes:  Index Scan using order_item_order_id_idx
```

</details>

---

## Exercise 3 — VACUUM and Dead Row Management

**Goal:** Understand autovacuum behavior and manually control vacuuming.

```sql
-- Step 1: Check the current dead tuple count for all StoreForge tables:
SELECT
    relname AS table,
    n_live_tup,
    n_dead_tup,
    ROUND(100.0 * n_dead_tup / NULLIF(n_live_tup + n_dead_tup, 0), 1) AS dead_pct,
    last_autovacuum,
    last_autoanalyze,
    pg_size_pretty(pg_total_relation_size(relid)) AS total_size
FROM pg_stat_user_tables
ORDER BY dead_pct DESC NULLS LAST;

-- Step 2: Deliberately create dead rows:
UPDATE product SET price = price * 1.01;  -- update all products
SELECT n_live_tup, n_dead_tup FROM pg_stat_user_tables WHERE relname = 'product';

-- Step 3: Run VACUUM VERBOSE to see what it cleans up:
VACUUM VERBOSE product;

-- Step 4: Check dead rows again — should be near zero:
SELECT n_live_tup, n_dead_tup FROM pg_stat_user_tables WHERE relname = 'product';

-- Step 5: Set aggressive autovacuum thresholds for order_item:
ALTER TABLE order_item SET (
    autovacuum_vacuum_scale_factor = 0.01,
    autovacuum_analyze_scale_factor = 0.005
);
SELECT reloptions FROM pg_class WHERE relname = 'order_item';

-- Step 6: Check XID ages to verify no table is approaching wraparound danger:
SELECT
    relname,
    age(relfrozenxid) AS xid_age,
    ROUND(100.0 * age(relfrozenxid) / 200000000.0, 1) AS pct_of_freeze_max
FROM pg_class
WHERE relkind = 'r' AND relname NOT LIKE 'pg_%'
ORDER BY xid_age DESC;
-- Alert threshold: pct_of_freeze_max > 50% warrants attention.
```

<details>
<summary>Show solutions</summary>

```sql
-- Step 1: Baseline dead row check — Run before any updates.

-- Step 2: After mass update:
UPDATE product SET price = price * 1.01;
SELECT n_live_tup, n_dead_tup FROM pg_stat_user_tables WHERE relname = 'product';
-- n_dead_tup should equal the number of products (since every row was replaced).

-- Step 3: VACUUM with verbose output:
VACUUM VERBOSE product;
-- Look for output lines like:
-- "found N removable, M nonremovable row versions"
-- "removed N row versions in N pages"

-- Step 4: Zero (or near-zero) dead rows after vacuum.

-- Step 5: Table storage options:
ALTER TABLE order_item SET (
    autovacuum_vacuum_scale_factor = 0.01,
    autovacuum_analyze_scale_factor = 0.005
);
SELECT reloptions FROM pg_class WHERE relname = 'order_item';
-- reloptions: {autovacuum_vacuum_scale_factor=0.01,autovacuum_analyze_scale_factor=0.005}

-- Step 6: XID age check:
SELECT relname, age(relfrozenxid) AS xid_age,
       ROUND(100.0 * age(relfrozenxid) / 200000000.0, 1) AS pct_of_freeze_max
FROM pg_class
WHERE relkind = 'r' AND relname NOT LIKE 'pg_%'
ORDER BY xid_age DESC;
-- Fresh development databases typically have low xid_age (<< 1M).
```

</details>

---

## Exercise 4 — Query Optimization with pg_stat_statements

**Goal:** Find the slowest query, understand why it is slow, and fix it.

```sql
-- Step 1: Run a few queries to populate pg_stat_statements:
SELECT * FROM product WHERE LOWER(name) LIKE '%shoe%';
SELECT count(*) FROM "order" o JOIN order_item oi ON oi.order_id = o.id WHERE o.status = 'delivered';
SELECT c.*, a.city FROM customer c JOIN address a ON a.customer_id = c.id WHERE a.city = 'Austin' AND a.state = 'TX';

-- Step 2: Inspect pg_stat_statements for your session's queries:
SELECT
    LEFT(query, 100) AS query_snippet,
    calls,
    round(mean_exec_time::numeric, 3) AS mean_ms,
    round(total_exec_time::numeric, 1) AS total_ms
FROM pg_stat_statements
ORDER BY mean_exec_time DESC
LIMIT 10;

-- Step 3: Take the slowest query. Run EXPLAIN ANALYZE on it.
--         Identify what makes it slow (likely a function call in WHERE, or missing index).

-- Step 4: Fix the slow query using one of these techniques:
--   a) Expression index matching the function in WHERE
--   b) Extended statistics on correlated columns
--   c) Add a missing FK index

-- Step 5: Reset stats, re-run the query, and compare mean_exec_time before and after.
SELECT pg_stat_statements_reset();
```

<details>
<summary>Show solutions</summary>

```sql
-- Step 1: Seed some slow queries.

-- Step 2: Find slow queries in pg_stat_statements.

-- Step 3: EXPLAIN ANALYZE the LOWER(name) query:
EXPLAIN ANALYZE SELECT * FROM product WHERE LOWER(name) LIKE '%shoe%';
-- Shows: Seq Scan with Filter: lower(name::text) ~~ '%shoe%'::text
-- Problem: no index can serve LOWER(name) without an expression index.

-- Step 4a: Create expression + trigram index:
CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE INDEX CONCURRENTLY ON product USING GIN (LOWER(name) gin_trgm_ops);
ANALYZE product;

EXPLAIN ANALYZE SELECT * FROM product WHERE LOWER(name) LIKE '%shoe%';
-- After: Bitmap Index Scan using the trigram GIN index.

-- Address city+state correlation fix:
CREATE STATISTICS addr_city_state (dependencies) ON city, state FROM address;
ANALYZE address;
EXPLAIN SELECT * FROM address WHERE city = 'Austin' AND state = 'TX';
-- Row estimate improves.

-- Step 5: Reset and remeasure:
SELECT pg_stat_statements_reset();
-- Re-run the fixed query 5 times.
SELECT LEFT(query, 80), calls, round(mean_exec_time::numeric, 3) AS mean_ms
FROM pg_stat_statements ORDER BY mean_exec_time DESC LIMIT 5;
-- mean_ms should be significantly lower after the index.
```

</details>

---

## Capstone Checkpoint ✅

After completing all exercises, verify your StoreForge performance setup:

- [ ] All foreign key columns have B-tree indexes (`order_item.order_id`, `order_item.product_id`, `review.product_id`, etc.)
- [ ] `product.name` has a trigram GIN index for full-text ILIKE search
- [ ] `order_item` and `"order"` have custom autovacuum scale factors (1% and 5% respectively)
- [ ] `pg_stat_statements` extension is installed and returning query data
- [ ] `EXPLAIN ANALYZE` on the customer order summary shows a Hash Join (not Nested Loop with Seq Scan)
- [ ] Extended statistics created on correlated column pairs
- [ ] No StoreForge table has `dead_pct > 10%` after running `VACUUM ANALYZE`
- [ ] XID age for all tables is within safe bounds (`pct_of_freeze_max < 25%`)
