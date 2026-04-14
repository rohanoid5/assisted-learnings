# 8.4 — Grafana Dashboards & Alerting

## Concept

Metrics, traces, and logs are useless if nobody looks at them. Grafana is the visualization layer that transforms raw observability data into actionable insight. A well-designed dashboard answers the question "Is my system healthy?" in under 5 seconds. A poorly designed one creates 30 panels of meaningless charts that nobody reads.

This section covers dashboard design principles, Grafana-specific features (variables, annotations, alert rules), Prometheus alerting rules, Alertmanager configuration for notification routing, and — critically — how to avoid alert fatigue. The goal isn't "monitor everything" — it's "alert on what matters and make dashboards that tell a story."

---

## Deep Dive

### Dashboard Design Principles

The best dashboards follow a visual hierarchy — from high-level health at the top to detailed breakdowns at the bottom. A senior engineer should be able to assess system status in the first glance.

```
┌─────────────────────────────────────────────────────────────────────┐
│               Dashboard Layout: Service Health (RED)                 │
│                                                                     │
│  ┌────── Row 1: Golden Signals (stat panels) ────────────────────┐ │
│  │  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐     │ │
│  │  │ Req Rate │  │ Error %  │  │ p50 Lat  │  │ p99 Lat  │     │ │
│  │  │ 1,234/s  │  │  0.12%   │  │  12ms    │  │  145ms   │     │ │
│  │  │    ✓     │  │    ✓     │  │    ✓     │  │    ⚠     │     │ │
│  │  └──────────┘  └──────────┘  └──────────┘  └──────────┘     │ │
│  └───────────────────────────────────────────────────────────────┘ │
│                                                                     │
│  ┌────── Row 2: Time-series graphs ──────────────────────────────┐ │
│  │  ┌─────────────────────────┐  ┌─────────────────────────────┐ │ │
│  │  │ Request Rate by Status  │  │ Latency Distribution (p50,  │ │ │
│  │  │ (stacked area chart)    │  │  p95, p99 line chart)       │ │ │
│  │  │                         │  │                             │ │ │
│  │  │  2xx ████████████       │  │  p99 ─────────────╱        │ │ │
│  │  │  4xx ██                 │  │  p95 ───────────╱──        │ │ │
│  │  │  5xx █                  │  │  p50 ──────────────         │ │ │
│  │  └─────────────────────────┘  └─────────────────────────────┘ │ │
│  └───────────────────────────────────────────────────────────────┘ │
│                                                                     │
│  ┌────── Row 3: Breakdown by endpoint ───────────────────────────┐ │
│  │  ┌─────────────────────────────────────────────────────────┐   │ │
│  │  │ Top Endpoints by Error Rate (table)                     │   │ │
│  │  │ Endpoint         | Req/s | Err % | p99    | Status     │   │ │
│  │  │ POST /deploy     | 45.2  | 0.5%  | 230ms  | ⚠         │   │ │
│  │  │ GET /health      | 800.1 | 0.0%  | 2ms    | ✓         │   │ │
│  │  │ GET /deployments | 120.3 | 0.1%  | 45ms   | ✓         │   │ │
│  │  └─────────────────────────────────────────────────────────┘   │ │
│  └───────────────────────────────────────────────────────────────┘ │
│                                                                     │
│  ┌────── Row 4: Infrastructure (USE) ────────────────────────────┐ │
│  │  ┌──────────────────┐  ┌──────────────────┐  ┌────────────┐  │ │
│  │  │ CPU Utilization   │  │ Memory Usage      │  │ Pod Status │  │ │
│  │  │ per pod (gauge)   │  │ per pod (gauge)   │  │ (stat)     │  │ │
│  │  └──────────────────┘  └──────────────────┘  └────────────┘  │ │
│  └───────────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────────┘
```

**Principles:**

1. **Answer one question per dashboard.** "Is the API Gateway healthy?" is one dashboard. "What's the infrastructure status?" is another. Don't combine.
2. **Top-down hierarchy.** Stat panels at the top (current numbers + threshold colors), time-series in the middle (trends), tables at the bottom (details).
3. **Use color meaningfully.** Green = healthy, yellow = degraded, red = critical. Never use color decoratively.
4. **Include alert thresholds on graphs.** A horizontal line at the alert threshold lets on-call engineers see how close they are to paging.
5. **Link dashboards.** The overview dashboard links to per-service dashboards. Per-service dashboards link to Jaeger traces and Loki logs.

---

### Grafana Variables

Template variables make dashboards reusable. Instead of hardcoding `api-gateway`, use a variable `$service` that lets users switch between services.

