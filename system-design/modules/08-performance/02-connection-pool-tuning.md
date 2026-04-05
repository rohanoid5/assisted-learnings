# 8.2 — Connection Pool Tuning

## Concept

A connection pool pre-creates a fixed number of database connections and lends them to application code. The pool size is the single most impactful configuration knob for database-backed services: too small and requests queue up waiting for a connection; too large and the database runs out of resources and all queries slow down. The correct size is not 100 — it is derived from a formula.

---

## Deep Dive

### Why More Connections ≠ More Throughput

```
  Postgres can handle many simultaneous connections, but:
  
    - Each connection consumes ~5–10MB RAM on the Postgres server
    - Postgres uses one OS process per connection (not threads)
    - OS context-switching overhead grows with connection count
    - Postgres default: max_connections = 100
  
  At 200 connections with 4 Postgres CPU cores:
  
    200 queries all execute "simultaneously"
    CPU has 4 cores → at most 4 queries run at once
    Context switch overhead: ~2-5% CPU wasted per extra connection
    Net effect: MORE connections → SLOWER queries
  
  Optimal connections formula (Hikari/"C3P0" formula):
  
    pool_size = (num_db_cores × 2) + effective_spindle_count
    
    For a typical dev Postgres (2 vCPU, SSD = 1 spindle):
    pool_size = (2 × 2) + 1 = 5
    
    For production (8 vCPU Postgres RDS, SSD):
    pool_size = (8 × 2) + 1 = 17
    
  This is per application instance.
  With 10 ScaleForge pods, each pool = 10 connections → 100 total.
```

### Pool Anatomy

```
  pg.Pool lifecycle:

  Application startup:
    Pool created with { min: 2, max: 10 }
    2 connections opened to Postgres immediately (min)

  Request arrives:
    pool.query() → pool.connect() → assigns idle connection
    If no idle connection AND pool.totalCount < max:
      create new connection (~5ms overhead)
    If pool is at max AND all busy:
      request waits in queue (up to connectionTimeoutMillis)
      if timeout exceeded → throws "timeout exceeded when trying to connect"

  Request completes:
    client.release() → connection returned to idle pool

  Idle connections:
    Held for idleTimeoutMillis (e.g. 30000ms)
    Then closed (saves Postgres RAM)
    Pool re-creates if needed on next request

  ─────────────────────────────────────────────────────────
  
  Pool states and their Prometheus gauges:
  
  pool.totalCount     — all connections (idle + busy)     → gauge
  pool.idleCount      — waiting to be used                → gauge
  pool.waitingCount   — requests queued for a connection  → gauge (ALERT if > 0 for >5s)
```

### PgBouncer: The Connection Proxy

```
  ScaleForge on 10 pods × pool max 10 = 100 connections to Postgres.
  Scale to 100 pods × pool max 10 = 1,000 connections → Postgres OOM.

  PgBouncer solution:
  
    ScaleForge pods       PgBouncer          Postgres
    ┌──────────────┐      ┌──────────┐       ┌──────────┐
    │ pod 1: pool  │      │          │       │          │
    │  max=10      ├─────►│  Pools   ├──────►│ max_conn │
    │ pod 2: pool  │      │          │       │   = 20   │
    │  max=10      ├─────►│  1000    │       │          │
    │    ...       │      │  client  │       └──────────┘
    │ pod 100: pool│      │  conns   │
    │  max=10      ├─────►│  → 20    │
    └──────────────┘      │  server  │
                          │  conns   │
                          └──────────┘
    
    1000 application connections → 20 Postgres connections.
    PgBouncer multiplexes at the transaction level
    (server connection released after each COMMIT/ROLLBACK).
    
    BUT: PgBouncer transaction-mode cannot support:
      - LISTEN/NOTIFY (requires session-level connection)
      - Advisory locks
      - Prepared statements (in pgbouncer < 1.21)
    
    → Use a separate direct pg.Client for those features.
```

---

## Code Examples

### Pool Configuration with Metrics Export

```typescript
// src/db/pool.ts
import pg from 'pg';
import { Gauge } from 'prom-client';
import { metricsRegistry } from '../metrics/registry.js';

const DB_MAX_CONNECTIONS = Number(process.env.DB_POOL_MAX ?? 10);

export const primaryPool = new pg.Pool({
  connectionString: process.env.DATABASE_URL,
  max: DB_MAX_CONNECTIONS,
  min: 2,                         // always keep 2 connections warm
  idleTimeoutMillis: 30_000,      // close idle connections after 30s
  connectionTimeoutMillis: 5_000, // throw if no connection available in 5s
  statement_timeout: 10_000,      // kill queries running longer than 10s
});

// Export pool stats as Prometheus gauges
// Note: these use callbacks because pool.totalCount etc. are live properties
const poolTotalGauge = new Gauge({
  name: 'pg_pool_total_connections',
  help: 'Total number of connections in the pool',
  registers: [metricsRegistry],
  collect() { this.set(primaryPool.totalCount); },
});

const poolIdleGauge = new Gauge({
  name: 'pg_pool_idle_connections',
  help: 'Number of idle connections in the pool',
  registers: [metricsRegistry],
  collect() { this.set(primaryPool.idleCount); },
});

const poolWaitingGauge = new Gauge({
  name: 'pg_pool_waiting_requests',
  help: 'Number of requests waiting for a connection',
  registers: [metricsRegistry],
  collect() { this.set(primaryPool.waitingCount); },
});

// Log pool errors so they're visible even without Prometheus
primaryPool.on('error', (err) => {
  console.error({ err }, 'pg pool error');
});
```

