# 7.6 — Observability

## Concept

Observability is the ability to understand what a distributed system is doing at runtime from its external outputs. The three pillars are **logs** (discrete events), **metrics** (aggregated numbers over time), and **traces** (a request's path across service boundaries). Without all three, diagnosing production incidents in a microservices system becomes guesswork.

---

## Deep Dive

### The Three Pillars

```
  ┌──────────────────────────────────────────────────────────────────┐
  │  LOGS — what happened                                            │
  │                                                                  │
  │  {"level":"info","msg":"URL created","shortCode":"aB3x9Z",       │
  │   "userId":"u_123","latencyMs":12,"correlationId":"abc-def"}     │
  │                                                                  │
  │  Use for: debugging specific requests, audit trails              │
  │  Tool: pino → stdout → Loki or CloudWatch                        │
  └──────────────────────────────────────────────────────────────────┘

  ┌──────────────────────────────────────────────────────────────────┐
  │  METRICS — how the system is behaving (aggregated)               │
  │                                                                  │
  │  http_request_duration_seconds{route="/api/v1/urls",             │
  │    method="POST",status="201"} histogram                         │
  │  redis_cache_hits_total counter                                  │
  │  bullmq_queue_depth gauge                                        │
  │                                                                  │
  │  Use for: dashboards, alerting, capacity planning                │
  │  Tool: prom-client → /metrics → Prometheus → Grafana             │
  └──────────────────────────────────────────────────────────────────┘

  ┌──────────────────────────────────────────────────────────────────┐
  │  TRACES — the full path of a request                             │
  │                                                                  │
  │  Trace: corr-id=abc-def                                          │
  │   ├─ Span: Gateway (2ms) — auth + routing                        │
  │   ├─ Span: ScaleForge (18ms)                                     │
  │   │    ├─ Span: Redis GET (0.5ms) — cache miss                   │
  │   │    └─ Span: Postgres INSERT (8ms)                            │
  │   └─ Span: FlowForge HTTP (12ms) — notification enqueue          │
  │                                                                  │
  │  Use for: latency attribution, bottleneck identification         │
  │  Tool: OpenTelemetry SDK → Jaeger or Tempo                       │
  └──────────────────────────────────────────────────────────────────┘
```

### Correlation IDs: The Backbone of Distributed Tracing

```
  When a user request flows through multiple services, a correlation ID
  threads all log lines and spans together.

  Without correlation ID:
    ScaleForge log: "DB query took 800ms" — which request? which user?
    FlowForge log:  "Email sent" — which job triggered this?
  
  With correlation ID:
    All services log {"correlationId":"abc-def",...}
    Log query: grep correlationId=abc-def across ALL service logs
    Returns complete timeline of that one request.
  
  Convention: X-Correlation-Id request/response header
    If header present → use it
    If missing → generate UUID at gateway
    Always forward downstream on outbound requests
```

### What to Instrument

```
  Minimum viable instrumentation for each service:
  
  ┌──────────────────────────────────────┬──────────────────────────┐
  │ Metric                               │ Alert threshold          │
  ├──────────────────────────────────────┼──────────────────────────┤
  │ http_request_duration_seconds p99    │ > 1s for 5 min           │
  │ http_request_total{status="5xx"}     │ error rate > 1%          │
  │ db_pool_waiting_count                │ > 5 waiting              │
  │ redis_connected (0/1 gauge)          │ = 0 for > 30s            │
  │ bullmq_queue_depth (waiting jobs)    │ > 1000                   │
  └──────────────────────────────────────┴──────────────────────────┘
```

---

## Code Examples

### Prometheus Metrics Middleware

```typescript
// src/observability/metrics.ts
import { Registry, Counter, Histogram, Gauge, collectDefaultMetrics } from 'prom-client';
import type { Request, Response, NextFunction } from 'express';

export const registry = new Registry();

// Process-level defaults: CPU, memory, event loop lag, GC stats
collectDefaultMetrics({ register: registry });

// HTTP request counter
export const httpRequestsTotal = new Counter({
  name: 'http_requests_total',
  help: 'Total HTTP requests',
  labelNames: ['method', 'route', 'status'] as const,
  registers: [registry],
});

// HTTP request duration histogram (milliseconds, stored as seconds)
export const httpRequestDuration = new Histogram({
  name: 'http_request_duration_seconds',
  help: 'HTTP request duration in seconds',
  labelNames: ['method', 'route', 'status'] as const,
  // Buckets tuned for a web API: 5ms to 10s
  buckets: [0.005, 0.01, 0.025, 0.05, 0.1, 0.25, 0.5, 1, 2.5, 10],
  registers: [registry],
});

// BullMQ queue depth gauge (updated on job enqueue/complete/fail)
export const queueDepthGauge = new Gauge({
  name: 'bullmq_queue_depth',
  help: 'Number of waiting + delayed jobs in the queue',
  labelNames: ['queue'] as const,
  registers: [registry],
});

// Cache hit/miss counters
export const cacheHitsTotal = new Counter({
  name: 'cache_hits_total',
  help: 'Cache hits',
  labelNames: ['tier'] as const,  // "l1", "l2" (redis)
  registers: [registry],
});

export const cacheMissesTotal = new Counter({
  name: 'cache_misses_total',
  help: 'Cache misses (fell through to DB)',
  registers: [registry],
});
```

```typescript
// src/observability/metrics.middleware.ts
import { httpRequestsTotal, httpRequestDuration } from './metrics.js';
import type { Request, Response, NextFunction } from 'express';

export function metricsMiddleware(req: Request, res: Response, next: NextFunction): void {
  const startTime = process.hrtime.bigint();

  res.on('finish', () => {
    const durationMs = Number(process.hrtime.bigint() - startTime) / 1_000_000;
    const durationSec = durationMs / 1000;

    // Normalize route — use Express's matched route pattern, not the raw path
    // Prevents high cardinality from /abc123, /xy9z, etc.
    const route = req.route?.path ?? req.path;
    const labels = {
      method: req.method,
      route,
      status: String(res.statusCode),
    };

    httpRequestsTotal.inc(labels);
    httpRequestDuration.observe(labels, durationSec);
  });

  next();
}
```

```typescript
// src/routes/metrics.router.ts
import { Router } from 'express';
import { registry } from '../observability/metrics.js';

export const metricsRouter = Router();

// Prometheus scrape endpoint — do NOT expose publicly
// In docker-compose: only accessible within the internal network
metricsRouter.get('/', async (_req, res) => {
  res.set('Content-Type', registry.contentType);
  res.send(await registry.metrics());
});
```

### Structured Logging with pino and Correlation IDs

```typescript
// src/logger.ts
import pino from 'pino';

export const logger = pino({
  level: process.env.LOG_LEVEL ?? 'info',
  // In production, log as JSON. In dev, use pino-pretty.
  transport: process.env.NODE_ENV === 'development'
    ? { target: 'pino-pretty', options: { colorize: true } }
    : undefined,
});

// Usage in request handlers:
// const reqLogger = logger.child({ correlationId: req.correlationId });
// reqLogger.info({ shortCode, latencyMs }, 'URL redirect served');
// → {"level":"info","correlationId":"abc-def","shortCode":"aB3x9Z",...}
```

```typescript
// src/middleware/correlation-id.middleware.ts
import { randomUUID } from 'node:crypto';
import type { Request, Response, NextFunction } from 'express';
import { logger } from '../logger.js';

declare global {
  namespace Express {
    interface Request {
      correlationId: string;
      log: typeof logger;
    }
  }
}

export function correlationIdMiddleware(req: Request, res: Response, next: NextFunction): void {
  const id = (req.headers['x-correlation-id'] as string | undefined) ?? randomUUID();
  req.correlationId = id;
  req.log = logger.child({ correlationId: id, service: 'scaleforge' });
  res.setHeader('X-Correlation-Id', id);
  next();
}
```

### Registering Everything in `app.ts`

```typescript
// src/app.ts (relevant excerpt)
import { correlationIdMiddleware } from './middleware/correlation-id.middleware.js';
import { metricsMiddleware } from './observability/metrics.middleware.js';
import { metricsRouter } from './routes/metrics.router.js';
import { healthRouter } from './routes/health.router.js';

// Observe every request
app.use(correlationIdMiddleware);
app.use(metricsMiddleware);

// Prometheus scrape — bind to internal port only in production
app.use('/metrics', metricsRouter);

// Kubernetes probes
app.use('/health', healthRouter);
```

### prometheus.yml (Scrape Config)

```yaml
# monitoring/prometheus.yml
global:
  scrape_interval: 15s

scrape_configs:
  - job_name: scaleforge
    static_configs:
      - targets: ['scaleforge:3001']
    metrics_path: /metrics

  - job_name: flowforge
    static_configs:
      - targets: ['flowforge:3002']
    metrics_path: /metrics
```

---

## Try It Yourself

**Exercise:** Verify that correlation IDs thread across service logs.

```typescript
// observability.exercise.ts

// TODO:
// 1. Add correlationIdMiddleware to both ScaleForge and FlowForge
// 2. When ScaleForge calls FlowForge, forward the X-Correlation-Id header:
//    fetch(url, { headers: { 'X-Correlation-Id': req.correlationId } })
//
// 3. Start docker-compose up
// 4. Make a POST /api/v1/urls with notifyOnCreate: true
// 5. Note the X-Correlation-Id in the response header
// 6. Check logs:
//    docker-compose logs scaleforge | grep <correlationId>
//    docker-compose logs flowforge  | grep <correlationId>
//    → Both should show matching log lines for the same request
//
// 7. Add the metricsMiddleware to ScaleForge
// 8. Hit GET /metrics — you should see:
//    http_requests_total{method="POST",route="/api/v1/urls",status="201"} 1
//    http_request_duration_seconds_bucket{...}
//
// Bonus: Add prom-client to docker-compose alongside prometheus.yml
// and observe the scraped metrics in Prometheus UI at http://localhost:9090
// Query: histogram_quantile(0.99, sum by(route,le)(rate(http_request_duration_seconds_bucket[5m])))
// → p99 latency per route over the last 5 minutes
```

<details>
<summary>Show correlation ID forwarding in FlowForgeClient</summary>

```typescript
// src/clients/flowforge.client.ts (updated to forward correlation ID)
export class FlowForgeClient {
  constructor(
    private readonly baseUrl: string,
    private readonly timeoutMs = 5000,
  ) {}

  async enqueueNotification(
    job: NotificationJobInput,
    correlationId: string,             // ← accept from caller
  ): Promise<string> {
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), this.timeoutMs);
    try {
      const res = await fetch(`${this.baseUrl}/api/v1/notifications`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'X-Correlation-Id': correlationId,  // ← forward downstream
        },
        body: JSON.stringify(job),
        signal: controller.signal,
      });
      const body = (await res.json()) as { jobId: string };
      return body.jobId;
    } finally {
      clearTimeout(timer);
    }
  }
}
```

</details>

---

## Capstone Connection

Every redirect through ScaleForge and every notification dispatched through FlowForge now produces structured log lines sharing the same `correlationId`. When a user reports "my short link redirected but I never got the email", you search for their correlationId across both service logs and see exactly which step failed and why — in seconds, not hours.
