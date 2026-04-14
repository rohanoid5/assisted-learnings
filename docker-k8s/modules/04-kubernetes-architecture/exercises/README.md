# Module 04 — Exercises

Hands-on practice with Kubernetes architecture, control plane components, and kubectl. These exercises build your intuition for how the cluster works by having you observe the machinery in action.

> **⚠️ Prerequisite:** You need Docker running locally and `kind` installed (`brew install kind`). All exercises run on a local kind cluster — no cloud account required.

---

## Exercise 1: Set Up a kind Cluster

**Goal:** Create a multi-node kind cluster (1 control-plane, 2 workers), verify all components are running, and explore the cluster architecture with kubectl.

### Steps

1. **Create a kind cluster configuration:**

```yaml
# Save as kind-config.yaml
kind: Cluster
apiVersion: kind.x-k8s.io/v1alpha4
name: deployforge
nodes:
- role: control-plane
- role: worker
- role: worker
```

2. **Create the cluster:**

```bash
kind create cluster --config kind-config.yaml

# Verify kubectl is configured
kubectl cluster-info
kubectl config current-context
# → kind-deployforge
```

3. **Explore the cluster:**

```bash
# List all nodes
kubectl get nodes -o wide

# Check control plane components
kubectl get pods -n kube-system

# You should see:
# - coredns (DNS for the cluster)
# - etcd (key-value store)
# - kube-apiserver
# - kube-controller-manager
# - kube-proxy (one per node)
# - kube-scheduler
# - kindnet (CNI plugin)
```

4. **Create the DeployForge namespace:**

```bash
kubectl create namespace deployforge

# Set it as the default for your context
kubectl config set-context --current --namespace=deployforge

# Verify
kubectl config get-contexts
```

5. **Verify the Docker containers backing the cluster:**

```bash
# kind uses Docker containers as "nodes"
docker ps --filter "name=deployforge"

# You should see 3 containers:
# - deployforge-control-plane
# - deployforge-worker
# - deployforge-worker2
```

### Verification

```bash
# All nodes should be Ready
kubectl get nodes
# → 3 nodes, all STATUS: Ready

# All system pods should be Running
kubectl get pods -n kube-system
# → All STATUS: Running

# The DeployForge namespace should exist
kubectl get namespace deployforge
# → STATUS: Active

# You should be in the deployforge context
kubectl config current-context
# → kind-deployforge
```

<details>
<summary>Show solution</summary>

```bash
#!/bin/bash
set -euo pipefail

echo "=== Step 1: Create kind cluster ==="
cat <<EOF > kind-config.yaml
kind: Cluster
apiVersion: kind.x-k8s.io/v1alpha4
name: deployforge
nodes:
- role: control-plane
- role: worker
- role: worker
EOF

# Delete existing cluster if present
kind delete cluster --name deployforge 2>/dev/null || true

kind create cluster --config kind-config.yaml
rm kind-config.yaml

echo ""
echo "=== Step 2: Verify cluster ==="
kubectl cluster-info
echo ""
kubectl get nodes -o wide

echo ""
echo "=== Step 3: Check control plane pods ==="
kubectl get pods -n kube-system -o wide

echo ""
echo "=== Step 4: Create DeployForge namespace ==="
kubectl create namespace deployforge
kubectl config set-context --current --namespace=deployforge

echo ""
echo "=== Step 5: Docker containers backing the cluster ==="
docker ps --filter "name=deployforge" --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"

echo ""
echo "=== Verification ==="
echo "Nodes:"
kubectl get nodes
echo ""
echo "System pods:"
kubectl get pods -n kube-system --no-headers | awk '{print $1, $3}'
echo ""
echo "Namespace:"
kubectl get namespace deployforge
echo ""
echo "Context:"
kubectl config current-context

echo ""
echo "✅ Cluster is ready for the remaining exercises!"
```

</details>

---

## Exercise 2: Trace a Request

**Goal:** Deploy a simple pod and trace its full lifecycle: kubectl → API server → etcd → scheduler → kubelet → container runtime. Use kubectl verbose logging and event watching to see every step.

