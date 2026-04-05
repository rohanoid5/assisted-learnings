# 8.4 — Prometheus & Grafana Dashboards

## Concept

Prometheus is a time-series database that scrapes metrics from your services at regular intervals. Grafana visualizes those metrics as dashboards and fires alerts when values cross thresholds. Together they give you a real-time view of your system's health — not just "is it up?" but "what is the p99 latency right now, and is it trending upward?"

---

## Deep Dive

### Prometheus Pull Model

```
  Prometheus scrapes your service's /metrics endpoint every 15s.
  Your service does NOT push data to Prometheus.

  ┌────────────────────────────────────────────────────┐
  │  Prometheus (pull model)                           │
  │                                                    │
  │  every 15s:                                        │
  │    GET scaleforge:3001/metrics                     │
  │    GET flowforge:3002/metrics                      │
  │    GET node-exporter:9100/metrics  (OS metrics)    │
  │                                                    │
  │  Stores each scrape as a timestamped sample        │
  │  in its local TSDB (time-series database)          │
  └────────────────────────────────────────────────────┘
  
  Why pull?
    + Prometheus controls the scrape interval (no thundering push)
    + Failed scrapes are recorded as gaps — observable in Grafana
    + Services don't need Prometheus credentials
    - Services must be reachable from Prometheus (not push via webhook)
```

### PromQL Cheat Sheet for the Dashboards You Need

```
  Rate of requests per second (last 5 minutes):
    rate(http_requests_total{service="scaleforge"}[5m])

  Error rate as a percentage:
    rate(http_requests_total{status=~"5.."}[5m])
    /
    rate(http_requests_total[5m])
    * 100

  p99 redirect latency:
    histogram_quantile(0.99,
      rate(http_request_duration_seconds_bucket{route="/:code"}[5m])
    )

  Redis cache hit rate:
    rate(cache_hits_total{tier="l2"}[5m])
    /
    (rate(cache_hits_total{tier="l2"}[5m]) + rate(cache_misses_total{tier="l2"}[5m]))
    * 100

  DB pool saturation (waiting requests > 0 = alert):
    pg_pool_waiting_requests > 0

  BullMQ queue depth (FlowForge):
    bullmq_queue_waiting_jobs_total{queue="notifications"}
```

### Alerting Rules

```yaml
# prometheus/alerts.yml
groups:
  - name: scaleforge
    rules:
      - alert: HighRedirectLatency
        expr: |
          histogram_quantile(0.99,
            rate(http_request_duration_seconds_bucket{route="/:code"}[5m])
          ) > 0.1
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "Redirect p99 > 100ms for 5 minutes"

      - alert: HighErrorRate
        expr: |
          rate(http_requests_total{status=~"5.."}[5m])
          / rate(http_requests_total[5m]) > 0.01
        for: 2m
        labels:
          severity: critical
        annotations:
          summary: "Error rate > 1%"

      - alert: DatabasePoolExhausted
        expr: pg_pool_waiting_requests > 0
        for: 30s
        labels:
          severity: warning
        annotations:
          summary: "DB pool has waiting requests — consider increasing pool size"

      - alert: NotificationQueueBacklog
        expr: bullmq_queue_waiting_jobs_total{queue="notifications"} > 1000
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "FlowForge notification queue backlog > 1000 jobs"
```

---

## Code Examples

### Docker Compose: Prometheus + Grafana

```yaml
# docker-compose.yml (additions)
services:
  prometheus:
    image: prom/prometheus:v2.48.0
    volumes:
      - ./prometheus/prometheus.yml:/etc/prometheus/prometheus.yml
      - ./prometheus/alerts.yml:/etc/prometheus/alerts.yml
      - prometheus_data:/prometheus
    ports:
      - "9090:9090"
    command:
      - '--config.file=/etc/prometheus/prometheus.yml'
      - '--storage.tsdb.retention.time=15d'

  grafana:
    image: grafana/grafana:10-ubuntu
    volumes:
      - grafana_data:/var/lib/grafana
      - ./grafana/provisioning:/etc/grafana/provisioning
    environment:
      GF_SECURITY_ADMIN_PASSWORD: admin
      GF_AUTH_ANONYMOUS_ENABLED: "true"
    ports:
      - "3000:3000"
    depends_on: [prometheus]

volumes:
  prometheus_data:
  grafana_data:
```

```yaml
# prometheus/prometheus.yml
global:
  scrape_interval: 15s
  evaluation_interval: 15s

rule_files:
  - "alerts.yml"

scrape_configs:
  - job_name: scaleforge
    static_configs:
      - targets: ['scaleforge:3001']

  - job_name: flowforge
    static_configs:
      - targets: ['flowforge:3002']

  - job_name: node-exporter   # OS-level metrics (CPU, RAM, disk)
    static_configs:
      - targets: ['node-exporter:9100']
```

### BullMQ Metrics Exporter

