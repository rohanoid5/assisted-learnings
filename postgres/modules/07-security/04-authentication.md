# Authentication

## Concept

PostgreSQL delegates authentication configuration to two files: `pg_hba.conf` (host-based authentication) controls who can connect from where using which method, and `postgresql.conf` controls global settings like SSL and password encryption. Understanding these files is essential for securing a production cluster — the default configuration ships intentionally open and must be hardened before exposure to a network.

---

## pg_hba.conf Structure

Each line in `pg_hba.conf` has the format:

```
TYPE  DATABASE  USER  ADDRESS  METHOD  [OPTIONS]
```

PostgreSQL reads rules top-to-bottom and uses the **first matching rule**:

```
# TYPE   DATABASE          USER              ADDRESS           METHOD
# Local socket connections (Unix domain):
local    all               postgres                            peer
local    storeforge_dev    storeforge_admin                    peer

# IPv4 application server:
host     storeforge_dev    api_service       10.0.1.0/24       scram-sha-256

# IPv4 admin access from office network:
host     storeforge_dev    admin_rohan       192.168.1.0/24    scram-sha-256

# Reporting role from analytics subnet:
host     storeforge_dev    reporting_svc     10.0.2.0/24       scram-sha-256

# SSL-only replication:
hostssl  replication       replication_user  10.0.0.0/8        scram-sha-256

# Deny everything else:
host     all               all               0.0.0.0/0         reject
host     all               all               ::/0              reject
```

---

## Authentication Methods

| Method | Description | Use Case |
|---|---|---|
| `scram-sha-256` | Challenge-response password hash | All production password auth |
| `md5` | MD5 password hash (deprecated) | Legacy — migrate away |
| `peer` | OS username = DB role name | Local socket connections |
| `ident` | OS username via ident server | Rare; avoid for remote |
| `gss` | Kerberos/GSSAPI | Enterprise AD environments |
| `cert` | TLS client certificate | High-security service auth |
| `ldap` | LDAP server validation | Corporate directory |
| `reject` | Always reject | Explicit deny rules |
| `trust` | No password required | Localhost dev only |

```sql
-- Verify your PostgreSQL is using scram-sha-256 for password storage:
SHOW password_encryption;
-- scram-sha-256 (good)
-- md5 (migrate)

-- Set in postgresql.conf:
-- password_encryption = scram-sha-256

-- After changing password_encryption, existing passwords must be reset:
ALTER ROLE api_service PASSWORD 'NewPass!';  -- re-hashes with new algorithm
```

---

## Viewing Current Configuration

```sql
-- View hba rules (PostgreSQL 10+):
SELECT line_number, type, database, user_name, address, auth_method, error
FROM pg_hba_file_rules
ORDER BY line_number;

-- Check authentication settings:
SHOW hba_file;
SHOW password_encryption;
SHOW ssl;

-- View all config settings from postgresql.conf:
SELECT name, setting, unit, context
FROM pg_settings
WHERE name IN (
    'password_encryption', 'ssl', 'listen_addresses',
    'max_connections', 'log_connections', 'log_disconnections'
);
```

---

## SSL Configuration

```sql
-- Check if SSL is on:
SHOW ssl;
-- 'on' or 'off'

-- Check current connection SSL status:
SELECT ssl, version, cipher, bits FROM pg_stat_ssl WHERE pid = pg_backend_pid();

-- Force SSL for connections (in pg_hba.conf use 'hostssl' instead of 'host'):
-- hostssl  storeforge_dev  api_service  10.0.0.0/8  scram-sha-256

-- postgresql.conf settings for SSL:
-- ssl = on
-- ssl_cert_file = 'server.crt'
-- ssl_key_file  = 'server.key'
-- ssl_ca_file   = 'root.crt'          # for client cert verification
```

---

## .pgpass File

The `.pgpass` file allows scripts and tools to connect without interactive password prompts, without embedding passwords in shell scripts:

