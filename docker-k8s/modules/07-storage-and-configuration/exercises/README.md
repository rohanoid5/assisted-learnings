# Module 07 — Exercises

Hands-on practice with PersistentVolumes, ConfigMaps, Secrets, Helm charts, and Kustomize overlays. These exercises build the complete storage and configuration layer for DeployForge — from database persistence through multi-environment templating.

> **⚠️ Prerequisite:** You need a running kind cluster with the `deployforge` namespace and workloads from Module 06. If you don't have one, run:
> ```bash
> cat <<EOF | kind create cluster --config=-
> kind: Cluster
> apiVersion: kind.x-k8s.io/v1alpha4
> name: deployforge
> nodes:
> - role: control-plane
>   kubeadmConfigPatches:
>   - |
>     kind: InitConfiguration
>     nodeRegistration:
>       kubeletExtraArgs:
>         node-labels: "ingress-ready=true"
>   extraPortMappings:
>   - containerPort: 80
>     hostPort: 80
>     protocol: TCP
>   - containerPort: 443
>     hostPort: 443
>     protocol: TCP
> - role: worker
> - role: worker
> EOF
> kubectl create namespace deployforge
> kubectl config set-context --current --namespace=deployforge
> ```
>
> You'll also need Helm installed:
> ```bash
> brew install helm
> ```

---

## Exercise 1: Set Up Persistent Storage for PostgreSQL and Redis

**Goal:** Configure PersistentVolumeClaims and a custom StorageClass for DeployForge's stateful components. Verify data persists across pod restarts and understand the PV lifecycle.

### Steps

1. **Create a custom StorageClass:**

```yaml
# deployforge-storageclass.yaml
apiVersion: storage.k8s.io/v1
kind: StorageClass
metadata:
  name: deployforge-fast
provisioner: rancher.io/local-path
reclaimPolicy: Retain
volumeBindingMode: WaitForFirstConsumer
```

2. **Deploy PostgreSQL as a StatefulSet with a volumeClaimTemplate:**

```yaml
# postgres-statefulset.yaml
apiVersion: v1
kind: Service
metadata:
  name: postgres-headless
  namespace: deployforge
spec:
  clusterIP: None
  selector:
    app: postgres
  ports:
  - port: 5432
    targetPort: 5432
---
apiVersion: apps/v1
kind: StatefulSet
metadata:
  name: postgres
  namespace: deployforge
spec:
  serviceName: postgres-headless
  replicas: 1
  selector:
    matchLabels:
      app: postgres
  template:
    metadata:
      labels:
        app: postgres
    spec:
      containers:
      - name: postgres
        image: postgres:15-alpine
        ports:
        - containerPort: 5432
        env:
        - name: POSTGRES_DB
          value: "deployforge"
        - name: POSTGRES_USER
          value: "deployforge_app"
        - name: POSTGRES_PASSWORD
          valueFrom:
            secretKeyRef:
              name: deployforge-db-credentials
              key: DB_PASSWORD
        - name: PGDATA
          value: /var/lib/postgresql/data/pgdata
        volumeMounts:
        - name: postgres-data
          mountPath: /var/lib/postgresql/data
        readinessProbe:
          exec:
            command: ["pg_isready", "-U", "deployforge_app", "-d", "deployforge"]
          initialDelaySeconds: 5
          periodSeconds: 10
  volumeClaimTemplates:
  - metadata:
      name: postgres-data
    spec:
      accessModes: ["ReadWriteOnce"]
      storageClassName: deployforge-fast
      resources:
        requests:
          storage: 10Gi
```

3. **Create the Secret for the database password:**

```bash
kubectl create secret generic deployforge-db-credentials \
  --from-literal=DB_PASSWORD='deployforge-local-dev' \
  -n deployforge
```

4. **Deploy Redis with a volumeClaimTemplate:**

```yaml
# redis-statefulset.yaml
apiVersion: v1
kind: Service
metadata:
  name: redis-headless
  namespace: deployforge
spec:
  clusterIP: None
  selector:
    app: redis
  ports:
  - port: 6379
    targetPort: 6379
---
apiVersion: apps/v1
kind: StatefulSet
metadata:
  name: redis
  namespace: deployforge
spec:
  serviceName: redis-headless
  replicas: 1
  selector:
    matchLabels:
      app: redis
  template:
    metadata:
      labels:
        app: redis
    spec:
      containers:
      - name: redis
        image: redis:7-alpine
        ports:
        - containerPort: 6379
        command: ["redis-server", "--appendonly", "yes", "--dir", "/data"]
        volumeMounts:
        - name: redis-data
          mountPath: /data
        readinessProbe:
          exec:
            command: ["redis-cli", "ping"]
          initialDelaySeconds: 5
          periodSeconds: 10
  volumeClaimTemplates:
  - metadata:
      name: redis-data
    spec:
      accessModes: ["ReadWriteOnce"]
      storageClassName: deployforge-fast
      resources:
        requests:
          storage: 5Gi
```

