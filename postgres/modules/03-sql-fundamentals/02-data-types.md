# PostgreSQL Data Types: Choosing the Right Type

## Concept

Choosing the right data type for each column is one of the most impactful schema design decisions. The right type enforces domain constraints automatically, saves storage, enables type-specific operators and indexes, and prevents a class of application bugs entirely.

---

## Numeric Types

### Integer types

| Type | Storage | Range | Use for |
|------|---------|-------|---------|
| `SMALLINT` | 2 bytes | -32,768 to 32,767 | status codes, ratings |
| `INTEGER` / `INT` | 4 bytes | ±2.1 billion | most counters, quantities |
| `BIGINT` | 8 bytes | ±9.2 × 10^18 | PKs, user IDs, large counters |

```sql
-- StoreForge uses:
id             BIGINT GENERATED ALWAYS AS IDENTITY   -- primary keys
stock_quantity INTEGER                                -- product stock
rating         SMALLINT CHECK (rating BETWEEN 1 AND 5)  -- review rating
```

### Auto-increment patterns

```sql
-- Modern (SQL standard, preferred):
id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY

-- Legacy (still common, avoid for new schemas):
id BIGSERIAL PRIMARY KEY  -- shorthand for BIGINT + DEFAULT nextval(sequence)
```

### Decimal types

| Type | Precision | Use for |
|------|-----------|---------|
| `NUMERIC(p,s)` | Exact, arbitrary | Money, prices, financial calculations |
| `DECIMAL(p,s)` | Same as NUMERIC | Alias of NUMERIC |
| `REAL` | 6 decimal digits, inexact | Scientific data where approximation is OK |
| `DOUBLE PRECISION` | 15 decimal digits, inexact | Coordinates, physics |

```sql
-- ✅ Always use NUMERIC for money — never REAL or DOUBLE:
price      NUMERIC(10, 2)   -- up to 99,999,999.99
total      NUMERIC(12, 2)   -- up to 9,999,999,999.99
rating_avg NUMERIC(3, 2)    -- e.g., 4.75

-- ❌ This causes floating-point precision errors:
price REAL   -- 0.1 + 0.2 = 0.30000001192...
```

---

## Text Types

| Type | Behavior | Use for |
|------|----------|---------|
| `TEXT` | Unlimited length | Descriptions, notes, free-form content |
| `VARCHAR(n)` | Max n characters | Constrained strings (email, slug, country code) |
| `CHAR(n)` | Fixed n characters, space-padded | Rarely used; fixed codes |

```sql
-- StoreForge uses:
name     TEXT NOT NULL           -- no length limit needed
email    VARCHAR(255) NOT NULL   -- reasonable upper bound
country  CHAR(2) NOT NULL        -- always exactly 2 chars (ISO 3166)
slug     VARCHAR(200) NOT NULL   -- URL-safe identifier
```

**PostgreSQL insight:** `TEXT` and `VARCHAR` without a limit have identical performance. `VARCHAR(n)` is useful when you want to enforce a maximum length as a constraint.

### Case-insensitive text: citext

```sql
-- Enable the extension:
CREATE EXTENSION IF NOT EXISTS citext;

-- Use citext for case-insensitive equality:
email CITEXT NOT NULL UNIQUE
-- 'Alice@Example.com' = 'alice@example.com' → TRUE
```

### Full-text search: tsvector

```sql
-- tsvector: preprocessed text for full-text search
-- Covered in depth in Module 04; intro here:
search_vector tsvector

-- Convert text to tsvector:
SELECT to_tsvector('english', 'Wireless Noise Cancelling Headphones');
-- 'cancel':3 'headphon':4 'nois':2 'wireless':1
```

---

## Boolean

```sql
is_active   BOOLEAN NOT NULL DEFAULT true

-- Valid TRUE values: TRUE, 'true', 'yes', 'on', '1', 't', 'y'
-- Valid FALSE values: FALSE, 'false', 'no', 'off', '0', 'f', 'n'

SELECT is_active, NOT is_active AS is_inactive FROM customer WHERE id = 1;
```

---

## Date and Time Types

