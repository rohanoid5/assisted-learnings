# 10.2 — GitOps with ArgoCD

## Concept

GitOps answers a deceptively simple question: *what's running in my cluster right now?* If the answer requires SSH-ing into a box and running `kubectl get`, you've already lost. In a GitOps workflow, the answer is always "whatever's in the Git repo" — because an operator continuously reconciles cluster state against the declared state in version control.

This is more than automation. It's an audit trail, a rollback mechanism, and a security boundary rolled into one. No human ever runs `kubectl apply` against production. Instead, they open a pull request, get it reviewed, merge it, and a controller (ArgoCD) does the rest. If someone mutates the cluster directly, ArgoCD detects the drift and heals it.

This section covers the GitOps model, ArgoCD's architecture, and how to structure repositories so that Git truly is the single source of truth for your infrastructure.

---

## Deep Dive

### The Four Principles of GitOps

GitOps was formalized by Weaveworks (now part of the CNCF ecosystem). The model rests on four pillars:

```
┌──────────────────────────────────────────────────────────────┐
│                   The Four GitOps Principles                  │
├──────────────────────────────────────────────────────────────┤
│                                                               │
│   1. DECLARATIVE                                              │
│      The entire system is described declaratively.            │
│      (Kubernetes manifests, Helm charts, Kustomize overlays)  │
│                                                               │
│   2. VERSIONED & IMMUTABLE                                    │
│      The desired state is stored in Git — versioned,          │
│      signed, and immutable. Every change is a commit.         │
│                                                               │
│   3. PULLED AUTOMATICALLY                                     │
│      Agents pull desired state and apply it.                  │
│      No external CI system pushes to the cluster.             │
│                                                               │
│   4. CONTINUOUSLY RECONCILED                                  │
│      Agents observe actual state and correct drift.           │
│      The system self-heals toward the declared state.         │
│                                                               │
└──────────────────────────────────────────────────────────────┘
```

> **Key insight:** The "pull" model is what separates GitOps from traditional CI/CD. In push-based CD, a CI pipeline has cluster credentials and runs `kubectl apply`. In GitOps, the cluster pulls its own state — the CI system never touches the cluster. This drastically reduces the blast radius of a compromised CI pipeline.

### Push vs Pull Deployment

```
┌─────────────────────────────────┐  ┌─────────────────────────────────┐
│     Push-Based CD (Traditional) │  │     Pull-Based CD (GitOps)      │
├─────────────────────────────────┤  ├─────────────────────────────────┤
│                                  │  │                                  │
│  CI Pipeline                     │  │  CI Pipeline                     │
│      │                           │  │      │                           │
│      │ kubectl apply             │  │      │ git push (manifests)      │
│      │ (has cluster creds)       │  │      │ (no cluster creds)        │
│      ▼                           │  │      ▼                           │
│  ┌──────────┐                    │  │  ┌──────────┐                    │
│  │ Cluster  │                    │  │  │ Git Repo │                    │
│  └──────────┘                    │  │  └─────┬────┘                    │
│                                  │  │        │                         │
│  ✗ CI needs cluster access       │  │        │ watches (pull)          │
│  ✗ No drift detection            │  │        ▼                         │
│  ✗ No self-healing               │  │  ┌──────────┐                    │
│  ✗ Audit = CI logs only          │  │  │ ArgoCD   │ (in-cluster)       │
│                                  │  │  │ applies  │                    │
│                                  │  │  └─────┬────┘                    │
│                                  │  │        ▼                         │
│                                  │  │  ┌──────────┐                    │
│                                  │  │  │ Cluster  │                    │
│                                  │  │  └──────────┘                    │
│                                  │  │                                  │
│                                  │  │  ✓ CI has no cluster access      │
│                                  │  │  ✓ Drift detection built-in      │
│                                  │  │  ✓ Self-healing on drift         │
│                                  │  │  ✓ Audit = git log               │
└─────────────────────────────────┘  └─────────────────────────────────┘
```

### ArgoCD Architecture

ArgoCD runs inside the target cluster as a set of Kubernetes controllers:

