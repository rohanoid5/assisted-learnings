# 10.3 — Graceful Degradation

## Concept

Graceful degradation is the practice of designing systems with tiered fallbacks, so that when one component fails, the system continues to serve a reduced (but not zero) level of functionality. Instead of all-or-nothing, you get a spectrum from "everything works perfectly" down to "basic functionality only, best effort." Users experience degraded performance rather than a hard error.

---

## Deep Dive

### The Degradation Hierarchy

Systems typically have three or four tiers. Each tier activates only when the tier above it fails.

```
  Tier 0: FULL SERVICE
  ─────────────────────
  All systems operational.
  Redirects served from Redis cache (<1ms).
  Analytics writes to Postgres synchronously.
  FlowForge notifications enqueued immediately.
  
  ↓  Redis becomes unavailable
  
  Tier 1: DEGRADED — CACHE MISS FALLBACK
  ──────────────────────────────────────────
  Redirects served from Postgres database (5–10ms).
  Analytics still writes synchronously.
  Cache misses logged as metric for alerting.
  Users notice redirect is slightly slower.
  
  ↓  Postgres primary becomes unavailable
  
  Tier 2: DEGRADED — READ REPLICA + ASYNC WRITES
  ─────────────────────────────────────────────────
  Redirects served from Postgres read replica (10–20ms).
  Analytics writes queued in BullMQ for later processing.
  URL creation returns 503 with Retry-After.
  Users notice redirect works, creation is unavailable.
  
  ↓  All Postgres unavailable AND Redis down
  
  Tier 3: MINIMAL — STATIC FALLBACK
  ────────────────────────────────────
  Pre-cached lookup table in process memory (loaded at startup).
  Only covers top-1000 most popular URLs.
  Unknown codes return a static "service degraded" page.
  All writes queued locally (bounded buffer), persisted on recovery.
  Users can still resolve popular links.
```

### Explicit vs. Implicit Degradation

```
  Implicit (bad):
    catch (err) {
      return res.status(503).json({ error: 'Database unavailable' });
    }
    → User sees an error. No fallback attempted.
  
  Explicit with tier (good):
    catch (cacheErr) {
      logger.warn({ err: cacheErr }, 'Cache miss — falling back to DB');
      fallbackToDatabase();
    }
    → User sees a slightly slower response. Error is invisible.
```

### What to Pre-load at Startup

```
  Not every piece of data can be fallback-served.
  Choose data that is:
  
  1. High-read, low-write (URL → redirect target is immutable after creation)
  2. Small enough to fit in memory (~100MB is fine)
  3. Acceptable to be slightly stale (minutes, not milliseconds)
  
  ScaleForge pre-caches top-1000 URLs by click count:
    Key:   short code  (e.g. "abc123")
    Value: long URL    (e.g. "https://example.com/...")
    Size:  ~100 bytes × 1000 = 100KB — negligible
```

---

## Code Examples

### Three-Tier Redirect Handler

```typescript
// src/routes/redirect.route.ts
// Implements Tier 0 → Tier 1 → Tier 2 automatic fallback.

import { redis } from '../cache/redis.js';
import { redirectPool, readReplicaPool } from '../db/pool.js';
import { inMemoryHotCache } from '../cache/hot-cache.js';
import type { Request, Response } from 'express';
import { logger } from '../observability/logger.js';
import {
  cacheHitsCounter,
  cacheMissesCounter,
  redirectTier,  // gauge: 0=cache, 1=db, 2=replica, 3=inmem
} from '../observability/metrics.js';

export async function redirectHandler(req: Request, res: Response): Promise<void> {
  const { code } = req.params as { code: string };

  // Tier 0: Redis cache (hot path)
  try {
    const cached = await redis.get(`url:${code}`);
    if (cached) {
      cacheHitsCounter.inc();
      redirectTier.set(0);
      res.redirect(302, cached);
      return;
    }
    cacheMissesCounter.inc();
  } catch (cacheErr) {
    logger.warn({ err: cacheErr, code }, 'Redis unavailable, falling back to DB');
  }

  // Tier 1: Primary DB
  try {
    const result = await redirectPool.query<{ long_url: string }>(
      'SELECT long_url FROM urls WHERE short_code = $1 AND is_active = true',
      [code],
    );



    if (result.rows.length > 0) {
      const { long_url } = result.rows[0]!;

      // Best-effort cache repopulation — don't fail if Redis is still down
      redis.setex(`url:${code}`, 3600, long_url).catch(() => {});

      redirectTier.set(1);
      res.redirect(302, long_url);
      return;
    }
  } catch (dbErr) {
    logger.error({ err: dbErr, code }, 'Primary DB unavailable, falling back to replica');
  }

  // Tier 2: Read replica (only reads, so this is always accessible if replica data is recent)
  try {
    const result = await readReplicaPool.query<{ long_url: string }>(
      'SELECT long_url FROM urls WHERE short_code = $1 AND is_active = true',
      [code],
    );

    if (result.rows.length > 0) {
      redirectTier.set(2);
      res.redirect(302, result.rows[0]!.long_url);
      return;
    }
  } catch (replicaErr) {
    logger.error({ err: replicaErr, code }, 'Read replica unavailable, falling back to in-memory cache');
  }

  // Tier 3: In-memory hot cache (pre-loaded at startup for top-1000 URLs)
  const hotUrl = inMemoryHotCache.get(code);
  if (hotUrl) {
    redirectTier.set(3);
    res.redirect(302, hotUrl);
    return;
  }

  // All tiers exhausted — return a user-friendly degraded page
  res.status(503).send(`
    <!doctype html>
    <html>
      <head><title>ScaleForge — Service Degraded</title></head>
      <body>
        <h1>We're experiencing temporary issues 🔧</h1>
        <p>We couldn't resolve this link right now. Please try again in a few minutes.</p>
        <p>Link code: <code>${code}</code></p>
      </body>
    </html>
  `);
}
```

