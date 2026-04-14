# 5.3 — Scheduling: Affinity, Taints & Tolerations

## Concept

The Kubernetes scheduler's job is deceptively simple: given a pod that needs a node, find the best node to run it on. But "best" is where it gets complex. The scheduler must balance resource availability, hardware requirements, availability zone distribution, co-location preferences, and administrative constraints — all in milliseconds.

Understanding scheduling is essential for production clusters. Without it, your database might land on the same node as a CPU-hungry batch job, your replicas might all cluster on one availability zone, or your pods might get stuck in Pending because you don't understand why the scheduler is rejecting every node.

---

## Deep Dive

### How the Scheduler Works

The default scheduler (`kube-scheduler`) follows a two-phase process for every unscheduled pod:

```
┌──────────────────────────────────────────────────────────────────┐
│                    Scheduler Pipeline                              │
│                                                                   │
│  Unscheduled Pod                                                  │
│       │                                                           │
│       ▼                                                           │
│  ┌─────────────────────────────────────────────────────────┐      │
│  │  Phase 1: FILTERING (Predicates)                        │      │
│  │                                                         │      │
│  │  "Which nodes CAN run this pod?"                        │      │
│  │                                                         │      │
│  │  ✗ Node A — insufficient CPU (1800m requested, 500m    │      │
│  │             available)                                   │      │
│  │  ✓ Node B — 2000m CPU available, all taints tolerated   │      │
│  │  ✓ Node C — 1500m CPU available, all taints tolerated   │      │
│  │  ✗ Node D — taint "gpu-only" not tolerated              │      │
│  │                                                         │      │
│  │  Result: [Node B, Node C] pass filtering                │      │
│  └─────────────────────────────────────────────────────────┘      │
│       │                                                           │
│       ▼                                                           │
│  ┌─────────────────────────────────────────────────────────┐      │
│  │  Phase 2: SCORING (Priorities)                          │      │
│  │                                                         │      │
│  │  "Which surviving node is BEST?"                        │      │
│  │                                                         │      │
│  │  Node B: resource balance=7, affinity=5, spread=8 → 20 │      │
│  │  Node C: resource balance=6, affinity=9, spread=4 → 19 │      │
│  │                                                         │      │
│  │  Winner: Node B (score 20)                              │      │
│  └─────────────────────────────────────────────────────────┘      │
│       │                                                           │
│       ▼                                                           │
│  Bind pod to Node B                                               │
└──────────────────────────────────────────────────────────────────┘
```

Key filter plugins:

| Filter | What It Checks |
|--------|----------------|
| `NodeResourcesFit` | Does the node have enough CPU/memory for the pod's requests? |
| `NodeAffinity` | Does the node match the pod's nodeSelector or nodeAffinity? |
| `TaintToleration` | Does the pod tolerate the node's taints? |
| `PodTopologySpread` | Would scheduling here violate topology spread constraints? |
| `InterPodAffinity` | Does this node satisfy pod affinity/anti-affinity rules? |
| `NodeUnschedulable` | Is the node cordoned? |

Key scoring plugins:

| Scorer | What It Prefers |
|--------|-----------------|
| `NodeResourcesBalancedAllocation` | Nodes with balanced CPU/memory usage |
| `ImageLocality` | Nodes that already have the container image cached |
| `InterPodAffinity` | Nodes that satisfy preferred pod affinity rules |
| `NodeAffinity` | Nodes that match preferred node affinity rules |
| `TaintToleration` | Nodes with fewer taints |

---

### nodeSelector

The simplest scheduling constraint. The pod only runs on nodes whose labels match all key-value pairs in the selector.

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: api-gateway
  namespace: deployforge
spec:
  replicas: 3
  selector:
    matchLabels:
      app: api-gateway
  template:
    metadata:
      labels:
        app: api-gateway
    spec:
      nodeSelector:
        kubernetes.io/os: linux          # Built-in label
        node.kubernetes.io/instance-type: m5.large  # Cloud-provider label
      containers:
      - name: api
        image: deployforge/api-gateway:1.2.0
