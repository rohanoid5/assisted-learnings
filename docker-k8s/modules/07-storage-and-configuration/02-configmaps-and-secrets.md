# 7.2 — ConfigMaps, Secrets & External Secrets

## Concept

Configuration is the connective tissue between your application code and its runtime environment. In Kubernetes, the primary tools are **ConfigMaps** (for non-sensitive configuration) and **Secrets** (for credentials and sensitive data). But vanilla Kubernetes Secrets have a dirty secret of their own: they're just base64-encoded, not encrypted. Production environments need something stronger — **External Secrets Operator** and **sealed-secrets** bridge the gap between Kubernetes' native primitives and enterprise secrets management systems like HashiCorp Vault, AWS Secrets Manager, and GCP Secret Manager.

Understanding when to use each approach — and the security implications of each — is what separates a development cluster from a production-hardened deployment.

---

## Deep Dive

### ConfigMaps: The Basics

A ConfigMap holds key-value pairs or entire files that are injected into pods as environment variables or mounted as files. They decouple configuration from container images, making the same image deployable across dev, staging, and production.

```
┌─────────────────────────────────────────────────────────────────────┐
│                  ConfigMap Injection Methods                          │
│                                                                     │
│  Method 1: Environment Variables                                     │
│  ┌──────────────┐     ┌──────────────┐                              │
│  │ ConfigMap     │     │ Pod          │                              │
│  │ LOG_LEVEL=info│────▶│ env:         │                              │
│  │ API_URL=...   │     │  LOG_LEVEL   │ = "info"                    │
│  └──────────────┘     │  API_URL     │ = "http://..."              │
│                       └──────────────┘                              │
│                                                                     │
│  Method 2: Volume Mount (files)                                      │
│  ┌──────────────┐     ┌──────────────┐                              │
│  │ ConfigMap     │     │ Pod          │                              │
│  │ nginx.conf    │────▶│ /etc/nginx/  │                              │
│  │ app.yaml      │     │  nginx.conf  │ ← full file content         │
│  └──────────────┘     │  app.yaml    │                              │
│                       └──────────────┘                              │
│                                                                     │
│  Method 3: envFrom (all keys as env vars)                            │
│  ┌──────────────┐     ┌──────────────┐                              │
│  │ ConfigMap     │     │ Pod          │                              │
│  │ KEY1=val1     │────▶│ env:         │                              │
│  │ KEY2=val2     │     │  KEY1=val1   │ ← every key becomes an      │
│  │ KEY3=val3     │     │  KEY2=val2   │   env var automatically     │
│  └──────────────┘     │  KEY3=val3   │                              │
│                       └──────────────┘                              │
└─────────────────────────────────────────────────────────────────────┘
```

#### Creating ConfigMaps

```yaml
# Method 1: From literal values in YAML
apiVersion: v1
kind: ConfigMap
metadata:
  name: deployforge-config
  namespace: deployforge
data:
  LOG_LEVEL: "info"
  API_PORT: "3000"
  WORKER_CONCURRENCY: "5"
  FEATURE_DARK_LAUNCH: "false"
  DATABASE_POOL_SIZE: "10"
```

```bash
# Method 2: From the command line
kubectl create configmap deployforge-config \
  --from-literal=LOG_LEVEL=info \
  --from-literal=API_PORT=3000 \
  --from-literal=WORKER_CONCURRENCY=5 \
  -n deployforge

# Method 3: From a file
kubectl create configmap nginx-config \
  --from-file=nginx.conf=./nginx.conf \
  -n deployforge

# Method 4: From an entire directory
kubectl create configmap app-configs \
  --from-file=./config/ \
  -n deployforge
```

#### Consuming ConfigMaps in Pods

```yaml
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
      containers:
      - name: api
        image: deployforge/api-gateway:latest
        # Method 1: Individual env vars from ConfigMap keys
        env:
        - name: LOG_LEVEL
          valueFrom:
            configMapKeyRef:
              name: deployforge-config
              key: LOG_LEVEL
        - name: API_PORT
          valueFrom:
            configMapKeyRef:
              name: deployforge-config
              key: API_PORT
        # Method 2: All keys as env vars
        envFrom:
        - configMapRef:
            name: deployforge-config
            # optional: true    ← pod starts even if ConfigMap doesn't exist
        # Method 3: Mount as files
        volumeMounts:
        - name: config-volume
          mountPath: /etc/deployforge
          readOnly: true
      volumes:
      - name: config-volume
        configMap:
          name: deployforge-config
          # Optional: select specific keys and set file permissions
          items:
          - key: LOG_LEVEL
            path: log-level.conf
          defaultMode: 0444
```

