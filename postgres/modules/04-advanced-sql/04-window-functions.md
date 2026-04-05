# Window Functions

## Concept

Window functions are one of PostgreSQL's most powerful analytics tools. Unlike GROUP BY aggregates that collapse rows into one, window functions compute a value for each row using a "window" of related rows — without changing the number of output rows. They enable ranking, running totals, moving averages, lead/lag comparisons, and percentile calculations.

---

## Anatomy of a Window Function

```sql
function_name(args) OVER (
    PARTITION BY partition_column   -- divide rows into groups
    ORDER BY order_column           -- define ordering within each group
    ROWS/RANGE BETWEEN ...          -- define the frame of rows (optional)
)
```

- **`PARTITION BY`** — like GROUP BY but doesn't collapse rows; one partition per distinct value
- **`ORDER BY`** — order within the partition for ranking/running totals
- **Frame** — which rows relative to the current row are included (default varies by function)

---

## Ranking Functions

```sql
-- ROW_NUMBER: Unique sequential number, no ties:
SELECT
    name,
    price,
    ROW_NUMBER() OVER (ORDER BY price DESC) AS price_rank
FROM product
WHERE is_active = true;

-- RANK: Ties get same rank, next rank skips (1, 2, 2, 4):
SELECT
    name,
    price,
    RANK() OVER (ORDER BY price DESC) AS price_rank
FROM product
WHERE is_active = true;

-- DENSE_RANK: Ties get same rank, no skip (1, 2, 2, 3):
SELECT
    name,
    price,
    DENSE_RANK() OVER (ORDER BY price DESC) AS price_rank
FROM product
WHERE is_active = true;

-- NTILE: Divide rows into N equal buckets:
SELECT
    name,
    price,
    NTILE(4) OVER (ORDER BY price) AS price_quartile  -- Q1=cheap, Q4=expensive
FROM product
WHERE is_active = true;

-- PERCENT_RANK and CUME_DIST:
SELECT
    name,
    price,
    ROUND(100.0 * PERCENT_RANK() OVER (ORDER BY price), 1) AS percentile
FROM product
WHERE is_active = true;
```

---

## Ranking per Partition

The real power comes from combining `PARTITION BY` with ranking:

```sql
-- Top 3 products per category by price:
SELECT category_id, name, price, category_rank
FROM (
    SELECT
        category_id,
        name,
        price,
        RANK() OVER (PARTITION BY category_id ORDER BY price DESC) AS category_rank
    FROM product
    WHERE is_active = true
) AS ranked
WHERE category_rank <= 3
ORDER BY category_id, category_rank;

-- Each customer's most recent order:
SELECT customer_id, order_id, created_at, status
FROM (
    SELECT
        customer_id,
        id AS order_id,
        created_at,
        status,
        ROW_NUMBER() OVER (PARTITION BY customer_id ORDER BY created_at DESC) AS rn
    FROM "order"
) AS with_rn
WHERE rn = 1;

-- Best-reviewed product per category:
SELECT category_id, product_id, avg_rating, rating_rank
FROM (
    SELECT
        p.category_id,
        r.product_id,
        AVG(r.rating) AS avg_rating,
        RANK() OVER (PARTITION BY p.category_id ORDER BY AVG(r.rating) DESC) AS rating_rank
    FROM review r
    JOIN product p ON p.id = r.product_id
    GROUP BY p.category_id, r.product_id
) AS ratings
WHERE rating_rank = 1;
```

---

## LAG and LEAD: Comparing Rows

`LAG` accesses the previous row; `LEAD` accesses the next row in the ordered window.

```sql
-- Month-over-month revenue change:
WITH monthly_revenue AS (
    SELECT
        DATE_TRUNC('month', created_at) AS month,
        SUM(total_amount) AS revenue
    FROM "order"
    WHERE status = 'delivered'
    GROUP BY 1
)
SELECT
    month,
    revenue,
    LAG(revenue) OVER (ORDER BY month) AS prev_month_revenue,
    revenue - LAG(revenue) OVER (ORDER BY month) AS revenue_delta,
    ROUND(100.0 * (revenue - LAG(revenue) OVER (ORDER BY month))
         / NULLIF(LAG(revenue) OVER (ORDER BY month), 0), 1) AS pct_change
FROM monthly_revenue
ORDER BY month;

-- Next order date per customer (lead):
SELECT
    customer_id,
    id AS order_id,
    created_at,
    LEAD(created_at) OVER (PARTITION BY customer_id ORDER BY created_at) AS next_order_at
FROM "order"
ORDER BY customer_id, created_at;

-- LAG with offset and default:
LAG(price, 2, 0)  -- go back 2 rows, default to 0 if no row exists
OVER (PARTITION BY category_id ORDER BY created_at)
```

---

## Running and Moving Aggregates

