# 5.4 — Pod Lifecycle, Probes & Disruption Budgets

## Concept

A pod goes through a well-defined lifecycle from creation to termination, and understanding each phase is critical for building reliable services. Probes (startup, liveness, readiness) are how Kubernetes knows if your application is healthy, and getting them wrong is one of the most common causes of production incidents — either your app gets killed when it's still starting up, or broken instances keep receiving traffic because you forgot a readiness probe.

Disruption budgets complete the picture: they tell Kubernetes how many pods it can safely take offline during voluntary disruptions like node upgrades and cluster autoscaler scale-downs. Without PDBs, a `kubectl drain` can take down all your replicas simultaneously.

---

## Deep Dive

### Pod Phases

Every pod has a `.status.phase` that represents its high-level state:

```
┌──────────────────────────────────────────────────────────────┐
│                      Pod Lifecycle                             │
│                                                               │
│  kubectl apply                                                │
│       │                                                       │
│       ▼                                                       │
│  ┌─────────┐   Scheduler assigns    ┌─────────┐              │
│  │ Pending  │ ──────────────────────▶│ Running  │              │
│  │         │   a node + kubelet     │         │              │
│  │         │   pulls images +       │         │              │
│  │         │   starts containers    │         │              │
│  └─────────┘                        └────┬────┘              │
│       │                                  │                   │
│       │ (can't schedule:                 │                   │
│       │  insufficient resources,         ├──────────────┐    │
│       │  image pull error,               │              │    │
│       │  no matching node)               ▼              ▼    │
│       │                           ┌───────────┐ ┌────────┐  │
│       │                           │ Succeeded  │ │ Failed  │  │
│       │                           │ (exit 0)   │ │(exit≠0) │  │
│       │                           └───────────┘ └────────┘  │
│       │                                                      │
│       │          ┌─────────┐                                 │
│       └─────────▶│ Unknown  │  (node lost contact)           │
│                  └─────────┘                                 │
└──────────────────────────────────────────────────────────────┘
```

| Phase | Meaning |
|-------|---------|
| **Pending** | Pod accepted by the cluster but not yet running. Could be waiting for scheduling, image pull, or init containers. |
| **Running** | At least one container is running, starting, or restarting. |
| **Succeeded** | All containers terminated with exit code 0 and won't be restarted. Common for Jobs. |
| **Failed** | All containers terminated and at least one exited with a non-zero code. |
| **Unknown** | Pod state can't be determined, typically because the node lost contact with the control plane. |

```bash
# Check pod phase
kubectl get pod api-gateway-xxx -n deployforge -o jsonpath='{.status.phase}'

# Get detailed pod conditions
kubectl get pod api-gateway-xxx -n deployforge -o jsonpath='{range .status.conditions[*]}{.type}={.status}{"\n"}{end}'
# → PodScheduled=True
# → Initialized=True
# → ContainersReady=True
# → Ready=True
```

---

### Container States

Within a running pod, each container has its own state:

| State | Description |
|-------|-------------|
| **Waiting** | Container is not yet running (pulling image, waiting for init containers) |
| **Running** | Container is executing |
| **Terminated** | Container finished execution (either completed or errored) |

```bash
# Inspect container states
kubectl get pod api-gateway-xxx -n deployforge -o jsonpath='{range .status.containerStatuses[*]}{.name}: {.state}{"\n"}{end}'

# Common Waiting reasons:
# - ContainerCreating (normal — pulling image)
# - CrashLoopBackOff (container keeps crashing and kubelet is backing off restarts)
# - ImagePullBackOff (can't pull the image — wrong name, no auth, registry down)
# - ErrImagePull (first attempt to pull failed)
```

> **Key insight:** `CrashLoopBackOff` is not a state — it's a _reason_ for the `Waiting` state. It means the container has crashed multiple times and the kubelet is applying exponential backoff before the next restart: 10s, 20s, 40s, 80s, 160s, capped at 5 minutes.

---

### Init Container Ordering

Init containers execute sequentially. Each must complete successfully before the next starts. If any init container fails, the entire pod is restarted (according to `restartPolicy`).

