# Table Partitioning

## Concept

When a table grows very large (tens or hundreds of millions of rows), even the best indexes start to struggle. PostgreSQL **declarative partitioning** splits one logical table into multiple physical child tables — each storing a subset of the data. Queries that filter on the partition key only scan the relevant child tables (**partition pruning**), dramatically reducing I/O. Partitioning also makes archiving easy: `DETACH PARTITION` removes a child table in milliseconds without a lock on the parent.

---

## Partition Types Overview

| Type | Best For | Example Key |
|------|----------|-------------|
| `RANGE` | Dates, timestamps, IDs | `created_at`, `id` |
| `LIST` | Low-cardinality enums | `status`, `country` |
| `HASH` | Even distribution of keys | `customer_id` |

---

## RANGE Partitioning (by Date)

```sql
-- Create partitioned parent table (no data lives here directly):
CREATE TABLE order_partitioned (
    id             BIGINT         NOT NULL,
    customer_id    INTEGER        NOT NULL,
    status         TEXT           NOT NULL,
    total_amount   NUMERIC(10,2),
    created_at     TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    PRIMARY KEY (id, created_at)   -- partition key must be part of PK
) PARTITION BY RANGE (created_at);

-- Create child partitions — each covers a specific date range:
CREATE TABLE order_2023
    PARTITION OF order_partitioned
    FOR VALUES FROM ('2023-01-01') TO ('2024-01-01');

CREATE TABLE order_2024
    PARTITION OF order_partitioned
    FOR VALUES FROM ('2024-01-01') TO ('2025-01-01');

CREATE TABLE order_2025
    PARTITION OF order_partitioned
    FOR VALUES FROM ('2025-01-01') TO ('2026-01-01');

-- Default partition catches rows that don't match any partition:
CREATE TABLE order_future
    PARTITION OF order_partitioned DEFAULT;

-- Insert goes to the correct partition automatically:
INSERT INTO order_partitioned (id, customer_id, status, total_amount, created_at)
VALUES (1001, 42, 'delivered', 129.99, '2024-05-15');

-- Query the parent — PostgreSQL routes to the right child:
SELECT * FROM order_partitioned WHERE created_at = '2024-05-15';

-- Verify which partition a row landed in:
SELECT tableoid::regclass AS partition, * FROM order_partitioned LIMIT 5;
```

---

## LIST Partitioning

```sql
-- Partition by order status:
CREATE TABLE order_by_status (
    id           BIGINT      NOT NULL,
    customer_id  INTEGER     NOT NULL,
    status       TEXT        NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY  (id, status)
) PARTITION BY LIST (status);

CREATE TABLE order_active
    PARTITION OF order_by_status
    FOR VALUES IN ('pending', 'processing', 'shipped');

CREATE TABLE order_closed
    PARTITION OF order_by_status
    FOR VALUES IN ('delivered', 'cancelled', 'returned');
```

---

## HASH Partitioning

```sql
-- Distribute rows evenly across 4 shards by customer_id:
CREATE TABLE audit_log_partitioned (
    id           BIGSERIAL,
    table_name   TEXT,
    operation    TEXT,
    row_id       BIGINT,
    changed_at   TIMESTAMPTZ DEFAULT NOW(),
    PRIMARY KEY (id, customer_id),  -- assuming customer_id added
    customer_id  INTEGER
) PARTITION BY HASH (customer_id);

CREATE TABLE audit_log_p0 PARTITION OF audit_log_partitioned
    FOR VALUES WITH (MODULUS 4, REMAINDER 0);
CREATE TABLE audit_log_p1 PARTITION OF audit_log_partitioned
    FOR VALUES WITH (MODULUS 4, REMAINDER 1);
CREATE TABLE audit_log_p2 PARTITION OF audit_log_partitioned
    FOR VALUES WITH (MODULUS 4, REMAINDER 2);
CREATE TABLE audit_log_p3 PARTITION OF audit_log_partitioned
    FOR VALUES WITH (MODULUS 4, REMAINDER 3);
```

---

## Partition Pruning

```sql
-- PostgreSQL eliminates irrelevant partitions at planning time:
EXPLAIN SELECT * FROM order_partitioned
WHERE created_at BETWEEN '2024-01-01' AND '2024-12-31';
-- Output includes only order_2024, not order_2023 or order_2025.
-- Look for: "Partitions selected: 1 (of 4)" in the EXPLAIN output.

-- Verify pruning is enabled:
SHOW enable_partition_pruning;  -- 'on' by default

-- Dynamic pruning at execution time (for prepared statements):
PREPARE recent_orders(timestamptz) AS
    SELECT id, total_amount FROM order_partitioned WHERE created_at > $1;
EXPLAIN EXECUTE recent_orders('2024-06-01');
-- Pruning still works at execute time.

-- No pruning if the predicate doesn't include the partition key:
EXPLAIN SELECT * FROM order_partitioned WHERE customer_id = 42;
-- Plans a scan of ALL partitions — filter on non-partition-key columns.
```

---

## Indexes on Partitioned Tables

```sql
-- An index on the parent table automatically creates indexes on all children:
CREATE INDEX CONCURRENTLY ON order_partitioned (customer_id);
-- Creates order_2023_customer_id_idx, order_2024_customer_id_idx, etc.

-- Check indexes on child partitions:
SELECT indexname, tablename
FROM pg_indexes
WHERE tablename LIKE 'order_2%'
ORDER BY tablename, indexname;

-- Unique indexes must include the partition key:
-- This FAILS (created_at not included):
-- CREATE UNIQUE INDEX ON order_partitioned (id);

-- This WORKS:
CREATE UNIQUE INDEX ON order_partitioned (id, created_at);
```

---

## ATTACH and DETACH Partitions

