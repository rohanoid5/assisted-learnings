# Module 06 Exercises — PL/pgSQL

Practice everything from functions and triggers through dynamic SQL and extensions. Each exercise builds on the StoreForge schema.

---

## Exercise 1 — Trigger Suite

Deploy the full StoreForge trigger suite:

```sql
-- Step 1: create set_updated_at() and attach to customer and product.
-- Step 2: create audit_trigger_fn() and attach to customer, product, and "order".
-- Step 3: create check_stock_non_negative() and attach to product.
-- Step 4: create update_order_total() and attach to order_item.

-- Verification queries:
-- a) Update a product's price. Check that updated_at changed.
-- b) Insert, update, and delete a test customer. Confirm three rows appear in audit_log.
-- c) Try to set stock_quantity = -1 on any product. Confirm it raises an exception.
-- d) Insert an order_item. Confirm the parent order.total_amount updated automatically.
```

<details>
<summary>Show solution</summary>

```sql
-- set_updated_at function:
CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at := NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_customer_updated_at
BEFORE UPDATE ON customer
FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER trg_product_updated_at
BEFORE UPDATE ON product
FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- audit_trigger_fn:
CREATE OR REPLACE FUNCTION audit_trigger_fn()
RETURNS TRIGGER AS $$
BEGIN
    INSERT INTO audit_log (table_name, operation, row_id, old_data, new_data)
    VALUES (
        TG_TABLE_NAME,
        TG_OP,
        COALESCE(NEW.id, OLD.id),
        CASE WHEN TG_OP = 'INSERT' THEN NULL ELSE row_to_json(OLD)::JSONB END,
        CASE WHEN TG_OP = 'DELETE' THEN NULL ELSE row_to_json(NEW)::JSONB END
    );
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_audit_customer
AFTER INSERT OR UPDATE OR DELETE ON customer
FOR EACH ROW EXECUTE FUNCTION audit_trigger_fn();

CREATE TRIGGER trg_audit_product
AFTER INSERT OR UPDATE OR DELETE ON product
FOR EACH ROW EXECUTE FUNCTION audit_trigger_fn();

CREATE TRIGGER trg_audit_order
AFTER INSERT OR UPDATE OR DELETE ON "order"
FOR EACH ROW EXECUTE FUNCTION audit_trigger_fn();

-- check_stock_non_negative:
CREATE OR REPLACE FUNCTION check_stock_non_negative()
RETURNS TRIGGER AS $$
BEGIN
    IF NEW.stock_quantity < 0 THEN
        RAISE EXCEPTION 'Stock cannot go negative for product % (attempted: %)',
            NEW.id, NEW.stock_quantity USING ERRCODE = 'P0002';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_product_stock_check
BEFORE UPDATE OF stock_quantity ON product
FOR EACH ROW EXECUTE FUNCTION check_stock_non_negative();

-- update_order_total:
CREATE OR REPLACE FUNCTION update_order_total()
RETURNS TRIGGER AS $$
DECLARE
    v_order_id INTEGER;
BEGIN
    v_order_id := COALESCE(NEW.order_id, OLD.order_id);
    UPDATE "order"
    SET total_amount = (
        SELECT COALESCE(SUM(quantity * unit_price), 0)
        FROM order_item WHERE order_id = v_order_id
    )
    WHERE id = v_order_id;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_order_total
AFTER INSERT OR UPDATE OR DELETE ON order_item
FOR EACH ROW EXECUTE FUNCTION update_order_total();

-- Verification:
-- a) updated_at:
UPDATE product SET price = price + 0.01 WHERE id = 1;
SELECT updated_at FROM product WHERE id = 1;

-- b) audit_log:
INSERT INTO customer (name, email) VALUES ('Audit Test', 'audit@test.com');
UPDATE customer SET email = 'changed@test.com' WHERE email = 'audit@test.com';
DELETE FROM customer WHERE email = 'changed@test.com';
SELECT operation, table_name FROM audit_log ORDER BY changed_at DESC LIMIT 3;

-- c) stock check:
UPDATE product SET stock_quantity = -1 WHERE id = 1;  -- should error

-- d) order total:
SELECT total_amount FROM "order" WHERE id = 1;
INSERT INTO order_item (order_id, product_id, quantity, unit_price)
VALUES (1, 2, 2, 99.99);
SELECT total_amount FROM "order" WHERE id = 1;  -- should increase
```

