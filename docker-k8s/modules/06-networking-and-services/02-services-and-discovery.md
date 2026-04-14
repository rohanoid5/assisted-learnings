# 6.2 — Services, DNS & Service Discovery

## Concept

Pod IPs are ephemeral — every time a pod restarts, it gets a new IP. You can't hardcode `10.244.1.5` in your config and expect it to work tomorrow. Kubernetes Services solve this by providing a stable virtual IP (ClusterIP) and DNS name that automatically routes to the current set of healthy pod backends.

Services are the fundamental abstraction for connecting workloads in Kubernetes. They're implemented by kube-proxy (via iptables/IPVS rules) and discovered via CoreDNS. Understanding the five service types — ClusterIP, NodePort, LoadBalancer, ExternalName, and Headless — and when to use each one is critical for any production architecture.

---

## Deep Dive

### Service Types Overview

```
┌─────────────────────────────────────────────────────────────────────┐
│                   Kubernetes Service Types                            │
│                                                                     │
│  ┌─────────────────────────────────────────────────────────────┐    │
│  │  ClusterIP (default)                                         │    │
│  │  Virtual IP reachable ONLY from inside the cluster           │    │
│  │  10.96.100.10:3000 → pod1:3000, pod2:3000                   │    │
│  │  Use: internal service-to-service communication              │    │
│  └─────────────────────────────────────────────────────────────┘    │
│                                                                     │
│  ┌─────────────────────────────────────────────────────────────┐    │
│  │  NodePort (extends ClusterIP)                                │    │
│  │  Opens a port (30000-32767) on EVERY node                    │    │
│  │  <NodeIP>:30080 → ClusterIP:3000 → pod:3000                 │    │
│  │  Use: dev/test external access, bare-metal without LB        │    │
│  └─────────────────────────────────────────────────────────────┘    │
│                                                                     │
│  ┌─────────────────────────────────────────────────────────────┐    │
│  │  LoadBalancer (extends NodePort)                              │    │
│  │  Provisions cloud load balancer (AWS ELB, GCP LB, etc.)      │    │
│  │  external-ip:3000 → NodePort → ClusterIP → pod:3000         │    │
│  │  Use: production external access on cloud providers          │    │
│  └─────────────────────────────────────────────────────────────┘    │
│                                                                     │
│  ┌─────────────────────────────────────────────────────────────┐    │
│  │  ExternalName                                                │    │
│  │  CNAME alias to an external DNS name (no proxying)           │    │
│  │  my-db.ns.svc → rds.us-east-1.amazonaws.com                 │    │
│  │  Use: reference external services with in-cluster DNS name   │    │
│  └─────────────────────────────────────────────────────────────┘    │
│                                                                     │
│  ┌─────────────────────────────────────────────────────────────┐    │
│  │  Headless (ClusterIP: None)                                  │    │
│  │  No virtual IP — DNS returns individual pod IPs directly     │    │
│  │  nslookup postgres → 10.244.1.5, 10.244.2.3                 │    │
│  │  Use: StatefulSets needing stable per-pod DNS names          │    │
│  └─────────────────────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────────────────────┘
```

---

### ClusterIP Services

ClusterIP is the default Service type and the foundation of all other types. It allocates a virtual IP from the service CIDR range (e.g., `10.96.0.0/12`) that is only reachable from within the cluster.

```yaml
apiVersion: v1
kind: Service
metadata:
  name: api-gateway
  namespace: deployforge
  labels:
    app: api-gateway
    tier: frontend
spec:
  type: ClusterIP           # Default — can be omitted
  selector:
    app: api-gateway         # Routes to pods with this label
  ports:
  - name: http
    port: 3000               # Port the service listens on
    targetPort: 3000         # Port the pod container listens on
    protocol: TCP
```