### Steps

1. **Open two terminals. In Terminal 1, watch events:**

```bash
kubectl get events -n default --watch
```

2. **In Terminal 2, create a pod with verbose logging:**

```bash
# Create a pod with maximum verbosity
kubectl run trace-pod --image=nginx:alpine -n default -v=8 2>&1 | tee trace-output.txt
```

3. **Analyze the API request from the verbose output:**

```bash
# Find the HTTP request
grep "POST" trace-output.txt
# → POST https://127.0.0.1:6443/api/v1/namespaces/default/pods

# Find the response
grep "Response Status" trace-output.txt
# → 201 Created
```

4. **Watch the event sequence in Terminal 1:**

```
# You should see events in this order:
# 1. Scheduled — "Successfully assigned default/trace-pod to deployforge-worker"
# 2. Pulling — "Pulling image nginx:alpine"
# 3. Pulled — "Successfully pulled image nginx:alpine in 2.3s"
# 4. Created — "Created container trace-pod"
# 5. Started — "Started container trace-pod"
```

5. **Check the pod's status progression:**

```bash
# See the full status
kubectl describe pod trace-pod -n default

# Check which node it was scheduled to
kubectl get pod trace-pod -n default -o jsonpath='{.spec.nodeName}'

# Check the container state
kubectl get pod trace-pod -n default -o jsonpath='{.status.containerStatuses[0].state}'
```

6. **Verify the container is running on the node:**

```bash
# Check the container runtime on the node
NODE=$(kubectl get pod trace-pod -n default -o jsonpath='{.spec.nodeName}')
docker exec "$NODE" crictl ps | grep trace-pod
```

### Verification

You should be able to trace the complete lifecycle:

```
kubectl (HTTP POST) → API Server (201 Created)
                          │
                          ├── etcd (stored pod with nodeName="")
                          │
                          ├── Scheduler watches → assigns node → PATCH nodeName
                          │
                          ├── Kubelet on assigned node watches → sees new pod
                          │       │
                          │       ├── Pulls image
                          │       ├── Creates container via CRI
                          │       └── Starts container
                          │
                          └── Events recorded at each step
```

<details>
<summary>Show solution</summary>

```bash
#!/bin/bash
set -euo pipefail

echo "=== Tracing a Pod's Full Lifecycle ==="

# Step 1: Start event watcher in background
echo "Starting event watcher..."
kubectl get events -n default --watch &
WATCH_PID=$!
sleep 2

# Step 2: Create the pod with verbose logging
echo ""
echo "=== Creating pod with verbose logging ==="
kubectl run trace-pod --image=nginx:alpine -n default -v=8 2>&1 | \
  grep -E "(POST|GET|PATCH|Response Status|trace-pod)" | head -20

# Step 3: Wait for the pod to be running
echo ""
echo "=== Waiting for pod to be ready ==="
kubectl wait --for=condition=Ready pod/trace-pod -n default --timeout=60s

# Step 4: Analyze the lifecycle
echo ""
echo "=== Pod Events (in lifecycle order) ==="
kubectl get events -n default --field-selector involvedObject.name=trace-pod \
  --sort-by=.firstTimestamp

echo ""
echo "=== Pod Details ==="
NODE=$(kubectl get pod trace-pod -n default -o jsonpath='{.spec.nodeName}')
POD_IP=$(kubectl get pod trace-pod -n default -o jsonpath='{.status.podIP}')
echo "  Scheduled to node: $NODE"
echo "  Pod IP: $POD_IP"

echo ""
echo "=== Container on Node (via crictl) ==="
docker exec "$NODE" crictl ps | grep trace-pod

echo ""
echo "=== Container Details ==="
CONTAINER_ID=$(docker exec "$NODE" crictl ps --name trace-pod -q)
docker exec "$NODE" crictl inspect "$CONTAINER_ID" | \
  python3 -c "
import json, sys
data = json.load(sys.stdin)
info = data.get('info', {})
print(f\"  Container ID: {data['status']['id'][:12]}\")
print(f\"  State: {data['status']['state']}\")
print(f\"  Image: {data['status']['image']['image']}\")
print(f\"  Created: {data['status']['createdAt']}\")
" 2>/dev/null || echo "  (crictl inspect output not parseable — check manually)"

# Cleanup
kill $WATCH_PID 2>/dev/null
echo ""
echo "=== Lifecycle Summary ==="
echo "  1. kubectl sent POST to API server → 201 Created"
echo "  2. API server stored pod in etcd (nodeName empty)"
echo "  3. Scheduler assigned pod to $NODE"
echo "  4. Kubelet on $NODE pulled image and started container"
echo "  5. Pod running at IP $POD_IP"
echo ""
echo "Cleanup: kubectl delete pod trace-pod -n default"
```

