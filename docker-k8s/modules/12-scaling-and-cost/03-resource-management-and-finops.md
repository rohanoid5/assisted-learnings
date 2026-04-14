# 12.3 — Resource Management & FinOps

## Concept

Autoscaling tells Kubernetes *how many* pods and nodes to run. Resource management
tells it *how much* each pod may consume and *how much* each team is allowed to
claim. Without guardrails, a single misbehaving deployment can monopolize an entire
cluster — and without cost visibility, no one knows until the invoice arrives.

This section covers the financial side of Kubernetes: LimitRanges that set sensible
defaults for every pod, ResourceQuotas that cap total consumption per namespace,
PriorityClasses that decide who survives when resources run out, and FinOps
practices that turn opaque cloud bills into actionable engineering decisions. The
goal is a cluster where every workload has right-sized requests, every namespace has
a budget, and every dollar spent is attributable to a team or service.

---

## Deep Dive

### Resource Requests vs Limits — The Full Picture

Every container in Kubernetes can declare two resource boundaries:

| Field | Purpose | Scheduler Uses? | Enforced By |
|-------|---------|----------------|-------------|
| `requests` | Guaranteed minimum resources | ✅ (used to find a node) | kubelet (OOM score adjustment) |
| `limits` | Maximum resources allowed | ❌ | kubelet + cgroup (CPU throttle, OOM kill) |

```yaml
resources:
  requests:
    cpu: 250m         # ← "I need at least 0.25 cores to function"
    memory: 256Mi     # ← "I need at least 256Mi or I'll OOMKill"
  limits:
    cpu: 1000m        # ← "Throttle me if I try to use more than 1 core"
    memory: 512Mi     # ← "Kill me if I exceed 512Mi"
```

```
Memory behavior:

  Limit: 512Mi  ─────────────────────────────────────── OOMKilled!
                                              ╱
  Usage:       ▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓
                                             ╲
  Request: 256Mi ─────────────────────────────── safe zone
                                                  (guaranteed)

CPU behavior:

  Limit: 1000m ─────────────────────────────────── throttled (not killed)
                                          ╱
  Usage:       ▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓
                                         ╲
  Request: 250m ───────────────────────────── guaranteed
```

> **Key insight:** CPU is **compressible** — exceeding the limit just throttles the
> process. Memory is **incompressible** — exceeding the limit kills the container.
> This asymmetry means you should be more conservative with memory limits (set them
> close to requests + headroom) and more generous with CPU limits (or omit them
> entirely to avoid throttling).

#### QoS Classes

Kubernetes assigns a Quality of Service class based on how requests and limits are
set. QoS determines eviction priority when a node runs out of resources:

| QoS Class | Condition | Eviction Priority |
|-----------|-----------|-------------------|
| `Guaranteed` | requests == limits for all containers | Last to be evicted |
| `Burstable` | At least one request set, requests != limits | Middle |
| `BestEffort` | No requests or limits set | First to be evicted |

```yaml
# Guaranteed — production databases, critical APIs
resources:
  requests:
    cpu: 500m
    memory: 1Gi
  limits:
    cpu: 500m       # ← same as request
    memory: 1Gi     # ← same as request

# Burstable — most application workloads
resources:
  requests:
    cpu: 250m
    memory: 256Mi
  limits:
    cpu: 1000m      # ← higher than request (can burst)
    memory: 512Mi

# BestEffort — never do this in production
# (no resources block at all)
```

> **Production note:** For critical workloads like the DeployForge API and
> PostgreSQL, use `Guaranteed` QoS. For workers that can tolerate brief throttling,
> `Burstable` is appropriate. Never deploy `BestEffort` pods in production — they're
> the first to be killed under pressure.

### LimitRanges — Namespace-Level Defaults and Constraints

A LimitRange sets **default** requests/limits for containers that don't specify
them, and **min/max** bounds for those that do. It's your safety net against
developers deploying pods without resource declarations.

```yaml
# k8s/scaling/limit-range.yaml
apiVersion: v1
kind: LimitRange
metadata:
  name: deployforge-limits
  namespace: deployforge-prod
spec:
  limits:
    - type: Container
      default:                          # ← applied if no limits specified
        cpu: 500m
        memory: 512Mi
      defaultRequest:                   # ← applied if no requests specified
        cpu: 100m
        memory: 128Mi
      min:                              # ← floor: no container can request less
        cpu: 50m
        memory: 64Mi
      max:                              # ← ceiling: no container can request more
        cpu: 4
        memory: 8Gi
      maxLimitRequestRatio:
        cpu: "10"                       # ← limit can't be >10x the request
        memory: "4"                     # ← limit can't be >4x the request
    - type: Pod
      max:
        cpu: 8                          # ← total for all containers in a pod
        memory: 16Gi
    - type: PersistentVolumeClaim
      min:
        storage: 1Gi
      max:
        storage: 100Gi
```

