# Module 05 — Exercises

Hands-on practice with Kubernetes workload types, scheduling constraints, health probes, and disruption budgets. These exercises deploy the core DeployForge services as production-ready workloads on your kind cluster.

> **⚠️ Prerequisite:** You need a running kind cluster with the `deployforge` namespace from Module 04. If you don't have one, run:
> ```bash
> cat <<EOF | kind create cluster --config=-
> kind: Cluster
> apiVersion: kind.x-k8s.io/v1alpha4
> name: deployforge
> nodes:
> - role: control-plane
> - role: worker
> - role: worker
> EOF
> kubectl create namespace deployforge
> kubectl config set-context --current --namespace=deployforge
> ```

---

## Exercise 1: Deploy DeployForge Services as Proper Workload Types

**Goal:** Deploy the five core DeployForge services using the correct workload type for each: Deployment (stateless), StatefulSet (stateful), and DaemonSet (per-node).

### Steps

1. **Deploy the API Gateway as a Deployment:**

```yaml
# api-gateway.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: api-gateway
  namespace: deployforge
  labels:
    app: api-gateway
    tier: frontend
spec:
  replicas: 2
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
      - name: api
        image: nginx:1.25-alpine
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

2. **Deploy the Worker as a Deployment:**

Create a Worker Deployment with 2 replicas, using `busybox` to simulate a queue worker. Set resource requests to 50m CPU, 64Mi memory.

3. **Deploy PostgreSQL as a StatefulSet:**

Create a headless Service and StatefulSet for PostgreSQL with 1 replica, a `volumeClaimTemplate` (1Gi), and Guaranteed QoS.

4. **Deploy Redis as a StatefulSet:**

Same pattern as PostgreSQL — headless Service, StatefulSet, persistent storage, Guaranteed QoS.

5. **Deploy Nginx as a DaemonSet:**

Create a DaemonSet that runs Nginx on every worker node.

6. **Verify all services:**

```bash
# Check all workloads
kubectl get deployments,statefulsets,daemonsets -n deployforge

# Check all pods with node placement
kubectl get pods -n deployforge -o wide

# Check all PVCs
kubectl get pvc -n deployforge
```

### Verification

```bash
# Deployments: api-gateway (2/2), worker (2/2)
kubectl get deployments -n deployforge
# → api-gateway  2/2   2   2
# → worker       2/2   2   2

# StatefulSets: postgres (1/1), redis (1/1)
kubectl get statefulsets -n deployforge
# → postgres  1/1   1
# → redis     1/1   1

# DaemonSet: nginx-proxy (2 — one per worker node)
kubectl get daemonsets -n deployforge
# → nginx-proxy  2   2   2   2

# PVCs: data-postgres-0, data-redis-0
kubectl get pvc -n deployforge
# → data-postgres-0  Bound
# → data-redis-0     Bound
```

<details>
<summary>Show solution</summary>

```bash
#!/bin/bash
set -euo pipefail

NS="deployforge"

echo "=== Step 1: Deploy API Gateway (Deployment) ==="
cat <<'EOF' | kubectl apply -f -
apiVersion: apps/v1
kind: Deployment
metadata:
  name: api-gateway
  namespace: deployforge
  labels:
    app: api-gateway
    tier: frontend
spec:
  replicas: 2
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
      - name: api
        image: nginx:1.25-alpine
        ports:
        - containerPort: 80
        resources:
          requests:
            cpu: "100m"
            memory: "128Mi"
          limits:
            cpu: "200m"
            memory: "256Mi"
EOF

echo ""
echo "=== Step 2: Deploy Worker (Deployment) ==="
cat <<'EOF' | kubectl apply -f -
apiVersion: apps/v1
kind: Deployment
metadata:
  name: worker
  namespace: deployforge
  labels:
    app: worker
    tier: backend
spec:
  replicas: 2
  selector:
    matchLabels:
      app: worker
  template:
    metadata:
      labels:
        app: worker
        tier: backend
    spec:
      containers:
      - name: worker
        image: busybox:1.36
        command: ['sh', '-c', 'while true; do echo "[$(date)] Processing jobs..."; sleep 30; done']
        resources:
          requests:
            cpu: "50m"
            memory: "64Mi"
          limits:
            cpu: "100m"
            memory: "128Mi"
