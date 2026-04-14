# 9.1 — SLOs, SLIs & Error Budgets

## Concept

Every production system makes implicit promises to its users: the API will respond, the page will load, the job will complete. SRE makes those promises *explicit* by expressing reliability as measurable numbers — Service Level Indicators (SLIs), Service Level Objectives (SLOs), and error budgets. This shift from "is the system up?" to "how much unreliability can we tolerate?" is the single most important mindset change in reliability engineering.

SLOs aren't just monitoring metrics — they're a decision framework. When the error budget is healthy, teams ship features aggressively. When it's burning fast, they freeze features and fix reliability. This creates an objective, data-driven negotiation between product velocity and system stability.

---

## Deep Dive

### The SLI → SLO → SLA Pyramid

```
┌─────────────────────────────────────────────────┐
│                     SLA                          │
│  "We will refund 10% of fees if availability    │
│   drops below 99.9% in a calendar month"         │
│                                                   │
│  ┌─────────────────────────────────────────┐     │
│  │                SLO                       │     │
│  │  "99.9% of requests succeed within      │     │
│  │   500ms over a 30-day rolling window"    │     │
│  │                                          │     │
│  │  ┌─────────────────────────────────┐    │     │
│  │  │             SLI                  │    │     │
│  │  │  proportion of HTTP requests     │    │     │
│  │  │  returning 2xx in < 500ms        │    │     │
│  │  └─────────────────────────────────┘    │     │
│  └─────────────────────────────────────────┘     │
└─────────────────────────────────────────────────┘
```

| Term | Definition | Who Owns It | Example |
|------|-----------|-------------|---------|
| **SLI** | A quantitative measure of a specific aspect of service quality | Engineering | Ratio of successful requests to total requests |
| **SLO** | A target value or range for an SLI, measured over a time window | Engineering + Product | 99.9% availability over a 30-day rolling window |
| **SLA** | A contract with consequences (financial or otherwise) for missing the SLO | Business + Legal | 99.9% availability or 10% service credit |

> **Key insight:** SLOs are stricter than SLAs. If your SLA promises 99.9%, your internal SLO should be 99.95% — the gap is your safety margin before contractual penalties kick in.

### The Four Golden SLI Types

Not all SLIs are created equal. Google's SRE book identifies four categories that cover nearly every user-facing quality dimension:

| SLI Type | Measures | Formula | Good For |
|----------|----------|---------|----------|
| **Availability** | Did the request succeed? | `successful requests / total requests` | APIs, web servers, databases |
| **Latency** | How fast was the response? | `requests < threshold / total requests` | User-facing endpoints, real-time systems |
| **Throughput** | Is the system processing enough? | `processed items / expected items` | Batch jobs, message queues, pipelines |
| **Correctness** | Was the response right? | `correct responses / total responses` | Data pipelines, financial systems, search |

> **Production tip:** Start with availability and latency SLIs — they cover 80% of user-visible quality. Add throughput and correctness only when those dimensions matter to your users.

### SLI Specification Format

A well-defined SLI has four parts:

```
SLI Type:        Availability
SLI Specification: The proportion of HTTP requests to the API Gateway
                   that return a non-5xx status code
SLI Implementation: 1 - (rate(http_requests_total{status=~"5.."}[5m])
                         / rate(http_requests_total[5m]))
Measurement Point: Load balancer access logs (preferred) or
                   application-level Prometheus metrics
```

> **Key insight:** Measure SLIs as close to the user as possible. A health check returning 200 at the application level means nothing if the load balancer is dropping connections.

### SLO Specification Format

An SLO binds an SLI to a target and a measurement window:

```yaml
# slo-spec.yaml — DeployForge API Gateway SLO
slo:
  name: deployforge-api-availability
  description: API Gateway serves successful responses
  sli:
    type: availability
    metric: |
      sum(rate(http_requests_total{job="api-gateway",status!~"5.."}[5m]))
      /
      sum(rate(http_requests_total{job="api-gateway"}[5m]))
  objective: 0.999          # 99.9%
  window:
    type: rolling
    duration: 30d
  alerting:
    burn_rate_windows:
      - { long: 1h,  short: 5m,  factor: 14.4, severity: critical }
      - { long: 6h,  short: 30m, factor: 6.0,  severity: warning  }
      - { long: 3d,  short: 6h,  factor: 1.0,  severity: ticket   }
```

