# 8.1 — Metrics & Prometheus

## Concept

Metrics are numerical measurements collected over time — the heartbeat of your system. Unlike logs (which describe discrete events) or traces (which follow individual requests), metrics give you the aggregate picture: _how many requests per second are we handling? What's the 99th percentile latency? How close is the disk to full?_ Prometheus is the de facto standard for metrics in Kubernetes. Its pull-based scrape model, powerful query language (PromQL), and native Kubernetes service discovery make it the foundation of every serious observability stack.

Understanding metrics goes beyond knowing how to deploy Prometheus. You need to know _what_ to measure (the RED and USE methods), _how_ to measure it (the four metric types), and _how_ to query it (PromQL) to answer real operational questions. This section takes you from metric fundamentals through production-grade Prometheus architecture.

---

## Deep Dive

### The Four Metric Types

Prometheus defines four core metric types. Choosing the wrong type is a common mistake that leads to misleading dashboards and broken alerts.

| Type | Description | Example | Query Pattern |
|------|-------------|---------|---------------|
| **Counter** | Monotonically increasing value. Only goes up (or resets to 0). | Total HTTP requests, errors, bytes sent | `rate(counter[5m])` — per-second rate |
| **Gauge** | Value that goes up and down. | Memory usage, active connections, queue depth | Direct value or `delta()` |
| **Histogram** | Samples observations into configurable buckets. | Request latency, response size | `histogram_quantile(0.99, rate(hist_bucket[5m]))` |
| **Summary** | Client-side quantile calculation. | Similar to histogram but pre-computed | Rarely used — prefer histograms |

> **Key insight:** Always use **histograms** over summaries for latency. Histograms can be aggregated across instances (summaries cannot), and you can change bucket boundaries after deployment. The only downside is slightly higher cardinality.

```
┌─────────────────────────────────────────────────────────────────────┐
│                    Prometheus Metric Types                            │
│                                                                     │
│  Counter (monotonic)          Gauge (up/down)                       │
│                                                                     │
│  Value                        Value                                 │
│    │      ╱                     │    ╱╲                              │
│    │    ╱                       │   ╱  ╲   ╱╲                       │
│    │  ╱                         │  ╱    ╲ ╱  ╲                      │
│    │╱                           │╱        ╲                          │
│    └───────── Time              └───────── Time                     │
│  rate() converts to             Use directly or                     │
│  per-second rate                with delta()                        │
│                                                                     │
│  Histogram (bucketed)         Summary (quantiles)                   │
│                                                                     │
│  Bucket    Count               Quantile  Value                      │
│  ≤10ms      45                 0.50      12ms                       │
│  ≤25ms     120                 0.90      45ms                       │
│  ≤50ms     198                 0.99      120ms                      │
│  ≤100ms    210                 Computed client-side                  │
│  ≤250ms    215                 Cannot aggregate across               │
│  +Inf      215                 instances — prefer histograms         │
└─────────────────────────────────────────────────────────────────────┘
```

---

### Prometheus Architecture

Prometheus follows a **pull-based** model — it scrapes targets on a schedule rather than receiving pushed metrics. This is a deliberate design choice: the monitoring system controls the data collection rate, service discovery is decoupled from the application, and target health is automatically detected (if a scrape fails, the target is down).

