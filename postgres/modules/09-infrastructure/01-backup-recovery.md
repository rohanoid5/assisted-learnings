# Backup and Recovery

## Concept

Backups are not optional — they are the foundation of every production database. PostgreSQL offers two complementary strategies: **logical backups** (`pg_dump` / `pg_restore`) export data as SQL or a custom binary format, suitable for selective restores and migration between versions. **Physical backups** (`pg_basebackup` + WAL archiving) copy the raw data files and transaction logs, enabling **Point-In-Time Recovery (PITR)** — restoring the database to any moment in time, even between full backups.

---

## pg_dump — Logical Backups

```bash
# Custom format (recommended): compressed, supports parallel restore, table selection:
pg_dump -h localhost -U storeforge -d storeforge_dev \
    --format=custom \
    --compress=9 \
    --file=storeforge_$(date +%Y%m%d_%H%M%S).dump

# Plain SQL format (human-readable, but no parallel restore):
pg_dump -h localhost -U storeforge -d storeforge_dev \
    --format=plain \
    --file=storeforge.sql

# Dump a single table:
pg_dump -h localhost -U storeforge -d storeforge_dev \
    --table=product \
    --format=custom \
    --file=product_backup.dump

# Dump schema only (no data):
pg_dump -h localhost -U storeforge -d storeforge_dev \
    --schema-only \
    --file=schema.sql

# Dump data only (no DDL):
pg_dump -h localhost -U storeforge -d storeforge_dev \
    --data-only \
    --file=seed_data.sql

# Dump with explicit schema:
pg_dump -h localhost -U storeforge -d storeforge_dev \
    --schema=public \
    --format=custom \
    --file=public_schema.dump

# Parallel dump (splits into multiple files in a directory):
pg_dump -h localhost -U storeforge -d storeforge_dev \
    --format=directory \
    --jobs=4 \
    --file=storeforge_dump_dir/
```

---

## pg_restore — Restoring Logical Backups

```bash
# Restore from custom format to an existing (empty) database:
createdb -h localhost -U storeforge storeforge_restore

pg_restore -h localhost -U storeforge \
    --dbname=storeforge_restore \
    --verbose \
    storeforge_20240115.dump

# Parallel restore (much faster for large databases):
pg_restore -h localhost -U storeforge \
    --dbname=storeforge_restore \
    --jobs=4 \
    storeforge_dump_dir/

# Restore a single table:
pg_restore -h localhost -U storeforge \
    --dbname=storeforge_restore \
    --table=product \
    storeforge_20240115.dump

# Restore schema only (no data):
pg_restore -h localhost -U storeforge \
    --dbname=storeforge_restore \
    --schema-only \
    storeforge_20240115.dump

# List contents of a dump (without restoring):
pg_restore --list storeforge_20240115.dump | head -30
```

---

## pg_dumpall — Cluster-Level Backup

```bash
# Dump all databases + global objects (roles, tablespaces):
pg_dumpall -h localhost -U postgres \
    --file=cluster_backup.sql

# Restore (must be superuser):
psql -h localhost -U postgres < cluster_backup.sql

# Dump only global objects (roles + tablespaces, no table data):
pg_dumpall -h localhost -U postgres \
    --globals-only \
    --file=globals.sql
```

---

## WAL Archiving

```sql
-- Check current WAL settings:
SHOW wal_level;      -- should be 'replica' for streaming replication / PITR
SHOW archive_mode;   -- 'on' when WAL archiving is active
SHOW archive_command;
-- archive_command copies each WAL segment to your archive storage.

-- View current WAL write location:
SELECT pg_current_wal_lsn();

-- Force a WAL segment switch (useful to test your archive_command):
SELECT pg_switch_wal();

-- View WAL segments in pg_wal directory:
-- ls -lh $PGDATA/pg_wal/
```

```ini
# postgresql.conf settings for WAL archiving:
wal_level = replica
archive_mode = on
archive_command = 'aws s3 cp %p s3://storeforge-wal-archive/%f'
# %p = absolute path of WAL segment file
# %f = filename only

# Or a simple local copy for testing:
# archive_command = 'cp %p /var/lib/postgresql/wal_archive/%f'
```

---

## pg_basebackup — Physical Backup

```bash
# Full base backup (starting point for PITR):
pg_basebackup -h localhost -U replicator \
    --pgdata=/backups/storeforge_base \
    --format=tar \
    --gzip \
    --checkpoint=fast \
    --progress \
    --wal-method=stream   # streams WAL generated during backup

# Verify the backup:
ls -lh /backups/storeforge_base/
# base.tar.gz  pg_wal.tar.gz
```