### Error Budget Math

The error budget is the *allowed* unreliability within your SLO window. It's what makes SLOs actionable — not just aspirational.

```
Error Budget = (1 − SLO target) × measurement window
```

| SLO Target | Monthly Error Budget (30 days) | Daily Error Budget |
|-----------|-------------------------------|-------------------|
| 99%       | 7 hours 12 minutes            | 14 min 24 sec     |
| 99.5%     | 3 hours 36 minutes            | 7 min 12 sec      |
| 99.9%     | 43 minutes 12 seconds         | 1 min 26 sec      |
| 99.95%    | 21 minutes 36 seconds         | 43.2 sec          |
| 99.99%    | 4 minutes 19 seconds          | 8.6 sec           |

> **Key insight:** The jump from 99.9% to 99.99% doesn't sound like much, but it reduces your monthly error budget from 43 minutes to 4 minutes. That's a 10× reduction in tolerance for failure — and often a 10× increase in engineering cost.

### Error Budget as a Decision Framework

```
                    Error Budget Status
                           │
              ┌────────────┴────────────┐
              │                         │
        Budget Healthy              Budget Depleted
        (> 25% remaining)           (< 25% remaining)
              │                         │
              ▼                         ▼
   ┌──────────────────┐     ┌──────────────────────┐
   │ Ship features     │     │ Feature freeze        │
   │ Run experiments   │     │ Focus on reliability  │
   │ Chaos testing OK  │     │ No risky deploys      │
   │ Accept more risk  │     │ Increase test coverage │
   └──────────────────┘     └──────────────────────┘
```

Error budget policies define what happens at different budget thresholds:

```yaml
# error-budget-policy.yaml
policy:
  name: deployforge-error-budget-policy
  slo_ref: deployforge-api-availability

  thresholds:
    - remaining_percent: 75
      actions:
        - "Normal development velocity"
        - "Chaos experiments permitted"
        - "Canary deployments proceed automatically"

    - remaining_percent: 50
      actions:
        - "Increase canary bake time to 30 minutes"
        - "Require extra reviewer for infrastructure changes"
        - "Run chaos experiments only during business hours"

    - remaining_percent: 25
      actions:
        - "Feature freeze — reliability work only"
        - "No chaos experiments"
        - "All deploys require SRE approval"
        - "Postmortem required for any new error budget spend"

    - remaining_percent: 0
      actions:
        - "Full deployment freeze"
        - "All engineering effort on reliability"
        - "Escalate to VP Engineering"
```

### Multi-Window, Multi-Burn-Rate Alerting

A naive "alert when SLO is breached" fires too late. Multi-burn-rate alerting detects *how fast* you're consuming budget and alerts proportionally:

```
Burn Rate = actual error rate / allowed error rate
```

If your 30-day SLO allows 0.1% errors (99.9% availability):
- Burn rate 1.0 = consuming budget at exactly the sustainable rate
- Burn rate 14.4 = consuming the entire 30-day budget in ~2 days
- Burn rate 6.0 = consuming the entire 30-day budget in ~5 days

```
Burn Rate    Long Window    Short Window    Action         Time to Exhaust
────────────────────────────────────────────────────────────────────────
14.4×        1 hour         5 min           Page (SEV1)    ~50 hours
 6.0×        6 hours        30 min          Page (SEV2)    ~5 days
 1.0×        3 days         6 hours         Ticket         ~30 days
```

> **Production note:** The short window prevents stale alerts. If a 1-hour burn rate was high but the 5-minute rate has recovered, the incident is likely resolved — don't page someone at 3 AM for a self-healed issue.

### Implementing SLOs in Prometheus

