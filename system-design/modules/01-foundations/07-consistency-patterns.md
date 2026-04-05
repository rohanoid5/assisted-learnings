# 1.7 — Consistency Patterns

## Concept

Consistency patterns define how and when writes become visible across nodes in a distributed system. The spectrum runs from **weak** (no guarantee) to **strong** (every read sees the latest write), with **eventual consistency** as the pragmatic middle ground used by most large-scale systems. Choosing the wrong pattern causes either data loss/corruption or unnecessary performance penalties.

---

## Deep Dive

### Weak Consistency

After a write, reads **may or may not** see the updated value. There is no guarantee. The system makes no attempt to propagate writes to readers quickly.

```
Use cases:
  • Real-time games (it's OK if your position lags 100ms)
  • Live video/audio streaming (a few lost frames is fine)
  • Multiplayer state sync (approximate is acceptable)

Example in ScaleForge context:
  • An in-memory server-local counter for approximate rate limiting
    (each replica tracks its own, they never sync — approximate is enough)
```

### Eventual Consistency

After a write, **all replicas will converge to the same state — eventually**. If no new updates are made, all reads will eventually return the last write. The gap between write and convergence is the **replication lag** (typically milliseconds to seconds).

```
Timeline:
  T=0:   Write "abc123 → https://new.com" to primary DB
  T=1ms: Read from replica A → "https://old.com"  (replication lag)
  T=5ms: Read from replica B → "https://new.com"  (already replicated)
  T=10ms: Read from replica A → "https://new.com" (now replicated)
  T=∞:   All replicas agree  → eventual consistency achieved

Guarantees: convergence will happen.
Does NOT guarantee: when, or that intermediate reads are correct.
```

**Common eventual consistency patterns:**

| Pattern | Mechanism | Used In |
|---------|-----------|---------|
| Leader-follower replication | Writes to primary, async replicate | PostgreSQL streaming, MySQL replication |
| Last-Write-Wins (LWW) | Whichever write has latest timestamp wins | Cassandra, DynamoDB |
| CRDT (Conflict-free Replicated Data Types) | Mathematical structures that merge without conflicts | Redis, shopping carts |
| Gossip protocol | Nodes periodically exchange state | Cassandra, Consul |

### Strong Consistency (Linearizability)

Every read returns the result of the most recent completed write. Writes are globally ordered. All nodes appear to agree at all times.

```
Timeline:
  T=0:   Write completes on primary
  T=0:   ANY subsequent read from ANY node returns new value  
         (system waits for all replicas to confirm before acknowledging write)

Cost: Write latency = max(replica latency)
      In multi-region: latency = ~150ms cross-region round trip
      Solution: accept strong consistency only within a region
```

**Implementations:**
- PostgreSQL synchronous replication (`synchronous_commit = on`)
- Google Spanner (TrueTime API for global strong consistency)
- etcd / ZooKeeper (via Raft consensus)
- Redis with `WAIT numreplicas timeout`

### Where Each Pattern Fits in ScaleForge

```
Strong Consistency:
  ├── URL code uniqueness (PostgreSQL UNIQUE constraint)
  ├── User authentication (JWT secret must not be stale)
  └── Rate limit enforcement via Redis INCR (atomic)

Eventual Consistency:
  ├── Click analytics (BullMQ queue → async writes)
  ├── Cache population (Redis catches up to DB on next miss)
  └── Read replicas for analytics queries

Weak Consistency:
  └── Per-replica approximate counters (if implemented for perf)
```

---

## Code Examples

### Implementing Eventual Consistency — The Click Tracking Pipeline

```typescript
// src/workers/click-tracker.worker.ts
// 
// The redirect endpoint writes clicks to a queue (fire-and-forget).
// This worker processes them asynchronously — eventual consistency in action.

import { Worker, Queue } from 'bullmq';
import { Redis } from 'ioredis';
import { prisma } from '../db/client.js';

const redisConnection = new Redis({ host: 'localhost', port: 6379, maxRetriesPerRequest: null });

export const ClickQueue = new Queue('click-tracking', { connection: redisConnection });

// Worker runs on a separate process / replica
export const clickWorker = new Worker(
  'click-tracking',
  async (job) => {
    const { code, ip, userAgent, timestamp } = job.data as {
      code: string;
      ip: string;
      userAgent: string;
      timestamp: number;
    };

    const shortUrl = await prisma.shortURL.findUnique({ where: { code } });
    if (!shortUrl) return; // URL was deleted between redirect and this processing

    // Write click with a slight delay from actual redirect time
    // This is the "eventual" part — click recorded ~100ms-2s after redirect
    await prisma.click.create({
      data: {
        shortUrlId: shortUrl.id,
        ipAddress: ip,
        userAgent,
        country: await resolveCountry(ip),  // async geolocation
        timestamp: new Date(timestamp),
      },
    });

    // Update the denormalised click count on the URL row
    // (also eventual — will converge after worker processes all queued clicks)
    await prisma.shortURL.update({
      where: { code },
      data: { clickCount: { increment: 1 } },
    });
  },
  { connection: redisConnection, concurrency: 10 }
);

async function resolveCountry(ip: string): Promise<string> {
  // Stub — real implementation uses maxmind or ip-api.com
  return 'US';
}
```