```sql
-- ATTACH: add an existing table as a new partition (near-instant, briefly locks):
CREATE TABLE order_2026 (LIKE order_partitioned INCLUDING ALL);
-- Add a CHECK constraint first — PostgreSQL scans to verify:
ALTER TABLE order_2026
    ADD CONSTRAINT order_2026_check
    CHECK (created_at >= '2026-01-01' AND created_at < '2027-01-01');

-- Now attach without a full scan (PostgreSQL trusts the CHECK):
ALTER TABLE order_partitioned
    ATTACH PARTITION order_2026
    FOR VALUES FROM ('2026-01-01') TO ('2027-01-01');

-- DETACH: remove an old partition for archiving — takes milliseconds:
ALTER TABLE order_partitioned DETACH PARTITION order_2023;
-- order_2023 is now an ordinary table — copy it to cold storage or archive.
-- No lock on other partitions.

-- DETACH CONCURRENTLY (PG 14+): zero-lock detach using weaker lock:
ALTER TABLE order_partitioned DETACH PARTITION order_2024 CONCURRENTLY;

-- Drop the archived partition after confirming backup:
DROP TABLE order_2023;
```

---

## Foreign Keys and Partitioning Limitations

```sql
-- ⚠️  Limitations to be aware of:
-- 1. Foreign keys FROM a partitioned table to another table are supported
--    in PG 12+, but foreign keys TO (pointing at) a partitioned table
--    are not supported. Use CHECK constraints or application logic instead.

-- 2. BEFORE ROW triggers cannot fire on partitioned tables — only on children.
-- 3. Global unique constraints (without partition key) are not supported.
-- 4. After ATTACH, PostgreSQL scans the table to verify constraint compliance
--    unless a matching CHECK constraint is defined first.

-- Workaround for FK pointing at partitioned table: point at the specific child partition.
-- Better: redesign to have order_partitioned reference other tables, not be referenced.
```

---

## Try It Yourself

```sql
-- 1. Create a range-partitioned version of the audit_log table partitioned by
--    changed_at, with quarterly partitions for 2024 (Q1–Q4) and a default partition.

-- 2. Insert 5 audit log entries spread across different quarters of 2024.
--    Query the parent table and use SELECT tableoid::regclass to confirm each row
--    landed in the correct partition.

-- 3. Run EXPLAIN on the parent table with a date range filter. Confirm partition
--    pruning is working by checking the number of partitions scanned.

-- 4. Create a covering index on the partitioned audit_log (table_name, changed_at)
--    INCLUDE (operation, row_id). Verify child partitions each got the index.
```

<details>
<summary>Show solutions</summary>

```sql
-- 1. Partitioned audit_log by quarter:
CREATE TABLE audit_log_p (
    id          BIGSERIAL,
    table_name  TEXT         NOT NULL,
    operation   TEXT         NOT NULL,
    row_id      BIGINT,
    old_data    JSONB,
    new_data    JSONB,
    changed_by  TEXT,
    changed_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    PRIMARY KEY (id, changed_at)
) PARTITION BY RANGE (changed_at);

CREATE TABLE audit_log_p_2024q1 PARTITION OF audit_log_p
    FOR VALUES FROM ('2024-01-01') TO ('2024-04-01');
CREATE TABLE audit_log_p_2024q2 PARTITION OF audit_log_p
    FOR VALUES FROM ('2024-04-01') TO ('2024-07-01');
CREATE TABLE audit_log_p_2024q3 PARTITION OF audit_log_p
    FOR VALUES FROM ('2024-07-01') TO ('2024-10-01');
CREATE TABLE audit_log_p_2024q4 PARTITION OF audit_log_p
    FOR VALUES FROM ('2024-10-01') TO ('2025-01-01');
CREATE TABLE audit_log_p_default PARTITION OF audit_log_p DEFAULT;

-- 2. Insert and verify partitions:
INSERT INTO audit_log_p (table_name, operation, row_id, changed_at) VALUES
    ('product',  'UPDATE', 1, '2024-02-10 10:00:00+00'),
    ('order',    'INSERT', 5, '2024-05-22 14:30:00+00'),
    ('customer', 'UPDATE', 3, '2024-08-01 09:00:00+00'),
    ('review',   'DELETE', 7, '2024-11-11 16:45:00+00'),
    ('product',  'INSERT', 9, '2025-01-15 08:00:00+00');  -- goes to default

SELECT tableoid::regclass AS partition, id, table_name, changed_at
FROM audit_log_p
ORDER BY changed_at;

-- 3. Partition pruning via EXPLAIN:
EXPLAIN SELECT * FROM audit_log_p
WHERE changed_at BETWEEN '2024-07-01' AND '2024-09-30';
-- Should show only audit_log_p_2024q3 being scanned.

-- 4. Covering index propagates to all children:
CREATE INDEX CONCURRENTLY ON audit_log_p (table_name, changed_at)
    INCLUDE (operation, row_id);

SELECT indexname, tablename
FROM pg_indexes
WHERE tablename LIKE 'audit_log_p%'
ORDER BY tablename;
```

</details>

---

## Capstone Connection

StoreForge's partitioning strategy:
- **`"order"` table** — RANGE partitioned by `created_at`, yearly partitions; year-old partitions are `DETACH`ed and moved to cold storage (S3 via `aws_s3` extension)
- **`audit_log`** — RANGE partitioned by `changed_at`, quarterly; partition pruning makes compliance queries (90-day audit windows) dramatically faster
- **Partition automation** — a `pg_cron` job runs on the 1st of each month to `CREATE` next month's partition and `DETACH` the partition from 13 months ago
- **Indexes** — all secondary indexes (`customer_id`, `table_name`) are defined on the parent and propagate automatically to new partitions
