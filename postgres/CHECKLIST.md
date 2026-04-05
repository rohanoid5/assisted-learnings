# PostgreSQL Knowledge Checklist

Use this file to periodically self-assess. Review it monthly and update your ratings.

**Legend:** `[ ]` Not yet · `[~]` In progress · `[x]` Confident

---

## Module 1 — Introduction

### 1.1 What Are Relational Databases?
- [ ] Can define a relational database and explain the table/row/column model
- [ ] Understands primary keys, foreign keys, and referential integrity
- [ ] Can name 3 popular RDBMS and one situation where each shines

### 1.2 RDBMS Benefits & Limitations
- [ ] Can list at least 4 benefits: ACID guarantees, joins, constraints, standardized SQL
- [ ] Can list at least 3 limitations: vertical scaling ceiling, schema rigidity, object-relational impedance mismatch
- [ ] Knows when to reach for a NoSQL store instead

### 1.3 Object Model
- [ ] Understands the hierarchy: Cluster → Database → Schema → Table → Row → Column
- [ ] Knows the `public` schema default and when to use multiple schemas

### 1.4 Relational Model
- [ ] Understands relations (tables), tuples (rows), attributes (columns), and domains (types)
- [ ] Can explain candidate keys, primary keys, and superkeys
- [ ] Understands functional dependency and why it matters for normalization

### 1.5 PostgreSQL Overview
- [ ] Can describe PostgreSQL's key differentiators: extensibility, standards compliance, advanced types (JSONB, arrays, ranges)
- [ ] Knows the major PostgreSQL version history milestones and the annual release cadence
- [ ] Understands the process architecture: postmaster, backend processes, background workers

### 1.6 High-Level Concepts
- [ ] Can explain ACID — Atomicity, Consistency, Isolation, Durability — with a concrete example for each
- [ ] Understands MVCC (Multi-Version Concurrency Control): readers don't block writers
- [ ] Knows what the WAL (Write-Ahead Log) is and why it enables crash recovery and replication
- [ ] Can describe the query processing pipeline: parse → analyze → plan → execute

---

## Module 2 — Installation & Setup

### 2.1 Installation
- [ ] Can install PostgreSQL via Docker Compose and start a container
- [ ] Can install via Homebrew on macOS
- [ ] Knows where configuration files live: `postgresql.conf`, `pg_hba.conf`

### 2.2 psql Basics
- [ ] Comfortable with meta-commands: `\l`, `\c`, `\dt`, `\d table`, `\df`, `\du`, `\timing`
- [ ] Can use `\e` to open queries in an editor and `\i` to run SQL files
- [ ] Knows how to set `PGPASSWORD` vs use `.pgpass`

### 2.3 Managing PostgreSQL
- [ ] Can start, stop, restart, and reload with `pg_ctl` and/or `systemctl`
- [ ] Understands the difference between `reload` (re-reads config) and `restart` (drops connections)
- [ ] Knows basic `pg_lscluster` / `pg_createcluster` usage on Debian/Ubuntu

### 2.4 GUI Tools
- [ ] Can connect and explore a database with pgAdmin 4
- [ ] Can connect and explore a database with DBeaver
- [ ] Knows how to export/import data from the GUI

---

## Module 3 — SQL Fundamentals

### 3.1 DDL: Schemas & Tables
- [ ] Can `CREATE`, `ALTER`, `DROP` tables and schemas with appropriate clauses
- [ ] Knows `IF NOT EXISTS` and `CASCADE` / `RESTRICT` modifiers
- [ ] Can rename columns, change data types, and add constraints after the fact

### 3.2 Data Types
- [ ] Knows when to use `integer` vs `bigint` vs `numeric` vs `real` vs `double precision`
- [ ] Understands text types: `text`, `varchar(n)`, `char(n)` — knows `text` is preferred in Postgres
- [ ] Knows temporal types: `date`, `time`, `timestamp`, `timestamptz`, `interval`
- [ ] Understands `boolean`, `uuid`, `jsonb`, `bytea`, arrays, and enum types

### 3.3 Querying Data
- [ ] Can write `SELECT` with `FROM`, `WHERE`, `GROUP BY`, `HAVING`, `ORDER BY`, `LIMIT`, `OFFSET`
- [ ] Understands aggregates: `COUNT`, `SUM`, `AVG`, `MIN`, `MAX`
- [ ] Can use column aliases and table aliases

