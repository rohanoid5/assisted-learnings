# Common Table Expressions (CTEs)

## Concept

A Common Table Expression (CTE) is a named temporary result set defined with a `WITH` clause. It makes complex queries readable by breaking them into named steps — like assigning intermediate results to variables. PostgreSQL also supports recursive CTEs for hierarchical data (like category trees) and writeable CTEs for multi-step DML.

---

## Basic CTE Syntax

```sql
-- Simple CTE:
WITH active_products AS (
    SELECT id, name, price, category_id
    FROM product
    WHERE is_active = true
)
SELECT p.name, p.price, c.name AS category
FROM active_products p
JOIN category c ON c.id = p.category_id
WHERE p.price > 100
ORDER BY p.price DESC;

-- Multiple CTEs (comma-separated):
WITH
high_value_orders AS (
    SELECT id, customer_id, total_amount
    FROM "order"
    WHERE total_amount > 500 AND status = 'delivered'
),
top_customers AS (
    SELECT customer_id, SUM(total_amount) AS lifetime_value
    FROM high_value_orders
    GROUP BY customer_id
    HAVING SUM(total_amount) > 1000
)
SELECT c.name, c.email, tc.lifetime_value
FROM top_customers tc
JOIN customer c ON c.id = tc.customer_id
ORDER BY tc.lifetime_value DESC;
```

---

## CTE vs Subquery

Use CTEs when:
- The same subquery appears multiple times (CTEs are written once, referenced many times)
- The query has 3+ levels of nesting (CTEs flatten the structure)
- You need a recursive query
- You want named steps for readability

```sql
-- Subquery version (nested, harder to read):
SELECT name, revenue
FROM (
    SELECT customer_id, SUM(total_amount) AS revenue
    FROM "order"
    WHERE status = 'delivered'
    GROUP BY customer_id
) AS r
WHERE revenue > 1000;

-- CTE version (readable):
WITH customer_revenue AS (
    SELECT customer_id, SUM(total_amount) AS revenue
    FROM "order"
    WHERE status = 'delivered'
    GROUP BY customer_id
)
SELECT c.name, cr.revenue
FROM customer_revenue cr
JOIN customer c ON c.id = cr.customer_id
WHERE cr.revenue > 1000;
```

> **Performance note:** In PostgreSQL 12+, CTEs are inlined by default (the planner can push conditions through them). Use `WITH ... AS MATERIALIZED (...)` to force the old evaluation fence behavior when you need it.

---

## Recursive CTEs

Recursive CTEs are essential for querying hierarchical data — like StoreForge's self-referencing category tree (`category.parent_id → category.id`).

```sql
-- Category hierarchy with full path:
WITH RECURSIVE category_tree AS (
    -- Base case: top-level categories (no parent):
    SELECT
        id,
        name,
        parent_id,
        name::TEXT AS full_path,
        0 AS depth
    FROM category
    WHERE parent_id IS NULL

    UNION ALL

    -- Recursive case: children of the previous level:
    SELECT
        c.id,
        c.name,
        c.parent_id,
        (ct.full_path || ' > ' || c.name)::TEXT AS full_path,
        ct.depth + 1 AS depth
    FROM category c
    JOIN category_tree ct ON ct.id = c.parent_id
)
SELECT id, name, full_path, depth
FROM category_tree
ORDER BY full_path;

-- Find all descendants of "Electronics" (category id = 1):
WITH RECURSIVE subcategories AS (
    SELECT id, name FROM category WHERE id = 1  -- start node
    UNION ALL
    SELECT c.id, c.name
    FROM category c
    JOIN subcategories s ON s.id = c.parent_id
)
SELECT * FROM subcategories;

-- Count products per category including descendants:
WITH RECURSIVE subcategories AS (
    SELECT id FROM category WHERE id = 1
    UNION ALL
    SELECT c.id FROM category c JOIN subcategories s ON s.id = c.parent_id
),
product_counts AS (
    SELECT COUNT(*) AS product_count FROM product WHERE category_id IN (SELECT id FROM subcategories)
)
SELECT product_count FROM product_counts;
```

---

## Writeable CTEs (Data-Modifying)

CTEs can contain INSERT, UPDATE, and DELETE statements, enabling complex multi-step DML in a single query:

```sql
-- Deactivate old customers AND log the action in a single statement:
WITH deactivated AS (
    UPDATE customer
    SET is_active = false, updated_at = NOW()
    WHERE last_login < NOW() - INTERVAL '1 year'
      AND is_active = true
    RETURNING id, name, email
)
INSERT INTO audit_log (table_name, operation, row_id, new_data, changed_by, changed_at)
SELECT
    'customer',
    'UPDATE',
    id,
    jsonb_build_object('is_active', false, 'name', name, 'email', email),
    'system',
    NOW()
FROM deactivated;
```

```sql
-- Move an order item from one order to another (delete + insert atomically):
WITH removed AS (
    DELETE FROM order_item
    WHERE id = 55
    RETURNING order_id, product_id, quantity, unit_price
)
INSERT INTO order_item (order_id, product_id, quantity, unit_price)
SELECT 99, product_id, quantity, unit_price   -- move to order 99
FROM removed;
```

---

## Grouping Sets, Cube, and Rollup

These advanced GROUP BY extensions generate multiple levels of aggregation in one query:

```sql
-- GROUPING SETS: Specify each grouping combination explicitly:
SELECT
    category_id,
    DATE_TRUNC('month', created_at) AS month,
    COUNT(*) AS product_count
FROM product
WHERE is_active = true
GROUP BY GROUPING SETS (
    (category_id, DATE_TRUNC('month', created_at)),  -- per category per month
    (category_id),                                    -- per category (all time)
    ()                                                -- grand total
)
ORDER BY category_id NULLS LAST, month NULLS LAST;

-- ROLLUP: Hierarchy — subtotals roll up from most specific to least:
SELECT
    c.name   AS category,
    TO_CHAR(o.created_at, 'YYYY-MM') AS month,
    SUM(oi.quantity * oi.unit_price) AS revenue
FROM order_item oi
JOIN "order" o ON o.id = oi.order_id
JOIN product p ON p.id = oi.product_id
JOIN category c ON c.id = p.category_id
GROUP BY ROLLUP (c.name, TO_CHAR(o.created_at, 'YYYY-MM'))
ORDER BY category NULLS LAST, month NULLS LAST;
-- Produces: (category + month), (category subtotal), (grand total)

-- CUBE: All possible combinations:
SELECT
    c.name   AS category,
    o.status,
    COUNT(*) AS order_count
FROM "order" o
JOIN order_item oi ON oi.order_id = o.id
JOIN product p ON p.id = oi.product_id
JOIN category c ON c.id = p.category_id
GROUP BY CUBE (c.name, o.status);
-- Produces: (category + status), (category), (status), (grand total)

-- FILTER clause: Conditional aggregation in a single GROUP BY:
SELECT
    category_id,
    COUNT(*) AS total_products,
    COUNT(*) FILTER (WHERE is_active = true)  AS active_products,
    COUNT(*) FILTER (WHERE stock_quantity = 0) AS out_of_stock
FROM product
GROUP BY category_id;
```

---

## Try It Yourself

```sql
-- 1. Write a CTE that:
--    a. Computes revenue per customer
--    b. Ranks customers by revenue
--    c. Returns only the top 5 with their rank

-- 2. Create the full category path for all leaf categories
--    (categories with no children). Use a recursive CTE.

-- 3. Use a writeable CTE to:
--    a. Soft-delete all reviews with rating = 1
--       (add is_deleted boolean to review if needed)
--    b. Log each deletion to audit_log in the same statement.

-- 4. Write a ROLLUP query showing revenue by category and month.
```

<details>
<summary>Show solutions</summary>

```sql
-- 1. Top 5 customers by revenue:
WITH customer_revenue AS (
    SELECT
        c.id,
        c.name,
        c.email,
        SUM(o.total_amount) AS total_spent
    FROM customer c
    JOIN "order" o ON o.customer_id = c.id
    WHERE o.status = 'delivered'
    GROUP BY c.id, c.name, c.email
),
ranked AS (
    SELECT *, RANK() OVER (ORDER BY total_spent DESC) AS revenue_rank
    FROM customer_revenue
)
SELECT name, email, total_spent, revenue_rank
FROM ranked
WHERE revenue_rank <= 5;

-- 2. Full paths for leaf categories:
WITH RECURSIVE category_tree AS (
    SELECT id, name, parent_id, name::TEXT AS path
    FROM category WHERE parent_id IS NULL
    UNION ALL
    SELECT c.id, c.name, c.parent_id, (ct.path || ' > ' || c.name)::TEXT
    FROM category c
    JOIN category_tree ct ON ct.id = c.parent_id
),
leaf_categories AS (
    SELECT id, name, path
    FROM category_tree
    WHERE id NOT IN (SELECT DISTINCT parent_id FROM category WHERE parent_id IS NOT NULL)
)
SELECT name, path FROM leaf_categories ORDER BY path;

-- 3. Writeable CTE for review soft-delete + audit:
-- (First add column: ALTER TABLE review ADD COLUMN is_deleted BOOLEAN DEFAULT false;)
WITH deleted_reviews AS (
    UPDATE review
    SET is_deleted = true
    WHERE rating = 1 AND is_deleted = false
    RETURNING id, product_id, customer_id, rating
)
INSERT INTO audit_log (table_name, operation, row_id, old_data, changed_by, changed_at)
SELECT
    'review',
    'DELETE',
    id,
    jsonb_build_object('product_id', product_id, 'customer_id', customer_id, 'rating', rating),
    CURRENT_USER,
    NOW()
FROM deleted_reviews;

-- 4. Revenue ROLLUP by category and month:
SELECT
    COALESCE(c.name, '(All Categories)')     AS category,
    COALESCE(TO_CHAR(o.created_at, 'YYYY-MM'), '(All Months)') AS month,
    SUM(oi.quantity * oi.unit_price)          AS revenue
FROM order_item oi
JOIN "order" o ON o.id = oi.order_id
JOIN product p ON p.id = oi.product_id
JOIN category c ON c.id = p.category_id
GROUP BY ROLLUP (c.name, TO_CHAR(o.created_at, 'YYYY-MM'))
ORDER BY category NULLS LAST, month NULLS LAST;
```

</details>

---

## Capstone Connection

StoreForge's category tree is a classic recursive structure: Electronics → Audio → Headphones. The `product_search()` function uses a recursive CTE to resolve all subcategory IDs before filtering products — so searching "Electronics" returns products from all nested subcategories. The admin reporting dashboard uses ROLLUP to produce the monthly revenue summary table in a single query.
