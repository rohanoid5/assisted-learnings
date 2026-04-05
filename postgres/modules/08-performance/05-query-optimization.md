# Query Optimization

## Concept

Writing a query that returns correct results is step one. Writing one that returns results *fast* is step two. PostgreSQL gives you excellent tools to find slow queries before users do: `pg_stat_statements` surfaces the top offenders by total time, `EXPLAIN ANALYZE` shows exactly where the planner went wrong, and `CREATE STATISTICS` teaches the planner about correlated columns. This lesson covers the full workflow from detection to fix.

---

## pg_stat_statements

```sql
-- Enable the extension (requires postgresql.conf restart):
-- shared_preload_libraries = 'pg_stat_statements'  -- in postgresql.conf
CREATE EXTENSION IF NOT EXISTS pg_stat_statements;

-- Top 10 slowest queries by TOTAL execution time:
SELECT
    LEFT(query, 80) AS query_snippet,
    calls,
    round(total_exec_time::numeric, 0) AS total_ms,
    round(mean_exec_time::numeric, 2) AS mean_ms,
    round(stddev_exec_time::numeric, 2) AS stddev_ms,
    round(rows::numeric / NULLIF(calls, 0), 1) AS avg_rows
FROM pg_stat_statements
ORDER BY total_exec_time DESC
LIMIT 10;

-- Top queries by AVERAGE execution time (rare but expensive):
SELECT LEFT(query, 80), calls, round(mean_exec_time::numeric, 2) AS mean_ms
FROM pg_stat_statements
ORDER BY mean_exec_time DESC
LIMIT 10;

-- High call frequency — worth optimizing even if fast:
SELECT LEFT(query, 80), calls, round(mean_exec_time::numeric, 2) AS mean_ms,
       round(total_exec_time::numeric, 0) AS total_ms
FROM pg_stat_statements
ORDER BY calls DESC
LIMIT 10;

-- Reset stats after a change to measure improvement:
SELECT pg_stat_statements_reset();
```

---

## Slow Query Logging

```sql
-- postgresql.conf:
-- log_min_duration_statement = 200   -- log queries taking > 200ms
-- log_line_prefix = '%t [%p]: [%l-1] user=%u,db=%d '
-- log_lock_waits = on

-- Parse recent slow queries from the log:
-- SHOW log_directory;   -- usually /var/log/postgresql or $PGDATA/log
-- tail -n 200 /var/log/postgresql/postgresql-today.log | grep "duration"

-- Adjust threshold at session level for testing without restart:
SET log_min_duration_statement = 0;  -- log EVERYTHING (dev/testing only)
SELECT * FROM product WHERE category_id = 1;  -- this will appear in the log
RESET log_min_duration_statement;
```

---

## Extended Statistics

The planner assumes columns are independent. When columns are correlated (e.g., `city` and `postal_code`), row estimates are wildly off:

```sql
-- Example: planner estimates city + state combinattion
EXPLAIN SELECT * FROM address WHERE city = 'Austin' AND state = 'TX';
-- Planner may estimate 1 row when there are 1,000 because it multiplies:
-- p(city='Austin') * p(state='TX') independently.

-- Create statistics that capture the correlation:
CREATE STATISTICS addr_city_state ON city, state FROM address;
ANALYZE address;

EXPLAIN SELECT * FROM address WHERE city = 'Austin' AND state = 'TX';
-- Now the estimate is dramatically better.

-- Also captures functional dependencies and MCV (Most Common Values):
CREATE STATISTICS order_status_customer
    (dependencies, mcv)
    ON status, customer_id
FROM "order";
ANALYZE "order";

-- View existing extended statistics:
SELECT stxname, stxkeys, stxkind
FROM pg_statistic_ext
WHERE stxnamespace = (SELECT oid FROM pg_namespace WHERE nspname = 'public');
```

---

## Common Query Anti-Patterns

### 1. Function in WHERE Clause (Prevents Index Use)