```yaml
# recording-rules-slo.yaml
apiVersion: monitoring.coreos.com/v1
kind: PrometheusRule
metadata:
  name: deployforge-slo-rules
  namespace: deployforge
  labels:
    release: kube-prometheus-stack
spec:
  groups:
    - name: deployforge-slo-availability
      interval: 30s
      rules:
        # SLI: availability (proportion of non-5xx requests)
        - record: deployforge:api_availability:ratio_rate5m
          expr: |
            sum(rate(http_requests_total{job="api-gateway",status!~"5.."}[5m]))
            /
            sum(rate(http_requests_total{job="api-gateway"}[5m]))

        # SLI: latency (proportion of requests under 500ms)
        - record: deployforge:api_latency_sli:ratio_rate5m
          expr: |
            sum(rate(http_request_duration_seconds_bucket{job="api-gateway",le="0.5"}[5m]))
            /
            sum(rate(http_request_duration_seconds_count{job="api-gateway"}[5m]))

        # Error ratio (inverse of availability — useful for burn rate math)
        - record: deployforge:api_errors:ratio_rate5m
          expr: |
            1 - deployforge:api_availability:ratio_rate5m

        # Multi-window error ratios for burn-rate alerting
        - record: deployforge:api_errors:ratio_rate1h
          expr: |
            1 - (
              sum(rate(http_requests_total{job="api-gateway",status!~"5.."}[1h]))
              /
              sum(rate(http_requests_total{job="api-gateway"}[1h]))
            )

        - record: deployforge:api_errors:ratio_rate6h
          expr: |
            1 - (
              sum(rate(http_requests_total{job="api-gateway",status!~"5.."}[6h]))
              /
              sum(rate(http_requests_total{job="api-gateway"}[6h]))
            )

        - record: deployforge:api_errors:ratio_rate3d
          expr: |
            1 - (
              sum(rate(http_requests_total{job="api-gateway",status!~"5.."}[3d]))
              /
              sum(rate(http_requests_total{job="api-gateway"}[3d]))
            )

    - name: deployforge-slo-alerts
      rules:
        # Page: burning 14.4× — exhausts 30-day budget in ~50 hours
        - alert: DeployForgeHighErrorBurnRate
          expr: |
            deployforge:api_errors:ratio_rate1h > (14.4 * 0.001)
            and
            deployforge:api_errors:ratio_rate5m > (14.4 * 0.001)
          for: 2m
          labels:
            severity: critical
            slo: api-availability
          annotations:
            summary: "DeployForge API burning error budget 14.4× faster than sustainable"
            description: |
              The API Gateway error rate over the last 1h is {{ $value | humanizePercentage }}.
              At this burn rate, the 30-day error budget will be exhausted in ~50 hours.
            runbook_url: "https://wiki.internal/runbooks/deployforge-api-errors"

        # Page: burning 6× — exhausts 30-day budget in ~5 days
        - alert: DeployForgeModerateErrorBurnRate
          expr: |
            deployforge:api_errors:ratio_rate6h > (6.0 * 0.001)
            and
            deployforge:api_errors:ratio_rate30m > (6.0 * 0.001)
          for: 5m
          labels:
            severity: warning
            slo: api-availability
          annotations:
            summary: "DeployForge API burning error budget 6× faster than sustainable"
            description: |
              The API Gateway error rate over the last 6h is {{ $value | humanizePercentage }}.
              At this burn rate, the 30-day error budget will be exhausted in ~5 days.

        # Ticket: burning 1× — budget will be fully consumed by window end
        - alert: DeployForgeSteadyErrorBurnRate
          expr: |
            deployforge:api_errors:ratio_rate3d > (1.0 * 0.001)
            and
            deployforge:api_errors:ratio_rate6h > (1.0 * 0.001)
          for: 30m
          labels:
            severity: ticket
            slo: api-availability
          annotations:
            summary: "DeployForge API error budget consumption is on track to exhaust"
            description: |
              At the current error rate, the 30-day error budget will be
              fully consumed before the window resets.
```

### Error Budget Remaining — Grafana Query

Track how much budget you have left over a 30-day rolling window:

```promql
# Error budget remaining (as a fraction, 0.0 = exhausted, 1.0 = untouched)
1 - (
  (
    1 - avg_over_time(deployforge:api_availability:ratio_rate5m[30d])
  ) / 0.001
)
```

Where `0.001` is `1 - 0.999` (your allowed error rate for a 99.9% SLO).

