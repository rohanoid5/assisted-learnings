# 4.3 — kubectl, API Server & Resource Model

## Concept

`kubectl` is your primary interface to Kubernetes. But it's not magic — every `kubectl` command translates to an HTTP request to the API server's REST API. Understanding this translation — how kubectl reads your kubeconfig, constructs HTTP requests, and interprets API responses — gives you debugging superpowers.

When a pod won't start, when RBAC blocks your deployment, when a webhook rejects your manifest — knowing how to trace the API request flow lets you pinpoint the failure instantly instead of guessing.

---

## Deep Dive

### kubectl Internals

#### kubeconfig

kubectl uses a **kubeconfig** file (default: `~/.kube/config`) to know _which_ cluster to talk to, _how_ to authenticate, and _which_ namespace to use.

```yaml
apiVersion: v1
kind: Config

# Clusters define API server endpoints
clusters:
- cluster:
    server: https://127.0.0.1:6443
    certificate-authority-data: <base64-encoded-ca-cert>
  name: kind-deployforge

# Users define authentication credentials
users:
- name: kind-deployforge
  user:
    client-certificate-data: <base64-encoded-client-cert>
    client-key-data: <base64-encoded-client-key>

# Contexts bind a cluster + user + namespace
contexts:
- context:
    cluster: kind-deployforge
    user: kind-deployforge
    namespace: deployforge
  name: deployforge-dev

# Current context determines what kubectl uses by default
current-context: deployforge-dev
```

```bash
# View current context
kubectl config current-context

# List all contexts
kubectl config get-contexts

# Switch context
kubectl config use-context deployforge-dev

# Set a default namespace for the current context
kubectl config set-context --current --namespace=deployforge

# View the full kubeconfig (be careful — contains credentials)
kubectl config view
```

#### How kubectl Builds Requests

Under the hood, kubectl uses the **client-go** library to:

1. Read kubeconfig → determine API server URL + credentials
2. Map the command to an HTTP verb + resource path
3. Serialize the request (JSON or protobuf)
4. Send the HTTP request with authentication headers
5. Deserialize the response

```
kubectl get pods -n deployforge nginx-abc123
   │
   └──▶  GET https://127.0.0.1:6443/api/v1/namespaces/deployforge/pods/nginx-abc123
         Headers:
           Authorization: Bearer <token>  (or client cert via TLS)
           Accept: application/json

kubectl apply -f deployment.yaml
   │
   └──▶  PATCH https://127.0.0.1:6443/apis/apps/v1/namespaces/default/deployments/my-app
         Headers:
           Content-Type: application/apply-patch+yaml  (server-side apply)
         Body: <deployment YAML>
```

---

### API Request Flow

Every request to the API server passes through the same pipeline (detailed in 4.1). Here's the full picture from kubectl's perspective:

```
┌──────────────────────────────────────────────────────────────────────┐
│                    Full API Request Lifecycle                          │
│                                                                      │
│  kubectl                                                             │
│    │                                                                 │
│    │  1. Read kubeconfig (cluster, user, namespace)                  │
│    │  2. Build HTTP request                                          │
│    │  3. TLS handshake with API server                               │
│    │                                                                 │
│    ▼                                                                 │
│  API Server                                                          │
│    │                                                                 │
│    │  4. Authentication — verify identity (cert, token, OIDC)        │
│    │  5. Authorization — check RBAC (Role/ClusterRole bindings)      │
│    │  6. Mutating Admission — webhooks can modify the request        │
│    │  7. Schema Validation — check against OpenAPI spec              │
│    │  8. Validating Admission — webhooks can reject the request      │
│    │  9. Persist to etcd — store the resource (with resourceVersion) │
│    │                                                                 │
│    ▼                                                                 │
│  etcd                                                                │
│    │                                                                 │
│    │  10. Write confirmed (Raft quorum)                              │
│    │                                                                 │
│    ▼                                                                 │
│  API Server                                                          │
│    │                                                                 │
│    │  11. Return response to kubectl (with resourceVersion)          │
│    │  12. Notify watchers (controllers, kubelet via informers)       │
│    │                                                                 │
│    ▼                                                                 │
│  kubectl                                                             │
│       13. Display formatted output                                   │
└──────────────────────────────────────────────────────────────────────┘
```