### Implementing Strong Consistency — URL Code Uniqueness

```typescript
// src/services/url.service.ts
// 
// URL code generation must be strongly consistent — no two URLs can get
// the same code. PostgreSQL UNIQUE constraint + retry handles this.

import { prisma } from '../db/client.js';
import { nanoid } from 'nanoid';
import { Prisma } from '@prisma/client';

const MAX_RETRIES = 5;

export async function createShortUrl(
  longUrl: string,
  userId: string
): Promise<{ code: string }> {
  let attempt = 0;

  while (attempt < MAX_RETRIES) {
    const code = nanoid(6);  // Random 6-char code

    try {
      // PostgreSQL enforces UNIQUE(code) at the DB level — strong consistency.
      // If two requests race with the same code, one will succeed, one throws.
      await prisma.shortURL.create({
        data: { code, longUrl, userId },
      });

      return { code };
    } catch (error) {
      // P2002: Prisma's code for unique constraint violation
      if (error instanceof Prisma.PrismaClientKnownRequestError && error.code === 'P2002') {
        attempt++;
        // Try a different code (collision rate with nanoid(6) over 64 chars ≈ 0.001%)
        continue;
      }
      throw error;
    }
  }

  throw new Error(`Failed to generate unique code after ${MAX_RETRIES} attempts`);
}
```

### Implementing Read-Your-Writes — Session Consistency

```typescript
// src/middleware/session-cache.ts
//
// Ensures that after creating a URL, the creator immediately sees it —
// even before replication has propagated to read replicas.

const SESSION_WRITES_TTL = 60;  // Track recently-written codes for 60 seconds

export class SessionConsistencyCache {
  constructor(private readonly redis: Redis) {}

  // Called after a successful URL creation
  async markWritten(sessionId: string, code: string): Promise<void> {
    const key = `session:${sessionId}:writes`;
    await this.redis.sadd(key, code);
    await this.redis.expire(key, SESSION_WRITES_TTL);
  }

  // Called before deciding which replica to read from
  async wasWrittenInSession(sessionId: string, code: string): Promise<boolean> {
    const key = `session:${sessionId}:writes`;
    return (await this.redis.sismember(key, code)) === 1;
  }
}

// Usage in URL lookup:
// if (await sessionCache.wasWrittenInSession(sessionId, code)) {
//   // Read from primary (strong consistency) — this is the creator
//   return readFromPrimary(code);
// } else {
//   // Read from replica (eventual consistency) — general visitor
//   return readFromReplica(code);
// }
```

---

## Try It Yourself

**Exercise:** Add consistency guarantees to the rate limiter from Topic 1.4.

The in-memory rate limiter in Topic 1.4 has **weak consistency** — each ScaleForge replica tracks its own window independently. A `FREE` tier user can send 5 req/s per replica, meaning if there are 3 replicas, they effectively get 15 req/s.

Upgrade it to **strong consistency** using Redis atomic operations:

```typescript
// src/resilience/distributed-rate-limiter.ts

import { Redis } from 'ioredis';

export class DistributedRateLimiter {
  constructor(
    private readonly redis: Redis,
    private readonly maxRequests: number,
    private readonly windowMs: number
  ) {}

  async isAllowed(key: string): Promise<boolean> {
    const now = Date.now();
    const windowKey = `ratelimit:${key}:${Math.floor(now / this.windowMs)}`;

    // TODO: use Redis INCR + EXPIRE atomically (MULTI/EXEC or Lua script)
    // to count requests within the current time window.
    // Return true if count <= maxRequests, false otherwise.
    //
    // Hint: INCR is atomic — safe across multiple replicas
    throw new Error('Not implemented');
  }
}
```

<details>
<summary>Show solution</summary>

```typescript
async isAllowed(key: string): Promise<boolean> {
  const now = Date.now();
  const windowKey = `ratelimit:${key}:${Math.floor(now / this.windowMs)}`;

  // INCR is atomic — all replicas hitting the same Redis key are consistent
  const count = await this.redis.incr(windowKey);

  if (count === 1) {
    // First request in this window — set expiry so key auto-cleans
    await this.redis.pexpire(windowKey, this.windowMs * 2);
  }

  return count <= this.maxRequests;
}
```

**Tradeoff**: This is now **strongly consistent** across all app replicas (Redis `INCR` is serialized). The cost is one Redis round-trip per request (~0.3ms added to redirect latency). For ScaleForge's 20ms p99 target, this is acceptable.

**Further improvement**: Use a Lua script to combine `INCR` + `PEXPIRE` in a single atomic operation, avoiding the race between them.

</details>

---

## Capstone Connection

Module 1 ends here. The consistency vocabulary from this topic — weak, eventual, strong, read-your-writes — will be the language used throughout the rest of the tutorial when justifying every architecture decision. In Module 09, the CQRS pattern for ScaleForge analytics is specifically designed around eventual consistency: writes go to one model, reads to another, and they converge asynchronously. Understanding the spectrum from this topic is what makes that design legible.
