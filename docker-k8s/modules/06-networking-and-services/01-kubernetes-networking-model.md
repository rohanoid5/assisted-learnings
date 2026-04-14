# 6.1 — Kubernetes Networking Model & CNI

## Concept

Kubernetes imposes a fundamental networking contract: every pod gets its own IP address, and every pod can communicate with every other pod without NAT. This "flat network" model sounds simple, but implementing it across multiple nodes with potentially thousands of pods requires sophisticated networking infrastructure.

Unlike Docker's default bridge networking — where containers on different hosts can't talk to each other without port mapping — Kubernetes delegates the actual network plumbing to Container Network Interface (CNI) plugins. These plugins (Calico, Cilium, Flannel, and others) implement the flat network using different technologies: VXLAN overlays, BGP routing, eBPF, or direct cloud-provider integrations. Understanding which approach your CNI uses is critical for debugging, performance tuning, and security.

---

## Deep Dive

### The Three Networking Requirements

Kubernetes defines three fundamental networking requirements that every cluster must satisfy:

```
┌─────────────────────────────────────────────────────────────────────┐
│              Kubernetes Networking Requirements                       │
│                                                                     │
│  1. Pod-to-Pod Communication                                        │
│     ┌─────────┐         ┌─────────┐                                 │
│     │  Pod A   │────────▶│  Pod B   │  Any pod can reach any other   │
│     │ 10.244.1.5│        │10.244.2.8│  pod by its IP, without NAT.  │
│     └─────────┘         └─────────┘                                 │
│     (Node 1)             (Node 2)                                    │
│                                                                     │
│  2. Pod-to-Service Communication                                    │
│     ┌─────────┐         ┌──────────────┐       ┌─────────┐         │
│     │  Pod A   │────────▶│  Service VIP  │──────▶│  Pod B   │        │
│     │          │         │  10.96.0.10   │       │          │        │
│     └─────────┘         └──────────────┘       └─────────┘         │
│     kube-proxy intercepts VIP traffic and load-balances to pods     │
│                                                                     │
│  3. External-to-Service Communication                               │
│     ┌──────────┐        ┌──────────────┐       ┌─────────┐         │
│     │ External  │───────▶│  NodePort /   │──────▶│  Pod     │        │
│     │ Client    │        │  LoadBalancer │       │          │        │
│     └──────────┘        └──────────────┘       └─────────┘         │
│     External traffic enters via NodePort, LoadBalancer, or Ingress  │
└─────────────────────────────────────────────────────────────────────┘
```

These requirements are non-negotiable. If your CNI plugin doesn't satisfy all three, your cluster doesn't have valid Kubernetes networking.

> **Key insight:** Kubernetes doesn't implement networking itself. It defines the _contract_ and delegates implementation to CNI plugins. This is the same pattern as CRI (container runtime) and CSI (storage) — Kubernetes is an interface layer.

---

### How Pod IPs Work

Every pod gets a unique IP address from the cluster's pod CIDR range. The kubelet on each node requests an IP from the CNI plugin when a pod starts.

```
┌────────────────────────────────────────────────────────────────────┐
│                   Pod IP Allocation (IPAM)                          │
│                                                                    │
│  Cluster Pod CIDR: 10.244.0.0/16                                   │
│                                                                    │
│  ┌──── Node 1 ─────────────────┐  ┌──── Node 2 ─────────────────┐ │
│  │  Node CIDR: 10.244.1.0/24   │  │  Node CIDR: 10.244.2.0/24   │ │
│  │                              │  │                              │ │
│  │  ┌───────────┐ ┌──────────┐ │  │  ┌───────────┐ ┌──────────┐ │ │
│  │  │ Pod A      │ │ Pod B     │ │  │  │ Pod C      │ │ Pod D     │ │ │
│  │  │ 10.244.1.2 │ │10.244.1.3│ │  │  │ 10.244.2.2 │ │10.244.2.3│ │ │
│  │  └───────────┘ └──────────┘ │  │  └───────────┘ └──────────┘ │ │
│  │                              │  │                              │ │
│  │  veth pairs → cbr0 bridge    │  │  veth pairs → cbr0 bridge    │ │
│  └──────────────────────────────┘  └──────────────────────────────┘ │
│                                                                    │
│  Each node gets a /24 subnet → 254 pod IPs per node               │
│  The CNI plugin handles allocation and routing between nodes       │
└────────────────────────────────────────────────────────────────────┘
```