```
┌──────────────────────────────────────────────────────────────────┐
│                    ClusterIP Service Flow                          │
│                                                                  │
│   Client Pod                                                     │
│   (worker-xxx)                                                   │
│       │                                                          │
│       │  curl http://api-gateway.deployforge.svc.cluster.local:3000
│       │                                                          │
│       ▼                                                          │
│   ┌──────────────────────┐                                       │
│   │  CoreDNS              │  Resolves to ClusterIP               │
│   │  api-gateway →        │  10.96.100.10                        │
│   │  10.96.100.10         │                                      │
│   └──────────┬───────────┘                                       │
│              │                                                   │
│              ▼                                                   │
│   ┌──────────────────────┐                                       │
│   │  kube-proxy           │  iptables DNAT rule:                 │
│   │  (iptables/IPVS)     │  10.96.100.10:3000 →                 │
│   │                       │  10.244.1.5:3000 (50%)               │
│   │                       │  10.244.2.8:3000 (50%)               │
│   └──────────┬───────────┘                                       │
│              │                                                   │
│         ┌────┴────┐                                              │
│         ▼         ▼                                              │
│   ┌──────────┐ ┌──────────┐                                      │
│   │ api-gw-1 │ │ api-gw-2 │  Actual pod backends                │
│   │10.244.1.5│ │10.244.2.8│                                      │
│   └──────────┘ └──────────┘                                      │
└──────────────────────────────────────────────────────────────────┘
```

> **Key insight:** The ClusterIP doesn't correspond to any network interface — it's a "virtual" IP that only exists in iptables/IPVS rules. You can't ping it (ICMP isn't typically programmed), but TCP/UDP traffic to it gets DNAT'd to a real pod IP.

---

### NodePort Services

NodePort extends ClusterIP by opening a static port on every node in the cluster. External traffic to `<any-node-ip>:<nodePort>` gets routed to the Service.

```yaml
apiVersion: v1
kind: Service
metadata:
  name: api-gateway-nodeport
  namespace: deployforge
spec:
  type: NodePort
  selector:
    app: api-gateway
  ports:
  - name: http
    port: 3000               # ClusterIP port (internal)
    targetPort: 3000         # Pod port
    nodePort: 30080          # External port (30000-32767)
    protocol: TCP
```

```bash
# Access from outside the cluster
curl http://<node-ip>:30080/health
# Even if the pod isn't on this node, kube-proxy routes the traffic

# In kind, get the node's IP
NODE_IP=$(kubectl get nodes deployforge-worker \
  -o jsonpath='{.status.addresses[?(@.type=="InternalIP")].address}')
curl http://${NODE_IP}:30080/health
```

**When to use NodePort:**
- Development and testing (quick external access without a load balancer)
- Bare-metal clusters without a cloud load balancer
- Behind an external load balancer you manage yourself

**When NOT to use NodePort:**
- Production on cloud providers (use LoadBalancer or Ingress)
- When you need more than ~2767 externally-exposed services
- When you need TLS termination (use Ingress instead)

---

### LoadBalancer Services

LoadBalancer extends NodePort by provisioning a cloud provider's load balancer (AWS ELB/NLB, GCP LB, Azure LB). The cloud controller manager creates the external resource and configures it to forward traffic to the NodePorts.

```yaml
apiVersion: v1
kind: Service
metadata:
  name: api-gateway-lb
  namespace: deployforge
  annotations:
    # AWS-specific: use Network Load Balancer
    service.beta.kubernetes.io/aws-load-balancer-type: "nlb"
    # Preserve client source IP
    service.beta.kubernetes.io/aws-load-balancer-scheme: "internet-facing"
spec:
  type: LoadBalancer
  selector:
    app: api-gateway
  ports:
  - name: http
    port: 80
    targetPort: 3000
  - name: https
    port: 443
    targetPort: 3000
```

