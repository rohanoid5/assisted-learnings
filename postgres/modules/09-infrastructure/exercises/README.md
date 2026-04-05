# Module 09 Exercises — Infrastructure

## Setup

Connect to your StoreForge database:
```bash
psql -h localhost -U storeforge -d storeforge_dev
```

You will also need a second database for replication exercises:
```bash
createdb -h localhost -U storeforge storeforge_replica
```

---

## Exercise 1 — Backup and Restore

**Goal:** Practice pg_dump and selective pg_restore.

```bash
# Part A: Create a full backup

# 1. Create a custom format backup of storeforge_dev:
pg_dump -h localhost -U storeforge -d storeforge_dev \
    --format=custom \
    --compress=9 \
    --file=/tmp/storeforge_backup.dump

# 2. List the contents of the backup:
pg_restore --list /tmp/storeforge_backup.dump | head -30

# 3. Confirm the backup file was created and note its size:
ls -lh /tmp/storeforge_backup.dump

# Part B: Selective restore

# 4. Restore only the product and category tables to storeforge_replica:
pg_restore -h localhost -U storeforge \
    --dbname=storeforge_replica \
    --table=category \
    --table=product \
    /tmp/storeforge_backup.dump

# 5. Verify the restore:
psql -h localhost -U storeforge -d storeforge_replica \
    -c "SELECT count(*) FROM product;"

psql -h localhost -U storeforge -d storeforge_replica \
    -c "SELECT count(*) FROM category;"
```

```sql
-- Part C: Schema-only backup workflow
-- 6. Take a schema-only backup and inspect it:
```

```bash
pg_dump -h localhost -U storeforge -d storeforge_dev \
    --schema-only \
    --file=/tmp/storeforge_schema.sql

# Count the DDL statements:
grep -c "^CREATE" /tmp/storeforge_schema.sql
```

<details>
<summary>Show solutions</summary>

```bash
# Part A:
pg_dump -h localhost -U storeforge -d storeforge_dev \
    --format=custom --compress=9 --file=/tmp/storeforge_backup.dump

pg_restore --list /tmp/storeforge_backup.dump | head -30
# Shows: TABLE DATA, INDEX, SEQUENCE, CONSTRAINT entries

ls -lh /tmp/storeforge_backup.dump
# Example: -rw-r--r-- 1 user group 2.4M  storeforge_backup.dump

# Part B:
pg_restore -h localhost -U storeforge \
    --dbname=storeforge_replica \
    --table=category \
    --table=product \
    /tmp/storeforge_backup.dump

psql -h localhost -U storeforge -d storeforge_replica -c "SELECT count(*) FROM product;"
# Should match count in storeforge_dev

psql -h localhost -U storeforge -d storeforge_replica -c "SELECT count(*) FROM category;"

# Part C:
pg_dump -h localhost -U storeforge -d storeforge_dev \
    --schema-only --file=/tmp/storeforge_schema.sql

grep -c "^CREATE" /tmp/storeforge_schema.sql
# Shows how many CREATE TABLE/INDEX/FUNCTION statements exist
head -60 /tmp/storeforge_schema.sql
# Review the DDL output
```

</details>

---

## Exercise 2 — Monitoring Queries

**Goal:** Practice using pg_stat_* views to inspect database health.

