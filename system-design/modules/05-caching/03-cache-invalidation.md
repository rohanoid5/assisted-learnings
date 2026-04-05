# 5.3 — Cache Invalidation

## Concept

"There are only two hard problems in computer science: cache invalidation and naming things." Cache invalidation is hard because the cache and database can diverge. When data changes, every cached copy of that data must be updated or deleted before it causes a visible inconsistency. The strategies differ in how aggressively they maintain freshness vs. how much complexity they add.

---

## Deep Dive

### Why Invalidation is Hard

```
System state with two cached replicas:

  t=0:   User updates short URL "abc" → redirects from A → B
  t=0:   Postgres updated successfully: target_url = B
  t=0:   Cache key url:abc = A (not yet invalidated)
  
  t=1ms: User clicks the short URL on their phone
         Phone hits Replica 1 → reads url:abc from Redis → returns A  ✗ STALE
  t=3ms: User clicks on laptop  
         Laptop hits Replica 2 → reads url:abc from Redis → returns A  ✗ STALE
         
  Until Redis TTL expires (5 min) → all users redirected to wrong URL
  
  With proper invalidation:
    t=0: Postgres updated
    t=0: DEL url:abc in Redis
    t=0: Next read misses cache, gets B from Postgres, caches B
         → All users see B immediately ✓
```

### Invalidation Strategies Compared

```
  Strategy          Description                  Consistency  Complexity
  ────────────────  ───────────────────────────  ───────────  ──────────
  TTL-only          Keys expire automatically    Eventual     Simple
                    Never explicitly invalidated  (lag = TTL)
  
  Delete on write   DELETE key after DB write    Strong        Medium
                    (cache-aside + invalidation)
  
  Update on write   SET key to new value         Strong        Medium
  (write-through)   synchronously with DB write
  
  Event-driven      DB emits event on change     Strong        High
  (CDC)             Consumer deletes/updates key  (near-real-time)
                    Works across services
                    
  Version tagging   Add version to cache key     Strong        High
                    e.g., url:abc:v3
                    Old version keys auto-expire
```

### Delete vs Update on Cache Write

```
  DELETE key (cache-aside with invalidation):
    - Simpler implementation
    - Next read auto-populates from DB
    - Slightly higher DB load (one extra read after each write)
    - No risk of populating wrong value

  SET key to new value (write-through):
    - Zero lag: cache already has new value
    - One fewer read trip to DB
    - Risk: if app code calculates the cached value differently
            than a fresh DB read, cache and DB can diverge
    - Double-write: both DB write and Redis SET must succeed
      (if Redis SET fails after DB write → stale cache until TTL)
    
  For most cases: DELETE on write is safer and simpler.
  Use write-through only when the cache key is inexpensive to compute
  and you need to eliminate the cold-start read trip.
```

### The "Thundering Herd" from Invalidation

```
  After invalidating a hot cache key (url:abc with 100k users/min):
  
  t=0: DEL url:abc
  t=1ms: 1000 concurrent requests arrive, all get cache MISS
  t=1ms: 1000 concurrent DB queries hit Postgres simultaneously
         → Postgres overloaded → slow → more misses → cycle worsens
         
  Solution: Use a mutex lock on cache miss (see 5.4 Cache Stampede)
  Or: Use stale-while-revalidate (serve old value while one goroutine refetches)
```

---

## Code Examples

### Delete on Write (Recommended Pattern)

```typescript
// src/cache/url-cache.ts — complete cache-aside + invalidation implementation

import { redisClient } from './redis.client.js';
import { primaryPool, replicaPool } from '../db/pool.js';

const KEY = (code: string) => `url:${code}`;
const TTL = 300; // 5 minutes

// READ
export async function getTarget(code: string): Promise<string | null> {
  const cached = await redisClient.get(KEY(code));
  
  if (cached !== null) {
    return cached === '\x00' ? null : cached; // '\x00' = null sentinel
  }

  const result = await replicaPool.query<{ target_url: string }>(
    'SELECT target_url FROM urls WHERE code = $1 AND deleted_at IS NULL',
    [code]
  );

  const target = result.rows[0]?.target_url ?? null;

  // Cache both hits and misses (negative caching prevents DB hammering for 404s)
  await redisClient.set(KEY(code), target ?? '\x00', 'EX', target ? TTL : 30);

  return target;
}

// UPDATE — write to DB, then invalidate cache
export async function updateTarget(code: string, newTarget: string): Promise<void> {
  await primaryPool.query(
    'UPDATE urls SET target_url = $1, updated_at = NOW() WHERE code = $2',
    [newTarget, code]
  );
  
  // Invalidate: delete stale version from cache
  // Next read will repopulate from DB
  await redisClient.del(KEY(code));
}

// DELETE (soft delete) — mark deleted in DB, remove from cache
export async function softDeleteUrl(code: string): Promise<void> {
  await primaryPool.query(
    'UPDATE urls SET deleted_at = NOW() WHERE code = $1',
    [code]
  );
  
  // Remove from cache — subsequent requests will get 404 (via '\x00' sentinel)
  await redisClient.del(KEY(code));
}
```

