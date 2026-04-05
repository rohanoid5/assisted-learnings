# Indexes

## Concept

An index is a separate data structure that PostgreSQL maintains alongside your table, enabling the planner to find rows without reading every page. The wrong index wastes write performance and disk space. The right index can turn a 2-second query into 2 milliseconds. PostgreSQL offers more index types than almost any other RDBMS — choosing the right type and columns is a core skill.

---

## B-Tree Indexes (Default)

B-tree is the default index type and supports equality (`=`), range (`<`, `>`, `BETWEEN`), `IS NULL`, `LIKE 'prefix%'`, and `ORDER BY`:

```sql
-- Single-column index:
CREATE INDEX idx_product_category ON product(category_id);
CREATE INDEX idx_product_price    ON product(price);
CREATE INDEX idx_order_customer   ON "order"(customer_id);
CREATE INDEX idx_order_status     ON "order"(status);
CREATE INDEX idx_order_item_order ON order_item(order_id);
CREATE INDEX idx_order_item_product ON order_item(product_id);
CREATE INDEX idx_review_product   ON review(product_id);

-- These are the FK indexes that should always exist on the referencing column.
-- PostgreSQL does NOT create them automatically for FOREIGN KEY constraints.

-- Composite index (order matters — leftmost prefix rule):
CREATE INDEX idx_order_customer_status ON "order"(customer_id, status);
-- Supports: WHERE customer_id = ?
--           WHERE customer_id = ? AND status = ?
-- Does NOT efficiently support: WHERE status = ?  (alone, without customer_id)

-- Verify index is used:
EXPLAIN SELECT * FROM "order" WHERE customer_id = 1 AND status = 'pending';
-- Index Scan using idx_order_customer_status
```

---

## Partial Indexes

Index only a subset of rows — smaller, faster, covers the common case:

```sql
-- Only index active products (most queries filter by is_active = TRUE):
CREATE INDEX idx_product_active ON product(category_id, price)
    WHERE is_active;

-- Only index pending and processing orders (completed orders rarely queried):
CREATE INDEX idx_order_open ON "order"(customer_id, created_at)
    WHERE status IN ('pending', 'processing', 'shipped');

-- Only index the default address (one per customer):
CREATE UNIQUE INDEX idx_address_default_one ON address(customer_id)
    WHERE is_default;

-- Query must include the WHERE clause of the partial index to use it:
EXPLAIN SELECT * FROM product WHERE category_id = 3 AND is_active;
-- Uses idx_product_active
EXPLAIN SELECT * FROM product WHERE category_id = 3;
-- Cannot use idx_product_active (missing "is_active" predicate)
```

---

## Covering Indexes (INCLUDE)

Add non-key columns so the index can serve the query without accessing the heap:

```sql
-- Index Scan still needs a heap fetch to get "name" and "price".
-- Index Only Scan avoids the heap fetch entirely:
CREATE INDEX idx_product_cat_covering ON product(category_id, is_active)
    INCLUDE (name, price, slug);

EXPLAIN SELECT name, price, slug FROM product
WHERE category_id = 3 AND is_active;
-- Index Only Scan using idx_product_cat_covering (no heap access)

-- For ORDER BY + LIMIT patterns (avoids sort node):
CREATE INDEX idx_order_customer_created ON "order"(customer_id, created_at DESC);

EXPLAIN SELECT id, status, total_amount
FROM "order"
WHERE customer_id = 1
ORDER BY created_at DESC
LIMIT 10;
-- Index Scan using idx_order_customer_created — no Sort node!
```

---

## Expression Indexes

Index a computed expression rather than a column value:

```sql
-- Case-insensitive email lookups:
CREATE UNIQUE INDEX idx_customer_email_lower ON customer(LOWER(email));

-- Query MUST use the same expression:
SELECT * FROM customer WHERE LOWER(email) = 'alice@example.com';  -- uses index

-- Slug from name (if using computed slugs in WHERE):
CREATE INDEX idx_product_slug_lower ON product(LOWER(slug));

-- Date-based partitioning queries:
CREATE INDEX idx_order_created_date ON "order"(DATE(created_at));

SELECT COUNT(*) FROM "order" WHERE DATE(created_at) = '2024-01-15';  -- index used
```

