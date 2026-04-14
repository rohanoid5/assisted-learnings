# 4.1 — Control Plane Deep Dive

## Concept

The Kubernetes control plane is the brain of the cluster. It doesn't run your application workloads — it decides _where_ they should run, _watches_ that they keep running, and _reacts_ when reality drifts from your declared desired state. Every `kubectl apply` you run ultimately talks to the control plane, and every self-healing action Kubernetes takes originates from a control plane component.

Understanding the control plane is the difference between "my pod is stuck in Pending and I don't know why" and "the scheduler couldn't find a node matching the resource request — let me check the node allocatable resources."

---

## Deep Dive

### Architecture Overview

```
┌────────────────────────────────────────────────────────────────────────────┐
│                          CONTROL PLANE                                     │
│                                                                            │
│  ┌──────────────────┐      ┌──────────────────┐                           │
│  │   kube-apiserver  │◀────▶│      etcd         │                          │
│  │                    │      │  (source of truth)│                          │
│  │  • REST API        │      │  • Raft consensus │                          │
│  │  • AuthN/AuthZ     │      │  • Key-value store│                          │
│  │  • Admission       │      │  • Watch support  │                          │
│  │  • Validation      │      └──────────────────┘                          │
│  └──────┬──────┬──────┘                                                    │
│         │      │                                                           │
│    watch│      │watch                                                      │
│         │      │                                                           │
│  ┌──────▼──────┐  ┌───────────────────────┐  ┌──────────────────────────┐ │
│  │kube-scheduler│  │kube-controller-manager│  │cloud-controller-manager  │ │
│  │              │  │                       │  │                          │ │
│  │• Filter nodes│  │• Deployment controller│  │• Node controller (cloud) │ │
│  │• Score nodes │  │• ReplicaSet controller│  │• Route controller        │ │
│  │• Bind pod    │  │• Node controller      │  │• Service LB controller   │ │
│  └──────────────┘  │• Job controller       │  └──────────────────────────┘ │
│                    │• EndpointSlice ctrl   │                               │
│                    │• ServiceAccount ctrl  │                               │
│                    └───────────────────────┘                               │
│                                                                            │
│        ┌──────────────────────────────────────────────────┐                │
│        │              Watch Event Flow                     │                │
│        │                                                   │                │
│        │  etcd → API Server → Informer Cache → Controller  │                │
│        │                          │                        │                │
│        │                     SharedInformer                │                │
│        │                     (in-memory cache              │                │
│        │                      + event handlers)            │                │
│        └──────────────────────────────────────────────────┘                │
└────────────────────────────────────────────────────────────────────────────┘
```

> **Key insight:** Only the API server talks to etcd. Every other component communicates through the API server, which acts as the single gateway to cluster state.

---

### kube-apiserver

The API server is the front door to the cluster. Every interaction — `kubectl`, the kubelet registering a node, a controller watching for changes — goes through its REST API.

#### Request Processing Pipeline

Every request passes through these stages in order:

```
                    ┌─────────────────────────────────────────────────────┐
                    │              API Server Request Pipeline             │
                    │                                                     │
 kubectl ──────▶   │  1. Authentication   "Who are you?"                 │
                    │        │                                            │
                    │        ▼                                            │
                    │  2. Authorization    "Can you do this?"             │
                    │     (RBAC)              │                           │
                    │                         ▼                           │
                    │  3. Mutating         "Let me modify                 │
                    │     Admission         your request"                 │
                    │                         │                           │
                    │                         ▼                           │
                    │  4. Schema            "Is this valid                │
                    │     Validation         YAML/JSON?"                  │
                    │                         │                           │
                    │                         ▼                           │
                    │  5. Validating        "Does this meet               │
                    │     Admission          policy rules?"               │
                    │                         │                           │
                    │                         ▼                           │
                    │  6. Persist to etcd   "Stored."                     │
                    │                                                     │
                    └─────────────────────────────────────────────────────┘
```

**1. Authentication** — Verifies identity via one or more methods:

