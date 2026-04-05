# Streaming Replication

## Concept

**Streaming replication** sends a continuous stream of Write-Ahead Log (WAL) records from a primary server to one or more standby servers. Each standby replays those WAL records in real time, staying within seconds of the primary. Standbys can serve **read-only queries** (hot standby), offloading analytics and reporting traffic. If the primary fails, a standby can be promoted to become the new primary.

---

## How It Works

```
Primary Server                     Standby Server
┌────────────────────┐             ┌────────────────────┐
│  Writes → WAL      │──WAL stream─→ WAL receiver        │
│  pg_wal/           │             │  replay process     │
│  wal_sender        │◄──feedback──│  pg_stat_replication│
└────────────────────┘             └────────────────────┘
```

WAL records are sent by the **wal sender** process on the primary and received by the **WAL receiver** on the standby. The standby replays the records, keeping its data files in sync.

---

## Primary Server Setup

```sql
-- 1. Set replication-required parameters in postgresql.conf:
-- wal_level = replica        (or 'logical' for logical replication)
-- max_wal_senders = 5        (how many standbys can connect simultaneously)
-- wal_keep_size = 64         (MB of WAL to retain for slow standbys)
-- hot_standby = on           (set on standby; on primary doesn't hurt)

SHOW wal_level;            -- must be 'replica' or 'logical'
SHOW max_wal_senders;      -- must be > 0

-- 2. Create a dedicated replication role:
CREATE ROLE replicator WITH REPLICATION LOGIN PASSWORD 'replsecret';

-- 3. Allow the standby to connect in pg_hba.conf:
-- TYPE  DATABASE    USER        ADDRESS             METHOD
-- host  replication replicator  192.168.1.102/32    scram-sha-256
-- Reload:
SELECT pg_reload_conf();

-- 4. Check wal_level change requires restart:
SELECT name, setting, context FROM pg_settings WHERE name = 'wal_level';
-- context = 'postmaster' → restart required
```

---

## Taking a Base Backup (Standby Bootstrap)

```bash
# On the standby server — copy primary's data directory:
pg_basebackup \
    --host=primary-server-ip \
    --port=5432 \
    --username=replicator \
    --pgdata=/var/lib/postgresql/16/main_standby \
    --format=plain \
    --wal-method=stream \
    --checkpoint=fast \
    --progress \
    --verbose

# This creates a complete copy of the primary including:
# - All data files
# - WAL segments generated during the backup (--wal-method=stream)
# - pg_hba.conf, postgresql.conf
```

---

## Standby Server Configuration

```bash
# PG 12+: use standby.signal file (replaces recovery.conf):
touch /var/lib/postgresql/16/main_standby/standby.signal
```

```ini
# In postgresql.conf on the standby:
primary_conninfo = 'host=primary-server-ip port=5432 user=replicator password=replsecret application_name=standby1'
hot_standby = on
hot_standby_feedback = on    # prevents primary from vacuuming rows the standby might need

# Optional: recovery delay (e.g., a 5-minute lagging standby for accidental deletes):
# recovery_min_apply_delay = '5min'
```

```bash
# Start the standby:
pg_ctl start -D /var/lib/postgresql/16/main_standby
# PostgreSQL will enter recovery mode and begin streaming WAL from the primary.
```

---

## Monitoring Replication

```sql
-- On the PRIMARY: check connected standbys and replication lag:
SELECT
    application_name,
    client_addr,
    state,
    sent_lsn,
    write_lsn,
    flush_lsn,
    replay_lsn,
    write_lag,
    flush_lag,
    replay_lag,
    sync_state
FROM pg_stat_replication;
-- sent_lsn:   WAL sent to standby
-- replay_lsn: WAL applied on standby
-- replay_lag: how far behind the standby is in time

-- Replication lag in bytes:
SELECT application_name,
       pg_wal_lsn_diff(pg_current_wal_lsn(), replay_lsn) AS lag_bytes
FROM pg_stat_replication;

-- On the STANDBY: check replication status:
SELECT status, receive_start_lsn, received_lsn, last_msg_receipt_time
FROM pg_stat_wal_receiver;

-- Confirm standby is in recovery mode:
SELECT pg_is_in_recovery();   -- TRUE on standby, FALSE on primary
SELECT pg_last_wal_receive_lsn();
SELECT pg_last_wal_replay_lsn();
SELECT pg_last_xact_replay_timestamp();  -- when was the last WAL record applied?
```

---

## Hot Standby — Read-Only Queries

