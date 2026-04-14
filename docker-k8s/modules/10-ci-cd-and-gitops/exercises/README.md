# Module 10 — Exercises

Build a complete CI/CD and progressive delivery pipeline for DeployForge, from GitHub Actions to ArgoCD to canary deployments with feature flags.

> **Prerequisite:** A running `kind` cluster with `kubectl` configured. You'll also need `helm`, `docker`, and the ArgoCD CLI installed.
> ```bash
> # Verify prerequisites
> kind get clusters        # should show at least one cluster
> kubectl cluster-info     # should connect successfully
> helm version             # v3.x required
> docker info              # Docker daemon running
> ```

---

## Exercise 1: Write a GitHub Actions CI Pipeline for DeployForge

**Goal:** Create a production-grade CI pipeline that lints, tests, builds, scans, and pushes a container image for DeployForge.

### Steps

1. **Create the workflow directory and file:**

   ```bash
   mkdir -p .github/workflows
   touch .github/workflows/ci.yml
   ```

2. **Define the trigger and environment variables:**

   The pipeline should:
   - Trigger on pushes to `main` and on PRs targeting `main`
   - Also trigger on version tags (`v*`)
   - Set `REGISTRY` and `IMAGE_NAME` as environment variables
   - Use concurrency groups to cancel outdated runs

3. **Implement the `lint` job:**

   - Check out code
   - Set up Node.js 20 with npm caching
   - Run `npm ci`
   - Run `npm run lint` and `npm run typecheck`
   - Lint the Dockerfile with hadolint

4. **Implement the `test` job:**

   - Depends on `lint`
   - Uses a PostgreSQL service container (postgres:15) for integration tests
   - Runs `npm test -- --coverage --forceExit`
   - Uploads coverage as an artifact

5. **Implement the `build-and-push` job:**

   - Depends on `test`
   - Sets up QEMU and Docker Buildx for multi-arch builds
   - Uses `docker/metadata-action` for intelligent tagging:
     - SHA tags: `sha-<7chars>`
     - Branch tags: `main`
     - PR tags: `pr-<number>`
     - Semver tags: `v1.2.3`, `v1.2`, `v1`
     - `latest` on default branch only
   - Builds for `linux/amd64` and `linux/arm64`
   - Pushes only on non-PR events
   - Uses GHA cache (`type=gha,mode=max`)

6. **Implement the `scan` job:**

   - Depends on `build-and-push`
   - Runs Trivy vulnerability scanner
   - Fails on CRITICAL severity
   - Uploads SARIF results to GitHub Security tab

7. **Verify your workflow syntax:**

   ```bash
   # If you have act installed, validate locally:
   act push --list
   # Should show: lint, test, build-and-push, scan

   # Or use actionlint:
   brew install actionlint
   actionlint .github/workflows/ci.yml
   ```

<details>
<summary>Show solution</summary>