| Method | Use Case |
|--------|----------|
| X.509 client certificates | kubelet, controller-manager, scheduler |
| Bearer tokens (JWT) | Service accounts, OIDC |
| Authentication proxy | External identity providers |
| Bootstrap tokens | Node registration (kubeadm) |

**2. Authorization** — Kubernetes supports multiple authorizers (evaluated in order):

| Mode | Description |
|------|-------------|
| **RBAC** | Role-based access control (the default and most common) |
| **ABAC** | Attribute-based (legacy, avoid) |
| **Webhook** | External HTTP service makes the decision |
| **Node** | Special-purpose authorizer for kubelet requests |

**3. Admission Controllers** — Intercept requests _after_ auth but _before_ persistence:

| Type | When | Examples |
|------|------|---------|
| **Mutating** | Can modify the request | `DefaultStorageClass`, `MutatingAdmissionWebhook` |
| **Validating** | Can reject the request | `LimitRanger`, `ResourceQuota`, `ValidatingAdmissionWebhook` |

> **Production note:** Admission webhooks are how tools like Istio inject sidecars, OPA/Gatekeeper enforce policies, and cert-manager handles certificate requests. They're the primary extension point for the API server.

#### API Aggregation

The API server supports extension through aggregation — you can register additional API servers that handle custom API groups. This is how the metrics server provides `metrics.k8s.io/v1beta1` and how custom resources get served.

```bash
# Check registered API services
kubectl get apiservices | head -20

# The metrics server registers as:
# v1beta1.metrics.k8s.io   kube-system/metrics-server   True   45d
```

---

### etcd

etcd is a distributed, consistent key-value store that serves as Kubernetes' single source of truth. Every piece of cluster state — pods, services, secrets, configmaps, RBAC rules — lives in etcd and nowhere else.

#### Why etcd Is Critical

| Property | Why It Matters |
|----------|---------------|
| **Consistency** | Raft consensus ensures all nodes agree on state — no split-brain |
| **Watch support** | Controllers can efficiently watch for changes without polling |
| **MVCC** | Multi-version concurrency control enables optimistic locking (resourceVersion) |
| **Transactional** | Compare-and-swap operations prevent race conditions |
| **Sequential consistency** | Reads reflect the most recent committed write |

#### Data Model

All Kubernetes resources are stored under a key prefix:

```
/registry/<api-group>/<resource-type>/<namespace>/<name>

# Examples:
/registry/pods/default/nginx-7d9fc5b9b8-x4vnl
/registry/deployments/kube-system/coredns
/registry/services/specs/default/kubernetes
/registry/secrets/default/my-secret
/registry/configmaps/kube-system/kubeadm-config
```

Values are stored as serialized Protocol Buffers (not JSON) for performance.

#### Raft Consensus

etcd uses the Raft consensus algorithm to replicate data across cluster members:

```
┌─────────────────────────────────────────────────────────────┐
│                    Raft Consensus                            │
│                                                             │
│  ┌──────────┐   write   ┌──────────┐   replicate            │
│  │  Client   │─────────▶│  Leader   │──────────┐             │
│  └──────────┘           │ (etcd-0)  │          │             │
│                          └──────────┘          │             │
│                               │                │             │
│                          replicate         replicate         │
│                               │                │             │
│                               ▼                ▼             │
│                         ┌──────────┐    ┌──────────┐        │
│                         │ Follower │    │ Follower │        │
│                         │ (etcd-1) │    │ (etcd-2) │        │
│                         └──────────┘    └──────────┘        │
│                                                             │
│  • Writes go to the leader only                             │
│  • Leader replicates to followers                           │
│  • Committed when majority (quorum) acknowledges            │
│  • If leader fails, followers elect a new leader            │
│  • Quorum = (n/2) + 1  (3 nodes → need 2 for quorum)      │
└─────────────────────────────────────────────────────────────┘
```

> **Production note:** Always run etcd with an odd number of members (3, 5, 7). Even numbers don't improve fault tolerance — a 4-node cluster tolerates exactly the same number of failures as a 3-node cluster (1 failure), but 5 nodes tolerate 2 failures.