```sql
-- BAD: forces full table scan — index on email is useless:
SELECT * FROM customer WHERE LOWER(email) = 'alice@example.com';

-- GOOD option A: normalize data at write time (store email in lowercase):
UPDATE customer SET email = LOWER(email);
ALTER TABLE customer ADD CONSTRAINT email_lowercase
    CHECK (email = LOWER(email));

-- GOOD option B: create an expression index that matches the query:
CREATE INDEX CONCURRENTLY ON customer (LOWER(email));
SELECT * FROM customer WHERE LOWER(email) = 'alice@example.com';  -- now uses index

-- BAD: DATE() strips the time, breaks timestamp index:
SELECT * FROM "order" WHERE DATE(created_at) = '2024-01-15';

-- GOOD: use a range predicate, preserving the index:
SELECT * FROM "order"
WHERE created_at >= '2024-01-15'
  AND created_at <  '2024-01-16';
```

### 2. Implicit Type Casts

```sql
-- BAD: comparing integer column to a string literal forces a cast:
SELECT * FROM "order" WHERE id = '12345';   -- id is INTEGER, '12345' is TEXT
-- PostgreSQL will cast, probably still use the index, but can cause issues
-- with non-default collations or on foreign columns in joins.

-- GOOD: always match the column type:
SELECT * FROM "order" WHERE id = 12345;

-- BAD: phone is VARCHAR, comparison against an integer:
SELECT * FROM customer WHERE phone = 5125551234;

-- GOOD:
SELECT * FROM customer WHERE phone = '512-555-1234';
```

### 3. SELECT * (Fetches More Than Needed)

```sql
-- BAD: fetches all columns, prevents Index Only Scan, larger network payload:
SELECT * FROM product WHERE is_active = TRUE;

-- GOOD: fetch only what you need:
SELECT id, name, price, slug FROM product WHERE is_active = TRUE;

-- Index Only Scan is only possible when all selected columns are in the index:
CREATE INDEX CONCURRENTLY ON product (is_active) INCLUDE (name, price, slug);
EXPLAIN SELECT id, name, price, slug FROM product WHERE is_active = TRUE;
-- Now shows: Index Only Scan
```

### 4. OR Instead of UNION (Can't Use Multiple Indexes)

```sql
-- BAD: PostgreSQL may not efficiently combine two indexes with OR:
SELECT * FROM "order"
WHERE customer_id = 42
   OR shipping_address_id = 17;

-- GOOD: UNION lets each branch use its own index:
SELECT * FROM "order" WHERE customer_id = 42
UNION ALL
SELECT * FROM "order" WHERE shipping_address_id = 17;
-- (Use UNION if duplicates are possible, UNION ALL if you accept them)
```

### 5. N+1 Query Pattern

```sql
-- BAD (application code doing N+1):
-- For each order, app fires: SELECT * FROM order_item WHERE order_id = ?
-- For 100 orders → 101 queries.

-- GOOD: fetch everything in one query:
SELECT
    o.id AS order_id,
    o.created_at,
    o.total_amount,
    json_agg(json_build_object(
        'product_id', oi.product_id,
        'quantity', oi.quantity,
        'unit_price', oi.unit_price
    ) ORDER BY oi.id) AS items
FROM "order" o
JOIN order_item oi ON oi.order_id = o.id
WHERE o.customer_id = 42
GROUP BY o.id
ORDER BY o.created_at DESC;
-- 1 query instead of 101.
```

---

## CTE Optimization Fences

```sql
-- Before PG 12, every CTE was ALWAYS MATERIALIZED (computed once, stored):
-- This prevented the planner from pushing predicates into the CTE.

-- PG 12+: CTEs that are read once and have no side effects are inlined by default.
-- You can control this explicitly:

-- Force materialization (old behavior, useful when CTE is read multiple times):
WITH expensive_data AS MATERIALIZED (
    SELECT customer_id, SUM(total_amount) AS lifetime_value
    FROM "order"
    WHERE status = 'delivered'
    GROUP BY customer_id
)
SELECT c.name, d.lifetime_value
FROM customer c
JOIN expensive_data d ON d.customer_id = c.id
WHERE d.lifetime_value > 500;

-- Prevent materialization (allow planner to inline and optimize):
WITH recent_orders AS NOT MATERIALIZED (
    SELECT * FROM "order" WHERE created_at > NOW() - INTERVAL '7 days'
)
SELECT * FROM recent_orders WHERE customer_id = 42;
-- Planner can combine the WHERE clauses and use a composite index.
```

