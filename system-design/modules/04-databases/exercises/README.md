# Module 04 — Exercises

## Overview

These exercises complete the ScaleForge database milestone: indexed queries, read/write routing, safe migrations, and PgBouncer in front of Postgres.

**Time estimate:** 3–4 hours  
**Prerequisites:** Docker Desktop, psql, Node.js 20+

---

## Exercise 1 — Verify index usage with EXPLAIN ANALYZE

**Goal:** Confirm that the `code` lookup uses an index scan, not a sequential scan.

```bash
# 1. Connect to the ScaleForge Postgres instance
docker compose exec postgres psql -U user -d scaleforge

# 2. Generate 100,000 test rows
psql> INSERT INTO urls (code, target_url, user_id)
      SELECT
        substr(md5(i::text), 1, 8),
        'https://example.com/' || i,
        gen_random_uuid()
      FROM generate_series(1, 100000) AS i;

# 3. Run EXPLAIN ANALYZE for the redirect query
psql> EXPLAIN (ANALYZE, BUFFERS) 
      SELECT target_url FROM urls WHERE code = 'a1b2c3d4';

# Expected output — look for "Index Scan" or "Index Only Scan":
#   Index Only Scan using urls_code_idx on urls
#     (cost=0.42..8.44 rows=1 width=50)
#     (actual time=0.021..0.022 rows=1 loops=1)
#     Index Cond: (code = 'a1b2c3d4')
#   Planning Time: 0.2 ms
#   Execution Time: 0.1 ms

# 4. Drop the index and repeat — observe Seq Scan and increased execution time
psql> DROP INDEX urls_code_idx;
psql> EXPLAIN ANALYZE SELECT target_url FROM urls WHERE code = 'a1b2c3d4';

# 5. Re-create the index
psql> CREATE UNIQUE INDEX CONCURRENTLY urls_code_idx
        ON urls (code) WHERE deleted_at IS NULL;
```

**Checkpoint:** After re-creating the index, execution time should be < 1ms for a single row lookup.

---

## Exercise 2 — Fix an N+1 Query

**Goal:** Rewrite a URL listing query that has an N+1 pattern.

```typescript
// exercises/04-n-plus-one.exercise.ts

// You have this SLOW implementation:
async function listUrlsWithClicksBad(userId: string): Promise<object[]> {
  const urls = await primaryPool.query(
    'SELECT id, code, target_url FROM urls WHERE user_id = $1 LIMIT 20',
    [userId]
  );
  
  return await Promise.all(
    urls.rows.map(async (url) => {
      // THIS IS THE N+1: one query per URL row
      const clicks = await replicaPool.query(
        'SELECT COUNT(*) FROM click_events WHERE url_id = $1',
        [url.id]
      );
      return {
        ...url,
        clickCount: parseInt(clicks.rows[0].count, 10),
      };
    })
  );
}

// TODO:
// 1. Rewrite as a single SQL query using LEFT JOIN + COUNT + GROUP BY
// 2. Verify that running the new query generates exactly 1 database roundtrip
//    by enabling pg query logging: add "log: ['query']" to pool config
// 3. Compare execution time: N+1 vs JOIN version at 20 URLs, 100 URLs, 1000 URLs
```

<details>
<summary>Show solution</summary>

```typescript
async function listUrlsWithClicksGood(userId: string): Promise<object[]> {
  const result = await replicaPool.query<{
    id: string;
    code: string;
    target_url: string;
    click_count: string;
  }>(
    `SELECT
       u.id,
       u.code,
       u.target_url,
       COALESCE(c.click_count, 0) AS click_count
     FROM urls u
     LEFT JOIN (
       SELECT url_id, COUNT(*) AS click_count
       FROM click_events
       GROUP BY url_id
     ) c ON c.url_id = u.id
     WHERE u.user_id = $1
       AND u.deleted_at IS NULL
     ORDER BY u.created_at DESC
     LIMIT 20`,
    [userId]
  );

  return result.rows.map((row) => ({
    ...row,
    clickCount: parseInt(row.click_count, 10),
  }));
}
// 1 query vs N+1 queries. At 20 URLs: 1 query vs 21 queries.
```

</details>

---

## Exercise 3 — Write and Apply a Zero-Downtime Migration

