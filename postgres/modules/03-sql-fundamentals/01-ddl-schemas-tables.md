# DDL: Schemas, Tables, and Alterations

## Concept

**Data Definition Language (DDL)** is the subset of SQL used to define and modify the structure of your database — creating tables, altering columns, adding constraints, and managing schemas. Unlike DML (INSERT, UPDATE, DELETE), DDL operations are not easily reversible once committed, so understanding them well before running them matters.

---

## Schemas

A **schema** is a namespace within a database. It groups related tables, views, and functions.

```sql
-- Create a schema:
CREATE SCHEMA storeforge;

-- Create a schema owned by a specific role:
CREATE SCHEMA storeforge AUTHORIZATION storeforge_api;

-- List schemas:
\dn

-- Set your search path (which schemas to look in, in order):
SET search_path TO public;
SET search_path TO storeforge, public;   -- looks in storeforge first, then public

-- Make it persist for a user:
ALTER ROLE storeforge SET search_path TO public;

-- Drop a schema (only works if empty):
DROP SCHEMA storeforge;

-- Drop schema and everything in it:
DROP SCHEMA storeforge CASCADE;  -- ⚠️ destroys all tables/functions inside
```

For StoreForge, we use the default `public` schema. In multi-tenant applications, schemas per tenant are a common pattern.

---

## CREATE TABLE

```sql
CREATE TABLE customer (
    id          BIGINT GENERATED ALWAYS AS IDENTITY,
    name        TEXT NOT NULL,
    email       VARCHAR(255) NOT NULL,
    phone       VARCHAR(20),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),

    -- Constraints can be inline or at the end:
    PRIMARY KEY (id),
    UNIQUE (email),
    CONSTRAINT chk_email_format CHECK (email LIKE '%@%')
);
```

### IF NOT EXISTS

```sql
-- Safe to run repeatedly (idempotent):
CREATE TABLE IF NOT EXISTS customer ( ... );
```

### CREATE TABLE AS (from a query)

```sql
-- Create a table from a SELECT result (useful for snapshots/migrations):
CREATE TABLE product_backup AS
SELECT * FROM product WHERE created_at < '2024-01-01';

-- LIKE copies column definitions (but not constraints):
CREATE TABLE product_staging (LIKE product INCLUDING DEFAULTS);
```

---

## ALTER TABLE

The workhorse for schema evolution:

```sql
-- Add a column (safe on large tables — just updates catalog):
ALTER TABLE customer ADD COLUMN loyalty_points INTEGER NOT NULL DEFAULT 0;

-- Add a column with a volatile default (rewrites the table — use with caution):
ALTER TABLE customer ADD COLUMN joined_date DATE DEFAULT CURRENT_DATE;

-- Remove a column:
ALTER TABLE customer DROP COLUMN phone;
ALTER TABLE customer DROP COLUMN IF EXISTS phone;   -- safe if doesn't exist

-- Rename a column:
ALTER TABLE customer RENAME COLUMN name TO full_name;

-- Change a data type (requires no existing incompatible data):
ALTER TABLE customer ALTER COLUMN phone TYPE TEXT;

-- Set/drop a default:
ALTER TABLE customer ALTER COLUMN loyalty_points SET DEFAULT 10;
ALTER TABLE customer ALTER COLUMN loyalty_points DROP DEFAULT;

-- Set/drop NOT NULL:
ALTER TABLE customer ALTER COLUMN phone SET NOT NULL;
ALTER TABLE customer ALTER COLUMN phone DROP NOT NULL;

-- Rename the table:
ALTER TABLE customer RENAME TO client;

-- Add a constraint after creation:
ALTER TABLE customer ADD CONSTRAINT uq_customer_email UNIQUE (email);
ALTER TABLE customer ADD CONSTRAINT chk_loyalty_points CHECK (loyalty_points >= 0);

-- Drop a constraint:
ALTER TABLE customer DROP CONSTRAINT chk_loyalty_points;

-- Add a foreign key:
ALTER TABLE "order" ADD CONSTRAINT fk_order_customer
    FOREIGN KEY (customer_id) REFERENCES customer(id) ON DELETE RESTRICT;
```

### Adding a NOT NULL column to an existing table

A common challenge:

```sql
-- ❌ This fails if the table has rows:
ALTER TABLE product ADD COLUMN weight_kg NUMERIC(8,3) NOT NULL;
-- ERROR: column "weight_kg" contains null values

-- ✅ Three-step pattern for large/production tables:
-- Step 1: Add nullable with a default
ALTER TABLE product ADD COLUMN weight_kg NUMERIC(8,3) DEFAULT 0;
-- Step 2: Backfill existing rows (or set real values)
UPDATE product SET weight_kg = 0.5 WHERE weight_kg IS NULL;
-- Step 3: Add NOT NULL constraint (validates all rows first)
ALTER TABLE product ALTER COLUMN weight_kg SET NOT NULL;
```

---

## DROP TABLE

```sql
-- Drop a table:
DROP TABLE product;

-- Safe drop (no error if doesn't exist):
DROP TABLE IF EXISTS product;

-- Drop and also drop any foreign keys referencing this table:
DROP TABLE product CASCADE;   -- ⚠️ will also drop order_item.product_id FK

-- Drop multiple tables:
DROP TABLE IF EXISTS order_item, "order", customer CASCADE;
```

---

## TRUNCATE