5. **Apply everything and verify:**

```bash
kubectl apply -f deployforge-storageclass.yaml
kubectl apply -f postgres-statefulset.yaml
kubectl apply -f redis-statefulset.yaml

# Wait for pods to be ready
kubectl wait --for=condition=Ready pod/postgres-0 -n deployforge --timeout=120s
kubectl wait --for=condition=Ready pod/redis-0 -n deployforge --timeout=120s
```

6. **Verify data persistence:**

Write data to PostgreSQL and Redis. Delete the pods (not the StatefulSets). Wait for them to be recreated. Verify the data is still there.

7. **Inspect the PV lifecycle:**

```bash
kubectl get pvc -n deployforge
kubectl get pv
kubectl describe pv <pv-name>
```

### Verification

```bash
# PVCs should be Bound
kubectl get pvc -n deployforge
# NAME                     STATUS   VOLUME         CAPACITY   STORAGECLASS
# postgres-data-postgres-0 Bound    pvc-abc123...  10Gi       deployforge-fast
# redis-data-redis-0       Bound    pvc-def456...  5Gi        deployforge-fast

# PostgreSQL should be accepting connections
kubectl exec -n deployforge postgres-0 -- pg_isready -U deployforge_app
# → accepting connections

# Redis should respond to ping
kubectl exec -n deployforge redis-0 -- redis-cli ping
# → PONG
```

<details>
<summary>Show solution</summary>

```bash
#!/bin/bash
set -euo pipefail

NS="deployforge"

echo "=== Step 1: Create StorageClass ==="
cat <<'EOF' | kubectl apply -f -
apiVersion: storage.k8s.io/v1
kind: StorageClass
metadata:
  name: deployforge-fast
provisioner: rancher.io/local-path
reclaimPolicy: Retain
volumeBindingMode: WaitForFirstConsumer
EOF

echo ""
echo "=== Step 2: Create DB Secret ==="
kubectl create secret generic deployforge-db-credentials \
  --from-literal=DB_PASSWORD='deployforge-local-dev' \
  -n $NS --dry-run=client -o yaml | kubectl apply -f -

echo ""
echo "=== Step 3: Deploy PostgreSQL StatefulSet ==="
cat <<'EOF' | kubectl apply -f -
apiVersion: v1
kind: Service
metadata:
  name: postgres-headless
  namespace: deployforge
spec:
  clusterIP: None
  selector:
    app: postgres
  ports:
  - port: 5432
    targetPort: 5432
---
apiVersion: apps/v1
kind: StatefulSet
metadata:
  name: postgres
  namespace: deployforge
spec:
  serviceName: postgres-headless
  replicas: 1
  selector:
    matchLabels:
      app: postgres
  template:
    metadata:
      labels:
        app: postgres
    spec:
      containers:
      - name: postgres
        image: postgres:15-alpine
        ports:
        - containerPort: 5432
        env:
        - name: POSTGRES_DB
          value: "deployforge"
        - name: POSTGRES_USER
          value: "deployforge_app"
        - name: POSTGRES_PASSWORD
          valueFrom:
            secretKeyRef:
              name: deployforge-db-credentials
              key: DB_PASSWORD
        - name: PGDATA
          value: /var/lib/postgresql/data/pgdata
        volumeMounts:
        - name: postgres-data
          mountPath: /var/lib/postgresql/data
        readinessProbe:
          exec:
            command: ["pg_isready", "-U", "deployforge_app", "-d", "deployforge"]
          initialDelaySeconds: 5
          periodSeconds: 10
  volumeClaimTemplates:
  - metadata:
      name: postgres-data
    spec:
      accessModes: ["ReadWriteOnce"]
      storageClassName: deployforge-fast
      resources:
        requests:
          storage: 10Gi
EOF

echo ""
echo "=== Step 4: Deploy Redis StatefulSet ==="
cat <<'EOF' | kubectl apply -f -
apiVersion: v1
kind: Service
metadata:
  name: redis-headless
  namespace: deployforge
spec:
  clusterIP: None
  selector:
    app: redis
  ports:
  - port: 6379
    targetPort: 6379
---
apiVersion: apps/v1
kind: StatefulSet
metadata:
  name: redis
  namespace: deployforge
spec:
  serviceName: redis-headless
  replicas: 1
  selector:
    matchLabels:
      app: redis
  template:
    metadata:
      labels:
        app: redis
    spec:
      containers:
      - name: redis
        image: redis:7-alpine
        ports:
        - containerPort: 6379
        command: ["redis-server", "--appendonly", "yes", "--dir", "/data"]
        volumeMounts:
        - name: redis-data
          mountPath: /data
        readinessProbe:
          exec:
            command: ["redis-cli", "ping"]
          initialDelaySeconds: 5
          periodSeconds: 10
  volumeClaimTemplates:
  - metadata:
      name: redis-data
    spec:
      accessModes: ["ReadWriteOnce"]
      storageClassName: deployforge-fast
      resources:
        requests:
          storage: 5Gi
EOF

echo ""
echo "=== Step 5: Wait for pods ==="
kubectl wait --for=condition=Ready pod/postgres-0 -n $NS --timeout=120s
kubectl wait --for=condition=Ready pod/redis-0 -n $NS --timeout=120s

echo ""
echo "=== Step 6: Write test data ==="
kubectl exec -n $NS postgres-0 -- psql -U deployforge_app -d deployforge \
  -c "CREATE TABLE IF NOT EXISTS persistence_test (id serial, msg text); \
      INSERT INTO persistence_test (msg) VALUES ('data survives restarts');"

kubectl exec -n $NS redis-0 -- redis-cli SET persistence:test "data survives restarts"

echo ""
echo "=== Step 7: Delete pods (not StatefulSets) ==="
kubectl delete pod postgres-0 redis-0 -n $NS --grace-period=0
echo "Waiting for pods to be recreated..."
kubectl wait --for=condition=Ready pod/postgres-0 -n $NS --timeout=120s
kubectl wait --for=condition=Ready pod/redis-0 -n $NS --timeout=120s

echo ""
echo "=== Step 8: Verify data persisted ==="
echo "PostgreSQL:"
kubectl exec -n $NS postgres-0 -- psql -U deployforge_app -d deployforge \
  -c "SELECT * FROM persistence_test;"
# → data survives restarts

echo ""
echo "Redis:"
kubectl exec -n $NS redis-0 -- redis-cli GET persistence:test
# → data survives restarts

echo ""
echo "=== Step 9: Inspect PV lifecycle ==="
kubectl get pvc -n $NS
kubectl get pv -o custom-columns='NAME:.metadata.name,CAPACITY:.spec.capacity.storage,STATUS:.status.phase,CLAIM:.spec.claimRef.name,RECLAIM:.spec.persistentVolumeReclaimPolicy'
```

