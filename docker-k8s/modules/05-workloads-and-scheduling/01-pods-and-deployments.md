# 5.1 — Pods, ReplicaSets & Deployments

## Concept

A Pod is the smallest deployable unit in Kubernetes — not a container, but a _group_ of containers that share a network namespace, IPC namespace, and optionally storage volumes. In practice, most pods contain a single application container, but the multi-container pattern (sidecars, init containers) is fundamental to real-world architectures.

You rarely create pods directly. Instead, you declare a Deployment, which manages a ReplicaSet, which manages Pods. This three-layer hierarchy gives you rolling updates, rollback history, and self-healing — the building blocks that turn "I deployed a container" into "I'm running a production service."

---

## Deep Dive

### Pod Spec Anatomy

Every pod spec lives inside a workload controller's `template` field. Understanding the pod spec is understanding what Kubernetes actually runs.

```yaml
apiVersion: v1
kind: Pod
metadata:
  name: api-gateway
  namespace: deployforge
  labels:
    app: api-gateway
    tier: frontend
spec:
  # Init containers run sequentially BEFORE app containers start
  initContainers:
  - name: wait-for-postgres
    image: busybox:1.36
    command: ['sh', '-c', 'until nc -z postgres-0.postgres.deployforge.svc.cluster.local 5432; do sleep 2; done']

  containers:
  - name: api
    image: deployforge/api-gateway:1.2.0
    ports:
    - containerPort: 3000
      protocol: TCP
    env:
    - name: NODE_ENV
      value: "production"
    - name: DATABASE_URL
      valueFrom:
        secretKeyRef:
          name: postgres-credentials
          key: url
    resources:
      requests:          # Scheduler uses these to find a node
        cpu: "250m"      # 0.25 CPU cores
        memory: "256Mi"  # 256 MiB
      limits:            # Kubelet enforces these hard caps
        cpu: "500m"
        memory: "512Mi"
    volumeMounts:
    - name: config
      mountPath: /app/config
      readOnly: true

  # Sidecar container — runs alongside the main container
  - name: log-shipper
    image: fluent/fluent-bit:2.2
    volumeMounts:
    - name: shared-logs
      mountPath: /var/log/app

  volumes:
  - name: config
    configMap:
      name: api-gateway-config
  - name: shared-logs
    emptyDir: {}

  restartPolicy: Always           # Always | OnFailure | Never
  terminationGracePeriodSeconds: 30
  serviceAccountName: api-gateway
```

```
┌─────────────────────────────────────────────────────────────┐
│                          Pod                                 │
│                                                             │
│  ┌──────────────────┐                                       │
│  │  Init Container   │  Runs first. Must succeed before     │
│  │  wait-for-postgres│  app containers start.               │
│  └────────┬─────────┘                                       │
│           │ completes                                       │
│           ▼                                                 │
│  ┌──────────────────┐  ┌──────────────────┐                 │
│  │  App Container    │  │  Sidecar          │                │
│  │  api-gateway      │  │  log-shipper      │  Run in        │
│  │  :3000            │  │                   │  parallel      │
│  └──────────────────┘  └──────────────────┘                 │
│           │                     │                           │
│           ▼                     ▼                           │
│  ┌──────────────────────────────────────┐                   │
│  │  Shared Resources                     │                   │
│  │  • Network namespace (same IP)        │                   │
│  │  • Volumes (emptyDir: shared-logs)    │                   │
│  │  • IPC namespace                      │                   │
│  └──────────────────────────────────────┘                   │
└─────────────────────────────────────────────────────────────┘
```

> **Key insight:** All containers in a pod share the same network namespace. They communicate via `localhost` and see the same IP address. This is why sidecars work — the log shipper can read from a shared volume that the main container writes to, and a proxy sidecar can intercept traffic on `localhost`.

---

### Init Containers

Init containers run sequentially before any app containers start. Each must complete successfully (exit code 0) before the next one begins. If an init container fails, the kubelet restarts the pod according to the `restartPolicy`.

Common patterns:

| Pattern | Example | Why Init Container? |
|---------|---------|---------------------|
| Wait for dependency | `nc -z postgres 5432` | Don't crash-loop waiting for the database |
| Schema migration | `npx prisma migrate deploy` | Run once before app starts |
| Config generation | Template rendering | Generate config from environment |
| Permission setup | `chown -R 1000:1000 /data` | Fix volume permissions before app needs them |

