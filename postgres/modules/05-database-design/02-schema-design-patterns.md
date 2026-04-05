# Schema Design Patterns

## Concept

Beyond normalization rules, experienced PostgreSQL developers apply a set of recurring schema design patterns that handle specific real-world requirements: polymorphic references, soft deletes, audit trails, and hierarchical data. Knowing these patterns — and their trade-offs — lets you design schemas that are both correct and practical to maintain.

---

## Soft Delete Pattern

Instead of physically removing rows (`DELETE`), mark them as deleted with a flag. Preserves history, enables undo, and maintains referential integrity.

```sql
-- Soft delete on customer:
ALTER TABLE product ADD COLUMN deleted_at TIMESTAMPTZ;
-- deleted_at IS NULL = "active"; IS NOT NULL = "deleted at that timestamp"

-- Soft delete a product:
UPDATE product
SET deleted_at = NOW()
WHERE id = 42;

-- All queries must filter it out:
SELECT * FROM product WHERE deleted_at IS NULL;

-- Create a partial index to only index active products:
CREATE INDEX idx_product_active ON product (category_id) WHERE deleted_at IS NULL;

-- Best practice: wrap in a view so every query doesn't need the WHERE:
CREATE VIEW active_products AS
    SELECT * FROM product WHERE deleted_at IS NULL;

-- Recover a soft-deleted product:
UPDATE product SET deleted_at = NULL WHERE id = 42;
```

