# 12.1 — HPA & VPA: Pod Autoscaling

## Concept

Scaling a Kubernetes deployment by hand — `kubectl scale --replicas=10` — works
for one-off adjustments, but it fails the moment humans go to sleep and traffic
doesn't. Pod autoscaling delegates that decision to a control loop that continuously
compares observed metrics with a target, adding or removing replicas (HPA) or
adjusting resource requests (VPA) to keep the system in balance.

The Horizontal Pod Autoscaler (HPA) and Vertical Pod Autoscaler (VPA) attack the
same problem — mismatched capacity — from opposite directions. HPA adds more pods
when demand rises; VPA gives each pod more (or fewer) resources. Understanding when
to use each, how they interact, and where they break is the difference between a
system that glides through traffic spikes and one that flaps between OOMKills and
wasted capacity.

---

## Deep Dive

### The HPA Control Loop

The HPA controller runs inside `kube-controller-manager` on a default 15-second
sync period. Each cycle it:

1. **Fetches metrics** from the Metrics API (resource metrics) or Custom/External
   Metrics API (application metrics).
2. **Computes desired replicas** using the ratio formula.
3. **Applies scaling behavior policies** (stabilization, rate limits).
4. **Updates the Deployment/ReplicaSet** `.spec.replicas`.

```
                        ┌─────────────────────┐
                        │  metrics-server      │
                        │  (CPU / memory)      │
                        └──────────┬──────────┘
                                   │
                        ┌──────────▼──────────┐
                        │  Metrics API         │
                        │  /apis/metrics.k8s.io│
                        └──────────┬──────────┘
                                   │
┌──────────────────┐    ┌──────────▼──────────┐
│ Prometheus       │───▶│  Custom Metrics API  │
│ Adapter          │    │  /apis/custom.metrics │
└──────────────────┘    └──────────┬──────────┘
                                   │
                        ┌──────────▼──────────┐
                        │  HPA Controller      │
                        │  (every 15s)         │
                        │                      │
                        │  desiredReplicas =   │
                        │  ceil(current × ratio│)
                        └──────────┬──────────┘
                                   │
                        ┌──────────▼──────────┐
                        │  Deployment          │
                        │  .spec.replicas = N  │
                        └─────────────────────┘
```

### The Scaling Algorithm

The core formula is deceptively simple:

```
desiredReplicas = ceil[ currentReplicas × ( currentMetricValue / desiredMetricValue ) ]
```

For multiple metrics, the HPA calculates desired replicas for **each** metric
independently and takes the **maximum**. This ensures that if any single metric
is breached, the workload scales up.

| Scenario | Current Replicas | Current CPU | Target CPU | Desired |
|----------|-----------------|-------------|------------|---------|
| Under load | 3 | 90% | 50% | ceil(3 × 90/50) = 6 |
| Idle | 6 | 20% | 50% | ceil(6 × 20/50) = 3 |
| At target | 4 | 48% | 50% | ceil(4 × 48/50) = 4 |

> **Key insight:** The algorithm uses a 10% tolerance band by default. If the ratio
> falls between 0.9 and 1.1, the HPA does nothing. This prevents flapping when
> metrics hover near the target. You can tune this with
> `--horizontal-pod-autoscaler-tolerance` on the controller manager, but the default
> is sensible for most workloads.

### HPA v2 API — Resource Metrics

The `autoscaling/v2` API replaced `v1` as the stable API and supports three metric
types: `Resource`, `Pods`, and `Object` (plus `External` for out-of-cluster metrics).

```yaml
# k8s/scaling/hpa-cpu-memory.yaml
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: deployforge-api
  namespace: deployforge-prod
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: deployforge-api
  minReplicas: 2
  maxReplicas: 20
  metrics:
    - type: Resource
      resource:
        name: cpu
        target:
          type: Utilization          # ← percentage of request
          averageUtilization: 60
    - type: Resource
      resource:
        name: memory
        target:
          type: Utilization
          averageUtilization: 75     # ← memory is less elastic; higher target
```