```

```bash
# View node labels
kubectl get nodes --show-labels

# Add a custom label
kubectl label node deployforge-worker disk=ssd

# Remove a label
kubectl label node deployforge-worker disk-
```

> **Key insight:** `nodeSelector` is a hard constraint — if no node matches, the pod stays Pending forever. Use node affinity for soft preferences.

---

### Node Affinity

Node affinity is the expressive successor to `nodeSelector`. It supports two modes:

| Mode | Behavior | Equivalent |
|------|----------|------------|
| `requiredDuringSchedulingIgnoredDuringExecution` | Hard constraint — pod won't schedule without a match | Like `nodeSelector` but with richer operators |
| `preferredDuringSchedulingIgnoredDuringExecution` | Soft preference — scheduler prefers matching nodes but will fall back | No `nodeSelector` equivalent |

> **Note:** "IgnoredDuringExecution" means: if a node's labels change after the pod is scheduled, the pod is NOT evicted. It stays where it is.

```yaml
spec:
  affinity:
    nodeAffinity:
      # Hard requirement: must be a Linux node in us-east-1a or us-east-1b
      requiredDuringSchedulingIgnoredDuringExecution:
        nodeSelectorTerms:
        - matchExpressions:
          - key: kubernetes.io/os
            operator: In
            values: ["linux"]
          - key: topology.kubernetes.io/zone
            operator: In
            values: ["us-east-1a", "us-east-1b"]

      # Soft preference: prefer nodes with SSD storage
      preferredDuringSchedulingIgnoredDuringExecution:
      - weight: 80                   # 1-100, higher = stronger preference
        preference:
          matchExpressions:
          - key: disk-type
            operator: In
            values: ["ssd"]
      - weight: 20                   # Lower weight — less important
        preference:
          matchExpressions:
          - key: tier
            operator: In
            values: ["dedicated"]
```

Supported operators:

| Operator | Meaning | Example |
|----------|---------|---------|
| `In` | Label value is in the list | `zone In [us-east-1a, us-east-1b]` |
| `NotIn` | Label value is not in the list | `tier NotIn [spot]` |
| `Exists` | Label key exists (any value) | `gpu Exists` |
| `DoesNotExist` | Label key does not exist | `maintenance DoesNotExist` |
| `Gt` | Greater than (numeric) | `cores Gt 4` |
| `Lt` | Less than (numeric) | `cores Lt 32` |

---

### Pod Affinity and Anti-Affinity

Pod affinity/anti-affinity lets you control co-location based on _what other pods are running on a node_, not node labels.

```
┌─────────────────────────────────────────────────────────────────┐
│  Pod Anti-Affinity: "Don't put two api-gateway pods on the      │
│                      same node"                                  │
│                                                                  │
│  ┌───────────────────┐     ┌───────────────────┐                │
│  │  Node 1            │     │  Node 2            │               │
│  │  ┌───────────────┐│     │  ┌───────────────┐│               │
│  │  │ api-gateway-0 ││     │  │ api-gateway-1 ││               │
│  │  └───────────────┘│     │  └───────────────┘│               │
│  │                    │     │                    │               │
│  │  ✗ api-gateway-2  │     │  ✗ api-gateway-2  │               │
│  │  (anti-affinity   │     │  (anti-affinity    │               │
│  │   blocks this)    │     │   blocks this)     │               │
│  └───────────────────┘     └───────────────────┘                │
│                                                                  │
│  ┌───────────────────┐                                          │
│  │  Node 3            │ ← api-gateway-2 scheduled here          │
│  │  ┌───────────────┐│                                          │
│  │  │ api-gateway-2 ││                                          │
│  │  └───────────────┘│                                          │
│  └───────────────────┘                                          │
│                                                                  │
│  Pod Affinity: "Put the worker near the redis pod"               │
│                                                                  │
│  ┌───────────────────┐     ┌───────────────────┐                │
│  │  Node 1            │     │  Node 2            │               │
│  │  ┌─────┐ ┌──────┐│     │                    │               │
│  │  │redis│ │worker││     │  (no redis here,   │               │
│  │  └─────┘ └──────┘│     │   so worker avoids)│               │
│  └───────────────────┘     └───────────────────┘                │
└─────────────────────────────────────────────────────────────────┘
```

#### Pod Anti-Affinity (Spread Replicas Across Nodes)

```yaml
spec:
  affinity:
    podAntiAffinity:
      # Hard: never two api-gateway pods on the same node
      requiredDuringSchedulingIgnoredDuringExecution:
      - labelSelector:
          matchExpressions:
          - key: app
            operator: In
            values: ["api-gateway"]
        topologyKey: kubernetes.io/hostname    # "same node" = same hostname

      # Soft: try to spread across availability zones
      preferredDuringSchedulingIgnoredDuringExecution:
      - weight: 100
        podAffinityTerm:
          labelSelector:
            matchExpressions:
            - key: app
              operator: In
              values: ["api-gateway"]
          topologyKey: topology.kubernetes.io/zone  # "same zone"