---

### API Groups, Versions, and Resources (GVR)

Every Kubernetes resource is identified by three components:

| Component | Example | What It Represents |
|-----------|---------|-------------------|
| **Group** | `apps`, `batch`, `""` (core) | Category of resources |
| **Version** | `v1`, `v1beta1` | API stability level |
| **Resource** | `deployments`, `pods`, `services` | The resource type |

The API path is constructed from GVR:

```
Core group (empty string):
  /api/v1/namespaces/{ns}/pods/{name}
  /api/v1/namespaces/{ns}/services/{name}
  /api/v1/nodes/{name}

Named groups:
  /apis/apps/v1/namespaces/{ns}/deployments/{name}
  /apis/batch/v1/namespaces/{ns}/jobs/{name}
  /apis/networking.k8s.io/v1/namespaces/{ns}/ingresses/{name}
  /apis/rbac.authorization.k8s.io/v1/clusterroles/{name}
```

```bash
# Discover all API groups
kubectl api-versions

# List all resource types
kubectl api-resources

# List resources in a specific API group
kubectl api-resources --api-group=apps

# Get the preferred version for a resource
kubectl explain deployment
# → VERSION: apps/v1

# See the full GVR path for any resource
kubectl get --raw /apis/apps/v1 | python3 -m json.tool | head -30
```

#### Version Stability

| Level | Meaning | Example |
|-------|---------|---------|
| `v1` | Stable, GA — won't change | `apps/v1`, `v1` (core) |
| `v1beta1` | Beta — API may change, features may be incomplete | `flowcontrol.apiserver.k8s.io/v1beta1` |
| `v1alpha1` | Alpha — experimental, may be removed entirely | Feature-gated APIs |

---

### kubectl Verbs — The Essential Toolkit

#### Reading Resources

```bash
# List pods (default namespace)
kubectl get pods

# List pods in all namespaces with extra info
kubectl get pods -A -o wide

# Describe a resource (events, conditions, full details)
kubectl describe pod nginx-abc123

# Get raw YAML for a resource (great for debugging)
kubectl get deployment my-app -o yaml

# Get specific fields with JSONPath
kubectl get pods -o jsonpath='{range .items[*]}{.metadata.name}{"\t"}{.status.phase}{"\n"}{end}'

# Custom columns for tabular output
kubectl get pods -o custom-columns=\
NAME:.metadata.name,\
STATUS:.status.phase,\
NODE:.spec.nodeName,\
IP:.status.podIP,\
RESTARTS:.status.containerStatuses[0].restartCount

# Watch for changes in real time
kubectl get pods --watch

# Sort by a field
kubectl get pods --sort-by=.status.startTime

# Filter by label
kubectl get pods -l app=deployforge,tier=backend

# Filter by field
kubectl get pods --field-selector status.phase=Running
```

#### Debugging

```bash
# View container logs
kubectl logs my-pod
kubectl logs my-pod -c sidecar          # specific container
kubectl logs my-pod --previous          # logs from crashed container
kubectl logs -l app=deployforge --tail=50  # logs from all pods with label
kubectl logs my-pod -f                  # follow (tail -f)

# Exec into a running container
kubectl exec -it my-pod -- /bin/sh
kubectl exec -it my-pod -c sidecar -- /bin/bash

# Port-forward to a pod or service
kubectl port-forward pod/my-pod 8080:80
kubectl port-forward svc/my-service 8080:80

# Debug a running pod (ephemeral debug container)
kubectl debug my-pod -it --image=busybox --target=app

# Debug a node
kubectl debug node/kind-worker -it --image=busybox

# Copy files to/from a container
kubectl cp my-pod:/var/log/app.log ./app.log
kubectl cp ./config.yaml my-pod:/etc/app/config.yaml

# Get events sorted by time
kubectl get events --sort-by=.lastTimestamp

# Top — resource usage (requires metrics-server)
kubectl top pods
kubectl top nodes
```

