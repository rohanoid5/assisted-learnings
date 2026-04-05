# Connection Pooling

## Concept

Each PostgreSQL connection spawns a dedicated backend process consuming roughly 5–10 MB of shared memory — even if the connection is idle. A busy application with hundreds of concurrent clients (e.g., a thread pool of 200 workers, each holding a connection) exhausts memory and degrades performance through heavy context-switching. **Connection pooling** sits between the application and PostgreSQL, reusing a small number of server-side connections across many application-side connections, dramatically reducing overhead.

---

## Why Pooling Matters

```sql
-- See current connections and their state:
SELECT state, wait_event_type, wait_event, count(*)
FROM pg_stat_activity
WHERE datname = 'storeforge_dev'
GROUP BY state, wait_event_type, wait_event
ORDER BY count DESC;
-- state = 'idle':    connection open but doing nothing — wasted resource
-- state = 'active':  currently executing a query
-- state = 'idle in transaction': DANGER — holding locks, blocking others

-- Total connections vs limit:
SELECT count(*), (SELECT setting::int FROM pg_settings WHERE name = 'max_connections')
FROM pg_stat_activity;

-- Each idle connection still costs ~5MB RAM for the backend process.
-- With max_connections = 200 and 200 idle connections: ~1GB wasted on nothing.
```

---

## PgBouncer

PgBouncer is the most widely deployed PostgreSQL connection pooler:

```
Application threads → PgBouncer (listens on port 6432) → PostgreSQL (port 5432)
```

### Pooling Modes

| Mode | Server Connection Released | Session Features Available |
|------|---------------------------|---------------------------|
| **Session** | When client disconnects | Full (SET, prepared stmts, LISTEN) |
| **Transaction** | After each transaction commits/rolls back | Limited (no `SET` persistence) |
| **Statement** | After each individual statement | Minimal (no multi-statement txns) |

**Transaction mode is recommended for most web applications.** Applications must not use `SET` outside transactions and should not use prepared statements (unless using `server_reset_query = DISCARD ALL`).

---

## PgBouncer Configuration

```ini
; /etc/pgbouncer/pgbouncer.ini

[databases]
; Virtual database name → real connection string
storeforge_dev = host=127.0.0.1 port=5432 dbname=storeforge_dev

[pgbouncer]
listen_addr = 127.0.0.1
listen_port = 6432

; Authentication:
auth_type = scram-sha-256
auth_file = /etc/pgbouncer/userlist.txt

; Pool mode:
pool_mode = transaction

; Max server connections PgBouncer opens to PostgreSQL:
max_client_conn = 1000   ; application-facing connections accepted
default_pool_size = 25   ; server-facing connections per database/user pair

; How long to wait for a server connection before error:
server_connect_timeout = 5
client_login_timeout = 10

; Idle server connections to retire:
server_idle_timeout = 600

; When a server connection is returned to pool, run this:
server_reset_query = DISCARD ALL

; Log slow connections (seconds):
log_connections = 0
log_disconnections = 0
log_stats = 1
stats_period = 60
```

```bash
# userlist.txt — hashed passwords (use pgbouncer's own md5 format):
# "username" "md5<md5(password+username)>"
# Or with scram-sha-256, mirror the pg_shadow hash:
"storeforge_api" "SCRAM-SHA-256$4096:..."
```

```bash
# Start PgBouncer:
pgbouncer -d /etc/pgbouncer/pgbouncer.ini

# Reload config without restart:
pgbouncer -R /etc/pgbouncer/pgbouncer.ini
```

---

## Monitoring PgBouncer

```bash
# Connect to PgBouncer admin console (user must be in admin_users):
psql -h 127.0.0.1 -p 6432 -U pgbouncer pgbouncer
```

```sql
-- Show pool status:
SHOW POOLS;
-- database | user | cl_active | cl_waiting | sv_active | sv_idle | sv_used | maxwait
-- cl_active:   clients currently paired with a server connection
-- cl_waiting:  clients waiting for a free server connection ← watch this
-- sv_active:   server connections executing a query
-- sv_idle:     server connections pooled, waiting for a client
-- maxwait:     longest wait time (seconds) — alert if > 1s

-- Overall throughput stats:
SHOW STATS;
-- total_requests, total_received, total_sent, total_query_time

-- All configured databases:
SHOW DATABASES;

-- Active client connections:
SHOW CLIENTS;

-- Active server connections:
SHOW SERVERS;

-- Live configuration:
SHOW CONFIG;

-- Pause a database for maintenance (blocks new queries):
PAUSE storeforge_dev;
-- Do maintenance work...
RESUME storeforge_dev;

-- Force close all server connections for a pool:
RECONNECT storeforge_dev;
```

---

## Pool Size Calculation