Faster than `DELETE FROM table` for emptying a table — no row-by-row processing:

```sql
TRUNCATE product;                          -- remove all rows
TRUNCATE product RESTART IDENTITY;         -- also reset sequences
TRUNCATE product, order_item CASCADE;      -- truncate and cascade to referencing tables
```

⚠️ `TRUNCATE` is DDL (acquires ACCESS EXCLUSIVE lock) and cannot be filtered with WHERE.

---

## Temporary Tables

Exist only for the current session:

```sql
CREATE TEMP TABLE staging_products (LIKE product);

-- Load data into staging, validate, then merge:
INSERT INTO staging_products SELECT * FROM product WHERE category_id = 1;
-- ... process ...
DROP TABLE staging_products;  -- or just disconnect — auto-dropped
```

---

## The Full StoreForge DDL

Here's the complete table creation sequence for StoreForge (also in `capstone/storeforge/schema.sql`):

```sql
-- Custom types:
CREATE TYPE order_status AS ENUM (
    'pending', 'confirmed', 'shipped', 'delivered', 'cancelled', 'refunded'
);

-- Tables (in dependency order):
CREATE TABLE category (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name        VARCHAR(100) NOT NULL,
    slug        VARCHAR(100) NOT NULL UNIQUE,
    parent_id   BIGINT REFERENCES category(id) ON DELETE SET NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE customer (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name           TEXT NOT NULL,
    email          VARCHAR(255) NOT NULL UNIQUE,
    phone          VARCHAR(20),
    is_active      BOOLEAN NOT NULL DEFAULT true,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE address (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    customer_id BIGINT NOT NULL REFERENCES customer(id) ON DELETE CASCADE,
    line1       TEXT NOT NULL,
    line2       TEXT,
    city        TEXT NOT NULL,
    state       VARCHAR(100),
    country     CHAR(2) NOT NULL,
    postal_code VARCHAR(20) NOT NULL,
    is_default  BOOLEAN NOT NULL DEFAULT false
);

CREATE TABLE product (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    category_id    BIGINT NOT NULL REFERENCES category(id),
    name           VARCHAR(200) NOT NULL,
    slug           VARCHAR(200) NOT NULL UNIQUE,
    description    TEXT,
    price          NUMERIC(10,2) NOT NULL CHECK (price >= 0),
    stock_quantity INTEGER NOT NULL DEFAULT 0 CHECK (stock_quantity >= 0),
    attributes     JSONB NOT NULL DEFAULT '{}',
    is_active      BOOLEAN NOT NULL DEFAULT true,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE "order" (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    customer_id      BIGINT NOT NULL REFERENCES customer(id),
    shipping_address_id BIGINT REFERENCES address(id),
    status           order_status NOT NULL DEFAULT 'pending',
    total_amount     NUMERIC(10,2) NOT NULL CHECK (total_amount >= 0),
    notes            TEXT,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE order_item (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    order_id    BIGINT NOT NULL REFERENCES "order"(id) ON DELETE CASCADE,
    product_id  BIGINT NOT NULL REFERENCES product(id) ON DELETE RESTRICT,
    quantity    INTEGER NOT NULL CHECK (quantity > 0),
    unit_price  NUMERIC(10,2) NOT NULL CHECK (unit_price >= 0)
);

CREATE TABLE review (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    product_id  BIGINT NOT NULL REFERENCES product(id) ON DELETE CASCADE,
    customer_id BIGINT NOT NULL REFERENCES customer(id),
    rating      SMALLINT NOT NULL CHECK (rating BETWEEN 1 AND 5),
    comment     TEXT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (product_id, customer_id)  -- one review per customer per product
);

CREATE TABLE audit_log (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    table_name  TEXT NOT NULL,
    operation   TEXT NOT NULL CHECK (operation IN ('INSERT', 'UPDATE', 'DELETE')),
    row_id      BIGINT,
    old_data    JSONB,
    new_data    JSONB,
    changed_by  TEXT NOT NULL DEFAULT current_user,
    changed_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
```

---

## Try It Yourself

```sql
-- 1. Create the full StoreForge schema:
\i capstone/storeforge/schema.sql
-- or paste the DDL above

-- 2. Verify all tables:
\dt

-- 3. Describe the product table and check all constraints:
\d product

-- 4. Add an optional discount_percent column to product:
ALTER TABLE product
    ADD COLUMN discount_percent NUMERIC(5,2) DEFAULT 0
    CHECK (discount_percent BETWEEN 0 AND 100);

-- 5. Describe product again and verify the new column appeared:
\d product

-- 6. Drop the column (we won't need it in our schema):
ALTER TABLE product DROP COLUMN discount_percent;
```

<details>
<summary>Expected \dt output after schema creation</summary>

```
         List of relations
 Schema |    Name    | Type  |   Owner    
--------+------------+-------+------------
 public | address    | table | storeforge
 public | audit_log  | table | storeforge
 public | category   | table | storeforge
 public | customer   | table | storeforge
 public | order      | table | storeforge
 public | order_item | table | storeforge
 public | product    | table | storeforge
 public | review     | table | storeforge
(8 rows)
```

</details>

---

## Capstone Connection

The DDL above *is* the StoreForge schema. Understanding every line of it — the chosen data types, constraint names, `ON DELETE` behaviors, and ordering (dependencies first) — gives you the foundation for every module that follows.
