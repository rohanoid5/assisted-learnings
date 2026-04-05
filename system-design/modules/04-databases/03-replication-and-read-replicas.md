# 4.3 — Replication and Read Replicas

## Concept

Replication continuously synchronises data from a primary database to one or more replica databases. It serves two purposes: **availability** (replicas become primary if the primary fails) and **read scaling** (spread read queries across replicas so the primary only handles writes). For ScaleForge, routing `GET /api/v1/urls` queries to a read replica keeps the primary free for writes and reduces contention.

---

## Deep Dive

### Streaming Replication

```
Primary                         Replica 1               Replica 2
──────                          ─────────               ─────────
  Write: INSERT INTO urls ...
  ↓
  Write to WAL (Write-Ahead Log)
  (WAL = append-only change log)
  ↓
  Apply to primary data files
  ↓──────────────────────────►  Receive WAL stream
                                Apply WAL to data files    (same, async)
                                
WAL = Write-Ahead Log
  - Every change (INSERT/UPDATE/DELETE) is recorded as a WAL entry
  - Primary sends WAL stream continuously to replicas (TCP connection)
  - Replica applies WAL entries in order → data converges

Replication lag:
  Async: Primary commits before replica confirms → replica may be milliseconds behind
  Sync:  Primary waits for replica to confirm → zero lag, but write latency increases
  
  Most setups: async replication (default in PostgreSQL)
  Result: replicas are typically 1-50ms behind primary on write-heavy workloads
```

### Replication Topologies

```
  Primary-Replica (most common):
    
    [Primary] ──WAL──► [Replica 1]   Reads: Replica 1 or 2
                  └──► [Replica 2]   Writes: Primary only
    
    Failover: promote Replica 1 to primary (manual or with Patroni/repmgr)

  Cascade Replication (large-scale):
    
    [Primary] ──WAL──► [Replica 1] ──WAL──► [Replica 2]
                                        └──► [Replica 3]
    
    Reduces primary CPU/network burden.
    Replica 2 and 3 may lag more than Replica 1.

  Multi-Primary (rare, complex):
    
    [Primary A] ◄──► [Primary B]   Both accept writes
    
    Conflict resolution required. Used by CockroachDB, Galera.
    High complexity. Use only if geographic write locality is critical.
```

### Read-Your-Writes Problem

```
  Timeline:
    t=0:  Client writes URL code "abc" → Primary
    t=0:  Primary commits, replica replication starts
    t=1ms Primary responds 201 Created to client
    t=2ms Client does GET /api/v1/urls — routed to Replica
    t=2ms Replica hasn't received WAL entry yet (lag = 3ms)
    t=2ms Replica returns 404 Not Found for "abc"
    
    Client sees: "I just created abc but it's gone!"
    
  Solutions:
    1. Sticky writes: route reads to primary for N seconds after a write
       - Simplest: set a cookie/header "read-from-primary" with expiry
       
    2. Session consistency: track write timestamp, replica only serves
       reads at that timestamp or later
       
    3. Synchronous replication: replicas never lag → but slower writes
    
    4. Application-level: cache the just-created object, serve from cache
       for next N seconds (already done in resolveRedirect with Redis!)
       
  ScaleForge: Because we write to Redis on URL creation, the redirect path
  always uses Redis/Primary-direct. The listing endpoint (user dashboard)
  can tolerate slight staleness — if a URL appears 1 second late in the list,
  that's acceptable.
```

---

## Code Examples

### Dual Pool: Primary for Writes, Replica for Reads

```typescript
// src/db/pool.ts — separate connection pools for primary and replica

import pg from 'pg';

const { Pool } = pg;

// Primary: accepts reads AND writes, but we route only writes here
export const primaryPool = new Pool({
  connectionString: process.env.DATABASE_PRIMARY_URL,
  max: 10,
  min: 2,
  idleTimeoutMillis: 30_000,
  connectionTimeoutMillis: 5_000,
  statement_timeout: 10_000,
});

// Replica: read-only, can accept more connections (reads are cheaper)
export const replicaPool = new Pool({
  connectionString: process.env.DATABASE_REPLICA_URL ?? process.env.DATABASE_PRIMARY_URL,
  max: 20,       // More connections for read traffic
  min: 2,
  idleTimeoutMillis: 30_000,
  connectionTimeoutMillis: 5_000,
  statement_timeout: 5_000,  // Reads should be faster — tighter timeout
});

// If no replica configured (development), both pools point to primary
// by using DATABASE_PRIMARY_URL as fallback for DATABASE_REPLICA_URL
```

