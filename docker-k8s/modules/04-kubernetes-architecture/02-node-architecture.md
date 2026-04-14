# 4.2 — Node Architecture: Kubelet, Kube-Proxy & CRI

## Concept

Control plane components decide _what_ should run and _where_. But the actual work of pulling images, creating containers, managing networking, and running health checks happens on **worker nodes**. Every node runs three critical components: the kubelet, kube-proxy, and a container runtime.

Understanding node architecture is essential for debugging the most common production problems: pods stuck in `ContainerCreating`, containers being OOM-killed, probes failing, and services not routing traffic. These are all node-level issues.

---

## Deep Dive

### Node Components Overview

```
┌──────────────────────────────────────────────────────────────────────┐
│                          WORKER NODE                                  │
│                                                                      │
│  ┌──────────────────────────────────────────────────────────────┐    │
│  │                         kubelet                               │    │
│  │                                                               │    │
│  │  • Watches API server for pods assigned to this node         │    │
│  │  • Manages pod lifecycle (create, update, delete)            │    │
│  │  • Runs liveness, readiness, and startup probes              │    │
│  │  • Reports node status and resource usage                    │    │
│  │  • Manages container logs and exec                           │    │
│  │  • Handles image garbage collection                          │    │
│  │                                                               │    │
│  │         ┌──────────────┐                                     │    │
│  │         │  CRI (gRPC)  │                                     │    │
│  │         └──────┬───────┘                                     │    │
│  │                │                                              │    │
│  │         ┌──────▼───────┐                                     │    │
│  │         │  containerd  │  (or CRI-O)                         │    │
│  │         │              │                                     │    │
│  │         │  ┌────────┐  │                                     │    │
│  │         │  │  runc   │  │  ← OCI runtime (creates containers)│    │
│  │         │  └────────┘  │                                     │    │
│  │         └──────────────┘                                     │    │
│  └──────────────────────────────────────────────────────────────┘    │
│                                                                      │
│  ┌──────────────────────────────────────────────────────────────┐    │
│  │                       kube-proxy                              │    │
│  │                                                               │    │
│  │  • Programs iptables / IPVS rules for Service routing        │    │
│  │  • Watches Service and EndpointSlice objects                 │    │
│  │  • Enables ClusterIP, NodePort, and LoadBalancer access      │    │
│  └──────────────────────────────────────────────────────────────┘    │
│                                                                      │
│  ┌───────────────────────────────────────────────────────────────┐   │
│  │                       Pod Network                              │   │
│  │                                                                │   │
│  │  ┌──────────────────┐    ┌──────────────────┐                 │   │
│  │  │  Pod A            │    │  Pod B            │                 │   │
│  │  │  ┌─────┐ ┌─────┐ │    │  ┌─────┐         │                 │   │
│  │  │  │ app │ │sidecar│ │    │  │ app │         │                 │   │
│  │  │  └──┬──┘ └──┬───┘ │    │  └──┬──┘         │                 │   │
│  │  │     └───┬───┘     │    │     │             │                 │   │
│  │  │    pause container│    │  pause container  │                 │   │
│  │  │    (net namespace)│    │  (net namespace)  │                 │   │
│  │  │    eth0: 10.244.1.5│    │  eth0: 10.244.1.6│                 │   │
│  │  └──────────────────┘    └──────────────────┘                 │   │
│  │                                                                │   │
│  │  CNI plugin manages pod network interfaces                    │   │
│  └───────────────────────────────────────────────────────────────┘   │
└──────────────────────────────────────────────────────────────────────┘
```

---

### kubelet

The kubelet is the node agent. It runs on every node (including control plane nodes) and is responsible for the actual pod lifecycle.

#### Pod Lifecycle Management

When a pod is scheduled to a node, the kubelet:

```
Pod assigned to this node (via API server watch)
      │
      ▼
1. Pull container images (if not cached)
      │
      ▼
2. Create the pod sandbox (pause container + network namespace)
      │
      ▼
3. Start init containers (sequentially, each must succeed)
      │
      ▼
4. Start app containers (in parallel)
      │
      ▼
5. Run startup probes (if defined — blocks liveness/readiness)
      │
      ▼
6. Run liveness + readiness probes (continuously)
      │
      ▼
7. Report pod status back to API server
```

#### Health Probes

Probes are the kubelet's mechanism for determining container health. Getting probes right is one of the most impactful things you can do for application reliability.