</details>

---

## Exercise 2 — PL/pgSQL Functions

Write the following functions from scratch:

```sql
-- Part A: place_order_simple(p_customer_id INT, p_items JSONB)
-- Accepts items as: '[{"product_id": 1, "quantity": 2}, ...]'
-- - Validate stock for each item (raise exception if insufficient)
-- - Create the order record (status = 'pending')
-- - Insert order_item rows using unit_price from product.price at time of order
-- - Deduct stock
-- - Return the new order id
-- (Simplified version — no locking; that was covered in Module 04)

-- Part B: customer_summary(p_customer_id INT)
-- RETURNS TABLE(metric TEXT, value TEXT)
-- Return rows:
--   ('total_orders',    count of all orders)
--   ('total_spent',     SUM of delivered order totals)
--   ('avg_order_value', AVG of all order totals)
--   ('last_order_date', most recent order created_at)
--   ('active_since',    customer.created_at)
```

<details>
<summary>Show solution</summary>

```sql
-- Part A:
CREATE OR REPLACE FUNCTION place_order_simple(
    p_customer_id INTEGER,
    p_items       JSONB
) RETURNS INTEGER AS $$
DECLARE
    v_order_id  INTEGER;
    item        JSONB;
    v_product_id INTEGER;
    v_qty        INTEGER;
    v_price      NUMERIC;
    v_stock      INTEGER;
BEGIN
    -- Validate all stock before making changes:
    FOR item IN SELECT * FROM jsonb_array_elements(p_items)
    LOOP
        v_product_id := (item->>'product_id')::INTEGER;
        v_qty        := (item->>'quantity')::INTEGER;

        SELECT price, stock_quantity INTO v_price, v_stock
        FROM product WHERE id = v_product_id AND is_active;

        IF NOT FOUND THEN
            RAISE EXCEPTION 'Product % not found or inactive', v_product_id;
        END IF;
        IF v_stock < v_qty THEN
            RAISE EXCEPTION 'Insufficient stock for product % (have %, need %)',
                v_product_id, v_stock, v_qty;
        END IF;
    END LOOP;

    -- Create order:
    INSERT INTO "order" (customer_id, status, total_amount)
    VALUES (p_customer_id, 'pending', 0)
    RETURNING id INTO v_order_id;

    -- Insert items and deduct stock:
    FOR item IN SELECT * FROM jsonb_array_elements(p_items)
    LOOP
        v_product_id := (item->>'product_id')::INTEGER;
        v_qty        := (item->>'quantity')::INTEGER;

        SELECT price INTO v_price FROM product WHERE id = v_product_id;

        INSERT INTO order_item (order_id, product_id, quantity, unit_price)
        VALUES (v_order_id, v_product_id, v_qty, v_price);

        UPDATE product SET stock_quantity = stock_quantity - v_qty
        WHERE id = v_product_id;
    END LOOP;

    -- total_amount is maintained by trigger; return:
    RETURN v_order_id;
END;
$$ LANGUAGE plpgsql;

-- Part B:
CREATE OR REPLACE FUNCTION customer_summary(p_customer_id INTEGER)
RETURNS TABLE(metric TEXT, value TEXT) AS $$
BEGIN
    RETURN QUERY
    SELECT 'total_orders'::TEXT,
           COUNT(*)::TEXT
    FROM "order" WHERE customer_id = p_customer_id;

    RETURN QUERY
    SELECT 'total_spent'::TEXT,
           COALESCE(SUM(total_amount), 0)::TEXT
    FROM "order" WHERE customer_id = p_customer_id AND status = 'delivered';

    RETURN QUERY
    SELECT 'avg_order_value'::TEXT,
           ROUND(COALESCE(AVG(total_amount), 0), 2)::TEXT
    FROM "order" WHERE customer_id = p_customer_id;

    RETURN QUERY
    SELECT 'last_order_date'::TEXT,
           MAX(created_at)::TEXT
    FROM "order" WHERE customer_id = p_customer_id;

    RETURN QUERY
    SELECT 'active_since'::TEXT,
           created_at::TEXT
    FROM customer WHERE id = p_customer_id;
END;
$$ LANGUAGE plpgsql STABLE;

SELECT * FROM customer_summary(1);
```