```yaml
# .github/workflows/ci.yml
name: DeployForge CI

on:
  push:
    branches: [main]
    tags: ['v*']
  pull_request:
    branches: [main]

env:
  REGISTRY: ghcr.io
  IMAGE_NAME: ${{ github.repository }}

concurrency:
  group: ci-${{ github.ref }}
  cancel-in-progress: true

jobs:
  lint:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - uses: actions/setup-node@v4
        with:
          node-version: '20'
          cache: 'npm'

      - run: npm ci
      - run: npm run lint
      - run: npm run typecheck

      - name: Lint Dockerfile
        uses: hadolint/hadolint-action@v3.1.0
        with:
          dockerfile: Dockerfile

  test:
    runs-on: ubuntu-latest
    needs: [lint]
    services:
      postgres:
        image: postgres:15
        env:
          POSTGRES_USER: test
          POSTGRES_PASSWORD: test
          POSTGRES_DB: deployforge_test
        ports:
          - 5432:5432
        options: >-
          --health-cmd pg_isready
          --health-interval 10s
          --health-timeout 5s
          --health-retries 5
    steps:
      - uses: actions/checkout@v4

      - uses: actions/setup-node@v4
        with:
          node-version: '20'
          cache: 'npm'

      - run: npm ci
      - run: npm test -- --coverage --forceExit
        env:
          DATABASE_URL: postgres://test:test@localhost:5432/deployforge_test
          NODE_ENV: test

      - name: Upload coverage
        uses: actions/upload-artifact@v4
        if: always()
        with:
          name: coverage-report
          path: coverage/lcov.info

  build-and-push:
    runs-on: ubuntu-latest
    needs: [test]
    permissions:
      contents: read
      packages: write
    outputs:
      image-digest: ${{ steps.build.outputs.digest }}
      image-tags: ${{ steps.meta.outputs.tags }}
    steps:
      - uses: actions/checkout@v4

      - name: Docker metadata
        id: meta
        uses: docker/metadata-action@v5
        with:
          images: ${{ env.REGISTRY }}/${{ env.IMAGE_NAME }}
          tags: |
            type=sha,prefix=sha-
            type=ref,event=branch
            type=ref,event=pr,prefix=pr-
            type=semver,pattern=v{{version}}
            type=semver,pattern=v{{major}}.{{minor}}
            type=semver,pattern=v{{major}}
            type=raw,value=latest,enable={{is_default_branch}}

      - name: Set up QEMU
        uses: docker/setup-qemu-action@v3

      - name: Set up Docker Buildx
        uses: docker/setup-buildx-action@v3

      - name: Log in to GHCR
        if: github.event_name != 'pull_request'
        uses: docker/login-action@v3
        with:
          registry: ${{ env.REGISTRY }}
          username: ${{ github.actor }}
          password: ${{ secrets.GITHUB_TOKEN }}

      - name: Build and push
        id: build
        uses: docker/build-push-action@v5
        with:
          context: .
          platforms: linux/amd64,linux/arm64
          push: ${{ github.event_name != 'pull_request' }}
          tags: ${{ steps.meta.outputs.tags }}
          labels: ${{ steps.meta.outputs.labels }}
          cache-from: type=gha
          cache-to: type=gha,mode=max
          build-args: |
            BUILD_DATE=${{ github.event.head_commit.timestamp }}
            VCS_REF=${{ github.sha }}

  scan:
    runs-on: ubuntu-latest
    needs: [build-and-push]
    if: github.event_name != 'pull_request'
    permissions:
      security-events: write
    steps:
      - name: Run Trivy (gate — critical only)
        uses: aquasecurity/trivy-action@0.28.0
        with:
          image-ref: ${{ env.REGISTRY }}/${{ env.IMAGE_NAME }}:sha-${{ github.sha }}
          format: 'table'
          exit-code: '1'
          severity: 'CRITICAL'
          ignore-unfixed: true

      - name: Run Trivy (report — all severities)
        uses: aquasecurity/trivy-action@0.28.0
        if: always()
        with:
          image-ref: ${{ env.REGISTRY }}/${{ env.IMAGE_NAME }}:sha-${{ github.sha }}
          format: 'sarif'
          output: 'trivy-results.sarif'
          severity: 'CRITICAL,HIGH,MEDIUM'

      - name: Upload SARIF to GitHub Security
        uses: github/codeql-action/upload-sarif@v3
        if: always()
        with:
          sarif_file: 'trivy-results.sarif'
```

Verify with:
```bash
actionlint .github/workflows/ci.yml
# → should show no errors

act push --list
# Job ID         Job name        Workflow name   Workflow file   Events
# lint           lint            DeployForge CI  ci.yml          push
# test           test            DeployForge CI  ci.yml          push
# build-and-push build-and-push  DeployForge CI  ci.yml          push
# scan           scan            DeployForge CI  ci.yml          push
```

</details>

---

