# Module 09 — Exercises

Hands-on practice with reliability engineering concepts. These exercises build DeployForge's reliability foundation — from defining SLOs through running chaos experiments to writing postmortems for simulated incidents.

> **⚠️ Prerequisite:** You need a running `kind` cluster with DeployForge workloads, Prometheus, and Grafana from Module 08. If you don't have one, run:
> ```bash
> cd docker-k8s/capstone/deployforge
> kind create cluster --config kind-config.yaml
> kubectl apply -k manifests/
> ```

---

## Exercise 1: Define SLOs for DeployForge

**Goal:** Write a formal SLO specification for the DeployForge API Gateway and Worker Service, including SLI definitions, targets, and measurement windows.

### Steps

1. **Identify the user-facing quality dimensions for each service:**

   Think about what matters to DeployForge users. The API Gateway serves HTTP requests — users care about availability and speed. The Worker processes deployment jobs — users care about success rate and completion time.

2. **Write SLI specifications for the API Gateway:**

   Define two SLIs using this format:

   ```yaml
   # slo-api-gateway.yaml
   slos:
     - name: api-gateway-availability
       sli:
         type: availability
         description: # What does this measure?
         good_events: # PromQL for successful events
         total_events: # PromQL for all events
       objective: # Target (e.g., 0.999)
       window:
         type: rolling
         duration: 30d

     - name: api-gateway-latency
       sli:
         type: latency
         description: # What does this measure?
         good_events: # PromQL for fast enough events
         total_events: # PromQL for all events
       objective: # Target (e.g., 0.99)
       window:
         type: rolling
         duration: 30d
   ```

3. **Write SLI specifications for the Worker Service:**

   Define two SLIs: job success rate and job duration.

4. **Create the Prometheus recording rules:**

   Implement the SLIs as Prometheus recording rules in a `PrometheusRule` manifest:

   ```bash
   kubectl apply -f slo-recording-rules.yaml
   ```

5. **Verify the SLIs are being computed:**

   ```bash
   # Port-forward to Prometheus
   kubectl port-forward -n monitoring svc/prometheus 9090:9090 &

   # Query the SLI
   curl -s "http://localhost:9090/api/v1/query" \
     --data-urlencode 'query=deployforge:api_availability:ratio_rate5m' \
     | jq '.data.result[0].value[1]'
   # → "0.9998..." (should be near 1.0 in a healthy cluster)
   ```

<details>
<summary>Show solution</summary>

```yaml
# slo-api-gateway.yaml
slos:
  - name: api-gateway-availability
    sli:
      type: availability
      description: >
        Proportion of HTTP requests to the API Gateway
        that return a non-5xx status code.
      good_events: |
        sum(rate(http_requests_total{job="api-gateway",status!~"5.."}[5m]))
      total_events: |
        sum(rate(http_requests_total{job="api-gateway"}[5m]))
    objective: 0.999
    window:
      type: rolling
      duration: 30d

  - name: api-gateway-latency
    sli:
      type: latency
      description: >
        Proportion of HTTP requests to the API Gateway
        that complete within 500ms.
      good_events: |
        sum(rate(http_request_duration_seconds_bucket{job="api-gateway",le="0.5"}[5m]))
      total_events: |
        sum(rate(http_request_duration_seconds_count{job="api-gateway"}[5m]))
    objective: 0.99
    window:
      type: rolling
      duration: 30d

# slo-worker.yaml
slos:
  - name: worker-job-success
    sli:
      type: availability
      description: >
        Proportion of BullMQ jobs that complete successfully
        (not failed or timed out).
      good_events: |
        sum(rate(job_processed_total{job="worker",status="success"}[5m]))
      total_events: |
        sum(rate(job_processed_total{job="worker"}[5m]))
    objective: 0.99
    window:
      type: rolling
      duration: 30d

  - name: worker-job-duration
    sli:
      type: latency
      description: >
        Proportion of jobs that complete within 30 seconds.
      good_events: |
        sum(rate(job_duration_seconds_bucket{job="worker",le="30"}[5m]))
      total_events: |
        sum(rate(job_duration_seconds_count{job="worker"}[5m]))
    objective: 0.95
    window:
      type: rolling
      duration: 30d
```

