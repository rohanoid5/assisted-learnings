# Transactions — Atomicity and Isolation

## Concept

A transaction is a unit of work that is either fully committed or fully rolled back — it never leaves the database in a partial state. PostgreSQL's transaction system is built on ACID guarantees, which become critical in multi-user e-commerce applications where two customers might concurrently try to buy the last item in stock.

---

## BEGIN, COMMIT, ROLLBACK

Every statement in PostgreSQL runs inside a transaction. For single statements, the transaction begins and commits automatically. For multi-statement work, use explicit control:

```sql
-- Start a transaction:
BEGIN;

-- Deduct stock from a product (part of placing an order):
UPDATE product
SET stock_quantity = stock_quantity - 2
WHERE id = 42 AND stock_quantity >= 2;

-- Create the order:
INSERT INTO "order" (customer_id, shipping_address_id, status, total_amount)
VALUES (7, 3, 'pending', 259.98)
RETURNING id;

-- Add order items:
INSERT INTO order_item (order_id, product_id, quantity, unit_price)
VALUES (currval('order_id_seq'), 42, 2, 129.99);

-- If everything succeeded:
COMMIT;

-- If anything went wrong, roll back everything:
-- ROLLBACK;
```

After `ROLLBACK`, the stock is restored, the order row is gone, and the `order_item` is gone. The database looks as if none of it ever happened.

---

## SAVEPOINT: Partial Rollback Within a Transaction

Savepoints let you roll back to a mid-transaction checkpoint without aborting the whole thing:

```sql
BEGIN;

INSERT INTO customer (name, email) VALUES ('Alice Smith', 'alice@example.com');

SAVEPOINT after_customer;

-- This might fail (duplicate email):
INSERT INTO customer (name, email) VALUES ('Alice Smith', 'alice@example.com');
-- ERROR: duplicate key value violates unique constraint

-- Roll back only to the savepoint, not the whole transaction:
ROLLBACK TO SAVEPOINT after_customer;

-- The first INSERT is still alive:
SELECT * FROM customer WHERE email = 'alice@example.com';

COMMIT;

-- Release a savepoint when no longer needed:
RELEASE SAVEPOINT after_customer;
```

---

## Isolation Levels

Isolation levels trade between consistency guarantees and concurrency performance:

| Level | Dirty Read | Non-Repeatable Read | Phantom Read |
|-------|-----------|--------------------|----|
| `READ UNCOMMITTED` | Not in PG* | Possible | Possible |
| `READ COMMITTED` (default) | Prevented | Possible | Possible |
| `REPEATABLE READ` | Prevented | Prevented | Prevented (PG) |
| `SERIALIZABLE` | Prevented | Prevented | Prevented |

\* PostgreSQL doesn't actually allow dirty reads even at `READ UNCOMMITTED`.

```sql
-- Set isolation level for a single transaction:
BEGIN TRANSACTION ISOLATION LEVEL REPEATABLE READ;

-- Or:
BEGIN;
SET TRANSACTION ISOLATION LEVEL SERIALIZABLE;

-- Check current isolation level:
SHOW transaction_isolation;
```

### When to Use Which Level

```sql
-- READ COMMITTED (default): Good for most OLTP queries
-- Even within one transaction, you'll see committed changes by others:
BEGIN;
SELECT stock_quantity FROM product WHERE id = 1;  -- returns 50

-- Another session commits: UPDATE product SET stock_quantity = 0 WHERE id = 1;

SELECT stock_quantity FROM product WHERE id = 1;  -- returns 0 (different!!)
COMMIT;

-- REPEATABLE READ: Use for reports / analytics that span multiple tables
-- Within one transaction, you see a consistent snapshot:
BEGIN TRANSACTION ISOLATION LEVEL REPEATABLE READ;
SELECT SUM(total_amount) FROM "order" WHERE status = 'pending';
-- ... do more work ...
SELECT COUNT(*) FROM order_item WHERE ...;  -- consistent with above snapshot
COMMIT;

-- SERIALIZABLE: Use for financial operations that must be logically sequential
BEGIN TRANSACTION ISOLATION LEVEL SERIALIZABLE;
-- Place order only if inventory is available:
SELECT stock_quantity FROM product WHERE id = 1 FOR UPDATE;
UPDATE product SET stock_quantity = stock_quantity - 1 WHERE id = 1;
COMMIT;
-- Will raise serialization_failure (40001) if concurrent transactions conflict.
-- Application must retry on 40001.
```

---

## Locking: SELECT FOR UPDATE and SELECT FOR SHARE