> **Production note:** Always set `minReplicas >= 2` for anything that handles
> traffic. A single replica means a single point of failure during node drains,
> rolling updates, or spot interruptions.

### HPA v2 API — Custom and External Metrics

Custom metrics come from your application via Prometheus (or another monitoring
system) and are exposed through a **Custom Metrics Adapter**.

```yaml
# k8s/scaling/hpa-custom-metrics.yaml
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: deployforge-api-rps
  namespace: deployforge-prod
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: deployforge-api
  minReplicas: 2
  maxReplicas: 30
  metrics:
    - type: Pods                       # ← per-pod custom metric
      pods:
        metric:
          name: http_requests_per_second
        target:
          type: AverageValue
          averageValue: "100"          # ← scale when avg > 100 RPS per pod
    - type: Object                     # ← metric from another K8s object
      object:
        describedObject:
          apiVersion: networking.k8s.io/v1
          kind: Ingress
          name: deployforge-ingress
        metric:
          name: requests_per_second
        target:
          type: Value
          value: "2000"                # ← total ingress throughput
  behavior:
    scaleUp:
      stabilizationWindowSeconds: 30   # ← react fast
      policies:
        - type: Percent
          value: 100                   # ← double replicas per period
          periodSeconds: 60
    scaleDown:
      stabilizationWindowSeconds: 300  # ← wait 5 min before scaling down
      policies:
        - type: Pods
          value: 2                     # ← remove max 2 pods per period
          periodSeconds: 60
```

### Scaling Behavior Policies

Behavior policies control *how fast* the HPA scales. Without them, the HPA can
double replicas in a single evaluation cycle and then yank them away 15 seconds
later — the classic flapping problem.

```
Timeline without behavior policies:
  t=0s   replicas: 3    CPU: 90%  → scale to 6
  t=15s  replicas: 6    CPU: 30%  → scale to 4
  t=30s  replicas: 4    CPU: 70%  → scale to 6
  t=45s  replicas: 6    CPU: 30%  → scale to 4    ← flapping!

Timeline with stabilization:
  t=0s   replicas: 3    CPU: 90%  → scale to 6 (immediate)
  t=15s  replicas: 6    CPU: 30%  → desire 4, but stabilization=300s → stay at 6
  t=300s replicas: 6    CPU: 30%  → now scale to 4 (stable for 5 min)
```

| Policy Field | Purpose | Recommended Value |
|-------------|---------|-------------------|
| `scaleUp.stabilizationWindowSeconds` | Delay before acting on scale-up desire | 0–60s (react fast) |
| `scaleDown.stabilizationWindowSeconds` | Delay before acting on scale-down desire | 300–600s (be cautious) |
| `scaleUp.policies[].type` | `Pods` (absolute) or `Percent` (relative) | `Percent: 100` for bursty traffic |
| `scaleDown.policies[].type` | `Pods` or `Percent` | `Pods: 2` for gradual drain |
| `selectPolicy` | `Max`, `Min`, or `Disabled` when multiple policies exist | `Max` for scale-up, `Min` for scale-down |

> **Key insight:** Asymmetric scaling — fast up, slow down — is almost always the
> right default. Scaling up too slowly loses requests; scaling down too fast wastes
> the warm-up time you already paid for. Think of it like hiring: hire fast for an
> emergency, but don't fire the moment the emergency passes.

### Metrics Server and the Prometheus Adapter

**Metrics Server** is the built-in lightweight aggregator that exposes CPU and
memory via the `metrics.k8s.io` API. It scrapes kubelets every 15 seconds and stores
only the latest data point — no history.

```bash
# Install metrics-server (if not already present)
kubectl apply -f https://github.com/kubernetes-sigs/metrics-server/releases/latest/download/components.yaml

# Verify
kubectl top pods -n deployforge-prod
# → NAME                              CPU(cores)   MEMORY(bytes)
# → deployforge-api-6f9b8c7d4-abc12  45m          128Mi
# → deployforge-api-6f9b8c7d4-def34  52m          135Mi
```

For **custom metrics**, you need an adapter that bridges your monitoring system to
the Kubernetes Custom Metrics API. The most common is the **Prometheus Adapter**:

```bash
# Install Prometheus Adapter via Helm
helm repo add prometheus-community https://prometheus-community.github.io/helm-charts
helm install prometheus-adapter prometheus-community/prometheus-adapter \
  --namespace monitoring \
  --set prometheus.url=http://prometheus-server.monitoring.svc \
  --set prometheus.port=9090
```

The adapter needs a configuration that maps Prometheus queries to Kubernetes metrics:

```yaml
# prometheus-adapter-config.yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: prometheus-adapter
  namespace: monitoring
data:
  config.yaml: |
    rules:
      - seriesQuery: 'http_requests_total{namespace!="",pod!=""}'
        resources:
          overrides:
            namespace: {resource: "namespace"}
            pod: {resource: "pod"}
        name:
          matches: "^(.*)_total$"
          as: "${1}_per_second"        # ← exposes as http_requests_per_second
        metricsQuery: 'rate(<<.Series>>{<<.LabelMatchers>>}[2m])'
      - seriesQuery: 'deployforge_queue_depth{namespace!="",pod!=""}'
        resources:
          overrides:
            namespace: {resource: "namespace"}
            pod: {resource: "pod"}
        name:
          matches: "^(.*)$"
          as: "${1}"
        metricsQuery: 'avg(<<.Series>>{<<.LabelMatchers>>})'
```

```bash
# Verify custom metrics are available
kubectl get --raw "/apis/custom.metrics.k8s.io/v1beta1" | jq '.resources[].name'
# → "pods/http_requests_per_second"
# → "pods/deployforge_queue_depth"

# Query a specific metric
kubectl get --raw "/apis/custom.metrics.k8s.io/v1beta1/namespaces/deployforge-prod/pods/*/http_requests_per_second" | jq .
```

### Vertical Pod Autoscaler (VPA)

While HPA changes the number of pods, VPA changes the **resource requests and
limits** on individual pods. It answers: "Is this container asking for the right
amount of CPU and memory?"

```
                    ┌───────────────────────┐
                    │  VPA Components        │
                    ├───────────────────────┤
                    │                       │
                    │  ┌─────────────────┐  │
                    │  │ Recommender     │  │  Analyzes historical usage
                    │  │ (reads metrics) │  │  Produces recommendations
                    │  └────────┬────────┘  │
                    │           │            │
                    │  ┌────────▼────────┐  │
                    │  │ Updater         │  │  Evicts pods whose requests
                    │  │ (evicts pods)   │  │  are outside recommendation
                    │  └────────┬────────┘  │
                    │           │            │
                    │  ┌────────▼────────┐  │
                    │  │ Admission       │  │  Mutates pod requests at
                    │  │ Controller      │  │  creation time
                    │  └─────────────────┘  │
                    └───────────────────────┘
```

#### VPA Modes

| Mode | Recommendations | Apply at Creation | Evict Running Pods |
|------|----------------|-------------------|--------------------|
| `Off` | ✓ | ✗ | ✗ |
| `Initial` | ✓ | ✓ | ✗ |
| `Recreate` | ✓ | ✓ | ✓ (kills pod, scheduler re-creates) |
| `Auto` | ✓ | ✓ | ✓ (in-place resize if supported, else recreate) |

> **Key insight:** Start with `Off` mode in production. It gives you recommendations
> without any disruption. Review the recommendations for a week, then switch to
> `Initial` so new pods get right-sized requests. Only move to `Auto` once you trust
> the recommendations and your workload tolerates pod restarts.

```yaml
# k8s/scaling/vpa-worker.yaml
apiVersion: autoscaling.k8s.io/v1
kind: VerticalPodAutoscaler
metadata:
  name: deployforge-worker
  namespace: deployforge-prod
spec:
  targetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: deployforge-worker
  updatePolicy:
    updateMode: "Auto"                  # ← will evict pods to apply new requests
  resourcePolicy:
    containerPolicies:
      - containerName: worker
        minAllowed:
          cpu: 100m                     # ← never go below 100m
          memory: 128Mi
        maxAllowed:
          cpu: 2                        # ← never exceed 2 cores
          memory: 4Gi
        controlledResources: ["cpu", "memory"]
        controlledValues: RequestsOnly  # ← don't touch limits
```

