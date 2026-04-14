# 13.2 — End-to-End Deployment Walkthrough

## Concept

You've built every piece of DeployForge in isolation: Dockerfiles in Module 02, Kubernetes manifests in Module 05, monitoring in Module 08, CI/CD in Module 10, scaling in Module 12. Now you need to prove that they all work *together*. This is the integration test for your infrastructure skills — a complete walkthrough from a cold start to a fully operational production deployment, executed command by command, with annotated output at every step.

The gap between "each piece works" and "the whole system works" is where most real-world outages live. Configuration mismatches between environments, missing RBAC permissions that only surface under ArgoCD's service account, Prometheus scrape targets that don't match actual pod labels, HPA metrics that reference a metric name that doesn't exist yet — these integration failures are invisible until you wire everything up. This walkthrough exposes them systematically.

---

## Deep Dive

### Deployment Architecture Overview

The end-to-end deployment follows this flow. Every box references the module where you built that component:

```
┌─────────────────────────────────────────────────────────────────────────┐
│                    DeployForge Deployment Flow                           │
│                                                                         │
│  ① Code Commit (Developer)                                              │
│     │                                                                   │
│     ▼                                                                   │
│  ② GitHub Actions CI  [Module 10]                                       │
│     ├── Lint + Typecheck                                                │
│     ├── Unit + Integration Tests                                        │
│     ├── Docker Build (multi-arch)                                       │
│     ├── Trivy Vulnerability Scan  [Module 03]                           │
│     └── Push to Container Registry                                      │
│            │                                                            │
│            ▼                                                            │
│  ③ Update Kustomize Image Tag  [Module 07]                              │
│     └── Commit to GitOps repo (or same repo, deploy/ directory)         │
│            │                                                            │
│            ▼                                                            │
│  ④ ArgoCD Detects Drift  [Module 10]                                    │
│     ├── Compares desired state (Git) vs live state (cluster)            │
│     └── Triggers sync                                                   │
│            │                                                            │
│            ▼                                                            │
│  ⑤ Argo Rollouts Canary Deploy  [Module 10]                            │
│     ├── 10% traffic → new version                                       │
│     ├── AnalysisTemplate queries Prometheus  [Module 08]                │
│     ├── If healthy: 50% → 100% (progressive)                           │
│     └── If unhealthy: automatic rollback                                │
│            │                                                            │
│            ▼                                                            │
│  ⑥ Full Promotion                                                       │
│     ├── Prometheus confirms SLO compliance  [Module 09]                 │
│     ├── HPA scales to match traffic  [Module 12]                        │
│     └── Grafana dashboards show green  [Module 08]                      │
│            │                                                            │
│            ▼                                                            │
│  ⑦ Steady State                                                        │
│     ├── Alerts armed for SLO burn-rate violations  [Module 09]          │
│     ├── Auto-scaling responding to load  [Module 12]                    │
│     └── ArgoCD watching for next commit                                 │
└─────────────────────────────────────────────────────────────────────────┘
```

### Phase 1: Fresh Cluster Bootstrap

Start from nothing. This verifies that your infrastructure-as-code is genuinely reproducible — no hidden manual steps.

#### Step 1: Create the kind Cluster

```bash
# Create a multi-node kind cluster with port mappings for Ingress
cat <<'EOF' > kind-config.yaml
kind: Cluster
apiVersion: kind.x-k8s.io/v1alpha4
nodes:
  - role: control-plane
    kubeadmConfigPatches:
      - |
        kind: InitConfiguration
        nodeRegistration:
          kubeletExtraArgs:
            node-labels: "ingress-ready=true"
    extraPortMappings:
      - containerPort: 80
        hostPort: 80
        protocol: TCP
      - containerPort: 443
        hostPort: 443
        protocol: TCP
  - role: worker
  - role: worker
EOF

kind create cluster --name deployforge --config kind-config.yaml
# → Creating cluster "deployforge" ...
# →  ✓ Ensuring node image (kindest/node:v1.29.0) 🖼
# →  ✓ Preparing nodes 📦 📦 📦
# →  ✓ Writing configuration 📜
# →  ✓ Starting control-plane 🕹️
# →  ✓ Installing CNI 🔌
# →  ✓ Installing StorageClass 💾
# →  ✓ Joining worker nodes 🚜
# → Set kubectl context to "kind-deployforge"

kubectl get nodes
# → NAME                       STATUS   ROLES           AGE   VERSION
# → deployforge-control-plane  Ready    control-plane   60s   v1.29.0
# → deployforge-worker         Ready    <none>          45s   v1.29.0
# → deployforge-worker2        Ready    <none>          45s   v1.29.0
```

#### Step 2: Install Cluster-Wide Dependencies

```bash
# Install metrics-server (required for HPA — Module 12)
kubectl apply -f https://github.com/kubernetes-sigs/metrics-server/releases/latest/download/components.yaml

# Patch metrics-server for kind (insecure TLS — dev only)
kubectl patch deployment metrics-server -n kube-system \
  --type='json' \
  -p='[{"op":"add","path":"/spec/template/spec/containers/0/args/-","value":"--kubelet-insecure-tls"}]'

# Install Nginx Ingress Controller (Module 06)
kubectl apply -f https://raw.githubusercontent.com/kubernetes/ingress-nginx/main/deploy/static/provider/kind/deploy.yaml

# Wait for Ingress controller to be ready
kubectl wait --namespace ingress-nginx \
  --for=condition=ready pod \
  --selector=app.kubernetes.io/component=controller \
  --timeout=120s
# → pod/ingress-nginx-controller-xxxxx condition met
```

#### Step 3: Create Namespaces and RBAC