```
┌─────────────────────────────────────────────────────────────────────┐
│                    Prometheus Architecture                            │
│                                                                     │
│  ┌─────────────┐     ┌──────────────┐     ┌─────────────────┐      │
│  │ Kubernetes   │     │ Prometheus    │     │ Alertmanager    │      │
│  │ API Server   │────▶│ Server        │────▶│                 │      │
│  │ (discovery)  │     │              │     │ ┌─────────────┐ │      │
│  └─────────────┘     │ ┌──────────┐ │     │ │ Slack       │ │      │
│                       │ │ TSDB     │ │     │ │ PagerDuty   │ │      │
│  ┌─────────────┐     │ │ (local   │ │     │ │ Email       │ │      │
│  │ Targets      │     │ │  disk)   │ │     │ └─────────────┘ │      │
│  │              │◀────│ └──────────┘ │     └─────────────────┘      │
│  │ /metrics     │scrape│              │                               │
│  │              │     │ ┌──────────┐ │     ┌─────────────────┐      │
│  │ api-gateway  │     │ │ PromQL   │ │────▶│ Grafana          │      │
│  │ worker       │     │ │ Engine   │ │query│ (dashboards)     │      │
│  │ postgres     │     │ └──────────┘ │     └─────────────────┘      │
│  │ redis        │     │              │                               │
│  │ node-exporter│     │ ┌──────────┐ │                               │
│  │ kube-state   │     │ │ Rules    │ │     ┌─────────────────┐      │
│  └─────────────┘     │ │ Engine   │ │     │ Recording rules  │      │
│                       │ └──────────┘ │     │ (pre-compute)    │      │
│                       └──────────────┘     └─────────────────┘      │
│                                                                     │
│  Flow: Discovery → Scrape → Store → Query/Alert                      │
└─────────────────────────────────────────────────────────────────────┘
```

**Key components:**

- **Prometheus Server** — Scrapes targets, stores time-series in its TSDB, evaluates rules, and serves PromQL queries.
- **Service Discovery** — In Kubernetes, Prometheus discovers targets via the API server (Endpoints, Pods, Services). No hardcoded target lists.
- **Alertmanager** — Receives alerts from Prometheus, deduplicates, groups, routes, and sends notifications (Slack, PagerDuty, email).
- **Exporters** — Sidecar or standalone processes that expose metrics for things that don't natively speak Prometheus (databases, hardware, etc.).

---

### Kubernetes Service Discovery

In Kubernetes, Prometheus discovers targets using **ServiceMonitor** custom resources (from the kube-prometheus-stack Helm chart). This is the bridge between "my service exposes /metrics" and "Prometheus scrapes it."

```yaml
# servicemonitor-api-gateway.yaml
apiVersion: monitoring.coreos.com/v1
kind: ServiceMonitor
metadata:
  name: api-gateway
  namespace: deployforge
  labels:
    release: prometheus    # must match Prometheus operator's selector
spec:
  selector:
    matchLabels:
      app: api-gateway
  endpoints:
  - port: http-metrics     # named port on the Service
    interval: 15s
    path: /metrics
  namespaceSelector:
    matchNames:
    - deployforge
```

> **Production note:** The `release: prometheus` label is critical — without it, the Prometheus Operator won't pick up the ServiceMonitor. This is the #1 reason "my metrics aren't showing up."

---

### PromQL Fundamentals

PromQL (Prometheus Query Language) is what transforms raw metrics into operational insight. It's not SQL — it operates on time-series vectors.

#### Selectors and Labels

```promql
# Instant vector — current value of all matching series
http_requests_total{service="api-gateway", status_code=~"5.."}

# Range vector — values over a time window
http_requests_total{service="api-gateway"}[5m]

# Label matchers:
#   =   exact match
#   !=  not equal
#   =~  regex match
#   !~  regex not match
```

#### Rate and Increase

```promql
# rate() — per-second average rate of increase over a window
# ALWAYS use rate() on counters — raw counter values are meaningless
rate(http_requests_total{service="api-gateway"}[5m])

# increase() — total increase over a window (rate * seconds)
increase(http_requests_total{service="api-gateway"}[1h])
```

> **Warning:** Never use `rate()` with a range smaller than 2× your scrape interval. If you scrape every 15s, use `[1m]` minimum to ensure at least two data points.

#### Aggregations

```promql
# Sum request rate across all pods
sum(rate(http_requests_total{service="api-gateway"}[5m]))

# Sum by status code — one series per code
sum by (status_code) (rate(http_requests_total{service="api-gateway"}[5m]))

# Average memory across all worker pods
avg(container_memory_working_set_bytes{pod=~"worker-.*"})

# Top 5 pods by CPU
topk(5, rate(container_cpu_usage_seconds_total[5m]))
```

