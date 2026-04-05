# 5.5 — Multi-Tier Caching

## Concept

Multi-tier caching stacks multiple cache layers — each faster and closer to the user than the last. The tier hierarchy for ScaleForge is: in-process memory (Node.js Map) → shared distributed cache (Redis) → persistent store (Postgres). Each tier has its own access latency, capacity, and consistency tradeoffs. The art is deciding which data belongs in which tier and how invalidation flows between them.

---

## Deep Dive

### Three-Tier Cache Architecture for ScaleForge

```
  User request ──► Nginx
                     │
                     ▼
             [Tier 0: Nginx Proxy Cache]    ~0.1ms
             Fastest possible: served from
             OS page cache before Node.js
             even receives the connection.
             ⚠ DISABLED for /redirect path
               (see analytics conflict below)
                     │
                     ▼
             [Tier 1: In-Process LRU]       ~0.01ms
             Node.js Worker Map<string, {value, expiresAt}>
             Capacity: 1000 entries per worker
             Survives Redis failure
             NOT shared between workers
                     │ MISS
                     ▼
             [Tier 2: Redis Shared Cache]   ~0.5–2ms
             All workers / all replicas share one view
             Survives worker restarts
             Eviction policy: allkeys-lru
             Capacity: ~500k URL entries per GB
                     │ MISS
                     ▼
             [Tier 3: Postgres Primary]     ~5–20ms
             Source of truth
             Only reached on cold start or
             after explicit cache invalidation
                     │
                     ▼
             Response: 302 Redirect
```

### The Analytics Contradiction

```
  If Nginx caches /abc redirect:
    - Nginx serves 302 directly from OS page cache
    - Node.js process is never invoked
    - Click event is never recorded in Postgres
    - Analytics are broken for all cached requests
    
  ScaleForge decision:
    /abc redirect path:   Nginx cache DISABLED
                          (analytics > performance)
    /api/v1/* paths:      Nginx cache ENABLED
                          (idempotent, no click tracking)
    
  nginx.conf:
    location ~ ^/[a-zA-Z0-9]{4,10}$ {
      proxy_cache off;          ← disables Nginx cache for redirects
      proxy_pass http://app;
    }
    location /api/v1 {
      proxy_cache api_cache;    ← caches API responses
      proxy_cache_valid 200 60s;
    }
```

### Cache Key Naming Convention

```
  Consistent key naming prevents collisions across tiers:
  
  Tier         Pattern                  Example
  ──────────── ──────────────────────── ──────────────────────────
  In-process   <code>                   "abc123"  (Map key)
  Redis        url:<code>               "url:abc123"
  Redis (stale) stale-url:<code>        "stale-url:abc123"
  Redis (null)  url:<code> = '\x00'     MISS sentinel
  Redis (lock)  lock:url:<code>         acquired by one worker
  
  Rule: Never use raw user input as a cache key. Always prefix with
  a key type. Prevents URL code "stats" from colliding with an
  internal "stats" key.
```

### Cache Hit Rate Math

```
  Tiered cache hit rates compound:
  
  Tier 1 (in-process) hit rate         = 40%  (top 1000 URLs)
  Tier 2 (Redis) hit rate of misses     = 55%  (99% of all URLs)
  Tier 3 (Postgres) hit rate of misses  = 100% (always returns or 404)
  
  Overall DB hit rate:
    (1 - 0.40) × (1 - 0.55) = 0.60 × 0.45 = 27%
    
  Meaning: with both tiers healthy, only 27% of requests ever
  reach Postgres. 73% return from cache with <2ms latency.
  
  With Redis down (Tier 1 only):
    (1 - 0.40) × 1 = 60% of requests hit Postgres
    → Postgres connection pool would saturate under high load
    → Redis failure must trigger alert + rate limiting
```

---

## Code Examples

### In-Process LRU Cache Layer (Tier 1)

```typescript
// src/cache/in-process-cache.ts — simple bounded LRU at the worker level
// Sits in front of Redis to avoid network round-trip for ultra-hot URLs

interface LRUEntry {
  value: string | null;
  expiresAt: number; // Date.now() ms
}

export class InProcessLRU {
  private map = new Map<string, LRUEntry>();
  private readonly maxSize: number;

  constructor(maxSize = 1000) {
    this.maxSize = maxSize;
  }

  get(key: string): string | null | undefined {
    const entry = this.map.get(key);
    if (!entry) return undefined; // miss

    if (Date.now() > entry.expiresAt) {
      this.map.delete(key);
      return undefined; // expired
    }

    // Move to end (LRU order) — Map preserves insertion order
    this.map.delete(key);
    this.map.set(key, entry);
    return entry.value;
  }

  set(key: string, value: string | null, ttlSeconds: number): void {
    if (this.map.size >= this.maxSize) {
      // Evict oldest (first entry in Map)
      const oldestKey = this.map.keys().next().value!;
      this.map.delete(oldestKey);
    }
    this.map.delete(key); // remove if exists to reset insertion order
    this.map.set(key, { value, expiresAt: Date.now() + ttlSeconds * 1000 });
  }

  del(key: string): void {
    this.map.delete(key);
  }

  get size(): number {
    return this.map.size;
  }
}

// Singleton per worker process — shared across all requests in this worker
export const urlLRU = new InProcessLRU(1000);
```