> **Key insight:** Environment variables from ConfigMaps are set at pod startup and don't update when the ConfigMap changes. Volume-mounted ConfigMaps _do_ update (within ~60s), but your application needs to watch the file or be signaled to reload.

---

### Secrets: Handling Sensitive Data

Kubernetes Secrets are structurally similar to ConfigMaps but have additional (if limited) security measures: values are base64-encoded, API access can be restricted via RBAC, and they can be encrypted at rest in etcd.

#### Secret Types

| Type | Usage | Example |
|------|-------|---------|
| `Opaque` | Arbitrary key-value data | DB passwords, API keys |
| `kubernetes.io/tls` | TLS certificate + key | Ingress TLS termination |
| `kubernetes.io/dockerconfigjson` | Docker registry credentials | Pulling from private registries |
| `kubernetes.io/basic-auth` | Username + password | Basic HTTP auth |
| `kubernetes.io/ssh-auth` | SSH private key | Git clone over SSH |
| `kubernetes.io/service-account-token` | Auto-generated SA token | (Managed by K8s) |

```yaml
# Opaque Secret — database credentials
apiVersion: v1
kind: Secret
metadata:
  name: deployforge-db-credentials
  namespace: deployforge
type: Opaque
stringData:                          # stringData accepts plain text (auto-encoded)
  DB_HOST: "postgres-headless.deployforge.svc.cluster.local"
  DB_PORT: "5432"
  DB_NAME: "deployforge"
  DB_USER: "deployforge_app"
  DB_PASSWORD: "change-me-in-production"

---
# TLS Secret for Ingress
apiVersion: v1
kind: Secret
metadata:
  name: deployforge-tls
  namespace: deployforge
type: kubernetes.io/tls
data:
  tls.crt: <base64-encoded-certificate>
  tls.key: <base64-encoded-private-key>

---
# Docker registry Secret
apiVersion: v1
kind: Secret
metadata:
  name: registry-credentials
  namespace: deployforge
type: kubernetes.io/dockerconfigjson
data:
  .dockerconfigjson: <base64-encoded-docker-config>
```

```bash
# Create Secrets from the CLI (preferred — avoids plain text in YAML)
kubectl create secret generic deployforge-db-credentials \
  --from-literal=DB_USER=deployforge_app \
  --from-literal=DB_PASSWORD='$(openssl rand -base64 24)' \
  -n deployforge

# Create TLS Secret from cert files
kubectl create secret tls deployforge-tls \
  --cert=./tls.crt --key=./tls.key \
  -n deployforge

# Create Docker registry Secret
kubectl create secret docker-registry registry-credentials \
  --docker-server=ghcr.io \
  --docker-username=myuser \
  --docker-password=mytoken \
  -n deployforge
```

#### Consuming Secrets in Pods

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: api-gateway
  namespace: deployforge
spec:
  template:
    spec:
      containers:
      - name: api
        image: deployforge/api-gateway:latest
        env:
        # Individual Secret keys as env vars
        - name: DB_PASSWORD
          valueFrom:
            secretKeyRef:
              name: deployforge-db-credentials
              key: DB_PASSWORD
        # All Secret keys as env vars
        envFrom:
        - secretRef:
            name: deployforge-db-credentials
        # Mount Secret as files (e.g., TLS certs)
        volumeMounts:
        - name: tls-certs
          mountPath: /etc/tls
          readOnly: true
      volumes:
      - name: tls-certs
        secret:
          secretName: deployforge-tls
          defaultMode: 0400            # Restrict file permissions
      # Pull from private registry using image pull Secret
      imagePullSecrets:
      - name: registry-credentials
```

---

### The Security Problem with Kubernetes Secrets

Kubernetes Secrets are **not encrypted by default**. They're base64-encoded in etcd, which is encoding, not encryption. Anyone with `kubectl get secret` permissions can decode them:

```bash
# This is trivial to reverse
kubectl get secret deployforge-db-credentials -n deployforge \
  -o jsonpath='{.data.DB_PASSWORD}' | base64 -d