| Cluster Size | Quorum | Tolerated Failures |
|-------------|--------|-------------------|
| 1 | 1 | 0 |
| 3 | 2 | 1 |
| 5 | 3 | 2 |
| 7 | 4 | 3 |

---

### kube-scheduler

The scheduler watches for newly created pods that have no assigned node (`.spec.nodeName` is empty), then selects the best node for each pod.

#### Scheduling Algorithm

The scheduler uses a two-phase algorithm:

```
                Unscheduled Pod
                      │
                      ▼
        ┌─────────────────────────┐
        │   1. FILTERING          │
        │   (find feasible nodes) │
        │                         │
        │   • NodeAffinity        │
        │   • Taints/Tolerations  │
        │   • Resource fit        │
        │   • PodTopologySpread   │
        │   • NodeSelector        │
        │   • Volume zone         │
        └───────────┬─────────────┘
                    │
          Feasible nodes (e.g., 3 of 5)
                    │
                    ▼
        ┌─────────────────────────┐
        │   2. SCORING            │
        │   (rank feasible nodes) │
        │                         │
        │   • LeastAllocated      │
        │   • BalancedAllocation  │
        │   • ImageLocality       │
        │   • InterPodAffinity    │
        │   • NodeAffinity (pref) │
        └───────────┬─────────────┘
                    │
          Highest-scoring node
                    │
                    ▼
        ┌─────────────────────────┐
        │   3. BINDING            │
        │   (assign pod to node)  │
        │                         │
        │   POST /binding to API  │
        └─────────────────────────┘
```

**Filtering plugins** remove nodes that can't run the pod:

| Plugin | What It Checks |
|--------|---------------|
| `NodeResourcesFit` | Does the node have enough CPU/memory? |
| `NodeAffinity` | Does the node match required affinity rules? |
| `TaintToleration` | Does the pod tolerate the node's taints? |
| `PodTopologySpread` | Would this violate topology spread constraints? |
| `VolumeBinding` | Can the required PVs be provisioned in this zone? |
| `NodePorts` | Are the required host ports available? |

**Scoring plugins** rank the remaining nodes (0–100 each):

| Plugin | Scoring Strategy |
|--------|-----------------|
| `LeastAllocated` | Prefers nodes with more available resources |
| `MostAllocated` | Prefers nodes that are already full (bin-packing) |
| `BalancedAllocation` | Prefers nodes with balanced CPU/memory ratio |
| `ImageLocality` | Prefers nodes that already have the container image cached |
| `InterPodAffinity` | Prefers nodes that satisfy inter-pod affinity rules |

#### Scheduling Profiles

Kubernetes 1.28+ supports scheduling profiles — you can run multiple schedulers with different configurations:

```yaml
apiVersion: kubescheduler.config.k8s.io/v1
kind: KubeSchedulerConfiguration
profiles:
  - schedulerName: default-scheduler
    plugins:
      score:
        enabled:
          - name: NodeResourcesBalancedAllocation
            weight: 1
          - name: ImageLocality
            weight: 1
  - schedulerName: high-density-scheduler
    plugins:
      score:
        enabled:
          - name: NodeResourcesMostAllocated  # bin-packing
            weight: 2
```

---

### kube-controller-manager

The controller manager runs a collection of **reconciliation loops** (controllers). Each controller watches a specific resource type and works to make the _current state_ match the _desired state_.

#### The Reconciliation Pattern

Every controller follows the same pattern:

```
┌────────────────────────────────────────────────────────┐
│              Controller Reconciliation Loop             │
│                                                        │
│   1. OBSERVE    Watch API server for changes           │
│        │        (via shared informers)                 │
│        ▼                                               │
│   2. DIFF       Compare desired state vs actual state  │
│        │                                               │
│        ▼                                               │
│   3. ACT        Take action to reconcile the diff      │
│        │        (create/update/delete resources)       │
│        │                                               │
│        └────────▶ Back to step 1 (continuous loop)     │
│                                                        │
└────────────────────────────────────────────────────────┘
```

#### Key Controllers