```

#### Pod Affinity (Co-locate with Related Pods)

```yaml
spec:
  affinity:
    podAffinity:
      # Prefer to place workers on the same node as Redis (lower latency)
      preferredDuringSchedulingIgnoredDuringExecution:
      - weight: 50
        podAffinityTerm:
          labelSelector:
            matchExpressions:
            - key: app
              operator: In
              values: ["redis"]
          topologyKey: kubernetes.io/hostname
```

The `topologyKey` determines the "scope" of affinity:

| topologyKey | Scope | Meaning |
|-------------|-------|---------|
| `kubernetes.io/hostname` | Node | Same physical/virtual machine |
| `topology.kubernetes.io/zone` | Zone | Same availability zone |
| `topology.kubernetes.io/region` | Region | Same cloud region |

> **Production note:** Required pod anti-affinity can make pods unschedulable if you don't have enough nodes. With 3 replicas and required anti-affinity by hostname, you need at least 3 nodes. Prefer soft anti-affinity for most cases and reserve hard anti-affinity for critical isolation requirements.

---

### Taints and Tolerations

Taints are applied to nodes to _repel_ pods. Tolerations are applied to pods to _allow_ scheduling on tainted nodes. This is the inverse of affinity — instead of attracting pods to nodes, taints push pods away.

```
┌─────────────────────────────────────────────────────────────────┐
│  Taints: "Keep off unless you explicitly tolerate"               │
│                                                                  │
│  Node: gpu-node                                                  │
│  Taint: gpu=true:NoSchedule                                     │
│                                                                  │
│  ┌────────────────────┐                                         │
│  │  Pod: api-gateway   │ ──── ✗ No toleration → NOT scheduled   │
│  │  (no tolerations)   │                                        │
│  └────────────────────┘                                         │
│                                                                  │
│  ┌────────────────────┐                                         │
│  │  Pod: ml-trainer    │ ──── ✓ Has toleration → scheduled      │
│  │  toleration:        │                                        │
│  │    gpu=true         │                                        │
│  └────────────────────┘                                         │
│                                                                  │
│  Important: tolerating a taint does NOT guarantee scheduling     │
│  on that node. It only ALLOWS it. Use nodeAffinity to            │
│  ATTRACT pods to specific nodes.                                 │
└─────────────────────────────────────────────────────────────────┘
```

#### Taint Effects

| Effect | Behavior |
|--------|----------|
| `NoSchedule` | New pods without a toleration won't be scheduled on this node. Existing pods are unaffected. |
| `PreferNoSchedule` | Soft version — scheduler tries to avoid but will use the node if necessary. |
| `NoExecute` | New pods are blocked AND existing pods without a toleration are evicted. |

```bash
# Add a taint to a node
kubectl taint nodes deployforge-worker dedicated=database:NoSchedule