```

**Mitigations (from least to most secure):**

```
┌─────────────────────────────────────────────────────────────────────┐
│              Secrets Security — Defense in Depth                      │
│                                                                     │
│  Level 1: RBAC (baseline)                                            │
│  ┌──────────────────────────────────────────────────────────────┐   │
│  │ Restrict who can `get`, `list`, `watch` Secrets               │   │
│  │ Deny Secret access in CI/CD service accounts                  │   │
│  │ Audit Secret access via API server audit logs                 │   │
│  └──────────────────────────────────────────────────────────────┘   │
│                                                                     │
│  Level 2: Encryption at Rest                                         │
│  ┌──────────────────────────────────────────────────────────────┐   │
│  │ EncryptionConfiguration on the API server                     │   │
│  │ Encrypts Secrets in etcd with AES-CBC or KMS provider         │   │
│  │ EKS/GKE/AKS do this by default with envelope encryption     │   │
│  └──────────────────────────────────────────────────────────────┘   │
│                                                                     │
│  Level 3: External Secrets (recommended)                             │
│  ┌──────────────────────────────────────────────────────────────┐   │
│  │ Secrets live in Vault / AWS SM / GCP SM — never in etcd       │   │
│  │ External Secrets Operator syncs them into K8s Secrets         │   │
│  │ Rotation happens at the source; K8s Secret auto-refreshes    │   │
│  └──────────────────────────────────────────────────────────────┘   │
│                                                                     │
│  Level 4: CSI Secret Store Driver                                    │
│  ┌──────────────────────────────────────────────────────────────┐   │
│  │ Secrets injected directly into pod filesystem via CSI         │   │
│  │ Never stored as K8s Secret objects at all                     │   │
│  │ Most secure, but more complex to set up                       │   │
│  └──────────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────────┘
```

---

### Immutable ConfigMaps and Secrets

Kubernetes 1.21+ supports immutable ConfigMaps and Secrets. Once marked immutable, they cannot be updated — only deleted and recreated. This provides:

1. **Protection against accidental changes** — a bad config push can't overwrite a running config
2. **Performance** — the kubelet doesn't need to watch for changes
3. **Scalability** — reduces API server load in large clusters with thousands of ConfigMaps

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: deployforge-config-v3
  namespace: deployforge
immutable: true                       # Cannot be modified after creation
data:
  LOG_LEVEL: "warn"
  API_PORT: "3000"
  CONFIG_VERSION: "v3"
```

> **Pattern:** Use versioned names (e.g., `deployforge-config-v3`) with immutable ConfigMaps. Deployments reference the specific version. Rolling to a new config means creating `v4` and updating the Deployment reference — which triggers a rolling update automatically.

---

### External Secrets Operator (ESO)

The External Secrets Operator syncs secrets from external providers (Vault, AWS Secrets Manager, GCP Secret Manager, Azure Key Vault) into Kubernetes Secret objects. It's the de facto standard for production secrets management.

```
┌─────────────────────────────────────────────────────────────────────┐
│              External Secrets Operator Architecture                   │
│                                                                     │
│  ┌──── External Provider ─────────┐                                 │
│  │  HashiCorp Vault               │                                 │
│  │  secret/data/deployforge/db    │                                 │
│  │  ┌───────────────────────┐     │                                 │
│  │  │ username: deploy_app   │     │                                 │
│  │  │ password: s3cur3!pass  │     │                                 │
│  │  └───────────────────────┘     │                                 │
│  └────────────────┬───────────────┘                                 │
│                   │                                                  │
│                   │ ESO polls every refreshInterval                  │
│                   ▼                                                  │
│  ┌──── Kubernetes Cluster ────────────────────────────────────────┐ │
│  │                                                                 │ │
│  │  SecretStore / ClusterSecretStore                               │ │
│  │  (connection config for the provider)                           │ │
│  │         │                                                       │ │
│  │         ▼                                                       │ │
│  │  ExternalSecret CR                                              │ │
│  │  (defines which external secrets to sync)                       │ │
│  │         │                                                       │ │
│  │         │ ESO Controller reconciles                             │ │
│  │         ▼                                                       │ │
│  │  Kubernetes Secret                                              │ │
│  │  (auto-created and kept in sync)                                │ │
│  │  ┌───────────────────────┐                                      │ │
│  │  │ DB_USER: deploy_app    │                                      │ │
│  │  │ DB_PASSWORD: s3cur3!..│                                      │ │
│  │  └───────────────────────┘                                      │ │
│  │         │                                                       │ │
│  │         ▼                                                       │ │
│  │  Pod (mounts Secret as env/volume)                              │ │
│  └─────────────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────────┘
```

#### Setting Up ESO with HashiCorp Vault

