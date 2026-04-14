# Module 12 — Exercises

Build autoscaling and resource management into DeployForge, one layer at a time.

> **Prerequisites:**
> - A running Kubernetes cluster with `metrics-server` installed
> - Concepts from [01-horizontal-and-vertical-autoscaling.md](../01-horizontal-and-vertical-autoscaling.md), [02-cluster-autoscaling.md](../02-cluster-autoscaling.md), and [03-resource-management-and-finops.md](../03-resource-management-and-finops.md) read
> - `kubectl` and `helm` configured
>
> ```bash
> kubectl top nodes
> # → NAME       CPU(cores)   CPU%   MEMORY(bytes)   MEMORY%
> # → minikube   250m         12%    1024Mi           52%
>
> helm version --short
> # → v3.x.x
> ```

---

## Exercise 1: Configure HPA for DeployForge API with Custom Metrics

**Goal:** Set up an HPA that scales the DeployForge API based on HTTP requests per
second, with CPU as a fallback metric. Include behavior policies that scale up
aggressively and scale down conservatively.

### Steps

1. **Create the DeployForge API deployment** (if not already running):
   ```bash
   kubectl create namespace deployforge-prod
   ```
   - Create `k8s/scaling/api-deployment.yaml` with:
     - Image: `nginx` (as a stand-in for the API)
     - Replicas: 2
     - Resource requests: 200m CPU, 256Mi memory
     - Resource limits: 1 CPU, 512Mi memory
     - Labels: `app: deployforge-api`, `team: platform-engineering`
     - A `/metrics` port exposed on 9090

2. **Create the HPA manifest** (`k8s/scaling/hpa-api.yaml`):
   - Target: `deployforge-api` Deployment
   - Min replicas: 2, Max replicas: 20
   - Primary metric: `http_requests_per_second` (Pods type, target: 100 avg)
   - Fallback: CPU utilization at 60%
   - Scale-up: 0s stabilization, 100% increase per 30s period
   - Scale-down: 300s stabilization, max 2 pods removed per 60s

3. **Apply and verify:**
   ```bash
   kubectl apply -f k8s/scaling/api-deployment.yaml
   kubectl apply -f k8s/scaling/hpa-api.yaml
   kubectl describe hpa deployforge-api -n deployforge-prod
   ```

4. **Simulate load and observe scaling:**
   ```bash
   # In a separate terminal, generate load
   kubectl run load-gen --image=busybox --restart=Never -- \
     /bin/sh -c "while true; do wget -q -O- http://deployforge-api.deployforge-prod.svc/; done"

   # Watch HPA react
   kubectl get hpa -n deployforge-prod -w
   ```

<details>
<summary>Show solution</summary>

```yaml
# k8s/scaling/api-deployment.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: deployforge-api
  namespace: deployforge-prod
  labels:
    app: deployforge-api
    team: platform-engineering
    product: deployforge
    environment: production
spec:
  replicas: 2
  selector:
    matchLabels:
      app: deployforge-api
  template:
    metadata:
      labels:
        app: deployforge-api
        team: platform-engineering
      annotations:
        prometheus.io/scrape: "true"
        prometheus.io/port: "9090"
        prometheus.io/path: "/metrics"
    spec:
      containers:
        - name: api
          image: nginx:1.25
          ports:
            - name: http
              containerPort: 80
            - name: http-metrics
              containerPort: 9090
          resources:
            requests:
              cpu: 200m
              memory: 256Mi
            limits:
              cpu: 1000m
              memory: 512Mi
          readinessProbe:
            httpGet:
              path: /
              port: 80
            initialDelaySeconds: 5
            periodSeconds: 10
          livenessProbe:
            httpGet:
              path: /
              port: 80
            initialDelaySeconds: 10
            periodSeconds: 15

---
apiVersion: v1
kind: Service
metadata:
  name: deployforge-api
  namespace: deployforge-prod
  labels:
    app: deployforge-api
spec:
  selector:
    app: deployforge-api
  ports:
    - name: http
      port: 80
      targetPort: 80
    - name: http-metrics
      port: 9090
      targetPort: 9090
```

