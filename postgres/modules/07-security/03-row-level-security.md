# Row-Level Security

## Concept

Table privileges control whether a role can access a table at all. Row-Level Security (RLS) controls **which rows** that role sees or modifies within the table. A customer should only see their own orders. A regional manager should only report on their territory. With RLS, these rules live in the database — not scattered across application code — so they apply consistently to every query, regardless of which application or SQL client connects.

---

## Enabling RLS

```sql
-- RLS is opt-in per table. Enabling it blocks all access until you add policies:
ALTER TABLE "order" ENABLE ROW LEVEL SECURITY;
ALTER TABLE order_item ENABLE ROW LEVEL SECURITY;
ALTER TABLE review ENABLE ROW LEVEL SECURITY;

-- The table owner bypasses RLS by default.
-- To enforce it even on the owner (e.g., for testing):
ALTER TABLE "order" FORCE ROW LEVEL SECURITY;
```

---

## CREATE POLICY

A policy consists of:
- **Command**: `SELECT`, `INSERT`, `UPDATE`, `DELETE`, or `ALL`
- **USING**: filter applied to existing rows (SELECT, UPDATE, DELETE)
- **WITH CHECK**: filter applied to new rows being written (INSERT, UPDATE)

```sql
-- Customers can only see their own orders.
-- The application sets 'app.customer_id' as a session variable:

CREATE POLICY customer_own_orders ON "order"
    AS PERMISSIVE               -- multiple PERMISSIVE policies are OR'd together
    FOR ALL
    TO storeforge_api           -- applies to this role only
    USING (
        customer_id = current_setting('app.customer_id', TRUE)::INTEGER
    )
    WITH CHECK (
        customer_id = current_setting('app.customer_id', TRUE)::INTEGER
    );

-- The second argument TRUE to current_setting() means "return NULL if missing"
-- (rather than raising an error). NULL = 0 evaluates to FALSE = row is hidden.
```

---

## Setting Session Variables

The application sets the current customer's identity before running queries:

```sql
-- Application connection setup (run after connecting, before first query):
SET LOCAL app.customer_id = '42';

-- Now all queries on "order" are transparently filtered:
SELECT * FROM "order";
-- Returns only orders where customer_id = 42

INSERT INTO "order" (customer_id, status, total_amount)
VALUES (99, 'pending', 0);
-- Fails WITH CHECK: 99 != 42

-- SET LOCAL only lasts for the current transaction:
BEGIN;
SET LOCAL app.customer_id = '42';
SELECT * FROM "order";  -- filtered to customer 42
COMMIT;
-- After commit, the setting is gone
```

---

## Order Items via Join

Order items don't have a `customer_id`, but they belong to orders. RLS on `order_item` can reference the filtered `"order"` table:

```sql
CREATE POLICY customer_own_order_items ON order_item
    FOR ALL
    TO storeforge_api
    USING (
        order_id IN (
            SELECT id FROM "order"
            WHERE customer_id = current_setting('app.customer_id', TRUE)::INTEGER
        )
    );
```

---

## Admin Bypass

The admin role sees everything:

```sql
-- Option 1: BYPASSRLS attribute on the role:
ALTER ROLE storeforge_admin BYPASSRLS;

-- Option 2: Explicitly add a policy that allows admin full access:
CREATE POLICY admin_all_orders ON "order"
    FOR ALL
    TO storeforge_admin
    USING (TRUE)            -- see all rows
    WITH CHECK (TRUE);      -- write any row

-- Option 3: Disable RLS for admin tables (keep FORCE off):
-- Table owner bypasses RLS unless FORCE ROW LEVEL SECURITY is set.
```

---

## RESTRICTIVE Policies

By default policies are `PERMISSIVE` — rows matching any policy are returned. `RESTRICTIVE` policies act as mandatory AND-filters:

```sql
-- Always filter out soft-deleted orders, regardless of other policies:
CREATE POLICY no_deleted_orders ON "order"
    AS RESTRICTIVE
    FOR ALL
    TO storeforge_api
    USING (deleted_at IS NULL);

-- Now a customer sees only their own non-deleted orders:
-- PERMISSIVE: customer_id = current_setting(...)   (their orders)
-- RESTRICTIVE: deleted_at IS NULL                  (AND not deleted)
-- Result: their own, non-deleted orders
```

---

## Product Reviews Policy

