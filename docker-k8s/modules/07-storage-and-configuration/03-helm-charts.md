# 7.3 — Helm Charts: Templating & Package Management

## Concept

As your Kubernetes manifests grow — Deployments, Services, ConfigMaps, Secrets, Ingress, NetworkPolicies, PVCs — you face an inevitable problem: how do you manage 30+ YAML files across dev, staging, and production without copy-pasting yourself into a maintenance nightmare? **Helm** is Kubernetes' package manager. It lets you define parameterized templates (charts), inject environment-specific values, manage releases with atomic install/upgrade/rollback, and share reusable packages via chart repositories.

Helm is not just "templating for YAML" — it's a full release management system. It tracks every installation as a versioned release, enables one-command rollbacks, supports pre/post-install hooks (run migrations before deploying), and manages chart dependencies (need PostgreSQL? Add it as a subchart). If you're deploying anything beyond trivial workloads, Helm is table stakes.

---

## Deep Dive

### Chart Structure

A Helm chart is a directory with a specific layout. Understanding each file's role is critical for building maintainable charts.

```
deployforge/                          # Chart root directory
├── Chart.yaml                        # Chart metadata (name, version, dependencies)
├── values.yaml                       # Default configuration values
├── values-dev.yaml                   # Dev overrides (optional, convention)
├── values-staging.yaml               # Staging overrides (optional)
├── values-prod.yaml                  # Prod overrides (optional)
├── charts/                           # Dependency charts (auto-populated)
│   ├── postgresql-15.5.0.tgz
│   └── redis-19.0.0.tgz
├── templates/                        # Go templates → rendered K8s manifests
│   ├── _helpers.tpl                  # Named template definitions
│   ├── deployment.yaml               # Deployment template
│   ├── service.yaml                  # Service template
│   ├── configmap.yaml                # ConfigMap template
│   ├── secret.yaml                   # Secret template
│   ├── ingress.yaml                  # Ingress template
│   ├── hpa.yaml                      # HorizontalPodAutoscaler template
│   ├── pvc.yaml                      # PersistentVolumeClaim template
│   ├── networkpolicy.yaml            # NetworkPolicy template
│   ├── serviceaccount.yaml           # ServiceAccount template
│   ├── tests/                        # Helm test pods
│   │   └── test-connection.yaml
│   ├── hooks/                        # Lifecycle hooks
│   │   ├── pre-install-migration.yaml
│   │   └── post-upgrade-notify.yaml
│   └── NOTES.txt                     # Post-install instructions
└── .helmignore                       # Files to exclude from packaging
```

### Chart.yaml

```yaml
# Chart.yaml — chart metadata
apiVersion: v2                        # Helm 3 chart API version
name: deployforge
description: A production-grade deployment platform
type: application                     # 'application' or 'library'
version: 0.7.0                        # Chart version (semver) — bump on chart changes
appVersion: "1.2.0"                   # App version — bump on application code changes

maintainers:
  - name: Platform Team
    email: platform@deployforge.io

# Chart dependencies — pulled via `helm dependency update`
dependencies:
  - name: postgresql
    version: "15.5.x"                 # Semver range
    repository: "https://charts.bitnami.com/bitnami"
    condition: postgresql.enabled       # Only include if values.postgresql.enabled=true
  - name: redis
    version: "19.x.x"
    repository: "https://charts.bitnami.com/bitnami"
    condition: redis.enabled
```

---

### values.yaml

The `values.yaml` file is the configuration interface for your chart. Every value here can be overridden per-environment.

```yaml
# values.yaml — default values (dev-friendly)

# -- Number of API Gateway replicas
replicaCount: 1

image:
  repository: deployforge/api-gateway
  tag: "latest"                       # Override in CI: --set image.tag=$GIT_SHA
  pullPolicy: IfNotPresent

# -- Service account configuration
serviceAccount:
  create: true
  name: "deployforge"
  annotations: {}

# -- API Gateway configuration
apiGateway:
  port: 3000
  logLevel: "debug"
  logFormat: "text"
  workerConcurrency: 2
  databasePoolSize: 5

# -- Worker configuration
worker:
  enabled: true
  replicas: 1
  port: 4000
  concurrency: 3

# -- Ingress configuration
ingress:
  enabled: false                      # Disabled by default for local dev
  className: "nginx"
  annotations: {}
  hosts:
    - host: deployforge.local
      paths:
        - path: /
          pathType: Prefix
  tls: []

# -- Resource limits
resources:
  requests:
    cpu: 100m
    memory: 128Mi
  limits:
    cpu: 500m
    memory: 512Mi

# -- HorizontalPodAutoscaler
autoscaling:
  enabled: false
  minReplicas: 2
  maxReplicas: 10
  targetCPUUtilizationPercentage: 70

# -- Persistence (for StatefulSet components)
persistence:
  postgres:
    enabled: true
    storageClass: ""                  # Use cluster default
    size: 10Gi
    accessMode: ReadWriteOnce
  redis:
    enabled: true
    storageClass: ""
    size: 5Gi
    accessMode: ReadWriteOnce

# -- External secrets (ESO)
externalSecrets:
  enabled: false                      # Enable in staging/prod
  secretStore: "vault-backend"
  refreshInterval: "1h"

# -- Subchart overrides
postgresql:
  enabled: true
  auth:
    postgresPassword: "local-dev-only"
    database: "deployforge"

redis:
  enabled: true
  auth:
    password: "local-dev-redis"
  architecture: standalone
```