```sql
-- 1. Check the connection state breakdown:
SELECT state, count(*), array_agg(pid) AS pids
FROM pg_stat_activity
WHERE datname = 'storeforge_dev'
GROUP BY state;

-- 2. Find the cache hit ratio:
SELECT
    datname,
    blks_hit,
    blks_read,
    ROUND(100.0 * blks_hit / NULLIF(blks_hit + blks_read, 0), 2) AS cache_hit_pct
FROM pg_stat_database
WHERE datname = 'storeforge_dev';

-- 3. List tables with their seq_scan count and idx_scan count.
--    Identify which table (if any) has a suspiciously high seq_scan_pct:
SELECT relname, seq_scan, idx_scan,
       COALESCE(ROUND(100.0 * seq_scan / NULLIF(seq_scan + idx_scan, 0), 1), 0) AS seq_scan_pct
FROM pg_stat_user_tables
ORDER BY seq_scan DESC;

-- 4. Find the largest tables and their index overhead:
SELECT
    relname AS table,
    pg_size_pretty(pg_table_size(relid)) AS data_size,
    pg_size_pretty(pg_indexes_size(relid)) AS index_size,
    pg_size_pretty(pg_total_relation_size(relid)) AS total_size,
    ROUND(100.0 * pg_indexes_size(relid) / NULLIF(pg_total_relation_size(relid), 0), 1)
        AS index_overhead_pct
FROM pg_stat_user_tables
ORDER BY pg_total_relation_size(relid) DESC
LIMIT 5;

-- 5. Simulate a blocked query and detect it:
--    Terminal 1: BEGIN; UPDATE product SET price = 999 WHERE id = 1;  (don't commit)
--    Terminal 2: UPDATE product SET price = 888 WHERE id = 1;  (this will block)
--    Terminal 3: Run this detection query:
SELECT
    blocked.pid AS blocked_pid,
    LEFT(blocked.query, 60) AS blocked_query,
    blocking.pid AS blocking_pid,
    LEFT(blocking.query, 60) AS blocking_query
FROM pg_stat_activity blocked
JOIN pg_stat_activity blocking
    ON blocking.pid = ANY(pg_blocking_pids(blocked.pid))
WHERE blocked.wait_event_type = 'Lock';

--    Terminal 1: ROLLBACK;  (unblocks Terminal 2)
```

<details>
<summary>Show solutions</summary>

```sql
-- 1. Connection states:
SELECT state, count(*)
FROM pg_stat_activity
WHERE datname = 'storeforge_dev'
GROUP BY state ORDER BY count DESC;
-- Expected: mostly 'idle' in a dev environment, 'active' during queries.

-- 2. Cache hit ratio:
SELECT datname,
       ROUND(100.0 * blks_hit / NULLIF(blks_hit + blks_read, 0), 2) AS cache_hit_pct
FROM pg_stat_database WHERE datname = 'storeforge_dev';
-- Fresh dev database may show low hit ratio (cold cache).
-- Hit production data repeatedly and re-check — should climb toward 99%.

-- 3. Seq scan analysis:
SELECT relname, seq_scan, idx_scan,
       COALESCE(ROUND(100.0 * seq_scan / NULLIF(seq_scan + idx_scan, 0), 1), 0) AS seq_scan_pct
FROM pg_stat_user_tables ORDER BY seq_scan DESC;
-- Small tables (category) will show high seq_scan_pct — this is normal.
-- Large tables (order_item, product) should show low seq_scan_pct if indexed.

-- 4. Table size with index overhead:
SELECT relname,
       pg_size_pretty(pg_table_size(relid)) AS data_size,
       pg_size_pretty(pg_indexes_size(relid)) AS index_size,
       pg_size_pretty(pg_total_relation_size(relid)) AS total_size,
       ROUND(100.0 * pg_indexes_size(relid) / NULLIF(pg_total_relation_size(relid), 0), 1) AS idx_pct
FROM pg_stat_user_tables
ORDER BY pg_total_relation_size(relid) DESC LIMIT 5;

-- 5. Block detection (run after starting Terminal 1 and Terminal 2):
SELECT
    blocked.pid,
    LEFT(blocked.query, 60) AS blocked_query,
    blocking.pid AS blocking_pid
FROM pg_stat_activity blocked
JOIN pg_stat_activity blocking
    ON blocking.pid = ANY(pg_blocking_pids(blocked.pid))
WHERE blocked.wait_event_type = 'Lock';
-- Should show Terminal 2 blocked by Terminal 1.
```

</details>

---

## Exercise 3 — Replication Awareness

**Goal:** Understand replication configuration and monitoring queries (no second server required).

