# Advanced Filtering — Full-Text Search and JSONB

## Concept

PostgreSQL provides two standout features that distinguish it from other relational databases: native full-text search (FTS) and a first-class JSONB binary column type. Both enable query patterns that would otherwise require a separate search engine or a document database — making PostgreSQL capable of handling a much wider range of workloads within a single system.

---

## Full-Text Search

### Core Types

- **`tsvector`** — a preprocessed, normalized list of lexemes (dictionary words) representing a document
- **`tsquery`** — a search condition: words combined with `&` (AND), `|` (OR), `!` (NOT), and `<->` (FOLLOWED BY)

```sql
-- Convert text to tsvector:
SELECT to_tsvector('english', 'Wireless Bluetooth Headphones with Noise Cancellation');
-- 'bluetooth':2 'cancellat':6 'headphon':3 'nois':5 'wireless':1

-- Create a tsquery:
SELECT to_tsquery('english', 'headphones & wireless');
-- 'headphon' & 'wireless'

SELECT plainto_tsquery('english', 'noise cancelling headphones');
-- 'nois' & 'cancel' & 'headphon'  (no operator syntax needed)

SELECT websearch_to_tsquery('english', '"noise cancelling" headphones -wired');
-- phrase search + NOT

-- Match a document against a query:
SELECT 'Wireless Bluetooth Headphones' @@ to_tsquery('english', 'headphones');
-- true
```

### Searching Products

```sql
-- Basic full-text search on product name and description:
SELECT id, name, price
FROM product
WHERE to_tsvector('english', name || ' ' || COALESCE(description, ''))
   @@ plainto_tsquery('english', 'wireless headphones');

-- Add a generated FTS column (stored, auto-updated):
ALTER TABLE product
    ADD COLUMN search_vector TSVECTOR
    GENERATED ALWAYS AS (
        to_tsvector('english',
            name || ' ' || COALESCE(description, ''))
    ) STORED;

-- Index it for fast search:
CREATE INDEX idx_product_search ON product USING GIN (search_vector);

-- Now queries are instant:
SELECT name, price
FROM product
WHERE search_vector @@ plainto_tsquery('english', 'wireless headphones')
ORDER BY ts_rank(search_vector, plainto_tsquery('english', 'wireless headphones')) DESC;
```

### Ranking and Highlighting

```sql
-- Rank results by relevance:
SELECT
    name,
    price,
    ts_rank(
        to_tsvector('english', name || ' ' || COALESCE(description, '')),
        plainto_tsquery('english', 'wireless headphones')
    ) AS relevance
FROM product
WHERE to_tsvector('english', name || ' ' || COALESCE(description, ''))
   @@ plainto_tsquery('english', 'wireless headphones')
ORDER BY relevance DESC;

-- Highlight matching terms in result:
SELECT
    name,
    ts_headline(
        'english',
        description,
        plainto_tsquery('english', 'wireless headphones'),
        'StartSel=<mark>, StopSel=</mark>, MaxWords=20'
    ) AS highlighted_description
FROM product
WHERE search_vector @@ plainto_tsquery('english', 'wireless headphones');
```

---

## JSONB — Binary JSON in PostgreSQL

`JSONB` stores JSON as a parsed binary format — indexed, queryable, and faster than `JSON` for reads.

### Basic JSONB Operators

```sql
-- The product.attributes column is JSONB:
-- Example value: {"color": "black", "wireless": true, "battery_hours": 20}

-- Get value by key (returns JSONB):
SELECT attributes -> 'color' FROM product WHERE id = 1;
-- "black"  (quoted — still JSONB)

-- Get value as text (returns TEXT):
SELECT attributes ->> 'color' FROM product WHERE id = 1;
-- black

-- Navigate nested objects:
SELECT attributes -> 'dimensions' ->> 'weight_g' FROM product;
-- '340'

-- Check key existence:
SELECT name FROM product WHERE attributes ? 'wireless';

-- Check multiple keys:
SELECT name FROM product WHERE attributes ?& ARRAY['color', 'wireless'];  -- has BOTH
SELECT name FROM product WHERE attributes ?| ARRAY['color', 'material']; -- has EITHER

-- Contains operator (@>): JSONB on left contains right:
SELECT name FROM product
WHERE attributes @> '{"wireless": true}'::JSONB;

SELECT name FROM product
WHERE attributes @> '{"color": "black", "wireless": true}'::JSONB;
```

### JSONB Query Operators

```sql
-- Get array element (0-indexed):
SELECT attributes -> 'tags' -> 0 FROM product;

-- Expand JSONB array to rows:
SELECT p.name, tag
FROM product p, jsonb_array_elements_text(p.attributes -> 'tags') AS tag;

-- Path query (#>):
SELECT attributes #> '{dimensions, weight_g}' FROM product;   -- JSONB
SELECT attributes #>> '{dimensions, weight_g}' FROM product;  -- TEXT

-- Filter on nested value:
SELECT name, price
FROM product
WHERE (attributes ->> 'battery_hours')::INTEGER > 15;
```