```
┌──────────────────────────────────────────────────────────────────┐
│              LoadBalancer Service Architecture                     │
│                                                                  │
│  Internet                                                        │
│     │                                                            │
│     ▼                                                            │
│  ┌──────────────────────────┐                                    │
│  │  Cloud Load Balancer      │  External IP: 34.102.136.180      │
│  │  (AWS NLB / GCP LB)      │  Health checks NodePorts          │
│  └──────────┬───────────────┘                                    │
│             │                                                    │
│     ┌───────┼───────┐                                            │
│     ▼       ▼       ▼                                            │
│  Node 1   Node 2   Node 3     ← NodePort 30080 on ALL nodes     │
│     │       │       │                                            │
│     └───────┼───────┘                                            │
│             ▼                                                    │
│  ┌──────────────────────────┐                                    │
│  │  ClusterIP 10.96.100.10  │  ← kube-proxy DNAT                │
│  └──────────┬───────────────┘                                    │
│         ┌───┴───┐                                                │
│         ▼       ▼                                                │
│      Pod 1    Pod 2           ← Actual backends                  │
└──────────────────────────────────────────────────────────────────┘
```

> **Cost warning:** Each LoadBalancer Service provisions a separate cloud load balancer. At $15–25/month per LB (AWS), exposing 10 services means $150–250/month. This is why Ingress exists — one LoadBalancer fronting multiple services.

---

### ExternalName Services

ExternalName creates a CNAME record in CoreDNS, mapping an in-cluster service name to an external DNS name. No proxying or port-forwarding occurs.

```yaml
apiVersion: v1
kind: Service
metadata:
  name: external-db
  namespace: deployforge
spec:
  type: ExternalName
  externalName: prod-db.cluster-xxxxx.us-east-1.rds.amazonaws.com
```

```bash
# From inside a pod, this DNS lookup:
nslookup external-db.deployforge.svc.cluster.local
# Returns: prod-db.cluster-xxxxx.us-east-1.rds.amazonaws.com

# Your app connects to "external-db" — if you later migrate the DB
# into the cluster, just change the Service to a ClusterIP type.
# No application code changes needed.
```

---

### Headless Services

A headless Service has `clusterIP: None`. Instead of returning a virtual IP, DNS queries return the individual pod IPs. This is essential for StatefulSets where clients need to address specific pods.

```yaml
apiVersion: v1
kind: Service
metadata:
  name: postgres
  namespace: deployforge
spec:
  clusterIP: None             # ← Headless!
  selector:
    app: postgres
  ports:
  - name: postgres
    port: 5432
    targetPort: 5432
```

```bash
# DNS returns individual pod IPs (not a virtual IP)
kubectl exec -n deployforge api-gateway-xxx -- nslookup postgres.deployforge.svc.cluster.local
# Server:    10.96.0.10
# Name:      postgres.deployforge.svc.cluster.local
# Address:   10.244.1.5
# Address:   10.244.2.3

# For StatefulSets, each pod gets a stable DNS name:
# postgres-0.postgres.deployforge.svc.cluster.local → 10.244.1.5
# postgres-1.postgres.deployforge.svc.cluster.local → 10.244.2.3
```

```
┌──────────────────────────────────────────────────────────────────┐
│         Headless Service: DNS Returns Pod IPs Directly            │
│                                                                  │
│   Regular ClusterIP:          Headless (clusterIP: None):        │
│                                                                  │
│   nslookup api-gateway        nslookup postgres                  │
│   → 10.96.100.10 (VIP)       → 10.244.1.5 (pod-0)              │
│                                → 10.244.2.3 (pod-1)              │
│                                                                  │
│   Client picks randomly       Client can address specific pods   │
│   (kube-proxy decides)        (app-level logic decides)          │
│                                                                  │
│   Good for: stateless          Good for: stateful workloads,     │
│   load balancing               leader election, replication      │
└──────────────────────────────────────────────────────────────────┘
```

---

### CoreDNS and Service Discovery

CoreDNS is the cluster DNS server, running as a Deployment in `kube-system`. Every pod is configured (via `/etc/resolv.conf`) to use CoreDNS for DNS resolution. CoreDNS watches the Kubernetes API for Service and Endpoint changes, serving DNS records automatically.

#### DNS Record Formats