**When to use:** When data has compliance/audit requirements (you can't truly delete it), or when deleting would break foreign key constraints you don't want to cascade.

**When NOT to use:** Real high-volume tables where soft deletes cause index bloat. Use archival (move to a history table) instead.

---

## Audit Trail Pattern

Track who changed what and when. Two common implementations: trigger-based audit log, or temporal tables.

```sql
-- StoreForge's audit_log table:
CREATE TABLE audit_log (
    id         BIGSERIAL PRIMARY KEY,
    table_name TEXT NOT NULL,
    operation  TEXT NOT NULL CHECK (operation IN ('INSERT', 'UPDATE', 'DELETE')),
    row_id     INTEGER NOT NULL,
    old_data   JSONB,
    new_data   JSONB,
    changed_by TEXT NOT NULL DEFAULT CURRENT_USER,
    changed_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Trigger function that fires on any table:
CREATE OR REPLACE FUNCTION audit_trigger_fn()
RETURNS TRIGGER AS $$
BEGIN
    INSERT INTO audit_log (table_name, operation, row_id, old_data, new_data)
    VALUES (
        TG_TABLE_NAME,
        TG_OP,
        CASE TG_OP WHEN 'DELETE' THEN OLD.id ELSE NEW.id END,
        CASE WHEN TG_OP = 'INSERT' THEN NULL ELSE row_to_json(OLD)::JSONB END,
        CASE WHEN TG_OP = 'DELETE' THEN NULL ELSE row_to_json(NEW)::JSONB END
    );
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Attach to any table:
CREATE TRIGGER audit_customer
AFTER INSERT OR UPDATE OR DELETE ON customer
FOR EACH ROW EXECUTE FUNCTION audit_trigger_fn();

-- Query audit history:
SELECT operation, old_data ->> 'email' AS old_email, new_data ->> 'email' AS new_email, changed_at
FROM audit_log
WHERE table_name = 'customer' AND row_id = 7
ORDER BY changed_at DESC;
```

---

## Hierarchical Data: Adjacency List vs LTREE

PostgreSQL supports multiple strategies for tree structures:

### Adjacency List (used by StoreForge)

```sql
-- Simple, flexible, native:
CREATE TABLE category (
    id        SERIAL PRIMARY KEY,
    name      TEXT NOT NULL,
    parent_id INTEGER REFERENCES category(id)  -- NULL = root
);

-- Query tree: use recursive CTE (see Module 04)
-- Pros: simple to write, easy to move nodes
-- Cons: recursive CTE required for tree traversal (n+1 queries if naively app-side)
```

### `ltree` Extension (path-based)

```sql
-- More efficient traversal, requires path maintenance:
CREATE EXTENSION IF NOT EXISTS ltree;

CREATE TABLE category_ltree (
    id   SERIAL PRIMARY KEY,
    name TEXT NOT NULL,
    path LTREE               -- e.g., 'Electronics.Audio.Headphones'
);

CREATE INDEX idx_category_path ON category_ltree USING GIST (path);

-- Find all descendants of Electronics:
SELECT * FROM category_ltree WHERE path <@ 'Electronics';

-- Find all ancestors of Headphones:
SELECT * FROM category_ltree WHERE 'Electronics.Audio.Headphones' <@ path;

-- Pros: very fast subtree queries with GiST index
-- Cons: path must be maintained on tree restructuring
```

---

## EAV vs JSONB: Variable Attributes

"Entity-Attribute-Value" tables tried to handle variable attributes before JSONB existed. Don't use EAV in PostgreSQL — use JSONB.

```sql
-- EAV anti-pattern (don't do this):
CREATE TABLE product_attribute (
    product_id INTEGER,
    attr_name  TEXT,
    attr_value TEXT  -- all values are TEXT, no type safety
);
-- Querying ALL attributes for a product requires many JOINs.
-- No indexing on specific attributes. No types. Terrible performance.

-- JSONB pattern (do this):
ALTER TABLE product ADD COLUMN attributes JSONB;
-- Query: WHERE attributes @> '{"wireless": true}'
-- Index: CREATE INDEX ON product USING GIN (attributes)
-- Full type safety per key, fast indexed lookups, no extra tables.
```

---

## Polymorphic Associations

When a single column needs to refer to rows in multiple different tables (e.g., `comment` can reference an `order` OR a `product`):

```sql
-- Anti-pattern (nullable FKs, no referential integrity):
CREATE TABLE comment (
    id         SERIAL PRIMARY KEY,
    body       TEXT,
    product_id INTEGER REFERENCES product(id),  -- NULL if for an order
    order_id   INTEGER REFERENCES "order"(id)   -- NULL if for a product
);
-- Problem: both nullable FKs, no way to enforce exactly-one-is-set at DB level.

-- Pattern 1: Separate comment tables (clearest, strictest):
CREATE TABLE product_comment (id SERIAL PRIMARY KEY, product_id INTEGER REFERENCES product(id), body TEXT);
CREATE TABLE order_comment   (id SERIAL PRIMARY KEY, order_id   INTEGER REFERENCES "order"(id), body TEXT);

-- Pattern 2: Discriminator column (common in ORMs):
CREATE TABLE comment (
    id            SERIAL PRIMARY KEY,
    entity_type   TEXT NOT NULL CHECK (entity_type IN ('product', 'order')),
    entity_id     INTEGER NOT NULL,
    -- No FK enforcement; application must enforce consistency
    body          TEXT
);
CREATE INDEX idx_comment_entity ON comment (entity_type, entity_id);

-- Pattern 3: Inherit from base table:
CREATE TABLE base_commentable (id SERIAL PRIMARY KEY);
ALTER TABLE product INHERITS base_commentable;
ALTER TABLE "order" INHERITS base_commentable;
CREATE TABLE comment (
    id            SERIAL PRIMARY KEY,
    commentable_id INTEGER REFERENCES base_commentable(id),
    body           TEXT
);
-- Rarely used; inheritance in PostgreSQL has significant limitations.
```

---

## Versioning / Temporal Data

Track the full history of row changes over time:

```sql
-- Temporal table pattern: current + history tables:
CREATE TABLE product_price (
    id         SERIAL PRIMARY KEY,
    product_id INTEGER REFERENCES product(id),
    price      NUMERIC(10,2) NOT NULL,
    valid_from TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    valid_to   TIMESTAMPTZ             -- NULL = currently valid
);

-- Current price:
SELECT price FROM product_price
WHERE product_id = 42 AND valid_to IS NULL;

-- Price at a specific historical point:
SELECT price FROM product_price
WHERE product_id = 42
  AND valid_from <= '2024-06-01'
  AND (valid_to IS NULL OR valid_to > '2024-06-01');

-- Update price (close old, open new):
BEGIN;
UPDATE product_price SET valid_to = NOW()
WHERE product_id = 42 AND valid_to IS NULL;

INSERT INTO product_price (product_id, price) VALUES (42, 89.99);
COMMIT;
```

---

## Try It Yourself

```sql
-- 1. Add soft-delete support to the product table.
--    - Add a deleted_at column.
--    - Create a partial index for active products.
--    - Create an active_products view.
--    - Write a "soft delete" UPDATE and verify the view filters it.

-- 2. Attach the audit_trigger_fn() trigger to the product table.
--    Update a product's price and verify the audit_log entry.

-- 3. The StoreForge category table uses adjacency list.
--    Add a few subcategories ("Electronics > Audio > Headphones")
--    and write the recursive CTE to display the full tree.
```

<details>
<summary>Show solutions</summary>

```sql
-- 1. Soft delete pattern:
ALTER TABLE product ADD COLUMN deleted_at TIMESTAMPTZ;

CREATE INDEX idx_product_active_category
    ON product (category_id)
    WHERE deleted_at IS NULL;

CREATE VIEW active_products AS
    SELECT * FROM product WHERE deleted_at IS NULL;

-- Soft delete product 99:
UPDATE product SET deleted_at = NOW() WHERE id = 99;

-- Verify view excludes it:
SELECT id FROM active_products WHERE id = 99;  -- 0 rows

-- 2. Audit trigger on product:
CREATE TRIGGER audit_product
AFTER INSERT OR UPDATE OR DELETE ON product
FOR EACH ROW EXECUTE FUNCTION audit_trigger_fn();

-- Update price:
UPDATE product SET price = 89.99 WHERE id = 1;

-- Check audit:
SELECT operation, old_data ->> 'price' AS old_price, new_data ->> 'price' AS new_price
FROM audit_log
WHERE table_name = 'product' AND row_id = 1
ORDER BY changed_at DESC
LIMIT 1;

-- 3. Category tree:
INSERT INTO category (name, parent_id) VALUES
    ('Audio',       1),  -- parent = Electronics (id=1)
    ('Headphones',  (SELECT id FROM category WHERE name = 'Audio'));

WITH RECURSIVE tree AS (
    SELECT id, name, parent_id, name::TEXT AS path, 0 AS depth
    FROM category WHERE parent_id IS NULL
    UNION ALL
    SELECT c.id, c.name, c.parent_id, (t.path || ' > ' || c.name)::TEXT, t.depth + 1
    FROM category c JOIN tree t ON t.id = c.parent_id
)
SELECT depth, name, path FROM tree ORDER BY path;
```

</details>

---

## Capstone Connection

StoreForge applies several of these patterns:
- The `audit_log` table + trigger captures every INSERT/UPDATE/DELETE on `customer` and `product`
- `category.parent_id` implements the adjacency list hierarchy — queried with recursive CTEs in the product search function
- `product.attributes JSONB` replaces what would be an unmaintainable EAV table for variable product specs
- `order.shipping_address_id` snapshots the address at order time — a deliberate design choice to preserve historical accuracy
