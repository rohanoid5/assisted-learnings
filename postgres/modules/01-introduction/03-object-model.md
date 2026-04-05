# The Object Model: Databases, Schemas, Tables, Rows, and Columns

## Concept

PostgreSQL organizes data in a hierarchy: **server → database → schema → table → row → column**. Understanding this hierarchy prevents confusion when you see connection strings, permission errors, or statements like `SELECT * FROM public.product`.

PostgreSQL also extends the standard SQL "object model" with its own constructs: types, sequences, functions, views, indexes, and extensions — all of which live within schemas and are first-class objects you can manipulate with SQL.

---

## The Hierarchy

```
PostgreSQL Server (port 5432)
├── Database: postgres            ← default maintenance database
├── Database: storeforge_dev      ← your development database
│   ├── Schema: public            ← default schema
│   │   ├── Table: customer
│   │   ├── Table: product
│   │   ├── Table: order
│   │   ├── View: product_summary
│   │   ├── Sequence: customer_id_seq
│   │   └── Function: place_order(...)
│   └── Schema: analytics         ← optional: separate namespace
│       ├── View: monthly_revenue
│       └── Materialized View: product_stats
└── Database: storeforge_test
    └── Schema: public
        └── (same tables, isolated data)
```

Each level of this hierarchy has distinct properties:

| Level | Key Properties |
|-------|---------------|
| **Server** | Runs on a port; has global config (`postgresql.conf`, `pg_hba.conf`) |
| **Database** | Fully isolated; connections target one database; can't JOIN across databases |
| **Schema** | Namespace within a database; used for multi-tenancy, separation of concerns |
| **Table** | Stores rows; belongs to exactly one schema |
| **Row** | One record; ordered set of column values |
| **Column** | Has a name, data type, and optional constraints |

---

## Databases