#### Histogram Quantiles

```promql
# 99th percentile request duration
histogram_quantile(
  0.99,
  sum by (le) (rate(http_request_duration_seconds_bucket{service="api-gateway"}[5m]))
)

# 50th percentile (median) by endpoint
histogram_quantile(
  0.5,
  sum by (le, endpoint) (rate(http_request_duration_seconds_bucket{service="api-gateway"}[5m]))
)
```

> **Key insight:** `histogram_quantile` needs the `le` (less-than-or-equal) label in the `by` clause. Forgetting it produces garbage results. Always include any grouping labels AND `le`.

---

### Recording Rules

Recording rules pre-compute expensive PromQL expressions and store the results as new time-series. This improves dashboard load time and makes alerting rules simpler.

```yaml
# recording-rules.yaml
apiVersion: monitoring.coreos.com/v1
kind: PrometheusRule
metadata:
  name: deployforge-recording-rules
  namespace: deployforge
  labels:
    release: prometheus
spec:
  groups:
  - name: deployforge.api-gateway.rules
    interval: 30s
    rules:
    # Request rate by status class
    - record: deployforge:api_gateway:request_rate_by_status:5m
      expr: |
        sum by (status_class) (
          rate(http_requests_total{service="api-gateway"}[5m])
        )

    # p99 latency
    - record: deployforge:api_gateway:latency_p99:5m
      expr: |
        histogram_quantile(0.99,
          sum by (le) (rate(http_request_duration_seconds_bucket{service="api-gateway"}[5m]))
        )

    # Error rate (5xx / total)
    - record: deployforge:api_gateway:error_rate:5m
      expr: |
        sum(rate(http_requests_total{service="api-gateway", status_code=~"5.."}[5m]))
        /
        sum(rate(http_requests_total{service="api-gateway"}[5m]))
```

> **Production note:** Recording rule naming convention is `level:metric:operations`. This makes it obvious the metric is derived, not raw. Grafana dashboards should query recording rules, not raw expressions.

---

### The RED Method

The RED method answers the question "Is my request-driven service healthy?" with three signals:

| Signal | Metric | What It Tells You |
|--------|--------|--------------------|
| **R**ate | `rate(http_requests_total[5m])` | Demand on the service. Traffic drops → possible upstream failure. |
| **E**rrors | `rate(http_requests_total{status=~"5.."}[5m])` | Quality of service. Error ratio above threshold → page. |
| **D**uration | `histogram_quantile(0.99, ...)` | User experience. Latency spike → possible dependency issue. |

Use RED for any service that handles requests: API gateways, web servers, gRPC services, queue consumers.

---

### The USE Method

The USE method answers "Is my infrastructure resource constrained?" with three signals per resource:

| Signal | CPU Example | Memory Example | Disk Example |
|--------|-------------|----------------|--------------|
| **U**tilization | `rate(cpu_usage[5m]) / cpu_limit` | `memory_working_set / memory_limit` | `disk_used / disk_total` |
| **S**aturation | CPU throttling events | OOM kills, swap usage | IO queue depth |
| **E**rrors | — | `oom_kills_total` | Disk I/O errors |

Use USE for every resource type: CPU, memory, disk, network, file descriptors, connection pools.