```yaml
# k8s/scaling/hpa-api.yaml
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: deployforge-api
  namespace: deployforge-prod
  labels:
    app: deployforge-api
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
    - type: Resource
      resource:
        name: cpu
        target:
          type: Utilization
          averageUtilization: 60
  behavior:
    scaleUp:
      stabilizationWindowSeconds: 0
      policies:
        - type: Percent
          value: 100
          periodSeconds: 30
      selectPolicy: Max
    scaleDown:
      stabilizationWindowSeconds: 300
      policies:
        - type: Pods
          value: 2
          periodSeconds: 60
      selectPolicy: Min
```

Verify:
```bash
kubectl apply -f k8s/scaling/api-deployment.yaml
kubectl apply -f k8s/scaling/hpa-api.yaml

kubectl get deploy,hpa,svc -n deployforge-prod
# → NAME                              READY   UP-TO-DATE   AVAILABLE   AGE
# → deployment.apps/deployforge-api   2/2     2            2           30s
# →
# → NAME                                          REFERENCE              TARGETS                    MINPODS   MAXPODS   REPLICAS
# → horizontalpodautoscaler.autoscaling/deploy... Deployment/deploy...   <unknown>/100, 15%/60%     2         20        2
# →
# → NAME                      TYPE        CLUSTER-IP     PORT(S)
# → service/deployforge-api   ClusterIP   10.96.xxx.xx   80/TCP,9090/TCP

kubectl describe hpa deployforge-api -n deployforge-prod
# → Metrics:
# →   "http_requests_per_second" on pods: <unknown> / 100   ← unknown until Prometheus adapter is set up
# →   resource cpu on pods: 15% / 60%                       ← CPU metric works immediately
# → Behavior:
# →   Scale Up:   Stabilization: 0s; Max: 100% pods/30s
# →   Scale Down: Stabilization: 300s; Max: 2 pods/60s
```

</details>

---

## Exercise 2: Set Up VPA for DeployForge Workers

**Goal:** Deploy a VPA for the DeployForge worker deployment in `Off` mode to
generate recommendations, then review the recommendations and create a plan for
switching to `Auto` mode.

### Steps

1. **Install the VPA components** (if not already present):
   ```bash
   # Clone the VPA repo
   git clone https://github.com/kubernetes/autoscaler.git --depth 1
   cd autoscaler/vertical-pod-autoscaler
   ./hack/vpa-up.sh
   ```

2. **Create the worker deployment** (`k8s/scaling/worker-deployment.yaml`):
   - Image: `busybox` with a CPU-intensive loop (simulates work)
   - Replicas: 3
   - Resource requests: 500m CPU, 512Mi memory (intentionally over-provisioned)
   - Resource limits: 2 CPU, 2Gi memory
   - Labels: `app: deployforge-worker`

3. **Create the VPA in Off mode** (`k8s/scaling/vpa-worker.yaml`):
   - Target: `deployforge-worker` Deployment
   - Mode: `Off`
   - Min: 50m CPU, 64Mi memory
   - Max: 2 CPU, 4Gi memory
   - Control: `RequestsOnly`

4. **Wait 5 minutes and check recommendations:**
   ```bash
   kubectl describe vpa deployforge-worker -n deployforge-prod
   ```

5. **Create a second VPA manifest** (`k8s/scaling/vpa-worker-auto.yaml`) that
   switches to `Auto` mode with the same bounds — to be applied after review.

<details>
<summary>Show solution</summary>

```yaml
# k8s/scaling/worker-deployment.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: deployforge-worker
  namespace: deployforge-prod
  labels:
    app: deployforge-worker
    team: platform-engineering
    product: deployforge
    environment: production
spec:
  replicas: 3
  selector:
    matchLabels:
      app: deployforge-worker
  template:
    metadata:
      labels:
        app: deployforge-worker
        team: platform-engineering
    spec:
      containers:
        - name: worker
          image: busybox:1.36
          command: ["/bin/sh", "-c"]
          args:
            - |
              while true; do
                # Simulate variable CPU work
                dd if=/dev/urandom bs=1024 count=100 2>/dev/null | md5sum > /dev/null
                sleep $((RANDOM % 5 + 1))
              done
          resources:
            requests:
              cpu: 500m
              memory: 512Mi
            limits:
              cpu: 2
              memory: 2Gi
```

