# Extensions

## Concept

PostgreSQL's extension system (`CREATE EXTENSION`) is one of its most powerful features: capabilities that would require a separate product in other databases are installable in seconds inside the same cluster. Extensions add functions, types, operators, and index methods that integrate natively with the query planner. The extensions covered here are the ones StoreForge uses in production — from password hashing to fuzzy search to scheduled jobs.

---

## Managing Extensions

```sql
-- Install an extension (requires superuser or pg_extension_owner):
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS pgcrypto;
CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE EXTENSION IF NOT EXISTS btree_gist;    -- already used in Module 05
CREATE EXTENSION IF NOT EXISTS ltree;
CREATE EXTENSION IF NOT EXISTS pg_stat_statements;

-- List installed extensions:
\dx

-- Or via catalog:
SELECT name, default_version, installed_version, comment
FROM pg_available_extensions
WHERE installed_version IS NOT NULL
ORDER BY name;

-- Drop if no longer needed:
DROP EXTENSION IF EXISTS hstore CASCADE;

-- Extensions install into a schema (usually public):
SHOW extwlist.extensions;  -- if allow-list is enabled
```

---

## uuid-ossp

Generates RFC 4122-compliant UUIDs. Most commonly used when you need globally unique identifiers without a central sequence:

```sql
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- v4: random UUID (most common):
SELECT uuid_generate_v4();
-- 3d2f0cb4-5e3f-4f12-9c4e-a5e8c0d1b2f3

-- v1: time-based (contains MAC address — avoid in multi-tenant setups):
SELECT uuid_generate_v1();

-- Use as a table default:
CREATE TABLE api_key (
    id          UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    customer_id INTEGER NOT NULL REFERENCES customer(id),
    token       TEXT    NOT NULL UNIQUE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at  TIMESTAMPTZ
);

-- Note: PostgreSQL 13+ has gen_random_uuid() built-in (no extension needed):
SELECT gen_random_uuid();
```

---

## pgcrypto

Provides cryptographic hash functions, symmetric/asymmetric encryption, and password hashing with bcrypt — essential for storing credentials:

```sql
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- Password hashing with bcrypt (cost factor 10 = ~100ms per hash):
-- Never store plaintext passwords. Always use bcrypt or Argon2.

-- Create the credential store:
CREATE TABLE customer_credential (
    customer_id   INTEGER     PRIMARY KEY REFERENCES customer(id) ON DELETE CASCADE,
    password_hash TEXT        NOT NULL,
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Store a password:
INSERT INTO customer_credential (customer_id, password_hash)
VALUES (1, crypt('mysecretpassword', gen_salt('bf', 10)));
-- 'bf' = blowfish (bcrypt), 10 = cost factor

-- Verify a password (timing-safe comparison):
SELECT EXISTS (
    SELECT 1
    FROM customer_credential
    WHERE customer_id = 1
      AND password_hash = crypt('mysecretpassword', password_hash)
) AS is_valid;
-- true

SELECT EXISTS (
    SELECT 1
    FROM customer_credential
    WHERE customer_id = 1
      AND password_hash = crypt('wrongpassword', password_hash)
) AS is_valid;
-- false

-- Generate a secure random API token:
SELECT encode(gen_random_bytes(32), 'hex') AS api_token;

-- Symmetric encryption (for less critical data):
SELECT pgp_sym_encrypt('sensitive note', 'encryption-key-keep-secret');
SELECT pgp_sym_decrypt(
    pgp_sym_encrypt('sensitive note', 'my-key'),
    'my-key'
);
```

---

## pg_trgm

Provides trigram-based similarity matching — the foundation for fuzzy search and fast ILIKE on large text columns:

```sql
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- Similarity score (0..1):
SELECT similarity('headphones', 'headphone');  -- ~0.78
SELECT similarity('headphones', 'keyboard');   -- ~0.0

-- GIN index for fast trigram matching:
CREATE INDEX CONCURRENTLY idx_product_name_trgm
    ON product USING GIN (name gin_trgm_ops);

CREATE INDEX CONCURRENTLY idx_product_description_trgm
    ON product USING GIN (description gin_trgm_ops);

-- Fuzzy search using ILIKE (uses GIN index automatically):
SELECT id, name, price
FROM product
WHERE name ILIKE '%wirelss headphon%'  -- typo: 'wirelss' instead of 'wireless'
  AND is_active;
-- No results with plain LIKE; trigram makes it possible

-- Explicit similarity query:
SELECT id, name, price, similarity(name, 'wireles hedphones') AS sim
FROM product
WHERE name % 'wireles hedphones'  -- % operator uses trigram index
  AND is_active
ORDER BY sim DESC
LIMIT 10;

-- Tune the similarity threshold (default 0.3):
SET pg_trgm.similarity_threshold = 0.25;  -- per session

-- Combined FTS + trigram fallback in one query:
SELECT id, name, price,
       GREATEST(
           ts_rank(search_vector, websearch_to_tsquery('english', 'wireless headphones')),
           similarity(name, 'wireless headphones')
       ) AS score
FROM product
WHERE (
    search_vector @@ websearch_to_tsquery('english', 'wireless headphones')
    OR name % 'wireless headphones'
)
  AND is_active
ORDER BY score DESC
LIMIT 20;
```