A customer can only INSERT and DELETE their own reviews:

```sql
ALTER TABLE review ENABLE ROW LEVEL SECURITY;

-- Anyone can SELECT all reviews (public content):
CREATE POLICY reviews_public_read ON review
    FOR SELECT
    TO storeforge_api
    USING (TRUE);

-- Customers can only write their own reviews:
CREATE POLICY reviews_own_write ON review
    FOR INSERT
    TO storeforge_api
    WITH CHECK (
        customer_id = current_setting('app.customer_id', TRUE)::INTEGER
    );

CREATE POLICY reviews_own_delete ON review
    FOR DELETE
    TO storeforge_api
    USING (
        customer_id = current_setting('app.customer_id', TRUE)::INTEGER
    );
```

---

## Inspecting Policies

```sql
-- List all RLS policies:
SELECT tablename, policyname, roles, cmd, qual, with_check
FROM pg_policies
WHERE schemaname = 'public'
ORDER BY tablename, policyname;

-- Check if RLS is enabled on a table:
SELECT relname, relrowsecurity, relforcerowsecurity
FROM pg_class
WHERE relname IN ('order', 'order_item', 'review')
ORDER BY relname;
```

---

## Try It Yourself

```sql
-- 1. Enable RLS on "order". Create a policy that filters rows to the customer
--    identified by current_setting('app.customer_id', TRUE).
--    Test by: BEGIN; SET LOCAL app.customer_id = '1'; SELECT id FROM "order"; COMMIT;

-- 2. Create an admin bypass policy that allows storeforge_admin to see all orders.
--    Confirm that SET LOCAL app.customer_id has no effect for admin role.

-- 3. Add a RESTRICTIVE policy on "order" that hides cancelled orders from all
--    non-admin roles (status != 'cancelled').

-- 4. Enable RLS on review. Rules:
--    - Anyone via storeforge_api can SELECT.
--    - Only the owner (app.customer_id) can DELETE their review.
```

<details>
<summary>Show solutions</summary>

```sql
-- 1. Enable RLS and customer filter:
ALTER TABLE "order" ENABLE ROW LEVEL SECURITY;

CREATE POLICY customer_own_orders ON "order"
    FOR ALL TO storeforge_api
    USING (customer_id = current_setting('app.customer_id', TRUE)::INTEGER)
    WITH CHECK (customer_id = current_setting('app.customer_id', TRUE)::INTEGER);

BEGIN;
SET LOCAL app.customer_id = '1';
SELECT id, status FROM "order";  -- only customer 1's orders
COMMIT;

-- 2. Admin bypass:
CREATE POLICY admin_all_orders ON "order"
    FOR ALL TO storeforge_admin
    USING (TRUE) WITH CHECK (TRUE);

-- Or grant bypassrls:
ALTER ROLE storeforge_admin BYPASSRLS;

-- 3. RESTRICTIVE: hide cancelled orders:
CREATE POLICY hide_cancelled ON "order"
    AS RESTRICTIVE
    FOR SELECT TO storeforge_api
    USING (status != 'cancelled');

-- 4. Review policies:
ALTER TABLE review ENABLE ROW LEVEL SECURITY;

CREATE POLICY reviews_public_select ON review
    FOR SELECT TO storeforge_api
    USING (TRUE);

CREATE POLICY reviews_own_insert ON review
    FOR INSERT TO storeforge_api
    WITH CHECK (
        customer_id = current_setting('app.customer_id', TRUE)::INTEGER
    );

CREATE POLICY reviews_own_delete ON review
    FOR DELETE TO storeforge_api
    USING (
        customer_id = current_setting('app.customer_id', TRUE)::INTEGER
    );
```

</details>

---

## Capstone Connection

StoreForge's RLS design principles:

1. **Session variable pattern**: application middleware sets `SET LOCAL app.customer_id` at transaction start — every query thereafter is automatically scoped
2. **Order isolation**: `customer_own_orders` ensures customers cannot enumerate or modify other customers' orders even if they discover an order ID
3. **RESTRICTIVE for data hygiene**: `no_deleted_orders` ensures soft-deleted orders are invisible to the API without application-level filtering
4. **Public vs private content**: reviews are publicly readable but privately writeable — the dual policy approach models this cleanly
5. **Admin BYPASSRLS**: the DBA and admin migration scripts bypass RLS so data maintenance isn't hampered by customer-scoped policies