```typescript
// src/monitoring/queue-metrics.ts
// BullMQ doesn't export Prometheus metrics by default.
// This poller reads queue stats and exports them as Prometheus gauges.

import { Queue } from 'bullmq';
import { Gauge } from 'prom-client';
import { metricsRegistry } from '../metrics/registry.js';
import { redis } from '../cache/redis.js';

const queueNames = ['notifications', 'emails', 'webhooks'];

const waitingGauge = new Gauge({
  name: 'bullmq_queue_waiting_jobs_total',
  help: 'Number of waiting jobs in BullMQ queue',
  labelNames: ['queue'] as const,
  registers: [metricsRegistry],
});

const activeGauge = new Gauge({
  name: 'bullmq_queue_active_jobs_total',
  help: 'Number of active (processing) jobs in BullMQ queue',
  labelNames: ['queue'] as const,
  registers: [metricsRegistry],
});

const failedGauge = new Gauge({
  name: 'bullmq_queue_failed_jobs_total',
  help: 'Number of failed jobs in BullMQ queue (DLQ depth)',
  labelNames: ['queue'] as const,
  registers: [metricsRegistry],
});

export async function startQueueMetricsPoller(intervalMs = 15_000): Promise<void> {
  const queues = queueNames.map((name) => new Queue(name, { connection: redis }));

  async function poll() {
    await Promise.all(queues.map(async (queue) => {
      const [waiting, active, failed] = await Promise.all([
        queue.getWaitingCount(),
        queue.getActiveCount(),
        queue.getFailedCount(),
      ]);
      waitingGauge.set({ queue: queue.name }, waiting);
      activeGauge.set({ queue: queue.name }, active);
      failedGauge.set({ queue: queue.name }, failed);
    }));
  }

  // Poll immediately, then on interval
  await poll();
  setInterval(poll, intervalMs);
}
```

### Grafana Dashboard Provisioning (JSON)

```json
// grafana/provisioning/dashboards/scaleforge.json
// Add this to auto-provision the dashboard on container startup.
// You can also build it in the Grafana UI and export as JSON.
{
  "title": "ScaleForge Overview",
  "panels": [
    {
      "title": "Redirect p99 Latency (ms)",
      "type": "timeseries",
      "targets": [{
        "expr": "histogram_quantile(0.99, rate(http_request_duration_seconds_bucket{route='/:code'}[5m])) * 1000",
        "legendFormat": "p99"
      }, {
        "expr": "histogram_quantile(0.50, rate(http_request_duration_seconds_bucket{route='/:code'}[5m])) * 1000",
        "legendFormat": "p50"
      }]
    },
    {
      "title": "Requests per second",
      "type": "timeseries",
      "targets": [{
        "expr": "rate(http_requests_total{route='/:code'}[1m])",
        "legendFormat": "redirects/s"
      }]
    },
    {
      "title": "Redis Cache Hit Rate (%)",
      "type": "gauge",
      "targets": [{
        "expr": "rate(cache_hits_total{tier='l2'}[5m]) / (rate(cache_hits_total{tier='l2'}[5m]) + rate(cache_misses_total{tier='l2'}[5m])) * 100"
      }],
      "fieldConfig": { "min": 0, "max": 100, "thresholds": [
        { "value": 0,  "color": "red" },
        { "value": 80, "color": "yellow" },
        { "value": 95, "color": "green" }
      ]}
    },
    {
      "title": "DB Pool Waiting Requests",
      "type": "timeseries",
      "targets": [{
        "expr": "pg_pool_waiting_requests",
        "legendFormat": "waiting"
      }]
    }
  ]
}
```

---

## Try It Yourself

**Exercise:** Build the ScaleForge dashboard and generate a latency spike.

```bash
# Step 1: Start the full stack
docker-compose up -d

# Step 2: Verify metrics are reachable
curl http://localhost:3001/metrics | grep http_request

# Step 3: Open Grafana at http://localhost:3000
# Add Prometheus as data source: http://prometheus:9090
# Import the dashboard JSON from grafana/provisioning/dashboards/scaleforge.json

# Step 4: Generate baseline traffic (watch the dashboard)
autocannon -c 50 -d 60 http://localhost:3001/abc123

# Step 5: Generate a latency spike by:
# a) Stopping Redis (forces all requests to hit Postgres)
docker-compose stop redis

# b) Observe the p99 panel spike
# c) Restart Redis and watch recovery

docker-compose start redis

# TODO: Note how long it takes for p99 to return to baseline after Redis restarts.
# This is your "cache warmup time" — the window during which you are most vulnerable.
```

<details>
<summary>Show how to create an alert notification channel</summary>

```yaml
# grafana/provisioning/alerting/contact-points.yaml
apiVersion: 1
contactPoints:
  - orgId: 1
    name: slack-alerts
    receivers:
      - uid: slack
        type: slack
        settings:
          url: "${SLACK_WEBHOOK_URL}"
          channel: "#alerts"
          title: "{{ .CommonLabels.alertname }}"
          text: "{{ range .Alerts }}{{ .Annotations.summary }}{{ end }}"
```

</details>

---

## Capstone Connection

The Prometheus + Grafana stack turns ScaleForge's metrics from raw numbers into an operational runtime view. When the "High Redirect Latency" alert fires at 2am, the on-call engineer opens Grafana, sees the Redis cache hit rate gauge drop to 40% (was 98%), cross-references the timeline with a recent Redis restart event, and knows immediately that the system is mid-warmup. Without dashboards, this diagnosis takes hours. With them, it takes seconds.
