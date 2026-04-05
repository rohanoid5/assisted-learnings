# SQL Functions & Function Properties

## Concept

Not every database function needs the full PL/pgSQL block syntax. `LANGUAGE sql` functions are simpler, faster, and easier to reason about: they are a single SQL query body with named parameters. Beyond the language choice, PostgreSQL exposes three orthogonal function properties — volatility, parallel safety, and security context — that let the query planner optimise calls and let DBAs control privilege escalation. Getting these right can make the difference between a function you can use in an index expression and one that forces sequential scans.

---

## LANGUAGE sql Functions

```sql
-- Simplest form: a named SQL query with typed parameters:
CREATE OR REPLACE FUNCTION product_revenue(p_product_id INTEGER)
RETURNS NUMERIC AS $$
    SELECT COALESCE(SUM(quantity * unit_price), 0)
    FROM order_item
    WHERE product_id = p_product_id;
$$ LANGUAGE sql STABLE;

SELECT name, product_revenue(id) AS revenue
FROM product
WHERE is_active
ORDER BY revenue DESC
LIMIT 10;

-- Multi-return (RETURNS TABLE) without PL/pgSQL:
CREATE OR REPLACE FUNCTION orders_for_customer(p_customer_id INTEGER)
RETURNS TABLE(order_id INT, status order_status, total NUMERIC, created_at TIMESTAMPTZ) AS $$
    SELECT id, status, total_amount, created_at
    FROM "order"
    WHERE customer_id = p_customer_id
    ORDER BY created_at DESC;
$$ LANGUAGE sql STABLE;

SELECT * FROM orders_for_customer(1);

-- RETURNS SETOF a table type:
CREATE OR REPLACE FUNCTION active_products_in_category(p_category_slug TEXT)
RETURNS SETOF product AS $$
    SELECT p.*
    FROM product p JOIN category c ON c.id = p.category_id
    WHERE c.slug = p_category_slug AND p.is_active
    ORDER BY p.name;
$$ LANGUAGE sql STABLE;

SELECT id, name, price FROM active_products_in_category('electronics');
```

---

## LANGUAGE sql vs LANGUAGE plpgsql

| Scenario | Use |
|---|---|
| Single query, no branching | `LANGUAGE sql` |
| Multiple statements, variables, loops | `LANGUAGE plpgsql` |
| Trigger function | `LANGUAGE plpgsql` (required) |
| Exception handling | `LANGUAGE plpgsql` |
| Dynamic SQL (`EXECUTE`) | `LANGUAGE plpgsql` |
| Inline into calling query (inlining optimisation) | `LANGUAGE sql` |

`LANGUAGE sql` functions with a single `SELECT` can be **inlined** by the planner into the calling query, eliminating function call overhead entirely. `LANGUAGE plpgsql` functions are always a black box to the planner.

---

## Volatility Categories

Volatility tells the planner whether the function can return different results across calls in the same query:

```sql
-- IMMUTABLE: same inputs always produce same output; no DB access allowed.
-- Safe to use in index expressions and constant-folded.
CREATE OR REPLACE FUNCTION price_with_tax(p_price NUMERIC, p_rate NUMERIC)
RETURNS NUMERIC AS $$
    SELECT ROUND(p_price * (1 + p_rate), 2);
$$ LANGUAGE sql IMMUTABLE;

-- Use in a generated column or index:
ALTER TABLE product
    ADD COLUMN price_with_gst NUMERIC
    GENERATED ALWAYS AS (ROUND(price * 1.18, 2)) STORED;

-- STABLE: same inputs produce same output within a single transaction.
-- Can read the DB; cannot modify. Planner can optimise repeated calls.
CREATE OR REPLACE FUNCTION category_name(p_category_id INTEGER)
RETURNS TEXT AS $$
    SELECT name FROM category WHERE id = p_category_id;
$$ LANGUAGE sql STABLE;

-- VOLATILE (default): may return different results on every call.
-- Required for functions that modify data or call RANDOM()/NOW().
CREATE OR REPLACE FUNCTION log_event(p_message TEXT)
RETURNS VOID AS $$
    INSERT INTO audit_log (table_name, operation, new_data, changed_at)
    VALUES ('system', 'EVENT', jsonb_build_object('msg', p_message), NOW());
$$ LANGUAGE sql VOLATILE;
```

Declaring a function `IMMUTABLE` when it is not causes subtly wrong query results. When in doubt, use `STABLE` or `VOLATILE`.

---

## Parallel Safety

Controls whether the function can run in parallel query workers:

```sql
-- PARALLEL SAFE: no side effects, no session state, no unlogged tables.
CREATE OR REPLACE FUNCTION slug_from_name(p_name TEXT)
RETURNS TEXT AS $$
    SELECT lower(regexp_replace(trim(p_name), '\s+', '-', 'g'));
$$ LANGUAGE sql IMMUTABLE PARALLEL SAFE;

-- PARALLEL RESTRICTED (default): can run in parallel but only in leader.
-- PARALLEL UNSAFE: disables parallelism for the entire query.

-- Mark existing function safe:
ALTER FUNCTION price_with_tax(NUMERIC, NUMERIC) PARALLEL SAFE;
```

---

## STRICT (RETURNS NULL ON NULL INPUT)

```sql
-- A STRICT function short-circuits and returns NULL if any argument is NULL:
CREATE OR REPLACE FUNCTION discount_price(p_price NUMERIC, p_pct NUMERIC)
RETURNS NUMERIC AS $$
    SELECT ROUND(p_price * (1 - p_pct / 100), 2);
$$ LANGUAGE sql IMMUTABLE STRICT PARALLEL SAFE;

SELECT discount_price(100.00, 20);   -- 80.00
SELECT discount_price(NULL,  20);    -- NULL (no function body executed)
```

