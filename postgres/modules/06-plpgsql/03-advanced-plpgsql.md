# Advanced PL/pgSQL

## Concept

Once you can write basic functions and triggers, you need the advanced mechanics: cursors for row-at-a-time processing, dynamic SQL for table/column names that cannot be parameterised, arrays for in-memory collections, and set-returning functions that stream results. These tools expand PL/pgSQL from simple helpers into a fully capable server-side language.

---

## Cursors

Cursors let you iterate over a result set one row at a time, which is useful when the result is too large to hold in memory or when you need to process rows sequentially:

```sql
-- Explicit cursor with OPEN/FETCH/CLOSE:
CREATE OR REPLACE FUNCTION process_overdue_orders()
RETURNS INTEGER AS $$
DECLARE
    cur CURSOR FOR
        SELECT id, customer_id
        FROM "order"
        WHERE status = 'pending'
          AND created_at < NOW() - INTERVAL '7 days'
        ORDER BY id;
    rec RECORD;
    processed INTEGER := 0;
BEGIN
    OPEN cur;
    LOOP
        FETCH cur INTO rec;
        EXIT WHEN NOT FOUND;

        -- Cancel the overdue order:
        UPDATE "order" SET status = 'cancelled' WHERE id = rec.id;
        processed := processed + 1;
    END LOOP;
    CLOSE cur;

    RETURN processed;
END;
$$ LANGUAGE plpgsql;

SELECT process_overdue_orders();

-- Implicit cursor (FOR loop — cleaner for most cases):
CREATE OR REPLACE FUNCTION tag_popular_products()
RETURNS VOID AS $$
DECLARE
    rec RECORD;
BEGIN
    FOR rec IN
        SELECT p.id, SUM(oi.quantity) AS total_sold
        FROM product p JOIN order_item oi ON oi.product_id = p.id
        GROUP BY p.id
        HAVING SUM(oi.quantity) > 100
    LOOP
        UPDATE product
        SET attributes = jsonb_set(
            COALESCE(attributes, '{}'::JSONB),
            '{featured}',
            'true'::JSONB
        )
        WHERE id = rec.id;
    END LOOP;
END;
$$ LANGUAGE plpgsql;
```

---

## Dynamic SQL with EXECUTE

When table names, column names, or operators must be determined at runtime, you need `EXECUTE`. Never concatenate user-supplied strings directly — always use `USING` to pass values as parameters:

```sql
-- Safe dynamic SQL with USING (prevents SQL injection):
CREATE OR REPLACE FUNCTION get_row_count(p_table TEXT)
RETURNS BIGINT AS $$
DECLARE
    v_count BIGINT;
BEGIN
    -- Use format() + %I for identifier quoting, USING for values:
    EXECUTE format('SELECT COUNT(*) FROM %I', p_table)
    INTO v_count;
    RETURN v_count;
END;
$$ LANGUAGE plpgsql;

SELECT get_row_count('product');  -- 50
SELECT get_row_count('customer'); -- 100

-- Dynamic WHERE clause with a value parameter:
CREATE OR REPLACE FUNCTION find_active_rows(
    p_table TEXT,
    p_status TEXT DEFAULT 'active'
) RETURNS BIGINT AS $$
DECLARE
    v_count BIGINT;
BEGIN
    EXECUTE format('SELECT COUNT(*) FROM %I WHERE status = $1', p_table)
    INTO v_count
    USING p_status;  -- $1 is bound here, never concatenated
    RETURN v_count;
END;
$$ LANGUAGE plpgsql;

-- Generic soft-delete using dynamic SQL:
CREATE OR REPLACE PROCEDURE soft_delete(p_table TEXT, p_id INTEGER)
LANGUAGE plpgsql AS $$
BEGIN
    EXECUTE format(
        'UPDATE %I SET deleted_at = NOW() WHERE id = $1 AND deleted_at IS NULL',
        p_table
    ) USING p_id;

    IF NOT FOUND THEN
        RAISE WARNING 'Row % not found or already deleted in %', p_id, p_table;
    END IF;
END;
$$;

CALL soft_delete('customer', 42);
```

---

## GET DIAGNOSTICS

Access metadata about the most recent SQL statement:

```sql
CREATE OR REPLACE PROCEDURE deactivate_category_products(p_category_id INT)
LANGUAGE plpgsql AS $$
DECLARE
    v_rows_affected INTEGER;
BEGIN
    UPDATE product
    SET is_active = FALSE
    WHERE category_id = p_category_id AND is_active = TRUE;

    GET DIAGNOSTICS v_rows_affected = ROW_COUNT;

    RAISE NOTICE 'Deactivated % products in category %',
        v_rows_affected, p_category_id;
END;
$$;

CALL deactivate_category_products(3);
-- NOTICE: Deactivated 8 products in category 3
```

---

## Arrays in PL/pgSQL

```sql
-- Array declaration and iteration:
CREATE OR REPLACE FUNCTION bulk_activate_customers(p_ids INTEGER[])
RETURNS INTEGER AS $$
DECLARE
    v_id INTEGER;
    v_count INTEGER := 0;
BEGIN
    FOREACH v_id IN ARRAY p_ids LOOP
        UPDATE customer SET is_active = TRUE WHERE id = v_id;
        IF FOUND THEN
            v_count := v_count + 1;
        END IF;
    END LOOP;
    RETURN v_count;
END;
$$ LANGUAGE plpgsql;

SELECT bulk_activate_customers(ARRAY[1, 2, 3, 7, 12]);

-- More efficient: use unnest() for set-based operation instead:
CREATE OR REPLACE FUNCTION bulk_activate_customers_v2(p_ids INTEGER[])
RETURNS INTEGER AS $$
DECLARE
    v_count INTEGER;
BEGIN
    UPDATE customer
    SET is_active = TRUE
    WHERE id = ANY(p_ids) AND is_active = FALSE;

    GET DIAGNOSTICS v_count = ROW_COUNT;
    RETURN v_count;
END;
$$ LANGUAGE plpgsql;

-- Building an array inside a function:
CREATE OR REPLACE FUNCTION get_product_ids_by_category(p_slug TEXT)
RETURNS INTEGER[] AS $$
DECLARE
    v_ids INTEGER[];
BEGIN
    SELECT ARRAY_AGG(id ORDER BY id)
    INTO v_ids
    FROM product p
    JOIN category c ON c.id = p.category_id
    WHERE c.slug = p_slug AND p.is_active;

    RETURN COALESCE(v_ids, ARRAY[]::INTEGER[]);
END;
$$ LANGUAGE plpgsql;

SELECT get_product_ids_by_category('electronics');
```

---

## Set-Returning Functions

Return multiple rows from a function using `RETURNS TABLE` or `RETURNS SETOF`:

```sql
-- RETURNS TABLE with RETURN QUERY EXECUTE (dynamic set-returning):
CREATE OR REPLACE FUNCTION search_products(
    p_query TEXT,
    p_min_price NUMERIC DEFAULT NULL,
    p_max_price NUMERIC DEFAULT NULL,
    p_limit  INTEGER DEFAULT 20
)
RETURNS TABLE(
    id          INTEGER,
    name        TEXT,
    price       NUMERIC,
    rank        REAL,
    stock_quantity INTEGER
) AS $$
DECLARE
    v_sql  TEXT;
    v_args TEXT[] := ARRAY[$1::TEXT];  -- tsquery arg always present
BEGIN
    v_sql := '
        SELECT p.id, p.name, p.price,
               ts_rank(search_vector, query) AS rank,
               p.stock_quantity
        FROM product p,
             websearch_to_tsquery(''english'', $1) query
        WHERE p.search_vector @@ query
          AND p.is_active';

    IF p_min_price IS NOT NULL THEN
        v_sql := v_sql || format(' AND p.price >= %s', p_min_price);
    END IF;
    IF p_max_price IS NOT NULL THEN
        v_sql := v_sql || format(' AND p.price <= %s', p_max_price);
    END IF;

    v_sql := v_sql || format(' ORDER BY rank DESC LIMIT %s', p_limit);

    RETURN QUERY EXECUTE v_sql USING p_query;
END;
$$ LANGUAGE plpgsql STABLE;

SELECT * FROM search_products('wireless headphones', p_max_price => 200);
```

---

## Function Overloading

PostgreSQL resolves functions by argument types, allowing the same name with different signatures:

```sql
-- Two versions of get_customer:
CREATE OR REPLACE FUNCTION get_customer(p_id INTEGER)
RETURNS customer AS $$
    SELECT * FROM customer WHERE id = p_id;
$$ LANGUAGE sql STABLE;

CREATE OR REPLACE FUNCTION get_customer(p_email TEXT)
RETURNS customer AS $$
    SELECT * FROM customer WHERE email = p_email;
$$ LANGUAGE sql STABLE;

-- PostgreSQL picks the right one based on argument type:
SELECT * FROM get_customer(42);             -- uses INTEGER version
SELECT * FROM get_customer('alice@ex.com'); -- uses TEXT version
```