```json
{
  "templating": {
    "list": [
      {
        "name": "namespace",
        "type": "query",
        "query": "label_values(kube_pod_info, namespace)",
        "current": { "text": "deployforge", "value": "deployforge" },
        "refresh": 2
      },
      {
        "name": "service",
        "type": "query",
        "query": "label_values(http_requests_total{namespace=\"$namespace\"}, service)",
        "refresh": 2,
        "multi": true,
        "includeAll": true
      },
      {
        "name": "interval",
        "type": "interval",
        "query": "1m,5m,15m,1h",
        "current": { "text": "5m", "value": "5m" }
      }
    ]
  }
}
```

Use variables in panel queries:
```promql
# Request rate for selected service(s)
sum by (service) (rate(http_requests_total{namespace="$namespace", service=~"$service"}[$interval]))

# Error rate for selected service(s)
sum(rate(http_requests_total{namespace="$namespace", service=~"$service", status_code=~"5.."}[$interval]))
/
sum(rate(http_requests_total{namespace="$namespace", service=~"$service"}[$interval]))
```

---

### Annotations

Annotations mark events on graphs — deployments, config changes, incidents. They provide context for "why did the graph change at this point?"

```promql
# Annotation query: show deployment events
# In Grafana, add an annotation with:
# Data source: Prometheus
# Query: changes(kube_deployment_status_observed_generation{namespace="deployforge"}[1m]) > 0
# Title: Deployment updated
# Tags: deployment

# Or from Loki — show deployment logs as annotations
# Data source: Loki
# Query: {namespace="deployforge"} |= "deployment_started" | json
```

> **Key insight:** Annotations turn "the latency spiked at 14:32" into "the latency spiked at 14:32, right after deployment v1.2.3 rolled out." This is the difference between "let me check what changed" (10 minutes) and "I see what happened" (10 seconds).

---

### Prometheus Alerting Rules

Alerting rules are evaluated by Prometheus and sent to Alertmanager. They define _when_ to fire, _how long_ to wait, and _what metadata_ to attach.

```yaml
# deployforge-alert-rules.yaml
apiVersion: monitoring.coreos.com/v1
kind: PrometheusRule
metadata:
  name: deployforge-service-alerts
  namespace: deployforge
  labels:
    release: prometheus
spec:
  groups:
  - name: deployforge.api-gateway
    rules:
    # High error rate — page immediately
    - alert: APIGatewayHighErrorRate
      expr: |
        deployforge:api_gateway:error_rate:5m > 0.05
      for: 2m
      labels:
        severity: critical
        service: api-gateway
        team: platform
      annotations:
        summary: "API Gateway error rate above 5%"
        description: |
          Error rate is {{ $value | humanizePercentage }} for the last 2 minutes.
          This affects user-facing traffic.
        runbook_url: "https://wiki.deployforge.dev/runbooks/api-gateway-errors"
        dashboard_url: "https://grafana.deployforge.dev/d/api-gw/api-gateway?orgId=1"

    # High latency — warn
    - alert: APIGatewayHighLatency
      expr: |
        deployforge:api_gateway:latency_p99:5m > 1.0
      for: 5m
      labels:
        severity: warning
        service: api-gateway
        team: platform
      annotations:
        summary: "API Gateway p99 latency above 1s"
        description: |
          p99 latency is {{ $value | humanizeDuration }} for the last 5 minutes.
          Check downstream dependencies (PostgreSQL, Redis).
        runbook_url: "https://wiki.deployforge.dev/runbooks/api-gateway-latency"

    # Traffic drop — possible upstream issue
    - alert: APIGatewayTrafficDrop
      expr: |
        deployforge:api_gateway:request_rate:5m
        < (deployforge:api_gateway:request_rate:5m offset 1h) * 0.5
      for: 10m
      labels:
        severity: warning
        service: api-gateway
        team: platform
      annotations:
        summary: "API Gateway traffic dropped by 50%+"
        description: "Current rate: {{ $value }}/s. Check upstream load balancer and DNS."

  - name: deployforge.worker
    rules:
    - alert: WorkerJobBacklog
      expr: |
        deployforge_queue_depth{queue="deploy"} > 100
      for: 5m
      labels:
        severity: warning
        service: worker
        team: platform
      annotations:
        summary: "Deploy queue backlog above 100 jobs"
        description: |
          Queue depth: {{ $value }}. Worker may be under-scaled or stuck.
          Check for failed jobs and worker pod health.
        runbook_url: "https://wiki.deployforge.dev/runbooks/worker-backlog"

    - alert: WorkerJobFailureRate
      expr: |
        rate(deployforge_jobs_total{status="failed"}[5m])
        /
        rate(deployforge_jobs_total[5m]) > 0.1
      for: 5m
      labels:
        severity: critical
        service: worker
        team: platform
      annotations:
        summary: "Worker job failure rate above 10%"
        description: "{{ $value | humanizePercentage }} of jobs are failing."
```