The IP Address Management (IPAM) component of the CNI plugin tracks which IPs are assigned. When a pod is deleted, its IP is returned to the pool. This is why pod IPs are ephemeral — you never hardcode a pod IP; you use Services instead.

---

### Cross-Node Networking: How Packets Actually Flow

When Pod A on Node 1 sends a packet to Pod C on Node 2, the packet must traverse the node boundary. How this happens depends on your CNI plugin:

```
┌────────────────────────────────────────────────────────────────────┐
│           Packet Flow: Pod A (Node 1) → Pod C (Node 2)             │
│                                                                    │
│  ┌──── Node 1 ──────────────────────────────────┐                  │
│  │                                               │                  │
│  │  Pod A (10.244.1.2)                           │                  │
│  │    │                                          │                  │
│  │    │ 1. Packet leaves pod via veth pair        │                  │
│  │    ▼                                          │                  │
│  │  ┌──────────┐                                 │                  │
│  │  │ veth pair │  Virtual ethernet device        │                  │
│  │  └────┬─────┘  connects pod netns to host     │                  │
│  │       │                                       │                  │
│  │       │ 2. Arrives at node's network bridge    │                  │
│  │       ▼                                       │                  │
│  │  ┌──────────┐                                 │                  │
│  │  │  cbr0 /   │  Bridge or routing table       │                  │
│  │  │  cni0     │  decides: dst is NOT local     │                  │
│  │  └────┬─────┘                                 │                  │
│  │       │                                       │                  │
│  │       │ 3. Route to Node 2 via overlay/BGP     │                  │
│  │       ▼                                       │                  │
│  │  ┌──────────┐                                 │                  │
│  │  │  eth0     │  Encapsulate (VXLAN) or route   │                  │
│  │  │ 192.168.1.10│ directly (BGP)               │                  │
│  │  └────┬─────┘                                 │                  │
│  └───────┼───────────────────────────────────────┘                  │
│          │                                                          │
│          │ 4. Packet traverses physical network                      │
│          │    (VXLAN tunnel or BGP-routed)                          │
│          ▼                                                          │
│  ┌──── Node 2 ──────────────────────────────────┐                  │
│  │  ┌──────────┐                                 │                  │
│  │  │  eth0     │  Decapsulate or receive via     │                  │
│  │  │ 192.168.1.11│ BGP route                    │                  │
│  │  └────┬─────┘                                 │                  │
│  │       │                                       │                  │
│  │       │ 5. Route to local pod subnet           │                  │
│  │       ▼                                       │                  │
│  │  ┌──────────┐                                 │                  │
│  │  │  cbr0 /   │                                │                  │
│  │  │  cni0     │                                │                  │
│  │  └────┬─────┘                                 │                  │
│  │       │                                       │                  │
│  │       │ 6. Deliver to pod via veth pair         │                  │
│  │       ▼                                       │                  │
│  │  Pod C (10.244.2.2)                           │                  │
│  │    ✓ Packet delivered!                        │                  │
│  └───────────────────────────────────────────────┘                  │
└────────────────────────────────────────────────────────────────────┘
```

---

### CNI Plugins Compared

The Container Network Interface (CNI) is a specification for configuring network interfaces in Linux containers. Kubernetes calls the CNI plugin binary during pod creation and deletion. The plugin is responsible for:

1. Allocating an IP address to the pod
2. Setting up the network interface (veth pair)
3. Configuring routing so the pod can reach other pods and services

| Feature | Calico | Cilium | Flannel | Weave Net |
|---------|--------|--------|---------|-----------|
| **Data plane** | iptables or eBPF | eBPF | VXLAN overlay | VXLAN + sleeve |
| **Routing** | BGP or VXLAN | Direct or VXLAN | VXLAN only | Mesh overlay |
| **NetworkPolicy** | ✅ Full support | ✅ Full + extended | ❌ None | ✅ Basic |
| **Performance** | High (BGP mode) | Highest (eBPF) | Moderate | Moderate |
| **Encryption** | WireGuard | WireGuard/IPsec | None built-in | IPsec (sleeve) |
| **Observability** | Basic flow logs | Hubble (deep) | None | Basic |
| **Complexity** | Medium | Medium-High | Low | Low |
| **Best for** | Production, hybrid | Security-focused, eBPF | Simple clusters | Dev/test |