## Exercise 2: Deploy ArgoCD and Set Up GitOps for DeployForge

**Goal:** Install ArgoCD in a kind cluster, configure a GitOps repository structure, and deploy DeployForge via ArgoCD.

### Steps

1. **Create a kind cluster with port mapping for ArgoCD:**

   ```bash
   cat <<EOF | kind create cluster --name gitops-lab --config=-
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
         - containerPort: 30080
           hostPort: 8080
         - containerPort: 30443
           hostPort: 8443
   EOF
   ```

2. **Install ArgoCD:**

   ```bash
   kubectl create namespace argocd
   kubectl apply -n argocd \
     -f https://raw.githubusercontent.com/argoproj/argo-cd/stable/manifests/install.yaml

   # Wait for all pods
   kubectl wait --for=condition=Ready pod \
     -l app.kubernetes.io/part-of=argocd \
     -n argocd \
     --timeout=300s
   ```

3. **Access the ArgoCD UI:**

   ```bash
   # Get the admin password
   kubectl -n argocd get secret argocd-initial-admin-secret \
     -o jsonpath="{.data.password}" | base64 -d; echo

   # Port-forward
   kubectl port-forward svc/argocd-server -n argocd 8080:443 &

   # Login with CLI
   argocd login localhost:8080 --username admin --password <password> --insecure
   ```

4. **Create a GitOps repository structure locally:**

   ```
   deployforge-gitops/
   ├── apps/
   │   └── deployforge.yaml
   ├── base/
   │   ├── kustomization.yaml
   │   ├── namespace.yaml
   │   ├── deployment.yaml
   │   ├── service.yaml
   │   └── configmap.yaml
   ├── overlays/
   │   ├── staging/
   │   │   ├── kustomization.yaml
   │   │   └── patches/
   │   │       └── replicas.yaml
   │   └── production/
   │       ├── kustomization.yaml
   │       └── patches/
   │           ├── replicas.yaml
   │           └── resources.yaml
   └── argocd/
       ├── appproject.yaml
       └── root-app.yaml
   ```

   Create all manifests. The Deployment should use the `nginx:1.25` image as a placeholder (since DeployForge's image isn't published yet).

5. **Apply the ArgoCD AppProject and root Application:**

   ```bash
   kubectl apply -f argocd/appproject.yaml
   kubectl apply -f argocd/root-app.yaml
   ```

6. **Verify sync status:**

   ```bash
   argocd app list
   # NAME         CLUSTER                         NAMESPACE   STATUS  HEALTH
   # root         https://kubernetes.default.svc  argocd      Synced  Healthy
   # deployforge  https://kubernetes.default.svc  deployforge Synced  Healthy

   argocd app get deployforge
   ```

7. **Test drift detection:**

   ```bash
   # Manually scale the deployment (simulating unauthorized change)
   kubectl scale deployment deployforge-api -n deployforge --replicas=10

   # Watch ArgoCD detect and revert the drift
   argocd app get deployforge --refresh
   # → Status should show OutOfSync, then self-heal back to declared replicas
   ```

<details>
<summary>Show solution</summary>

```yaml
# base/namespace.yaml
apiVersion: v1
kind: Namespace
metadata:
  name: deployforge

---
# base/deployment.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: deployforge-api
  namespace: deployforge
spec:
  replicas: 1
  selector:
    matchLabels:
      app: deployforge-api
  template:
    metadata:
      labels:
        app: deployforge-api
    spec:
      containers:
        - name: api
          image: nginx:1.25
          ports:
            - containerPort: 80
          resources:
            requests:
              cpu: 50m
              memory: 64Mi
            limits:
              cpu: 200m
              memory: 128Mi
          readinessProbe:
            httpGet:
              path: /
              port: 80
            initialDelaySeconds: 5
            periodSeconds: 10

---
# base/service.yaml
apiVersion: v1
kind: Service
metadata:
  name: deployforge-api
  namespace: deployforge
spec:
  selector:
    app: deployforge-api
  ports:
    - port: 80
      targetPort: 80

---
# base/configmap.yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: deployforge-config
  namespace: deployforge
data:
  NODE_ENV: production
  LOG_LEVEL: info

---
# base/kustomization.yaml
apiVersion: kustomize.config.k8s.io/v1beta1
kind: Kustomization
resources:
  - namespace.yaml
  - deployment.yaml
  - service.yaml
  - configmap.yaml
commonLabels:
  app.kubernetes.io/name: deployforge
  app.kubernetes.io/part-of: deployforge
```

