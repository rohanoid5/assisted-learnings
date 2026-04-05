# Module 03 Exercises — SQL Fundamentals

Work through these exercises against your StoreForge database. Each exercise builds on the schema defined in lesson `01-ddl-schemas-tables.md`.

---

## Exercise 1 — Create a Table with All Constraint Types

Design and create a `coupon` table for StoreForge promotions.

**Requirements:**
- A unique, human-readable coupon code (e.g., `SUMMER20`)
- Discount percentage between 0 and 100 (exclusive)
- An optional fixed-amount discount
- An expiry date (must be in the future when inserted)
- A minimum order amount (defaults to 0)
- Whether the coupon is active (defaults to true)
- `created_at` timestamp (auto-set)

```sql
-- Write the CREATE TABLE statement:
CREATE TABLE coupon (
    -- your answer here
);

-- Verify the structure:
\d coupon
```

<details>
<summary>Show solution</summary>

```sql
CREATE TABLE coupon (
    id               SERIAL PRIMARY KEY,
    code             TEXT NOT NULL,
    discount_percent NUMERIC(5,2) CHECK (discount_percent > 0 AND discount_percent < 100),
    discount_fixed   NUMERIC(10,2) CHECK (discount_fixed > 0),
    expires_at       TIMESTAMPTZ NOT NULL CHECK (expires_at > NOW()),
    min_order_amount NUMERIC(10,2) NOT NULL DEFAULT 0.00,
    is_active        BOOLEAN NOT NULL DEFAULT true,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT coupon_code_unique UNIQUE (code),
    CONSTRAINT coupon_has_discount CHECK (
        discount_percent IS NOT NULL OR discount_fixed IS NOT NULL
    )
);

-- Test valid insert:
INSERT INTO coupon (code, discount_percent, expires_at, min_order_amount)
VALUES ('SUMMER20', 20.00, NOW() + INTERVAL '30 days', 50.00);

-- Test constraint violation (expired date):
INSERT INTO coupon (code, discount_percent, expires_at)
VALUES ('BADCOUPON', 10.00, '2020-01-01');
-- ERROR: new row for relation "coupon" violates check constraint "coupon_expires_at_check"

-- Test constraint violation (no discount type):
INSERT INTO coupon (code, expires_at)
VALUES ('NODISCOUNT', NOW() + INTERVAL '7 days');
-- ERROR: violates check constraint "coupon_has_discount"
```

</details>

---

## Exercise 2 — Data Type Selection Challenge

For each column description, choose the best PostgreSQL data type and justify your choice:

| Column | Description | Your Answer |
|--------|-------------|-------------|
| `product.price` | Money, max $9,999.99, 2 decimal places | ? |
| `customer.is_verified` | True/false flag | ? |
| `order.status` | One of: pending, shipped, delivered, cancelled | ? |
| `product.attributes` | Arbitrary key-value product metadata | ? |
| `user.password_hash` | Bcrypt hash string, always 60 chars | ? |
| `log.ip_address` | IPv4 or IPv6 client address | ? |
| `event.occurred_at` | Timestamp that should always be UTC | ? |
| `review.rating` | Integer 1–5 | ? |

<details>
<summary>Show answers</summary>

| Column | Best Type | Reasoning |
|--------|-----------|-----------|
| `product.price` | `NUMERIC(8,2)` | Exact decimal arithmetic; never use FLOAT for money |
| `customer.is_verified` | `BOOLEAN` | Native true/false; no 0/1 int needed |
| `order.status` | Custom `ENUM` type or `TEXT` with CHECK | ENUM adds type safety; TEXT + CHECK is easier to alter |
| `product.attributes` | `JSONB` | Supports indexing (GIN), faster reads, validates JSON |
| `user.password_hash` | `CHAR(60)` | Fixed-length bcrypt hashes; CHAR avoids padding issues if consistent |
| `log.ip_address` | `INET` | Native IP type supports subnet math and `>>` containment operators |
| `event.occurred_at` | `TIMESTAMPTZ` | Converts to UTC on store; safe across timezone changes |
| `review.rating` | `SMALLINT` | Range 1–5 fits in 2-byte SMALLINT; add CHECK (rating BETWEEN 1 AND 5) |