### SLOs ≠ 100%

One of the hardest lessons in SRE: **100% is the wrong target.**

| Why 100% Fails | Consequence |
|----------------|-------------|
| Users can't tell the difference between 99.99% and 100% | Over-investing in reliability beyond user perception |
| Dependencies aren't 100% reliable | Your mobile network, DNS, CDN all have error rates |
| Zero error budget = zero deployments | No budget to spend means no tolerance for change |
| Opportunity cost is real | Engineering time on the last 0.01% could build features users actually want |

> **Key insight (from Google SRE Workbook):** The right SLO is the one where, if you barely miss it, users barely notice — and if you significantly miss it, they definitely notice.

---

## Code Examples

### TypeScript: Tracking Error Budget Programmatically

```typescript
// error-budget-tracker.ts
interface SLOConfig {
  name: string;
  target: number;       // e.g., 0.999 for 99.9%
  windowDays: number;   // e.g., 30
}

interface ErrorBudgetStatus {
  totalBudgetMinutes: number;
  consumedMinutes: number;
  remainingMinutes: number;
  remainingPercent: number;
  burnRate: number;
  recommendation: string;
}

function calculateErrorBudget(
  config: SLOConfig,
  actualAvailability: number,
  elapsedDays: number
): ErrorBudgetStatus {
  const totalBudgetMinutes = (1 - config.target) * config.windowDays * 24 * 60;
  const consumedMinutes = (1 - actualAvailability) * elapsedDays * 24 * 60;
  const remainingMinutes = Math.max(0, totalBudgetMinutes - consumedMinutes);
  const remainingPercent = (remainingMinutes / totalBudgetMinutes) * 100;

  // Burn rate: how fast are we consuming budget relative to sustainable rate?
  const expectedConsumed = (elapsedDays / config.windowDays) * totalBudgetMinutes;
  const burnRate = expectedConsumed > 0 ? consumedMinutes / expectedConsumed : 0;

  let recommendation: string;
  if (remainingPercent > 75) {
    recommendation = "Budget healthy — normal velocity, chaos experiments permitted";
  } else if (remainingPercent > 50) {
    recommendation = "Budget moderate — extend canary bake times, review recent incidents";
  } else if (remainingPercent > 25) {
    recommendation = "Budget low — feature freeze, reliability work only";
  } else {
    recommendation = "Budget critical — deployment freeze, all hands on reliability";
  }

  return {
    totalBudgetMinutes,
    consumedMinutes,
    remainingMinutes,
    remainingPercent,
    burnRate,
    recommendation,
  };
}

// Example: DeployForge API Gateway, 15 days into a 30-day window
const status = calculateErrorBudget(
  { name: "api-availability", target: 0.999, windowDays: 30 },
  0.9994,   // actual availability so far
  15        // days elapsed
);

console.log(`Error Budget: ${status.remainingPercent.toFixed(1)}% remaining`);
console.log(`Budget: ${status.remainingMinutes.toFixed(1)} of ${status.totalBudgetMinutes.toFixed(1)} min`);
console.log(`Burn rate: ${status.burnRate.toFixed(2)}×`);
console.log(`→ ${status.recommendation}`);
// Error Budget: 40.0% remaining
// Budget: 17.3 of 43.2 min
// Burn rate: 1.20×
// → Budget moderate — extend canary bake times, review recent incidents
```

### Bash: Quick Error Budget Calculator