```bash
# See effective limits
kubectl describe limitrange deployforge-limits -n deployforge-prod
# → Type        Resource  Min    Max    Default Request  Default Limit  Max Limit/Request Ratio
# → ----        --------  ---    ---    ---------------  -------------  -----------------------
# → Container   cpu       50m    4      100m             500m           10
# → Container   memory    64Mi   8Gi    128Mi            512Mi          4
# → Pod         cpu       -      8      -                -              -
# → Pod         memory    -      16Gi   -                -              -
# → PVC         storage   1Gi    100Gi  -                -              -

# What happens when you violate a LimitRange:
kubectl run test --image=nginx --requests='cpu=10m' -n deployforge-prod
# → Error from server (Forbidden): ... minimum cpu usage per Container is 50m, but request is 10m
```

### ResourceQuotas — Namespace Budgets

While LimitRange controls individual pods, ResourceQuota controls the **total**
resource consumption for an entire namespace. It's how you implement multi-tenancy
and prevent one team from consuming the entire cluster.

```yaml
# k8s/scaling/resource-quota.yaml
apiVersion: v1
kind: ResourceQuota
metadata:
  name: deployforge-prod-quota
  namespace: deployforge-prod
spec:
  hard:
    # Compute
    requests.cpu: "8"                   # ← total CPU requests across all pods
    requests.memory: 16Gi
    limits.cpu: "16"                    # ← total CPU limits
    limits.memory: 32Gi

    # Object counts
    pods: "50"                          # ← max 50 pods in this namespace
    services: "20"
    services.loadbalancers: "2"         # ← limit expensive LB services
    persistentvolumeclaims: "10"
    configmaps: "30"
    secrets: "30"

    # Storage
    requests.storage: 200Gi            # ← total PVC storage

    # Scope: only count pods of a specific priority
    # (covered in PriorityClass section below)
```

```yaml
# Per-environment quotas — staging gets less than production
# k8s/scaling/resource-quota-staging.yaml
apiVersion: v1
kind: ResourceQuota
metadata:
  name: deployforge-staging-quota
  namespace: deployforge-staging
spec:
  hard:
    requests.cpu: "4"
    requests.memory: 8Gi
    limits.cpu: "8"
    limits.memory: 16Gi
    pods: "30"
    services.loadbalancers: "1"
    requests.storage: 50Gi
```

```bash
# Monitor quota usage
kubectl describe resourcequota deployforge-prod-quota -n deployforge-prod
# → Name:                   deployforge-prod-quota
# → Resource                Used    Hard
# → --------                ----    ----
# → configmaps              12      30
# → limits.cpu              6200m   16
# → limits.memory           12Gi    32Gi
# → persistentvolumeclaims  3       10
# → pods                    15      50
# → requests.cpu            3100m   8        ← 38.75% utilized
# → requests.memory         6Gi     16Gi     ← 37.5% utilized
# → requests.storage        60Gi    200Gi
# → secrets                 8       30
# → services                5       20
# → services.loadbalancers  1       2

# What happens when you hit the quota:
kubectl scale deployment deployforge-worker --replicas=100 -n deployforge-prod
# → deployment.apps/deployforge-worker scaled
# But new pods will fail to create:
kubectl get events -n deployforge-prod --field-selector reason=FailedCreate
# → Error creating: pods "deployforge-worker-xxx" is forbidden:
# → exceeded quota: deployforge-prod-quota, requested: requests.cpu=100m,
# → used: requests.cpu=7900m, limited: requests.cpu=8
```

### PriorityClasses — Workload Prioritization

When a cluster runs out of resources, Kubernetes uses PriorityClasses to decide
which pods to evict first and which to protect. Higher priority pods can also
**preempt** lower priority pods to get scheduled.

```yaml
# k8s/scaling/priority-classes.yaml

# Critical infrastructure — never evict
apiVersion: scheduling.k8s.io/v1
kind: PriorityClass
metadata:
  name: deployforge-critical
value: 1000000
globalDefault: false
preemptionPolicy: PreemptLowerPriority
description: "Critical DeployForge services (API, database proxies)"

---
# Standard workloads — default for most deployments
apiVersion: scheduling.k8s.io/v1
kind: PriorityClass
metadata:
  name: deployforge-standard
value: 100000
globalDefault: true                     # ← applied to pods without explicit priority
preemptionPolicy: PreemptLowerPriority
description: "Standard DeployForge workloads"

---
# Batch and background jobs — evict first under pressure
apiVersion: scheduling.k8s.io/v1
kind: PriorityClass
metadata:
  name: deployforge-batch
value: 10000
globalDefault: false
preemptionPolicy: Never                 # ← won't preempt others, just queues
description: "Batch jobs and background tasks"

---
# Overprovisioning placeholder — always evict first
apiVersion: scheduling.k8s.io/v1
kind: PriorityClass
metadata:
  name: overprovisioning
value: -1
globalDefault: false
preemptionPolicy: Never
description: "Cluster overprovisioning buffer"
```