```
┌─────────────────────────────────────────────────────────────────────┐
│                   RED + USE: What to Monitor                         │
│                                                                     │
│  ┌──── Services (RED) ─────────────────────────────────────────┐    │
│  │                                                              │    │
│  │  API Gateway          Worker Service                         │    │
│  │  ├── Rate: req/s      ├── Rate: jobs/s                       │    │
│  │  ├── Errors: 5xx/s    ├── Errors: failed_jobs/s              │    │
│  │  └── Duration: p99    └── Duration: job_duration_p99         │    │
│  │                                                              │    │
│  └──────────────────────────────────────────────────────────────┘    │
│                                                                     │
│  ┌──── Resources (USE) ────────────────────────────────────────┐    │
│  │                                                              │    │
│  │  CPU              Memory           Disk          Network     │    │
│  │  ├── Util: %      ├── Util: %      ├── Util: %   ├── Util   │    │
│  │  ├── Sat: thrttl  ├── Sat: OOMs    ├── Sat: IOPS ├── Sat    │    │
│  │  └── Err: —       └── Err: OOMs    └── Err: I/O  └── Err    │    │
│  │                                                              │    │
│  └──────────────────────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────────────────────┘
```

---

### Key Exporters for Kubernetes

| Exporter | What It Exposes | Deployment |
|----------|-----------------|------------|
| **node-exporter** | Host-level metrics: CPU, memory, disk, network per node | DaemonSet |
| **kube-state-metrics** | Kubernetes object state: desired vs current replicas, pod phases, resource requests/limits | Deployment |
| **cAdvisor** | Container-level resource usage (built into kubelet) | Built-in |
| **postgres_exporter** | PostgreSQL statistics: connections, transactions, replication lag, table sizes | Sidecar |
| **redis_exporter** | Redis metrics: connected clients, memory, keyspace, command stats | Sidecar |

> **Production note:** `kube-state-metrics` + `node-exporter` together give you complete USE method coverage for infrastructure. Deploy them first — they're free insight.

---

### Instrumenting a Node.js/TypeScript Service

Using the `prom-client` library to expose application metrics:

```typescript
// src/metrics.ts — DeployForge API Gateway metrics module
import { Registry, Counter, Histogram, Gauge, collectDefaultMetrics } from 'prom-client';

export const registry = new Registry();

// Collect Node.js runtime metrics (event loop lag, heap, GC, etc.)
collectDefaultMetrics({ register: registry, prefix: 'deployforge_' });

// RED method: Request counter
export const httpRequestsTotal = new Counter({
  name: 'http_requests_total',
  help: 'Total number of HTTP requests',
  labelNames: ['method', 'endpoint', 'status_code', 'status_class'] as const,
  registers: [registry],
});

// RED method: Request duration histogram
export const httpRequestDuration = new Histogram({
  name: 'http_request_duration_seconds',
  help: 'HTTP request duration in seconds',
  labelNames: ['method', 'endpoint', 'status_code'] as const,
  buckets: [0.005, 0.01, 0.025, 0.05, 0.1, 0.25, 0.5, 1, 2.5, 5, 10],
  registers: [registry],
});

// Business metric: Active deployments gauge
export const activeDeployments = new Gauge({
  name: 'deployforge_active_deployments',
  help: 'Number of currently running deployments',
  registers: [registry],
});

// Business metric: Deployment duration histogram
export const deploymentDuration = new Histogram({
  name: 'deployforge_deployment_duration_seconds',
  help: 'Time to complete a deployment',
  labelNames: ['status', 'environment'] as const,
  buckets: [5, 10, 30, 60, 120, 300, 600],
  registers: [registry],
});
```

```typescript
// src/middleware/metrics.ts — Express middleware for automatic RED metrics
import { Request, Response, NextFunction } from 'express';
import { httpRequestsTotal, httpRequestDuration } from '../metrics';

export function metricsMiddleware(req: Request, res: Response, next: NextFunction): void {
  const start = process.hrtime.bigint();

  res.on('finish', () => {
    const durationNs = Number(process.hrtime.bigint() - start);
    const durationSec = durationNs / 1e9;
    const statusClass = `${Math.floor(res.statusCode / 100)}xx`;

    // Normalize endpoint to avoid cardinality explosion
    // e.g., /api/deployments/abc123 → /api/deployments/:id
    const endpoint = normalizeEndpoint(req.route?.path || req.path);

    httpRequestsTotal.inc({
      method: req.method,
      endpoint,
      status_code: String(res.statusCode),
      status_class: statusClass,
    });

    httpRequestDuration.observe(
      { method: req.method, endpoint, status_code: String(res.statusCode) },
      durationSec,
    );
  });

  next();
}

function normalizeEndpoint(path: string): string {
  // Replace UUIDs and numeric IDs with :id to keep cardinality bounded
  return path
    .replace(/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}/gi, ':id')
    .replace(/\/\d+/g, '/:id');
}
```