**Goal:** Add a `custom_domain` column to URLs (for branded short links).

```bash
# Your task: write a safe, zero-downtime migration file

# 1. Create: migrations/004_add_custom_domain.sql
# Requirements:
#   - Add nullable column `custom_domain VARCHAR(255)` to urls
#   - Add unique index on (custom_domain) WHERE custom_domain IS NOT NULL
#   - Use IF NOT EXISTS / CONCURRENTLY so it's re-runnable and non-locking
#   - Add a check constraint: custom_domain must look like a domain (contains a dot)

# 2. Apply it:
docker compose exec app npx node-pg-migrate up

# 3. Verify it appears in applied migrations:
docker compose exec postgres psql -U user -d scaleforge \
  -c "SELECT id, name, run_on FROM pgmigrations ORDER BY run_on"

# 4. Verify the constraint works:
docker compose exec postgres psql -U user -d scaleforge \
  -c "UPDATE urls SET custom_domain = 'not-a-domain' WHERE id = (SELECT id FROM urls LIMIT 1)"
# Expected: ERROR: check constraint violated
```

<details>
<summary>Show migration file</summary>

```sql
-- migrations/004_add_custom_domain.sql

ALTER TABLE urls ADD COLUMN IF NOT EXISTS custom_domain VARCHAR(255);

-- Unique index: no two URLs can share the same custom domain
CREATE UNIQUE INDEX CONCURRENTLY IF NOT EXISTS urls_custom_domain_idx
  ON urls (custom_domain)
  WHERE custom_domain IS NOT NULL AND deleted_at IS NULL;

-- Check constraint: domain must contain at least one dot
ALTER TABLE urls ADD CONSTRAINT IF NOT EXISTS urls_custom_domain_format
  CHECK (custom_domain IS NULL OR custom_domain ~ '^[a-z0-9]([a-z0-9\-]*[a-z0-9])?(\.[a-z]{2,})+$');
```

</details>

---

## Exercise 4 — Add PgBouncer to Docker Compose

**Goal:** Route app connections through PgBouncer instead of directly to Postgres.

```yaml
# TODO: Add to docker-compose.yml

# 1. Add the pgbouncer service (see 06-connection-pooling-pgbouncer.md for config)

# 2. Change the app service DATABASE_PRIMARY_URL to point to pgbouncer:
#    DATABASE_PRIMARY_URL: postgres://user:pass@pgbouncer:6432/scaleforge

# 3. Start the stack:
docker compose up -d

# 4. Verify PgBouncer is proxying requests by checking its stats:
docker compose exec pgbouncer psql -h localhost -p 6432 -U pgbouncer pgbouncer -c "SHOW POOLS"

# Expected output shows your database in the pools table:
# database  | cl_active | cl_waiting | sv_active | sv_idle | maxwait
# scaleforge|     3     |     0      |     3     |    0    |   0

# 5. Verify zero errors: run the benchmark from Module 03 exercises
autocannon http://localhost:8080/bench01 -d 10 -c 100
```

---

## Summary Checklist

- [ ] `EXPLAIN ANALYZE` confirms Index Scan for code lookups (< 1ms)
- [ ] URL listing endpoint uses JOIN instead of N+1 queries
- [ ] Migration 004 applied successfully and re-runnable idempotently
- [ ] PgBouncer in docker-compose.yml, app connects through it
- [ ] `SHOW POOLS` in PgBouncer shows active connections
- [ ] Benchmark at 100 concurrent connections passes with zero errors

---

## Module 04 Capstone Milestone ✓

After completing these exercises, ScaleForge's data layer is production-grade:

| Requirement | Implementation |
|---|---|
| O(log N) code lookups | B-Tree unique partial index on `code` |
| Zero N+1 queries | JOIN with aggregation in listing endpoint |
| Read/write routing | Dual-pool: primary for writes, replica for reads |
| Zero-downtime migrations | Additive-only, `CONCURRENTLY`, idempotent |
| Connection scaling | PgBouncer transaction mode (100:20 multiplexing) |

**Next:** Module 05 covers caching — Redis eviction policies, cache invalidation strategies, and building a multi-tier cache (Nginx → Redis → Postgres) that pushes ScaleForge's redirect p99 well below 1ms.
