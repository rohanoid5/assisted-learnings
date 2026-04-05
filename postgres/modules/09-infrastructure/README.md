# Module 09 — Infrastructure & Operations

## Overview

A well-designed database is only half the story. The other half is operating it reliably: backing it up, replicating it for availability, managing connection pressure, and observing its health in production.

This module covers the full operational lifecycle of a PostgreSQL deployment — from `pg_dump` scripts you can run today, to streaming replication, connection pooling with PgBouncer, production monitoring, high-availability with Patroni, and safe version upgrades.

---

## Learning Objectives

By the end of this module, you should be able to:

- [ ] Back up a PostgreSQL database with `pg_dump`, `pg_dumpall`, and `pg_basebackup`
- [ ] Restore from a dump and verify data integrity
- [ ] Set up streaming replication between a primary and at least one replica
- [ ] Explain the difference between streaming and logical replication and when to use each
- [ ] Configure PgBouncer in transaction pooling mode for connection management
- [ ] Query `pg_stat_activity`, `pg_stat_statements`, and `pg_stat_user_tables` to diagnose problems
- [ ] Understand Patroni's role in automatic failover
- [ ] Perform a major version upgrade using `pg_upgrade`

---

## Topics

| # | File | Concept |
|---|------|---------|
| 1 | [01-backup-recovery.md](01-backup-recovery.md) | `pg_dump`, `pg_dumpall`, custom format, `pg_restore`, `pg_basebackup`, WAL archiving |
| 2 | [02-streaming-replication.md](02-streaming-replication.md) | `primary_conninfo`, `wal_level`, `max_wal_senders`, `hot_standby`, sync vs async |
| 3 | [03-logical-replication.md](03-logical-replication.md) | Publications, subscriptions, use cases (selective replication, zero-downtime migration) |
| 4 | [04-connection-pooling.md](04-connection-pooling.md) | PgBouncer: session, transaction, statement modes; `pgbouncer.ini`; auth setup |
| 5 | [05-monitoring.md](05-monitoring.md) | `pg_stat_activity`, `pg_stat_statements`, `pg_stat_user_tables`, slow query log, check_pgactivity |
| 6 | [06-high-availability.md](06-high-availability.md) | Patroni architecture, DCS (etcd/Consul), automatic failover, HAProxy integration |
| 7 | [07-upgrade-procedures.md](07-upgrade-procedures.md) | `pg_upgrade` workflow, logical replication upgrade path, blue-green upgrade strategy |

---

## Estimated Time

**5–7 hours** (including exercises)

---

## Prerequisites

- Modules 03–08 completed — production-like StoreForge database
- Docker Compose available (for multi-container exercises involving replicas and PgBouncer)

---

## Capstone Milestone

By the end of this module you should have:

1. **Backup script** — shell script that runs `pg_dump` in custom format, compresses, and timestamps the output; verified restore works
2. **Streaming replica** — second Docker container running as a hot standby; verified with `pg_stat_replication`
3. **PgBouncer** — running in transaction mode between client and PostgreSQL, validated with `SHOW POOLS` and `SHOW STATS`
4. **Slow query identification** — enabled `pg_stat_statements`, found the top-3 slowest queries by `mean_exec_time`
5. **Monitoring baseline** — documented key metrics: active connections, cache hit ratio, replication lag, table bloat
