# Monitoring PostgreSQL

## Concept

A PostgreSQL server reports on itself through a rich set of **statistics views** — tables prefixed with `pg_stat_` that are updated in real time. These views let you observe everything: which queries are running right now, how often each index is used, how many dead rows exist in each table, whether replication is lagging, and whether locks are blocking transactions. Pairing these views with a metrics exporter (like **postgres_exporter**) and a visualization layer (Grafana) turns this raw data into actionable dashboards and alerts.

---

## Essential pg_stat_* Views

### Active Queries and Locks

```sql
-- What is running right now?
SELECT
    pid,
    now() - query_start AS duration,
    state,
    wait_event_type,
    wait_event,
    LEFT(query, 80) AS query
FROM pg_stat_activity
WHERE state != 'idle'
  AND pid != pg_backend_pid()
ORDER BY duration DESC;

-- Long-running queries (> 30 seconds):
SELECT pid, now() - query_start AS duration, state, LEFT(query, 80) AS query
FROM pg_stat_activity
WHERE state != 'idle'
  AND query_start < NOW() - INTERVAL '30 seconds'
ORDER BY duration DESC;

-- Blocked queries (waiting for a lock):
SELECT
    blocked.pid AS blocked_pid,
    blocked.query AS blocked_query,
    blocking.pid AS blocking_pid,
    blocking.query AS blocking_query
FROM pg_stat_activity blocked
JOIN pg_stat_activity blocking
    ON blocking.pid = ANY(pg_blocking_pids(blocked.pid))
WHERE blocked.wait_event_type = 'Lock';

-- Kill a specific query by PID (non-destructive):
SELECT pg_cancel_backend(12345);

-- Kill and disconnect the connection:
SELECT pg_terminate_backend(12345);
```

---

### Table Statistics

```sql
-- Table access patterns:
SELECT
    relname AS table,
    seq_scan,               -- full table scans
    idx_scan,               -- index scans
    n_tup_ins AS inserts,
    n_tup_upd AS updates,
    n_tup_del AS deletes,
    n_live_tup,
    n_dead_tup,
    ROUND(100.0 * n_dead_tup / NULLIF(n_live_tup + n_dead_tup, 0), 1) AS bloat_pct,
    last_autovacuum,
    last_autoanalyze
FROM pg_stat_user_tables
ORDER BY seq_scan DESC;

-- Tables with high seq_scan ratio (potential missing indexes):
SELECT relname, seq_scan, idx_scan,
       ROUND(100.0 * seq_scan / NULLIF(seq_scan + idx_scan, 0), 1) AS seq_scan_pct
FROM pg_stat_user_tables
WHERE seq_scan + idx_scan > 100
ORDER BY seq_scan_pct DESC;
```

---

### Index Statistics

```sql
-- Index usage:
SELECT
    t.relname AS table,
    i.indexrelname AS index,
    i.idx_scan AS scans,
    pg_size_pretty(pg_relation_size(i.indexrelid)) AS index_size
FROM pg_stat_user_indexes i
JOIN pg_stat_user_tables t ON t.relid = i.relid
ORDER BY i.idx_scan DESC;

-- Unused indexes (never or rarely scanned) — candidates for removal:
SELECT schemaname, tablename, indexname, idx_scan,
       pg_size_pretty(pg_relation_size(indexrelid)) AS waste
FROM pg_stat_user_indexes
WHERE idx_scan < 5
ORDER BY pg_relation_size(indexrelid) DESC;
```

---

### Database-Level Statistics

```sql
-- Cache hit ratio (should be > 99% for a well-tuned server):
SELECT
    datname,
    blks_hit,
    blks_read,
    ROUND(100.0 * blks_hit / NULLIF(blks_hit + blks_read, 0), 2) AS cache_hit_pct,
    xact_commit,
    xact_rollback,
    conflicts,
    deadlocks,
    temp_files,
    pg_size_pretty(temp_bytes) AS temp_disk_used
FROM pg_stat_database
WHERE datname = 'storeforge_dev';

-- WAL statistics (PG 14+):
SELECT * FROM pg_stat_wal;
-- wal_records, wal_bytes, wal_buffers_full, wal_write_time
```

---

### Replication and Checkpoint Stats

```sql
-- Replication lag (run on primary):
SELECT application_name, state, sent_lsn, replay_lsn,
       write_lag, flush_lag, replay_lag, sync_state
FROM pg_stat_replication;

-- Checkpoint frequency:
SELECT checkpoints_timed, checkpoints_req,
       checkpoint_write_time, checkpoint_sync_time,
       buffers_checkpoint, buffers_clean, buffers_backend
FROM pg_stat_bgwriter;
-- checkpoints_req >> checkpoints_timed: checkpoint happening too often
-- Increase max_wal_size if this is the case.
```

---

### Size and Storage

```sql
-- Database sizes:
SELECT datname, pg_size_pretty(pg_database_size(datname)) AS size
FROM pg_database ORDER BY pg_database_size(datname) DESC;

-- Table sizes with indexes:
SELECT
    relname AS table,
    pg_size_pretty(pg_table_size(relid)) AS table_size,
    pg_size_pretty(pg_indexes_size(relid)) AS index_size,
    pg_size_pretty(pg_total_relation_size(relid)) AS total_size
FROM pg_stat_user_tables
ORDER BY pg_total_relation_size(relid) DESC;

-- Largest tables in the database:
SELECT relname,
       pg_size_pretty(pg_total_relation_size(oid)) AS total_size
FROM pg_class
WHERE relkind = 'r'
  AND relnamespace = (SELECT oid FROM pg_namespace WHERE nspname = 'public')
ORDER BY pg_total_relation_size(oid) DESC
LIMIT 10;
```