```yaml
# slo-recording-rules.yaml
apiVersion: monitoring.coreos.com/v1
kind: PrometheusRule
metadata:
  name: deployforge-slo-recording-rules
  namespace: deployforge
  labels:
    release: kube-prometheus-stack
spec:
  groups:
    - name: deployforge-slo-slis
      interval: 30s
      rules:
        - record: deployforge:api_availability:ratio_rate5m
          expr: |
            sum(rate(http_requests_total{job="api-gateway",status!~"5.."}[5m]))
            /
            sum(rate(http_requests_total{job="api-gateway"}[5m]))

        - record: deployforge:api_latency_sli:ratio_rate5m
          expr: |
            sum(rate(http_request_duration_seconds_bucket{job="api-gateway",le="0.5"}[5m]))
            /
            sum(rate(http_request_duration_seconds_count{job="api-gateway"}[5m]))

        - record: deployforge:worker_success:ratio_rate5m
          expr: |
            sum(rate(job_processed_total{job="worker",status="success"}[5m]))
            /
            sum(rate(job_processed_total{job="worker"}[5m]))

        - record: deployforge:worker_duration_sli:ratio_rate5m
          expr: |
            sum(rate(job_duration_seconds_bucket{job="worker",le="30"}[5m]))
            /
            sum(rate(job_duration_seconds_count{job="worker"}[5m]))
```

</details>

---

## Exercise 2: Calculate and Visualize Error Budgets

**Goal:** Calculate the error budget for each DeployForge SLO and create a Grafana dashboard panel that shows remaining budget in real time.

### Steps

1. **Calculate the error budget for each SLO by hand:**

   Fill in this table:

   | SLO | Target | Window | Total Budget | Budget per Day |
   |-----|--------|--------|-------------|----------------|
   | API Availability | 99.9% | 30d | ? min | ? sec |
   | API Latency | 99% | 30d | ? min | ? sec |
   | Worker Success | 99% | 30d | ? min | ? sec |
   | Worker Duration | 95% | 30d | ? min | ? sec |

2. **Write a PromQL query for error budget remaining:**

   The formula is:

   ```
   budget_remaining = 1 - (actual_error_rate_over_window / allowed_error_rate)
   ```

   Write this for the API Gateway availability SLO.

3. **Create multi-burn-rate alert rules:**

   Implement the three-tier alerting from Section 9.1 for the API Gateway availability SLO. Apply them to your cluster:

   ```bash
   kubectl apply -f slo-alert-rules.yaml
   ```

4. **Build a Grafana dashboard panel:**

   Create a dashboard with:
   - A gauge panel showing error budget remaining (0-100%)
   - A time-series panel showing burn rate over the last 24 hours
   - A stat panel showing current SLI value

   ```bash
   # Port-forward to Grafana
   kubectl port-forward -n monitoring svc/grafana 3000:3000 &
   # Open http://localhost:3000 and create a new dashboard
   ```

5. **Verify alerting works by simulating errors:**

   ```bash
   # Generate some 500 errors to consume error budget
   for i in $(seq 1 100); do
     curl -s -o /dev/null -w "%{http_code}\n" \
       http://localhost:8080/api/force-error 2>/dev/null
   done

   # Check that the burn-rate alert fires
   curl -s "http://localhost:9090/api/v1/alerts" | jq '.data.alerts[] | select(.labels.slo == "api-availability")'
   ```

<details>
<summary>Show solution</summary>

**Error budget calculations:**

| SLO | Target | Window | Allowed Error Rate | Total Budget | Budget per Day |
|-----|--------|--------|--------------------|-------------|----------------|
| API Availability | 99.9% | 30d | 0.1% | 43.2 min | 1 min 26 sec |
| API Latency | 99% | 30d | 1% | 432 min (7.2h) | 14 min 24 sec |
| Worker Success | 99% | 30d | 1% | 432 min (7.2h) | 14 min 24 sec |
| Worker Duration | 95% | 30d | 5% | 2160 min (36h) | 72 min |

**PromQL for error budget remaining:**

