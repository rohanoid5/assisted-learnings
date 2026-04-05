# PostgreSQL vs The World: Understanding Where PostgreSQL Fits

## Concept

PostgreSQL is not the only database, and it's not always the right choice. But understanding what makes it distinctive — and how it compares to MySQL, SQL Server, and document databases — gives you a mental model for knowing when PostgreSQL is the right tool and how to use its unique features effectively.

---

## PostgreSQL vs MySQL: The Two Pillars of Open-Source SQL

Both are mature, widely deployed, and free. The differences matter when choosing and when migrating:

| Feature | PostgreSQL | MySQL / MariaDB |
|---|---|---|
| **Standards compliance** | Excellent (SQL:2019) | Partial; many extensions |
| **ACID compliance** | Full, all engines | Full (InnoDB engine only) |
| **JSON support** | JSONB — binary, indexed, operators | JSON — stored as text; limited indexing |
| **Full-text search** | Built-in `tsvector`, GiN indexes | Built-in but less powerful |
| **Window functions** | Full support (SQL:2003+) | Full support (MySQL 8+) |
| **CTEs** | Full support, writeable CTEs | Full support (MySQL 8+) |
| **Partial indexes** | ✅ | ❌ |
| **Materialized views** | ✅ | ❌ |
| **Custom types / domains** | ✅ | ❌ |
| **Table inheritance** | ✅ | ❌ |
| **Declarative partitioning** | ✅ | ✅ |
| **Row-level security (RLS)** | ✅ | ❌ |
| **Extensions** | Rich (`PostGIS`, `pg_trgm`, `uuid-ossp`, `pgvector`) | Limited |
| **Case-sensitive identifiers** | Yes (quoted) | Configurable (often case-insensitive) |
| **String quoting** | Single quotes for strings | Single *or* double quotes for strings |
| **Boolean type** | Native `BOOLEAN` | `TINYINT(1)` |
| **Default NULL ordering** | `NULLS LAST` (ASC), `NULLS FIRST` (DESC) | `NULLS` sort first in ASC |
| **Auto-increment** | `GENERATED ALWAYS AS IDENTITY` or `SERIAL` | `AUTO_INCREMENT` |
| **Schema support** | Full namespaced schemas | Schemas ≈ databases |

### Key syntax differences

```sql
-- MySQL auto-increment:
CREATE TABLE product (
  id INT AUTO_INCREMENT PRIMARY KEY
);

-- PostgreSQL equivalent:
CREATE TABLE product (
  id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY
);
```

```sql
-- MySQL: double quotes work for strings
SELECT * FROM product WHERE name = "Headphones";

-- PostgreSQL: single quotes only for strings, double quotes for identifiers
SELECT * FROM product WHERE name = 'Headphones';
SELECT * FROM "product" WHERE "name" = 'Headphones';
```

```sql
-- MySQL LIMIT with offset:
SELECT * FROM product LIMIT 10 OFFSET 20;

-- PostgreSQL supports both syntaxes:
SELECT * FROM product LIMIT 10 OFFSET 20;     -- same syntax ✅
SELECT * FROM product OFFSET 20 ROWS FETCH NEXT 10 ROWS ONLY;  -- SQL standard
```

---

## PostgreSQL vs SQL Server

SQL Server (Microsoft) is an enterprise-grade relational database common in Windows/.NET environments.

| Feature | PostgreSQL | SQL Server |
|---|---|---|
| **License** | Open-source (PostgreSQL License) | Proprietary (expensive) |
| **Platform** | Linux, macOS, Windows, Docker | Windows-first; Linux support added |
| **Store procedures** | PL/pgSQL | T-SQL |
| **Identity columns** | `GENERATED ALWAYS AS IDENTITY` | `IDENTITY(1,1)` |
| **String concat** | `\|\|` or `CONCAT()` | `+` or `CONCAT()` |
| **Pagination** | `LIMIT / OFFSET` | `OFFSET / FETCH NEXT` (SQL:2008) |
| **Table variables** | CTEs + temp tables | `DECLARE @t TABLE (...)` |
| **JSON support** | JSONB (first-class type) | JSON (text-based, limited) |
| **Replication** | Streaming + logical (built-in) | AlwaysOn AG (enterprise license) |
| **Partitioning** | Declarative (built-in) | Partitioning (enterprise license) |
| **Full-text** | `tsvector` + GiN | Full-Text Search service |

```sql
-- SQL Server pagination:
SELECT * FROM product
ORDER BY id
OFFSET 20 ROWS FETCH NEXT 10 ROWS ONLY;

-- PostgreSQL supports both:
SELECT * FROM product ORDER BY id OFFSET 20 LIMIT 10;
```

---

## PostgreSQL vs MongoDB (Document DB)

This is the most common "when do I choose relational vs. NoSQL?" question.

| Aspect | PostgreSQL | MongoDB |
|---|---|---|
| **Data model** | Tables with typed columns | Documents (JSON) in collections |
| **Schema** | Enforced (DDL required) | Flexible (no upfront schema) |
| **Relationships** | Foreign keys, JOINs | Embedding (nest) or manual references |
| **ACID** | Full, including multi-table transactions | Full ACID (since 4.0) |
| **Horizontal scaling** | Read replicas; vertical scaling | Advanced sharding built-in |
| **Query language** | SQL (declarative, powerful) | MQL (MongoDB Query Language) |
| **JSON** | JSONB — query, index, extract fields | Native BSON (binary JSON) |
| **Full-text search** | `tsvector`, `GIN` | Atlas Search (add-on) |
| **Aggregation** | SQL GROUP BY; window functions | Aggregation pipeline |

