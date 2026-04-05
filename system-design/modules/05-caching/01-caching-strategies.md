# 5.1 — Caching Strategies

## Concept

A caching strategy defines when data is read from and written to the cache relative to the backing store. Picking the wrong strategy causes bugs like serving stale data forever, writing data that's never read, or losing writes on cache eviction.

---

## Deep Dive

### The Three Read Patterns

```
Cache-Aside (Lazy Loading):
  Read:
    1. Check cache. HIT → return.
    2. MISS → query DB, populate cache, return.
    
  Write:
    1. Write to DB.
    2. Invalidate (DELETE) cache key.
    
  Pro: Only caches what's actually requested (no wasted memory)
  Pro: DB is still the source of truth — cache failure is transparent
  Con: Cache miss causes double round-trip (cache + DB)
  Con: Stale data possible between DB write and cache invalidation
  
  Used by: ScaleForge redirect cache ← this is what you have

  ┌─────┐  1.GET    ┌───────┐  2.MISS   ┌────────┐
  │App  │──────────►│ Cache │──────────►│  DB    │
  │     │◄──────────│       │◄──────────│        │
  │     │  MISS     │       │  3.SET    │        │
  └─────┘           └───────┘           └────────┘
   │                    ▲
   │     4.return       │
   └────────────────────┘

Read-Through:
  Cache sits in front of DB; cache handles DB reads automatically.
  App only talks to cache.
  
  App → Cache → (on miss) DB → Cache (stores result) → App
  
  Pro: Simpler app code — only one data source to query
  Con: Cold start: first request always misses (warm-up period)
  Con: Cache must know how to talk to the DB (coupling)
  Used by: CDN origin fetch, Varnish, some ORMs

Write-Through:
  On every write, data is written to both cache AND DB synchronously.
  
  App → Cache (write + forward) → DB
  
  Pro: Cache always has warm data, no cold start for written keys
  Con: Every write is slower (two synchronous writes)
  Con: Cache is filled with data that may never be read
  Used by: CPU L1/L2/L3 caches, hardware write-back buffers
```

### The Two Write Patterns

```
Write-Around:
  Write directly to DB; skip cache.
  Cache is populated on future reads (demand-driven).
  
  Good for: data written once, read many times later 
  Bad for:  data read immediately after write (read-your-writes)

Write-Back (Write-Behind):
  Write to cache immediately; write to DB asynchronously (later).
  
  App writes → Cache (immediate) → [async background] → DB
  
  Pro: Blazing fast writes (cached write returns instantly)
  Con: DATA LOSS if cache crashes before DB write completes
  Used when: throughput is paramount, small data loss is acceptable
  ScaleForge click counting: Redis INCR → async BullMQ → Postgres UPDATE
  (This is effectively write-back, with BullMQ as the async bridge)
```

### Choosing a Strategy

```
Data type                   Read pattern    Write pattern     TTL
──────────────────────────  ──────────────  ───────────────── ──────────
Short URL redirect targets  Cache-aside     Write-around      5 min
  (url:{code} → target)     (lazy)          (invalidate)
  
Session tokens              Cache-aside     Write-through     24 hr
  (token:{id} → user_id)
  
Click counters              Write-back      —                 None
  (clicks:{code})           (Redis INCR)                      (flush async)
  
Rate limit counters         Read-through    Write-back        60s
  (ratelimit:{ip})          (Redis INCR)    (in-Redis)        (key TTL = window)
  
User profile                Cache-aside     Write-around      10 min
  (user:{id})               (lazy)
```

---

## Code Examples

### Cache-Aside Pattern

```typescript
// src/cache/url-cache.ts — canonical cache-aside implementation

import { redisClient } from './redis.client.js';
import { primaryPool } from '../db/pool.js';

const KEY_PREFIX = 'url:';
const TTL_SECONDS = 300; // 5 minutes

// READ — cache-aside
export async function getCachedTarget(code: string): Promise<string | null> {
  // 1. Check cache
  const cached = await redisClient.get(`${KEY_PREFIX}${code}`);
  if (cached !== null) {
    return cached === '' ? null : cached; // '' sentinel = "known not-found"
  }

  // 2. Cache miss — query DB
  const result = await primaryPool.query<{ target_url: string }>(
    'SELECT target_url FROM urls WHERE code = $1 AND deleted_at IS NULL',
    [code]
  );

  if (result.rows.length === 0) {
    // Cache negative result with shorter TTL to prevent DB hammering for non-existent codes
    await redisClient.set(`${KEY_PREFIX}${code}`, '', 'EX', 30);
    return null;
  }

  const targetUrl = result.rows[0].target_url;

  // 3. Populate cache
  await redisClient.set(`${KEY_PREFIX}${code}`, targetUrl, 'EX', TTL_SECONDS);

  return targetUrl;
}

// WRITE — write-around + cache invalidation
export async function updateUrlTarget(code: string, newTarget: string): Promise<void> {
  // Write to DB first (source of truth)
  await primaryPool.query(
    'UPDATE urls SET target_url = $1 WHERE code = $2',
    [newTarget, code]
  );

  // Invalidate cache — next read will repopulate from DB
  await redisClient.del(`${KEY_PREFIX}${code}`);
}
```