#### Creating and Modifying

```bash
# Imperative creation (quick testing)
kubectl run nginx --image=nginx:alpine
kubectl create deployment my-app --image=nginx:alpine --replicas=3
kubectl expose deployment my-app --port=80 --type=ClusterIP
kubectl create namespace deployforge

# Declarative creation (production)
kubectl apply -f deployment.yaml
kubectl apply -f k8s/                    # apply all files in a directory
kubectl apply -k overlays/production/    # apply with Kustomize

# Edit a resource in your editor
kubectl edit deployment my-app

# Patch a resource
kubectl patch deployment my-app -p '{"spec":{"replicas":5}}'

# Scale
kubectl scale deployment my-app --replicas=5

# Rollout management
kubectl rollout status deployment my-app
kubectl rollout history deployment my-app
kubectl rollout undo deployment my-app
kubectl rollout restart deployment my-app

# Delete
kubectl delete -f deployment.yaml
kubectl delete pod my-pod --grace-period=0 --force
```

---

### JSONPath and Custom Columns

JSONPath is your power tool for extracting specific data from kubectl output.

```bash
# Get all pod names
kubectl get pods -o jsonpath='{.items[*].metadata.name}'

# Get pod name and IP as a formatted table
kubectl get pods -o jsonpath='{range .items[*]}{.metadata.name}{"\t"}{.status.podIP}{"\n"}{end}'

# Get the restart count of the first container in each pod
kubectl get pods -o jsonpath='{range .items[*]}{.metadata.name}{"\t"}{.status.containerStatuses[0].restartCount}{"\n"}{end}'

# Get nodes that are Ready
kubectl get nodes -o jsonpath='{range .items[*]}{.metadata.name}{"\t"}{range .status.conditions[?(@.type=="Ready")]}{.status}{end}{"\n"}{end}'

# Get all container images in the cluster
kubectl get pods -A -o jsonpath='{range .items[*]}{range .spec.containers[*]}{.image}{"\n"}{end}{end}' | sort -u

# Custom columns — cleaner alternative for tabular output
kubectl get pods -o custom-columns=\
'NAME:.metadata.name,STATUS:.status.phase,RESTARTS:.status.containerStatuses[0].restartCount,AGE:.metadata.creationTimestamp'
```

---

### Server-Side Apply vs Client-Side Apply

Kubernetes supports two apply strategies:

| Feature | Client-Side Apply | Server-Side Apply |
|---------|------------------|------------------|
| Where merging happens | kubectl client | API server |
| Field ownership tracking | `kubectl.kubernetes.io/last-applied-configuration` annotation | Field manager metadata |
| Conflict detection | Limited (last-applied vs live) | Per-field ownership — detects conflicts from any source |
| Multi-tool support | Breaks if resources are modified by other tools | Works correctly with controllers, webhooks, etc. |
| Flag | `kubectl apply -f` (default < 1.22) | `kubectl apply -f --server-side` |

```bash
# Client-side apply (traditional — stores state in annotation)
kubectl apply -f deployment.yaml

# Server-side apply (recommended — proper field ownership)
kubectl apply -f deployment.yaml --server-side

# Force server-side apply (take ownership of conflicting fields)
kubectl apply -f deployment.yaml --server-side --force-conflicts

# See field managers and ownership
kubectl get deployment my-app -o yaml | grep -A 5 managedFields
```

