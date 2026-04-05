# 4.2 — Indexes and Query Optimization

## Concept

An index is a separate data structure that allows the database to locate rows without scanning the entire table. Without an index on `urls.code`, every redirect lookup requires Postgres to read every row — a sequential scan that degrades from 1ms to 10s as the table grows. This topic covers how indexes work, when to add them, and how to verify they're being used.

---

## Deep Dive

### How B-Tree Indexes Work

```
Table: urls (10 million rows)
  id | code      | target_url                | user_id | created_at
  ───┼───────────┼───────────────────────────┼─────────┼────────────
  1  | abc123    | https://example.com       | user_1  | 2024-01-01
  2  | xyz789    | https://google.com        | user_1  | 2024-01-02
  ...10 million rows...

SELECT * FROM urls WHERE code = 'abc123';

WITHOUT index (sequential scan):
  Postgres reads EVERY row, checks each code column
  10,000,000 rows × 8 bytes/row = ~80MB of disk reads
  Time: ~500ms on SSD, 5s on HDD

WITH B-Tree index on code:
  Index structure (balanced tree):
                    [m500000]
                   /          \
           [m250000]          [m750000]
          /         \        /          \
     [m125000]  [m375000] [m625000]  [m875000]
     ...
     
  Postgres traverses 24 levels (log2 of 10M) to find 'abc123'
  24 disk reads vs 10,000,000 disk reads
  Time: <1ms
  
  Space cost: ~200MB for the index (worth it!)
```

### Index Types in PostgreSQL

```
Index type         Best for                        Example
─────────────────  ──────────────────────────────  ─────────────────────────────────────
B-Tree (default)   Equality, range, ORDER BY        WHERE code = 'abc', WHERE created_at > '2024-01'
Hash               Equality only (faster for =)     WHERE session_id = 'xyz' (exact match only)  
GIN                Arrays, JSONB keys, full text    WHERE tags @> '{"nodejs"}'
GIST               Geographic data, ranges          WHERE point <-> '(0,0)' < 100 (geo proximity)
BRIN               Very large monotonically-growing WHERE created_at BETWEEN ...
                   tables (minimal storage)         (works for time-ordered append-only data)
Partial            Only index a subset of rows      WHERE deleted_at IS NULL (active records only)
Expression         Index on computed value          WHERE lower(email) = 'user@example.com'
```

### EXPLAIN ANALYZE Breakdown

```
Running: EXPLAIN ANALYZE SELECT * FROM urls WHERE code = 'abc123';

WITHOUT index (Seq Scan):
  Seq Scan on urls  (cost=0.00..235816.00 rows=1 width=87)
                    (actual time=312.458..456.789 rows=1 loops=1)
    Filter: (code = 'abc123')
    Rows Removed by Filter: 9999999
  Planning Time: 0.1 ms
  Execution Time: 456.9 ms   ← nearly half a second!

WITH index (Index Scan):
  Index Scan using urls_code_idx on urls
    (cost=0.56..8.58 rows=1 width=87)
    (actual time=0.041..0.043 rows=1 loops=1)
    Index Cond: (code = 'abc123')
  Planning Time: 0.1 ms
  Execution Time: 0.1 ms   ← 4000× faster

Key numbers to read:
  cost=X..Y    Estimated startup cost .. total cost (arbitrary units)
  rows=N       Estimated rows returned (check vs actual)
  actual time  Real measured time in milliseconds
  loops=N      How many times this node executed (watch for N+1 symptom)
  
Danger signs:
  Seq Scan on a large table  → add index
  rows=1000 actual rows=1    → outdated statistics, run ANALYZE
  loops=1000                 → N+1 query pattern in calling code
  Hash Join cost very high   → consider materialized view or denormalization
```

### Composite Indexes

```sql
-- Scenario: User dashboard listing for user's URLs
SELECT * FROM urls
WHERE user_id = $1 AND deleted_at IS NULL
ORDER BY created_at DESC
LIMIT 20;

-- Single-column index on user_id: uses index scan, but then sorts in memory
-- Composite index (user_id, created_at): index scan + index-based sort (no heap sort)
-- Include deleted_at in composite if filtering on it frequently

CREATE INDEX CONCURRENTLY urls_user_created_idx 
ON urls (user_id, created_at DESC)
WHERE deleted_at IS NULL;   -- partial index: only live URLs, so index is smaller
```

---

## Code Examples

### ScaleForge Schema with Indexes

```sql
-- migrations/001_initial_schema.sql

CREATE TABLE urls (
  id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
  code        VARCHAR(12) NOT NULL,
  target_url  TEXT        NOT NULL,
  user_id     UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  deleted_at  TIMESTAMPTZ           -- soft delete
);

-- Index 1: Primary access pattern — redirect lookup by short code
-- Unique ensures no duplicate codes at the DB level (not just app level)
CREATE UNIQUE INDEX CONCURRENTLY urls_code_idx
  ON urls (code)
  WHERE deleted_at IS NULL;
-- Partial: deleted URLs don't need to be in the active-lookup index
-- CONCURRENTLY: build index without locking the table (safe for production)

-- Index 2: User's URL listing — sorted by creation date
CREATE INDEX CONCURRENTLY urls_user_created_idx
  ON urls (user_id, created_at DESC)
  WHERE deleted_at IS NULL;

-- Index 3: Click events — query clicks for a URL in a time window
CREATE TABLE click_events (
  id         BIGSERIAL   PRIMARY KEY,
  url_id     UUID        NOT NULL REFERENCES urls(id) ON DELETE CASCADE,
  clicked_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  country    VARCHAR(2),
  referrer   TEXT
);

CREATE INDEX CONCURRENTLY click_events_url_time_idx
  ON click_events (url_id, clicked_at DESC);
-- BRIN would work here too (monotonically growing), but B-Tree is simpler
-- to reason about for range queries

-- Verify indexes exist:
-- SELECT indexname, indexdef FROM pg_indexes WHERE tablename = 'urls';
```

