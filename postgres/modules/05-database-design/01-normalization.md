# Normalization

## Concept

Normalization is the process of structuring tables to reduce redundancy and prevent update anomalies. Each normal form eliminates a specific class of data integrity problem. In practice, most production schemas target 3NF — stopping at BCNF or beyond typically introduces complexity that hurts join performance without a meaningful quality benefit. Understanding normalizing forms also makes you better at intentional denormalization (which has legitimate performance uses).

---

## Why Normalization Matters

Without normalization, modifying data requires finding and updating the same value in multiple rows. If you miss any, the data becomes inconsistent (an "update anomaly"). There are three types:

- **Insertion anomaly** — you can't add data without unrelated data existing first
- **Update anomaly** — changing one fact requires updating multiple rows, risking inconsistency
- **Deletion anomaly** — deleting one record accidentally destroys other independent facts

---

## First Normal Form (1NF)

**Rule:** Every column holds a single, atomic value. No repeating groups; no multi-value columns.

**Violation example:**
```
customer_id | name  | phone_numbers
1           | Alice | '555-1111, 555-2222'   ← multi-value: NOT 1NF
2           | Bob   | '555-3333'
```

**Fix:**
```sql
-- Turn repeating values into their own table:
CREATE TABLE customer_phone (
    id          SERIAL PRIMARY KEY,
    customer_id INTEGER REFERENCES customer(id),
    phone       TEXT NOT NULL,
    phone_type  TEXT DEFAULT 'mobile'
);
```

PostgreSQL `ARRAY` and `JSONB` types technically violate strict 1NF — their use should be deliberate (e.g., for truly variable-length attributes) and not a substitute for proper normalization.

---

## Second Normal Form (2NF)

**Rule:** Must be in 1NF, AND every non-key attribute depends on the **whole** primary key (not just part of it). Only relevant when using composite keys.

**Violation example:**
```
order_id | product_id | product_name | quantity | unit_price
101      | 42         | 'Headphones' | 2        | 79.99   ← product_name depends on product_id alone
101      | 55         | 'Keyboard'   | 1        | 129.99
```

`product_name` depends only on `product_id`, not on `(order_id, product_id)`. Updating the name in `product` won't update historical `order_item` rows.

**Fix:** Move `product_name` to the `product` table. The `order_item` table only keeps what truly depends on the full composite key:

```sql
-- In StoreForge: order_item stores only quantity and unit_price
-- (unit_price is snapshotted intentionally — historical price at time of order)
CREATE TABLE order_item (
    id          SERIAL PRIMARY KEY,
    order_id    INTEGER REFERENCES "order"(id) ON DELETE CASCADE,
    product_id  INTEGER REFERENCES product(id) ON DELETE RESTRICT,
    quantity    INTEGER NOT NULL CHECK (quantity > 0),
    unit_price  NUMERIC(10,2) NOT NULL  -- snapshot at order time, not FK to product.price
);
```

---

## Third Normal Form (3NF)

**Rule:** Must be in 2NF, AND no non-key attribute depends on another non-key attribute (no transitive dependencies).

**Violation example:**
```
order_id | customer_id | customer_city | customer_zip
101      | 7           | 'Austin'      | '78701'     ← city depends on zip, not order_id
```

`customer_city` depends on `customer_zip`, which in turn depends on `customer_id`. If a customer moves, you update `customer_city` — but old orders still show the old city.

**Fix:** Move address data to its own table (as StoreForge does with the `address` table):

```sql
-- StoreForge: address is properly separated from customer and order:
CREATE TABLE address (
    id          SERIAL PRIMARY KEY,
    customer_id INTEGER NOT NULL REFERENCES customer(id) ON DELETE CASCADE,
    line1       TEXT NOT NULL,
    city        TEXT NOT NULL,
    state       TEXT NOT NULL,
    country     CHAR(2) NOT NULL,
    postal_code TEXT NOT NULL,
    is_default  BOOLEAN NOT NULL DEFAULT false
);

-- Orders reference the address at time of order:
-- order.shipping_address_id → address.id
-- This is INTENTIONAL and valid — not a 3NF violation because we want the snapshot
```

---

## BCNF (Boyce-Codd Normal Form)