| Controller | Watches | Reconciles |
|-----------|---------|------------|
| **Deployment** | Deployment objects | Creates/updates ReplicaSets for rolling updates |
| **ReplicaSet** | ReplicaSet objects | Ensures N pod replicas are running |
| **Node** | Node objects + heartbeats | Marks nodes as NotReady, evicts pods from dead nodes |
| **Job** | Job objects | Creates pods, tracks completions, handles failures |
| **EndpointSlice** | Services + Pods | Maintains the list of pod IPs backing each service |
| **ServiceAccount** | Namespaces | Creates default ServiceAccount in new namespaces |
| **PersistentVolume** | PV + PVC objects | Binds PersistentVolumeClaims to PersistentVolumes |
| **Namespace** | Namespace objects | Cleans up resources when a namespace is deleted |
| **CronJob** | CronJob objects | Creates Job objects on schedule |
| **StatefulSet** | StatefulSet objects | Ordered pod creation/deletion with stable identity |

#### Deployment Rollout — How Controllers Chain Together

When you `kubectl apply` a Deployment, watch the chain reaction:

```
kubectl apply (Deployment)
       │
       ▼
  Deployment Controller sees new/updated Deployment
       │
       ├─── Creates a NEW ReplicaSet (with updated pod template)
       │
       ├─── Scales UP new ReplicaSet   (e.g., 1 → 2 → 3 pods)
       │
       └─── Scales DOWN old ReplicaSet (e.g., 3 → 2 → 1 → 0 pods)
                 │
                 ▼
       ReplicaSet Controller sees ReplicaSet with desired replicas
                 │
                 ├─── Creates Pods via API server
                 │
                 ▼
       Scheduler sees unbound Pods
                 │
                 ├─── Assigns each Pod to a Node
                 │
                 ▼
       Kubelet on each Node sees assigned Pod
                 │
                 └─── Pulls image, creates container, starts process
```

---

### The Watch Mechanism — Informers

Controllers don't poll the API server. They use the **watch** protocol — a long-lived HTTP connection that streams change events as they happen.

In practice, controllers use **SharedInformers** — a client-go abstraction that:

1. **Lists** all resources of a type on startup (full sync)
2. **Watches** for changes via a streaming connection
3. **Caches** the full state in memory (thread-safe store)
4. **Dispatches** events to registered handlers

```
┌──────────────────────────────────────────────────────────────┐
│                  SharedInformer Architecture                   │
│                                                               │
│  ┌───────────┐    HTTP watch     ┌─────────────┐             │
│  │ API Server │ ─────────────▶   │  Reflector   │             │
│  └───────────┘                   │  (list+watch)│             │
│                                  └──────┬───────┘             │
│                                         │                     │
│                                  ┌──────▼───────┐             │
│                                  │  Delta FIFO  │             │
│                                  │  Queue       │             │
│                                  └──────┬───────┘             │
│                                         │                     │
│                            ┌────────────┴────────────┐        │
│                            │                         │        │
│                     ┌──────▼───────┐          ┌──────▼──────┐ │
│                     │  Indexer     │          │  Event       │ │
│                     │  (in-memory  │          │  Handlers    │ │
│                     │   cache)     │          │  OnAdd()     │ │
│                     │              │          │  OnUpdate()  │ │
│                     │  Lister:     │          │  OnDelete()  │ │
│                     │  Get(key)    │          └─────────────┘ │
│                     │  List()      │                          │
│                     └──────────────┘                          │
└──────────────────────────────────────────────────────────────┘
```

> **Key insight:** Because of the in-memory cache, controllers rarely need to make GET requests to the API server. They read from their local cache and only write changes back. This dramatically reduces API server load — a cluster with 10,000 pods generates a manageable number of watch events, not 10,000 polling requests per second.

---

### cloud-controller-manager

The cloud-controller-manager runs controllers that interact with the underlying cloud provider's API. It was extracted from kube-controller-manager to allow cloud providers to release independently.

| Controller | Responsibility |
|-----------|---------------|
| **Node** | Detects when a cloud VM backing a node is deleted, updates node status |
| **Route** | Configures cloud routes for pod-to-pod networking across nodes |
| **Service** | Creates cloud load balancers for `type: LoadBalancer` services |

