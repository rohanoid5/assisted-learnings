# Joining Tables: INNER, LEFT, RIGHT, FULL, CROSS, and Self Joins

## Concept

A **JOIN** combines rows from two or more tables based on a related column — typically a foreign key relationship. JOINs are the heart of relational databases: data is stored in separate tables (normalized) and assembled via JOINs when needed.

---

## Join Concept: Visualizing the Logic

```
customer table:               order table:
┌────┬───────┐               ┌────┬─────────────┬──────────┐
│ id │ name  │               │ id │ customer_id │ total    │
├────┼───────┤               ├────┼─────────────┼──────────┤
│  1 │ Alice │               │ 10 │      1      │  99.00   │
│  2 │ Bob   │               │ 11 │      1      │  149.00  │
│  3 │ Carol │               │ 12 │      3      │  49.00   │
│  4 │ Dave  │               └────┴─────────────┴──────────┘
└────┴───────┘

INNER JOIN:  Alice (2 orders), Carol (1 order) — Bob and Dave excluded
LEFT JOIN:   Alice (2 orders), Bob (NULL), Carol (1 order), Dave (NULL)
RIGHT JOIN:  Alice (2 rows), Carol (1 row) — no unmatched rights here
FULL OUTER:  Alice (2 rows), Bob (NULL), Carol (1 row), Dave (NULL)
```

---

## INNER JOIN

Returns only rows where the join condition is satisfied in **both** tables:

```sql
-- Orders with customer names:
SELECT
    o.id        AS order_id,
    c.name      AS customer_name,
    o.status,
    o.total_amount
FROM "order" o
INNER JOIN customer c ON c.id = o.customer_id;

-- Equivalent shorthand (JOIN = INNER JOIN):
FROM "order" o
JOIN customer c ON c.id = o.customer_id;

-- Three-way join (order → order_item → product):
SELECT
    o.id          AS order_id,
    c.name        AS customer_name,
    p.name        AS product_name,
    oi.quantity,
    oi.unit_price,
    oi.quantity * oi.unit_price  AS line_total
FROM "order" o
JOIN customer c    ON c.id  = o.customer_id
JOIN order_item oi ON oi.order_id = o.id
JOIN product p     ON p.id = oi.product_id
ORDER BY o.id, p.name;
```

---

## LEFT JOIN (LEFT OUTER JOIN)

Returns all rows from the **left** table, and matching rows from the right. Non-matching right rows come back as NULL:

```sql
-- All customers, even those with no orders:
SELECT
    c.name,
    COUNT(o.id)           AS order_count,
    COALESCE(SUM(o.total_amount), 0) AS total_spent
FROM customer c
LEFT JOIN "order" o ON o.customer_id = c.id
GROUP BY c.id, c.name
ORDER BY total_spent DESC;

-- Products with their review summary (including products with no reviews):
SELECT
    p.name,
    COUNT(r.id)       AS review_count,
    AVG(r.rating)     AS avg_rating
FROM product p
LEFT JOIN review r ON r.product_id = p.id
GROUP BY p.id, p.name;

-- Find products with NO reviews (anti-join pattern):
SELECT p.name
FROM product p
LEFT JOIN review r ON r.product_id = p.id
WHERE r.id IS NULL;   -- right side is NULL = no match = no reviews
```

---

## RIGHT JOIN (RIGHT OUTER JOIN)

All rows from the **right** table, matching rows from the left. Uncommon — you can always rewrite as a LEFT JOIN by swapping table order:

```sql
-- Equivalent: all orders, matching customer (if exists):
SELECT o.id, c.name
FROM customer c
RIGHT JOIN "order" o ON o.customer_id = c.id;

-- Same as:
SELECT o.id, c.name
FROM "order" o
LEFT JOIN customer c ON c.id = o.customer_id;
```

---

## FULL OUTER JOIN

Returns all rows from **both** tables. Unmatched rows from either side get NULLs:

```sql
-- Useful for reconciliation: find rows in one table not in the other:
SELECT
    c.name       AS customer_name,
    o.id         AS order_id,
    o.total_amount
FROM customer c
FULL OUTER JOIN "order" o ON o.customer_id = c.id
WHERE c.id IS NULL OR o.id IS NULL;  -- rows with no match on either side
```

---

## CROSS JOIN (Cartesian Product)

Every row from the left table combined with every row from the right table. Produces M × N rows:

```sql
-- Generate all combinations:
SELECT c.name AS category, s.status
FROM category c
CROSS JOIN (VALUES ('pending'), ('shipped'), ('delivered')) AS s(status);

-- Use case: generate a grid for reporting (all months × all categories):
SELECT
    generate_series(1, 12) AS month,
    c.name AS category
FROM category c
ORDER BY month, category;
```

---

## SELF JOIN

A table joined to itself — useful for hierarchical or comparative data:

```sql
-- Category tree: show category name with its parent name:
SELECT
    child.name   AS subcategory,
    parent.name  AS parent_category
FROM category child
LEFT JOIN category parent ON parent.id = child.parent_id
ORDER BY parent.name NULLS FIRST, child.name;

-- Find customers who ordered the same product as another customer:
SELECT DISTINCT
    c1.name AS customer_1,
    c2.name AS customer_2,
    p.name  AS common_product
FROM order_item oi1
JOIN order_item oi2  ON oi2.product_id = oi1.product_id AND oi2.order_id <> oi1.order_id
JOIN "order" o1      ON o1.id = oi1.order_id
JOIN "order" o2      ON o2.id = oi2.order_id
JOIN customer c1     ON c1.id = o1.customer_id
JOIN customer c2     ON c2.id = o2.customer_id
JOIN product p       ON p.id = oi1.product_id
WHERE c1.id < c2.id;  -- avoid duplicates (A-B and B-A are the same pair)
```

---

## JOIN with Multiple Conditions

```sql
-- Join on multiple columns:
SELECT p.*, r.rating
FROM product p
JOIN review r ON r.product_id = p.id AND r.customer_id = 42;

-- Non-equi join (joining on inequality):
SELECT
    p.name              AS product,
    ROUND(p.price, 0)   AS price,
    pt.label            AS tier
FROM product p
JOIN (VALUES
    (0.00,   19.99, 'Budget'),
    (20.00,  99.99, 'Mid-range'),
    (100.00, 9999.99, 'Premium')
) AS pt(min_price, max_price, label)
ON p.price BETWEEN pt.min_price AND pt.max_price;
```

---

## USING vs ON

```sql
-- ON: explicit, always works:
JOIN customer c ON c.id = o.customer_id

-- USING: shorthand when column names are identical in both tables:
JOIN customer USING (customer_id)
-- Produces one column 'customer_id' instead of two; can be ambiguous
```

---

## JOIN Performance Tips

```sql
-- Always join on indexed columns. Foreign keys should be indexed:
CREATE INDEX ON order_item (order_id);
CREATE INDEX ON order_item (product_id);
CREATE INDEX ON "order" (customer_id);

-- Filter early to reduce join input size:
SELECT p.name, oi.quantity
FROM order_item oi
JOIN "order" o ON o.id = oi.order_id AND o.status = 'shipped'  -- filter in join condition
JOIN product p ON p.id = oi.product_id
WHERE p.category_id = 1;

-- Avoid OR in join conditions — they often prevent index use:
-- ❌
JOIN a ON a.id = b.a_id OR a.id = b.b_id
-- ✅ Use UNION instead
```

---

## Try It Yourself

```sql
-- 1. Full order summary with all related data:
SELECT
    o.id                                     AS order_id,
    c.name                                   AS customer,
    o.status,
    p.name                                   AS product,
    oi.quantity,
    oi.unit_price,
    oi.quantity * oi.unit_price              AS line_total,
    o.total_amount                           AS order_total,
    to_char(o.created_at, 'YYYY-MM-DD')      AS order_date
FROM "order" o
JOIN customer   c   ON c.id  = o.customer_id
JOIN order_item oi  ON oi.order_id = o.id
JOIN product    p   ON p.id  = oi.product_id
ORDER BY o.id, p.name;

-- 2. Category hierarchy (parent → children):
SELECT
    COALESCE(parent.name, '(root)') AS parent,
    child.name                       AS child,
    COUNT(p.id)                      AS product_count
FROM category child
LEFT JOIN category parent   ON parent.id = child.parent_id
LEFT JOIN product p         ON p.category_id = child.id
GROUP BY parent.name, child.name
ORDER BY parent.name NULLS FIRST, child.name;

-- 3. Customers with total spend and order count (include customers with zero orders):
SELECT
    c.name,
    c.email,
    COUNT(o.id)                      AS total_orders,
    COALESCE(SUM(o.total_amount), 0) AS total_spent
FROM customer c
LEFT JOIN "order" o ON o.customer_id = c.id
GROUP BY c.id, c.name, c.email
ORDER BY total_spent DESC;
```

<details>
<summary>Query 3 — why LEFT JOIN rather than INNER JOIN?</summary>

With INNER JOIN, customers who have never placed an order would be excluded from the results entirely. With LEFT JOIN, they appear with `total_orders = 0` and `total_spent = 0.00` — which is what a customer list page should show.

The `COALESCE(SUM(...), 0)` handles the case where all joined rows are NULL (no orders): SUM of NULLs = NULL, and COALESCE converts that to 0.

</details>

---

## Capstone Connection

Every StoreForge API response that produces a full "order with items" view uses a chain of JOINs:

```sql
-- The "My Orders" page query:
SELECT
    o.id, o.status, o.total_amount, o.created_at,
    json_agg(json_build_object(
        'product', p.name,
        'quantity', oi.quantity,
        'unit_price', oi.unit_price
    ) ORDER BY p.name) AS items
FROM "order" o
JOIN order_item oi ON oi.order_id = o.id
JOIN product p     ON p.id = oi.product_id
WHERE o.customer_id = :customer_id
GROUP BY o.id
ORDER BY o.created_at DESC;
```

This pattern — JOIN + GROUP BY + json_agg — returns one row per order with all items embedded as JSON, perfect for a REST API response.