```bash
# Install External Secrets Operator via Helm
helm repo add external-secrets https://charts.external-secrets.io
helm repo update
helm install external-secrets external-secrets/external-secrets \
  -n external-secrets --create-namespace
```

```yaml
# ClusterSecretStore — cluster-wide connection to Vault
apiVersion: external-secrets.io/v1beta1
kind: ClusterSecretStore
metadata:
  name: vault-backend
spec:
  provider:
    vault:
      server: "https://vault.example.com"
      path: "secret"
      version: "v2"
      auth:
        kubernetes:
          mountPath: "kubernetes"
          role: "deployforge"
          serviceAccountRef:
            name: "vault-auth"
            namespace: "external-secrets"

---
# ExternalSecret — sync specific secrets from Vault
apiVersion: external-secrets.io/v1beta1
kind: ExternalSecret
metadata:
  name: deployforge-db-credentials
  namespace: deployforge
spec:
  refreshInterval: 1h                # How often to re-sync from Vault
  secretStoreRef:
    name: vault-backend
    kind: ClusterSecretStore
  target:
    name: deployforge-db-credentials  # K8s Secret name to create
    creationPolicy: Owner             # ESO owns the Secret lifecycle
  data:
  - secretKey: DB_USER                # Key in K8s Secret
    remoteRef:
      key: secret/data/deployforge/db # Path in Vault
      property: username              # Field in Vault secret
  - secretKey: DB_PASSWORD
    remoteRef:
      key: secret/data/deployforge/db
      property: password
```

```bash
# Verify the ExternalSecret synced successfully
kubectl get externalsecret -n deployforge
# NAME                          STORE          REFRESH   STATUS
# deployforge-db-credentials    vault-backend  1h        SecretSynced

# The K8s Secret is auto-created
kubectl get secret deployforge-db-credentials -n deployforge
```

---

### Sealed Secrets for GitOps

Sealed Secrets solve a different problem: how do you commit secrets to Git safely? The Bitnami Sealed Secrets controller encrypts secrets with a cluster-specific key. Only the controller in _your_ cluster can decrypt them.

```bash
# Install sealed-secrets controller
helm repo add sealed-secrets https://bitnami-labs.github.io/sealed-secrets
helm install sealed-secrets sealed-secrets/sealed-secrets \
  -n kube-system

# Install kubeseal CLI
brew install kubeseal

# Create a regular Secret, then seal it
kubectl create secret generic deployforge-db-credentials \
  --from-literal=DB_PASSWORD='super-secret' \
  --dry-run=client -o yaml | \
  kubeseal --format yaml > sealed-db-credentials.yaml
```

```yaml
# sealed-db-credentials.yaml — safe to commit to Git
apiVersion: bitnami.com/v1alpha1
kind: SealedSecret
metadata:
  name: deployforge-db-credentials
  namespace: deployforge
spec:
  encryptedData:
    DB_PASSWORD: AgBy8h...long-encrypted-string...==
  template:
    metadata:
      name: deployforge-db-credentials
      namespace: deployforge
    type: Opaque
```

```bash
# Apply the SealedSecret — controller decrypts and creates the K8s Secret
kubectl apply -f sealed-db-credentials.yaml
kubectl get secret deployforge-db-credentials -n deployforge
```

> **GitOps workflow:** SealedSecrets live in your Git repo alongside other manifests. When ArgoCD or Flux applies them, the controller decrypts them into regular Secrets. Rotation means re-sealing with `kubeseal` and committing the new SealedSecret.

---

## Code Examples

### Complete ConfigMap + Secret Setup for DeployForge

