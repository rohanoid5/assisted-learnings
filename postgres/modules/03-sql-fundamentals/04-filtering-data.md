# Filtering Data: WHERE, ORDER BY, and Pattern Matching

## Concept

The `WHERE` clause is the most powerful tool for retrieving exactly the rows you need. This lesson covers comparison operators, logical operators, pattern matching, range tests, and sorting — the building blocks of every real-world query.

---

## Comparison Operators

```sql
-- Basic comparisons:
SELECT * FROM product WHERE price = 49.99;
SELECT * FROM product WHERE price <> 49.99;    -- not equal (also != works)
SELECT * FROM product WHERE price > 50;
SELECT * FROM product WHERE price >= 50;
SELECT * FROM product WHERE price < 20;
SELECT * FROM product WHERE price <= 20;

-- NULL comparisons (must use IS NULL, not = NULL):
SELECT * FROM review WHERE comment IS NULL;
SELECT * FROM review WHERE comment IS NOT NULL;
SELECT * FROM customer WHERE phone IS DISTINCT FROM NULL;  -- cleaner IS NOT NULL
SELECT * FROM product WHERE discount IS NOT DISTINCT FROM NULL;  -- includes NULL
```

---

## Logical Operators: AND, OR, NOT

```sql
-- AND: all conditions must be true:
SELECT * FROM product 
WHERE price < 100 AND stock_quantity > 0 AND is_active = true;

-- OR: any condition can be true:
SELECT * FROM "order"
WHERE status = 'pending' OR status = 'confirmed';

-- NOT: negate a condition:
SELECT * FROM product WHERE NOT is_active;
SELECT * FROM customer WHERE NOT (email LIKE '%@gmail.com');

-- Complex: parentheses control evaluation order:
SELECT * FROM product
WHERE (price < 20 OR price > 500)
  AND stock_quantity > 0
  AND is_active;
```

---

## IN and NOT IN

```sql
-- IN: match any value in the list:
SELECT * FROM "order"
WHERE status IN ('pending', 'confirmed', 'shipped');

-- NOT IN: exclude values:
SELECT * FROM "order"
WHERE status NOT IN ('cancelled', 'refunded');

-- IN with a subquery:
SELECT * FROM product
WHERE id IN (
    SELECT DISTINCT product_id FROM order_item
    WHERE order_id IN (
        SELECT id FROM "order" WHERE customer_id = 42
    )
);

-- ⚠️ NOT IN with NULLs is dangerous:
-- If the subquery returns even one NULL, NOT IN returns zero rows:
SELECT * FROM product WHERE id NOT IN (SELECT product_id FROM review);
-- If any review.product_id is NULL → returns nothing

-- Fix: use NOT EXISTS or IS DISTINCT FROM:
SELECT * FROM product p
WHERE NOT EXISTS (SELECT 1 FROM review r WHERE r.product_id = p.id);
```

---

## BETWEEN

```sql
-- Inclusive range:
SELECT * FROM product WHERE price BETWEEN 20 AND 100;
-- Equivalent to: WHERE price >= 20 AND price <= 100

-- Date range:
SELECT * FROM "order"
WHERE created_at BETWEEN '2024-01-01' AND '2024-12-31 23:59:59';

-- Better for dates: use >= and < to avoid time-of-day issues:
SELECT * FROM "order"
WHERE created_at >= '2024-01-01'
  AND created_at <  '2025-01-01';

-- NOT BETWEEN:
SELECT * FROM product WHERE price NOT BETWEEN 10 AND 50;
```

---

## LIKE and ILIKE: Pattern Matching

```sql
-- LIKE: case-sensitive pattern matching
-- % = any sequence of characters
-- _ = any single character

SELECT * FROM product WHERE name LIKE 'Wire%';    -- starts with 'Wire'
SELECT * FROM product WHERE name LIKE '%phone';   -- ends with 'phone'
SELECT * FROM product WHERE name LIKE '%head%';   -- contains 'head'
SELECT * FROM product WHERE slug LIKE '%-pro-_';  -- contains '-pro-' followed by 1 char

-- ILIKE: case-insensitive (PostgreSQL extension):
SELECT * FROM product WHERE name ILIKE '%headphone%';  -- matches "Headphone", "HEADPHONE", etc.

-- Escape the wildcard character:
SELECT * FROM product WHERE name LIKE '50\%% off';  -- matches '50% off ...'
SELECT * FROM product WHERE name LIKE '50!% off' ESCAPE '!';  -- alternative escape

-- NOT LIKE / NOT ILIKE:
SELECT * FROM customer WHERE email NOT LIKE '%@gmail.com';
```

### Trigram similarity (fuzzy matching)

```sql
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- Find products with names similar to a typo:
SELECT name, similarity(name, 'headfone') AS sim
FROM product
WHERE similarity(name, 'headfone') > 0.3
ORDER BY sim DESC;

-- Index for fast LIKE/ILIKE on large tables:
CREATE INDEX ON product USING GIN (name gin_trgm_ops);
-- After this index: ILIKE '%headphone%' uses the index instead of seq scan
```

---

## Regular Expressions