```
┌───────────────────────────────────────────────────────────────┐
│                     ArgoCD Architecture                        │
├───────────────────────────────────────────────────────────────┤
│                                                                │
│  ┌─────────────────────────────────────────────────────────┐  │
│  │                    argocd-server                          │  │
│  │  (API Server + Web UI)                                    │  │
│  │  - Serves the dashboard on :443                           │  │
│  │  - Handles CLI/API requests                               │  │
│  │  - Manages RBAC and SSO                                   │  │
│  └──────────────────────┬──────────────────────────────────┘  │
│                          │                                     │
│  ┌───────────────────────┼──────────────────────────────────┐ │
│  │                       ▼                                   │ │
│  │  ┌──────────────────────────────────────┐                │ │
│  │  │       argocd-application-controller   │                │ │
│  │  │                                       │                │ │
│  │  │  - Watches Application CRDs           │                │ │
│  │  │  - Compares desired vs live state     │                │ │
│  │  │  - Executes sync operations           │                │ │
│  │  │  - Runs health checks                 │                │ │
│  │  └───────────────┬──────────────────────┘                │ │
│  │                   │                                       │ │
│  │  ┌────────────────▼─────────────────────┐                │ │
│  │  │        argocd-repo-server             │                │ │
│  │  │                                       │                │ │
│  │  │  - Clones Git repos                   │                │ │
│  │  │  - Renders Helm/Kustomize templates   │                │ │
│  │  │  - Caches manifests                   │                │ │
│  │  └──────────────────────────────────────┘                │ │
│  │                                                           │ │
│  │  ┌──────────────────────────────────────┐                │ │
│  │  │     argocd-applicationset-controller  │                │ │
│  │  │                                       │                │ │
│  │  │  - Generates Applications from        │                │ │
│  │  │    templates (Git, list, cluster)     │                │ │
│  │  └──────────────────────────────────────┘                │ │
│  └──────────────────────────────────────────────────────────┘ │
│                                                                │
│  ┌──────────────────────────────────────────────────────────┐ │
│  │                    argocd-redis                            │ │
│  │  (Cache for repo-server and controller)                   │ │
│  └──────────────────────────────────────────────────────────┘ │
└───────────────────────────────────────────────────────────────┘
```

| Component | Purpose | Scaling Notes |
|-----------|---------|---------------|
| **argocd-server** | API, Web UI, CLI gateway | Stateless — scale horizontally behind a load balancer |
| **application-controller** | Reconciliation loop, sync, health | Sharding by cluster or app for large installations |
| **repo-server** | Git clone, Helm/Kustomize render | CPU-intensive — scale based on repo count and render complexity |
| **applicationset-controller** | Template-based Application generation | Lightweight — one replica is usually sufficient |
| **redis** | In-memory cache | Single instance; consider HA Redis for large deployments |

### Application CRD

The `Application` is ArgoCD's fundamental resource. It maps a Git repo path to a cluster namespace:

```yaml
apiVersion: argoproj.io/v1alpha1
kind: Application
metadata:
  name: deployforge
  namespace: argocd
  finalizers:
    - resources-finalizer.argocd.argoproj.io
spec:
  project: default

  source:
    repoURL: https://github.com/org/deployforge-gitops.git
    targetRevision: main
    path: environments/production

    # For Helm-based sources:
    # helm:
    #   valueFiles:
    #     - values-production.yaml

    # For Kustomize-based sources:
    # kustomize:
    #   namePrefix: prod-

  destination:
    server: https://kubernetes.default.svc
    namespace: deployforge

  syncPolicy:
    automated:
      prune: true              # ← delete resources removed from Git
      selfHeal: true           # ← revert manual cluster changes
      allowEmpty: false        # ← don't sync if Git renders zero resources
    syncOptions:
      - CreateNamespace=true
      - PrunePropagationPolicy=foreground
      - PruneLast=true
    retry:
      limit: 5
      backoff:
        duration: 5s
        factor: 2
        maxDuration: 3m
```

> **Production note:** `prune: true` is powerful and dangerous. It means if you accidentally delete a manifest file from Git, ArgoCD will delete the corresponding resource from the cluster. Always pair it with `allowEmpty: false` so a misconfigured render (producing zero manifests) doesn't wipe your namespace.

