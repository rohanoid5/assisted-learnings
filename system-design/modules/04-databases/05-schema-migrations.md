# 4.5 — Schema Migrations

## Concept

A schema migration is a version-controlled, repeatable change to a database schema. Without migrations, schema changes are applied by hand, are not reproducible, and can't be rolled back. With migrations, the database schema evolves in lockstep with application code — every developer, CI environment, and production deployment runs the same changes in the same order.

---

## Deep Dive

### The Migration Lifecycle

```
  Developer        Git              CI                Production
  ─────────        ───              ──                ──────────
  Creates          Commits          Validates         Applies
  migration file   migration        migration is      migration
                   with app code    valid SQL         before app
                                                      deployment
                   ↓                ↓                 ↓
                   Both DB &        Tests run         New app code
                   app code in      against           can use new
                   same commit      migrated DB       schema
```

### Zero-Downtime Migrations

```
DANGEROUS approach (causes downtime):
  1. Stop old app
  2. Run ALTER TABLE  ← table locked, no reads/writes
  3. Start new app
  
  Problem: ALTER TABLE locks the table. On a 100M row table, RENAME COLUMN
  can take 30+ minutes. Zero traffic during that time.

SAFE approach (additive, non-breaking migrations):
  
  Phase 1 (deploy first): Add new column nullable
    ALTER TABLE urls ADD COLUMN short_title TEXT;
    (Adds column immediately, no lock, existing rows get NULL)
    
  Phase 2 (background job): Backfill existing rows
    UPDATE urls SET short_title = substr(target_url, 0, 100)
    WHERE short_title IS NULL LIMIT 1000;  -- in batches, not all at once!
    
  Phase 3 (next deploy): Make column NOT NULL if needed
    ALTER TABLE urls 
    ALTER COLUMN short_title SET NOT NULL,
    ALTER COLUMN short_title SET DEFAULT '';
    
Rule: Never do in one migration what Blue/Green deploy can do safely across two deploys.
```

### Dangerous Operations and Safe Alternatives

```
DANGEROUS                              SAFE ALTERNATIVE
───────────────────────────────────    ──────────────────────────────────────────
ALTER TABLE ... ADD COLUMN NOT NULL    Add column as nullable first, then backfill,
                                       then set NOT NULL (3 separate deploys)
                                       
ALTER TABLE ... RENAME COLUMN name    Add new column (short_title), migrate reads
  → short_title                       to check both columns, backfill, then drop old
  
DROP COLUMN                           Set column to nullable, stop writing, then drop
DROP TABLE                            Rename first, keep for 30 days, then drop

ADD INDEX                             CREATE INDEX CONCURRENTLY (builds without table lock)
ADD UNIQUE CONSTRAINT                 CREATE UNIQUE INDEX CONCURRENTLY, then add constraint
                                       
UPDATE entire table at once           Batch update: WHERE id > $last AND id < $next LIMIT 1000

TRUNCATE                              (always dangerous — confirm with human)
```

### Migration Tools

```
Tool           Language    Approach        Notable for
─────────────  ──────────  ──────────────  ────────────────────────────────────
Flyway         Java/SQL    Versioned SQL   Widely used in enterprise, simple
Liquibase      Java/XML    XML or SQL      Rollback support, diff generation
Prisma         TypeScript  Schema-driven   Generates SQL from schema.prisma
node-pg-migrate Node.js   JS/SQL          Lightweight, SQL or JS migration files
golang-migrate  Go         SQL files only  Language-agnostic, single binary

ScaleForge uses node-pg-migrate (SQL files, version controlled)
```

---

## Code Examples

### Migration Files with node-pg-migrate

```sql
-- migrations/001_initial_schema.sql

-- Users table (auth)
CREATE TABLE IF NOT EXISTS users (
  id         UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
  email      VARCHAR(255) NOT NULL UNIQUE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Short URLs
CREATE TABLE IF NOT EXISTS urls (
  id         UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
  code       VARCHAR(12) NOT NULL,
  target_url TEXT        NOT NULL,
  user_id    UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  deleted_at TIMESTAMPTZ
);

CREATE UNIQUE INDEX CONCURRENTLY urls_code_idx
  ON urls (code)
  WHERE deleted_at IS NULL;

CREATE INDEX CONCURRENTLY urls_user_created_idx
  ON urls (user_id, created_at DESC)
  WHERE deleted_at IS NULL;

-- Click events (partitioned by month)
CREATE TABLE IF NOT EXISTS click_events (
  id         BIGSERIAL   NOT NULL,
  url_id     UUID        NOT NULL REFERENCES urls(id) ON DELETE CASCADE,
  clicked_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  country    VARCHAR(2),
  referrer   TEXT
) PARTITION BY RANGE (clicked_at);

-- First partition (created manually; subsequent partitions via scheduler)
CREATE TABLE click_events_2024_01
  PARTITION OF click_events
  FOR VALUES FROM ('2024-01-01') TO ('2024-02-01');
```

