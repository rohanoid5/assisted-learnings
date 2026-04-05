-- =============================================================================
-- StoreForge Security: Roles, Grants, and Row-Level Security
-- Run AFTER functions.sql and BEFORE indexes.sql.
--
-- Architecture:
--   Group roles (no LOGIN):  storeforge_admin, storeforge_api, storeforge_readonly
--   Login roles:             api_service (for the app server), admin_rohan (DBA)
-- =============================================================================

-- =============================================================================
-- 1. Revoke default public schema access
--    By default any role can create objects in public. Lock this down.
-- =============================================================================
REVOKE CREATE ON SCHEMA public FROM PUBLIC;

-- =============================================================================
-- 2. Group roles (no LOGIN — never connect directly)
-- =============================================================================
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'storeforge_readonly') THEN
        CREATE ROLE storeforge_readonly NOLOGIN;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'storeforge_api') THEN
        CREATE ROLE storeforge_api NOLOGIN;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'storeforge_admin') THEN
        CREATE ROLE storeforge_admin NOLOGIN BYPASSRLS;
    END IF;
END;
$$;

-- =============================================================================
-- 3. Login roles (inherit from group roles)
-- =============================================================================
DO $$
BEGIN
    -- Application server role
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'api_service') THEN
        CREATE ROLE api_service LOGIN PASSWORD 'change_me_in_production';
    END IF;
    -- DBA / admin role
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'admin_rohan') THEN
        CREATE ROLE admin_rohan LOGIN PASSWORD 'change_me_in_production';
    END IF;
END;
$$;

GRANT storeforge_api   TO api_service;
GRANT storeforge_admin TO admin_rohan;

-- =============================================================================
-- 4. Schema access
-- =============================================================================
GRANT USAGE ON SCHEMA public TO storeforge_readonly, storeforge_api, storeforge_admin;

-- =============================================================================
-- 5. Table grants
-- =============================================================================

-- storeforge_readonly: SELECT only on non-sensitive tables
GRANT SELECT ON
    category, product, "order", order_item, review, address
TO storeforge_readonly;

-- storeforge_api: Full DML on operational tables; no access to credentials or audit
GRANT SELECT, INSERT, UPDATE, DELETE ON
    customer,
    address,
    "order",
    order_item,
    review
TO storeforge_api;

GRANT SELECT ON
    category, product
TO storeforge_api;

GRANT INSERT, UPDATE ON
    product
TO storeforge_api;

-- api can insert audit_log rows (via SECURITY DEFINER function — not directly needed,
-- but explicit permission helps if the trigger owner changes)
GRANT INSERT ON audit_log TO storeforge_api;

-- api can use sequences (needed for INSERT with SERIAL/BIGSERIAL)
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO storeforge_api;

-- storeforge_admin: all privileges
GRANT ALL PRIVILEGES ON ALL TABLES    IN SCHEMA public TO storeforge_admin;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public TO storeforge_admin;

-- =============================================================================
-- 6. Function grants
-- =============================================================================
-- Public-facing functions usable by the API role
GRANT EXECUTE ON FUNCTION place_order(INT, INT, JSONB)    TO storeforge_api;
GRANT EXECUTE ON FUNCTION product_search(TEXT, INT)       TO storeforge_api, storeforge_readonly;
GRANT EXECUTE ON FUNCTION customer_summary(INT)           TO storeforge_api;
GRANT EXECUTE ON FUNCTION verify_password(INT, TEXT)      TO storeforge_api;
GRANT EXECUTE ON FUNCTION set_password(INT, TEXT)         TO storeforge_api;

-- Admin gets everything
GRANT EXECUTE ON ALL FUNCTIONS IN SCHEMA public TO storeforge_admin;

-- =============================================================================
-- 7. Default privileges for future objects
--    Ensures newly created tables/sequences are automatically accessible.
-- =============================================================================
ALTER DEFAULT PRIVILEGES IN SCHEMA public
    GRANT SELECT ON TABLES TO storeforge_readonly;

