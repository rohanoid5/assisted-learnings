# Module 04 Exercises — Advanced SQL

Work through these exercises against your StoreForge database. You should have the full schema from Module 03 loaded, with seed data in all tables.

---

## Exercise 1 — Transactions and Locking

**Scenario:** A customer places an order for 3 units of product ID 1 and 1 unit of product ID 2.

Write a complete transaction that:
1. Locks both product rows (to prevent concurrent overselling)
2. Checks that both products have sufficient stock
3. Creates an order record
4. Creates order items
5. Deducts stock from each product
6. Rolls back everything if stock is insufficient

```sql
-- Write your transaction here:
BEGIN;

-- ...

COMMIT;
```

<details>
<summary>Show solution</summary>

```sql
BEGIN;

-- Step 1: Lock product rows (use FOR UPDATE to prevent concurrent changes):
-- Order by id to prevent deadlocks:
SELECT id, name, price, stock_quantity
FROM product
WHERE id IN (1, 2)
ORDER BY id
FOR UPDATE;

-- Step 2: Check stock (use DO block or app-level logic):
DO $$
DECLARE
    stock1 INTEGER;
    stock2 INTEGER;
BEGIN
    SELECT stock_quantity INTO stock1 FROM product WHERE id = 1;
    SELECT stock_quantity INTO stock2 FROM product WHERE id = 2;

    IF stock1 < 3 THEN
        RAISE EXCEPTION 'Insufficient stock for product 1 (need 3, have %)', stock1;
    END IF;
    IF stock2 < 1 THEN
        RAISE EXCEPTION 'Insufficient stock for product 2 (need 1, have %)', stock2;
    END IF;
END $$;

-- Step 3: Create the order:
INSERT INTO "order" (customer_id, shipping_address_id, status, total_amount)
VALUES (1, 1, 'pending', 0.00)  -- total_amount updated after items
RETURNING id;

-- Step 4: Create order items (using currval for the order id):
INSERT INTO order_item (order_id, product_id, quantity, unit_price)
SELECT currval('order_id_seq'), 1, 3, price FROM product WHERE id = 1;

INSERT INTO order_item (order_id, product_id, quantity, unit_price)
SELECT currval('order_id_seq'), 2, 1, price FROM product WHERE id = 2;

-- Step 5: Update total amount:
UPDATE "order"
SET total_amount = (
    SELECT SUM(quantity * unit_price)
    FROM order_item
    WHERE order_id = currval('order_id_seq')
)
WHERE id = currval('order_id_seq');

-- Step 6: Deduct stock:
UPDATE product SET stock_quantity = stock_quantity - 3 WHERE id = 1;
UPDATE product SET stock_quantity = stock_quantity - 1 WHERE id = 2;

COMMIT;
```

</details>

---

## Exercise 2 — CTEs and Recursive Queries

**2a.** Write a recursive CTE that:
- Starts at the root categories
- Expands to all descendants
- Returns: `id`, `name`, `parent_id`, `depth` (0 = root), `full_path` (e.g., "Electronics > Audio > Headphones")

**2b.** Using a non-recursive CTE, find the top 5 customers by total lifetime spend (delivered orders only). Show their name, email, total spend, and number of orders.

**2c.** Using a writeable CTE, deactivate all products that:
- Have been active for more than 2 years (`created_at < NOW() - INTERVAL '2 years'`)
- Have stock = 0
...and simultaneously insert a row into `audit_log` for each one.

```sql
-- 2a:

-- 2b:

-- 2c:
```

<details>
<summary>Show solutions</summary>

```sql
-- 2a: Full category tree with path:
WITH RECURSIVE category_tree AS (
    SELECT id, name, parent_id, 0 AS depth, name::TEXT AS full_path
    FROM category WHERE parent_id IS NULL

    UNION ALL

    SELECT c.id, c.name, c.parent_id, ct.depth + 1,
           (ct.full_path || ' > ' || c.name)::TEXT
    FROM category c
    JOIN category_tree ct ON ct.id = c.parent_id
)
SELECT id, name, parent_id, depth, full_path
FROM category_tree
ORDER BY full_path;

-- 2b: Top 5 customers by lifetime value:
WITH customer_stats AS (
    SELECT
        c.id,
        c.name,
        c.email,
        COUNT(o.id)          AS order_count,
        SUM(o.total_amount)  AS lifetime_value
    FROM customer c
    JOIN "order" o ON o.customer_id = c.id
    WHERE o.status = 'delivered'
    GROUP BY c.id, c.name, c.email
)
SELECT name, email, lifetime_value, order_count
FROM customer_stats
ORDER BY lifetime_value DESC
LIMIT 5;

-- 2c: Deactivate old, empty-stock products + audit:
WITH deactivated AS (
    UPDATE product
    SET is_active = false
    WHERE is_active = true
      AND stock_quantity = 0
      AND created_at < NOW() - INTERVAL '2 years'
    RETURNING id, name
)
INSERT INTO audit_log (table_name, operation, row_id, new_data, changed_by, changed_at)
SELECT
    'product',
    'UPDATE',
    id,
    jsonb_build_object('is_active', false, 'name', name),
    CURRENT_USER,
    NOW()
FROM deactivated;
```

</details>

---

## Exercise 3 — Window Functions

**3a.** For each order, show:
- Order ID, customer name, total amount
- Running total of amount per customer (ordered by date)
- Rank of this order by amount within the customer's orders