### Complete Multi-Tier Redirect Handler

```typescript
// src/handlers/redirect.handler.ts
// Full implementation showing all three cache tiers in sequence with timing metrics

import type { Request, Response } from 'express';
import { urlLRU } from '../cache/in-process-cache.js';
import { redisClient } from '../cache/redis.client.js';
import { replicaPool, primaryPool } from '../db/pool.js';
import { clickQueue } from '../queues/click.queue.js';
import pino from 'pino';

const log = pino({ name: 'redirect' });

export async function redirectHandler(req: Request, res: Response): Promise<void> {
  const { code } = req.params;
  const timings: Record<string, number> = {};
  const t0 = Date.now();

  // Tier 1: In-process LRU — sub-millisecond, no network
  const t1Start = Date.now();
  const local = urlLRU.get(code);
  timings.tier1 = Date.now() - t1Start;

  if (local !== undefined) {
    res.setHeader('X-Cache', 'HIT-L1');
    res.setHeader('X-Cache-Timings', JSON.stringify(timings));
    if (local === null) { res.status(404).send('Not found'); return; }
    await enqueueClick(code, req);
    res.redirect(302, local);
    return;
  }

  // Tier 2: Redis shared cache — ~0.5–2ms
  const t2Start = Date.now();
  let redisValue: string | null = null;
  try {
    redisValue = await redisClient.get(`url:${code}`);
  } catch (err) {
    log.error(err, 'Redis unavailable — falling through to DB');
  }
  timings.tier2 = Date.now() - t2Start;

  if (redisValue !== null) {
    const target = redisValue === '\x00' ? null : redisValue;
    // Backfill Tier 1 from Redis hit
    urlLRU.set(code, target, 60); // shorter TTL in-process (60s vs Redis 300s)
    res.setHeader('X-Cache', 'HIT-L2');
    res.setHeader('X-Cache-Timings', JSON.stringify(timings));
    if (target === null) { res.status(404).send('Not found'); return; }
    await enqueueClick(code, req);
    res.redirect(302, target);
    return;
  }

  // Tier 3: Postgres — source of truth
  const t3Start = Date.now();
  const result = await replicaPool.query<{ target_url: string }>(
    'SELECT target_url FROM urls WHERE code = $1 AND deleted_at IS NULL',
    [code]
  );
  timings.tier3 = Date.now() - t3Start;
  timings.total = Date.now() - t0;

  const target = result.rows[0]?.target_url ?? null;
  const toStore = target ?? '\x00';
  const ttl = target ? 300 : 30;

  // Populate both cache tiers
  try {
    await redisClient.set(`url:${code}`, toStore, 'EX', ttl);
  } catch (err) {
    log.warn(err, 'Failed to populate Redis cache');
  }
  urlLRU.set(code, target, Math.min(ttl, 60));

  res.setHeader('X-Cache', 'MISS');
  res.setHeader('X-Cache-Timings', JSON.stringify(timings));

  log.debug({ code, timings }, 'Cache miss — served from DB');

  if (target === null) { res.status(404).send('Not found'); return; }
  await enqueueClick(code, req);
  res.redirect(302, target);
}

async function enqueueClick(code: string, req: Request): Promise<void> {
  try {
    await clickQueue.add('click', {
      code,
      timestamp: Date.now(),
      ip: req.ip,
      userAgent: req.get('user-agent') ?? '',
    });
  } catch (err) {
    // Click tracking is non-critical — don't fail the redirect
    log.warn(err, 'Failed to enqueue click event');
  }
}
```

### Cache Hit Rate Monitoring

```typescript
// src/metrics/cache-metrics.ts — track hit rates across all tiers

import { redisClient } from '../cache/redis.client.js';
import { urlLRU } from '../cache/in-process-cache.js';

interface CacheMetrics {
  redis: {
    hitRate: number;   // 0–1
    hits: number;
    misses: number;
    memoryUsedMb: number;
    evictedKeys: number;
  };
  lru: {
    size: number;
    maxSize: number;
  };
}

export async function getCacheMetrics(): Promise<CacheMetrics> {
  // Redis INFO stats command returns key metrics
  const info = await redisClient.info('stats');
  const statsMap = parseRedisInfo(info);

  const hits = parseInt(statsMap['keyspace_hits'] ?? '0');
  const misses = parseInt(statsMap['keyspace_misses'] ?? '0');
  const evicted = parseInt(statsMap['evicted_keys'] ?? '0');
  const total = hits + misses;

  const memInfo = await redisClient.info('memory');
  const memMap = parseRedisInfo(memInfo);
  const usedBytes = parseInt(memMap['used_memory'] ?? '0');

  return {
    redis: {
      hitRate: total > 0 ? hits / total : 0,
      hits,
      misses,
      memoryUsedMb: Math.round(usedBytes / 1024 / 1024),
      evictedKeys: evicted,
    },
    lru: {
      size: urlLRU.size,
      maxSize: 1000,
    },
  };
}

function parseRedisInfo(info: string): Record<string, string> {
  return Object.fromEntries(
    info
      .split('\r\n')
      .filter((line) => line.includes(':'))
      .map((line) => line.split(':') as [string, string])
  );
}

// Add as an admin endpoint:
// app.get('/admin/cache-metrics', async (req, res) => {
//   res.json(await getCacheMetrics());
// });
```