---

## GIN Indexes

Generalised Inverted Indexes — ideal for multi-valued types like JSONB, arrays, and full-text search:

```sql
-- Full-text search (covered in Module 04-06):
CREATE INDEX idx_product_fts ON product USING GIN (search_vector);

-- JSONB containment and key existence:
CREATE INDEX idx_product_attributes ON product USING GIN (attributes);

-- Supports: @> (contains), ? (key exists), ?& (all keys), ?| (any key)
EXPLAIN SELECT * FROM product WHERE attributes @> '{"color": "red"}';
-- Bitmap Index Scan using idx_product_attributes

-- Array index:
-- CREATE INDEX idx_tags ON article USING GIN (tags);  -- example

-- Trigram (from Module 06 Extensions):
CREATE INDEX idx_product_name_trgm ON product USING GIN (name gin_trgm_ops);
```

---

## GiST Indexes

Generalised Search Tree — for geometric, range, and proximity data:

```sql
-- Exclusion constraints require GiST (or btree_gist):
CREATE EXTENSION IF NOT EXISTS btree_gist;
CREATE EXTENSION IF NOT EXISTS btree_gist;

-- Price range exclusion (no overlapping discounts):
CREATE TABLE product_discount (
    id          SERIAL PRIMARY KEY,
    product_id  INTEGER NOT NULL REFERENCES product(id),
    discount_pct NUMERIC(5,2) NOT NULL,
    active_during TSTZRANGE NOT NULL
);

CREATE INDEX idx_discount_range ON product_discount USING GIST (product_id, active_during);

-- ltree hierarchy (from Module 06 Extensions):
CREATE INDEX idx_category_path_gist ON category USING GIST (path);

-- pg_trgm similarity search:
CREATE INDEX idx_product_desc_trgm ON product USING GIN (description gin_trgm_ops);
```

---

## BRIN Indexes

Block Range INdexes — tiny indexes for very large, naturally ordered tables like time-series data:

```sql
-- BRIN works when physical row order correlates with the indexed column.
-- audit_log rows are inserted in time order — BRIN is extremely compact:
CREATE INDEX idx_audit_log_brin ON audit_log USING BRIN (changed_at)
    WITH (pages_per_range = 128);

-- Much smaller than B-tree, but only useful when data is physically sorted:
SELECT pg_size_pretty(pg_relation_size('idx_audit_log_brin')) AS brin_size;
```

---

## Hash Indexes

Fast equality lookups only — smaller than B-tree for this case:

```sql
-- Hash index for pure equality on high-cardinality column:
CREATE INDEX idx_customer_email_hash ON customer USING HASH (email);

-- Only supports: WHERE email = 'alice@example.com'
-- Does NOT support: LIKE, range, IS NULL, ORDER BY
-- Usually B-tree is preferred (more flexible), but hash is slightly faster for pure equality.
```

---

## Index Management

```sql
-- Build index without blocking reads/writes (takes longer but safe in production):
CREATE INDEX CONCURRENTLY idx_order_created ON "order"(created_at);

-- Rebuild a bloated index:
REINDEX INDEX CONCURRENTLY idx_product_category;

-- Find unused indexes (candidates for removal):
SELECT schemaname, tablename, indexname,
       idx_scan, idx_tup_read, idx_tup_fetch,
       pg_size_pretty(pg_relation_size(indexrelid)) AS index_size
FROM pg_stat_user_indexes
WHERE idx_scan = 0                    -- never used since last stats reset
  AND indexrelname NOT LIKE 'pg_%'
ORDER BY pg_relation_size(indexrelid) DESC;

-- Find duplicate or redundant indexes:
SELECT a.indexname AS index_a, b.indexname AS index_b,
       a.tablename, a.indexdef
FROM pg_indexes a JOIN pg_indexes b
    ON a.tablename = b.tablename
    AND a.indexname < b.indexname
    AND a.indexdef = b.indexdef
WHERE a.schemaname = 'public';

-- Index sizes:
SELECT indexname,
       pg_size_pretty(pg_relation_size(indexrelid)) AS size,
       idx_scan AS scans
FROM pg_stat_user_indexes
ORDER BY pg_relation_size(indexrelid) DESC;
```

