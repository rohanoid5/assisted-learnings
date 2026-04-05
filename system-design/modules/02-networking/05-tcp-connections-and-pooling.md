# 2.5 — TCP, Connections, and Pooling

## Concept

Every database query, Redis command, and inter-service call goes over a TCP connection. Establishing a TCP connection is not free — it takes 1.5 round trips and CPU on both ends. Connection pooling amortises this cost by keeping connections alive and reusing them across requests. Misconfigured pool sizes are one of the most common causes of performance degradation under load.

---

## Deep Dive

### TCP Connection Lifecycle

```
Establishing a connection (3-way handshake):
  Client                Server
    │── SYN ────────────►│  T=0ms
    │◄── SYN-ACK ────────│  T=0.5ms (one round trip)
    │── ACK ────────────►│  T=1ms
    │── GET /query ──────►│  T=1ms  (can send data now)
    │◄── 200 OK ──────────│  T=5ms  (query executes: ~4ms for simple SELECT)
    │
    Total overhead: 1.5 × RTT just to start talking

Closing a connection (4-way handshake):
  Client                Server
    │── FIN ────────────►│
    │◄── ACK ────────────│
    │◄── FIN ────────────│
    │── ACK ────────────►│

Without pooling: Pay 1.5×RTT for EVERY database query.
  On localhost: RTT ~0.1ms → overhead ~0.15ms (minor)
  On cloud same-region: RTT ~1ms → overhead ~1.5ms per query
  Cross-region: RTT ~100ms → overhead ~150ms per query (painful)
```

### Keep-Alive — Reusing TCP Connections

HTTP keep-alive (persistent connections) reuses a TCP connection for multiple requests:

```
Without keep-alive:
  Handshake → Request 1 → Close → Handshake → Request 2 → Close

With keep-alive:
  Handshake → Request 1 → Request 2 → Request 3 → ... → [idle timeout] → Close
  
For ScaleForge:
  - Browser→Nginx: HTTP/2 (multiplexed, persistent by default)
  - Nginx→Node app: upstream keepalive 32 (reuse up to 32 connections)
  - Node app→PostgreSQL: pg connection pool (persistent, reused)
  - Node app→Redis: ioredis persistent connection (single connection, pipelining)
```

### Connection Pooling — The Maths

#### Why Pool Matters for PostgreSQL

PostgreSQL creates a **new OS process** for each connection (pre-fork model). Each process consumes ~5-10 MB RAM:

```
100 connections × 8 MB each = 800 MB RAM just for connections
1000 connections × 8 MB each = 8 GB RAM (exceeds most cloud DB tiers)
```

PostgreSQL's hard limit is typically 100-200 connections (configurable). Exceeding it causes `FATAL: sorry, too many clients already`.

#### Calculating Optimal Pool Size

Little's Law for connection pools: $connections\_needed = throughput \times query\_time$

For ScaleForge analytics queries (not the redirect path, which uses Redis):

```
Analytics write throughput: 1,157 clicks/sec (average)
Average query time: 5ms = 0.005s (INSERT to clicks table)

Connections needed = 1,157 × 0.005 = 5.8 ≈ 6 connections

For 3 replicas: 6 connections × 3 app replicas = 18 total connections to DB

Pool size per replica: 10 (headroom above the calculated 6)
PgBouncer/connection pooler: sits between app and Postgres,
  reduces actual Postgres connections needed
```

#### Pool Size Rule of Thumb (HikariCP / pg's recommendation)

For I/O-bound workloads (most web apps):

$$pool\_size = \frac{cpu\_cores \times 2 + effective\_spindle\_count}{\text{number of app replicas}}$$

For a 4-core DB server with SSD, 3 app replicas: $(4 \times 2 + 1) / 3 \approx 3$ per replica. Start there and measure.

---

## Code Examples

### Configuring PostgreSQL Connection Pool with `pg`

```typescript
// src/db/pool.ts

import pg from 'pg';
import { logger } from '../logger.js';

const { Pool } = pg;

const pool = new Pool({
  connectionString: process.env.DATABASE_URL,
  
  // Pool configuration
  max: 10,              // Maximum connections in the pool per app instance
  min: 2,               // Keep 2 connections alive (don't tear down on idle)
  idleTimeoutMillis: 10_000,  // Close idle connections after 10 seconds
  connectionTimeoutMillis: 5_000,  // Fail fast if can't get connection in 5s
  
  // Statement timeout — prevent runaway queries from blocking a connection
  // Equivalent to: SET statement_timeout = '30s' on each connection
  statement_timeout: 30_000,
});

// Monitor pool health — expose these as Prometheus metrics in Module 08
pool.on('error', (err) => logger.error({ err }, 'Idle client error'));
pool.on('connect', () => logger.debug('New DB connection created'));
pool.on('acquire', () => logger.debug(`Pool size: ${pool.totalCount}, idle: ${pool.idleCount}, waiting: ${pool.waitingCount}`));

export { pool };

// Expose pool stats for metrics
export function getPoolStats() {
  return {
    total: pool.totalCount,
    idle: pool.idleCount,
    waiting: pool.waitingCount,
  };
}
```