```
┌──────────────────────────────────────────────────────────────┐
│  Pod Startup Sequence                                         │
│                                                               │
│  1. Init Container: wait-for-db                               │
│     ┌─────────────────────────────────────────┐               │
│     │ nc -z postgres 5432 → success (exit 0)  │               │
│     └─────────────────────────┬───────────────┘               │
│                               │ complete                      │
│                               ▼                               │
│  2. Init Container: run-migrations                            │
│     ┌─────────────────────────────────────────┐               │
│     │ npx prisma migrate deploy → exit 0      │               │
│     └─────────────────────────┬───────────────┘               │
│                               │ complete                      │
│                               ▼                               │
│  3. Native Sidecar (restartPolicy: Always):                   │
│     ┌─────────────────────────────────────────┐               │
│     │ fluent-bit (starts, keeps running)       │               │
│     └─────────────────────────┬───────────────┘               │
│                               │ started                       │
│                               ▼                               │
│  4. App Container: api-gateway                                │
│     ┌─────────────────────────────────────────┐               │
│     │ node server.js (starts, keeps running)   │               │
│     └─────────────────────────────────────────┘               │
│                                                               │
│  Pod condition: Initialized=True (all init containers done)   │
└──────────────────────────────────────────────────────────────┘
```

```yaml
spec:
  initContainers:
  # Step 1: Wait for database
  - name: wait-for-db
    image: busybox:1.36
    command: ['sh', '-c', 'until nc -z postgres-0.postgres.deployforge.svc.cluster.local 5432; do echo "waiting for db..."; sleep 2; done']

  # Step 2: Run migrations
  - name: run-migrations
    image: deployforge/api-gateway:1.2.0
    command: ['npx', 'prisma', 'migrate', 'deploy']
    env:
    - name: DATABASE_URL
      valueFrom:
        secretKeyRef:
          name: postgres-credentials
          key: url

  # Step 3: Native sidecar (K8s 1.28+)
  - name: log-shipper
    image: fluent/fluent-bit:2.2
    restartPolicy: Always

  containers:
  # Step 4: Main application
  - name: api
    image: deployforge/api-gateway:1.2.0
```

---

### Probes

Probes are the mechanism by which the kubelet monitors container health. There are three types, each serving a distinct purpose:

```
┌──────────────────────────────────────────────────────────────────┐
│                        Probe Timeline                             │
│                                                                   │
│  Container starts                                                 │
│       │                                                           │
│       │  initialDelaySeconds                                      │
│       │◄──────────────────────▶│                                  │
│       │                        │                                  │
│       │  ┌─────────────────────┤                                  │
│       │  │  Startup Probe      │  "Is the app finished starting?" │
│       │  │  (disables liveness │  Protects slow-starting apps.    │
│       │  │   and readiness     │  Runs until success or           │
│       │  │   until success)    │  failureThreshold reached.       │
│       │  └────────┬────────────┘                                  │
│       │           │ success                                       │
│       │           ▼                                               │
│       │  ┌────────────────────┐  ┌────────────────────────┐      │
│       │  │  Liveness Probe    │  │  Readiness Probe        │      │
│       │  │                    │  │                          │      │
│       │  │  "Is the app       │  │  "Can the app handle    │      │
│       │  │   still alive?"    │  │   traffic right now?"   │      │
│       │  │                    │  │                          │      │
│       │  │  Failure → kubelet │  │  Failure → pod removed  │      │
│       │  │  kills container   │  │  from Service endpoints │      │
│       │  │  and restarts it   │  │  (stops receiving       │      │
│       │  │                    │  │   traffic)              │      │
│       │  └────────────────────┘  └────────────────────────┘      │
│       │                                                           │
│       │  Both run periodically for the container's lifetime       │
└──────────────────────────────────────────────────────────────────┘
```

| Probe | Purpose | On Failure |
|-------|---------|------------|
| **Startup** | Detect when a slow-starting app is ready. Disables liveness/readiness until it succeeds. | Container is killed and restarted |
| **Liveness** | Detect deadlocked or hung processes that are still running but can't serve requests. | Container is killed and restarted |
| **Readiness** | Detect when a container can't serve traffic (e.g., loading cache, database connection lost). | Pod removed from Service endpoints (no traffic) |

