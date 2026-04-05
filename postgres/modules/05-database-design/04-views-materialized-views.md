# Views and Materialized Views

## Concept

A view is a stored SELECT query that behaves like a virtual table. A materialized view is a view whose results are physically stored on disk and can be indexed — trading staleness for query speed. Both solve the problem of repetitive complex queries, but they have different trade-offs that make each appropriate in different contexts.

---

## Regular Views

```sql
-- Create a view for the active product catalog with category info:
CREATE VIEW product_catalog AS
SELECT
    p.id,
    p.name,
    p.slug,
    p.price,
    p.stock_quantity,
    p.attributes,
    c.name AS category_name,
    c.slug AS category_slug,
    AVG(r.rating) AS avg_rating,
    COUNT(r.id)   AS review_count
FROM product p
JOIN category c ON c.id = p.category_id
LEFT JOIN review r ON r.product_id = p.id
WHERE p.is_active = true
  AND p.deleted_at IS NULL
GROUP BY p.id, c.name, c.slug;

-- Use it like a table:
SELECT name, price, avg_rating FROM product_catalog
WHERE category_name = 'Electronics'
ORDER BY avg_rating DESC NULLS LAST;

-- Drop view:
DROP VIEW product_catalog;

-- Update view definition:
CREATE OR REPLACE VIEW product_catalog AS ...;
```

**Key rule:** Regular views are always fresh — they re-run the query every time. No stale data, but also no performance benefit for expensive queries.

---

## Updatable Views

Simple views (single table, no aggregation, no GROUP BY, no DISTINCT) are automatically updatable:

```sql
-- Simple updatable view:
CREATE VIEW active_products AS
    SELECT * FROM product WHERE is_active = true AND deleted_at IS NULL;

-- You can INSERT, UPDATE, DELETE through it:
INSERT INTO active_products (name, slug, price, stock_quantity, category_id)
VALUES ('New Widget', 'new-widget', 29.99, 100, 1);

UPDATE active_products SET price = 32.99 WHERE id = 1;

-- Block: view with WHERE clause won't prevent inserting into hidden rows:
-- Use WITH CHECK OPTION to enforce the view's WHERE on writes:
CREATE VIEW active_products AS
    SELECT * FROM product WHERE is_active = true
WITH CHECK OPTION;

-- Now this fails (would be hidden by the view):
INSERT INTO active_products (name, slug, price, stock_quantity, category_id, is_active)
VALUES ('Hidden Product', 'hidden', 5.00, 1, 1, false);
-- ERROR: new row violates check option for view "active_products"
```

---

## INSTEAD OF Triggers on Views

For views that aren't automatically updatable, define `INSTEAD OF` triggers:

```sql
CREATE VIEW order_summary AS
SELECT
    o.id,
    c.name AS customer_name,
    o.status,
    o.total_amount
FROM "order" o
JOIN customer c ON c.id = o.customer_id;

-- Allow status updates via the view:
CREATE OR REPLACE FUNCTION update_order_summary()
RETURNS TRIGGER AS $$
BEGIN
    UPDATE "order" SET status = NEW.status WHERE id = NEW.id;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER order_summary_update
INSTEAD OF UPDATE ON order_summary
FOR EACH ROW EXECUTE FUNCTION update_order_summary();

-- Now this works:
UPDATE order_summary SET status = 'shipped' WHERE id = 101;
```

---

## Materialized Views

Materialized views store the query result physically. They're ideal for expensive analytical queries that don't need real-time freshness:

```sql
-- Create a materialized view of monthly sales:
CREATE MATERIALIZED VIEW monthly_sales AS
SELECT
    DATE_TRUNC('month', o.created_at) AS month,
    c.name AS category,
    COUNT(DISTINCT o.id)                AS order_count,
    SUM(oi.quantity)                    AS units_sold,
    SUM(oi.quantity * oi.unit_price)    AS revenue
FROM order_item oi
JOIN "order" o   ON o.id = oi.order_id
JOIN product p   ON p.id = oi.product_id
JOIN category c  ON c.id = p.category_id
WHERE o.status = 'delivered'
GROUP BY 1, 2
WITH NO DATA;  -- Don't populate yet; useful for setting up indexes first

-- Populate it:
REFRESH MATERIALIZED VIEW monthly_sales;

-- Index it (indexes on materialized views are supported!):
CREATE INDEX ON monthly_sales (month DESC);
CREATE INDEX ON monthly_sales (category);

-- Query it instantly:
SELECT category, SUM(revenue) FROM monthly_sales
WHERE month >= '2024-01-01'
GROUP BY category;
```

---

## Refreshing Materialized Views