```promql
# Error budget remaining for API availability (as percentage, 0-100)
(
  1 - (
    (1 - avg_over_time(deployforge:api_availability:ratio_rate5m[30d]))
    /
    0.001
  )
) * 100
```

**Multi-burn-rate alert rules:**

```yaml
# slo-alert-rules.yaml
apiVersion: monitoring.coreos.com/v1
kind: PrometheusRule
metadata:
  name: deployforge-slo-alerts
  namespace: deployforge
  labels:
    release: kube-prometheus-stack
spec:
  groups:
    - name: deployforge-slo-burn-rate
      rules:
        # Multi-window error ratios
        - record: deployforge:api_errors:ratio_rate1h
          expr: |
            1 - (
              sum(rate(http_requests_total{job="api-gateway",status!~"5.."}[1h]))
              /
              sum(rate(http_requests_total{job="api-gateway"}[1h]))
            )
        - record: deployforge:api_errors:ratio_rate5m
          expr: |
            1 - (
              sum(rate(http_requests_total{job="api-gateway",status!~"5.."}[5m]))
              /
              sum(rate(http_requests_total{job="api-gateway"}[5m]))
            )
        - record: deployforge:api_errors:ratio_rate6h
          expr: |
            1 - (
              sum(rate(http_requests_total{job="api-gateway",status!~"5.."}[6h]))
              /
              sum(rate(http_requests_total{job="api-gateway"}[6h]))
            )
        - record: deployforge:api_errors:ratio_rate30m
          expr: |
            1 - (
              sum(rate(http_requests_total{job="api-gateway",status!~"5.."}[30m]))
              /
              sum(rate(http_requests_total{job="api-gateway"}[30m]))
            )
        - record: deployforge:api_errors:ratio_rate3d
          expr: |
            1 - (
              sum(rate(http_requests_total{job="api-gateway",status!~"5.."}[3d]))
              /
              sum(rate(http_requests_total{job="api-gateway"}[3d]))
            )

        # 14.4× burn rate (critical)
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
            summary: "API error budget burning at 14.4×"
            runbook_url: "https://wiki.internal/runbooks/deployforge-api-errors"

        # 6× burn rate (warning)
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
            summary: "API error budget burning at 6×"

        # 1× burn rate (ticket)
        - alert: DeployForgeSteadyBurnRate
          expr: |
            deployforge:api_errors:ratio_rate3d > (1.0 * 0.001)
            and
            deployforge:api_errors:ratio_rate6h > (1.0 * 0.001)
          for: 30m
          labels:
            severity: ticket
            slo: api-availability
          annotations:
            summary: "API error budget on track to exhaust"
```

**Grafana dashboard JSON model (import via Dashboard → Import):**

```json
{
  "title": "DeployForge SLO Dashboard",
  "panels": [
    {
      "title": "Error Budget Remaining",
      "type": "gauge",
      "targets": [{
        "expr": "(1 - ((1 - avg_over_time(deployforge:api_availability:ratio_rate5m[30d])) / 0.001)) * 100"
      }],
      "fieldConfig": {
        "defaults": {
          "min": 0, "max": 100, "unit": "percent",
          "thresholds": {
            "steps": [
              { "color": "red", "value": 0 },
              { "color": "orange", "value": 25 },
              { "color": "yellow", "value": 50 },
              { "color": "green", "value": 75 }
            ]
          }
        }
      }
    },
    {
      "title": "Burn Rate (24h)",
      "type": "timeseries",
      "targets": [
        { "expr": "deployforge:api_errors:ratio_rate1h / 0.001", "legendFormat": "1h burn rate" },
        { "expr": "deployforge:api_errors:ratio_rate6h / 0.001", "legendFormat": "6h burn rate" }
      ]
    },
    {
      "title": "Current Availability SLI",
      "type": "stat",
      "targets": [{
        "expr": "deployforge:api_availability:ratio_rate5m * 100"
      }],
      "fieldConfig": {
        "defaults": { "unit": "percent", "decimals": 3 }
      }
    }
  ]
}
```

</details>

---

## Exercise 3: Run Chaos Experiments Against the Kind Cluster

**Goal:** Install Chaos Mesh, run three chaos experiments against DeployForge, and observe the impact on SLOs.