```yaml
# k8s/scaling/vpa-worker.yaml (Off mode — recommendation only)
apiVersion: autoscaling.k8s.io/v1
kind: VerticalPodAutoscaler
metadata:
  name: deployforge-worker
  namespace: deployforge-prod
  labels:
    app: deployforge-worker
spec:
  targetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: deployforge-worker
  updatePolicy:
    updateMode: "Off"
  resourcePolicy:
    containerPolicies:
      - containerName: worker
        minAllowed:
          cpu: 50m
          memory: 64Mi
        maxAllowed:
          cpu: 2
          memory: 4Gi
        controlledResources: ["cpu", "memory"]
        controlledValues: RequestsOnly
```

```yaml
# k8s/scaling/vpa-worker-auto.yaml (Auto mode — for production rollout)
apiVersion: autoscaling.k8s.io/v1
kind: VerticalPodAutoscaler
metadata:
  name: deployforge-worker
  namespace: deployforge-prod
  labels:
    app: deployforge-worker
spec:
  targetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: deployforge-worker
  updatePolicy:
    updateMode: "Auto"
    minReplicas: 2                       # ← keep at least 2 pods during updates
  resourcePolicy:
    containerPolicies:
      - containerName: worker
        minAllowed:
          cpu: 50m
          memory: 64Mi
        maxAllowed:
          cpu: 2
          memory: 4Gi
        controlledResources: ["cpu", "memory"]
        controlledValues: RequestsOnly
```

Verify:
```bash
kubectl apply -f k8s/scaling/worker-deployment.yaml
kubectl apply -f k8s/scaling/vpa-worker.yaml

# Wait for recommendations (5+ minutes)
sleep 300

kubectl describe vpa deployforge-worker -n deployforge-prod
# → Recommendation:
# →   Container Recommendations:
# →     Container Name: worker
# →       Lower Bound:   Cpu: 25m,   Memory: 64Mi
# →       Target:        Cpu: 100m,  Memory: 128Mi    ← much less than 500m/512Mi!
# →       Upper Bound:   Cpu: 400m,  Memory: 512Mi
# →       Uncapped Target: Cpu: 100m, Memory: 128Mi

# The VPA recommends 100m CPU vs 500m requested — 80% over-provisioned!
# When ready to apply, switch to Auto mode:
# kubectl apply -f k8s/scaling/vpa-worker-auto.yaml
```

</details>

---

## Exercise 3: Implement Resource Quotas and LimitRanges

**Goal:** Set up a complete multi-tenant resource management strategy for DeployForge
with LimitRanges, ResourceQuotas, and PriorityClasses across production and staging
namespaces.

### Steps

1. **Create PriorityClasses** (`k8s/scaling/priority-classes.yaml`):
   - `deployforge-critical` (value: 1000000) — API, database proxies
   - `deployforge-standard` (value: 100000, globalDefault: true) — most workloads
   - `deployforge-batch` (value: 10000, preemptionPolicy: Never) — background jobs

2. **Create production namespace setup** (`k8s/scaling/prod-namespace.yaml`):
   - LimitRange: default 500m/512Mi limits, 100m/128Mi requests, min 50m/64Mi, max 4/8Gi
   - Overall ResourceQuota: 8 CPU req, 16Gi mem req, 50 pods
   - Critical-scoped quota: 4 CPU, 8Gi
   - Batch-scoped quota: 2 CPU, 4Gi, 20 pods