# Remove a taint (note the trailing dash)
kubectl taint nodes deployforge-worker dedicated=database:NoSchedule-

# View taints on a node
kubectl describe node deployforge-worker | grep -A5 Taints
```

#### Tolerating Taints

```yaml
spec:
  tolerations:
  # Exact match: tolerate key=value with specific effect
  - key: "dedicated"
    operator: "Equal"
    value: "database"
    effect: "NoSchedule"

  # Exists match: tolerate any value for the key
  - key: "node.kubernetes.io/not-ready"
    operator: "Exists"
    effect: "NoExecute"
    tolerationSeconds: 300        # Tolerate for 5 min, then evict

  # Tolerate ALL taints (use with caution — DaemonSets often do this)
  - operator: "Exists"
```

Common built-in taints:

| Taint | Applied When | Default Toleration |
|-------|-------------|-------------------|
| `node.kubernetes.io/not-ready` | Node fails health check | Pods tolerate for 300s |
| `node.kubernetes.io/unreachable` | Node is unreachable | Pods tolerate for 300s |
| `node.kubernetes.io/memory-pressure` | Node is low on memory | Evicts BestEffort pods |
| `node.kubernetes.io/disk-pressure` | Node is low on disk | Prevents new scheduling |
| `node-role.kubernetes.io/control-plane` | Control plane nodes | Only system pods tolerate |

> **Key insight:** Taints + tolerations = "keep off unless allowed." Node affinity = "attract to specific nodes." For dedicated nodes (e.g., database-only nodes), you need BOTH: a taint to repel other pods AND node affinity on the database to attract it there.

---

### Topology Spread Constraints

Topology spread constraints give you fine-grained control over how pods are distributed across failure domains (nodes, zones, regions).

```yaml
spec:
  topologySpreadConstraints:
  - maxSkew: 1                         # Max difference in pod count between zones
    topologyKey: topology.kubernetes.io/zone
    whenUnsatisfiable: DoNotSchedule   # Hard constraint
    labelSelector:
      matchLabels:
        app: api-gateway
    matchLabelKeys:                    # K8s 1.27+: scope to same revision
    - pod-template-hash

  - maxSkew: 1
    topologyKey: kubernetes.io/hostname
    whenUnsatisfiable: ScheduleAnyway  # Soft constraint (best-effort)
    labelSelector:
      matchLabels:
        app: api-gateway
```

```
# maxSkew: 1 across zones with 4 replicas and 3 zones:

Zone A: ██        (1 pod)
Zone B: ██        (1 pod)       skew = max - min = 2 - 1 = 1 ✓
Zone C: ████      (2 pods)

# If Zone C already has 2 and A has 1, next pod goes to A or B (not C)
```

| Parameter | Options | Meaning |
|-----------|---------|---------|
| `maxSkew` | Integer ≥ 1 | Maximum allowed difference in pod count between topology domains |
| `topologyKey` | Node label key | Defines the topology domain (zone, node, region) |
| `whenUnsatisfiable` | `DoNotSchedule` / `ScheduleAnyway` | Hard vs soft constraint |
| `matchLabelKeys` | List of label keys | Scope calculation to pods with matching values for these keys |

> **Production note:** Topology spread constraints are the recommended way to distribute pods across zones for high availability. They're more predictable than pod anti-affinity for this use case because they consider global distribution, not just pairwise relationships.

---

### Priority Classes and Preemption

Priority classes let you define scheduling priority. When a high-priority pod can't be scheduled due to insufficient resources, the scheduler can preempt (evict) lower-priority pods to make room.

```yaml
apiVersion: scheduling.k8s.io/v1
kind: PriorityClass
metadata:
  name: deployforge-critical