### Sync Strategies: Auto vs Manual

| Strategy | When to Use | Risk Level |
|----------|-------------|------------|
| **Auto sync + self-heal** | Non-production environments, stateless apps | Low (self-correcting) |
| **Auto sync, no self-heal** | Staging environments | Medium (manual changes persist until next Git push) |
| **Manual sync** | Production databases, stateful workloads | Lowest (human approves every change) |

```yaml
# Auto sync with self-heal (dev/staging)
syncPolicy:
  automated:
    prune: true
    selfHeal: true

# Manual sync (production databases)
syncPolicy: {}
# Sync triggered via: argocd app sync <app-name>
```

### Repository Structure — App of Apps Pattern

For managing dozens of applications, the **app-of-apps** pattern uses one ArgoCD Application to manage other Application CRDs:

```
deployforge-gitops/
├── apps/                              ← "root" app points here
│   ├── api.yaml                       ← Application CRD for API service
│   ├── worker.yaml                    ← Application CRD for worker service
│   ├── postgres.yaml                  ← Application CRD for database
│   └── monitoring.yaml                ← Application CRD for Prometheus stack
├── environments/
│   ├── base/                          ← shared manifests (Kustomize base)
│   │   ├── kustomization.yaml
│   │   ├── deployment.yaml
│   │   ├── service.yaml
│   │   └── ingress.yaml
│   ├── staging/                       ← staging overrides
│   │   ├── kustomization.yaml
│   │   └── patches/
│   │       └── replicas.yaml
│   └── production/                    ← production overrides
│       ├── kustomization.yaml
│       └── patches/
│           ├── replicas.yaml
│           ├── resources.yaml
│           └── hpa.yaml
└── argocd/
    ├── root-app.yaml                  ← the one app ArgoCD watches
    └── appproject.yaml
```

The root Application watches the `apps/` directory:

```yaml
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
```

Each file in `apps/` is itself an Application CRD:

```yaml
# apps/api.yaml
apiVersion: argoproj.io/v1alpha1
kind: Application
metadata:
  name: deployforge-api
  namespace: argocd
spec:
  project: deployforge
  source:
    repoURL: https://github.com/org/deployforge-gitops.git
    targetRevision: main
    path: environments/production
  destination:
    server: https://kubernetes.default.svc
    namespace: deployforge
  syncPolicy:
    automated:
      prune: true
      selfHeal: true
```

> **Key insight:** The app-of-apps pattern gives you a single point of onboarding. Adding a new microservice to ArgoCD = adding one YAML file to the `apps/` directory. No CLI commands, no UI clicks — just a Git commit.

### ApplicationSet for Dynamic Generation

When app-of-apps feels too manual, ApplicationSet generates Applications from templates:

```yaml
apiVersion: argoproj.io/v1alpha1
kind: ApplicationSet
metadata:
  name: deployforge-services
  namespace: argocd
spec:
  generators:
    - git:
        repoURL: https://github.com/org/deployforge-gitops.git
        revision: main
        directories:
          - path: services/*
  template:
    metadata:
      name: '{{path.basename}}'
    spec:
      project: deployforge
      source:
        repoURL: https://github.com/org/deployforge-gitops.git
        targetRevision: main
        path: '{{path}}'
      destination:
        server: https://kubernetes.default.svc
        namespace: '{{path.basename}}'
      syncPolicy:
        automated:
          prune: true
          selfHeal: true
        syncOptions:
          - CreateNamespace=true
```

This automatically creates an ArgoCD Application for every directory under `services/`. Add a new service directory → new Application appears.

### Secrets in GitOps

Secrets are the hard problem. You can't commit plaintext secrets to Git, but GitOps demands everything be in Git. Solutions:

| Tool | Approach | Pros | Cons |
|------|----------|------|------|
| **Sealed Secrets** | Encrypt with cluster's public key; controller decrypts | Simple, no external deps | Secrets tied to one cluster |
| **SOPS** | Encrypt with KMS/PGP; decrypt at render time | Multi-cloud KMS support | Requires key management |
| **External Secrets Operator** | Sync from Vault/AWS SM/GCP SM | Secrets stay in vault | Additional component to manage |
| **Vault Agent Injector** | Sidecar injects secrets at pod start | Dynamic secrets, rotation | Complex setup, adds latency |

