# High Availability

## Concept

**High Availability (HA)** means the database continues serving clients even when a server fails. Streaming replication gives you a standby, but promoting it requires a decision: *Is the primary actually down, or just temporarily unreachable?* Automatic failover requires an orchestration layer that monitors the primary, coordinates consensus among nodes, updates connection strings, and ensures the old primary does not come back as a second primary (**split-brain**). The two most widely deployed solutions are **Patroni** and **pg_auto_failover**.

---

## Key HA Concepts

```
             ┌─────────────┐
             │  DCS / etcd │  ← Distributed consensus store
             │  ZooKeeper  │    (stores who is primary, config, locks)
             └──────┬──────┘
                    │
       ┌────────────┼────────────┐
       ▼            ▼            ▼
  ┌─────────┐  ┌─────────┐  ┌─────────┐
  │Patroni-1│  │Patroni-2│  │Patroni-3│
  │ Primary │  │ Standby │  │ Standby │
  │  PG 16  │  │  PG 16  │  │  PG 16  │
  └─────────┘  └─────────┘  └─────────┘
       ▲
       │  HAProxy / pgBouncer routes writes to primary
```

**DCS (Distributed Configuration Store)**: etcd, Consul, or ZooKeeper stores the cluster state. A node must win a DCS lock to be primary. If the primary loses its DCS connection, it demotes itself — preventing split-brain.

---

## Patroni

Patroni is a Python daemon that wraps PostgreSQL with automatic leader election:

```yaml
# /etc/patroni/patroni.yml (per-node configuration):
scope: storeforge-cluster
namespace: /db/
name: pg-node-1

restapi:
  listen: 0.0.0.0:8008
  connect_address: 192.168.1.101:8008

etcd:
  hosts: etcd1:2379,etcd2:2379,etcd3:2379

bootstrap:
  dcs:
    ttl: 30
    loop_wait: 10
    retry_timeout: 10
    maximum_lag_on_failover: 1048576   # 1MB — standby must be within 1MB to be eligible
  initdb:
    - encoding: UTF8
    - data-checksums

postgresql:
  listen: 0.0.0.0:5432
  connect_address: 192.168.1.101:5432
  data_dir: /var/lib/postgresql/16/main
  bin_dir: /usr/lib/postgresql/16/bin
  parameters:
    wal_level: replica
    hot_standby: on
    max_wal_senders: 10
    max_replication_slots: 10
    wal_log_hints: on   # required for pg_rewind

  authentication:
    replication:
      username: replicator
      password: replsecret
    superuser:
      username: postgres
      password: supersecret
```

```bash
# Start Patroni (runs PostgreSQL under its control):
patroni /etc/patroni/patroni.yml

# Check cluster state with patronictl:
patronictl -c /etc/patroni/patroni.yml list
# Member     Host              Role    State   TL  Lag in MB
# pg-node-1  192.168.1.101:5432  Leader  running  1  0
# pg-node-2  192.168.1.102:5432  Replica running  1  0
# pg-node-3  192.168.1.103:5432  Replica running  1  0

# Planned switchover (promotes a specific replica):
patronictl -c /etc/patroni/patroni.yml switchover storeforge-cluster \
    --master pg-node-1 \
    --candidate pg-node-2

# Manual failover (when primary is unresponsive):
patronictl -c /etc/patroni/patroni.yml failover storeforge-cluster

# Restart PostgreSQL on one node via Patroni:
patronictl -c /etc/patroni/patroni.yml restart storeforge-cluster pg-node-1

# Edit cluster DCS configuration:
patronictl -c /etc/patroni/patroni.yml edit-config
```

---

## pg_auto_failover

A lighter-weight alternative developed by Citus/Microsoft — suitable for 2-node (primary + standby) setups:

```bash
# Set up a monitor node:
pg_autoctl create monitor --pgdata /var/lib/postgresql/monitor --pgport 5000

# Create primary:
pg_autoctl create postgres \
    --pgdata /var/lib/postgresql/primary \
    --monitor postgres://autoctl_node@monitor-host:5000/pg_auto_failover

# Create standby:
pg_autoctl create postgres \
    --pgdata /var/lib/postgresql/standby \
    --monitor postgres://autoctl_node@monitor-host:5000/pg_auto_failover

# Show current formation:
pg_autoctl show state --monitor postgres://autoctl_node@monitor-host:5000/pg_auto_failover
# Name    |  Port | Group |  Node Id | Current State | Assigned State
# primary |  5432 |     0 |        1 | primary       | primary
# standby |  5432 |     0 |        2 | secondary     | secondary

# Perform a planned failover:
pg_autoctl perform failover \
    --monitor postgres://autoctl_node@monitor-host:5000/pg_auto_failover
```

---

## Switchover vs Failover