#### Probe Mechanisms

| Type | How It Works | Use Case |
|------|-------------|----------|
| **HTTP GET** | Sends HTTP request; 200-399 = success | Web servers, REST APIs |
| **TCP Socket** | Opens TCP connection; connection established = success | Databases, Redis, any TCP service |
| **Exec** | Runs a command inside the container; exit 0 = success | Custom health checks, file existence |
| **gRPC** | Calls gRPC health check; SERVING = success | gRPC services (K8s 1.27+) |

#### Probe Configuration

```yaml
spec:
  containers:
  - name: api
    image: deployforge/api-gateway:1.2.0
    ports:
    - containerPort: 3000

    # Startup probe — protects during initial startup
    startupProbe:
      httpGet:
        path: /health
        port: 3000
      initialDelaySeconds: 5
      periodSeconds: 5
      failureThreshold: 30          # 30 × 5s = 150s max startup time
      # No successThreshold needed (always 1 for startup/liveness)

    # Liveness probe — detects hung processes
    livenessProbe:
      httpGet:
        path: /health
        port: 3000
      periodSeconds: 10             # Check every 10 seconds
      failureThreshold: 3           # 3 consecutive failures → restart
      timeoutSeconds: 5             # Each check times out after 5s

    # Readiness probe — controls traffic routing
    readinessProbe:
      httpGet:
        path: /ready
        port: 3000
      periodSeconds: 5
      failureThreshold: 3           # 3 failures → remove from endpoints
      successThreshold: 2           # 2 successes → add back to endpoints
      timeoutSeconds: 3
```

#### Probe Best Practices

| Practice | Why |
|----------|-----|
| Always use a startup probe for apps that take >10s to start | Prevents liveness probe from killing the container during startup |
| Separate `/health` and `/ready` endpoints | Liveness checks app process health; readiness checks dependencies |
| Don't check dependencies in liveness probes | If the database is down, restarting the app won't fix it — you'll get a restart storm |
| DO check dependencies in readiness probes | Removes pods from endpoints when they can't serve requests |
| Set `timeoutSeconds` higher than your slowest health check response | Prevents false positives under load |
| Use `successThreshold: 2` for readiness | Prevents flapping — pod must pass 2 checks before receiving traffic again |

> **Key insight:** The most common probe mistake is putting dependency checks (database, Redis) in the liveness probe. When the database goes down, all pods restart simultaneously, creating a thundering herd when the database recovers. Put dependency checks in the readiness probe instead — pods stop receiving traffic but stay alive and ready to resume when the dependency returns.

---

### Graceful Shutdown

When Kubernetes terminates a pod (scaling down, rolling update, node drain), it follows a specific sequence:

```
┌──────────────────────────────────────────────────────────────────┐
│                    Graceful Shutdown Sequence                      │
│                                                                   │
│  1. Pod is marked for deletion                                    │
│     • Pod removed from Service endpoints (no new traffic)         │
│     • Pod status set to Terminating                               │
│                                                                   │
│  2. preStop hook executes (if defined)                            │
│     ┌─────────────────────────────────────┐                       │
│     │ e.g., sleep 5 — wait for in-flight  │                       │
│     │ requests to drain from load balancer│                       │
│     └───────────────────────┬─────────────┘                       │
│                             │ hook completes (or times out)       │
│                             ▼                                     │
│  3. SIGTERM sent to PID 1 in the container                        │
│     ┌─────────────────────────────────────┐                       │
│     │ App receives SIGTERM and begins      │                       │
│     │ graceful shutdown:                   │                       │
│     │ • Stop accepting new connections     │                       │
│     │ • Finish in-flight requests          │                       │
│     │ • Close database connections         │                       │
│     │ • Flush buffers                      │                       │
│     └───────────────────────┬─────────────┘                       │
│                             │                                     │
│  4. terminationGracePeriodSeconds countdown                       │
│     (default: 30 seconds — covers BOTH preStop + SIGTERM)         │
│                             │                                     │
│     If app exits before timeout → done                            │
│     If timeout expires → SIGKILL (forced kill, no cleanup)        │
│                             │                                     │
│  5. Pod resources released                                        │
└──────────────────────────────────────────────────────────────────┘
```