```bash
# Check VPA recommendations
kubectl describe vpa deployforge-worker -n deployforge-prod
# → Recommendation:
# →   Container Recommendations:
# →     Container Name: worker
# →       Lower Bound:   Cpu: 150m,  Memory: 256Mi
# →       Target:        Cpu: 350m,  Memory: 512Mi   ← what VPA recommends
# →       Upper Bound:   Cpu: 800m,  Memory: 1536Mi
# →       Uncapped Target: Cpu: 350m, Memory: 512Mi
```

### The VPA Recommendation Engine

The Recommender component uses a **decaying histogram** of past resource usage:

1. It samples CPU and memory every 60 seconds (configurable).
2. Recent samples carry more weight than older ones (exponential decay).
3. It produces a **confidence interval**: lower bound, target, and upper bound.
4. **Target** is the p90 of the weighted histogram — covers 90% of observed usage.
5. **Upper bound** is the p95 with a safety margin — covers burst spikes.

```
Memory usage histogram for deployforge-worker:
                                                     upper bound
                        target (p90)                      │
                            │                             │
  ┌──────────────────────────────────────────────────────────┐
  │  ▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓░░░░░░░░░░░░░░░░                    │
  │  ▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓░░░░░░░░░░                    │
  │  ▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓░░░░░░                    │
  │  ▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓░░                    │
  └──┬───────────────────┬────────────┬──────────────────────┘
     128Mi             512Mi        1536Mi
     lower bound
```

> **Production note:** VPA needs at least 24–48 hours of metric history to produce
> stable recommendations. Don't trust day-one recommendations for production
> workloads — they're based on startup and warmup patterns, not real traffic.

### HPA + VPA Compatibility

Running HPA and VPA on the **same metric** (e.g., both scaling on CPU) creates a
conflict: HPA wants to add replicas, VPA wants to increase per-pod resources, and
they fight. The official guidance:

| Scenario | Safe? | Explanation |
|----------|-------|-------------|
| HPA on CPU + VPA on CPU | ❌ | Both react to the same signal — feedback loop |
| HPA on custom metric + VPA on CPU/memory | ✅ | Different signals, no conflict |
| HPA on CPU + VPA in `Off` mode | ✅ | VPA only recommends, doesn't act |
| HPA on CPU + VPA with `controlledValues: RequestsOnly` | ⚠️ | Can work, but VPA changing requests changes HPA's utilization denominator |

The safe pattern for DeployForge:

```yaml
# HPA scales on application metric (RPS)
# VPA right-sizes CPU/memory requests
# No conflict because HPA isn't using Resource metrics

# HPA: scales on http_requests_per_second
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: deployforge-api
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: deployforge-api
  minReplicas: 2
  maxReplicas: 20
  metrics:
    - type: Pods
      pods:
        metric:
          name: http_requests_per_second
        target:
          type: AverageValue
          averageValue: "100"

---
# VPA: adjusts CPU/memory requests
apiVersion: autoscaling.k8s.io/v1
kind: VerticalPodAutoscaler
metadata:
  name: deployforge-api-vpa
spec:
  targetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: deployforge-api
  updatePolicy:
    updateMode: "Initial"               # ← only apply on new pods, not evict
  resourcePolicy:
    containerPolicies:
      - containerName: api
        controlledValues: RequestsOnly
```

### KEDA — Event-Driven Autoscaling

KEDA (Kubernetes Event-Driven Autoscaling) extends the HPA model to scale on
**event sources**: message queues, databases, cron schedules, and 60+ other scalers.
Unlike the Prometheus adapter approach, KEDA handles the adapter plumbing for you.