**Sealed Secrets** is the most common GitOps-native approach:

```bash
# Install kubeseal CLI
brew install kubeseal

# Fetch the cluster's public certificate
kubeseal --fetch-cert \
  --controller-name=sealed-secrets \
  --controller-namespace=kube-system \
  > pub-cert.pem

# Create a sealed secret from a regular secret
kubectl create secret generic db-credentials \
  --from-literal=username=deployforge \
  --from-literal=password=supersecret \
  --dry-run=client -o yaml | \
  kubeseal --format yaml \
    --cert pub-cert.pem \
    > sealed-db-credentials.yaml
```

```yaml
# sealed-db-credentials.yaml — safe to commit to Git
apiVersion: bitnami.com/v1alpha1
kind: SealedSecret
metadata:
  name: db-credentials
  namespace: deployforge
spec:
  encryptedData:
    username: AgA3...base64...==
    password: AgBx...base64...==
  template:
    metadata:
      name: db-credentials
      namespace: deployforge
    type: Opaque
```

> **Caution:** SealedSecrets are encrypted for a specific cluster's key pair. If you rebuild the cluster, you must back up the controller's private key or re-encrypt all secrets.

### Drift Detection and Self-Healing

ArgoCD continuously compares the live state in the cluster against the desired state in Git. When they diverge, ArgoCD reports the Application as **OutOfSync**.

```
┌──────────────────────────────────────────────────────────────┐
│               ArgoCD Reconciliation Loop                      │
├──────────────────────────────────────────────────────────────┤
│                                                               │
│   ┌──────────┐     compare      ┌──────────────┐            │
│   │ Git Repo │ ──────────────── │ Live Cluster  │            │
│   │ (desired)│                  │ (actual)      │            │
│   └──────────┘                  └──────────────┘            │
│        │                              │                      │
│        └──────────┬───────────────────┘                      │
│                   ▼                                           │
│        ┌──────────────────┐                                  │
│        │   States Match?   │                                  │
│        └────────┬─────────┘                                  │
│            ┌────┴────┐                                       │
│           Yes        No                                      │
│            │          │                                       │
│            ▼          ▼                                       │
│        ┌────────┐ ┌────────────────┐                        │
│        │ Synced │ │  OutOfSync     │                        │
│        │ ✓      │ │  selfHeal=true │──▶ auto-revert         │
│        └────────┘ │  selfHeal=false│──▶ alert + wait        │
│                   └────────────────┘                        │
│                                                               │
│   Default reconciliation interval: 3 minutes                  │
│   Webhook-triggered reconciliation: ~seconds                  │
└──────────────────────────────────────────────────────────────┘
```

Configure webhook-triggered sync for near-instant reconciliation:

```yaml
# On the Git repo, add a webhook:
# URL:  https://argocd.example.com/api/webhook
# Type: application/json
# Events: push
```

ArgoCD also performs **health checks** beyond simple Kubernetes readiness:

| Resource | Health Check | Healthy When |
|----------|-------------|-------------|
| Deployment | Rollout complete | All replicas updated, available, and ready |
| StatefulSet | All pods running | ordinal-indexed pods match desired count |
| Ingress | LB assigned | `status.loadBalancer.ingress` is non-empty |
| Job | Completed | `.status.succeeded >= 1` |
| PVC | Bound | `.status.phase == Bound` |

### Multi-Cluster Management

ArgoCD can manage workloads across multiple clusters from a single control plane:

```bash
# Add an external cluster
argocd cluster add staging-context --name staging

# List managed clusters
argocd cluster list
# SERVER                          NAME       STATUS
# https://kubernetes.default.svc  in-cluster Successful
# https://staging.k8s.example.com staging    Successful
```

Use AppProject to scope which clusters and namespaces each team can deploy to:

```yaml
apiVersion: argoproj.io/v1alpha1
kind: AppProject
metadata:
  name: deployforge
  namespace: argocd
spec:
  description: DeployForge project
  sourceRepos:
    - 'https://github.com/org/deployforge-gitops.git'
  destinations:
    - server: https://kubernetes.default.svc
      namespace: deployforge
    - server: https://kubernetes.default.svc
      namespace: deployforge-staging
  clusterResourceWhitelist:
    - group: ''
      kind: Namespace
  namespaceResourceBlacklist:
    - group: ''
      kind: ResourceQuota
  roles:
    - name: deployer
      description: Can sync applications
      policies:
        - p, proj:deployforge:deployer, applications, sync, deployforge/*, allow
        - p, proj:deployforge:deployer, applications, get, deployforge/*, allow
      groups:
        - deployforge-team
```

---

## Code Examples

### Installing ArgoCD on a kind Cluster

```bash
#!/bin/bash
set -euo pipefail

# Create a kind cluster if not already running
kind create cluster --name argocd-demo 2>/dev/null || true

# Install ArgoCD
kubectl create namespace argocd 2>/dev/null || true
kubectl apply -n argocd \
  -f https://raw.githubusercontent.com/argoproj/argo-cd/stable/manifests/install.yaml

# Wait for ArgoCD to be ready
echo "Waiting for ArgoCD pods..."
kubectl wait --for=condition=Ready pod \
  -l app.kubernetes.io/part-of=argocd \
  -n argocd \
  --timeout=300s

# Get the initial admin password
ARGOCD_PASSWORD=$(kubectl -n argocd get secret argocd-initial-admin-secret \
  -o jsonpath="{.data.password}" | base64 -d)
echo "ArgoCD admin password: ${ARGOCD_PASSWORD}"

# Port-forward the ArgoCD UI
echo "Access ArgoCD at https://localhost:8080"
echo "Username: admin"
kubectl port-forward svc/argocd-server -n argocd 8080:443 &
```

### Deploying an Application via ArgoCD CLI

```bash
# Install the ArgoCD CLI
brew install argocd

# Login to ArgoCD
argocd login localhost:8080 \
  --username admin \
  --password "${ARGOCD_PASSWORD}" \
  --insecure

# Create an Application
argocd app create deployforge \
  --repo https://github.com/org/deployforge-gitops.git \
  --path environments/production \
  --dest-server https://kubernetes.default.svc \
  --dest-namespace deployforge \
  --sync-policy automated \
  --auto-prune \
  --self-heal

# Check application status
argocd app get deployforge
# Name:               deployforge
# Server:             https://kubernetes.default.svc
# Namespace:          deployforge
# Sync Status:        Synced
# Health Status:      Healthy

# View sync history
argocd app history deployforge
# ID  DATE                 REVISION
# 1   2024-01-15 14:30:22  abc1234 (main)
# 2   2024-01-15 15:45:10  def5678 (main)

# Manual sync (for manual sync policy)
argocd app sync deployforge

# Rollback to a previous revision
argocd app rollback deployforge 1
```

### Installing Sealed Secrets

```bash
#!/bin/bash
set -euo pipefail

# Install Sealed Secrets controller
helm repo add sealed-secrets https://bitnami-labs.github.io/sealed-secrets
helm repo update

helm install sealed-secrets sealed-secrets/sealed-secrets \
  --namespace kube-system \
  --set fullnameOverride=sealed-secrets

# Wait for controller
kubectl wait --for=condition=Ready pod \
  -l app.kubernetes.io/name=sealed-secrets \
  -n kube-system \
  --timeout=120s

# Install kubeseal CLI
brew install kubeseal

# Verify connectivity
kubeseal --fetch-cert \
  --controller-name=sealed-secrets \
  --controller-namespace=kube-system \
  > /dev/null && echo "✓ Sealed Secrets is working"
```

### Configuring ArgoCD Notifications

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: argocd-notifications-cm
  namespace: argocd
data:
  service.slack: |
    token: $slack-token
  template.app-sync-succeeded: |
    message: |
      ✅ {{.app.metadata.name}} synced successfully
      Revision: {{.app.status.sync.revision}}
      Health: {{.app.status.health.status}}
  template.app-sync-failed: |
    message: |
      ❌ {{.app.metadata.name}} sync failed
      Revision: {{.app.status.sync.revision}}
      Message: {{.app.status.conditions | first | default "unknown"}}
  trigger.on-sync-succeeded: |
    - when: app.status.operationState.phase in ['Succeeded']
      send: [app-sync-succeeded]
  trigger.on-sync-failed: |
    - when: app.status.operationState.phase in ['Error', 'Failed']
      send: [app-sync-failed]