---

### Alertmanager Configuration

Alertmanager receives alerts from Prometheus and routes them to the right notification channel. The key concepts are **routing trees**, **grouping**, **inhibition**, and **silencing**.

```yaml
# alertmanager-config.yaml
apiVersion: v1
kind: Secret
metadata:
  name: alertmanager-prometheus-kube-prometheus-alertmanager
  namespace: monitoring
stringData:
  alertmanager.yaml: |
    global:
      resolve_timeout: 5m

    route:
      # Default receiver
      receiver: 'slack-platform'
      # Group alerts by these labels — one notification per group
      group_by: ['alertname', 'service']
      # Wait before sending first notification (batch alerts)
      group_wait: 30s
      # Wait before sending updated notification
      group_interval: 5m
      # Wait before re-sending a firing alert
      repeat_interval: 4h

      routes:
      # Critical alerts → PagerDuty + Slack
      - match:
          severity: critical
        receiver: 'pagerduty-platform'
        continue: true  # also send to default (Slack)

      # Worker-specific alerts → worker team channel
      - match:
          service: worker
        receiver: 'slack-worker-team'

    receivers:
    - name: 'slack-platform'
      slack_configs:
      - api_url: 'https://hooks.slack.com/services/T00/B00/XXXX'
        channel: '#platform-alerts'
        title: '{{ .GroupLabels.alertname }}'
        text: |
          *Service:* {{ .GroupLabels.service }}
          *Severity:* {{ .CommonLabels.severity }}
          {{ range .Alerts }}
          • {{ .Annotations.summary }}
            {{ .Annotations.description }}
            <{{ .Annotations.runbook_url }}|Runbook> | <{{ .Annotations.dashboard_url }}|Dashboard>
          {{ end }}
        send_resolved: true

    - name: 'pagerduty-platform'
      pagerduty_configs:
      - routing_key: '<pagerduty-integration-key>'
        severity: '{{ .CommonLabels.severity }}'
        description: '{{ .CommonAnnotations.summary }}'
        details:
          service: '{{ .CommonLabels.service }}'
          runbook: '{{ .CommonAnnotations.runbook_url }}'

    - name: 'slack-worker-team'
      slack_configs:
      - api_url: 'https://hooks.slack.com/services/T00/B00/YYYY'
        channel: '#worker-alerts'

    # Inhibition: critical alerts suppress warnings for same service
    inhibit_rules:
    - source_match:
        severity: critical
      target_match:
        severity: warning
      equal: ['service']
```

> **Production note:** The `inhibit_rules` section is critical for alert fatigue prevention. When a critical alert fires for a service, it suppresses all warning alerts for the same service. You don't need both "error rate is 50%" (critical) and "latency is high" (warning) — the critical alert is enough.

---

### Alert Fatigue Prevention

Alert fatigue is when engineers stop responding to alerts because there are too many. It's the #1 killer of on-call culture.

| Anti-Pattern | Fix |
|-------------|-----|
| Alert on every metric | Only alert on customer-impacting symptoms, not causes |
| Too-low thresholds | Set thresholds based on SLO budgets, not gut feeling |
| No `for` duration | Always require sustained failure (2-5 min minimum) |
| Missing runbooks | Every alert must link to a runbook with clear action steps |
| No severity levels | Use `critical` (page) vs `warning` (review next business day) |
| Alerting on causes | Alert on "error rate > 5%" not "CPU > 80%". CPU can be high and fine. |
| Duplicate alerts | Use inhibition rules to suppress redundant alerts |
| No alert review process | Monthly review: delete alerts nobody acted on |

```
┌─────────────────────────────────────────────────────────────────────┐
│               Alert Severity Decision Tree                           │
│                                                                     │
│  Is it customer-impacting RIGHT NOW?                                │
│  │                                                                  │
│  ├── YES → Is data loss or security at risk?                        │
│  │         │                                                        │
│  │         ├── YES → CRITICAL (page immediately)                    │
│  │         │         Examples: error rate > 5%, data corruption      │
│  │         │                                                        │
│  │         └── NO  → CRITICAL (page in business hours)              │
│  │                   Examples: elevated latency, partial outage     │
│  │                                                                  │
│  └── NO  → Will it become customer-impacting if unchecked?          │
│            │                                                        │
│            ├── YES → WARNING (ticket, review within 24h)            │
│            │         Examples: disk 80%, replica mismatch            │
│            │                                                        │
│            └── NO  → INFO (dashboard only, no notification)         │
│                      Examples: CPU spike during deploy, GC pause    │
└─────────────────────────────────────────────────────────────────────┘
```

---

