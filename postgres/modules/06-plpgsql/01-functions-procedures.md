# PL/pgSQL — Functions and Procedures

## Concept

PL/pgSQL (Procedural Language/PostgreSQL) is PostgreSQL's built-in procedural extension to SQL. It adds variables, control flow (IF/LOOP/FOR), exception handling, and cursor support. PL/pgSQL code runs inside the PostgreSQL server, eliminating round-trips and enabling complex multi-step logic as reusable, encapsulated units — functions, procedures, and trigger functions.

---

## Anatomy of a PL/pgSQL Function

```sql
CREATE OR REPLACE FUNCTION function_name(param1 type, param2 type)
RETURNS return_type
LANGUAGE plpgsql
-- Optional modifiers:
SECURITY DEFINER   -- run as function owner, not caller
SET search_path = public  -- always set with SECURITY DEFINER to prevent hijacking
STABLE   -- same inputs → same output within a transaction (allows caching)
-- VOLATILE (default): may change output even with same inputs (e.g., NOW())
-- IMMUTABLE: deterministic, no DB access (e.g., pure math)
AS $$
DECLARE
    variable_name type := initial_value;
    another_var   type;
BEGIN
    -- body
    RETURN value;
END;
$$ ;
```

---

## Basic Variables and Control Flow

```sql
-- A simple function: get a product's stock status:
CREATE OR REPLACE FUNCTION get_stock_status(p_product_id INTEGER)
RETURNS TEXT
LANGUAGE plpgsql
STABLE
AS $$
DECLARE
    v_quantity INTEGER;
BEGIN
    SELECT stock_quantity INTO v_quantity
    FROM product
    WHERE id = p_product_id AND is_active = true;

    IF NOT FOUND THEN
        RETURN 'not_found';
    ELSIF v_quantity = 0 THEN
        RETURN 'out_of_stock';
    ELSIF v_quantity < 10 THEN
        RETURN 'low_stock';
    ELSE
        RETURN 'in_stock';
    END IF;
END;
$$;

-- Call it:
SELECT get_stock_status(42);
SELECT name, get_stock_status(id) AS stock_status FROM product WHERE is_active = true;
```

---

## RETURNS TABLE: Return Multiple Rows and Columns

```sql
-- Return all products in a category with their stock status:
CREATE OR REPLACE FUNCTION get_category_products(p_category_slug TEXT)
RETURNS TABLE (
    product_id   INTEGER,
    product_name TEXT,
    price        NUMERIC,
    stock_status TEXT
)
LANGUAGE plpgsql
STABLE
AS $$
BEGIN
    RETURN QUERY
    SELECT
        p.id,
        p.name,
        p.price,
        CASE
            WHEN p.stock_quantity = 0  THEN 'out_of_stock'
            WHEN p.stock_quantity < 10 THEN 'low_stock'
            ELSE 'in_stock'
        END
    FROM product p
    JOIN category c ON c.id = p.category_id
    WHERE c.slug = p_category_slug
      AND p.is_active = true
    ORDER BY p.price;
END;
$$;

-- Call:
SELECT * FROM get_category_products('electronics');
```

---

## LOOP, FOR, and WHILE

```sql
-- Loop through order items to validate an order:
CREATE OR REPLACE FUNCTION validate_cart(p_cart_items JSONB)
RETURNS TEXT
LANGUAGE plpgsql
AS $$
DECLARE
    v_item        JSONB;
    v_product_id  INTEGER;
    v_qty         INTEGER;
    v_stock       INTEGER;
    v_product_name TEXT;
BEGIN
    FOR v_item IN SELECT * FROM jsonb_array_elements(p_cart_items)
    LOOP
        v_product_id := (v_item ->> 'product_id')::INTEGER;
        v_qty        := (v_item ->> 'quantity')::INTEGER;

        SELECT name, stock_quantity
        INTO v_product_name, v_stock
        FROM product
        WHERE id = v_product_id AND is_active = true;

        IF NOT FOUND THEN
            RETURN 'ERROR: Product ' || v_product_id || ' not found';
        ELSIF v_stock < v_qty THEN
            RETURN 'ERROR: Insufficient stock for "' || v_product_name || '"'
                || ' (need ' || v_qty || ', have ' || v_stock || ')';
        END IF;
    END LOOP;

    RETURN 'OK';
END;
$$;

-- Test:
SELECT validate_cart('[{"product_id": 1, "quantity": 2}, {"product_id": 2, "quantity": 1}]');
```

