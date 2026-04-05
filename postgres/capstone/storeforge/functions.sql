-- =============================================================================
-- StoreForge Functions & Triggers
-- Run AFTER schema.sql. Run BEFORE security.sql.
-- =============================================================================

CREATE EXTENSION IF NOT EXISTS pgcrypto;
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- =============================================================================
-- 1. Utility: set_updated_at()
--    Updates the updated_at column to NOW() before any UPDATE.
-- =============================================================================
CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS TRIGGER
LANGUAGE plpgsql AS $$
BEGIN
    NEW.updated_at := NOW();
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_customer_updated_at
    BEFORE UPDATE ON customer
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER trg_order_updated_at
    BEFORE UPDATE ON "order"
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- =============================================================================
-- 2. Audit: audit_trigger_fn()
--    Writes old/new row state to audit_log for INSERT, UPDATE, DELETE.
-- =============================================================================
CREATE OR REPLACE FUNCTION audit_trigger_fn()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp AS $$
BEGIN
    IF (TG_OP = 'INSERT') THEN
        INSERT INTO audit_log (table_name, operation, row_id, old_data, new_data)
        VALUES (TG_TABLE_NAME, TG_OP, NEW.id, NULL, to_jsonb(NEW));
    ELSIF (TG_OP = 'UPDATE') THEN
        INSERT INTO audit_log (table_name, operation, row_id, old_data, new_data)
        VALUES (TG_TABLE_NAME, TG_OP, NEW.id, to_jsonb(OLD), to_jsonb(NEW));
    ELSIF (TG_OP = 'DELETE') THEN
        INSERT INTO audit_log (table_name, operation, row_id, old_data, new_data)
        VALUES (TG_TABLE_NAME, TG_OP, OLD.id, to_jsonb(OLD), NULL);
    END IF;
    RETURN NULL; -- result is ignored for AFTER triggers
END;
$$;

-- Attach audit trigger to high-value tables
CREATE TRIGGER trg_customer_audit
    AFTER INSERT OR UPDATE OR DELETE ON customer
    FOR EACH ROW EXECUTE FUNCTION audit_trigger_fn();

CREATE TRIGGER trg_order_audit
    AFTER INSERT OR UPDATE OR DELETE ON "order"
    FOR EACH ROW EXECUTE FUNCTION audit_trigger_fn();

CREATE TRIGGER trg_product_audit
    AFTER INSERT OR UPDATE OR DELETE ON product
    FOR EACH ROW EXECUTE FUNCTION audit_trigger_fn();

-- =============================================================================
-- 3. Inventory: check_stock_non_negative()
--    Prevents stock_quantity from being set below zero.
-- =============================================================================
CREATE OR REPLACE FUNCTION check_stock_non_negative()
RETURNS TRIGGER
LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.stock_quantity < 0 THEN
        RAISE EXCEPTION 'Insufficient stock for product % (requested: %, available: %)',
            NEW.id,
            OLD.stock_quantity - NEW.stock_quantity,
            OLD.stock_quantity;
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_product_stock_check
    BEFORE UPDATE OF stock_quantity ON product
    FOR EACH ROW EXECUTE FUNCTION check_stock_non_negative();

-- =============================================================================
-- 4. Order total: update_order_total()
--    Recalculates order.total_amount whenever order_item changes.
-- =============================================================================
CREATE OR REPLACE FUNCTION update_order_total()
RETURNS TRIGGER
LANGUAGE plpgsql AS $$
DECLARE
    v_order_id BIGINT;
BEGIN
    -- Determine which order was affected
    v_order_id := COALESCE(NEW.order_id, OLD.order_id);

    UPDATE "order"
    SET total_amount = (
        SELECT COALESCE(SUM(quantity * unit_price), 0)
        FROM order_item
        WHERE order_id = v_order_id
    )
    WHERE id = v_order_id;

    RETURN NULL;
END;
$$;

CREATE TRIGGER trg_order_item_total
    AFTER INSERT OR UPDATE OR DELETE ON order_item
    FOR EACH ROW EXECUTE FUNCTION update_order_total();