#### Calico (BGP Mode)

Calico's BGP mode is the gold standard for bare-metal and on-prem clusters. Instead of encapsulating packets in a VXLAN tunnel, it advertises pod routes via BGP — each node tells its peers "10.244.1.0/24 is reachable through me." This means zero encapsulation overhead.

```
┌──── Node 1 ────────┐        BGP Peering        ┌──── Node 2 ────────┐
│  10.244.1.0/24      │◄─────────────────────────▶│  10.244.2.0/24      │
│                     │  "I own 10.244.1.0/24"    │                     │
│  Route table:       │  "I own 10.244.2.0/24"    │  Route table:       │
│  10.244.2.0/24 →    │                           │  10.244.1.0/24 →    │
│    via 192.168.1.11 │                           │    via 192.168.1.10 │
└─────────────────────┘                           └─────────────────────┘
```

#### Cilium (eBPF)

Cilium replaces iptables with eBPF programs attached directly to network interfaces. This bypasses the kernel's networking stack for much of the path, giving significantly better performance at scale (thousands of services).

```
Traditional (iptables):        Cilium (eBPF):
  App → syscall → netfilter      App → syscall → eBPF program
  → iptables chain walk           → direct routing decision
  → conntrack → NAT               → XDP fast path
  → routing decision              → packet sent
  → packet sent
                                  Fewer kernel transitions = lower latency
```

#### Flannel (VXLAN)

Flannel is the simplest CNI — it creates a VXLAN overlay network with no NetworkPolicy support. It's great for learning and development but insufficient for production workloads that need microsegmentation.

---

### kube-proxy: The Service Networking Engine

While CNI plugins handle pod-to-pod networking, `kube-proxy` handles service-to-pod networking. It runs on every node and watches the API server for Service and Endpoints changes, then programs rules to redirect traffic destined for a Service's ClusterIP to the actual pod IPs.

kube-proxy operates in one of three modes:

```
┌─────────────────────────────────────────────────────────────────────┐
│                    kube-proxy Modes                                   │
│                                                                     │
│  1. iptables mode (default)                                         │
│     ┌──────────┐     iptables       ┌─────────┐                    │
│     │ Client   │────▶ DNAT rules ──▶│ Pod     │                    │
│     │ Pod      │     (random pick)   │ Backend │                    │
│     └──────────┘                     └─────────┘                    │
│     - One iptables rule per Service endpoint                        │
│     - O(n) rule matching — slows at 5000+ services                  │
│     - No real load balancing (random selection)                      │
│                                                                     │
│  2. IPVS mode                                                       │
│     ┌──────────┐     IPVS virtual    ┌─────────┐                   │
│     │ Client   │────▶ server       ──▶│ Pod     │                   │
│     │ Pod      │     (hash table)     │ Backend │                   │
│     └──────────┘                      └─────────┘                   │
│     - O(1) connection lookup via hash table                         │
│     - Real load balancing: round-robin, least-conn, source-hash     │
│     - Better for clusters with 1000+ services                       │
│                                                                     │
│  3. eBPF mode (Cilium replaces kube-proxy)                          │
│     ┌──────────┐     eBPF program    ┌─────────┐                   │
│     │ Client   │────▶ (XDP/TC)     ──▶│ Pod     │                   │
│     │ Pod      │                      │ Backend │                   │
│     └──────────┘                      └─────────┘                   │
│     - Fastest: skips entire iptables/netfilter stack                │
│     - Cilium's kube-proxy replacement                               │
│     - Session affinity, DSR, Maglev hashing                         │
└─────────────────────────────────────────────────────────────────────┘
```

#### iptables Deep Dive

When kube-proxy runs in iptables mode, it creates chains of rules for every Service. Here's what a ClusterIP Service looks like in iptables:

```bash
# View the actual iptables rules for a service
kubectl exec -n kube-system kube-proxy-xxxxx -- iptables-save | grep api-gateway

# Simplified view of what's created:
# 1. KUBE-SERVICES chain — matches destination ClusterIP
-A KUBE-SERVICES -d 10.96.100.10/32 -p tcp --dport 3000 -j KUBE-SVC-API-GW

# 2. KUBE-SVC chain — load balances across endpoints
-A KUBE-SVC-API-GW -m statistic --mode random --probability 0.5 -j KUBE-SEP-POD1
-A KUBE-SVC-API-GW -j KUBE-SEP-POD2

# 3. KUBE-SEP chains — DNAT to actual pod IPs
-A KUBE-SEP-POD1 -p tcp -j DNAT --to-destination 10.244.1.5:3000
-A KUBE-SEP-POD2 -p tcp -j DNAT --to-destination 10.244.2.8:3000
```

> **Key insight:** The "random probability" is how iptables achieves even distribution. With 2 endpoints, the first rule has a 50% chance; the second catches everything that falls through. With 3 endpoints: 33%, 50%, 100%.

---

### Network Namespaces and veth Pairs

Every pod runs in its own Linux network namespace, isolated from the host and other pods. The CNI plugin connects each pod's namespace to the host using a **veth pair** — a virtual ethernet cable with one end in the pod and one end on the host.

```bash
# From inside a pod — you see the pod's network namespace
kubectl exec -it api-gateway-xxx -n deployforge -- ip addr
# 1: lo: <LOOPBACK,UP> ... inet 127.0.0.1/8
# 3: eth0@if10: <BROADCAST,UP> ... inet 10.244.1.5/24

# From the node — you see the host end of the veth pair
# (ssh into node or use kind exec)
docker exec -it deployforge-worker -- ip link show
# 10: vethXXXXXX@if3: <BROADCAST,UP> ... master cni0
```

```
┌─────────────── Node ──────────────────────────────┐
│                                                    │
│  ┌──── Pod netns ─────┐                            │
│  │  eth0 (10.244.1.5) │                            │
│  │    │                │                            │
│  │    │ veth pair       │                            │
│  └────┼────────────────┘                            │
│       │                                            │
│       │ host end: vethXXXXXX                        │
│       │                                            │
│  ┌────▼────────────────┐                            │
│  │  cni0 bridge         │  All veth pairs connect    │
│  │  (10.244.1.1)        │  to this bridge            │
│  └────┬────────────────┘                            │
│       │                                            │
│  ┌────▼────────────────┐                            │
│  │  eth0 (192.168.1.10) │  Physical/virtual NIC     │
│  └─────────────────────┘                            │
└────────────────────────────────────────────────────┘
```

---

### Debugging CNI and Pod Networking

When networking breaks, you need to know where to look.

```bash
# Check which CNI plugin is installed
ls /etc/cni/net.d/
# → 10-calico.conflist  (or 10-flannel.conflist, 05-cilium.conflist)

# Check pod IP assignment
kubectl get pods -n deployforge -o wide
# NAME              READY   STATUS    IP            NODE
# api-gateway-xxx   1/1     Running   10.244.1.5    worker-1
# postgres-0        1/1     Running   10.244.2.3    worker-2

# Test pod-to-pod connectivity
kubectl exec -it api-gateway-xxx -n deployforge -- \
  ping -c 3 10.244.2.3
# PING 10.244.2.3: 64 bytes from 10.244.2.3: time=0.5ms

# Check node routing table (from inside a kind node)
docker exec -it deployforge-worker -- ip route
# 10.244.1.0/24 dev cni0 proto kernel scope link src 10.244.1.1
# 10.244.2.0/24 via 172.18.0.3 dev eth0   ← route to other node's pods

# Check CNI plugin logs
kubectl logs -n kube-system -l k8s-app=calico-node --tail=50

# Verify kube-proxy is running and in the expected mode
kubectl logs -n kube-system -l k8s-app=kube-proxy --tail=20 | grep "Using"
# → "Using iptables proxier"
```

Common networking failure modes:

| Symptom | Likely Cause | Debug Command |
|---------|-------------|---------------|
| Pod stuck in `ContainerCreating` | CNI plugin not installed or crashing | `kubectl describe pod`, `journalctl -u kubelet` |
| Pod can't reach pods on same node | Bridge misconfigured | `ip link show cni0`, check veth pairs |
| Pod can't reach pods on other nodes | Overlay broken, firewall blocking VXLAN (UDP 4789) | `tcpdump -i eth0 udp port 4789` |
| Pod can't reach Service ClusterIP | kube-proxy not running or misconfigured | `kubectl logs -n kube-system kube-proxy-*` |
| DNS resolution fails | CoreDNS pods down or misconfigured | `kubectl get pods -n kube-system -l k8s-app=kube-dns` |

---

## Code Examples

### Inspecting Your Cluster's CNI Configuration

```bash
# In a kind cluster, the default CNI is kindnet
kubectl get pods -n kube-system -l app=kindnet
# NAME             READY   STATUS    RESTARTS   AGE
# kindnet-xxxxx    1/1     Running   0          1d

# View the CNI config
kubectl exec -n kube-system kindnet-xxxxx -- cat /etc/cni/net.d/10-kindnet.conflist
```

### Installing Calico (Replacing Default CNI)

```bash
# For a production cluster, install Calico
kubectl apply -f https://raw.githubusercontent.com/projectcalico/calico/v3.27.0/manifests/calico.yaml

# Verify Calico is running
kubectl get pods -n kube-system -l k8s-app=calico-node
# One pod per node, all Running

# Check Calico IPAM allocations
kubectl exec -n kube-system calico-node-xxxxx -- calico-node -bird -show-routes
```

### Verifying the Flat Network Model

```bash
#!/bin/bash
# deploy-network-test.sh — Verify pods can reach each other across nodes

# Deploy two pods on different nodes
cat <<'EOF' | kubectl apply -f -
apiVersion: v1
kind: Pod
metadata:
  name: net-test-1
  namespace: deployforge
  labels:
    app: net-test
spec:
  nodeSelector:
    kubernetes.io/hostname: deployforge-worker
  containers:
  - name: tools
    image: nicolaka/netshoot:latest
    command: ['sleep', '3600']
---
apiVersion: v1
kind: Pod
metadata:
  name: net-test-2
  namespace: deployforge
  labels:
    app: net-test
spec:
  nodeSelector:
    kubernetes.io/hostname: deployforge-worker2
  containers:
  - name: tools
    image: nicolaka/netshoot:latest
    command: ['sleep', '3600']
EOF

# Wait for pods to be ready
kubectl wait --for=condition=Ready pod/net-test-1 pod/net-test-2 \
  -n deployforge --timeout=60s

# Get Pod 2's IP
POD2_IP=$(kubectl get pod net-test-2 -n deployforge -o jsonpath='{.status.podIP}')

# Test connectivity from Pod 1 to Pod 2
kubectl exec -n deployforge net-test-1 -- ping -c 3 "$POD2_IP"
# → 3 packets transmitted, 3 received, 0% packet loss

# Test TCP connectivity
kubectl exec -n deployforge net-test-1 -- nc -zv "$POD2_IP" 80 2>&1 || true

# Trace the route
kubectl exec -n deployforge net-test-1 -- traceroute -n "$POD2_IP"

# Clean up
kubectl delete pod net-test-1 net-test-2 -n deployforge
```

---

## Try It Yourself

### Challenge 1: Map Your Cluster's Network Topology

Inspect your kind cluster's network setup. Answer these questions:
1. What CNI plugin is installed?
2. What is the cluster's pod CIDR?
3. What subnet does each node own?
4. Can you trace a packet from one pod to another on a different node?

<details>
<summary>Show solution</summary>