</details>

---

## Exercise 2: Create ConfigMaps and Secrets for DeployForge

**Goal:** Build the complete configuration layer for DeployForge: a ConfigMap for application settings, Secrets for credentials, and an nginx ConfigMap for the reverse proxy. Demonstrate environment variable injection, file mounting, and hot reload behavior.

### Steps

1. **Create the application ConfigMap:**

```yaml
# deployforge-configmap.yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: deployforge-config
  namespace: deployforge
data:
  LOG_LEVEL: "info"
  LOG_FORMAT: "json"
  API_PORT: "3000"
  WORKER_CONCURRENCY: "5"
  DATABASE_POOL_SIZE: "10"
  DATABASE_POOL_TIMEOUT: "30000"
  REDIS_DB: "0"
  FEATURE_DARK_LAUNCH: "false"
  FEATURE_CANARY_DEPLOYMENTS: "true"
  CORS_ORIGINS: "https://deployforge.local"
```

2. **Create Secrets for database, Redis, and API keys:**

```bash
# Database credentials
kubectl create secret generic deployforge-db-credentials \
  --from-literal=DB_HOST=postgres-headless.deployforge.svc.cluster.local \
  --from-literal=DB_PORT=5432 \
  --from-literal=DB_NAME=deployforge \
  --from-literal=DB_USER=deployforge_app \
  --from-literal=DB_PASSWORD='deployforge-local-dev' \
  -n deployforge --dry-run=client -o yaml | kubectl apply -f -

# Redis credentials
kubectl create secret generic deployforge-redis-credentials \
  --from-literal=REDIS_HOST=redis-headless.deployforge.svc.cluster.local \
  --from-literal=REDIS_PORT=6379 \
  --from-literal=REDIS_PASSWORD='redis-local-dev' \
  -n deployforge

# API keys
kubectl create secret generic deployforge-api-keys \
  --from-literal=JWT_SECRET='dev-jwt-secret-32-characters-min' \
  --from-literal=GITHUB_WEBHOOK_SECRET='dev-webhook-secret' \
  --from-literal=ENCRYPTION_KEY='dev-encryption-key-32-chars-long!' \
  -n deployforge
```

3. **Create a test Deployment that consumes both ConfigMap and Secrets:**

```yaml
# config-test-deployment.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: config-test
  namespace: deployforge
spec:
  replicas: 1
  selector:
    matchLabels:
      app: config-test
  template:
    metadata:
      labels:
        app: config-test
    spec:
      containers:
      - name: app
        image: busybox:1.36
        command: ['sh', '-c', 'while true; do echo "LOG_LEVEL=$LOG_LEVEL DB_HOST=$DB_HOST JWT=$(echo $JWT_SECRET | cut -c1-8)..."; sleep 10; done']
        envFrom:
        - configMapRef:
            name: deployforge-config
        - secretRef:
            name: deployforge-db-credentials
        env:
        - name: JWT_SECRET
          valueFrom:
            secretKeyRef:
              name: deployforge-api-keys
              key: JWT_SECRET
```

