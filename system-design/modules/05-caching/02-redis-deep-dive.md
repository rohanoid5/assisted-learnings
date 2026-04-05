# 5.2 — Redis Deep Dive

## Concept

Redis is a single-threaded, in-memory data structure server. Its single-threaded nature means all commands are atomic — no locks needed, no races between commands. Its rich data structures (strings, hashes, lists, sorted sets, streams) match common system design patterns directly. Understanding which structure to use is the difference between elegant Redis usage and awkward workarounds.

---

## Deep Dive

### Redis Data Structures and When to Use Them

```
STRING               HASH                  LIST
──────────────       ─────────────────     ─────────────────
key → value          key → {field:value}   key → [v1, v2, ...]
GET/SET/INCR         HGET/HSET/HGETALL     LPUSH/RPOP/LRANGE

Best for:            Best for:             Best for:
- URL targets        - Session objects     - Activity feeds
- Rate limit count   - User profile        - Job queues (BRPOP)
- Feature flags      - Product details     - Recent items (LTRIM)
- Distributed lock


SET                  SORTED SET            STREAM
──────────────       ─────────────────     ────────────────────
key → {v1,v2,...}    key → {v1:score, ...} key → append-only log
SADD/SISMEMBER       ZADD/ZSCORE/ZRANGE    XADD/XREAD/XACK

Best for:            Best for:             Best for:
- Deduplication      - Leaderboards        - Event sourcing
- Set operations     - Top-N queries       - Activity streams
- Unique visitors    - Time-windowed        - Real-time analytics
  per URL              rate limiting        - Fan-out messaging
```

### Atomic Operations Are the Key Insight

```
Problem: Two app replicas increment a rate limit counter concurrently.

Using GET + SET (WRONG — race condition):
  Replica A:  GET ratelimit:ip → 5
  Replica B:  GET ratelimit:ip → 5
  Replica A:  SET ratelimit:ip 6
  Replica B:  SET ratelimit:ip 6    ← lost the other increment!
  Result: 6 (should be 7)

Using INCR (CORRECT — atomic):
  Replica A:  INCR ratelimit:ip → 6
  Replica B:  INCR ratelimit:ip → 7   ← atomic, no race
  Result: 7 ✓

The single-threaded model guarantees no two commands execute simultaneously.
Redis processes one command to completion before the next begins.

Pipeline vs Transaction:
  Pipeline: sends N commands in one TCP roundtrip (no atomicity guarantee)
  MULTI/EXEC: atomic transaction (all or nothing, no interleaving)
  
  Use pipeline for performance (bulk SET on cache warm-up)
  Use MULTI/EXEC for atomicity (increment-and-check rate limit)
```

### Redis Persistence

```
No persistence (default):  Data lost on restart. Use only for caches
                           where repopulation from DB is acceptable.
                           
RDB (snapshot):            Saves snapshot every N seconds or M changes.
                           Fast recovery (binary format).
                           Data loss = since last snapshot.
                           
AOF (append-only file):    Logs every write command.
                           Can sync on every command (fsync=always) → sub-second data loss
                           Default: sync every second → max 1s data loss
                           Larger files than RDB, slower recovery.
                           
AOF + RDB (recommended):   AOF for durability, RDB for fast startup.

ScaleForge configuration:
  url:{code} cache keys → no persistence needed (repopulate from Postgres on miss)
  clicks:pending:{id} → RDB or AOF if click data loss is unacceptable
  rate limit counters → no persistence (missing counter = allow request, fine)
  session tokens → AOF (session loss on restart is bad UX)
```

---

## Code Examples

### Rate Limiter with Sorted Set (Sliding Window)

```typescript
// src/cache/rate-limiter.ts — precise sliding window rate limiting

import { redisClient } from './redis.client.js';

export async function isRateLimited(
  identifier: string,     // IP or user ID
  windowMs: number,       // Window size in milliseconds
  maxRequests: number     // Max requests allowed in window
): Promise<{ limited: boolean; remaining: number; resetIn: number }> {
  const key      = `ratelimit:${identifier}`;
  const now      = Date.now();
  const windowStart = now - windowMs;

  // Use Lua script for atomicity (single round-trip + atomic execution)
  const script = `
    local key = KEYS[1]
    local now = tonumber(ARGV[1])
    local windowStart = tonumber(ARGV[2])
    local maxRequests = tonumber(ARGV[3])
    local ttl = tonumber(ARGV[4])
    
    -- Remove timestamps outside the window
    redis.call('ZREMRANGEBYSCORE', key, '-inf', windowStart)
    
    -- Count requests in window
    local count = redis.call('ZCARD', key)
    
    if count >= maxRequests then
      return {1, 0}  -- limited: true, remaining: 0
    end
    
    -- Add current request with timestamp as both score and member
    redis.call('ZADD', key, now, now .. '-' .. math.random(1000000))
    redis.call('PEXPIRE', key, ttl)
    
    return {0, maxRequests - count - 1}  -- limited: false, remaining
  `;

  const result = await redisClient.eval(
    script, 1, key, String(now), String(windowStart), String(maxRequests), String(windowMs)
  ) as [number, number];

  return {
    limited: result[0] === 1,
    remaining: result[1],
    resetIn: windowMs,
  };
}
```

### Leaderboard with Sorted Set