---

### Go Template Syntax

Helm uses Go's `text/template` package with Sprig functions. The key constructs:

```yaml
# templates/deployment.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: {{ include "deployforge.fullname" . }}      # Named template
  labels:
    {{- include "deployforge.labels" . | nindent 4 }}
spec:
  {{- if not .Values.autoscaling.enabled }}
  replicas: {{ .Values.replicaCount }}               # Value substitution
  {{- end }}
  selector:
    matchLabels:
      {{- include "deployforge.selectorLabels" . | nindent 6 }}
  template:
    metadata:
      annotations:
        # Force rollout on ConfigMap change
        checksum/config: {{ include (print $.Template.BasePath "/configmap.yaml") . | sha256sum }}
      labels:
        {{- include "deployforge.selectorLabels" . | nindent 8 }}
    spec:
      serviceAccountName: {{ include "deployforge.serviceAccountName" . }}
      containers:
      - name: {{ .Chart.Name }}
        image: "{{ .Values.image.repository }}:{{ .Values.image.tag | default .Chart.AppVersion }}"
        imagePullPolicy: {{ .Values.image.pullPolicy }}
        ports:
        - name: http
          containerPort: {{ .Values.apiGateway.port }}
        envFrom:
        - configMapRef:
            name: {{ include "deployforge.fullname" . }}-config
        {{- if .Values.externalSecrets.enabled }}
        - secretRef:
            name: {{ include "deployforge.fullname" . }}-db-credentials
        {{- end }}
        resources:
          {{- toYaml .Values.resources | nindent 12 }}
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
      {{- with .Values.nodeSelector }}
      nodeSelector:
        {{- toYaml . | nindent 8 }}
      {{- end }}
      {{- with .Values.tolerations }}
      tolerations:
        {{- toYaml . | nindent 8 }}
      {{- end }}
```

**Key template patterns:**

| Pattern | Example | Purpose |
|---------|---------|---------|
| Value substitution | `{{ .Values.replicaCount }}` | Insert a value from values.yaml |
| Conditionals | `{{- if .Values.ingress.enabled }}` | Include/exclude blocks |
| Loops | `{{- range .Values.ingress.hosts }}` | Iterate over lists |
| Named templates | `{{ include "deployforge.fullname" . }}` | Reusable template fragments |
| Pipelines | `{{ .Values.resources \| toYaml \| nindent 12 }}` | Chain functions |
| Default values | `{{ .Values.image.tag \| default .Chart.AppVersion }}` | Fallback values |
| Whitespace control | `{{-` and `-}}` | Trim leading/trailing whitespace |

---

### Named Templates (_helpers.tpl)

The `_helpers.tpl` file defines reusable template fragments. Files starting with `_` are not rendered as manifests.

```yaml
# templates/_helpers.tpl

{{/*
Expand the name of the chart.
*/}}
{{- define "deployforge.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Create a fully qualified app name.
Truncate at 63 chars because K8s labels are limited to 63 chars.
*/}}
{{- define "deployforge.fullname" -}}
{{- if .Values.fullnameOverride }}
{{- .Values.fullnameOverride | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- $name := default .Chart.Name .Values.nameOverride }}
{{- if contains $name .Release.Name }}
{{- .Release.Name | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- printf "%s-%s" .Release.Name $name | trunc 63 | trimSuffix "-" }}
{{- end }}
{{- end }}
{{- end }}

{{/*
Common labels
*/}}
{{- define "deployforge.labels" -}}
helm.sh/chart: {{ include "deployforge.chart" . }}
{{ include "deployforge.selectorLabels" . }}
{{- if .Chart.AppVersion }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
{{- end }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- end }}

{{/*
Selector labels
*/}}
{{- define "deployforge.selectorLabels" -}}
app.kubernetes.io/name: {{ include "deployforge.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end }}

{{/*
Chart name and version (for labels)
*/}}
{{- define "deployforge.chart" -}}
{{- printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
ServiceAccount name
*/}}
{{- define "deployforge.serviceAccountName" -}}
{{- if .Values.serviceAccount.create }}
{{- default (include "deployforge.fullname" .) .Values.serviceAccount.name }}
{{- else }}
{{- default "default" .Values.serviceAccount.name }}
{{- end }}
{{- end }}
```

