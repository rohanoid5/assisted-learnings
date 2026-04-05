# 8.3 — Query Performance Analysis

## Concept

A slow SQL query is the most common cause of high p99 latency in database-backed services. `EXPLAIN ANALYZE` shows exactly what Postgres chose to do for a query — whether it used an index, how many rows it scanned, and where time was spent. Reading `EXPLAIN` output is the single most valuable debugging skill for backend engineers.

---

## Deep Dive

### Reading `EXPLAIN ANALYZE` Output

```sql
-- ScaleForge redirect query
EXPLAIN ANALYZE
  SELECT original_url, is_active
  FROM urls
  WHERE short_code = 'abc123';
```

```
  WITHOUT an index:

  Seq Scan on urls  (cost=0.00..2847.00 rows=1 width=120)
                    (actual time=18.432..18.444 rows=1 loops=1)
    Filter: ((short_code)::text = 'abc123'::text)
    Rows Removed by Filter: 142349
  Planning Time: 0.15 ms
  Execution Time: 18.45 ms
  
  ──── Reading this ────────────────────────────────────────────
  "Seq Scan" = read EVERY row in the table (142,350 rows scanned
               to find 1 match)
  "Rows Removed by Filter: 142349" = all those reads were wasted
  "Execution Time: 18.45 ms" = 18ms for one redirect → terrible
  
  ──────────────────────────────────────────────────────────────

  WITH an index on short_code:

  Index Scan using idx_urls_short_code on urls
    (cost=0.42..8.44 rows=1 width=120)
    (actual time=0.028..0.030 rows=1 loops=1)
    Index Cond: ((short_code)::text = 'abc123'::text)
  Planning Time: 0.12 ms
  Execution Time: 0.03 ms
  
  ──── Reading this ────────────────────────────────────────────
  "Index Scan" = used the index, jumped directly to the row
  "Execution Time: 0.03 ms" = 600× faster
```

### How Indexes Work Internally

```
  B-Tree index (default Postgres index type):
  
  Conceptually a sorted tree of (value → heap page location):
  
             ┌──────────┐
             │  "mnopqr" │
             └──┬────┬──┘
      ┌─────────┘    └─────────┐
  ┌───┴────┐              ┌────┴───┐
  │ "abcde"│              │ "stuv" │
  └───┬────┘              └────────┘
      │
  ┌───┴──────────────────┐
  │ short_code  │ row ptr │
  │ "abc123"    │ page 42 │
  │ "abc456"    │ page 17 │
  └─────────────┴─────────┘
  
  lookup("abc123"):
    1. Start at root
    2. "abc123" < "mnopqr" → go left
    3. "abc123" > "abcde" → go right (leaf)
    4. Found page 42 → read that row
    
  3-4 page reads instead of reading all 142,350 rows.
  
  ─────────────────────────────────────────────────────────
  
  When NOT to add an index:
  
  - On a column that you never filter/sort by
  - On a very low-cardinality column (e.g., boolean is_active
    where 99% are true — a seq scan on 1% still reads that 1%)
  - On tables with heavy write load (each INSERT/UPDATE must
    also update every index on that table)
```

### Common Slow Query Patterns

```
  1. Missing index on foreign key
     SELECT * FROM click_events WHERE url_id = $1
     → No index on click_events.url_id → SeqScan on millions of rows
     Fix: CREATE INDEX idx_click_events_url_id ON click_events(url_id);

  2. Function on indexed column (index not used!)
     WHERE LOWER(email) = $1        -- index on email, not LOWER(email)
     Fix: CREATE INDEX idx_users_email_lower ON users(LOWER(email));
          (functional index)

  3. Leading wildcard pattern
     WHERE short_code LIKE '%abc'   -- B-tree can't help with suffix search
     Fix: Reverse the column and match the reverse prefix,
          or use pg_trgm trigram index for full substring search

  4. N+1 query: loop in application code
     for (const url of urls) {
       await pool.query('SELECT * FROM click_events WHERE url_id = $1', [url.id]);
     }
     Fix: JOIN or IN clause — one query instead of N

  5. Missing LIMIT on large result sets
     SELECT * FROM click_events WHERE url_id = $1
     -- returns 10 million rows to Node.js
     Fix: Always paginate: LIMIT 100 OFFSET $2
```

---

## Code Examples

### Query Analysis Helper

```typescript
// src/db/analyze.ts
// Development-only helper to print EXPLAIN ANALYZE for any query.
// Never use in production — EXPLAIN ANALYZE actually EXECUTES the query.

import { primaryPool } from './pool.js';

export async function analyzeQuery(
  sql: string,
  params: unknown[],
): Promise<void> {
  if (process.env.NODE_ENV === 'production') {
    throw new Error('analyzeQuery must not run in production');
  }

  const explainSql = `EXPLAIN (ANALYZE, BUFFERS, FORMAT TEXT) ${sql}`;
  const result = await primaryPool.query(explainSql, params);
  const plan = (result.rows as Array<{ 'QUERY PLAN': string }>)
    .map((r) => r['QUERY PLAN'])
    .join('\n');

  console.log('\n─── EXPLAIN ANALYZE ───────────────────────────────');
  console.log(sql);
  console.log('────────────────────────────────────────────────────');
  console.log(plan);
  console.log('────────────────────────────────────────────────────\n');
}
```

