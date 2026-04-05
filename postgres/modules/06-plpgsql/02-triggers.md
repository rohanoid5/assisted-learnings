# Triggers

## Concept

A trigger is a function that PostgreSQL automatically calls when a specified event occurs on a table or view — before or after an INSERT, UPDATE, DELETE, or TRUNCATE. Triggers are the mechanism that enforces cross-table invariants, maintains derived data, and logs changes without requiring applications to explicitly call audit functions. In StoreForge, triggers maintain `updated_at` timestamps, log changes to `audit_log`, and update `order.total_amount`.

---

## Trigger Anatomy

```sql
-- A trigger consists of two parts:
-- 1. A trigger function (returns TRIGGER, not a regular type)
-- 2. A CREATE TRIGGER statement that attaches it to a table

-- Trigger function template:
CREATE OR REPLACE FUNCTION my_trigger_fn()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    -- Available variables:
    -- TG_OP     : 'INSERT', 'UPDATE', 'DELETE', 'TRUNCATE'
    -- TG_TABLE_NAME : name of the table
    -- TG_WHEN   : 'BEFORE' or 'AFTER'
    -- TG_LEVEL  : 'ROW' or 'STATEMENT'
    -- NEW       : new row (INSERT, UPDATE) — mutable in BEFORE triggers
    -- OLD       : old row (UPDATE, DELETE)

    -- For BEFORE triggers: return NEW to allow, NULL to suppress
    -- For AFTER triggers: return value is ignored
    RETURN NEW;
END;
$$;
```

---

## BEFORE vs AFTER Triggers

```sql
-- BEFORE trigger: runs before the operation, can modify or cancel it:
CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at := NOW();
    RETURN NEW;  -- return the modified row
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_customer_updated_at
BEFORE UPDATE ON customer
FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- Test:
UPDATE customer SET email = 'new@example.com' WHERE id = 1;
SELECT updated_at FROM customer WHERE id = 1;  -- automatically updated

-- AFTER trigger: runs after the operation (can't modify the row):
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
    RETURN NULL;  -- ignored for AFTER triggers
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_audit_product
AFTER INSERT OR UPDATE OR DELETE ON product
FOR EACH ROW EXECUTE FUNCTION audit_trigger_fn();
```

---

## ROW vs STATEMENT Level

```sql
-- FOR EACH ROW: fires once per affected row (most common):
CREATE TRIGGER trg_per_row
AFTER UPDATE ON product
FOR EACH ROW EXECUTE FUNCTION my_fn();

-- FOR EACH STATEMENT: fires once per SQL statement, regardless of rows affected:
CREATE OR REPLACE FUNCTION log_bulk_update()
RETURNS TRIGGER AS $$
BEGIN
    INSERT INTO audit_log (table_name, operation, row_id, new_data, changed_at)
    VALUES (TG_TABLE_NAME, TG_OP || '_BULK', -1, NULL, NOW());
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_bulk_statement
AFTER UPDATE ON product
FOR EACH STATEMENT EXECUTE FUNCTION log_bulk_update();
```

---

## Conditional Triggers (WHEN clause)

```sql
-- Only fire the updated_at trigger when specific columns change:
CREATE TRIGGER trg_product_updated_at
BEFORE UPDATE ON product
FOR EACH ROW
WHEN (OLD.* IS DISTINCT FROM NEW.*)  -- any column changed
EXECUTE FUNCTION set_updated_at();

-- Only fire audit for price changes:
CREATE TRIGGER trg_audit_price_change
AFTER UPDATE OF price ON product
FOR EACH ROW
WHEN (OLD.price IS DISTINCT FROM NEW.price)
EXECUTE FUNCTION audit_trigger_fn();
```

---

## Inventory Check Trigger

Prevent stock from going negative:

```sql
CREATE OR REPLACE FUNCTION check_stock_non_negative()
RETURNS TRIGGER AS $$
BEGIN
    IF NEW.stock_quantity < 0 THEN
        RAISE EXCEPTION 'Stock cannot go negative for product % (attempted: %)',
            NEW.id, NEW.stock_quantity
            USING ERRCODE = 'P0002';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_product_stock_check
BEFORE UPDATE OF stock_quantity ON product
FOR EACH ROW EXECUTE FUNCTION check_stock_non_negative();

-- Test:
UPDATE product SET stock_quantity = -5 WHERE id = 1;
-- ERROR: Stock cannot go negative for product 1 (attempted: -5)
```

---

## Order Total Maintenance Trigger

Keep `order.total_amount` consistent with its `order_item` rows:

```sql
CREATE OR REPLACE FUNCTION update_order_total()
RETURNS TRIGGER AS $$
DECLARE
    v_order_id INTEGER;
BEGIN
    -- Determine which order was affected:
    v_order_id := COALESCE(NEW.order_id, OLD.order_id);

    UPDATE "order"
    SET total_amount = (
        SELECT COALESCE(SUM(quantity * unit_price), 0)
        FROM order_item
        WHERE order_id = v_order_id
    )
    WHERE id = v_order_id;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_order_total
AFTER INSERT OR UPDATE OR DELETE ON order_item
FOR EACH ROW EXECUTE FUNCTION update_order_total();

-- Now whenever order_item changes, order.total_amount is automatically synced:
INSERT INTO order_item (order_id, product_id, quantity, unit_price)
VALUES (1, 2, 3, 49.99);
SELECT total_amount FROM "order" WHERE id = 1;  -- updated automatically
```