```
┌───────────────────────────────────────────────────────────────────┐
│                   Kubernetes DNS Records                           │
│                                                                   │
│  Service A record:                                                │
│    <service>.<namespace>.svc.cluster.local                        │
│    api-gateway.deployforge.svc.cluster.local → 10.96.100.10      │
│                                                                   │
│  Service SRV record:                                              │
│    _<port-name>._<protocol>.<service>.<namespace>.svc.cluster.local
│    _http._tcp.api-gateway.deployforge.svc.cluster.local           │
│    → 0 100 3000 api-gateway.deployforge.svc.cluster.local         │
│                                                                   │
│  StatefulSet pod A record (requires headless service):            │
│    <pod-name>.<service>.<namespace>.svc.cluster.local             │
│    postgres-0.postgres.deployforge.svc.cluster.local → 10.244.1.5│
│                                                                   │
│  Pod A record (if enabled):                                       │
│    <pod-ip-dashed>.<namespace>.pod.cluster.local                  │
│    10-244-1-5.deployforge.pod.cluster.local → 10.244.1.5         │
└───────────────────────────────────────────────────────────────────┘
```

#### DNS Search Domains

Every pod's `/etc/resolv.conf` includes search domains, so you don't need the full FQDN:

```bash
kubectl exec -n deployforge api-gateway-xxx -- cat /etc/resolv.conf
# nameserver 10.96.0.10
# search deployforge.svc.cluster.local svc.cluster.local cluster.local
# options ndots:5
```

This means the following are all equivalent (from a pod in the `deployforge` namespace):

```bash
# All resolve to the same ClusterIP
curl http://api-gateway:3000                                          # short name
curl http://api-gateway.deployforge:3000                              # namespace-qualified
curl http://api-gateway.deployforge.svc:3000                          # svc-qualified
curl http://api-gateway.deployforge.svc.cluster.local:3000            # FQDN
```

> **Performance tip:** The `ndots:5` setting means any name with fewer than 5 dots gets the search domains appended first. `api-gateway` has 0 dots, so it tries `api-gateway.deployforge.svc.cluster.local` first — which works. But `some.external.api.com` has 3 dots, so Kubernetes appends search domains first, causing 4 failed lookups before the real resolution. For external domains, use the trailing dot: `some.external.api.com.` to skip the search path.

---

### Endpoints and EndpointSlices

When you create a Service with a selector, Kubernetes automatically creates an Endpoints object that tracks the IPs of all matching pods. In clusters with many endpoints, EndpointSlices (groups of up to 100 endpoints) provide better scalability.

```bash
# View endpoints for a service
kubectl get endpoints api-gateway -n deployforge
# NAME          ENDPOINTS                         AGE
# api-gateway   10.244.1.5:3000,10.244.2.8:3000   1d

# View EndpointSlices (more detailed)
kubectl get endpointslices -n deployforge -l kubernetes.io/service-name=api-gateway
# NAME                  ADDRESSTYPE   PORTS   ENDPOINTS         AGE
# api-gateway-xxxxx     IPv4          3000    10.244.1.5,...    1d

# Detailed endpoint info including pod references and conditions
kubectl describe endpointslice api-gateway-xxxxx -n deployforge
```

#### Services Without Selectors

You can create a Service without a selector and manually define its endpoints. This is useful for integrating external services:

```yaml
# Service without selector
apiVersion: v1
kind: Service
metadata:
  name: external-metrics-db
  namespace: deployforge
spec:
  ports:
  - port: 5432
    targetPort: 5432
---
# Manually define endpoints
apiVersion: v1
kind: Endpoints
metadata:
  name: external-metrics-db    # Must match Service name
  namespace: deployforge
subsets:
- addresses:
  - ip: 10.0.0.50             # External database IP
  - ip: 10.0.0.51             # Replica
  ports:
  - port: 5432
```

---

### Session Affinity

By default, kube-proxy distributes traffic randomly across backends. If you need a client to consistently reach the same pod (e.g., for WebSocket connections or in-memory sessions), use session affinity:

```yaml
apiVersion: v1
kind: Service
metadata:
  name: api-gateway
  namespace: deployforge
spec:
  selector:
    app: api-gateway
  sessionAffinity: ClientIP        # Sticky sessions based on client IP
  sessionAffinityConfig:
    clientIP:
      timeoutSeconds: 10800       # 3 hours (default)
  ports:
  - port: 3000
    targetPort: 3000
```

> **Warning:** Session affinity only works with `ClientIP` — there's no cookie-based affinity at the Service level. For cookie-based stickiness, use an Ingress controller annotation.

---

### externalTrafficPolicy

When external traffic arrives at a NodePort or LoadBalancer Service, it might land on a node that doesn't have a matching pod. By default, kube-proxy forwards the traffic to a pod on another node — but this adds a hop and loses the client's source IP.

```yaml
apiVersion: v1
kind: Service
metadata:
  name: api-gateway
  namespace: deployforge
spec:
  type: NodePort
  externalTrafficPolicy: Local     # Only route to pods on THIS node
  selector:
    app: api-gateway
  ports:
  - port: 3000
    targetPort: 3000
    nodePort: 30080
```

```
┌─────────────────────────────────────────────────────────────────┐
│            externalTrafficPolicy Comparison                      │
│                                                                 │
│  Cluster (default):            Local:                           │
│                                                                 │
│  Client → Node 1 (no pod)      Client → Node 1 (no pod)        │
│            │                              │                     │
│            │ kube-proxy forwards          ✗ Connection refused!  │
│            │ to Node 2                    (no local endpoints)   │
│            ▼                                                    │
│          Node 2 (has pod)      Client → Node 2 (has pod)        │
│            │                              │                     │
│            ▼                              ▼                     │
│          Pod ← source NAT'd    Pod ← original client IP         │
│          (loses client IP)     (preserved!)                      │
│                                                                 │
│  ✅ Even traffic distribution   ✅ Client IP preserved           │
│  ✅ All nodes accept traffic   ✅ One fewer network hop          │
│  ❌ Client IP lost             ❌ Uneven load if pods unevenly  │
│  ❌ Extra network hop              distributed across nodes      │
└─────────────────────────────────────────────────────────────────┘
```

---

## Code Examples

### Creating All DeployForge Services

```yaml
# deployforge-services.yaml
# ClusterIP services for internal communication

---
apiVersion: v1
kind: Service
metadata:
  name: api-gateway
  namespace: deployforge
  labels:
    app: api-gateway
    tier: frontend
spec:
  type: ClusterIP
  selector:
    app: api-gateway
  ports:
  - name: http
    port: 3000
    targetPort: 3000

---
apiVersion: v1
kind: Service
metadata:
  name: worker
  namespace: deployforge
  labels:
    app: worker
    tier: backend
spec:
  type: ClusterIP
  selector:
    app: worker
  ports:
  - name: grpc
    port: 4000
    targetPort: 4000

---
# Headless service for PostgreSQL StatefulSet
apiVersion: v1
kind: Service
metadata:
  name: postgres
  namespace: deployforge
  labels:
    app: postgres
    tier: data
spec:
  clusterIP: None
  selector:
    app: postgres
  ports:
  - name: postgres
    port: 5432
    targetPort: 5432

---
# Regular ClusterIP for PostgreSQL (for services that don't need
# to address specific pods)
apiVersion: v1
kind: Service
metadata:
  name: postgres-read
  namespace: deployforge
  labels:
    app: postgres
    tier: data
spec:
  type: ClusterIP
  selector:
    app: postgres
  ports:
  - name: postgres
    port: 5432
    targetPort: 5432

---
# Headless service for Redis StatefulSet
apiVersion: v1
kind: Service
metadata:
  name: redis
  namespace: deployforge
  labels:
    app: redis
    tier: data
spec:
  clusterIP: None
  selector:
    app: redis
  ports:
  - name: redis
    port: 6379
    targetPort: 6379
```