```sql
-- Running total of order amounts per customer:
SELECT
    customer_id,
    id AS order_id,
    total_amount,
    SUM(total_amount) OVER (
        PARTITION BY customer_id
        ORDER BY created_at
        ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW
    ) AS running_total
FROM "order"
WHERE status = 'delivered';

-- 3-month moving average of revenue:
WITH monthly AS (
    SELECT DATE_TRUNC('month', created_at) AS month, SUM(total_amount) AS revenue
    FROM "order" WHERE status = 'delivered'
    GROUP BY 1
)
SELECT
    month,
    revenue,
    ROUND(AVG(revenue) OVER (
        ORDER BY month
        ROWS BETWEEN 2 PRECEDING AND CURRENT ROW  -- current + 2 prior months
    ), 2) AS moving_avg_3m
FROM monthly
ORDER BY month;

-- Cumulative percentage of revenue (running share of total):
SELECT
    name,
    price,
    SUM(price) OVER (ORDER BY price DESC) AS cumulative_price,
    SUM(price) OVER () AS grand_total,
    ROUND(100.0 * SUM(price) OVER (ORDER BY price DESC) / SUM(price) OVER (), 1) AS cumulative_pct
FROM product
WHERE is_active = true;
```

---

## Frame Specification

```sql
-- ROWS BETWEEN: Physical rows:
ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW  -- all rows up to and including current
ROWS BETWEEN 2 PRECEDING AND 2 FOLLOWING          -- ±2 rows (5-row window)
ROWS BETWEEN CURRENT ROW AND UNBOUNDED FOLLOWING  -- current row to end

-- RANGE BETWEEN: Logical range based on ORDER BY value:
RANGE BETWEEN INTERVAL '7 days' PRECEDING AND CURRENT ROW  -- last 7 days
RANGE BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW           -- default for most agg functions

-- Named window (reuse window definition):
SELECT
    name,
    price,
    SUM(price)   OVER w AS running_sum,
    AVG(price)   OVER w AS running_avg,
    COUNT(*)     OVER w AS running_count
FROM product
WHERE is_active = true
WINDOW w AS (ORDER BY created_at ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW);
```

---

## Try It Yourself

```sql
-- 1. For each product, show:
--    - product name, category, price
--    - rank within its category (highest price = 1)
--    - how its price compares to the category min and max

-- 2. Calculate month-over-month order count growth (% change).
--    - Use LAG to compare each month to the previous.

-- 3. Show the top-5 customers by lifetime value, with a running total of
--    revenue as you go from rank 1 to rank 5.

-- 4. For each customer, find how many days elapsed between their first and
--    second order. Use LAG or MIN/LEAD.
```

<details>
<summary>Show solutions</summary>

```sql
-- 1. Products ranked within category with min/max:
SELECT
    p.name,
    c.name AS category,
    p.price,
    RANK() OVER (PARTITION BY p.category_id ORDER BY p.price DESC) AS cat_rank,
    MIN(p.price) OVER (PARTITION BY p.category_id) AS cat_min,
    MAX(p.price) OVER (PARTITION BY p.category_id) AS cat_max
FROM product p
JOIN category c ON c.id = p.category_id
WHERE p.is_active = true
ORDER BY category, cat_rank;

-- 2. Month-over-month order count growth:
WITH monthly_counts AS (
    SELECT
        DATE_TRUNC('month', created_at) AS month,
        COUNT(*) AS order_count
    FROM "order"
    GROUP BY 1
)
SELECT
    month,
    order_count,
    LAG(order_count) OVER (ORDER BY month) AS prev_month,
    ROUND(100.0 * (order_count - LAG(order_count) OVER (ORDER BY month))
         / NULLIF(LAG(order_count) OVER (ORDER BY month), 0), 1) AS pct_growth
FROM monthly_counts
ORDER BY month;

-- 3. Top 5 customers with running revenue total:
WITH revenue AS (
    SELECT
        c.name,
        SUM(o.total_amount) AS total
    FROM customer c
    JOIN "order" o ON o.customer_id = c.id
    GROUP BY c.id, c.name
)
SELECT
    name,
    total,
    RANK() OVER (ORDER BY total DESC) AS revenue_rank,
    SUM(total) OVER (ORDER BY total DESC ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW) AS running_total
FROM revenue
ORDER BY revenue_rank
LIMIT 5;

-- 4. Days between first and second order per customer:
WITH numbered_orders AS (
    SELECT
        customer_id,
        created_at,
        ROW_NUMBER() OVER (PARTITION BY customer_id ORDER BY created_at) AS rn
    FROM "order"
),
first_two AS (
    SELECT customer_id, created_at, rn FROM numbered_orders WHERE rn <= 2
)
SELECT
    customer_id,
    MIN(created_at) AS first_order,
    MAX(created_at) AS second_order,
    EXTRACT(EPOCH FROM MAX(created_at) - MIN(created_at)) / 86400 AS days_between
FROM first_two
GROUP BY customer_id
HAVING COUNT(*) = 2
ORDER BY days_between;
```

</details>

---

## Capstone Connection

StoreForge uses window functions throughout its analytics layer:
- **Sales dashboard** — `SUM() OVER (ORDER BY month)` for cumulative revenue charts
- **Inventory alerts** — `LAG(stock_quantity) OVER (ORDER BY updated_at)` to detect sudden stock drops that may indicate fulfillment errors
- **Customer segmentation** — `NTILE(4) OVER (ORDER BY lifetime_value)` to classify customers into Bronze/Silver/Gold/Platinum tiers
- **Top products widget** — `RANK() OVER (PARTITION BY category_id ORDER BY sales_count DESC)` to show the #1 product per category