---

## Try It Yourself

```sql
-- 1. Create the missing FK indexes on order_item and review.
--    Run EXPLAIN ANALYZE on:
--    SELECT * FROM order_item WHERE order_id = 1;
--    Before and after adding the index. Note the plan change.

-- 2. Create a partial index for open orders (status IN ('pending','processing','shipped'))
--    on "order"(customer_id, created_at).
--    Run EXPLAIN on: SELECT * FROM "order" WHERE customer_id = 1 AND status = 'pending';
--    Confirm Index Scan is used.

-- 3. Create a covering index on product(category_id, is_active) INCLUDE (name, price).
--    Run EXPLAIN on: SELECT name, price FROM product WHERE category_id = 3 AND is_active;
--    Confirm Index Only Scan (no heap fetch).

-- 4. Query pg_stat_user_indexes after running a few SELECT queries.
--    Identify which indexes have been scanned. Which have idx_scan = 0?
```

<details>
<summary>Show solutions</summary>

```sql
-- 1. FK indexes:
CREATE INDEX IF NOT EXISTS idx_order_item_order   ON order_item(order_id);
CREATE INDEX IF NOT EXISTS idx_order_item_product ON order_item(product_id);
CREATE INDEX IF NOT EXISTS idx_review_product     ON review(product_id);
CREATE INDEX IF NOT EXISTS idx_review_customer    ON review(customer_id);

-- Before (Seq Scan):
EXPLAIN ANALYZE SELECT * FROM order_item WHERE order_id = 1;

-- After:
EXPLAIN ANALYZE SELECT * FROM order_item WHERE order_id = 1;
-- Index Scan using idx_order_item_order

-- 2. Partial index for open orders:
CREATE INDEX idx_order_open ON "order"(customer_id, created_at DESC)
    WHERE status IN ('pending', 'processing', 'shipped');

EXPLAIN SELECT * FROM "order"
WHERE customer_id = 1 AND status = 'pending'
ORDER BY created_at DESC;
-- Index Scan using idx_order_open

-- 3. Covering index:
CREATE INDEX idx_product_cat_cover ON product(category_id, is_active)
    INCLUDE (name, price);

EXPLAIN SELECT name, price FROM product WHERE category_id = 3 AND is_active;
-- Index Only Scan using idx_product_cat_cover

-- 4. Index usage stats:
SELECT indexname, idx_scan, idx_tup_read,
       pg_size_pretty(pg_relation_size(indexrelid)) AS size
FROM pg_stat_user_indexes
ORDER BY idx_scan DESC, pg_relation_size(indexrelid) DESC;
```

</details>

---

## Capstone Connection

StoreForge's complete index strategy (see `capstone/storeforge/indexes.sql`):

| Index | Type | Columns | Purpose |
|---|---|---|---|
| `product_pkey` | B-tree | `id` | PK — auto-created |
| `idx_order_item_order` | B-tree | `order_id` | FK lookup — JOIN performance |
| `idx_order_item_product` | B-tree | `product_id` | FK lookup |
| `idx_order_open` | B-tree (partial) | `customer_id, created_at` | Open order pagination |
| `idx_product_active` | B-tree (partial) | `category_id, price` | Active product browse |
| `idx_product_cat_cover` | B-tree (covering) | `category_id, is_active` + `name, price` | Category page Index Only Scan |
| `idx_product_fts` | GIN | `search_vector` | Full-text search |
| `idx_product_name_trgm` | GIN | `name gin_trgm_ops` | Fuzzy search |
| `idx_product_attributes` | GIN | `attributes` | JSONB containment |
| `idx_customer_email_lower` | B-tree (expression) | `LOWER(email)` | Case-insensitive login lookup |
| `idx_audit_log_brin` | BRIN | `changed_at` | Time-range queries on large table |