**Rule:** Must be in 3NF, AND for every functional dependency X → Y, X must be a superkey.

BCNF handles a rare case where 3NF allows some redundancy due to overlapping candidate keys. In practice, most 3NF tables are also in BCNF. BCNF can force table decompositions that make some queries harder — consider carefully.

---

## 4NF and Beyond

**4NF** eliminates multi-valued dependencies (a table has two independent sets of multi-valued facts — separate them). **5NF** handles join dependencies (very rare). In practice, you'll almost never encounter tables requiring 4NF or 5NF in typical applications.

---

## Denormalization: The Intentional Trade-Off

Sometimes controlled denormalization improves read performance at the cost of storage and write complexity:

```sql
-- StoreForge: product.unit_price is DENORMALIZED into order_item intentionally.
-- We don't JOIN back to product.price for historical orders — prices change!
-- The snapshot in order_item.unit_price is correct and desired.

-- Denormalized total_amount in "order":
-- order.total_amount = SUM(order_item.quantity * order_item.unit_price)
-- We store it for fast display without a SUM JOIN every page load.
-- Risk: if order_item rows are modified independently, total_amount can drift.
-- Mitigation: use a trigger to update total_amount, or always recompute it.

-- Materialized views (Module 05 lesson 05) are a structured form of denormalization
-- with controlled refresh semantics.
```

---

## StoreForge Normalization Audit

| Column | Table | Normal Form | Notes |
|--------|-------|-------------|-------|
| `product.price` | `product` | ✅ 3NF | Fully determined by PK |
| `order_item.unit_price` | `order_item` | ✅ Intentional denorm | Historical price snapshot |
| `order.total_amount` | `order` | ⚠️ Derived | Redundant with SUM(order_item), but cached |
| `address` data separate | both `customer` and `order` | ✅ 3NF | Correct separation |
| `category.parent_id` | `category` | ✅ Valid self-ref | Hierarchical data, expected |

---

## Try It Yourself

```sql
-- Evaluate the following un-normalized table and write the normalized schema:
-- orders_flat:
-- order_id, order_date, customer_id, customer_email, customer_city,
-- product_id, product_name, category_name, qty, price_per_unit

-- 1. Identify all 1NF, 2NF, and 3NF violations.
-- 2. Write the normalized CREATE TABLE statements.
-- 3. Write the INSERT statements to migrate sample data into the normalized tables.
```

<details>
<summary>Show normalized schema</summary>

```sql
-- Violations:
-- 1NF: Assuming each row has one product (OK), but category_name is multi-value if a product
--      could be in multiple categories. Add category table.
-- 2NF: customer_email, customer_city depend on customer_id, not (order_id, product_id).
--      product_name, category_name depend on product_id alone.
-- 3NF: customer_city might depend on customer_zip (transitive) if zip is present.

-- Normalized tables:
CREATE TABLE category (
    id   SERIAL PRIMARY KEY,
    name TEXT NOT NULL UNIQUE
);

CREATE TABLE customer (
    id    SERIAL PRIMARY KEY,
    email TEXT NOT NULL UNIQUE,
    city  TEXT
);

CREATE TABLE product (
    id          SERIAL PRIMARY KEY,
    name        TEXT NOT NULL,
    category_id INTEGER REFERENCES category(id),
    is_active   BOOLEAN DEFAULT true
);

CREATE TABLE "order" (
    id          SERIAL PRIMARY KEY,
    customer_id INTEGER REFERENCES customer(id),
    order_date  TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE order_item (
    id             SERIAL PRIMARY KEY,
    order_id       INTEGER REFERENCES "order"(id),
    product_id     INTEGER REFERENCES product(id),
    qty            INTEGER NOT NULL,
    price_per_unit NUMERIC(10,2) NOT NULL  -- snapshot price
);
```

</details>

---

## Capstone Connection

StoreForge's schema is designed to 3NF:
- Customer address data is in a separate `address` table (not repeated in orders)
- Category names are in `category` (not repeated in `product.category_name`)
- `unit_price` in `order_item` is a deliberate 3NF departure — it's a historical snapshot of the price at order time, not a transitive dependency to `product.price`
- `order.total_amount` is a deliberate performance denormalization with a trigger to keep it current