> **Production note:** Server-side apply is the future. It properly handles conflicts when multiple tools (kubectl, Helm, ArgoCD, controllers) modify the same resource. Use `--server-side` for any CI/CD pipeline or GitOps workflow.

---

### Dry-Run Modes

Test your manifests without actually applying them:

```bash
# Client-side dry-run — validates YAML syntax only (no server validation)
kubectl apply -f deployment.yaml --dry-run=client -o yaml

# Server-side dry-run — validates against API server
# (runs authentication, authorization, admission, validation — but doesn't persist)
kubectl apply -f deployment.yaml --dry-run=server -o yaml

# Diff — show what would change (like terraform plan)
kubectl diff -f deployment.yaml
```

| Mode | Auth | Admission | Validation | Persist |
|------|------|-----------|------------|---------|
| `--dry-run=client` | ❌ | ❌ | ❌ (syntax only) | ❌ |
| `--dry-run=server` | ✅ | ✅ | ✅ | ❌ |
| `kubectl diff` | ✅ | ✅ | ✅ | ❌ (shows diff) |

---

### kubectl Plugins and krew

kubectl supports plugins — any executable named `kubectl-<name>` in your PATH becomes a subcommand.

```bash
# Install krew (kubectl plugin manager)
# https://krew.sigs.k8s.io/docs/user-guide/setup/install/

# Search for plugins
kubectl krew search

# Install useful plugins
kubectl krew install ctx         # fast context switching
kubectl krew install ns          # fast namespace switching
kubectl krew install neat        # clean up YAML output
kubectl krew install tree        # show resource hierarchy
kubectl krew install images      # show container images
kubectl krew install node-shell  # shell into nodes

# Usage
kubectl ctx deployforge-dev      # switch context
kubectl ns deployforge           # switch namespace
kubectl neat get pod my-pod -o yaml  # clean YAML without managed fields
kubectl tree deployment my-app   # show deployment → replicaset → pods
```

---

### Verbose Logging

The `-v` flag controls kubectl's verbosity — essential for debugging API issues:

| Level | What It Shows |
|-------|-------------|
| `-v=0` | Default output |
| `-v=4` | Shows HTTP request URLs |
| `-v=6` | Shows HTTP request + response headers |
| `-v=8` | Shows HTTP request + response bodies (very verbose) |
| `-v=9` | Shows curl-equivalent commands |

```bash
# See the exact API call kubectl makes
kubectl get pods -v=8 2>&1 | head -20

# Output includes:
# GET https://127.0.0.1:6443/api/v1/namespaces/default/pods?limit=500
# Request Headers: ...
# Response Status: 200 OK
# Response Body: {"kind":"PodList",...}

# See the curl equivalent
kubectl get pods -v=9 2>&1 | grep "curl"
```

---

### Querying the API Server Directly

You can bypass kubectl entirely and talk to the API server with curl:

```bash
# Start a proxy (handles authentication for you)
kubectl proxy --port=8001 &

# Now query the API directly
curl http://localhost:8001/api/v1/namespaces/default/pods | python3 -m json.tool | head -20

# List all API groups
curl http://localhost:8001/apis | python3 -m json.tool

# Get a specific deployment
curl http://localhost:8001/apis/apps/v1/namespaces/default/deployments/my-app

# Watch for changes (server-sent events)
curl http://localhost:8001/api/v1/namespaces/default/pods?watch=true

# Stop the proxy
kill %1

# Without proxy — use the service account token (from inside a pod)
TOKEN=$(cat /var/run/secrets/kubernetes.io/serviceaccount/token)
APISERVER=https://kubernetes.default.svc
curl -s --cacert /var/run/secrets/kubernetes.io/serviceaccount/ca.crt \
  -H "Authorization: Bearer $TOKEN" \
  $APISERVER/api/v1/namespaces/default/pods
```

---

## Code Examples

### Example 1: kubectl with Verbose Logging