### Cache Warming on Startup

```typescript
// src/cache/warm-up.ts — prepopulate cache with top N URLs on startup
// Prevents cold-cache latency spike after deployments

import { primaryPool } from '../db/pool.js';
import { redisClient } from './redis.client.js';
import { urlLRU } from './in-process-cache.js';
import pino from 'pino';

const log = pino({ name: 'cache-warm-up' });

export async function warmUpCache(topN = 500): Promise<void> {
  log.info({ topN }, 'Starting cache warm-up...');
  const start = Date.now();

  // Fetch top N most clicked URLs from DB (use click count from Postgres)
  const result = await primaryPool.query<{ code: string; target_url: string }>(
    `SELECT code, target_url
     FROM urls
     WHERE deleted_at IS NULL
     ORDER BY click_count DESC
     LIMIT $1`,
    [topN]
  );

  if (result.rows.length === 0) {
    log.info('No URLs to warm up');
    return;
  }

  // Batch-write to Redis using a pipeline (single RTT for all writes)
  const pipeline = redisClient.pipeline();
  for (const row of result.rows) {
    pipeline.set(`url:${row.code}`, row.target_url, 'EX', 300);
  }
  await pipeline.exec();

  // Also populate in-process LRU with top 100 (LRU capacity = 1000)
  for (const row of result.rows.slice(0, 100)) {
    urlLRU.set(row.code, row.target_url, 60);
  }

  log.info(
    { count: result.rows.length, elapsed: Date.now() - start },
    'Cache warm-up complete'
  );
}

// Call from server startup:
// server.listen(PORT, async () => {
//   await warmUpCache(500);
//   log.info({ port: PORT }, 'Server ready');
// });
```

---

## Try It Yourself

**Exercise:** Instrument and observe the cache warm-up effect on latency.

```typescript
// cache-warmup-bench.exercise.ts

// Scenario: Run a 10-minute load test against the redirect endpoint.
// At t=0, Redis is empty (cold). Watch the X-Cache header and latency change.

// TODO:
// 1. Clear Redis:  redis-cli FLUSHALL
// 2. Start your server WITHOUT calling warmUpCache()
// 3. Run autocannon for 10 seconds, target a URL code you created:
//    npx autocannon -d 10 -c 50 http://localhost:3001/<your-code>
//    - Record: avg latency, p99 latency, X-Cache miss rate
//
// 4. Restart server WITH warmUpCache() enabled
// 5. Run the same autocannon command immediately on startup
//    - Record: avg latency, p99 latency, X-Cache miss rate
//
// Expected cold behavior:    p99 ~ 20ms, ~20% MISS on first run
// Expected warm behavior:    p99 ~ 2ms,  ~0%  MISS (all cache hits)
//
// 6. BONUS: Use `redis-cli monitor` in a separate terminal while running
//    the benchmark to observe actual Redis GET commands
```

<details>
<summary>Show expected results</summary>

```
Cold cache (no warmup):
  First 10s of load test:
    X-Cache: MISS   ~30% of requests (until cache self-warms from traffic)
    avg latency: ~15ms  (DB round-trip for misses)
    p99 latency: ~45ms  (connection pool contention during initial burst)
  
  After self-warming (next 10s):
    X-Cache: HIT    ~95% of requests
    avg latency: ~1.5ms
    p99 latency: ~4ms

Warm cache (with warmUp()):
  From request 1:
    X-Cache: HIT    ~99% of requests
    avg latency: ~1ms
    p99 latency: ~3ms

Lesson: warmUpCache() eliminates the cold-start latency window
entirely — critical after deployments at peak traffic hours.
```

</details>

---

## Capstone Connection

The multi-tier cache stack is the performance core of ScaleForge. The In-Process LRU eliminates Redis round-trips for the top 1000 URLs — a short link shared on Twitter might get 50k hits in 5 minutes, and with 3 app replicas each caching it locally, Redis receives at most 3 GET requests per LRU TTL (60s) instead of 50k. The `X-Cache-Timings` header records tier latencies per request — use it with your load test output to graph cache hit rates and prove the performance improvement.
