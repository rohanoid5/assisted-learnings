# Constraints Deep Dive

## Concept

Constraints are PostgreSQL's enforcement layer for business rules at the database level. You've already used the basics (PK, FK, NOT NULL, CHECK, UNIQUE). This lesson covers advanced constraint techniques: deferrable foreign keys, exclusion constraints, partial unique indexes, generated columns, and domain types — tools that let you encode complex rules directly into the schema.

---

## Deferrable Constraints

By default, constraints are checked immediately after each statement. Deferrable constraints can delay the check until `COMMIT`, enabling operations that would temporarily violate a constraint.

```sql
-- Example: You need to swap two slug values between products.
-- Immediate FK/UNIQUE check would fail at the first UPDATE.

-- Create a DEFERRABLE UNIQUE constraint:
ALTER TABLE product
    ADD CONSTRAINT uq_product_slug UNIQUE (slug)
    DEFERRABLE INITIALLY IMMEDIATE;
-- DEFERRABLE = can be deferred; INITIALLY IMMEDIATE = deferred only when explicitly set

-- Swap slugs without error:
BEGIN;
SET CONSTRAINTS uq_product_slug DEFERRED;
UPDATE product SET slug = 'temp-slug-999' WHERE id = 1;
UPDATE product SET slug = 'old-slug-1' WHERE id = 2;
UPDATE product SET slug = 'old-slug-2' WHERE id = 1;
COMMIT;
-- Constraint checked at COMMIT, not per-statement.

-- DEFERRABLE INITIALLY DEFERRED: always deferred unless explicitly set to IMMEDIATE:
ALTER TABLE order_item
    ADD CONSTRAINT fk_order_item_order
    FOREIGN KEY (order_id) REFERENCES "order"(id)
    DEFERRABLE INITIALLY DEFERRED;
```

---

## Exclusion Constraints

Exclusion constraints generalize UNIQUE constraints. While UNIQUE says "no two rows can have the same value", exclusion constraints say "no two rows can satisfy some operator relationship". They require a GiST or SP-GiST index.

```sql
-- Install btree_gist to use B-tree operators in exclusion constraints:
CREATE EXTENSION IF NOT EXISTS btree_gist;

-- Time-range booking: prevent overlapping reservations:
CREATE TABLE room_booking (
    id          SERIAL PRIMARY KEY,
    room_id     INTEGER NOT NULL,
    booked_for  TSTZRANGE NOT NULL,  -- time range type

    EXCLUDE USING GIST (
        room_id  WITH =,              -- same room
        booked_for WITH &&            -- overlapping time range
    )
);

-- Test:
INSERT INTO room_booking (room_id, booked_for)
VALUES (1, '[2024-06-01 09:00, 2024-06-01 11:00)');

INSERT INTO room_booking (room_id, booked_for)
VALUES (1, '[2024-06-01 10:00, 2024-06-01 12:00)');
-- ERROR: conflicting key value violates exclusion constraint

-- Different room is fine:
INSERT INTO room_booking (room_id, booked_for)
VALUES (2, '[2024-06-01 10:00, 2024-06-01 12:00)');  -- OK

-- StoreForge use case: prevent duplicate active discount periods for a product:
CREATE TABLE product_discount (
    id         SERIAL PRIMARY KEY,
    product_id INTEGER NOT NULL REFERENCES product(id),
    discount   NUMERIC(5,2) NOT NULL CHECK (discount > 0 AND discount < 100),
    valid_for  TSTZRANGE NOT NULL,

    EXCLUDE USING GIST (
        product_id WITH =,
        valid_for  WITH &&
    )
);
```

---

## Partial Unique Indexes as Constraints

A unique index can apply to only a subset of rows (filtered by a `WHERE` clause). This is more flexible than a table-level UNIQUE constraint.

```sql
-- Only one default address per customer (partial unique):
CREATE UNIQUE INDEX uq_default_address_per_customer
    ON address (customer_id)
    WHERE is_default = true;
-- Two addresses for the same customer: OK as long as only one has is_default = true.

-- Only one active email per customer (soft-delete aware):
CREATE UNIQUE INDEX uq_customer_active_email
    ON customer (email)
    WHERE deleted_at IS NULL;
-- Deleted customers can share email with new accounts.

-- Unique slug only among active products:
CREATE UNIQUE INDEX uq_product_slug_active
    ON product (slug)
    WHERE deleted_at IS NULL;
```

---

## Check Constraints: Advanced Patterns

```sql
-- Cross-column check:
ALTER TABLE product_discount
    ADD CONSTRAINT chk_discount_range
    CHECK (lower(valid_for) < upper(valid_for));

-- Conditional check (only check when condition is true):
ALTER TABLE "order"
    ADD CONSTRAINT chk_shipped_has_address
    CHECK (
        status != 'shipped'                  -- If not shipped, no requirement
        OR shipping_address_id IS NOT NULL   -- If shipped, address must exist
    );

-- Complex business rule:
ALTER TABLE coupon
    ADD CONSTRAINT chk_coupon_has_one_discount_type
    CHECK (
        (discount_percent IS NOT NULL AND discount_fixed IS NULL) OR
        (discount_percent IS NULL AND discount_fixed IS NOT NULL)
    );
```