### Steps

1. **Install Chaos Mesh:**

   ```bash
   helm repo add chaos-mesh https://charts.chaos-mesh.org
   helm repo update

   helm install chaos-mesh chaos-mesh/chaos-mesh \
     --namespace chaos-mesh \
     --create-namespace \
     --set chaosDaemon.runtime=containerd \
     --set chaosDaemon.socketPath=/run/containerd/containerd.sock \
     --version 2.7.0

   # Verify
   kubectl get pods -n chaos-mesh
   ```

2. **Experiment 1 — Pod Kill:**

   Create and apply a `PodChaos` manifest that kills one API Gateway pod. Before applying, write down your hypothesis: what should happen to the error rate and latency?

   ```bash
   kubectl apply -f chaos-pod-kill.yaml

   # Watch pods
   kubectl get pods -n deployforge -l app=api-gateway -w

   # Check error rate (in another terminal)
   curl -s "http://localhost:9090/api/v1/query" \
     --data-urlencode 'query=deployforge:api_errors:ratio_rate5m' \
     | jq '.data.result[0].value[1]'
   ```

3. **Experiment 2 — Network Latency:**

   Create a `NetworkChaos` manifest that adds 200ms latency to traffic going to the Worker Service. Observe the impact on the latency SLI.

   ```bash
   kubectl apply -f chaos-network-latency.yaml

   # Watch p99 latency
   curl -s "http://localhost:9090/api/v1/query" \
     --data-urlencode 'query=histogram_quantile(0.99, sum(rate(http_request_duration_seconds_bucket{job="api-gateway"}[5m])) by (le))' \
     | jq '.data.result[0].value[1]'
   ```

4. **Experiment 3 — CPU Stress:**

   Apply CPU stress to one Worker pod and observe if the HPA (if configured) scales up the deployment.

5. **Clean up all experiments:**

   ```bash
   kubectl delete podchaos,networkchaos,stresschaos --all -n deployforge
   ```

6. **Document your findings:**

   For each experiment, record:
   - Hypothesis (before)
   - Actual result (after)
   - Was the hypothesis confirmed or disproved?
   - Action items (if weaknesses were found)

<details>
<summary>Show solution</summary>

**Pod Kill manifest:**

```yaml
# chaos-pod-kill.yaml
apiVersion: chaos-mesh.org/v1alpha1
kind: PodChaos
metadata:
  name: api-gateway-pod-kill
  namespace: deployforge
spec:
  action: pod-kill
  mode: one
  selector:
    namespaces:
      - deployforge
    labelSelectors:
      app: api-gateway
  duration: "30s"
```

**Network Latency manifest:**

```yaml
# chaos-network-latency.yaml
apiVersion: chaos-mesh.org/v1alpha1
kind: NetworkChaos
metadata:
  name: worker-latency-injection
  namespace: deployforge
spec:
  action: delay
  mode: all
  selector:
    namespaces:
      - deployforge
    labelSelectors:
      app: worker
  delay:
    latency: "200ms"
    jitter: "50ms"
    correlation: "100"
  direction: to
  duration: "3m"
```

**CPU Stress manifest:**

```yaml
# chaos-cpu-stress.yaml
apiVersion: chaos-mesh.org/v1alpha1
kind: StressChaos
metadata:
  name: worker-cpu-stress
  namespace: deployforge
spec:
  mode: one
  selector:
    namespaces:
      - deployforge
    labelSelectors:
      app: worker
  stressors:
    cpu:
      workers: 2
      load: 80
  duration: "3m"
```

**Example findings document:**

| Experiment | Hypothesis | Result | Confirmed? | Action Item |
|-----------|-----------|--------|-----------|-------------|
| Pod Kill | Zero errors with 3 replicas | 2 failed requests during pod termination | ❌ Disproved | Add `preStop` hook with 5s sleep for graceful drain |
| Network Latency | p99 stays under 500ms | p99 rose to 650ms | ❌ Disproved | Reduce worker call timeout from 1s to 400ms, add circuit breaker |
| CPU Stress | HPA scales worker to 4 replicas | HPA didn't trigger (CPU metric delayed) | ❌ Disproved | Reduce HPA scale-up stabilization window from 5m to 2m |