-- =============================================================================
-- 5. Business: place_order(p_customer_id, p_address_id, p_items)
--
--    Atomically places an order:
--      - Validates customer and address
--      - Locks product rows to prevent overselling
--      - Decrements stock_quantity
--      - Creates order and order_item rows
--      - Returns the new order id
--
--    p_items format: '[{"product_id": 1, "quantity": 2}, ...]'
--
--    Example:
--      SELECT place_order(1, 1, '[{"product_id":3,"quantity":1}]');
-- =============================================================================
CREATE OR REPLACE FUNCTION place_order(
    p_customer_id  INT,
    p_address_id   INT,
    p_items        JSONB
)
RETURNS BIGINT
LANGUAGE plpgsql AS $$
DECLARE
    v_order_id BIGINT;
    v_item     JSONB;
    v_product  product%ROWTYPE;
BEGIN
    -- Validate customer exists and is active
    IF NOT EXISTS (
        SELECT 1 FROM customer WHERE id = p_customer_id AND is_active = TRUE
    ) THEN
        RAISE EXCEPTION 'Customer % not found or inactive', p_customer_id;
    END IF;

    -- Validate address belongs to customer
    IF NOT EXISTS (
        SELECT 1 FROM address WHERE id = p_address_id AND customer_id = p_customer_id
    ) THEN
        RAISE EXCEPTION 'Address % does not belong to customer %', p_address_id, p_customer_id;
    END IF;

    -- Validate input
    IF jsonb_array_length(p_items) = 0 THEN
        RAISE EXCEPTION 'Order must contain at least one item';
    END IF;

    -- Create the order (total_amount will be updated by trigger)
    INSERT INTO "order" (customer_id, shipping_address_id, status, total_amount)
    VALUES (p_customer_id, p_address_id, 'pending', 0)
    RETURNING id INTO v_order_id;

    -- Process each item
    FOR v_item IN SELECT * FROM jsonb_array_elements(p_items) LOOP
        -- Lock the product row to prevent concurrent overselling
        SELECT * INTO v_product
        FROM product
        WHERE id = (v_item->>'product_id')::INT AND is_active = TRUE
        FOR UPDATE;

        IF NOT FOUND THEN
            RAISE EXCEPTION 'Product % not found or unavailable', v_item->>'product_id';
        END IF;

        IF v_product.stock_quantity < (v_item->>'quantity')::INT THEN
            RAISE EXCEPTION 'Insufficient stock for product % (available: %)',
                v_product.id, v_product.stock_quantity;
        END IF;

        -- Decrement stock
        UPDATE product
        SET stock_quantity = stock_quantity - (v_item->>'quantity')::INT
        WHERE id = v_product.id;

        -- Insert order item (total_amount trigger fires here)
        INSERT INTO order_item (order_id, product_id, quantity, unit_price)
        VALUES (v_order_id, v_product.id, (v_item->>'quantity')::INT, v_product.price);
    END LOOP;

    RETURN v_order_id;
END;
$$;

-- =============================================================================
-- 6. Search: product_search(p_query, p_limit)
--
--    Full-text search with trigram fallback for partial/fuzzy matches.
--    Returns products ordered by relevance.
--
--    Example:
--      SELECT * FROM product_search('wireless headphones', 10);
--      SELECT * FROM product_search('lapto', 5);  -- trigram catches typo
-- =============================================================================
CREATE OR REPLACE FUNCTION product_search(
    p_query TEXT,
    p_limit INT  DEFAULT 20
)
RETURNS TABLE (
    id            INT,
    name          TEXT,
    price         NUMERIC(10, 2),
    stock_quantity INT,
    rank           REAL
)
LANGUAGE plpgsql
STABLE
SET search_path = public, pg_temp AS $$
BEGIN
    RETURN QUERY
    SELECT
        p.id,
        p.name,
        p.price,
        p.stock_quantity,
        -- Combine FTS rank with trigram similarity for robust scoring
        (ts_rank(p.search_vector, query) + similarity(p.name, p_query)) AS rank
    FROM
        product p,
        to_tsquery('english', regexp_replace(trim(p_query), '\s+', ' & ', 'g')) AS query
    WHERE
        p.is_active = TRUE
        AND (
            p.search_vector @@ query
            OR p.name % p_query  -- trigram similarity (pg_trgm)
        )
    ORDER BY rank DESC
    LIMIT p_limit;