| Probe Type | Purpose | Failure Action | Default |
|-----------|---------|----------------|---------|
| **Startup** | Is the app finished initializing? | Keep checking (blocks other probes) | None |
| **Liveness** | Is the app still alive? | Restart the container | None |
| **Readiness** | Can the app handle traffic? | Remove from Service endpoints | None |

**Probe methods:**

| Method | How It Works | Best For |
|--------|-------------|----------|
| `httpGet` | HTTP GET to a path; 200–399 = success | Web services |
| `tcpSocket` | TCP connect to a port; connection = success | Databases, non-HTTP services |
| `exec` | Run a command in the container; exit 0 = success | Custom health logic |
| `grpc` | gRPC health check protocol | gRPC services |

**Probe timing parameters:**

```yaml
livenessProbe:
  httpGet:
    path: /healthz
    port: 8080
  initialDelaySeconds: 15    # Wait before first check
  periodSeconds: 10           # How often to check
  timeoutSeconds: 3           # How long to wait for response
  successThreshold: 1         # Successes needed after failure
  failureThreshold: 3         # Failures before taking action
```

> **Production note:** The most common probe mistake is setting `initialDelaySeconds` too low. If your app takes 30 seconds to start and liveness fires at 10 seconds, the kubelet will restart it — creating an infinite restart loop. Use `startupProbe` to handle slow startups cleanly.

#### Common Probe Anti-Patterns

| Anti-Pattern | Problem | Fix |
|-------------|---------|-----|
| Liveness checks dependencies | Database is down → app restarts → still can't connect | Liveness should only check the process itself |
| No readiness probe | Traffic routed before app is ready → HTTP 503s | Always define readiness for Services |
| Same check for liveness and readiness | Can't distinguish "dead" from "overloaded" | Different endpoints: `/healthz` vs `/readyz` |
| Too aggressive intervals | Probes consume resources, false positives | Start with 10s period, 3 failure threshold |

---

### Container Runtime Interface (CRI)

The kubelet doesn't create containers directly. It uses the **Container Runtime Interface (CRI)** — a gRPC API that decouples the kubelet from any specific runtime.

```
┌──────────────────────────────────────────────────────────────┐
│                    CRI Architecture                           │
│                                                              │
│  ┌──────────┐     gRPC      ┌──────────────────┐            │
│  │  kubelet  │ ────────────▶ │  CRI Runtime     │            │
│  └──────────┘               │                    │            │
│                              │  ┌──────────────┐ │            │
│  CRI API:                    │  │  containerd   │ │            │
│  • RunPodSandbox()          │  │  (or CRI-O)   │ │            │
│  • CreateContainer()        │  └───────┬──────┘ │            │
│  • StartContainer()         │          │         │            │
│  • StopContainer()          │  ┌───────▼──────┐ │            │
│  • RemoveContainer()        │  │    runc       │ │            │
│  • ListContainers()         │  │  (OCI runtime)│ │            │
│  • PullImage()              │  └──────────────┘ │            │
│                              └──────────────────┘            │
└──────────────────────────────────────────────────────────────┘
```

#### Runtime Comparison

| Feature | containerd | CRI-O |
|---------|-----------|-------|
| Origin | Docker/CNCF | Red Hat/CNCF |
| Scope | General-purpose container runtime | Purpose-built for Kubernetes CRI |
| Image support | Docker, OCI | OCI only |
| Used by | EKS, GKE, AKS, kind, k3s | OpenShift, some on-prem |
| CLI | `ctr`, `nerdctl` | `crictl` |
| Size | Larger (supports non-K8s workloads) | Smaller (K8s-only) |

> **History:** Docker (dockershim) was removed from Kubernetes in v1.24. containerd was extracted from Docker and became the standalone CRI runtime. If you see old documentation referencing dockershim — it no longer applies.

---

### kube-proxy

kube-proxy implements Kubernetes **Service** networking on each node. It watches the API server for Service and EndpointSlice objects, then programs the kernel networking rules so that traffic sent to a Service ClusterIP reaches the right pods.

#### iptables Mode (Default)