```

Annotate your Application to enable notifications:

```yaml
apiVersion: argoproj.io/v1alpha1
kind: Application
metadata:
  name: deployforge
  namespace: argocd
  annotations:
    notifications.argoproj.io/subscribe.on-sync-succeeded.slack: deployments
    notifications.argoproj.io/subscribe.on-sync-failed.slack: deployments
```

---

## Try It Yourself

### Challenge 1: Design a GitOps Repo Structure

You have three services (api, worker, scheduler) deployed to two environments (staging, production). Design the directory structure for a GitOps repo using Kustomize overlays.

<details>
<summary>Show solution</summary>

```
deployforge-gitops/
├── apps/                            # ArgoCD Application CRDs
│   ├── staging/
│   │   ├── api.yaml
│   │   ├── worker.yaml
│   │   └── scheduler.yaml
│   └── production/
│       ├── api.yaml
│       ├── worker.yaml
│       └── scheduler.yaml
├── base/                            # Shared manifests
│   ├── api/
│   │   ├── kustomization.yaml
│   │   ├── deployment.yaml
│   │   ├── service.yaml
│   │   └── serviceaccount.yaml
│   ├── worker/
│   │   ├── kustomization.yaml
│   │   ├── deployment.yaml
│   │   └── serviceaccount.yaml
│   └── scheduler/
│       ├── kustomization.yaml
│       ├── cronjob.yaml
│       └── serviceaccount.yaml
├── overlays/
│   ├── staging/
│   │   ├── api/
│   │   │   ├── kustomization.yaml   # patches: 1 replica, debug logging
│   │   │   └── patches/
│   │   │       └── env.yaml
│   │   ├── worker/
│   │   │   └── kustomization.yaml
│   │   └── scheduler/
│   │       └── kustomization.yaml
│   └── production/
│       ├── api/
│       │   ├── kustomization.yaml   # patches: 3 replicas, HPA, PDB
│       │   └── patches/
│       │       ├── replicas.yaml
│       │       ├── resources.yaml
│       │       └── hpa.yaml
│       ├── worker/
│       │   └── kustomization.yaml
│       └── scheduler/
│           └── kustomization.yaml
└── argocd/
    ├── root-app.yaml
    └── appproject.yaml
```

Each staging Application CRD:
```yaml
# apps/staging/api.yaml
apiVersion: argoproj.io/v1alpha1
kind: Application
metadata:
  name: staging-api
  namespace: argocd
spec:
  project: deployforge
  source:
    repoURL: https://github.com/org/deployforge-gitops.git
    targetRevision: main
    path: overlays/staging/api
  destination:
    server: https://kubernetes.default.svc
    namespace: deployforge-staging
  syncPolicy:
    automated:
      prune: true
      selfHeal: true
    syncOptions:
      - CreateNamespace=true
```

</details>

### Challenge 2: Write a SealedSecret for Database Credentials

Create a SealedSecret manifest for DeployForge's database credentials (username, password, connection string). Include the `kubeseal` command chain to generate it.

<details>
<summary>Show solution</summary>

```bash
#!/bin/bash
set -euo pipefail

# Fetch the controller's public cert
kubeseal --fetch-cert \
  --controller-name=sealed-secrets \
  --controller-namespace=kube-system \
  > pub-cert.pem

# Create the sealed secret
kubectl create secret generic deployforge-db-credentials \
  --namespace=deployforge \
  --from-literal=DB_USER=deployforge \
  --from-literal=DB_PASSWORD='Pr0d-S3cur3-P@ss!' \
  --from-literal=DB_HOST=postgres.deployforge.svc.cluster.local \
  --from-literal=DB_PORT=5432 \
  --from-literal=DB_NAME=deployforge_prod \
  --from-literal=DATABASE_URL='postgresql://deployforge:Pr0d-S3cur3-P@ss!@postgres.deployforge.svc.cluster.local:5432/deployforge_prod' \
  --dry-run=client -o yaml | \
  kubeseal \
    --format yaml \
    --cert pub-cert.pem \
    --scope namespace-wide \
  > deploy/sealed-secrets/db-credentials.yaml