</details>

---

## Exercise 3 — Dynamic SQL

```sql
-- Part A: table_stats(p_schema TEXT DEFAULT 'public')
-- RETURNS TABLE(table_name TEXT, row_count BIGINT)
-- Use dynamic SQL + information_schema to iterate all tables in the schema
-- and return the live row count for each.

-- Part B: generic_upsert_log(p_table TEXT, p_id INT, p_column TEXT, p_value TEXT)
-- Use EXECUTE to update a single text-compatible column in any table.
-- Log the change to audit_log.
-- Validate that p_table is in a pre-approved set: ('customer', 'product', 'category').
-- (This demonstrates safe allowlist-based dynamic SQL.)
```

<details>
<summary>Show solution</summary>

```sql
-- Part A:
CREATE OR REPLACE FUNCTION table_stats(p_schema TEXT DEFAULT 'public')
RETURNS TABLE(table_name TEXT, row_count BIGINT) AS $$
DECLARE
    tbl RECORD;
    v_count BIGINT;
BEGIN
    FOR tbl IN
        SELECT t.table_name
        FROM information_schema.tables t
        WHERE t.table_schema = p_schema AND t.table_type = 'BASE TABLE'
        ORDER BY t.table_name
    LOOP
        EXECUTE format('SELECT COUNT(*) FROM %I.%I', p_schema, tbl.table_name)
        INTO v_count;
        table_name := tbl.table_name;
        row_count  := v_count;
        RETURN NEXT;
    END LOOP;
END;
$$ LANGUAGE plpgsql;

SELECT * FROM table_stats();

-- Part B:
CREATE OR REPLACE PROCEDURE generic_upsert_log(
    p_table  TEXT,
    p_id     INTEGER,
    p_column TEXT,
    p_value  TEXT
)
LANGUAGE plpgsql AS $$
DECLARE
    allowed_tables TEXT[] := ARRAY['customer', 'product', 'category'];
BEGIN
    -- Allowlist validation:
    IF p_table != ALL(allowed_tables) THEN
        RAISE EXCEPTION 'Table "%" is not in the approved list', p_table;
    END IF;

    EXECUTE format(
        'UPDATE %I SET %I = $1 WHERE id = $2',
        p_table, p_column
    ) USING p_value, p_id;

    INSERT INTO audit_log (table_name, operation, row_id, new_data, changed_at)
    VALUES (
        p_table, 'GENERIC_UPDATE', p_id,
        jsonb_build_object('column', p_column, 'value', p_value),
        NOW()
    );
END;
$$;

CALL generic_upsert_log('customer', 1, 'phone', '+61-400-000-001');
CALL generic_upsert_log('nonexistent', 1, 'col', 'val');  -- should error
```

</details>

---

## Exercise 4 — Extensions

