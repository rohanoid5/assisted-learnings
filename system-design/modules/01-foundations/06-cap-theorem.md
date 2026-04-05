# 1.6 — CAP Theorem

## Concept

The CAP theorem states that a distributed data store can guarantee at most **two** of three properties simultaneously: **Consistency** (every read returns the latest write), **Availability** (every request receives a response), and **Partition Tolerance** (the system continues operating despite network failures between nodes). Since network partitions are inevitable in any distributed system, the real choice is between CP (sacrifice availability) and AP (sacrifice consistency) when a partition occurs.

---

## Deep Dive

### The Three Properties

```
           Consistency
               /\
              /  \
             /    \
            /  CP  \
           /────────\
          / CA  │ AP \
         /──────┴─────\
        Availability──Partition
                      Tolerance
```

**Consistency (C)**: Every read receives the most recent write (or an error). All nodes see the same data at the same time.

**Availability (A)**: Every request receives a response (not necessarily the latest data). The system never returns an error just because nodes can't agree.

**Partition Tolerance (P)**: The system continues to operate even when network messages between nodes are lost or delayed.

### Why P Is Not Optional

A "partition" is when nodes in your distributed system can't communicate — a network switch fails, a firewall rule misfires, a data center loses connectivity. In any system with more than one machine, partitions happen. Period.

- Cloud providers target < 0.01% network failure rate, but that's still ~52 minutes/year
- A deployment that doesn't handle partitions correctly will fail catastrophically during one
- Therefore: you must tolerate partitions — and **the only real choice is CP vs. AP**

### CP vs. AP in Practice

| System | Type | Behavior During Partition |
|--------|------|--------------------------|
| PostgreSQL | CP | Rejects writes on replica; primary still serves reads/writes |
| Redis (single-leader) | CP | Read replicas may serve stale data OR refuse reads |
| MongoDB (with w:majority) | CP | Returns error if majority write can't be confirmed |
| DynamoDB | AP | Serves reads (possibly stale); writes always succeed |
| Cassandra | AP | Always available; may show different replicas different data |
| CockroachDB | CP | Distributed SQL; sacrifices availability during partition |
| Zookeeper | CP | Used for leader election; unavailable during partition |

### The PACELC Extension

CAP only applies during network partitions. PACELC extends it: **during normal operation**, distributed systems trade off **latency vs. consistency** too.

| | During Partition | During Normal Operation |
|---|---|---|
| Amazon DynamoDB | Availability | Low Latency (accepts eventual consistency) |
| Google Spanner | Consistency | Consistency (accepts higher latency for global linearizability) |
| Cassandra | Availability | Low Latency (tunable consistency levels) |

For ScaleForge:
- Redirect path → choose **EL** (low latency) — Redis returns fast even if slightly stale
- URL creation → choose **EC** (consistency) — two users can't get duplicate codes

---

## Code Examples

### Demonstrating CP Behaviour — PostgreSQL Consistency

```typescript
// experiments/cp-database.ts
// Shows how PostgreSQL (CP) behaves: consistent reads, blocks on unreachable replica

import { PrismaClient } from '@prisma/client';

const prisma = new PrismaClient();

async function demonstrateConsistency() {
  // Write to primary (strong consistency)
  const url = await prisma.shortURL.create({
    data: { code: 'test01', longUrl: 'https://example.com', userId: 'user-1' },
  });

  // Immediate read — PostgreSQL guarantees you see your own write
  // even in a replicated setup (read-your-writes with same connection)
  const found = await prisma.shortURL.findUnique({ where: { code: 'test01' } });

  console.log('Write → immediate read consistent:', found?.code === url.code);
  // true: CP database guarantees this

  // In a partition scenario: PostgreSQL primary becomes unreachable
  // → system throws an error rather than serving stale data
  // This is the "C" in CP: prefer correctness over availability
}
```

### Demonstrating AP Behaviour — Redis Cache

