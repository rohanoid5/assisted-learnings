# Modifying Data: INSERT, UPDATE, DELETE, and UPSERT

## Concept

DML — Data Manipulation Language — is how you change the data in your tables. PostgreSQL's DML has powerful features beyond the SQL standard: `RETURNING` clauses, `INSERT ON CONFLICT` (upsert), `UPDATE FROM`, and `DELETE USING` that make complex data operations concise and atomic.

---

## INSERT

### Basic insert

```sql
-- Single row:
INSERT INTO category (name, slug)
VALUES ('Electronics', 'electronics');

-- Multiple rows (one statement — more efficient than multiple single inserts):
INSERT INTO category (name, slug) VALUES
    ('Clothing',      'clothing'),
    ('Books',         'books'),
    ('Home & Garden', 'home-garden'),
    ('Sports',        'sports');
```

### RETURNING — get the result back

```sql
-- Get the generated id and default values:
INSERT INTO customer (name, email)
VALUES ('Alice Johnson', 'alice@example.com')
RETURNING id, created_at;

-- Get all columns:
INSERT INTO "order" (customer_id, status, total_amount)
VALUES (1, 'pending', 0.00)
RETURNING *;

-- Use RETURNING in a CTE (see Module 04):
WITH new_order AS (
    INSERT INTO "order" (customer_id, total_amount)
    VALUES (42, 149.99)
    RETURNING id
)
INSERT INTO order_item (order_id, product_id, quantity, unit_price)
SELECT id, 7, 2, 74.995 FROM new_order;
```

### INSERT FROM SELECT (insert from another query)

```sql
-- Copy rows from one table to another:
INSERT INTO product_backup (id, name, price)
SELECT id, name, price FROM product
WHERE created_at < '2024-01-01';

-- Insert categories from a staging table:
INSERT INTO category (name, slug)
SELECT name, slug FROM staging_categories
WHERE NOT EXISTS (
    SELECT 1 FROM category c WHERE c.slug = staging_categories.slug
);
```

---

## UPDATE

### Basic update

```sql
-- Update all rows (⚠️ no WHERE = affects every row):
UPDATE product SET is_active = false;   -- disables ALL products

-- Update with WHERE:
UPDATE product SET is_active = false WHERE id = 42;

-- Update multiple columns:
UPDATE product
SET
    price          = 89.99,
    stock_quantity = stock_quantity - 1,
    updated_at     = now()    -- if you have an updated_at column
WHERE id = 7;
```

### UPDATE with RETURNING

```sql
-- See what changed:
UPDATE product
SET stock_quantity = stock_quantity - :quantity
WHERE id = :product_id
  AND stock_quantity >= :quantity  -- only if enough stock
RETURNING id, name, stock_quantity AS new_stock;

-- If 0 rows returned → insufficient stock
```

### UPDATE FROM (update based on another table)

```sql
-- Apply a 10% discount to all products in the 'sale' category:
UPDATE product p
SET price = ROUND(p.price * 0.9, 2)
FROM category c
WHERE p.category_id = c.id
  AND c.slug = 'sale';

-- Update order total from order_items:
UPDATE "order" o
SET total_amount = (
    SELECT COALESCE(SUM(quantity * unit_price), 0)
    FROM order_item
    WHERE order_id = o.id
)
WHERE o.status = 'pending';
```

### Bulk update pattern

```sql
-- Update multiple rows with different values using VALUES:
UPDATE product AS p
SET price = v.new_price
FROM (VALUES
    (1,  19.99),
    (7,  89.99),
    (12, 149.99)
) AS v(product_id, new_price)
WHERE p.id = v.product_id;
```

---

## DELETE

### Basic delete

```sql
-- Delete specific rows:
DELETE FROM review WHERE id = 42;
DELETE FROM product WHERE is_active = false AND stock_quantity = 0;

-- Delete all rows (prefer TRUNCATE for full table clear):
DELETE FROM order_item;  -- slow on large tables

-- DELETE with RETURNING:
DELETE FROM "order"
WHERE status = 'cancelled' AND created_at < now() - INTERVAL '1 year'
RETURNING id, customer_id, total_amount;

-- DELETE with USING (join to filter):
DELETE FROM order_item oi
USING "order" o
WHERE oi.order_id = o.id
  AND o.status = 'cancelled'
  AND o.created_at < now() - INTERVAL '90 days';
```

### Soft delete pattern

Instead of physically deleting rows, mark them as deleted:

```sql
-- Add a soft-delete column:
ALTER TABLE product ADD COLUMN deleted_at TIMESTAMPTZ;

-- "Delete":
UPDATE product SET deleted_at = now() WHERE id = 42;

-- Query (exclude deleted rows):
SELECT * FROM product WHERE deleted_at IS NULL;

-- Create a view for convenience:
CREATE VIEW active_products AS
SELECT * FROM product WHERE deleted_at IS NULL;
```

---

## INSERT ON CONFLICT (UPSERT)

Upsert inserts a new row or, if a conflict occurs on a unique/primary key, updates the existing row. This is one of PostgreSQL's most useful features.