A **database** in PostgreSQL is a fully isolated environment. Connections target a specific database — you cannot `JOIN` between tables in different databases in a single query (unlike SQL Server's `db1.schema.table` syntax).

```sql
-- List all databases on the server
\l

-- Create the StoreForge development database
CREATE DATABASE storeforge_dev
  OWNER storeforge
  ENCODING 'UTF8'
  LC_COLLATE 'en_US.UTF-8'
  LC_CTYPE 'en_US.UTF-8';

-- Connect to it
\c storeforge_dev
```

**Typical setup for a project:**
- `storeforge_dev` — development database
- `storeforge_test` — testing database (isolated, can be dropped and recreated freely)
- `storeforge` — production database

---

## Schemas

A **schema** is a namespace inside a database. Every table, view, function, and sequence belongs to a schema. The default schema is `public`.

```sql
-- List schemas in the current database
\dn

-- Create a separate analytics schema
CREATE SCHEMA analytics;

-- Create a table inside a specific schema
CREATE TABLE analytics.monthly_revenue (
    month       DATE,
    revenue     NUMERIC(12, 2),
    order_count INTEGER
);

-- Reference it with schema-qualified name
SELECT * FROM analytics.monthly_revenue;

-- Set search_path to avoid qualifying every table name
SET search_path TO public, analytics;
SELECT * FROM monthly_revenue;  -- now found without schema prefix
```

**Why use multiple schemas?**
- Multi-tenant applications: one schema per tenant
- Separating operational tables from analytics views
- Namespace collision avoidance when combining extensions

---

## Tables

A **table** is a named collection of rows with a fixed set of columns. Tables are the fundamental storage unit.

```sql
-- Create the customer table
CREATE TABLE customer (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    email         VARCHAR(254) NOT NULL UNIQUE,
    name          VARCHAR(200) NOT NULL,
    password_hash TEXT         NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- Inspect its structure
\d customer
```

Tables can also be:
- **Temporary** — exist only for the current session: `CREATE TEMPORARY TABLE ...`
- **Partitioned** — split into sub-tables for performance (Module 08)
- **Foreign** — wrappers around external data sources (postgres_fdw)

---

## Rows and Columns

A **row** is a single record in a table. Each row has exactly the columns defined by the table's schema.

A **column** has:
- A **name** (must be unique within the table)
- A **data type** (covered in detail in [02-data-types.md](02-data-types.md))
- Optional **constraints** (`NOT NULL`, `UNIQUE`, `CHECK`, default value)

```sql
-- Inspect what columns a table has
\d customer

-- Or query the information schema
SELECT column_name, data_type, is_nullable, column_default
FROM information_schema.columns
WHERE table_schema = 'public'
  AND table_name   = 'customer'
ORDER BY ordinal_position;
```

---

## Queries as First-Class Objects

PostgreSQL treats **queries** as objects that can be named and reused:

```sql
-- A view is a named query
CREATE VIEW product_summary AS
SELECT p.id, p.name, p.price, c.name AS category,
       AVG(r.rating)::NUMERIC(3,2) AS avg_rating
FROM product p
LEFT JOIN category c ON c.id = p.category_id
LEFT JOIN review r   ON r.product_id = p.id
GROUP BY p.id, p.name, p.price, c.name;

-- A materialized view stores the result
CREATE MATERIALIZED VIEW product_stats AS
SELECT /* same query */;

-- Use either like a table
SELECT * FROM product_summary WHERE avg_rating > 4.0;
```

---

## Data Types as Objects

Unlike MySQL, PostgreSQL allows you to create **custom data types**:

```sql
-- Enum type (named set of values)
CREATE TYPE order_status AS ENUM (
    'PENDING', 'CONFIRMED', 'SHIPPED', 'DELIVERED', 'CANCELLED'
);

-- Use it in a table
CREATE TABLE "order" (
    id     BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    status order_status NOT NULL DEFAULT 'PENDING'
);

-- Composite type
CREATE TYPE money_amount AS (
    amount   NUMERIC(12,2),
    currency CHAR(3)
);
```

Enums provide both validation (only listed values accepted) and documentation (the valid states are part of the schema).

---

## Try It Yourself

Connect to your PostgreSQL instance (Module 02 covers this in detail) and explore the object hierarchy:

```sql
-- 1. List all databases
\l

-- 2. Create and connect to storeforge_dev
CREATE DATABASE storeforge_dev;
\c storeforge_dev

-- 3. List schemas
\dn

-- 4. Create a test table
CREATE TABLE test_object_model (
    id   SERIAL PRIMARY KEY,
    note TEXT
);

-- 5. Confirm it exists
\dt

-- 6. Check its definition
\d test_object_model

-- 7. Clean up
DROP TABLE test_object_model;
```

<details>
<summary>Expected psql output for \dt</summary>

```
          List of relations
 Schema |       Name        | Type  |  Owner
--------+-------------------+-------+----------
 public | test_object_model | table | postgres
(1 row)
```

And `\d test_object_model`:
```
              Table "public.test_object_model"
 Column | Type    | Collation | Nullable | Default
--------+---------+-----------+----------+-----------------------------------
 id     | integer |           | not null | nextval('test_object_model_id_seq'::regclass)
 note   | text    |           |          |
Indexes:
    "test_object_model_pkey" PRIMARY KEY, btree (id)
```

</details>

---

## Capstone Connection

The StoreForge object hierarchy:

- **Database:** `storeforge_dev` (dev), `storeforge_test` (tests), `storeforge` (prod)
- **Schema:** `public` (operational tables) — optionally `analytics` for reporting views  
- **Tables:** `customer`, `category`, `product`, `address`, `order`, `order_item`, `review`, `audit_log`
- **Custom types:** `order_status` enum (`PENDING/CONFIRMED/SHIPPED/DELIVERED/CANCELLED`)
- **Sequences:** Auto-generated for every `GENERATED ALWAYS AS IDENTITY` column
- **Views:** `product_summary` (created in Module 05)
- **Functions + triggers:** `place_order`, inventory check trigger, audit log trigger (Module 06)