```bash
# Create namespaces for DeployForge environments
kubectl create namespace deployforge-prod
kubectl create namespace deployforge-staging
kubectl create namespace monitoring
kubectl create namespace argocd

# Apply ResourceQuotas (Module 12)
cat <<'EOF' | kubectl apply -f -
apiVersion: v1
kind: ResourceQuota
metadata:
  name: deployforge-quota
  namespace: deployforge-prod
spec:
  hard:
    requests.cpu: "8"
    requests.memory: 16Gi
    limits.cpu: "16"
    limits.memory: 32Gi
    pods: "50"
    services: "20"
    persistentvolumeclaims: "10"
EOF
# → resourcequota/deployforge-quota created

# Apply LimitRange defaults (Module 12)
cat <<'EOF' | kubectl apply -f -
apiVersion: v1
kind: LimitRange
metadata:
  name: deployforge-limits
  namespace: deployforge-prod
spec:
  limits:
    - default:
        cpu: 500m
        memory: 512Mi
      defaultRequest:
        cpu: 100m
        memory: 128Mi
      type: Container
EOF
# → limitrange/deployforge-limits created

# Apply NetworkPolicy: deny-all default (Module 06)
cat <<'EOF' | kubectl apply -f -
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: default-deny-all
  namespace: deployforge-prod
spec:
  podSelector: {}
  policyTypes:
    - Ingress
    - Egress
EOF
# → networkpolicy/default-deny-all created
```

### Phase 2: Deploy Stateful Services

Databases and caches go first — they're the foundation everything else depends on.

#### Step 4: Deploy PostgreSQL

```yaml
# k8s/base/postgres/statefulset.yaml — PostgreSQL StatefulSet (Module 05)
apiVersion: apps/v1
kind: StatefulSet
metadata:
  name: postgres
  namespace: deployforge-prod
  labels:
    app: postgres
    tier: database
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
        tier: database
    spec:
      securityContext:
        runAsNonRoot: true          # Module 03: security hardening
        runAsUser: 999
        fsGroup: 999
      containers:
        - name: postgres
          image: postgres:15-alpine  # Module 02: pinned version
          ports:
            - containerPort: 5432
              name: postgres
          env:
            - name: POSTGRES_DB
              value: deployforge
            - name: POSTGRES_USER
              valueFrom:
                secretKeyRef:        # Module 07: secrets management
                  name: postgres-credentials
                  key: username
            - name: POSTGRES_PASSWORD
              valueFrom:
                secretKeyRef:
                  name: postgres-credentials
                  key: password
            - name: PGDATA
              value: /var/lib/postgresql/data/pgdata
          resources:                 # Module 12: resource management
            requests:
              cpu: 250m
              memory: 512Mi
            limits:
              cpu: "1"
              memory: 1Gi
          volumeMounts:
            - name: postgres-data
              mountPath: /var/lib/postgresql/data
          livenessProbe:             # Module 05: health checks
            exec:
              command: ["pg_isready", "-U", "deployforge"]
            initialDelaySeconds: 30
            periodSeconds: 10
          readinessProbe:
            exec:
              command: ["pg_isready", "-U", "deployforge"]
            initialDelaySeconds: 5
            periodSeconds: 5
  volumeClaimTemplates:              # Module 07: persistent storage
    - metadata:
        name: postgres-data
      spec:
        accessModes: ["ReadWriteOnce"]
        resources:
          requests:
            storage: 10Gi
---
# k8s/base/postgres/service.yaml
apiVersion: v1
kind: Service
metadata:
  name: postgres
  namespace: deployforge-prod
  labels:
    app: postgres
spec:
  type: ClusterIP
  ports:
    - port: 5432
      targetPort: 5432
      name: postgres
  selector:
    app: postgres
```

```bash
# Create PostgreSQL credentials secret
kubectl create secret generic postgres-credentials \
  --namespace deployforge-prod \
  --from-literal=username=deployforge \
  --from-literal=password="$(openssl rand -base64 24)"
# → secret/postgres-credentials created

# Apply the StatefulSet
kubectl apply -f k8s/base/postgres/
# → statefulset.apps/postgres created
# → service/postgres created

# Wait for PostgreSQL to be ready
kubectl wait --namespace deployforge-prod \
  --for=condition=ready pod \
  --selector=app=postgres \
  --timeout=120s
# → pod/postgres-0 condition met

# Verify
kubectl exec -n deployforge-prod postgres-0 -- pg_isready -U deployforge
# → /var/run/postgresql:5432 - accepting connections
```

#### Step 5: Deploy Redis

```yaml
# k8s/base/redis/deployment.yaml — Redis Deployment (Module 05)
apiVersion: apps/v1
kind: Deployment
metadata:
  name: redis
  namespace: deployforge-prod
  labels:
    app: redis
    tier: cache
spec:
  replicas: 1
  selector:
    matchLabels:
      app: redis
  template:
    metadata:
      labels:
        app: redis
        tier: cache
    spec:
      securityContext:
        runAsNonRoot: true
        runAsUser: 999
      containers:
        - name: redis
          image: redis:7-alpine
          command: ["redis-server", "--maxmemory", "256mb", "--maxmemory-policy", "allkeys-lru"]
          ports:
            - containerPort: 6379
              name: redis
          resources:
            requests:
              cpu: 100m
              memory: 256Mi
            limits:
              cpu: 500m
              memory: 512Mi
          livenessProbe:
            exec:
              command: ["redis-cli", "ping"]
            initialDelaySeconds: 10
            periodSeconds: 10
          readinessProbe:
            exec:
              command: ["redis-cli", "ping"]
            initialDelaySeconds: 5
            periodSeconds: 5
---
# k8s/base/redis/service.yaml
apiVersion: v1
kind: Service
metadata:
  name: redis
  namespace: deployforge-prod
  labels:
    app: redis
spec:
  type: ClusterIP
  ports:
    - port: 6379
      targetPort: 6379
      name: redis
  selector:
    app: redis
```

```bash
kubectl apply -f k8s/base/redis/
# → deployment.apps/redis created
# → service/redis created

kubectl wait --namespace deployforge-prod \
  --for=condition=ready pod \
  --selector=app=redis \
  --timeout=60s
# → pod/redis-xxxxx condition met
```

#### Step 6: Network Policies for Stateful Services