---

## Try It Yourself

```sql
-- 1. Use pg_stat_statements to find the 5 queries with the highest mean_exec_time
--    in your StoreForge database. What do you observe?

-- 2. The following query is slow. Identify why and fix it:
--    SELECT * FROM product WHERE UPPER(name) LIKE 'SHOE%';

-- 3. Rewrite this N+1-prone query as a single efficient query:
--    "For each active customer, get their most recent order status."
--    (The app currently fires one query per customer.)

-- 4. Create extended statistics on "order"(customer_id, status).
--    Run ANALYZE, then compare the row estimate on this query before and after:
--    SELECT count(*) FROM "order" WHERE customer_id = 5 AND status = 'delivered';
```

<details>
<summary>Show solutions</summary>

```sql
-- 1. pg_stat_statements exploration:
SELECT
    LEFT(query, 100) AS query_snippet,
    calls,
    round(mean_exec_time::numeric, 2) AS mean_ms,
    round(total_exec_time::numeric, 0) AS total_ms
FROM pg_stat_statements
ORDER BY mean_exec_time DESC
LIMIT 5;
-- Findings will vary based on your session; look for full table scans on large tables.

-- 2. Fix the UPPER() predicate:
-- Problem: UPPER(name) forces a sequential scan — the index on 'name' is useless.

-- Option A: expression index:
CREATE INDEX CONCURRENTLY ON product (UPPER(name) text_pattern_ops);
SELECT * FROM product WHERE UPPER(name) LIKE 'SHOE%';
-- Now uses the expression index.

-- Option B: use pg_trgm + GIN for arbitrary LIKE patterns:
CREATE INDEX CONCURRENTLY ON product USING GIN (name gin_trgm_ops);
SELECT * FROM product WHERE name ILIKE 'shoe%';

-- 3. Most recent order status per active customer:
SELECT DISTINCT ON (c.id)
    c.id AS customer_id,
    c.name,
    o.status AS latest_order_status,
    o.created_at AS latest_order_date
FROM customer c
JOIN "order" o ON o.customer_id = c.id
WHERE c.is_active = TRUE
ORDER BY c.id, o.created_at DESC;

-- Or with a window function:
SELECT customer_id, name, latest_order_status, latest_order_date
FROM (
    SELECT
        c.id AS customer_id,
        c.name,
        o.status AS latest_order_status,
        o.created_at AS latest_order_date,
        ROW_NUMBER() OVER (PARTITION BY c.id ORDER BY o.created_at DESC) AS rn
    FROM customer c
    JOIN "order" o ON o.customer_id = c.id
    WHERE c.is_active = TRUE
) t
WHERE rn = 1;

-- 4. Extended statistics for correlated columns:
EXPLAIN SELECT count(*) FROM "order" WHERE customer_id = 5 AND status = 'delivered';
-- Note the estimated rows BEFORE.

CREATE STATISTICS order_customer_status (dependencies, mcv)
    ON customer_id, status
FROM "order";

ANALYZE "order";

EXPLAIN SELECT count(*) FROM "order" WHERE customer_id = 5 AND status = 'delivered';
-- Note the estimated rows AFTER — should be much closer to actual.
```

</details>

---

## Capstone Connection

StoreForge query optimization practices:
- **`pg_stat_statements`** reviewed weekly — top 10 queries by total time are candidates for indexing or rewriting
- **`log_min_duration_statement = 200`** in production; anything over 200ms is logged and reviewed in the next sprint
- **Extended statistics** defined on `("order".customer_id, "order".status)` and `(address.city, address.state)` — the two places where correlated predicates caused plan regressions
- **No `SELECT *`** in application code — ORM configured to fetch only mapped columns
- **Expression index `LOWER(email)`** on `customer` ensures case-insensitive login lookup stays O(log n)