```yaml
# deployforge-config.yaml
---
# Application configuration (non-sensitive)
apiVersion: v1
kind: ConfigMap
metadata:
  name: deployforge-config
  namespace: deployforge
data:
  LOG_LEVEL: "info"
  LOG_FORMAT: "json"
  API_PORT: "3000"
  WORKER_PORT: "4000"
  WORKER_CONCURRENCY: "5"
  DATABASE_POOL_SIZE: "10"
  DATABASE_POOL_TIMEOUT: "30000"
  REDIS_DB: "0"
  FEATURE_DARK_LAUNCH: "false"
  FEATURE_CANARY_DEPLOYMENTS: "true"
  CORS_ORIGINS: "https://deployforge.example.com"

---
# Nginx configuration file
apiVersion: v1
kind: ConfigMap
metadata:
  name: deployforge-nginx-config
  namespace: deployforge
data:
  nginx.conf: |
    worker_processes auto;
    events {
        worker_connections 1024;
    }
    http {
        upstream api {
            server api-gateway:3000;
        }
        server {
            listen 80;
            server_name deployforge.local;

            location /api/ {
                proxy_pass http://api;
                proxy_set_header Host $host;
                proxy_set_header X-Real-IP $remote_addr;
            }

            location /health {
                return 200 'OK';
                add_header Content-Type text/plain;
            }
        }
    }

---
# Database credentials (use External Secrets in production)
apiVersion: v1
kind: Secret
metadata:
  name: deployforge-db-credentials
  namespace: deployforge
type: Opaque
stringData:
  DB_HOST: "postgres-headless.deployforge.svc.cluster.local"
  DB_PORT: "5432"
  DB_NAME: "deployforge"
  DB_USER: "deployforge_app"
  DB_PASSWORD: "local-dev-only-change-in-prod"

---
# Redis credentials
apiVersion: v1
kind: Secret
metadata:
  name: deployforge-redis-credentials
  namespace: deployforge
type: Opaque
stringData:
  REDIS_HOST: "redis-headless.deployforge.svc.cluster.local"
  REDIS_PORT: "6379"
  REDIS_PASSWORD: "local-dev-redis-password"

---
# API keys and tokens
apiVersion: v1
kind: Secret
metadata:
  name: deployforge-api-keys
  namespace: deployforge
type: Opaque
stringData:
  GITHUB_WEBHOOK_SECRET: "dev-webhook-secret"
  JWT_SECRET: "dev-jwt-secret-change-in-prod"
  ENCRYPTION_KEY: "dev-encryption-key-32-chars-long!"
```

```bash
# Apply all configuration
kubectl apply -f deployforge-config.yaml

# Verify
kubectl get configmaps -n deployforge
kubectl get secrets -n deployforge
kubectl describe configmap deployforge-config -n deployforge
```

### Deployment Using Both ConfigMaps and Secrets

```yaml
# api-gateway-deployment.yaml
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
      annotations:
        # Force rolling update when ConfigMap changes
        checksum/config: "{{ include (print $.Template.BasePath \"/configmap.yaml\") . | sha256sum }}"
    spec:
      containers:
      - name: api
        image: deployforge/api-gateway:latest
        ports:
        - containerPort: 3000
        # Non-sensitive config from ConfigMap
        envFrom:
        - configMapRef:
            name: deployforge-config
        # Sensitive config from Secrets
        - secretRef:
            name: deployforge-db-credentials
        - secretRef:
            name: deployforge-redis-credentials
        # Individual sensitive values
        env:
        - name: JWT_SECRET
          valueFrom:
            secretKeyRef:
              name: deployforge-api-keys
              key: JWT_SECRET
        # Mount nginx config as a file
        volumeMounts:
        - name: nginx-config
          mountPath: /etc/nginx/nginx.conf
          subPath: nginx.conf
          readOnly: true
      volumes:
      - name: nginx-config
        configMap:
          name: deployforge-nginx-config
```

---

## Try It Yourself

### Challenge 1: ConfigMap Hot Reload

Create a ConfigMap mounted as a volume. Update the ConfigMap and observe that the file changes inside the running pod _without_ a restart. Then do the same with an env var and confirm it does NOT update.

<details>
<summary>Show solution</summary>

```bash
#!/bin/bash
set -euo pipefail

NS="deployforge"

echo "=== Step 1: Create ConfigMap and Pod ==="
cat <<'EOF' | kubectl apply -f -
apiVersion: v1
kind: ConfigMap
metadata:
  name: hot-reload-test
  namespace: deployforge
data:
  config.txt: "version=1"
  ENV_VALUE: "original"
---
apiVersion: v1
kind: Pod
metadata:
  name: config-watcher
  namespace: deployforge
spec:
  containers:
  - name: watcher
    image: busybox:1.36
    command: ['sh', '-c', 'while true; do echo "File: $(cat /config/config.txt) | Env: $ENV_VALUE"; sleep 5; done']
    env:
    - name: ENV_VALUE
      valueFrom:
        configMapKeyRef:
          name: hot-reload-test
          key: ENV_VALUE
    volumeMounts:
    - name: config
      mountPath: /config
  volumes:
  - name: config
    configMap:
      name: hot-reload-test
EOF

kubectl wait --for=condition=Ready pod/config-watcher -n $NS --timeout=60s
sleep 3
echo "Before update:"
kubectl logs config-watcher -n $NS --tail=1

echo ""
echo "=== Step 2: Update ConfigMap ==="
kubectl patch configmap hot-reload-test -n $NS \
  --type merge -p '{"data":{"config.txt":"version=2","ENV_VALUE":"updated"}}'

echo "Waiting 90s for volume update to propagate..."
sleep 90

echo ""
echo "After update:"
kubectl logs config-watcher -n $NS --tail=1
# File: version=2 | Env: original
# ↑ file updated!    ↑ env var NOT updated (requires pod restart)

echo ""
echo "=== Cleanup ==="
kubectl delete pod config-watcher -n $NS --grace-period=0
kubectl delete configmap hot-reload-test -n $NS
```