```bash
# Apply all services
kubectl apply -f deployforge-services.yaml

# Verify services
kubectl get svc -n deployforge
# NAME          TYPE        CLUSTER-IP     EXTERNAL-IP   PORT(S)    AGE
# api-gateway   ClusterIP   10.96.100.10   <none>        3000/TCP   1s
# worker        ClusterIP   10.96.100.11   <none>        4000/TCP   1s
# postgres      ClusterIP   None           <none>        5432/TCP   1s
# postgres-read ClusterIP   10.96.100.12   <none>        5432/TCP   1s
# redis         ClusterIP   None           <none>        6379/TCP   1s

# Verify DNS resolution from a pod
kubectl exec -n deployforge api-gateway-xxx -- nslookup api-gateway.deployforge.svc.cluster.local
kubectl exec -n deployforge api-gateway-xxx -- nslookup postgres.deployforge.svc.cluster.local

# Verify endpoints
kubectl get endpoints -n deployforge
```

### Testing Service Discovery

```bash
#!/bin/bash
# test-service-discovery.sh — Verify all DeployForge services are discoverable

NS="deployforge"

echo "=== Testing DNS Resolution ==="

# Deploy a debug pod
kubectl run dns-test --image=busybox:1.36 -n $NS \
  --restart=Never -- sleep 3600
kubectl wait --for=condition=Ready pod/dns-test -n $NS --timeout=60s

# Test short name resolution (same namespace)
echo "Short name (api-gateway):"
kubectl exec -n $NS dns-test -- nslookup api-gateway

# Test cross-namespace resolution
echo "Cross-namespace (kube-dns.kube-system):"
kubectl exec -n $NS dns-test -- nslookup kube-dns.kube-system.svc.cluster.local

# Test headless service returns pod IPs
echo "Headless service (postgres):"
kubectl exec -n $NS dns-test -- nslookup postgres.$NS.svc.cluster.local

# Test StatefulSet pod DNS
echo "StatefulSet pod (postgres-0):"
kubectl exec -n $NS dns-test -- nslookup postgres-0.postgres.$NS.svc.cluster.local

# Clean up
kubectl delete pod dns-test -n $NS --grace-period=0
```

---

## Try It Yourself

### Challenge 1: Build the Complete DeployForge Service Layer

Create all five Services for DeployForge (API Gateway ClusterIP, Worker ClusterIP, PostgreSQL Headless, Redis Headless, and an Nginx NodePort for external dev access). Verify each Service resolves via DNS and routes to the correct pods.

<details>
<summary>Show solution</summary>

```bash
#!/bin/bash
set -euo pipefail

NS="deployforge"

echo "=== Creating all DeployForge services ==="
cat <<'EOF' | kubectl apply -f -
apiVersion: v1
kind: Service
metadata:
  name: api-gateway
  namespace: deployforge
  labels:
    app: api-gateway
    tier: frontend
spec:
  selector:
    app: api-gateway
  ports:
  - name: http
    port: 3000
    targetPort: 3000
---
apiVersion: v1
kind: Service
metadata:
  name: worker
  namespace: deployforge
  labels:
    app: worker
    tier: backend
spec:
  selector:
    app: worker
  ports:
  - name: grpc
    port: 4000
    targetPort: 4000
---
apiVersion: v1
kind: Service
metadata:
  name: postgres
  namespace: deployforge
  labels:
    app: postgres
    tier: data
spec:
  clusterIP: None
  selector:
    app: postgres
  ports:
  - name: postgres
    port: 5432
    targetPort: 5432
---
apiVersion: v1
kind: Service
metadata:
  name: redis
  namespace: deployforge
  labels:
    app: redis
    tier: data
spec:
  clusterIP: None
  selector:
    app: redis
  ports:
  - name: redis
    port: 6379
    targetPort: 6379
---
apiVersion: v1
kind: Service
metadata:
  name: nginx-external
  namespace: deployforge
  labels:
    app: nginx
    tier: frontend
spec:
  type: NodePort
  selector:
    app: nginx
  ports:
  - name: http
    port: 80
    targetPort: 80
    nodePort: 30080
EOF

echo ""
echo "=== Verifying services ==="
kubectl get svc -n $NS

echo ""
echo "=== Verifying endpoints ==="
kubectl get endpoints -n $NS

echo ""
echo "=== Testing DNS resolution ==="
kubectl run dns-verify --image=busybox:1.36 -n $NS \
  --restart=Never -- sleep 3600
kubectl wait --for=condition=Ready pod/dns-verify -n $NS --timeout=60s

for SVC in api-gateway worker postgres redis; do
  echo "Resolving $SVC:"
  kubectl exec -n $NS dns-verify -- nslookup $SVC 2>&1 | head -6
  echo "---"
done

kubectl delete pod dns-verify -n $NS --grace-period=0
```