| Type | Storage | Range | Includes timezone? |
|------|---------|-------|-------------------|
| `DATE` | 4 bytes | 4713 BC to 5874897 AD | No |
| `TIME` | 8 bytes | 00:00:00 to 24:00:00 | No |
| `TIMETZ` | 12 bytes | — | Yes (avoid) |
| `TIMESTAMP` | 8 bytes | 4713 BC to 294276 AD | No |
| `TIMESTAMPTZ` | 8 bytes | Same | Yes (stored as UTC) |
| `INTERVAL` | 16 bytes | Durations | — |

```sql
-- Always use TIMESTAMPTZ for machine-generated timestamps:
created_at TIMESTAMPTZ NOT NULL DEFAULT now()

-- Use DATE when time is irrelevant:
birth_date DATE

-- INTERVAL for durations:
SELECT '2024-03-01'::DATE - '2024-01-01'::DATE;  -- 60 (days as INTEGER)
SELECT now() - INTERVAL '30 days';               -- subtract a duration
SELECT INTERVAL '1 year 2 months 3 days';
```

### Timezone behavior

```sql
-- TIMESTAMPTZ stores UTC internally, displays in session timezone:
SET TIME ZONE 'America/New_York';
SELECT now();  -- shows in EST/EDT

SET TIME ZONE 'UTC';
SELECT now();  -- shows in UTC

-- Convert timezone:
SELECT created_at AT TIME ZONE 'America/New_York' FROM "order" WHERE id = 1;
```

---

## JSONB: Binary JSON

PostgreSQL's most powerful differentiating feature for flexible data:

```sql
attributes JSONB NOT NULL DEFAULT '{}'

-- Insert JSON:
INSERT INTO product (name, price, stock_quantity, category_id, slug, attributes)
VALUES ('Gaming Mouse', 59.99, 200, 1, 'gaming-mouse', 
        '{"dpi": 16000, "buttons": 7, "color": "black", "wireless": false}');

-- Access JSON fields:
SELECT attributes->>'color' AS color FROM product WHERE id = 1;
-- -> returns JSON type, ->> returns text

-- Access nested JSON:
SELECT attributes->'specs'->>'weight' FROM product;

-- Check if key exists:
SELECT * FROM product WHERE attributes ? 'wireless';

-- Filter by JSON value:
SELECT * FROM product WHERE (attributes->>'dpi')::INT > 10000;

-- JSON operators:
-- ->   : get JSON object by key (returns JSON)
-- ->>  : get JSON object by key (returns TEXT)
-- #>   : get JSON at path (returns JSON): attributes #> '{specs,weight}'
-- #>>  : get JSON at path (returns TEXT)
-- ?    : does key exist?
-- @>   : does JSON contain? attributes @> '{"color": "black"}'
-- <@   : is JSON contained by?

-- Index on JSONB (enables fast queries):
CREATE INDEX ON product USING GIN (attributes);
```

---

## Arrays

```sql
-- Array column:
tags TEXT[] DEFAULT '{}'

-- Insert:
INSERT INTO product (... tags ...) VALUES (... ARRAY['sale', 'featured', 'new'] ...);
-- or:
VALUES (... '{"sale","featured","new"}'::TEXT[] ...);

-- Query:
SELECT * FROM product WHERE 'sale' = ANY(tags);
SELECT * FROM product WHERE tags @> ARRAY['sale', 'featured'];  -- contains both

-- Array operators:
-- ANY(arr)       : value = any element
-- ALL(arr)       : value = all elements
-- @>             : left array contains right
-- <@             : left array contained by right
-- &&             : arrays overlap (share any element)
-- array_length(arr, 1) : length of first dimension
```

---

## UUID

```sql
-- Enable extension:
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- UUID as primary key (use carefully — random UUIDs harm index performance):
id UUID DEFAULT uuid_generate_v4() PRIMARY KEY

-- UUID v7 (time-ordered, better for PKs — PostgreSQL 17+):
id UUID DEFAULT gen_random_uuid() PRIMARY KEY
-- gen_random_uuid() generates v4; use pgcrypto for more options
```

For StoreForge, we use `BIGINT IDENTITY` PKs for performance. UUIDs are useful when records are created across distributed systems before being merged into one database.