> **Key insight:** `terminationGracePeriodSeconds` is the TOTAL budget for both the preStop hook AND the SIGTERM handling. If your preStop hook takes 10 seconds and your app needs 20 seconds to drain, set `terminationGracePeriodSeconds: 35` (with some buffer).

#### preStop Hook

```yaml
spec:
  terminationGracePeriodSeconds: 45
  containers:
  - name: api
    image: deployforge/api-gateway:1.2.0
    lifecycle:
      preStop:
        exec:
          command:
          - sh
          - -c
          - |
            # Wait for load balancer to stop sending traffic
            # (endpoint removal is async — there's a small window)
            sleep 5

            # Signal the app to start draining
            # (or just let SIGTERM handle it)
```

#### SIGTERM Handling in Node.js (DeployForge API Gateway)

```typescript
// server.ts — Express app with graceful shutdown
const server = app.listen(3000, () => {
  console.log('API Gateway listening on :3000');
});

// Handle SIGTERM from Kubernetes
process.on('SIGTERM', () => {
  console.log('SIGTERM received. Starting graceful shutdown...');

  // Stop accepting new connections
  server.close(() => {
    console.log('HTTP server closed. Cleaning up...');

    // Close database pool
    prisma.$disconnect().then(() => {
      // Close Redis connection
      redis.disconnect().then(() => {
        console.log('All connections closed. Exiting.');
        process.exit(0);
      });
    });
  });

  // Force exit if cleanup takes too long
  setTimeout(() => {
    console.error('Forced shutdown after timeout');
    process.exit(1);
  }, 25000); // 25s — leave room within terminationGracePeriodSeconds
});
```

---

### PodDisruptionBudgets

A PodDisruptionBudget (PDB) limits how many pods from a set can be voluntarily disrupted at the same time. This protects your service during:

- Node drains (`kubectl drain`)
- Cluster autoscaler scale-downs
- Node OS upgrades
- Voluntary evictions

PDBs do NOT protect against involuntary disruptions (node crash, OOM kill, hardware failure).

```yaml
apiVersion: policy/v1
kind: PodDisruptionBudget
metadata:
  name: api-gateway-pdb
  namespace: deployforge
spec:
  minAvailable: 2                    # At least 2 pods must stay running
  selector:
    matchLabels:
      app: api-gateway
---
apiVersion: policy/v1
kind: PodDisruptionBudget
metadata:
  name: postgres-pdb
  namespace: deployforge
spec:
  maxUnavailable: 0                  # Never voluntarily disrupt the database
  selector:
    matchLabels:
      app: postgres
```

| Field | Meaning | Example |
|-------|---------|---------|
| `minAvailable` | Minimum pods that must remain running | `2` or `"50%"` |
| `maxUnavailable` | Maximum pods that can be disrupted | `1` or `"25%"` |

You can specify either `minAvailable` or `maxUnavailable`, not both.

```
┌──────────────────────────────────────────────────────────────────┐
│  PDB: api-gateway-pdb (minAvailable: 2)                          │
│                                                                   │
│  Current state: 3 pods running                                    │
│                                                                   │
│  kubectl drain node-1:                                            │
│                                                                   │
│  Node-1 has 1 api-gateway pod                                     │
│  → Can evict: 3 running - 1 evicted = 2 remaining ≥ 2 min ✓     │
│  → Pod evicted, rescheduled to another node                       │
│                                                                   │
│  If we then try to drain node-2 (has 1 api-gateway pod):         │
│  → Can't evict: 2 running - 1 evicted = 1 remaining < 2 min ✗   │
│  → kubectl drain waits until a new pod is running                │
│                                                                   │
│  PDB: postgres-pdb (maxUnavailable: 0)                            │
│  → No voluntary disruption allowed — kubectl drain blocks         │
│    until you manually intervene or remove the PDB                 │
└──────────────────────────────────────────────────────────────────┘
```