> **Note:** In a `kind` cluster or bare-metal deployment, there is no cloud-controller-manager. Tools like MetalLB provide `LoadBalancer` functionality instead.

---

### HA Control Plane Topologies

Production clusters run multiple replicas of control plane components for high availability.

#### Stacked etcd (Default with kubeadm)

```
┌───────────────────┐  ┌───────────────────┐  ┌───────────────────┐
│ Control Plane 1    │  │ Control Plane 2    │  │ Control Plane 3    │
│                    │  │                    │  │                    │
│  ┌──────────────┐ │  │  ┌──────────────┐ │  │  ┌──────────────┐ │
│  │ API Server   │ │  │  │ API Server   │ │  │  │ API Server   │ │
│  │ Scheduler    │ │  │  │ Scheduler    │ │  │  │ Scheduler    │ │
│  │ Ctrl Manager │ │  │  │ Ctrl Manager │ │  │  │ Ctrl Manager │ │
│  │ etcd         │ │  │  │ etcd         │ │  │  │ etcd         │ │
│  └──────────────┘ │  │  └──────────────┘ │  │  └──────────────┘ │
└───────────────────┘  └───────────────────┘  └───────────────────┘

Pros: Simpler to set up, fewer machines
Cons: Losing a node loses both etcd member AND control plane
```

#### External etcd

```
┌──────────────────┐  ┌──────────────────┐  ┌──────────────────┐
│ Control Plane 1   │  │ Control Plane 2   │  │ Control Plane 3   │
│  API Server       │  │  API Server       │  │  API Server       │
│  Scheduler        │  │  Scheduler        │  │  Scheduler        │
│  Ctrl Manager     │  │  Ctrl Manager     │  │  Ctrl Manager     │
└────────┬─────────┘  └────────┬─────────┘  └────────┬─────────┘
         │                     │                      │
         └─────────────────────┼──────────────────────┘
                               │
         ┌─────────────────────┼──────────────────────┐
         │                     │                      │
┌────────▼─────────┐  ┌───────▼──────────┐  ┌───────▼──────────┐
│    etcd-0         │  │    etcd-1         │  │    etcd-2         │
│  (dedicated host) │  │  (dedicated host) │  │  (dedicated host) │
└──────────────────┘  └──────────────────┘  └──────────────────┘

Pros: etcd can be scaled independently, losing a CP node doesn't affect etcd
Cons: More machines, more operational complexity
```

> **Production note:** Scheduler and controller-manager use **leader election** — only one instance is active at a time. The others are on standby. API servers, however, can all be active simultaneously behind a load balancer.

---

## Code Examples

### Example 1: Querying etcd Directly

```bash
# In a kind cluster, exec into the etcd pod
kubectl exec -it -n kube-system etcd-deployforge-control-plane -- sh

# List all keys (warning: lots of output in a real cluster)
etcdctl --endpoints=https://127.0.0.1:2379 \
  --cacert=/etc/kubernetes/pki/etcd/ca.crt \
  --cert=/etc/kubernetes/pki/etcd/server.crt \
  --key=/etc/kubernetes/pki/etcd/server.key \
  get / --prefix --keys-only | head -30

# Read a specific pod from etcd (raw protobuf — not human-readable)
etcdctl --endpoints=https://127.0.0.1:2379 \
  --cacert=/etc/kubernetes/pki/etcd/ca.crt \
  --cert=/etc/kubernetes/pki/etcd/server.crt \
  --key=/etc/kubernetes/pki/etcd/server.key \
  get /registry/pods/kube-system/coredns-$(kubectl get pods -n kube-system -l k8s-app=kube-dns -o jsonpath='{.items[0].metadata.name}' | cut -d'-' -f2-)

# Count all keys by resource type
etcdctl --endpoints=https://127.0.0.1:2379 \
  --cacert=/etc/kubernetes/pki/etcd/ca.crt \
  --cert=/etc/kubernetes/pki/etcd/server.crt \
  --key=/etc/kubernetes/pki/etcd/server.key \
  get / --prefix --keys-only | \
  sed 's|/registry/||' | cut -d'/' -f1 | sort | uniq -c | sort -rn
```