```typescript
// src/db/url.repository.ts — smart routing based on operation type

import { primaryPool, replicaPool } from './pool.js';

interface UrlRecord {
  id: string;
  code: string;
  target_url: string;
  user_id: string;
  created_at: Date;
  click_count: number;
}

// WRITE → primary pool
export async function createUrl(
  code: string,
  targetUrl: string,
  userId: string
): Promise<UrlRecord> {
  const result = await primaryPool.query<UrlRecord>(
    `INSERT INTO urls (code, target_url, user_id)
     VALUES ($1, $2, $3)
     RETURNING *`,
    [code, targetUrl, userId]
  );
  return result.rows[0];
}

// READ (listing, non-critical) → replica pool
export async function listUserUrls(
  userId: string,
  limit: number,
  offset: number
): Promise<UrlRecord[]> {
  const result = await replicaPool.query<UrlRecord>(
    `SELECT u.*, COALESCE(c.cnt, 0) AS click_count
     FROM urls u
     LEFT JOIN (
       SELECT url_id, COUNT(*) AS cnt
       FROM click_events
       GROUP BY url_id
     ) c ON c.url_id = u.id
     WHERE u.user_id = $1 AND u.deleted_at IS NULL
     ORDER BY u.created_at DESC
     LIMIT $2 OFFSET $3`,
    [userId, limit, offset]
  );
  return result.rows;
}

// READ (redirect) → primary pool (for read-your-writes consistency)
// In practice, Redis cache means this query is rare
export async function resolveCode(code: string): Promise<string | null> {
  const result = await primaryPool.query<{ target_url: string }>(
    'SELECT target_url FROM urls WHERE code = $1 AND deleted_at IS NULL',
    [code]
  );
  return result.rows[0]?.target_url ?? null;
}
```

### Replica Lag Monitor

```typescript
// src/db/replica-monitor.ts — check replication lag in real time

import { primaryPool, replicaPool } from './pool.js';

interface ReplicationLag {
  lagBytes: number;
  lagMs: number;              // Estimated lag in milliseconds
  replicaTimestamp: Date;
  primaryTimestamp: Date;
}

export async function checkReplicationLag(): Promise<ReplicationLag | null> {
  try {
    const [primaryResult, replicaResult] = await Promise.all([
      primaryPool.query<{ now: Date }>('SELECT NOW() AS now'),
      replicaPool.query<{ now: Date; replay_lag: string }>(
        `SELECT NOW() AS now,
                EXTRACT(EPOCH FROM (NOW() - pg_last_xact_replay_timestamp())) * 1000 AS replay_lag`
      ),
    ]);

    const primaryTime = primaryResult.rows[0].now;
    const replicaTime = replicaResult.rows[0].now;
    const lagMs       = parseFloat(String(replicaResult.rows[0].replay_lag)) || 0;

    return {
      lagBytes: 0,    // Would need pg_stat_replication on primary (different query)
      lagMs: Math.round(lagMs),
      replicaTimestamp: replicaTime,
      primaryTimestamp: primaryTime,
    };
  } catch {
    return null; // Replica unavailable
  }
}

// Example: expose as /metrics endpoint
// Alert if lag > 5000ms (5 seconds) — indicates replica is falling behind
```

---

## Try It Yourself

**Exercise:** Implement replica lag detection and automatic fallback to primary.

```typescript
// replica-fallback.exercise.ts

// TODO:
// 1. Write a function `getReadPool(maxLagMs: number): Pool` that:
//    a. Calls checkReplicationLag()
//    b. If lag < maxLagMs, returns replicaPool
//    c. If lag >= maxLagMs OR replica unavailable, returns primaryPool
//    d. Logs a warning when falling back to primary
//
// 2. Update listUserUrls() to call getReadPool(2000) instead of hardcoding replicaPool
//
// 3. Test scenario:
//    - Pause replication (with Docker: docker pause scaleforge-replica-1)
//    - Wait 5 seconds
//    - Call GET /api/v1/urls — should automatically route to primary
//    - Resume replica: docker unpause scaleforge-replica-1
//    - After replica catches up, reads should return to replica
```

<details>
<summary>Show solution</summary>

```typescript
import { primaryPool, replicaPool } from './db/pool.js';
import { checkReplicationLag } from './db/replica-monitor.js';
import type { Pool } from 'pg';
import pino from 'pino';

const log = pino({ name: 'replica-fallback' });

export async function getReadPool(maxLagMs: number = 2000): Promise<Pool> {
  const lag = await checkReplicationLag();

  if (lag === null) {
    log.warn('Replica unreachable — using primary for read');
    return primaryPool;
  }

  if (lag.lagMs > maxLagMs) {
    log.warn({ lagMs: lag.lagMs, maxLagMs }, 'Replica lag too high — using primary for read');
    return primaryPool;
  }

  return replicaPool;
}

// Updated listUserUrls:
export async function listUserUrls(userId: string, limit: number, offset: number) {
  const pool = await getReadPool(2000); // Fall back if replica > 2s behind
  const result = await pool.query(
    `SELECT * FROM urls WHERE user_id = $1 AND deleted_at IS NULL
     ORDER BY created_at DESC LIMIT $2 OFFSET $3`,
    [userId, limit, offset]
  );
  return result.rows;
}
```

</details>

---

## Capstone Connection

The dual-pool pattern here directly powers ScaleForge's read/write split. With 3 app replicas and 2 DB replicas:
- Total write connections: 3 replicas × 10 = 30 connections to primary (within `max_connections=100`)
- Total read connections: 3 replicas × 20 = 60 connections to each read replica

In Module 4.6 you'll add PgBouncer — which multiplexes those 60 connections through just 10 actual Postgres connections, letting you scale to 20 app replicas without exhausting `max_connections`.