> **Production note:** Setting `maxUnavailable: 0` on a single-replica StatefulSet means `kubectl drain` will block indefinitely. This is intentional for databases — it forces the operator to handle the drain manually (e.g., failover to a replica first). For services with multiple replicas, use `minAvailable: N-1` or `maxUnavailable: 1`.

```bash
# Check PDB status
kubectl get pdb -n deployforge
# NAME              MIN AVAILABLE   MAX UNAVAILABLE   ALLOWED DISRUPTIONS   AGE
# api-gateway-pdb   2               N/A               1                     5m
# postgres-pdb      N/A             0                 0                     5m

# "ALLOWED DISRUPTIONS" shows how many pods can currently be evicted
```

---

## Code Examples

### Example 1: Complete Probe Configuration for API Gateway

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
      terminationGracePeriodSeconds: 45
      containers:
      - name: api
        image: nginx:alpine
        ports:
        - containerPort: 80

        startupProbe:
          httpGet:
            path: /
            port: 80
          initialDelaySeconds: 2
          periodSeconds: 3
          failureThreshold: 20       # 20 × 3s = 60s max startup

        livenessProbe:
          httpGet:
            path: /
            port: 80
          periodSeconds: 10
          failureThreshold: 3
          timeoutSeconds: 5

        readinessProbe:
          httpGet:
            path: /
            port: 80
          periodSeconds: 5
          failureThreshold: 3
          successThreshold: 2
          timeoutSeconds: 3

        lifecycle:
          preStop:
            exec:
              command: ['sh', '-c', 'sleep 5']

        resources:
          requests:
            cpu: "100m"
            memory: "128Mi"
          limits:
            cpu: "200m"
            memory: "256Mi"
```

### Example 2: TCP Probe for PostgreSQL

```yaml
# PostgreSQL doesn't serve HTTP, so use a TCP socket probe
spec:
  containers:
  - name: postgres
    image: postgres:15-alpine
    ports:
    - containerPort: 5432

    startupProbe:
      tcpSocket:
        port: 5432
      initialDelaySeconds: 5
      periodSeconds: 5
      failureThreshold: 30          # 30 × 5s = 150s for WAL recovery

    livenessProbe:
      exec:
        command:
        - pg_isready
        - -U
        - deployforge
      periodSeconds: 10
      failureThreshold: 3
      timeoutSeconds: 5

    readinessProbe:
      exec:
        command:
        - pg_isready
        - -U
        - deployforge
      periodSeconds: 5
      failureThreshold: 3
      timeoutSeconds: 3
```

### Example 3: Watch Pod Lifecycle Events

```bash
# Watch events for a specific pod
kubectl describe pod api-gateway-xxx -n deployforge | tail -20
# Events:
#   Type    Reason     Age   From               Message
#   ----    ------     ----  ----               -------
#   Normal  Scheduled  60s   default-scheduler  Successfully assigned deployforge/api-gateway-xxx to worker-1
#   Normal  Pulling    59s   kubelet            Pulling image "nginx:alpine"
#   Normal  Pulled     57s   kubelet            Successfully pulled image
#   Normal  Created    57s   kubelet            Created container api
#   Normal  Started    56s   kubelet            Started container api

# Watch all events in the namespace
kubectl get events -n deployforge --sort-by=.lastTimestamp --watch

# Filter for probe-related events
kubectl get events -n deployforge --field-selector reason=Unhealthy
```

### Example 4: Simulate a Graceful Shutdown

```bash
# Delete a pod and watch the shutdown sequence
kubectl delete pod api-gateway-xxx -n deployforge &

# In another terminal, watch events
kubectl get events -n deployforge --watch --field-selector involvedObject.name=api-gateway-xxx
# → Killing: Stopping container api (after preStop + SIGTERM)