---

## Point-In-Time Recovery (PITR)

PITR lets you restore to any LSN (Log Sequence Number) or timestamp between backups:

```bash
# 1. Stop PostgreSQL (if running):
pg_ctl stop -D $PGDATA

# 2. Restore the base backup to a new data directory:
rm -rf /var/lib/postgresql/restore_point
mkdir /var/lib/postgresql/restore_point
tar -xzf /backups/storeforge_base/base.tar.gz -C /var/lib/postgresql/restore_point

# 3. Create recovery configuration (PG 12+ uses recovery.conf inside postgresql.conf):
cat >> /var/lib/postgresql/restore_point/postgresql.conf << 'EOF'
restore_command = 'aws s3 cp s3://storeforge-wal-archive/%f %p'
recovery_target_time = '2024-05-15 14:30:00 UTC'
recovery_target_action = 'promote'
EOF

# 4. Create standby.signal to enter recovery mode:
touch /var/lib/postgresql/restore_point/standby.signal

# 5. Start PostgreSQL — it will replay WAL up to the target time:
pg_ctl start -D /var/lib/postgresql/restore_point

# 6. Once recovered, verify the data, then promote:
# SELECT pg_promote();   -- from psql, or PostgreSQL auto-promotes at target
```

---

## Backup Verification

```sql
-- After restore, verify row counts match expectations:
SELECT relname, n_live_tup
FROM pg_stat_user_tables
ORDER BY n_live_tup DESC;

-- Check that sequences are correct (not reset to 1):
SELECT sequence_name, last_value
FROM information_schema.sequences
JOIN (
    SELECT sequence_name,
           nextval(quote_ident(sequence_name)) - 1 AS last_value
    FROM information_schema.sequences
    WHERE sequence_schema = 'public'
) s USING (sequence_name);

-- Verify data integrity with spot checks:
SELECT count(*) FROM "order";
SELECT max(created_at) FROM "order";   -- should be close to backup time
SELECT count(*) FROM review;
```

---

## Try It Yourself

```sql
-- 1. Create a backup of your storeforge_dev database in custom format.
--    List its contents with pg_restore --list.

-- 2. Create a new database storeforge_test and restore only the product
--    and category tables from your backup.

-- 3. Check your current wal_level and archive_mode settings.
--    What change would be needed to enable WAL archiving?

-- 4. Write the pg_dump command to export only the schema (no data) of
--    storeforge_dev to a file called schema_only.sql.
```

<details>
<summary>Show solutions</summary>

```bash
# 1. Full custom backup + list:
pg_dump -h localhost -U storeforge -d storeforge_dev \
    --format=custom --compress=9 --file=storeforge_backup.dump

pg_restore --list storeforge_backup.dump | head -40

# 2. Selective restore:
createdb -h localhost -U storeforge storeforge_test

pg_restore -h localhost -U storeforge \
    --dbname=storeforge_test \
    --table=category \
    --table=product \
    storeforge_backup.dump

# Verify:
psql -h localhost -U storeforge -d storeforge_test \
    -c "SELECT count(*) FROM product;"
```

```sql
-- 3. Check WAL settings:
SHOW wal_level;     -- 'replica' needed for archiving / streaming replication
SHOW archive_mode;  -- 'on' for archiving (requires restart)
SHOW archive_command;

-- To enable WAL archiving, add to postgresql.conf + restart:
-- wal_level = replica
-- archive_mode = on
-- archive_command = 'cp %p /path/to/wal_archive/%f'
```

```bash
# 4. Schema-only dump:
pg_dump -h localhost -U storeforge -d storeforge_dev \
    --schema-only \
    --file=schema_only.sql

head -30 schema_only.sql
```

</details>

---

## Capstone Connection

StoreForge backup strategy follows the **3-2-1 rule**:
- **3 copies**: production DB, daily logical backup, weekly physical backup
- **2 media types**: local disk (NVMe), cloud object storage (S3)
- **1 offsite**: S3 bucket in a different AWS region

Implementation:
- **Daily**: `pg_dump --format=custom --jobs=4` runs at 02:00 UTC via `pg_cron`, uploads to S3
- **Continuous WAL archiving**: `archive_command` ships WAL segments to S3 within seconds — enables PITR to any 5-minute window
- **Weekly**: `pg_basebackup` full physical backup, stored for 30 days
- **Restore drills**: monthly, a PITR restore is tested in an isolated environment and checked with row count verification queries
