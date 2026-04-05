# 5.4 — Cache Stampede

## Concept

A cache stampede (also called a thundering herd) occurs when a popular cache key expires and many concurrent requests all find a cache miss simultaneously. Each request independently queries the database, causing a sudden spike of identical queries. The system that normally handles 5 req/s to the database suddenly faces 500 req/s at once — enough to saturate connection pools and cause cascading failures.

---

## Deep Dive

### The Stampede Timeline

```
  Scenario: 300 concurrent users hit /abc at t=300s (TTL=300s)

  t=299s: url:abc = "https://example.com" (1 second left on TTL)
  t=300s: TTL expires → key evicted from Redis
  
              ┌── Request 1: GET url:abc → MISS → SELECT urls ...
              ├── Request 2: GET url:abc → MISS → SELECT urls ...
              ├── Request 3: GET url:abc → MISS → SELECT urls ...
              │   ...x297 more queries...
              └── Request 300: GET url:abc → MISS → SELECT urls ...
              
                    300 simultaneous DB queries
                    Postgres max_connections = 100 → queue backlog
                    Query timeout = 5s → all 300 requests hang
                    
  Result: p99 latency spikes from 2ms → 5000ms for all users

  After all queries return: 300 writers race to SET url:abc
    → only 1 wins (last write wins in Redis), no harm done
    → but the damage — DB overload — already happened
```

### Solution A: Cache Lock (Mutex on Cache Miss)

```
  Only one request fetches from DB; others wait and re-read cache:
  
  t=300s: url:abc TTL expires
  
  Request 1: GET url:abc → MISS
             SETNX lock:url:abc "1" EX 5 → acquired lock
             SELECT urls WHERE code='abc' → "https://example.com"
             SET url:abc "https://example.com" EX 300
             DEL lock:url:abc
             → return "https://example.com"
             
  Request 2: GET url:abc → MISS (lock holder hasn't SET yet)
             SETNX lock:url:abc "1" → returns 0 (lock taken)
             wait 50ms → retry GET url:abc → HIT
             → return cached value (no DB query)
             
  DB queries: exactly 1 ✓
```

### Solution B: Probabilistic Early Expiration (XFetch)

```
  Refresh cache before it expires — with randomness to spread the load:
  
  XFetch formula:
    Should refresh = (currentTime - lastFetchTime) * beta * -log(rand()) > remainingTTL
    
    where:
      beta = tuning parameter (0.5–2.0, higher = more aggressive prefetch)
      rand() = random number [0, 1)
      The closer to expiry, the higher the probability of a refresh

  Example with remainingTTL = 10s, lastFetchTime = 290 ago, beta = 1.0:
    (290 * 1.0 * -log(0.03)) ≈ (290 * 3.5) ≈ 1015 >> 10 → refresh triggered early
    
  Result: Cache is refreshed by ONE request while all others still get
          cache hits (the old value is still valid for 10s).
          By the time TTL expires, new value is already in cache.
```

### Solution C: Stale-While-Revalidate

```
  Serve the expired (stale) value immediately; refresh in background:
  
  url:abc TTL = 300s. Hard expiry tracks freshness, not eviction.
  Store a separate "stale" key with a much longer TTL (3600s).
  
  t=300s: url:abc → MISS (soft TTL expired)
  
  Request 1: GET stale-url:abc → HIT (returns stale value, still valid for 3300s)
             Queue background job: refresh url:abc
             Return stale value immediately → user gets 302 in 1ms
             
  Background: SELECT urls WHERE code='abc'
              SET url:abc "new-target" EX 300
              SET stale-url:abc "new-target" EX 3600
              
  All 300 requests: serve from stale key instantly
  DB queries: 1 background (non-blocking) ✓
  
  Tradeoff: users see stale data for up to one refresh cycle
```

---

## Code Examples

### Solution A: Cache Lock

