# Module 07 Exercises — Security

Secure the StoreForge database using roles, privileges, row-level security, authentication hardening, and encryption.

---

## Exercise 1 — Role Hierarchy

Set up the complete StoreForge role hierarchy:

```sql
-- Step 1: Create three group roles (no LOGIN):
--   storeforge_readonly, storeforge_api, storeforge_admin

-- Step 2: Create two login roles:
--   api_service (member of storeforge_api, CONNECTION LIMIT 20)
--   reporting_svc (member of storeforge_readonly, CONNECTION LIMIT 5)

-- Step 3: Grant the full privilege chain to each group:
--   storeforge_readonly: CONNECT + USAGE + SELECT on all tables
--   storeforge_api: CONNECT + USAGE + SELECT/INSERT/UPDATE/DELETE + sequence access

-- Step 4: Grant EXECUTE on place_order() and change_order_status() to storeforge_api.

-- Verification queries:
-- a) SELECT rolname, rolcanlogin FROM pg_roles WHERE rolname LIKE 'storeforge%';
-- b) has_table_privilege('storeforge_readonly', 'product', 'SELECT')
-- c) has_table_privilege('storeforge_readonly', 'product', 'DELETE')
-- d) has_function_privilege('storeforge_api', 'place_order(integer,jsonb)', 'EXECUTE')
```

<details>
<summary>Show solution</summary>

```sql
-- Group roles:
CREATE ROLE storeforge_readonly;
CREATE ROLE storeforge_api;
CREATE ROLE storeforge_admin;

-- Login roles:
CREATE ROLE api_service
    LOGIN PASSWORD 'ApiService#Secure1'
    CONNECTION LIMIT 20;

CREATE ROLE reporting_svc
    LOGIN PASSWORD 'Reporting#Secure1'
    CONNECTION LIMIT 5;

GRANT storeforge_api      TO api_service;
GRANT storeforge_readonly TO reporting_svc;

-- Database + schema:
GRANT CONNECT ON DATABASE storeforge_dev TO storeforge_readonly, storeforge_api;
GRANT USAGE   ON SCHEMA public           TO storeforge_readonly, storeforge_api;

-- Table privileges:
GRANT SELECT ON ALL TABLES IN SCHEMA public TO storeforge_readonly;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO storeforge_api;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO storeforge_api;

-- Function privileges:
GRANT EXECUTE ON FUNCTION place_order(INTEGER, JSONB) TO storeforge_api;
GRANT EXECUTE ON FUNCTION change_order_status(INTEGER, order_status) TO storeforge_api;

-- Verification:
SELECT (
    has_table_privilege('storeforge_readonly', 'product', 'SELECT') AND
    NOT has_table_privilege('storeforge_readonly', 'product', 'DELETE') AND
    has_function_privilege('storeforge_api', 'place_order(integer,jsonb)', 'EXECUTE')
) AS privilege_check;  -- should be true
```

</details>

---

## Exercise 2 — Row-Level Security

Implement customer-scoped order isolation:

```sql
-- Part A:
-- Enable RLS on "order". Add a PERMISSIVE policy that filters orders
-- to current_setting('app.customer_id', TRUE)::INTEGER.
-- Test with: BEGIN; SET LOCAL app.customer_id = '1'; SELECT COUNT(*) FROM "order"; COMMIT;
-- Then change to customer_id = 2 and compare.

-- Part B:
-- Add a RESTRICTIVE policy on "order" that hides cancelled orders
-- from storeforge_api role.

-- Part C:
-- Enable RLS on review. Write three policies:
-- 1. SELECT: all rows visible to storeforge_api
-- 2. INSERT: only app.customer_id == customer_id
-- 3. DELETE: only app.customer_id == customer_id
```

<details>
<summary>Show solution</summary>

```sql
-- Part A:
ALTER TABLE "order" ENABLE ROW LEVEL SECURITY;

CREATE POLICY customer_own_orders ON "order"
    FOR ALL TO storeforge_api
    USING (customer_id = current_setting('app.customer_id', TRUE)::INTEGER)
    WITH CHECK (customer_id = current_setting('app.customer_id', TRUE)::INTEGER);

-- Admin bypass:
ALTER ROLE storeforge_admin BYPASSRLS;

-- Test:
BEGIN;
SET LOCAL app.customer_id = '1';
SELECT COUNT(*) FROM "order";  -- orders for customer 1
COMMIT;

BEGIN;
SET LOCAL app.customer_id = '2';
SELECT COUNT(*) FROM "order";  -- orders for customer 2
COMMIT;

-- Part B:
CREATE POLICY hide_cancelled ON "order"
    AS RESTRICTIVE
    FOR SELECT TO storeforge_api
    USING (status != 'cancelled');

-- Part C:
ALTER TABLE review ENABLE ROW LEVEL SECURITY;

CREATE POLICY reviews_select ON review
    FOR SELECT TO storeforge_api USING (TRUE);

CREATE POLICY reviews_insert ON review
    FOR INSERT TO storeforge_api
    WITH CHECK (customer_id = current_setting('app.customer_id', TRUE)::INTEGER);

CREATE POLICY reviews_delete ON review
    FOR DELETE TO storeforge_api
    USING (customer_id = current_setting('app.customer_id', TRUE)::INTEGER);
```