</details>

### Challenge 2: Debug a Broken Service

A Service named `api-gateway-broken` exists but doesn't route traffic. Find and fix the problem.

Hints: Check the selector labels, endpoint list, and pod labels.

<details>
<summary>Show solution</summary>

```bash
#!/bin/bash
set -euo pipefail

NS="deployforge"

# Create the broken service (selector doesn't match any pods)
cat <<'EOF' | kubectl apply -f -
apiVersion: v1
kind: Service
metadata:
  name: api-gateway-broken
  namespace: deployforge
spec:
  selector:
    app: api-gatway    # Typo! Should be "api-gateway"
  ports:
  - port: 3000
    targetPort: 3000
EOF

echo "=== Step 1: Check endpoints (should be empty) ==="
kubectl get endpoints api-gateway-broken -n $NS
# → ENDPOINTS: <none>

echo ""
echo "=== Step 2: Check what the service selects ==="
kubectl get svc api-gateway-broken -n $NS -o jsonpath='{.spec.selector}' | python3 -m json.tool
# → {"app": "api-gatway"}  ← typo!

echo ""
echo "=== Step 3: Check what labels pods actually have ==="
kubectl get pods -n $NS --show-labels | grep api-gateway
# → app=api-gateway,tier=frontend  ← correct spelling

echo ""
echo "=== Step 4: Fix the selector ==="
kubectl patch svc api-gateway-broken -n $NS \
  --type='json' -p='[{"op":"replace","path":"/spec/selector/app","value":"api-gateway"}]'

echo ""
echo "=== Step 5: Verify endpoints now populated ==="
kubectl get endpoints api-gateway-broken -n $NS
# → 10.244.1.5:3000,10.244.2.8:3000

# Clean up
kubectl delete svc api-gateway-broken -n $NS
```

</details>

---

## Capstone Connection

**DeployForge** uses a layered service architecture that maps directly to the service types covered in this section:

- **ClusterIP Services** — The API Gateway and Worker services use ClusterIP for internal communication. The API Gateway calls the Worker service via `worker.deployforge.svc.cluster.local:4000`, and both connect to databases via their service DNS names. No external exposure needed for these internal paths.
- **Headless Services** — PostgreSQL and Redis run as StatefulSets with headless services, giving each pod a stable DNS name (`postgres-0.postgres.deployforge.svc.cluster.local`). This is critical for database replication, where the primary must be addressable by replicas.
- **CoreDNS** — All DeployForge services use DNS-based discovery. The API Gateway's `DATABASE_URL` environment variable uses the headless service name (`postgres-0.postgres.deployforge.svc.cluster.local:5432/deployforge`), not a pod IP. If the pod restarts on a different node with a new IP, DNS automatically resolves to the new address.
- **NodePort (dev only)** — During development on kind, a NodePort exposes Nginx on port 30080 for local testing. In Module 06.3, you'll replace this with a proper Ingress controller.
- **Session affinity** — If DeployForge's API Gateway needs WebSocket support (for real-time deployment logs), you'd enable `sessionAffinity: ClientIP` to keep WebSocket connections pinned to the same pod.
- **externalTrafficPolicy** — When DeployForge moves to a cloud provider with a LoadBalancer Service, setting `externalTrafficPolicy: Local` preserves client IPs for audit logging and rate limiting.