### Detecting Pool Exhaustion Before It Happens

```typescript
// src/monitoring/pool-health.ts
// Alert before pool exhaustion — a waiting count > 0 means requests are queueing

import { getPoolStats } from '../db/pool.js';
import { logger } from '../logger.js';

const POOL_MAX = 10;
const WARNING_THRESHOLD = 0.8; // Alert when 80% of connections are in use

export function monitorPoolHealth(intervalMs = 5000) {
  return setInterval(() => {
    const { total, idle, waiting } = getPoolStats();
    const utilization = (total - idle) / POOL_MAX;

    if (waiting > 0) {
      logger.warn({ waiting, total, idle }, 'DB pool: requests waiting for connection!');
      // This is a leading indicator of performance degradation
      // Next steps: increase pool size OR reduce query latency OR add read replica
    } else if (utilization > WARNING_THRESHOLD) {
      logger.warn({ utilization: (utilization * 100).toFixed(0) + '%' }, 'DB pool utilization high');
    }
  }, intervalMs);
}
```

### Redis Connection — Single Connection vs. Cluster

```typescript
// src/cache/redis.client.ts
// Redis (unlike PostgreSQL) handles ~50k commands/sec on a single connection
// via command pipelining — no pool needed for single-node Redis.

import { Redis } from 'ioredis';

// Single Redis connection — reused for all commands
export const redisClient = new Redis({
  host: process.env.REDIS_HOST ?? 'localhost',
  port: 6379,
  
  // Automatic reconnection with exponential backoff
  retryStrategy: (times) => {
    if (times > 10) return null; // Stop retrying after 10 attempts
    return Math.min(times * 200, 2000); // 200ms → 2000ms backoff
  },
  
  // Lazy connect — don't fail on startup if Redis is briefly unavailable
  lazyConnect: true,
  
  // Commands that should not be retried after reconnection
  // (e.g., don't re-run INCR — it would increment twice)
  maxRetriesPerRequest: 0,
  
  enableOfflineQueue: false, // Fail immediately when disconnected (don't queue)
});

redisClient.on('error', (err) => console.error('Redis error:', err));
redisClient.on('connect', () => console.log('Redis connected'));
redisClient.on('reconnecting', () => console.warn('Redis reconnecting...'));

await redisClient.connect();
```

---

## Try It Yourself

**Exercise:** Benchmark the impact of connection pooling on redirect latency.

```typescript
// experiments/pool-benchmark.ts
// Compare: one connection per request vs. shared pool

import pg from 'pg';

const { Pool, Client } = pg;
const CONNECTION_STRING = process.env.DATABASE_URL ?? 'postgresql://localhost/scaleforge';

async function withoutPool(queries: number): Promise<number> {
  // TODO: Run `queries` sequential SELECTs, each creating + destroying a new connection
  // Measure total time in ms
  throw new Error('Not implemented');
}

async function withPool(queries: number, poolSize: number): Promise<number> {
  // TODO: Create a Pool with `poolSize`, run `queries` concurrent SELECTs
  // Measure total time in ms
  throw new Error('Not implemented');
}

const QUERY_COUNT = 100;
console.log(`\nRunning ${QUERY_COUNT} queries each:\n`);

const noPool = await withoutPool(QUERY_COUNT);
console.log(`Without pool: ${noPool}ms`);

for (const size of [1, 5, 10, 20]) {
  const withPoolTime = await withPool(QUERY_COUNT, size);
  console.log(`Pool size ${size}: ${withPoolTime}ms`);
}
```

<details>
<summary>Show solution</summary>

```typescript
async function withoutPool(queries: number): Promise<number> {
  const start = performance.now();
  for (let i = 0; i < queries; i++) {
    const client = new Client({ connectionString: CONNECTION_STRING });
    await client.connect();
    await client.query('SELECT 1');
    await client.end();
  }
  return performance.now() - start;
}

async function withPool(queries: number, poolSize: number): Promise<number> {
  const pool = new Pool({ connectionString: CONNECTION_STRING, max: poolSize });
  const start = performance.now();
  await Promise.all(
    Array.from({ length: queries }, () => pool.query('SELECT 1'))
  );
  await pool.end();
  return performance.now() - start;
}

// Typical results (on local Postgres):
// Without pool:    800ms  (100 × ~8ms handshake + ~0.1ms query)
// Pool size 1:     300ms  (reuses same connection, but serial)
// Pool size 10:     50ms  (10 concurrent, 10 batches of 10)
// Pool size 20:     30ms  (20 concurrent, 5 batches of 20)
// Pool size 100:    25ms  (diminishing returns)
```

</details>

---

## Capstone Connection

ScaleForge's pool configuration directly determines throughput capacity. At 10k redirect req/s with 10% cache misses (requiring a DB lookup), the click INSERT workers need ~6 connections per replica — well within a pool of 10. But the analytics-service queries in Module 07 (complex JOINs and aggregations for reports) change the calculus: a single report query might take 200ms, requiring 3 replicas × 5 report workers × 0.2s = 3 connections dedicated just to reports. Monitoring `pool.waitingCount` (introduced here) is what alerts you before report queries start starving click writes.