```yaml
initContainers:
- name: migrate-db
  image: deployforge/api-gateway:1.2.0
  command: ['npx', 'prisma', 'migrate', 'deploy']
  env:
  - name: DATABASE_URL
    valueFrom:
      secretKeyRef:
        name: postgres-credentials
        key: url
```

---

### Sidecar Containers

Sidecar containers run alongside the main application container for the entire pod lifetime. Kubernetes 1.28+ introduced native sidecar support via `restartPolicy: Always` on init containers, which guarantees sidecars start before and stop after the main container.

```yaml
initContainers:
# Native sidecar (K8s 1.28+) — starts before main, stops after main
- name: log-shipper
  image: fluent/fluent-bit:2.2
  restartPolicy: Always       # This makes it a native sidecar
  volumeMounts:
  - name: shared-logs
    mountPath: /var/log/app
```

Common sidecar patterns:

- **Log shipping** — Fluent Bit reads logs from a shared volume
- **Service mesh proxy** — Envoy/Istio intercepts all traffic
- **Config reload** — Watches ConfigMap changes and signals the main container
- **TLS termination** — Handles certificates so the app doesn't need to

---

### ReplicaSets

A ReplicaSet ensures a specified number of pod replicas are running at all times. You almost never create ReplicaSets directly — Deployments manage them for you.

```
┌─────────────────────────────────────────────────────┐
│                     ReplicaSet                       │
│         desiredReplicas: 3                           │
│                                                     │
│   ┌─────────┐    ┌─────────┐    ┌─────────┐        │
│   │  Pod 1  │    │  Pod 2  │    │  Pod 3  │        │
│   │ Running │    │ Running │    │ Running │        │
│   └─────────┘    └─────────┘    └─────────┘        │
│                                                     │
│   Controller loop:                                  │
│   current(3) == desired(3) → do nothing             │
│                                                     │
│   If Pod 2 dies:                                    │
│   current(2) < desired(3) → create new pod          │
└─────────────────────────────────────────────────────┘
```

The ReplicaSet controller watches for pods that match its `selector.matchLabels` and reconciles toward the desired count. This is the core self-healing mechanism — you declare "I want 3 pods" and Kubernetes maintains that state.

> **Key insight:** ReplicaSets use label selectors, not names, to find their pods. If you manually create a pod with matching labels, the ReplicaSet will count it as one of its own — and potentially terminate it if it already has enough replicas.

---

### Deployments

A Deployment is a higher-level controller that manages ReplicaSets and provides:

- **Declarative updates** — Change the pod template and the Deployment controller handles the transition
- **Rolling updates** — Gradually replace old pods with new ones, zero-downtime
- **Rollback** — Revert to any previous revision
- **Revision history** — Track what changed and when

```
┌─────────────────────────────────────────────────────────────────┐
│                        Deployment                                │
│                   api-gateway (revision 3)                        │
│                                                                  │
│   ┌─────────────────────────┐  ┌─────────────────────────┐      │
│   │  ReplicaSet (rev 3)      │  │  ReplicaSet (rev 2)      │     │
│   │  replicas: 3 (active)    │  │  replicas: 0 (scaled down)│     │
│   │                          │  │  (kept for rollback)       │     │
│   │  ┌─────┐┌─────┐┌─────┐ │  └─────────────────────────┘      │
│   │  │Pod 1││Pod 2││Pod 3│ │                                    │
│   │  └─────┘└─────┘└─────┘ │  ┌─────────────────────────┐      │
│   └─────────────────────────┘  │  ReplicaSet (rev 1)      │     │
│                                 │  replicas: 0 (scaled down)│     │
│                                 │  (kept for rollback)       │     │
│                                 └─────────────────────────┘      │
└─────────────────────────────────────────────────────────────────┘
```

#### Deployment YAML

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: api-gateway
  namespace: deployforge
  labels:
    app: api-gateway
    tier: frontend
spec:
  replicas: 3
  revisionHistoryLimit: 10          # Keep 10 old ReplicaSets for rollback
  selector:
    matchLabels:
      app: api-gateway              # Must match template labels
  strategy:
    type: RollingUpdate
    rollingUpdate:
      maxSurge: 1                   # At most 1 extra pod during update
      maxUnavailable: 0             # Never reduce below desired count
  template:
    metadata:
      labels:
        app: api-gateway
        tier: frontend
    spec:
      containers:
      - name: api
        image: deployforge/api-gateway:1.2.0
        ports:
        - containerPort: 3000
        resources:
          requests:
            cpu: "250m"
            memory: "256Mi"
          limits:
            cpu: "500m"
            memory: "512Mi"
