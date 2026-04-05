# Importing and Exporting Data with COPY

## Concept

PostgreSQL's `COPY` command is the fastest way to bulk-load or bulk-export data. It reads/writes directly to the PostgreSQL server's file system — bypassing individual row parsing overhead — and can process millions of rows per minute. It's the right tool for seeding databases, data migrations, and data pipeline integration.

---

## COPY vs INSERT: When to Use Each

| Operation | COPY | INSERT |
|-----------|------|--------|
| Bulk load (10,000+ rows) | ✅ Much faster | ❌ Slow |
| Application data ingestion | ❌ Not practical | ✅ Natural |
| CSV import from files | ✅ Native | ❌ Need ETL |
| Transactional single rows | ❌ Overkill | ✅ Correct |
| Network streaming | `\copy` (psql) | ✅ Natural |
| Server file access | Requires superuser | No restriction |

---

## Server-Side COPY (superuser, file on server)

`COPY` (uppercase) reads/writes files on the PostgreSQL **server** machine.

```sql
-- Export a table to CSV on the server:
COPY product TO '/tmp/products.csv' 
    WITH (FORMAT CSV, HEADER true, DELIMITER ',', NULL 'NULL');

-- Export with custom delimiter:
COPY customer TO '/tmp/customers.tsv' WITH (FORMAT TEXT, DELIMITER E'\t');

-- Export a query result (not just a full table):
COPY (
    SELECT p.name, c.name AS category, p.price, p.stock_quantity
    FROM product p
    JOIN category c ON c.id = p.category_id
    WHERE p.is_active = true
    ORDER BY c.name, p.name
) TO '/tmp/active_products.csv' WITH (FORMAT CSV, HEADER true);

-- Import from a CSV file on the server:
COPY product (name, slug, price, stock_quantity, category_id)
FROM '/tmp/imports/products.csv'
WITH (FORMAT CSV, HEADER true, NULL 'NULL');

-- Binary format (fastest, not human-readable):
COPY product TO '/tmp/product.bin' WITH (FORMAT BINARY);
COPY product FROM '/tmp/product.bin' WITH (FORMAT BINARY);
```

---

## Client-Side \copy (psql only, your local machine)

`\copy` (lowercase backslash) is a psql meta-command that transfers data between the **client** machine and the server. No file access privileges needed on the server.

```bash
# In psql:

# Export to local machine:
\copy product TO '/Users/you/Desktop/products.csv' WITH (FORMAT CSV, HEADER true)

# Export query result:
\copy (SELECT name, price FROM product ORDER BY price) TO '/tmp/prices.csv' CSV HEADER

# Import from local machine:
\copy product (name, slug, price, stock_quantity, category_id) 
  FROM '/Users/you/Desktop/imports/products.csv' CSV HEADER

# Or from the shell (non-interactive):
psql -h localhost -U storeforge -d storeforge_dev \
  -c "\copy product TO '/tmp/product_export.csv' CSV HEADER"
```

---

## COPY Format Options

```sql
-- FORMAT options:
FORMAT CSV       -- comma-separated (default delimiter is comma)
FORMAT TEXT      -- PostgreSQL custom text format (default delimiter is tab)
FORMAT BINARY    -- binary; fastest; not portable across versions

-- Common options:
HEADER true      -- first row is header (CSV only; ignored on import for column names)
HEADER false     -- no header row
DELIMITER ','    -- use comma as delimiter
DELIMITER E'\t'  -- tab-delimited
NULL 'NULL'      -- string to treat as NULL ('NULL', '', '\N', etc.)
QUOTE '"'        -- quote character for fields containing delimiter
ESCAPE '\'       -- escape character
ENCODING 'UTF8'  -- file encoding
FORCE_NULL (col1, col2)   -- treat empty string as NULL for these columns
FORCE_NOT_NULL (col)      -- never treat this column as NULL
```

---

## Handling Import Errors

`COPY` is all-or-nothing: any error in any row rolls back the entire COPY. For fault-tolerant loading:

```sql
-- Option 1: Load into a staging table first, validate, then INSERT:
CREATE TEMP TABLE staging_products (LIKE product);

\copy staging_products FROM '/tmp/imports/products.csv' CSV HEADER

-- Validate:
SELECT * FROM staging_products WHERE price < 0;  -- should be empty
SELECT * FROM staging_products WHERE name IS NULL;  -- should be empty

-- Insert validated rows:
INSERT INTO product (name, slug, price, stock_quantity, category_id)
SELECT name, slug, price, stock_quantity, category_id
FROM staging_products
WHERE price >= 0 AND name IS NOT NULL;

DROP TABLE staging_products;
```