```sql
-- migrations/002_add_custom_alias.sql
-- Additive-only: adds a nullable column, safe for zero-downtime deploy

ALTER TABLE urls ADD COLUMN IF NOT EXISTS alias VARCHAR(255);

-- Index on alias for lookup (CONCURRENTLY = no table lock)
CREATE UNIQUE INDEX CONCURRENTLY IF NOT EXISTS urls_alias_idx
  ON urls (alias)
  WHERE alias IS NOT NULL AND deleted_at IS NULL;
```

### Running Migrations in TypeScript

```typescript
// src/db/migrate.ts — run migrations on startup or as a standalone script

import { run } from 'node-pg-migrate';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import pino from 'pino';

const log = pino({ name: 'migrate' });
const __dirname = path.dirname(fileURLToPath(import.meta.url));

export async function runMigrations(): Promise<void> {
  try {
    await run({
      databaseUrl:     process.env.DATABASE_PRIMARY_URL!,
      migrationsTable: 'pgmigrations',
      dir:             path.join(__dirname, '../../migrations'),
      direction:       'up',
      count:           Infinity,      // Apply all pending migrations
      verbose:         false,
      noLock:          false,         // Use advisory lock (prevents concurrent migration runs)
    });
    log.info('Database migrations complete');
  } catch (err) {
    log.error(err, 'Migration failed');
    throw err;
  }
}

// Run on app startup (before accepting traffic)
// This is safe because of the advisory lock — only one instance runs migrations
// even when multiple app replicas start simultaneously.
```

```typescript
// src/server.ts — migrations before server starts
import { runMigrations } from './db/migrate.js';

async function start() {
  await runMigrations(); // Run migrations first — DB schema matches code
  
  const { app } = await import('./app.js');
  const port = parseInt(process.env.PORT ?? '3001', 10);
  
  const server = app.listen(port, () => {
    log.info({ port }, 'Server started');
  });

  return server;
}

start().catch((err) => {
  console.error('Failed to start server:', err);
  process.exit(1);
});
```

### Verifying Migration was Applied

```typescript
// src/db/migration-status.ts

import { primaryPool } from './pool.js';

interface MigrationRecord {
  id: number;
  name: string;
  run_on: Date;
}

export async function listAppliedMigrations(): Promise<MigrationRecord[]> {
  const result = await primaryPool.query<MigrationRecord>(
    'SELECT id, name, run_on FROM pgmigrations ORDER BY run_on'
  );
  return result.rows;
}
```

---

## Try It Yourself

**Exercise:** Add a custom URL alias feature via a safe zero-downtime migration.

```sql
-- TODO:
-- 1. Write migration 003_add_url_title.sql
--    Add a nullable `title` column (VARCHAR(255)) to the urls table.
--    Do NOT add NOT NULL — safe for deploy while old code is running.
--
-- 2. Write a backfill script that sets title = domain(target_url)
--    for all existing URLs, 500 rows at a time.
--    (Hint: regexp_replace(target_url, '^https?://([^/]+).*', '\1') extracts domain in SQL)
--
-- 3. Write a CHECK to ensure title length is between 1 and 255 if provided:
--    ALTER TABLE urls ADD CONSTRAINT title_length_check
--    CHECK (title IS NULL OR char_length(title) BETWEEN 1 AND 255);
--
-- 4. Verify the migration with listAppliedMigrations() — confirm name appears
```

<details>
<summary>Show solution</summary>

```sql
-- migrations/003_add_url_title.sql

-- Step 1: Add nullable column (immediate, no lock)
ALTER TABLE urls ADD COLUMN IF NOT EXISTS title VARCHAR(255);

-- Step 2: Backfill existing rows with extracted domain (done in batches in production,
--         but for dev/test a single UPDATE is fine on small datasets)
UPDATE urls
SET title = regexp_replace(target_url, '^https?://([^/]+).*', '\1')
WHERE title IS NULL;

-- Step 3: Add length check constraint
ALTER TABLE urls ADD CONSTRAINT url_title_length_check
  CHECK (title IS NULL OR char_length(title) BETWEEN 1 AND 255);
```

```typescript
// Verify
import { listAppliedMigrations } from './db/migration-status.js';

const applied = await listAppliedMigrations();
console.table(applied);
// Expected output includes rows for 001_, 002_, 003_
```

</details>

---

## Capstone Connection

ScaleForge uses `runMigrations()` at startup. An advisory lock (`noLock: false`) ensures that when all 3 app replicas start simultaneously, only one runs migrations — the others wait and then proceed once the schema is ready. This is the standard "run-on-startup" pattern for containerized services. The alternative (a dedicated migration job as a Kubernetes init container) is shown in Module 11's Kubernetes manifests.