```typescript
// experiments/ap-cache.ts
// Shows how Redis read replicas (AP) can serve stale data during a partition

import { Redis } from 'ioredis';

// Primary Redis — writes go here
const redisPrimary = new Redis({ host: 'localhost', port: 6379 });

// Read replica — reads come from here (lower latency, but can be stale)
// In production: Redis Replica or Redis Cluster read node
const redisReplica = new Redis({ host: 'localhost', port: 6380 }); // different port

async function demonstrateEventualConsistency() {
  // Write to primary
  await redisPrimary.set('url:abc123', 'https://old-url.com', 'EX', 300);
  console.log('Wrote old URL to primary');

  // Simulate update
  await redisPrimary.set('url:abc123', 'https://new-url.com', 'EX', 300);
  console.log('Updated URL on primary');

  // Read immediately from replica
  // In a healthy system: replication lag ~1-2ms, usually consistent
  // During partition: replica still returns 'old-url' — AP behaviour
  const fromReplica = await redisReplica.get('url:abc123');
  console.log('Read from replica:', fromReplica);
  // May return: 'https://old-url.com'  ← stale but available
  // Will not return: an error           ← availability preserved

  await redisPrimary.disconnect();
  await redisReplica.disconnect();
}

await demonstrateEventualConsistency();
```

### Choosing Consistency Level at Query Time (Cassandra-style tunable consistency)

```typescript
// In systems like Cassandra or DynamoDB, you choose consistency per operation.
// Here's how to model that decision in TypeScript:

type ConsistencyLevel = 'eventual' | 'read-your-writes' | 'strong';

interface ReadOptions {
  consistency: ConsistencyLevel;
}

class TunableUrlRepository {
  async findByCode(code: string, options: ReadOptions): Promise<string | null> {
    switch (options.consistency) {
      case 'eventual':
        // Read from Redis cache — fast, possibly stale
        return this.readFromCache(code);

      case 'read-your-writes':
        // Read from Redis; if missing, go to DB (ensures creator sees their URL)
        return (await this.readFromCache(code)) ?? this.readFromDatabase(code);

      case 'strong':
        // Always read from PostgreSQL primary — never stale, but slower
        return this.readFromDatabase(code);
    }
  }

  private async readFromCache(code: string): Promise<string | null> {
    // ... Redis lookup
    return null;
  }

  private async readFromDatabase(code: string): Promise<string | null> {
    // ... Postgres lookup
    return null;
  }
}

// ScaleForge uses different consistency levels for different operations:
const repo = new TunableUrlRepository();

// Redirect: eventual is fine — stale by 5 min is acceptable
const redirectUrl = await repo.findByCode('abc123', { consistency: 'eventual' });

// Creator viewing their own URL: read-your-writes
const myUrl = await repo.findByCode('abc123', { consistency: 'read-your-writes' });

// Admin audit: must be accurate
const auditUrl = await repo.findByCode('abc123', { consistency: 'strong' });
```

---

## Try It Yourself

**Exercise:** Classify each ScaleForge operation as requiring CP or AP behavior.

```typescript
// For each operation below, decide:
// 1. CP (need strong consistency, will sacrifice availability)
// 2. AP (can tolerate stale data, must stay available)
// 3. Neither matters much (low-stakes operation)
//
// Justify your answer.

const operations = [
  'User registers a new account',
  'User creates a short URL',
  'Visitor is redirected via short URL',
  'Click analytics are recorded',
  'User views their total click count',
  'Admin deletes a URL for abuse',
  'Rate limiter checks remaining quota',
  'Health check endpoint responds',
];
```

<details>
<summary>Show reasoning</summary>

| Operation | Type | Reasoning |
|-----------|------|-----------|
| User registers | CP | Duplicate emails must be prevented — requires unique constraint enforcement |
| Create short URL | CP | Code uniqueness required — two users can't get `abc123` |
| Redirect visitor | AP | Stale cache serving a deleted URL for 5 min is acceptable; unavailability is not |
| Record click | AP | Losing a few click events is acceptable; blocking redirects for analytics is not |
| View click count | AP/eventual | 5-second lag in analytics is fine |
| Admin deletes URL | CP | Must propagate before next redirect attempt — use short TTL + cache invalidation |
| Rate limiter | AP | Slightly over-serving on a partition is safer than blocking all traffic |
| Health check | AP | Must always return a response — that's the point of health checks |

</details>

---

## Capstone Connection

This topic explains every major technology choice in ScaleForge: Redis (AP) for the redirect path, PostgreSQL (CP) for URL creation and auth. When Module 07 splits ScaleForge into `url-service` and `analytics-service`, the reason the services communicate asynchronously via BullMQ (rather than synchronous HTTP) is precisely CAP: if analytics-service is partitioned from url-service, the redirect must still work (AP) — the queue absorbs the partition like a buffer.