```sql
-- On the standby (hot_standby = on), you can run SELECT queries:
SELECT count(*) FROM product;
SELECT * FROM "order" WHERE status = 'delivered' LIMIT 100;

-- Write attempts fail with a clear error:
UPDATE product SET price = 99.99 WHERE id = 1;
-- ERROR: cannot execute UPDATE in a read-only transaction

-- Useful for:
-- - Read replicas for API read traffic
-- - Analytics / reporting queries
-- - pg_dump (avoids load on primary)

-- pg_basebackup to seed another standby can also target this standby.

-- Conflict resolution (standby vs vacuum):
-- If a long query on the standby conflicts with a primary VACUUM, standby waits.
-- hot_standby_feedback = on sends feedback to primary to prevent conflict.
-- max_standby_streaming_delay = 30s  -- how long to wait before cancelling conflicting query
```

---

## Promoting a Standby

```bash
# If the primary fails, promote the standby to become the new primary:
pg_ctl promote -D /var/lib/postgresql/16/main_standby
# OR:
psql -c "SELECT pg_promote();"

# PostgreSQL exits recovery mode and begins accepting writes.
# Verify:
```

```sql
SELECT pg_is_in_recovery();  -- should return FALSE after promotion

-- After promotion, the old primary (if it recovers) must NOT be allowed to write.
-- Point its recovery back to the new primary, or it will diverge (split-brain).
-- pg_rewind can rehabilitate the old primary as a new standby:
```

```bash
# Rewind old primary to sync with the new primary (instead of full base backup):
pg_rewind \
    --target-pgdata=/var/lib/postgresql/16/main \
    --source-server="host=new-primary port=5432 user=replicator" \
    --progress
```

---

## Replication Slots (Optional)

```sql
-- Replication slots prevent WAL from being deleted until the standby confirms receipt.
-- Use when you CAN'T afford a standby to fall too far behind:
SELECT pg_create_physical_replication_slot('standby1_slot');

-- Monitor slot lag (DANGER: unbounded growth if standby disconnects!):
SELECT slot_name, active, pg_wal_lsn_diff(pg_current_wal_lsn(), restart_lsn) AS lag_bytes
FROM pg_replication_slots;

-- Drop unused slot (prevents WAL accumulation):
SELECT pg_drop_replication_slot('standby1_slot');
```

---

## Try It Yourself

```sql
-- 1. Check your primary's wal_level and max_wal_senders.
--    What changes are needed to enable streaming replication?

-- 2. Create a replication user called 'replicator' with REPLICATION privilege.
--    What pg_hba.conf entry would allow it to connect from 10.0.0.0/8?

-- 3. On a running primary, inspect pg_stat_replication.
--    What do write_lag, flush_lag, and replay_lag each represent?

-- 4. How would you set a 10-minute replication delay on a standby?
--    In what scenarios is a delayed standby useful?
```

<details>
<summary>Show solutions</summary>

```sql
-- 1. Check replication readiness:
SHOW wal_level;         -- needs to be 'replica' or 'logical'
SHOW max_wal_senders;   -- needs to be >= 1
-- Changes require postgresql.conf edit + PostgreSQL restart (context = postmaster).

-- 2. Replication user:
CREATE ROLE replicator WITH REPLICATION LOGIN PASSWORD 'strong_password_here';

-- pg_hba.conf entry (add before the catch-all line):
-- host  replication  replicator  10.0.0.0/8  scram-sha-256
SELECT pg_reload_conf();  -- reload pg_hba.conf (no restart needed for hba changes)

-- 3. Lag explanation:
-- write_lag:  time between primary WAL write and standby confirming it was WRITTEN to disk
-- flush_lag:  time between primary commit and standby flushing WAL to durable storage
-- replay_lag: time between primary commit and standby actually APPLYING the change to data files
-- replay_lag is most important for read-replica freshness.

-- 4. Delayed standby:
-- In postgresql.conf on the standby:
-- recovery_min_apply_delay = '10min'

-- Use cases for delayed standby:
-- - Catch accidental mass DELETEs/UPDATEs before they replicate
-- - Buy time to promote before the damage propagates
-- - Data recovery without a full PITR restore
```

</details>

---

## Capstone Connection

StoreForge replication architecture:
- **1 primary** handles all writes (orders, inventory updates, reviews)
- **2 hot standbys** serve read-only API traffic (product search, order history) via a load balancer routing `SELECT` queries to replicas
- **1 delayed standby** (5-minute lag) as an emergency recovery buffer — has saved the team twice from accidental bulk updates
- **Monitoring**: `replay_lag > 10s` fires a PagerDuty alert; `lag_bytes > 100MB` from `pg_stat_replication` is logged and reviewed
- **No replication slots** used — WAL is kept via `wal_keep_size = 256MB`; if a standby falls more than 256MB behind, a fresh `pg_basebackup` is taken