4. **Verify the configuration is injected correctly:**

```bash
kubectl apply -f deployforge-configmap.yaml
kubectl apply -f config-test-deployment.yaml
kubectl logs -n deployforge -l app=config-test --tail=1
```

5. **Test ConfigMap hot reload (volume mount):**

Create a second pod that mounts the ConfigMap as a file. Update the ConfigMap and verify the file changes inside the pod without a restart.

6. **Clean up the test Deployment:**

```bash
kubectl delete deployment config-test -n deployforge
```

### Verification

```bash
# ConfigMap should have all expected keys
kubectl get configmap deployforge-config -n deployforge -o yaml | grep -E 'LOG_LEVEL|API_PORT|WORKER_CONCURRENCY'

# Secrets should exist with correct types
kubectl get secrets -n deployforge
# deployforge-db-credentials    Opaque   5
# deployforge-redis-credentials Opaque   3
# deployforge-api-keys          Opaque   3

# Pod should have all env vars injected
kubectl exec -n deployforge deploy/config-test -- env | sort | grep -E 'LOG_LEVEL|DB_HOST|JWT_SECRET'
```

<details>
<summary>Show solution</summary>

```bash
#!/bin/bash
set -euo pipefail

NS="deployforge"

echo "=== Step 1: Create ConfigMap ==="
cat <<'EOF' | kubectl apply -f -
apiVersion: v1
kind: ConfigMap
metadata:
  name: deployforge-config
  namespace: deployforge
data:
  LOG_LEVEL: "info"
  LOG_FORMAT: "json"
  API_PORT: "3000"
  WORKER_CONCURRENCY: "5"
  DATABASE_POOL_SIZE: "10"
  DATABASE_POOL_TIMEOUT: "30000"
  REDIS_DB: "0"
  FEATURE_DARK_LAUNCH: "false"
  FEATURE_CANARY_DEPLOYMENTS: "true"
  CORS_ORIGINS: "https://deployforge.local"
EOF

echo ""
echo "=== Step 2: Create Secrets ==="
kubectl create secret generic deployforge-db-credentials \
  --from-literal=DB_HOST=postgres-headless.deployforge.svc.cluster.local \
  --from-literal=DB_PORT=5432 \
  --from-literal=DB_NAME=deployforge \
  --from-literal=DB_USER=deployforge_app \
  --from-literal=DB_PASSWORD='deployforge-local-dev' \
  -n $NS --dry-run=client -o yaml | kubectl apply -f -

kubectl create secret generic deployforge-redis-credentials \
  --from-literal=REDIS_HOST=redis-headless.deployforge.svc.cluster.local \
  --from-literal=REDIS_PORT=6379 \
  --from-literal=REDIS_PASSWORD='redis-local-dev' \
  -n $NS --dry-run=client -o yaml | kubectl apply -f -

kubectl create secret generic deployforge-api-keys \
  --from-literal=JWT_SECRET='dev-jwt-secret-32-characters-min' \
  --from-literal=GITHUB_WEBHOOK_SECRET='dev-webhook-secret' \
  --from-literal=ENCRYPTION_KEY='dev-encryption-key-32-chars-long!' \
  -n $NS --dry-run=client -o yaml | kubectl apply -f -

echo ""
echo "=== Step 3: Deploy test pod (env vars) ==="
cat <<'EOF' | kubectl apply -f -
apiVersion: apps/v1
kind: Deployment
metadata:
  name: config-test
  namespace: deployforge
spec:
  replicas: 1
  selector:
    matchLabels:
      app: config-test
  template:
    metadata:
      labels:
        app: config-test
    spec:
      containers:
      - name: app
        image: busybox:1.36
        command: ['sh', '-c', 'while true; do echo "LOG=$LOG_LEVEL DB=$DB_HOST JWT=$(echo $JWT_SECRET | cut -c1-8)..."; sleep 10; done']
        envFrom:
        - configMapRef:
            name: deployforge-config
        - secretRef:
            name: deployforge-db-credentials
        env:
        - name: JWT_SECRET
          valueFrom:
            secretKeyRef:
              name: deployforge-api-keys
              key: JWT_SECRET
EOF

kubectl wait --for=condition=Available deployment/config-test -n $NS --timeout=60s
sleep 5

echo ""
echo "=== Step 4: Verify env vars ==="
kubectl exec -n $NS deploy/config-test -- env | sort | grep -E 'LOG_LEVEL|DB_HOST|JWT_SECRET|API_PORT'

echo ""
echo "=== Step 5: Deploy pod with volume mount (hot reload test) ==="
cat <<'EOF' | kubectl apply -f -
apiVersion: v1
kind: Pod
metadata:
  name: config-volume-test
  namespace: deployforge
spec:
  containers:
  - name: watcher
    image: busybox:1.36
    command: ['sh', '-c', 'while true; do echo "Config: $(cat /config/LOG_LEVEL)"; sleep 5; done']
    volumeMounts:
    - name: config
      mountPath: /config
  volumes:
  - name: config
    configMap:
      name: deployforge-config
EOF

kubectl wait --for=condition=Ready pod/config-volume-test -n $NS --timeout=60s
sleep 3

echo ""
echo "Before update:"
kubectl exec -n $NS config-volume-test -- cat /config/LOG_LEVEL

echo ""
echo "=== Step 6: Update ConfigMap (hot reload) ==="
kubectl patch configmap deployforge-config -n $NS \
  --type merge -p '{"data":{"LOG_LEVEL":"debug"}}'
echo "Waiting 90s for kubelet to sync volume..."
sleep 90

echo "After update:"
kubectl exec -n $NS config-volume-test -- cat /config/LOG_LEVEL
# → debug (volume mount auto-updated!)

echo ""
echo "Env var in original pod (NOT updated — requires restart):"
kubectl exec -n $NS deploy/config-test -- sh -c 'echo $LOG_LEVEL'
# → info (env vars are immutable after pod start)

echo ""
echo "=== Step 7: Restore ConfigMap and clean up test resources ==="
kubectl patch configmap deployforge-config -n $NS \
  --type merge -p '{"data":{"LOG_LEVEL":"info"}}'
kubectl delete deployment config-test -n $NS
kubectl delete pod config-volume-test -n $NS --grace-period=0
```