</details>

---

## Exercise 3: Explore the Control Plane

**Goal:** Get inside the control plane components. Exec into the etcd container, list keys, and read a stored resource. Inspect the scheduler and controller-manager logs to see their decision-making.

### Steps

1. **Explore etcd:**

```bash
# Exec into the etcd pod
kubectl exec -it -n kube-system etcd-deployforge-control-plane -- sh

# Set up the etcdctl environment
export ETCDCTL_API=3
export ETCDCTL_ENDPOINTS=https://127.0.0.1:2379
export ETCDCTL_CACERT=/etc/kubernetes/pki/etcd/ca.crt
export ETCDCTL_CERT=/etc/kubernetes/pki/etcd/server.crt
export ETCDCTL_KEY=/etc/kubernetes/pki/etcd/server.key

# Check etcd health
etcdctl endpoint health

# Count total keys
etcdctl get / --prefix --keys-only | grep -c "/"

# List key prefixes (resource types)
etcdctl get / --prefix --keys-only | sed 's|/registry/||' | cut -d'/' -f1 | sort -u

# Find DeployForge namespace
etcdctl get / --prefix --keys-only | grep deployforge

# Check etcd cluster status
etcdctl endpoint status --write-out=table

# Exit
exit
```

2. **Inspect scheduler decisions:**

```bash
# Watch the scheduler logs while creating a deployment
kubectl logs -n kube-system -l component=kube-scheduler --tail=5

# Create a deployment
kubectl create deployment scheduler-explore --image=nginx:alpine --replicas=3 -n default

# Check which nodes the pods were assigned to
kubectl get pods -n default -l app=scheduler-explore -o wide

# See if the scheduler spread pods across nodes
kubectl get pods -n default -l app=scheduler-explore \
  -o jsonpath='{range .items[*]}{.metadata.name}{"\t"}{.spec.nodeName}{"\n"}{end}'
```

3. **Inspect controller-manager actions:**

```bash
# Scale down and watch the controller manager react
kubectl scale deployment scheduler-explore --replicas=1 -n default

# Check controller-manager logs for the scale-down
kubectl logs -n kube-system -l component=kube-controller-manager --tail=20 | \
  grep -i "scheduler-explore"

# Delete a pod and watch the ReplicaSet controller recreate it
POD=$(kubectl get pods -n default -l app=scheduler-explore -o jsonpath='{.items[0].metadata.name}')
kubectl delete pod "$POD" -n default

# Watch the replacement pod appear
kubectl get pods -n default -l app=scheduler-explore --watch
```

### Verification

```bash
# You should be able to answer:
# 1. How many keys are in etcd?
# 2. What resource types are stored?
# 3. Where is the DeployForge namespace stored?
# 4. Which nodes did the scheduler choose for the 3 replicas?
# 5. How quickly did the controller recreate the deleted pod?

# Cleanup
kubectl delete deployment scheduler-explore -n default
```

<details>
<summary>Show solution</summary>