### SLO Dashboards

An SLO (Service Level Objective) dashboard shows how much error budget remains. Instead of "is it broken now?" it answers "are we on track to meet our reliability target?"

```promql
# SLO: 99.9% availability (43.2 minutes of downtime per 30 days)
# Error budget: 0.1% of requests can fail

# Error budget consumed (30-day rolling)
1 - (
  sum(increase(http_requests_total{service="api-gateway", status_code!~"5.."}[30d]))
  /
  sum(increase(http_requests_total{service="api-gateway"}[30d]))
) / 0.001

# Burn rate — how fast are we consuming budget?
# If burn rate > 1, we'll exhaust the budget before the window ends
(
  sum(rate(http_requests_total{service="api-gateway", status_code=~"5.."}[1h]))
  /
  sum(rate(http_requests_total{service="api-gateway"}[1h]))
) / 0.001

# Multi-window burn rate alert (Google SRE book approach)
# Fast burn: 14.4x over 1h AND 14.4x over 5m → page
# Slow burn: 6x over 6h AND 6x over 30m → ticket
```

```
┌─────────────────────────────────────────────────────────────────────┐
│                SLO Dashboard Layout                                  │
│                                                                     │
│  ┌──── SLO: API Gateway Availability ───────────────────────────┐  │
│  │                                                               │  │
│  │  Target: 99.9%    Current: 99.94%    Budget Remaining: 62%    │  │
│  │  ┌─────────────────────────────────────────┐                  │  │
│  │  │ ████████████████████████████░░░░░░░░░░░ │  62% remaining   │  │
│  │  └─────────────────────────────────────────┘                  │  │
│  │                                                               │  │
│  │  Burn rate (1h): 0.8x    Status: ✓ On track                  │  │
│  │  Burn rate (6h): 0.3x    Status: ✓ Healthy                   │  │
│  │                                                               │  │
│  │  Error budget consumed over time:                             │  │
│  │  100% ─────────────────────────────────────── budget limit    │  │
│  │                                           ╱                   │  │
│  │                                     ╱────                     │  │
│  │                               ╱────                           │  │
│  │   0% ─────────────╱─────────                                  │  │
│  │       Day 1      Day 10     Day 20      Day 30                │  │
│  └───────────────────────────────────────────────────────────────┘  │
│                                                                     │
│  ┌──── SLO: API Gateway Latency ────────────────────────────────┐  │
│  │  Target: p99 < 500ms for 99% of time                          │  │
│  │  Current: 99.7%    Budget Remaining: 70%                      │  │
│  └───────────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────┘
```

---

### Grafana Data Source Configuration

Connect Grafana to all three observability backends:

```yaml
# grafana-datasources.yaml — provision via ConfigMap or Helm values
apiVersion: v1
kind: ConfigMap
metadata:
  name: grafana-datasources
  namespace: monitoring
  labels:
    grafana_datasource: "true"
data:
  datasources.yaml: |
    apiVersion: 1
    datasources:
    - name: Prometheus
      type: prometheus
      url: http://prometheus-kube-prometheus-prometheus.monitoring:9090
      isDefault: true
      access: proxy
      jsonData:
        timeInterval: "15s"

    - name: Loki
      type: loki
      url: http://loki.deployforge:3100
      access: proxy
      jsonData:
        derivedFields:
        - name: TraceID
          datasourceUid: jaeger
          matcherRegex: '"trace_id":"([a-f0-9]+)"'
          url: '$${__value.raw}'

    - name: Jaeger
      uid: jaeger
      type: jaeger
      url: http://jaeger-query.deployforge:16686
      access: proxy
```

> **Key insight:** The `derivedFields` configuration in the Loki datasource is what enables one-click trace-to-log correlation. When you view a log line containing a `trace_id`, Grafana renders it as a clickable link that opens the full trace in Jaeger.

---

### Dashboard as Code

Store dashboards in version control using Grafana's JSON model. Deploy them via ConfigMaps or the Grafana provisioning API.