```bash
# Create a pod and trace every API call
kubectl run trace-demo --image=nginx:alpine -v=8 2>&1 | grep -E "(GET|POST|PATCH|PUT|DELETE|Response)"

# Watch the output — you'll see:
# POST https://127.0.0.1:6443/api/v1/namespaces/default/pods
# Response Status: 201 Created

# Now get the pod with full request details
kubectl get pod trace-demo -v=6 2>&1 | head -20

# Delete with verbose output
kubectl delete pod trace-demo -v=8 2>&1 | grep -E "(DELETE|Response)"
```

### Example 2: Discovering API Resources

```bash
# What resources exist in the apps group?
kubectl api-resources --api-group=apps -o wide

# What verbs are available for deployments?
kubectl api-resources --api-group=apps -o wide | grep deployments
# → deployments  deploy  apps/v1  true  Deployment
#   [create delete deletecollection get list patch update watch]

# Explore the schema of a resource
kubectl explain deployment.spec.strategy
kubectl explain deployment.spec.template.spec.containers.livenessProbe

# List all namespaced vs cluster-scoped resources
echo "=== Namespaced ==="
kubectl api-resources --namespaced=true | head -20
echo "=== Cluster-scoped ==="
kubectl api-resources --namespaced=false
```

### Example 3: Advanced Output Formatting

```bash
# Create some test resources
kubectl create deployment format-demo --image=nginx:alpine --replicas=3
sleep 10

# JSONPath: Get pod names and their node assignments
kubectl get pods -l app=format-demo \
  -o jsonpath='{range .items[*]}Pod: {.metadata.name} → Node: {.spec.nodeName}{"\n"}{end}'

# Custom columns: Show deployment rollout status
kubectl get deployments -o custom-columns=\
NAME:.metadata.name,\
DESIRED:.spec.replicas,\
CURRENT:.status.replicas,\
READY:.status.readyReplicas,\
UPDATED:.status.updatedReplicas,\
AVAILABLE:.status.availableReplicas,\
STRATEGY:.spec.strategy.type

# Go template: More complex formatting
kubectl get pods -l app=format-demo \
  -o go-template='{{range .items}}{{.metadata.name}} [{{.status.phase}}] on {{.spec.nodeName}}
{{end}}'

# YAML output piped through yq for specific fields
kubectl get deployment format-demo -o yaml | grep -A 3 "strategy:"

# Cleanup
kubectl delete deployment format-demo
```

### Example 4: Server-Side Apply and Conflict Detection

```bash
# Create a deployment with server-side apply
cat <<EOF | kubectl apply -f - --server-side --field-manager=tutorial
apiVersion: apps/v1
kind: Deployment
metadata:
  name: ssa-demo
  namespace: default
spec:
  replicas: 3
  selector:
    matchLabels:
      app: ssa-demo
  template:
    metadata:
      labels:
        app: ssa-demo
    spec:
      containers:
      - name: app
        image: nginx:alpine
EOF

# Now try to change replicas with a different field manager
# (simulates another tool like an autoscaler modifying the same resource)
cat <<EOF | kubectl apply -f - --server-side --field-manager=autoscaler
apiVersion: apps/v1
kind: Deployment
metadata:
  name: ssa-demo
  namespace: default
spec:
  replicas: 5
  selector:
    matchLabels:
      app: ssa-demo
  template:
    metadata:
      labels:
        app: ssa-demo
    spec:
      containers:
      - name: app
        image: nginx:alpine
EOF
# → Conflict! The "tutorial" field manager owns .spec.replicas

# Force it to take ownership
cat <<EOF | kubectl apply -f - --server-side --field-manager=autoscaler --force-conflicts
apiVersion: apps/v1
kind: Deployment
metadata:
  name: ssa-demo
spec:
  replicas: 5
  selector:
    matchLabels:
      app: ssa-demo
  template:
    metadata:
      labels:
        app: ssa-demo
    spec:
      containers:
      - name: app
        image: nginx:alpine
EOF

# Check who owns what
kubectl get deployment ssa-demo -o yaml | grep -A 20 "managedFields"

# Cleanup
kubectl delete deployment ssa-demo
```

