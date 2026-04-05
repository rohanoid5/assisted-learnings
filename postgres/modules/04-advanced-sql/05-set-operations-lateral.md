# Set Operations and LATERAL Joins

## Concept

Set operations — `UNION`, `INTERSECT`, and `EXCEPT` — combine results from multiple `SELECT` statements using relational algebra. `LATERAL` joins are a different but complementary tool: they let a subquery in the `FROM` clause reference columns from earlier in the same `FROM` clause, enabling powerful patterns like top-N per group without window functions.

---

## UNION: Combine Rows

`UNION` merges two result sets with the same number and type of columns:

```sql
-- UNION ALL: Keep duplicates (faster — no dedup pass):
SELECT 'active_customer' AS source, id, name, email FROM customer WHERE is_active = true
UNION ALL
SELECT 'inactive_customer', id, name, email FROM customer WHERE is_active = false;

-- UNION (no ALL): Remove duplicates (adds a sort/hash step):
-- Find all entity IDs that appear in either table:
SELECT product_id AS id FROM order_item
UNION
SELECT product_id AS id FROM review;

-- Combine two search results:
SELECT id, name, 'product'  AS type FROM product WHERE name ILIKE '%wireless%'
UNION ALL
SELECT id, name, 'category' AS type FROM category WHERE name ILIKE '%wireless%'
ORDER BY type, name;
```

**Rule:** Column count and types must be compatible. Column names come from the first SELECT.

---

## INTERSECT: Common Rows

`INTERSECT` returns only rows that appear in both result sets:

```sql
-- Products that have been ordered AND reviewed:
SELECT product_id FROM order_item
INTERSECT
SELECT product_id FROM review;

-- Customers with a delivered order AND who left a review:
SELECT customer_id FROM "order" WHERE status = 'delivered'
INTERSECT
SELECT customer_id FROM review;

-- INTERSECT ALL: Keep duplicates in intersection:
SELECT product_id FROM order_item
INTERSECT ALL
SELECT product_id FROM review;
```

---

## EXCEPT: Rows in First but Not Second

`EXCEPT` is the set difference operator — rows in the first query that are NOT in the second:

```sql
-- Products that have been ordered but never reviewed:
SELECT DISTINCT product_id FROM order_item
EXCEPT
SELECT product_id FROM review;
-- Equivalent to NOT EXISTS; EXCEPT is cleaner for simple cases

-- Active customers who have never placed an order:
SELECT id FROM customer WHERE is_active = true
EXCEPT
SELECT DISTINCT customer_id FROM "order";

-- Products in category 1 but not in any order from the past month:
SELECT id FROM product WHERE category_id = 1
EXCEPT
SELECT DISTINCT oi.product_id
FROM order_item oi
JOIN "order" o ON o.id = oi.order_id
WHERE o.created_at >= NOW() - INTERVAL '30 days';
```

---

## LATERAL Joins

A `LATERAL` join is like a correlated subquery in the `FROM` clause — it can reference columns from tables to its left. This makes it perfect for "top-N per group" queries without window functions.

```sql
-- Top 2 products per category (LATERAL):
SELECT c.name AS category, p.name AS product, p.price
FROM category c
CROSS JOIN LATERAL (
    SELECT name, price
    FROM product
    WHERE category_id = c.id   -- ← references 'c' from the outer FROM
      AND is_active = true
    ORDER BY price DESC
    LIMIT 2
) AS p
ORDER BY c.name, p.price DESC;

-- Most recent order per customer (LATERAL):
SELECT cu.name, cu.email, recent.id AS last_order_id, recent.created_at
FROM customer cu
LEFT JOIN LATERAL (
    SELECT id, created_at, total_amount
    FROM "order"
    WHERE customer_id = cu.id
    ORDER BY created_at DESC
    LIMIT 1
) AS recent ON true
WHERE cu.is_active = true;
-- LEFT JOIN LATERAL ... ON true: keeps customers with no orders (recent.* is NULL)

-- Unnest JSONB array with LATERAL:
-- If product.attributes contains {"colors": ["red", "blue", "green"]}:
SELECT p.name, color.value
FROM product p
CROSS JOIN LATERAL jsonb_array_elements_text(p.attributes -> 'colors') AS color(value)
WHERE p.is_active = true;
```

