# 8.5 — Connection Pooling

## Concept

Every database query uses a connection from a pool. Too few connections → requests queue. Too many → the database is overwhelmed. Getting pool sizing right is critical for production performance. This topic explains Prisma's pooling, PgBouncer, and how to size the pool for your workload.

---

## Deep Dive

### How Prisma Manages Connections

Prisma uses a connection pool managed by the Rust query engine:

```typescript
const db = new PrismaClient({
  datasources: {
    db: {
      url: process.env.DATABASE_URL,
    },
  },
  // Connection limit is set in the URL:
  // postgresql://user:pass@host:5432/db?connection_limit=10&pool_timeout=10
});
```

Connection pool parameters in the URL:
| Param | Default | Meaning |
|-------|---------|---------|
| `connection_limit` | `num_cpus * 2 + 1` | Max concurrent connections |
| `pool_timeout` | `10` | Seconds to wait for a connection before error |
| `connect_timeout` | `5` | Seconds to wait for initial connection |

### Sizing the Pool

```
Rule of thumb:
  DB connection limit ÷ number of app instances = pool size per instance

PostgreSQL default max_connections = 100
With 4 app instances:
  pool_size = (100 - 5 reserved) / 4 ≈ 23 per instance

For transactional workloads: smaller pools work better
For read-heavy workloads: slightly larger pools
```

### PgBouncer (Essential for Serverless / High Concurrency)

PostgreSQL connections are expensive (each is a forked process). PgBouncer multiplexes many app connections onto fewer DB connections:

```
App instances (100s of connections)
     ↓
PgBouncer (transaction mode)
     ↓
PostgreSQL (10-20 actual connections)
```

```
# PgBouncer connection string
DATABASE_URL="postgresql://user:pass@pgbouncer-host:6432/db?pgbouncer=true"
# Note: ?pgbouncer=true disables features incompatible with PgBouncer (prepared statements)
```

### Monitoring Pool Health

```typescript
db.$on('query', (e) => {
  // High pool_timeout errors → pool too small
  // High average duration → queries too slow
  if (e.duration > 1000) {
    logger.warn('Slow query', { sql: e.query, duration: e.duration });
  }
});
```

---

## Try It Yourself

**Exercise:** Calculate the correct `connection_limit` for this scenario:
- PostgreSQL server: `max_connections = 100`, 10 reserved for admin
- App instances: 3
- Worker threads per instance: 4 (each may hold a connection during job execution)

What should `connection_limit` be?

<details>
<summary>Show solution</summary>

```
Available connections = 100 - 10 = 90
Per instance = 90 / 3 = 30

However, with 4 worker threads that may each hold a connection during execution,
you want at least pool_size ≥ worker_count + buffer.

Recommendation: connection_limit=25
(25 × 3 = 75 — leaves 15 for migrations, admin, other tools)
```

In the `.env`:
```
DATABASE_URL=postgresql://user:pass@localhost:5432/pipeforge?connection_limit=25&pool_timeout=15
```

</details>

---

## Capstone Connection

PipeForge's `WORKER_POOL_SIZE` env variable (default 4) directly affects how many concurrent jobs run. Since each job may execute DB queries, the pool size must be `≥ WORKER_POOL_SIZE + buffer`. The `src/config/env.ts` Zod schema validates that `WORKER_POOL_SIZE ≤ connection_limit - 5` to catch misconfiguration at startup.