EOF

echo ""
echo "=== Step 3: Deploy PostgreSQL (StatefulSet) ==="
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
    targetPort: 5432
    name: postgres
---
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
        tier: data
    spec:
      containers:
      - name: postgres
        image: postgres:15-alpine
        ports:
        - containerPort: 5432
        env:
        - name: POSTGRES_USER
          value: "deployforge"
        - name: POSTGRES_PASSWORD
          value: "password"
        - name: POSTGRES_DB
          value: "deployforge_dev"
        - name: PGDATA
          value: "/var/lib/postgresql/data/pgdata"
        volumeMounts:
        - name: data
          mountPath: /var/lib/postgresql/data
        resources:
          requests:
            cpu: "250m"
            memory: "256Mi"
          limits:
            cpu: "250m"
            memory: "256Mi"
  volumeClaimTemplates:
  - metadata:
      name: data
    spec:
      accessModes: ["ReadWriteOnce"]
      resources:
        requests:
          storage: 1Gi
EOF

echo ""
echo "=== Step 4: Deploy Redis (StatefulSet) ==="
cat <<'EOF' | kubectl apply -f -
apiVersion: v1
kind: Service
metadata:
  name: redis
  namespace: deployforge
spec:
  clusterIP: None
  selector:
    app: redis
  ports:
  - port: 6379
    targetPort: 6379
    name: redis
---
apiVersion: apps/v1
kind: StatefulSet
metadata:
  name: redis
  namespace: deployforge
spec:
  serviceName: redis
  replicas: 1
  selector:
    matchLabels:
      app: redis
  template:
    metadata:
      labels:
        app: redis
        tier: data
    spec:
      containers:
      - name: redis
        image: redis:7-alpine
        ports:
        - containerPort: 6379
        command: ['redis-server', '--appendonly', 'yes', '--dir', '/data']
        volumeMounts:
        - name: data
          mountPath: /data
        resources:
          requests:
            cpu: "100m"
            memory: "128Mi"
          limits:
            cpu: "100m"
            memory: "128Mi"
  volumeClaimTemplates:
  - metadata:
      name: data
    spec:
      accessModes: ["ReadWriteOnce"]
      resources:
        requests:
          storage: 1Gi
EOF

echo ""
echo "=== Step 5: Deploy Nginx Proxy (DaemonSet) ==="
cat <<'EOF' | kubectl apply -f -
apiVersion: apps/v1
kind: DaemonSet
metadata:
  name: nginx-proxy
  namespace: deployforge
  labels:
    app: nginx-proxy
spec:
  selector:
    matchLabels:
      app: nginx-proxy
  updateStrategy:
    type: RollingUpdate
    rollingUpdate:
      maxUnavailable: 1
  template:
    metadata:
      labels:
        app: nginx-proxy
        tier: ingress
    spec:
      containers:
      - name: nginx
        image: nginx:1.25-alpine
        ports:
        - containerPort: 80
        resources:
          requests:
            cpu: "50m"
            memory: "64Mi"
          limits:
            cpu: "100m"
            memory: "128Mi"
EOF

echo ""
echo "=== Waiting for all workloads ==="
kubectl rollout status deployment/api-gateway -n $NS --timeout=60s
kubectl rollout status deployment/worker -n $NS --timeout=60s
kubectl rollout status statefulset/postgres -n $NS --timeout=120s
kubectl rollout status statefulset/redis -n $NS --timeout=60s

echo ""
echo "=== Verification ==="
echo "Deployments:"
kubectl get deployments -n $NS
echo ""
echo "StatefulSets:"
kubectl get statefulsets -n $NS
echo ""
echo "DaemonSets:"
kubectl get daemonsets -n $NS
echo ""
echo "All Pods (with node placement):"
kubectl get pods -n $NS -o wide
echo ""
echo "PersistentVolumeClaims:"
kubectl get pvc -n $NS