| Operation | Trigger | Downtime | Risk |
|-----------|---------|----------|------|
| **Switchover** | Planned (maintenance, upgrades) | Seconds | Very low |
| **Failover** | Unplanned (primary crash, network partition) | Seconds to minutes | Data loss possible if async |

```sql
-- Check synchronous vs asynchronous replication:
SHOW synchronous_standby_names;   -- empty = async (potential data loss on failover)
SHOW synchronous_commit;          -- 'on' with sync standby = no data loss

-- For zero RPO (Recovery Point Objective):
-- synchronous_standby_names = 'ANY 1 (standby1, standby2)'
-- synchronous_commit = 'remote_write'   -- waits for standby to write to WAL
-- or: synchronous_commit = 'on'         -- waits for standby to flush + confirm

-- Check replication mode of connected standbys:
SELECT application_name, sync_state  -- 'sync', 'async', 'potential', 'quorum'
FROM pg_stat_replication;
```

---

## HAProxy for Connection Routing

```ini
# /etc/haproxy/haproxy.cfg

frontend postgres_write
    bind *:5432
    default_backend primary_backend

frontend postgres_read
    bind *:5433
    default_backend replica_backend

backend primary_backend
    option httpchk GET /primary
    # Patroni exposes HTTP health endpoint: /primary returns 200 only for primary
    server pg-node-1 192.168.1.101:5432 check port 8008
    server pg-node-2 192.168.1.102:5432 check port 8008
    server pg-node-3 192.168.1.103:5432 check port 8008

backend replica_backend
    option httpchk GET /replica
    balance roundrobin
    server pg-node-1 192.168.1.101:5432 check port 8008
    server pg-node-2 192.168.1.102:5432 check port 8008
    server pg-node-3 192.168.1.103:5432 check port 8008
```

---

## Try It Yourself

```sql
-- 1. What is split-brain and why is a distributed consensus store (DCS) needed to prevent it?

-- 2. Your standby is 5MB behind the primary when the primary fails.
--    In Patroni, what setting controls whether this standby is eligible to be promoted?
--    What maximum_lag_on_failover value would exclude it?

-- 3. What is the difference between RPO and RTO?
--    With async replication and synchronous_commit = 'on', what is your RPO?

-- 4. In Patroni, what is the difference between 'switchover' and 'failover'?
--    Which command is safe to run during a maintenance window?
```

<details>
<summary>Show solutions</summary>

```text
-- 1. Split-brain:
Split-brain occurs when two PostgreSQL nodes both believe they are the primary and
both accept writes. The data diverges, leading to data loss or corruption when you
try to reconcile. A DCS prevents this by requiring the primary to hold an exclusive
lock in the DCS — if it loses the lock (e.g., DCS unreachable), it immediately
demotes itself and stops accepting writes. Only one node can hold the lock at a time.

-- 2. Patroni lag eligibility:
Setting: maximum_lag_on_failover (in bytes)
If set to 1048576 (1MB), a standby 5MB behind is EXCLUDED from promotion candidates.
Increase to 10485760 (10MB) to include it.
If ALL standbys exceed the lag threshold, Patroni will NOT automatically promote
(to prevent data loss). A manual failover override is required.

-- 3. RPO vs RTO:
RPO (Recovery Point Objective): How much DATA LOSS is acceptable?
  - Async replication: potentially seconds of data loss (depends on lag at failure)
  - Sync replication (synchronous_commit = 'on'): RPO = 0 (no committed data lost)
  Note: synchronous_commit = 'on' on the PRIMARY alone is not enough — you need
  synchronous_standby_names to name the sync standbys.

RTO (Recovery Time Objective): How long can the system be UNAVAILABLE?
  - With Patroni + HAProxy: typically 10-30 seconds for automatic failover

-- 4. Switchover vs Failover in Patroni:
switchover: planned, graceful — primary demotes itself, chosen replica promoted.
  Zero data loss guaranteed. Use during: maintenance, node replacement, upgrades.

failover: forced — used when primary is unresponsive.
  May have data loss if replication was async.
  Use only when primary is confirmed down (not just network-partitioned).
  Command: patronictl failover
```

</details>

---

## Capstone Connection

StoreForge HA configuration:
- **3-node Patroni cluster** with etcd DCS (3 etcd nodes on dedicated VMs)
- **Async replication** to both standbys; `maximum_lag_on_failover = 10MB`
- **HAProxy** routes port 5432 writes to the current primary, port 5433 reads to replicas
- **PgBouncer** in front of HAProxy so connection strings never change during failover
- **RTO target**: ≤ 20 seconds (Patroni `ttl = 30`, `loop_wait = 10` is the monitoring heartbeat)
- **RPO target**: < 5 seconds of data loss acceptable for analytics; zero loss for payments (payment inserts use `synchronous_commit = 'remote_write'` at session level)
- **Switchover procedure**: ran quarterly during maintenance windows to verify failover works cleanly