```yaml
# k8s/scaling/keda-scaledobject.yaml
apiVersion: keda.sh/v1alpha1
kind: ScaledObject
metadata:
  name: deployforge-worker-scaler
  namespace: deployforge-prod
spec:
  scaleTargetRef:
    name: deployforge-worker
  pollingInterval: 15                    # ← check every 15s
  cooldownPeriod: 300                    # ← wait 5 min before scaling to zero
  minReplicaCount: 1
  maxReplicaCount: 50
  triggers:
    - type: redis-lists                  # ← scale on Redis queue depth
      metadata:
        address: redis.deployforge-prod.svc:6379
        listName: deployforge:jobs
        listLength: "10"                 # ← 1 replica per 10 queued jobs
    - type: cron                         # ← pre-scale for known traffic patterns
      metadata:
        timezone: America/New_York
        start: "0 8 * * 1-5"            # ← scale up at 8 AM weekdays
        end: "0 20 * * 1-5"             # ← scale down at 8 PM
        desiredReplicas: "5"
```

```
KEDA Architecture:

┌─────────────────┐    ┌──────────────────┐    ┌──────────────────┐
│  Event Source    │    │  KEDA Operator   │    │  HPA             │
│  (Redis queue,  │───▶│  (ScaledObject   │───▶│  (standard K8s   │
│   Kafka topic,  │    │   controller)    │    │   autoscaling)   │
│   Prometheus)   │    └──────────────────┘    └────────┬─────────┘
└─────────────────┘                                     │
                                                        ▼
                                               ┌──────────────────┐
                                               │  Deployment      │
                                               │  replicas: N     │
                                               └──────────────────┘
```

> **Key insight:** KEDA doesn't replace HPA — it creates HPA objects behind the
> scenes. The `ScaledObject` is a higher-level abstraction that configures an HPA
> with external metrics. This means you get all HPA behavior policies for free.

KEDA's killer feature is **scale to zero**: unlike HPA (which has a minimum of 1
replica), KEDA can scale a deployment to 0 replicas during idle periods and bring
it back up when events arrive.

---

## Code Examples

### Complete HPA Setup with Metrics Pipeline

This example sets up the full pipeline: application exposes Prometheus metrics →
ServiceMonitor scrapes them → Prometheus Adapter exposes them as Custom Metrics →
HPA reacts.

```yaml
# 1. Application exposes /metrics endpoint
# (built into DeployForge API via prom-client or micrometer)

# 2. ServiceMonitor tells Prometheus to scrape the app
apiVersion: monitoring.coreos.com/v1
kind: ServiceMonitor
metadata:
  name: deployforge-api
  namespace: deployforge-prod
  labels:
    release: prometheus                  # ← must match Prometheus operator selector
spec:
  selector:
    matchLabels:
      app: deployforge-api
  endpoints:
    - port: http-metrics
      interval: 15s
      path: /metrics

---
# 3. Prometheus Adapter rule (in adapter ConfigMap)
# rules:
#   - seriesQuery: 'http_server_requests_seconds_count{namespace!="",pod!=""}'
#     resources:
#       overrides:
#         namespace: {resource: "namespace"}
#         pod: {resource: "pod"}
#     name:
#       matches: "^http_server_requests_seconds_count$"
#       as: "http_requests_per_second"
#     metricsQuery: 'sum(rate(<<.Series>>{<<.LabelMatchers>>}[2m])) by (<<.GroupBy>>)'

---
# 4. HPA uses the custom metric
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: deployforge-api
  namespace: deployforge-prod
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: deployforge-api
  minReplicas: 2
  maxReplicas: 20
  metrics:
    - type: Pods
      pods:
        metric:
          name: http_requests_per_second
        target:
          type: AverageValue
          averageValue: "100"
  behavior:
    scaleUp:
      stabilizationWindowSeconds: 0
      policies:
        - type: Percent
          value: 100
          periodSeconds: 30
    scaleDown:
      stabilizationWindowSeconds: 300
      policies:
        - type: Pods
          value: 1
          periodSeconds: 60
      selectPolicy: Min               # ← use the most conservative scale-down
```

### VPA with Recommendation Export

Use VPA in `Off` mode to generate recommendations, then export them for review
before applying.