```yaml
# overlays/staging/kustomization.yaml
apiVersion: kustomize.config.k8s.io/v1beta1
kind: Kustomization
resources:
  - ../../base
namePrefix: staging-
namespace: deployforge-staging
patches:
  - path: patches/replicas.yaml

---
# overlays/staging/patches/replicas.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: deployforge-api
spec:
  replicas: 1

---
# overlays/production/kustomization.yaml
apiVersion: kustomize.config.k8s.io/v1beta1
kind: Kustomization
resources:
  - ../../base
namespace: deployforge
patches:
  - path: patches/replicas.yaml
  - path: patches/resources.yaml

---
# overlays/production/patches/replicas.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: deployforge-api
spec:
  replicas: 3

---
# overlays/production/patches/resources.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: deployforge-api
spec:
  template:
    spec:
      containers:
        - name: api
          resources:
            requests:
              cpu: 100m
              memory: 128Mi
            limits:
              cpu: 500m
              memory: 512Mi
```

```yaml
# argocd/appproject.yaml
apiVersion: argoproj.io/v1alpha1
kind: AppProject
metadata:
  name: deployforge
  namespace: argocd
spec:
  description: DeployForge project
  sourceRepos:
    - '*'
  destinations:
    - server: https://kubernetes.default.svc
      namespace: deployforge
    - server: https://kubernetes.default.svc
      namespace: deployforge-staging
    - server: https://kubernetes.default.svc
      namespace: argocd
  clusterResourceWhitelist:
    - group: ''
      kind: Namespace

---
# argocd/root-app.yaml
apiVersion: argoproj.io/v1alpha1
kind: Application
metadata:
  name: root
  namespace: argocd
spec:
  project: default
  source:
    repoURL: https://github.com/org/deployforge-gitops.git
    targetRevision: main
    path: apps
  destination:
    server: https://kubernetes.default.svc
    namespace: argocd
  syncPolicy:
    automated:
      prune: true
      selfHeal: true

---
# apps/deployforge.yaml
apiVersion: argoproj.io/v1alpha1
kind: Application
metadata:
  name: deployforge
  namespace: argocd
  finalizers:
    - resources-finalizer.argocd.argoproj.io
spec:
  project: deployforge
  source:
    repoURL: https://github.com/org/deployforge-gitops.git
    targetRevision: main
    path: overlays/production
  destination:
    server: https://kubernetes.default.svc
    namespace: deployforge
  syncPolicy:
    automated:
      prune: true
      selfHeal: true
    syncOptions:
      - CreateNamespace=true
    retry:
      limit: 5
      backoff:
        duration: 5s
        factor: 2
        maxDuration: 3m
```

For local testing without a real Git repo, you can use a local path source or apply manifests directly:

```bash
# Apply the base manifests directly for testing
kubectl apply -k overlays/production/

# Verify the deployment
kubectl get all -n deployforge
# → deployment, replicaset, pods, service should all be present

# Test drift detection
kubectl scale deployment deployforge-api -n deployforge --replicas=10
kubectl get deployment deployforge-api -n deployforge -w
# → ArgoCD will scale it back to 3 within the reconciliation interval
```

</details>

---

## Exercise 3: Implement Canary Deployment with Argo Rollouts