```
# ~/.pgpass format: hostname:port:database:username:password
# Permissions must be 0600 (chmod 600 ~/.pgpass)

localhost:5432:storeforge_dev:api_service:AppServicePass!
localhost:5432:storeforge_dev:admin_rohan:AdminPass!
*:5432:storeforge_dev:reporting_svc:ReportPass!
```

```bash
# Set permissions:
chmod 600 ~/.pgpass

# Now psql uses .pgpass automatically:
psql -h localhost -U api_service -d storeforge_dev
```

---

## Connection Logging

Enable connection logging to detect brute-force attempts:

```sql
-- In postgresql.conf:
-- log_connections = on         -- log each successful connection
-- log_disconnections = on      -- log each disconnection with duration
-- log_failed_connections = on  -- log each failed auth attempt

-- View recent connections in logs, or via pg_stat_activity:
SELECT pid, usename, application_name, client_addr, state, backend_start
FROM pg_stat_activity
WHERE backend_type = 'client backend'
ORDER BY backend_start;
```

---

## Reload vs Restart

```sql
-- pg_hba.conf changes take effect on reload (no downtime):
SELECT pg_reload_conf();

-- In the shell:
-- pg_ctl reload -D /var/lib/postgresql/data
-- systemctl reload postgresql

-- postgresql.conf changes requiring restart (context = 'postmaster'):
SELECT name, setting, context
FROM pg_settings
WHERE context = 'postmaster' AND name IN ('ssl', 'listen_addresses', 'max_connections');
-- These require: pg_ctl restart  (causes brief downtime)
```

---

## Try It Yourself

```sql
-- 1. Check the current password_encryption setting.
--    If it is not 'scram-sha-256', document what steps you would take to migrate.

-- 2. Write the pg_hba.conf lines (as comments/notes) that would:
--    a) Allow api_service to connect from 10.0.0.0/8 using scram-sha-256
--    b) Allow admin_rohan from 192.168.1.100/32 only
--    c) Reject all other connections

-- 3. Query pg_stat_activity to list all currently active client connections,
--    showing username, application_name, client address, and state.

-- 4. Query pg_hba_file_rules to view the actual hba config currently loaded.
```

<details>
<summary>Show solutions</summary>

```sql
-- 1. Check password encryption:
SHOW password_encryption;
-- To migrate to scram-sha-256:
-- Set in postgresql.conf: password_encryption = scram-sha-256
-- Reload config: SELECT pg_reload_conf();
-- Reset all role passwords: ALTER ROLE api_service PASSWORD 'pass';
-- Verify: SHOW password_encryption;  -- scram-sha-256

-- 2. pg_hba.conf rules (comment):
-- host  storeforge_dev  api_service  10.0.0.0/8       scram-sha-256
-- host  storeforge_dev  admin_rohan  192.168.1.100/32  scram-sha-256
-- host  all             all          0.0.0.0/0         reject
-- host  all             all          ::/0              reject

-- 3. Active connections:
SELECT pid, usename, application_name, client_addr, state,
       now() - backend_start AS connection_age
FROM pg_stat_activity
WHERE backend_type = 'client backend'
ORDER BY backend_start;

-- 4. HBA rules:
SELECT line_number, type, database, user_name, address, auth_method
FROM pg_hba_file_rules
ORDER BY line_number;
```

</details>

---

## Capstone Connection

StoreForge's authentication hardening checklist:
- `password_encryption = scram-sha-256` in `postgresql.conf`
- All application connections use `hostssl` lines in `pg_hba.conf` (TLS required)
- `api_service` can only connect from the application server subnet (`10.0.1.0/24`)
- Admin access is restricted to the office IP range or VPN gateway
- A final `reject` rule catches all other connection attempts
- `log_connections = on` and `log_failed_connections = on` feed into the SIEM alert pipeline
- `.pgpass` is used only on the DBA workstation; application credentials are injected via environment variables, never stored in files