```sql
-- Option 2: PostgreSQL 14+: log errors instead of failing (requires superuser):
COPY product FROM '/tmp/products.csv' WITH (FORMAT CSV, HEADER true)
-- Note: COPY itself doesn't have an ON ERROR option, but pg_read_file + custom
-- functions can handle this. Use staging tables for production workflows.
```

---

## pg_dump and pg_restore: Database-Level Export/Import

For full database exports (not just single tables):

```bash
# Export entire database:
pg_dump -h localhost -U storeforge -d storeforge_dev \
  --format=custom \
  --file=storeforge_backup.dump

# Export as SQL text:
pg_dump -h localhost -U storeforge -d storeforge_dev \
  --format=plain \
  --file=storeforge_backup.sql

# Export specific tables only:
pg_dump -h localhost -U storeforge -d storeforge_dev \
  -t product -t category \
  --format=plain \
  --file=catalog_backup.sql

# Export schema only (no data):
pg_dump -h localhost -U storeforge -d storeforge_dev \
  --schema-only \
  --file=schema_only.sql

# Export data only (no DDL):
pg_dump -h localhost -U storeforge -d storeforge_dev \
  --data-only \
  --format=custom \
  --file=data_only.dump

# Restore from custom format:
pg_restore -h localhost -U storeforge -d storeforge_dev \
  --clean --if-exists \
  storeforge_backup.dump

# Restore from SQL text:
psql -h localhost -U storeforge -d storeforge_dev < storeforge_backup.sql
```

---

## Seeding StoreForge with COPY

The `capstone/storeforge/seed.sql` file uses a mix of `COPY` and `INSERT` to seed data:

```sql
-- seed.sql pattern for categories (few rows → INSERT):
INSERT INTO category (name, slug) VALUES
    ('Electronics', 'electronics'),
    ('Clothing', 'clothing'),
    ('Books', 'books');

-- seed.sql pattern for products (CSV inline with COPY FROM stdin):
COPY product (name, slug, price, stock_quantity, category_id, attributes) FROM stdin WITH (FORMAT CSV);
Wireless Headphones,wireless-headphones,79.99,150,1,"{""color"":""black"",""wireless"":true}"
Mechanical Keyboard,mechanical-keyboard,129.99,75,1,"{""switch_type"":""Cherry MX Blue""}"
\.
-- The \. (backslash-dot) terminates the COPY FROM stdin block
```

---

## Try It Yourself

```sql
-- 1. Export the product table to CSV:
\copy product TO '/tmp/products_export.csv' WITH (FORMAT CSV, HEADER true)

-- 2. Look at the file:
\! head -5 /tmp/products_export.csv

-- 3. Create a staging table and import back:
CREATE TEMP TABLE products_import (LIKE product);
\copy products_import FROM '/tmp/products_export.csv' WITH (FORMAT CSV, HEADER true)

-- 4. Verify the count:
SELECT COUNT(*) FROM products_import;
SELECT COUNT(*) FROM product;

-- 5. Export just active products with a custom query:
\copy (
    SELECT p.name, c.name AS category, p.price, p.stock_quantity
    FROM product p
    JOIN category c ON c.id = p.category_id
    WHERE p.is_active = true
    ORDER BY p.price DESC
) TO '/tmp/active_products.csv' CSV HEADER

-- 6. Check the export:
\! wc -l /tmp/active_products.csv
```

<details>
<summary>Expected output</summary>

```
-- After \! head -5 /tmp/products_export.csv:
id,name,slug,price,stock_quantity,category_id,attributes,is_active,created_at
1,Wireless Headphones,wireless-headphones,79.99,150,1,"{""color"":""black""}",t,2024-01-10 ...
2,Mechanical Keyboard,mechanical-keyboard,129.99,75,1,...

-- COUNT comparison should be equal (staging = base table)

-- wc -l /tmp/active_products.csv: (number of active products) + 1 for header
```

</details>

---

## Capstone Connection

StoreForge uses COPY in two contexts:
- **Seeding data** — `seed.sql` uses `COPY product FROM stdin` to efficiently load 50 test products
- **Reporting exports** — an admin cron job uses `\copy (SELECT ...) TO '/reports/daily_sales.csv'` to export daily sales for the accounting system
- **Data migration** — when upgrading to a new database or moving environments: `pg_dump` → `pg_restore`