---

## ltree

Provides a `ltree` data type for storing and querying hierarchical label tree paths — far more powerful than the adjacency list `parent_id` pattern:

```sql
CREATE EXTENSION IF NOT EXISTS ltree;

-- Add a path column to category:
ALTER TABLE category ADD COLUMN path ltree;

-- Populate paths for existing categories:
UPDATE category SET path = 'electronics' WHERE slug = 'electronics';
UPDATE category SET path = 'electronics.phones' WHERE slug = 'phones';
UPDATE category SET path = 'electronics.phones.smartphones' WHERE slug = 'smartphones';
UPDATE category SET path = 'clothing' WHERE slug = 'clothing';
UPDATE category SET path = 'clothing.mens' WHERE slug = 'mens-clothing';

-- Create a GiST index for fast ancestor/descendant queries:
CREATE INDEX idx_category_path_gist ON category USING GIST (path);
CREATE INDEX idx_category_path_btree ON category USING BTREE (path);

-- Find all descendants of 'electronics' (using @> / <@ operators):
SELECT name, path
FROM category
WHERE path <@ 'electronics'  -- path is a descendant of 'electronics'
ORDER BY path;

-- Find the parent of 'smartphones':
SELECT name, path
FROM category
WHERE path = subpath('electronics.phones.smartphones', 0, -1);
-- 'electronics.phones'

-- Find depth:
SELECT name, nlevel(path) AS depth
FROM category ORDER BY path;

-- Pattern matching with lquery:
SELECT name FROM category
WHERE path ~ '*.phones.*';  -- any path containing 'phones'

-- Count products per top-level category and all descendants:
SELECT c.name, COUNT(p.id) AS product_count
FROM category c
JOIN category descendant ON descendant.path <@ c.path
JOIN product p ON p.category_id = descendant.id
WHERE nlevel(c.path) = 1  -- top-level only
GROUP BY c.id, c.name
ORDER BY product_count DESC;
```

---

## pg_stat_statements

Tracks execution statistics for every normalised SQL statement — the first tool to reach for when investigating slow queries:

```sql
CREATE EXTENSION IF NOT EXISTS pg_stat_statements;

-- Requires postgresql.conf:
-- shared_preload_libraries = 'pg_stat_statements'
-- pg_stat_statements.track = all  (or 'top' for top-level only)

-- Show top 10 slowest queries by total time:
SELECT
    substring(query, 1, 80)   AS query_short,
    calls,
    ROUND(total_exec_time::NUMERIC, 2)  AS total_ms,
    ROUND(mean_exec_time::NUMERIC, 2)   AS avg_ms,
    rows
FROM pg_stat_statements
ORDER BY total_exec_time DESC
LIMIT 10;

-- Find queries with high average time (optimisation targets):
SELECT
    substring(query, 1, 80) AS query_short,
    calls,
    ROUND(mean_exec_time::NUMERIC, 2) AS avg_ms
FROM pg_stat_statements
WHERE calls > 50
ORDER BY mean_exec_time DESC
LIMIT 10;

-- Reset counters:
SELECT pg_stat_statements_reset();
```

---

## pg_cron

Runs SQL or functions on a cron schedule inside the database (requires `shared_preload_libraries = 'pg_cron'`):

