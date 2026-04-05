# Module 05 — Exercises

## Prerequisites

Modules 01–05 topics complete. ScaleForge running locally with Redis and Postgres via Docker Compose.

```bash
# Start dependencies
docker compose up -d postgres redis

# Verify Redis is running
redis-cli ping   # → PONG

# Start ScaleForge
cd capstone/scaleforge
npm run dev
```

---

## Exercise 1 — Observe Cache Behavior with `redis-cli monitor`

**Goal:** Watch live cache hits and misses in real time during a load test.

**Steps:**

1. Open two terminals.

2. Terminal 1 — start Redis monitor:
   ```bash
   redis-cli monitor
   ```

3. Terminal 2 — create a URL and run a load test:
   ```bash
   # Create a short URL
   curl -X POST http://localhost:3001/api/v1/urls \
     -H 'Content-Type: application/json' \
     -d '{"url": "https://example.com"}'
   # Note the code returned, e.g., "abc123"
   
   # Clear Redis so we start cold
   redis-cli DEL url:abc123
   
   # Run load test
   npx autocannon -d 10 -c 20 http://localhost:3001/abc123
   ```

4. Watch Terminal 1. You should see:
   - First request: `"GET" "url:abc123"` → miss, then `"SET" "url:abc123" "https://example.com"`
   - All subsequent: `"GET" "url:abc123"` (no SET = cache hits)

5. Check the `X-Cache` response header:
   ```bash
   curl -v http://localhost:3001/abc123 2>&1 | grep X-Cache
   # X-Cache: HIT-L2  (Redis hit)
   # X-Cache: HIT-L1  (In-process LRU hit)
   # X-Cache: MISS    (served from DB)
   ```

**Expected outcome:** After the first request populates the cache, all subsequent requests should be `HIT-L2` or `HIT-L1`. Zero further DB queries.

---

## Exercise 2 — Simulate and Fix a Cache Invalidation Bug

**Goal:** Experience the "stale for 5 minutes" problem if you invalidate out of order, then implement the fix.

**Setup:**

```typescript
// invalidation-bug.ts

import { redisClient } from './src/cache/redis.client.js';
import { primaryPool } from './src/db/pool.js';

const code = 'bugtest';

// Buggy invalidation order: DELETE cache BEFORE writing DB
// (leaves a window where DB has old value but cache is empty)
async function buggyUpdateUrl(newTarget: string) {
  await redisClient.del(`url:${code}`);  // ← wrong order
  // ↑ Between this line and the next, a concurrent reader
  //   will miss cache, read old value from DB, and re-cache old value!
  await new Promise(r => setTimeout(r, 100)); // simulate slow DB
  await primaryPool.query(
    'UPDATE urls SET target_url = $1 WHERE code = $2',
    [newTarget, code]
  );
}
```

**Steps:**

1. Create a URL with code `bugtest`
2. Access it once to populate cache
3. Start a concurrent reader that reads every 10ms
4. Call `buggyUpdateUrl('https://new.com')`
5. Check if the concurrent reader ever sees the old value re-cached

**Fix:** Swap the order — write DB first, then delete cache. Verify the fix by re-running the concurrent reader test.

---

## Exercise 3 — Measure Cache Stampede vs. Locked Handler

**Goal:** Quantify the difference in DB query count between a naive handler and one protected with `getWithLock`.

```typescript
// stampede-bench.exercise.ts

import { redisClient } from './src/cache/redis.client.js';
import { getWithLock } from './src/cache/with-lock.js';

let dbCallCount = 0;

async function mockDbFetch(code: string): Promise<string | null> {
  dbCallCount++;
  await new Promise(r => setTimeout(r, 20)); // 20ms simulated DB latency
  return `https://target-for-${code}.com`;
}

async function naiveGet(code: string): Promise<string | null> {
  const cached = await redisClient.get(`url:${code}`);
  if (cached !== null) return cached;
  const value = await mockDbFetch(code);
  if (value) await redisClient.set(`url:${code}`, value, 'EX', 300);
  return value;
}

async function lockedGet(code: string): Promise<string | null> {
  return getWithLock(`url:${code}`, () => mockDbFetch(code), 300);
}

// TODO:
// 1. Run naiveGet with 100 concurrent requests on a cache miss
//    Count how many times mockDbFetch was called
// 2. Clear Redis and run lockedGet with 100 concurrent requests
//    Count how many times mockDbFetch was called
// 3. Fill in expected vs. actual results below:
//
//    Naive:  expected ~100 DB calls  actual: ___
//    Locked: expected 1 DB call      actual: ___
```

<details>
<summary>Show solution</summary>

```typescript
async function runTest(label: string, handler: (code: string) => Promise<string | null>) {
  await redisClient.del('url:stampede');
  dbCallCount = 0;
  const start = Date.now();
  await Promise.all(Array.from({ length: 100 }, () => handler('stampede')));
  console.log(`${label}: ${dbCallCount} DB calls in ${Date.now() - start}ms`);
}

await runTest('Naive (stampede)', naiveGet);
await runTest('With lock (protected)', lockedGet);

// Output:
// Naive (stampede): 97 DB calls in 310ms
// With lock (protected): 1 DB calls in 72ms
```

</details>

---

## Exercise 4 — Cold vs. Warm Cache Latency Comparison

**Goal:** Measure the p99 latency improvement from cache warming.

**Steps:**

1. **Cold run** — flush Redis before the test:
   ```bash
   redis-cli FLUSHALL
   npx autocannon -d 30 -c 50 \
     --latency \
     http://localhost:3001/<your-url-code>
   ```
   Record: `p99`, `avg`, cache miss rate from `X-Cache` headers.

2. **Warm run** — populate cache first, then test:
   ```bash
   # Hit the URL once to warm it up
   curl http://localhost:3001/<your-url-code>
   
   # Now run the load test with warm cache
   npx autocannon -d 30 -c 50 \
     --latency \
     http://localhost:3001/<your-url-code>
   ```
   Record: `p99`, `avg`, cache miss rate.

3. **Startup warm run** — enable `warmUpCache()` in server.ts, restart, and immediately run:
   ```bash
   npx autocannon -d 30 -c 50 \
     --latency \
     http://localhost:3001/<your-url-code>
   ```
   Compare: is there any cold-start penalty even on initial traffic?

**Expected results:**

| Scenario | avg latency | p99 latency | DB queries |
|---|---|---|---|
| Cold Redis | ~15ms | ~50ms | ~30% of requests |
| Warm Redis | ~1.5ms | ~4ms | ~1% of requests |
| warmUpCache() | ~1ms | ~3ms | 0% (all from cache) |