**Goal:** Replace DeployForge's standard Deployment with an Argo Rollout that uses a canary strategy with automated Prometheus-based analysis.

### Steps

1. **Install Argo Rollouts:**

   ```bash
   kubectl create namespace argo-rollouts
   kubectl apply -n argo-rollouts \
     -f https://github.com/argoproj/argo-rollouts/releases/latest/download/install.yaml

   kubectl wait --for=condition=Ready pod \
     -l app.kubernetes.io/name=argo-rollouts \
     -n argo-rollouts \
     --timeout=120s

   # Install the kubectl plugin
   brew install argoproj/tap/kubectl-argo-rollouts
   ```

2. **Install Prometheus (for canary analysis):**

   ```bash
   helm repo add prometheus-community https://prometheus-community.github.io/helm-charts
   helm repo update

   helm install prometheus prometheus-community/kube-prometheus-stack \
     --namespace monitoring \
     --create-namespace \
     --set grafana.enabled=false \
     --set alertmanager.enabled=false \
     --wait
   ```

3. **Convert the DeployForge Deployment to a Rollout:**

   Replace `kind: Deployment` with `kind: Rollout` and add:
   - A canary strategy with steps: 5% → 25% → 50% → 100%
   - Stable and canary Services
   - Pause durations between steps
   - AnalysisTemplate references at each step

4. **Create AnalysisTemplates:**

   - `canary-success-rate`: Query Prometheus for HTTP 2xx/3xx rate, require ≥95%
   - `canary-latency`: Query Prometheus for p99 latency, require ≤500ms

5. **Deploy the Rollout:**

   ```bash
   kubectl apply -f deploy/rollouts/
   kubectl argo rollouts get rollout deployforge-api -n deployforge
   ```

6. **Trigger a canary rollout:**

   ```bash
   # Update the image to simulate a new version
   kubectl argo rollouts set image deployforge-api \
     api=nginx:1.26 \
     -n deployforge

   # Watch the canary progression
   kubectl argo rollouts get rollout deployforge-api -n deployforge --watch
   ```

7. **Test rollback:**

   ```bash
   # Abort the rollout mid-canary
   kubectl argo rollouts abort deployforge-api -n deployforge

   # Verify traffic returns to stable version
   kubectl argo rollouts get rollout deployforge-api -n deployforge
   ```

8. **Open the Argo Rollouts dashboard:**

   ```bash
   kubectl argo rollouts dashboard &
   # → http://localhost:3100
   ```

<details>
<summary>Show solution</summary>