**3b.** Calculate month-over-month order count change. Show month, count, previous month count, absolute delta, and % change.

**3c.** Find the top 2 best-selling products (by quantity sold) in each category. Use window functions, not LATERAL.

```sql
-- 3a:

-- 3b:

-- 3c:
```

<details>
<summary>Show solutions</summary>

```sql
-- 3a: Running per-customer total and within-customer rank:
SELECT
    o.id                                          AS order_id,
    c.name                                        AS customer_name,
    o.total_amount,
    SUM(o.total_amount) OVER (
        PARTITION BY o.customer_id
        ORDER BY o.created_at
        ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW
    )                                             AS running_total,
    RANK() OVER (
        PARTITION BY o.customer_id
        ORDER BY o.total_amount DESC
    )                                             AS customer_order_rank
FROM "order" o
JOIN customer c ON c.id = o.customer_id
ORDER BY c.name, o.created_at;

-- 3b: Month-over-month order count change:
WITH monthly AS (
    SELECT
        DATE_TRUNC('month', created_at) AS month,
        COUNT(*)                        AS order_count
    FROM "order"
    GROUP BY 1
)
SELECT
    month,
    order_count,
    LAG(order_count) OVER (ORDER BY month)     AS prev_month,
    order_count - LAG(order_count) OVER (ORDER BY month) AS delta,
    ROUND(
        100.0 * (order_count - LAG(order_count) OVER (ORDER BY month))
              / NULLIF(LAG(order_count) OVER (ORDER BY month), 0),
        1
    )                                          AS pct_change
FROM monthly
ORDER BY month;

-- 3c: Top 2 products per category by quantity sold:
SELECT category_name, product_name, total_qty, qty_rank
FROM (
    SELECT
        c.name                                AS category_name,
        p.name                                AS product_name,
        SUM(oi.quantity)                      AS total_qty,
        RANK() OVER (
            PARTITION BY p.category_id
            ORDER BY SUM(oi.quantity) DESC
        )                                     AS qty_rank
    FROM order_item oi
    JOIN product p   ON p.id = oi.product_id
    JOIN category c  ON c.id = p.category_id
    GROUP BY p.category_id, p.id, c.name, p.name
) AS ranked
WHERE qty_rank <= 2
ORDER BY category_name, qty_rank;
```

</details>

---

## Exercise 4 — Full-Text Search and JSONB

**4a.** Add a `search_vector` generated column to `product` and create a GIN index on it.

**4b.** Search for products matching "wireless noise cancelling" using `websearch_to_tsquery`. Show results ordered by relevance.

**4c.** Find all products where `attributes` contains `"wireless": true` AND `battery_hours > 15`. Show name, price, and battery hours.

**4d.** Build a JSON document for 5 recent orders showing: `{ order_id, status, customer: { name, email }, item_count, total }`.

```sql
-- 4a:

-- 4b:

-- 4c:

-- 4d:
```

<details>
<summary>Show solutions</summary>

```sql
-- 4a: Generated FTS column:
ALTER TABLE product
ADD COLUMN search_vector TSVECTOR
GENERATED ALWAYS AS (
    to_tsvector('english', name || ' ' || COALESCE(description, ''))
) STORED;

CREATE INDEX idx_product_fts ON product USING GIN (search_vector);

-- 4b: Ranked FTS:
SELECT
    name,
    price,
    ROUND(ts_rank(search_vector, q)::NUMERIC, 4) AS rank
FROM product,
     websearch_to_tsquery('english', 'wireless noise cancelling') AS q
WHERE is_active = true
  AND search_vector @@ q
ORDER BY rank DESC;

-- 4c: JSONB filter:
SELECT
    name,
    price,
    (attributes ->> 'battery_hours')::INTEGER AS battery_hours
FROM product
WHERE attributes @> '{"wireless": true}'
  AND (attributes ->> 'battery_hours')::INTEGER > 15
ORDER BY battery_hours DESC;

-- 4d: Order JSON documents:
SELECT jsonb_build_object(
    'order_id',   o.id,
    'status',     o.status,
    'customer',   jsonb_build_object('name', c.name, 'email', c.email),
    'item_count', item_counts.cnt,
    'total',      o.total_amount
) AS order_doc
FROM "order" o
JOIN customer c ON c.id = o.customer_id
LEFT JOIN (
    SELECT order_id, COUNT(*) AS cnt FROM order_item GROUP BY order_id
) AS item_counts ON item_counts.order_id = o.id
ORDER BY o.created_at DESC
LIMIT 5;
```

</details>

---

## Capstone Checkpoint ✅

After completing these exercises, you should be able to:

- [ ] Write safe, atomic multi-step transactions with `BEGIN/COMMIT/ROLLBACK`
- [ ] Use `SELECT FOR UPDATE` and `SELECT FOR SHARE` to prevent race conditions
- [ ] Write both recursive and non-recursive CTEs
- [ ] Use writeable CTEs for multi-step DML in one statement
- [ ] Apply `ROW_NUMBER`, `RANK`, `LAG`, `LEAD`, and aggregate window functions
- [ ] Write full-text search queries using `tsvector`, `tsquery`, and `ts_rank`
- [ ] Query, filter, and update JSONB columns using `@>`, `?`, `->>`, `jsonb_set`
- [ ] Build JSONB API-style response documents with `jsonb_build_object` + `jsonb_agg`

**StoreForge Progress:** You can now answer complex analytics questions and build API-ready responses in SQL. Module 05 covers database design principles — normalization, schema patterns, and views.