# Check that the Deployment immediately creates a replacement
kubectl get pods -n deployforge -l app=api-gateway -w
```

---

## Try It Yourself

### Challenge 1: Configure Production-Ready Probes

Deploy the DeployForge API Gateway with:
- Startup probe: HTTP GET `/`, fails after 60s
- Liveness probe: HTTP GET `/`, every 10s, 3 failures to kill
- Readiness probe: HTTP GET `/`, every 5s, needs 2 successes to be ready
- preStop hook: sleep 5 seconds
- terminationGracePeriodSeconds: 30
- Verify probes are working by checking pod conditions

<details>
<summary>Show solution</summary>

```bash
#!/bin/bash
set -euo pipefail

NS="deployforge"

echo "=== Deploying API Gateway with probes ==="
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
      terminationGracePeriodSeconds: 30
      containers:
      - name: api
        image: nginx:alpine
        ports:
        - containerPort: 80
        startupProbe:
          httpGet:
            path: /
            port: 80
          initialDelaySeconds: 2
          periodSeconds: 3
          failureThreshold: 20
        livenessProbe:
          httpGet:
            path: /
            port: 80
          periodSeconds: 10
          failureThreshold: 3
          timeoutSeconds: 5
        readinessProbe:
          httpGet:
            path: /
            port: 80
          periodSeconds: 5
          failureThreshold: 3
          successThreshold: 2
          timeoutSeconds: 3
        lifecycle:
          preStop:
            exec:
              command: ['sh', '-c', 'sleep 5']
        resources:
          requests:
            cpu: "50m"
            memory: "64Mi"
          limits:
            cpu: "100m"
            memory: "128Mi"
EOF

echo ""
echo "=== Waiting for rollout ==="
kubectl rollout status deployment/api-gateway -n $NS --timeout=120s

echo ""
echo "=== Pod conditions ==="
kubectl get pods -n $NS -l app=api-gateway -o jsonpath='{range .items[*]}{.metadata.name}:{"\n"}{range .status.conditions[*]}  {.type}={.status}{"\n"}{end}{"\n"}{end}'

echo ""
echo "=== Probe configuration ==="
kubectl get deployment api-gateway -n $NS -o jsonpath='{.spec.template.spec.containers[0].startupProbe}' | python3 -m json.tool 2>/dev/null || echo "(raw output)"

echo ""
echo "=== Events ==="
kubectl get events -n $NS --sort-by=.lastTimestamp | tail -10

echo ""
echo "✅ API Gateway deployed with production-ready probes"
```

</details>

### Challenge 2: PodDisruptionBudgets

Create PDBs for the DeployForge services:
1. API Gateway: `minAvailable: 1` (with 2 replicas, allows 1 disruption)
2. PostgreSQL: `maxUnavailable: 0` (never voluntarily disrupt)
3. Verify the PDBs and check allowed disruptions
4. Test by simulating a node drain

<details>
<summary>Show solution</summary>

```bash
#!/bin/bash
set -euo pipefail

NS="deployforge"

echo "=== Creating PodDisruptionBudgets ==="

cat <<'EOF' | kubectl apply -f -
apiVersion: policy/v1
kind: PodDisruptionBudget
metadata:
  name: api-gateway-pdb
  namespace: deployforge
spec:
  minAvailable: 1
  selector:
    matchLabels:
      app: api-gateway
---
apiVersion: policy/v1
kind: PodDisruptionBudget
metadata:
  name: postgres-pdb
  namespace: deployforge
spec:
  maxUnavailable: 0
  selector:
    matchLabels:
      app: postgres
EOF

echo ""
echo "=== PDB Status ==="
kubectl get pdb -n $NS
# NAME              MIN AVAILABLE   MAX UNAVAILABLE   ALLOWED DISRUPTIONS
# api-gateway-pdb   1               N/A               1
# postgres-pdb      N/A             0                 0

echo ""
echo "=== API Gateway PDB details ==="
kubectl describe pdb api-gateway-pdb -n $NS

echo ""
echo "=== PostgreSQL PDB details ==="
kubectl describe pdb postgres-pdb -n $NS

echo ""
echo "=== Test: Simulate node drain (dry-run) ==="
# In a real cluster, you'd drain a node:
# kubectl drain <node-name> --ignore-daemonsets --delete-emptydir-data
# The drain will respect PDBs — it won't evict more pods than allowed