```bash
#!/usr/bin/env bash
# error-budget.sh — Calculate error budget from Prometheus

SLO_TARGET=0.999
WINDOW_DAYS=30

# Query Prometheus for current availability over the window
AVAILABILITY=$(curl -s "http://localhost:9090/api/v1/query" \
  --data-urlencode "query=avg_over_time(deployforge:api_availability:ratio_rate5m[${WINDOW_DAYS}d])" \
  | jq -r '.data.result[0].value[1]')

TOTAL_BUDGET_MIN=$(echo "scale=2; (1 - $SLO_TARGET) * $WINDOW_DAYS * 24 * 60" | bc)
CONSUMED_MIN=$(echo "scale=2; (1 - $AVAILABILITY) * $WINDOW_DAYS * 24 * 60" | bc)
REMAINING_MIN=$(echo "scale=2; $TOTAL_BUDGET_MIN - $CONSUMED_MIN" | bc)
REMAINING_PCT=$(echo "scale=1; ($REMAINING_MIN / $TOTAL_BUDGET_MIN) * 100" | bc)

echo "=== DeployForge API Gateway Error Budget ==="
echo "SLO Target:     $(echo "scale=1; $SLO_TARGET * 100" | bc)%"
echo "Current Avail:  $(echo "scale=4; $AVAILABILITY * 100" | bc)%"
echo "Total Budget:   ${TOTAL_BUDGET_MIN} min (${WINDOW_DAYS}-day window)"
echo "Consumed:       ${CONSUMED_MIN} min"
echo "Remaining:      ${REMAINING_MIN} min (${REMAINING_PCT}%)"

if (( $(echo "$REMAINING_PCT < 25" | bc -l) )); then
  echo "⚠️  ERROR BUDGET LOW — Feature freeze recommended"
elif (( $(echo "$REMAINING_PCT < 50" | bc -l) )); then
  echo "⚡ Error budget moderate — increase caution"
else
  echo "✅ Error budget healthy — ship with confidence"
fi
```

---

## Try It Yourself

### Challenge 1: Calculate the Error Budget for a Multi-SLI Service

DeployForge has two SLOs for the API Gateway:
- **Availability:** 99.9% of requests return non-5xx over 30 days
- **Latency:** 99% of requests complete in under 500ms over 30 days

After 10 days, availability is 99.85% and the latency SLI is at 98.5%. Calculate:
1. The remaining error budget (in minutes) for each SLI.
2. Which SLO is in more danger?
3. What action should the team take?

<details>
<summary>Show solution</summary>

**Availability SLO (99.9%, 30-day window):**

```
Total budget   = (1 − 0.999) × 30 × 24 × 60 = 43.2 min
Consumed (10d) = (1 − 0.9985) × 10 × 24 × 60 = 21.6 min
Remaining      = 43.2 − 21.6 = 21.6 min (50%)
```

**Latency SLO (99%, 30-day window):**

```
Total budget   = (1 − 0.99) × 30 × 24 × 60 = 432 min (7.2 hours)
Consumed (10d) = (1 − 0.985) × 10 × 24 × 60 = 216 min (3.6 hours)
Remaining      = 432 − 216 = 216 min (50%)
```

Both SLOs are at 50% budget remaining after only 33% of the window. The burn rate is 1.5× for both. The availability budget is more concerning in absolute terms — only 21.6 minutes remain, meaning a single 22-minute outage would breach the SLO.

**Action:** Increase canary bake times, investigate the cause of elevated errors and slow responses. If the trend continues, feature freeze by day 15.

</details>

### Challenge 2: Write Multi-Burn-Rate Alert Rules

Write Prometheus alerting rules for the DeployForge Worker Service with:
- SLO: 99% job success rate over 30 days
- Three burn rates: 14.4× (critical), 6× (warning), 1× (ticket)

<details>
<summary>Show solution</summary>

