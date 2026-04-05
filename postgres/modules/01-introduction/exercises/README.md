# Module 01 Exercises: Introduction to PostgreSQL

## Overview

These exercises reinforce the conceptual foundation from Module 01. They are intentionally discussion- and analysis-focused — you are not yet required to have PostgreSQL running. When you encounter a SQL snippet, read it and predict what it does before checking the answer.

---

## Exercise 1 — Relational vs Document: Choose Your Tool

A startup is building a platform and considering both PostgreSQL and MongoDB. Evaluate each use case and decide which is more appropriate:

**A.** A customer order history system where orders must include line items, each linked to inventory. Cancellations must deduct stock atomically.

**B.** A product catalog where each product category has wildly different attributes (electronics have wattage/voltage; clothing has size/material; food has allergens/weight).

**C.** A social media feed where "posts" are self-contained blobs of text + media + reactions, and the schema is expected to change weekly.

**D.** A financial ledger where every debit must have a corresponding credit, and an incomplete entry is worse than no entry at all.

<details>
<summary>Discussion</summary>

**A. Order history → PostgreSQL**
Atomic deduction of stock + foreign key integrity across orders, order_items, and inventory is exactly what a relational database does best. MVCC ensures concurrent orders don't corrupt stock.

**B. Product catalog → PostgreSQL with JSONB** (or MongoDB)
This is genuinely either/or. PostgreSQL's JSONB column lets you store category-specific attributes in the same table as the structured data (price, name, stock). MongoDB is also a valid choice if the document-centric model fits your team better. For StoreForge, we use JSONB in PostgreSQL: one system, full SQL power.

**C. Social media feed → MongoDB** (or PostgreSQL)
Self-contained documents with weekly schema changes favor MongoDB's schemaless nature. That said, PostgreSQL with JSONB handles evolving schemas too — the trade-off is JSON query syntax vs. SQL.

**D. Financial ledger → PostgreSQL always**
ACID with `SERIALIZABLE` isolation is non-negotiable for double-entry bookkeeping. A ledger with partial entries is dangerous. PostgreSQL's transactional guarantees are exactly what you need.

</details>

---

## Exercise 2 — Identify Constraint Violations

Review the `product` table definition and identify what constraint each invalid INSERT violates:

```sql
CREATE TABLE product (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name           VARCHAR(200) NOT NULL,
    price          NUMERIC(10,2) NOT NULL CHECK (price >= 0),
    stock_quantity INTEGER NOT NULL DEFAULT 0 CHECK (stock_quantity >= 0),
    category_id    BIGINT NOT NULL REFERENCES category(id),
    slug           VARCHAR(200) NOT NULL UNIQUE
);
```

For each INSERT below, predict whether it succeeds or fails, and if it fails, state which constraint is violated:

```sql
-- A
INSERT INTO product (name, price, category_id, slug)
VALUES ('Widget', 9.99, 1, 'widget-001');

-- B
INSERT INTO product (name, price, stock_quantity, category_id, slug)
VALUES (NULL, 9.99, 10, 1, 'widget-002');

-- C
INSERT INTO product (name, price, stock_quantity, category_id, slug)
VALUES ('Gadget', -1.00, 10, 1, 'gadget-001');

-- D
INSERT INTO product (name, price, stock_quantity, category_id, slug)
VALUES ('Thing', 5.00, 10, 9999, 'thing-001');

-- E
INSERT INTO product (name, price, stock_quantity, category_id, slug)
VALUES ('Widget Copy', 9.99, 0, 1, 'widget-001');
```

<details>
<summary>Answers</summary>

**A — ✅ Succeeds**. `stock_quantity` has `DEFAULT 0`, so it's not required in the INSERT. All other NOT NULL columns are provided.

**B — ❌ Fails: NOT NULL violation**. `name` is `NOT NULL`; `NULL` violates this constraint.

**C — ❌ Fails: CHECK violation**. `price = -1.00` violates `CHECK (price >= 0)`.

**D — ❌ Fails: FOREIGN KEY violation**. `category_id = 9999` does not exist in the `category` table. PostgreSQL enforces referential integrity.

**E — ❌ Fails: UNIQUE violation**. `slug = 'widget-001'` was already inserted in statement A. The UNIQUE constraint prevents a duplicate.

</details>

---

## Exercise 3 — NULL Behavior

Predict the result of each SQL expression:

```sql
-- A
SELECT NULL = NULL;

-- B
SELECT NULL IS NULL;

-- C
SELECT NULL + 42;

-- D
SELECT COALESCE(NULL, NULL, 'fallback');

-- E: What is the count result?
-- (Assume review table has 5 rows: ratings = {5, 4, NULL, 3, NULL})
SELECT COUNT(*), COUNT(rating) FROM review;

-- F
SELECT NULL OR TRUE;
SELECT NULL AND FALSE;
```

<details>
<summary>Answers</summary>

**A → NULL** (not `TRUE`). NULL compared to anything with `=`, `<`, `>` returns NULL.

**B → TRUE**. The `IS NULL` operator is specifically designed for NULL testing.

