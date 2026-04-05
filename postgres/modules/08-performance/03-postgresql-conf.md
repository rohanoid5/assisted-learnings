# postgresql.conf Tuning

## Concept

PostgreSQL ships with conservative defaults suitable for running on a 256 MB virtual machine. A real workload — even a modest one — benefits enormously from tuning five or six key settings. This lesson covers the most impactful parameters, how to read and apply them, and how to use `pg_settings` to understand the current configuration without editing files directly.

---

## Configuration Hierarchy

```sql
-- Settings can be applied at multiple levels (highest precedence wins):
-- 1. postgresql.conf (server-wide default)
-- 2. ALTER DATABASE SET ... (overrides for a specific database)
-- 3. ALTER ROLE SET ...     (overrides for a specific role)
-- 4. SET ... (session-level, lasts until end of session or RESET)
-- 5. SET LOCAL ... (transaction-level, reverts on COMMIT/ROLLBACK)

-- Inspect current effective value for all key params:
SELECT name, setting, unit, context, short_desc
FROM pg_settings
WHERE name IN (
    'shared_buffers', 'work_mem', 'maintenance_work_mem',
    'effective_cache_size', 'wal_buffers', 'max_connections',
    'autovacuum', 'checkpoint_completion_target',
    'default_statistics_target', 'random_page_cost', 'effective_io_concurrency'
)
ORDER BY name;

-- Find the config file location:
SHOW config_file;

-- Reload non-restart settings:
SELECT pg_reload_conf();

-- Check if a setting requires restart:
SELECT name, context FROM pg_settings
WHERE context = 'postmaster'  -- requires restart
ORDER BY name;
```

---

## Memory Settings

```sql
-- shared_buffers: PostgreSQL's shared page cache (the most important setting).
-- Rule of thumb: 25% of total RAM. On a 16 GB server: 4 GB.
-- Context: postmaster (restart required)
-- postgresql.conf:
-- shared_buffers = 4GB

-- work_mem: per-sort / per-hash-table allocation.
-- Each sort/hash node in a query can use this much. One complex query
-- can use many multiples of work_mem simultaneously.
-- Rule of thumb: (RAM - shared_buffers) / (max_connections * 2)
-- On 16 GB, 100 connections: (12 GB) / 200 = ~60 MB. Start with 16–64 MB.
-- Context: user (changeable per session / per role)
-- postgresql.conf:
-- work_mem = 32MB

-- Increase work_mem for a specific heavy query:
SET work_mem = '256MB';
SELECT * FROM order_item oi
JOIN "order" o ON o.id = oi.order_id
ORDER BY oi.unit_price DESC;
RESET work_mem;

-- maintenance_work_mem: for VACUUM, CREATE INDEX, ALTER TABLE.
-- Can be much larger; typically 512 MB – 2 GB.
-- postgresql.conf:
-- maintenance_work_mem = 512MB

-- effective_cache_size: tells the planner how much total memory is available
-- for caching (OS page cache + shared_buffers). Does NOT allocate memory.
-- Rule of thumb: 50-75% of total RAM.
-- postgresql.conf:
-- effective_cache_size = 12GB
```

---

## WAL and Checkpoint Settings

```sql
-- wal_buffers: write-ahead log buffer. Default is 1/32 of shared_buffers (max 16 MB).
-- Usually fine at 16 MB unless you have very high write throughput.
-- postgresql.conf: wal_buffers = 16MB

-- checkpoint_completion_target: spreads checkpoint I/O over the interval.
-- Higher = smoother I/O (less spike), at cost of slightly more WAL generated.
-- postgresql.conf: checkpoint_completion_target = 0.9

-- min_wal_size / max_wal_size: controls WAL segment reuse.
-- postgresql.conf:
-- min_wal_size = 256MB
-- max_wal_size = 2GB

-- synchronous_commit: controls durability vs latency trade-off.
-- 'on'  (default): commit returns only after WAL is flushed to disk — full durability
-- 'off' : commit returns when WAL is in memory — up to 3x write throughput, but if
--         the server crashes, the last ~200ms of transactions are lost (no corruption,
--         just a rollback to consistent state).
-- Use 'off' only for non-critical data (analytics, event logs).
ALTER ROLE reporting_svc SET synchronous_commit = 'off';
```

---

## Planner Cost Settings

```sql
-- random_page_cost: estimated cost of a random page read.
-- Default: 4 (calibrated for spinning disk).
-- For SSD: set 1.1 – 2.0. This tells the planner indexes are cheaper.
-- postgresql.conf: random_page_cost = 1.1

-- seq_page_cost: estimated cost of a sequential page read (always 1.0 — baseline).

-- effective_io_concurrency: how many disk reads the OS can process simultaneously.
-- For SSD: 200. For NVMe: 400+.
-- postgresql.conf: effective_io_concurrency = 200

-- default_statistics_target: number of histogram buckets for column statistics.
-- Higher = better estimates for skewed distributions, but slower ANALYZE.
-- Default: 100. For important filtered columns: ALTER COLUMN SET STATISTICS.
-- postgresql.conf: default_statistics_target = 100

-- Increase statistics for a specific column with skewed data:
ALTER TABLE "order" ALTER COLUMN status SET STATISTICS 500;
ANALYZE "order";
```