### Example 2: Watching API Server Events in Real Time

```bash
# Watch all events cluster-wide (very noisy — great for learning)
kubectl get events --all-namespaces --watch

# In another terminal, create a pod and watch the events:
kubectl run nginx-watch-demo --image=nginx:alpine

# You'll see events like:
# Scheduled  "Successfully assigned default/nginx-watch-demo to kind-worker"
# Pulling    "Pulling image nginx:alpine"
# Pulled     "Successfully pulled image nginx:alpine"
# Created    "Created container nginx-watch-demo"
# Started    "Started container nginx-watch-demo"

# Watch with a field selector for a specific pod
kubectl get events --field-selector involvedObject.name=nginx-watch-demo --watch

# Cleanup
kubectl delete pod nginx-watch-demo
```

### Example 3: Inspecting Control Plane Component Logs

```bash
# Scheduler logs — see scheduling decisions
kubectl logs -n kube-system -l component=kube-scheduler --tail=20

# Controller manager logs — see reconciliation actions
kubectl logs -n kube-system -l component=kube-controller-manager --tail=20

# API server logs — see request processing
kubectl logs -n kube-system -l component=kube-apiserver --tail=20

# etcd logs — see Raft consensus and compaction
kubectl logs -n kube-system -l component=etcd --tail=20

# Follow scheduler logs while creating a deployment
kubectl logs -n kube-system -l component=kube-scheduler -f &
kubectl create deployment scheduler-demo --image=nginx:alpine --replicas=3
# Watch the scheduler log each pod-to-node binding decision
kubectl delete deployment scheduler-demo
```

### Example 4: Checking etcd Health and Performance

```bash
# Check etcd cluster health
kubectl exec -it -n kube-system etcd-deployforge-control-plane -- \
  etcdctl --endpoints=https://127.0.0.1:2379 \
  --cacert=/etc/kubernetes/pki/etcd/ca.crt \
  --cert=/etc/kubernetes/pki/etcd/server.crt \
  --key=/etc/kubernetes/pki/etcd/server.key \
  endpoint health

# Check etcd cluster status (shows leader, DB size, Raft term)
kubectl exec -it -n kube-system etcd-deployforge-control-plane -- \
  etcdctl --endpoints=https://127.0.0.1:2379 \
  --cacert=/etc/kubernetes/pki/etcd/ca.crt \
  --cert=/etc/kubernetes/pki/etcd/server.crt \
  --key=/etc/kubernetes/pki/etcd/server.key \
  endpoint status --write-out=table

# Check etcd member list
kubectl exec -it -n kube-system etcd-deployforge-control-plane -- \
  etcdctl --endpoints=https://127.0.0.1:2379 \
  --cacert=/etc/kubernetes/pki/etcd/ca.crt \
  --cert=/etc/kubernetes/pki/etcd/server.crt \
  --key=/etc/kubernetes/pki/etcd/server.key \
  member list --write-out=table
```

---

## Try It Yourself

### Challenge 1: Trace a Scheduling Decision

Create a pod that requests specific resources and watch the scheduler's decision-making process. Then create a pod that _can't_ be scheduled and diagnose why.

<details>
<summary>Show solution</summary>

```bash
# Step 1: Check available resources on your nodes
kubectl describe nodes | grep -A 5 "Allocatable:"

# Step 2: Create a pod with specific resource requests
cat <<EOF | kubectl apply -f -
apiVersion: v1
kind: Pod
metadata:
  name: schedule-test
spec:
  containers:
  - name: app
    image: nginx:alpine
    resources:
      requests:
        cpu: "100m"
        memory: "64Mi"
      limits:
        cpu: "200m"
        memory: "128Mi"
EOF

# Step 3: Watch events to see the scheduling decision
kubectl get events --field-selector involvedObject.name=schedule-test
# → "Successfully assigned default/schedule-test to kind-worker"

# Step 4: Now create an unschedulable pod (request more resources than any node has)
cat <<EOF | kubectl apply -f -
apiVersion: v1
kind: Pod
metadata:
  name: unschedulable-test
spec:
  containers:
  - name: app
    image: nginx:alpine
    resources:
      requests:
        cpu: "100"
        memory: "1000Gi"
EOF

# Step 5: Watch the pod stay in Pending
kubectl get pod unschedulable-test
# → STATUS: Pending

# Step 6: Diagnose the issue
kubectl describe pod unschedulable-test
# → Events: "0/3 nodes are available: 3 Insufficient cpu, 3 Insufficient memory."

# Cleanup
kubectl delete pod schedule-test unschedulable-test
```