```
┌──────────────────────────────────────────────────────────────────┐
│                    iptables Mode                                  │
│                                                                  │
│  Client Pod                                                      │
│      │                                                           │
│      │  dst: 10.96.0.10:80 (ClusterIP)                          │
│      ▼                                                           │
│  iptables DNAT rule                                              │
│      │                                                           │
│      │  Randomly selects a backend pod:                          │
│      │  ┌─────────────────────────┐                              │
│      │  │ probability 0.33 → Pod A (10.244.1.5:8080)            │
│      │  │ probability 0.33 → Pod B (10.244.2.3:8080)            │
│      │  │ probability 0.34 → Pod C (10.244.3.7:8080)            │
│      │  └─────────────────────────┘                              │
│      ▼                                                           │
│  Packet rewritten: dst → 10.244.1.5:8080                        │
│  Routed to the backend pod                                       │
└──────────────────────────────────────────────────────────────────┘
```

- Works by inserting DNAT rules into the kernel's netfilter (iptables)
- Rules are O(n) for n services — each packet walks the chain
- Suitable for clusters up to ~5,000 services
- No connection tracking overhead for idle connections

#### IPVS Mode

```
┌──────────────────────────────────────────────────────────────────┐
│                    IPVS Mode                                      │
│                                                                  │
│  Client Pod                                                      │
│      │                                                           │
│      │  dst: 10.96.0.10:80 (ClusterIP)                          │
│      ▼                                                           │
│  IPVS virtual server (hash-based lookup — O(1))                  │
│      │                                                           │
│      │  Load balancing algorithm:                                │
│      │  ┌─────────────────────────┐                              │
│      │  │ rr (round-robin)        │                              │
│      │  │ lc (least-connection)   │                              │
│      │  │ dh (dest hashing)       │                              │
│      │  │ sh (source hashing)     │                              │
│      │  │ sed (shortest delay)    │                              │
│      │  └─────────────────────────┘                              │
│      ▼                                                           │
│  Packet rewritten: dst → 10.244.1.5:8080                        │
│  Routed to the backend pod                                       │
└──────────────────────────────────────────────────────────────────┘
```

| Feature | iptables | IPVS |
|---------|---------|------|
| Lookup performance | O(n) — linear chain walk | O(1) — hash table lookup |
| Load balancing | Random probability | Round-robin, least-conn, etc. |
| Scale limit | ~5,000 services | ~25,000+ services |
| Rule update | Full chain rewrite | Incremental updates |
| Session affinity | Basic (iptables statistic) | Native support |
| Default | ✅ | Must opt-in |

> **Production note:** For most clusters (< 5,000 services), iptables mode is fine. Switch to IPVS when you notice kube-proxy taking a long time to sync rules or when you need more advanced load balancing algorithms.

---

### Container Networking — The Pause Container

Every pod in Kubernetes has a hidden **pause container** (also called the infrastructure container). This container does almost nothing — it runs a `pause` binary that sleeps forever. Its sole purpose is to **hold the network namespace**.

```
┌─────────────────────────────────────────────┐
│  Pod                                         │
│                                              │
│  ┌────────────────────────────────────────┐  │
│  │  pause container (infrastructure)      │  │
│  │                                        │  │
│  │  Owns:                                 │  │
│  │  • Network namespace (eth0: 10.244.x)  │  │
│  │  • IPC namespace                       │  │
│  │  • PID namespace (optional)            │  │
│  └────────────────────────────────────────┘  │
│       ▲              ▲              ▲        │
│       │ shares net   │ shares net   │        │
│  ┌────┴────┐   ┌────┴────┐   ┌────┴────┐   │
│  │  app    │   │ sidecar │   │  init   │   │
│  │container│   │container│   │container│   │
│  │  :8080  │   │  :9090  │   │  (done) │   │
│  └─────────┘   └─────────┘   └─────────┘   │
│                                              │
│  All containers share localhost              │
│  app can reach sidecar at 127.0.0.1:9090   │
└─────────────────────────────────────────────┘
```

Why not just use one of the app containers to hold the namespace?

- If the app container crashes and restarts, the network namespace would be destroyed and recreated — breaking all connections
- The pause container is stable (it literally does nothing) and ensures the network persists across container restarts
- It enables the kubelet to set up networking _before_ any app containers start

---

### Node Registration and Heartbeats

When a kubelet starts, it registers the node with the API server. Then it continuously sends heartbeats to prove the node is alive.

| Mechanism | Purpose | Default Interval |
|-----------|---------|-----------------|
| **Node Status** | Full status update (conditions, addresses, capacity) | 5 minutes |
| **Lease** | Lightweight heartbeat (just "I'm alive") | 10 seconds |

The **Node controller** (in kube-controller-manager) watches for missing heartbeats:

1. After **40 seconds** without a lease renewal → Node condition changes to `Unknown`
2. After **5 minutes** of `Unknown` → Pods are evicted (rescheduled to other nodes)

> **Production note:** These timescales mean it takes ~5–6 minutes for Kubernetes to detect a dead node and reschedule its pods. This is intentional — network blips shouldn't trigger mass pod evictions. For faster failover, tune `--node-monitor-grace-period` on the controller manager.

---

### Resource Management

The kubelet enforces resource **requests** and **limits** for every container using cgroups.

| Concept | What It Means | Used For |
|---------|--------------|----------|
| **Request** | Guaranteed minimum resources | Scheduler uses this for placement decisions |
| **Limit** | Maximum allowed resources | Kubelet enforces this via cgroups |
| **Allocatable** | Node capacity minus system reserved | What's available for pods |

```
Node Capacity (e.g., 4 CPU, 16Gi memory)
  │
  ├── System Reserved (kubelet, OS): 0.5 CPU, 1Gi
  ├── Kube Reserved (kube-proxy, etc.): 0.5 CPU, 1Gi
  ├── Eviction Threshold: 100Mi
  │
  └── Allocatable = 3 CPU, 13.9Gi  ← What the scheduler sees
```

#### Eviction

When node resources run low, the kubelet starts **evicting pods** to protect the node:

| Signal | Threshold (default) | Action |
|--------|-------------------|--------|
| `memory.available` | < 100Mi | Evict pods (BestEffort first, then Burstable) |
| `nodefs.available` | < 10% | Evict pods using the most disk |
| `imagefs.available` | < 15% | Garbage collect unused images |
| `pid.available` | < ~100 | Evict pods creating the most PIDs |

**QoS classes** determine eviction priority:

| QoS Class | When | Eviction Priority |
|----------|------|------------------|
| **Guaranteed** | requests == limits for all containers | Last to be evicted |
| **Burstable** | At least one request OR limit set | Middle priority |
| **BestEffort** | No requests or limits set | First to be evicted |

---

### Garbage Collection

The kubelet automatically cleans up unused resources:

**Image garbage collection:**
- Triggered when disk usage exceeds `imageGCHighThresholdPercent` (default: 85%)
- Removes images not used by any running pod, oldest first
- Stops when disk drops below `imageGCLowThresholdPercent` (default: 80%)

**Container garbage collection:**
- Keeps the last `maxPerPodContainerCount` terminated containers per pod (default: 1)
- Maximum `maxContainerCount` total terminated containers on the node (default: -1 = unlimited)
- Containers older than `minimumGCAge` are eligible (default: 0 = immediately)

---

## Code Examples

### Example 1: Inspecting Kubelet Status

```bash
# Check node status and conditions
kubectl get nodes -o wide
kubectl describe node kind-worker

# Key sections to look at:
# Conditions:    Ready, MemoryPressure, DiskPressure, PIDPressure
# Capacity:      Total node resources
# Allocatable:   Resources available for pods
# Non-terminated Pods: What's running on this node

# Check kubelet logs on a kind node
docker exec kind-worker journalctl -u kubelet --no-pager --lines=30

# Check the kubelet configuration
docker exec kind-worker cat /var/lib/kubelet/config.yaml

# Verify the CRI endpoint
docker exec kind-worker crictl info
```

### Example 2: Checking kube-proxy Mode

```bash
# Check which mode kube-proxy is running in
kubectl get configmap -n kube-system kube-proxy -o yaml | grep mode

# If mode is "" or "iptables":
# View iptables rules for services
docker exec kind-worker iptables -t nat -L KUBE-SERVICES | head -20

# Count the number of iptables rules (grows with services)
docker exec kind-worker iptables -t nat -L | wc -l

# If mode is "ipvs":
# List IPVS virtual servers
docker exec kind-worker ipvsadm -Ln

# Check kube-proxy logs for sync times
kubectl logs -n kube-system -l k8s-app=kube-proxy --tail=10
```

### Example 3: Viewing Node Conditions and Resource Allocation