---

### ConfigMap Template

```yaml
# templates/configmap.yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: {{ include "deployforge.fullname" . }}-config
  labels:
    {{- include "deployforge.labels" . | nindent 4 }}
data:
  LOG_LEVEL: {{ .Values.apiGateway.logLevel | quote }}
  LOG_FORMAT: {{ .Values.apiGateway.logFormat | quote }}
  API_PORT: {{ .Values.apiGateway.port | quote }}
  WORKER_CONCURRENCY: {{ .Values.apiGateway.workerConcurrency | quote }}
  DATABASE_POOL_SIZE: {{ .Values.apiGateway.databasePoolSize | quote }}
```

---

### Ingress Template (with conditionals)

```yaml
# templates/ingress.yaml
{{- if .Values.ingress.enabled -}}
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: {{ include "deployforge.fullname" . }}
  labels:
    {{- include "deployforge.labels" . | nindent 4 }}
  {{- with .Values.ingress.annotations }}
  annotations:
    {{- toYaml . | nindent 4 }}
  {{- end }}
spec:
  {{- if .Values.ingress.className }}
  ingressClassName: {{ .Values.ingress.className }}
  {{- end }}
  {{- if .Values.ingress.tls }}
  tls:
    {{- range .Values.ingress.tls }}
    - hosts:
        {{- range .hosts }}
        - {{ . | quote }}
        {{- end }}
      secretName: {{ .secretName }}
    {{- end }}
  {{- end }}
  rules:
    {{- range .Values.ingress.hosts }}
    - host: {{ .host | quote }}
      http:
        paths:
          {{- range .paths }}
          - path: {{ .path }}
            pathType: {{ .pathType }}
            backend:
              service:
                name: {{ include "deployforge.fullname" $ }}
                port:
                  name: http
          {{- end }}
    {{- end }}
{{- end }}
```

---

### Hooks: Pre-Install Database Migration

Hooks run at specific points in the release lifecycle — before install, after upgrade, before delete, etc.

```yaml
# templates/hooks/pre-install-migration.yaml
apiVersion: batch/v1
kind: Job
metadata:
  name: {{ include "deployforge.fullname" . }}-db-migrate
  labels:
    {{- include "deployforge.labels" . | nindent 4 }}
  annotations:
    "helm.sh/hook": pre-install,pre-upgrade
    "helm.sh/hook-weight": "-5"       # Lower weight runs first
    "helm.sh/hook-delete-policy": before-hook-creation,hook-succeeded
spec:
  backoffLimit: 3
  template:
    spec:
      restartPolicy: Never
      containers:
      - name: migrate
        image: "{{ .Values.image.repository }}:{{ .Values.image.tag | default .Chart.AppVersion }}"
        command: ["npm", "run", "db:migrate"]
        envFrom:
        - secretRef:
            name: {{ include "deployforge.fullname" . }}-db-credentials
```

**Hook annotations explained:**

| Annotation | Value | Meaning |
|------------|-------|---------|
| `helm.sh/hook` | `pre-install` | Run before first `helm install` |
| | `pre-upgrade` | Run before `helm upgrade` |
| | `post-install` | Run after install succeeds |
| | `pre-delete` | Run before `helm uninstall` |
| `helm.sh/hook-weight` | `-5` | Execution order (lower = first) |
| `helm.sh/hook-delete-policy` | `hook-succeeded` | Delete Job after success |
| | `before-hook-creation` | Delete old Job before creating new one |

---

### Chart Testing

```yaml
# templates/tests/test-connection.yaml
apiVersion: v1
kind: Pod
metadata:
  name: {{ include "deployforge.fullname" . }}-test-connection
  labels:
    {{- include "deployforge.labels" . | nindent 4 }}
  annotations:
    "helm.sh/hook": test
spec:
  restartPolicy: Never
  containers:
  - name: curl
    image: curlimages/curl:8.5.0
    command: ['curl']
    args:
    - '--fail'
    - '--silent'
    - '--show-error'
    - '--max-time'
    - '10'
    - 'http://{{ include "deployforge.fullname" . }}:{{ .Values.apiGateway.port }}/health'
```