### Slow Query Logging via `pg` Event

```typescript
// src/db/slow-query-logger.ts
// Logs any query that takes longer than a threshold.
// Add as a wrapper around pool.query().

import type { Pool } from 'pg';
import { logger } from '../logger.js';
import { Histogram } from 'prom-client';
import { metricsRegistry } from '../metrics/registry.js';

const SLOW_QUERY_MS = Number(process.env.SLOW_QUERY_THRESHOLD_MS ?? 100);

const dbQueryDuration = new Histogram({
  name: 'db_query_duration_seconds',
  help: 'PostgreSQL query duration',
  labelNames: ['query_name'] as const,
  buckets: [0.001, 0.005, 0.01, 0.05, 0.1, 0.5, 1, 5],
  registers: [metricsRegistry],
});

type QueryName = string;

// Typed wrapper — use this instead of pool.query() directly
export async function query<T extends pg.QueryResultRow>(
  pool: Pool,
  queryName: QueryName,
  sql: string,
  params?: unknown[],
): Promise<pg.QueryResult<T>> {
  const start = performance.now();
  try {
    const result = await pool.query<T>(sql, params);
    return result;
  } finally {
    const durationMs = performance.now() - start;
    dbQueryDuration.observe({ query_name: queryName }, durationMs / 1000);

    if (durationMs > SLOW_QUERY_MS) {
      logger.warn({
        queryName,
        durationMs: Math.round(durationMs),
        threshold: SLOW_QUERY_MS,
      }, 'Slow query detected');
    }
  }
}

// Usage:
// import { query } from '../db/slow-query-logger.js';
// const result = await query(primaryPool, 'get_url', 'SELECT ... WHERE short_code = $1', [code]);
```

### Important ScaleForge Indexes

```sql
-- migrations/004_performance_indexes.sql

-- Hot path: redirect lookup
-- Every redirect hits this query. MUST use index scan.
CREATE INDEX CONCURRENTLY idx_urls_short_code
  ON urls (short_code);

-- Click analytics: recent events per URL
CREATE INDEX CONCURRENTLY idx_click_events_url_id_created
  ON click_events (url_id, created_at DESC);

-- User's URLs page: paginated list
CREATE INDEX CONCURRENTLY idx_urls_user_id_created
  ON urls (user_id, created_at DESC)
  WHERE deleted_at IS NULL;  -- partial index: skip soft-deleted rows

-- CONCURRENTLY means:
--   Index builds in the background without locking writes.
--   Takes longer but is safe in production (no downtime).
```

---

## Try It Yourself

**Exercise:** Find and fix a slow query using `EXPLAIN ANALYZE`.

```sql
-- slow-query.exercise.sql

-- Step 1: Seed the database with enough data to see the problem
INSERT INTO urls (short_code, original_url, user_id, created_at)
SELECT
  md5(random()::text),
  'https://example.com/' || generate_series,
  gen_random_uuid(),
  NOW() - (random() * INTERVAL '365 days')
FROM generate_series(1, 200000);

-- Step 2: Run EXPLAIN ANALYZE on the redirect query WITHOUT the index
--         (Drop the index first if it exists)
DROP INDEX IF EXISTS idx_urls_short_code;

EXPLAIN ANALYZE
  SELECT original_url, is_active
  FROM urls
  WHERE short_code = 'abc123';

-- TODO: Record the execution time (____ ms)

-- Step 3: Create the index
CREATE INDEX CONCURRENTLY idx_urls_short_code ON urls (short_code);

-- Step 4: Run EXPLAIN ANALYZE again
EXPLAIN ANALYZE
  SELECT original_url, is_active
  FROM urls
  WHERE short_code = 'abc123';

-- TODO: Record the new execution time (____ ms) and the speedup ratio.

-- Step 5: Find click_events for a URL
-- Does this use an index? What does the plan say?
EXPLAIN ANALYZE
  SELECT COUNT(*) FROM click_events
  WHERE url_id = (SELECT id FROM urls WHERE short_code = 'abc123');
```

<details>
<summary>Show expected results and discussion</summary>

```
Without index:
  Seq Scan — ~30ms for 200,000 rows
  Rows Removed by Filter: 199,999

With index:
  Index Scan — ~0.03ms
  Speedup: ~1000×

For click_events:
  If url_id has no index → Seq Scan on potentially millions of rows.
  Fix: CREATE INDEX idx_click_events_url_id ON click_events (url_id);
  
  If url_id already has an index → Index Scan.
  
  For COUNT(*) Postgres may use "Index Only Scan" if the index
  covers all needed columns — even faster than Index Scan.
```

</details>

---

## Capstone Connection

The `short_code` index on the `urls` table is the single most important optimization in ScaleForge. Without it, adding a second user makes redirects twice as slow. With it, a Seq Scan at 200,000 rows takes 18ms; an Index Scan takes 0.03ms — a 600× difference. The slow query logger exports a `db_query_duration_seconds` histogram to Prometheus, so a Grafana alert fires if any named query regresses beyond its expected duration.
