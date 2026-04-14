# 7.4 — Kustomize: Overlay-Based Configuration

## Concept

Kustomize takes a fundamentally different approach from Helm: instead of templating YAML with Go syntax, you start with plain, valid Kubernetes manifests (the "base") and layer environment-specific modifications on top (the "overlays"). There's no templating language, no value injection, no rendered output to debug — just standard YAML with declarative patches.

This "overlay" model has a powerful benefit: your base manifests are real Kubernetes resources you can `kubectl apply` directly. Overlays add, modify, or delete fields without touching the base. The result is easier to review in PRs, harder to break with syntax errors, and natively supported by `kubectl` (no Helm binary needed). Kustomize is built into `kubectl -k` since Kubernetes 1.14.

The tradeoff? Kustomize has no release management, no rollback, no hooks, and no package repository. It excels at environment configuration, not application distribution. Many production teams use both: Helm for packaging and distribution, Kustomize for last-mile environment patches.

---

## Deep Dive

### Kustomize Directory Structure

The standard Kustomize layout separates base manifests from per-environment overlays:

```
deployforge-kustomize/
├── base/                              # Shared resources (the "source of truth")
│   ├── kustomization.yaml             # Lists all base resources
│   ├── deployment.yaml                # API Gateway Deployment
│   ├── service.yaml                   # ClusterIP Service
│   ├── configmap.yaml                 # Default ConfigMap
│   ├── statefulset-postgres.yaml      # PostgreSQL StatefulSet
│   ├── statefulset-redis.yaml         # Redis StatefulSet
│   ├── ingress.yaml                   # Base Ingress (may be disabled)
│   └── networkpolicy.yaml             # Base NetworkPolicy
│
├── overlays/
│   ├── dev/                           # Development environment
│   │   ├── kustomization.yaml
│   │   ├── patch-deployment.yaml      # 1 replica, debug logging
│   │   ├── patch-resources.yaml       # Small resource limits
│   │   └── configmap-env.yaml         # Dev-specific config
│   │
│   ├── staging/                       # Staging environment
│   │   ├── kustomization.yaml
│   │   ├── patch-deployment.yaml      # 2 replicas, INFO logging
│   │   ├── patch-resources.yaml       # Medium resource limits
│   │   └── configmap-env.yaml         # Staging-specific config
│   │
│   └── prod/                          # Production environment
│       ├── kustomization.yaml
│       ├── patch-deployment.yaml      # 3 replicas, WARN logging, anti-affinity
│       ├── patch-resources.yaml       # Large resource limits
│       ├── patch-hpa.yaml             # Enable HPA
│       ├── configmap-env.yaml         # Prod-specific config
│       └── external-secret.yaml       # ESO ExternalSecret (prod only)
```

```
┌─────────────────────────────────────────────────────────────────────┐
│                  Kustomize Overlay Model                              │
│                                                                     │
│  base/                                                               │
│  ┌──────────────────────────────────────────────────────────────┐   │
│  │ deployment.yaml    service.yaml    configmap.yaml             │   │
│  │ (1 replica)        (ClusterIP)     (LOG_LEVEL=info)           │   │
│  └──────────────────────────────────────────────────────────────┘   │
│           │                    │                     │               │
│     ┌─────┴───┐          ┌────┴────┐          ┌─────┴───┐          │
│     ▼         ▼          ▼         ▼          ▼         ▼          │
│  overlays/dev/      overlays/staging/     overlays/prod/           │
│  ┌────────────┐     ┌──────────────┐     ┌──────────────┐         │
│  │ replicas: 1│     │ replicas: 2  │     │ replicas: 3  │         │
│  │ LOG=debug  │     │ LOG=info     │     │ LOG=warn     │         │
│  │ cpu: 100m  │     │ cpu: 250m    │     │ cpu: 1000m   │         │
│  │ mem: 128Mi │     │ mem: 512Mi   │     │ mem: 2Gi     │         │
│  └────────────┘     └──────────────┘     │ HPA enabled  │         │
│                                          │ anti-affinity │         │
│                                          │ ESO secrets   │         │
│                                          └──────────────┘         │
│                                                                     │
│  kubectl apply -k overlays/prod/                                    │
│  → base + prod patches = production-ready manifests                 │
└─────────────────────────────────────────────────────────────────────┘
```

---

### The Base: kustomization.yaml

The base `kustomization.yaml` lists all resources that form the foundation:

```yaml
# base/kustomization.yaml
apiVersion: kustomize.config.k8s.io/v1beta1
kind: Kustomization

# Common metadata applied to ALL resources
commonLabels:
  app.kubernetes.io/name: deployforge
  app.kubernetes.io/managed-by: kustomize

# Namespace for all resources (can be overridden in overlay)
namespace: deployforge

# Resources included in the base
resources:
  - deployment.yaml
  - service.yaml
  - configmap.yaml
  - statefulset-postgres.yaml
  - statefulset-redis.yaml
  - networkpolicy.yaml
```

```yaml
# base/deployment.yaml — plain, valid K8s manifest
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
```

```yaml
# base/service.yaml
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
```

```yaml
# base/configmap.yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: deployforge-config
data:
  LOG_LEVEL: "info"
  LOG_FORMAT: "json"
  API_PORT: "3000"
  WORKER_CONCURRENCY: "5"
  DATABASE_POOL_SIZE: "10"
```

---

### Overlays: Patching the Base

Overlays reference the base and apply patches. Kustomize supports two patch strategies:

#### Strategic Merge Patches

Strategic merge patches are partial YAML documents. Kustomize merges them with the base using Kubernetes-aware merge logic (it knows that `containers` is a list keyed by `name`, for example).

```yaml
# overlays/dev/kustomization.yaml
apiVersion: kustomize.config.k8s.io/v1beta1
kind: Kustomization

resources:
  - ../../base                         # Reference the base

# Override namespace
namespace: deployforge-dev

# Name prefix/suffix for isolation
namePrefix: dev-

# Image override (no tag templating needed)
images:
  - name: deployforge/api-gateway
    newTag: dev-latest

# Patches applied on top of base
patches:
  - path: patch-deployment.yaml
    target:
      kind: Deployment
      name: api-gateway
  - path: patch-resources.yaml
    target:
      kind: Deployment
      name: api-gateway

# ConfigMap overlay
configMapGenerator:
  - name: deployforge-config
    behavior: merge                    # Merge with base ConfigMap
    literals:
      - LOG_LEVEL=debug
      - LOG_FORMAT=text
      - FEATURE_DEBUG_MODE=true
```

```yaml
# overlays/dev/patch-deployment.yaml — strategic merge patch
apiVersion: apps/v1
kind: Deployment
metadata:
  name: api-gateway
spec:
  replicas: 1
  template:
    spec:
      containers:
      - name: api
        env:
        - name: NODE_ENV
          value: "development"
```

```yaml
# overlays/dev/patch-resources.yaml — small resources for dev
apiVersion: apps/v1
kind: Deployment
metadata:
  name: api-gateway
spec:
  template:
    spec:
      containers:
      - name: api
        resources:
          requests:
            cpu: 50m
            memory: 64Mi
          limits:
            cpu: 200m
            memory: 256Mi
```

#### JSON Patches (RFC 6902)

JSON patches are more surgical — they specify exact operations (add, remove, replace) at exact paths. Use them when strategic merge patches are too coarse.

```yaml
# overlays/prod/kustomization.yaml
apiVersion: kustomize.config.k8s.io/v1beta1
kind: Kustomization

resources:
  - ../../base
  - hpa.yaml                          # Add HPA (prod only)
  - external-secret.yaml              # Add ExternalSecret (prod only)

namespace: deployforge

images:
  - name: deployforge/api-gateway
    newTag: v1.2.0                     # Pinned version

patches:
  # Strategic merge patch — replicas and anti-affinity
  - path: patch-deployment.yaml
    target:
      kind: Deployment
      name: api-gateway

  # JSON patch — precise resource modification
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

configMapGenerator:
  - name: deployforge-config
    behavior: merge
    literals:
      - LOG_LEVEL=warn
      - LOG_FORMAT=json
      - DATABASE_POOL_SIZE=25
      - WORKER_CONCURRENCY=10

secretGenerator:
  - name: deployforge-api-keys
    literals:
      - JWT_SECRET=PLACEHOLDER
    type: Opaque
```

```yaml
# overlays/prod/patch-deployment.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: api-gateway
spec:
  replicas: 3
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
      containers:
      - name: api
        env:
        - name: NODE_ENV
          value: "production"
```

```yaml
# overlays/prod/hpa.yaml — only in production
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
```

---

### Generators: ConfigMaps and Secrets from Sources

Kustomize can generate ConfigMaps and Secrets and automatically append a content hash to the name. This forces Deployments to roll when config changes — no annotation hacks needed.