```sql
-- Refresh blocks reads on the view while refreshing (like a table lock):
REFRESH MATERIALIZED VIEW monthly_sales;

-- CONCURRENTLY: allows reads during refresh — requires a UNIQUE index on the view:
CREATE UNIQUE INDEX ON monthly_sales (month, category);

REFRESH MATERIALIZED VIEW CONCURRENTLY monthly_sales;
-- Reads continue during refresh; slightly slower than non-concurrent refresh

-- Check last refresh time:
SELECT schemaname, matviewname, ispopulated
FROM pg_matviews;

-- Schedule refresh (use pg_cron extension or application-level cron):
-- Example: refresh every hour via cron:
-- 0 * * * * psql -U storeforge -d storeforge_dev -c "REFRESH MATERIALIZED VIEW CONCURRENTLY monthly_sales"
```

---

## View Security: SECURITY DEFINER

By default, views run with the permissions of the querying user. `SECURITY DEFINER` makes them run with the view owner's permissions — enabling controlled access to restricted tables:

```sql
-- Create a view that gives read-only customer data without exposing the full table:
CREATE VIEW customer_public_profile
WITH (security_barrier = true)  -- prevents filter pushdown (security)
AS
SELECT id, name, created_at FROM customer WHERE is_active = true;

-- Alternatively, SECURITY DEFINER function acting like a view:
CREATE FUNCTION get_customer_profile(p_id INTEGER)
RETURNS TABLE (name TEXT, created_at TIMESTAMPTZ)
SECURITY DEFINER
SET search_path = public
LANGUAGE SQL AS $$
    SELECT name, created_at FROM customer WHERE id = p_id AND is_active = true;
$$;

GRANT EXECUTE ON FUNCTION get_customer_profile(INTEGER) TO api_user;
-- api_user can call this function but doesn't need SELECT on customer directly
```

---

## Try It Yourself

```sql
-- 1. Create a regular view called order_detail_view that shows:
--    order_id, customer_name, customer_email, product_name,
--    quantity, unit_price, line_total (qty * price), order_status

-- 2. Create a materialized view for product stats:
--    product_id, product_name, total_orders, total_qty_sold, avg_rating, revenue
--    Index it on revenue DESC.

-- 3. Refresh the materialized view and compare its results 
--    against a live query. They should match at refresh time.

-- 4. Use WITH CHECK OPTION to create a view that only shows 
--    pending orders, and verify an INSERT of a 'delivered' order is blocked.
```

<details>
<summary>Show solutions</summary>

```sql
-- 1. Order detail view:
CREATE VIEW order_detail_view AS
SELECT
    o.id                            AS order_id,
    c.name                          AS customer_name,
    c.email                         AS customer_email,
    p.name                          AS product_name,
    oi.quantity,
    oi.unit_price,
    oi.quantity * oi.unit_price     AS line_total,
    o.status                        AS order_status
FROM order_item oi
JOIN "order"   o  ON o.id  = oi.order_id
JOIN customer  c  ON c.id  = o.customer_id
JOIN product   p  ON p.id  = oi.product_id;

-- 2. Product stats materialized view:
CREATE MATERIALIZED VIEW product_stats AS
SELECT
    p.id                                AS product_id,
    p.name                              AS product_name,
    COUNT(DISTINCT oi.order_id)         AS total_orders,
    COALESCE(SUM(oi.quantity), 0)       AS total_qty_sold,
    ROUND(AVG(r.rating), 2)             AS avg_rating,
    COALESCE(SUM(oi.quantity * oi.unit_price), 0) AS revenue
FROM product p
LEFT JOIN order_item oi ON oi.product_id = p.id
LEFT JOIN review r      ON r.product_id  = p.id
GROUP BY p.id, p.name;

CREATE INDEX ON product_stats (revenue DESC);

REFRESH MATERIALIZED VIEW product_stats;

-- 3. Verify:
SELECT product_name, revenue FROM product_stats ORDER BY revenue DESC LIMIT 5;
-- Compare with live:
SELECT p.name, SUM(oi.quantity * oi.unit_price) AS revenue
FROM order_item oi JOIN product p ON p.id = oi.product_id
GROUP BY p.id, p.name ORDER BY revenue DESC LIMIT 5;

-- 4. Pending orders view with CHECK OPTION:
CREATE VIEW pending_orders AS
SELECT * FROM "order" WHERE status = 'pending'
WITH CHECK OPTION;

-- This should fail:
UPDATE pending_orders SET status = 'delivered' WHERE id = 1;
-- ERROR: new row violates check option for view "pending_orders"
```

</details>

---

## Capstone Connection

StoreForge uses two materialized views:
- **`product_stats`** — precomputed product sales and review stats, refreshed every hour. Powers the product listing page without expensive live aggregation on every request.
- **`monthly_sales`** — pre-aggregated revenue by category and month, refreshed nightly. Powers the admin analytics dashboard with sub-millisecond query times on millions of order rows.

Both use `REFRESH MATERIALIZED VIEW CONCURRENTLY` so the dashboard stays available during refresh.