echo ""
echo "✅ All DeployForge services deployed!"
```

</details>

---

## Exercise 2: Rolling Updates and Rollback

**Goal:** Practice rolling updates, observe zero-downtime behavior, and perform rollbacks using revision history.

### Steps

1. **Annotate the current deployment with a change cause:**

```bash
kubectl annotate deployment/api-gateway -n deployforge \
  kubernetes.io/change-cause="initial deploy with nginx:1.25-alpine"
```

2. **Update the API Gateway image to `nginx:1.26-alpine`:**

Watch the rollout in real time. Verify that at no point do you have fewer than 2 running pods.

3. **View rollout history:**

```bash
kubectl rollout history deployment/api-gateway -n deployforge
```

4. **Deploy a broken image (`nginx:99.99-doesnotexist`):**

Observe the rollout stall and pods enter `ImagePullBackOff`.

5. **Roll back to the previous working revision.**

6. **Roll back to the original revision (revision 1).**

7. **Verify the final state matches the original deployment.**

### Verification

```bash
# All pods should be running with the original image
kubectl get pods -n deployforge -l app=api-gateway \
  -o jsonpath='{range .items[*]}{.spec.containers[0].image}{"\n"}{end}'
# → nginx:1.25-alpine (all pods)

# Rollout history should show multiple revisions
kubectl rollout history deployment/api-gateway -n deployforge
```

<details>
<summary>Show solution</summary>

```bash
#!/bin/bash
set -euo pipefail

NS="deployforge"

echo "=== Step 1: Annotate current deployment ==="
kubectl annotate deployment/api-gateway -n $NS \
  kubernetes.io/change-cause="initial deploy with nginx:1.25-alpine" --overwrite

echo ""
echo "=== Step 2: Update to nginx:1.26-alpine ==="
kubectl set image deployment/api-gateway api=nginx:1.26-alpine -n $NS
kubectl annotate deployment/api-gateway -n $NS \
  kubernetes.io/change-cause="update to nginx:1.26-alpine" --overwrite

echo "Watching rollout..."
kubectl rollout status deployment/api-gateway -n $NS --timeout=120s

echo ""
echo "Current images:"
kubectl get pods -n $NS -l app=api-gateway \
  -o jsonpath='{range .items[*]}  {.metadata.name}: {.spec.containers[0].image}{"\n"}{end}'

echo ""
echo "=== Step 3: View rollout history ==="
kubectl rollout history deployment/api-gateway -n $NS

echo ""
echo "=== Step 4: Deploy broken image ==="
kubectl set image deployment/api-gateway api=nginx:99.99-doesnotexist -n $NS
kubectl annotate deployment/api-gateway -n $NS \
  kubernetes.io/change-cause="broken: nginx:99.99-doesnotexist" --overwrite

echo "Waiting 15 seconds to observe failure..."
sleep 15

echo ""
echo "Pod status (should see ImagePullBackOff):"
kubectl get pods -n $NS -l app=api-gateway

echo ""
echo "=== Step 5: Roll back to previous version ==="
kubectl rollout undo deployment/api-gateway -n $NS
kubectl rollout status deployment/api-gateway -n $NS --timeout=60s

echo ""
echo "After rollback:"
kubectl get pods -n $NS -l app=api-gateway \
  -o jsonpath='{range .items[*]}  {.metadata.name}: {.spec.containers[0].image}{"\n"}{end}'

echo ""
echo "=== Step 6: Roll back to revision 1 ==="
kubectl rollout undo deployment/api-gateway -n $NS --to-revision=1
kubectl rollout status deployment/api-gateway -n $NS --timeout=60s

echo ""
echo "=== Step 7: Final verification ==="
echo "Final images (should be nginx:1.25-alpine):"
kubectl get pods -n $NS -l app=api-gateway \
  -o jsonpath='{range .items[*]}  {.metadata.name}: {.spec.containers[0].image}{"\n"}{end}'

echo ""
echo "Full rollout history:"
kubectl rollout history deployment/api-gateway -n $NS

