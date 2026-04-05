# 1.5 — Availability vs. Consistency

## Concept

Availability means the system responds to every request; consistency means every response reflects the most recent write. In a distributed system, network partitions are inevitable — and during one, you must choose: stop serving requests to guarantee consistency, or keep serving with potentially stale data to guarantee availability. This tradeoff is the most consequential architectural decision in distributed systems design.

---

## Deep Dive

### Measuring Availability: The Nines

Availability is expressed as a percentage of time the system is operational:

| Availability | Downtime / year | Downtime / month | Downtime / day |
|-------------|----------------|-----------------|---------------|
| 99% ("two nines") | 3.65 days | 7.2 hours | 14.4 min |
| 99.9% ("three nines") | 8.76 hours | 43.8 min | 1.44 min |
| 99.99% ("four nines") | 52.6 min | 4.38 min | 8.64 sec |
| 99.999% ("five nines") | 5.26 min | 26.3 sec | 0.86 sec |

Five nines requires near-zero planned downtime — no deployment restarts allowed. This typically demands blue-green deployments, hot module reloading, or active-active multi-region.

### Availability in Parallel vs. Sequence

How you chain components changes your overall availability:

**In sequence** (each component must work for the system to work):

$$A_{total} = A_1 \times A_2 \times A_3$$

Example: API (99.99%) → DB (99.99%) → Cache (99.99%) = 99.97% — worse than every individual component!

**In parallel** (system works if at least one component works):

$$A_{total} = 1 - (1 - A_1) \times (1 - A_2)$$

Example: Two DB replicas each at 99.9%: $1 - (0.001)^2 = 99.9999\%$

**Implication**: Every new required dependency in your critical path reduces availability. Use async processing, caches, circuit breakers, and fallbacks to decouple components from the critical path.

### The Consistency Spectrum

```
Weak ◀─────────────────────────────────────────▶ Strong
     
Weak         Eventual         Read-your-writes      Strong
Consistency  Consistency      Consistency           Consistency
     │              │              │                    │
     │         Reads may      After a write,      Every read
     │         return stale   you see your        returns the
     │         data until     write (but not      latest write
     │         convergence    others') ←           globally
     │                        Session guarantee
     │
     └─ DNS propagation       └─ Social feeds     └─ Bank balance
     └─ CDN cache             └─ Shopping cart    └─ Inventory count
                                                  └─ Distributed locks
```

### When to Choose Availability Over Consistency

For ScaleForge:
- **Redirect** → choose availability. If cache is slightly stale (shows deleted URL for 5 min), that's acceptable. Not redirecting at all is much worse.
- **Click analytics** → eventual consistency is fine. Reports being 5 seconds behind doesn't harm users.
- **URL creation** → strong consistency required. Two users can't get the same short code.
- **User account balance** (hypothetical Pro tier) → strong consistency. Users must see accurate quota.

---

## Code Examples

### Calculating Your System's Effective Availability

```typescript
// docs/availability-calculator.ts

function availabilityInSequence(...availabilities: number[]): number {
  return availabilities.reduce((acc, a) => acc * a, 1);
}

function availabilityInParallel(...availabilities: number[]): number {
  const failureProbability = availabilities.reduce((acc, a) => acc * (1 - a), 1);
  return 1 - failureProbability;
}

function toNines(availability: number): string {
  const downtime = (1 - availability) * 365 * 24 * 60; // minutes per year
  if (downtime < 1) return `${(downtime * 60).toFixed(1)} sec/year`;
  if (downtime < 60) return `${downtime.toFixed(1)} min/year`;
  return `${(downtime / 60).toFixed(1)} hrs/year`;
}

// ScaleForge critical path: Client → Nginx → App → Redis
const singlePath = availabilityInSequence(0.9999, 0.9999, 0.9999, 0.9999);
console.log(`Single path: ${(singlePath * 100).toFixed(4)}% (${toNines(singlePath)})`);
// Single path: 99.9600% (3.5 hrs/year) — FOUR nines degrades to THREE

// With 3 app replicas (parallel):
const appAvailability = availabilityInParallel(0.9999, 0.9999, 0.9999);
const withReplicas = availabilityInSequence(0.9999, appAvailability, 0.9999);
console.log(`With 3 replicas: ${(withReplicas * 100).toFixed(6)}% (${toNines(withReplicas)})`);
// With 3 replicas: 99.9999% (0.5 min/year) — approaches five nines

// Redis with replica:
const redisAvailability = availabilityInParallel(0.9999, 0.9999);
const withRedisReplica = availabilityInSequence(0.9999, appAvailability, redisAvailability);
console.log(`With Redis replica: ${(withRedisReplica * 100).toFixed(6)}%`);
```

