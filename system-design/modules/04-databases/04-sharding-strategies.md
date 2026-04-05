# 4.4 — Sharding Strategies

## Concept

Sharding (horizontal partitioning) splits a database table across multiple independent servers (shards). A single Postgres instance can hold ~10TB and serve ~50,000 transactions/sec — for most applications this ceiling is never reached. When it is, sharding is the answer. Understanding sharding tradeoffs upfront shapes database schema decisions from day one.

---

## Deep Dive

### When Sharding Becomes Necessary

```
Single Postgres instance limits (approximate, hardware-dependent):
  Storage:    ~10TB (practical limit before vacuum/bloat issues)
  Writes:     ~5,000 TPS sustained (CPU-bound)
  Connections: 100-500 (beyond this, pgBouncer or sharding)
  
At what scale does ScaleForge need sharding?
  - Assuming ~100 bytes per URL row
  - 10TB = 100 billion rows = 100 billion short URLs
  - Write rate: 1,000 URL creates/sec × 86,400 sec/day = 86M/day
  - Time to exhaust single instance: 100B / 86M ≈ 3 years of URLs
  
  Conclusion: ScaleForge doesn't need sharding for URL storage for years.
  Sharding becomes relevant sooner for click_events:
    10,000 redirects/sec × 86,400 sec = 864M click events/day
    At 50 bytes each = 43GB/day → 1.7TB in 40 days → sharding needed in months
    
  Strategy: Partition click_events by time (Postgres native range partitioning)
            rather than distributed sharding — much simpler.
```

### Sharding Strategies

```
  1. Range Sharding (horizontal range of key values)
     
     Shard 1: user_id 0 – 10,000,000
     Shard 2: user_id 10,000,001 – 20,000,000
     Shard 3: user_id 20,000,001 – ...
     
     Pros: Simple, range queries stay on one shard
     Cons: Hot shards (new users always go to last shard)
           Re-sharding when shard fills up is painful
     
  2. Hash Sharding
     
     shard_id = hash(user_id) % num_shards
     
     Shard 0: hash(user_id) mod 4 = 0
     Shard 1: hash(user_id) mod 4 = 1
     Shard 2: hash(user_id) mod 4 = 2
     Shard 3: hash(user_id) mod 4 = 3
     
     Pros: Even distribution (no hot shards)
     Cons: Range queries touch all shards
           Adding shards requires rehashing ~half the data
     
  3. Consistent Hashing (from Module 3.1)
  
     Shard nodes on a ring; data key hashes to nearest node clockwise.
     Adding a shard: only 1/N of data moves to the new shard.
     Used by: Cassandra, DynamoDB, Redis Cluster
     
  4. Directory Sharding
     
     Lookup table: user_id → shard_id
     
     Pros: Flexible — can rebalance any time
     Cons: Lookup table is a bottleneck and single point of failure
```

### Cross-Shard Query Problem

```
  WITHOUT sharding:
    SELECT u.code, COUNT(c.id) AS clicks
    FROM urls u JOIN click_events c ON c.url_id = u.id
    WHERE u.user_id = $1
    GROUP BY u.code;
    
    → Single query, single result set ✓

  WITH sharding (user on shard 1, click events on shard 3):
    // Cannot JOIN across different database servers
    
    // Application must:
    // 1. Query shard 1: get URL IDs for user
    // 2. Determine which shards have click_events for those URL IDs
    // 3. Query those shards separately
    // 4. Merge results in application code
    
    → Multiple queries, application-side merge ✗ (complex, slower)
    
  This is why sharding should be avoided as long as possible!
  Alternative: Postgres native partitioning (stays on one instance,
  eliminates cross-shard problem, manages growth).
```

### Postgres Native Partitioning (Preferred Over Sharding)

```sql
-- Partition click_events by month (range partitioning)
-- No application changes required — Postgres routes queries automatically

CREATE TABLE click_events (
  id         BIGSERIAL   NOT NULL,
  url_id     UUID        NOT NULL,
  clicked_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  country    VARCHAR(2),
  referrer   TEXT
) PARTITION BY RANGE (clicked_at);

-- Create one partition per month
CREATE TABLE click_events_2024_01
  PARTITION OF click_events
  FOR VALUES FROM ('2024-01-01') TO ('2024-02-01');

CREATE TABLE click_events_2024_02
  PARTITION OF click_events
  FOR VALUES FROM ('2024-02-01') TO ('2024-03-01');

-- PostgreSQL 17+ can auto-create partitions dynamically.
-- Alternatively, use a cron job to add next month's partition.

-- Old partitions can be detached and archived to cold storage:
-- ALTER TABLE click_events DETACH PARTITION click_events_2024_01;
-- pg_dump click_events_2024_01 → S3
-- DROP TABLE click_events_2024_01;
```

---

## Code Examples

### Shard-Aware URL Repository