</details>

---

## Exercise 3 — Aggregation and Grouping

Using the StoreForge schema, write queries to answer:

**3a.** What is the average product price per category? Show the category name and average price, sorted by average price descending.

**3b.** How many orders has each customer placed? Show only customers with 2 or more orders. Include the customer's name and email.

**3c.** What is the total revenue (sum of `total_amount`) per month in 2024? Format the month as `YYYY-MM`.

```sql
-- Write your queries here:
-- 3a:

-- 3b:

-- 3c:
```

<details>
<summary>Show solutions</summary>

```sql
-- 3a: Average price per category:
SELECT
    c.name AS category,
    ROUND(AVG(p.price), 2) AS avg_price,
    COUNT(p.id) AS product_count
FROM product p
JOIN category c ON c.id = p.category_id
WHERE p.is_active = true
GROUP BY c.id, c.name
ORDER BY avg_price DESC;

-- 3b: Customers with 2+ orders:
SELECT
    cu.name,
    cu.email,
    COUNT(o.id) AS order_count
FROM customer cu
JOIN "order" o ON o.customer_id = cu.id
GROUP BY cu.id, cu.name, cu.email
HAVING COUNT(o.id) >= 2
ORDER BY order_count DESC;

-- 3c: Monthly revenue in 2024:
SELECT
    TO_CHAR(created_at, 'YYYY-MM') AS month,
    SUM(total_amount)              AS revenue,
    COUNT(id)                      AS order_count
FROM "order"
WHERE created_at >= '2024-01-01'
  AND created_at <  '2025-01-01'
GROUP BY TO_CHAR(created_at, 'YYYY-MM')
ORDER BY month;
```

</details>

---

## Exercise 4 — DML and Upsert

**4a.** A customer updates their email. Write an `UPDATE` that:
- Changes the email
- Sets `updated_at = NOW()`
- Returns the old and new email (use RETURNING)

**4b.** A customer adds a new default address. Before inserting, the current default must be cleared. Write a transaction that does both safely.

**4c.** A new product arrives with this data:
- Name: `"USB-C Hub"`, slug: `"usb-c-hub"`, price: `49.99`, stock: `200`
- If the slug already exists, update the price and stock instead.

```sql
-- Write your queries:
-- 4a:

-- 4b:

-- 4c:
```

<details>
<summary>Show solutions</summary>

```sql
-- 4a: Update email with RETURNING:
UPDATE customer
SET email      = 'newemail@example.com',
    updated_at = NOW()
WHERE id = 1
RETURNING id, name, email;
-- Note: RETURNING shows the NEW values. To see old/new use a CTE:
WITH old AS (
    SELECT email FROM customer WHERE id = 1
)
UPDATE customer
SET email      = 'newemail@example.com',
    updated_at = NOW()
WHERE id = 1
RETURNING (SELECT email FROM old) AS old_email, email AS new_email;

-- 4b: Swap default address atomically:
BEGIN;

UPDATE address
SET is_default = false
WHERE customer_id = 1 AND is_default = true;

INSERT INTO address (customer_id, line1, city, state, country, postal_code, is_default)
VALUES (1, '456 Oak Ave', 'Austin', 'TX', 'US', '78701', true);

COMMIT;

-- 4c: Upsert product:
INSERT INTO product (name, slug, price, stock_quantity, category_id)
VALUES ('USB-C Hub', 'usb-c-hub', 49.99, 200, 1)
ON CONFLICT (slug) DO UPDATE
    SET price          = EXCLUDED.price,
        stock_quantity = EXCLUDED.stock_quantity,
        updated_at     = NOW()  -- if you add updated_at to product
WHERE product.is_active = true;
```

</details>

---

## Exercise 5 — Multi-Table JOIN for Order Summary

