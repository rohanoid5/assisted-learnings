# Roles and Users

## Concept

PostgreSQL uses a unified **role** model: there is no separate concept of "user" versus "group". A role with the `LOGIN` attribute is a user; a role without it is a group. Roles can be members of other roles, inheriting their permissions. This model lets you define fine-grained privilege sets once and attach them to as many login accounts as needed — a critical foundation for multi-tier application security.

---

## Creating Roles

```sql
-- Create a group role (no LOGIN — cannot connect directly):
CREATE ROLE storeforge_readonly;
CREATE ROLE storeforge_api;
CREATE ROLE storeforge_admin;

-- Create a login role (user):
CREATE ROLE api_service
    LOGIN
    PASSWORD 'ChangeMeInProd!'  -- use scram-sha-256 (set in pg_hba.conf)
    CONNECTION LIMIT 20         -- max simultaneous connections
    VALID UNTIL '2026-01-01';   -- optional expiry

CREATE ROLE admin_rohan
    LOGIN
    PASSWORD 'AnotherStrongPass!';

-- Role hierarchy: grant group roles to login roles:
GRANT storeforge_api TO api_service;
GRANT storeforge_admin TO admin_rohan;

-- One login role can be a member of multiple groups:
GRANT storeforge_readonly TO api_service;
```

---

## Role Attributes

```sql
-- Key attributes:
-- SUPERUSER    — bypasses all privilege checks (avoid in application roles)
-- CREATEDB     — can create databases
-- CREATEROLE   — can create other roles
-- LOGIN        — can connect to PostgreSQL
-- REPLICATION  — can initiate replication streaming
-- BYPASSRLS    — bypasses row-level security (Module 07-03)
-- CONNECTION LIMIT n — -1 means unlimited

-- Inspect roles:
\du

-- Or via catalog:
SELECT rolname, rolsuper, rolcreatedb, rolcreaterole, rolcanlogin,
       rolconnlimit, rolvaliduntil
FROM pg_roles
WHERE rolname LIKE 'storeforge%' OR rolname LIKE 'api%' OR rolname LIKE 'admin%'
ORDER BY rolname;

-- Alter existing role:
ALTER ROLE api_service CONNECTION LIMIT 50;
ALTER ROLE api_service PASSWORD 'NewSecurePass!';
ALTER ROLE api_service VALID UNTIL 'infinity';  -- remove expiry
```

---

## Role Membership and Inheritance

```sql
-- INHERIT (default): the login role automatically has all privileges of its groups:
CREATE ROLE storeforge_reporting LOGIN PASSWORD 'ReportPass!' INHERIT;
GRANT storeforge_readonly TO storeforge_reporting;
-- storeforge_reporting now has all privileges of storeforge_readonly

-- NOINHERIT: role must explicitly SET ROLE to activate group privileges:
CREATE ROLE storeforge_dba LOGIN PASSWORD 'DbaPass!' NOINHERIT;
GRANT storeforge_admin TO storeforge_dba;

-- storeforge_dba connects and must activate:
SET ROLE storeforge_admin;   -- now has admin privileges
RESET ROLE;                   -- return to original role

-- Check current role:
SELECT current_user, session_user;
-- current_user = active role after SET ROLE
-- session_user = original login role (unchanged)

-- List role memberships:
SELECT r.rolname AS role, m.rolname AS member
FROM pg_auth_members am
JOIN pg_roles r ON r.oid = am.roleid
JOIN pg_roles m ON m.oid = am.member
ORDER BY r.rolname, m.rolname;
```

---

## Dropping Roles

```sql
-- A role cannot be dropped if it owns objects.
-- Reassign ownership first:
REASSIGN OWNED BY api_service TO storeforge_admin;
DROP OWNED BY api_service;  -- drops privileges
DROP ROLE api_service;

-- Or drop cascade (use carefully):
DROP ROLE IF EXISTS storeforge_reporting;
```

---

## pg_hba.conf Overview

The `pg_hba.conf` file controls which hosts can connect, using which authentication method:

```
# TYPE  DATABASE          USER                  ADDRESS         METHOD
local   all               postgres                              peer
host    storeforge_dev    api_service           10.0.0.0/8      scram-sha-256
host    storeforge_dev    admin_rohan           192.168.1.0/24  scram-sha-256
host    all               all                   0.0.0.0/0       reject
```

```sql
-- Inspect the effective pg_hba rules (PostgreSQL 16+):
SELECT type, database, user_name, address, auth_method
FROM pg_hba_file_rules
ORDER BY rule_number;

-- Reload config without restart:
SELECT pg_reload_conf();
```

---

## Try It Yourself

```sql
-- 1. Create three group roles: storeforge_readonly, storeforge_api, storeforge_admin.
-- 2. Create a login role called app_user with password '5toreForge#1'.
--    Grant it storeforge_api membership.
-- 3. Verify the membership with a query against pg_auth_members.
-- 4. Alter app_user to expire in 1 year from today.
-- 5. Drop app_user safely (reassign + drop owned + drop role).
```

<details>
<summary>Show solutions</summary>

```sql
-- 1. Group roles:
CREATE ROLE storeforge_readonly;
CREATE ROLE storeforge_api;
CREATE ROLE storeforge_admin;

-- 2. Login role:
CREATE ROLE app_user
    LOGIN
    PASSWORD '5toreForge#1'
    CONNECTION LIMIT 10;

GRANT storeforge_api TO app_user;

-- 3. Verify membership:
SELECT r.rolname AS group_role, m.rolname AS member_role
FROM pg_auth_members am
JOIN pg_roles r ON r.oid = am.roleid
JOIN pg_roles m ON m.oid = am.member
WHERE m.rolname = 'app_user';

-- 4. Set expiry:
ALTER ROLE app_user VALID UNTIL (NOW() + INTERVAL '1 year')::TEXT;

-- 5. Safe drop:
REASSIGN OWNED BY app_user TO storeforge_admin;
DROP OWNED BY app_user;
DROP ROLE app_user;
```

</details>

---

## Capstone Connection

StoreForge's role hierarchy:

| Role | Type | Purpose |
|---|---|---|
| `storeforge_admin` | Group | DDL + full DML access |
| `storeforge_api` | Group | Application read/write via SECURITY DEFINER functions |
| `storeforge_readonly` | Group | Reporting and analytics queries |
| `api_service` | Login | Production application server |
| `admin_rohan` | Login | DBA operations account |
| `reporting_svc` | Login | BI/analytics service |

The application server (`api_service`) never connects with admin credentials. All sensitive write operations go through `SECURITY DEFINER` functions owned by `storeforge_admin` and granted to `storeforge_api`.