```yaml
# k8s/base/network-policies/allow-database-access.yaml (Module 06)
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: allow-postgres-from-app
  namespace: deployforge-prod
spec:
  podSelector:
    matchLabels:
      app: postgres
  policyTypes:
    - Ingress
  ingress:
    - from:
        - podSelector:
            matchLabels:
              tier: application    # Only app-tier pods can reach PostgreSQL
      ports:
        - protocol: TCP
          port: 5432
---
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: allow-redis-from-app
  namespace: deployforge-prod
spec:
  podSelector:
    matchLabels:
      app: redis
  policyTypes:
    - Ingress
  ingress:
    - from:
        - podSelector:
            matchLabels:
              tier: application
      ports:
        - protocol: TCP
          port: 6379
```

```bash
kubectl apply -f k8s/base/network-policies/
# → networkpolicy.networking.k8s.io/allow-postgres-from-app created
# → networkpolicy.networking.k8s.io/allow-redis-from-app created
```

### Phase 3: Deploy Application Services

With databases running, deploy the API Gateway and Worker Service.

#### Step 7: Deploy the API Gateway

```yaml
# k8s/base/api-gateway/deployment.yaml — API Gateway (Module 05)
apiVersion: apps/v1
kind: Deployment
metadata:
  name: api-gateway
  namespace: deployforge-prod
  labels:
    app: api-gateway
    tier: application
spec:
  replicas: 3
  selector:
    matchLabels:
      app: api-gateway
  template:
    metadata:
      labels:
        app: api-gateway
        tier: application
      annotations:
        prometheus.io/scrape: "true"     # Module 08: Prometheus scraping
        prometheus.io/port: "3000"
        prometheus.io/path: "/metrics"
    spec:
      serviceAccountName: api-gateway
      securityContext:
        runAsNonRoot: true
        runAsUser: 1000
      containers:
        - name: api-gateway
          image: deployforge/api-gateway:v1.0.0  # ← Updated by CI/CD
          ports:
            - containerPort: 3000
              name: http
          env:
            - name: NODE_ENV
              value: production
            - name: PORT
              value: "3000"
            - name: DATABASE_URL
              valueFrom:
                secretKeyRef:
                  name: api-gateway-config
                  key: database-url
            - name: REDIS_URL
              value: "redis://redis:6379"
            - name: LOG_LEVEL
              value: "info"
            - name: OTEL_EXPORTER_OTLP_ENDPOINT   # Module 08: tracing
              value: "http://otel-collector.monitoring:4318"
          resources:
            requests:
              cpu: 200m
              memory: 256Mi
            limits:
              cpu: "1"
              memory: 512Mi
          livenessProbe:
            httpGet:
              path: /health/live
              port: http
            initialDelaySeconds: 15
            periodSeconds: 15
            failureThreshold: 3
          readinessProbe:
            httpGet:
              path: /health/ready
              port: http
            initialDelaySeconds: 5
            periodSeconds: 5
            failureThreshold: 3
          startupProbe:               # Module 05: startup probe for slow starts
            httpGet:
              path: /health/live
              port: http
            failureThreshold: 30
            periodSeconds: 2
---
# k8s/base/api-gateway/service.yaml
apiVersion: v1
kind: Service
metadata:
  name: api-gateway
  namespace: deployforge-prod
  labels:
    app: api-gateway
spec:
  type: ClusterIP
  ports:
    - port: 3000
      targetPort: 3000
      name: http
  selector:
    app: api-gateway
---
# k8s/base/api-gateway/serviceaccount.yaml
apiVersion: v1
kind: ServiceAccount
metadata:
  name: api-gateway
  namespace: deployforge-prod
  labels:
    app: api-gateway
```

#### Step 8: Deploy the Worker Service

```yaml
# k8s/base/worker/deployment.yaml — Worker Service (Module 05)
apiVersion: apps/v1
kind: Deployment
metadata:
  name: worker
  namespace: deployforge-prod
  labels:
    app: worker
    tier: application
spec:
  replicas: 2
  selector:
    matchLabels:
      app: worker
  template:
    metadata:
      labels:
        app: worker
        tier: application
      annotations:
        prometheus.io/scrape: "true"
        prometheus.io/port: "9090"
        prometheus.io/path: "/metrics"
    spec:
      serviceAccountName: worker
      securityContext:
        runAsNonRoot: true
        runAsUser: 1000
      containers:
        - name: worker
          image: deployforge/worker:v1.0.0
          ports:
            - containerPort: 9090
              name: metrics
          env:
            - name: NODE_ENV
              value: production
            - name: REDIS_URL
              value: "redis://redis:6379"
            - name: DATABASE_URL
              valueFrom:
                secretKeyRef:
                  name: worker-config
                  key: database-url
            - name: CONCURRENCY
              value: "5"
            - name: OTEL_EXPORTER_OTLP_ENDPOINT
              value: "http://otel-collector.monitoring:4318"
          resources:
            requests:
              cpu: 100m
              memory: 256Mi
            limits:
              cpu: 500m
              memory: 512Mi
          livenessProbe:
            httpGet:
              path: /health
              port: metrics
            initialDelaySeconds: 10
            periodSeconds: 15
---
# k8s/base/worker/service.yaml
apiVersion: v1
kind: Service
metadata:
  name: worker
  namespace: deployforge-prod
  labels:
    app: worker
spec:
  type: ClusterIP
  ports:
    - port: 9090
      targetPort: 9090
      name: metrics
  selector:
    app: worker
---
apiVersion: v1
kind: ServiceAccount
metadata:
  name: worker
  namespace: deployforge-prod
```