echo "To test in your kind cluster:"
echo "  kubectl drain deployforge-worker --ignore-daemonsets --delete-emptydir-data --dry-run=client"

echo ""
echo "✅ PDBs configured for DeployForge services"
```

</details>

### Challenge 3: Observe CrashLoopBackOff

Deploy a deliberately broken container and observe the pod lifecycle:
1. Create a Deployment with a command that exits with error code 1
2. Watch the pod go through Running → Error → CrashLoopBackOff
3. Observe the exponential backoff timing
4. Fix the issue and verify recovery

<details>
<summary>Show solution</summary>

```bash
#!/bin/bash
set -euo pipefail

NS="deployforge"

echo "=== Deploying broken container ==="
cat <<'EOF' | kubectl apply -f -
apiVersion: apps/v1
kind: Deployment
metadata:
  name: broken-app
  namespace: deployforge
spec:
  replicas: 1
  selector:
    matchLabels:
      app: broken-app
  template:
    metadata:
      labels:
        app: broken-app
    spec:
      containers:
      - name: app
        image: busybox:1.36
        command: ['sh', '-c', 'echo "Starting..."; exit 1']
        resources:
          requests:
            cpu: "10m"
            memory: "16Mi"
EOF

echo ""
echo "=== Watching pod status (observe CrashLoopBackOff) ==="
echo "Watch for 30 seconds..."
kubectl get pods -n $NS -l app=broken-app -w &
WATCH_PID=$!
sleep 30
kill $WATCH_PID 2>/dev/null
wait $WATCH_PID 2>/dev/null

echo ""
echo "=== Pod events showing restart backoff ==="
POD_NAME=$(kubectl get pods -n $NS -l app=broken-app -o jsonpath='{.items[0].metadata.name}')
kubectl describe pod $POD_NAME -n $NS | grep -A 20 "Events:"

echo ""
echo "=== Restart count ==="
kubectl get pod $POD_NAME -n $NS -o jsonpath='Restarts: {.status.containerStatuses[0].restartCount}'
echo ""

echo ""
echo "=== Fix: Update to a working command ==="
kubectl set image deployment/broken-app app=nginx:alpine -n $NS
kubectl rollout status deployment/broken-app -n $NS --timeout=60s

echo ""
echo "=== Recovered ==="
kubectl get pods -n $NS -l app=broken-app

echo ""
echo "=== Cleanup ==="
kubectl delete deployment broken-app -n $NS
```

</details>

---

## Capstone Connection

**DeployForge** relies on pod lifecycle management, probes, and disruption budgets for production reliability:

- **Init container ordering** — The API Gateway pod starts with `wait-for-db` (TCP check on PostgreSQL) followed by `run-migrations` (Prisma migrate). This ensures the database schema is current before the app starts accepting traffic. In Module 10 (CI/CD & GitOps), you'll run migrations as a separate Job instead, to avoid blocking pod startup.
- **Startup probes** — Both the API Gateway and PostgreSQL use startup probes. The gateway needs time to compile TypeScript and load middleware; PostgreSQL needs time for WAL recovery after a crash. Without startup probes, the liveness probe would kill these containers during normal startup.
- **Liveness vs readiness** — The API Gateway's liveness probe checks `/health` (is the Node.js event loop responding?). The readiness probe checks `/ready` (are database and Redis connections healthy?). When Redis goes down, the gateway stops receiving traffic (readiness fails) but isn't restarted (liveness still passes) — it recovers automatically when Redis returns.
- **Graceful shutdown** — The API Gateway has a 5-second preStop hook to allow kube-proxy/ingress to update endpoints, then handles SIGTERM by closing the HTTP server and draining connections. `terminationGracePeriodSeconds: 45` gives ample time for the preStop (5s) + SIGTERM handling (up to 35s) + buffer.
- **PodDisruptionBudgets** — The API Gateway PDB (`minAvailable: 2` with 3 replicas) allows rolling node upgrades one at a time. The PostgreSQL PDB (`maxUnavailable: 0`) forces manual intervention before disrupting the database — in Module 09 (Reliability), you'll implement automatic failover with a PostgreSQL operator.