**C → NULL**. NULL propagates through arithmetic — any NULL in an expression makes the whole expression NULL.

**D → 'fallback'**. COALESCE returns the first non-NULL argument. Both first two are NULL, so it returns `'fallback'`.

**E → COUNT(*) = 5, COUNT(rating) = 3**. `COUNT(*)` counts rows; `COUNT(column)` counts non-NULL values in that column.

**F:**
- `NULL OR TRUE → TRUE` — because TRUE OR anything is TRUE
- `NULL AND FALSE → FALSE` — because FALSE AND anything is FALSE
These are three-valued logic edge cases where the NULL value doesn't matter because one operand determines the result.

</details>

---

## Exercise 4 — Sketch the StoreForge Schema

Without looking at the full schema, sketch the tables you would need for StoreForge (an e-commerce platform) and their relationships. Include:

- The core entities (customers, products, orders)
- At least one junction/associative table
- At least one self-referencing table

Then answer:
1. What is the difference between `order.total_amount` and summing `order_item.unit_price * quantity`? Why might they differ?
2. Why does `order_item` capture `unit_price` instead of referencing `product.price`?
3. Why might `category` be a self-referencing table (with `parent_id`)?

<details>
<summary>Discussion</summary>

**Schema sketch:**
```
customer ─────────────────────────────────────────────────┐
    │ 1                                                    │
    │                                                      │ M
    ▼ M                                                    ▼
  order  ─────────────── order_item ──────────── product
    │ 1             M           M           1      │ M
    │                                              │
    ▼ M                                            ▼ 1
  address                                      category (parent_id → self)
                                                   │ 1
                                                   ▼ M
                                               category (children)
```

**Junction table:** `order_item` — connects `order` and `product` (many orders have many products, each order_item represents one product line in one order)

**Self-referencing:** `category.parent_id → category.id` — enables unlimited hierarchy depth (Electronics → Audio → Headphones)

**Answers:**
1. `order.total_amount` may include discounts, shipping costs, and taxes that aren't reflected in the sum of line items. Alternatively, it may be a cached value to avoid recalculating on every read.

2. `product.price` changes over time (sales, inflation, repricing). `unit_price` captures the price *at the moment of purchase* — historical order totals must remain accurate.

3. `category.parent_id` creates a tree structure. A `NULL` parent_id indicates a root category (Electronics). A non-null parent_id creates a subcategory. Recursive CTEs (Module 04) can traverse this tree.

</details>

---

## Exercise 5 — ACID Scenario Analysis

For each scenario, identify which ACID property is being tested and whether PostgreSQL handles it correctly:

**A.** A user places an order. The system inserts a row in `order`, then crashes before inserting `order_item`. After restart, there is a row in `order` with no items.

**B.** Two users simultaneously buy the last unit of a product. Both transactions read `stock_quantity = 1`, both proceed, and both `UPDATE stock_quantity = 0`. After both commit, `stock_quantity = 0` but two orders exist for the same unit.

**C.** A report query scanning millions of rows blocks an order insertion because it's reading the `product` table.

**D.** A long-running migration `ALTER TABLE product ADD COLUMN` acquires a lock and blocks reading the product table for 5 minutes during a busy period.

<details>
<summary>Analysis</summary>

**A — Violates Atomicity: INCORRECT behavior**
If the application code is NOT wrapped in a transaction, the two inserts run independently. PostgreSQL guarantees atomicity *within a transaction*. The fix: `BEGIN; INSERT INTO order ...; INSERT INTO order_item ...; COMMIT;` — if order_item fails, order is rolled back.

**B — Violates Isolation: Concurrency bug**
This is a classic race condition ("lost update"). Both transactions read the same value before either writes. The fix: use `SELECT ... FOR UPDATE` to lock the product row, or `UPDATE product SET stock_quantity = stock_quantity - 1 WHERE id = ? AND stock_quantity > 0` and check affected row count.

**C — Does NOT happen: MVCC prevents reader/writer blocking**
In PostgreSQL, readers don't block writers and writers don't block readers (with the exception of explicit `SELECT FOR UPDATE`). A SELECT scan does not block an INSERT. The report and the order proceed in parallel.

**D — Can happen: DDL takes an ACCESS EXCLUSIVE lock**
`ALTER TABLE` on a busy table can be problematic — it waits for existing queries to finish, and new queries wait behind the lock. The mitigation is `ALTER TABLE ... SET lock_timeout = '2s'` and using tools like `pg_repack` for online schema changes.

</details>

---

## Capstone Checkpoint ✅

Before moving to Module 02, confirm you can answer these questions without notes:

- [ ] What is the difference between a database, a schema, and a table in PostgreSQL?
- [ ] What does NULL mean, and why does `NULL = NULL` return NULL?
- [ ] Name the four ACID properties and give a one-sentence example of each in StoreForge
- [ ] What is MVCC and why does it matter for concurrent users?
- [ ] Name three features PostgreSQL has that MySQL 5.x does not
- [ ] Why does `order_item` have its own `unit_price` column instead of using `product.price`?