---

## postgres_exporter (Prometheus)

```bash
# Install and run postgres_exporter:
# https://github.com/prometheus-community/postgres_exporter

export DATA_SOURCE_NAME="postgresql://storeforge:secret@localhost:5432/storeforge_dev?sslmode=disable"
./postgres_exporter --web.listen-address=":9187"

# Key metrics exposed:
# pg_stat_activity_count{state}         — connections by state
# pg_stat_user_tables_seq_scan          — full table scans per table
# pg_stat_user_tables_n_dead_tup        — dead rows per table
# pg_database_size_bytes                — database size
# pg_stat_replication_pg_wal_lsn_diff   — replication lag in bytes
# pg_stat_bgwriter_checkpoints_req_total — forced checkpoints
```

```yaml
# prometheus.yml scrape config:
scrape_configs:
  - job_name: postgres
    static_configs:
      - targets: ['localhost:9187']
    scrape_interval: 15s
```

---

## Key Alerting Rules

```yaml
# prometheus_alerts.yml — example Prometheus alert rules:
groups:
  - name: postgres
    rules:
      - alert: PostgresReplicationLag
        expr: pg_stat_replication_pg_wal_lsn_diff > 52428800  # 50MB
        for: 2m
        labels:
          severity: warning
        annotations:
          summary: "Replication lag > 50MB"

      - alert: PostgresDeadTuplesHigh
        expr: pg_stat_user_tables_n_dead_tup > 100000
        for: 10m
        labels:
          severity: warning
        annotations:
          summary: "High dead row count — autovacuum may be struggling"

      - alert: PostgresCacheHitLow
        expr: |
          rate(pg_stat_database_blks_hit[5m]) /
          (rate(pg_stat_database_blks_hit[5m]) + rate(pg_stat_database_blks_read[5m])) < 0.95
        for: 5m
        labels:
          severity: critical
        annotations:
          summary: "Cache hit ratio below 95% — increase shared_buffers"

      - alert: PostgresDeadlocks
        expr: increase(pg_stat_database_deadlocks_total[5m]) > 0
        labels:
          severity: warning
        annotations:
          summary: "Deadlocks detected in the last 5 minutes"

      - alert: PostgresConnectionsNearLimit
        expr: |
          (pg_stat_activity_count / pg_settings_max_connections) > 0.8
        for: 2m
        labels:
          severity: critical
        annotations:
          summary: "PostgreSQL connections exceeding 80% of max_connections"
```

---

## Try It Yourself

```sql
-- 1. Find the top 5 tables by total size in your storeforge_dev database.

-- 2. Calculate the cache hit ratio for storeforge_dev.
--    What does a ratio below 90% indicate?

-- 3. Run this query to simulate a slow operation, then catch it with pg_stat_activity:
--    In one connection: SELECT pg_sleep(30);
--    In another connection immediately: find it via pg_stat_activity.
--    Then cancel it using pg_cancel_backend().

-- 4. Identify any tables with high seq_scan counts relative to idx_scan.
--    What does a high seq_scan percentage suggest?
```

<details>
<summary>Show solutions</summary>

```sql
-- 1. Top tables by total size:
SELECT relname AS table,
       pg_size_pretty(pg_total_relation_size(relid)) AS total_size,
       pg_size_pretty(pg_table_size(relid)) AS data_size,
       pg_size_pretty(pg_indexes_size(relid)) AS index_size
FROM pg_stat_user_tables
ORDER BY pg_total_relation_size(relid) DESC
LIMIT 5;

-- 2. Cache hit ratio:
SELECT
    ROUND(100.0 * blks_hit / NULLIF(blks_hit + blks_read, 0), 2) AS cache_hit_pct
FROM pg_stat_database
WHERE datname = 'storeforge_dev';
-- < 90%: too much disk I/O — increase shared_buffers or add RAM.
-- > 99%: healthy — most reads served from memory.

-- 3. Slow query detection:
-- Terminal 1:
SELECT pg_sleep(30);  -- leaves this running

-- Terminal 2:
SELECT pid, now() - query_start AS duration, state, query
FROM pg_stat_activity
WHERE query LIKE '%sleep%' AND state = 'active';

-- Cancel it (graceful):
SELECT pg_cancel_backend(<pid_from_above>);

-- If cancel doesn't work (stuck in wait event):
SELECT pg_terminate_backend(<pid>);

-- 4. High seq_scan analysis:
SELECT relname, seq_scan, idx_scan,
       ROUND(100.0 * seq_scan / NULLIF(seq_scan + idx_scan, 0), 1) AS seq_scan_pct
FROM pg_stat_user_tables
WHERE seq_scan + idx_scan > 0
ORDER BY seq_scan_pct DESC;

-- High seq_scan_pct suggests:
-- a) The table is small (seq scan is fine for tables < 1000 rows)
-- b) A missing index on a frequently queried column (for larger tables)
-- c) Queries that fetch a large % of the table (index not useful anyway)
-- Use EXPLAIN ANALYZE to distinguish cases.
```

</details>

---

## Capstone Connection

StoreForge monitoring stack:
- **postgres_exporter** on each PostgreSQL node, scraped every 15 seconds by Prometheus
- **Grafana dashboard** shows: cache hit %, queries/sec, replication lag, top 10 slowest query fingerprints, deadlock rate, table bloat %, connection pool utilization
- **PagerDuty alerts** fire on: replication lag > 50MB, connection saturation > 80%, cache hit < 95%, deadlocks > 0 in any 5-minute window, disk usage > 80%
- **Weekly DBA review**: `pg_stat_user_tables` for bloat trends, `pg_stat_user_indexes` for unused indexes, `pg_stat_statements` for newly slow queries
