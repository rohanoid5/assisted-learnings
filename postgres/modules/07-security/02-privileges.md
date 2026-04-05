# Privileges

## Concept

Roles don't automatically have access to anything. Every operation — SELECT, INSERT, UPDATE, DELETE, EXECUTE, USAGE — must be explicitly granted. PostgreSQL has a layered privilege system: you grant on databases, schemas, tables, columns, sequences, and functions independently. Understanding the full grant chain (database → schema → object) is essential because missing a grant at any layer silently blocks access.

---

## The Grant Chain

To access a table, a role needs:
1. `CONNECT` privilege on the database
2. `USAGE` privilege on the schema
3. `SELECT` / `INSERT` / `UPDATE` / `DELETE` on the table itself

```sql
-- Layer 1: database connection:
GRANT CONNECT ON DATABASE storeforge_dev TO storeforge_readonly;
GRANT CONNECT ON DATABASE storeforge_dev TO storeforge_api;

-- Layer 2: schema visibility:
GRANT USAGE ON SCHEMA public TO storeforge_readonly;
GRANT USAGE ON SCHEMA public TO storeforge_api;

-- Layer 3: table-level privileges:
GRANT SELECT ON ALL TABLES IN SCHEMA public TO storeforge_readonly;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO storeforge_api;

-- Sequence access (needed for SERIAL / IDENTITY columns):
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO storeforge_api;
```

---

## DEFAULT PRIVILEGES

`GRANT ON ALL TABLES` only covers currently existing tables. Use `ALTER DEFAULT PRIVILEGES` so future tables are automatically covered:

```sql
-- Future tables created by storeforge_admin will be readable by storeforge_readonly:
ALTER DEFAULT PRIVILEGES FOR ROLE storeforge_admin IN SCHEMA public
    GRANT SELECT ON TABLES TO storeforge_readonly;

-- Future tables writable by storeforge_api:
ALTER DEFAULT PRIVILEGES FOR ROLE storeforge_admin IN SCHEMA public
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO storeforge_api;

-- Future sequences:
ALTER DEFAULT PRIVILEGES FOR ROLE storeforge_admin IN SCHEMA public
    GRANT USAGE, SELECT ON SEQUENCES TO storeforge_api;

-- Check what default privileges are configured:
SELECT pg_get_userbyid(d.defaclrole) AS owner,
       nspname AS schema,
       d.defaclobjtype AS obj_type,
       array_to_string(d.defaclacl, ', ') AS acl
FROM pg_default_acl d
JOIN pg_namespace n ON n.oid = d.defaclnamespace;
```

---

## Function Privileges

```sql
-- Grant EXECUTE on specific functions to storeforge_api:
GRANT EXECUTE ON FUNCTION place_order(INTEGER, JSONB) TO storeforge_api;
GRANT EXECUTE ON FUNCTION change_order_status(INTEGER, order_status) TO storeforge_api;
GRANT EXECUTE ON FUNCTION verify_password(INTEGER, TEXT) TO storeforge_api;

-- Grant EXECUTE on all current functions in schema:
GRANT EXECUTE ON ALL FUNCTIONS IN SCHEMA public TO storeforge_readonly;

-- Default for future functions:
ALTER DEFAULT PRIVILEGES FOR ROLE storeforge_admin IN SCHEMA public
    GRANT EXECUTE ON FUNCTIONS TO storeforge_api;
```

---

## Column-Level Privileges

Grant access to specific columns for sensitive tables:

```sql
-- The api role can SELECT specific columns from customer,
-- but never the raw password_hash:
REVOKE SELECT ON customer_credential FROM storeforge_api;

-- Only the verify_password() SECURITY DEFINER function can access it.

-- Column-level SELECT for a masked customer view:
GRANT SELECT (id, name, email, phone, is_active, created_at)
    ON customer TO storeforge_readonly;
-- storeforge_readonly cannot see updated_at or any column not listed
```

---

## REVOKE

```sql
-- Remove a privilege:
REVOKE DELETE ON "order" FROM storeforge_api;

-- Revoke all privileges on a table:
REVOKE ALL PRIVILEGES ON product FROM storeforge_readonly;

-- Revoke from all roles in a schema:
REVOKE SELECT ON ALL TABLES IN SCHEMA public FROM storeforge_readonly;
```

---

## Inspecting Privileges