```bash
#!/bin/bash
# scripts/export-vpa-recommendations.sh
# Export VPA recommendations as a table for team review

echo "=== VPA Recommendations Report ==="
echo "Generated: $(date -u +%Y-%m-%dT%H:%M:%SZ)"
echo ""

for vpa in $(kubectl get vpa -A -o jsonpath='{range .items[*]}{.metadata.namespace}/{.metadata.name}{"\n"}{end}'); do
  ns=$(echo "$vpa" | cut -d/ -f1)
  name=$(echo "$vpa" | cut -d/ -f2)

  echo "--- $ns/$name ---"
  kubectl get vpa "$name" -n "$ns" -o jsonpath='{range .status.recommendation.containerRecommendations[*]}
  Container: {.containerName}
    Current Request:  CPU={.target.cpu}, Memory={.target.memory}
    Lower Bound:      CPU={.lowerBound.cpu}, Memory={.lowerBound.memory}
    Upper Bound:      CPU={.upperBound.cpu}, Memory={.upperBound.memory}
{end}'
  echo ""
done
```

### Multi-Metric HPA with Fallback

A production-grade HPA that uses custom metrics when available and falls back to
CPU when the metrics pipeline is degraded:

```yaml
# k8s/scaling/hpa-multi-metric.yaml
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: deployforge-api-multi
  namespace: deployforge-prod
  annotations:
    autoscaling.alpha.kubernetes.io/metrics: "rps,cpu"
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: deployforge-api
  minReplicas: 2
  maxReplicas: 20
  metrics:
    # Primary: custom RPS metric
    - type: Pods
      pods:
        metric:
          name: http_requests_per_second
        target:
          type: AverageValue
          averageValue: "100"
    # Fallback: CPU (always available via metrics-server)
    - type: Resource
      resource:
        name: cpu
        target:
          type: Utilization
          averageUtilization: 70
    # Safety net: memory pressure
    - type: Resource
      resource:
        name: memory
        target:
          type: Utilization
          averageUtilization: 80
  behavior:
    scaleUp:
      stabilizationWindowSeconds: 30
      policies:
        - type: Percent
          value: 50                    # ← add 50% more pods per minute
          periodSeconds: 60
        - type: Pods
          value: 4                     # ← or add 4 pods, whichever is greater
          periodSeconds: 60
      selectPolicy: Max
    scaleDown:
      stabilizationWindowSeconds: 300
      policies:
        - type: Percent
          value: 10                    # ← remove 10% per 2 minutes
          periodSeconds: 120
      selectPolicy: Min
```

```bash
# Monitor HPA decisions
kubectl describe hpa deployforge-api-multi -n deployforge-prod
# → Events:
# →   Normal  SuccessfulRescale  2m   horizontal-pod-autoscaler
# →   New size: 6; reason: pods metric http_requests_per_second above target

# Watch scaling in real time
kubectl get hpa -n deployforge-prod -w
# → NAME                     REFERENCE              TARGETS              MINPODS MAXPODS REPLICAS AGE
# → deployforge-api-multi    Deployment/deploy...   85/100, 45%/70%      2       20      6        5m
```

---

## Try It Yourself

### Challenge 1: Create an HPA with Behavior Policies

Create an HPA for a deployment called `web-frontend` that:
- Targets 50% average CPU utilization
- Scales between 2 and 15 replicas
- Scales up by at most 3 pods every 30 seconds
- Scales down by at most 1 pod every 2 minutes with a 5-minute stabilization window

<details>
<summary>Show solution</summary>

```yaml
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: web-frontend
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: web-frontend
  minReplicas: 2
  maxReplicas: 15
  metrics:
    - type: Resource
      resource:
        name: cpu
        target:
          type: Utilization
          averageUtilization: 50
  behavior:
    scaleUp:
      stabilizationWindowSeconds: 0
      policies:
        - type: Pods
          value: 3
          periodSeconds: 30
    scaleDown:
      stabilizationWindowSeconds: 300
      policies:
        - type: Pods
          value: 1
          periodSeconds: 120
```

Verify:
```bash
kubectl apply -f hpa-web-frontend.yaml
kubectl describe hpa web-frontend
# → Metrics:    ( current / target )
# →   resource cpu on pods: <unknown> / 50%
# → Min replicas: 2
# → Max replicas: 15
# → Behavior:
# →   Scale Up:   Stabilization: 0s; Max: 3 pods/30s
# →   Scale Down: Stabilization: 300s; Max: 1 pod/120s
```