```yaml
# kustomization.yaml with generators
configMapGenerator:
  # From literal values
  - name: deployforge-config
    literals:
      - LOG_LEVEL=info
      - API_PORT=3000

  # From a file
  - name: nginx-config
    files:
      - nginx.conf=configs/nginx.conf

  # From an env file
  - name: app-env
    envs:
      - .env.staging

secretGenerator:
  # From literals
  - name: db-credentials
    literals:
      - DB_USER=deployforge_app
      - DB_PASSWORD=change-me
    type: Opaque

  # From files (e.g., TLS certs)
  - name: tls-certs
    files:
      - tls.crt=certs/server.crt
      - tls.key=certs/server.key
    type: kubernetes.io/tls

# Control hash suffix behavior
generatorOptions:
  disableNameSuffixHash: false         # true = no hash suffix (not recommended)
  labels:
    generated-by: kustomize
```

When you build:

```bash
kubectl kustomize overlays/staging/
# Outputs ConfigMap named: deployforge-config-k892m7g5h2
# The hash changes when content changes → triggers Deployment rollout
```

> **Key insight:** The hash suffix is Kustomize's killer feature for ConfigMap/Secret management. It solves the "I updated the ConfigMap but pods didn't restart" problem that plagues teams using plain `kubectl apply`.

---

### Transformers

Transformers modify fields across all resources. Common transformers include:

```yaml
# kustomization.yaml — common transformers
apiVersion: kustomize.config.k8s.io/v1beta1
kind: Kustomization

# Add prefix/suffix to all resource names
namePrefix: prod-
nameSuffix: -v2

# Override namespace for all resources
namespace: deployforge-prod

# Add labels to all resources
commonLabels:
  environment: production
  team: platform

# Add annotations to all resources
commonAnnotations:
  managed-by: kustomize
  owner: platform-team@deployforge.io

# Override container images
images:
  - name: deployforge/api-gateway
    newName: ghcr.io/deployforge/api-gateway    # Registry change
    newTag: v1.2.0                               # Tag change
  - name: deployforge/worker
    newTag: v1.2.0
  - name: postgres
    newName: postgres
    newTag: "15.4-alpine"                        # Pin database version

# Replace replicas for specific resources
replicas:
  - name: api-gateway
    count: 5
  - name: worker
    count: 3
```

---

### Building and Applying

```bash
# Preview rendered output (dry run)
kubectl kustomize overlays/dev/

# Apply directly
kubectl apply -k overlays/dev/

# Apply with server-side apply (recommended for production)
kubectl apply -k overlays/prod/ --server-side

# Diff before applying
kubectl diff -k overlays/staging/

# Delete all resources from an overlay
kubectl delete -k overlays/dev/
```

---

### Kustomize vs Helm: When to Use Each

```
┌────────────────────────────────────────────────────────────────────┐
│                    Kustomize vs Helm                                │
│                                                                    │
│  ┌──────────────────────┐      ┌──────────────────────┐           │
│  │     Kustomize         │      │       Helm            │           │
│  ├──────────────────────┤      ├──────────────────────┤           │
│  │ ✓ Plain YAML          │      │ ✓ Go templating       │           │
│  │ ✓ No new syntax       │      │ ✓ Parameterized       │           │
│  │ ✓ Built into kubectl  │      │ ✓ Package management  │           │
│  │ ✓ Easy to review      │      │ ✓ Release management  │           │
│  │ ✓ Overlay model       │      │ ✓ Rollback support    │           │
│  │                       │      │ ✓ Hooks & lifecycle   │           │
│  │ ✗ No release tracking │      │ ✓ Chart repositories  │           │
│  │ ✗ No rollback         │      │ ✓ Dependencies        │           │
│  │ ✗ No hooks            │      │                       │           │
│  │ ✗ No package registry │      │ ✗ Complex syntax      │           │
│  │ ✗ Limited logic       │      │ ✗ Hard to debug       │           │
│  └──────────────────────┘      │ ✗ Extra binary        │           │
│                                 └──────────────────────┘           │
│                                                                    │
│  Best for:                      Best for:                          │
│  • Environment overlays         • Distributing applications        │
│  • Team-internal config         • Complex parameterization         │
│  • GitOps workflows             • Third-party charts (Prometheus)  │
│  • Simple customization         • Release lifecycle (rollback)     │
│                                                                    │
│  Many teams use BOTH:                                              │
│  helm template → kustomize overlay → kubectl apply                 │
│  (render)        (patch)             (deploy)                      │
└────────────────────────────────────────────────────────────────────┘
```