value: 1000000
globalDefault: false
preemptionPolicy: PreemptLowerPriority
description: "Critical DeployForge services (API Gateway, database)"
---
apiVersion: scheduling.k8s.io/v1
kind: PriorityClass
metadata:
  name: deployforge-standard
value: 500000
globalDefault: false
preemptionPolicy: PreemptLowerPriority
description: "Standard DeployForge services (workers, batch jobs)"
---
apiVersion: scheduling.k8s.io/v1
kind: PriorityClass
metadata:
  name: deployforge-batch
value: 100000
globalDefault: false
preemptionPolicy: Never              # Never preempt other pods
description: "Batch jobs — low priority, no preemption"
```

```yaml
# Using a priority class in a Deployment
spec:
  template:
    spec:
      priorityClassName: deployforge-critical
      containers:
      - name: api
        image: deployforge/api-gateway:1.2.0
```

> **Key insight:** Built-in `system-cluster-critical` (2 billion) and `system-node-critical` (2 billion + 1000) priority classes exist for core cluster components. Never assign values near these ranges for application workloads.

---

## Code Examples

### Example 1: Label Nodes and Use nodeSelector

```bash
# Label nodes (in a kind cluster, nodes are named like deployforge-worker, deployforge-worker2)
kubectl label node deployforge-worker tier=frontend
kubectl label node deployforge-worker2 tier=backend

# Verify labels
kubectl get nodes -L tier
# NAME                          STATUS   ROLES           AGE   VERSION   TIER
# deployforge-control-plane     Ready    control-plane   1d    v1.28.0
# deployforge-worker            Ready    <none>          1d    v1.28.0   frontend
# deployforge-worker2           Ready    <none>          1d    v1.28.0   backend

# Deploy API Gateway only to frontend-tier nodes
cat <<'EOF' | kubectl apply -f -
apiVersion: apps/v1
kind: Deployment
metadata:
  name: api-gateway
  namespace: deployforge
spec:
  replicas: 2
  selector:
    matchLabels:
      app: api-gateway
  template:
    metadata:
      labels:
        app: api-gateway
    spec:
      nodeSelector:
        tier: frontend
      containers:
      - name: api
        image: nginx:alpine
        resources:
          requests:
            cpu: "100m"
            memory: "128Mi"
EOF

# Verify — all pods should be on deployforge-worker
kubectl get pods -n deployforge -l app=api-gateway -o wide
```

### Example 2: Taint a Node for Dedicated Database Use

```bash
# Taint a node so only database pods can run there
kubectl taint nodes deployforge-worker2 dedicated=database:NoSchedule

# Deploy PostgreSQL with matching toleration and node affinity
cat <<'EOF' | kubectl apply -f -
apiVersion: apps/v1
kind: StatefulSet
metadata:
  name: postgres
  namespace: deployforge
spec:
  serviceName: postgres
  replicas: 1
  selector:
    matchLabels:
      app: postgres
  template:
    metadata:
      labels:
        app: postgres
    spec:
      tolerations:
      - key: "dedicated"
        operator: "Equal"
        value: "database"
        effect: "NoSchedule"
      affinity:
        nodeAffinity:
          requiredDuringSchedulingIgnoredDuringExecution:
            nodeSelectorTerms:
            - matchExpressions:
              - key: dedicated
                operator: In
                values: ["database"]
      containers:
      - name: postgres
        image: postgres:15-alpine
        env:
        - name: POSTGRES_PASSWORD
          value: "password"
        resources:
          requests:
            cpu: "250m"
            memory: "256Mi"
EOF

# Verify postgres lands on the tainted node
kubectl get pods -n deployforge -l app=postgres -o wide
# → Running on deployforge-worker2