echo "✓ Sealed secret written to deploy/sealed-secrets/db-credentials.yaml"
echo "  This file is safe to commit to Git."

# Verify it can be applied (dry-run)
kubectl apply -f deploy/sealed-secrets/db-credentials.yaml --dry-run=server
```

The `--scope namespace-wide` flag means the SealedSecret can be renamed within its namespace but not moved to another namespace. This is a good balance between flexibility and security.

</details>

### Challenge 3: Configure Drift Detection Alerting

Set up ArgoCD so that when someone runs a manual `kubectl edit` on a production deployment (causing drift), it: (a) self-heals within 30 seconds, (b) sends a Slack notification identifying what changed.

<details>
<summary>Show solution</summary>

```yaml
# Application with self-heal and notifications
apiVersion: argoproj.io/v1alpha1
kind: Application
metadata:
  name: deployforge-prod
  namespace: argocd
  annotations:
    notifications.argoproj.io/subscribe.on-health-degraded.slack: alerts
    notifications.argoproj.io/subscribe.on-sync-status-unknown.slack: alerts
spec:
  project: deployforge
  source:
    repoURL: https://github.com/org/deployforge-gitops.git
    targetRevision: main
    path: overlays/production/api
  destination:
    server: https://kubernetes.default.svc
    namespace: deployforge
  syncPolicy:
    automated:
      prune: true
      selfHeal: true       # ← reverts manual changes
    syncOptions:
      - RespectIgnoreDifferences=true
```

To reduce self-heal latency below the default 3 minutes, configure ArgoCD's reconciliation timeout:

```yaml
# argocd-cm ConfigMap
apiVersion: v1
kind: ConfigMap
metadata:
  name: argocd-cm
  namespace: argocd
data:
  timeout.reconciliation: "30s"    # ← check every 30 seconds
```

Add a notification template for drift events:

```yaml
# argocd-notifications-cm
apiVersion: v1
kind: ConfigMap
metadata:
  name: argocd-notifications-cm
  namespace: argocd
data:
  template.drift-detected: |
    message: |
      ⚠️ Drift detected in {{.app.metadata.name}}
      Someone modified the cluster directly.
      ArgoCD self-healed to match Git state.
      Sync revision: {{.app.status.sync.revision}}
      Time: {{.app.status.operationState.finishedAt}}
  trigger.on-self-healed: |
    - when: app.status.operationState.phase in ['Succeeded'] and
            app.status.operationState.initiatedBy.automated == true
      send: [drift-detected]
```

With this configuration:
1. Manual `kubectl edit` causes drift
2. ArgoCD detects within 30 seconds (reduced reconciliation interval)
3. Self-heal reverts the change automatically
4. Slack notification fires identifying the auto-heal event

</details>

---

## Capstone Connection

**DeployForge** uses ArgoCD as the deployment backbone:

- **`deploy/argocd/application.yaml`** — An Application CRD pointing at DeployForge's GitOps repo. Auto-sync is enabled for staging; production uses manual sync with required approval.
- **App-of-apps pattern** — A root Application in `deploy/argocd/root-app.yaml` manages all DeployForge services. Adding a new microservice means adding one YAML file.
- **`deploy/argocd/appproject.yaml`** — An AppProject scoping DeployForge's allowed repos, destination clusters, and namespaces. Team members get `sync` and `get` permissions; only CI can update image tags.
- **Sealed Secrets** — Database credentials, API keys, and TLS certificates are stored as SealedSecrets in `deploy/sealed-secrets/`. The encryption key is backed up outside Git.
- **Drift detection** — Self-healing is enabled on all environments. The ArgoCD reconciliation interval is set to 30 seconds. Drift events trigger Slack notifications so the team can investigate who ran unauthorized `kubectl` commands.
- **Notifications** — ArgoCD sends Slack messages on sync success, sync failure, and health degradation. Production sync failures also page the on-call engineer.