```bash
# Create application secrets
kubectl create secret generic api-gateway-config \
  --namespace deployforge-prod \
  --from-literal=database-url="postgresql://deployforge:$(kubectl get secret postgres-credentials -n deployforge-prod -o jsonpath='{.data.password}' | base64 -d)@postgres:5432/deployforge"

kubectl create secret generic worker-config \
  --namespace deployforge-prod \
  --from-literal=database-url="postgresql://deployforge:$(kubectl get secret postgres-credentials -n deployforge-prod -o jsonpath='{.data.password}' | base64 -d)@postgres:5432/deployforge"

# Deploy application services
kubectl apply -f k8s/base/api-gateway/
kubectl apply -f k8s/base/worker/
# → deployment.apps/api-gateway created
# → service/api-gateway created
# → serviceaccount/api-gateway created
# → deployment.apps/worker created
# → service/worker created
# → serviceaccount/worker created

# Wait for all pods
kubectl wait --namespace deployforge-prod \
  --for=condition=ready pod \
  --selector=tier=application \
  --timeout=120s

# Verify all pods are running
kubectl get pods -n deployforge-prod
# → NAME                           READY   STATUS    RESTARTS   AGE
# → api-gateway-xxxxx-yyyyy        1/1     Running   0          45s
# → api-gateway-xxxxx-zzzzz        1/1     Running   0          45s
# → api-gateway-xxxxx-wwwww        1/1     Running   0          45s
# → postgres-0                     1/1     Running   0          3m
# → redis-xxxxx-yyyyy              1/1     Running   0          2m
# → worker-xxxxx-yyyyy             1/1     Running   0          40s
# → worker-xxxxx-zzzzz             1/1     Running   0          40s
```

#### Step 9: Configure Ingress

```yaml
# k8s/base/ingress/ingress.yaml — Ingress rule (Module 06)
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: deployforge-ingress
  namespace: deployforge-prod
  annotations:
    nginx.ingress.kubernetes.io/rate-limit: "100"
    nginx.ingress.kubernetes.io/rate-limit-window: "1m"
    nginx.ingress.kubernetes.io/proxy-body-size: "10m"
spec:
  ingressClassName: nginx
  rules:
    - host: deployforge.local
      http:
        paths:
          - path: /api
            pathType: Prefix
            backend:
              service:
                name: api-gateway
                port:
                  number: 3000
          - path: /
            pathType: Prefix
            backend:
              service:
                name: nginx-frontend
                port:
                  number: 80
```

```bash
kubectl apply -f k8s/base/ingress/
# → ingress.networking.k8s.io/deployforge-ingress created

# Add local DNS entry
echo "127.0.0.1 deployforge.local" | sudo tee -a /etc/hosts

# Test connectivity
curl -s http://deployforge.local/api/health | jq
# → { "status": "ok", "version": "1.0.0", "uptime": 45 }
```

### Phase 4: Deploy Observability Stack

#### Step 10: Install Prometheus + Grafana via Helm

```bash
# Add Helm repos (Module 08)
helm repo add prometheus-community https://prometheus-community.github.io/helm-charts
helm repo add grafana https://grafana.github.io/helm-charts
helm repo update

# Install kube-prometheus-stack (Prometheus + Grafana + Alertmanager)
helm install monitoring prometheus-community/kube-prometheus-stack \
  --namespace monitoring \
  --set prometheus.prometheusSpec.serviceMonitorSelectorNilUsesHelmValues=false \
  --set prometheus.prometheusSpec.podMonitorSelectorNilUsesHelmValues=false \
  --set grafana.adminPassword=admin \
  --set grafana.sidecar.dashboards.enabled=true \
  --set grafana.sidecar.dashboards.label=grafana_dashboard \
  --wait --timeout 5m
# → NAME: monitoring
# → STATUS: deployed

# Verify Prometheus is scraping targets
kubectl port-forward -n monitoring svc/monitoring-kube-prometheus-prometheus 9090:9090 &
curl -s http://localhost:9090/api/v1/targets | jq '.data.activeTargets | length'
# → 15  (approximate — depends on cluster configuration)
```

#### Step 11: Deploy ServiceMonitors for DeployForge

```yaml
# monitoring/servicemonitors/api-gateway-monitor.yaml (Module 08)
apiVersion: monitoring.coreos.com/v1
kind: ServiceMonitor
metadata:
  name: api-gateway
  namespace: monitoring
  labels:
    app: api-gateway
spec:
  namespaceSelector:
    matchNames:
      - deployforge-prod
  selector:
    matchLabels:
      app: api-gateway
  endpoints:
    - port: http
      path: /metrics
      interval: 15s
---
# monitoring/servicemonitors/worker-monitor.yaml
apiVersion: monitoring.coreos.com/v1
kind: ServiceMonitor
metadata:
  name: worker
  namespace: monitoring
  labels:
    app: worker
spec:
  namespaceSelector:
    matchNames:
      - deployforge-prod
  selector:
    matchLabels:
      app: worker
  endpoints:
    - port: metrics
      path: /metrics
      interval: 15s
```

```bash
kubectl apply -f monitoring/servicemonitors/
# → servicemonitor.monitoring.coreos.com/api-gateway created
# → servicemonitor.monitoring.coreos.com/worker created

# Verify targets appear in Prometheus (may take ~30s)
curl -s http://localhost:9090/api/v1/targets \
  | jq '.data.activeTargets[] | select(.labels.job == "api-gateway") | .health'
# → "up"
```

#### Step 12: Deploy SLO Recording Rules and Alerts