</details>

---

## Exercise 3: Build a Helm Chart for DeployForge

**Goal:** Create a complete Helm chart for DeployForge with parameterized templates, environment-specific values files, a pre-install migration hook, and chart tests. Install it in your kind cluster.

### Steps

1. **Scaffold the chart:**

```bash
helm create deployforge-chart
cd deployforge-chart
```

2. **Replace `values.yaml` with DeployForge-specific values:**

Set defaults for image, replicas, ports, logging, persistence, and Ingress.

3. **Create templates for:**
   - API Gateway Deployment
   - ClusterIP Service
   - ConfigMap (from values)
   - Ingress (conditional on `.Values.ingress.enabled`)
   - ServiceAccount

4. **Create the `_helpers.tpl` with named templates:**
   - `deployforge.name`
   - `deployforge.fullname`
   - `deployforge.labels`
   - `deployforge.selectorLabels`

5. **Create a pre-install hook Job** that echoes "Running database migration..."

6. **Create a test pod** that curls the service health endpoint.

7. **Create environment-specific values:**
   - `values-dev.yaml` — 1 replica, debug logging
   - `values-prod.yaml` — 3 replicas, warn logging, Ingress enabled

8. **Lint, template, and install:**

```bash
helm lint .
helm template deployforge . --namespace deployforge
helm install deployforge . --namespace deployforge --wait
helm test deployforge -n deployforge
```

9. **Upgrade with prod values and verify rollback:**

```bash
helm upgrade deployforge . -n deployforge --values values-prod.yaml
helm history deployforge -n deployforge
helm rollback deployforge 1 -n deployforge
```

### Verification

```bash
# Chart should be deployed
helm list -n deployforge
# NAME         NAMESPACE    REVISION  STATUS    CHART
# deployforge  deployforge  1         deployed  deployforge-0.1.0

# All resources should be running
kubectl get all -n deployforge -l app.kubernetes.io/instance=deployforge

# Helm test should pass
helm test deployforge -n deployforge
# TEST SUITE:     deployforge-test-connection
# Phase:          Succeeded
```

<details>
<summary>Show solution</summary>