```bash
#!/bin/bash
set -euo pipefail

echo "=== 1. CNI Plugin ==="
kubectl get pods -n kube-system -o wide | grep -E 'kindnet|calico|cilium|flannel'
# → kindnet pods (kind's default CNI)

echo ""
echo "=== 2. Cluster Pod CIDR ==="
kubectl cluster-info dump | grep -m 1 "cluster-cidr" || \
  kubectl get nodes -o jsonpath='{.items[*].spec.podCIDR}'
# → 10.244.0.0/24 10.244.1.0/24 ...

echo ""
echo "=== 3. Per-Node Subnets ==="
kubectl get nodes -o custom-columns=\
'NAME:.metadata.name,POD-CIDR:.spec.podCIDR'
# NAME                      POD-CIDR
# deployforge-control-plane 10.244.0.0/24
# deployforge-worker        10.244.1.0/24
# deployforge-worker2       10.244.2.0/24

echo ""
echo "=== 4. Cross-Node Packet Trace ==="
# Deploy a netshoot pod
kubectl run net-debug --image=nicolaka/netshoot -n deployforge \
  --restart=Never -- sleep 3600
kubectl wait --for=condition=Ready pod/net-debug -n deployforge --timeout=60s

# Find a pod on a different node
TARGET_POD=$(kubectl get pods -n deployforge -o wide | \
  awk 'NR>1 {print $6}' | head -1)

echo "Tracing route to $TARGET_POD..."
kubectl exec -n deployforge net-debug -- traceroute -n "$TARGET_POD"

# Clean up
kubectl delete pod net-debug -n deployforge --grace-period=0
```

</details>

### Challenge 2: Observe iptables Rules for a Service

Create a simple Service and inspect the iptables rules kube-proxy creates for it.

<details>
<summary>Show solution</summary>

```bash
#!/bin/bash
set -euo pipefail

# Create a test deployment and service
cat <<'EOF' | kubectl apply -f -
apiVersion: apps/v1
kind: Deployment
metadata:
  name: iptables-test
  namespace: deployforge
spec:
  replicas: 2
  selector:
    matchLabels:
      app: iptables-test
  template:
    metadata:
      labels:
        app: iptables-test
    spec:
      containers:
      - name: nginx
        image: nginx:1.25-alpine
        ports:
        - containerPort: 80
---
apiVersion: v1
kind: Service
metadata:
  name: iptables-test-svc
  namespace: deployforge
spec:
  selector:
    app: iptables-test
  ports:
  - port: 80
    targetPort: 80
EOF

kubectl wait --for=condition=Available deployment/iptables-test \
  -n deployforge --timeout=60s

# Get the ClusterIP
CLUSTER_IP=$(kubectl get svc iptables-test-svc -n deployforge \
  -o jsonpath='{.spec.clusterIP}')
echo "Service ClusterIP: $CLUSTER_IP"

# Get the endpoint IPs
kubectl get endpoints iptables-test-svc -n deployforge
# → 10.244.1.x:80,10.244.2.x:80

# On a kind node, inspect the iptables rules
docker exec deployforge-worker iptables-save | grep "$CLUSTER_IP" || \
  echo "(Run this on a node with iptables access)"

# Clean up
kubectl delete deployment iptables-test -n deployforge
kubectl delete svc iptables-test-svc -n deployforge
```

</details>

---

## Capstone Connection

**DeployForge** relies on the Kubernetes flat networking model as the foundation for all service communication:

- **Pod-to-pod communication** — The API Gateway pods reach PostgreSQL and Redis pods by their ClusterIP Services, which kube-proxy resolves to actual pod IPs. Understanding this path is essential for debugging connectivity issues during deployments.
- **CNI plugin choice** — In a production DeployForge deployment, you'd choose Calico (BGP mode for bare-metal) or Cilium (for eBPF-powered NetworkPolicies). In Module 06.4, you'll implement NetworkPolicies that require a CNI plugin with policy support — Flannel won't work.
- **Cross-node traffic** — DeployForge's pod anti-affinity rules (from Module 05) spread API Gateway pods across nodes. This means every API request potentially involves cross-node networking. Understanding the overhead of VXLAN encapsulation vs BGP direct routing helps you make informed CNI choices.
- **kube-proxy mode** — For a cluster running DeployForge at scale (hundreds of services), you'd switch kube-proxy to IPVS mode for O(1) service routing, or use Cilium to replace kube-proxy entirely with eBPF.
- **Debugging skills** — When a DeployForge deployment fails health checks, the first question is often "can the pod reach its dependencies?" The tools in this section (`kubectl exec`, `ping`, `nslookup`, `traceroute`) are your go-to debugging arsenal.