Write a single query that produces a per-order summary with:
- Order ID
- Customer name and email
- Number of items in the order
- Order total
- Status
- Created date (formatted as `YYYY-MM-DD`)

Filter to only `delivered` orders. Sort by created date descending, show the 10 most recent.

```sql
-- Write your query:
```

<details>
<summary>Show solution</summary>

```sql
SELECT
    o.id                                AS order_id,
    cu.name                             AS customer_name,
    cu.email                            AS customer_email,
    COUNT(oi.id)                        AS item_count,
    o.total_amount,
    o.status,
    TO_CHAR(o.created_at, 'YYYY-MM-DD') AS order_date
FROM "order" o
JOIN customer    cu ON cu.id = o.customer_id
JOIN order_item  oi ON oi.order_id = o.id
WHERE o.status = 'delivered'
GROUP BY o.id, cu.name, cu.email, o.total_amount, o.status, o.created_at
ORDER BY o.created_at DESC
LIMIT 10;
```

</details>

---

## Exercise 6 — COPY Import / Export

**6a.** Export all active products (with their category name) to a CSV file at `/tmp/storeforge_products.csv`.

**6b.** Create a temp staging table, import the CSV back into it, and verify the row count matches.

**6c.** Export a monthly order summary (month + revenue + count) for the current year to `/tmp/order_summary.csv`.

```sql
-- 6a: Export active products with category:
\copy (...) TO '/tmp/storeforge_products.csv' CSV HEADER

-- 6b: Staging table import:

-- 6c: Monthly summary export:
\copy (...) TO '/tmp/order_summary.csv' CSV HEADER
```

<details>
<summary>Show solutions</summary>

```sql
-- 6a: Export active products:
\copy (
    SELECT p.id, p.name, p.slug, c.name AS category, p.price, p.stock_quantity
    FROM product p
    JOIN category c ON c.id = p.category_id
    WHERE p.is_active = true
    ORDER BY c.name, p.name
) TO '/tmp/storeforge_products.csv' WITH (FORMAT CSV, HEADER true)

-- 6b: Import back to staging:
CREATE TEMP TABLE staging_products (
    id             INTEGER,
    name           TEXT,
    slug           TEXT,
    category       TEXT,
    price          NUMERIC(10,2),
    stock_quantity INTEGER
);

\copy staging_products FROM '/tmp/storeforge_products.csv' WITH (FORMAT CSV, HEADER true)

-- Verify:
SELECT COUNT(*) FROM staging_products;
SELECT COUNT(*) FROM product WHERE is_active = true;
-- Both should match

-- 6c: Monthly order summary:
\copy (
    SELECT
        TO_CHAR(created_at, 'YYYY-MM') AS month,
        COUNT(id)                      AS order_count,
        SUM(total_amount)              AS revenue
    FROM "order"
    WHERE DATE_PART('year', created_at) = DATE_PART('year', NOW())
    GROUP BY TO_CHAR(created_at, 'YYYY-MM')
    ORDER BY month
) TO '/tmp/order_summary.csv' WITH (FORMAT CSV, HEADER true)
```

</details>

---

## Capstone Checkpoint ✅

After completing these exercises, you should be able to:

- [ ] Design tables with appropriate constraints (PK, FK, CHECK, UNIQUE, NOT NULL)
- [ ] Choose the right PostgreSQL data type for any column
- [ ] Write SELECT queries with GROUP BY, HAVING, aggregate functions, and subqueries
- [ ] Perform INSERT, UPDATE, DELETE, and upsert (ON CONFLICT) operations
- [ ] Join multiple tables (INNER, LEFT, FULL OUTER, SELF) to answer business questions
- [ ] Use `\copy` to export query results and import CSV data
- [ ] Validate imported data using staging tables before committing to production tables

**StoreForge Progress:** The full schema is in place, sample data is loaded, and you can query the complete order history. Up next: Module 04 covers advanced SQL techniques — window functions, CTEs, and lateral joins — that unlock analytics queries.
