# Module 05 Exercises — Database Design

Work through these exercises against your StoreForge database. These cover normalization analysis, schema design pattern implementation, advanced constraints, and views.

---

## Exercise 1 — Normalization Analysis

Given this un-normalized table representing order data:

```
order_id | order_date | customer_id | customer_name | customer_email | customer_city |
product_id | product_name | category_name | unit_price | qty | line_total
```

**1a.** Identify all normalization violations (which normal form each violates and why).

**1b.** Write a fully normalized schema (CREATE TABLE statements) that eliminates all violations.

**1c.** Identify which denormalizations in StoreForge's actual schema are **intentional** and explain why each is valid.

<details>
<summary>Show answers</summary>

**1a. Violations:**
- **2NF violation**: `customer_name`, `customer_email`, `customer_city` depend on `customer_id` (not on the full composite key of `order_id + product_id`). `product_name` and `category_name` depend on `product_id` alone.
- **3NF violation**: `customer_city` may transitively depend on a zip code (if zip were present); `category_name` depends on `product_id → category_id → category_name` (transitive).
- **Derived column**: `line_total = unit_price × qty` is a derived value, not a stored dependency violation per se, but storing it risks inconsistency.

**1b. Normalized schema:**
```sql
CREATE TABLE category (id SERIAL PRIMARY KEY, name TEXT NOT NULL UNIQUE);

CREATE TABLE customer (
    id    SERIAL PRIMARY KEY,
    name  TEXT NOT NULL,
    email TEXT NOT NULL UNIQUE,
    city  TEXT
);

CREATE TABLE product (
    id          SERIAL PRIMARY KEY,
    name        TEXT NOT NULL,
    category_id INTEGER REFERENCES category(id),
    current_price NUMERIC(10,2)
);

CREATE TABLE "order" (
    id          SERIAL PRIMARY KEY,
    customer_id INTEGER REFERENCES customer(id),
    order_date  TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE order_item (
    id         SERIAL PRIMARY KEY,
    order_id   INTEGER REFERENCES "order"(id),
    product_id INTEGER REFERENCES product(id),
    qty        INTEGER NOT NULL,
    unit_price NUMERIC(10,2) NOT NULL  -- snapshot; intentional
    -- line_total NOT stored; compute as qty * unit_price
);
```

**1c. Intentional denormalizations in StoreForge:**
- `order_item.unit_price` — historical price snapshot; correct denormalization
- `order.total_amount` — precomputed sum; backed by trigger for consistency
- `audit_log.table_name` (TEXT, not FK) — intentional; audit schema must outlive the referenced tables

</details>

---

## Exercise 2 — Schema Design Patterns

**2a.** Implement soft-delete for the `product` table:
- Add a `deleted_at TIMESTAMPTZ` column
- Create a partial unique index on `slug` for non-deleted products only
- Create an `active_products` view
- Soft-delete product ID 1 and verify it's filtered from the view

**2b.** Design a `coupon_usage` table that:
- References a `coupon` and an `order`
- Prevents the same customer from using the same coupon twice (use the customer from the order)
- Records when and by whom it was applied

**2c.** Create a `price_history` table to track all price changes for products:
- Store the old price, new price, changed_at, and changed_by
- Write a trigger that automatically inserts into this table when `product.price` is updated

```sql
-- 2a:

-- 2b:

-- 2c:
```

<details>
<summary>Show solutions</summary>

```sql
-- 2a: Soft delete:
ALTER TABLE product ADD COLUMN deleted_at TIMESTAMPTZ;

DROP INDEX IF EXISTS uq_product_slug;  -- remove old unique constraint if present
CREATE UNIQUE INDEX uq_product_slug_active ON product (slug) WHERE deleted_at IS NULL;

CREATE VIEW active_products AS SELECT * FROM product WHERE deleted_at IS NULL;

UPDATE product SET deleted_at = NOW() WHERE id = 1;

SELECT id FROM active_products WHERE id = 1;  -- 0 rows

-- 2b: Coupon usage (prevent same customer using same coupon twice):
CREATE TABLE coupon_usage (
    id         SERIAL PRIMARY KEY,
    coupon_id  INTEGER NOT NULL REFERENCES coupon(id),
    order_id   INTEGER NOT NULL REFERENCES "order"(id),
    applied_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    applied_by TEXT NOT NULL DEFAULT CURRENT_USER,
    UNIQUE (coupon_id, order_id)
);

-- Prevent same customer using same coupon (via partial unique on join):
CREATE UNIQUE INDEX uq_coupon_per_customer
    ON coupon_usage (coupon_id, (SELECT customer_id FROM "order" WHERE id = order_id));
-- Note: Expression-based unique index; complex; may prefer app-level enforcement
-- or a denormalized customer_id column in coupon_usage for simplicity.

-- Simpler approach with customer_id denormalized:
CREATE TABLE coupon_usage (
    id          SERIAL PRIMARY KEY,
    coupon_id   INTEGER NOT NULL REFERENCES coupon(id),
    order_id    INTEGER NOT NULL REFERENCES "order"(id),
    customer_id INTEGER NOT NULL REFERENCES customer(id),
    applied_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (coupon_id, customer_id)  -- one use per customer per coupon
);

-- 2c: Price history trigger:
CREATE TABLE price_history (
    id         BIGSERIAL PRIMARY KEY,
    product_id INTEGER NOT NULL REFERENCES product(id),
    old_price  NUMERIC(10,2) NOT NULL,
    new_price  NUMERIC(10,2) NOT NULL,
    changed_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    changed_by TEXT NOT NULL DEFAULT CURRENT_USER
);

CREATE OR REPLACE FUNCTION record_price_change() RETURNS TRIGGER AS $$
BEGIN
    IF OLD.price IS DISTINCT FROM NEW.price THEN
        INSERT INTO price_history (product_id, old_price, new_price)
        VALUES (OLD.id, OLD.price, NEW.price);
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_price_history
AFTER UPDATE OF price ON product
FOR EACH ROW EXECUTE FUNCTION record_price_change();

-- Test:
UPDATE product SET price = 99.99 WHERE id = 2;
SELECT * FROM price_history WHERE product_id = 2;
```

