# Querying Data: SELECT, FROM, and Basic Clauses

## Concept

`SELECT` is the most-used SQL statement. Mastering it — from simple column selection to expressions, functions, and aliasing — gives you the foundation for all analytical work. PostgreSQL's `SELECT` implements the full SQL standard plus many extensions.

---

## Basic SELECT Structure

```sql
SELECT  column_list       -- what to return (columns, expressions, functions)
FROM    table_name        -- where to get data
WHERE   condition         -- filter rows (optional)
GROUP BY columns          -- aggregate (optional)
HAVING  condition         -- filter aggregated groups (optional)
ORDER BY columns          -- sort (optional)
LIMIT   n                 -- max rows to return (optional)
OFFSET  n                 -- skip n rows (optional for pagination)
```

---

## Selecting Columns

```sql
-- All columns (avoid in production queries — fragile to schema changes):
SELECT * FROM customer;

-- Specific columns:
SELECT id, name, email FROM customer;

-- With aliases:
SELECT 
    id          AS customer_id,
    name        AS full_name,
    email       AS contact_email
FROM customer;

-- Expressions:
SELECT
    name,
    price,
    price * 1.1 AS price_with_tax,
    UPPER(name) AS name_upper,
    LENGTH(name) AS name_length
FROM product;

-- Literal values and functions:
SELECT
    'StoreForge'           AS platform,
    current_user           AS logged_in_as,
    current_database()     AS database,
    now()                  AS query_time,
    42                     AS the_answer;
```

---

## DISTINCT

```sql
-- Remove duplicate rows from result:
SELECT DISTINCT country FROM address;

-- On multiple columns (combination must be unique):
SELECT DISTINCT city, country FROM address ORDER BY country, city;

-- Count distinct values:
SELECT COUNT(DISTINCT country) AS unique_countries FROM address;
```

---

## Column Expressions and Functions

### String functions

```sql
SELECT
    name,
    LOWER(name)                AS name_lower,
    UPPER(name)                AS name_upper,
    LENGTH(name)               AS name_len,
    TRIM(name)                 AS name_trimmed,
    LEFT(name, 10)             AS name_preview,
    SUBSTRING(name FROM 1 FOR 5) AS first5,
    REPLACE(name, ' ', '-')   AS slug_candidate,
    CONCAT(name, ' (', id::TEXT, ')') AS display_name,
    name || ' — ' || description AS combined   -- || is pg string concat
FROM product;
```

### Numeric functions

```sql
SELECT
    price,
    ROUND(price, 1)            AS rounded_1dp,
    CEIL(price)                AS ceiling,
    FLOOR(price)               AS floor,
    ABS(price - 50)            AS distance_from_50,
    GREATEST(price, 10)        AS min_10,
    LEAST(price, 100)          AS max_100
FROM product;
```

### Date/time functions

```sql
SELECT
    created_at,
    EXTRACT(YEAR FROM created_at)   AS order_year,
    EXTRACT(MONTH FROM created_at)  AS order_month,
    EXTRACT(DOW FROM created_at)    AS day_of_week,  -- 0=Sunday
    DATE_TRUNC('month', created_at) AS month_start,
    DATE_TRUNC('week', created_at)  AS week_start,
    now() - created_at              AS age,
    created_at::DATE                AS date_only
FROM "order";
```

### Conditional expressions

```sql
-- CASE WHEN:
SELECT
    name,
    stock_quantity,
    CASE
        WHEN stock_quantity = 0     THEN 'Out of Stock'
        WHEN stock_quantity < 10    THEN 'Low Stock'
        WHEN stock_quantity < 100   THEN 'In Stock'
        ELSE 'Well Stocked'
    END AS stock_status
FROM product;

-- Simple CASE (equality checks):
SELECT
    status,
    CASE status
        WHEN 'pending'   THEN '⏳ Pending'
        WHEN 'shipped'   THEN '🚚 Shipped'
        WHEN 'delivered' THEN '✅ Delivered'
        ELSE '❓ ' || status::TEXT
    END AS status_label
FROM "order";

-- COALESCE (first non-null):
SELECT
    name,
    COALESCE(phone, 'No phone') AS phone_display
FROM customer;

-- NULLIF (returns null if values are equal — avoids division by zero):
SELECT 
    total_sales / NULLIF(num_orders, 0) AS avg_order_value
FROM some_aggregate;
```

---

## Aggregate Functions

Aggregate functions operate on a set of rows and return one value:

```sql
-- Count:
SELECT COUNT(*) FROM customer;
SELECT COUNT(phone) FROM customer;     -- excludes NULLs
SELECT COUNT(DISTINCT country) FROM address;

-- Numeric aggregates:
SELECT
    MIN(price)          AS cheapest,
    MAX(price)          AS most_expensive,
    AVG(price)          AS average_price,
    SUM(price)          AS total_list_value,
    STDDEV(price)       AS price_stddev,
    PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY price) AS median_price
FROM product;

-- String aggregation:
SELECT
    category_id,
    STRING_AGG(name, ', ' ORDER BY name) AS product_names
FROM product
GROUP BY category_id;

-- Array aggregation:
SELECT
    customer_id,
    ARRAY_AGG(product_id ORDER BY id) AS ordered_products
FROM order_item oi
JOIN "order" o ON o.id = oi.order_id
GROUP BY customer_id;
```

---

## GROUP BY

```sql
-- Count orders per status:
SELECT status, COUNT(*) AS count
FROM "order"
GROUP BY status
ORDER BY count DESC;

-- Total revenue per category:
SELECT
    c.name AS category,
    SUM(oi.quantity * oi.unit_price) AS revenue
FROM order_item oi
JOIN product p ON p.id = oi.product_id
JOIN category c ON c.id = p.category_id
GROUP BY c.id, c.name
ORDER BY revenue DESC;

-- Group by expression:
SELECT
    DATE_TRUNC('month', created_at) AS month,
    COUNT(*) AS orders,
    SUM(total_amount) AS revenue
FROM "order"
GROUP BY DATE_TRUNC('month', created_at)
ORDER BY month;

-- HAVING (filter on aggregated result):
SELECT
    customer_id,
    COUNT(*) AS order_count,
    SUM(total_amount) AS total_spent
FROM "order"
GROUP BY customer_id
HAVING SUM(total_amount) > 500     -- only customers who spent > $500
ORDER BY total_spent DESC;
```

---

## LIMIT and OFFSET (Pagination)

```sql
-- Get 10 most expensive products:
SELECT name, price FROM product ORDER BY price DESC LIMIT 10;

-- Page 3 (0-indexed pages of 10):
SELECT name, price FROM product ORDER BY price DESC LIMIT 10 OFFSET 20;

-- SQL standard equivalent:
SELECT name, price FROM product
ORDER BY price DESC
OFFSET 20 ROWS FETCH NEXT 10 ROWS ONLY;
```

⚠️ `OFFSET` pagination has a performance problem at large offsets — it scans and discards all skipped rows. For large datasets, use **keyset pagination**:

```sql
-- Instead of OFFSET, use the last seen id:
SELECT name, price FROM product
WHERE id > :last_seen_id
ORDER BY id
LIMIT 10;
```

---

## Try It Yourself

Run these queries against your seeded StoreForge database:

```sql
-- 1. List all products with their price including 10% tax, sorted by price:
SELECT
    name,
    price                        AS base_price,
    ROUND(price * 1.1, 2)        AS price_with_tax,
    CASE
        WHEN price < 20  THEN 'Budget'
        WHEN price < 100 THEN 'Mid-range'
        ELSE 'Premium'
    END AS tier
FROM product
ORDER BY price;

-- 2. Count products per category, showing category name:
SELECT
    c.name      AS category,
    COUNT(p.id) AS product_count,
    MIN(p.price) AS cheapest,
    MAX(p.price) AS priciest
FROM category c
LEFT JOIN product p ON p.category_id = c.id
GROUP BY c.id, c.name
ORDER BY product_count DESC;

-- 3. Monthly order summary for the current year:
SELECT
    DATE_TRUNC('month', created_at) AS month,
    COUNT(*)                         AS total_orders,
    SUM(total_amount)                AS total_revenue,
    AVG(total_amount)                AS avg_order_value
FROM "order"
WHERE EXTRACT(YEAR FROM created_at) = EXTRACT(YEAR FROM now())
GROUP BY DATE_TRUNC('month', created_at)
ORDER BY month;
```

<details>
<summary>Expected query 2 output (after seeding)</summary>

```
   category    | product_count | cheapest | priciest 
---------------+---------------+----------+----------
 Electronics   |            20 |    19.99 |   999.99
 Clothing      |            15 |     9.99 |   249.99
 Books         |            10 |     4.99 |    59.99
 Home & Garden |             5 |    12.99 |   199.99
(4 rows)
```

</details>

---

## Capstone Connection

The queries you've learned here power StoreForge's:
- **Product listings** — SELECT with LIMIT/OFFSET (or keyset pagination) and ORDER BY price/rating
- **Category product counts** — GROUP BY + COUNT for the category browse page
- **Sales reports** — GROUP BY DATE_TRUNC('month') + SUM for monthly revenue dashboards
- **Stock status labels** — CASE WHEN for the inventory management UI