---

## ENUM Types

```sql
-- Create a custom enum type:
CREATE TYPE order_status AS ENUM (
    'pending', 'confirmed', 'shipped', 'delivered', 'cancelled', 'refunded'
);

-- Use in a column:
status order_status NOT NULL DEFAULT 'pending'

-- Add a value (only append, never remove):
ALTER TYPE order_status ADD VALUE 'returned' AFTER 'delivered';

-- List enum values:
SELECT unnest(enum_range(NULL::order_status));

-- Pros: storage efficient, enforces valid values, readable in queries
-- Cons: adding values requires DDL; removing values requires creating a new type
```

---

## Network and Binary Types

```sql
-- Network address types:
ip_address  INET        -- '192.168.1.1/24' or '::1'
mac_address MACADDR     -- '08:00:2b:01:02:03'
cidr_range  CIDR        -- '192.168.1.0/24'

-- Binary data:
data BYTEA              -- raw bytes; use for images, files (prefer object storage)

-- Range types:
valid_period DATERANGE  -- '[2024-01-01, 2024-12-31)'
price_range  NUMRANGE   -- '[10.00, 100.00)'
-- Useful with EXCLUDE constraints to prevent overlapping ranges
```

---

## Choosing the Right Type: Decision Guide

```
Is it money/price?              → NUMERIC(10,2)
Is it a primary key?            → BIGINT GENERATED ALWAYS AS IDENTITY
Is it a foreign key?            → BIGINT (match the PK type)
Is it a timestamp?              → TIMESTAMPTZ (not TIMESTAMP)
Is it a date only?              → DATE
Is it a short enum?             → ENUM type or SMALLINT with CHECK
Is it free-form text?           → TEXT
Is it a constrained string?     → VARCHAR(n)
Is it flexible/varying JSON?    → JSONB
Is it a true/false flag?        → BOOLEAN
Is it an ISO 3166 country code? → CHAR(2)
Is it a phone/postal code?      → TEXT or VARCHAR(20)
```

---

## Try It Yourself

```sql
-- 1. Test type casting:
SELECT
    '42'::INTEGER,
    '3.14'::NUMERIC(5,2),
    'TRUE'::BOOLEAN,
    '2024-06-15'::DATE,
    '2024-06-15 14:30:00+00'::TIMESTAMPTZ,
    ARRAY[1,2,3],
    '{"key": "value"}'::JSONB;

-- 2. JSONB operations on the product table:
SELECT
    name,
    attributes->>'color'   AS color,
    attributes ? 'wireless' AS has_wireless_key,
    attributes @> '{"wireless": true}'  AS is_wireless
FROM product;

-- 3. Date arithmetic:
SELECT
    now()                           AS current_time,
    now() - INTERVAL '7 days'       AS one_week_ago,
    EXTRACT(YEAR FROM now())        AS current_year,
    DATE_TRUNC('month', now())      AS start_of_month;

-- 4. Find products ordered in the last 30 days:
SELECT DISTINCT p.name
FROM product p
JOIN order_item oi ON oi.product_id = p.id
JOIN "order" o ON o.id = oi.order_id
WHERE o.created_at >= now() - INTERVAL '30 days';
```

<details>
<summary>Expected output for query 1</summary>

```
 int4 | numeric | bool | date       | timestamptz               | array   | jsonb
------+---------+------+------------+---------------------------+---------+-------------
   42 |    3.14 | t    | 2024-06-15 | 2024-06-15 14:30:00+00   | {1,2,3} | {"key": "value"}
```

Note: `t` is how psql displays `TRUE` by default. Run `\pset bool 'true/false'` to show as text.

</details>

---

## Capstone Connection

StoreForge's type choices are deliberate:
- `NUMERIC(10,2)` for `price` and `unit_price` — never lose a cent to float rounding
- `TIMESTAMPTZ` for all timestamps — supports global customers across timezones
- `JSONB` for `product.attributes` — electronics vs. clothing need different fields without extra tables
- `CHAR(2)` for `address.country` — enforces ISO 3166-1 alpha-2 length constraint at the DB level
- `ENUM order_status` — prevents invalid status strings, readable in queries and logs