### Using Helm + Kustomize Together

```yaml
# overlays/prod/kustomization.yaml — post-processing Helm output
apiVersion: kustomize.config.k8s.io/v1beta1
kind: Kustomization

# Use Helm-rendered output as a resource
resources:
  - ../../helm-rendered/               # Output of `helm template`

# Apply Kustomize patches on top
patches:
  - path: patch-prod-annotations.yaml
  - path: patch-monitoring-sidecar.yaml

commonAnnotations:
  prometheus.io/scrape: "true"
  prometheus.io/port: "9090"
```

```bash
# Workflow: Helm render → Kustomize patch → apply
helm template deployforge ./chart \
  --namespace deployforge \
  --values chart/values-prod.yaml \
  --output-dir helm-rendered/

kubectl apply -k overlays/prod/
```

---

## Code Examples

### Complete Kustomize Setup for DeployForge

```yaml
# base/kustomization.yaml
apiVersion: kustomize.config.k8s.io/v1beta1
kind: Kustomization

namespace: deployforge

commonLabels:
  app.kubernetes.io/name: deployforge
  app.kubernetes.io/part-of: deployforge-platform

resources:
  - deployment.yaml
  - service.yaml
  - statefulset-postgres.yaml
  - statefulset-redis.yaml
  - networkpolicy.yaml

configMapGenerator:
  - name: deployforge-config
    literals:
      - LOG_LEVEL=info
      - LOG_FORMAT=json
      - API_PORT=3000
      - WORKER_CONCURRENCY=5
      - DATABASE_POOL_SIZE=10
```

```yaml
# overlays/staging/kustomization.yaml
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
      - op: add
        path: /spec/template/spec/containers/0/env
        value:
          - name: NODE_ENV
            value: staging
```

```bash
# Build and compare environments
echo "=== Dev ==="
kubectl kustomize overlays/dev/ | grep replicas
echo "=== Staging ==="
kubectl kustomize overlays/staging/ | grep replicas
echo "=== Prod ==="
kubectl kustomize overlays/prod/ | grep replicas
```

---

## Try It Yourself

### Challenge 1: Build a Three-Environment Kustomize Setup

Create a base with a Deployment, Service, and ConfigMap. Then create overlays for dev (1 replica, debug logging), staging (2 replicas, info logging), and prod (3 replicas, warn logging). Build all three and compare the output.

<details>
<summary>Show solution</summary>

```bash
#!/bin/bash
set -euo pipefail

WORK_DIR="kustomize-challenge"
mkdir -p "$WORK_DIR"/{base,overlays/{dev,staging,prod}}

echo "=== Step 1: Create base ==="
cat > "$WORK_DIR/base/deployment.yaml" <<'EOF'
apiVersion: apps/v1
kind: Deployment
metadata:
  name: myapp
spec:
  replicas: 1
  selector:
    matchLabels:
      app: myapp
  template:
    metadata:
      labels:
        app: myapp
    spec:
      containers:
      - name: app
        image: nginx:1.25-alpine
        ports:
        - containerPort: 80
        envFrom:
        - configMapRef:
            name: myapp-config
EOF

cat > "$WORK_DIR/base/service.yaml" <<'EOF'
apiVersion: v1
kind: Service
metadata:
  name: myapp
spec:
  selector:
    app: myapp
  ports:
  - port: 80
    targetPort: 80
EOF

cat > "$WORK_DIR/base/kustomization.yaml" <<'EOF'
apiVersion: kustomize.config.k8s.io/v1beta1
kind: Kustomization
resources:
  - deployment.yaml
  - service.yaml
configMapGenerator:
  - name: myapp-config
    literals:
      - LOG_LEVEL=info
      - API_PORT=80
EOF

echo "=== Step 2: Create dev overlay ==="
cat > "$WORK_DIR/overlays/dev/kustomization.yaml" <<'EOF'
apiVersion: kustomize.config.k8s.io/v1beta1
kind: Kustomization
resources:
  - ../../base
namePrefix: dev-
namespace: dev
replicas:
  - name: myapp
    count: 1
configMapGenerator:
  - name: myapp-config
    behavior: merge
    literals:
      - LOG_LEVEL=debug
EOF

echo "=== Step 3: Create staging overlay ==="
cat > "$WORK_DIR/overlays/staging/kustomization.yaml" <<'EOF'
apiVersion: kustomize.config.k8s.io/v1beta1
kind: Kustomization
resources:
  - ../../base
namePrefix: staging-
namespace: staging
replicas:
  - name: myapp
    count: 2
configMapGenerator:
  - name: myapp-config
    behavior: merge
    literals:
      - LOG_LEVEL=info
EOF

echo "=== Step 4: Create prod overlay ==="
cat > "$WORK_DIR/overlays/prod/kustomization.yaml" <<'EOF'
apiVersion: kustomize.config.k8s.io/v1beta1
kind: Kustomization
resources:
  - ../../base
namePrefix: prod-
namespace: production
replicas:
  - name: myapp
    count: 3
configMapGenerator:
  - name: myapp-config
    behavior: merge
    literals:
      - LOG_LEVEL=warn
EOF

echo ""
echo "=== Step 5: Compare environments ==="
for env in dev staging prod; do
  echo "--- $env ---"
  kubectl kustomize "$WORK_DIR/overlays/$env/" | grep -E 'replicas:|LOG_LEVEL|namespace:'
  echo ""
done

echo "=== Cleanup ==="
rm -rf "$WORK_DIR"
```