END;
$$;

-- =============================================================================
-- 7. Reporting: customer_summary(p_customer_id)
--
--    Returns aggregated lifetime value statistics for a customer.
--
--    Example:
--      SELECT * FROM customer_summary(1);
-- =============================================================================
CREATE OR REPLACE FUNCTION customer_summary(p_customer_id INT)
RETURNS TABLE (
    customer_name      TEXT,
    email              TEXT,
    total_orders       BIGINT,
    total_spent        NUMERIC,
    avg_order_value    NUMERIC,
    first_order_at     TIMESTAMPTZ,
    last_order_at      TIMESTAMPTZ,
    favourite_category TEXT
)
LANGUAGE plpgsql
STABLE
SET search_path = public, pg_temp AS $$
BEGIN
    RETURN QUERY
    WITH order_stats AS (
        SELECT
            COUNT(*)                     AS order_count,
            SUM(o.total_amount)          AS lifetime_value,
            AVG(o.total_amount)          AS avg_value,
            MIN(o.created_at)            AS first_at,
            MAX(o.created_at)            AS last_at
        FROM "order" o
        WHERE o.customer_id = p_customer_id
          AND o.status NOT IN ('cancelled', 'returned')
    ),
    top_cat AS (
        SELECT c.name
        FROM order_item oi
        JOIN "order" o  ON o.id = oi.order_id
        JOIN product  p ON p.id = oi.product_id
        JOIN category c ON c.id = p.category_id
        WHERE o.customer_id = p_customer_id
        GROUP BY c.name
        ORDER BY SUM(oi.quantity) DESC
        LIMIT 1
    )
    SELECT
        cu.name::TEXT,
        cu.email::TEXT,
        os.order_count,
        COALESCE(os.lifetime_value, 0),
        COALESCE(ROUND(os.avg_value, 2), 0),
        os.first_at,
        os.last_at,
        tc.name::TEXT
    FROM customer cu
    CROSS JOIN order_stats os
    LEFT JOIN top_cat tc ON TRUE
    WHERE cu.id = p_customer_id;
END;
$$;

-- =============================================================================
-- 8. Security: verify_password(p_customer_id, p_password)
--    Returns TRUE if the plain-text password matches the stored bcrypt hash.
--
--    Example:
--      SELECT verify_password(1, 'password123');
-- =============================================================================
CREATE OR REPLACE FUNCTION verify_password(
    p_customer_id INT,
    p_password    TEXT
)
RETURNS BOOLEAN
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp AS $$
DECLARE
    v_hash TEXT;
BEGIN
    SELECT password_hash INTO v_hash
    FROM customer_credential
    WHERE customer_id = p_customer_id;

    IF v_hash IS NULL THEN
        RETURN FALSE;
    END IF;

    -- pgcrypto's crypt() re-hashes the plain text using the salt embedded in v_hash
    RETURN v_hash = crypt(p_password, v_hash);
END;
$$;

-- =============================================================================
-- 9. Security: set_password(p_customer_id, p_password)
--    Hashes a plain-text password with bcrypt (cost=10) and stores it.
--    Only callable by roles with EXECUTE privilege on this function.
--
--    Example:
--      SELECT set_password(1, 'my-new-secure-password');
-- =============================================================================
CREATE OR REPLACE FUNCTION set_password(
    p_customer_id INT,
    p_password    TEXT
)
RETURNS VOID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp AS $$
BEGIN
    INSERT INTO customer_credential (customer_id, password_hash)
    VALUES (p_customer_id, crypt(p_password, gen_salt('bf', 10)))
    ON CONFLICT (customer_id) DO UPDATE
        SET password_hash = crypt(p_password, gen_salt('bf', 10)),
            updated_at    = NOW();
END;
$$;

-- =============================================================================
-- Quick smoke test (run manually to verify):
-- SELECT place_order(1, 1, '[{"product_id": 14, "quantity": 2}]');
-- SELECT * FROM customer_summary(1);
-- SELECT * FROM product_search('laptop', 5);
-- SELECT verify_password(1, 'password123');  -- TRUE if seed.sql was run
-- =============================================================================