### Implementing Read-Your-Writes Consistency

```typescript
// src/services/url.service.ts — ensuring the creator sees their own URL immediately

import { redisClient } from '../cache/redis.client.js';
import { prisma } from '../db/client.js';

export const UrlService = {
  async create(longUrl: string, userId: string): Promise<{ code: string }> {
    // Write to PostgreSQL (authoritative store — strong consistency)
    const shortUrl = await prisma.shortURL.create({
      data: { code: generateCode(), longUrl, userId },
    });

    // IMMEDIATELY write to Redis so the creator's next request sees it
    // This is "read-your-writes" consistency within the session
    await redisClient.set(
      `url:${shortUrl.code}`,
      longUrl,
      'EX', 300  // 5-minute TTL
    );

    return { code: shortUrl.code };
  },

  async lookup(code: string): Promise<string | null> {
    // 1. Check Redis (fast, potentially slightly stale for OTHER users)
    const cached = await redisClient.get(`url:${code}`);
    if (cached) return cached;

    // 2. Fallback to PostgreSQL (authoritative, always consistent)
    const shortUrl = await prisma.shortURL.findUnique({ where: { code } });
    if (!shortUrl) return null;

    // 3. Repopulate cache for future requests
    await redisClient.set(`url:${code}`, shortUrl.longUrl, 'EX', 300);
    return shortUrl.longUrl;
  },

  async delete(code: string): Promise<void> {
    await prisma.shortURL.delete({ where: { code } });

    // Invalidate cache — but there's a window where other servers
    // still have it cached. This is "eventual consistency" by design.
    // If strict consistency is needed, use a very short TTL or pub/sub invalidation.
    await redisClient.del(`url:${code}`);
  },
};

function generateCode(): string {
  const chars = 'abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789';
  return Array.from({ length: 6 }, () => chars[Math.floor(Math.random() * chars.length)]).join('');
}
```

---

## Try It Yourself

**Exercise:** Model ScaleForge's availability SLA.

```typescript
// experiments/availability.ts

// Given:
// - Nginx LB: 99.99%
// - App servers: 3 replicas, each at 99.9%
// - Redis: primary + replica, each at 99.99%
// - PostgreSQL: primary + standby, each at 99.95%

// TODO: Calculate the effective system availability using
// availabilityInSequence and availabilityInParallel from above.

// Questions to answer:
// 1. What is the effective availability of the app layer (3 replicas in parallel)?
// 2. What is the effective availability of Redis (primary + replica)?
// 3. What is the end-to-end availability of the critical redirect path?
// 4. How many minutes of downtime per year does that represent?
// 5. What single component limits availability the most?
```

<details>
<summary>Show solution</summary>

```typescript
const app = availabilityInParallel(0.999, 0.999, 0.999);     // 99.9999%
const redis = availabilityInParallel(0.9999, 0.9999);         // 99.999999%
const postgres = availabilityInParallel(0.9995, 0.9995);      // 99.999975%

const total = availabilityInSequence(0.9999, app, redis, postgres);
console.log(`Total: ${(total * 100).toFixed(6)}% (${toNines(total)})`);
// Total: 99.9989% (~5.7 min/year)

// Bottleneck: Nginx at 99.99% is now the limiting factor.
// To push to 99.999%, add a second Nginx instance or use a managed LB (AWS ALB at 99.999%).
```

</details>

---

## Capstone Connection

ScaleForge targets 99.99% availability. This topic explains why Module 03's design uses 3 app replicas (parallel availability), why Module 04 adds a PostgreSQL read replica, and why Module 10's circuit breaker pattern prevents a failing downstream dependency from making the entire redirect path unavailable. The formula above is why your SLA must be negotiated before choosing the architecture — the number of replicas is literally a math calculation from the availability target.