```sql
-- 1. Install pgcrypto. Create customer_credential for customer id 1 with password 'hunter2'.
--    Write a function verify_password(p_customer_id INT, p_password TEXT) RETURNS BOOLEAN.

-- 2. Install pg_trgm. Create a GIN index on product.name.
--    Write a function fuzzy_product_search(p_query TEXT, p_threshold REAL DEFAULT 0.3)
--    RETURNS TABLE(id INT, name TEXT, similarity REAL).
--    Results should be ordered by similarity descending.

-- 3. (Bonus) Install ltree. Add a path column to category.
--    Write a function category_descendants(p_slug TEXT)
--    RETURNS TABLE(id INT, name TEXT, depth INT).
```

<details>
<summary>Show solution</summary>

```sql
-- 1. pgcrypto:
CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE IF NOT EXISTS customer_credential (
    customer_id   INTEGER PRIMARY KEY REFERENCES customer(id) ON DELETE CASCADE,
    password_hash TEXT NOT NULL,
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

INSERT INTO customer_credential (customer_id, password_hash)
VALUES (1, crypt('hunter2', gen_salt('bf', 10)))
ON CONFLICT (customer_id) DO UPDATE
    SET password_hash = EXCLUDED.password_hash, updated_at = NOW();

CREATE OR REPLACE FUNCTION verify_password(
    p_customer_id INTEGER,
    p_password    TEXT
) RETURNS BOOLEAN AS $$
    SELECT EXISTS (
        SELECT 1 FROM customer_credential
        WHERE customer_id = p_customer_id
          AND password_hash = crypt(p_password, password_hash)
    );
$$ LANGUAGE sql STABLE STRICT;

SELECT verify_password(1, 'hunter2');  -- true
SELECT verify_password(1, 'wrong');    -- false

-- 2. pg_trgm fuzzy search:
CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_product_name_trgm
    ON product USING GIN (name gin_trgm_ops);

CREATE OR REPLACE FUNCTION fuzzy_product_search(
    p_query     TEXT,
    p_threshold REAL DEFAULT 0.3
) RETURNS TABLE(id INT, name TEXT, sim REAL) AS $$
    SELECT p.id, p.name, similarity(p.name, p_query) AS sim
    FROM product p
    WHERE p.name % p_query  -- uses GIN index
      AND similarity(p.name, p_query) >= p_threshold
      AND p.is_active
    ORDER BY sim DESC;
$$ LANGUAGE sql STABLE;

SELECT * FROM fuzzy_product_search('wireles headphon', 0.2);

-- 3. ltree:
CREATE EXTENSION IF NOT EXISTS ltree;
ALTER TABLE category ADD COLUMN IF NOT EXISTS path ltree;

CREATE INDEX IF NOT EXISTS idx_category_path ON category USING GIST (path);

CREATE OR REPLACE FUNCTION category_descendants(p_slug TEXT)
RETURNS TABLE(id INT, name TEXT, depth INT) AS $$
DECLARE
    v_path ltree;
BEGIN
    SELECT path INTO v_path FROM category WHERE slug = p_slug;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'Category slug "%" not found', p_slug;
    END IF;

    RETURN QUERY
    SELECT c.id, c.name, nlevel(c.path) - nlevel(v_path) AS depth
    FROM category c
    WHERE c.path <@ v_path AND c.path != v_path
    ORDER BY c.path;
END;
$$ LANGUAGE plpgsql STABLE;
```

</details>

---

## Capstone Checkpoint ✅

After completing these exercises, you should be able to:

- [ ] Implement the full StoreForge trigger suite: `set_updated_at`, audit log, stock guard, order total sync
- [ ] Write PL/pgSQL functions with DECLARE, SELECT INTO, loops, RAISE EXCEPTION, and RETURN QUERY
- [ ] Use EXECUTE with `%I` and `USING` to write safe dynamic SQL
- [ ] Validate allowlists in dynamic SQL to prevent SQL injection
- [ ] Install pgcrypto and store/verify bcrypt-hashed passwords
- [ ] Create GIN trigram indexes and write fuzzy search functions with pg_trgm
- [ ] Use ltree for ancestor/descendant category queries