```yaml
# monitoring/alerts/slo-burn-rate.yaml — Multi-burn-rate alerts (Module 09)
apiVersion: monitoring.coreos.com/v1
kind: PrometheusRule
metadata:
  name: deployforge-slo-alerts
  namespace: monitoring
  labels:
    role: alert-rules
spec:
  groups:
    - name: deployforge-slo-availability
      interval: 30s
      rules:
        # Recording rule: availability SLI
        - record: deployforge:api_availability:ratio_rate5m
          expr: |
            1 - (
              sum(rate(http_requests_total{namespace="deployforge-prod",service="api-gateway",code=~"5.."}[5m]))
              /
              sum(rate(http_requests_total{namespace="deployforge-prod",service="api-gateway"}[5m]))
            )

        # Recording rule: 1-hour error rate
        - record: deployforge:api_error_rate:ratio_rate1h
          expr: |
            sum(rate(http_requests_total{namespace="deployforge-prod",service="api-gateway",code=~"5.."}[1h]))
            /
            sum(rate(http_requests_total{namespace="deployforge-prod",service="api-gateway"}[1h]))

        # Recording rule: 6-hour error rate
        - record: deployforge:api_error_rate:ratio_rate6h
          expr: |
            sum(rate(http_requests_total{namespace="deployforge-prod",service="api-gateway",code=~"5.."}[6h]))
            /
            sum(rate(http_requests_total{namespace="deployforge-prod",service="api-gateway"}[6h]))

        # Critical: 14.4× burn rate over 1 hour (pages immediately)
        - alert: DeployForgeAPIHighBurnRate_Critical
          expr: |
            deployforge:api_error_rate:ratio_rate1h > (14.4 * 0.001)
            and
            deployforge:api_error_rate:ratio_rate5m > (14.4 * 0.001)
          for: 2m
          labels:
            severity: critical
            service: deployforge-api
          annotations:
            summary: "DeployForge API burning error budget at 14.4× rate"
            description: "Error rate {{ $value | humanizePercentage }} over 1h — budget will be exhausted in ~2.5 days at this rate."
            runbook_url: "https://deployforge.internal/runbooks/api-high-error-rate"

        # Warning: 6× burn rate over 6 hours (pages within 30min)
        - alert: DeployForgeAPIHighBurnRate_Warning
          expr: |
            deployforge:api_error_rate:ratio_rate6h > (6 * 0.001)
            and
            deployforge:api_error_rate:ratio_rate1h > (6 * 0.001)
          for: 5m
          labels:
            severity: warning
            service: deployforge-api
          annotations:
            summary: "DeployForge API burning error budget at 6× rate"
            description: "Error rate {{ $value | humanizePercentage }} over 6h — investigate before budget exhaustion."

        # Ticket: 1× burn rate over 3 days (creates a ticket, no page)
        - alert: DeployForgeAPIBudgetConsumption_Ticket
          expr: |
            deployforge:api_error_rate:ratio_rate6h > (1 * 0.001)
          for: 1h
          labels:
            severity: info
            service: deployforge-api
          annotations:
            summary: "DeployForge API consuming error budget at normal+ rate"
            description: "Sustained error rate above SLO target. Track in next sprint."
```

```bash
kubectl apply -f monitoring/alerts/
# → prometheusrule.monitoring.coreos.com/deployforge-slo-alerts created

# Verify rules are loaded
curl -s http://localhost:9090/api/v1/rules \
  | jq '.data.groups[] | select(.name == "deployforge-slo-availability") | .rules | length'
# → 6
```

#### Step 13: Import Grafana Dashboards

```bash
# Create ConfigMap for Grafana dashboard auto-import (Module 08)
kubectl create configmap deployforge-overview-dashboard \
  --namespace monitoring \
  --from-file=monitoring/dashboards/overview.json \
  --dry-run=client -o yaml \
  | kubectl label --local -f - grafana_dashboard=1 -o yaml \
  | kubectl apply -f -
# → configmap/deployforge-overview-dashboard created

# Access Grafana
kubectl port-forward -n monitoring svc/monitoring-grafana 3001:80 &
echo "Grafana available at http://localhost:3001 (admin/admin)"
```

### Phase 5: Configure GitOps with ArgoCD

#### Step 14: Install ArgoCD

```bash
# Install ArgoCD (Module 10)
kubectl apply -n argocd -f https://raw.githubusercontent.com/argoproj/argo-cd/stable/manifests/install.yaml

kubectl wait --namespace argocd \
  --for=condition=ready pod \
  --selector=app.kubernetes.io/name=argocd-server \
  --timeout=180s

# Get initial admin password
ARGOCD_PASSWORD=$(kubectl -n argocd get secret argocd-initial-admin-secret \
  -o jsonpath="{.data.password}" | base64 -d)
echo "ArgoCD password: $ARGOCD_PASSWORD"

# Port-forward ArgoCD UI
kubectl port-forward -n argocd svc/argocd-server 8080:443 &
echo "ArgoCD available at https://localhost:8080"
```

#### Step 15: Create the ArgoCD Application

```yaml
# deploy/argocd/application.yaml — DeployForge ArgoCD Application (Module 10)
apiVersion: argoproj.io/v1alpha1
kind: Application
metadata:
  name: deployforge-prod
  namespace: argocd
  finalizers:
    - resources-finalizer.argocd.argoproj.io
spec:
  project: default
  source:
    repoURL: https://github.com/your-org/deployforge.git
    targetRevision: main
    path: k8s/overlays/prod      # Kustomize overlay (Module 07)
  destination:
    server: https://kubernetes.default.svc
    namespace: deployforge-prod
  syncPolicy:
    automated:
      prune: true                # Delete resources removed from Git
      selfHeal: true             # Correct manual drift
      allowEmpty: false
    syncOptions:
      - CreateNamespace=false
      - PrunePropagationPolicy=foreground
      - PruneLast=true
    retry:
      limit: 5
      backoff:
        duration: 5s
        factor: 2
        maxDuration: 3m
```

```bash
kubectl apply -f deploy/argocd/application.yaml
# → application.argoproj.io/deployforge-prod created

# Check sync status
argocd app get deployforge-prod --grpc-web
# → Name:               deployforge-prod
# → Server:             https://kubernetes.default.svc
# → Namespace:          deployforge-prod
# → Sync Status:        Synced
# → Health Status:      Healthy
```

### Phase 6: Configure Progressive Delivery

#### Step 16: Install Argo Rollouts

```bash
# Install Argo Rollouts (Module 10)
kubectl create namespace argo-rollouts
kubectl apply -n argo-rollouts -f https://github.com/argoproj/argo-rollouts/releases/latest/download/install.yaml

kubectl wait --namespace argo-rollouts \
  --for=condition=ready pod \
  --selector=app.kubernetes.io/name=argo-rollouts \
  --timeout=120s
```

