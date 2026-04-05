# 4.6 — Connection Pooling with PgBouncer

## Concept

PostgreSQL creates a new OS process for each connection — and each process uses ~5MB of memory. At 200 connections, that's 1GB just for connection overhead. More importantly, Postgres's query planner and lock manager degrade as connection count rises. PgBouncer is a lightweight connection multiplexer that sits between your app and Postgres, allowing thousands of application-level connections to share a small pool of actual Postgres connections.

---

## Deep Dive

### The Connection Problem at Scale

```
Without PgBouncer — 5 app replicas × 10 pool connections:

  App Replica 1  ──10 conns──►┐
  App Replica 2  ──10 conns──►│
  App Replica 3  ──10 conns──►├──► Postgres (50 connections)
  App Replica 4  ──10 conns──►│    ~250MB overhead, OK
  App Replica 5  ──10 conns──►┘

Scale to 20 replicas:
  20 replicas × 10 connections = 200 connections
  Postgres max_connections = 100 (default) → FATAL: too many connections

With PgBouncer — 20 app replicas × 10 pool connections → 10 Postgres connections:

  App Replica 1   ──10 conns──►┐
  App Replica 2   ──10 conns──►│
  ...             ──10 conns──►├──► PgBouncer ──10 conns──► Postgres
  App Replica 20  ──10 conns──►┘    (multiplexes)
                                     200 → 10 connections
                                     ~50MB overhead, 95% saved
```

### PgBouncer Pooling Modes

```
Session mode (default):
  Each client connection is assigned to a server connection for 
  its entire session duration.
  
  App connects → gets dedicated Postgres connection → App disconnects → connection released
  
  Limitation: If the app's pool has idle connections open, they hold Postgres 
  connections even during idle time. No real multiplexing benefit.
  
Transaction mode (recommended for stateless apps):
  A Postgres connection is assigned only for the duration of one TRANSACTION.
  Between transactions, the Postgres connection is returned to the pool.
  
  App sends BEGIN → PgBouncer assigns Postgres connection
  App sends COMMIT/ROLLBACK → Postgres connection returned to PgBouncer pool
  
  Benefit: 200 app connections can share 10 Postgres connections
  because apps spend most time between transactions (waiting for HTTP requests).
  
  Limitation: Can't use session-level features:
    - SET (session variables) → use SET LOCAL inside transaction instead
    - LISTEN/NOTIFY → use a separate dedicated connection for this
    - Prepared statements (unless enable_protocol_aware=on in PgBouncer 1.21+)

Statement mode (rarely used):
  Connection returned to pool after each individual SQL statement.
  Can't run multi-statement transactions. Almost never appropriate.
```

### The PgBouncer Configuration Stack

```
/etc/pgbouncer/pgbouncer.ini:

  [databases]
  scaleforge = host=postgres port=5432 dbname=scaleforge

  [pgbouncer]
  pool_mode = transaction
  max_client_conn = 1000     # Max app-side connections to PgBouncer
  default_pool_size = 20     # Postgres connections per [database] entry
  reserve_pool_size = 5      # Extra connections for traffic bursts
  reserve_pool_timeout = 3   # Wait N seconds before using reserve pool
  server_idle_timeout = 600  # Close idle Postgres connections after 10 min
  server_lifetime = 3600     # Recycle Postgres connections hourly
  
Reading the numbers:
  max_client_conn = 1000  → can handle 1000 concurrent app connections
  default_pool_size = 20  → uses at most 20 Postgres connections
  Multiplexing ratio = 1000:20 = 50:1
```

---

## Code Examples

### Docker Compose Configuration

```yaml
# docker-compose.yml — add PgBouncer service

services:
  pgbouncer:
    image: bitnami/pgbouncer:1.22.1
    environment:
      POSTGRESQL_HOST: postgres
      POSTGRESQL_PORT: 5432
      POSTGRESQL_DATABASE: scaleforge
      POSTGRESQL_USERNAME: user
      POSTGRESQL_PASSWORD: pass
      PGBOUNCER_POOL_MODE: transaction
      PGBOUNCER_MAX_CLIENT_CONN: 1000
      PGBOUNCER_DEFAULT_POOL_SIZE: 20
      PGBOUNCER_RESERVE_POOL_SIZE: 5
      PGBOUNCER_SERVER_TLS_SSLMODE: disable   # Internal network, TLS optional
    ports:
      - "5433:6432"   # PgBouncer listens on 6432 by default
    depends_on:
      postgres:
        condition: service_healthy

  app:
    build: .
    environment:
      # App now connects to PgBouncer, not Postgres directly
      DATABASE_PRIMARY_URL: postgres://user:pass@pgbouncer:6432/scaleforge
    depends_on:
      - pgbouncer
      - redis
```

### App Pool Configuration for Transaction Mode