```yaml
apiVersion: monitoring.coreos.com/v1
kind: PrometheusRule
metadata:
  name: deployforge-worker-slo-alerts
  namespace: deployforge
spec:
  groups:
    - name: deployforge-worker-slo
      rules:
        # Recording rules for error ratios at different windows
        - record: deployforge:worker_errors:ratio_rate1h
          expr: |
            1 - (
              sum(rate(job_processed_total{job="worker",status="success"}[1h]))
              /
              sum(rate(job_processed_total{job="worker"}[1h]))
            )
        - record: deployforge:worker_errors:ratio_rate6h
          expr: |
            1 - (
              sum(rate(job_processed_total{job="worker",status="success"}[6h]))
              /
              sum(rate(job_processed_total{job="worker"}[6h]))
            )

        # Critical: 14.4× burn rate (1h long window, 5m short window)
        # Allowed error rate = 1 - 0.99 = 0.01
        # Threshold = 14.4 × 0.01 = 0.144
        - alert: WorkerHighBurnRate
          expr: |
            deployforge:worker_errors:ratio_rate1h > 0.144
            and
            deployforge:worker_errors:ratio_rate5m > 0.144
          for: 2m
          labels:
            severity: critical
          annotations:
            summary: "Worker job failure rate burning budget at 14.4×"

        # Warning: 6× burn rate
        # Threshold = 6.0 × 0.01 = 0.06
        - alert: WorkerModerateBurnRate
          expr: |
            deployforge:worker_errors:ratio_rate6h > 0.06
            and
            deployforge:worker_errors:ratio_rate30m > 0.06
          for: 5m
          labels:
            severity: warning
          annotations:
            summary: "Worker job failure rate burning budget at 6×"

        # Ticket: 1× burn rate
        # Threshold = 1.0 × 0.01 = 0.01
        - alert: WorkerSteadyBurnRate
          expr: |
            deployforge:worker_errors:ratio_rate3d > 0.01
            and
            deployforge:worker_errors:ratio_rate6h > 0.01
          for: 30m
          labels:
            severity: ticket
          annotations:
            summary: "Worker error budget on track to exhaust before window resets"
```

</details>

### Challenge 3: Design an SLO for a New Service

DeployForge is adding a **Deployment Pipeline Service** that takes a deployment request and executes a multi-step pipeline (build → test → deploy → verify). Design:

1. Two SLIs (choose appropriate types)
2. SLO targets with justification
3. Error budget policy with three thresholds

<details>
<summary>Show solution</summary>

**SLI 1 — Availability:**

```
Type:           Availability
Specification:  Proportion of pipeline trigger requests that are accepted
                (return 2xx) and eventually reach a terminal state
                (success or explicit failure, not stuck/orphaned)
Implementation: sum(rate(pipeline_completed_total[5m]))
                / sum(rate(pipeline_triggered_total[5m]))
Target:         99.5% over 30 days
Justification:  Pipelines are batch operations — users tolerate slightly
                lower availability than real-time APIs. 99.5% allows
                ~3.6 hours of downtime per month.
```

**SLI 2 — Latency (duration):**

```
Type:           Latency
Specification:  Proportion of pipelines that complete within 10 minutes
Implementation: sum(rate(pipeline_duration_seconds_bucket{le="600"}[5m]))
                / sum(rate(pipeline_duration_seconds_count[5m]))
Target:         95% over 30 days
Justification:  Deployment pipelines have high variance. A 95% target
                allows 5% of pipelines to run long (large builds,
                flaky tests) without breaching the SLO.
```

**Error Budget Policy:**

| Budget Remaining | Action |
|-----------------|--------|
| > 50% | Normal velocity. Experimental pipeline features allowed. |
| 25–50% | Increase pipeline timeout buffers. Review recent slow pipelines. No experimental features. |
| < 25% | Pipeline feature freeze. All effort on pipeline reliability. Audit resource limits and retry logic. |

</details>

---

## Capstone Connection

**DeployForge** uses SLOs as the reliability contract between the platform and its users:

- **API Gateway SLO** — The Express/TypeScript API Gateway targets 99.9% availability and p99 latency under 500ms over a 30-day rolling window. Recording rules pre-compute the SLI from `http_requests_total` and `http_request_duration_seconds_bucket` metrics instrumented in Module 08.
- **Worker Service SLO** — The BullMQ Worker targets 99% job success rate and p99 job duration under 30 seconds. These SLIs use the `job_processed_total` and `job_duration_seconds` metrics.
- **Error budget dashboard** — A Grafana dashboard displays real-time error budget consumption for both services, with panels showing burn rate trends and remaining budget as a percentage. This dashboard is the first thing the on-call engineer checks each morning.
- **Multi-burn-rate alerts** — PrometheusRules implement three-tier burn-rate alerting (critical/warning/ticket) that pages the on-call only when budget consumption rate warrants human intervention — not on every transient error.
- **Error budget policy** — A written policy (tracked in the DeployForge repo) defines what happens at 75%, 50%, 25%, and 0% budget remaining — from normal velocity through full deployment freeze. In Module 10, the CI/CD pipeline will automatically gate deployments based on error budget status.