### 3.4 Filtering Data
- [ ] Can use comparison operators, `IN`, `NOT IN`, `BETWEEN`, `IS NULL`, `IS NOT NULL`
- [ ] Knows pattern matching: `LIKE`, `ILIKE`, `SIMILAR TO`, `~` (regex)
- [ ] Understands operator precedence and can use parentheses to control evaluation

### 3.5 Modifying Data
- [ ] Can write `INSERT INTO ... VALUES ...` and `INSERT INTO ... SELECT ...`
- [ ] Can `UPDATE` with `WHERE` and a subquery
- [ ] Can `DELETE` with `WHERE` and understands the danger of missing `WHERE`
- [ ] Can write an `INSERT ... ON CONFLICT DO UPDATE` (UPSERT)

### 3.6 Joining Tables
- [ ] Can use `INNER JOIN`, `LEFT JOIN`, `RIGHT JOIN`, `FULL OUTER JOIN`, `CROSS JOIN`
- [ ] Understands self-joins and when to use table aliases for them
- [ ] Knows `NATURAL JOIN` and why it's dangerous in practice

### 3.7 Import & Export with COPY
- [ ] Can `COPY table TO file DELIMITER ',' CSV HEADER`
- [ ] Can `COPY table FROM file` for bulk loading
- [ ] Understands the difference between server-side `COPY` and client-side `\copy`

---

## Module 4 — Advanced SQL

### 4.1 Transactions
- [ ] Can wrap statements in `BEGIN` / `COMMIT` / `ROLLBACK`
- [ ] Knows all 4 isolation levels and what each prevents: dirty read, non-repeatable read, phantom read, serialization anomaly
- [ ] Understands `SAVEPOINT` and `ROLLBACK TO SAVEPOINT`
- [ ] Knows `FOR UPDATE` / `FOR SHARE` row-level locking

### 4.2 Subqueries & Derived Tables
- [ ] Can write correlated and non-correlated subqueries
- [ ] Knows `EXISTS`, `NOT EXISTS`, `ANY`, `ALL`, `IN` with subqueries
- [ ] Can write `FROM (SELECT ...) AS alias` derived tables

### 4.3 Common Table Expressions
- [ ] Can write a basic CTE with `WITH name AS (...)`
- [ ] Can chain multiple CTEs
- [ ] Can write a recursive CTE for hierarchical data (e.g., org charts, adjacency lists)

### 4.4 Window Functions
- [ ] Understands OVER (PARTITION BY ... ORDER BY ...) clause
- [ ] Can use ranking: `ROW_NUMBER()`, `RANK()`, `DENSE_RANK()`
- [ ] Can use offset functions: `LAG()`, `LEAD()`, `FIRST_VALUE()`, `LAST_VALUE()`
- [ ] Can use `SUM() OVER (...)` for running totals
- [ ] Knows the frame clause: `ROWS BETWEEN ...`, `RANGE BETWEEN ...`

### 4.5 Set Operations & LATERAL
- [ ] Knows `UNION`, `UNION ALL`, `INTERSECT`, `EXCEPT` and their deduplication behavior
- [ ] Can write a `LATERAL` join to pass row values to a subquery

### 4.6 Full-Text Search & JSONB
- [ ] Can create a `tsvector` column, index it with GIN, and query with `@@` and `to_tsquery`
- [ ] Can query a JSONB column with `->`, `->>`, `#>`, `@>`, `?`
- [ ] Can index JSONB fields with `GIN` and specific keys with a functional index

---

## Module 5 — Database Design

### 5.1 Normalization
- [ ] Can identify and normalize to 1NF, 2NF, 3NF, BCNF
- [ ] Understands update/insert/delete anomalies that normalization prevents
- [ ] Knows when to deliberately denormalize (performance, query simplicity)

### 5.2 Schema Design Patterns
- [ ] Understands single-table, multi-table, and polymorphic association patterns
- [ ] Can design a soft-delete pattern (`deleted_at TIMESTAMPTZ`)
- [ ] Knows audit patterns: created_at, updated_at, created_by, updated_by

### 5.3 Constraints
- [ ] Can apply: `NOT NULL`, `UNIQUE`, `PRIMARY KEY`, `FOREIGN KEY`, `CHECK`, `EXCLUSION`
- [ ] Knows `DEFERRABLE INITIALLY DEFERRED` for deferred constraint checking
- [ ] Understands partial unique index as a flexible uniqueness constraint