### Write-Back Pattern for Click Counting

```typescript
// src/cache/click-counter.ts — write-back via Redis + async flush to Postgres

import { redisClient } from './redis.client.js';
import { primaryPool } from '../db/pool.js';

// WRITE: Increment in Redis (< 0.1ms — synchronous with redirect)
export async function incrementClickCount(urlId: string): Promise<void> {
  // Redis INCR is atomic — safe for concurrent increments from multiple replicas
  const key = `clicks:pending:${urlId}`;
  await redisClient.incr(key);
  // Set a short TTL as safety net (flush every 60s even if worker is down)
  await redisClient.expire(key, 120);
}

// FLUSH: Background worker drains Redis into Postgres every 10 seconds
export async function flushClickCounts(): Promise<void> {
  const keys = await redisClient.keys('clicks:pending:*');
  if (keys.length === 0) return;

  const pipeline = redisClient.pipeline();
  for (const key of keys) {
    pipeline.getdel(key); // Atomic get-and-delete
  }
  const values = await pipeline.exec();

  // Batch update Postgres
  for (let i = 0; i < keys.length; i++) {
    const urlId = keys[i].replace('clicks:pending:', '');
    const count = parseInt(String(values?.[i]?.[1]), 10);
    if (count > 0) {
      await primaryPool.query(
        'UPDATE urls SET click_count = click_count + $1 WHERE id = $2',
        [count, urlId]
      );
    }
  }
}

// Run flushClickCounts() on a setInterval in the worker process
setInterval(flushClickCounts, 10_000); // Every 10 seconds
```

---

## Try It Yourself

**Exercise:** Add a cache warm-up function to pre-populate Redis with the top 100 most-clicked URLs.

```typescript
// cache-warmup.exercise.ts

// When the app starts fresh (or Redis restarts), the first 100 redirects
// will miss the cache and hit Postgres. This is called a "cold start".
//
// Write a warmUpCache() function that:
// 1. Queries Postgres for the top 100 URLs by click_count
// 2. Bulk-loads them into Redis using MSET or pipelining
// 3. Sets TTL of 5 minutes for each
// 4. Logs how many URLs were warmed
// 5. Call this from src/server.ts before the HTTP server starts listening

// TODO: implement warmUpCache()
```

<details>
<summary>Show solution</summary>

```typescript
import { redisClient } from './cache/redis.client.js';
import { primaryPool } from './db/pool.js';
import pino from 'pino';

const log = pino({ name: 'cache-warmup' });

export async function warmUpCache(): Promise<void> {
  const result = await primaryPool.query<{ code: string; target_url: string }>(
    `SELECT code, target_url
     FROM urls
     WHERE deleted_at IS NULL
     ORDER BY click_count DESC
     LIMIT 100`
  );

  if (result.rows.length === 0) {
    log.info('No URLs to warm — cache skipped');
    return;
  }

  // Use pipeline for bulk writes (single round-trip to Redis)
  const pipeline = redisClient.pipeline();
  for (const { code, target_url } of result.rows) {
    pipeline.set(`url:${code}`, target_url, 'EX', 300);
  }
  await pipeline.exec();

  log.info({ count: result.rows.length }, 'Cache warm-up complete');
}
```

</details>

---

## Capstone Connection

ScaleForge combines all three strategies: cache-aside for redirects, write-back for click counts, and write-around for user-managed URL metadata. Cache-aside keeps the implementation simple — if Redis is unavailable, the app falls back to Postgres transparently. Write-back for click counts enables the sub-millisecond redirect path even at 10k req/s by ensuring no write blocks the redirect response. In Module 05.3, you'll add the missing piece: invalidation when a URL is updated or deleted.
