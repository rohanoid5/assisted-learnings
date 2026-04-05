-- =============================================================================
-- StoreForge Indexes
-- Run AFTER schema.sql and functions.sql.
-- All indexes use CONCURRENTLY so they can be created without locking writes.
-- NOTE: CONCURRENTLY cannot run inside a transaction block.
--       Run this file with: psql -f indexes.sql (not inside BEGIN/COMMIT).
-- =============================================================================

CREATE EXTENSION IF NOT EXISTS pg_trgm;  -- required for gin_trgm_ops

-- =============================================================================
-- Section 1: Foreign Key Indexes
-- PostgreSQL does NOT automatically index FK columns.
-- Without these, every FK lookup (e.g., finding orders for a customer) causes
-- a sequential scan on the child table.
-- =============================================================================

-- order.customer_id → JOIN "order" ON customer_id = ?
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_order_customer_id
    ON "order" (customer_id);

-- order.shipping_address_id → JOIN "order" ON shipping_address_id = ?
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_order_shipping_address_id
    ON "order" (shipping_address_id);

-- order_item.order_id → the most frequently joined FK in the schema
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_order_item_order_id
    ON order_item (order_id);

-- order_item.product_id → product sold report, inventory impact queries
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_order_item_product_id
    ON order_item (product_id);

-- product.category_id → list products by category
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_product_category_id
    ON product (category_id);

-- review.product_id → list reviews for a product page
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_review_product_id
    ON review (product_id);

-- review.customer_id → "my reviews" page
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_review_customer_id
    ON review (customer_id);

-- address.customer_id → fetch addresses for a customer
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_address_customer_id
    ON address (customer_id);

-- category.parent_id → traverse category hierarchy
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_category_parent_id
    ON category (parent_id);

-- audit_log.row_id + table_name → "who changed this record?" queries
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_audit_log_row
    ON audit_log (table_name, row_id);

-- =============================================================================
-- Section 2: Functional Indexes
-- Index the result of an expression rather than a raw column value.
-- =============================================================================

-- Lowercase email lookup (case-insensitive login)
-- Supports: WHERE lower(email) = lower('User@Example.com')
-- The schema also has a CHECK that enforces email = lower(email),
-- but module 03 teaches expression indexes as a concept.
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_customer_email_lower
    ON customer (lower(email));

-- =============================================================================
-- Section 3: Full-Text Search (GIN)
-- The search_vector column is a GENERATED column maintained automatically.
-- GIN index makes @@ operator fast for any text query.
-- =============================================================================

-- FTS on generated search_vector column
-- Supports: WHERE search_vector @@ to_tsquery('english', 'laptop & fast')
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_product_search_vector
    ON product USING GIN (search_vector);

-- =============================================================================
-- Section 4: Trigram Indexes (GIN with gin_trgm_ops)
-- Enables fast LIKE, ILIKE, and similarity() queries.
-- Requires the pg_trgm extension (installed above).
-- =============================================================================

-- Fuzzy product name search
-- Supports: WHERE name ILIKE '%lapto%'  AND  name % 'lapto' (similarity)
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_product_name_trgm
    ON product USING GIN (name gin_trgm_ops);

-- Fuzzy customer name search (admin search bar)
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_customer_name_trgm
    ON customer USING GIN (name gin_trgm_ops);

-- =============================================================================
-- Section 5: JSONB Indexes (GIN)
-- GIN on a JSONB column lets PostgreSQL index every key and value inside the
-- document, enabling fast @>, ?, and @? operators without knowing keys upfront.
-- =============================================================================

-- JSONB product attributes: brand, color, size, etc.
-- Supports: WHERE attributes @> '{"brand": "AudioMax"}'
-- Supports: WHERE attributes ? 'wireless'
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_product_attributes_gin
    ON product USING GIN (attributes);

-- =============================================================================
-- Section 6: Partial Indexes
-- Index only the rows that match a WHERE clause.
-- Smaller index → fits in RAM → faster scans.
-- =============================================================================

-- Only index active products (inactive products are rarely queried by customers)
-- Supports: WHERE is_active = TRUE ORDER BY created_at DESC
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_product_active_created
    ON product (created_at DESC)
    WHERE is_active = TRUE;

-- Only index open orders (delivered/cancelled orders are rarely filtered)
-- Supports: WHERE status IN ('pending', 'processing', 'shipped')
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_order_open_status
    ON "order" (customer_id, created_at DESC)
    WHERE status IN ('pending', 'processing', 'shipped');

-- =============================================================================
-- Section 7: Composite Indexes
-- Multi-column indexes that match the most common query patterns.
-- Column order matters: put equality columns first, range/sort columns last.
-- =============================================================================

-- Customer order history (equality on customer_id, range/sort on created_at)
-- Supports: WHERE customer_id = ? ORDER BY created_at DESC
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_order_customer_created
    ON "order" (customer_id, created_at DESC);

-- Product listing by category with active filter and price sort
-- Supports: WHERE category_id = ? AND is_active = TRUE ORDER BY price
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_product_category_active_price
    ON product (category_id, is_active, price)
    WHERE is_active = TRUE;

-- =============================================================================
-- Section 8: BRIN Index (Block Range INdex)
-- Lightweight index for naturally ordered append-only tables.
-- 1000x smaller than btree; ideal for audit_log, large time-series tables.
-- =============================================================================

-- Audit log timestamp: rows are naturally inserted in time order
-- Supports: WHERE changed_at BETWEEN '2024-01-01' AND '2024-02-01'
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_audit_log_changed_at_brin
    ON audit_log USING BRIN (changed_at)
    WITH (pages_per_range = 128);

-- =============================================================================
-- Verification: run after creating indexes to confirm they exist
-- =============================================================================
-- SELECT indexname, tablename, indexdef
-- FROM pg_indexes
-- WHERE schemaname = 'public'
-- ORDER BY tablename, indexname;
--
-- SELECT relname AS table, indexrelname AS index,
--        pg_size_pretty(pg_relation_size(indexrelid)) AS size
-- FROM pg_stat_user_indexes
-- WHERE schemaname = 'public'
-- ORDER BY pg_relation_size(indexrelid) DESC;