#### Step 17: Convert API Gateway to Argo Rollout

```yaml
# deploy/rollouts/api-gateway-rollout.yaml — Canary deployment (Module 10)
apiVersion: argoproj.io/v1alpha1
kind: Rollout
metadata:
  name: api-gateway
  namespace: deployforge-prod
  labels:
    app: api-gateway
    tier: application
spec:
  replicas: 3
  revisionHistoryLimit: 3
  selector:
    matchLabels:
      app: api-gateway
  template:
    metadata:
      labels:
        app: api-gateway
        tier: application
      annotations:
        prometheus.io/scrape: "true"
        prometheus.io/port: "3000"
        prometheus.io/path: "/metrics"
    spec:
      serviceAccountName: api-gateway
      securityContext:
        runAsNonRoot: true
        runAsUser: 1000
      containers:
        - name: api-gateway
          image: deployforge/api-gateway:v1.0.0
          ports:
            - containerPort: 3000
              name: http
          env:
            - name: NODE_ENV
              value: production
            - name: PORT
              value: "3000"
            - name: DATABASE_URL
              valueFrom:
                secretKeyRef:
                  name: api-gateway-config
                  key: database-url
            - name: REDIS_URL
              value: "redis://redis:6379"
          resources:
            requests:
              cpu: 200m
              memory: 256Mi
            limits:
              cpu: "1"
              memory: 512Mi
          livenessProbe:
            httpGet:
              path: /health/live
              port: http
            initialDelaySeconds: 15
            periodSeconds: 15
          readinessProbe:
            httpGet:
              path: /health/ready
              port: http
            initialDelaySeconds: 5
            periodSeconds: 5
  strategy:
    canary:
      canaryService: api-gateway-canary
      stableService: api-gateway-stable
      trafficRouting:
        nginx:
          stableIngress: deployforge-ingress
      steps:
        - setWeight: 10            # 10% traffic to canary
        - pause: { duration: 2m }  # Let metrics accumulate
        - analysis:                # Automated health check
            templates:
              - templateName: api-gateway-success-rate
            args:
              - name: service-name
                value: api-gateway-canary
        - setWeight: 50            # 50% traffic if analysis passes
        - pause: { duration: 3m }
        - analysis:
            templates:
              - templateName: api-gateway-success-rate
            args:
              - name: service-name
                value: api-gateway-canary
        - setWeight: 100           # Full promotion
      analysis:
        successfulRunHistoryLimit: 3
        unsuccessfulRunHistoryLimit: 3
---
# deploy/rollouts/analysis-template.yaml — Canary analysis (Module 10)
apiVersion: argoproj.io/v1alpha1
kind: AnalysisTemplate
metadata:
  name: api-gateway-success-rate
  namespace: deployforge-prod
spec:
  args:
    - name: service-name
  metrics:
    - name: success-rate
      interval: 30s
      count: 3
      successCondition: result[0] >= 0.99
      failureLimit: 1
      provider:
        prometheus:
          address: http://monitoring-kube-prometheus-prometheus.monitoring:9090
          query: |
            sum(rate(http_requests_total{
              namespace="deployforge-prod",
              service="{{args.service-name}}",
              code!~"5.."
            }[2m]))
            /
            sum(rate(http_requests_total{
              namespace="deployforge-prod",
              service="{{args.service-name}}"
            }[2m]))
    - name: p99-latency
      interval: 30s
      count: 3
      successCondition: result[0] <= 0.5
      failureLimit: 1
      provider:
        prometheus:
          address: http://monitoring-kube-prometheus-prometheus.monitoring:9090
          query: |
            histogram_quantile(0.99,
              sum(rate(http_request_duration_seconds_bucket{
                namespace="deployforge-prod",
                service="{{args.service-name}}"
              }[2m])) by (le)
            )
```

```bash
# Create canary and stable services
cat <<'EOF' | kubectl apply -f -
apiVersion: v1
kind: Service
metadata:
  name: api-gateway-canary
  namespace: deployforge-prod
spec:
  ports:
    - port: 3000
      targetPort: 3000
  selector:
    app: api-gateway
---
apiVersion: v1
kind: Service
metadata:
  name: api-gateway-stable
  namespace: deployforge-prod
spec:
  ports:
    - port: 3000
      targetPort: 3000
  selector:
    app: api-gateway
EOF

# Apply the Rollout (replaces the Deployment)
kubectl delete deployment api-gateway -n deployforge-prod --ignore-not-found
kubectl apply -f deploy/rollouts/
# → rollout.argoproj.io/api-gateway created
# → analysistemplate.argoproj.io/api-gateway-success-rate created

# Watch the rollout status
kubectl argo rollouts get rollout api-gateway -n deployforge-prod --watch
# → Name:            api-gateway
# → Namespace:       deployforge-prod
# → Status:          ✔ Healthy
# → Strategy:        Canary
# →   Step:          7/7
# →   SetWeight:     100
# →   ActualWeight:  100
```

### Phase 7: Configure Auto-Scaling

#### Step 18: Apply HPA for API Gateway

```yaml
# k8s/scaling/hpa-api.yaml — HPA with custom metrics (Module 12)
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: api-gateway-hpa
  namespace: deployforge-prod
spec:
  scaleTargetRef:
    apiVersion: argoproj.io/v1alpha1
    kind: Rollout                  # Targets the Rollout, not Deployment
    name: api-gateway
  minReplicas: 3
  maxReplicas: 10
  metrics:
    - type: Pods
      pods:
        metric:
          name: http_requests_per_second
        target:
          type: AverageValue
          averageValue: "100"       # Scale up when avg RPS exceeds 100
    - type: Resource
      resource:
        name: cpu
        target:
          type: Utilization
          averageUtilization: 70
  behavior:
    scaleUp:
      stabilizationWindowSeconds: 30
      policies:
        - type: Percent
          value: 50                 # Add up to 50% more pods per scale event
          periodSeconds: 60
    scaleDown:
      stabilizationWindowSeconds: 300  # Wait 5min before scaling down
      policies:
        - type: Percent
          value: 25
          periodSeconds: 120
```