### Event-Driven Cache Invalidation (Advanced)

```typescript
// src/cache/invalidation-listener.ts
// Uses Postgres LISTEN/NOTIFY for cross-service invalidation.
// When any service updates a URL (via Postgres), all app instances
// get notified via the Postgres notification channel.

import pg from 'pg';
import { redisClient } from './redis.client.js';
import pino from 'pino';

const log = pino({ name: 'cache-invalidation' });

// Maintain a dedicated, persistent connection for LISTEN
// (PgBouncer transaction mode doesn't support LISTEN — use direct Postgres connection)
const notifyClient = new pg.Client({
  connectionString: process.env.DATABASE_PRIMARY_URL,
});

export async function startCacheInvalidationListener(): Promise<void> {
  await notifyClient.connect();

  // Listen for URL update notifications
  await notifyClient.query('LISTEN url_updated');

  notifyClient.on('notification', async (msg) => {
    if (msg.channel !== 'url_updated') return;
    
    const code = msg.payload;
    if (!code) return;
    
    log.debug({ code }, 'Received invalidation notification for URL');
    await redisClient.del(`url:${code}`);
  });

  notifyClient.on('error', (err) => {
    log.error(err, 'Postgres notification listener error — reconnecting');
    setTimeout(startCacheInvalidationListener, 5000);
  });

  log.info('Cache invalidation listener started');
}

// In Postgres, create a trigger that fires NOTIFY on UPDATE:
// CREATE OR REPLACE FUNCTION notify_url_change() RETURNS TRIGGER AS $$
// BEGIN
//   PERFORM pg_notify('url_updated', NEW.code);
//   RETURN NEW;
// END;
// $$ LANGUAGE plpgsql;
//
// CREATE TRIGGER url_change_trigger
// AFTER UPDATE ON urls
// FOR EACH ROW EXECUTE FUNCTION notify_url_change();
```

---

## Try It Yourself

**Exercise:** Test your invalidation logic end-to-end.

```typescript
// cache-invalidation.exercise.ts

// Scenario: User updates a short URL's target. All cached versions must
// reflect the new target immediately after the PATCH request completes.

// TODO:
// 1. Create a test URL via POST /api/v1/urls (code: "testinv")
// 2. Make a redirect request: GET /testinv
//    → Verify Redis now has key url:testinv
//    → Record: redis.get('url:testinv') === original target
//
// 3. Update the URL via PATCH /api/v1/urls/testinv
//    Body: { targetUrl: "https://new-destination.com" }
//    → After PATCH, verify Redis key is GONE: redis.get('url:testinv') === null
//
// 4. Make another redirect: GET /testinv
//    → Verify the response is now 302 to the NEW target
//    → Verify Redis is repopulated with the new value
//
// Use node:assert to automate these checks.
```

<details>
<summary>Show test script</summary>

```typescript
import assert from 'node:assert/strict';
import { redisClient } from './cache/redis.client.js';

const BASE = 'http://localhost:3001';

// 1. Create URL
const create = await fetch(`${BASE}/api/v1/urls`, {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ url: 'https://original.com', code: 'testinv' }),
});
assert.equal(create.status, 201);

// 2. Trigger cache population
const redirect = await fetch(`${BASE}/testinv`, { redirect: 'manual' });
assert.equal(redirect.status, 302);
const cached = await redisClient.get('url:testinv');
assert.equal(cached, 'https://original.com', 'Cache should be populated after redirect');

// 3. Update URL
const update = await fetch(`${BASE}/api/v1/urls/testinv`, {
  method: 'PATCH',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ url: 'https://new-destination.com' }),
});
assert.equal(update.status, 200);
const afterUpdate = await redisClient.get('url:testinv');
assert.equal(afterUpdate, null, 'Cache should be invalidated after update');

// 4. Verify new redirect
const newRedirect = await fetch(`${BASE}/testinv`, { redirect: 'manual' });
assert.equal(newRedirect.headers.get('location'), 'https://new-destination.com');
const repopulated = await redisClient.get('url:testinv');
assert.equal(repopulated, 'https://new-destination.com', 'Cache should have new value');

console.log('Cache invalidation test passed ✓');
```

</details>

---

## Capstone Connection

The `DEL` after DB write in `updateTarget()` is the atomic cache invalidation. It ensures the stale value is gone before the next reader can pick it up — because the next read will query Postgres (now updated) and repopulate Redis with the fresh value. The edge case to handle: what if the DB write succeeds but the Redis DEL fails? In that case, the stale value persists until its 5-minute TTL expires. For ScaleForge's SLA ("updated destination reachable within 5 minutes"), this is acceptable. For stricter SLAs, use the Postgres LISTEN/NOTIFY pattern shown above.
