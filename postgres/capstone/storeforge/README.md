# StoreForge — Capstone Project

StoreForge is the running capstone for the PostgreSQL Learning Path. It is a
production-realistic e-commerce database that ties together every concept
taught across the 9 modules — from basic SQL to infrastructure and security.

---

## Overview

| File            | Purpose                              | Module(s) Referenced |
|-----------------|--------------------------------------|----------------------|
| `schema.sql`    | DDL — tables, types, constraints     | 03, 04, 05           |
| `seed.sql`      | Sample data for all exercises        | 03, 04               |
| `functions.sql` | Triggers, business logic, search     | 06, 07               |
| `security.sql`  | Roles, grants, RLS policies          | 07                   |
| `indexes.sql`   | All indexes with explanations        | 08                   |

---

## Prerequisites

- **PostgreSQL 16+**
- **Extensions** (installed automatically by the SQL files):
  - `pgcrypto` — password hashing (bcrypt) and encryption
  - `pg_trgm` — trigram similarity for fuzzy search
- Docker is the recommended way to run a local instance:

```bash
docker run -d --name storeforge-postgres \
  -e POSTGRES_USER=storeforge \
  -e POSTGRES_PASSWORD=secret \
  -e POSTGRES_DB=storeforge_dev \
  -p 5432:5432 \
  -v storeforge_pgdata:/var/lib/postgresql/data \
  postgres:16-alpine
```

Connect with:

```bash
psql -h localhost -U storeforge -d storeforge_dev
```

---

## Setup Steps

Run the files in this exact order:

```bash
PGPASSWORD=secret psql -h localhost -U storeforge -d storeforge_dev \
  -f schema.sql

PGPASSWORD=secret psql -h localhost -U storeforge -d storeforge_dev \
  -f seed.sql

PGPASSWORD=secret psql -h localhost -U storeforge -d storeforge_dev \
  -f functions.sql

PGPASSWORD=secret psql -h localhost -U storeforge -d storeforge_dev \
  -f security.sql

# indexes.sql uses CONCURRENTLY — must NOT run inside a transaction
PGPASSWORD=secret psql -h localhost -U storeforge -d storeforge_dev \
  --single-transaction=off \
  -f indexes.sql
```

---

## Verification

After running all files, confirm the setup:

```sql
-- Row counts
SELECT relname AS table, n_live_tup AS approx_rows
FROM pg_stat_user_tables
ORDER BY relname;

-- Installed extensions
SELECT name, installed_version
FROM pg_available_extensions
WHERE installed_version IS NOT NULL;

-- All indexes
SELECT tablename, indexname
FROM pg_indexes
WHERE schemaname = 'public'
ORDER BY tablename, indexname;

-- Smoke-test functions
SELECT place_order(1, 1, '[{"product_id": 14, "quantity": 1}]');
SELECT * FROM product_search('laptop', 5);
SELECT * FROM customer_summary(1);
SELECT verify_password(1, 'password123');  -- should return TRUE
```

---

## Schema Diagram

```
category ─────────────────────────────────────────────────────┐
  │ (self-ref parent_id)                                       │
  └──< product                                                 │
           │                                                   │
           └──< order_item >──── order >── customer ──< address│
                                   │                      │    │
                              (shipping_address_id)       └────┘
           └──< review >──── customer

customer ──< customer_credential   (1:1, bcrypt password)
customer ──< payment_method        (1:N, encrypted card data)
[all writes] ──> audit_log         (append-only changelog)
```

---

## Design Decisions

### JSONB for Product Attributes
Products in different categories have entirely different attribute shapes
(shoes have sizes, laptops have RAM, tents have capacity). Rather than
maintaining a schema-per-category or a generic EAV table, `attributes JSONB`
provides flexibility while keeping querying simple. A GIN index on this column
makes `@>` containment queries fast.

**Trade-off:** No relational integrity on attribute values; enforce shape in
the application layer or use JSON Schema validation in a CHECK constraint.

### Generated TSVECTOR Column for Full-Text Search
The `product.search_vector` column is `GENERATED ALWAYS AS STORED`, combining
`name` (weight A) and `description` (weight B) into a pre-parsed tsvector.
This means FTS ranking is always up to date without an application-level step,
and a GIN index can be maintained automatically.

### Row-Level Security with Session Variables
RLS policies use `current_setting('app.customer_id', TRUE)` rather than a
dedicated column per session. The `TRUE` flag makes the function return NULL
(rather than raising an error) when the setting is not defined — useful for
background jobs that bypass RLS by running as `storeforge_admin` (`BYPASSRLS`).

The application server must always run `SET LOCAL app.customer_id = ?` inside
a transaction before touching RLS-protected tables.

### Encrypted Payment Data
`payment_method.card_number_enc` and `expiry_enc` store values encrypted with
`pgp_sym_encrypt()` from pgcrypto. The encryption key lives in the application
layer — PostgreSQL never sees the plaintext value, even in logs or backups.

### BRIN on audit_log
`audit_log` is append-only and rows are inserted in time order. A BRIN index
is 100–1000x smaller than a btree index on `changed_at` and answers
date-range queries with similar efficiency for this access pattern.

---

## Module Cross-References

| Concept                                     | Module                    | File(s)                        |
|---------------------------------------------|---------------------------|--------------------------------|
| CREATE TABLE, data types, constraints       | 03 — SQL Fundamentals     | schema.sql                     |
| JOINs, aggregates, CTEs, window functions   | 04 — Advanced SQL         | seed.sql (queries)             |
| Normalization, FK on-delete actions         | 05 — Database Design      | schema.sql                     |
| PL/pgSQL triggers and functions             | 06 — PL/pgSQL             | functions.sql                  |
| Roles, grants, RLS, pgcrypto               | 07 — Security             | security.sql, functions.sql    |
| EXPLAIN ANALYZE, indexes, query tuning      | 08 — Performance          | indexes.sql                    |
| Backup, replication, monitoring             | 09 — Infrastructure       | All files (targets for backup) |