</details>

---

## Exercise 4: Write a Postmortem for a Simulated Incident

**Goal:** Simulate an incident scenario and write a complete blameless postmortem with timeline, root cause analysis, and actionable follow-ups.

### Scenario

At 14:30 UTC, the DeployForge CI/CD pipeline deployed version `v2.4.0` of the API Gateway. The new version included a dependency upgrade that changed the default connection pool size from 20 to 5. Within 2 minutes, the API Gateway started returning 503 errors for 40% of requests. The on-call engineer was paged at 14:32 but initially investigated the wrong service (Worker). At 14:50, a second engineer identified the connection pool issue. The team rolled back at 14:55, and service recovered by 15:05 — a total outage of 35 minutes.

### Steps

1. **Classify the incident severity** based on the description.

2. **Write the complete postmortem** using the template from Section 9.3. Include:
   - Summary (2-3 sentences)
   - Impact section with error budget consumption
   - Detailed timeline with timestamps
   - Root cause analysis (systemic, not blaming individuals)
   - Contributing factors (at least 4)
   - What went well (at least 3 items)
   - What went poorly (at least 3 items)
   - Action items (at least 5, with owner, priority, and due date)
   - Lessons learned (at least 3)

3. **Calculate the error budget impact:**

   The API Gateway has a 99.9% availability SLO with a 30-day window. Before this incident, 60% of the error budget remained (25.9 minutes). Calculate how much budget this incident consumed and what the new remaining budget is.

4. **Determine if the error budget policy should trigger any actions** based on the remaining budget after this incident.

<details>
<summary>Show solution</summary>

**Severity: SEV2** — 40% error rate affecting many users, but not a total outage. Workaround exists (healthy endpoints still serving).

**Error budget calculation:**

```
Error budget consumed = 35 min × 0.40 error rate = 14 minutes
(Not all requests failed — 40% did, so budget consumed = duration × error proportion)

Previous remaining: 25.9 min (60% of 43.2 min)
New remaining: 25.9 - 14.0 = 11.9 min (27.5% of 43.2 min)
```

At 27.5% remaining, the error budget policy triggers:
- **Feature freeze** — reliability work only
- **No chaos experiments** until budget recovers
- **All deploys require SRE approval**

**Complete postmortem:**

