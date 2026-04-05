# Logical Replication

## Concept

**Logical replication** replicates individual rows of data (DML operations) rather than raw WAL bytes. Unlike streaming replication — which replicates the entire cluster — logical replication can replicate **specific tables**, **specific DML types** (e.g., only INSERT), or from one database version to another. This makes it the right tool for zero-downtime major version upgrades, selective data syncing, and multi-direction data flows.

---

## Logical vs Streaming Replication

| Feature | Streaming (Physical) | Logical |
|---------|---------------------|---------|
| Unit of replication | WAL bytes (block-level) | Rows (decoded DML) |
| Target must be identical schema? | Yes — exact replica | No — can be a subset |
| Cross-version replication | No | Yes (PG 10→16) |
| Selective tables | No | Yes |
| Subscriber can accept writes? | No (read-only) | Yes (non-replicated tables) |
| Use case | HA, read replicas | Upgrades, data syncing |

---

## Publications

A **publication** defines *what* to replicate — which tables and which DML operations:

```sql
-- Publish all operations on all tables:
CREATE PUBLICATION storeforge_pub FOR ALL TABLES;

-- Publish specific tables only:
CREATE PUBLICATION product_catalog_pub FOR TABLE
    category, product;

-- Publish specific operations only:
CREATE PUBLICATION orders_insert_only_pub FOR TABLE "order", order_item
    WITH (publish = 'insert');

-- Publish INSERT and UPDATE, but not DELETE:
CREATE PUBLICATION product_sync_pub FOR TABLE product
    WITH (publish = 'insert, update');

-- View existing publications:
SELECT pubname, puballtables, pubinsert, pubupdate, pubdelete, pubtruncate
FROM pg_publication;

-- View which tables are in a publication:
SELECT tablename
FROM pg_publication_tables
WHERE pubname = 'storeforge_pub';

-- Add a table to an existing publication:
ALTER PUBLICATION product_catalog_pub ADD TABLE address;

-- Drop a publication:
DROP PUBLICATION storeforge_pub;
```

---

## Subscriptions

A **subscription** defines *where* to receive the data — connecting to a publisher and applying changes:

```sql
-- On the SUBSCRIBER database:
-- First ensure wal_level = logical on the PUBLISHER (requires restart):
-- SHOW wal_level;  -- must be 'logical'

-- Ensure the replica identity is set (needed for UPDATE/DELETE replication):
ALTER TABLE product REPLICA IDENTITY FULL;      -- sends full old row, safest
ALTER TABLE "order" REPLICA IDENTITY DEFAULT;   -- uses primary key (default, preferred)
-- REPLICA IDENTITY NOTHING means UPDATE/DELETE on this table cannot be replicated.

-- Create a subscription on the subscriber:
CREATE SUBSCRIPTION storeforge_sub
    CONNECTION 'host=primary-server port=5432 dbname=storeforge_dev user=replicator password=replsecret'
    PUBLICATION storeforge_pub;

-- The subscription immediately starts syncing:
-- 1. Initial data copy (table sync): copies existing rows
-- 2. Ongoing stream: applies INSERT/UPDATE/DELETE as they happen

-- View subscriptions:
SELECT subname, subenabled, subpublications, subslotname
FROM pg_subscription;

-- Monitor subscription progress:
SELECT
    subname,
    srrelid::regclass AS table,
    srsubstate,   -- 'r' = ready (streaming), 'i' = initial sync
    srsyncedlsn
FROM pg_subscription_rel;

-- Check for replication worker errors:
SELECT * FROM pg_stat_subscription;
```

---

## Logical Replication Slots

```sql
-- Subscriptions automatically create a replication slot on the publisher:
SELECT slot_name, plugin, active, confirmed_flush_lsn
FROM pg_replication_slots
WHERE slot_type = 'logical';
-- plugin = 'pgoutput' is the built-in logical replication decoder

-- Monitor lag: if confirmed_flush_lsn << current LSN, the subscriber is behind:
SELECT slot_name,
       pg_wal_lsn_diff(pg_current_wal_lsn(), confirmed_flush_lsn) AS lag_bytes
FROM pg_replication_slots
WHERE slot_type = 'logical';

-- ⚠️  DANGER: if a subscription disconnects and the slot is never dropped,
-- WAL accumulates without bound, potentially filling the disk.
-- Always drop unused slots:
SELECT pg_drop_replication_slot('storeforge_sub');
```

---

## Zero-Downtime Major Version Upgrade

The recommended logical replication upgrade path from PG 15 → PG 16:

```
Step 1: Install PG 16 alongside PG 15 (different port)
Step 2: Create an empty PG 16 database with the same schema
Step 3: Create a subscription on PG 16 pointing at PG 15 publication
Step 4: Wait for initial sync to complete (srsubstate = 'r' for all tables)
Step 5: Verify lag_bytes ≈ 0
Step 6: Put PG 15 in read-only mode (revoke write privileges, or use pg_hba.conf)
Step 7: Let PG 16 catch up to final LSN
Step 8: Promote PG 16 (drop subscription) and update app connection strings
Step 9: Decommission PG 15
```

```sql
-- Step 2: Create matching schema on PG 16 subscriber:
-- (restore schema-only dump from PG 15)

-- Step 3: Create subscription on PG 16:
CREATE SUBSCRIPTION upgrade_sub
    CONNECTION 'host=pg15-server port=5432 dbname=storeforge_dev user=replicator password=replsecret'
    PUBLICATION storeforge_pub;

-- Step 4: Wait for sync:
SELECT srrelid::regclass, srsubstate
FROM pg_subscription_rel
ORDER BY srsubstate;  -- wait until all rows show 'r'

-- Step 5: Verify lag:
SELECT slot_name,
       pg_wal_lsn_diff(pg_current_wal_lsn(), confirmed_flush_lsn) AS lag_bytes
FROM pg_replication_slots;   -- run on PG 15

-- Step 8: Drop subscription on PG 16 (promotes it to standalone):
DROP SUBSCRIPTION upgrade_sub;
```

---

## Row Filters (PG 15+)

```sql
-- Replicate only active products to a subscriber:
CREATE PUBLICATION active_products_pub FOR TABLE product
    WHERE (is_active = TRUE);

-- Replicate only delivered orders:
CREATE PUBLICATION delivered_orders_pub FOR TABLE "order"
    WHERE (status = 'delivered');
```

---

## Try It Yourself

```sql
-- 1. On your storeforge_dev database, set wal_level to 'logical'.
--    What restart is required? After restarting, verify with SHOW wal_level.

-- 2. Create a publication called 'product_catalog_pub' that publishes
--    INSERT and UPDATE (but NOT DELETE) operations on the product and category tables.

-- 3. Set REPLICA IDENTITY FULL on the customer table and REPLICA IDENTITY DEFAULT
--    on the "order" table. What is the difference and when would you choose each?

-- 4. Check pg_replication_slots after you drop a subscription.
--    Does the slot get cleaned up automatically? What happens if it doesn't?
```

<details>
<summary>Show solutions</summary>

```sql
-- 1. Enable logical replication:
-- In postgresql.conf:
-- wal_level = logical      (requires PostgreSQL restart)
-- After restart:
SHOW wal_level;  -- should return 'logical'

-- 2. Create publication:
CREATE PUBLICATION product_catalog_pub FOR TABLE product, category
    WITH (publish = 'insert, update');

SELECT pubname, pubinsert, pubupdate, pubdelete
FROM pg_publication WHERE pubname = 'product_catalog_pub';
-- pubdelete should be FALSE.

-- View tables included:
SELECT tablename FROM pg_publication_tables WHERE pubname = 'product_catalog_pub';

-- 3. Replica identity:
ALTER TABLE customer REPLICA IDENTITY FULL;
ALTER TABLE "order" REPLICA IDENTITY DEFAULT;

-- Difference:
-- REPLICA IDENTITY FULL: sends the entire old row with every UPDATE/DELETE.
-- Guarantees the subscriber can identify the row even without a PK.
-- More WAL/bandwidth. Use when PK is missing or composite and unreliable.

-- REPLICA IDENTITY DEFAULT (uses PK): sends only PK values in the old row image.
-- Efficient in WAL. Requires a primary key. Use for most tables.

-- 4. Slot cleanup:
-- Dropping a subscription with 'CREATE SUBSCRIPTION ... WITH (slot_name = ...)' 
-- does NOT automatically drop the slot on the publisher.
-- DROP SUBSCRIPTION does drop the slot IF the subscription was created without 
-- specifying an external slot (default behavior).

-- If the slot is not cleaned up:
-- WAL accumulates without limit → disk fills up.
-- Always verify after dropping a subscription:
SELECT slot_name FROM pg_replication_slots;
-- Manually drop if needed:
-- SELECT pg_drop_replication_slot('storeforge_sub');
```

</details>

---

## Capstone Connection

StoreForge uses logical replication for:
- **Major version upgrades** — the PG 15→16 upgrade was executed using the logical replication approach with < 30 seconds of application downtime (the cutover window)
- **Analytics database** — a separate `storeforge_analytics` PostgreSQL instance receives a publication of `"order"`, `order_item`, `product`, and `review` — the analytics DB has additional columns and materialized views not present in the OLTP database
- **Data warehouse pre-processing** — INSERT-only logical publication feeds an event stream into a Debezium connector which routes row changes to Apache Kafka, then to Snowflake