</details>

### Challenge 2: Create a Secret and Verify Encoding

Create a Secret using `stringData`, inspect the stored `data` (base64-encoded), and decode it. Then restrict access using RBAC so only the `deployforge` ServiceAccount can read it.

<details>
<summary>Show solution</summary>

```bash
#!/bin/bash
set -euo pipefail

NS="deployforge"

echo "=== Step 1: Create Secret with stringData ==="
cat <<'EOF' | kubectl apply -f -
apiVersion: v1
kind: Secret
metadata:
  name: encoding-test
  namespace: deployforge
type: Opaque
stringData:
  API_KEY: "my-super-secret-api-key-12345"
  DB_PASSWORD: "p@ssw0rd!with-special-chars"
EOF

echo ""
echo "=== Step 2: Inspect stored data (base64-encoded) ==="
echo "Raw data field:"
kubectl get secret encoding-test -n $NS -o jsonpath='{.data.API_KEY}'
echo ""

echo ""
echo "Decoded:"
kubectl get secret encoding-test -n $NS \
  -o jsonpath='{.data.API_KEY}' | base64 -d
echo ""

echo ""
echo "=== Step 3: Create RBAC to restrict access ==="
cat <<'EOF' | kubectl apply -f -
apiVersion: v1
kind: ServiceAccount
metadata:
  name: deployforge-app
  namespace: deployforge
---
apiVersion: rbac.authorization.k8s.io/v1
kind: Role
metadata:
  name: secret-reader
  namespace: deployforge
rules:
- apiGroups: [""]
  resources: ["secrets"]
  resourceNames: ["encoding-test"]
  verbs: ["get"]
---
apiVersion: rbac.authorization.k8s.io/v1
kind: RoleBinding
metadata:
  name: deployforge-secret-reader
  namespace: deployforge
subjects:
- kind: ServiceAccount
  name: deployforge-app
  namespace: deployforge
roleRef:
  kind: Role
  name: secret-reader
  apiGroup: rbac.authorization.k8s.io
EOF

echo ""
echo "RBAC created — only deployforge-app SA can read encoding-test Secret"
kubectl get role,rolebinding -n $NS

echo ""
echo "=== Cleanup ==="
kubectl delete secret encoding-test -n $NS
kubectl delete sa deployforge-app -n $NS
kubectl delete role secret-reader -n $NS
kubectl delete rolebinding deployforge-secret-reader -n $NS
```

</details>

---

## Capstone Connection

**DeployForge** uses a layered configuration management strategy:

- **ConfigMaps for app config** — Feature flags, log levels, API URLs, concurrency settings, and CORS origins are stored in `deployforge-config`. Each environment (dev/staging/prod) has its own ConfigMap with environment-specific values, managed via Kustomize overlays (Module 07.4).
- **Secrets for credentials** — Database passwords, Redis passwords, JWT secrets, and webhook signing keys are stored in Kubernetes Secrets. In the kind dev environment, these are created from `stringData` manifests. In production, they're synced from HashiCorp Vault via External Secrets Operator.
- **External Secrets Operator** — Production DeployForge clusters use ESO with a `ClusterSecretStore` pointing to Vault. `ExternalSecret` CRs define the mapping from Vault paths to K8s Secret keys. Rotation happens in Vault; ESO re-syncs every hour.
- **Immutable ConfigMaps** — DeployForge uses versioned, immutable ConfigMaps (e.g., `deployforge-config-v12`) in production. The Helm chart (Module 07.3) auto-generates the version suffix from a content hash, ensuring Deployments roll when config changes.
- **Nginx config** — The reverse proxy configuration is a ConfigMap mounted as a file. Changes to routing rules are applied by updating the ConfigMap and triggering a rolling restart via `kubectl rollout restart`.
- **RBAC lockdown** — Only the `deployforge-app` ServiceAccount can read Secrets in the `deployforge` namespace. CI/CD service accounts have zero Secret access.