```markdown
# Postmortem: API Gateway 503 Errors After v2.4.0 Deployment

**Date:** 2024-06-20
**Authors:** Alice (IC), Bob (Ops Lead), Carol (Comms)
**Status:** Complete
**Severity:** SEV2

## Summary

A deployment of API Gateway v2.4.0 at 14:30 UTC caused 40% of API
requests to return 503 errors for 35 minutes. The root cause was a
dependency upgrade that silently changed the default database connection
pool size from 20 to 5, causing connection exhaustion under production load.
The team rolled back to v2.3.0 at 14:55, and service recovered by 15:05.

## Impact

- **Duration:** 35 minutes (14:30 – 15:05 UTC)
- **Users affected:** ~40% of API requests failed (estimated 2,100 failed requests)
- **Error budget consumed:** 14 minutes of the 43.2-minute budget
- **Error budget remaining:** 11.9 minutes (27.5%) — triggers feature freeze policy
- **Revenue impact:** None directly, but 12 deployments were delayed
- **Data loss:** None

## Timeline (all times UTC)

| Time  | Event |
|-------|-------|
| 14:30 | CI/CD pipeline deploys API Gateway v2.4.0 (canary disabled for "minor" update) |
| 14:32 | Alert fires: `DeployForgeHighErrorBurnRate` — 14.4× burn rate detected |
| 14:33 | On-call @alice acknowledges page |
| 14:35 | @alice declares SEV2, opens #inc-2024-0620-api-503 |
| 14:37 | @alice assigns @bob as Ops Lead, begins investigating Worker Service (incorrect) |
| 14:42 | @bob reports Worker Service is healthy — all jobs succeeding |
| 14:45 | @carol joins as Comms Lead, posts first status update |
| 14:47 | @alice redirects investigation to API Gateway pods |
| 14:50 | @bob identifies connection pool exhaustion in API Gateway logs |
| 14:52 | @bob traces issue to dependency upgrade changing pool default from 20 to 5 |
| 14:55 | Rollback initiated: `kubectl rollout undo deployment/api-gateway` |
| 14:58 | New pods running v2.3.0, error rate declining |
| 15:05 | Error rate returns to baseline, incident resolved |
| 15:10 | @carol posts resolution to status page and Slack |

## Root Cause

API Gateway v2.4.0 included an upgrade of the `pg-pool` dependency from
v3.5 to v4.0. The v4.0 release changed the default `max` connection pool
size from 20 to 5 (documented in the library's CHANGELOG but not in the
upgrade guide's "Breaking Changes" section).

The DeployForge API Gateway relied on the library default rather than
explicitly setting `max: 20` in its configuration. Under production load
of ~200 req/s, the 5-connection pool was immediately saturated, causing
connection acquisition timeouts for ~40% of requests.

The canary deployment phase was skipped for this release because it was
classified as a "minor dependency update" — a process gap that prevented
early detection.

## Contributing Factors

- [ ] Relied on library default for connection pool size instead of explicit config
- [ ] Canary deployment phase was skipped for "minor" updates
- [ ] Dependency upgrade changelog wasn't reviewed for default value changes
- [ ] Initial investigation targeted the wrong service (Worker), costing 10 minutes
- [ ] No specific metric or alert for database connection pool utilization
- [ ] CI/CD staging test uses lower concurrency than production

## What Went Well

- Burn-rate alert fired within 2 minutes of the issue starting
- Incident was declared quickly with proper role assignment
- Rollback was clean and completed in under 10 minutes
- Communication in the incident channel was clear and timestamped
- Error budget dashboard immediately showed the impact

## What Went Poorly

- Initial 10 minutes spent investigating the wrong service
- Canary deployment was skipped, removing the early detection safety net
- No runbook for connection pool exhaustion existed
- Dependency upgrade defaults weren't audited before deployment

## Action Items

| ID | Action | Owner | Priority | Due | Status |
|----|--------|-------|----------|-----|--------|
| 1 | Explicitly set connection pool `max: 20` in API Gateway config | @bob | P1 | 06-21 | Open |
| 2 | Add `pg_stat_activity` connection count alert (>80% of pool max) | @alice | P1 | 06-22 | Open |
| 3 | Enforce canary deployment for ALL releases (no skip option) | @carol | P1 | 06-25 | Open |
| 4 | Add dependency audit step in CI: flag default value changes | @dave | P2 | 07-01 | Open |
| 5 | Write runbook for database connection pool exhaustion | @alice | P2 | 06-28 | Open |
| 6 | Increase CI staging load test concurrency to match production | @bob | P2 | 07-05 | Open |
| 7 | Add "which service to investigate first" flowchart to on-call guide | @carol | P3 | 07-10 | Open |

## Lessons Learned

1. **Library defaults are hidden configuration.** Any configuration value
   that affects production behavior should be explicitly set, never inherited
   from library defaults. Defaults change across versions.

2. **Canary deployments are not optional.** The 10 minutes of canary bake
   time we skipped would have caught this issue before it affected all users.
   No deployment should bypass canary, regardless of perceived risk.

3. **Triage efficiency matters.** The 10 minutes spent investigating the
   wrong service extended the incident by 30%. On-call runbooks should
   include a "which service is likely affected" decision tree based on
   the alert that fired.
```

</details>

---

## Wrapping Up

After completing these exercises, you should have:

- ✅ Formal SLO definitions for the DeployForge API Gateway and Worker Service
- ✅ Prometheus recording rules computing SLIs in real time
- ✅ Multi-burn-rate alert rules for error budget consumption
- ✅ A Grafana error budget dashboard
- ✅ Chaos Mesh installed and three experiments run against the cluster
- ✅ A documented findings report from chaos experiments
- ✅ A complete blameless postmortem with actionable follow-ups

These artifacts form the reliability engineering foundation for DeployForge. In [Module 10 — CI/CD & GitOps](../10-cicd-gitops/), you'll integrate SLO-based deployment gating and automated chaos tests into the delivery pipeline.