```sql
-- ~ : matches regex (case-sensitive)
-- ~* : matches regex (case-insensitive)
-- !~ : does not match
-- !~* : does not match (case-insensitive)

-- Products starting with a letter followed by digits:
SELECT * FROM product WHERE slug ~ '^[a-z]+-[0-9]+$';

-- Emails from non-gmail domains:
SELECT * FROM customer WHERE email !~* '@gmail\.com$';

-- Extract part of a string with regex:
SELECT regexp_replace(email, '@.*', '') AS username FROM customer;
SELECT (regexp_match(slug, '^([a-z]+)-'))[1] AS first_word FROM product;
```

---

## ANY and ALL (Operators with Arrays/Subqueries)

```sql
-- ANY: value matches any element:
SELECT * FROM product WHERE price = ANY(ARRAY[19.99, 29.99, 49.99]);
SELECT * FROM product WHERE price > ANY(SELECT price FROM product WHERE category_id = 1);

-- ALL: value satisfies condition for all elements:
SELECT * FROM product WHERE price > ALL(SELECT price FROM product WHERE category_id = 2);
-- Returns products more expensive than every product in category 2
```

---

## ORDER BY

```sql
-- Ascending (default):
SELECT name, price FROM product ORDER BY price;
SELECT name, price FROM product ORDER BY price ASC;

-- Descending:
SELECT name, price FROM product ORDER BY price DESC;

-- Multiple columns:
SELECT name, category_id, price FROM product
ORDER BY category_id ASC, price DESC;

-- By column alias:
SELECT name, price * 1.1 AS final_price FROM product
ORDER BY final_price DESC;

-- By column position (fragile — avoid):
SELECT name, price FROM product ORDER BY 2 DESC;  -- 2 = second column

-- NULLs placement (PostgreSQL defaults: NULLs LAST for ASC, NULLS FIRST for DESC):
SELECT name, phone FROM customer ORDER BY phone ASC NULLS LAST;
SELECT name, phone FROM customer ORDER BY phone DESC NULLS LAST;

-- Random order (useful for sampling):
SELECT * FROM product ORDER BY RANDOM() LIMIT 5;
```

---

## Filtering with Subqueries in WHERE

```sql
-- Products that have at least one review with rating >= 4:
SELECT * FROM product
WHERE id IN (SELECT product_id FROM review WHERE rating >= 4);

-- Customers who have never placed an order:
SELECT * FROM customer
WHERE id NOT IN (SELECT DISTINCT customer_id FROM "order");

-- Products below average price in their category:
SELECT p.*
FROM product p
WHERE p.price < (
    SELECT AVG(p2.price)
    FROM product p2
    WHERE p2.category_id = p.category_id
);
```

---

## Try It Yourself

```sql
-- 1. Find all active products priced between $10 and $199.99, ordered by price:
SELECT name, price, stock_quantity
FROM product
WHERE is_active = true
  AND price BETWEEN 10 AND 199.99
ORDER BY price;

-- 2. Find customers whose email contains 'gmail' (case-insensitive):
SELECT name, email FROM customer WHERE email ILIKE '%gmail%';

-- 3. Find orders from the last 7 days that are NOT in 'cancelled' or 'refunded' status:
SELECT id, customer_id, status, total_amount
FROM "order"
WHERE created_at >= now() - INTERVAL '7 days'
  AND status NOT IN ('cancelled', 'refunded')
ORDER BY created_at DESC;

-- 4. Find products with no reviews:
SELECT p.name, p.price
FROM product p
WHERE NOT EXISTS (
    SELECT 1 FROM review r WHERE r.product_id = p.id
)
ORDER BY p.name;

-- 5. Find products where stock is low (between 1 and 9 units):
SELECT name, stock_quantity,
    CASE WHEN stock_quantity = 0 THEN 'OUT OF STOCK'
         WHEN stock_quantity < 5 THEN 'CRITICAL'
         ELSE 'LOW'
    END AS alert_level
FROM product
WHERE stock_quantity BETWEEN 1 AND 9
ORDER BY stock_quantity;
```

<details>
<summary>Notes on expected outputs</summary>

**Query 4 — NOT EXISTS vs NOT IN:**
`NOT EXISTS` is safer than `NOT IN` for this use case. If `review.product_id` could ever be NULL, `NOT IN` would return zero rows (three-valued logic issue). `NOT EXISTS` correctly handles NULLs and is typically better optimized by PostgreSQL.

**Query 3 — Date range:**
`now() - INTERVAL '7 days'` computes a timestamp exactly 7 days before the current moment. This is more precise than comparing to a truncated date. If you want "since the start of 7 days ago," use `DATE_TRUNC('day', now()) - INTERVAL '7 days'`.

</details>

---

## Capstone Connection

- **Product search by price range** — `WHERE price BETWEEN :min AND :max AND is_active = true`
- **Order history filter** — `WHERE customer_id = :id AND created_at >= :start AND status NOT IN ('cancelled', 'refunded')`
- **Low inventory alert** — `WHERE stock_quantity BETWEEN 1 AND 9 ORDER BY stock_quantity`
- **Product name search** — `WHERE name ILIKE '%' || :query || '%'` (use GIN index + tsvector for production)
