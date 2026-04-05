# 11.1 — ScaleForge: Full Source Walkthrough

## Overview

ScaleForge is the URL-shortener capstone. This file annotates the complete request lifecycle for the two most important operations — **redirect** and **URL creation** — pointing to every pattern applied along the way.

---

## Project Structure

```
scaleforge/
├── src/
│   ├── app.ts                    — Express setup, middleware chain
│   ├── server.ts                 — HTTP server + graceful shutdown
│   ├── config.ts                 — Environment variable parsing (Zod)
│   ├── db/
│   │   └── pool.ts               — redirectPool, writePool, adminPool (Modules 08.2, 09.3)
│   ├── cache/
│   │   ├── redis.ts              — ioredis with commandTimeout (Modules 05, 09.4)
│   │   └── hot-cache.ts          — In-memory top-1000 cache (Module 10.3)
│   ├── resilience/
│   │   ├── circuit-breaker.ts    — CircuitBreaker class (Module 09.1)
│   │   ├── retry.ts              — withRetry + full jitter (Module 09.2)
│   │   ├── bulkhead.ts           — Semaphore, bulkhead middleware (Module 09.3)
│   │   ├── timeout.ts            — combinedSignal, timeoutSignal (Module 09.4)
│   │   └── rate-limiter.ts       — RateLimiter, sliding window Lua (Module 09.5)
│   ├── clients/
│   │   └── flowforge.client.ts   — FlowForge HTTP client (Modules 07.2, 09.1–9.4)
│   ├── middleware/
│   │   ├── deadline.middleware.ts — X-Request-Deadline header (Module 09.4)
│   │   ├── rate-limit.middleware.ts — per-user 429 (Module 09.5)
│   │   ├── auth.middleware.ts     — JWT validation → res.locals.userId
│   │   └── metrics.middleware.ts  — prom-client timing + count (Module 08.4)
│   ├── routes/
│   │   ├── redirect.route.ts     — GET /r/:code, 4-tier fallback (Module 10.3)
│   │   ├── url.routes.ts         — POST/GET /api/v1/urls (Modules 04, 07.5)
│   │   ├── health.routes.ts      — /live, /ready (Module 07.4)
│   │   └── metrics.routes.ts     — GET /metrics
│   └── observability/
│       ├── logger.ts             — pino structured logger (Module 07.6)
│       └── metrics.ts            — all prom-client definitions (Module 08.4)
├── docker-compose.yml            — full local stack
└── prometheus/
    ├── prometheus.yml
    └── slo-alerts.yml
```

---

## Middleware Chain (in `app.ts`)

```typescript
// src/app.ts
// Middleware runs top to bottom. Order matters.

import express from 'express';
import { correlationIdMiddleware } from './middleware/correlation-id.middleware.js';
import { deadlineMiddleware }      from './middleware/deadline.middleware.js';
import { metricsMiddleware }       from './middleware/metrics.middleware.js';
import { authMiddleware }          from './middleware/auth.middleware.js';
import { redirectRouter }          from './routes/redirect.routes.js';
import { urlRouter }               from './routes/url.routes.js';
import { healthRouter }            from './routes/health.routes.js';
import { metricsRouter }           from './routes/metrics.routes.js';

export const app = express();

// 1. Correlation ID — assign or propagate X-Correlation-ID (Module 07.2)
app.use(correlationIdMiddleware);

// 2. Deadline — read X-Request-Deadline, create AbortSignal (Module 09.4)
app.use(deadlineMiddleware);

// 3. Metrics — record latency + request count for all routes (Module 08.4)
app.use(metricsMiddleware);

// 4. Body parsing (only for API routes — not for redirects)
app.use('/api', express.json({ limit: '100kb' }));

// 5. Health check routes — no auth needed
app.use('/health', healthRouter);

// 6. Redirect route — no auth (public)
//    Bulkhead middleware applied inside the router (Module 09.3)
app.use('/r', redirectRouter);

// 7. Auth — all /api routes require a valid JWT
app.use('/api', authMiddleware);

// 8. Per-user rate limit on URL creation specifically
//    Applied at route level in url.routes.ts

// 9. API routes
app.use('/api/v1/urls', urlRouter);

// 10. Prometheus metrics endpoint — no auth, scrape-only
app.use('/metrics', metricsRouter);
```

---

## Request Lifecycle: GET /r/:code

```
  Browser clicks https://scl.ge/abc123
       │
       ▼
  Nginx (rate limit check: IP-based, 100 req/s)
       │
       ▼
  Express: correlationId → deadline → metricsMiddleware
       │
       ▼
  redirectRouter → redirectHandler
       │
       ├─ [Tier 0] redis.get('url:abc123')
       │       Hit → res.redirect(302, longUrl)    ← ~1ms
       │       Miss (or Redis error) → fall through
       │
       ├─ [Tier 1] redirectPool.query(...)
       │       Row found → redis.setex(...) [best effort] → res.redirect(302, longUrl)  ← ~8ms
       │       Row not found OR pool error → fall through
       │
       ├─ [Tier 2] readReplicaPool.query(...)
       │       Row found → res.redirect(302, longUrl)    ← ~15ms
       │       Row not found OR replica error → fall through
       │
       ├─ [Tier 3] inMemoryHotCache.get('abc123')
       │       Hit → res.redirect(302, longUrl)    ← <0.1ms
       │       Miss → fall through
       │
       └─ All tiers failed → res.status(503)...  HTML degraded page

  After response:
    metrics.middleware → httpRequestsTotal.inc(), httpRequestDurationSeconds.observe()
    pino logger → structured access log with correlationId, tier, duration
```