```typescript
// src/db/pool.ts — adjust for PgBouncer transaction mode

import pg from 'pg';

const { Pool } = pg;

export const primaryPool = new Pool({
  connectionString: process.env.DATABASE_PRIMARY_URL,
  // With PgBouncer transaction mode, the app can use a larger pool
  // because PgBouncer multiplexes them into fewer Postgres connections
  max: 20,         // Each replica holds up to 20 PgBouncer connections
  min: 2,
  idleTimeoutMillis: 10_000,      // Return idle connections quickly (PgBouncer handles pooling)
  connectionTimeoutMillis: 5_000,

  // IMPORTANT for PgBouncer transaction mode:
  // Disable statement-level keepalive — PgBouncer manages connection lifecycle
  // Avoid session-level SET statements — use SET LOCAL inside transactions instead
});

// PgBouncer transaction mode: DO NOT use session variables
// BAD:
//   await pool.query("SET search_path TO myschema");  // Session variable — breaks in TXN mode
// GOOD:
//   await pool.query("BEGIN");
//   await pool.query("SET LOCAL search_path TO myschema");  // LOCAL = transaction-scoped
//   await pool.query("COMMIT");
```

### Monitoring PgBouncer Stats

```typescript
// src/db/pgbouncer-stats.ts
// PgBouncer exposes a virtual `pgbouncer` database with stats tables.
// Connect to it to get pool utilization metrics.

import pg from 'pg';
const { Pool } = pg;

// Connect to the PgBouncer admin database (not the app database)
const adminPool = new Pool({
  host:     process.env.PGBOUNCER_HOST ?? 'localhost',
  port:     parseInt(process.env.PGBOUNCER_PORT ?? '5433', 10),
  database: 'pgbouncer',   // Magic database name for admin access
  user:     'pgbouncer',   // Admin user
  password: process.env.PGBOUNCER_ADMIN_PASSWORD,
  max: 1,   // One admin connection is enough
});

interface PgBouncerPoolStats {
  database:        string;
  cl_active:       number;    // Active client connections
  cl_waiting:      number;    // Clients waiting for a server connection
  sv_active:       number;    // Active server (Postgres) connections
  sv_idle:         number;    // Idle server connections
  sv_used:         number;    // Server connections used but not active
  maxwait:         number;    // Longest wait time in seconds
}

export async function getPgBouncerStats(): Promise<PgBouncerPoolStats[]> {
  const result = await adminPool.query<PgBouncerPoolStats>('SHOW POOLS');
  return result.rows;
}

// Key metrics to alert on:
//   cl_waiting > 0  — clients waiting for connections (pool too small)
//   maxwait > 1     — wait time > 1s (immediate action needed)
//   sv_idle > default_pool_size * 0.8  — pool oversized (shrink it)
```

---

## Try It Yourself

**Exercise:** Calculate the optimal PgBouncer pool size for ScaleForge's production deployment.

```
Given:
  - 5 app replicas, each with pool.max = 20 (100 total PgBouncer client connections)
  - Postgres can comfortably handle 50 concurrent connections
  - Average transaction duration: 2ms
  - Peak request rate: 5,000 req/sec
  - Each request runs 1 SQL transaction

# TODO: Fill in the blanks using Little's Law (L = λW)

Step 1: Calculate required Postgres connections
  Lambda (λ) = requests per second = ______
  W (average transaction time) = ______
  L (connections in flight) = λ × W = ______

Step 2: Add safety margin (never run > 70% of capacity)
  Required connections with margin = L / 0.7 = ______

Step 3: Set PgBouncer default_pool_size and verify it fits within Postgres max_connections

  PGBOUNCER_DEFAULT_POOL_SIZE = ______
  Does ______ < 50? (Postgres comfort zone) ______ (yes/no)
```

<details>
<summary>Show solution</summary>

```
Step 1:
  λ = 5,000 requests/sec
  W = 2ms = 0.002 seconds
  L = 5,000 × 0.002 = 10 connections in flight at peak

Step 2:
  Required with margin = 10 / 0.7 = 14.3 → round up to 15

Step 3:
  PGBOUNCER_DEFAULT_POOL_SIZE = 15
  15 < 50? Yes ✓ (well within Postgres comfort zone)

Conclusion:
  With PgBouncer at pool_size=15:
    - 5 replicas × 20 = 100 PgBouncer client connections
    - PgBouncer multiplexes into 15 Postgres connections
    - Multiplexing ratio: 100:15 ≈ 7:1
    - Headroom: 50 - 15 = 35 connections available for analytics queries,
      migrations, admin access

Without PgBouncer:
  5 replicas × 20 = 100 Postgres connections directly
  This exceeds Postgres max_connections=100 (default)
  Connection refused errors at peak!

With PgBouncer: 100 app connections → 15 Postgres connections
  Postgres stays comfortable even at peak
```

</details>

---

## Capstone Connection

PgBouncer is the component that allows ScaleForge to horizontally scale to 20+ app replicas without exhausting Postgres connections. It's placed between the app and database in `docker-compose.yml`, transparent to the application code (just a different `DATABASE_URL`). The one caveat: disable `LISTEN/NOTIFY` over the PgBouncer connection — if you need real-time notification from Postgres (e.g., invalidating Redis on URL changes), maintain a separate, dedicated direct connection to Postgres outside of PgBouncer.
