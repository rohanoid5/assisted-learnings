# Subqueries and Derived Tables

## Concept

A subquery is a SELECT statement nested inside another SQL statement. Subqueries let you break complex problems into readable steps — answer one question, then use that answer as input to another. PostgreSQL supports subqueries in the `SELECT`, `FROM`, `WHERE`, and `HAVING` clauses, as well as subqueries that return single values, rows, or full result sets.

---

## Scalar Subqueries

A scalar subquery returns exactly one row and one column. It can be used anywhere a single value is expected.

```sql
-- Avg price of all active products:
SELECT
    name,
    price,
    price - (SELECT AVG(price) FROM product WHERE is_active = true) AS diff_from_avg
FROM product
WHERE is_active = true
ORDER BY diff_from_avg DESC;

-- Use in WHERE clause:
SELECT name, price
FROM product
WHERE price = (SELECT MAX(price) FROM product WHERE is_active = true);

-- Use in HAVING:
SELECT category_id, AVG(price) AS avg_price
FROM product
GROUP BY category_id
HAVING AVG(price) > (SELECT AVG(price) FROM product);
```

---

## Non-Correlated Subqueries

A non-correlated subquery runs once and its result is used by the outer query. PostgreSQL evaluates it as a constant.

```sql
-- Find all products priced above the average:
SELECT name, price
FROM product
WHERE price > (
    SELECT AVG(price) FROM product WHERE is_active = true
);

-- Find customers who have placed at least one order:
SELECT name, email
FROM customer
WHERE id IN (
    SELECT DISTINCT customer_id FROM "order"
);

-- Products that have never been ordered (NOT IN):
SELECT name
FROM product
WHERE id NOT IN (
    SELECT DISTINCT product_id FROM order_item
);
-- ⚠ NOT IN + NULL: If ANY product_id is NULL in order_item, this returns no rows!
-- Prefer NOT EXISTS (see below) to avoid the NULL trap.
```

---

## Correlated Subqueries

A correlated subquery references a column from the outer query. It runs once per outer row — use carefully for large data sets.

```sql
-- For each product, how many times has it been ordered?
SELECT
    p.name,
    (SELECT COUNT(*) FROM order_item oi WHERE oi.product_id = p.id) AS order_count
FROM product p
WHERE p.is_active = true
ORDER BY order_count DESC;

-- Find each customer's most recent order date:
SELECT
    c.name,
    (SELECT MAX(o.created_at)
     FROM "order" o
     WHERE o.customer_id = c.id) AS last_order_at
FROM customer c
WHERE c.is_active = true;
```

---

## EXISTS and NOT EXISTS

`EXISTS` short-circuits as soon as it finds one matching row — significantly faster than `IN` for large subquery results, and safe with NULLs.

```sql
-- Customers who have placed at least one order (EXISTS):
SELECT name, email
FROM customer c
WHERE EXISTS (
    SELECT 1 FROM "order" o WHERE o.customer_id = c.id
);

-- Products that have NEVER been reviewed (NOT EXISTS — NULL-safe):
SELECT p.name
FROM product p
WHERE NOT EXISTS (
    SELECT 1 FROM review r WHERE r.product_id = p.id
);

-- Products currently out of stock AND has pending orders (both conditions):
SELECT p.name, p.stock_quantity
FROM product p
WHERE p.stock_quantity = 0
  AND EXISTS (
      SELECT 1
      FROM order_item oi
      JOIN "order" o ON o.id = oi.order_id
      WHERE oi.product_id = p.id
        AND o.status = 'pending'
  );
```

---

## Derived Tables (Subquery in FROM)

A subquery in the `FROM` clause creates a temporary derived table that the outer query can reference like a real table.