---

## SECURITY DEFINER

By default a function runs as the **calling user** (`SECURITY INVOKER`). `SECURITY DEFINER` elevates it to run as the **owner**. This is the standard technique for giving unprivileged roles access to selected operations:

```sql
-- Owner: storeforge_admin (has UPDATE on customer)
-- Caller: storeforge_api (no UPDATE on customer)

CREATE OR REPLACE FUNCTION update_customer_email(
    p_customer_id INTEGER,
    p_new_email   TEXT
)
RETURNS VOID
LANGUAGE plpgsql
SECURITY DEFINER
-- Mandatory: always fix search_path in SECURITY DEFINER functions
-- to prevent search_path hijacking attacks:
SET search_path = public, pg_temp
AS $$
BEGIN
    IF p_new_email !~ '^[^@]+@[^@]+\.[^@]+$' THEN
        RAISE EXCEPTION 'Invalid email format: %', p_new_email;
    END IF;

    UPDATE customer SET email = p_new_email WHERE id = p_customer_id;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'Customer % not found', p_customer_id;
    END IF;
END;
$$;

-- Grant execute to the API role:
GRANT EXECUTE ON FUNCTION update_customer_email(INTEGER, TEXT) TO storeforge_api;

-- Inspect function security settings:
SELECT proname, prosecdef, provolatile, proparallel
FROM pg_proc
WHERE proname IN ('update_customer_email', 'category_name', 'slug_from_name');
```

---

## RETURNS VOID

```sql
-- Use for procedures or side-effect functions that return nothing:
CREATE OR REPLACE FUNCTION mark_order_shipped(p_order_id INTEGER)
RETURNS VOID AS $$
    UPDATE "order"
    SET status = 'shipped', updated_at = NOW()
    WHERE id = p_order_id AND status = 'processing';
$$ LANGUAGE sql VOLATILE;

SELECT mark_order_shipped(42);  -- returns empty
```

---

## Try It Yourself

```sql
-- 1. Create an IMMUTABLE function apply_discount(price NUMERIC, pct NUMERIC)
--    that returns the discounted price. Mark it STRICT and PARALLEL SAFE.
--    Test it with nulls and normal values.

-- 2. Create a STABLE SQL function customer_lifetime_value(p_customer_id INTEGER)
--    returning the SUM of order totals for that customer (delivered orders only).

-- 3. Create a SECURITY DEFINER function change_order_status(
--        p_order_id INTEGER, p_new_status order_status)
--    that only allows forward status transitions:
--    pending → processing → shipped → delivered
--    Raise an exception for invalid transitions.
--    Set search_path = public, pg_temp.
```

<details>
<summary>Show solutions</summary>

```sql
-- 1. apply_discount:
CREATE OR REPLACE FUNCTION apply_discount(price NUMERIC, pct NUMERIC)
RETURNS NUMERIC AS $$
    SELECT ROUND(price * (1 - pct / 100.0), 2);
$$ LANGUAGE sql IMMUTABLE STRICT PARALLEL SAFE;

SELECT apply_discount(200.00, 15);  -- 170.00
SELECT apply_discount(NULL, 15);    -- NULL (STRICT short-circuits)

-- 2. customer_lifetime_value:
CREATE OR REPLACE FUNCTION customer_lifetime_value(p_customer_id INTEGER)
RETURNS NUMERIC AS $$
    SELECT COALESCE(SUM(total_amount), 0)
    FROM "order"
    WHERE customer_id = p_customer_id AND status = 'delivered';
$$ LANGUAGE sql STABLE;

SELECT name, customer_lifetime_value(id) AS ltv
FROM customer ORDER BY ltv DESC LIMIT 5;

-- 3. change_order_status with transition guard:
CREATE OR REPLACE FUNCTION change_order_status(
    p_order_id   INTEGER,
    p_new_status order_status
)
RETURNS VOID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
    v_current order_status;
BEGIN
    SELECT status INTO STRICT v_current
    FROM "order" WHERE id = p_order_id;

    -- Validate forward progression:
    IF NOT (
        (v_current = 'pending'    AND p_new_status = 'processing') OR
        (v_current = 'processing' AND p_new_status = 'shipped')    OR
        (v_current = 'shipped'    AND p_new_status = 'delivered')  OR
        (v_current = p_new_status)  -- idempotent same-state is fine
    ) THEN
        RAISE EXCEPTION 'Invalid status transition: % → %',
            v_current, p_new_status;
    END IF;

    UPDATE "order"
    SET status = p_new_status, updated_at = NOW()
    WHERE id = p_order_id;
END;
$$;

GRANT EXECUTE ON FUNCTION change_order_status(INTEGER, order_status) TO storeforge_api;
```

</details>

---

## Capstone Connection

StoreForge function property guidelines:
| Function | Language | Volatility | Parallel | Security |
|---|---|---|---|---|
| `slug_from_name` | sql | IMMUTABLE | SAFE | INVOKER |
| `category_name` | sql | STABLE | RESTRICTED | INVOKER |
| `product_revenue` | sql | STABLE | RESTRICTED | INVOKER |
| `customer_lifetime_value` | sql | STABLE | RESTRICTED | INVOKER |
| `update_customer_email` | plpgsql | VOLATILE | UNSAFE | **DEFINER** |
| `change_order_status` | plpgsql | VOLATILE | UNSAFE | **DEFINER** |
| `place_order` | plpgsql | VOLATILE | UNSAFE | **DEFINER** |

`SECURITY DEFINER` is used only for the handful of write operations that `storeforge_api` must perform on tables it does not directly own — every DEFINER function pins `search_path` to prevent privilege escalation.