---

## Exception Handling

```sql
CREATE OR REPLACE FUNCTION safe_deduct_stock(p_product_id INTEGER, p_qty INTEGER)
RETURNS BOOLEAN
LANGUAGE plpgsql
AS $$
BEGIN
    UPDATE product
    SET stock_quantity = stock_quantity - p_qty
    WHERE id = p_product_id AND stock_quantity >= p_qty;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'Insufficient stock for product %', p_product_id
            USING ERRCODE = 'P0001';  -- custom error code
    END IF;

    RETURN TRUE;

EXCEPTION
    WHEN OTHERS THEN
        -- Log the error and re-raise:
        RAISE WARNING 'Failed to deduct stock for product %: %', p_product_id, SQLERRM;
        RETURN FALSE;
END;
$$;

-- SQLERRM: error message of current exception
-- SQLSTATE: SQL error code (e.g., '23503' for FK violation)
-- RAISE NOTICE / RAISE WARNING / RAISE EXCEPTION: logging levels
```

---

## Procedures (PostgreSQL 11+)

Procedures are like functions but:
- Called with `CALL`, not `SELECT`
- Can `COMMIT`/`ROLLBACK` within their body
- Cannot return a value (use `INOUT` parameters instead)

```sql
-- Procedure to process pending orders (batch):
CREATE OR REPLACE PROCEDURE process_orders_batch(p_batch_size INTEGER DEFAULT 10)
LANGUAGE plpgsql
AS $$
DECLARE
    v_order_id INTEGER;
BEGIN
    FOR v_order_id IN
        SELECT id FROM "order"
        WHERE status = 'pending'
        ORDER BY created_at
        LIMIT p_batch_size
        FOR UPDATE SKIP LOCKED
    LOOP
        -- Simulate order processing:
        UPDATE "order" SET status = 'processing' WHERE id = v_order_id;
        -- Could call external service, emit event, etc.
        COMMIT;  -- Commit each order individually (allowed in procedures)
    END LOOP;
END;
$$;

-- Call:
CALL process_orders_batch(5);
CALL process_orders_batch();  -- uses default 10
```

---

## The place_order() Function

StoreForge's core transactional function:

```sql
CREATE OR REPLACE FUNCTION place_order(
    p_customer_id          INTEGER,
    p_shipping_address_id  INTEGER,
    p_items                JSONB,   -- [{"product_id": 1, "quantity": 2}, ...]
    p_notes                TEXT DEFAULT NULL
)
RETURNS INTEGER  -- returns the new order ID
LANGUAGE plpgsql
AS $$
DECLARE
    v_item       JSONB;
    v_product_id INTEGER;
    v_qty        INTEGER;
    v_price      NUMERIC(10,2);
    v_stock      INTEGER;
    v_order_id   INTEGER;
    v_total      NUMERIC(10,2) := 0;
BEGIN
    -- Validate customer exists and is active:
    IF NOT EXISTS (SELECT 1 FROM customer WHERE id = p_customer_id AND is_active = true) THEN
        RAISE EXCEPTION 'Customer % not found or inactive', p_customer_id;
    END IF;

    -- Validate address belongs to customer:
    IF NOT EXISTS (
        SELECT 1 FROM address WHERE id = p_shipping_address_id AND customer_id = p_customer_id
    ) THEN
        RAISE EXCEPTION 'Address % does not belong to customer %',
            p_shipping_address_id, p_customer_id;
    END IF;

    -- Lock all products first (ordered by id to prevent deadlocks):
    PERFORM id FROM product
    WHERE id IN (
        SELECT (item ->> 'product_id')::INTEGER FROM jsonb_array_elements(p_items) AS item
    )
    ORDER BY id
    FOR UPDATE;

    -- Validate stock and compute total:
    FOR v_item IN SELECT * FROM jsonb_array_elements(p_items) LOOP
        v_product_id := (v_item ->> 'product_id')::INTEGER;
        v_qty        := (v_item ->> 'quantity')::INTEGER;

        SELECT price, stock_quantity
        INTO v_price, v_stock
        FROM product
        WHERE id = v_product_id AND is_active = true;

        IF NOT FOUND THEN
            RAISE EXCEPTION 'Product % not found or inactive', v_product_id;
        END IF;
        IF v_stock < v_qty THEN
            RAISE EXCEPTION 'Insufficient stock for product %: need %, have %',
                v_product_id, v_qty, v_stock;
        END IF;

        v_total := v_total + (v_price * v_qty);
    END LOOP;

    -- Create the order:
    INSERT INTO "order" (customer_id, shipping_address_id, status, total_amount, notes)
    VALUES (p_customer_id, p_shipping_address_id, 'pending', v_total, p_notes)
    RETURNING id INTO v_order_id;

    -- Create order items and deduct stock:
    FOR v_item IN SELECT * FROM jsonb_array_elements(p_items) LOOP
        v_product_id := (v_item ->> 'product_id')::INTEGER;
        v_qty        := (v_item ->> 'quantity')::INTEGER;
        SELECT price INTO v_price FROM product WHERE id = v_product_id;

        INSERT INTO order_item (order_id, product_id, quantity, unit_price)
        VALUES (v_order_id, v_product_id, v_qty, v_price);

        UPDATE product SET stock_quantity = stock_quantity - v_qty WHERE id = v_product_id;
    END LOOP;

    RETURN v_order_id;
END;
$$;

-- Usage:
SELECT place_order(
    1,   -- customer_id
    1,   -- shipping_address_id
    '[{"product_id": 1, "quantity": 2}, {"product_id": 3, "quantity": 1}]',
    'Please leave at door'
);
```

---

## Try It Yourself

```sql
-- 1. Write a function apply_coupon(order_id, coupon_code) that:
--    - Looks up the coupon by code (active, not expired)
--    - Validates the order total meets the minimum order amount
--    - Applies the discount to order.total_amount
--    - Inserts a record into coupon_usage
--    - Returns the new total

-- 2. Write a function customer_lifetime_value(customer_id) that returns
--    the total spend from delivered orders for a customer.
--    Return 0 if the customer has no delivered orders.

-- 3. Write a procedure archive_old_orders(months_old INTEGER) that:
--    - Selects delivered orders older than N months
--    - Inserts them into an order_archive table (create it too)
--    - Deletes them from the live "order" table
--    - COMMITs in batches of 100
```

<details>
<summary>Show solution sketches</summary>

```sql
-- 2. Lifetime value function:
CREATE OR REPLACE FUNCTION customer_lifetime_value(p_customer_id INTEGER)
RETURNS NUMERIC(10,2)
LANGUAGE plpgsql
STABLE
AS $$
DECLARE
    v_total NUMERIC(10,2);
BEGIN
    SELECT COALESCE(SUM(total_amount), 0)
    INTO v_total
    FROM "order"
    WHERE customer_id = p_customer_id AND status = 'delivered';

    RETURN v_total;
END;
$$;

-- Usage:
SELECT name, customer_lifetime_value(id) AS ltv
FROM customer ORDER BY ltv DESC LIMIT 10;
```

</details>

---

## Capstone Connection

`place_order()` is StoreForge's most critical function — it enforces:
- Stock validation before any changes
- Atomic lock acquisition in consistent order (deadlock prevention)
- Exact unit price capture at order time (historical snapshot)
- Automatic total computation

It's called by the API layer rather than letting the app build raw SQL — centralizing business logic at the database level where it's always enforced regardless of which client accesses the DB.