```sql
-- Average price per category, filtered to categories averaging > $50:
SELECT category_name, avg_price
FROM (
    SELECT c.name AS category_name, AVG(p.price) AS avg_price
    FROM product p
    JOIN category c ON c.id = p.category_id
    WHERE p.is_active = true
    GROUP BY c.name
) AS category_stats
WHERE avg_price > 50
ORDER BY avg_price DESC;

-- Top 3 customers by revenue, with their rank:
SELECT customer_name, total_spent, revenue_rank
FROM (
    SELECT
        cu.name AS customer_name,
        SUM(o.total_amount) AS total_spent,
        RANK() OVER (ORDER BY SUM(o.total_amount) DESC) AS revenue_rank
    FROM customer cu
    JOIN "order" o ON o.customer_id = cu.id
    GROUP BY cu.id, cu.name
) AS customer_revenue
WHERE revenue_rank <= 3;
```

---

## Subqueries in UPDATE and DELETE

```sql
-- Mark products as inactive if they've had no orders in 6 months:
UPDATE product
SET is_active = false
WHERE id NOT IN (
    SELECT DISTINCT oi.product_id
    FROM order_item oi
    JOIN "order" o ON o.id = oi.order_id
    WHERE o.created_at >= NOW() - INTERVAL '6 months'
)
AND is_active = true
RETURNING name;

-- Delete reviews from inactive customers:
DELETE FROM review
WHERE customer_id IN (
    SELECT id FROM customer WHERE is_active = false
)
RETURNING id, product_id, rating;
```

---

## Try It Yourself

```sql
-- 1. Find the 3 most expensive active products per category.
-- Hint: use a derived table with RANK() or a correlated subquery.

-- 2. Find customers who have ordered but never left a review.
-- Hint: EXISTS + NOT EXISTS.

-- 3. Show each product with its price relative to the category average:
--    columns: product name, price, category avg price, % above/below avg.

-- 4. Find orders where every item is from the same category.
-- Hint: correlated subquery with COUNT and DISTINCT.
```

<details>
<summary>Show solutions</summary>

```sql
-- 1. Top 3 most expensive products per category:
SELECT category_name, product_name, price
FROM (
    SELECT
        c.name AS category_name,
        p.name AS product_name,
        p.price,
        RANK() OVER (PARTITION BY c.id ORDER BY p.price DESC) AS price_rank
    FROM product p
    JOIN category c ON c.id = p.category_id
    WHERE p.is_active = true
) AS ranked
WHERE price_rank <= 3
ORDER BY category_name, price_rank;

-- 2. Customers who ordered but never reviewed:
SELECT c.name, c.email
FROM customer c
WHERE EXISTS (
    SELECT 1 FROM "order" o WHERE o.customer_id = c.id
)
AND NOT EXISTS (
    SELECT 1 FROM review r WHERE r.customer_id = c.id
);

-- 3. Products vs category average:
SELECT
    p.name,
    p.price,
    ROUND(cat_avg.avg_price, 2) AS category_avg,
    ROUND(100.0 * (p.price - cat_avg.avg_price) / cat_avg.avg_price, 1) AS pct_diff
FROM product p
JOIN (
    SELECT category_id, AVG(price) AS avg_price
    FROM product WHERE is_active = true
    GROUP BY category_id
) AS cat_avg ON cat_avg.category_id = p.category_id
WHERE p.is_active = true
ORDER BY pct_diff DESC;

-- 4. Orders where all items are in the same category:
SELECT o.id, o.total_amount
FROM "order" o
WHERE (
    SELECT COUNT(DISTINCT p.category_id)
    FROM order_item oi
    JOIN product p ON p.id = oi.product_id
    WHERE oi.order_id = o.id
) = 1;
```

</details>

---

## Capstone Connection

StoreForge's product catalog API uses a derived table approach to compute category-level statistics (count, min price, max price, avg rating) in a single query rather than multiple round trips. The `NOT EXISTS` pattern also powers the "products with no reviews" feature of the admin dashboard — showing which products need attention — safely handling the NULL edge case that `NOT IN` would miss.