# Verify api-gateway does NOT land on the tainted node
kubectl get pods -n deployforge -l app=api-gateway -o wide
# → Running on deployforge-worker (not worker2)
```

### Example 3: Topology Spread for API Gateway

```bash
cat <<'EOF' | kubectl apply -f -
apiVersion: apps/v1
kind: Deployment
metadata:
  name: api-gateway
  namespace: deployforge
spec:
  replicas: 4
  selector:
    matchLabels:
      app: api-gateway
  template:
    metadata:
      labels:
        app: api-gateway
    spec:
      topologySpreadConstraints:
      - maxSkew: 1
        topologyKey: kubernetes.io/hostname
        whenUnsatisfiable: ScheduleAnyway
        labelSelector:
          matchLabels:
            app: api-gateway
      containers:
      - name: api
        image: nginx:alpine
        resources:
          requests:
            cpu: "50m"
            memory: "64Mi"
EOF

# Check distribution across nodes
kubectl get pods -n deployforge -l app=api-gateway -o wide
# → Pods distributed evenly across worker nodes
```

---

## Try It Yourself

### Challenge 1: Multi-Constraint Scheduling

Set up the DeployForge cluster with these scheduling constraints:
1. Label `deployforge-worker` as `tier=general` and `deployforge-worker2` as `tier=database`
2. Taint `deployforge-worker2` with `dedicated=database:NoSchedule`
3. Deploy the API Gateway (2 replicas) with `nodeSelector: tier: general`
4. Deploy PostgreSQL with a toleration for the database taint + node affinity targeting `tier=database`
5. Verify each workload lands on the correct node

<details>
<summary>Show solution</summary>

```bash
#!/bin/bash
set -euo pipefail

NS="deployforge"

echo "=== Step 1: Label nodes ==="
kubectl label node deployforge-worker tier=general --overwrite
kubectl label node deployforge-worker2 tier=database --overwrite
kubectl get nodes -L tier

echo ""
echo "=== Step 2: Taint the database node ==="
kubectl taint nodes deployforge-worker2 dedicated=database:NoSchedule --overwrite

echo ""
echo "=== Step 3: Deploy API Gateway ==="
cat <<'EOF' | kubectl apply -f -
apiVersion: apps/v1
kind: Deployment
metadata:
  name: api-gateway
  namespace: deployforge
spec:
  replicas: 2
  selector:
    matchLabels:
      app: api-gateway
  template:
    metadata:
      labels:
        app: api-gateway
    spec:
      nodeSelector:
        tier: general
      containers:
      - name: api
        image: nginx:alpine
        resources:
          requests:
            cpu: "50m"
            memory: "64Mi"
EOF
kubectl rollout status deployment/api-gateway -n $NS --timeout=60s

echo ""
echo "=== Step 4: Deploy PostgreSQL ==="
cat <<'EOF' | kubectl apply -f -
apiVersion: v1
kind: Service
metadata:
  name: postgres
  namespace: deployforge
spec:
  clusterIP: None
  selector:
    app: postgres
  ports:
  - port: 5432
EOF

cat <<'EOF' | kubectl apply -f -
apiVersion: apps/v1
kind: StatefulSet
metadata:
  name: postgres
  namespace: deployforge
spec:
  serviceName: postgres
  replicas: 1
  selector:
    matchLabels:
      app: postgres
  template:
    metadata:
      labels:
        app: postgres
    spec:
      tolerations:
      - key: "dedicated"
        operator: "Equal"
        value: "database"
        effect: "NoSchedule"
      affinity:
        nodeAffinity:
          requiredDuringSchedulingIgnoredDuringExecution:
            nodeSelectorTerms:
            - matchExpressions:
              - key: tier
                operator: In
                values: ["database"]
      containers:
      - name: postgres
        image: postgres:15-alpine
        env:
        - name: POSTGRES_PASSWORD
          value: "password"
        resources:
          requests:
            cpu: "250m"
            memory: "256Mi"
          limits:
            cpu: "250m"
            memory: "256Mi"
        volumeMounts:
        - name: data
          mountPath: /var/lib/postgresql/data
  volumeClaimTemplates:
  - metadata:
      name: data
    spec:
      accessModes: ["ReadWriteOnce"]
      resources:
        requests:
          storage: 1Gi
