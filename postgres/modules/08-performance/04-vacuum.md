# VACUUM and Table Maintenance

## Concept

PostgreSQL uses Multi-Version Concurrency Control (MVCC): instead of overwriting a row when it is updated or deleted, it marks the old row as "dead" and writes a new version. Over time, dead row versions accumulate — this is called **table bloat**. `VACUUM` reclaims that space (making it reusable by future inserts). `ANALYZE` updates column statistics so the planner makes good decisions. Without regular vacuuming, queries slow down and — if left long enough — a devastating **transaction ID wrap-around** can freeze the database entirely.

---

## How MVCC Creates Dead Rows

```sql
-- Every row has hidden system columns:
SELECT ctid, xmin, xmax, id, name
FROM product
LIMIT 5;
-- ctid:  physical location (page, offset)
-- xmin:  transaction that inserted this row (it's "alive" to transactions >= xmin)
-- xmax:  transaction that deleted/updated this row (0 = still live)

-- When you UPDATE a row:
UPDATE product SET price = 99.99 WHERE id = 1;
-- PostgreSQL inserts a NEW row with xmin = current_txn
-- And sets xmax = current_txn on the OLD row (marking it dead)
-- Both versions exist on disk until VACUUM reclaims the dead one.

-- See dead row count:
SELECT relname, n_live_tup, n_dead_tup,
       last_vacuum, last_autovacuum, last_analyze, last_autoanalyze
FROM pg_stat_user_tables
WHERE relname IN ('product', 'customer', 'order', 'order_item')
ORDER BY n_dead_tup DESC;
```

---

## VACUUM

```sql
-- Standard VACUUM: marks dead rows as reusable (does NOT return space to OS).
-- Non-blocking — readers and writers continue normally.
VACUUM product;
VACUUM customer;
VACUUM "order";
VACUUM ANALYZE product;  -- VACUUM + update statistics in one pass

-- VACUUM all tables in the database:
VACUUM;

-- VACUUM FULL: rewrites the entire table to reclaim space to the OS.
-- EXCLUSIVE LOCK — blocks all reads and writes. Use with extreme caution.
-- Only needed when a table has severe bloat (e.g., after a mass delete).
VACUUM FULL product;     -- reclaims OS space but locks the table
-- Alternative: pg_repack extension does this without a lock

-- VACUUM VERBOSE: shows what it does:
VACUUM VERBOSE product;
-- INFO: vacuuming "public.product"
-- INFO: scanned index "product_pkey" to remove 12 row versions
-- INFO: "product": found 12 removable, 50 nonremovable row versions
```

---

## ANALYZE

```sql
-- Update planner statistics (random sample of column values):
ANALYZE product;
ANALYZE "order";
ANALYZE;  -- all tables in the database

-- Check when a table was last analyzed:
SELECT relname, last_analyze, last_autoanalyze, n_live_tup
FROM pg_stat_user_tables
ORDER BY last_analyze NULLS FIRST;

-- After bulk data loads or major changes, always ANALYZE manually:
COPY product FROM '/tmp/new_products.csv' CSV HEADER;
ANALYZE product;

-- Increase sample size for a specific column (already seen in Module 08-03):
ALTER TABLE "order" ALTER COLUMN status SET STATISTICS 500;
ANALYZE "order";
```

---

## Autovacuum

Autovacuum is a background daemon that runs VACUUM and ANALYZE automatically based on thresholds:

```sql
-- Check if autovacuum is running:
SHOW autovacuum;  -- 'on'

-- Default trigger thresholds (in postgresql.conf):
-- autovacuum_vacuum_threshold  = 50      -- minimum dead rows before vacuum
-- autovacuum_vacuum_scale_factor = 0.2   -- + 20% of table size threshold
-- For a 50-row table: vacuum triggers at 50 + (50 * 0.2) = 60 dead rows
-- For a 1M-row table: vacuum triggers at 50 + (1M * 0.2) = 200,050 dead rows
-- The scale_factor is too large for big tables — override per-table:

-- Override autovacuum thresholds for a high-churn table:
ALTER TABLE order_item SET (
    autovacuum_vacuum_scale_factor = 0.01,   -- vacuum after 1% dead rows
    autovacuum_analyze_scale_factor = 0.005  -- analyze after 0.5% change
);

ALTER TABLE "order" SET (
    autovacuum_vacuum_scale_factor = 0.05,
    autovacuum_analyze_scale_factor = 0.02
);

-- Show table storage parameters:
SELECT relname, reloptions
FROM pg_class
WHERE relname IN ('order_item', 'order')
ORDER BY relname;

-- Monitor active autovacuum workers:
SELECT pid, query, now() - query_start AS duration
FROM pg_stat_activity
WHERE query LIKE 'autovacuum:%';
```

---

## Transaction ID Wraparound