```sql
-- Table privileges (shorthand in psql):
\dp product
\dp customer

-- Full privilege query:
SELECT grantee, table_name, privilege_type
FROM information_schema.role_table_grants
WHERE table_schema = 'public'
  AND table_name IN ('product', 'customer', 'order')
ORDER BY table_name, grantee, privilege_type;

-- Column privileges:
SELECT grantee, table_name, column_name, privilege_type
FROM information_schema.column_privileges
WHERE table_schema = 'public'
ORDER BY table_name, grantee;

-- Function privileges:
SELECT grantee, routine_name, privilege_type
FROM information_schema.routine_privileges
WHERE routine_schema = 'public'
ORDER BY routine_name, grantee;

-- Check if the current role has a specific privilege:
SELECT has_table_privilege('storeforge_api', 'product', 'SELECT');
SELECT has_table_privilege('storeforge_readonly', 'order_item', 'DELETE');
SELECT has_function_privilege('storeforge_api', 'place_order(integer,jsonb)', 'EXECUTE');
```

---

## Locking Down the Public Schema

By default, every role can create objects in the `public` schema. Lock this down:

```sql
-- PostgreSQL 14 changed the default, but for older versions:
REVOKE CREATE ON SCHEMA public FROM PUBLIC;

-- Only storeforge_admin can create objects:
GRANT CREATE ON SCHEMA public TO storeforge_admin;

-- Verify:
SELECT has_schema_privilege('storeforge_api', 'public', 'CREATE');  -- false
SELECT has_schema_privilege('storeforge_admin', 'public', 'CREATE'); -- true
```

---

## Try It Yourself

```sql
-- 1. Grant the complete privilege chain to storeforge_readonly:
--    CONNECT on the database, USAGE on public, SELECT on all tables.
--    Then verify with has_table_privilege().

-- 2. Grant storeforge_api the ability to call place_order() and change_order_status().
--    Verify with has_function_privilege().

-- 3. Set default privileges so that all future tables created by the current user
--    are automatically granted SELECT to storeforge_readonly.

-- 4. Confirm that storeforge_readonly cannot INSERT into product using
--    has_table_privilege(). Then verify it CAN select.
```

<details>
<summary>Show solutions</summary>

```sql
-- 1. Full chain for storeforge_readonly:
GRANT CONNECT ON DATABASE storeforge_dev TO storeforge_readonly;
GRANT USAGE ON SCHEMA public TO storeforge_readonly;
GRANT SELECT ON ALL TABLES IN SCHEMA public TO storeforge_readonly;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO storeforge_readonly;

SELECT has_table_privilege('storeforge_readonly', 'product', 'SELECT');   -- true
SELECT has_table_privilege('storeforge_readonly', 'customer', 'SELECT');  -- true

-- 2. Function grants for storeforge_api:
GRANT EXECUTE ON FUNCTION place_order(INTEGER, JSONB) TO storeforge_api;
GRANT EXECUTE ON FUNCTION change_order_status(INTEGER, order_status) TO storeforge_api;

SELECT has_function_privilege(
    'storeforge_api',
    'place_order(integer,jsonb)',
    'EXECUTE'
);  -- true

-- 3. Default privileges:
ALTER DEFAULT PRIVILEGES IN SCHEMA public
    GRANT SELECT ON TABLES TO storeforge_readonly;

-- 4. Verify SELECT yes, INSERT no:
SELECT has_table_privilege('storeforge_readonly', 'product', 'INSERT');  -- false
SELECT has_table_privilege('storeforge_readonly', 'product', 'SELECT');  -- true
```

</details>

---

## Capstone Connection

StoreForge's complete grant matrix:

| Privilege | `storeforge_admin` | `storeforge_api` | `storeforge_readonly` |
|---|---|---|---|
| CONNECT | ✅ | ✅ | ✅ |
| USAGE on public | ✅ | ✅ | ✅ |
| SELECT all tables | ✅ | ✅ | ✅ |
| INSERT / UPDATE / DELETE | ✅ | via DEFINER fns | ❌ |
| DDL (CREATE/ALTER/DROP) | ✅ | ❌ | ❌ |
| EXECUTE functions | ✅ | selected only | ❌ |
| customer_credential | ✅ | via DEFINER fn | ❌ |

The `api_service` login role (member of `storeforge_api`) has no raw DML access to sensitive tables — all mutations go through `SECURITY DEFINER` functions.