```bash
kubectl apply -f k8s/scaling/hpa-api.yaml
# → horizontalpodautoscaler.autoscaling/api-gateway-hpa created

kubectl get hpa -n deployforge-prod
# → NAME              REFERENCE              TARGETS         MINPODS   MAXPODS   REPLICAS   AGE
# → api-gateway-hpa   Rollout/api-gateway    <unknown>/100   3         10        3          10s
```

### Phase 8: Verify the Complete Stack

#### Step 19: End-to-End Health Check

```bash
#!/bin/bash
# scripts/verify-deployment.sh — Full stack verification

echo "=== DeployForge End-to-End Verification ==="
echo ""

# 1. Kubernetes cluster health
echo "--- Cluster Health ---"
kubectl get nodes -o wide
echo ""

# 2. All pods running
echo "--- Pod Status ---"
kubectl get pods -n deployforge-prod -o wide
echo ""

# 3. Services reachable
echo "--- Service Endpoints ---"
kubectl get endpoints -n deployforge-prod
echo ""

# 4. API health check
echo "--- API Health ---"
kubectl port-forward -n deployforge-prod svc/api-gateway 3000:3000 &
PF_PID=$!
sleep 2
curl -s http://localhost:3000/health | jq
kill $PF_PID 2>/dev/null
echo ""

# 5. Prometheus targets
echo "--- Prometheus Targets ---"
kubectl port-forward -n monitoring svc/monitoring-kube-prometheus-prometheus 9090:9090 &
PF_PID=$!
sleep 2
TARGETS=$(curl -s http://localhost:9090/api/v1/targets | jq '.data.activeTargets | length')
echo "Active scrape targets: $TARGETS"
kill $PF_PID 2>/dev/null
echo ""

# 6. ArgoCD sync status
echo "--- ArgoCD Status ---"
argocd app get deployforge-prod --grpc-web -o json | jq '{syncStatus: .status.sync.status, healthStatus: .status.health.status}'
echo ""

# 7. HPA status
echo "--- HPA Status ---"
kubectl get hpa -n deployforge-prod
echo ""

# 8. Rollout status
echo "--- Rollout Status ---"
kubectl argo rollouts status api-gateway -n deployforge-prod
echo ""

echo "=== Verification Complete ==="
```

```bash
chmod +x scripts/verify-deployment.sh
./scripts/verify-deployment.sh
# → === DeployForge End-to-End Verification ===
# → --- Cluster Health ---
# → NAME                       STATUS   ROLES           AGE   VERSION
# → deployforge-control-plane  Ready    control-plane   15m   v1.29.0
# → ...
# → --- Pod Status ---
# → NAME                           READY   STATUS    RESTARTS   AGE
# → api-gateway-xxxxx              1/1     Running   0          10m
# → ...
# → --- API Health ---
# → { "status": "ok", "version": "1.0.0" }
# → --- Prometheus Targets ---
# → Active scrape targets: 17
# → --- ArgoCD Status ---
# → { "syncStatus": "Synced", "healthStatus": "Healthy" }
# → --- HPA Status ---
# → NAME              REFERENCE              TARGETS   MINPODS   MAXPODS   REPLICAS
# → api-gateway-hpa   Rollout/api-gateway    12/100    3         10        3
# → --- Rollout Status ---
# → Healthy
# → === Verification Complete ===
```

### Phase 9: Simulate a Code Change End-to-End

#### Step 20: Push a Change and Watch the Pipeline

This is the moment of truth: a code change flowing through the entire pipeline.

```bash
# 1. Simulate a code change — update the API version
#    (In a real setup, this would be a git push that triggers CI)

# 2. Build a new image (simulating CI)
docker build -t deployforge/api-gateway:v1.1.0 -f docker/api-gateway/Dockerfile .
kind load docker-image deployforge/api-gateway:v1.1.0 --name deployforge

# 3. Update the Kustomize image tag (simulating CI updating the GitOps repo)
cd k8s/overlays/prod
kustomize edit set image deployforge/api-gateway=deployforge/api-gateway:v1.1.0
cd -

# 4. Commit and push (triggers ArgoCD sync)
git add k8s/overlays/prod/kustomization.yaml
git commit -m "chore: bump api-gateway to v1.1.0"
git push origin main

# 5. Watch ArgoCD detect the change and sync
argocd app get deployforge-prod --grpc-web --refresh
# → Sync Status: OutOfSync → Syncing → Synced

# 6. Watch the canary rollout
kubectl argo rollouts get rollout api-gateway -n deployforge-prod --watch
# → Name:            api-gateway
# → Status:          ॥ Paused
# → Strategy:        Canary
# →   Step:          1/7
# →   SetWeight:     10
# →   ActualWeight:  10
# →
# → (waits 2 minutes, then runs AnalysisTemplate...)
# →
# → Status:          ✔ Healthy
# → Strategy:        Canary
# →   Step:          7/7
# →   SetWeight:     100
# →   ActualWeight:  100

# 7. Verify the new version is live
curl -s http://deployforge.local/api/health | jq .version
# → "1.1.0"
```

---

## Code Examples

### Complete Kustomization File for Production

```yaml
# k8s/overlays/prod/kustomization.yaml — Production overlay (Module 07)
apiVersion: kustomize.config.k8s.io/v1beta1
kind: Kustomization

namespace: deployforge-prod

resources:
  - ../../base/postgres
  - ../../base/redis
  - ../../base/api-gateway
  - ../../base/worker
  - ../../base/ingress
  - ../../base/network-policies

patches:
  - path: patches/api-gateway-replicas.yaml
  - path: patches/resource-overrides.yaml

images:
  - name: deployforge/api-gateway
    newTag: v1.0.0                    # ← Updated by CI pipeline
  - name: deployforge/worker
    newTag: v1.0.0

labels:
  - pairs:
      environment: production
      managed-by: kustomize
```