```bash
#!/bin/bash
set -euo pipefail

echo "=== Exercise 3: Exploring the Control Plane ==="

# Part 1: etcd exploration
echo ""
echo "=== Part 1: etcd ==="
ETCD_OPTS="--endpoints=https://127.0.0.1:2379 \
  --cacert=/etc/kubernetes/pki/etcd/ca.crt \
  --cert=/etc/kubernetes/pki/etcd/server.crt \
  --key=/etc/kubernetes/pki/etcd/server.key"

echo "Health:"
kubectl exec -n kube-system etcd-deployforge-control-plane -- \
  etcdctl $ETCD_OPTS endpoint health

echo ""
echo "Total keys:"
kubectl exec -n kube-system etcd-deployforge-control-plane -- \
  etcdctl $ETCD_OPTS get / --prefix --keys-only 2>/dev/null | grep -c "/" || echo "0"

echo ""
echo "Resource types stored in etcd:"
kubectl exec -n kube-system etcd-deployforge-control-plane -- \
  etcdctl $ETCD_OPTS get / --prefix --keys-only 2>/dev/null | \
  sed '/^$/d' | sed 's|/registry/||' | cut -d'/' -f1 | sort -u

echo ""
echo "DeployForge-related keys:"
kubectl exec -n kube-system etcd-deployforge-control-plane -- \
  etcdctl $ETCD_OPTS get / --prefix --keys-only 2>/dev/null | grep deployforge

echo ""
echo "etcd cluster status:"
kubectl exec -n kube-system etcd-deployforge-control-plane -- \
  etcdctl $ETCD_OPTS endpoint status --write-out=table

# Part 2: Scheduler
echo ""
echo "=== Part 2: Scheduler Decisions ==="
kubectl create deployment scheduler-explore --image=nginx:alpine --replicas=3 -n default
sleep 10

echo "Pod placement:"
kubectl get pods -n default -l app=scheduler-explore \
  -o jsonpath='{range .items[*]}  {.metadata.name} → {.spec.nodeName}{"\n"}{end}'

# Part 3: Controller Manager
echo ""
echo "=== Part 3: Controller Manager ==="
echo "Scaling to 1 replica..."
kubectl scale deployment scheduler-explore --replicas=1 -n default
sleep 5

echo "Current pods after scale-down:"
kubectl get pods -n default -l app=scheduler-explore --no-headers

echo ""
echo "Deleting the remaining pod..."
POD=$(kubectl get pods -n default -l app=scheduler-explore -o jsonpath='{.items[0].metadata.name}')
kubectl delete pod "$POD" -n default --wait=false

echo "Watching for replacement (5 seconds)..."
sleep 5
kubectl get pods -n default -l app=scheduler-explore --no-headers
echo "  → ReplicaSet controller created a replacement pod"

# Cleanup
echo ""
echo "=== Cleanup ==="
kubectl delete deployment scheduler-explore -n default
echo "Done."
```

</details>

---

## Exercise 4: kubectl Power User

**Goal:** Practice advanced kubectl techniques: JSONPath queries, custom columns, debugging commands, and imperative-to-declarative workflow.

### Steps

1. **Set up test resources:**

```bash
# Create several deployments in the deployforge namespace
kubectl create deployment api-gateway --image=nginx:alpine --replicas=2 -n deployforge
kubectl create deployment worker --image=busybox:latest --replicas=1 -n deployforge -- sh -c "while true; do echo working; sleep 30; done"
kubectl create deployment redis --image=redis:7-alpine --replicas=1 -n deployforge

# Create services
kubectl expose deployment api-gateway --port=80 -n deployforge
kubectl expose deployment redis --port=6379 -n deployforge

# Wait for everything to be ready
sleep 15
```

2. **JSONPath queries:**

```bash
# List all pod names and their IPs
kubectl get pods -n deployforge \
  -o jsonpath='{range .items[*]}{.metadata.name}{"\t"}{.status.podIP}{"\n"}{end}'

# Get all container images across all namespaces
kubectl get pods -A \
  -o jsonpath='{range .items[*]}{range .spec.containers[*]}{.image}{"\n"}{end}{end}' | sort -u

# Get all pods that are Ready
kubectl get pods -n deployforge \
  -o jsonpath='{range .items[*]}{range .status.conditions[?(@.type=="Ready")]}{..metadata.name}: {.status}{"\n"}{end}{end}'

# Get service ClusterIPs
kubectl get svc -n deployforge \
  -o jsonpath='{range .items[*]}{.metadata.name}{"\t"}{.spec.clusterIP}{"\t"}{.spec.ports[0].port}{"\n"}{end}'
```