```bash
# Run Helm tests after install
helm test deployforge -n deployforge
# NAME:   deployforge
# STATUS: deployed
# TEST SUITE:     deployforge-test-connection
# Last Started:   Mon Jan 15 10:30:00 2024
# Last Completed: Mon Jan 15 10:30:02 2024
# Phase:          Succeeded
```

---

### Helm CLI Workflows

```bash
# --- Repository Management ---
helm repo add bitnami https://charts.bitnami.com/bitnami
helm repo update
helm search repo bitnami/postgresql --versions

# --- Dependency Management ---
cd deployforge/
helm dependency update                # Download subchart tarballs to charts/
helm dependency list                  # Show dependency status

# --- Template Rendering (debug without installing) ---
helm template deployforge . \
  --namespace deployforge \
  --values values-staging.yaml \
  --set image.tag=abc123 \
  --debug > rendered.yaml            # Inspect the full output

# --- Linting ---
helm lint .                           # Check for errors and warnings
helm lint . --values values-prod.yaml # Lint with specific values

# --- Install ---
helm install deployforge . \
  --namespace deployforge \
  --create-namespace \
  --values values.yaml \
  --set image.tag=v1.2.0 \
  --wait --timeout 5m                # Wait for all resources to be ready

# --- Upgrade ---
helm upgrade deployforge . \
  --namespace deployforge \
  --values values.yaml \
  --set image.tag=v1.3.0 \
  --atomic                           # Auto-rollback on failure

# --- Rollback ---
helm history deployforge -n deployforge
# REVISION  STATUS      CHART             APP VERSION  DESCRIPTION
# 1         superseded  deployforge-0.6.0  1.1.0       Install complete
# 2         superseded  deployforge-0.7.0  1.2.0       Upgrade complete
# 3         deployed    deployforge-0.7.0  1.3.0       Upgrade complete

helm rollback deployforge 2 -n deployforge
# Rolled back to revision 2 (v1.2.0)

# --- Diff (requires helm-diff plugin) ---
helm plugin install https://github.com/databus23/helm-diff
helm diff upgrade deployforge . \
  --namespace deployforge \
  --values values-prod.yaml \
  --set image.tag=v1.4.0             # Preview changes before applying

# --- Uninstall ---
helm uninstall deployforge -n deployforge
```

---

### OCI-Based Chart Registries

Helm 3.8+ supports storing charts in OCI (container) registries — the same registries that hold your Docker images:

```bash
# Login to an OCI registry
helm registry login ghcr.io -u myuser

# Package and push a chart
helm package .                        # → deployforge-0.7.0.tgz
helm push deployforge-0.7.0.tgz oci://ghcr.io/myorg/charts

# Install from OCI registry
helm install deployforge oci://ghcr.io/myorg/charts/deployforge \
  --version 0.7.0 \
  --namespace deployforge
```

---

## Code Examples

### Environment-Specific Values Files

```yaml
# values-prod.yaml — production overrides
replicaCount: 3

image:
  tag: "v1.2.0"                       # Pinned version, no "latest"
  pullPolicy: Always

apiGateway:
  logLevel: "warn"
  logFormat: "json"
  workerConcurrency: 10
  databasePoolSize: 25

worker:
  replicas: 5
  concurrency: 10

ingress:
  enabled: true
  className: "nginx"
  annotations:
    cert-manager.io/cluster-issuer: "letsencrypt-prod"
    nginx.ingress.kubernetes.io/rate-limit: "100"
  hosts:
    - host: deployforge.example.com
      paths:
        - path: /
          pathType: Prefix
  tls:
    - secretName: deployforge-tls
      hosts:
        - deployforge.example.com

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

persistence:
  postgres:
    storageClass: "gp3-encrypted"
    size: 100Gi
  redis:
    storageClass: "gp3-encrypted"
    size: 20Gi

externalSecrets:
  enabled: true
  secretStore: "vault-backend"
  refreshInterval: "30m"

postgresql:
  enabled: false                       # Use managed RDS in production

redis:
  enabled: false                       # Use ElastiCache in production
```