```sql
-- 1. Check your current replication-related settings:
SELECT name, setting, unit, context
FROM pg_settings
WHERE name IN (
    'wal_level', 'max_wal_senders', 'wal_keep_size',
    'hot_standby', 'synchronous_commit', 'synchronous_standby_names',
    'archive_mode', 'archive_command'
)
ORDER BY name;

-- 2. What wal_level is required for each of these scenarios?
--    a) Streaming replication only
--    b) Logical replication / pg_logical
--    c) Minimal WAL (no replication possible, fastest inserts)
--    Check: SELECT unnest(enum_range(NULL::text)) isn't applicable here —
--    instead look at pg_settings where name = 'wal_level'.

-- 3. Create a replication user (won't connect without a real standby, but validates DDL):
CREATE ROLE replicator WITH REPLICATION LOGIN PASSWORD 'replsecret';
-- Verify:
SELECT rolname, rolreplication, rolcanlogin FROM pg_roles WHERE rolname = 'replicator';

-- 4. Check pg_stat_replication (will be empty without a standby, but validates understanding):
SELECT count(*) AS connected_standbys FROM pg_stat_replication;

-- 5. Create a logical publication and verify it:
ALTER TABLE product REPLICA IDENTITY DEFAULT;
ALTER TABLE "order" REPLICA IDENTITY DEFAULT;
CREATE PUBLICATION storeforge_pub FOR TABLE product, "order", order_item, review;

SELECT pubname, puballtables, pubinsert, pubupdate, pubdelete
FROM pg_publication WHERE pubname = 'storeforge_pub';

SELECT tablename FROM pg_publication_tables WHERE pubname = 'storeforge_pub';

-- 6. Clean up:
DROP PUBLICATION storeforge_pub;
DROP ROLE replicator;
```

<details>
<summary>Show solutions</summary>

```sql
-- 1. Replication settings:
SELECT name, setting, context FROM pg_settings
WHERE name IN ('wal_level', 'max_wal_senders', 'wal_keep_size',
               'hot_standby', 'synchronous_commit')
ORDER BY name;
-- wal_level = 'replica' is the default in PG 13+
-- max_wal_senders = 10 by default (allows up to 10 standbys)
-- context = 'postmaster' means restart required for that setting.

-- 2. wal_level requirements:
-- a) Streaming replication: wal_level = 'replica' (or 'logical')
-- b) Logical replication: wal_level = 'logical' (superset of replica)
-- c) Minimal: wal_level = 'minimal' — smallest WAL, no replication possible

-- 3. Create replication role:
CREATE ROLE replicator WITH REPLICATION LOGIN PASSWORD 'replsecret';
SELECT rolname, rolreplication, rolcanlogin FROM pg_roles WHERE rolname = 'replicator';
-- rolreplication: true  rolcanlogin: true

-- 4. Empty pg_stat_replication is expected without a standby.

-- 5. Publication creation:
ALTER TABLE product REPLICA IDENTITY DEFAULT;
ALTER TABLE "order" REPLICA IDENTITY DEFAULT;
CREATE PUBLICATION storeforge_pub FOR TABLE product, "order", order_item, review;

SELECT pubname, pubinsert, pubupdate, pubdelete
FROM pg_publication WHERE pubname = 'storeforge_pub';
-- All should be: true

SELECT tablename FROM pg_publication_tables WHERE pubname = 'storeforge_pub';
-- 4 rows: product, order, order_item, review

-- 6. Cleanup:
DROP PUBLICATION storeforge_pub;
DROP ROLE replicator;
```

</details>

---

## Capstone Checkpoint ✅

After completing all exercises, verify your StoreForge infrastructure readiness:

- [ ] Full custom-format backup created with `pg_dump --format=custom`
- [ ] Selective table restore verified (`product` + `category` into `storeforge_replica`)
- [ ] `pg_stat_database` cache hit ratio checked and understood
- [ ] `pg_stat_user_tables` shows no unexpectedly high `seq_scan` on large tables
- [ ] Lock blocking simulation performed and blocked query detected via `pg_stat_activity`
- [ ] Replication user created with `REPLICATION` attribute
- [ ] Logical publication created with `REPLICA IDENTITY DEFAULT` set on key tables
- [ ] `pg_stat_replication` and `pg_replication_slots` views understood even without a live standby