```yaml
# grafana-dashboard-configmap.yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: deployforge-dashboards
  namespace: monitoring
  labels:
    grafana_dashboard: "true"
data:
  api-gateway-red.json: |
    {
      "dashboard": {
        "title": "DeployForge — API Gateway (RED)",
        "uid": "deployforge-api-gw-red",
        "tags": ["deployforge", "api-gateway", "red"],
        "templating": {
          "list": [
            {
              "name": "interval",
              "type": "interval",
              "query": "1m,5m,15m,1h",
              "current": { "text": "5m", "value": "5m" }
            }
          ]
        },
        "panels": [
          {
            "title": "Request Rate",
            "type": "stat",
            "gridPos": { "h": 4, "w": 6, "x": 0, "y": 0 },
            "targets": [{
              "expr": "sum(rate(http_requests_total{service=\"api-gateway\"}[$interval]))",
              "legendFormat": "req/s"
            }],
            "fieldConfig": {
              "defaults": {
                "unit": "reqps",
                "thresholds": {
                  "steps": [
                    { "color": "green", "value": null },
                    { "color": "yellow", "value": 0 }
                  ]
                }
              }
            }
          },
          {
            "title": "Error Rate",
            "type": "stat",
            "gridPos": { "h": 4, "w": 6, "x": 6, "y": 0 },
            "targets": [{
              "expr": "100 * sum(rate(http_requests_total{service=\"api-gateway\", status_code=~\"5..\"}[$interval])) / sum(rate(http_requests_total{service=\"api-gateway\"}[$interval]))",
              "legendFormat": "error %"
            }],
            "fieldConfig": {
              "defaults": {
                "unit": "percent",
                "thresholds": {
                  "steps": [
                    { "color": "green", "value": null },
                    { "color": "yellow", "value": 1 },
                    { "color": "red", "value": 5 }
                  ]
                }
              }
            }
          },
          {
            "title": "p99 Latency",
            "type": "stat",
            "gridPos": { "h": 4, "w": 6, "x": 12, "y": 0 },
            "targets": [{
              "expr": "histogram_quantile(0.99, sum by (le) (rate(http_request_duration_seconds_bucket{service=\"api-gateway\"}[$interval])))",
              "legendFormat": "p99"
            }],
            "fieldConfig": {
              "defaults": {
                "unit": "s",
                "thresholds": {
                  "steps": [
                    { "color": "green", "value": null },
                    { "color": "yellow", "value": 0.5 },
                    { "color": "red", "value": 1.0 }
                  ]
                }
              }
            }
          }
        ]
      }
    }
```

---

## Code Examples

### Complete Observability Stack Deployment

```bash
#!/bin/bash
set -euo pipefail

NS="deployforge"

echo "=== Deploy Complete Observability Stack ==="

# 1. Prometheus + Grafana + Alertmanager
helm repo add prometheus-community https://prometheus-community.github.io/helm-charts
helm repo add grafana https://grafana.github.io/helm-charts
helm repo update

helm upgrade --install prometheus prometheus-community/kube-prometheus-stack \
  --namespace monitoring --create-namespace \
  --set prometheus.prometheusSpec.serviceMonitorSelectorNilUsesHelmValues=false \
  --set grafana.adminPassword='deployforge-admin' \
  --set grafana.sidecar.dashboards.enabled=true \
  --set grafana.sidecar.dashboards.label=grafana_dashboard \
  --set grafana.sidecar.datasources.enabled=true \
  --set grafana.sidecar.datasources.label=grafana_datasource \
  --wait

# 2. Loki (log storage)
helm upgrade --install loki grafana/loki-stack \
  --namespace $NS \
  --set loki.persistence.enabled=false \
  --set promtail.enabled=false \
  --set grafana.enabled=false \
  --wait

# 3. Apply datasource config (connects Grafana to Loki + Jaeger)
kubectl apply -f grafana-datasources.yaml

# 4. Apply dashboard ConfigMaps
kubectl apply -f grafana-dashboard-configmap.yaml

# 5. Apply alert rules
kubectl apply -f deployforge-alert-rules.yaml

echo ""
echo "=== Access URLs ==="
echo "Grafana:      kubectl port-forward -n monitoring svc/prometheus-grafana 3000:80"
echo "              http://localhost:3000 (admin / deployforge-admin)"
echo "Prometheus:   kubectl port-forward -n monitoring svc/prometheus-kube-prometheus-prometheus 9090:9090"
echo "              http://localhost:9090"
echo "Alertmanager: kubectl port-forward -n monitoring svc/prometheus-kube-prometheus-alertmanager 9093:9093"
echo "              http://localhost:9093"
echo "Jaeger:       kubectl port-forward -n deployforge svc/jaeger-query 16686:16686"
echo "              http://localhost:16686"
```

### Verifying the Full Observability Pipeline

