# 4.1 — Relational vs NoSQL vs NewSQL

## Concept

There is no universally best database. The right choice depends on your data's structure, query patterns, consistency requirements, and scale profile. Most production systems use 2–4 different storage technologies — each chosen for what it does best. ScaleForge uses PostgreSQL (relational) for URL records and Redis (key-value) for hot data; understanding why clarifies the general principle.

---

## Deep Dive

### The Storage Landscape

```
Data model taxonomy:

  RELATIONAL (SQL)              DOCUMENT                  KEY-VALUE
  PostgreSQL, MySQL             MongoDB, DynamoDB          Redis, DynamoDB
  ────────────────              ──────────────────         ────────────────
  Tables with schemas           JSON documents in          Hash map:
  Rows with typed columns       collections                  key → value
  ACID transactions             Flexible schema            No schema
  JOIN across tables            Embed vs reference         O(1) reads/writes
  Strong consistency            Tunable consistency        TTL support
  
  WIDE-COLUMN                   TIME-SERIES               GRAPH
  Cassandra, HBase              InfluxDB, TimescaleDB      Neo4j, Amazon Neptune
  ────────────────              ─────────────────────      ──────────────────────
  Rows + dynamic columns        Timestamped events         Nodes and edges
  Write-optimized               Specialized indexes        Relationship traversal
  No JOINs                      Downsampling / aggregates  Path finding queries
  Linear horizontal scaling     Retention policies         Social graphs
```

### SQL vs NoSQL Tradeoffs

```
SQL (Relational):
  Strengths:
    ✓ ACID guarantees (Atomicity, Consistency, Isolation, Durability)
    ✓ Ad-hoc queries — schema is queryable any way, even ways not anticipated
    ✓ Referential integrity enforced at DB level (foreign keys)
    ✓ Mature tooling, 40+ years of optimization
    ✓ JOINs allow normalised design without duplication
    
  Weaknesses:
    ✗ Schema migrations required for structure changes
    ✗ Horizontal sharding is complex (see Module 4.4)
    ✗ Impedance mismatch — objects to rows mapping requires ORM or manual work
    ✗ Object/document hierarchies require multiple JOINs or JSONB column

NoSQL (Document, Key-Value, Wide-Column):
  Strengths:
    ✓ Schema-free — add a field without ALTER TABLE
    ✓ Built for horizontal scale (sharding is the default model)
    ✓ Document model matches object graph (no ORM needed)
    ✓ Purpose-built for specific access patterns (key lookups, time series)
    
  Weaknesses:
    ✗ Eventual consistency by default (not ACID across documents)
    ✗ No server-side JOINs — application-side joins = N+1 pattern
    ✗ Schema validation must be in application code (or schema validation layer)
    ✗ Limited query expressiveness outside the primary access pattern
```

### When to Use What

```
Use case                    Technology      Reason
──────────────────────────  ──────────────  ─────────────────────────────────────
URL records + click counts  PostgreSQL      ACID, relational queries, referential integrity
Session tokens              Redis           TTL, O(1) lookup, pub/sub
User activity feed          DynamoDB/Cassandra  Write-heavy, time-ordered, no JOINs
System metrics (req/s, p99) Prometheus/InfluxDB  Optimised for time-window aggregation
Social graph traversal      Neo4j           Friend-of-friend queries
Full text search            Elasticsearch   Inverted index, relevance scoring
Large binary files          S3/GCS          Block storage, CDN integration
Audit logs (append only)    PostgreSQL      ACID inserts, point-in-time recovery
```

### The N+1 Problem

```
N+1 is one of the most common database performance bugs.
It happens when you load a list of items, then issue one query per item.

WRONG (N+1):
  SELECT * FROM urls WHERE user_id = 1;        -- 1 query → 50 rows
  
  for each url:
    SELECT * FROM clicks WHERE url_id = ?;     -- 50 queries (one per URL)
    
  Total: 51 queries
  
CORRECT (join / eager load):
  SELECT u.*, COUNT(c.id) AS click_count
  FROM urls u
  LEFT JOIN click_events c ON c.url_id = u.id
  WHERE u.user_id = 1
  GROUP BY u.id;                               -- 1 query
  
  Total: 1 query
  
With ORMs (Prisma/TypeORM):
  // BAD: triggers N+1
  const urls = await prisma.url.findMany({ where: { userId: 1 } });
  for (const url of urls) {
    const clicks = await prisma.clickEvent.count({ where: { urlId: url.id } });  // N queries!
  }
  
  // GOOD: single query with include
  const urls = await prisma.url.findMany({
    where: { userId: 1 },
    include: { _count: { select: { clickEvents: true } } }  // 1 query with JOIN
  });
```