</details>

### Challenge 2: Controller Chain Reaction

Create a Deployment and trace the full controller chain: Deployment → ReplicaSet → Pods. Watch in real time how each controller reacts.

<details>
<summary>Show solution</summary>

```bash
# Terminal 1: Watch all resources
kubectl get deploy,rs,pods --watch &

# Terminal 2: Watch events
kubectl get events --watch &

# Create a deployment
kubectl create deployment chain-demo --image=nginx:alpine --replicas=3

# You should see the chain reaction:
# 1. Deployment "chain-demo" created
# 2. ReplicaSet "chain-demo-<hash>" created by Deployment controller
# 3. Pods "chain-demo-<hash>-<id>" created by ReplicaSet controller
# 4. Pods scheduled to nodes by Scheduler
# 5. Containers pulled and started by Kubelet

# Now trigger a rollout and watch the cascade again
kubectl set image deployment/chain-demo nginx=nginx:1.25-alpine

# The Deployment controller will:
# 1. Create a NEW ReplicaSet with the updated image
# 2. Scale UP the new RS, scale DOWN the old RS (rolling update)

# Check the rollout history
kubectl rollout history deployment chain-demo
kubectl rollout status deployment chain-demo

# Cleanup
kubectl delete deployment chain-demo
```

</details>

### Challenge 3: etcd Key Explorer

Exec into the etcd pod in your kind cluster. Count the number of keys per resource type and find where your DeployForge namespace is stored.

<details>
<summary>Show solution</summary>

```bash
# First, create the DeployForge namespace
kubectl create namespace deployforge

# Exec into etcd
kubectl exec -it -n kube-system etcd-deployforge-control-plane -- sh

# Set up etcdctl alias for convenience
alias etcdctl='etcdctl --endpoints=https://127.0.0.1:2379 \
  --cacert=/etc/kubernetes/pki/etcd/ca.crt \
  --cert=/etc/kubernetes/pki/etcd/server.crt \
  --key=/etc/kubernetes/pki/etcd/server.key'

# Count keys by resource type
etcdctl get / --prefix --keys-only | \
  sed '/^$/d' | sed 's|/registry/||' | \
  cut -d'/' -f1 | sort | uniq -c | sort -rn

# Find the DeployForge namespace key
etcdctl get / --prefix --keys-only | grep deployforge

# You should see:
# /registry/namespaces/deployforge
# /registry/serviceaccounts/deployforge/default
# /registry/secrets/deployforge/default-token-xxxxx

# Exit the etcd pod
exit
```

</details>

---

## Capstone Connection

**DeployForge** relies on the control plane for every aspect of its operation:

- **Reconciliation loops** are the heart of Kubernetes' self-healing. When you define a DeployForge Deployment with 3 replicas in Module 05, the Deployment controller and ReplicaSet controller work together to ensure exactly 3 pods are always running — automatically replacing any that fail.
- **The scheduler** determines which worker nodes run DeployForge pods. In Module 12 (Scaling & Cost), you'll configure scheduling constraints to spread DeployForge across availability zones for high availability.
- **etcd** stores every DeployForge resource. When you later encounter issues with stale data or conflicting updates (Module 10, GitOps), understanding `resourceVersion` and optimistic locking will be essential.
- **Admission controllers** will be critical in Module 07 (Storage & Configuration) when you configure webhook-based policies that validate DeployForge manifests before they're accepted.