</details>

### Challenge 2: Configure VPA in Off Mode

Set up a VPA for a deployment called `batch-processor` in `Off` mode with:
- Minimum: 50m CPU, 64Mi memory
- Maximum: 4 CPU, 8Gi memory
- Only control requests, not limits

Then inspect the recommendations.

<details>
<summary>Show solution</summary>

```yaml
apiVersion: autoscaling.k8s.io/v1
kind: VerticalPodAutoscaler
metadata:
  name: batch-processor
spec:
  targetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: batch-processor
  updatePolicy:
    updateMode: "Off"
  resourcePolicy:
    containerPolicies:
      - containerName: "*"
        minAllowed:
          cpu: 50m
          memory: 64Mi
        maxAllowed:
          cpu: 4
          memory: 8Gi
        controlledResources: ["cpu", "memory"]
        controlledValues: RequestsOnly
```

Verify:
```bash
kubectl apply -f vpa-batch-processor.yaml

# Wait 5+ minutes for recommendations to appear
kubectl get vpa batch-processor -o yaml | grep -A 20 'recommendation:'
# → recommendation:
# →   containerRecommendations:
# →   - containerName: batch-processor
# →     lowerBound:
# →       cpu: 100m
# →       memory: 128Mi
# →     target:
# →       cpu: 250m
# →       memory: 384Mi
# →     upperBound:
# →       cpu: 1
# →       memory: 1Gi
```

</details>

### Challenge 3: Set Up KEDA for Queue-Based Scaling

Write a KEDA `ScaledObject` that scales `email-sender` based on a Redis list called
`emails:pending`. Scale 1 replica per 5 pending messages, minimum 0, maximum 20.
Include a 5-minute cooldown.

<details>
<summary>Show solution</summary>

```yaml
apiVersion: keda.sh/v1alpha1
kind: ScaledObject
metadata:
  name: email-sender-scaler
spec:
  scaleTargetRef:
    name: email-sender
  pollingInterval: 10
  cooldownPeriod: 300
  minReplicaCount: 0
  maxReplicaCount: 20
  triggers:
    - type: redis-lists
      metadata:
        address: redis.default.svc:6379
        listName: emails:pending
        listLength: "5"
```

Verify:
```bash
kubectl apply -f keda-email-sender.yaml

# Push messages and watch scaling
redis-cli LPUSH emails:pending msg1 msg2 msg3 msg4 msg5 msg6 msg7 msg8 msg9 msg10

kubectl get scaledobject email-sender-scaler
# → NAME                   SCALETARGETKIND   SCALETARGETNAME   MIN   MAX   TRIGGERS     READY   ACTIVE
# → email-sender-scaler    apps/v1.Deploy... email-sender      0     20    redis-lists   True    True

kubectl get hpa
# → NAME                           REFERENCE                  TARGETS     MINPODS   MAXPODS   REPLICAS
# → keda-hpa-email-sender-scaler   Deployment/email-sender    10/5 (avg)  1         20        2
```

</details>

---

## Capstone Connection

**DeployForge** uses every autoscaling pattern covered in this section:

- **HPA on custom metrics** (`k8s/scaling/hpa-api.yaml`): The API gateway scales on
  `http_requests_per_second` rather than raw CPU, so replica count tracks actual user
  demand — not background garbage collection or health-check overhead.
- **VPA for workers** (`k8s/scaling/vpa-worker.yaml`): Background deployment workers
  have unpredictable memory profiles depending on job size. VPA in `Auto` mode
  right-sizes their requests so they don't waste memory during light jobs or OOMKill
  during heavy ones.
- **KEDA for queue draining** (`k8s/scaling/keda-scaledobject.yaml`): When the Redis
  job queue backs up, KEDA scales workers to match queue depth — and scales to zero
  during off-hours to save resources.

In the next section, you'll learn how the **Cluster Autoscaler** ensures there are
enough nodes for all these pods to land on.
