# Module 08 — Exercises: Performance & Monitoring

---

## Exercise 1: Flame Graph Investigation

**Goal:** Identify the largest CPU consumer in the redirect path using `0x`.

**Setup:**
```bash
npm install -g 0x autocannon

# Build and start ScaleForge under the profiler
0x dist/server.js &
SERVER_PID=$!
```

**Steps:**
```bash
# 1. Warm up the cache so requests are all cache hits
for i in $(seq 1 100); do curl -s http://localhost:3001/abc123 > /dev/null; done

# 2. Apply load for 15 seconds
autocannon -c 100 -d 15 http://localhost:3001/abc123

# 3. Stop the server (0x generates the flame graph)
kill $SERVER_PID
# Opens: flamegraph.html
```

**What to look for:**
1. Find the widest bar in the flame graph. What function is it?
2. Is the widest bar in your code or in `node_modules`?
3. Look for any synchronous operation (marked as not async) consuming >5% CPU.

**Hypothesis challenge:** Before running, predict which function will be hottest. Check if you're right.

---

## Exercise 2: Pool Exhaustion Under Load

**Goal:** Observe pool waiting requests spike, and add a circuit breaker.

**Steps:**
```bash
# 1. Set max pool to 3 connections
export DB_POOL_MAX=3
node dist/server.js &

# 2. Disable Redis so every redirect hits the DB
redis-cli DEBUG SLEEP 9999   # makes Redis unresponsive for ~3 hours

# 3. Blast requests
autocannon -c 100 -d 15 http://localhost:3001/abc123

# 4. Watch Prometheus metrics during the test
watch -n 1 'curl -s http://localhost:3001/metrics | grep pg_pool'
```

**Expected:** `pg_pool_waiting_requests` spikes. After `connectionTimeoutMillis=5000ms`, requests fail with 500.

**Task:** Modify the redirect handler to:
1. Catch the `"timeout exceeded"` error from pg
2. Return `503 Service Unavailable` with `Retry-After: 5`
3. Increment a `pg_pool_timeout_total` Prometheus counter
4. Verify this with autocannon — error responses should be 503, not 500

---

## Exercise 3: Slow Query Detection

**Goal:** Add indexes and verify EXPLAIN ANALYZE shows the improvement.

**Steps:**
```sql
-- Connect to Postgres
psql -U app -d scaleforge

-- 1. Seed 200,000 URLs
INSERT INTO urls (short_code, original_url, user_id, created_at)
SELECT
  substr(md5(random()::text), 1, 8),
  'https://example.com/' || generate_series,
  gen_random_uuid(),
  NOW() - (random() * INTERVAL '365 days')
FROM generate_series(1, 200000);

-- 2. Check for missing indexes (watch for "Seq Scan" on large tables)
EXPLAIN ANALYZE
  SELECT original_url FROM urls WHERE short_code = 'abc12345';

-- 3. Record execution time (should be 15-30ms)

-- 4. Create the index
CREATE INDEX CONCURRENTLY idx_urls_short_code ON urls(short_code);

-- 5. Re-run EXPLAIN ANALYZE
-- Expected: Index Scan, 0.03-0.1ms

-- 6. Check for any other missing indexes:
SELECT
  relname AS table,
  seq_scan,
  idx_scan,
  n_live_tup AS rows
FROM pg_stat_user_tables
ORDER BY seq_scan DESC;
-- High seq_scan count on a large table = likely missing index
```

**Fill in the table:**

| Query | Before index | After index | Speedup |
|-------|-------------|-------------|---------|
| redirect lookup | ____ms | ____ms | ____× |
| click events by URL | ____ms | ____ms | ____× |

---

## Exercise 4: SLO Regression Test in CI

**Goal:** Run a load test as part of your build, fail if SLO is not met.

**Setup:** Add to `package.json`:
```json
{
  "scripts": {
    "load-test": "ts-node scripts/load-test.ts"
  }
}
```

**Steps:**
```bash
# 1. Copy the load test script from 05-load-testing.md

# 2. Run it against your running service
npm run load-test

# 3. Introduce a regression: add a deliberate 10ms sleep to the redirect handler
#    res.set('X-Slow', 'true');
#    await new Promise(resolve => setTimeout(resolve, 10));
#
# 4. Re-run the load test — it should fail (p99 exceeds 50ms)

# 5. Remove the sleep and verify the test passes again

# 6. Add this to your GitHub Actions workflow:
```

```yaml
# .github/workflows/load-test.yml
name: Load Test
on: [push]
jobs:
  load-test:
    runs-on: ubuntu-latest
    services:
      postgres:
        image: postgres:16-alpine
        env: { POSTGRES_DB: scaleforge, POSTGRES_USER: app, POSTGRES_PASSWORD: secret }
      redis:
        image: redis:7-alpine
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-node@v4
        with: { node-version: '20' }
      - run: npm ci && npm run build
      - run: npm start &
      - run: sleep 3   # wait for server to start
      - run: npm run load-test
```