echo ""
echo "✅ Rolling update and rollback exercise complete!"
```

</details>

---

## Exercise 3: Scheduling Constraints

**Goal:** Set up node labels, taints, and affinity rules to control where DeployForge pods are placed.

### Steps

1. **Label the worker nodes:**

```bash
kubectl label node deployforge-worker tier=general --overwrite
kubectl label node deployforge-worker2 tier=database --overwrite
```

2. **Taint the database node:**

```bash
kubectl taint nodes deployforge-worker2 dedicated=database:NoSchedule
```

3. **Update the API Gateway Deployment to use `nodeSelector: tier: general`.**

4. **Update the PostgreSQL StatefulSet to tolerate the `dedicated=database` taint and use node affinity targeting `tier=database`.**

5. **Add pod anti-affinity to the API Gateway** so replicas spread across nodes (soft preference, since we may only have one general node).

6. **Verify pod placement:**

```bash
kubectl get pods -n deployforge -o custom-columns=\
NAME:.metadata.name,\
NODE:.spec.nodeName,\
STATUS:.status.phase
```

### Verification

```bash
# API Gateway pods should be on deployforge-worker
# PostgreSQL should be on deployforge-worker2
# Worker pods should be on deployforge-worker (can't schedule on tainted node)
kubectl get pods -n deployforge -o wide | grep -E 'api-gateway|postgres|worker'
```

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
echo "=== Step 2: Taint database node ==="
kubectl taint nodes deployforge-worker2 dedicated=database:NoSchedule --overwrite
echo "Taint applied. Verifying:"
kubectl describe node deployforge-worker2 | grep -A2 Taints

echo ""
echo "=== Step 3: Update API Gateway with nodeSelector + anti-affinity ==="
cat <<'EOF' | kubectl apply -f -
apiVersion: apps/v1
kind: Deployment
metadata:
  name: api-gateway
  namespace: deployforge
  labels:
    app: api-gateway
    tier: frontend
spec:
  replicas: 2
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
      nodeSelector:
        tier: general
      affinity:
        podAntiAffinity:
          preferredDuringSchedulingIgnoredDuringExecution:
          - weight: 100
            podAffinityTerm:
              labelSelector:
                matchExpressions:
                - key: app
                  operator: In
                  values: ["api-gateway"]
              topologyKey: kubernetes.io/hostname
      containers:
      - name: api
        image: nginx:1.25-alpine
        ports:
        - containerPort: 80
        resources:
          requests:
            cpu: "100m"
            memory: "128Mi"
          limits:
            cpu: "200m"
            memory: "256Mi"
EOF
kubectl rollout status deployment/api-gateway -n $NS --timeout=60s

echo ""
echo "=== Step 4: Update PostgreSQL with toleration + node affinity ==="
kubectl delete statefulset postgres -n $NS --cascade=orphan 2>/dev/null || true
kubectl delete pods -l app=postgres -n $NS 2>/dev/null || true
kubectl delete pvc -l app=postgres -n $NS 2>/dev/null || true

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
        tier: data
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
        ports:
        - containerPort: 5432
        env:
        - name: POSTGRES_USER
          value: "deployforge"
        - name: POSTGRES_PASSWORD
          value: "password"
        - name: POSTGRES_DB
          value: "deployforge_dev"
        - name: PGDATA
          value: "/var/lib/postgresql/data/pgdata"
        volumeMounts:
        - name: data
          mountPath: /var/lib/postgresql/data
        resources:
          requests:
            cpu: "250m"
            memory: "256Mi"
          limits:
            cpu: "250m"
            memory: "256Mi"
  volumeClaimTemplates:
  - metadata:
      name: data
    spec:
      accessModes: ["ReadWriteOnce"]
      resources:
        requests:
          storage: 1Gi
EOF
kubectl rollout status statefulset/postgres -n $NS --timeout=120s

echo ""
echo "=== Step 5: Verify placement ==="
echo ""
echo "Pod placement:"
kubectl get pods -n $NS -o custom-columns=\
NAME:.metadata.name,\
NODE:.spec.nodeName,\
STATUS:.status.phase

echo ""
echo "Expected:"
echo "  api-gateway pods → deployforge-worker (tier=general)"
echo "  postgres-0       → deployforge-worker2 (tier=database, toleration)"
echo "  worker pods      → deployforge-worker (can't use tainted node)"

echo ""
echo "=== Cleanup taints (for other exercises) ==="
echo "Run when done: kubectl taint nodes deployforge-worker2 dedicated=database:NoSchedule-"

echo ""
echo "✅ Scheduling constraints exercise complete!"
```