```

---

### Update Strategies

#### RollingUpdate (Default)

Gradually replaces old pods with new ones. The two key parameters control the speed vs safety trade-off:

| Parameter | Default | Effect |
|-----------|---------|--------|
| `maxSurge` | 25% | Max pods above desired count during update |
| `maxUnavailable` | 25% | Max pods below desired count during update |

```
# maxSurge: 1, maxUnavailable: 0 (safest — "one at a time, never fewer than desired")
# With 3 replicas:

Step 1:  old old old new(starting)    → 4 pods total (surge=1)
Step 2:  old old new new(ready)       → new pod ready, terminate one old
Step 3:  old new new new(starting)    → 4 pods again
Step 4:  new new new                  → done, 3 pods
```

```
# maxSurge: 0, maxUnavailable: 1 (saves resources — "remove before adding")
# With 3 replicas:

Step 1:  old old ___(terminated)      → 2 pods (unavailable=1)
Step 2:  old old new(starting)        → new pod creating
Step 3:  old old new(ready)           → terminate another old
Step 4:  old new ___(terminated)      → 2 pods again
Step 5:  old new new(starting)        → creating replacement
Step 6:  new new new                  → done
```

> **Production note:** For zero-downtime deploys, use `maxSurge: 1, maxUnavailable: 0`. This is slower but guarantees you always have at least `replicas` pods serving traffic.

#### Recreate

Terminates all old pods before creating new ones. Use this only when:
- The app can't handle two versions running simultaneously (database schema conflicts)
- You're running a single-replica development deployment and don't care about downtime

```yaml
strategy:
  type: Recreate
  # No rollingUpdate parameters
```

---

### Rollback with Revision History

Every time you change a Deployment's pod template, Kubernetes creates a new ReplicaSet (revision). Old ReplicaSets are kept (scaled to 0) for rollback.

```bash
# View rollout status
kubectl rollout status deployment/api-gateway -n deployforge

# View revision history
kubectl rollout history deployment/api-gateway -n deployforge
# REVISION  CHANGE-CAUSE
# 1         <none>
# 2         image update to 1.2.0
# 3         added resource limits

# See details of a specific revision
kubectl rollout history deployment/api-gateway -n deployforge --revision=2

# Rollback to previous revision
kubectl rollout undo deployment/api-gateway -n deployforge

# Rollback to a specific revision
kubectl rollout undo deployment/api-gateway -n deployforge --to-revision=1

# Pause a rollout (for canary-style manual verification)
kubectl rollout pause deployment/api-gateway -n deployforge
# ... verify the new version ...
kubectl rollout resume deployment/api-gateway -n deployforge
```

> **Key insight:** To track _why_ a revision was created, annotate deployments with `kubectl annotate deployment/api-gateway kubernetes.io/change-cause="image update to 1.3.0"` — or use `--record` (deprecated but still works). Without this, `CHANGE-CAUSE` shows `<none>`.

---

### Resource Requests and Limits

Requests and limits are the mechanism by which Kubernetes manages compute resources on shared nodes.

| Field | Used By | Effect |
|-------|---------|--------|
| `requests.cpu` | Scheduler | Minimum CPU guaranteed to the container; scheduler uses this to find a node |
| `requests.memory` | Scheduler | Minimum memory guaranteed; scheduler uses this to find a node |
| `limits.cpu` | Kubelet (CFS) | Maximum CPU the container can use; throttled (not killed) if exceeded |
| `limits.memory` | Kubelet (OOM) | Maximum memory; container is OOM-killed if it exceeds this |

```yaml
resources:
  requests:
    cpu: "250m"        # 250 millicores = 0.25 CPU core
    memory: "256Mi"    # 256 mebibytes
  limits:
    cpu: "500m"        # Throttled beyond this
    memory: "512Mi"    # OOM-killed beyond this