</details>

---

## Exercise 3 — Encryption

```sql
-- Part A: pgcrypto passwords
-- Create the customer_credential table (if not already done in Module 06).
-- Write a procedure set_password(p_customer_id INT, p_password TEXT) that
-- upserts the bcrypt hash into customer_credential.
-- Write a function check_password(p_customer_id INT, p_password TEXT) RETURNS BOOLEAN.
-- Test with: SELECT check_password(1, 'correct'); SELECT check_password(1, 'wrong');

-- Part B: Column encryption
-- Create the payment_method table with encrypted card_number_enc and expiry_enc columns.
-- Insert one row with pgp_sym_encrypt.
-- Write a query that decrypts and returns the last_four and expiry for customer_id = 1.
-- Confirm that selecting card_number_enc without decrypting returns BYTEA (not plaintext).

-- Part C: Masking
-- Create the customer_masked view that hides name, email, and phone.
-- Grant SELECT on customer_masked to storeforge_readonly.
-- Revoke direct SELECT on customer from storeforge_readonly.
```

<details>
<summary>Show solution</summary>

```sql
-- Part A:
CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE IF NOT EXISTS customer_credential (
    customer_id   INTEGER PRIMARY KEY REFERENCES customer(id) ON DELETE CASCADE,
    password_hash TEXT NOT NULL,
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE OR REPLACE PROCEDURE set_password(p_customer_id INTEGER, p_password TEXT)
LANGUAGE sql AS $$
    INSERT INTO customer_credential (customer_id, password_hash)
    VALUES (p_customer_id, crypt(p_password, gen_salt('bf', 10)))
    ON CONFLICT (customer_id) DO UPDATE
        SET password_hash = EXCLUDED.password_hash, updated_at = NOW();
$$;

CREATE OR REPLACE FUNCTION check_password(p_customer_id INTEGER, p_password TEXT)
RETURNS BOOLEAN AS $$
    SELECT EXISTS (
        SELECT 1 FROM customer_credential
        WHERE customer_id = p_customer_id
          AND password_hash = crypt(p_password, password_hash)
    );
$$ LANGUAGE sql STABLE STRICT;

CALL set_password(1, 'correct');
SELECT check_password(1, 'correct');  -- true
SELECT check_password(1, 'wrong');    -- false

-- Part B:
CREATE TABLE IF NOT EXISTS payment_method (
    id              SERIAL PRIMARY KEY,
    customer_id     INTEGER NOT NULL REFERENCES customer(id) ON DELETE CASCADE,
    card_type       TEXT NOT NULL,
    last_four       CHAR(4) NOT NULL,
    card_number_enc BYTEA NOT NULL,
    expiry_enc      BYTEA NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

INSERT INTO payment_method (customer_id, card_type, last_four, card_number_enc, expiry_enc)
VALUES (
    1, 'visa', '4242',
    pgp_sym_encrypt('4111111111114242', 'my-secret-key-32!'),
    pgp_sym_encrypt('1228', 'my-secret-key-32!')
);

-- Decrypt:
SELECT
    last_four,
    pgp_sym_decrypt(card_number_enc, 'my-secret-key-32!') AS pan,
    pgp_sym_decrypt(expiry_enc, 'my-secret-key-32!')      AS expiry
FROM payment_method WHERE customer_id = 1;

-- Raw bytea (unreadable):
SELECT card_number_enc FROM payment_method WHERE customer_id = 1;

-- Part C:
CREATE OR REPLACE VIEW customer_masked AS
SELECT
    id,
    'Customer_' || id::TEXT AS name,
    'user' || id::TEXT || '@example.com' AS email,
    CASE WHEN phone IS NOT NULL
         THEN LEFT(phone, 3) || '****' ELSE NULL END AS phone,
    is_active,
    created_at
FROM customer;

GRANT SELECT ON customer_masked TO storeforge_readonly;
REVOKE SELECT ON customer FROM storeforge_readonly;

-- Verify:
SELECT name, email FROM customer WHERE id = 1;           -- real data
SELECT name, email FROM customer_masked WHERE id = 1;    -- masked data
```

</details>

---

## Capstone Checkpoint ✅

After completing these exercises, you should be able to:

- [ ] Create a group role hierarchy and attach login roles correctly
- [ ] Grant the complete privilege chain: database → schema → tables → sequences → functions
- [ ] Verify privileges with `has_table_privilege()` and `has_function_privilege()`
- [ ] Enable RLS on a table and write PERMISSIVE + RESTRICTIVE policies
- [ ] Use `current_setting('app.customer_id', TRUE)` as a session-scoped identity claim
- [ ] Test RLS isolation with `SET LOCAL` in a transaction
- [ ] Store and verify bcrypt passwords with pgcrypto
- [ ] Encrypt sensitive column data with `pgp_sym_encrypt` / `pgp_sym_decrypt`
- [ ] Create a data-masking view for non-production environments