```typescript
// src/routes/metrics.ts — Expose /metrics endpoint for Prometheus scraping
import { Router, Request, Response } from 'express';
import { registry } from '../metrics';

const router = Router();

router.get('/metrics', async (_req: Request, res: Response) => {
  res.set('Content-Type', registry.contentType);
  res.end(await registry.metrics());
});

export default router;
```

> **Caution:** Label cardinality is the #1 cause of Prometheus OOM kills. Never put user IDs, request IDs, or unbounded values in labels. Keep label values bounded (status codes, endpoint patterns, environments).

---

## Code Examples

### Deploying Prometheus with Helm

```bash
#!/bin/bash
set -euo pipefail

NS="deployforge"

# Add the prometheus-community Helm repo
helm repo add prometheus-community https://prometheus-community.github.io/helm-charts
helm repo update

# Install kube-prometheus-stack (Prometheus + Grafana + Alertmanager + exporters)
helm install prometheus prometheus-community/kube-prometheus-stack \
  --namespace monitoring \
  --create-namespace \
  --set prometheus.prometheusSpec.serviceMonitorSelectorNilUsesHelmValues=false \
  --set prometheus.prometheusSpec.podMonitorSelectorNilUsesHelmValues=false \
  --set grafana.adminPassword='deployforge-admin' \
  --set alertmanager.enabled=true \
  --wait

# Verify all components are running
kubectl get pods -n monitoring
# → prometheus-kube-prometheus-operator-xxx   Running
# → prometheus-prometheus-kube-prometheus-prometheus-0   Running
# → prometheus-grafana-xxx   Running
# → alertmanager-prometheus-kube-prometheus-alertmanager-0   Running
# → prometheus-kube-state-metrics-xxx   Running
# → prometheus-prometheus-node-exporter-xxx   Running

# Port-forward to access Prometheus UI
kubectl port-forward -n monitoring svc/prometheus-kube-prometheus-prometheus 9090:9090 &
echo "Prometheus UI: http://localhost:9090"

# Port-forward to access Grafana
kubectl port-forward -n monitoring svc/prometheus-grafana 3000:80 &
echo "Grafana UI: http://localhost:3000 (admin / deployforge-admin)"
```

### ServiceMonitor for DeployForge API Gateway

```yaml
# api-gateway-service.yaml — Service with named metrics port
apiVersion: v1
kind: Service
metadata:
  name: api-gateway
  namespace: deployforge
  labels:
    app: api-gateway
spec:
  selector:
    app: api-gateway
  ports:
  - name: http
    port: 3000
    targetPort: 3000
  - name: http-metrics
    port: 9090
    targetPort: 9090
---
# api-gateway-servicemonitor.yaml
apiVersion: monitoring.coreos.com/v1
kind: ServiceMonitor
metadata:
  name: api-gateway
  namespace: deployforge
  labels:
    release: prometheus
spec:
  selector:
    matchLabels:
      app: api-gateway
  endpoints:
  - port: http-metrics
    interval: 15s
    path: /metrics
  namespaceSelector:
    matchNames:
    - deployforge
```

### Verifying Metrics Are Flowing

```bash
# Check that Prometheus is scraping the target
kubectl port-forward -n monitoring svc/prometheus-kube-prometheus-prometheus 9090:9090 &

# Check targets via API
curl -s http://localhost:9090/api/v1/targets | jq '.data.activeTargets[] | select(.labels.service=="api-gateway") | {instance, health, lastScrape}'

# Query a metric
curl -s 'http://localhost:9090/api/v1/query?query=http_requests_total{service="api-gateway"}' | jq '.data.result'

# Verify kube-state-metrics is working
curl -s 'http://localhost:9090/api/v1/query?query=kube_deployment_status_replicas{namespace="deployforge"}' | jq '.data.result'
```