3. **Create staging namespace setup** (`k8s/scaling/staging-namespace.yaml`):
   - LimitRange: same structure but lower defaults (250m/256Mi limits)
   - Overall ResourceQuota: 4 CPU req, 8Gi mem req, 30 pods
   - No scoped quotas (staging doesn't separate by priority)

4. **Test enforcement:**
   ```bash
   # Try to exceed quotas and see the error messages
   kubectl run oversize --image=nginx --requests='cpu=10' -n deployforge-prod
   # Should fail

   # Deploy without resources and verify defaults are applied
   kubectl run no-resources --image=nginx -n deployforge-prod
   kubectl get pod no-resources -n deployforge-prod -o jsonpath='{.spec.containers[0].resources}'
   ```

<details>
<summary>Show solution</summary>

```yaml
# k8s/scaling/priority-classes.yaml
apiVersion: scheduling.k8s.io/v1
kind: PriorityClass
metadata:
  name: deployforge-critical
value: 1000000
globalDefault: false
preemptionPolicy: PreemptLowerPriority
description: "Critical DeployForge services — API, database proxies"

---
apiVersion: scheduling.k8s.io/v1
kind: PriorityClass
metadata:
  name: deployforge-standard
value: 100000
globalDefault: true
preemptionPolicy: PreemptLowerPriority
description: "Standard DeployForge workloads"

---
apiVersion: scheduling.k8s.io/v1
kind: PriorityClass
metadata:
  name: deployforge-batch
value: 10000
globalDefault: false
preemptionPolicy: Never
description: "Batch jobs and background tasks — will not preempt other pods"
```

```yaml
# k8s/scaling/prod-namespace.yaml
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

```yaml
# k8s/scaling/staging-namespace.yaml
apiVersion: v1
kind: Namespace
metadata:
  name: deployforge-staging
  labels:
    team: platform-engineering
    product: deployforge
    environment: staging
    cost-center: "CC-1234"

---
apiVersion: v1
kind: LimitRange
metadata:
  name: deployforge-limits
  namespace: deployforge-staging
spec:
  limits:
    - type: Container
      default:
        cpu: 250m
        memory: 256Mi
      defaultRequest:
        cpu: 50m
        memory: 64Mi
      min:
        cpu: 25m
        memory: 32Mi
      max:
        cpu: 2
        memory: 4Gi
      maxLimitRequestRatio:
        cpu: "10"
        memory: "4"
    - type: Pod
      max:
        cpu: 4
        memory: 8Gi

---
apiVersion: v1
kind: ResourceQuota
metadata:
  name: compute-quota
  namespace: deployforge-staging
spec:
  hard:
    requests.cpu: "4"
    requests.memory: 8Gi
    limits.cpu: "8"
    limits.memory: 16Gi
    pods: "30"

---
apiVersion: v1
kind: ResourceQuota
metadata:
  name: object-quota
  namespace: deployforge-staging
spec:
  hard:
    services: "10"
    services.loadbalancers: "1"
    persistentvolumeclaims: "5"
    configmaps: "20"
    secrets: "20"
    requests.storage: 50Gi
```

Verify:
```bash
kubectl apply -f k8s/scaling/priority-classes.yaml
kubectl apply -f k8s/scaling/prod-namespace.yaml
kubectl apply -f k8s/scaling/staging-namespace.yaml

# Test LimitRange defaults
kubectl run defaults-test --image=nginx -n deployforge-prod
kubectl get pod defaults-test -n deployforge-prod -o jsonpath='{.spec.containers[0].resources}' | jq .
# → {
# →   "limits": { "cpu": "500m", "memory": "512Mi" },
# →   "requests": { "cpu": "100m", "memory": "128Mi" }
# → }

# Test LimitRange min enforcement
kubectl run too-small --image=nginx --requests='cpu=10m' -n deployforge-prod
# → Error from server (Forbidden): ... minimum cpu usage per Container is 50m, but request is 10m

# Test ResourceQuota enforcement
kubectl run too-big --image=nginx --requests='cpu=10' -n deployforge-prod
# → Error from server (Forbidden): exceeded quota: compute-quota

# Test quota usage tracking
kubectl describe resourcequota compute-quota -n deployforge-prod
# → Resource          Used    Hard
# → --------          ----    ----
# → limits.cpu        500m    16
# → limits.memory     512Mi   32Gi
# → pods              1       50
# → requests.cpu      100m    8
# → requests.memory   128Mi   16Gi

# Clean up test pods
kubectl delete pod defaults-test -n deployforge-prod
```

</details>

---

## Exercise 4: Analyze and Optimize Resource Usage

**Goal:** Build a resource analysis toolkit: a script that generates a right-sizing
report, identifies over-provisioned workloads, calculates potential savings, and
produces an optimization plan.

### Steps

1. **Deploy sample workloads** with varying resource profiles:
   ```bash
   # Over-provisioned workload (requests >> usage)
   kubectl create deployment over-prov --image=nginx --replicas=3 -n deployforge-prod
   kubectl set resources deployment over-prov -n deployforge-prod \
     --requests='cpu=1,memory=1Gi' --limits='cpu=2,memory=2Gi'

   # Right-sized workload
   kubectl create deployment right-sized --image=nginx --replicas=2 -n deployforge-prod
   kubectl set resources deployment right-sized -n deployforge-prod \
     --requests='cpu=100m,memory=128Mi' --limits='cpu=200m,memory=256Mi'
   ```

2. **Write a right-sizing report script** (`scripts/resource-report.sh`):
   - List all deployments in a given namespace
   - Show CPU and memory: requested vs actual (from `kubectl top`)
   - Calculate efficiency percentage for each
   - Flag over-provisioned (<30% efficiency) and under-provisioned (>90%)
   - Sum total requested vs total used across all deployments
   - Calculate potential savings as percentage of wasted requests

3. **Write a quota usage report** (`scripts/quota-report.sh`):
   - List all ResourceQuotas in a namespace
   - Show used vs hard for each resource
   - Calculate percentage utilization
   - Flag quotas approaching limits (>80% used)

4. **Produce an optimization plan:**
   - Based on the report, list specific changes (new requests/limits)
   - Estimate savings in CPU and memory

<details>
<summary>Show solution</summary>

```bash
#!/bin/bash
# scripts/resource-report.sh
# Usage: ./resource-report.sh <namespace>

NAMESPACE=${1:-deployforge-prod}

echo "╔══════════════════════════════════════════════════════════════╗"
echo "║           Resource Right-Sizing Report                      ║"
echo "║           Namespace: $NAMESPACE"
echo "║           Generated: $(date -u +%Y-%m-%dT%H:%M:%SZ)"
echo "╚══════════════════════════════════════════════════════════════╝"
echo ""

total_req_cpu=0
total_used_cpu=0
total_req_mem=0
total_used_mem=0
over_prov_count=0
under_prov_count=0

printf "%-25s %-8s %-10s %-10s %-8s %-10s %-10s %-8s %-12s\n" \
  "DEPLOYMENT" "PODS" "CPU_REQ" "CPU_USED" "CPU_EFF" "MEM_REQ" "MEM_USED" "MEM_EFF" "STATUS"
printf "%-25s %-8s %-10s %-10s %-8s %-10s %-10s %-8s %-12s\n" \
  "----------" "----" "-------" "--------" "-------" "-------" "--------" "-------" "------"

for deploy in $(kubectl get deploy -n "$NAMESPACE" -o jsonpath='{.items[*].metadata.name}'); do
  # Pod count
  pods=$(kubectl get deploy "$deploy" -n "$NAMESPACE" -o jsonpath='{.status.readyReplicas}')
  pods=${pods:-0}

  # CPU request (per pod, in millicores)
  cpu_req_raw=$(kubectl get deploy "$deploy" -n "$NAMESPACE" \
    -o jsonpath='{.spec.template.spec.containers[0].resources.requests.cpu}')
  if [[ "$cpu_req_raw" == *m ]]; then
    cpu_req_per_pod=${cpu_req_raw%m}
  elif [[ -n "$cpu_req_raw" ]]; then
    cpu_req_per_pod=$((cpu_req_raw * 1000))
  else
    cpu_req_per_pod=0
  fi
  cpu_req_total=$((cpu_req_per_pod * pods))

  # Memory request (per pod, in Mi)
  mem_req_raw=$(kubectl get deploy "$deploy" -n "$NAMESPACE" \
    -o jsonpath='{.spec.template.spec.containers[0].resources.requests.memory}')
  mem_req_per_pod=$(echo "$mem_req_raw" | sed 's/Mi//;s/Gi/*1024/' | bc 2>/dev/null)
  mem_req_per_pod=${mem_req_per_pod:-0}
  mem_req_total=$((mem_req_per_pod * pods))

  # Actual CPU usage (average per pod)
  cpu_used=$(kubectl top pods -n "$NAMESPACE" -l app="$deploy" --no-headers 2>/dev/null \
    | awk '{gsub(/m/,"",$2); sum+=$2; count++} END {if(count>0) print int(sum/count); else print 0}')
  cpu_used_total=$((cpu_used * pods))

  # Actual memory usage (average per pod)
  mem_used=$(kubectl top pods -n "$NAMESPACE" -l app="$deploy" --no-headers 2>/dev/null \
    | awk '{gsub(/Mi/,"",$3); sum+=$3; count++} END {if(count>0) print int(sum/count); else print 0}')
  mem_used_total=$((mem_used * pods))

  # Efficiency
  if [ "$cpu_req_per_pod" -gt 0 ] 2>/dev/null; then
    cpu_eff=$((cpu_used * 100 / cpu_req_per_pod))
  else
    cpu_eff=0
  fi

  if [ "$mem_req_per_pod" -gt 0 ] 2>/dev/null; then
    mem_eff=$((mem_used * 100 / mem_req_per_pod))
  else
    mem_eff=0
  fi

  # Status
  if [ "$cpu_eff" -lt 30 ] || [ "$mem_eff" -lt 30 ]; then
    status="⚠️  OVER-PROV"
    over_prov_count=$((over_prov_count + 1))
  elif [ "$cpu_eff" -gt 90 ] || [ "$mem_eff" -gt 90 ]; then
    status="🔴 UNDER-PROV"
    under_prov_count=$((under_prov_count + 1))
  else
    status="✅ OK"
  fi

  # Accumulate totals
  total_req_cpu=$((total_req_cpu + cpu_req_total))
  total_used_cpu=$((total_used_cpu + cpu_used_total))
  total_req_mem=$((total_req_mem + mem_req_total))
  total_used_mem=$((total_used_mem + mem_used_total))

  printf "%-25s %-8s %-10s %-10s %-8s %-10s %-10s %-8s %-12s\n" \
    "$deploy" "$pods" "${cpu_req_per_pod}m" "${cpu_used}m" "${cpu_eff}%" \
    "${mem_req_per_pod}Mi" "${mem_used}Mi" "${mem_eff}%" "$status"
done

echo ""
echo "═══════════════════════════════════════"
echo "SUMMARY"
echo "═══════════════════════════════════════"
echo "Total CPU requested:  ${total_req_cpu}m"
echo "Total CPU used:       ${total_used_cpu}m"
if [ "$total_req_cpu" -gt 0 ]; then
  overall_cpu_eff=$((total_used_cpu * 100 / total_req_cpu))
  wasted_cpu=$((total_req_cpu - total_used_cpu))
  echo "Overall CPU efficiency: ${overall_cpu_eff}%"
  echo "Wasted CPU:           ${wasted_cpu}m (potential savings)"
fi
echo ""
echo "Total memory requested: ${total_req_mem}Mi"
echo "Total memory used:      ${total_used_mem}Mi"
if [ "$total_req_mem" -gt 0 ]; then
  overall_mem_eff=$((total_used_mem * 100 / total_req_mem))
  wasted_mem=$((total_req_mem - total_used_mem))
  echo "Overall memory efficiency: ${overall_mem_eff}%"
  echo "Wasted memory:          ${wasted_mem}Mi (potential savings)"
fi
echo ""
echo "Over-provisioned workloads: $over_prov_count"
echo "Under-provisioned workloads: $under_prov_count"
```

```bash
#!/bin/bash
# scripts/quota-report.sh
# Usage: ./quota-report.sh <namespace>

NAMESPACE=${1:-deployforge-prod}

echo "╔══════════════════════════════════════════════════════════════╗"
echo "║           Resource Quota Usage Report                       ║"
echo "║           Namespace: $NAMESPACE"
echo "║           Generated: $(date -u +%Y-%m-%dT%H:%M:%SZ)"
echo "╚══════════════════════════════════════════════════════════════╝"
echo ""

for quota in $(kubectl get resourcequota -n "$NAMESPACE" -o jsonpath='{.items[*].metadata.name}'); do
  echo "--- Quota: $quota ---"

  # Check for scope
  scope=$(kubectl get resourcequota "$quota" -n "$NAMESPACE" \
    -o jsonpath='{.spec.scopeSelector.matchExpressions[0].values[0]}' 2>/dev/null)
  if [ -n "$scope" ]; then
    echo "Scope: PriorityClass=$scope"
  fi
  echo ""

  printf "%-30s %-12s %-12s %-10s %-10s\n" "RESOURCE" "USED" "HARD" "USAGE%" "STATUS"
  printf "%-30s %-12s %-12s %-10s %-10s\n" "--------" "----" "----" "------" "------"

  kubectl get resourcequota "$quota" -n "$NAMESPACE" -o json | jq -r '
    .status.hard as $hard | .status.used as $used |
    ($hard | to_entries[]) |
    "\(.key) \($used[.key]) \(.value)"
  ' | while read -r resource used hard; do
    # Parse numeric values for comparison
    used_num=$(echo "$used" | sed 's/[^0-9.]//g')
    hard_num=$(echo "$hard" | sed 's/[^0-9.]//g')

    if [ -n "$hard_num" ] && [ "$hard_num" != "0" ]; then
      pct=$(echo "$used_num $hard_num" | awk '{printf "%.0f", ($1/$2)*100}')
    else
      pct=0
    fi

    if [ "$pct" -ge 90 ]; then
      status="🔴 CRITICAL"
    elif [ "$pct" -ge 80 ]; then
      status="⚠️  WARNING"
    else
      status="✅ OK"
    fi

    printf "%-30s %-12s %-12s %-10s %-10s\n" "$resource" "$used" "$hard" "${pct}%" "$status"
  done

  echo ""
done
```

Verify:
```bash
chmod +x scripts/resource-report.sh scripts/quota-report.sh

./scripts/resource-report.sh deployforge-prod
# → ╔══════════════════════════════════════════════════════════════╗
# → ║           Resource Right-Sizing Report                      ║
# → ╚══════════════════════════════════════════════════════════════╝
# →
# → DEPLOYMENT                PODS     CPU_REQ    CPU_USED   CPU_EFF  MEM_REQ    MEM_USED   MEM_EFF  STATUS
# → ----------                ----     -------    --------   -------  -------    --------   -------  ------
# → deployforge-api           2        200m       45m        22%      256Mi      85Mi       33%      ⚠️  OVER-PROV
# → deployforge-worker        3        500m       100m       20%      512Mi      128Mi      25%      ⚠️  OVER-PROV
# → over-prov                 3        1000m      8m         0%       1024Mi     5Mi        0%       ⚠️  OVER-PROV
# → right-sized               2        100m       42m        42%      128Mi      55Mi       42%      ✅ OK
# →
# → SUMMARY
# → Total CPU requested:  5200m
# → Total CPU used:       510m
# → Overall CPU efficiency: 9%
# → Wasted CPU:           4690m (potential savings)

./scripts/quota-report.sh deployforge-prod
# → ╔══════════════════════════════════════════════════════════════╗
# → ║           Resource Quota Usage Report                       ║
# → ╚══════════════════════════════════════════════════════════════╝
# →
# → --- Quota: compute-quota ---
# → RESOURCE                       USED         HARD         USAGE%     STATUS
# → --------                       ----         ----         ------     ------
# → requests.cpu                   2100m        8            26%        ✅ OK
# → requests.memory                2432Mi       16Gi         14%        ✅ OK
# → pods                           10           50           20%        ✅ OK

# Clean up sample workloads
kubectl delete deployment over-prov right-sized -n deployforge-prod
```

</details>

---

## Checklist

- [ ] Created HPA with custom metrics and behavior policies for DeployForge API
- [ ] Deployed VPA in Off mode and reviewed recommendations for workers
- [ ] Created VPA Auto mode manifest ready for production rollout
- [ ] Set up PriorityClasses for critical, standard, and batch workloads
- [ ] Configured LimitRanges with defaults and bounds for production namespace
- [ ] Created ResourceQuotas with scoped quotas for priority-based budgets
- [ ] Configured staging namespace with reduced quotas
- [ ] Built resource efficiency reporting script
- [ ] Built quota usage monitoring script
- [ ] Identified over-provisioned workloads and documented optimization plan