```sql
-- pg_cron is typically available on managed platforms (RDS, Supabase, etc.)
-- and can be compiled for self-hosted PostgreSQL.

-- Schedule a nightly job to refresh the product stats matview:
SELECT cron.schedule(
    'refresh-product-stats',       -- job name
    '0 2 * * *',                   -- cron expression: 02:00 every day
    $$REFRESH MATERIALIZED VIEW CONCURRENTLY product_stats$$
);

-- Schedule weekly order total reconciliation:
SELECT cron.schedule(
    'reconcile-order-totals',
    '0 3 * * 0',                   -- 03:00 every Sunday
    $$
    UPDATE "order" o
    SET total_amount = (
        SELECT COALESCE(SUM(quantity * unit_price), 0) FROM order_item WHERE order_id = o.id
    )
    WHERE o.updated_at > NOW() - INTERVAL '7 days'
    $$
);

-- List scheduled jobs:
SELECT * FROM cron.job;

-- View execution history:
SELECT * FROM cron.job_run_details ORDER BY start_time DESC LIMIT 20;

-- Remove a job:
SELECT cron.unschedule('refresh-product-stats');
```

---

## Try It Yourself

```sql
-- 1. Install uuid-ossp and pgcrypto (or verify they're already installed with \dx).
--    Create the customer_credential table.
--    Insert a hashed password for customer with id = 1.
--    Write a query that returns TRUE if the password 'secret123' matches, FALSE otherwise.

-- 2. Install pg_trgm and create a GIN trigram index on product.name.
--    Search for products matching 'wireles headphon' (with typos).
--    Compare results with and without the % operator.

-- 3. Install ltree.
--    Add a path column to category. Populate at least 4 paths with real hierarchy.
--    Query: find all descendants of your top-level category.
--    Query: count products in each top-level category including all descendants.
```

<details>
<summary>Show solutions</summary>

```sql
-- 1. pgcrypto + customer_credential:
CREATE EXTENSION IF NOT EXISTS pgcrypto;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TABLE IF NOT EXISTS customer_credential (
    customer_id   INTEGER PRIMARY KEY REFERENCES customer(id) ON DELETE CASCADE,
    password_hash TEXT    NOT NULL,
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

INSERT INTO customer_credential (customer_id, password_hash)
VALUES (1, crypt('secret123', gen_salt('bf', 10)))
ON CONFLICT (customer_id) DO UPDATE
    SET password_hash = EXCLUDED.password_hash, updated_at = NOW();

-- Verify:
SELECT (password_hash = crypt('secret123', password_hash)) AS matches
FROM customer_credential WHERE customer_id = 1;
-- true

SELECT (password_hash = crypt('wrongpass', password_hash)) AS matches
FROM customer_credential WHERE customer_id = 1;
-- false

-- 2. pg_trgm fuzzy search:
CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_product_name_trgm
    ON product USING GIN (name gin_trgm_ops);

-- Fuzzy with % operator:
SELECT id, name, similarity(name, 'wireles headphon') AS sim
FROM product
WHERE name % 'wireles headphon' AND is_active
ORDER BY sim DESC;

-- Without trigram for comparison:
SELECT id, name FROM product WHERE name ILIKE '%wireles headphon%';

-- 3. ltree hierarchy:
CREATE EXTENSION IF NOT EXISTS ltree;
ALTER TABLE category ADD COLUMN IF NOT EXISTS path ltree;

UPDATE category SET path = 'electronics'                   WHERE slug = 'electronics';
UPDATE category SET path = 'electronics.phones'            WHERE slug = 'phones';
UPDATE category SET path = 'electronics.phones.smartphones' WHERE slug = 'smartphones';
UPDATE category SET path = 'clothing'                      WHERE slug = 'clothing';

CREATE INDEX IF NOT EXISTS idx_category_path ON category USING GIST (path);

-- All descendants of electronics:
SELECT name, path FROM category WHERE path <@ 'electronics' ORDER BY path;

-- Products per top-level category including descendants:
SELECT c.name, COUNT(p.id) AS products
FROM category c
JOIN category d ON d.path <@ c.path
JOIN product p ON p.category_id = d.id
WHERE nlevel(c.path) = 1
GROUP BY c.id, c.name
ORDER BY products DESC;
```

</details>

---

## Capstone Connection

StoreForge extension stack:

| Extension | Purpose | Used In |
|---|---|---|
| `uuid-ossp` | UUID PKs for API keys | `api_key` table |
| `pgcrypto` | bcrypt password hashing | `customer_credential` table |
| `pg_trgm` | Fuzzy product name search | `product` name/description GIN index |
| `btree_gist` | Exclusion constraints | `product_discount` overlap check |
| `ltree` | Category hierarchy | `category.path` column |
| `pg_stat_statements` | Query performance profiling | Module 08 — Performance Tuning |

Every extension is installed with `IF NOT EXISTS` in the schema migration and documented in `capstone/storeforge/schema.sql`.