```yaml
# deploy/rollouts/rollout.yaml
apiVersion: argoproj.io/v1alpha1
kind: Rollout
metadata:
  name: deployforge-api
  namespace: deployforge
spec:
  replicas: 5
  revisionHistoryLimit: 3
  selector:
    matchLabels:
      app: deployforge-api
  template:
    metadata:
      labels:
        app: deployforge-api
      annotations:
        prometheus.io/scrape: "true"
        prometheus.io/port: "80"
    spec:
      containers:
        - name: api
          image: nginx:1.25
          ports:
            - name: http
              containerPort: 80
          readinessProbe:
            httpGet:
              path: /
              port: http
            initialDelaySeconds: 5
            periodSeconds: 10
          resources:
            requests:
              cpu: 50m
              memory: 64Mi
            limits:
              cpu: 200m
              memory: 128Mi
  strategy:
    canary:
      canaryService: deployforge-api-canary
      stableService: deployforge-api-stable
      steps:
        # Step 1: Small canary
        - setWeight: 5
        - pause: { duration: 1m }
        - analysis:
            templates:
              - templateName: canary-success-rate

        # Step 2: Increase traffic
        - setWeight: 25
        - pause: { duration: 2m }
        - analysis:
            templates:
              - templateName: canary-success-rate
              - templateName: canary-latency

        # Step 3: Majority traffic
        - setWeight: 50
        - pause: { duration: 3m }
        - analysis:
            templates:
              - templateName: canary-success-rate
              - templateName: canary-latency

        # Step 4: Full promotion
        - setWeight: 100

---
# deploy/rollouts/services.yaml
apiVersion: v1
kind: Service
metadata:
  name: deployforge-api-stable
  namespace: deployforge
spec:
  selector:
    app: deployforge-api
  ports:
    - port: 80
      targetPort: http
      name: http

---
apiVersion: v1
kind: Service
metadata:
  name: deployforge-api-canary
  namespace: deployforge
spec:
  selector:
    app: deployforge-api
  ports:
    - port: 80
      targetPort: http
      name: http

---
# deploy/rollouts/analysis.yaml
apiVersion: argoproj.io/v1alpha1
kind: AnalysisTemplate
metadata:
  name: canary-success-rate
  namespace: deployforge
spec:
  metrics:
    - name: success-rate
      interval: 30s
      count: 5
      failureLimit: 1
      successCondition: result[0] >= 0.95
      provider:
        prometheus:
          address: http://prometheus-kube-prometheus-prometheus.monitoring:9090
          query: |
            sum(rate(
              nginx_http_requests_total{
                status=~"[23]..",
                pod=~"deployforge-api-.*"
              }[1m]
            )) /
            sum(rate(
              nginx_http_requests_total{
                pod=~"deployforge-api-.*"
              }[1m]
            ))

---
apiVersion: argoproj.io/v1alpha1
kind: AnalysisTemplate
metadata:
  name: canary-latency
  namespace: deployforge
spec:
  metrics:
    - name: p99-latency
      interval: 30s
      count: 5
      failureLimit: 1
      successCondition: result[0] <= 500
      provider:
        prometheus:
          address: http://prometheus-kube-prometheus-prometheus.monitoring:9090
          query: |
            histogram_quantile(0.99,
              sum(rate(
                nginx_http_request_duration_seconds_bucket{
                  pod=~"deployforge-api-.*"
                }[1m]
              )) by (le)
            ) * 1000
```

Deploy and test:

```bash
# Create namespace
kubectl create namespace deployforge 2>/dev/null || true

# Apply all rollout manifests
kubectl apply -f deploy/rollouts/

# Verify initial deployment
kubectl argo rollouts get rollout deployforge-api -n deployforge
# → Status: Healthy, all pods running nginx:1.25

# Trigger a canary by updating the image
kubectl argo rollouts set image deployforge-api \
  api=nginx:1.26 \
  -n deployforge

# Watch the progression
kubectl argo rollouts get rollout deployforge-api -n deployforge --watch
# → Should show steps progressing: 5% → 25% → 50% → 100%

# To abort mid-rollout
kubectl argo rollouts abort deployforge-api -n deployforge

# To manually promote (skip remaining steps)
kubectl argo rollouts promote deployforge-api -n deployforge

# View rollout history
kubectl argo rollouts list rollouts -n deployforge
```

> **Note:** In a kind cluster without real traffic, the Prometheus queries may return `NaN` (no data). The analysis may fail or succeed depending on how the `successCondition` handles missing data. In production, you'd generate synthetic traffic during canary analysis. For this exercise, you can use `--bypass-analysis` flag or remove the analysis steps temporarily to see the weight progression.

</details>

---

## Exercise 4: Set Up Feature Flags with Flagd and OpenFeature

**Goal:** Deploy flagd to the kind cluster, configure feature flags for DeployForge, and write a simple application that evaluates flags.

### Steps

1. **Deploy flagd with feature flag definitions:**

   Create a ConfigMap with flags for:
   - `new-dashboard` — enabled for 10% of users
   - `maintenance-mode` — global kill switch, default off
   - `dark-api-v2` — enabled for internal users only