### CI Pipeline Snippet — Image Tag Update

```yaml
# .github/workflows/ci.yml — Update GitOps repo after build (Module 10)
update-gitops:
  needs: [build-and-push, scan]
  runs-on: ubuntu-latest
  if: github.ref == 'refs/heads/main'
  steps:
    - uses: actions/checkout@v4

    - name: Update Kustomize image tag
      run: |
        cd k8s/overlays/prod
        kustomize edit set image \
          deployforge/api-gateway=${{ env.REGISTRY }}/${{ env.IMAGE_NAME }}:sha-${{ github.sha }}

    - name: Commit and push
      run: |
        git config user.name "github-actions[bot]"
        git config user.email "github-actions[bot]@users.noreply.github.com"
        git add k8s/overlays/prod/kustomization.yaml
        git commit -m "chore: update api-gateway to sha-${{ github.sha }}"
        git push
```

---

## Try It Yourself

### Challenge 1: Add a Database Migration Job

DeployForge needs to run database migrations before the new version of the API Gateway starts serving traffic. Create a Kubernetes Job that:
1. Runs as a pre-sync hook in ArgoCD (so it executes before the Rollout updates)
2. Uses the same database credentials as the API Gateway
3. Has a backoff limit of 3 and a TTL of 10 minutes

<details>
<summary>Show solution</summary>

```yaml
# k8s/base/migrations/migration-job.yaml
apiVersion: batch/v1
kind: Job
metadata:
  name: deployforge-migration
  namespace: deployforge-prod
  annotations:
    argocd.argoproj.io/hook: PreSync       # Runs before main sync
    argocd.argoproj.io/hook-delete-policy: BeforeHookCreation
spec:
  backoffLimit: 3
  ttlSecondsAfterFinished: 600    # Auto-cleanup after 10 minutes
  template:
    spec:
      restartPolicy: Never
      securityContext:
        runAsNonRoot: true
        runAsUser: 1000
      containers:
        - name: migrate
          image: deployforge/api-gateway:v1.0.0  # Same image, different entrypoint
          command: ["node", "dist/migrate.js"]
          env:
            - name: DATABASE_URL
              valueFrom:
                secretKeyRef:
                  name: api-gateway-config
                  key: database-url
          resources:
            requests:
              cpu: 100m
              memory: 128Mi
            limits:
              cpu: 500m
              memory: 256Mi
```

Verify:
```bash
kubectl apply -f k8s/base/migrations/migration-job.yaml
kubectl wait --for=condition=complete job/deployforge-migration -n deployforge-prod --timeout=60s
# → job.batch/deployforge-migration condition met

kubectl logs job/deployforge-migration -n deployforge-prod
# → Running migrations...
# → Migration 001_create_deployments: OK
# → Migration 002_add_status_column: OK
# → All migrations complete.
```

</details>

### Challenge 2: Simulate a Failed Canary and Verify Rollback

1. Build a "bad" image that returns 500 errors on 20% of requests
2. Trigger a canary rollout with this image
3. Watch the AnalysisTemplate detect the failure
4. Verify the rollout automatically aborts and reverts to the stable version

<details>
<summary>Show solution</summary>

```bash
# 1. Build and load a faulty image
# (Simulate by adding a route that returns 500 randomly)
docker build -t deployforge/api-gateway:v1.2.0-bad -f docker/api-gateway/Dockerfile.bad .
kind load docker-image deployforge/api-gateway:v1.2.0-bad --name deployforge

# 2. Update the image tag to trigger a rollout
cd k8s/overlays/prod
kustomize edit set image deployforge/api-gateway=deployforge/api-gateway:v1.2.0-bad
cd -

kubectl argo rollouts set image api-gateway \
  api-gateway=deployforge/api-gateway:v1.2.0-bad \
  -n deployforge-prod

# 3. Watch the rollout — it should pause at 10%, run analysis, and fail
kubectl argo rollouts get rollout api-gateway -n deployforge-prod --watch
# → Status:          ✖ Degraded
# → Strategy:        Canary
# →   Step:          2/7
# →   SetWeight:     10
# → AnalysisRun:     api-gateway-xxxxx - ✖ Failed
# → Message:         RollbackSuccessful: Rollout is aborted and has been rolled back

# 4. Verify stable version is still serving
curl -s http://deployforge.local/api/health | jq .version
# → "1.1.0"  (previous stable version, not v1.2.0-bad)

# 5. Check the AnalysisRun for details
kubectl argo rollouts get rollout api-gateway -n deployforge-prod
# → Shows analysis failure reason: success-rate < 0.99
```

</details>

---

## Capstone Connection

**DeployForge** is now fully deployed end-to-end:

- **Infrastructure layer** — A multi-node `kind` cluster with Ingress, metrics-server, namespaces, RBAC, ResourceQuotas, and NetworkPolicies. Every component from Modules 04–07 is wired together.
- **Application layer** — API Gateway (Express/TypeScript) and Worker Service (BullMQ) running as Kubernetes workloads with proper health checks, security contexts, and resource limits. Stateful services (PostgreSQL, Redis) run with persistent storage and access-controlled network policies.
- **Observability layer** — Prometheus scrapes application metrics via ServiceMonitors. Grafana dashboards visualize service health and SLO budget consumption. Multi-burn-rate alerts fire when error budget consumption warrants attention. OpenTelemetry tracing connects requests across services.
- **Delivery layer** — ArgoCD watches the Git repository and auto-syncs the production overlay. Argo Rollouts executes canary deployments with Prometheus-backed AnalysisTemplates. Failed canaries automatically roll back. The CI pipeline builds, scans, and updates the GitOps image tag.
- **Scaling layer** — HPA scales the API Gateway based on request rate. ResourceQuotas prevent namespace budget overruns. PriorityClasses protect critical workloads.

In [03-operational-excellence.md](03-operational-excellence.md), you'll learn what happens after the deploy succeeds — day-2 operations, upgrades, disaster recovery, and the practices that keep this system running for months and years.