### Explicit Connection Lease for Multi-Statement Operations

```typescript
// Use pool.connect() when you need to hold a connection across
// multiple queries (e.g., a transaction). Release the client in
// a finally block — never let it escape into the wild.

import { primaryPool } from '../db/pool.js';

export async function transferClickCount(
  fromCode: string,
  toCode: string,
  count: number,
): Promise<void> {
  const client = await primaryPool.connect();
  try {
    await client.query('BEGIN');
    await client.query(
      'UPDATE urls SET click_count = click_count - $2 WHERE short_code = $1',
      [fromCode, count],
    );
    await client.query(
      'UPDATE urls SET click_count = click_count + $2 WHERE short_code = $1',
      [toCode, count],
    );
    await client.query('COMMIT');
  } catch (err) {
    await client.query('ROLLBACK');
    throw err;
  } finally {
    client.release();  // ALWAYS release, even on error
  }
}
```

### Read Replica Split

```typescript
// src/db/pool.ts (extended)
// Write operations → primaryPool (single master)
// Read operations  → replicaPool (one or more read replicas)
//
// The redirect query is SELECT-only — it can use the replica.
// URL creates and click_count updates go to primary.

export const replicaPool = new pg.Pool({
  connectionString: process.env.DATABASE_REPLICA_URL ?? process.env.DATABASE_URL,
  max: DB_MAX_CONNECTIONS,
  min: 2,
  idleTimeoutMillis: 30_000,
  connectionTimeoutMillis: 5_000,
  statement_timeout: 5_000,  // read queries should be fast — shorter timeout
});

// Usage:
// const { rows } = await replicaPool.query(
//   'SELECT original_url, is_active FROM urls WHERE short_code = $1',
//   [code],
// );
```

---

## Try It Yourself

**Exercise:** Find the pool exhaustion point for your machine.

```bash
# pool-exhaustion.exercise.sh

# Step 1: Set pool max to a very small number
export DB_POOL_MAX=3

# Step 2: Start ScaleForge
node dist/server.js &

# Step 3: Blast it with more concurrency than the pool allows
autocannon \
  -c 50 \          # 50 concurrent connections
  -d 10 \          # 10 seconds
  http://localhost:3001/api/v1/urls

# Expected: some requests return 500 with "timeout exceeded when trying to connect"
# The error rate tells you how many req/s exceeded pool capacity
```

```typescript
// pool-exhaustion.exercise.ts

// TODO:
// 1. Add a try/catch around pool.query() in the redirect handler
//    that specifically catches the "timeout exceeded" error and
//    returns 503 with Retry-After: 5 instead of letting it crash.
//
// 2. Add a Prometheus counter for pool timeout events:
//    pg_pool_timeout_total{pool="primary"}
//
// 3. Tune pool max upward until the timeout counter drops to zero
//    under your expected peak load.
//    Note the Postgres RAM usage at each pool size
//    (SELECT sum(pg_backend_memory_contexts.total_bytes)
//     FROM pg_backend_memory_contexts;)
```

<details>
<summary>Show 503 pool exhaustion handler</summary>

```typescript
// In redirect.router.ts:
import { DatabaseError } from 'pg';

try {
  const target = await getTarget(req.params.code);
  // ...
} catch (err) {
  const msg = (err as Error).message ?? '';
  if (msg.includes('timeout exceeded when trying to connect')) {
    poolTimeoutsTotal.inc({ pool: 'primary' });
    res.setHeader('Retry-After', '5');
    res.status(503).json({ error: 'Database unavailable, please retry' });
    return;
  }
  throw err;
}
```

</details>

---

## Capstone Connection

ScaleForge's redirect path under high concurrency is a race for pool connections. With 5000 req/s and a pool of 10, each connection handles 500 req/s — fine if Redis caches most redirects (no DB query needed). When the cache cold-starts (e.g., after a Redis restart), every request hits Postgres simultaneously, exhausting the pool. The `pg_pool_waiting_requests` gauge going above zero is your warning signal that you need to either increase pool size, add replicas, or improve cache warm-up speed.