3. **Custom columns:**

```bash
# Rich pod view
kubectl get pods -n deployforge -o custom-columns=\
NAME:.metadata.name,\
STATUS:.status.phase,\
NODE:.spec.nodeName,\
IP:.status.podIP,\
RESTARTS:.status.containerStatuses[0].restartCount,\
IMAGE:.spec.containers[0].image

# Deployment rollout status
kubectl get deployments -n deployforge -o custom-columns=\
NAME:.metadata.name,\
DESIRED:.spec.replicas,\
READY:.status.readyReplicas,\
UPDATED:.status.updatedReplicas,\
AVAILABLE:.status.availableReplicas
```

4. **Debugging techniques:**

```bash
# Exec into the API gateway pod
kubectl exec -it -n deployforge deployment/api-gateway -- sh -c 'ls /etc/nginx && cat /etc/nginx/nginx.conf'

# Port-forward to test the service locally
kubectl port-forward -n deployforge svc/api-gateway 8080:80 &
PF_PID=$!
sleep 2
curl -s http://localhost:8080 | head -5
kill $PF_PID

# Check resource usage (if metrics-server is installed)
kubectl top pods -n deployforge 2>/dev/null || echo "metrics-server not installed — skip"

# Debug a pod with an ephemeral container
kubectl debug -n deployforge deployment/api-gateway -it --image=busybox -- sh -c 'wget -qO- localhost:80 | head -5'
```

5. **Imperative to declarative workflow:**

```bash
# Generate YAML from imperative command (don't actually create)
kubectl create deployment postgres --image=postgres:15 \
  --dry-run=client -o yaml -n deployforge > postgres-deployment.yaml

# Edit the generated YAML (add env vars, resources, etc.)
# Then apply declaratively
kubectl apply -f postgres-deployment.yaml

# Generate a service YAML
kubectl expose deployment api-gateway --port=80 --type=NodePort \
  --dry-run=client -o yaml -n deployforge > api-gateway-svc.yaml

# Clean up generated files
rm -f postgres-deployment.yaml api-gateway-svc.yaml
```

### Verification

```bash
# Can you answer these with a single kubectl command?

# 1. What container images are running in the deployforge namespace?
kubectl get pods -n deployforge -o jsonpath='{range .items[*]}{.spec.containers[0].image}{"\n"}{end}' | sort -u

# 2. Which node has the most pods?
kubectl get pods -n deployforge -o jsonpath='{range .items[*]}{.spec.nodeName}{"\n"}{end}' | sort | uniq -c | sort -rn | head -1

# 3. What's the total number of containers running cluster-wide?
kubectl get pods -A --no-headers | wc -l

# 4. Are all pods healthy?
kubectl get pods -n deployforge -o jsonpath='{range .items[*]}{.metadata.name}: {.status.conditions[?(@.type=="Ready")].status}{"\n"}{end}'
```

<details>
<summary>Show solution</summary>