---

## Try It Yourself

### Challenge 1: Write PromQL for the RED Method

Write PromQL queries that compute all three RED signals for the DeployForge API Gateway: total request rate, error rate as a percentage, and p99 latency. Format them as recording rules.

<details>
<summary>Show solution</summary>

```yaml
# deployforge-red-rules.yaml
apiVersion: monitoring.coreos.com/v1
kind: PrometheusRule
metadata:
  name: deployforge-red-rules
  namespace: deployforge
  labels:
    release: prometheus
spec:
  groups:
  - name: deployforge.red
    interval: 30s
    rules:
    # Rate: total requests per second
    - record: deployforge:api_gateway:request_rate:5m
      expr: |
        sum(rate(http_requests_total{service="api-gateway"}[5m]))

    # Errors: 5xx error percentage
    - record: deployforge:api_gateway:error_percentage:5m
      expr: |
        100 * (
          sum(rate(http_requests_total{service="api-gateway", status_code=~"5.."}[5m]))
          /
          sum(rate(http_requests_total{service="api-gateway"}[5m]))
        )

    # Duration: p99 latency in seconds
    - record: deployforge:api_gateway:latency_p99:5m
      expr: |
        histogram_quantile(0.99,
          sum by (le) (rate(http_request_duration_seconds_bucket{service="api-gateway"}[5m]))
        )

    # Bonus: p50 and p95 for dashboard use
    - record: deployforge:api_gateway:latency_p50:5m
      expr: |
        histogram_quantile(0.50,
          sum by (le) (rate(http_request_duration_seconds_bucket{service="api-gateway"}[5m]))
        )

    - record: deployforge:api_gateway:latency_p95:5m
      expr: |
        histogram_quantile(0.95,
          sum by (le) (rate(http_request_duration_seconds_bucket{service="api-gateway"}[5m]))
        )
```

```bash
kubectl apply -f deployforge-red-rules.yaml

# Verify rules are loaded
curl -s http://localhost:9090/api/v1/rules | jq '.data.groups[] | select(.name=="deployforge.red") | .rules[].name'
# → deployforge:api_gateway:request_rate:5m
# → deployforge:api_gateway:error_percentage:5m
# → deployforge:api_gateway:latency_p99:5m
# → deployforge:api_gateway:latency_p50:5m
# → deployforge:api_gateway:latency_p95:5m
```

</details>

### Challenge 2: Detect a Cardinality Problem

You notice Prometheus memory usage is climbing. Write a PromQL query to find the top 10 metrics by cardinality (number of unique label combinations). Then explain how to fix a hypothetical `http_requests_total` metric that has a `user_id` label.

<details>
<summary>Show solution</summary>

```promql
# Find top 10 metrics by cardinality
topk(10, count by (__name__) ({__name__=~".+"}))

# Check cardinality of a specific metric
count(http_requests_total)

# See which label has the most unique values
count by (user_id) (http_requests_total)
# → If this returns thousands of series, user_id is the problem
```

**Fix:** Remove `user_id` from the metric labels. User-level tracking belongs in traces or logs, not metrics. If you need per-user rate limiting metrics, use a bounded label like `user_tier` (free, pro, enterprise) instead.

```typescript
// BAD — unbounded cardinality
httpRequestsTotal.inc({
  method: 'GET',
  endpoint: '/api/deployments',
  user_id: req.userId,  // ✗ thousands of unique values
});

// GOOD — bounded cardinality
httpRequestsTotal.inc({
  method: 'GET',
  endpoint: '/api/deployments',
  user_tier: req.userTier,  // ✓ 3-5 unique values
});
```