```sql
-- Insert or ignore (do nothing on conflict):
INSERT INTO category (name, slug)
VALUES ('Electronics', 'electronics')
ON CONFLICT (slug) DO NOTHING;

-- Insert or update (conflict on primary key):
INSERT INTO product (id, name, price, stock_quantity, category_id, slug)
OVERRIDING SYSTEM VALUE
VALUES (7, 'Wireless Headphones', 89.99, 150, 1, 'wireless-headphones')
ON CONFLICT (id) DO UPDATE SET
    price          = EXCLUDED.price,
    stock_quantity = EXCLUDED.stock_quantity,
    name           = EXCLUDED.name;
-- EXCLUDED refers to the row that would have been inserted

-- Insert or update (conflict on unique column):
INSERT INTO customer (name, email)
VALUES ('Alice Johnson', 'alice@example.com')
ON CONFLICT (email) DO UPDATE SET
    name       = EXCLUDED.name,
    updated_at = now()
RETURNING id, name, email, (xmax <> 0) AS was_updated;
-- xmax <> 0 is a trick to detect if the row was updated vs. inserted
```

### Conditional upsert

```sql
-- Only update if the new price is lower (never increase price via upsert):
INSERT INTO product (id, name, price, stock_quantity, category_id, slug)
OVERRIDING SYSTEM VALUE
VALUES (7, 'Wireless Headphones', 79.99, 150, 1, 'wireless-headphones')
ON CONFLICT (id) DO UPDATE SET
    price = LEAST(product.price, EXCLUDED.price)
WHERE product.price > EXCLUDED.price;  -- condition in DO UPDATE

-- Update stock only if product exists (do NOT insert):
INSERT INTO product (id, stock_quantity, name, price, category_id, slug)
OVERRIDING SYSTEM VALUE
VALUES (99, 50, '', 0, 1, '')
ON CONFLICT (id) DO UPDATE SET
    stock_quantity = product.stock_quantity + EXCLUDED.stock_quantity
-- If product 99 doesn't exist, inserts a bad row — filter with WHERE:
WHERE product.id = 99;
-- Actually: use UPDATE, not upsert, when you don't want to insert
```

---

## Common DML Patterns

### Atomic order placement (BEGIN + RETURNING chain)

```sql
BEGIN;

-- 1. Create the order:
INSERT INTO "order" (customer_id, status, total_amount)
VALUES (42, 'pending', 0.00)
RETURNING id AS order_id \gset

-- 2. Add items:
INSERT INTO order_item (order_id, product_id, quantity, unit_price)
SELECT :order_id, p.id, 2, p.price
FROM product p WHERE p.id = 7;

-- 3. Deduct stock:
UPDATE product SET stock_quantity = stock_quantity - 2
WHERE id = 7 AND stock_quantity >= 2;

-- 4. Recalculate total:
UPDATE "order" SET
    total_amount = (SELECT SUM(quantity * unit_price) FROM order_item WHERE order_id = :order_id),
    status = 'confirmed'
WHERE id = :order_id;

COMMIT;
```

---

## Try It Yourself

```sql
-- 1. Insert seed categories and get back their IDs:
INSERT INTO category (name, slug) VALUES
    ('Electronics', 'electronics'),
    ('Clothing', 'clothing'),
    ('Books', 'books')
RETURNING id, name;

-- 2. Insert a product and use RETURNING to get its id:
INSERT INTO product (name, slug, price, stock_quantity, category_id, attributes)
VALUES (
    'Mechanical Keyboard',
    'mechanical-keyboard',
    129.99,
    75,
    1,   -- Electronics
    '{"switch_type": "Cherry MX Blue", "layout": "TKL", "backlit": true}'
)
RETURNING id, name, created_at;

-- 3. Update the product's price and verify with RETURNING:
UPDATE product
SET price = 119.99
WHERE slug = 'mechanical-keyboard'
RETURNING id, name, price AS new_price;

-- 4. Upsert a customer (insert or update email):
INSERT INTO customer (name, email)
VALUES ('Bob Smith', 'bob@example.com')
ON CONFLICT (email) DO UPDATE SET
    name = EXCLUDED.name,
    updated_at = now()
RETURNING id, name, email, (xmax <> 0) AS was_updated;

-- Run it again with a different name — observe was_updated = true.

-- 5. Soft-delete a product:
UPDATE product SET is_active = false WHERE slug = 'mechanical-keyboard';
SELECT name, is_active FROM product WHERE slug = 'mechanical-keyboard';
```

<details>
<summary>Expected output for query 2</summary>

```
 id |        name         |          created_at          
----+---------------------+-----------------------------
  9 | Mechanical Keyboard | 2024-06-15 12:00:00.123456+00
```

The `id` is auto-generated. `created_at` is `DEFAULT now()` — you don't need to provide it.

For query 4 (first run): `was_updated = false` (it was inserted)
For query 4 (second run with different name): `was_updated = true` (it was updated)

</details>

---

## Capstone Connection

- **Order placement** — `INSERT INTO order + order_item + UPDATE stock` wrapped in a transaction
- **Inventory sync** — `INSERT ... ON CONFLICT DO UPDATE SET stock_quantity = ...` for bulk stock updates from supplier feeds
- **Customer registration** — `INSERT ... ON CONFLICT (email) DO UPDATE` for social login (same email, returning user)
- **Review submission** — `INSERT ... ON CONFLICT (product_id, customer_id) DO UPDATE` to allow editing existing reviews