</details>

---

## Exercise 4: Probes, Graceful Shutdown & Disruption Budgets

**Goal:** Configure production-ready health probes, graceful shutdown, and PodDisruptionBudgets for all DeployForge services.

### Steps

1. **Update the API Gateway Deployment with full probe configuration:**
   - Startup probe: HTTP GET `/`, initial delay 2s, period 3s, 20 failures
   - Liveness probe: HTTP GET `/`, period 10s, 3 failures, 5s timeout
   - Readiness probe: HTTP GET `/`, period 5s, 3 failures, 2 successes
   - preStop: sleep 5
   - terminationGracePeriodSeconds: 30

2. **Update the PostgreSQL StatefulSet with probes:**
   - Startup probe: TCP port 5432, initial delay 5s, 30 failures
   - Liveness probe: exec `pg_isready -U deployforge`, period 10s
   - Readiness probe: exec `pg_isready -U deployforge`, period 5s

3. **Create PodDisruptionBudgets:**
   - API Gateway: `minAvailable: 1`
   - PostgreSQL: `maxUnavailable: 0`
   - Redis: `maxUnavailable: 0`

4. **Verify everything:**

```bash
# Check probe configuration
kubectl get deployment api-gateway -n deployforge -o yaml | grep -A 10 "Probe"

# Check PDB status
kubectl get pdb -n deployforge

# Simulate a disruption — delete a pod and watch recovery
kubectl delete pod -l app=api-gateway -n deployforge --wait=false
kubectl get pods -n deployforge -l app=api-gateway -w
```

### Verification

```bash
# All pods should be Running and Ready
kubectl get pods -n deployforge
# → All Running, READY x/x

# PDBs should show allowed disruptions
kubectl get pdb -n deployforge
# → api-gateway-pdb: ALLOWED DISRUPTIONS = 1
# → postgres-pdb:    ALLOWED DISRUPTIONS = 0
# → redis-pdb:       ALLOWED DISRUPTIONS = 0
```

<details>
<summary>Show solution</summary>