Explicit row locks prevent race conditions in high-concurrency scenarios:

```sql
-- SELECT FOR UPDATE: Lock the row, block other FOR UPDATE selectors
-- Use case: "check and act" in the same transaction

BEGIN;
-- Lock the product row so nobody else can change the stock concurrently:
SELECT id, stock_quantity
FROM product
WHERE id = 42 AND stock_quantity > 0
FOR UPDATE;

-- Nobody else can UPDATE this row until we COMMIT or ROLLBACK:
UPDATE product
SET stock_quantity = stock_quantity - 1
WHERE id = 42;

INSERT INTO order_item (order_id, product_id, quantity, unit_price)
VALUES (101, 42, 1, 79.99);

COMMIT;
```

```sql
-- SELECT FOR SHARE: Read lock (blocks updates, not other reads)
-- Use case: You need to read a record and ensure nobody deletes it while you work:
BEGIN;
SELECT * FROM customer WHERE id = 7 FOR SHARE;
-- Other transactions can still SELECT FOR SHARE — but not FOR UPDATE or DELETE
COMMIT;
```

```sql
-- NOWAIT: Fail immediately instead of waiting for a lock:
SELECT * FROM product WHERE id = 42 FOR UPDATE NOWAIT;
-- ERROR: could not obtain lock on row in relation "product"

-- SKIP LOCKED: Skip rows that are locked (for job queue patterns):
SELECT * FROM order_item
WHERE processed = false
ORDER BY id
FOR UPDATE SKIP LOCKED
LIMIT 10;
-- Perfect for worker processes picking tasks without contention
```

---

## Deadlocks

A deadlock occurs when two transactions each hold a lock the other needs:

```sql
-- Session A:
BEGIN;
UPDATE product SET stock_quantity = stock_quantity - 1 WHERE id = 1;  -- locks row 1
-- (stalls waiting for B to release row 2)
UPDATE product SET stock_quantity = stock_quantity - 1 WHERE id = 2;

-- Session B (concurrent):
BEGIN;
UPDATE product SET stock_quantity = stock_quantity - 1 WHERE id = 2;  -- locks row 2
-- (stalls waiting for A to release row 1)
UPDATE product SET stock_quantity = stock_quantity - 1 WHERE id = 1;

-- PostgreSQL detects the cycle and kills one transaction:
-- ERROR: deadlock detected
-- DETAIL: Process 12345 waits for ShareLock on transaction 678; blocked by process 99
```

**Prevention:** Always acquire locks in consistent order (e.g., always lock lower ID first).

---

## Try It Yourself

```sql
-- 1. Start a transaction and simulate a partial failure (stock too low):
BEGIN;

-- Check stock (only buy if available):
SELECT id, name, stock_quantity FROM product WHERE id = 1;

-- If stock > 0, try this update:
UPDATE product
SET stock_quantity = stock_quantity - 100  -- intentionally too many
WHERE id = 1 AND stock_quantity >= 100;

-- Check rows updated (should be 0 if stock < 100):
-- 0 rows affected means the UPDATE silently did nothing
-- Use an explicit check:
DO $$
BEGIN
    IF NOT FOUND THEN
        RAISE EXCEPTION 'Insufficient stock for product 1';
    END IF;
END $$;

-- The RAISE EXCEPTION aborts the transaction, but ROLLBACK is cleaner:
ROLLBACK;

-- 2. Verify: stock should be unchanged:
SELECT stock_quantity FROM product WHERE id = 1;

-- 3. Try SAVEPOINT:
BEGIN;
SAVEPOINT pre_insert;
INSERT INTO customer (name, email) VALUES ('Test User', 'test@storeforge.com');
-- If this is the second run, it may fail on unique email:
-- ROLLBACK TO SAVEPOINT pre_insert;
COMMIT;
```

<details>
<summary>Key takeaway</summary>

After a `ROLLBACK`, the product's `stock_quantity` is exactly what it was before the transaction started — as if the UPDATE never happened. The `SAVEPOINT` lets you recover from a specific failed statement without losing all other work in the transaction.

</details>

---

## Capstone Connection

StoreForge's `place_order()` stored procedure (defined in the capstone SQL files) runs inside an explicit transaction:
1. `SELECT ... FOR UPDATE` on all ordered products (locks rows, checks stock)
2. `UPDATE product` to deduct stock quantities
3. `INSERT INTO "order"` to create the order record
4. `INSERT INTO order_item` for each line item
5. `COMMIT` — only now are all changes visible to other sessions

If any step fails (e.g., one product runs out of stock mid-insert), the whole transaction rolls back and inventory stays consistent.