```typescript
// src/cache/with-lock.ts — distributed mutex to prevent cache stampede

import { redisClient } from './redis.client.js';
import pino from 'pino';

const log = pino({ name: 'cache-lock' });

export async function getWithLock<T>(
  key: string,
  fetcher: () => Promise<T | null>,
  ttl: number,
  options: { lockTtl?: number; retries?: number; retryDelay?: number } = {}
): Promise<T | null> {
  const lockKey = `lock:${key}`;
  const { lockTtl = 5, retries = 10, retryDelay = 50 } = options;

  // Fast path: cache hit
  const cached = await redisClient.get(key);
  if (cached !== null) {
    return cached === '\x00' ? null : (JSON.parse(cached) as T);
  }

  // Try to acquire lock using SET NX EX (atomic)
  const lockAcquired = await redisClient.set(lockKey, '1', 'EX', lockTtl, 'NX');

  if (lockAcquired === 'OK') {
    // This instance won the lock — fetch from DB
    log.debug({ key }, 'Acquired cache lock, fetching from DB');
    try {
      const value = await fetcher();
      const toStore = value !== null ? JSON.stringify(value) : '\x00';
      const storeTtl = value !== null ? ttl : 30; // shorter TTL for null sentinel
      await redisClient.set(key, toStore, 'EX', storeTtl);
      return value;
    } finally {
      await redisClient.del(lockKey);
    }
  }

  // Lock not acquired — another instance is fetching
  // Poll for cache to become populated
  for (let i = 0; i < retries; i++) {
    await sleep(retryDelay);
    const polled = await redisClient.get(key);
    if (polled !== null) {
      log.debug({ key, attempt: i + 1 }, 'Cache populated by lock holder');
      return polled === '\x00' ? null : (JSON.parse(polled) as T);
    }
  }

  // Lock holder probably crashed — fetch directly as fallback
  log.warn({ key }, 'Lock holder did not populate cache; fetching directly');
  return fetcher();
}

function sleep(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

// Usage in redirect handler:
// const target = await getWithLock(
//   `url:${code}`,
//   () => dbGetTarget(code),
//   300
// );
```

### Solution B: Probabilistic Early Expiration (XFetch)

```typescript
// src/cache/with-per.ts — XFetch algorithm (probabilistic early expiration)
// Paper: "Optimal Probabilistic Cache Stampede Prevention" — Vattani, Chierichetti, Lowenstein

import { redisClient } from './redis.client.js';

interface PerEntry<T> {
  value: T | null;
  fetchedAt: number;   // Unix timestamp seconds
  delta: number;       // Time it took to fetch (seconds) — higher delta = more eager refresh
}

export async function getWithPER<T>(
  key: string,
  fetcher: () => Promise<T | null>,
  ttl: number,
  beta = 1.0  // Tuning: 0.5 = conservative, 2.0 = aggressive prefetch
): Promise<T | null> {
  const metaKey = `per-meta:${key}`;
  const now = Math.floor(Date.now() / 1000);

  const [raw, remainingTtlStr] = await Promise.all([
    redisClient.get(metaKey),
    redisClient.ttl(metaKey),
  ]);

  if (raw !== null && remainingTtlStr > 0) {
    const entry = JSON.parse(raw) as PerEntry<T>;
    const remainingTtl = remainingTtlStr;

    // XFetch decision: should we early-refresh?
    const shouldRefresh = entry.delta * beta * -Math.log(Math.random()) > remainingTtl;

    if (!shouldRefresh) {
      // Serve from cache — TTL is still healthy
      return entry.value;
    }

    // Probabilistically decided to refresh early
    // (other concurrent requests will NOT refresh because they'll calculate
    //  different random numbers — most will return false for shouldRefresh)
  }

  // Cache miss OR decided to refresh: fetch from DB
  const fetchStart = Date.now();
  const value = await fetcher();
  const delta = (Date.now() - fetchStart) / 1000;

  const entry: PerEntry<T> = { value, fetchedAt: now, delta };
  await redisClient.set(metaKey, JSON.stringify(entry), 'EX', ttl);

  return value;
}
```

### Solution C: Stale-While-Revalidate