```bash
#!/bin/bash
set -euo pipefail

NS="deployforge"

echo "=== Step 1: API Gateway with probes and graceful shutdown ==="
cat <<'EOF' | kubectl apply -f -
apiVersion: apps/v1
kind: Deployment
metadata:
  name: api-gateway
  namespace: deployforge
  labels:
    app: api-gateway
    tier: frontend
spec:
  replicas: 2
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
      terminationGracePeriodSeconds: 30
      containers:
      - name: api
        image: nginx:1.25-alpine
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
            cpu: "100m"
            memory: "128Mi"
          limits:
            cpu: "200m"
            memory: "256Mi"
EOF
kubectl rollout status deployment/api-gateway -n $NS --timeout=120s

echo ""
echo "=== Step 2: PostgreSQL with probes ==="
# Remove taint if it exists from previous exercise
kubectl taint nodes deployforge-worker2 dedicated=database:NoSchedule- 2>/dev/null || true

kubectl delete statefulset postgres -n $NS --cascade=orphan 2>/dev/null || true
kubectl delete pods -l app=postgres -n $NS 2>/dev/null || true
kubectl delete pvc -l app=postgres -n $NS 2>/dev/null || true

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
        tier: data
    spec:
      terminationGracePeriodSeconds: 60
      containers:
      - name: postgres
        image: postgres:15-alpine
        ports:
        - containerPort: 5432
        env:
        - name: POSTGRES_USER
          value: "deployforge"
        - name: POSTGRES_PASSWORD
          value: "password"
        - name: POSTGRES_DB
          value: "deployforge_dev"
        - name: PGDATA
          value: "/var/lib/postgresql/data/pgdata"
        startupProbe:
          tcpSocket:
            port: 5432
          initialDelaySeconds: 5
          periodSeconds: 5
          failureThreshold: 30
        livenessProbe:
          exec:
            command: ['pg_isready', '-U', 'deployforge']
          periodSeconds: 10
          failureThreshold: 3
          timeoutSeconds: 5
        readinessProbe:
          exec:
            command: ['pg_isready', '-U', 'deployforge']
          periodSeconds: 5
          failureThreshold: 3
          timeoutSeconds: 3
        volumeMounts:
        - name: data
          mountPath: /var/lib/postgresql/data
        resources:
          requests:
            cpu: "250m"
            memory: "256Mi"
          limits:
            cpu: "250m"
            memory: "256Mi"
  volumeClaimTemplates:
  - metadata:
      name: data
    spec:
      accessModes: ["ReadWriteOnce"]
      resources:
        requests:
          storage: 1Gi
EOF
kubectl rollout status statefulset/postgres -n $NS --timeout=120s

echo ""
echo "=== Step 3: Create PodDisruptionBudgets ==="
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
---
apiVersion: policy/v1
kind: PodDisruptionBudget
metadata:
  name: redis-pdb
  namespace: deployforge
spec:
  maxUnavailable: 0
  selector:
    matchLabels:
      app: redis
EOF

echo ""
echo "=== Step 4: Verification ==="
echo ""
echo "All pods:"
kubectl get pods -n $NS
echo ""
echo "Pod conditions (API Gateway):"
kubectl get pods -n $NS -l app=api-gateway \
  -o jsonpath='{range .items[*]}{.metadata.name}:{"\n"}{range .status.conditions[*]}  {.type}={.status}{"\n"}{end}{"\n"}{end}'
echo ""
echo "PodDisruptionBudgets:"
kubectl get pdb -n $NS

echo ""
echo "=== Test: Delete an API Gateway pod and watch recovery ==="
FIRST_POD=$(kubectl get pods -n $NS -l app=api-gateway -o jsonpath='{.items[0].metadata.name}')
echo "Deleting $FIRST_POD..."
kubectl delete pod $FIRST_POD -n $NS --wait=false

echo "Watching recovery (10 seconds)..."
kubectl get pods -n $NS -l app=api-gateway -w &
WATCH_PID=$!
sleep 10
kill $WATCH_PID 2>/dev/null
wait $WATCH_PID 2>/dev/null

echo ""
echo "Final state:"
kubectl get pods -n $NS -l app=api-gateway
echo ""
echo "PDB status after disruption:"
kubectl get pdb api-gateway-pdb -n $NS

echo ""
echo "✅ Probes, graceful shutdown, and PDB exercise complete!"
```

</details>

---

## Capstone Checkpoint

Before moving to [Module 06 — Networking & Services](../06-networking-and-services/), make sure you can answer these questions:

### Pods & Deployments

1. What is the relationship between Pods, ReplicaSets, and Deployments?
2. What's the difference between `maxSurge: 1, maxUnavailable: 0` and `maxSurge: 0, maxUnavailable: 1`?
3. How do you roll back a Deployment to a specific revision?
4. What are the three QoS classes and when is each assigned?
5. When would you use the Recreate strategy instead of RollingUpdate?

### StatefulSets, DaemonSets & Jobs

6. What three guarantees does a StatefulSet provide that a Deployment doesn't?
7. Why does a StatefulSet require a headless Service?
8. What happens to a StatefulSet's PVC when the pod is deleted?
9. When would you use a DaemonSet instead of a Deployment with node affinity?
10. What's the difference between `restartPolicy: Never` and `OnFailure` in a Job?
11. Why should you use `concurrencyPolicy: Forbid` for database backup CronJobs?

### Scheduling

12. Describe the two phases of the Kubernetes scheduler algorithm.
13. What's the difference between `requiredDuringSchedulingIgnoredDuringExecution` and `preferredDuringSchedulingIgnoredDuringExecution`?
14. How do taints and tolerations differ from node affinity?
15. When would you need both a taint AND node affinity for the same node?
16. What does `topologySpreadConstraints` with `maxSkew: 1` ensure?

### Pod Lifecycle & Probes

17. What's the difference between a startup probe and a liveness probe?
18. Why should you NOT check database connectivity in a liveness probe?
19. What is the order of events during a pod graceful shutdown?
20. How does `terminationGracePeriodSeconds` relate to the preStop hook?
21. What does a PodDisruptionBudget with `maxUnavailable: 0` do during a `kubectl drain`?
22. Describe the complete lifecycle of a DeployForge API Gateway pod from creation to termination.