---

## NOT VALID: Adding Constraints Without Full-Table Scan

For large tables, adding a constraint with validation can lock the table for a long time. `NOT VALID` adds the constraint for future rows immediately, then validates existing rows separately.

```sql
-- Step 1: Add constraint for new rows only (fast, no lock):
ALTER TABLE order_item
    ADD CONSTRAINT chk_quantity_positive CHECK (quantity > 0)
    NOT VALID;

-- Step 2: Validate existing rows (uses weaker lock, can run concurrently):
ALTER TABLE order_item VALIDATE CONSTRAINT chk_quantity_positive;
-- Once validated, the constraint is fully enforced.
```

---

## Generated Columns

Generated columns are computed from other columns in the same row. PostgreSQL supports STORED generated columns (physically stored and indexed).

```sql
-- Generated slug from name (simple):
ALTER TABLE category
    ADD COLUMN slug_generated TEXT
    GENERATED ALWAYS AS (
        LOWER(REGEXP_REPLACE(name, '[^a-zA-Z0-9]+', '-', 'g'))
    ) STORED;

-- Generated full name:
ALTER TABLE customer
    ADD COLUMN full_name_upper TEXT
    GENERATED ALWAYS AS (UPPER(name)) STORED;

-- Generated FTS vector (seen in Module 04):
ALTER TABLE product
    ADD COLUMN search_vector TSVECTOR
    GENERATED ALWAYS AS (
        to_tsvector('english', name || ' ' || COALESCE(description, ''))
    ) STORED;

-- Index it:
CREATE INDEX ON product USING GIN (search_vector);
```

---

## Domain Types

A domain is a named constraint wrapper over a base type — reusable across tables.

```sql
-- Create domains for common constrained types:
CREATE DOMAIN positive_numeric AS NUMERIC CHECK (VALUE > 0);
CREATE DOMAIN email_address    AS TEXT    CHECK (VALUE ~* '^[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}$');
CREATE DOMAIN rating_1_to_5    AS SMALLINT CHECK (VALUE BETWEEN 1 AND 5);
CREATE DOMAIN percentage       AS NUMERIC(5,2) CHECK (VALUE >= 0 AND VALUE <= 100);
CREATE DOMAIN country_code     AS CHAR(2) CHECK (VALUE ~ '^[A-Z]{2}$');

-- Use in table definitions:
CREATE TABLE pricing_rule (
    id            SERIAL PRIMARY KEY,
    product_id    INTEGER REFERENCES product(id),
    discount_pct  percentage NOT NULL,    -- reuses domain
    min_qty       positive_numeric NOT NULL
);

ALTER TABLE customer ALTER COLUMN email TYPE email_address;
-- This adds the email validation as a domain check, enforced forever on this column.
```

---

## Try It Yourself

```sql
-- 1. Add an exclusion constraint to product_discount to prevent 
--    overlapping discount periods for the same product.

-- 2. Create a partial unique index so each customer can only have 
--    ONE active (not soft-deleted) email address.

-- 3. Add a generated column to customer that stores UPPER(email) 
--    for case-insensitive display.

-- 4. Create a domain for US zip codes (5 digits or 5+4 format: 12345 or 12345-6789)
--    and apply it to the address.postal_code column when country = 'US'.
--    (Hint: use a CHECK constraint on the table itself; domains can't reference other columns.)
```

<details>
<summary>Show solutions</summary>

```sql
-- 1. Exclusion constraint for product_discount overlap:
CREATE EXTENSION IF NOT EXISTS btree_gist;

ALTER TABLE product_discount
    ADD EXCLUDE USING GIST (
        product_id WITH =,
        valid_for  WITH &&
    );

-- 2. Partial unique index for active customer email:
CREATE UNIQUE INDEX uq_customer_active_email
    ON customer (email)
    WHERE deleted_at IS NULL;

-- 3. Generated column for uppercase email:
ALTER TABLE customer
    ADD COLUMN email_display TEXT
    GENERATED ALWAYS AS (UPPER(email)) STORED;

-- 4. US zip code check (table-level):
ALTER TABLE address
    ADD CONSTRAINT chk_us_zip_format
    CHECK (
        country != 'US'  -- Only apply to US addresses
        OR postal_code ~ '^\d{5}(-\d{4})?$'
    );

-- Domain definition for reference:
CREATE DOMAIN us_zip_code AS TEXT CHECK (VALUE ~ '^\d{5}(-\d{4})?$');
```

</details>

---

## Capstone Connection

StoreForge uses several of these constraint techniques:
- `address.is_default` partial unique index ensures exactly one default address per customer
- `review` table has a `UNIQUE(product_id, customer_id)` constraint — one review per customer per product
- `order_item.unit_price` has `CHECK (unit_price > 0)` — rejects zero-price order items
- The `product_discount` table (added in Module 08) uses an exclusion constraint to prevent merchants from creating overlapping sale periods for the same product