PostgreSQL uses 32-bit transaction IDs that wrap around after ~2.1 billion transactions. If a table is never vacuumed, it accumulates old XIDs and eventually must be frozen to prevent data corruption:

```sql
-- How many transactions until wraparound risk:
SELECT relname,
       age(relfrozenxid) AS xid_age,
       2100000000 - age(relfrozenxid) AS transactions_until_danger
FROM pg_class
WHERE relkind = 'r'
  AND relname NOT LIKE 'pg_%'
ORDER BY age(relfrozenxid) DESC
LIMIT 10;

-- PostgreSQL emergency freezes at autovacuum_freeze_max_age (default: 200M XIDs)
-- WARNING appears in logs at vacuum_freeze_max_age (default: 150M XIDs)

-- Force freeze specific table:
VACUUM FREEZE product;

-- PostgreSQL 14+ shows wraparound danger in pg_stat_user_tables:
SELECT relname, n_dead_tup, last_vacuum,
       age(c.relfrozenxid) AS xid_age
FROM pg_stat_user_tables s
JOIN pg_class c ON c.relname = s.relname
ORDER BY xid_age DESC;
```

---

## Detecting Bloat

```sql
-- Rough bloat estimate using pg_stat_user_tables:
SELECT
    relname AS table,
    n_live_tup,
    n_dead_tup,
    ROUND(100.0 * n_dead_tup / NULLIF(n_live_tup + n_dead_tup, 0), 1) AS dead_pct,
    pg_size_pretty(pg_total_relation_size(relid)) AS total_size
FROM pg_stat_user_tables
WHERE n_dead_tup > 0
ORDER BY dead_pct DESC;

-- More precise bloat: use pgstattuple extension:
CREATE EXTENSION IF NOT EXISTS pgstattuple;
SELECT * FROM pgstattuple('order_item');
-- Returns: table_len, dead_tuple_count, dead_tuple_len, free_space, free_percent
```

---

## Try It Yourself

```sql
-- 1. Run VACUUM ANALYZE on all StoreForge tables.
--    Then query pg_stat_user_tables to see last_vacuum and n_dead_tup.

-- 2. Create some dead rows:
--    UPDATE product SET price = price + 0.01 WHERE TRUE;  -- updates every row
--    SELECT n_live_tup, n_dead_tup FROM pg_stat_user_tables WHERE relname = 'product';
--    Note the dead row count. Then VACUUM ANALYZE product.
--    Query again — dead rows should be near zero.

-- 3. Lower the autovacuum_vacuum_scale_factor for order_item to 0.01
--    and autovacuum_analyze_scale_factor to 0.005.
--    Verify the storage parameter is set with:
--    SELECT reloptions FROM pg_class WHERE relname = 'order_item';

-- 4. Query the XID age of all user tables and identify which table is closest
--    to needing emergency freezing (highest xid_age).
```

<details>
<summary>Show solutions</summary>

```sql
-- 1. VACUUM ANALYZE:
VACUUM ANALYZE product;
VACUUM ANALYZE customer;
VACUUM ANALYZE "order";
VACUUM ANALYZE order_item;
VACUUM ANALYZE review;

SELECT relname, last_vacuum, last_analyze, n_live_tup, n_dead_tup
FROM pg_stat_user_tables
ORDER BY relname;

-- 2. Dead rows then vacuum:
UPDATE product SET price = price + 0.01;  -- creates dead row for every product row

SELECT n_live_tup, n_dead_tup
FROM pg_stat_user_tables WHERE relname = 'product';
-- n_dead_tup should be around 50 (or your product count)

VACUUM ANALYZE product;

SELECT n_live_tup, n_dead_tup
FROM pg_stat_user_tables WHERE relname = 'product';
-- n_dead_tup should now be 0 (or near 0)

-- 3. Autovacuum thresholds:
ALTER TABLE order_item SET (
    autovacuum_vacuum_scale_factor = 0.01,
    autovacuum_analyze_scale_factor = 0.005
);

SELECT relname, reloptions
FROM pg_class WHERE relname = 'order_item';

-- 4. XID age:
SELECT relname, age(relfrozenxid) AS xid_age
FROM pg_class
WHERE relkind = 'r'
  AND relname NOT LIKE 'pg_%'
ORDER BY xid_age DESC
LIMIT 5;
```

</details>

---

## Capstone Connection

StoreForge's vacuum strategy:
- **Autovacuum enabled** with lower `scale_factor` overrides on `order_item` and `"order"` (the two most frequently mutated tables)
- **Manual `VACUUM ANALYZE`** run after each bulk data migration (seed scripts, category imports)
- **XID age monitoring** in the weekly DBA health check — alert if any table exceeds 1.5 billion XIDs
- **`VACUUM FULL`** avoided; instead `pg_repack` is used offline during maintenance windows if bloat exceeds 30%
- **`pgstattuple`** installed and queried monthly to detect bloat trends before they impact query performance