### JSONB Modification

```sql
-- Set a key:
UPDATE product
SET attributes = jsonb_set(attributes, '{in_stock}', 'true')
WHERE id = 1;

-- Add/merge keys:
UPDATE product
SET attributes = attributes || '{"certified_refurbished": false}'::JSONB
WHERE id = 1;

-- Remove a key:
UPDATE product
SET attributes = attributes - 'in_stock'
WHERE id = 1;

-- Remove nested key:
UPDATE product
SET attributes = attributes #- '{dimensions, weight_g}'
WHERE id = 1;
```

### JSONB Aggregation and JSON Building

```sql
-- Build JSON objects from columns:
SELECT jsonb_build_object(
    'order_id', o.id,
    'customer', jsonb_build_object('name', c.name, 'email', c.email),
    'items', jsonb_agg(jsonb_build_object(
        'product', p.name,
        'quantity', oi.quantity,
        'price', oi.unit_price
    ))
) AS order_json
FROM "order" o
JOIN customer c ON c.id = o.customer_id
JOIN order_item oi ON oi.order_id = o.id
JOIN product p ON p.id = oi.product_id
WHERE o.id = 42
GROUP BY o.id, c.name, c.email;

-- Aggregate rows into JSON array:
SELECT json_agg(row_to_json(p))
FROM (SELECT id, name, price FROM product WHERE is_active = true) p;
```

### GIN Index on JSONB

```sql
-- Index all keys in JSONB for @>, ?, ?&, ?| operators:
CREATE INDEX idx_product_attributes ON product USING GIN (attributes);

-- Index specific path (more selective):
CREATE INDEX idx_product_wireless ON product USING GIN ((attributes -> 'wireless'));

-- Verify index is used:
EXPLAIN SELECT name FROM product WHERE attributes @> '{"wireless": true}';
-- Should show: Bitmap Index Scan on idx_product_attributes
```

---

## Try It Yourself

```sql
-- 1. Add a generated search_vector column to product and create a GIN index.

-- 2. Search for active products matching 'wireless headphones', ranked by relevance.

-- 3. Find all products where attributes contain a 'colors' array, and expand
--    each color to its own row (product name + color).

-- 4. Using JSONB operators, find all products where:
--    - attributes.wireless = true
--    - (attributes.battery_hours) > 10

-- 5. Write a query that produces a JSON document per order with nested
--    customer and items (like an API response).
```

<details>
<summary>Show solutions</summary>

```sql
-- 1. Generated search_vector + index:
ALTER TABLE product
    ADD COLUMN search_vector TSVECTOR
    GENERATED ALWAYS AS (
        to_tsvector('english', name || ' ' || COALESCE(description, ''))
    ) STORED;

CREATE INDEX idx_product_fts ON product USING GIN (search_vector);

-- 2. Ranked full-text search:
SELECT
    name,
    price,
    ts_rank(search_vector, query) AS relevance
FROM product,
     plainto_tsquery('english', 'wireless headphones') AS query
WHERE is_active = true
  AND search_vector @@ query
ORDER BY relevance DESC;

-- 3. Expand JSONB array of colors:
SELECT p.name, color.value AS color
FROM product p
CROSS JOIN LATERAL jsonb_array_elements_text(p.attributes -> 'colors') AS color(value)
WHERE p.is_active = true
ORDER BY p.name, color;

-- 4. JSONB filter with type cast:
SELECT name, price, attributes -> 'battery_hours' AS battery
FROM product
WHERE attributes @> '{"wireless": true}'::JSONB
  AND (attributes ->> 'battery_hours')::INTEGER > 10
ORDER BY (attributes ->> 'battery_hours')::INTEGER DESC;

-- 5. Order JSON document:
SELECT jsonb_pretty(
    jsonb_build_object(
        'order_id',    o.id,
        'status',      o.status,
        'total',       o.total_amount,
        'customer',    jsonb_build_object('name', c.name, 'email', c.email),
        'items',       COALESCE(items_agg.items, '[]'::JSONB)
    )
) AS order_document
FROM "order" o
JOIN customer c ON c.id = o.customer_id
LEFT JOIN LATERAL (
    SELECT jsonb_agg(
        jsonb_build_object(
            'product',  p.name,
            'qty',      oi.quantity,
            'price',    oi.unit_price
        ) ORDER BY oi.id
    ) AS items
    FROM order_item oi
    JOIN product p ON p.id = oi.product_id
    WHERE oi.order_id = o.id
) AS items_agg ON true
WHERE o.id = 1;
```

</details>

---

## Capstone Connection

StoreForge's product search combines full-text and JSONB querying:
- The `product_search()` function uses `search_vector @@ websearch_to_tsquery(...)` for keyword matching, ranked by `ts_rank`
- The product API supports attribute filters like `?color=black&wireless=true`, implemented as `attributes @> '{"color": "black", "wireless": true}'::JSONB`
- The order service returns the full order as a JSONB document (built with `jsonb_build_object` + `jsonb_agg`) to minimize round trips from the application layer