```bash
#!/bin/bash
set -euo pipefail

echo "=== Verify Metrics Pipeline ==="
# Check Prometheus targets
kubectl port-forward -n monitoring svc/prometheus-kube-prometheus-prometheus 9090:9090 &
sleep 3
TARGETS=$(curl -s http://localhost:9090/api/v1/targets | jq '.data.activeTargets | length')
echo "Active Prometheus targets: $TARGETS"

# Check a recording rule
curl -s 'http://localhost:9090/api/v1/query?query=deployforge:api_gateway:request_rate:5m' | \
  jq '.data.result[0].value[1]'

echo ""
echo "=== Verify Tracing Pipeline ==="
# Check Jaeger has traces
kubectl port-forward -n deployforge svc/jaeger-query 16686:16686 &
sleep 3
curl -s 'http://localhost:16686/api/services' | jq '.data'

echo ""
echo "=== Verify Logging Pipeline ==="
# Check Loki has logs
kubectl port-forward -n deployforge svc/loki 3100:3100 &
sleep 3
curl -s 'http://localhost:3100/loki/api/v1/label' | jq '.data'

echo ""
echo "=== Verify Alerting Pipeline ==="
# Check alert rules are loaded
RULES=$(curl -s http://localhost:9090/api/v1/rules?type=alert | jq '.data.groups | length')
echo "Alert rule groups: $RULES"

# Check Alertmanager is receiving
kubectl port-forward -n monitoring svc/prometheus-kube-prometheus-alertmanager 9093:9093 &
sleep 3
curl -s http://localhost:9093/api/v2/status | jq '.config.original'
```

---

## Try It Yourself

### Challenge 1: Build a RED Dashboard from Scratch