```bash
# Deploy to production with prod values
helm upgrade --install deployforge . \
  --namespace deployforge \
  --values values.yaml \
  --values values-prod.yaml \
  --set image.tag=$CI_COMMIT_SHA \
  --atomic \
  --timeout 10m \
  --wait
```

---

## Try It Yourself

### Challenge 1: Create a Minimal Helm Chart

Use `helm create` to scaffold a chart, strip it down to the essentials, and deploy a simple nginx-based service. Customize it with your own values.

<details>
<summary>Show solution</summary>

```bash
#!/bin/bash
set -euo pipefail

echo "=== Step 1: Scaffold the chart ==="
helm create my-first-chart
cd my-first-chart

echo ""
echo "=== Step 2: Inspect the structure ==="
find . -type f | head -20

echo ""
echo "=== Step 3: Customize values.yaml ==="
cat > values.yaml <<'EOF'
replicaCount: 2

image:
  repository: nginx
  pullPolicy: IfNotPresent
  tag: "1.25-alpine"

service:
  type: ClusterIP
  port: 80

ingress:
  enabled: false

resources:
  requests:
    cpu: 50m
    memory: 64Mi
  limits:
    cpu: 100m
    memory: 128Mi

autoscaling:
  enabled: false
EOF

echo ""
echo "=== Step 4: Lint the chart ==="
cd ..
helm lint my-first-chart

echo ""
echo "=== Step 5: Render templates (dry run) ==="
helm template my-release my-first-chart --namespace deployforge | head -50

echo ""
echo "=== Step 6: Install ==="
helm install my-release my-first-chart \
  --namespace deployforge \
  --wait --timeout 2m

echo ""
echo "=== Step 7: Verify ==="
helm list -n deployforge
kubectl get all -n deployforge -l app.kubernetes.io/instance=my-release

echo ""
echo "=== Step 8: Test ==="
helm test my-release -n deployforge

echo ""
echo "=== Cleanup ==="
helm uninstall my-release -n deployforge
rm -rf my-first-chart
```

</details>

### Challenge 2: Add a Pre-Install Hook

Add a database migration Job as a pre-install/pre-upgrade hook to an existing chart. Verify it runs before the main Deployment starts.

<details>
<summary>Show solution</summary>

```yaml
# Save as my-chart/templates/hooks/pre-install-job.yaml
apiVersion: batch/v1
kind: Job
metadata:
  name: {{ include "my-first-chart.fullname" . }}-pre-install
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
      - name: migration
        image: busybox:1.36
        command: ['sh', '-c', 'echo "Running database migration..." && sleep 5 && echo "Migration complete!"']
```

```bash
# Install and watch the hook run first
helm install my-release my-first-chart \
  --namespace deployforge \
  --wait --timeout 2m

# Check hook execution
kubectl get jobs -n deployforge
# NAME                              COMPLETIONS   DURATION   AGE
# my-release-my-first-chart-pre-install   1/1     7s         30s

# The Job ran and completed before the Deployment was created
# With hook-succeeded delete policy, it's already cleaned up

helm uninstall my-release -n deployforge
```

</details>

---

## Capstone Connection

**DeployForge** is packaged as a Helm chart for distribution and deployment:

- **Chart structure** — The `deployforge/` chart includes templates for the API Gateway Deployment, Worker Deployment, PostgreSQL StatefulSet, Redis StatefulSet, Services, Ingress, ConfigMaps, Secrets, NetworkPolicies, HPA, and ServiceAccounts. Subchart dependencies on Bitnami PostgreSQL and Redis are conditional — enabled in dev, disabled in prod (where managed services are used).
- **Values-driven environments** — `values.yaml` provides sane defaults for local development. `values-staging.yaml` and `values-prod.yaml` override replicas, resource limits, log levels, and enable features like autoscaling, Ingress with TLS, and External Secrets. CI/CD pipelines pass `--set image.tag=$GIT_SHA` for immutable deployments.
- **Hooks for migrations** — A pre-install/pre-upgrade hook runs database migrations (`npm run db:migrate`) before the new application version starts. The hook has a `hook-delete-policy: before-hook-creation,hook-succeeded` to keep the cluster clean.
- **Chart testing** — `helm test` runs a curl pod that verifies `/health` returns 200. In CI, this gates the promotion from staging to production.
- **OCI registry** — The chart is published to `oci://ghcr.io/deployforge/charts/deployforge` on every tagged release. Teams pull the chart from the registry, not from a Git checkout.
- **Helm + Kustomize** — In Module 07.4, you'll see how Kustomize overlays can post-process Helm-rendered output for additional environment-specific patches that don't fit neatly into values.yaml.