---

## Try It Yourself

### Challenge 1: API Explorer

Without using `kubectl get`, use `kubectl proxy` and `curl` to: list all pods in the `kube-system` namespace, get the details of a specific pod, and watch for pod changes.

<details>
<summary>Show solution</summary>

```bash
# Start the kubectl proxy
kubectl proxy --port=8001 &
PROXY_PID=$!

# 1. List all pods in kube-system
echo "=== All kube-system pods ==="
curl -s http://localhost:8001/api/v1/namespaces/kube-system/pods | \
  python3 -c "
import json, sys
data = json.load(sys.stdin)
for pod in data['items']:
    name = pod['metadata']['name']
    phase = pod['status']['phase']
    print(f'  {name}: {phase}')
"

# 2. Get details of a specific pod (coredns)
COREDNS_POD=$(curl -s http://localhost:8001/api/v1/namespaces/kube-system/pods | \
  python3 -c "
import json, sys
data = json.load(sys.stdin)
for pod in data['items']:
    if 'coredns' in pod['metadata']['name']:
        print(pod['metadata']['name'])
        break
")

echo ""
echo "=== Details for $COREDNS_POD ==="
curl -s "http://localhost:8001/api/v1/namespaces/kube-system/pods/$COREDNS_POD" | \
  python3 -c "
import json, sys
pod = json.load(sys.stdin)
print(f\"  Name: {pod['metadata']['name']}\")
print(f\"  Node: {pod['spec']['nodeName']}\")
print(f\"  IP: {pod['status']['podIP']}\")
print(f\"  Phase: {pod['status']['phase']}\")
print(f\"  Containers: {[c['name'] for c in pod['spec']['containers']]}\")
"

# 3. Watch for pod changes (run for 10 seconds)
echo ""
echo "=== Watching for pod changes (10 seconds) ==="
timeout 10 curl -s "http://localhost:8001/api/v1/namespaces/default/pods?watch=true" | \
  while IFS= read -r line; do
    echo "$line" | python3 -c "
import json, sys
try:
    event = json.load(sys.stdin)
    print(f\"  {event['type']}: {event['object']['metadata']['name']}\")
except: pass
" 2>/dev/null
  done

# Cleanup
kill $PROXY_PID 2>/dev/null
echo "Done."
```

</details>

### Challenge 2: RBAC Detective

Create a ServiceAccount that can only list pods in a specific namespace. Verify it works and verify it can't do anything else.

<details>
<summary>Show solution</summary>

```bash
# Create a namespace and service account
kubectl create namespace rbac-test
kubectl create serviceaccount pod-reader -n rbac-test

# Create a Role that only allows listing pods
cat <<EOF | kubectl apply -f -
apiVersion: rbac.authorization.k8s.io/v1
kind: Role
metadata:
  name: pod-reader
  namespace: rbac-test
rules:
- apiGroups: [""]
  resources: ["pods"]
  verbs: ["get", "list", "watch"]
EOF

# Bind the role to the service account
cat <<EOF | kubectl apply -f -
apiVersion: rbac.authorization.k8s.io/v1
kind: RoleBinding
metadata:
  name: pod-reader-binding
  namespace: rbac-test
subjects:
- kind: ServiceAccount
  name: pod-reader
  namespace: rbac-test
roleRef:
  kind: Role
  name: pod-reader
  apiGroup: rbac.authorization.k8s.io
EOF

# Test: Can the service account list pods? (should succeed)
kubectl auth can-i list pods -n rbac-test --as=system:serviceaccount:rbac-test:pod-reader
# → yes

# Test: Can it delete pods? (should fail)
kubectl auth can-i delete pods -n rbac-test --as=system:serviceaccount:rbac-test:pod-reader
# → no

# Test: Can it list pods in default namespace? (should fail)
kubectl auth can-i list pods -n default --as=system:serviceaccount:rbac-test:pod-reader
# → no

# Test: Can it list deployments? (should fail)
kubectl auth can-i list deployments -n rbac-test --as=system:serviceaccount:rbac-test:pod-reader
# → no

# Actually use the service account to list pods
kubectl run test-pod --image=nginx:alpine -n rbac-test
kubectl get pods -n rbac-test --as=system:serviceaccount:rbac-test:pod-reader
# → Shows the test-pod

# Try to delete with the restricted account
kubectl delete pod test-pod -n rbac-test --as=system:serviceaccount:rbac-test:pod-reader
# → Error: pods "test-pod" is forbidden

# Cleanup
kubectl delete namespace rbac-test
```