EOF
kubectl rollout status statefulset/postgres -n $NS --timeout=60s

echo ""
echo "=== Step 5: Verify placement ==="
echo "API Gateway pods (should be on deployforge-worker):"
kubectl get pods -n $NS -l app=api-gateway -o wide
echo ""
echo "PostgreSQL pods (should be on deployforge-worker2):"
kubectl get pods -n $NS -l app=postgres -o wide
```

</details>

### Challenge 2: Pod Anti-Affinity for High Availability

Modify the API Gateway deployment so that:
- Hard anti-affinity prevents two API Gateway pods from running on the same node
- Soft anti-affinity tries to spread across zones (using `topology.kubernetes.io/zone`)
- Deploy 2 replicas and verify they land on different nodes

<details>
<summary>Show solution</summary>

```yaml
# api-gateway-ha.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: api-gateway
  namespace: deployforge
spec:
  replicas: 2
  selector:
    matchLabels:
      app: api-gateway
  template:
    metadata:
      labels:
        app: api-gateway
    spec:
      affinity:
        podAntiAffinity:
          requiredDuringSchedulingIgnoredDuringExecution:
          - labelSelector:
              matchExpressions:
              - key: app
                operator: In
                values: ["api-gateway"]
            topologyKey: kubernetes.io/hostname
          preferredDuringSchedulingIgnoredDuringExecution:
          - weight: 100
            podAffinityTerm:
              labelSelector:
                matchExpressions:
                - key: app
                  operator: In
                  values: ["api-gateway"]
              topologyKey: topology.kubernetes.io/zone
      containers:
      - name: api
        image: nginx:alpine
        ports:
        - containerPort: 80
        resources:
          requests:
            cpu: "50m"
            memory: "64Mi"
```

```bash
#!/bin/bash
set -euo pipefail

kubectl apply -f api-gateway-ha.yaml
kubectl rollout status deployment/api-gateway -n deployforge --timeout=60s

echo "=== Pod placement (should be on different nodes) ==="
kubectl get pods -n deployforge -l app=api-gateway -o wide

echo ""
echo "=== Verify anti-affinity is configured ==="
kubectl get deployment api-gateway -n deployforge \
  -o jsonpath='{.spec.template.spec.affinity.podAntiAffinity}' | python3 -m json.tool
```

</details>

---

## Capstone Connection

**DeployForge** uses scheduling constraints to ensure reliability and resource isolation:

- **Pod anti-affinity** — API Gateway replicas use hard anti-affinity by hostname to guarantee they run on different nodes. If one node fails, the other replica continues serving traffic without interruption.
- **Dedicated database nodes** — In production, PostgreSQL and Redis run on nodes tainted with `dedicated=database:NoSchedule`. This prevents noisy-neighbor issues where a CPU-hungry worker starves the database. The StatefulSets tolerate this taint and use node affinity to target these nodes.
- **Topology spread** — Workers use `topologySpreadConstraints` with `maxSkew: 1` across zones for even distribution. This ensures queue processing capacity survives a zone failure.
- **Priority classes** — The API Gateway and databases use `deployforge-critical` priority. Workers use `deployforge-standard`. Batch jobs use `deployforge-batch` with `preemptionPolicy: Never`. If the cluster is resource-constrained, critical services preempt workers, but workers never preempt other workloads.
- **Control plane taints** — Kind's control plane node has the `node-role.kubernetes.io/control-plane:NoSchedule` taint by default. DeployForge's DaemonSet (Nginx) doesn't tolerate this, so it only runs on worker nodes — which is exactly what you want.
