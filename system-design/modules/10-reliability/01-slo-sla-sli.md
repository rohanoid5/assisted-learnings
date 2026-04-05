# 10.1 — SLOs, SLAs, and SLIs

## Concept

SLIs, SLOs, and SLAs form a hierarchy of reliability commitments: SLIs are what you measure, SLOs are internal targets you set for those measurements, and SLAs are contractual promises made to customers. Getting this language right matters because it determines how you prioritize engineering work — you don't fix things that are within SLO, and you stop feature work when you're burning through error budget too fast.

---

## Deep Dive

### The Three-Level Hierarchy

```
  ┌─────────────────────────────────────────────┐
  │                                             │
  │   SLA  — Service Level Agreement           │
  │   "We will refund you if uptime < 99.9%"   │
  │   Owner: Legal / Business                  │
  │                                             │
  │   SLO  — Service Level Objective           │
  │   "We target 99.95% availability"          │
  │   Owner: Engineering (internal)            │
  │   Note: always STRICTER than SLA            │
  │           (safety margin)                  │
  │                                             │
  │   SLI  — Service Level Indicator           │
  │   "Current availability: 99.97%"           │
  │   Owner: Monitoring / Prometheus           │
  │   Note: the actual measured number         │
  │                                             │
  └─────────────────────────────────────────────┘
  
  Relationship:
    SLI meets SLO → SLA is satisfied (with margin)
    SLI breaches SLO, but within SLA → internal alarm, no credits
    SLI breaches SLA → customer credits / contractual consequence
```

### Common SLI Types

```
  Availability:
    good_requests / total_requests
    "good" = status < 500 AND latency < threshold
  
  Latency:
    histogram_quantile(0.99, rate(http_request_duration_seconds_bucket[5m]))
    "What latency are 99% of requests below?"
  
  Throughput:
    rate(http_requests_total[5m])
    "How many requests/second are we handling?"
  
  Freshness (for caches):
    age_seconds of cache entry vs. data in DB
    "How stale is the data the user sees?"
  
  Durability (for storage):
    1 - (lost_writes / total_writes)
    "What fraction of committed writes are retrievable?"
```

### Choosing Good SLIs

Not all SLIs are equal. A good SLI:

| Property | Bad SLI | Good SLI |
|---|---|---|
| User-facing | CPU utilization | HTTP error rate |
| Measurable precisely | "system felt fast" | p99 latency ≤ 50ms |
| Actionable on breach | "uptime" (too vague) | 5xx rate > 0.1% over 5 min |
| Not too many | 20 SLOs for a single service | 3–5 focused SLOs |

### ScaleForge SLI Definitions

```typescript
// Expressed as PromQL — these queries feed the SLO dashboards

// 1. Availability SLI: fraction of requests that returned non-5xx
// success_rate = (total - errors) / total
const availabilitySLI = `
  1 - (
    sum(rate(http_requests_total{status=~"5..",job="scaleforge"}[30d]))
    /
    sum(rate(http_requests_total{job="scaleforge"}[30d]))
  )
`;

// 2. Redirect latency SLI: 99th percentile over the rolling 30-day window
const redirectLatencySLI = `
  histogram_quantile(
    0.99,
    sum(rate(http_request_duration_seconds_bucket{
      job="scaleforge",
      handler="/r/:code"
    }[30d])) by (le)
  )
`;

// SLO targets: availabilitySLI >= 0.9995, redirectLatencySLI <= 0.05 (50ms)
```

---

## Code Examples

### Emitting SLI-Compatible Metrics