```bash
#!/bin/bash
set -euo pipefail

NS="deployforge"

echo "=== Step 1: Scaffold chart ==="
helm create deployforge-chart
cd deployforge-chart

echo ""
echo "=== Step 2: Write values.yaml ==="
cat > values.yaml <<'EOF'
replicaCount: 1

image:
  repository: nginx
  pullPolicy: IfNotPresent
  tag: "1.25-alpine"

serviceAccount:
  create: true
  name: "deployforge"

apiGateway:
  port: 80
  logLevel: "info"
  logFormat: "json"
  workerConcurrency: 5
  databasePoolSize: 10

ingress:
  enabled: false
  className: "nginx"
  hosts:
    - host: deployforge.local
      paths:
        - path: /
          pathType: Prefix
  tls: []

resources:
  requests:
    cpu: 100m
    memory: 128Mi
  limits:
    cpu: 500m
    memory: 512Mi

autoscaling:
  enabled: false
  minReplicas: 2
  maxReplicas: 10
  targetCPUUtilizationPercentage: 70
EOF

echo ""
echo "=== Step 3: Write ConfigMap template ==="
cat > templates/configmap.yaml <<'TMPL'
apiVersion: v1
kind: ConfigMap
metadata:
  name: {{ include "deployforge-chart.fullname" . }}-config
  labels:
    {{- include "deployforge-chart.labels" . | nindent 4 }}
data:
  LOG_LEVEL: {{ .Values.apiGateway.logLevel | quote }}
  LOG_FORMAT: {{ .Values.apiGateway.logFormat | quote }}
  API_PORT: {{ .Values.apiGateway.port | quote }}
  WORKER_CONCURRENCY: {{ .Values.apiGateway.workerConcurrency | quote }}
  DATABASE_POOL_SIZE: {{ .Values.apiGateway.databasePoolSize | quote }}
TMPL

echo ""
echo "=== Step 4: Write pre-install hook ==="
mkdir -p templates/hooks
cat > templates/hooks/pre-install-migration.yaml <<'TMPL'
apiVersion: batch/v1
kind: Job
metadata:
  name: {{ include "deployforge-chart.fullname" . }}-db-migrate
  labels:
    {{- include "deployforge-chart.labels" . | nindent 4 }}
  annotations:
    "helm.sh/hook": pre-install,pre-upgrade
    "helm.sh/hook-weight": "-5"
    "helm.sh/hook-delete-policy": before-hook-creation,hook-succeeded
spec:
  backoffLimit: 1
  template:
    spec:
      restartPolicy: Never
      containers:
      - name: migrate
        image: busybox:1.36
        command: ['sh', '-c', 'echo "Running database migration..." && sleep 3 && echo "Migration complete!"']
TMPL

echo ""
echo "=== Step 5: Write values-dev.yaml and values-prod.yaml ==="
cat > values-dev.yaml <<'EOF'
replicaCount: 1
apiGateway:
  logLevel: "debug"
  logFormat: "text"
resources:
  requests:
    cpu: 50m
    memory: 64Mi
  limits:
    cpu: 200m
    memory: 256Mi
EOF

cat > values-prod.yaml <<'EOF'
replicaCount: 3
apiGateway:
  logLevel: "warn"
  logFormat: "json"
  workerConcurrency: 10
  databasePoolSize: 25
ingress:
  enabled: true
  className: "nginx"
  hosts:
    - host: deployforge.example.com
      paths:
        - path: /
          pathType: Prefix
resources:
  requests:
    cpu: 500m
    memory: 512Mi
  limits:
    cpu: "2"
    memory: 2Gi
autoscaling:
  enabled: true
  minReplicas: 3
  maxReplicas: 20
  targetCPUUtilizationPercentage: 70
EOF

echo ""
echo "=== Step 6: Lint ==="
helm lint .
helm lint . --values values-prod.yaml

echo ""
echo "=== Step 7: Preview rendered output ==="
helm template deployforge . --namespace $NS | head -60

echo ""
echo "=== Step 8: Install ==="
helm install deployforge . --namespace $NS --wait --timeout 3m

echo ""
echo "=== Step 9: Verify ==="
helm list -n $NS
kubectl get all -n $NS -l app.kubernetes.io/instance=deployforge

echo ""
echo "=== Step 10: Test ==="
helm test deployforge -n $NS

echo ""
echo "=== Step 11: Upgrade with prod values ==="
helm upgrade deployforge . -n $NS --values values-prod.yaml --wait

echo ""
echo "=== Step 12: Check history ==="
helm history deployforge -n $NS

echo ""
echo "=== Step 13: Rollback to revision 1 ==="
helm rollback deployforge 1 -n $NS --wait
helm history deployforge -n $NS

echo ""
echo "=== Cleanup ==="
helm uninstall deployforge -n $NS
cd ..
rm -rf deployforge-chart
```

</details>

---

## Exercise 4: Create Kustomize Overlays for Dev, Staging, and Prod

**Goal:** Build a complete Kustomize directory structure for DeployForge with a shared base and three environment-specific overlays. Each overlay should customize replicas, resource limits, logging, and ConfigMap values. Deploy each to separate namespaces and compare the rendered output.

### Steps

1. **Create the directory structure:**

```bash
mkdir -p deployforge-kustomize/{base,overlays/{dev,staging,prod}}
```

2. **Create the base manifests:**
   - `deployment.yaml` — API Gateway Deployment (1 replica, `deployforge/api-gateway:latest`)
   - `service.yaml` — ClusterIP Service
   - `kustomization.yaml` — lists resources, includes a `configMapGenerator`

3. **Create the dev overlay:**
   - `kustomization.yaml` — reference `../../base`, set `namespace: deployforge-dev`, `namePrefix: dev-`, 1 replica, debug logging
   - Override image tag to `dev-latest`

4. **Create the staging overlay:**
   - `kustomization.yaml` — reference `../../base`, set `namespace: deployforge-staging`, `namePrefix: staging-`, 2 replicas, info logging

