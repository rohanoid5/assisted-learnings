-- =============================================================================
-- StoreForge Database Schema
-- PostgreSQL 16+
-- Master DDL file — run this first before any other SQL files.
-- =============================================================================

-- Custom types
CREATE TYPE order_status AS ENUM (
    'pending',
    'processing',
    'shipped',
    'delivered',
    'cancelled',
    'returned'
);

-- =============================================================================
-- Tables
-- =============================================================================

-- Category (self-referential tree)
CREATE TABLE category (
    id          SERIAL PRIMARY KEY,
    name        TEXT        NOT NULL,
    slug        TEXT        NOT NULL UNIQUE,
    parent_id   INTEGER     REFERENCES category(id) ON DELETE SET NULL
);
COMMENT ON TABLE  category          IS 'Product category tree (nested via parent_id).';
COMMENT ON COLUMN category.slug     IS 'URL-safe unique identifier, e.g. womens-shoes.';

-- Customer
CREATE TABLE customer (
    id          SERIAL          PRIMARY KEY,
    name        TEXT            NOT NULL,
    email       TEXT            NOT NULL UNIQUE CHECK (email = LOWER(email)),
    phone       TEXT,
    is_active   BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

-- Address
CREATE TABLE address (
    id          SERIAL      PRIMARY KEY,
    customer_id INTEGER     NOT NULL REFERENCES customer(id) ON DELETE CASCADE,
    line1       TEXT        NOT NULL,
    line2       TEXT,
    city        TEXT        NOT NULL,
    state       TEXT        NOT NULL,
    country     CHAR(2)     NOT NULL DEFAULT 'US',
    postal_code TEXT        NOT NULL,
    is_default  BOOLEAN     NOT NULL DEFAULT FALSE
);
CREATE UNIQUE INDEX address_one_default_per_customer
    ON address (customer_id) WHERE is_default = TRUE;

-- Product
CREATE TABLE product (
    id              SERIAL          PRIMARY KEY,
    category_id     INTEGER         NOT NULL REFERENCES category(id) ON DELETE RESTRICT,
    name            TEXT            NOT NULL,
    slug            TEXT            NOT NULL UNIQUE,
    description     TEXT,
    price           NUMERIC(10,2)   NOT NULL CHECK (price >= 0),
    stock_quantity  INTEGER         NOT NULL DEFAULT 0 CHECK (stock_quantity >= 0),
    attributes      JSONB           NOT NULL DEFAULT '{}',
    is_active       BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    search_vector   TSVECTOR        GENERATED ALWAYS AS (
                        to_tsvector('english', coalesce(name,'') || ' ' || coalesce(description,''))
                    ) STORED
);
COMMENT ON COLUMN product.attributes     IS 'Flexible product metadata: size, color, weight, etc.';
COMMENT ON COLUMN product.search_vector  IS 'Auto-generated FTS vector for full-text search.';

-- Order
CREATE TABLE "order" (
    id                  BIGSERIAL       PRIMARY KEY,
    customer_id         INTEGER         NOT NULL REFERENCES customer(id) ON DELETE RESTRICT,
    shipping_address_id INTEGER         REFERENCES address(id) ON DELETE SET NULL,
    status              order_status    NOT NULL DEFAULT 'pending',
    total_amount        NUMERIC(10,2)   NOT NULL DEFAULT 0 CHECK (total_amount >= 0),
    notes               TEXT,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);
COMMENT ON TABLE "order" IS 'Customer orders. Always double-quote in SQL (reserved word).';

-- Order item
CREATE TABLE order_item (
    id          BIGSERIAL       PRIMARY KEY,
    order_id    BIGINT          NOT NULL REFERENCES "order"(id) ON DELETE CASCADE,
    product_id  INTEGER         NOT NULL REFERENCES product(id) ON DELETE RESTRICT,
    quantity    INTEGER         NOT NULL CHECK (quantity > 0),
    unit_price  NUMERIC(10,2)   NOT NULL CHECK (unit_price >= 0)
);

-- Review
CREATE TABLE review (
    id          BIGSERIAL   PRIMARY KEY,
    product_id  INTEGER     NOT NULL REFERENCES product(id) ON DELETE CASCADE,
    customer_id INTEGER     NOT NULL REFERENCES customer(id) ON DELETE CASCADE,
    rating      SMALLINT    NOT NULL CHECK (rating BETWEEN 1 AND 5),
    comment     TEXT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (product_id, customer_id)
);

-- Audit log
CREATE TABLE audit_log (
    id          BIGSERIAL   PRIMARY KEY,
    table_name  TEXT        NOT NULL,
    operation   TEXT        NOT NULL CHECK (operation IN ('INSERT','UPDATE','DELETE')),
    row_id      BIGINT      NOT NULL,
    old_data    JSONB,
    new_data    JSONB,
    changed_by  TEXT        NOT NULL DEFAULT current_user,
    changed_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
COMMENT ON TABLE audit_log IS 'Immutable audit trail. Append only — no UPDATE or DELETE.';

-- Customer credentials (bcrypt password hash)
CREATE TABLE customer_credential (
    customer_id     INTEGER     PRIMARY KEY REFERENCES customer(id) ON DELETE CASCADE,
    password_hash   TEXT        NOT NULL,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Payment methods (encrypted card data)
CREATE TABLE payment_method (
    id              SERIAL      PRIMARY KEY,
    customer_id     INTEGER     NOT NULL REFERENCES customer(id) ON DELETE CASCADE,
    card_type       TEXT        NOT NULL,
    last_four       CHAR(4)     NOT NULL,
    card_number_enc BYTEA       NOT NULL,   -- pgp_sym_encrypt(card_number, key)
    expiry_enc      BYTEA       NOT NULL,   -- pgp_sym_encrypt(MM/YY, key)
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- =============================================================================
-- Constraints
-- =============================================================================

-- Product discount periods cannot overlap for the same product (requires btree_gist):
-- CREATE EXTENSION IF NOT EXISTS btree_gist;
-- ALTER TABLE product_discount ADD CONSTRAINT no_overlapping_discounts
--     EXCLUDE USING GIST (product_id WITH =, active_during WITH &&);
-- (Commented out — enable if btree_gist extension is available)