### Running EXPLAIN ANALYZE From TypeScript

```typescript
// src/db/query-analyzer.ts
// Use this in development or performance tests to catch missing indexes.

import { pool } from './pool.js';

export async function explainAnalyze(query: string, params: unknown[]): Promise<void> {
  const explainQuery = `EXPLAIN (ANALYZE, BUFFERS, FORMAT TEXT) ${query}`;
  const result = await pool.query<{ 'QUERY PLAN': string }>(explainQuery, params);

  console.log('\n--- EXPLAIN ANALYZE ---');
  for (const row of result.rows) {
    console.log(row['QUERY PLAN']);
  }
  console.log('--- END ---\n');
}

// Usage (dev only — never in production request path):
if (process.env.NODE_ENV === 'development') {
  await explainAnalyze(
    'SELECT * FROM urls WHERE code = $1',
    ['abc123']
  );
}

// What to look for:
// BAD: Seq Scan → add index
// GOOD: Index Scan or Index Only Scan
// CHECK: "Execution Time" — if >5ms for single row, investigate
// CHECK: "Rows Removed by Filter" — high value means poor selectivity
```

### Analyzing Index Usage with pg_stat_user_indexes

```typescript
// src/db/index-health.ts — check which indexes are actually being used
// Run this periodically to find unused indexes (they slow writes for no benefit)

import { pool } from './pool.js';

interface IndexStats {
  indexname: string;
  tablename: string;
  idx_scan: number;
  idx_tup_read: number;
  idx_tup_fetch: number;
  pg_relation_size: string;
}

export async function getIndexUsage(): Promise<IndexStats[]> {
  const result = await pool.query<IndexStats>(`
    SELECT
      s.indexrelname    AS indexname,
      s.relname         AS tablename,
      s.idx_scan,
      s.idx_tup_read,
      s.idx_tup_fetch,
      pg_size_pretty(pg_relation_size(s.indexrelid)) AS pg_relation_size
    FROM pg_stat_user_indexes s
    JOIN pg_index i ON s.indexrelid = i.indexrelid
    WHERE s.schemaname = 'public'
    ORDER BY s.idx_scan ASC;  -- Ascending: rarely-used indexes at top
  `);

  return result.rows;
}
// idx_scan = 0 after production traffic → candidate for removal
// (unless it's a constraint index like UNIQUE — those can't be removed without dropping constraint)
```

---

## Try It Yourself

**Exercise:** Use `EXPLAIN ANALYZE` to verify the code lookup uses an index scan.

```typescript
// index-exercise.ts — place in capstone/scaleforge/src/

import { pool } from './db/pool.js';
import { explainAnalyze } from './db/query-analyzer.js';

// TODO:
// 1. Run the redirect lookup query without any index:
//    DROP INDEX IF EXISTS urls_code_idx;
//    Run explainAnalyze('SELECT * FROM urls WHERE code = $1', ['abc123'])
//    Record: execution time, scan type, rows removed
//
// 2. Re-create the index:
//    CREATE UNIQUE INDEX CONCURRENTLY urls_code_idx ON urls (code)
//    WHERE deleted_at IS NULL;
//    Run explainAnalyze again.
//    Record: execution time, scan type
//
// 3. Generate 1 million rows with pg_bench or SQL:
//    INSERT INTO urls (code, target_url, user_id)
//    SELECT
//      substr(md5(random()::text), 1, 8),
//      'https://example.com/' || i,
//      gen_random_uuid()
//    FROM generate_series(1, 1000000) AS i;
//
// 4. Compare execution time with vs without index at 1M rows.
//    Fill in this table:
//      Rows     | No Index | With Index
//      ─────────┼──────────┼───────────
//      10,000   | ?ms      | ?ms
//      100,000  | ?ms      | ?ms
//      1,000,000| ?ms      | ?ms
```

<details>
<summary>Show expected results</summary>

```
Rows     | No Index (Seq Scan) | With Index (Index Scan)
─────────┼────────────────────┼────────────────────────
10,000   | ~3ms               | ~0.05ms
100,000  | ~25ms              | ~0.05ms
1,000,000| ~250ms             | ~0.05ms

Key insight: index lookup time is O(log N) — barely changes with row count.
Sequential scan is O(N) — grows linearly.

At 10M rows (ScaleForge's steady state after 1 year):
  Seq Scan: ~2,500ms (2.5 seconds per redirect — unusable)
  Index:    ~0.05ms (consistent regardless of table size)

Additional observation: "Index Only Scan" is even faster than "Index Scan"
because it reads directly from the index without touching the actual table rows.
This happens when all queried columns are in the index (covering index).

For the redirect query: SELECT target_url FROM urls WHERE code = $1
If you CREATE INDEX url_code_target_idx ON urls (code) INCLUDE (target_url);
PostgreSQL uses Index Only Scan → zero heap access.
```

</details>

---

## Capstone Connection

The `urls_code_idx` index is the single most critical performance component of ScaleForge — every redirect lookup depends on it. Without it, a table with 10M URLs would make every redirect take ~2.5 seconds. Adding `INCLUDE (target_url)` to make it a covering index (so Postgres never touches the actual table for the redirect query) is a stretch optimization covered in Module 08's performance tuning section.