```

```
┌─────────────────────────────────────────────────────────────┐
│  Node: 4 CPU cores, 8Gi memory                              │
│                                                             │
│  Allocatable after system reserved:                          │
│  CPU: 3800m    Memory: 7Gi                                  │
│                                                             │
│  ┌───────────────────┐ ┌───────────────────┐                │
│  │ api-gateway        │ │ worker             │               │
│  │ req: 250m / 256Mi  │ │ req: 500m / 512Mi  │               │
│  │ lim: 500m / 512Mi  │ │ lim: 1000m / 1Gi   │               │
│  └───────────────────┘ └───────────────────┘                │
│                                                             │
│  Remaining allocatable:                                      │
│  CPU: 3050m    Memory: 6.25Gi                               │
│                                                             │
│  Scheduler decision: "This node has room for more pods"      │
└─────────────────────────────────────────────────────────────┘
```

---

### QoS Classes

Kubernetes assigns a QoS class to every pod based on its resource configuration. This determines eviction priority when the node is under memory pressure.

| QoS Class | Criteria | Eviction Priority |
|-----------|----------|-------------------|
| **Guaranteed** | Every container has `requests == limits` for both CPU and memory | Last to be evicted |
| **Burstable** | At least one container has a request or limit set, but they don't all match | Evicted after BestEffort |
| **BestEffort** | No container has any requests or limits | First to be evicted |

```yaml
# Guaranteed — requests equal limits for all containers
resources:
  requests:
    cpu: "500m"
    memory: "512Mi"
  limits:
    cpu: "500m"        # Same as request
    memory: "512Mi"    # Same as request
```

```yaml
# Burstable — requests set but different from limits
resources:
  requests:
    cpu: "250m"
    memory: "256Mi"
  limits:
    cpu: "500m"        # Different from request
    memory: "512Mi"    # Different from request
```

```yaml
# BestEffort — no resources specified at all
# (container spec with no resources block)
```

> **Production note:** Always set both requests and limits for production workloads. For databases and latency-sensitive services (like DeployForge's API Gateway), use Guaranteed QoS. For batch workers, Burstable is fine since they can tolerate brief eviction.

---

## Code Examples

### Example 1: Create a Deployment with kubectl

```bash
# Imperative — useful for quick tests
kubectl create deployment api-gateway \
  --image=nginx:alpine \
  --replicas=3 \
  -n deployforge

# Check what was created
kubectl get deployment,replicaset,pods -n deployforge -l app=api-gateway
# → 1 Deployment, 1 ReplicaSet, 3 Pods

# Scale up
kubectl scale deployment/api-gateway --replicas=5 -n deployforge

# Update the image (triggers rolling update)
kubectl set image deployment/api-gateway \
  api-gateway=nginx:1.25 \
  -n deployforge

# Watch the rollout
kubectl rollout status deployment/api-gateway -n deployforge --watch
```

### Example 2: Generate YAML with Dry Run

```bash
# Generate a Deployment manifest without creating it
kubectl create deployment api-gateway \
  --image=deployforge/api-gateway:1.2.0 \
  --replicas=3 \
  --dry-run=client -o yaml -n deployforge > api-gateway-deployment.yaml

# Edit the YAML to add resources, strategy, probes, etc.
# Then apply it
kubectl apply -f api-gateway-deployment.yaml
```

### Example 3: Observe Rolling Update in Action

```bash
# Terminal 1 — watch pods in real time
kubectl get pods -n deployforge -l app=api-gateway -w

# Terminal 2 — trigger an update
kubectl set image deployment/api-gateway \
  api-gateway=nginx:1.25-alpine \
  -n deployforge

# You'll see new pods creating while old ones terminate:
# api-gateway-7d4f8b6c-abc12   1/1   Running     0     5m
# api-gateway-7d4f8b6c-def34   1/1   Running     0     5m
# api-gateway-7d4f8b6c-ghi56   1/1   Running     0     5m
# api-gateway-8e5f9c7d-jkl78   0/1   Pending     0     0s     ← new
# api-gateway-8e5f9c7d-jkl78   1/1   Running     0     3s     ← new is ready
# api-gateway-7d4f8b6c-abc12   1/1   Terminating 0     5m     ← old removed
```

### Example 4: Inspect ReplicaSets After Multiple Updates

```bash
# After several image updates, check ReplicaSets
kubectl get replicasets -n deployforge -l app=api-gateway
# NAME                          DESIRED   CURRENT   READY   AGE
# api-gateway-7d4f8b6c          0         0         0       30m   (rev 1)
# api-gateway-8e5f9c7d          0         0         0       20m   (rev 2)
# api-gateway-9f6g0d8e          3         3         3       5m    (rev 3, active)