---

## Code Examples

### PostgreSQL vs Redis: Right Tool for the Right Job

```typescript
// src/db/url.repository.ts
// Rule: PostgreSQL for persistent, relational data.
//       Redis for ephemeral, high-frequency, lookup-by-key data.

import { pool } from './pool.js';
import { redisClient } from '../cache/redis.client.js';

// CREATE — write to Postgres (source of truth)
export async function createUrl(code: string, targetUrl: string, userId: string) {
  const result = await pool.query<{ id: string; code: string; target_url: string }>(
    `INSERT INTO urls (code, target_url, user_id, created_at)
     VALUES ($1, $2, $3, NOW())
     RETURNING id, code, target_url`,
    [code, targetUrl, userId]
  );
  return result.rows[0];
}

// REDIRECT — read from Redis first (hot path, O(1)), fallback to Postgres
export async function resolveRedirect(code: string): Promise<string | null> {
  // Attempt Redis cache first (sub-millisecond)
  const cached = await redisClient.get(`url:${code}`);
  if (cached) return cached;

  // Cache miss — query Postgres
  const result = await pool.query<{ target_url: string }>(
    'SELECT target_url FROM urls WHERE code = $1 AND deleted_at IS NULL',
    [code]
  );

  if (result.rows.length === 0) return null;

  const { target_url } = result.rows[0];

  // Populate cache with 5-minute TTL
  await redisClient.set(`url:${code}`, target_url, 'EX', 300);

  return target_url;
}
```

### Fixing the N+1 Problem in Prisma

```typescript
// BEFORE (N+1 — do not do this):
async function listUrlsWithClicksBad(userId: string) {
  const urls = await prisma.url.findMany({ where: { userId } });
  return await Promise.all(
    urls.map(async (url) => ({
      ...url,
      clickCount: await prisma.clickEvent.count({ where: { urlId: url.id } }), // N queries
    }))
  );
}

// AFTER (single query with aggregation):
async function listUrlsWithClicksGood(userId: string) {
  return prisma.url.findMany({
    where: { userId },
    include: {
      _count: {
        select: { clickEvents: true },
      },
    },
    orderBy: { createdAt: 'desc' },
    take: 50,
  });
  // Prisma generates: SELECT u.*, COUNT(c.id) ... GROUP BY u.id LIMIT 50
}

// Verify with Prisma query logs:
// const prisma = new PrismaClient({ log: ['query'] })
// Count SELECT statements in the output — should be 1, not N+1
```

---

## Try It Yourself

**Exercise:** Classify the following ScaleForge data by the best storage technology and explain why.

```
Data                          Best Storage    Reason
──────────────────────────    ─────────────   ───────────────────────────────
1. url.code → url.target_url  ?               ?
2. Session token → user_id    ?               ?
3. Click events (raw)         ?               ?
4. Daily click aggregates     ?               ?
5. URL metadata (title, tags) ?               ?
6. Rate limit counter (IP)    ?               ?
```

<details>
<summary>Show answers</summary>

```
Data                          Best Storage    Reason
──────────────────────────    ─────────────   ───────────────────────────────
1. url.code → url.target_url  Redis           Hot path, O(1) key lookup, TTL
                               (+ Postgres    Postgres is source of truth;
                               as source)     Redis is the read cache

2. Session token → user_id    Redis           Short TTL (e.g., 24h), O(1),
                                              no need for persistence

3. Click events (raw)         Postgres        ACID inserts, queryable with
                               or Kafka       SQL, audit trail needed
                                              Kafka if throughput is very high

4. Daily click aggregates     PostgreSQL      Time-windowed queries (GROUP BY day)
                               TimescaleDB    TimescaleDB if keeping raw events
                               or Prometheus  for long periods

5. URL metadata (title, tags) PostgreSQL      Relational: tags table with
                                              many-to-many URL_tags

6. Rate limit counter (IP)    Redis           INCR is atomic, TTL resets window,
                                              must survive restarts → persist AOF
```

</details>

---

## Capstone Connection

ScaleForge's dual-database design (Postgres + Redis) is not redundancy — it's purposeful: Postgres provides durable ACID storage and relational query power, while Redis provides the sub-millisecond lookup performance needed for the redirect hot path. The Redis cache in `resolveRedirect()` here is the foundation for the full caching strategy in Module 05 (where you'll add cache invalidation on URL update and explore eviction policies).