---

## Request Lifecycle: POST /api/v1/urls

```
  POST /api/v1/urls
  { "longUrl": "https://example.com/long-path", "customCode": "mylink" }
       │
       ▼
  Nginx → rate limit (IP + user zone)
       │
       ▼
  Express: correlationId → deadline → metrics → body parse → auth
       │
       ▼
  urlCreationRateLimit middleware
  → redis ZREMRANGEBYSCORE + ZADD atomically (Module 09.5)
  → if count > 100: return 429 with Retry-After
       │
       ▼
  urlRouter POST handler
       │
       ├─ Validate body with Zod schema
       ├─ Check conflicts: SELECT WHERE short_code = $1
       │
       ├─ writePool.query INSERT INTO urls ...
       │
       ├─ redis.setex(url:<code>, 3600, longUrl)  [best-effort, no throw]
       │
       ├─ flowforgeClient.enqueueNotification(job, res.locals.signal)
       │       │
       │       └─ bulkhead.run(             [Module 09.3 — concurrency limit]
       │               breaker.call(        [Module 09.1 — skip if circuit open]
       │                 withRetry(         [Module 09.2 — retry on 5xx]
       │                   withTimeout(     [Module 09.4 — 5s per attempt]
       │                     doFetch(job, signal)
       │                   )
       │                 )
       │               )
       │             )
       │
       ├─ On FlowForge success: return 201 { shortCode, shortUrl }
       │
       └─ On FlowForge failure (all retries + circuit open):
               compensateUrlInsert(shortCode)   [Module 07.5 — saga]
               return 503 with Retry-After: 30
```

---

## Key Configuration Values

```typescript
// src/config.ts — validated at startup (Zod)
// If any required env var is missing or wrong type, the process exits immediately.

import { z } from 'zod';

const ConfigSchema = z.object({
  PORT:                    z.coerce.number().default(3000),
  DATABASE_URL:            z.string().url(),
  DATABASE_READ_URL:       z.string().url(),
  REDIS_URL:               z.string().url(),
  FLOWFORGE_URL:           z.string().url(),
  JWT_SECRET:              z.string().min(32),
  APP_BASE_URL:            z.string().url(),  // used to build shortUrl in response

  // Pool sizes
  DB_REDIRECT_POOL_MAX:    z.coerce.number().default(8),
  DB_WRITE_POOL_MAX:       z.coerce.number().default(5),
  DB_ADMIN_POOL_MAX:       z.coerce.number().default(2),

  // Circuit breaker
  FLOWFORGE_TIMEOUT_MS:    z.coerce.number().default(5000),
  CB_FAILURE_THRESHOLD:    z.coerce.number().default(5),
  CB_RESET_TIMEOUT_MS:     z.coerce.number().default(30_000),

  // Rate limiting
  URL_CREATION_LIMIT:      z.coerce.number().default(100),
  URL_CREATION_WINDOW_MS:  z.coerce.number().default(3_600_000),

  // Observability
  LOG_LEVEL:               z.enum(['trace', 'debug', 'info', 'warn', 'error']).default('info'),
  NODE_ENV:                z.enum(['development', 'test', 'production']).default('development'),
});

export type Config = z.infer<typeof ConfigSchema>;
export const config: Config = ConfigSchema.parse(process.env);
```

---

## Graceful Shutdown

```typescript
// src/server.ts
// On SIGTERM (Kubernetes scale-down, container stop), we:
// 1. Stop accepting new connections
// 2. Let in-flight requests finish (up to 10s)
// 3. Close DB pools + Redis connection
// 4. Exit

const server = app.listen(config.PORT, () => {
  logger.info({ port: config.PORT }, 'ScaleForge listening');
});

async function shutdown(signal: string): Promise<void> {
  logger.info({ signal }, 'Shutting down...');

  // Stop accepting new connections
  server.close(async () => {
    // Close pools — flushes any pending queries
    await Promise.allSettled([
      redirectPool.end(),
      writePool.end(),
      adminPool.end(),
      redis.quit(),
    ]);
    logger.info('Shutdown complete');
    process.exit(0);
  });

  // Force exit if graceful shutdown takes too long
  setTimeout(() => {
    logger.error('Shutdown timeout — forcing exit');
    process.exit(1);
  }, 10_000);
}

process.on('SIGTERM', () => void shutdown('SIGTERM'));
process.on('SIGINT',  () => void shutdown('SIGINT'));
```

---

## Capstone Connection

This walkthrough shows ScaleForge as the integration point for every module in the course. The redirect handler alone applies 5 independent patterns (caching, connection pooling, read replica routing, in-memory fallback, structured logging + metrics). The URL creation handler adds 6 more (validation, rate limiting, distributed saga, circuit breaker, retry, deadline propagation). Together they represent a system that degrades gracefully rather than failing catastrophically — and that can be observed, measured, and chaos-tested to prove it.