```bash
# Get node conditions in a compact format
kubectl get nodes -o jsonpath='{range .items[*]}{.metadata.name}{"\t"}{range .status.conditions[*]}{.type}={.status} {end}{"\n"}{end}'

# Check resource allocation on all nodes
kubectl describe nodes | grep -A 10 "Allocated resources:"

# Check how much is allocatable vs total capacity
kubectl get nodes -o custom-columns=\
NAME:.metadata.name,\
CPU_CAPACITY:.status.capacity.cpu,\
CPU_ALLOC:.status.allocatable.cpu,\
MEM_CAPACITY:.status.capacity.memory,\
MEM_ALLOC:.status.allocatable.memory

# List all pods on a specific node
kubectl get pods --all-namespaces --field-selector spec.nodeName=kind-worker -o wide

# Check the QoS class of all pods
kubectl get pods -o custom-columns=\
NAME:.metadata.name,\
QOS:.status.qosClass,\
NODE:.spec.nodeName
```

### Example 4: Probe Behavior in Action

```bash
# Deploy a pod with all three probe types
cat <<EOF | kubectl apply -f -
apiVersion: v1
kind: Pod
metadata:
  name: probe-demo
spec:
  containers:
  - name: app
    image: nginx:alpine
    ports:
    - containerPort: 80
    startupProbe:
      httpGet:
        path: /
        port: 80
      failureThreshold: 30
      periodSeconds: 2
    livenessProbe:
      httpGet:
        path: /
        port: 80
      periodSeconds: 10
      failureThreshold: 3
    readinessProbe:
      httpGet:
        path: /
        port: 80
      periodSeconds: 5
      failureThreshold: 2
EOF

# Watch the pod start up
kubectl get pod probe-demo --watch

# Check probe status in events
kubectl describe pod probe-demo | grep -A 20 "Events:"

# Simulate a liveness failure by replacing the index page with a 500
kubectl exec probe-demo -- sh -c 'rm /usr/share/nginx/html/index.html'

# Watch the kubelet detect the failure and restart the container
kubectl get pod probe-demo --watch
# → RESTARTS will increment after ~30 seconds (3 failures × 10s period)

# Cleanup
kubectl delete pod probe-demo
```

---

## Try It Yourself

### Challenge 1: Node Capacity Calculator

Write a script that shows, for each node: total capacity, allocatable resources, currently requested resources, and remaining capacity.

<details>
<summary>Show solution</summary>

```bash
#!/bin/bash
# node-capacity.sh — Show resource allocation per node

echo "=============================================="
echo "  Node Resource Allocation"
echo "=============================================="

for node in $(kubectl get nodes -o jsonpath='{.items[*].metadata.name}'); do
  echo ""
  echo "--- $node ---"

  # Get allocatable resources
  alloc_cpu=$(kubectl get node "$node" -o jsonpath='{.status.allocatable.cpu}')
  alloc_mem=$(kubectl get node "$node" -o jsonpath='{.status.allocatable.memory}')

  # Get requested resources (sum of all pod requests on this node)
  req_cpu=$(kubectl get pods --all-namespaces \
    --field-selector spec.nodeName="$node" \
    -o jsonpath='{range .items[*].spec.containers[*]}{.resources.requests.cpu}{"\n"}{end}' | \
    awk '{
      s=$1;
      if (s ~ /m$/) { gsub(/m/,"",s); total += s }
      else { total += s * 1000 }
    } END { print total "m" }')

  req_mem=$(kubectl get pods --all-namespaces \
    --field-selector spec.nodeName="$node" \
    -o jsonpath='{range .items[*].spec.containers[*]}{.resources.requests.memory}{"\n"}{end}' | \
    awk '{
      s=$1;
      if (s ~ /Mi$/) { gsub(/Mi/,"",s); total += s }
      else if (s ~ /Ki$/) { gsub(/Ki/,"",s); total += s/1024 }
      else if (s ~ /Gi$/) { gsub(/Gi/,"",s); total += s*1024 }
      else { total += s/(1024*1024) }
    } END { printf "%.0fMi\n", total }')

  echo "  Allocatable CPU: $alloc_cpu"
  echo "  Allocatable Mem: $alloc_mem"
  echo "  Requested CPU:   $req_cpu"
  echo "  Requested Mem:   $req_mem"

  # Number of pods on this node
  pod_count=$(kubectl get pods --all-namespaces \
    --field-selector spec.nodeName="$node" --no-headers | wc -l)
  echo "  Pods:            $pod_count"
done

echo ""
echo "=============================================="
```

</details>

### Challenge 2: Probe Failure Investigation

Deploy an app with a readiness probe that checks `/readyz`. Make the probe fail, observe the pod being removed from the Service endpoints, then fix it and watch it get added back.

<details>
<summary>Show solution</summary>