ALTER DEFAULT PRIVILEGES IN SCHEMA public
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO storeforge_api;

ALTER DEFAULT PRIVILEGES IN SCHEMA public
    GRANT USAGE, SELECT ON SEQUENCES TO storeforge_api;

ALTER DEFAULT PRIVILEGES IN SCHEMA public
    GRANT ALL ON TABLES    TO storeforge_admin;

ALTER DEFAULT PRIVILEGES IN SCHEMA public
    GRANT ALL ON SEQUENCES TO storeforge_admin;

-- =============================================================================
-- 8. Row-Level Security (RLS)
--
--    Pattern: the application sets a session variable before executing queries:
--      SET LOCAL app.customer_id = '42';
--
--    Policies ensure each customer can only see/modify their own rows.
--    Admin role has BYPASSRLS (set in step 2 above).
-- =============================================================================

-- --- customer table ---
ALTER TABLE customer ENABLE ROW LEVEL SECURITY;

-- Customers can only see and update their own row
CREATE POLICY customer_isolation ON customer
    FOR ALL
    TO storeforge_api
    USING (
        id = current_setting('app.customer_id', TRUE)::INT
    )
    WITH CHECK (
        id = current_setting('app.customer_id', TRUE)::INT
    );

-- Read-only role can see all customers (e.g., internal reports)
CREATE POLICY customer_readonly ON customer
    FOR SELECT
    TO storeforge_readonly
    USING (TRUE);

-- --- address table ---
ALTER TABLE address ENABLE ROW LEVEL SECURITY;

CREATE POLICY address_isolation ON address
    FOR ALL
    TO storeforge_api
    USING (
        customer_id = current_setting('app.customer_id', TRUE)::INT
    )
    WITH CHECK (
        customer_id = current_setting('app.customer_id', TRUE)::INT
    );

-- --- order table ---
ALTER TABLE "order" ENABLE ROW LEVEL SECURITY;

CREATE POLICY order_isolation ON "order"
    FOR ALL
    TO storeforge_api
    USING (
        customer_id = current_setting('app.customer_id', TRUE)::INT
    )
    WITH CHECK (
        customer_id = current_setting('app.customer_id', TRUE)::INT
    );

-- --- review table ---
ALTER TABLE review ENABLE ROW LEVEL SECURITY;

CREATE POLICY review_read_all ON review
    FOR SELECT
    TO storeforge_api, storeforge_readonly
    USING (TRUE);  -- anyone can read reviews

CREATE POLICY review_own_write ON review
    FOR INSERT
    TO storeforge_api
    WITH CHECK (
        customer_id = current_setting('app.customer_id', TRUE)::INT
    );

CREATE POLICY review_own_update ON review
    FOR UPDATE
    TO storeforge_api
    USING (
        customer_id = current_setting('app.customer_id', TRUE)::INT
    );

-- =============================================================================
-- 9. Protect sensitive tables from API role entirely
-- =============================================================================
REVOKE ALL ON customer_credential FROM storeforge_api;
REVOKE ALL ON payment_method      FROM storeforge_api;

-- The verify_password() and set_password() SECURITY DEFINER functions
-- provide the only sanctioned access to customer_credential.
-- payment_method access must go through a dedicated, audited function.

-- =============================================================================
-- Verification queries (run manually to confirm setup)
-- =============================================================================
-- \dp customer            -- show table privileges
-- \dp "order"
-- SELECT rolname, rolbypassrls FROM pg_roles WHERE rolname LIKE 'storeforge%';
--
-- Test RLS as the api_service role:
--   SET ROLE api_service;
--   SET LOCAL app.customer_id = '1';
--   SELECT * FROM customer;         -- should return only customer id=1
--   SELECT * FROM "order";          -- should return only orders for customer 1
--   RESET ROLE;