5. **Create the prod overlay:**
   - `kustomization.yaml` — reference `../../base`, set `namespace: deployforge-prod`, `namePrefix: prod-`, 3 replicas, warn logging
   - Add a JSON patch for production resource limits (cpu: 1, memory: 2Gi)
   - Add an HPA resource (prod only)

6. **Build and compare all three environments:**

```bash
echo "=== Dev ===" && kubectl kustomize overlays/dev/
echo "=== Staging ===" && kubectl kustomize overlays/staging/
echo "=== Prod ===" && kubectl kustomize overlays/prod/
```

7. **Apply the dev overlay to your cluster:**

```bash
kubectl create namespace deployforge-dev
kubectl apply -k overlays/dev/
kubectl get all -n deployforge-dev
```

8. **Clean up:**

```bash
kubectl delete -k overlays/dev/
kubectl delete namespace deployforge-dev
```

### Verification

```bash
# Dev: 1 replica, debug logging
kubectl kustomize overlays/dev/ | grep -E 'replicas:|LOG_LEVEL'
# replicas: 1
# LOG_LEVEL: debug

# Staging: 2 replicas, info logging
kubectl kustomize overlays/staging/ | grep -E 'replicas:|LOG_LEVEL'
# replicas: 2
# LOG_LEVEL: info

# Prod: 3 replicas, warn logging, HPA present
kubectl kustomize overlays/prod/ | grep -E 'replicas:|LOG_LEVEL|HorizontalPodAutoscaler'
# replicas: 3
# LOG_LEVEL: warn
# kind: HorizontalPodAutoscaler
```

<details>
<summary>Show solution</summary>