```
Eviction order under memory pressure:

  Priority -1    ┌──────────────────┐
  (placeholder)  │ overprovisioner  │  ← evicted first
                 └──────────────────┘
  Priority 10k   ┌──────────────────┐
  (batch)        │ batch-worker     │  ← evicted second
                 └──────────────────┘
  Priority 100k  ┌──────────────────┐
  (standard)     │ deployforge-api  │  ← evicted third
                 └──────────────────┘
  Priority 1M    ┌──────────────────┐
  (critical)     │ postgres-proxy   │  ← evicted last (protected)
                 └──────────────────┘
```

> **Key insight:** Set `preemptionPolicy: Never` for batch workloads. This means
> they won't kick other pods off nodes to run — they'll wait for capacity. Without
> this, a flood of batch jobs could preempt your API pods, causing an outage.

### Scoped ResourceQuotas with PriorityClasses

You can scope quotas to specific priority classes — giving critical workloads
guaranteed capacity while capping batch usage:

```yaml
# Guaranteed capacity for critical pods
apiVersion: v1
kind: ResourceQuota
metadata:
  name: critical-quota
  namespace: deployforge-prod
spec:
  hard:
    requests.cpu: "4"
    requests.memory: 8Gi
  scopeSelector:
    matchExpressions:
      - scopeName: PriorityClass
        operator: In
        values: ["deployforge-critical"]

---
# Limited capacity for batch pods
apiVersion: v1
kind: ResourceQuota
metadata:
  name: batch-quota
  namespace: deployforge-prod
spec:
  hard:
    requests.cpu: "2"
    requests.memory: 4Gi
    pods: "20"
  scopeSelector:
    matchExpressions:
      - scopeName: PriorityClass
        operator: In
        values: ["deployforge-batch"]
```

### Cost Allocation with Labels

Before you can optimize costs, you need to **attribute** them. Kubernetes labels are
the foundation for cost allocation:

```yaml
# Labeling standard for cost allocation
metadata:
  labels:
    # Business context
    team: platform-engineering           # ← which team owns this
    product: deployforge                 # ← which product
    environment: production              # ← dev/staging/production
    component: api                       # ← api/worker/database/cache

    # Cost context
    cost-center: "CC-1234"              # ← maps to finance system
    budget-owner: "john.doe"            # ← person responsible for costs
```

```
Cost allocation hierarchy:

  Cloud Account
  └── Kubernetes Cluster
      ├── Namespace: deployforge-prod     ← cost center: CC-1234
      │   ├── Deployment: api             ← team: platform
      │   │   └── Pod (requests: 500m CPU, 512Mi mem)
      │   ├── Deployment: worker          ← team: platform
      │   │   └── Pod (requests: 250m CPU, 256Mi mem)
      │   └── StatefulSet: postgres       ← team: data
      │       └── Pod (requests: 2 CPU, 4Gi mem)
      │
      ├── Namespace: deployforge-staging  ← cost center: CC-1234
      │   └── (smaller quotas, spot-only nodes)
      │
      └── Namespace: monitoring           ← cost center: CC-9999 (shared)
          └── Prometheus, Grafana, etc.
```

### Kubecost / OpenCost — Cost Visibility