```typescript
// src/db/shard-router.ts
// Demonstrates the sharding pattern — NOT used in ScaleForge (single instance is fine)
// This shows how you'd implement sharding IF you needed it.

import pg from 'pg';
const { Pool } = pg;

interface ShardConfig {
  id: number;
  connectionString: string;
}

export class ShardedUrlRepository {
  private shards: pg.Pool[];

  constructor(shardConfigs: ShardConfig[]) {
    this.shards = shardConfigs.map((cfg) => new Pool({ connectionString: cfg.connectionString }));
  }

  // Hash-based shard routing
  private getShardForCode(code: string): pg.Pool {
    // Simple hash: sum of char codes mod num shards
    // Production: use FNV-1a or xxHash for better distribution
    let hash = 0;
    for (let i = 0; i < code.length; i++) {
      hash = (hash * 31 + code.charCodeAt(i)) >>> 0; // Unsigned right shift = positive int
    }
    const shardIndex = hash % this.shards.length;
    return this.shards[shardIndex];
  }

  async createUrl(code: string, targetUrl: string): Promise<void> {
    const shard = this.getShardForCode(code);
    await shard.query(
      'INSERT INTO urls (code, target_url) VALUES ($1, $2)',
      [code, targetUrl]
    );
  }

  async resolveCode(code: string): Promise<string | null> {
    const shard = this.getShardForCode(code);
    const result = await shard.query<{ target_url: string }>(
      'SELECT target_url FROM urls WHERE code = $1',
      [code]
    );
    return result.rows[0]?.target_url ?? null;
  }

  // Cross-shard query: must fan out to ALL shards
  async countAllUrls(): Promise<number> {
    const counts = await Promise.all(
      this.shards.map(async (shard) => {
        const result = await shard.query<{ count: string }>('SELECT COUNT(*) FROM urls');
        return parseInt(result.rows[0].count, 10);
      })
    );
    return counts.reduce((sum, c) => sum + c, 0);
  }
}
```

---

## Try It Yourself

**Exercise:** Implement automatic monthly partition creation.

```typescript
// partition-manager.exercise.ts

// TODO:
// 1. Write createNextMonthPartition(pool: Pool): Promise<void>
//    - Compute next month's start and end date (use Date arithmetic)
//    - Generate and execute:
//      CREATE TABLE IF NOT EXISTS click_events_YYYY_MM
//        PARTITION OF click_events
//        FOR VALUES FROM ('YYYY-MM-01') TO ('YYYY-MM+1-01')
//    - Log "Created partition: click_events_YYYY_MM"
//
// 2. Schedule it to run on the 25th of every month
//    (so the next month's partition is always ready)
//    Hint: Use node-cron: cron.schedule('0 0 25 * *', ...)
//
// 3. Write a function listPartitions(pool: Pool): Promise<string[]>
//    to verify partitions exist:
//    SELECT tablename FROM pg_tables
//    WHERE tablename LIKE 'click_events_%'
//    ORDER BY tablename;
```

<details>
<summary>Show solution</summary>

```typescript
import { pool } from './db/pool.js';
import cron from 'node-cron';
import pino from 'pino';

const log = pino({ name: 'partition-manager' });

function nextMonthBounds(): { start: string; end: string; name: string } {
  const now = new Date();
  const nextMonth = new Date(now.getFullYear(), now.getMonth() + 1, 1);
  const monthAfter = new Date(now.getFullYear(), now.getMonth() + 2, 1);

  const fmt = (d: Date) => d.toISOString().slice(0, 10); // 'YYYY-MM-DD'
  const tableSuffix = nextMonth.toISOString().slice(0, 7).replace('-', '_'); // 'YYYY_MM'

  return {
    start: fmt(nextMonth),
    end:   fmt(monthAfter),
    name:  `click_events_${tableSuffix}`,
  };
}

export async function createNextMonthPartition(): Promise<void> {
  const { start, end, name } = nextMonthBounds();

  await pool.query(`
    CREATE TABLE IF NOT EXISTS ${name}
      PARTITION OF click_events
      FOR VALUES FROM ($1) TO ($2)
  `, [start, end]);

  log.info({ partition: name, start, end }, 'Created partition');
}

export async function listPartitions(): Promise<string[]> {
  const result = await pool.query<{ tablename: string }>(
    `SELECT tablename FROM pg_tables
     WHERE tablename LIKE 'click_events_%'
     ORDER BY tablename`
  );
  return result.rows.map((r) => r.tablename);
}

// Schedule: run on the 25th of each month at midnight
cron.schedule('0 0 25 * *', () => {
  createNextMonthPartition().catch((err) => log.error(err, 'Failed to create partition'));
});
```

</details>

---

## Capstone Connection

ScaleForge doesn't need distributed sharding — Postgres native range partitioning on `click_events` handles the high write rate while keeping all data on one instance (no cross-shard complexity). The partition manager runs as a scheduled job in the BullMQ worker process (introduced in Module 06). In Module 11 (Capstone Integration), you'll wire the partition creation into the FlowForge deployment pipeline as a DB migration step.