```bash
# Create a deployment with a readiness probe and a service
cat <<EOF | kubectl apply -f -
apiVersion: apps/v1
kind: Deployment
metadata:
  name: probe-test
spec:
  replicas: 2
  selector:
    matchLabels:
      app: probe-test
  template:
    metadata:
      labels:
        app: probe-test
    spec:
      containers:
      - name: app
        image: nginx:alpine
        ports:
        - containerPort: 80
        readinessProbe:
          httpGet:
            path: /readyz
            port: 80
          periodSeconds: 5
          failureThreshold: 2
---
apiVersion: v1
kind: Service
metadata:
  name: probe-test
spec:
  selector:
    app: probe-test
  ports:
  - port: 80
    targetPort: 80
EOF

# Wait for pods to start (they'll be NotReady because /readyz doesn't exist)
kubectl get pods -l app=probe-test --watch &

# Check endpoints — should be empty since readiness fails
kubectl get endpoints probe-test
# → ENDPOINTS: <none>

# Fix the readiness check by creating the endpoint
for pod in $(kubectl get pods -l app=probe-test -o name); do
  kubectl exec "$pod" -- sh -c 'echo "ok" > /usr/share/nginx/html/readyz'
done

# Watch the endpoints populate
sleep 10
kubectl get endpoints probe-test
# → ENDPOINTS: 10.244.1.5:80,10.244.2.3:80

# Now break one pod's readiness
POD=$(kubectl get pods -l app=probe-test -o jsonpath='{.items[0].metadata.name}')
kubectl exec "$POD" -- rm /usr/share/nginx/html/readyz

# Watch it become NotReady and get removed from endpoints
sleep 15
kubectl get pods -l app=probe-test
kubectl get endpoints probe-test
# → Only one IP in endpoints now

# Fix it
kubectl exec "$POD" -- sh -c 'echo "ok" > /usr/share/nginx/html/readyz'

# Watch it come back
sleep 10
kubectl get endpoints probe-test

# Cleanup
kubectl delete deployment probe-test
kubectl delete service probe-test
```

</details>

### Challenge 3: Container Runtime Exploration

Explore the container runtime on a kind node. List running containers using `crictl`, inspect a container's configuration, and find the pause container for a pod.

<details>
<summary>Show solution</summary>

```bash
# List all containers running on a worker node
docker exec kind-worker crictl ps

# List all pods (sandbox containers)
docker exec kind-worker crictl pods

# Find a specific pod's sandbox (pause container)
POD_ID=$(docker exec kind-worker crictl pods --name coredns -q | head -1)
echo "Pod sandbox ID: $POD_ID"

# Inspect the pod sandbox
docker exec kind-worker crictl inspectp "$POD_ID" | head -40

# List containers in that pod
docker exec kind-worker crictl ps --pod "$POD_ID"

# Inspect a container's details
CONTAINER_ID=$(docker exec kind-worker crictl ps --pod "$POD_ID" -q | head -1)
docker exec kind-worker crictl inspect "$CONTAINER_ID" | head -50

# Check the container's resource limits (cgroup settings)
docker exec kind-worker crictl inspect "$CONTAINER_ID" | grep -A 10 "linux"

# View container logs via crictl
docker exec kind-worker crictl logs "$CONTAINER_ID" 2>&1 | tail -10

# Show image list on the node
docker exec kind-worker crictl images
```

</details>

---

## Capstone Connection

**DeployForge** pods depend on node architecture for reliable operation:

- **Readiness probes** are critical for DeployForge's API Gateway. When the gateway starts, it needs to connect to PostgreSQL and Redis before accepting traffic. A properly configured readiness probe at `/readyz` prevents traffic routing to a pod that isn't ready — avoiding HTTP 503 errors during deployments.
- **Liveness probes** ensure the Worker Service recovers from deadlocks. BullMQ workers can occasionally get stuck processing a job. A liveness probe at `/healthz` that checks the event loop responsiveness lets the kubelet restart the worker automatically.
- **Resource requests and limits** defined in Module 05 translate directly to cgroup writes on the node. Setting `memory.limit: 512Mi` on the Worker Service means the kubelet writes `536870912` to the container's `memory.max` cgroup file — exactly what you saw in Module 01.
- **kube-proxy** rules are what make DeployForge's Services work. When you create a Service for the API Gateway in Module 06, kube-proxy programs iptables DNAT rules on every node so any pod can reach the gateway by its ClusterIP.