```typescript
// src/cache/leaderboard.ts — top URLs by click count, updated in real time

import { redisClient } from './redis.client.js';

const LEADERBOARD_KEY = 'leaderboard:clicks';

// Called from the click worker when flushing counts to Postgres
export async function updateLeaderboard(urlCode: string, clickDelta: number): Promise<void> {
  // ZINCRBY is atomic — safe for concurrent updates
  await redisClient.zincrby(LEADERBOARD_KEY, clickDelta, urlCode);
}

// Get top N URLs by click count
export async function getTopUrls(n: number): Promise<Array<{ code: string; clicks: number }>> {
  // ZREVRANGE with scores: returns [member, score, member, score, ...]
  const result = await redisClient.zrevrange(LEADERBOARD_KEY, 0, n - 1, 'WITHSCORES');

  const topUrls: Array<{ code: string; clicks: number }> = [];
  for (let i = 0; i < result.length; i += 2) {
    topUrls.push({
      code:   result[i],
      clicks: parseInt(result[i + 1], 10),
    });
  }
  return topUrls;
}

// Reset leaderboard (e.g., daily reset)
export async function resetLeaderboard(): Promise<void> {
  await redisClient.del(LEADERBOARD_KEY);
}
```

### Distributed Lock

```typescript
// src/cache/distributed-lock.ts
// When multiple replicas compete for the same exclusive resource (e.g., running
// the click flush job), use a distributed lock to ensure only one wins.

import { redisClient } from './redis.client.js';
import crypto from 'node:crypto';

export class DistributedLock {
  private lockValue = crypto.randomUUID(); // Unique ID for this lock holder

  constructor(
    private readonly key: string,
    private readonly ttlMs: number  // Lock expiry (safety net if holder crashes)
  ) {}

  // Returns true if lock was acquired, false if already held by another process
  async acquire(): Promise<boolean> {
    const result = await redisClient.set(
      this.key,
      this.lockValue,
      'NX',     // Set only if Not eXists
      'PX',     // TTL in milliseconds
      this.ttlMs
    );
    return result === 'OK';
  }

  // Only release if we still own the lock (Lua script for atomicity)
  async release(): Promise<void> {
    const script = `
      if redis.call('GET', KEYS[1]) == ARGV[1] then
        return redis.call('DEL', KEYS[1])
      else
        return 0
      end
    `;
    await redisClient.eval(script, 1, this.key, this.lockValue);
  }
}

// Usage:
const lock = new DistributedLock('lock:click-flush', 30_000);
if (await lock.acquire()) {
  try {
    await flushClickCounts();
  } finally {
    await lock.release();
  }
} else {
  // Another instance is running the flush — skip this run
}
```

---

## Try It Yourself

**Exercise:** Implement a unique visitor counter per URL using Redis HyperLogLog.

```typescript
// unique-visitors.exercise.ts

// Redis HyperLogLog is a probabilistic data structure that estimates unique counts
// with ~0.81% error using only 12KB of memory, regardless of cardinality.
// Compared to a SET (which stores every element), HyperLogLog uses ~1000× less memory.

// Redis commands:
//   PFADD key element    → add element to HyperLogLog (O(1))
//   PFCOUNT key          → get estimated unique count (O(1))
//   PFMERGE dest src...  → merge multiple HyperLogLogs (for aggregate stats)

// TODO:
// 1. Write trackUniqueVisitor(urlCode: string, visitorId: string): Promise<void>
//    Key pattern: hll:unique:{code}
//    visitorId can be a hashed IP address or session ID

// 2. Write getUniqueVisitorCount(urlCode: string): Promise<number>
//    Returns the estimated unique visitor count

// 3. Bonus: getUniqueVisitorCountAllUrls(): Promise<number>
//    Use PFMERGE to combine all HyperLogLogs and return global unique count
//    Key pattern: merge to hll:unique:all (overwrite on each call)
```

<details>
<summary>Show solution</summary>

```typescript
import { redisClient } from './cache/redis.client.js';

const HLL_PREFIX = 'hll:unique:';

export async function trackUniqueVisitor(
  urlCode: string,
  visitorId: string
): Promise<void> {
  // PFADD returns 1 if the HLL was updated (new unique visitor), 0 if already seen
  await redisClient.pfadd(`${HLL_PREFIX}${urlCode}`, visitorId);
}

export async function getUniqueVisitorCount(urlCode: string): Promise<number> {
  return redisClient.pfcount(`${HLL_PREFIX}${urlCode}`);
}

export async function getUniqueVisitorCountAllUrls(): Promise<number> {
  const keys = await redisClient.keys(`${HLL_PREFIX}*`);
  if (keys.length === 0) return 0;

  const mergeKey = 'hll:unique:all';
  await redisClient.pfmerge(mergeKey, ...keys);
  await redisClient.expire(mergeKey, 60); // Clear after a minute (temp merge result)
  return redisClient.pfcount(mergeKey);
}
```

</details>

---

## Capstone Connection

The three Redis patterns here are all active in ScaleForge:
- `STRING` + INCR → click counter write-back
- `SORTED SET` → real-time click leaderboard (used on the dashboard to show "your top 5 URLs this week")
- `DISTRIBUTED LOCK` → ensures only one instance runs the click flush job every 10 seconds

The HyperLogLog from this exercise adds unique visitor tracking at ~12KB per URL regardless of how many visitors — a 99.9% memory reduction vs storing all visitor IDs in a SET.
