# The Relational Model: Relations, Tuples, Attributes, Domains, and Constraints

## Concept

The relational model has precise mathematical foundations, but you don't need to be a mathematician to benefit from understanding the terminology. The vocabulary — relation, tuple, attribute, domain — maps directly to the SQL objects you work with every day, and understanding it makes constraint design and schema normalization much clearer.

---

## From Math to SQL: The Mapping

| Relational Theory | SQL Equivalent | StoreForge Example |
|-------------------|----------------|--------------------|
| **Relation** | Table | `product` |
| **Tuple** | Row | One product record |
| **Attribute** | Column | `price`, `name`, `stock_quantity` |
| **Domain** | Data type + constraints | `price NUMERIC(10,2) CHECK (price >= 0)` |
| **Degree** | Number of columns | `product` has 9 attributes |
| **Cardinality** | Number of rows | `product` has 50 rows |
| **Primary key** | Minimal set of attributes that uniquely identifies a tuple | `product.id` |
| **Foreign key** | Attribute(s) referencing the primary key of another relation | `order_item.product_id → product.id` |

---

## Relations

A **relation** is a set of tuples that share the same structure (same attributes in the same domains). The key mathematical property: **a relation is a *set*, not a list**.

This has a practical implication: a "pure" relation has no duplicate rows and no inherent ordering. SQL relaxes this (you can have duplicate rows in a table without a primary key, and you can `ORDER BY`), but the relational model doesn't guarantee order.

```sql
-- This is a relation:
SELECT DISTINCT customer_id FROM "order";
-- Returns a set of unique customer_ids

-- This is NOT a relation (has duplicates by design):
SELECT customer_id FROM "order";
-- Returns a multiset (bag) — mathematical distinction, practical reality
```

In practice, your tables are *multisets* unless they have primary key constraints. The `DISTINCT` keyword converts a multiset result to a set.

---

## Tuples

A **tuple** is one row in a relation — an ordered list of attribute values. When you `INSERT INTO product ...`, you're adding a tuple to the relation.

A tuple's identity in a well-designed relational schema comes from its **primary key**, not its position:

```sql
INSERT INTO product (name, price, stock_quantity, category_id)
VALUES ('Wireless Headphones', 79.99, 150, 3)
RETURNING id;
-- Returns the generated id — this is the tuple's identity
```

---

## Attributes and Domains

An **attribute** is a named column in a relation. Each attribute has a **domain** — the set of valid values it can hold.

In SQL, domains are expressed through **data types** combined with **constraints**:

```sql
CREATE TABLE product (
    id             BIGINT GENERATED ALWAYS AS IDENTITY,
                   -- Domain: positive integers, auto-assigned

    name           VARCHAR(200) NOT NULL,
                   -- Domain: any string up to 200 chars, never null

    price          NUMERIC(10, 2) NOT NULL CHECK (price >= 0),
                   -- Domain: non-negative decimal with 2dp

    stock_quantity INTEGER NOT NULL DEFAULT 0 CHECK (stock_quantity >= 0),
                   -- Domain: non-negative integer, defaults to 0

    category_id    BIGINT NOT NULL REFERENCES category(id),
                   -- Domain: must exist in category.id

    attributes     JSONB NOT NULL DEFAULT '{}',
                   -- Domain: valid JSON object
    
    created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW()
                   -- Domain: any timestamp with timezone
);
```

The narrower your domain definition, the stronger your data quality guarantee.

---

## NULL: The Third Value

`NULL` represents **the absence of a value**. It is not zero, not an empty string, not false. This causes the famous "three-valued logic" in SQL:

```sql
-- These are all different:
SELECT NULL = NULL;      -- NULL (not TRUE!)
SELECT NULL IS NULL;     -- TRUE
SELECT NULL IS NOT NULL; -- FALSE

-- NULL propagates through arithmetic:
SELECT NULL + 5;         -- NULL
SELECT COALESCE(NULL, 0) + 5;  -- 5 (COALESCE returns first non-null)
```

**NULL trap — GROUP BY:**
```sql
-- If review.comment is NULL, those rows still participate in COUNT(*)
SELECT COUNT(*)         AS total_reviews,
       COUNT(comment)   AS reviews_with_comment  -- NULLs excluded from COUNT(col)
FROM review
WHERE product_id = 7;
```

**Design guideline:** Use `NOT NULL` as a default; add `NULL` only when absence of a value is genuinely meaningful. For StoreForge, `order_item.product_id` should be nullable if you want to support soft-deleted products; otherwise, `NOT NULL` + `ON DELETE RESTRICT`.