### 5.4 Views & Materialized Views
- [ ] Can create and query a view
- [ ] Knows when a view is updatable
- [ ] Can create a materialized view and `REFRESH MATERIALIZED VIEW [CONCURRENTLY]`
- [ ] Understands when to use a materialized view vs an index for read performance

---

## Module 6 — PL/pgSQL & Programmability

### 6.1 Functions & Procedures
- [ ] Can write a PL/pgSQL function with parameters, variables, and `RETURN`
- [ ] Understands the difference between functions and procedures (`CALL` vs function call, transaction control)
- [ ] Knows volatility categories: `IMMUTABLE`, `STABLE`, `VOLATILE` and their optimizer implications

### 6.2 Triggers
- [ ] Can create a `BEFORE` / `AFTER` trigger on `INSERT`, `UPDATE`, `DELETE`
- [ ] Understands `NEW` and `OLD` record variables
- [ ] Knows `TRUNCATE` triggers and statement-level vs row-level triggers
- [ ] Understands the ordering implications when multiple triggers exist on the same table

### 6.3 Advanced PL/pgSQL
- [ ] Can use `IF / ELSIF / ELSE`, `LOOP`, `WHILE`, `FOR` (integer and query loops)
- [ ] Can use `EXCEPTION` / `WHEN` blocks for error handling
- [ ] Knows `EXECUTE` for dynamic SQL and is aware of SQL injection risks with format strings
- [ ] Can use cursors for large result sets

### 6.4 SQL Functions & Function Properties
- [ ] Can write simple SQL functions (without PL/pgSQL)
- [ ] Understands function overloading (same name, different argument types)
- [ ] Knows `SECURITY DEFINER` vs `SECURITY INVOKER` and the privilege implications

### 6.5 Extensions
- [ ] Knows how to install and list extensions with `CREATE EXTENSION`
- [ ] Familiar with: `uuid-ossp` / `gen_random_uuid()`, `pgcrypto`, `pg_stat_statements`, `pg_trgm`, `PostGIS`
- [ ] Understands that extensions require superuser or `pg_extension_owner` privileges

---

## Module 7 — Security & Access Control

### 7.1 Roles & Users
- [ ] Understands that users are roles with `LOGIN` privilege
- [ ] Can create roles with `CREATE ROLE`, assign them with `GRANT role TO user`
- [ ] Knows role inheritance and `SET ROLE`

### 7.2 Privileges
- [ ] Can `GRANT` and `REVOKE` on tables, schemas, sequences, functions
- [ ] Understands `GRANT ... WITH GRANT OPTION`
- [ ] Knows `DEFAULT PRIVILEGES` with `ALTER DEFAULT PRIVILEGES`
- [ ] Understands the public schema's default grant and why it should be revoked

### 7.3 Row-Level Security
- [ ] Can enable RLS on a table with `ALTER TABLE ... ENABLE ROW LEVEL SECURITY`
- [ ] Can write `CREATE POLICY` for `SELECT`, `INSERT`, `UPDATE`, `DELETE`
- [ ] Knows that table owners bypass RLS by default and how to change that

### 7.4 Authentication
- [ ] Understands `pg_hba.conf` entries: type, database, user, address, auth method
- [ ] Knows auth methods: `trust`, `md5`, `scram-sha-256`, `peer`, `cert`
- [ ] Knows that `scram-sha-256` is the preferred method and `md5` is deprecated

### 7.5 SSL & Encryption
- [ ] Can configure PostgreSQL to require SSL connections
- [ ] Knows `sslmode` values: `disable`, `require`, `verify-ca`, `verify-full`
- [ ] Understands `pgcrypto` for column-level encryption vs transparent data encryption (TDE)

---

## Module 8 — Performance Tuning

### 8.1 EXPLAIN & ANALYZE
- [ ] Can run `EXPLAIN (ANALYZE, BUFFERS, FORMAT TEXT)` and read the output
- [ ] Knows key node types: Seq Scan, Index Scan, Index Only Scan, Bitmap Heap Scan, Hash Join, Nested Loop
- [ ] Can identify a missing index or a bad row-count estimate from the plan

### 8.2 Indexes
- [ ] Knows B-tree (default), GIN, GIST, BRIN, Hash index types and when to use each
- [ ] Can create partial indexes (`WHERE clause`) and functional indexes
- [ ] Knows `CREATE INDEX CONCURRENTLY` to avoid table locks
- [ ] Understands index bloat and when to `REINDEX`