```bash
#!/bin/bash
set -euo pipefail

echo "=== Exercise 4: kubectl Power User ==="

NS="deployforge"

# Setup
echo "=== Setting up test resources ==="
kubectl create deployment api-gateway --image=nginx:alpine --replicas=2 -n $NS 2>/dev/null || true
kubectl create deployment worker --image=busybox:latest --replicas=1 -n $NS -- sh -c "while true; do echo working; sleep 30; done" 2>/dev/null || true
kubectl create deployment redis --image=redis:7-alpine --replicas=1 -n $NS 2>/dev/null || true
kubectl expose deployment api-gateway --port=80 -n $NS 2>/dev/null || true
kubectl expose deployment redis --port=6379 -n $NS 2>/dev/null || true

echo "Waiting for pods to be ready..."
kubectl wait --for=condition=Ready pods --all -n $NS --timeout=60s 2>/dev/null || true
sleep 5

# JSONPath queries
echo ""
echo "=== JSONPath: Pod names and IPs ==="
kubectl get pods -n $NS \
  -o jsonpath='{range .items[*]}  {.metadata.name}{"\t"}{.status.podIP}{"\n"}{end}'

echo ""
echo "=== JSONPath: Container images ==="
kubectl get pods -n $NS \
  -o jsonpath='{range .items[*]}  {.spec.containers[0].image}{"\n"}{end}' | sort -u

echo ""
echo "=== JSONPath: Service endpoints ==="
kubectl get svc -n $NS \
  -o jsonpath='{range .items[*]}  {.metadata.name}{"\t"}{.spec.clusterIP}:{.spec.ports[0].port}{"\n"}{end}'

# Custom columns
echo ""
echo "=== Custom columns: Rich pod view ==="
kubectl get pods -n $NS -o custom-columns=\
NAME:.metadata.name,\
STATUS:.status.phase,\
NODE:.spec.nodeName,\
IP:.status.podIP,\
IMAGE:.spec.containers[0].image

# Port-forward test
echo ""
echo "=== Port-forward test ==="
kubectl port-forward -n $NS svc/api-gateway 8888:80 &
PF_PID=$!
sleep 3
echo "  Response from API Gateway:"
curl -s http://localhost:8888 | head -3 | sed 's/^/    /'
kill $PF_PID 2>/dev/null
wait $PF_PID 2>/dev/null

# Dry-run workflow
echo ""
echo "=== Dry-run: Generate YAML ==="
kubectl create deployment postgres --image=postgres:15 \
  --dry-run=client -o yaml -n $NS 2>/dev/null | head -15

# Summary queries
echo ""
echo "=== Summary ==="
echo "Images in $NS:"
kubectl get pods -n $NS -o jsonpath='{range .items[*]}  {.spec.containers[0].image}{"\n"}{end}' | sort -u

echo ""
echo "Pod distribution by node:"
kubectl get pods -n $NS -o jsonpath='{range .items[*]}{.spec.nodeName}{"\n"}{end}' | sort | uniq -c | sort -rn

echo ""
echo "All pods healthy?"
kubectl get pods -n $NS -o jsonpath='{range .items[*]}  {.metadata.name}: Ready={.status.conditions[?(@.type=="Ready")].status}{"\n"}{end}'

echo ""
echo "=== Cleanup ==="
echo "Run these commands to clean up:"
echo "  kubectl delete deployment api-gateway worker redis -n $NS"
echo "  kubectl delete svc api-gateway redis -n $NS"
```

</details>

---

## Capstone Checkpoint

Before moving to Module 05, make sure you can answer these questions:

### Control Plane

1. What are the four main control plane components and what does each do?
2. Why is the API server the only component that talks to etcd directly?
3. What happens when the scheduler can't find a node for a pod?
4. Describe the six stages of API request processing (authn → etcd).
5. What is the reconciliation loop pattern and how does the Deployment controller use it?
6. Why should you run etcd with an odd number of members?

### Node Architecture

7. What is the role of the pause container in a pod?
8. What's the difference between a liveness probe and a readiness probe?
9. When would you use a startup probe?
10. What happens when a node stops sending heartbeats for 5 minutes?
11. How does the kubelet enforce a container's memory limit?
12. What's the difference between iptables and IPVS mode for kube-proxy?

### kubectl and API

13. What does the `-v=8` flag show you on a kubectl command?
14. How do you find all available API resources and their verbs?
15. What's the difference between client-side and server-side apply?
16. When would you use `kubectl diff` in a production workflow?
17. What's the API path for getting a pod named "nginx" in the "deployforge" namespace?
18. How do you switch kubectl to use a different namespace by default?

### DeployForge

19. Describe the full lifecycle of a DeployForge pod from `kubectl apply` to running container.
20. Why does DeployForge need separate liveness and readiness probes?