```bash
#!/bin/bash
set -euo pipefail

WORK_DIR="deployforge-kustomize"
mkdir -p "$WORK_DIR"/{base,overlays/{dev,staging,prod}}

echo "=== Step 1: Create base ==="
cat > "$WORK_DIR/base/deployment.yaml" <<'EOF'
apiVersion: apps/v1
kind: Deployment
metadata:
  name: api-gateway
spec:
  replicas: 1
  selector:
    matchLabels:
      app: api-gateway
  template:
    metadata:
      labels:
        app: api-gateway
    spec:
      containers:
      - name: api
        image: deployforge/api-gateway:latest
        ports:
        - containerPort: 3000
          name: http
        envFrom:
        - configMapRef:
            name: deployforge-config
        resources:
          requests:
            cpu: 100m
            memory: 128Mi
          limits:
            cpu: 500m
            memory: 512Mi
        livenessProbe:
          httpGet:
            path: /health
            port: http
          initialDelaySeconds: 15
          periodSeconds: 10
        readinessProbe:
          httpGet:
            path: /ready
            port: http
          initialDelaySeconds: 5
          periodSeconds: 5
EOF

cat > "$WORK_DIR/base/service.yaml" <<'EOF'
apiVersion: v1
kind: Service
metadata:
  name: api-gateway
spec:
  selector:
    app: api-gateway
  ports:
  - port: 3000
    targetPort: 3000
    name: http
EOF

cat > "$WORK_DIR/base/kustomization.yaml" <<'EOF'
apiVersion: kustomize.config.k8s.io/v1beta1
kind: Kustomization

commonLabels:
  app.kubernetes.io/name: deployforge
  app.kubernetes.io/managed-by: kustomize

resources:
  - deployment.yaml
  - service.yaml

configMapGenerator:
  - name: deployforge-config
    literals:
      - LOG_LEVEL=info
      - LOG_FORMAT=json
      - API_PORT=3000
      - WORKER_CONCURRENCY=5
      - DATABASE_POOL_SIZE=10
EOF

echo ""
echo "=== Step 2: Create dev overlay ==="
cat > "$WORK_DIR/overlays/dev/kustomization.yaml" <<'EOF'
apiVersion: kustomize.config.k8s.io/v1beta1
kind: Kustomization

resources:
  - ../../base

namespace: deployforge-dev
namePrefix: dev-

images:
  - name: deployforge/api-gateway
    newTag: dev-latest

replicas:
  - name: api-gateway
    count: 1

configMapGenerator:
  - name: deployforge-config
    behavior: merge
    literals:
      - LOG_LEVEL=debug
      - LOG_FORMAT=text
      - FEATURE_DEBUG_MODE=true

patches:
  - target:
      kind: Deployment
      name: api-gateway
    patch: |-
      - op: replace
        path: /spec/template/spec/containers/0/resources/requests/cpu
        value: 50m
      - op: replace
        path: /spec/template/spec/containers/0/resources/requests/memory
        value: 64Mi
      - op: replace
        path: /spec/template/spec/containers/0/resources/limits/cpu
        value: 200m
      - op: replace
        path: /spec/template/spec/containers/0/resources/limits/memory
        value: 256Mi
EOF

echo ""
echo "=== Step 3: Create staging overlay ==="
cat > "$WORK_DIR/overlays/staging/kustomization.yaml" <<'EOF'
apiVersion: kustomize.config.k8s.io/v1beta1
kind: Kustomization

resources:
  - ../../base

namespace: deployforge-staging
namePrefix: staging-

images:
  - name: deployforge/api-gateway
    newTag: staging-abc1234

replicas:
  - name: api-gateway
    count: 2

configMapGenerator:
  - name: deployforge-config
    behavior: merge
    literals:
      - LOG_LEVEL=info
      - DATABASE_POOL_SIZE=15
      - FEATURE_CANARY=true

patches:
  - target:
      kind: Deployment
      name: api-gateway
    patch: |-
      - op: replace
        path: /spec/template/spec/containers/0/resources/requests/cpu
        value: 250m
      - op: replace
        path: /spec/template/spec/containers/0/resources/requests/memory
        value: 512Mi
      - op: replace
        path: /spec/template/spec/containers/0/resources/limits/cpu
        value: "1"
      - op: replace
        path: /spec/template/spec/containers/0/resources/limits/memory
        value: 1Gi
EOF

echo ""
echo "=== Step 4: Create prod overlay ==="
cat > "$WORK_DIR/overlays/prod/hpa.yaml" <<'EOF'
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: api-gateway
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: api-gateway
  minReplicas: 3
  maxReplicas: 20
  metrics:
  - type: Resource
    resource:
      name: cpu
      target:
        type: Utilization
        averageUtilization: 70
EOF

cat > "$WORK_DIR/overlays/prod/kustomization.yaml" <<'EOF'
apiVersion: kustomize.config.k8s.io/v1beta1
kind: Kustomization

resources:
  - ../../base
  - hpa.yaml

namespace: deployforge-prod
namePrefix: prod-

images:
  - name: deployforge/api-gateway
    newTag: v1.2.0

replicas:
  - name: api-gateway
    count: 3

configMapGenerator:
  - name: deployforge-config
    behavior: merge
    literals:
      - LOG_LEVEL=warn
      - DATABASE_POOL_SIZE=25
      - WORKER_CONCURRENCY=10

patches:
  - target:
      kind: Deployment
      name: api-gateway
    patch: |-
      - op: replace
        path: /spec/template/spec/containers/0/resources/requests/cpu
        value: "1"
      - op: replace
        path: /spec/template/spec/containers/0/resources/requests/memory
        value: 2Gi
      - op: replace
        path: /spec/template/spec/containers/0/resources/limits/cpu
        value: "2"
      - op: replace
        path: /spec/template/spec/containers/0/resources/limits/memory
        value: 4Gi
  - target:
      kind: Deployment
      name: api-gateway
    patch: |-
      apiVersion: apps/v1
      kind: Deployment
      metadata:
        name: api-gateway
      spec:
        template:
          spec:
            affinity:
              podAntiAffinity:
                preferredDuringSchedulingIgnoredDuringExecution:
                - weight: 100
                  podAffinityTerm:
                    labelSelector:
                      matchLabels:
                        app: api-gateway
                    topologyKey: kubernetes.io/hostname
EOF

echo ""
echo "=== Step 5: Build and compare all environments ==="
for env in dev staging prod; do
  echo ""
  echo "--- $env ---"
  kubectl kustomize "$WORK_DIR/overlays/$env/" | grep -E 'replicas:|LOG_LEVEL|HorizontalPodAutoscaler|namespace:|cpu:|memory:' | head -15
done

echo ""
echo "=== Step 6: Full rendered output for prod ==="
kubectl kustomize "$WORK_DIR/overlays/prod/" | head -80

echo ""
echo "=== Cleanup ==="
rm -rf "$WORK_DIR"
```

</details>

---

## Capstone Connection

These exercises build the complete storage and configuration layer for **DeployForge**:

- **Exercise 1** establishes the data tier — PostgreSQL and Redis StatefulSets with PVCs on a custom `deployforge-fast` StorageClass. This is the foundation that Module 09 (Reliability Engineering) builds backup and disaster recovery on.
- **Exercise 2** creates the configuration layer — ConfigMaps for app settings, Secrets for credentials, and demonstrates the hot-reload behavior that informs how you'll design config updates in production (immutable ConfigMaps with hash suffixes).
- **Exercise 3** packages DeployForge as a distributable Helm chart. This is the artifact that other teams install to run their own DeployForge instances. The chart's values files, hooks, and tests form the deployment contract.
- **Exercise 4** implements the internal deployment strategy — Kustomize overlays that the DeployForge team uses to manage their own dev/staging/prod environments. ArgoCD (Module 10) watches these overlays and auto-syncs on merge.

Together, these four exercises give DeployForge persistent state, externalized configuration, a package distribution mechanism (Helm), and an environment management strategy (Kustomize) — the storage and configuration backbone of a production-grade deployment platform.