### 8.3 postgresql.conf Tuning
- [ ] Can set `shared_buffers`, `work_mem`, `maintenance_work_mem`, `max_connections` with justification
- [ ] Knows `effective_cache_size` and its effect on the planner
- [ ] Understands checkpoint tuning: `checkpoint_completion_target`, `max_wal_size`

### 8.4 VACUUM & Table Maintenance
- [ ] Explains what dead tuples are and why they accumulate (MVCC)
- [ ] Knows `VACUUM` vs `VACUUM FULL` vs `VACUUM ANALYZE` trade-offs
- [ ] Can configure `autovacuum` thresholds: `autovacuum_vacuum_scale_factor`, `autovacuum_vacuum_threshold`
- [ ] Understands table bloat and knows when `pg_repack` or `pg_squeeze` is appropriate

### 8.5 Query Optimization
- [ ] Can rewrite a correlated subquery as a JOIN for better performance
- [ ] Knows planner statistics (`pg_statistic`, `ANALYZE`) and how to control with `ALTER TABLE ... ALTER COLUMN ... SET STATISTICS`
- [ ] Understands `enable_seqscan`, `enable_hashjoin` "hints" (debugging only)
- [ ] Can use `pg_stat_statements` to find top slow queries

### 8.6 Table Partitioning
- [ ] Can create range, list, and hash partitioned tables
- [ ] Knows `PARTITION BY RANGE (date_column)`
- [ ] Understands partition pruning and constraint exclusion
- [ ] Knows trade-offs: FK references, partition-wise joins, maintenance overhead

### 8.7 Connection Pooling
- [ ] Can explain why connection overhead matters and what PgBouncer solves
- [ ] Knows PgBouncer pool modes: `session`, `transaction`, `statement` and compatibility constraints
- [ ] Can configure `pgbouncer.ini` basics: `pool_size`, `max_client_conn`, `server_idle_timeout`

---

## Module 9 — Infrastructure & Operations

### 9.1 Backup & Recovery
- [ ] Can perform logical backups with `pg_dump` and `pg_dumpall`
- [ ] Can restore with `pg_restore` and `psql`
- [ ] Understands PITR (Point-in-Time Recovery) with continuous WAL archiving
- [ ] Can set up `pgBackRest` or `Barman` for enterprise backup

### 9.2 Streaming Replication
- [ ] Can configure a primary and a standby with `primary_conninfo` and `recovery.conf` / `standby.signal`
- [ ] Knows `wal_level = replica`, `max_wal_senders`, `wal_keep_size`
- [ ] Understands replication lag and how to monitor with `pg_stat_replication`
- [ ] Can explain synchronous vs asynchronous replication trade-offs

### 9.3 Logical Replication
- [ ] Understands the difference between logical and streaming replication
- [ ] Can set up a publication and subscription for selective table replication
- [ ] Knows use cases: zero-downtime upgrades, cross-version replication, CDC

### 9.4 High Availability
- [ ] Understands the role of Patroni / repmgr / Stolon for automated failover
- [ ] Knows the split-brain problem and how distributed consensus (etcd, Consul) solves it
- [ ] Can describe the steps of a planned switchover vs an unplanned failover

### 9.5 Monitoring
- [ ] Can query key system catalogs: `pg_stat_activity`, `pg_locks`, `pg_stat_bgwriter`, `pg_stat_replication`
- [ ] Can set up `pg_exporter` + Prometheus + Grafana for metrics
- [ ] Knows how to detect and kill long-running queries and blocking locks

### 9.6 Upgrade Procedures
- [ ] Knows `pg_upgrade` for major version upgrades
- [ ] Understands logical replication-based blue/green upgrade with minimal downtime
- [ ] Knows the process: check extension compatibility → take backup → upgrade → verify → update config

### 9.7 Cloud Deployment
- [ ] Knows the trade-offs of RDS (managed, limited config) vs self-hosted on EC2/VM vs Aurora
- [ ] Understands Neon, Supabase, and PlanetScale-style managed Postgres offerings
- [ ] Can design a cloud HA topology with read replicas and a connection pooler

---

## Capstone — StoreForge

- [ ] Can describe the StoreForge schema: all tables, relationships, constraints
- [ ] Understands the security model: roles, privileges, and RLS policies
- [ ] Can trace through `functions.sql` and explain every function and procedure
- [ ] Can analyze the `indexes.sql` file and justify each index
- [ ] Has run `seed.sql` and written ad-hoc queries against the live data

---

## Review Log

| Date | Topics Reviewed | Gaps Identified |
|------|----------------|-----------------|
| | | |
| | | |
| | | |
