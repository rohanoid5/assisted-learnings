# Module 2 — Exercises

## Overview

These exercises complete Module 2 and produce the ScaleForge networking foundation — proper HTTP semantics, connection pooling configuration, and security headers. By the end, your ScaleForge app will behave correctly in production networking conditions.

---

## Exercise 1 — Implement the Redirect Endpoint with Correct HTTP Semantics

**Goal:** Build the complete redirect endpoint following all HTTP conventions from this module.

Complete the redirect router in `src/routes/redirect.router.ts` so that:

1. `GET /:code` returns `302 Found` with correct `Location` header
2. Response includes `Cache-Control: no-store` and `X-Request-Id`
3. Client IP is extracted from `X-Forwarded-For` (behind Nginx)
4. Click is enqueued asynchronously (non-blocking)
5. Returns `404` for unknown codes, `400` for invalid code format

**Test it:**

```bash
cd capstone/scaleforge
docker compose up -d

# Create a test URL directly in DB
docker compose exec postgres psql -U scaleforge -c \
  "INSERT INTO short_urls (code, long_url, user_id) VALUES ('test01', 'https://example.com', 'user-1');"

# Test the redirect
curl -v http://localhost:3001/test01

# Verify:
# < HTTP/1.1 302 Found
# < Location: https://example.com
# < Cache-Control: no-store
# < X-Request-Id: <uuid>
```

---

## Exercise 2 — Configure and Benchmark Connection Pool

**Goal:** Find the optimal PostgreSQL pool size for ScaleForge's analytics workload.

```typescript
// experiments/pool-optimization.ts
// Run queries simulating ScaleForge's click INSERT workload
// at different pool sizes, measure throughput and p99 latency

import pg from 'pg';
import { LatencyHistogram } from '../src/telemetry/latency.js';

const DB_URL = process.env.DATABASE_URL ?? 'postgresql://localhost/scaleforge_test';
const TARGET_QPS = 1157;         // clicks/sec at average load
const SIMULATION_SECONDS = 10;

async function benchmarkPool(poolSize: number): Promise<void> {
  const pool = new pg.Pool({ connectionString: DB_URL, max: poolSize });
  const histogram = new LatencyHistogram();

  // Generate TARGET_QPS * SIMULATION_SECONDS total queries
  const totalQueries = TARGET_QPS * SIMULATION_SECONDS;

  // TODO: Run INSERT queries in batches matching TARGET_QPS per second
  // For each query: measure latency, record in histogram
  // After all queries complete: print p50, p95, p99 and actual throughput

  await pool.end();
}

console.log('Pool Size | p50 (ms) | p95 (ms) | p99 (ms) | Actual QPS | Queue Waits');
for (const size of [2, 5, 10, 20, 50]) {
  await benchmarkPool(size);
}
```

**Questions to answer:**
1. At what pool size does p99 stop improving?
2. At what pool size does actual QPS plateau?
3. What is the minimum pool size that meets a p99 < 50ms SLA for click writes?

---

## Exercise 3 — Add CORS and Security Headers to ScaleForge

**Goal:** Harden the ScaleForge Express server.

Apply the following in `src/server.ts`:

1. Add `helmet()` with HSTS and CSP configured
2. Add CORS configured to allow only `http://localhost:5173` (dev) and `https://app.scaleforge.io` (prod) from an env variable
3. Add `express-rate-limit` to limit all API routes to 100 requests per 15 minutes per IP

```typescript
// src/server.ts additions:
import helmet from 'helmet';
import cors from 'cors';
import rateLimit from 'express-rate-limit';  // npm install express-rate-limit

// TODO: Add security middleware before routes
```

**Verify with curl:**

```bash
# Test CORS rejection
curl -v -H "Origin: https://evil.com" http://localhost:3001/api/v1/urls
# Should receive no Access-Control-Allow-Origin header (or a rejection)

# Test security headers
curl -I http://localhost:3001/health
# Should see: X-Content-Type-Options, X-Frame-Options, etc.

# Test rate limiting
for i in {1..105}; do curl -s -o /dev/null -w "%{http_code}\n" http://localhost:3001/health; done
# Should see: 200 ×100, then 429 ×5
```

<details>
<summary>Show solution</summary>

```typescript
// src/server.ts — security middleware section

import helmet from 'helmet';
import cors from 'cors';
import rateLimit from 'express-rate-limit';

const ALLOWED_ORIGINS = process.env.ALLOWED_ORIGINS?.split(',') ?? ['http://localhost:5173'];

app.use(helmet({
  hsts: { maxAge: 31536000, includeSubDomains: true },
  contentSecurityPolicy: {
    directives: { defaultSrc: ["'self'"], objectSrc: ["'none'"] },
  },
}));

app.use(cors({
  origin: (origin, cb) => {
    if (!origin || ALLOWED_ORIGINS.includes(origin)) cb(null, true);
    else cb(new Error(`Origin ${origin} not allowed`));
  },
  credentials: true,
}));

// Rate limiting — apply to all /api routes
app.use('/api', rateLimit({
  windowMs: 15 * 60 * 1000,  // 15 minutes
  max: 100,
  standardHeaders: true,
  legacyHeaders: false,
  message: { error: 'Too many requests, please slow down' },
}));
```

</details>

---

## Exercise 4 — Implement SSE Live Click Feed

**Goal:** Make the SSE endpoint from Topic 2.3 work end-to-end.

1. Wire the `ClickEventBus.publish(code, click)` call into the click worker (after the DB insert)
2. Implement the SSE endpoint in `src/routes/analytics.router.ts`
3. Test it with curl:

```bash
# Terminal 1: Subscribe to live events
curl -N http://localhost:3001/api/v1/urls/test01/analytics/live \
  -H "Authorization: Bearer <your-jwt>"

# Terminal 2: Trigger a redirect (which enqueues a click)
curl -Lv http://localhost:3001/test01

# Terminal 1 should receive:
# event: click
# data: {"country":"US","device":"unknown","timestamp":1234567890}
```

**Debugging checklist:**
- [ ] Is the BullMQ worker running? (`pnpm run worker` in a separate terminal)
- [ ] Does the queue have the click job? Check Bull Dashboard or `redis-cli LLEN bull:click-tracking:wait`
- [ ] Is `ClickEventBus.publish` called from the worker?
- [ ] Is `X-Accel-Buffering: no` set? (Without this, Nginx buffers SSE responses)