2. **Deploy flagd as a Kubernetes Deployment + Service:**

   ```bash
   kubectl apply -f deploy/feature-flags/
   kubectl wait --for=condition=Ready pod \
     -l app=flagd \
     -n deployforge \
     --timeout=60s
   ```

3. **Verify flagd is running:**

   ```bash
   # Port-forward to test locally
   kubectl port-forward svc/flagd -n deployforge 8013:8013 &

   # Test flag evaluation with grpcurl
   brew install grpcurl
   grpcurl -plaintext -d '{
     "flagKey": "new-dashboard",
     "context": {
       "targetingKey": "user-123",
       "email": "dev@company.com"
     }
   }' localhost:8013 schema.v1.Service/ResolveBoolean
   ```

4. **Write a simple Node.js script that evaluates flags:**

   Create a small TypeScript file that:
   - Connects to flagd
   - Evaluates all three flags for different user contexts
   - Prints the results

5. **Test flag changes via GitOps:**

   Update the ConfigMap to enable `maintenance-mode`, apply it, and verify flagd picks up the change without restart:

   ```bash
   # Edit the ConfigMap
   kubectl edit configmap deployforge-feature-flags -n deployforge
   # Change maintenance-mode defaultVariant to "on"

   # Verify flag evaluation changed
   grpcurl -plaintext -d '{
     "flagKey": "maintenance-mode",
     "context": { "targetingKey": "any-user" }
   }' localhost:8013 schema.v1.Service/ResolveBoolean
   # → should now return true
   ```

<details>
<summary>Show solution</summary>

```yaml
# deploy/feature-flags/configmap.yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: deployforge-feature-flags
  namespace: deployforge
data:
  flags.json: |
    {
      "$schema": "https://flagd.dev/schema/v0/flags.json",
      "flags": {
        "new-dashboard": {
          "state": "ENABLED",
          "variants": {
            "on": true,
            "off": false
          },
          "defaultVariant": "off",
          "targeting": {
            "if": [
              { "ends_with": [{ "var": "email" }, "@company.com"] },
              "on",
              "off"
            ]
          }
        },
        "maintenance-mode": {
          "state": "ENABLED",
          "variants": {
            "on": true,
            "off": false
          },
          "defaultVariant": "off"
        },
        "dark-api-v2": {
          "state": "ENABLED",
          "variants": {
            "on": true,
            "off": false
          },
          "defaultVariant": "off",
          "targeting": {
            "if": [
              { "==": [{ "var": "role" }, "internal"] },
              "on",
              "off"
            ]
          }
        }
      }
    }

---
# deploy/feature-flags/deployment.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: flagd
  namespace: deployforge
spec:
  replicas: 1
  selector:
    matchLabels:
      app: flagd
  template:
    metadata:
      labels:
        app: flagd
    spec:
      containers:
        - name: flagd
          image: ghcr.io/open-feature/flagd:v0.10.0
          ports:
            - containerPort: 8013
              name: grpc
          args:
            - start
            - --uri
            - file:/etc/flagd/flags.json
          volumeMounts:
            - name: flags
              mountPath: /etc/flagd
          readinessProbe:
            grpc:
              port: 8013
            initialDelaySeconds: 5
          resources:
            requests:
              cpu: 50m
              memory: 64Mi
            limits:
              cpu: 100m
              memory: 128Mi
      volumes:
        - name: flags
          configMap:
            name: deployforge-feature-flags

---
# deploy/feature-flags/service.yaml
apiVersion: v1
kind: Service
metadata:
  name: flagd
  namespace: deployforge
spec:
  selector:
    app: flagd
  ports:
    - name: grpc
      port: 8013
      targetPort: grpc
```

Deploy:

```bash
kubectl create namespace deployforge 2>/dev/null || true
kubectl apply -f deploy/feature-flags/

kubectl wait --for=condition=Ready pod \
  -l app=flagd \
  -n deployforge \
  --timeout=60s

echo "✓ Flagd is running"
```

Test with grpcurl:

```bash
kubectl port-forward svc/flagd -n deployforge 8013:8013 &

# Internal user — should get new-dashboard=true
grpcurl -plaintext -d '{
  "flagKey": "new-dashboard",
  "context": {
    "targetingKey": "user-1",
    "email": "alice@company.com"
  }
}' localhost:8013 schema.v1.Service/ResolveBoolean
# → { "value": true, "reason": "TARGETING_MATCH", "variant": "on" }

# External user — should get new-dashboard=false
grpcurl -plaintext -d '{
  "flagKey": "new-dashboard",
  "context": {
    "targetingKey": "user-2",
    "email": "customer@gmail.com"
  }
}' localhost:8013 schema.v1.Service/ResolveBoolean
# → { "value": false, "reason": "TARGETING_MATCH", "variant": "off" }

# Maintenance mode — should be off
grpcurl -plaintext -d '{
  "flagKey": "maintenance-mode",
  "context": { "targetingKey": "any" }
}' localhost:8013 schema.v1.Service/ResolveBoolean
# → { "value": false, "reason": "STATIC", "variant": "off" }

# Dark API — internal role
grpcurl -plaintext -d '{
  "flagKey": "dark-api-v2",
  "context": {
    "targetingKey": "user-3",
    "role": "internal"
  }
}' localhost:8013 schema.v1.Service/ResolveBoolean
# → { "value": true, "reason": "TARGETING_MATCH", "variant": "on" }
```

TypeScript evaluation script:

```typescript
// scripts/test-flags.ts
import { OpenFeature } from '@openfeature/server-sdk';
import { FlagdProvider } from '@openfeature/flagd-provider';

async function main() {
  await OpenFeature.setProviderAndWait(new FlagdProvider({
    host: 'localhost',
    port: 8013,
  }));

  const client = OpenFeature.getClient();

  const testCases = [
    {
      label: 'Internal user (new-dashboard)',
      flag: 'new-dashboard',
      context: { targetingKey: 'u1', email: 'dev@company.com' },
    },
    {
      label: 'External user (new-dashboard)',
      flag: 'new-dashboard',
      context: { targetingKey: 'u2', email: 'user@gmail.com' },
    },
    {
      label: 'Maintenance mode',
      flag: 'maintenance-mode',
      context: { targetingKey: 'u3' },
    },
    {
      label: 'Dark API (internal)',
      flag: 'dark-api-v2',
      context: { targetingKey: 'u4', role: 'internal' },
    },
    {
      label: 'Dark API (external)',
      flag: 'dark-api-v2',
      context: { targetingKey: 'u5', role: 'customer' },
    },
  ];

  for (const tc of testCases) {
    const value = await client.getBooleanValue(tc.flag, false, tc.context);
    console.log(`${tc.label}: ${value}`);
  }

  await OpenFeature.close();
}

main().catch(console.error);
```

Run it:

```bash
npx tsx scripts/test-flags.ts
# Internal user (new-dashboard): true
# External user (new-dashboard): false
# Maintenance mode: false
# Dark API (internal): true
# Dark API (external): false
```

</details>

---

## Checklist

After completing all exercises, verify:

- [ ] GitHub Actions CI workflow passes `actionlint` validation
- [ ] CI pipeline has all 4 stages: lint → test → build-and-push → scan
- [ ] ArgoCD is running and accessible on the kind cluster
- [ ] ArgoCD Application syncs manifests from the GitOps repo
- [ ] Drift detection works (manual kubectl changes get reverted)
- [ ] Argo Rollouts controller is installed
- [ ] Canary rollout progresses through weight steps
- [ ] AnalysisTemplates are configured for success rate and latency
- [ ] Rollback works when analysis fails or rollout is aborted
- [ ] Flagd is deployed and evaluating feature flags
- [ ] Feature flags target different users based on context attributes
- [ ] Flag changes via ConfigMap take effect without pod restarts