Using the Grafana UI (http://localhost:3000), create a dashboard for the DeployForge API Gateway that includes: stat panels for request rate, error percentage, and p99 latency; a time-series panel showing request rate by status class; and a table showing the top 5 endpoints by error rate.

<details>
<summary>Show solution</summary>

```json
{
  "dashboard": {
    "title": "DeployForge — API Gateway (RED) — Exercise",
    "uid": "ex-api-gw-red",
    "tags": ["deployforge", "exercise"],
    "time": { "from": "now-1h", "to": "now" },
    "templating": {
      "list": [{
        "name": "interval",
        "type": "interval",
        "query": "1m,5m,15m",
        "current": { "text": "5m", "value": "5m" }
      }]
    },
    "panels": [
      {
        "title": "Request Rate",
        "type": "stat",
        "gridPos": { "h": 4, "w": 6, "x": 0, "y": 0 },
        "targets": [{
          "expr": "sum(rate(http_requests_total{service=\"api-gateway\"}[$interval]))",
          "legendFormat": "req/s"
        }],
        "fieldConfig": { "defaults": { "unit": "reqps" } }
      },
      {
        "title": "Error %",
        "type": "stat",
        "gridPos": { "h": 4, "w": 6, "x": 6, "y": 0 },
        "targets": [{
          "expr": "100 * sum(rate(http_requests_total{service=\"api-gateway\",status_code=~\"5..\"}[$interval])) / sum(rate(http_requests_total{service=\"api-gateway\"}[$interval]))",
          "legendFormat": "error %"
        }],
        "fieldConfig": {
          "defaults": {
            "unit": "percent",
            "thresholds": { "steps": [
              { "color": "green", "value": null },
              { "color": "yellow", "value": 1 },
              { "color": "red", "value": 5 }
            ]}
          }
        }
      },
      {
        "title": "p99 Latency",
        "type": "stat",
        "gridPos": { "h": 4, "w": 6, "x": 12, "y": 0 },
        "targets": [{
          "expr": "histogram_quantile(0.99, sum by (le) (rate(http_request_duration_seconds_bucket{service=\"api-gateway\"}[$interval])))",
          "legendFormat": "p99"
        }],
        "fieldConfig": {
          "defaults": {
            "unit": "s",
            "thresholds": { "steps": [
              { "color": "green", "value": null },
              { "color": "yellow", "value": 0.5 },
              { "color": "red", "value": 1.0 }
            ]}
          }
        }
      },
      {
        "title": "Requests by Status Class",
        "type": "timeseries",
        "gridPos": { "h": 8, "w": 12, "x": 0, "y": 4 },
        "targets": [{
          "expr": "sum by (status_class) (rate(http_requests_total{service=\"api-gateway\"}[$interval]))",
          "legendFormat": "{{ status_class }}"
        }],
        "fieldConfig": {
          "defaults": { "unit": "reqps" },
          "overrides": [
            { "matcher": { "id": "byName", "options": "5xx" }, "properties": [{ "id": "color", "value": { "fixedColor": "red", "mode": "fixed" } }] },
            { "matcher": { "id": "byName", "options": "4xx" }, "properties": [{ "id": "color", "value": { "fixedColor": "yellow", "mode": "fixed" } }] },
            { "matcher": { "id": "byName", "options": "2xx" }, "properties": [{ "id": "color", "value": { "fixedColor": "green", "mode": "fixed" } }] }
          ]
        },
        "options": { "tooltip": { "mode": "multi" } }
      },
      {
        "title": "Latency Percentiles",
        "type": "timeseries",
        "gridPos": { "h": 8, "w": 12, "x": 12, "y": 4 },
        "targets": [
          { "expr": "histogram_quantile(0.50, sum by (le) (rate(http_request_duration_seconds_bucket{service=\"api-gateway\"}[$interval])))", "legendFormat": "p50" },
          { "expr": "histogram_quantile(0.95, sum by (le) (rate(http_request_duration_seconds_bucket{service=\"api-gateway\"}[$interval])))", "legendFormat": "p95" },
          { "expr": "histogram_quantile(0.99, sum by (le) (rate(http_request_duration_seconds_bucket{service=\"api-gateway\"}[$interval])))", "legendFormat": "p99" }
        ],
        "fieldConfig": { "defaults": { "unit": "s" } }
      },
      {
        "title": "Top Endpoints by Error Rate",
        "type": "table",
        "gridPos": { "h": 8, "w": 24, "x": 0, "y": 12 },
        "targets": [{
          "expr": "topk(5, sum by (endpoint) (rate(http_requests_total{service=\"api-gateway\",status_code=~\"5..\"}[$interval])) / sum by (endpoint) (rate(http_requests_total{service=\"api-gateway\"}[$interval])))",
          "legendFormat": "{{ endpoint }}",
          "instant": true,
          "format": "table"
        }],
        "fieldConfig": {
          "defaults": { "unit": "percentunit" }
        }
      }
    ]
  }
}
```

Import via Grafana UI: Dashboards → Import → paste JSON.

</details>

### Challenge 2: Configure Multi-Window Burn Rate Alerts

Implement the Google SRE multi-window burn rate alerting strategy for a 99.9% availability SLO. Create alerts for: fast burn (14.4× over 1h AND 5m) and slow burn (6× over 6h AND 30m).

<details>
<summary>Show solution</summary>

```yaml
# slo-burn-rate-alerts.yaml
apiVersion: monitoring.coreos.com/v1
kind: PrometheusRule
metadata:
  name: deployforge-slo-alerts
  namespace: deployforge
  labels:
    release: prometheus
spec:
  groups:
  - name: deployforge.slo.burn-rate
    rules:
    # Recording rules for error ratios at different windows
    - record: deployforge:api_gateway:error_ratio:5m
      expr: |
        sum(rate(http_requests_total{service="api-gateway", status_code=~"5.."}[5m]))
        / sum(rate(http_requests_total{service="api-gateway"}[5m]))

    - record: deployforge:api_gateway:error_ratio:30m
      expr: |
        sum(rate(http_requests_total{service="api-gateway", status_code=~"5.."}[30m]))
        / sum(rate(http_requests_total{service="api-gateway"}[30m]))

    - record: deployforge:api_gateway:error_ratio:1h
      expr: |
        sum(rate(http_requests_total{service="api-gateway", status_code=~"5.."}[1h]))
        / sum(rate(http_requests_total{service="api-gateway"}[1h]))

    - record: deployforge:api_gateway:error_ratio:6h
      expr: |
        sum(rate(http_requests_total{service="api-gateway", status_code=~"5.."}[6h]))
        / sum(rate(http_requests_total{service="api-gateway"}[6h]))

    # SLO: 99.9% → error budget: 0.1% = 0.001
    # Fast burn: 14.4x burn rate exhausts 30-day budget in 2 days
    - alert: SLOFastBurn
      expr: |
        deployforge:api_gateway:error_ratio:1h > (14.4 * 0.001)
        and
        deployforge:api_gateway:error_ratio:5m > (14.4 * 0.001)
      for: 2m
      labels:
        severity: critical
        slo: availability
        window: fast
      annotations:
        summary: "SLO fast burn: API Gateway availability budget exhausting rapidly"
        description: |
          1h error ratio: {{ with printf `deployforge:api_gateway:error_ratio:1h` | query }}{{ . | first | value | humanizePercentage }}{{ end }}
          At this rate, the 30-day error budget will be exhausted in ~2 days.
        runbook_url: "https://wiki.deployforge.dev/runbooks/slo-fast-burn"

    # Slow burn: 6x burn rate exhausts 30-day budget in 5 days
    - alert: SLOSlowBurn
      expr: |
        deployforge:api_gateway:error_ratio:6h > (6 * 0.001)
        and
        deployforge:api_gateway:error_ratio:30m > (6 * 0.001)
      for: 5m
      labels:
        severity: warning
        slo: availability
        window: slow
      annotations:
        summary: "SLO slow burn: API Gateway availability budget eroding"
        description: |
          6h error ratio: {{ with printf `deployforge:api_gateway:error_ratio:6h` | query }}{{ . | first | value | humanizePercentage }}{{ end }}
          At this rate, the 30-day error budget will be exhausted in ~5 days.
        runbook_url: "https://wiki.deployforge.dev/runbooks/slo-slow-burn"
```

```bash
kubectl apply -f slo-burn-rate-alerts.yaml

# Verify
curl -s http://localhost:9090/api/v1/rules?type=alert | \
  jq '.data.groups[] | select(.name=="deployforge.slo.burn-rate") | .rules[] | select(.type=="alerting") | .name'
# → SLOFastBurn
# → SLOSlowBurn
```

</details>

### Challenge 3: Create a Runbook-Linked Alert

Write a complete PrometheusRule for PostgreSQL connection pool exhaustion that includes: a meaningful `for` duration, severity labels, an annotation with a runbook URL, and a dashboard link. Then write the first three steps of the runbook.

<details>
<summary>Show solution</summary>

```yaml
# postgres-connection-alert.yaml
apiVersion: monitoring.coreos.com/v1
kind: PrometheusRule
metadata:
  name: deployforge-postgres-alerts
  namespace: deployforge
  labels:
    release: prometheus
spec:
  groups:
  - name: deployforge.postgres
    rules:
    - alert: PostgresConnectionPoolExhaustion
      expr: |
        pg_stat_activity_count{datname="deployforge"}
        /
        pg_settings_max_connections > 0.85
      for: 5m
      labels:
        severity: critical
        service: postgresql
        team: platform
      annotations:
        summary: "PostgreSQL connection pool >85% utilized"
        description: |
          {{ $value | humanizePercentage }} of max connections in use for 5+ minutes.
          Active connections: {{ with printf `pg_stat_activity_count{datname="deployforge"}` | query }}{{ . | first | value }}{{ end }}
          Max connections: {{ with printf `pg_settings_max_connections` | query }}{{ . | first | value }}{{ end }}
        runbook_url: "https://wiki.deployforge.dev/runbooks/postgres-connection-exhaustion"
        dashboard_url: "https://grafana.deployforge.dev/d/pg-overview/postgresql-overview"
```

**Runbook: PostgreSQL Connection Pool Exhaustion**

```markdown
## Runbook: postgres-connection-exhaustion

### 1. Assess impact
- Check if API Gateway is returning 5xx errors (connection refused)
- Check dashboard: are connections growing or steady?
- Determine if this is a spike (leak) or gradual growth (undersized pool)

### 2. Identify the source
kubectl exec -n deployforge postgres-0 -- psql -U deployforge_app -d deployforge -c \
  "SELECT client_addr, state, count(*) FROM pg_stat_activity
   GROUP BY client_addr, state ORDER BY count DESC;"
# Look for: many connections in 'idle' state from one client = connection leak

### 3. Mitigate immediately
# If connections are idle (leak), terminate idle connections:
kubectl exec -n deployforge postgres-0 -- psql -U deployforge_app -d deployforge -c \
  "SELECT pg_terminate_backend(pid) FROM pg_stat_activity
   WHERE state = 'idle' AND query_start < now() - interval '10 minutes';"

# If connections are active (legitimate load), increase max_connections temporarily:
# Edit the PostgreSQL ConfigMap and restart the pod
# Long-term: add PgBouncer as a connection pooler (see Module 12)
```

</details>

---

## Capstone Connection

**DeployForge** uses Grafana as the unified visualization and alerting layer:

- **Service Health Dashboard (RED)** — A top-level dashboard with stat panels for request rate, error percentage, and p99 latency for both the API Gateway and Worker Service. Time-series panels show trends; a table breaks down errors by endpoint. Deployed as a ConfigMap with the `grafana_dashboard` label for auto-provisioning.
- **Infrastructure Dashboard (USE)** — CPU utilization, memory usage, disk I/O, and network throughput per pod and per node. Powered by `node-exporter` and `kube-state-metrics` queries. Includes pod restart counts and OOM kill events.
- **SLO Dashboard** — Shows error budget remaining, burn rate, and a 30-day rolling availability percentage. Multi-window burn rate alerts (fast and slow) fire when the budget is being consumed too quickly.
- **Datasource Integration** — Grafana is configured with Prometheus (metrics), Loki (logs), and Jaeger (traces) datasources. Loki's derived fields link `trace_id` values directly to Jaeger, enabling one-click correlation from logs to traces.
- **Alerting pipeline** — PrometheusRules define alerts for error rate, latency, connection pool exhaustion, replica mismatches, and SLO burn rate. Alertmanager routes critical alerts to PagerDuty and all alerts to Slack. Inhibition rules suppress warnings when a critical alert is already firing.
- **Dashboard as Code** — All dashboards are stored as JSON in ConfigMaps and versioned in the `monitoring/` directory of the DeployForge repository. Changes go through PR review, just like application code. In Module 10 (CI/CD), dashboard changes are automatically applied via the GitOps pipeline.