</details>

### Challenge 2: Use JSON Patches to Add a Sidecar Container

Using a JSON patch in a Kustomize overlay, add a logging sidecar container to an existing Deployment without modifying the base.

<details>
<summary>Show solution</summary>

```yaml
# overlays/prod/patch-sidecar.yaml — adds a fluent-bit sidecar
apiVersion: apps/v1
kind: Deployment
metadata:
  name: api-gateway
spec:
  template:
    spec:
      containers:
      - name: fluent-bit
        image: fluent/fluent-bit:2.2
        volumeMounts:
        - name: shared-logs
          mountPath: /var/log/app
        resources:
          requests:
            cpu: 50m
            memory: 64Mi
          limits:
            cpu: 100m
            memory: 128Mi
      volumes:
      - name: shared-logs
        emptyDir: {}
```

```yaml
# overlays/prod/kustomization.yaml
apiVersion: kustomize.config.k8s.io/v1beta1
kind: Kustomization

resources:
  - ../../base

patches:
  - path: patch-sidecar.yaml
    target:
      kind: Deployment
      name: api-gateway
```

```bash
# Verify the sidecar appears in the rendered output
kubectl kustomize overlays/prod/ | grep -A5 "fluent-bit"
# - name: fluent-bit
#   image: fluent/fluent-bit:2.2
#   volumeMounts: ...
```

</details>

---

## Capstone Connection

**DeployForge** uses Kustomize for environment-specific configuration management:

- **Three-tier overlay structure** — `base/` contains the canonical DeployForge manifests. `overlays/dev/` sets 1 replica, debug logging, relaxed resource limits, and the `dev-latest` image tag. `overlays/staging/` uses 2 replicas, info logging, and a staging-specific image tag. `overlays/prod/` sets 3 replicas, warn logging, production resource limits, HPA, pod anti-affinity, and ExternalSecrets.
- **ConfigMap generators with hashes** — Environment-specific ConfigMaps are generated via `configMapGenerator` with automatic hash suffixes. When a config value changes (e.g., `LOG_LEVEL=warn` → `LOG_LEVEL=info` for debugging), the hash changes, and the Deployment rolls automatically — no manual restart needed.
- **Image management** — Each overlay specifies the exact image tag via the `images` transformer. CI/CD pipelines update the overlay's `kustomization.yaml` with the new Git SHA, which triggers a GitOps sync (ArgoCD detects the diff and applies).
- **Helm + Kustomize pipeline** — For distribution, DeployForge ships as a Helm chart (Module 07.3). For internal deployment, the team renders the Helm chart and applies Kustomize overlays on top for patches that don't fit neatly into Helm values (e.g., adding monitoring sidecars, injecting company-specific annotations).
- **GitOps integration** — ArgoCD watches the `overlays/prod/` directory in the Git repo. Every merge to `main` that touches overlay files triggers a sync. Kustomize's deterministic output makes ArgoCD diffs clean and reviewable.
- **Secret generators** — In dev, `secretGenerator` creates Secrets from literal values. In prod, Secrets are managed by External Secrets Operator (Module 07.2), so the `secretGenerator` is replaced by `ExternalSecret` resources in the prod overlay.