---

## INSTEAD OF Triggers on Views

```sql
-- Allow INSERTs into a complex view:
CREATE VIEW order_with_customer AS
SELECT o.id, o.status, o.total_amount, c.name AS customer_name
FROM "order" o JOIN customer c ON c.id = o.customer_id;

CREATE OR REPLACE FUNCTION insert_order_via_view()
RETURNS TRIGGER AS $$
DECLARE
    v_customer_id INTEGER;
BEGIN
    SELECT id INTO v_customer_id FROM customer WHERE name = NEW.customer_name;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'Customer "%" not found', NEW.customer_name;
    END IF;

    INSERT INTO "order" (customer_id, status, total_amount)
    VALUES (v_customer_id, NEW.status, NEW.total_amount);

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_order_view_insert
INSTEAD OF INSERT ON order_with_customer
FOR EACH ROW EXECUTE FUNCTION insert_order_via_view();
```

---

## Managing Triggers

```sql
-- List all triggers:
SELECT trigger_name, event_manipulation, event_object_table, action_timing
FROM information_schema.triggers
WHERE trigger_schema = 'public'
ORDER BY event_object_table, trigger_name;

-- Disable a trigger temporarily (useful for bulk loads):
ALTER TABLE product DISABLE TRIGGER trg_audit_product;
COPY product FROM '/tmp/bulk_products.csv' CSV HEADER;
ALTER TABLE product ENABLE TRIGGER trg_audit_product;

-- Drop a trigger:
DROP TRIGGER trg_audit_product ON product;

-- Drop the function:
DROP FUNCTION audit_trigger_fn();
```

---

## Try It Yourself

```sql
-- 1. Add the set_updated_at() trigger to both customer and product tables.
--    Verify: UPDATE a customer and check that updated_at changed.

-- 2. Add the audit_trigger_fn() trigger to the customer table.
--    Insert a new customer, update their email, delete them.
--    Query audit_log and confirm all three operations are recorded.

-- 3. Add the update_order_total() trigger to order_item.
--    Insert an order_item, then delete it, and verify total_amount changes both times.

-- 4. Create a trigger that prevents deleting a customer who has any pending orders:
--    RAISE EXCEPTION if a customer with pending orders is deleted.
```

<details>
<summary>Show solutions</summary>

```sql
-- 1. set_updated_at triggers:
CREATE TRIGGER trg_customer_updated_at
BEFORE UPDATE ON customer
FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER trg_product_updated_at
BEFORE UPDATE ON product
FOR EACH ROW EXECUTE FUNCTION set_updated_at();

SELECT updated_at FROM customer WHERE id = 1;
UPDATE customer SET email = 'newmail@test.com' WHERE id = 1;
SELECT updated_at FROM customer WHERE id = 1;  -- should be more recent

-- 2. Audit triggers on customer:
CREATE TRIGGER trg_audit_customer
AFTER INSERT OR UPDATE OR DELETE ON customer
FOR EACH ROW EXECUTE FUNCTION audit_trigger_fn();

INSERT INTO customer (name, email) VALUES ('Test Audited', 'audit@test.com');
UPDATE customer SET email = 'changed@test.com' WHERE email = 'audit@test.com';
DELETE FROM customer WHERE email = 'changed@test.com';

SELECT operation, old_data ->> 'email', new_data ->> 'email', changed_at
FROM audit_log WHERE table_name = 'customer'
ORDER BY changed_at DESC LIMIT 3;

-- 3. Order total trigger test:
-- (assumes trg_order_total already attached)
SELECT total_amount FROM "order" WHERE id = 1;

INSERT INTO order_item (order_id, product_id, quantity, unit_price)
VALUES (1, 2, 1, 100.00);
SELECT total_amount FROM "order" WHERE id = 1;  -- increased

DELETE FROM order_item WHERE order_id = 1 AND product_id = 2 AND unit_price = 100.00;
SELECT total_amount FROM "order" WHERE id = 1;  -- decreased back

-- 4. Prevent delete of customer with pending orders:
CREATE OR REPLACE FUNCTION prevent_customer_delete_with_pending_orders()
RETURNS TRIGGER AS $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM "order"
        WHERE customer_id = OLD.id AND status = 'pending'
    ) THEN
        RAISE EXCEPTION
            'Cannot delete customer % — they have pending orders', OLD.id;
    END IF;
    RETURN OLD;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_customer_no_delete_with_pending
BEFORE DELETE ON customer
FOR EACH ROW EXECUTE FUNCTION prevent_customer_delete_with_pending_orders();

-- Test:
DELETE FROM customer WHERE id = 1;  -- should fail if they have pending orders
```

</details>

---

## Capstone Connection

StoreForge's trigger suite:
1. **`set_updated_at`** — attached to `customer` and `product`; keeps `updated_at` current without application-layer responsibility
2. **`audit_trigger_fn`** — attached to `customer`, `product`, `"order"`; records every change to `audit_log` for compliance
3. **`check_stock_non_negative`** — prevents the DB from ever having negative inventory, even if application code has a bug
4. **`update_order_total`** — keeps `order.total_amount` in sync with `order_item` rows, eliminating the risk of total drift