---

## LATERAL vs Correlated Subquery

`LATERAL` shines when you need multiple columns from the subquery or multiple rows:

```sql
-- With correlated subquery (can only return one value per row):
SELECT
    c.name,
    (SELECT MAX(created_at) FROM "order" WHERE customer_id = c.id) AS last_order_at
    -- Can't also get the order total in the same subquery without another subquery
FROM customer c;

-- With LATERAL (multiple columns in one pass):
SELECT
    c.name,
    recent.created_at AS last_order_at,
    recent.total_amount AS last_order_amount,
    recent.status AS last_order_status
FROM customer c
LEFT JOIN LATERAL (
    SELECT created_at, total_amount, status
    FROM "order"
    WHERE customer_id = c.id
    ORDER BY created_at DESC
    LIMIT 1
) AS recent ON true;
```

---

## LATERAL with Functions

`LATERAL` is implicit when calling set-returning functions in `FROM`:

```sql
-- Unnest array (implicit LATERAL):
SELECT p.name, tag
FROM product p, unnest(p.tags) AS tag  -- tags is text[]
WHERE p.is_active = true;

-- Same as explicit LATERAL:
SELECT p.name, tags.tag
FROM product p
CROSS JOIN LATERAL unnest(p.tags) AS tags(tag)
WHERE p.is_active = true;

-- generate_series to expand date ranges:
SELECT g.date, COUNT(o.id) AS order_count
FROM generate_series(
    '2024-01-01'::DATE,
    '2024-12-31'::DATE,
    INTERVAL '1 day'
) AS g(date)
LEFT JOIN "order" o ON o.created_at::DATE = g.date
GROUP BY g.date
ORDER BY g.date;
-- Shows every day (even those with 0 orders) — not possible with JOIN alone
```

---

## Try It Yourself

```sql
-- 1. Find products that appear in orders but have never been reviewed.
--    Use EXCEPT.

-- 2. Use UNION ALL to build a combined "activity feed":
--    - Orders placed (type='order', timestamp=created_at)
--    - Reviews submitted (type='review', timestamp=created_at)
--    Show the 10 most recent events.

-- 3. For each category, show the cheapest and most expensive active product
--    using a LATERAL join.

-- 4. Use LATERAL to find the top 3 reviewers (by number of reviews)
--    per product category.
```

<details>
<summary>Show solutions</summary>

```sql
-- 1. Products ordered but never reviewed:
SELECT DISTINCT product_id FROM order_item
EXCEPT
SELECT product_id FROM review;

-- 2. Activity feed:
(
    SELECT 'order'  AS type, id, customer_id, created_at FROM "order"
    UNION ALL
    SELECT 'review', id, customer_id, created_at FROM review
)
ORDER BY created_at DESC
LIMIT 10;

-- 3. Cheapest and most expensive per category using LATERAL:
SELECT c.name AS category, cheapest.name AS cheapest, expensive.name AS most_expensive
FROM category c
CROSS JOIN LATERAL (
    SELECT name, price FROM product
    WHERE category_id = c.id AND is_active = true
    ORDER BY price ASC LIMIT 1
) AS cheapest
CROSS JOIN LATERAL (
    SELECT name, price FROM product
    WHERE category_id = c.id AND is_active = true
    ORDER BY price DESC LIMIT 1
) AS expensive;

-- 4. Top 3 reviewers per category:
SELECT c.name AS category, reviewer.customer_id, reviewer.review_count
FROM category c
CROSS JOIN LATERAL (
    SELECT r.customer_id, COUNT(*) AS review_count
    FROM review r
    JOIN product p ON p.id = r.product_id
    WHERE p.category_id = c.id
    GROUP BY r.customer_id
    ORDER BY review_count DESC
    LIMIT 3
) AS reviewer
ORDER BY c.name, reviewer.review_count DESC;
```

</details>

---

## Capstone Connection

StoreForge uses LATERAL joins in two key places:
1. **Product listing API** — for each category in a category tree, find the top 3 products sorted by review rating x sales count using `CROSS JOIN LATERAL (...) AS top_products`
2. **Daily email digest** — uses `generate_series` + `LEFT JOIN ... LATERAL` to produce a complete 30-day activity timeline, filling in zero-activity days that would be invisible with a plain JOIN