[Kubecost](https://www.kubecost.com/) (commercial) and [OpenCost](https://www.opencost.io/)
(CNCF sandbox) provide real-time cost allocation based on resource consumption:

```bash
# Install OpenCost via Helm
helm repo add opencost https://opencost.github.io/opencost-helm-chart
helm install opencost opencost/opencost \
  --namespace opencost \
  --create-namespace \
  --set opencost.prometheus.internal.serviceName=prometheus-server \
  --set opencost.prometheus.internal.namespaceName=monitoring
```

```bash
# Query costs via API
# Total namespace costs for the last 7 days
curl -s "http://localhost:9090/allocation/compute?window=7d&aggregate=namespace" | jq '
  .data[] | to_entries[] | {
    namespace: .key,
    cpuCost: .value.cpuCost,
    ramCost: .value.ramCost,
    totalCost: .value.totalCost
  }
'
# → {
# →   "namespace": "deployforge-prod",
# →   "cpuCost": 12.45,
# →   "ramCost": 8.32,
# →   "totalCost": 20.77
# → }
# → {
# →   "namespace": "deployforge-staging",
# →   "cpuCost": 3.21,
# →   "ramCost": 2.14,
# →   "totalCost": 5.35
# → }

# Cost by label (team-level chargeback)
curl -s "http://localhost:9090/allocation/compute?window=7d&aggregate=label:team" | jq '
  .data[] | to_entries[] | {
    team: .key,
    totalCost: .value.totalCost,
    cpuEfficiency: (.value.cpuCoreUsageAverage / .value.cpuCoreRequestAverage * 100 | round)
  }
'
# → {
# →   "team": "platform-engineering",
# →   "totalCost": 18.42,
# →   "cpuEfficiency": 62     ← 62% of requested CPU is actually used
# → }
```

### Right-Sizing Methodology

Right-sizing is the process of matching resource requests to actual usage. The
methodology:

```
Right-Sizing Workflow:
══════════════════════

  Step 1: Observe (1-2 weeks)
  ┌──────────────────────────────────────────┐
  │  Deploy VPA in Off mode                  │
  │  Record p50, p90, p99 CPU and memory     │
  │  Identify peak hours vs idle periods     │
  └──────────────────┬───────────────────────┘
                     │
  Step 2: Analyze    ▼
  ┌──────────────────────────────────────────┐
  │  Compare requests vs actual usage        │
  │  Flag over-provisioned (usage < 30% req) │
  │  Flag under-provisioned (usage > 90% req)│
  │  Check for OOMKills and CPU throttling   │
  └──────────────────┬───────────────────────┘
                     │
  Step 3: Adjust     ▼
  ┌──────────────────────────────────────────┐
  │  Set requests = VPA target (p90 usage)   │
  │  Set limits = VPA upper bound + 20%      │
  │  Roll out changes gradually (canary)     │
  └──────────────────┬───────────────────────┘
                     │
  Step 4: Validate   ▼
  ┌──────────────────────────────────────────┐
  │  Monitor for 48h after changes           │
  │  Check: OOMKills? Throttling? Latency?   │
  │  Adjust if metrics regressed             │
  └──────────────────┬───────────────────────┘
                     │
  Step 5: Automate   ▼
  ┌──────────────────────────────────────────┐
  │  Enable VPA in Initial or Auto mode      │
  │  Set min/max bounds from step 3          │
  │  Alert on recommendations outside bounds │
  └──────────────────────────────────────────┘
```

```bash
#!/bin/bash
# scripts/right-sizing-report.sh
# Generate a right-sizing report comparing requests vs actual usage

echo "=== Right-Sizing Report ==="
echo "Namespace: deployforge-prod"
echo "Period: last 7 days (from Prometheus)"
echo ""

printf "%-35s %-12s %-12s %-10s %-12s %-12s %-10s\n" \
  "WORKLOAD" "CPU_REQ" "CPU_USED" "CPU_EFF" "MEM_REQ" "MEM_USED" "MEM_EFF"
printf "%-35s %-12s %-12s %-10s %-12s %-12s %-10s\n" \
  "---" "---" "---" "---" "---" "---" "---"

# For each deployment, compare requests to actual usage
for deploy in $(kubectl get deploy -n deployforge-prod -o name); do
  name=$(echo "$deploy" | cut -d/ -f2)

  # Get resource requests from spec
  cpu_req=$(kubectl get "$deploy" -n deployforge-prod \
    -o jsonpath='{.spec.template.spec.containers[0].resources.requests.cpu}')
  mem_req=$(kubectl get "$deploy" -n deployforge-prod \
    -o jsonpath='{.spec.template.spec.containers[0].resources.requests.memory}')

  # Get actual usage from metrics-server
  cpu_used=$(kubectl top pods -n deployforge-prod -l app="$name" --no-headers 2>/dev/null \
    | awk '{sum+=$2} END {if(NR>0) printf "%.0fm", sum/NR; else print "N/A"}')
  mem_used=$(kubectl top pods -n deployforge-prod -l app="$name" --no-headers 2>/dev/null \
    | awk '{sum+=$3} END {if(NR>0) printf "%.0fMi", sum/NR; else print "N/A"}')

  printf "%-35s %-12s %-12s %-10s %-12s %-12s %-10s\n" \
    "$name" "$cpu_req" "$cpu_used" "-" "$mem_req" "$mem_used" "-"
done
```

### Idle Resource Detection

Idle resources are the biggest source of waste. Common patterns:

| Idle Resource | Detection | Action |
|--------------|-----------|--------|
| Pods with <5% CPU for 7+ days | Prometheus query: `avg_over_time(rate(container_cpu_usage_seconds_total[5m])[7d:1h]) < 0.05 * requests` | Right-size or scale to zero |
| Unattached PVCs | `kubectl get pvc -A --no-headers \| grep -v Bound` | Delete or attach |
| Unused LoadBalancer Services | LB with 0 active connections for 7+ days | Switch to ClusterIP + Ingress |
| Dev/staging running 24/7 | Utilization drops to near-zero outside business hours | Schedule scale-down |
| Orphaned namespaces | No active deployments, no recent pod activity | Archive and delete |

```bash
# Find PVCs not bound to any pod
kubectl get pvc -A -o json | jq -r '
  .items[] |
  select(.status.phase == "Bound") |
  .metadata.namespace + "/" + .metadata.name as $pvc |
  .spec.volumeName as $vol |
  {pvc: $pvc, volume: $vol, storage: .spec.resources.requests.storage}
' | while read -r line; do
  pvc=$(echo "$line" | jq -r '.pvc')
  ns=$(echo "$pvc" | cut -d/ -f1)
  name=$(echo "$pvc" | cut -d/ -f2)
  # Check if any pod references this PVC
  refs=$(kubectl get pods -n "$ns" -o json | jq -r \
    --arg pvc "$name" \
    '[.items[].spec.volumes[]? | select(.persistentVolumeClaim.claimName == $pvc)] | length')
  if [ "$refs" = "0" ]; then
    echo "UNUSED PVC: $pvc ($(echo "$line" | jq -r '.storage'))"
  fi
done
```

### Spot Instance Cost Strategies

| Strategy | Savings | Risk | Best For |
|----------|---------|------|----------|
| 100% on-demand | 0% | None | Databases, stateful workloads |
| Reserved Instances (1yr) | 30–40% | Commitment | Baseline, always-on workloads |
| Reserved Instances (3yr) | 50–60% | Long commitment | Predictable, stable workloads |
| Savings Plans (compute) | 20–40% | Flexible commitment | Mixed workloads |
| Spot instances | 60–90% | Interruption | Stateless, fault-tolerant |
| Spot + fallback on-demand | 40–70% | Minimal | Production with mixed pools |

```
Cost optimization pyramid:

                    ┌─────────┐
                    │  Spot   │  60-90% savings
                    │ (burst) │  ← interruption-tolerant
                    ├─────────┤
                    │ Savings │  20-40% savings
                    │  Plans  │  ← flexible compute commitment
                    ├─────────┤
                    │ Reserved│  30-60% savings
                    │Instance │  ← predictable base workloads
                    ├─────────┤
                    │On-Demand│  0% savings
                    │ (base)  │  ← critical, minimum HA
                    └─────────┘

  DeployForge strategy:
  • 2 on-demand nodes for API (minReplicas) + PostgreSQL
  • Savings Plan covering the on-demand base
  • Spot nodes for workers, batch jobs, and API overflow
  • Total estimated savings: 45-55% vs all on-demand
```

### Reserved Capacity Planning

A capacity planning model for DeployForge:

```
Capacity Planning Template:
═══════════════════════════

  1. Baseline (always-on workloads):
     ┌────────────────────┬──────────┬──────────┐
     │ Workload           │ CPU Req  │ Mem Req  │
     ├────────────────────┼──────────┼──────────┤
     │ API (min 2 pods)   │ 1000m    │ 1Gi      │
     │ PostgreSQL         │ 2000m    │ 4Gi      │
     │ Redis              │ 500m     │ 1Gi      │
     │ Monitoring stack   │ 1500m    │ 3Gi      │
     ├────────────────────┼──────────┼──────────┤
     │ Total baseline     │ 5000m    │ 9Gi      │
     └────────────────────┴──────────┴──────────┘
     → Reserve: 2 × m5.xlarge (4 CPU, 16Gi each)

  2. Peak capacity (HPA maxReplicas):
     ┌────────────────────┬──────────┬──────────┐
     │ Workload           │ CPU Req  │ Mem Req  │
     ├────────────────────┼──────────┼──────────┤
     │ API (max 20 pods)  │ 10000m   │ 10Gi     │
     │ Workers (max 50)   │ 12500m   │ 12.5Gi   │
     │ Baseline           │ 5000m    │ 9Gi      │
     ├────────────────────┼──────────┼──────────┤
     │ Total peak         │ 27500m   │ 31.5Gi   │
     └────────────────────┴──────────┴──────────┘
     → Spot pool: up to 6 × m5.xlarge

  3. Budget:
     Base (reserved):  2 nodes × $0.096/hr × 730hr/mo = $140/mo
     Peak (spot):      4 nodes × $0.038/hr × 200hr/mo = $30/mo
     Monitoring/LB:    ~$50/mo
     ─────────────────────────────────────────────────────
     Total:            ~$220/mo (vs ~$480/mo all on-demand)
```

---

## Code Examples

### Complete Namespace Setup with Quotas and Limits

Production-ready namespace configuration for DeployForge:

```yaml
# k8s/scaling/namespace-setup.yaml
# Apply all resources together: kubectl apply -f k8s/scaling/namespace-setup.yaml

apiVersion: v1
kind: Namespace
metadata:
  name: deployforge-prod
  labels:
    team: platform-engineering
    product: deployforge
    environment: production
    cost-center: "CC-1234"

---
apiVersion: v1
kind: LimitRange
metadata:
  name: deployforge-limits
  namespace: deployforge-prod
spec:
  limits:
    - type: Container
      default:
        cpu: 500m
        memory: 512Mi
      defaultRequest:
        cpu: 100m
        memory: 128Mi
      min:
        cpu: 50m
        memory: 64Mi
      max:
        cpu: 4
        memory: 8Gi
      maxLimitRequestRatio:
        cpu: "10"
        memory: "4"
    - type: Pod
      max:
        cpu: 8
        memory: 16Gi
    - type: PersistentVolumeClaim
      min:
        storage: 1Gi
      max:
        storage: 100Gi

---
apiVersion: v1
kind: ResourceQuota
metadata:
  name: compute-quota
  namespace: deployforge-prod
spec:
  hard:
    requests.cpu: "8"
    requests.memory: 16Gi
    limits.cpu: "16"
    limits.memory: 32Gi
    pods: "50"

---
apiVersion: v1
kind: ResourceQuota
metadata:
  name: object-quota
  namespace: deployforge-prod
spec:
  hard:
    services: "20"
    services.loadbalancers: "2"
    persistentvolumeclaims: "10"
    configmaps: "30"
    secrets: "30"
    requests.storage: 200Gi

---
apiVersion: v1
kind: ResourceQuota
metadata:
  name: critical-quota
  namespace: deployforge-prod
spec:
  hard:
    requests.cpu: "4"
    requests.memory: 8Gi
  scopeSelector:
    matchExpressions:
      - scopeName: PriorityClass
        operator: In
        values: ["deployforge-critical"]

---
apiVersion: v1
kind: ResourceQuota
metadata:
  name: batch-quota
  namespace: deployforge-prod
spec:
  hard:
    requests.cpu: "2"
    requests.memory: 4Gi
    pods: "20"
  scopeSelector:
    matchExpressions:
      - scopeName: PriorityClass
        operator: In
        values: ["deployforge-batch"]
```

```bash
# Apply and verify
kubectl apply -f k8s/scaling/namespace-setup.yaml

kubectl get limitrange,resourcequota -n deployforge-prod
# → NAME                                    CREATED AT
# → limitrange/deployforge-limits           2024-01-15T10:00:00Z
# →
# → NAME                                    AGE   REQUEST                                          LIMIT
# → resourcequota/compute-quota             10s   requests.cpu: 0/8, requests.memory: 0/16Gi      limits.cpu: 0/16, limits.memory: 0/32Gi
# → resourcequota/object-quota              10s   ...
# → resourcequota/critical-quota            10s   requests.cpu: 0/4, requests.memory: 0/8Gi
# → resourcequota/batch-quota               10s   requests.cpu: 0/2, requests.memory: 0/4Gi
```

### Cost Dashboard Queries (Prometheus)

Prometheus queries for a cost-awareness Grafana dashboard:

```promql
# CPU request efficiency by namespace (actual vs requested)
sum(rate(container_cpu_usage_seconds_total{namespace="deployforge-prod"}[5m]))
/
sum(kube_pod_container_resource_requests{namespace="deployforge-prod", resource="cpu"})

# Memory request efficiency
sum(container_memory_working_set_bytes{namespace="deployforge-prod"})
/
sum(kube_pod_container_resource_requests{namespace="deployforge-prod", resource="memory"})

# Pods close to OOMKill (memory usage > 90% of limit)
(
  sum by (pod, namespace) (container_memory_working_set_bytes{namespace=~"deployforge-.*"})
  /
  sum by (pod, namespace) (kube_pod_container_resource_limits{namespace=~"deployforge-.*", resource="memory"})
) > 0.9

# CPU throttling rate (containers being throttled)
sum by (pod, namespace) (
  rate(container_cpu_cfs_throttled_periods_total{namespace=~"deployforge-.*"}[5m])
)
/
sum by (pod, namespace) (
  rate(container_cpu_cfs_periods_total{namespace=~"deployforge-.*"}[5m])
) > 0.25

# Idle pods (< 5% CPU usage for extended period)
avg_over_time(
  (
    sum by (pod, namespace) (rate(container_cpu_usage_seconds_total{namespace=~"deployforge-.*"}[5m]))
    /
    sum by (pod, namespace) (kube_pod_container_resource_requests{namespace=~"deployforge-.*", resource="cpu"})
  )[24h:1h]
) < 0.05
```

### Scheduled Scaling for Non-Production

Scale down staging and dev environments outside business hours to save costs:

```yaml
# k8s/scaling/scheduled-scaling.yaml
# Uses a CronJob to scale down at night and back up in the morning

apiVersion: v1
kind: ServiceAccount
metadata:
  name: namespace-scaler
  namespace: deployforge-staging

---
apiVersion: rbac.authorization.k8s.io/v1
kind: Role
metadata:
  name: namespace-scaler
  namespace: deployforge-staging
rules:
  - apiGroups: ["apps"]
    resources: ["deployments", "statefulsets"]
    verbs: ["get", "list", "patch"]

---
apiVersion: rbac.authorization.k8s.io/v1
kind: RoleBinding
metadata:
  name: namespace-scaler
  namespace: deployforge-staging
subjects:
  - kind: ServiceAccount
    name: namespace-scaler
roleRef:
  apiGroup: rbac.authorization.k8s.io
  kind: Role
  name: namespace-scaler

---
# Scale down at 8 PM EST (1 AM UTC next day)
apiVersion: batch/v1
kind: CronJob
metadata:
  name: scale-down-staging
  namespace: deployforge-staging
spec:
  schedule: "0 1 * * 1-6"              # ← 1 AM UTC, Mon-Sat (8 PM EST Sun-Fri)
  jobTemplate:
    spec:
      template:
        spec:
          serviceAccountName: namespace-scaler
          restartPolicy: Never
          containers:
            - name: scaler
              image: bitnami/kubectl:1.29
              command: ["/bin/bash", "-c"]
              args:
                - |
                  echo "Scaling down staging environment..."
                  for deploy in $(kubectl get deploy -n deployforge-staging -o name); do
                    current=$(kubectl get "$deploy" -n deployforge-staging -o jsonpath='{.spec.replicas}')
                    kubectl annotate "$deploy" -n deployforge-staging \
                      scaler/previous-replicas="$current" --overwrite
                    kubectl scale "$deploy" -n deployforge-staging --replicas=0
                    echo "  $deploy: $current → 0"
                  done

---
# Scale up at 7 AM EST (12 PM UTC)
apiVersion: batch/v1
kind: CronJob
metadata:
  name: scale-up-staging
  namespace: deployforge-staging
spec:
  schedule: "0 12 * * 1-5"             # ← 12 PM UTC, Mon-Fri (7 AM EST)
  jobTemplate:
    spec:
      template:
        spec:
          serviceAccountName: namespace-scaler
          restartPolicy: Never
          containers:
            - name: scaler
              image: bitnami/kubectl:1.29
              command: ["/bin/bash", "-c"]
              args:
                - |
                  echo "Scaling up staging environment..."
                  for deploy in $(kubectl get deploy -n deployforge-staging -o name); do
                    previous=$(kubectl get "$deploy" -n deployforge-staging \
                      -o jsonpath='{.metadata.annotations.scaler/previous-replicas}')
                    replicas=${previous:-1}
                    kubectl scale "$deploy" -n deployforge-staging --replicas="$replicas"
                    echo "  $deploy: 0 → $replicas"
                  done
```

> **Key insight:** Scaling staging to zero overnight saves ~60% of that namespace's
> cost (assuming 14 hours of downtime per day). For a cluster spending $500/mo on
> staging, that's $300/mo saved with a single CronJob. Multiply by the number of
> non-production environments and the savings compound quickly.

---

## Try It Yourself

### Challenge 1: Design a LimitRange

Create a LimitRange for a `microservices` namespace where:
- Default container request: 200m CPU, 256Mi memory
- Default container limit: 1 CPU, 1Gi memory
- No container can request more than 2 CPU or 4Gi memory
- No container can request less than 25m CPU or 32Mi memory
- Limits can't exceed 8× the request for CPU or 3× for memory

<details>
<summary>Show solution</summary>

```yaml
apiVersion: v1
kind: LimitRange
metadata:
  name: microservices-limits
  namespace: microservices
spec:
  limits:
    - type: Container
      default:
        cpu: 1
        memory: 1Gi
      defaultRequest:
        cpu: 200m
        memory: 256Mi
      min:
        cpu: 25m
        memory: 32Mi
      max:
        cpu: 2
        memory: 4Gi
      maxLimitRequestRatio:
        cpu: "8"
        memory: "3"
```

Verify:
```bash
kubectl create namespace microservices
kubectl apply -f limitrange.yaml

# Test: deploy a pod without resources
kubectl run test --image=nginx -n microservices
kubectl get pod test -n microservices -o jsonpath='{.spec.containers[0].resources}' | jq .
# → {
# →   "limits": { "cpu": "1", "memory": "1Gi" },
# →   "requests": { "cpu": "200m", "memory": "256Mi" }
# → }

# Test: try to exceed max
kubectl run big --image=nginx -n microservices --requests='cpu=3'
# → Error from server (Forbidden): ... maximum cpu usage per Container is 2, but request is 3

kubectl delete namespace microservices
```

</details>

### Challenge 2: Create Scoped ResourceQuotas

Set up a namespace `team-alpha` with three ResourceQuotas:
1. Overall quota: 10 CPU requests, 20Gi memory requests, 60 pods
2. Critical-priority quota: 4 CPU, 8Gi (for PriorityClass `critical`)
3. Batch-priority quota: 2 CPU, 4Gi, max 15 pods (for PriorityClass `batch`)

<details>
<summary>Show solution</summary>

```yaml
apiVersion: v1
kind: Namespace
metadata:
  name: team-alpha

---
apiVersion: v1
kind: ResourceQuota
metadata:
  name: overall-quota
  namespace: team-alpha
spec:
  hard:
    requests.cpu: "10"
    requests.memory: 20Gi
    pods: "60"

---
apiVersion: v1
kind: ResourceQuota
metadata:
  name: critical-quota
  namespace: team-alpha
spec:
  hard:
    requests.cpu: "4"
    requests.memory: 8Gi
  scopeSelector:
    matchExpressions:
      - scopeName: PriorityClass
        operator: In
        values: ["critical"]

---
apiVersion: v1
kind: ResourceQuota
metadata:
  name: batch-quota
  namespace: team-alpha
spec:
  hard:
    requests.cpu: "2"
    requests.memory: 4Gi
    pods: "15"
  scopeSelector:
    matchExpressions:
      - scopeName: PriorityClass
        operator: In
        values: ["batch"]
```

Verify:
```bash
kubectl apply -f team-alpha-quotas.yaml

kubectl describe resourcequota -n team-alpha
# → Name:        overall-quota
# → Resource     Used  Hard
# → --------     ----  ----
# → pods         0     60
# → requests.cpu 0     10
# → requests.memory 0  20Gi
# →
# → Name:        critical-quota
# → Scopes:      PriorityClass In [critical]
# → Resource     Used  Hard
# → --------     ----  ----
# → requests.cpu 0     4
# → requests.memory 0  8Gi
# →
# → Name:        batch-quota
# → Scopes:      PriorityClass In [batch]
# → Resource     Used  Hard
# → --------     ----  ----
# → pods         0     15
# → requests.cpu 0     2
# → requests.memory 0  4Gi

kubectl delete namespace team-alpha
```

</details>

### Challenge 3: Write a Cost Efficiency Query

Write a bash script that lists all deployments in a namespace and calculates CPU
efficiency (actual CPU usage / CPU request). Flag deployments with <30% efficiency
as "over-provisioned" and >90% as "under-provisioned".

<details>
<summary>Show solution</summary>

```bash
#!/bin/bash
# right-sizing-check.sh <namespace>

NAMESPACE=${1:-default}

echo "=== CPU Efficiency Report: $NAMESPACE ==="
echo ""
printf "%-30s %-12s %-12s %-10s %-15s\n" "DEPLOYMENT" "CPU_REQ(m)" "CPU_USED(m)" "EFFICIENCY" "STATUS"
printf "%-30s %-12s %-12s %-10s %-15s\n" "----------" "----------" "-----------" "----------" "------"

for deploy in $(kubectl get deploy -n "$NAMESPACE" -o jsonpath='{.items[*].metadata.name}'); do
  # Get CPU request in millicores
  req_raw=$(kubectl get deploy "$deploy" -n "$NAMESPACE" \
    -o jsonpath='{.spec.template.spec.containers[0].resources.requests.cpu}')

  # Convert to millicores
  if [[ "$req_raw" == *m ]]; then
    req_m=${req_raw%m}
  else
    req_m=$((req_raw * 1000))
  fi

  # Get actual CPU usage (average across pods)
  used_m=$(kubectl top pods -n "$NAMESPACE" -l app="$deploy" --no-headers 2>/dev/null \
    | awk '{gsub(/m/,"",$2); sum+=$2; count++} END {if(count>0) print int(sum/count); else print 0}')

  # Calculate efficiency
  if [ "$req_m" -gt 0 ] 2>/dev/null; then
    efficiency=$((used_m * 100 / req_m))
  else
    efficiency=0
  fi

  # Determine status
  if [ "$efficiency" -lt 30 ]; then
    status="⚠️  OVER-PROV"
  elif [ "$efficiency" -gt 90 ]; then
    status="🔴 UNDER-PROV"
  else
    status="✅ OK"
  fi

  printf "%-30s %-12s %-12s %-10s %-15s\n" "$deploy" "${req_m}m" "${used_m}m" "${efficiency}%" "$status"
done
```

Verify:
```bash
chmod +x right-sizing-check.sh
./right-sizing-check.sh deployforge-prod
# → === CPU Efficiency Report: deployforge-prod ===
# →
# → DEPLOYMENT                     CPU_REQ(m)   CPU_USED(m)  EFFICIENCY STATUS
# → ----------                     ----------   -----------  ---------- ------
# → deployforge-api                500m         310m         62%        ✅ OK
# → deployforge-worker             250m         45m          18%        ⚠️  OVER-PROV
# → deployforge-scheduler          200m         185m         92%        🔴 UNDER-PROV
```

</details>

---

## Capstone Connection

**DeployForge** implements a complete resource management and FinOps strategy:

- **LimitRanges** (`k8s/scaling/limit-range.yaml`): Every namespace in DeployForge
  has default requests and limits, so no pod runs without resource declarations —
  even if a developer forgets to set them. This prevents `BestEffort` QoS pods from
  destabilizing the cluster.
- **ResourceQuotas** (`k8s/scaling/resource-quota.yaml`): Production gets 8 CPU /
  16Gi memory; staging gets half that. Scoped quotas ensure the critical API pods
  have guaranteed capacity even if batch jobs consume their full allocation.
- **PriorityClasses** (`k8s/scaling/priority-classes.yaml`): The API and database
  proxies run at `deployforge-critical` priority, standard services at
  `deployforge-standard`, and batch workers at `deployforge-batch`. Under memory
  pressure, batch jobs are evicted first — before any user-facing pod.
- **Cost labels**: Every DeployForge resource carries `team`, `product`,
  `environment`, and `cost-center` labels, feeding into OpenCost for monthly
  chargeback reports.

With autoscaling (Module 12.1–12.2) and resource management (this section) in
place, DeployForge has a complete scaling story — from pod-level HPA/VPA through
cluster-level autoscaling to financial guardrails. In the exercises that follow,
you'll wire all these pieces together.