---

## Connection Settings

```sql
-- max_connections: total allowed connections.
-- Default: 100. Each connection costs ~5-10 MB shared memory.
-- For most apps: use PgBouncer (Module 08-07) and keep max_connections low (100-200).
-- postgresql.conf: max_connections = 100

-- Authentication timeout:
-- postgresql.conf: authentication_timeout = 1min

-- Idle connection timeout (PostgreSQL 14+):
-- postgresql.conf: idle_in_transaction_session_timeout = 30s
-- Kills sessions that have been idle within a transaction for over 30 seconds.
-- Critical for preventing lock pile-ups.

-- Statement timeout (for runaway queries):
ALTER ROLE storeforge_api SET statement_timeout = '30s';

-- Per-session override:
SET statement_timeout = '5min';
SELECT ... -- long analytical query ...
RESET statement_timeout;
```

---

## Logging Settings

```sql
-- Enable slow query logging:
-- postgresql.conf:
-- log_min_duration_statement = 1000   -- log queries taking > 1 second
-- log_line_prefix = '%t [%p] %u@%d '  -- timestamp, PID, user, database
-- log_statement = 'none'              -- don't log all statements (too verbose)
-- log_checkpoints = on               -- log checkpoint activity
-- log_lock_waits = on                -- log lock waits > deadlock_timeout

-- View current log settings:
SELECT name, setting FROM pg_settings
WHERE name LIKE 'log_%'
ORDER BY name;
```

---

## Applying Changes

```sql
-- Apply setting for a specific role (no restart needed):
ALTER ROLE api_service SET work_mem = '64MB';
ALTER ROLE api_service SET statement_timeout = '30s';
ALTER ROLE api_service SET idle_in_transaction_session_timeout = '30s';

-- Apply setting for a specific database:
ALTER DATABASE storeforge_dev SET default_statistics_target = 200;

-- Verify the role's settings:
SELECT rolname, rolconfig
FROM pg_roles
WHERE rolname = 'api_service';
```

---

## Try It Yourself

```sql
-- 1. Query pg_settings for the five memory parameters:
--    shared_buffers, work_mem, maintenance_work_mem, effective_cache_size, wal_buffers.
--    Note their current values and units.

-- 2. Set random_page_cost = 1.1 for your session (simulating SSD).
--    Then run EXPLAIN on a query that uses a Seq Scan.
--    Does the planner switch to an Index Scan?

-- 3. Increase statistics for "order".status to 500. Run ANALYZE product.
--    Re-run EXPLAIN on a query filtering by status. Do estimates improve?

-- 4. Set statement_timeout = '100ms' for your session.
--    Try running a query that takes longer than that.
--    Observe the ERROR: canceling statement due to statement timeout.
```

<details>
<summary>Show solutions</summary>

```sql
-- 1. Memory parameters:
SELECT name, setting, unit
FROM pg_settings
WHERE name IN (
    'shared_buffers', 'work_mem', 'maintenance_work_mem',
    'effective_cache_size', 'wal_buffers'
)
ORDER BY name;

-- 2. SSD random_page_cost:
SET random_page_cost = 1.1;
EXPLAIN SELECT * FROM product WHERE category_id = 3;
-- May switch from Seq Scan to Index Scan depending on table size
RESET random_page_cost;

-- 3. Statistics:
ALTER TABLE "order" ALTER COLUMN status SET STATISTICS 500;
ANALYZE "order";

EXPLAIN SELECT customer_id, COUNT(*)
FROM "order" WHERE status = 'delivered'
GROUP BY customer_id;
-- Planner estimates for status = 'delivered' should be more accurate

-- 4. statement_timeout:
SET statement_timeout = '100ms';
SELECT pg_sleep(1);  -- should timeout
-- ERROR:  canceling statement due to statement timeout
RESET statement_timeout;
```

</details>

---

## Capstone Connection

StoreForge's recommended `postgresql.conf` values (16 GB RAM, NVMe SSD server):

```ini
# Memory
shared_buffers            = 4GB
work_mem                  = 32MB
maintenance_work_mem      = 1GB
effective_cache_size      = 12GB

# WAL
wal_buffers               = 16MB
checkpoint_completion_target = 0.9
min_wal_size              = 256MB
max_wal_size              = 2GB

# Planner (SSD/NVMe)
random_page_cost          = 1.1
effective_io_concurrency  = 400
default_statistics_target = 200

# Connections (using PgBouncer in front)
max_connections           = 100

# Logging
log_min_duration_statement = 500
log_checkpoints           = on
log_lock_waits            = on

# Safety
idle_in_transaction_session_timeout = 30s
```