---

## Variadic Functions

```sql
-- Accept any number of category slugs:
CREATE OR REPLACE FUNCTION products_in_categories(VARIADIC p_slugs TEXT[])
RETURNS TABLE(product_id INT, product_name TEXT, category_slug TEXT) AS $$
    SELECT p.id, p.name, c.slug
    FROM product p JOIN category c ON c.id = p.category_id
    WHERE c.slug = ANY(p_slugs) AND p.is_active
    ORDER BY c.slug, p.name;
$$ LANGUAGE sql STABLE;

SELECT * FROM products_in_categories('electronics', 'clothing', 'books');
```

---

## Try It Yourself

```sql
-- 1. Write a function get_row_count(p_table TEXT) that safely returns
--    the COUNT(*) for any table passed by name.
--    Call it for 'product', 'customer', and 'order'.

-- 2. Write a procedure soft_delete(p_table TEXT, p_id INTEGER)
--    that sets deleted_at = NOW() on the given row using dynamic SQL.
--    Print a WARNING if the row was not found.

-- 3. Write a function top_customers(p_n INTEGER)
--    that returns the top N customers by total order value.
--    Use RETURNS TABLE(customer_id INT, customer_name TEXT, total NUMERIC).

-- 4. Write a function get_product(p_id INTEGER) and a second overload
--    get_product(p_slug TEXT). Verify both work.
```

<details>
<summary>Show solutions</summary>

```sql
-- 1. Safe row count:
CREATE OR REPLACE FUNCTION get_row_count(p_table TEXT)
RETURNS BIGINT AS $$
DECLARE
    v_count BIGINT;
BEGIN
    EXECUTE format('SELECT COUNT(*) FROM %I', p_table) INTO v_count;
    RETURN v_count;
END;
$$ LANGUAGE plpgsql;

SELECT get_row_count('product');
SELECT get_row_count('customer');
SELECT get_row_count('order');

-- 2. Soft delete procedure:
CREATE OR REPLACE PROCEDURE soft_delete(p_table TEXT, p_id INTEGER)
LANGUAGE plpgsql AS $$
BEGIN
    EXECUTE format(
        'UPDATE %I SET deleted_at = NOW() WHERE id = $1 AND deleted_at IS NULL',
        p_table
    ) USING p_id;
    IF NOT FOUND THEN
        RAISE WARNING 'Row % not found or already deleted in %', p_id, p_table;
    END IF;
END;
$$;

CALL soft_delete('customer', 99);

-- 3. Top N customers:
CREATE OR REPLACE FUNCTION top_customers(p_n INTEGER DEFAULT 10)
RETURNS TABLE(customer_id INT, customer_name TEXT, total NUMERIC) AS $$
    SELECT c.id, c.name, SUM(o.total_amount)::NUMERIC AS total
    FROM customer c JOIN "order" o ON o.customer_id = c.id
    WHERE o.status = 'delivered'
    GROUP BY c.id, c.name
    ORDER BY total DESC
    LIMIT p_n;
$$ LANGUAGE sql STABLE;

SELECT * FROM top_customers(5);

-- 4. Overloaded get_product:
CREATE OR REPLACE FUNCTION get_product(p_id INTEGER)
RETURNS product AS $$
    SELECT * FROM product WHERE id = p_id AND is_active;
$$ LANGUAGE sql STABLE;

CREATE OR REPLACE FUNCTION get_product(p_slug TEXT)
RETURNS product AS $$
    SELECT * FROM product WHERE slug = p_slug AND is_active;
$$ LANGUAGE sql STABLE;

SELECT name FROM get_product(1);
SELECT name FROM get_product('wireless-headphones');
```

</details>

---

## Capstone Connection

Advanced PL/pgSQL techniques used in StoreForge:
- **`get_row_count` / `soft_delete`** — generic utilities built with `EXECUTE` + `%I` quoting, usable across all tables
- **`search_products`** — combines dynamic SQL with set-returning function to build a configurable FTS search endpoint
- **`top_customers`** — clean `RETURNS TABLE` function used by admin dashboards
- **Overloaded `get_product`** — lets application layers look up products by either PK or the URL-safe slug without branching