```typescript
// src/observability/metrics.ts
// All SLI metrics must be emitted in a form that PromQL can aggregate.
// The histogram for latency is the most important — it enables histogram_quantile().

import { Counter, Histogram, register } from 'prom-client';

// Counter: total requests, labeled by status and handler
export const httpRequestsTotal = new Counter({
  name: 'http_requests_total',
  help: 'Total HTTP requests',
  labelNames: ['method', 'handler', 'status'],
});

// Histogram: request duration — MUST use standard Prometheus buckets
// for histogram_quantile to be accurate
export const httpRequestDurationSeconds = new Histogram({
  name: 'http_request_duration_seconds',
  help: 'HTTP request duration in seconds',
  labelNames: ['method', 'handler', 'status'],
  // Buckets calibrated to ScaleForge's SLOs:
  // redirect SLO = 50ms, URL create SLO = 500ms
  buckets: [0.005, 0.01, 0.025, 0.05, 0.1, 0.25, 0.5, 1, 2.5, 5],
});

// Middleware to record both metrics on each request
import type { Request, Response, NextFunction } from 'express';

export function metricsMiddleware(req: Request, res: Response, next: NextFunction): void {
  const end = httpRequestDurationSeconds.startTimer();

  res.on('finish', () => {
    const labels = {
      method:  req.method,
      handler: req.route?.path ?? 'unknown',
      status:  String(res.statusCode),
    };

    end(labels);                     // records duration
    httpRequestsTotal.inc(labels);   // records count
  });

  next();
}
```

### SLO Alerting Rules

```yaml
# prometheus/slo-alerts.yml
groups:
  - name: scaleforge.slo
    rules:
      # Fire alert if 1-hour availability drops below 99.9%
      - alert: ScaleForgeRedirectAvailabilityBreach
        expr: |
          (
            sum(rate(http_requests_total{status!~"5..", handler="/r/:code"}[1h]))
            /
            sum(rate(http_requests_total{handler="/r/:code"}[1h]))
          ) < 0.999
        for: 5m
        labels:
          severity: page     # wake someone up
        annotations:
          summary: "Redirect availability SLO breach"
          description: >
            Availability over last 1h: {{ $value | humanizePercentage }}.
            SLO target: 99.9%.

      # Fire alert if p99 redirect latency exceeds 50ms
      - alert: ScaleForgeRedirectLatencySLO
        expr: |
          histogram_quantile(
            0.99,
            sum(rate(http_request_duration_seconds_bucket{handler="/r/:code"}[5m])) by (le)
          ) > 0.05
        for: 5m
        labels:
          severity: ticket
        annotations:
          summary: "Redirect p99 latency above 50ms SLO"
          description: "Current p99: {{ $value | humanizeDuration }}"
```

---

## Try It Yourself

**Exercise:** Define SLIs for the FlowForge notification delivery endpoint.

```typescript
// TODO: Write PromQL expressions for:
//
// 1. Delivery availability SLI for POST /api/v1/notifications
//    (hint: fraction of requests that returned non-5xx)
//
// 2. Delivery latency SLI — p95 latency over a 5-minute window
//
// 3. Queue depth SLI:
//    "the BullMQ waiting queue should never exceed 1000 jobs"
//    (hint: use the bullmq_queue_size gauge from Module 08)
//
// For each, state:
//   - SLI formula (in plain English or PromQL)
//   - SLO target value (a number you'd alert on)
//   - Why this SLI is user-facing
```

<details>
<summary>Show sample SLI definitions</summary>

```typescript
// 1. Availability SLI
const deliveryAvailabilitySLI = `
  1 - (
    sum(rate(http_requests_total{status=~"5..", handler="/api/v1/notifications"}[5m]))
    /
    sum(rate(http_requests_total{handler="/api/v1/notifications"}[5m]))
  )
`;
// SLO: >= 0.999 (99.9%)
// Why user-facing: a 5xx here means the notification was never enqueued

// 2. Latency SLI
const deliveryLatencySLI = `
  histogram_quantile(
    0.95,
    sum(rate(http_request_duration_seconds_bucket{handler="/api/v1/notifications"}[5m])) by (le)
  )
`;
// SLO: <= 0.5 (500ms p95)
// Why user-facing: users wait for acknowledgment before continuing

// 3. Queue depth SLI
const queueDepthSLI = `
  bullmq_queue_size{queue="notifications", state="waiting"}
`;
// SLO: <= 1000
// Why user-facing: high queue depth means notification delay for end users
```

</details>

---

## Capstone Connection

ScaleForge's redirect endpoint is the most user-visible path — it's in the hot path for every shortened link click. Setting a strict 99.9% availability SLO (tighter than the 99.5% SLA) gives the engineering team a 43-minute error budget per month to spend on deploying, experimenting, and absorbing unexpected failures while still honoring contractual commitments.