</details>

### Challenge 3: Dry-Run and Diff

Create a deployment, then modify the YAML file. Use `--dry-run=server` and `kubectl diff` to preview the changes before applying them.

<details>
<summary>Show solution</summary>

```bash
# Step 1: Create the initial deployment
cat <<EOF > /dev/stdin | kubectl apply -f -
apiVersion: apps/v1
kind: Deployment
metadata:
  name: diff-demo
spec:
  replicas: 2
  selector:
    matchLabels:
      app: diff-demo
  template:
    metadata:
      labels:
        app: diff-demo
    spec:
      containers:
      - name: app
        image: nginx:1.24-alpine
        resources:
          requests:
            cpu: 100m
            memory: 64Mi
EOF

# Step 2: Prepare the updated version (change image + replicas)
cat <<EOF > diff-demo-updated.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: diff-demo
spec:
  replicas: 3
  selector:
    matchLabels:
      app: diff-demo
  template:
    metadata:
      labels:
        app: diff-demo
    spec:
      containers:
      - name: app
        image: nginx:1.25-alpine
        resources:
          requests:
            cpu: 200m
            memory: 128Mi
EOF

# Step 3: Server-side dry-run — see what the full result would look like
echo "=== Dry Run Output ==="
kubectl apply -f diff-demo-updated.yaml --dry-run=server -o yaml | \
  grep -E "(replicas:|image:|cpu:|memory:)"

# Step 4: Diff — see exactly what would change
echo ""
echo "=== Diff ==="
kubectl diff -f diff-demo-updated.yaml

# The diff shows:
# - replicas: 2 → 3
# - image: nginx:1.24-alpine → nginx:1.25-alpine
# - cpu: 100m → 200m
# - memory: 64Mi → 128Mi

# Step 5: Apply the changes for real
kubectl apply -f diff-demo-updated.yaml

# Verify
kubectl get deployment diff-demo -o wide

# Cleanup
kubectl delete deployment diff-demo
rm -f diff-demo-updated.yaml
```

</details>

---

## Capstone Connection

**DeployForge's** deployment workflow depends on understanding kubectl and the API model:

- **Server-side apply** is the recommended approach for DeployForge's CI/CD pipeline (Module 10). When ArgoCD and GitHub Actions both manage DeployForge manifests, server-side apply with distinct field managers prevents one tool from clobbering changes made by the other.
- **Dry-run and diff** are essential for the GitOps workflow. Before merging a change to DeployForge manifests, the CI pipeline runs `kubectl diff` to preview the impact — catching misconfigurations before they reach the cluster.
- **RBAC** becomes critical when DeployForge's services need to interact with the Kubernetes API. The Worker Service needs a ServiceAccount with permissions to list and watch Job resources but shouldn't be able to delete namespaces.
- **Verbose logging** (`-v=8`) is your first tool when debugging admission webhook failures. In Module 07, when a validating webhook rejects a DeployForge manifest, the verbose output shows you the exact webhook response with the rejection reason.