</details>

### Challenge 3: Deploy node-exporter and kube-state-metrics Checks

Write PromQL queries that would alert on: (a) a node with >90% memory utilization, (b) a Deployment with fewer ready replicas than desired, and (c) a pod that has restarted more than 5 times in 30 minutes.

<details>
<summary>Show solution</summary>

```yaml
# infrastructure-alerts.yaml
apiVersion: monitoring.coreos.com/v1
kind: PrometheusRule
metadata:
  name: deployforge-infra-alerts
  namespace: deployforge
  labels:
    release: prometheus
spec:
  groups:
  - name: deployforge.infrastructure
    rules:
    # (a) Node memory > 90%
    - alert: NodeMemoryHigh
      expr: |
        (1 - (node_memory_MemAvailable_bytes / node_memory_MemTotal_bytes)) > 0.9
      for: 5m
      labels:
        severity: warning
      annotations:
        summary: "Node {{ $labels.instance }} memory above 90%"
        description: "Memory utilization is {{ $value | humanizePercentage }} for 5+ minutes."
        runbook_url: "https://wiki.deployforge.dev/runbooks/node-memory-high"

    # (b) Deployment replicas mismatch
    - alert: DeploymentReplicasMismatch
      expr: |
        kube_deployment_spec_replicas{namespace="deployforge"}
        !=
        kube_deployment_status_ready_replicas{namespace="deployforge"}
      for: 10m
      labels:
        severity: critical
      annotations:
        summary: "Deployment {{ $labels.deployment }} has replica mismatch"
        description: "Desired: {{ with printf `kube_deployment_spec_replicas{deployment='%s'}` $labels.deployment | query }}{{ . | first | value }}{{ end }}, Ready: {{ $value }}"

    # (c) Pod restart storm
    - alert: PodRestartStorm
      expr: |
        increase(kube_pod_container_status_restarts_total{namespace="deployforge"}[30m]) > 5
      for: 0m
      labels:
        severity: warning
      annotations:
        summary: "Pod {{ $labels.pod }} restarting frequently"
        description: "{{ $labels.pod }} has restarted {{ $value }} times in 30 minutes."
```

```bash
kubectl apply -f infrastructure-alerts.yaml

# Verify alert rules are loaded
curl -s http://localhost:9090/api/v1/rules?type=alert | \
  jq '.data.groups[] | select(.name=="deployforge.infrastructure") | .rules[].name'
# → NodeMemoryHigh
# → DeploymentReplicasMismatch
# → PodRestartStorm
```

</details>

---

## Capstone Connection

**DeployForge** uses Prometheus as the metrics backbone for the entire platform:

- **API Gateway metrics** — The Express/TypeScript API Gateway exposes RED metrics (`http_requests_total`, `http_request_duration_seconds`) via `prom-client` on a dedicated `/metrics` endpoint. A ServiceMonitor configures Prometheus to scrape it every 15 seconds.
- **Worker Service metrics** — The BullMQ Worker exposes job processing metrics: `deployforge_jobs_total` (counter by status), `deployforge_job_duration_seconds` (histogram), and `deployforge_queue_depth` (gauge). These drive alerting on job backlog growth.
- **Infrastructure exporters** — `node-exporter` and `kube-state-metrics` provide USE method coverage. `postgres_exporter` and `redis_exporter` run as sidecars, exposing database-specific metrics (connection pool usage, replication lag, cache hit ratio).
- **Recording rules** — Pre-computed RED and USE metrics power Grafana dashboards without expensive real-time queries. Naming follows the `level:metric:operations` convention.
- **Alerting foundation** — PrometheusRules define alerts for error rate spikes, latency degradation, replica mismatches, and resource pressure. In Module 08.4, you'll route these alerts through Alertmanager to Slack and PagerDuty.
- **Cardinality discipline** — All application metrics use bounded label sets (method, endpoint pattern, status class, environment). User-level and request-level tracking is delegated to traces and logs.