### In-Memory Hot Cache Setup

```typescript
// src/cache/hot-cache.ts
// Pre-loads the top-1000 most-clicked URLs into process memory at startup.
// Refreshed every 5 minutes so it stays roughly current.
// This is Tier 3 — only activates when all other data stores are unavailable.

import { redirectPool } from '../db/pool.js';
import { logger } from '../observability/logger.js';

const HOT_CACHE_SIZE = 1000;
const REFRESH_INTERVAL_MS = 5 * 60 * 1000;

export const inMemoryHotCache = new Map<string, string>();

export async function loadHotCache(): Promise<void> {
  try {
    const result = await redirectPool.query<{ short_code: string; long_url: string }>(`
      SELECT short_code, long_url
      FROM urls
      WHERE is_active = true
      ORDER BY click_count DESC
      LIMIT $1
    `, [HOT_CACHE_SIZE]);

    inMemoryHotCache.clear();
    for (const { short_code, long_url } of result.rows) {
      inMemoryHotCache.set(short_code, long_url);
    }

    logger.info({ count: result.rows.length }, 'Hot cache refreshed');
  } catch (err) {
    // Don't crash startup if this fails — hot cache is best-effort
    logger.warn({ err }, 'Failed to refresh hot cache — continuing with stale data');
  }
}

// Call on app startup + every 5 minutes
export function startHotCacheRefresh(): void {
  void loadHotCache();
  setInterval(() => void loadHotCache(), REFRESH_INTERVAL_MS);
}
```

### Monitoring Tier Usage

```typescript
// src/observability/metrics.ts — degradation gauge
import { Gauge } from 'prom-client';

// Tracks which tier most recent redirects used.
// Alert when tier > 0 for more than 1 minute (means cache is degraded).
export const redirectTier = new Gauge({
  name: 'scaleforge_redirect_tier',
  help: '0=cache, 1=primary DB, 2=replica, 3=in-memory. Higher = more degraded.',
});
```

```yaml
# prometheus/slo-alerts.yml — degradation alert
- alert: ScaleForgeRunningDegraded
  expr: scaleforge_redirect_tier > 0
  for: 1m
  labels:
    severity: ticket
  annotations:
    summary: "ScaleForge redirects running in degraded mode (tier {{ $value }})"
```

---

## Try It Yourself

**Exercise:** Simulate each degradation tier manually.

```bash
# Tier 0 → 1: Kill Redis
docker compose stop redis
# Expected: redirects still work, latency increases from <1ms to ~5ms
# Check: scaleforge_redirect_tier gauge changes to 1

# Tier 1 → 2: Kill primary DB (leave replica running)
docker compose stop postgres-primary
# Expected: redirects still work, URL creation returns 503
# Check: scaleforge_redirect_tier gauge changes to 2

# Tier 2 → 3: Kill read replica too
docker compose stop postgres-replica
# Expected: top-1000 URLs still redirect, unknown codes get degraded page
# Check: scaleforge_redirect_tier gauge changes to 3

# TODO: Test hot cache reload:
#   1. Stop all data stores
#   2. Query a URL that IS in the top-1000 → expect 302
#   3. Query a URL that is NOT in the top-1000 → expect 503 + degraded HTML
```

<details>
<summary>Show expected metrics during the simulation</summary>

```
Tier 0 (baseline):
  scaleforge_redirect_tier = 0
  http_request_duration_seconds{quantile="0.99"} ≈ 0.001 (1ms)

Tier 1 (no Redis):
  scaleforge_redirect_tier = 1
  http_request_duration_seconds{quantile="0.99"} ≈ 0.008 (8ms)
  ScaleForgeRunningDegraded alert fires after 1 minute

Tier 2 (no primary, replica active):
  scaleforge_redirect_tier = 2
  http_request_duration_seconds{quantile="0.99"} ≈ 0.015 (15ms)

Tier 3 (all external stores down):
  scaleforge_redirect_tier = 3
  http_request_duration_seconds{quantile="0.99"} ≈ 0.0001 (<0.1ms — it's in-memory!)
  But: new URLs can't be resolved, creation returns 503
```

</details>

---

## Capstone Connection

Graceful degradation is what separates ScaleForge from a toy URL shortener. The in-memory hot cache at Tier 3 means that even during a full data-store outage, the most critical 1000 links (think: your company's main website, a just-launched marketing campaign) continue to resolve. The degradation metric (`scaleforge_redirect_tier`) translates the on-call engineer's instinct — "something feels slow" — into an exact diagnosis: "we're on Tier 2, primary DB is down, replica is serving reads."