</details>

---

## Exercise 3 — Advanced Constraints

**3a.** Add an exclusion constraint to prevent overlapping promotional periods for the same product. Create a `promotion` table with `product_id`, `discount_pct`, and `active_during TSTZRANGE`.

**3b.** Add a `CHECK` constraint to the `order` table that ensures:
- `status = 'shipped'` requires `shipping_address_id IS NOT NULL`
- `status = 'cancelled'` allows `total_amount = 0`

**3c.** Add a generated column to `product` that stores the price times 1.2 as `price_with_tax`. Index it.

```sql
-- 3a:

-- 3b:

-- 3c:
```

<details>
<summary>Show solutions</summary>

```sql
-- 3a: Promotion table with exclusion:
CREATE EXTENSION IF NOT EXISTS btree_gist;

CREATE TABLE promotion (
    id           SERIAL PRIMARY KEY,
    product_id   INTEGER NOT NULL REFERENCES product(id),
    discount_pct NUMERIC(5,2) NOT NULL CHECK (discount_pct > 0 AND discount_pct < 100),
    active_during TSTZRANGE NOT NULL,

    EXCLUDE USING GIST (
        product_id    WITH =,
        active_during WITH &&
    )
);

-- 3b: Status check constraints:
ALTER TABLE "order"
    ADD CONSTRAINT chk_shipped_has_address
    CHECK (status != 'shipped' OR shipping_address_id IS NOT NULL);

ALTER TABLE "order"
    ADD CONSTRAINT chk_cancelled_total
    CHECK (status != 'cancelled' OR total_amount >= 0);

-- 3c: Generated tax column:
ALTER TABLE product
    ADD COLUMN price_with_tax NUMERIC(10,2)
    GENERATED ALWAYS AS (ROUND(price * 1.20, 2)) STORED;

CREATE INDEX idx_product_price_with_tax ON product (price_with_tax);

-- Verify:
SELECT name, price, price_with_tax FROM product LIMIT 5;
```

</details>

---

## Exercise 4 — Views and Materialized Views

**4a.** Create a view `customer_order_summary` that shows each customer's name, email, total orders, total spend, and whether they are a "VIP" (total spend > $1000).

**4b.** Create a materialized view `top_products` for the top 10 products by revenue (all time). Include a unique index so it can be refreshed concurrently.

**4c.** Simulate a refresh cycle:
1. Query `top_products` (empty or stale)
2. Insert new orders
3. Refresh concurrently
4. Query again — verify the results changed

```sql
-- 4a:

-- 4b:

-- 4c:
```

<details>
<summary>Show solutions</summary>

```sql
-- 4a: Customer order summary view:
CREATE VIEW customer_order_summary AS
SELECT
    c.id,
    c.name,
    c.email,
    COUNT(o.id)                    AS total_orders,
    COALESCE(SUM(o.total_amount), 0) AS total_spend,
    CASE WHEN SUM(o.total_amount) > 1000 THEN true ELSE false END AS is_vip
FROM customer c
LEFT JOIN "order" o ON o.customer_id = c.id AND o.status = 'delivered'
GROUP BY c.id, c.name, c.email;

-- 4b: Top 10 products materialized view:
CREATE MATERIALIZED VIEW top_products AS
SELECT
    p.id            AS product_id,
    p.name,
    SUM(oi.quantity * oi.unit_price) AS revenue,
    SUM(oi.quantity)                 AS units_sold,
    RANK() OVER (ORDER BY SUM(oi.quantity * oi.unit_price) DESC) AS revenue_rank
FROM product p
JOIN order_item oi ON oi.product_id = p.id
JOIN "order" o     ON o.id = oi.order_id
WHERE o.status = 'delivered'
GROUP BY p.id, p.name
ORDER BY revenue DESC
LIMIT 10;

-- Required unique index for CONCURRENTLY:
CREATE UNIQUE INDEX ON top_products (product_id);

REFRESH MATERIALIZED VIEW top_products;

-- 4c: Refresh cycle:
SELECT name, revenue FROM top_products;  -- before

INSERT INTO "order" (customer_id, shipping_address_id, status, total_amount)
VALUES (1, 1, 'delivered', 500.00)
RETURNING id;

INSERT INTO order_item (order_id, product_id, quantity, unit_price)
VALUES (currval('order_id_seq'), 1, 5, 100.00);

REFRESH MATERIALIZED VIEW CONCURRENTLY top_products;

SELECT name, revenue FROM top_products;  -- after (product 1 revenue should increase)
```

</details>

---

## Capstone Checkpoint ✅

After completing these exercises, you should be able to:

- [ ] Identify 1NF, 2NF, 3NF violations in a given schema and fix them
- [ ] Distinguish intentional denormalization from accidental violations
- [ ] Implement soft delete, audit logging, price history, and polymorphic associations
- [ ] Use deferrable constraints, exclusion constraints, and partial unique indexes
- [ ] Add generated columns and domain types for reusable constraint logic
- [ ] Create regular views with `WITH CHECK OPTION` and `INSTEAD OF` triggers
- [ ] Create materialized views, add indexes, and refresh them (including concurrently)

**StoreForge Progress:** Your schema is now production-quality: normalized, with audit trails, soft deletes, and analytics views. Module 06 covers PL/pgSQL — writing stored functions, procedures, and triggers in PostgreSQL's procedural language.