---

## Constraints: Making Domains Precise

PostgreSQL supports six fundamental constraint types:

### PRIMARY KEY
```sql
-- Uniquely identifies each tuple; implies NOT NULL + UNIQUE
PRIMARY KEY (id)
-- Or composite:
PRIMARY KEY (order_id, product_id)  -- for order_item
```

### FOREIGN KEY
```sql
-- Referential integrity: value must exist in referenced table
REFERENCES product(id) ON DELETE RESTRICT ON UPDATE CASCADE
```

**ON DELETE options:**
- `RESTRICT` — block the delete if referenced rows exist
- `CASCADE` — delete referencing rows too
- `SET NULL` — set the FK column to NULL
- `SET DEFAULT` — set to column's default value
- `NO ACTION` — deferred check (default)

### UNIQUE
```sql
-- One or more columns must be unique across the table
UNIQUE (email)
UNIQUE (order_id, product_id)  -- composite unique
```

### CHECK
```sql
-- Any boolean expression
CHECK (price >= 0)
CHECK (rating BETWEEN 1 AND 5)
CHECK (shipped_at IS NULL OR shipped_at >= created_at)
```

### NOT NULL
```sql
-- Column value must always be present
name TEXT NOT NULL
```

### EXCLUDE (PostgreSQL extension)
```sql
-- Generalizes UNIQUE — no two rows can satisfy the condition together
-- Requires the btree_gist extension
EXCLUDE USING gist (
    product_id WITH =,
    valid_during WITH &&   -- no overlapping date ranges for same product
)
```

---

## Keys: Primary, Foreign, Candidate, and Surrogate

```
candidate keys: {email}, {id} — any attribute(s) that uniquely identify a tuple
primary key: chosen from candidates — we choose {id} (surrogate)
natural key: {email} — has business meaning
surrogate key: {id} — generated, no business meaning, stable for foreign references
foreign key: order_item.product_id → product.id
```

**StoreForge key strategy:**
- Every table uses a generated surrogate key (`BIGINT GENERATED ALWAYS AS IDENTITY`) as primary key
- Natural keys (`customer.email`, `product.slug`) get a `UNIQUE` constraint to preserve their uniqueness guarantee
- Foreign keys always reference the surrogate PK

This pattern is pragmatic: surrogate keys are stable (email can change), compact (8 bytes vs. variable-length string), and index-efficient.

---

## Try It Yourself

Analyze the `order_item` table:

```sql
CREATE TABLE order_item (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    order_id    BIGINT         NOT NULL REFERENCES "order"(id) ON DELETE CASCADE,
    product_id  BIGINT         NOT NULL REFERENCES product(id) ON DELETE RESTRICT,
    quantity    INTEGER        NOT NULL CHECK (quantity > 0),
    unit_price  NUMERIC(10, 2) NOT NULL CHECK (unit_price >= 0)
);
```

Answer these questions:

1. What is the **degree** of this relation?
2. Why is there a separate `id` surrogate key instead of using `(order_id, product_id)` as the primary key?
3. `unit_price` stores the price at order time, not a reference to `product.price`. Why?
4. What happens if you try to `DELETE FROM "order" WHERE id = 5` and order 5 has order_items?

<details>
<summary>Answers</summary>

1. **Degree = 5** (id, order_id, product_id, quantity, unit_price — five attributes)

2. Using `(order_id, product_id)` as PK would prevent the same product appearing twice in an order. If you want multiple lines for the same product (e.g., different configurations), a surrogate key is needed. For StoreForge, the composite would be sufficient, but a surrogate key makes it easier to reference order_items from other tables (e.g., returns, complaints).

3. **Price at order time** — `product.price` can change after the order is placed. The `unit_price` column captures the price the customer actually paid. Historical order totals must remain accurate regardless of future price changes.

4. `ON DELETE CASCADE` means deleting the order automatically deletes its order_items. This is the correct behavior for `order → order_item` because an order_item has no meaning without its order.

</details>

---

## Capstone Connection

Every design decision in StoreForge maps to relational model concepts:

- `order_item.unit_price` — domain restricted to `NUMERIC(10,2) CHECK (unit_price >= 0)`, capturing historical truth
- `product.slug` — candidate key, enforced with `UNIQUE` alongside the surrogate PK
- `ON DELETE RESTRICT` on `order_item.product_id` — can't delete a product that's been ordered (data integrity)
- `ON DELETE CASCADE` on `order_item.order_id` — order_items are part of their order (existence dependency)
- `review.rating CHECK (rating BETWEEN 1 AND 5)` — domain enforcement at the table level