### PostgreSQL can store documents too

The real insight for application developers: **PostgreSQL's JSONB column type lets you store document-style data inside a relational table**. You get the best of both worlds.

```sql
-- Store arbitrary product attributes as JSONB:
CREATE TABLE product (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name       TEXT NOT NULL,
    price      NUMERIC(10,2) NOT NULL,
    attributes JSONB NOT NULL DEFAULT '{}'
    -- attributes varies by category: {"color": "red", "size": "L"} for apparel
    -- {"wattage": 60, "base": "E27"} for lighting
    -- {"brand": "Sony", "impedance": 32} for headphones
);

-- Query inside JSON:
SELECT name, attributes->>'color' AS color
FROM product
WHERE attributes->>'color' = 'red';

-- Full-text search on JSON value:
CREATE INDEX ON product USING GIN (attributes);

-- Filter by JSON structure:
SELECT * FROM product
WHERE attributes ? 'wattage';  -- has a 'wattage' key
```

---

## PostgreSQL's Unique Features

These are features that genuinely differentiate PostgreSQL and are worth learning:

### Extensions
```sql
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";     -- UUID generation
CREATE EXTENSION IF NOT EXISTS pg_trgm;          -- trigram similarity (fuzzy search)
CREATE EXTENSION IF NOT EXISTS pgvector;         -- vector similarity search (AI/ML)
CREATE EXTENSION IF NOT EXISTS postgis;          -- geospatial queries
CREATE EXTENSION IF NOT EXISTS pg_stat_statements; -- query performance tracking
```

### Full-text search (no external tool needed)
```sql
-- Add a search vector column to product
ALTER TABLE product ADD COLUMN search_vector tsvector;

UPDATE product SET search_vector =
    to_tsvector('english', COALESCE(name, '') || ' ' || COALESCE(description, ''));

CREATE INDEX product_search_idx ON product USING GIN(search_vector);

-- Search:
SELECT name FROM product
WHERE search_vector @@ plainto_tsquery('english', 'wireless noise cancelling');
```

### GENERATED columns
```sql
-- Automatically maintained derived column
ALTER TABLE product
ADD COLUMN name_lower TEXT GENERATED ALWAYS AS (LOWER(name)) STORED;
```

### Table inheritance
```sql
-- Base table + specialized tables
CREATE TABLE vehicle (id BIGINT PRIMARY KEY, make TEXT, model TEXT);
CREATE TABLE car (doors INT) INHERITS (vehicle);
CREATE TABLE truck (payload_kg NUMERIC) INHERITS (vehicle);
```

### `RETURNING` clause
```sql
-- Insert and get the result back in one round trip
INSERT INTO customer (name, email)
VALUES ('Alice', 'alice@example.com')
RETURNING id, created_at;
```

---

## Try It Yourself

In your StoreForge database, try these PostgreSQL-specific features:

```sql
-- 1. Use RETURNING to insert and get the new id:
INSERT INTO product (name, price, stock_quantity, category_id, attributes)
VALUES ('Bluetooth Speaker', 49.99, 75, 3, '{"color": "black", "battery_hours": 12}')
RETURNING id, name;

-- 2. Query the JSONB attributes:
SELECT name, attributes->>'color' AS color
FROM product
WHERE attributes ? 'color';

-- 3. Compare NULL handling to regular values:
SELECT 
    NULL = NULL         AS null_equals_null,
    NULL IS NULL        AS null_is_null,
    COALESCE(NULL, 'default')  AS coalesce_result;

-- 4. Use GENERATED ALWAYS AS IDENTITY — try inserting a manual id:
-- (This should fail — why?)
INSERT INTO product (id, name, price, stock_quantity, category_id)
OVERRIDING USER VALUE
VALUES (999, 'Test', 1.00, 0, 1);
```

<details>
<summary>Expected observations</summary>

1. **RETURNING** — you get back the generated `id` and the `created_at` timestamp set by the `DEFAULT NOW()` — no separate `SELECT` needed.

2. **JSONB query** — the `?` operator checks for key existence. The `->>` operator extracts as text. These are PostgreSQL-specific operators; MySQL doesn't have equivalents without a JSON_EXTRACT function.

3. **NULL behavior** — `NULL = NULL` is `NULL` (not `TRUE`), `NULL IS NULL` is `TRUE`. `COALESCE` returns the first non-null value in its argument list.

4. **GENERATED ALWAYS AS IDENTITY** — `OVERRIDING USER VALUE` lets you bypass the automatic generation for seeding. In normal operation, you cannot insert a manual value; the system always generates one. This is stricter than MySQL's `AUTO_INCREMENT`.

</details>

---

## Capstone Connection

StoreForge exploits PostgreSQL-specific features throughout its design:

- `product.attributes JSONB` — stores category-specific metadata without extra tables
- `product.search_vector tsvector` — enables full-text search without Elasticsearch
- `RETURNING id` used in the `place_order()` stored procedure to chain inserts
- `BIGINT GENERATED ALWAYS AS IDENTITY` on every table's primary key
- `row_number()` window function in sales reports (Module 04)
- Partitioning `order` by `created_at` year for performance at scale (Module 08)
