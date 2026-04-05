# High-Level Concepts: ACID, MVCC, WAL, and Query Processing

## Concept

Four concepts underpin how PostgreSQL works internally. You don't need to implement them, but understanding them explains behaviors you'll observe: why concurrent queries don't block each other, why PostgreSQL survives crashes, what "isolation level" means, and why `EXPLAIN` output looks the way it does.

---

## ACID: The Four Guarantees

ACID stands for **Atomicity, Consistency, Isolation, Durability**. These are transaction guarantees — they define what PostgreSQL promises when you wrap operations in a transaction.

### Atomicity — all or nothing

Every statement in a transaction either commits completely or rolls back completely. There is no partial result.

```sql
-- StoreForge order placement: must be atomic
BEGIN;

INSERT INTO "order" (customer_id, status, total_amount)
VALUES (42, 'pending', 149.99)
RETURNING id \gset order_

INSERT INTO order_item (order_id, product_id, quantity, unit_price)
VALUES (:order_id, 7, 2, 74.995);

UPDATE product SET stock_quantity = stock_quantity - 2
WHERE id = 7;

COMMIT;
-- Either all three statements succeed, or none of them do.
```

If the `UPDATE` fails (e.g., stock goes negative and a CHECK constraint fires), PostgreSQL rolls back both the `INSERT INTO order` and the `INSERT INTO order_item`. The database never holds a half-placed order.

### Consistency — constraints are always enforced

After every transaction, the database must be in a valid state — all constraints (NOT NULL, CHECK, FOREIGN KEY, UNIQUE) are satisfied.

```sql
-- This transaction will be rolled back — unit_price < 0 violates CHECK
BEGIN;
INSERT INTO order_item (order_id, product_id, quantity, unit_price)
VALUES (1, 7, 1, -5.00);  -- ⚠️ violates CHECK (unit_price >= 0)
COMMIT;
-- ERROR: new row for relation "order_item" violates check constraint
-- The entire transaction is rolled back
```

### Isolation — concurrent transactions don't see each other's in-progress work

PostgreSQL provides several isolation levels (defined in SQL standard):

| Level | Dirty Read | Non-Repeatable Read | Phantom Read |
|---|---|---|---|
| **Read Uncommitted** | Possible (PG: treated as RC) | Possible | Possible |
| **Read Committed** (default) | ✅ Prevented | Possible | Possible |
| **Repeatable Read** | ✅ Prevented | ✅ Prevented | Possible (PG: prevented) |
| **Serializable** | ✅ Prevented | ✅ Prevented | ✅ Prevented |

```sql
-- Default: Read Committed
BEGIN;                                  -- Transaction A
SELECT stock_quantity FROM product WHERE id = 7;  -- sees 150

-- Meanwhile Transaction B commits:
-- UPDATE product SET stock_quantity = 100 WHERE id = 7; COMMIT;

SELECT stock_quantity FROM product WHERE id = 7;  -- now sees 100 ← non-repeatable read
COMMIT;

-- Use REPEATABLE READ if you need a consistent snapshot:
BEGIN ISOLATION LEVEL REPEATABLE READ;
SELECT stock_quantity FROM product WHERE id = 7;  -- 100
-- ... Transaction B changes to 50 and commits ...
SELECT stock_quantity FROM product WHERE id = 7;  -- still 100 (snapshot held)
COMMIT;
```

### Durability — committed data survives crashes

Once PostgreSQL returns `COMMIT`, the data is on disk. Even if the server crashes immediately after, the committed data will be there when PostgreSQL restarts.

PostgreSQL achieves this through the **Write-Ahead Log (WAL)** — covered below.

---

## MVCC: Readers Don't Block Writers

**Multi-Version Concurrency Control (MVCC)** is the mechanism that makes PostgreSQL's isolation work efficiently. Instead of locking rows read by a query, PostgreSQL keeps multiple versions of each row.

```
Without MVCC (pessimistic locking):
┌──────────────────────────────────────────────────────┐
│ Writer: UPDATE product SET price = 89.99 WHERE id=7  │
│   → Locks the row                                    │
│                                                      │
│ Reader: SELECT * FROM product WHERE id=7             │
│   → WAITS for writer to unlock ⏳                    │
└──────────────────────────────────────────────────────┘

With MVCC (PostgreSQL):
┌──────────────────────────────────────────────────────┐
│ Writer: UPDATE product SET price = 89.99 WHERE id=7  │
│   → Creates NEW version of the row (xmax/xmin)      │
│   → Old version stays visible to existing readers    │
│                                                      │
│ Reader: SELECT * FROM product WHERE id=7             │
│   → Reads OLD version immediately — no wait ✅       │
└──────────────────────────────────────────────────────┘
```

### How MVCC works internally

Every row in PostgreSQL has hidden system columns:

```sql
-- See the hidden system columns:
SELECT xmin, xmax, id, name, price
FROM product
WHERE id = 7;
```

| Column | Meaning |
|---|---|
| `xmin` | Transaction ID that inserted this row version |
| `xmax` | Transaction ID that deleted/updated this row (0 = still visible) |