```
Rule of thumb:
ideal_pool_size = num_cores * 2 + num_spinning_disks

For a 4-core server with NVMe SSD:
ideal_pool_size ≈ 4 * 2 + 0 = 8–16 server connections

Max client connections a single PostgreSQL can handle efficiently:
max_connections ≈ 100–200 (e.g., 200 for a 16GB machine)

With pooling:
max_client_conn = 1000   # PgBouncer accepts 1000 app connections
default_pool_size = 20   # only 20 hit PostgreSQL at a time
```

```sql
-- Validate PostgreSQL can handle your pool size:
SELECT name, setting, unit
FROM pg_settings
WHERE name IN ('max_connections', 'reserved_connections', 'superuser_reserved_connections');

-- If max_connections = 200, reserve 5 for superuser, leave ~195 for pooler:
-- default_pool_size * num_databases * num_users should stay under 195.
```

---

## Application-Side Best Practices

```sql
-- Use short transactions — server connection is held for the entire txn in transaction mode:
BEGIN;
UPDATE "order" SET status = 'shipped' WHERE id = 1234;
-- Don't do anything slow here (external HTTP calls, sleep, etc.)
COMMIT;

-- Avoid SET outside transactions in transaction-mode pooling:
-- BAD (SET is lost when connection returns to pool):
SET app.customer_id = '42';
SELECT * FROM "order";  -- different server connection might be used!

-- GOOD: use SET LOCAL inside an explicit transaction:
BEGIN;
SET LOCAL app.customer_id = '42';
SELECT * FROM "order" WHERE customer_id = current_setting('app.customer_id')::int;
COMMIT;

-- Avoid LISTEN/NOTIFY in transaction mode (LISTEN is session-scoped):
-- Use session mode for pubsub consumers.
```

---

## Try It Yourself

```sql
-- 1. Check how many idle connections StoreForge currently has:
SELECT state, count(*) FROM pg_stat_activity
WHERE datname = 'storeforge_dev'
GROUP BY state;

-- 2. Calculate the ideal pool_size for a 2-core NVMe server.
--    With max_connections = 100, how many max_client_conn can you safely configure?

-- 3. The application currently runs this code pattern. What problem does it cause
--    in transaction-mode pooling?
--    SET app.user_id = '99';
--    SELECT * FROM customer WHERE id = current_setting('app.user_id')::int;
--    How would you fix it?

-- 4. In the PgBouncer admin console: which SHOW command would alert you that
--    clients are queueing for a connection? What metric would you monitor?
```

<details>
<summary>Show solutions</summary>

```sql
-- 1. Idle connection count:
SELECT state, count(*)
FROM pg_stat_activity
WHERE datname = 'storeforge_dev'
GROUP BY state
ORDER BY count DESC;
-- 'idle' count = connections open but not working — overhead.

-- 2. Pool size calculation:
-- 2-core NVMe: ideal_pool_size = 2 * 2 = 4-8 server connections
-- With max_connections = 100, reserve 5 for superuser:
-- Available for pooler = 95
-- Safely configure: default_pool_size = 10, max_client_conn = 500
-- (10 server connections per pool * multiple databases still leaves room)

-- 3. Problem and fix:
-- Problem: In transaction mode, the server connection is released after the query.
-- The next statement may be assigned a DIFFERENT server connection where the SET
-- is no longer in effect. RLS policies using current_setting() silently return NULL.

-- Fix: wrap in a transaction and use SET LOCAL:
BEGIN;
SET LOCAL app.user_id = '99';
SELECT * FROM customer WHERE id = current_setting('app.user_id')::int;
COMMIT;

-- 4. PgBouncer monitoring for queue buildup:
-- Command: SHOW POOLS;
-- Metric: cl_waiting > 0 means clients are queuing.
-- Alert threshold: cl_waiting > 0 consistently, or maxwait > 1 second.
-- Action: increase default_pool_size or optimize slow queries holding connections.
```

</details>

---

## Capstone Connection

StoreForge connection pooling setup:
- **PgBouncer** deployed as a sidecar to the API service (same host, port 6432)
- **Transaction mode** — the Spring Boot app uses HikariCP with pool size 10; each HikariCP connection maps to a PgBouncer client connection, and PgBouncer keeps only 20 server connections open at all times
- **`DISCARD ALL`** set as `server_reset_query` to clear prepared statements, advisory locks, and session state when a server connection is returned to the pool
- **Monitoring**: `cl_waiting` and `maxwait` from `SHOW POOLS` exported to Prometheus every 30 seconds; alert fires if `maxwait > 2s`
- **RLS pattern**: all `SET LOCAL app.customer_id` calls are inside explicit transactions, ensuring session variables are scoped correctly in transaction mode