```typescript
// src/cache/with-swr.ts — serve stale, refresh in background
// Zero blocking: all requests return in <1ms, DB is never hit synchronously

import { redisClient } from './redis.client.js';

const FRESH_TTL = 300;   // 5 minutes — "fresh" window
const STALE_TTL = 3600;  // 1 hour — "stale" window (emergency fallback)

async function getWithSWR<T extends object>(
  code: string,
  fetcher: () => Promise<T | null>
): Promise<T | null> {
  const freshKey = `url:${code}`;
  const staleKey = `stale-url:${code}`;

  // Try fresh cache first
  const fresh = await redisClient.get(freshKey);
  if (fresh !== null) {
    return fresh === '\x00' ? null : (JSON.parse(fresh) as T);
  }

  // Fresh key expired — try stale key
  const stale = await redisClient.get(staleKey);
  if (stale !== null) {
    // Serve stale immediately; refresh in background
    setImmediate(() => refreshInBackground(code, fetcher));
    return stale === '\x00' ? null : (JSON.parse(stale) as T);
  }

  // Both expired (cold start or very old URL) — block on DB fetch
  return refreshInBackground(code, fetcher);
}

async function refreshInBackground<T extends object>(
  code: string,
  fetcher: () => Promise<T | null>
): Promise<T | null> {
  // Prevent concurrent background refreshes with a short-lived lock
  const refreshLock = await redisClient.set(`refresh-lock:${code}`, '1', 'EX', 10, 'NX');
  if (!refreshLock) return null; // Another instance is already refreshing

  try {
    const value = await fetcher();
    const toStore = value !== null ? JSON.stringify(value) : '\x00';
    await Promise.all([
      redisClient.set(`url:${code}`, toStore, 'EX', FRESH_TTL),
      redisClient.set(`stale-url:${code}`, toStore, 'EX', STALE_TTL),
    ]);
    return value;
  } finally {
    await redisClient.del(`refresh-lock:${code}`);
  }
}
```

---

## Try It Yourself

**Exercise:** Observe and then fix a cache stampede.

```typescript
// cache-stampede.exercise.ts

// STEP 1 — Simulate the stampede
// Populate cache key "bench:url" → let TTL expire → fire 100 concurrent requests

async function simulateStampede(handler: (code: string) => Promise<string | null>) {
  // TODO: Set a key in Redis with a 3-second TTL
  await redisClient.set('bench:url', 'https://simulate.com', 'EX', 3);
  
  // Wait for expiry
  await sleep(3100);
  
  // Track DB call count (patch your DB pool to increment a counter)
  const dbCallsBefore = getDbCallCount();
  
  // TODO: Fire 100 concurrent requests
  await Promise.all(Array.from({ length: 100 }, () => handler('bench')));
  
  const dbCallsAfter = getDbCallCount();
  console.log(`DB calls during stampede: ${dbCallsAfter - dbCallsBefore}`);
  // Expected without fix: ~100
  // Expected with getWithLock: 1
  // Expected with SWR: 0 (stale serves) or 1 (background refresh)
}

// TODO: Run simulateStampede with the naive handler, then with getWithLock, then with SWR
// Measure and compare DB call counts using a mock counter
```

<details>
<summary>Show exercise solution</summary>

```typescript
import { redisClient } from './cache/redis.client.js';
import { getWithLock } from './cache/with-lock.js';

let dbCallCount = 0;

async function dbGetTarget(code: string): Promise<string | null> {
  dbCallCount++; // count every DB call
  await sleep(10); // simulate a real DB round trip
  return 'https://simulate.com';
}

// Handler WITHOUT protection
async function naiveHandler(code: string): Promise<string | null> {
  const cached = await redisClient.get(`url:${code}`);
  if (cached !== null) return cached;
  const value = await dbGetTarget(code);
  if (value) await redisClient.set(`url:${code}`, value, 'EX', 300);
  return value;
}

// Handler WITH lock
async function lockedHandler(code: string): Promise<string | null> {
  return getWithLock(`url:${code}`, () => dbGetTarget(code), 300);
}

async function runBenchmark(
  label: string,
  handler: (code: string) => Promise<string | null>
) {
  await redisClient.del('url:bench'); // clear cache
  dbCallCount = 0;
  const start = Date.now();
  await Promise.all(Array.from({ length: 100 }, () => handler('bench')));
  console.log(`${label}: ${dbCallCount} DB calls, ${Date.now() - start}ms total`);
}

await runBenchmark('Naive (stampede)', naiveHandler);
await runBenchmark('With cache lock', lockedHandler);

// Example output:
// Naive (stampede): 97 DB calls, 312ms total
// With cache lock: 1 DB calls, 67ms total
```

</details>

---

## Capstone Connection

ScaleForge's redirect endpoint is the hottest read path. Every viral link (think a URL shared on social media) creates thousands of concurrent requests. The `getWithLock` pattern in `url-cache.ts` ensures that when the TTL for `url:abc` expires at peak traffic, exactly one instance fetches from Postgres — not 300. The 50ms polling delay is imperceptible to end users given the redirect itself is sub-5ms. The XFetch pattern is an alternative that eliminates even the 50ms wait — implement it if your benchmarks show lock contention.