When you `UPDATE` a row, PostgreSQL:
1. Marks the old row version with `xmax = current_transaction_id` (it's "dead" to future transactions)
2. Inserts a new row version with `xmin = current_transaction_id`

Concurrent readers see the version that was committed at the start of *their* snapshot — no locking needed.

### The cost of MVCC: dead tuples and VACUUM

Dead tuple versions accumulate. `VACUUM` reclaims them. This is why PostgreSQL has autovacuum running continuously in the background — dead tuples don't clean themselves up.

```sql
-- See dead tuples in a table:
SELECT relname, n_live_tup, n_dead_tup, last_autovacuum
FROM pg_stat_user_tables
WHERE relname = 'product';
```

---

## WAL: Write-Ahead Log — How Durability Works

The **Write-Ahead Log** is a sequential log of every change PostgreSQL makes, written *before* the change is applied to data files.

```
Commit flow:
  1. PostgreSQL gets COMMIT from client
  2. Writes change record to WAL (fast sequential write)
  3. Returns "COMMIT" to client  ← durability guarantee happens here
  4. Later: applies change to actual data files (async)

If crash happens between steps 3 and 4:
  On restart → PostgreSQL replays WAL → data restored ✅
```

WAL files are stored in `$PGDATA/pg_wal/`. Each WAL file is 16MB by default.

### WAL enables streaming replication

WAL records are also streamed to **standby servers**. The standby replays the same WAL, keeping an identical copy of the primary. This is how PostgreSQL replication works (Module 09).

```
Primary server:
  Writes: INSERT/UPDATE/DELETE
  → Writes to WAL
  → Sends WAL records to standby over TCP

Standby server:
  Receives WAL records
  → Replays them
  → Stays synchronized with primary
```

---

## Query Processing Pipeline

When you run `SELECT * FROM product WHERE price < 50`, PostgreSQL executes a pipeline:

```
SQL text input
      │
      ▼
┌─────────────┐
│    Parser   │ — Tokenizes SQL, builds parse tree; syntax errors caught here
└─────────────┘
      │
      ▼
┌─────────────┐
│  Rewriter   │ — Expands views, applies rules; your view becomes its definition
└─────────────┘
      │
      ▼
┌─────────────┐
│   Planner   │ — Uses statistics to choose the best execution plan
│  Optimizer  │   (sequential scan vs. index scan; hash join vs. nested loop)
└─────────────┘
      │
      ▼
┌─────────────┐
│  Executor   │ — Runs the plan, returns rows to client
└─────────────┘
```

### The planner uses statistics

The planner's decisions are based on statistics about your data, collected by `ANALYZE`:

```sql
-- Run analyze to update statistics:
ANALYZE product;

-- Check statistics:
SELECT attname, n_distinct, correlation
FROM pg_stats
WHERE tablename = 'product';
```

`n_distinct` tells the planner how many unique values exist. If `-1`, it means every row is unique (100% selectivity). The planner uses this to estimate row counts and choose indexes vs. sequential scans.

### EXPLAIN: See the plan

```sql
EXPLAIN SELECT * FROM product WHERE price < 50;
-- Seq Scan on product (cost=0.00..1.20 rows=3 width=...)
-- (with few rows, an index scan wouldn't be faster)

EXPLAIN ANALYZE SELECT * FROM product WHERE id = 7;
-- Index Scan using product_pkey on product (actual time=0.04..0.05 rows=1 loops=1)
```

You'll learn `EXPLAIN` in depth in Module 08 (Performance Tuning).

---

## Try It Yourself

Explore ACID and MVCC in action:

```sql
-- 1. Open two psql sessions (two terminal windows).

-- Session A:
BEGIN;
UPDATE product SET price = 999.00 WHERE id = 1;
-- Don't commit yet!

-- Session B (in a separate terminal):
SELECT id, name, price FROM product WHERE id = 1;
-- What price do you see? Why?

-- Session A: commit
COMMIT;

-- Session B:
SELECT id, name, price FROM product WHERE id = 1;
-- What price do you see now?

-- 2. Try violating a constraint atomically:
BEGIN;
  INSERT INTO "order" (customer_id, status, total_amount)
  VALUES (1, 'pending', 100.00);

  INSERT INTO order_item (order_id, product_id, quantity, unit_price)
  VALUES (currval('order_id_seq'), 9999, 1, 100.00);  -- product 9999 doesn't exist
COMMIT;
-- The whole transaction is rolled back (NO orphaned order record)
```

<details>
<summary>Expected observations</summary>

1. **MVCC in action**:
   - Session B sees the *original* price while Session A's transaction is open (the old row version is still visible)
   - After Session A commits, Session B sees the new price on the next query (Read Committed behavior — each statement sees the latest committed data)

2. **Atomicity**:
   - The invalid `product_id` triggers a foreign key violation
   - PostgreSQL rolls back the entire transaction — the `order` row is NOT persisted
   - Even though the first `INSERT` succeeded in isolation, the transaction is atomic and both inserts are undone

</details>

---

## Capstone Connection

All four ACID properties are critical to StoreForge:

- **Atomicity** — `place_order()` inserts order + order_items + updates stock in one transaction. All succeed or all rollback.
- **Consistency** — `CHECK (stock_quantity >= 0)` prevents overselling; `CHECK (unit_price >= 0)` prevents negative prices.
- **Isolation** — concurrent shoppers checking stock see consistent data; REPEATABLE READ prevents two buyers from over-purchasing the last unit.
- **Durability** — a crashed server means no lost orders after COMMIT.
- **MVCC** — hundreds of concurrent product view queries don't block the single inventory update running for an order placement.
- **WAL** — forms the foundation of Module 09's streaming replication setup for StoreForge HA.