# Each old ReplicaSet is kept (scaled to 0) for rollback
# Controlled by revisionHistoryLimit (default: 10)
```

---

## Try It Yourself

### Challenge 1: Zero-Downtime Deployment

Create a Deployment for the DeployForge API Gateway with these requirements:
- 3 replicas, image `nginx:1.24-alpine`
- RollingUpdate strategy: `maxSurge: 1`, `maxUnavailable: 0`
- Resource requests: 100m CPU, 128Mi memory
- Resource limits: 200m CPU, 256Mi memory
- Labels: `app: api-gateway`, `tier: frontend`

Then update the image to `nginx:1.25-alpine` and verify zero-downtime rollout.

<details>
<summary>Show solution</summary>

```yaml
# api-gateway-deployment.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: api-gateway
  namespace: deployforge
  labels:
    app: api-gateway
    tier: frontend
spec:
  replicas: 3
  revisionHistoryLimit: 10
  selector:
    matchLabels:
      app: api-gateway
  strategy:
    type: RollingUpdate
    rollingUpdate:
      maxSurge: 1
      maxUnavailable: 0
  template:
    metadata:
      labels:
        app: api-gateway
        tier: frontend
    spec:
      containers:
      - name: api-gateway
        image: nginx:1.24-alpine
        ports:
        - containerPort: 80
        resources:
          requests:
            cpu: "100m"
            memory: "128Mi"
          limits:
            cpu: "200m"
            memory: "256Mi"
```

```bash
#!/bin/bash
set -euo pipefail

# Apply the deployment
kubectl apply -f api-gateway-deployment.yaml

# Wait for rollout to complete
kubectl rollout status deployment/api-gateway -n deployforge

# Verify 3 pods are running
kubectl get pods -n deployforge -l app=api-gateway
# → 3 pods, all Running

# Check QoS class (should be Burstable since requests != limits)
kubectl get pod -n deployforge -l app=api-gateway -o jsonpath='{.items[0].status.qosClass}'
# → Burstable

# Now update the image
kubectl set image deployment/api-gateway \
  api-gateway=nginx:1.25-alpine \
  -n deployforge

# Watch the rollout — you should always see at least 3 Running pods
kubectl rollout status deployment/api-gateway -n deployforge

# Verify the new image
kubectl get pods -n deployforge -l app=api-gateway \
  -o jsonpath='{range .items[*]}{.spec.containers[0].image}{"\n"}{end}'
# → nginx:1.25-alpine (all 3 pods)
```

</details>

### Challenge 2: Rollback After a Bad Deploy

Deploy a broken image (`nginx:99.99-doesnotexist`) and practice rollback:
1. Trigger the update
2. Observe the failed rollout
3. Check rollout history
4. Roll back to the working revision
5. Verify the rollback succeeded

<details>
<summary>Show solution</summary>

```bash
#!/bin/bash
set -euo pipefail

# Deploy a broken image
kubectl set image deployment/api-gateway \
  api-gateway=nginx:99.99-doesnotexist \
  -n deployforge

# Watch the rollout — it will stall because the image doesn't exist
kubectl rollout status deployment/api-gateway -n deployforge --timeout=30s || true
# → Waiting for deployment "api-gateway" rollout to finish...

# Check pod status — you'll see ImagePullBackOff
kubectl get pods -n deployforge -l app=api-gateway
# → Some pods in ErrImagePull or ImagePullBackOff

# Check rollout history
kubectl rollout history deployment/api-gateway -n deployforge

# Roll back to the previous working version
kubectl rollout undo deployment/api-gateway -n deployforge

# Watch it recover
kubectl rollout status deployment/api-gateway -n deployforge

# Verify pods are healthy again
kubectl get pods -n deployforge -l app=api-gateway
# → All 3 pods Running with the previous image
```

</details>

---

## Capstone Connection

**DeployForge** relies on Pods, ReplicaSets, and Deployments as the backbone of its stateless services:

- **API Gateway Deployment** — The Express/TypeScript gateway runs as a Deployment with `maxSurge: 1, maxUnavailable: 0` for zero-downtime updates. Resource requests ensure the scheduler places pods on nodes with capacity; limits prevent a single gateway from starving other services.
- **Worker Deployment** — BullMQ workers are also Deployments, but with different resource profiles (higher CPU for processing, lower memory) and a more aggressive `maxUnavailable: 1` since workers can tolerate brief capacity reductions during updates.
- **Init containers** — The API Gateway uses an init container to wait for PostgreSQL readiness before starting, preventing crash loops on cold starts.
- **Sidecar pattern** — In Module 08 (Observability), you'll add a Fluent Bit sidecar to ship structured logs from each DeployForge pod to a centralized logging backend.
- **QoS classes** — PostgreSQL and Redis will use Guaranteed QoS (Module 05.2, StatefulSets) to avoid eviction under memory pressure, while workers use Burstable QoS.
