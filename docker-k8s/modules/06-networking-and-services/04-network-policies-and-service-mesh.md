# 6.4 — NetworkPolicies & Service Mesh

## Concept

By default, Kubernetes allows all pods to communicate with all other pods — there's zero network isolation. This is great for getting started but terrible for security. A compromised pod in the frontend tier can directly access your database, exfiltrate data to the internet, or pivot to other namespaces.

NetworkPolicies fix this by acting as a firewall at the pod level. They use label selectors to define which pods can talk to which, on which ports, in which direction (ingress/egress). Combined with a default-deny policy, NetworkPolicies implement **microsegmentation** — the same zero-trust principle that enterprise networks have used for decades, now applied to individual pods.

Service meshes take this further by injecting a sidecar proxy into every pod, providing mutual TLS (mTLS) between services, traffic management (retries, timeouts, circuit breaking), and deep observability — all without changing application code.

---

## Deep Dive

### NetworkPolicy Basics

A NetworkPolicy is a namespace-scoped resource that selects pods and defines allowed ingress (incoming) and/or egress (outgoing) traffic rules. Any traffic not explicitly allowed is denied.

```
┌─────────────────────────────────────────────────────────────────────┐
│                  NetworkPolicy Mental Model                          │
│                                                                     │
│  1. Select which pods this policy applies to (podSelector)          │
│  2. Define allowed ingress sources and/or egress destinations       │
│  3. Everything else is DENIED for the selected pods                 │
│                                                                     │
│  ┌──────────────────────┐          ┌──────────────────────┐        │
│  │  Allowed Source       │────✅───▶│  Selected Pods        │        │
│  │  (label/namespace/IP) │          │  (podSelector match)  │        │
│  └──────────────────────┘          └──────────┬───────────┘        │
│                                               │                    │
│  ┌──────────────────────┐                     │                    │
│  │  Blocked Source       │────❌───▶           │                    │
│  │  (no matching rule)   │                    │                    │
│  └──────────────────────┘                     │                    │
│                                               │ egress             │
│                                    ┌──────────▼───────────┐        │
│                                    │  Allowed Destination  │  ✅    │
│                                    └──────────────────────┘        │
│                                    ┌──────────────────────┐        │
│                                    │  Blocked Destination  │  ❌    │
│                                    └──────────────────────┘        │
└─────────────────────────────────────────────────────────────────────┘
```

> **Critical prerequisite:** NetworkPolicies are enforced by the CNI plugin, not by Kubernetes itself. If your CNI doesn't support NetworkPolicies (e.g., Flannel, kindnet), the policies are silently ignored. Calico, Cilium, and Weave all support them.

#### Installing a NetworkPolicy-Capable CNI in kind

```bash
# kind's default CNI (kindnet) doesn't support NetworkPolicies.
# Install Calico for NetworkPolicy support:

# Create kind cluster WITHOUT default CNI
cat <<EOF | kind create cluster --config=-
kind: Cluster
apiVersion: kind.x-k8s.io/v1alpha4
name: deployforge
networking:
  disableDefaultCNI: true    # Don't install kindnet
  podSubnet: "10.244.0.0/16"
nodes:
- role: control-plane
- role: worker
- role: worker
EOF

# Install Calico
kubectl apply -f https://raw.githubusercontent.com/projectcalico/calico/v3.27.0/manifests/calico.yaml

# Wait for Calico to be ready
kubectl wait --for=condition=Ready pods -l k8s-app=calico-node \
  -n kube-system --timeout=120s
```

---

### Default Deny Policies

The first step in any NetworkPolicy strategy is establishing a default-deny baseline. Without this, pods that aren't selected by any NetworkPolicy remain wide open.

```yaml
# Default deny ALL ingress traffic in the namespace
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: default-deny-ingress
  namespace: deployforge
spec:
  podSelector: {}              # Empty = selects ALL pods in namespace
  policyTypes:
  - Ingress                    # Only affects ingress; egress is unrestricted

---
# Default deny ALL egress traffic in the namespace
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: default-deny-egress
  namespace: deployforge
spec:
  podSelector: {}
  policyTypes:
  - Egress

---
# Allow DNS egress (required — pods need DNS to function)
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: allow-dns-egress
  namespace: deployforge
spec:
  podSelector: {}
  policyTypes:
  - Egress
  egress:
  - to:
    - namespaceSelector:
        matchLabels:
          kubernetes.io/metadata.name: kube-system
      podSelector:
        matchLabels:
          k8s-app: kube-dns
    ports:
    - protocol: UDP
      port: 53
    - protocol: TCP
      port: 53
```

```
┌─────────────────────────────────────────────────────────────────────┐
│            Default Deny + Allow DNS                                  │
│                                                                     │
│  After applying these policies, the only allowed traffic is:        │
│                                                                     │
│  ┌──────────────┐      UDP/TCP 53       ┌──────────────┐           │
│  │ Any pod in   │─────────────────────▶│ CoreDNS       │           │
│  │ deployforge  │                       │ (kube-system) │           │
│  └──────────────┘                       └──────────────┘           │
│         │                                                           │
│         ├──── All other ingress: ❌ DENIED                          │
│         └──── All other egress:  ❌ DENIED                          │
│                                                                     │
│  Now you explicitly allow only the traffic you need.                │
└─────────────────────────────────────────────────────────────────────┘
```

> **Key insight:** Always allow DNS egress when using default-deny egress policies. Without DNS, pods can't resolve service names and essentially can't communicate with anything.

---

### Ingress Rules: Who Can Talk TO My Pods

Ingress rules define which sources are allowed to send traffic to the selected pods.

```yaml
# Allow API Gateway to receive traffic from Ingress controller
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: allow-ingress-to-api-gateway
  namespace: deployforge
spec:
  podSelector:
    matchLabels:
      app: api-gateway
      tier: frontend
  policyTypes:
  - Ingress
  ingress:
  - from:
    # Allow from Ingress controller namespace
    - namespaceSelector:
        matchLabels:
          kubernetes.io/metadata.name: ingress-nginx
      podSelector:
        matchLabels:
          app.kubernetes.io/component: controller
    ports:
    - protocol: TCP
      port: 3000
```

#### Selector Types

NetworkPolicy rules support three types of selectors — and they compose with AND/OR logic that's easy to get wrong:

```yaml
ingress:
- from:
  # AND logic: namespace AND pod selector must BOTH match
  - namespaceSelector:
      matchLabels:
        env: production
    podSelector:
      matchLabels:
        role: frontend
  # OR logic: this is a SEPARATE "from" entry
  - ipBlock:
      cidr: 10.0.0.0/8
      except:
      - 10.0.1.0/24
```

```
┌──────────────────────────────────────────────────────────────────┐
│          NetworkPolicy Selector Logic (Critical!)                 │
│                                                                  │
│  WITHIN a single "from" entry = AND                              │
│  ─────────────────────────────                                   │
│  - namespaceSelector: {env: prod}                                │
│    podSelector: {role: frontend}                                 │
│  → Must be in "prod" namespace AND have "frontend" label         │
│                                                                  │
│  BETWEEN "from" entries = OR                                     │
│  ───────────────────────────                                     │
│  - namespaceSelector: {env: prod}                                │
│    podSelector: {role: frontend}                                 │
│  - ipBlock: {cidr: 10.0.0.0/8}                                  │
│  → Either (prod namespace + frontend) OR (10.0.0.0/8 range)     │
│                                                                  │
│  ⚠️ Common mistake — these are DIFFERENT:                        │
│                                                                  │
│  # This allows pods from prod namespace WITH frontend label:     │
│  - namespaceSelector: {env: prod}                                │
│    podSelector: {role: frontend}                                 │
│                                                                  │
│  # This allows ALL pods from prod namespace                      │
│  # OR ANY pod with frontend label (in ANY namespace!):           │
│  - namespaceSelector: {env: prod}                                │
│  - podSelector: {role: frontend}                                 │
└──────────────────────────────────────────────────────────────────┘
```

---

### Egress Rules: Where Can My Pods Talk TO

Egress rules define which destinations the selected pods are allowed to reach.

```yaml
# API Gateway can only talk to Worker and PostgreSQL
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: api-gateway-egress
  namespace: deployforge
spec:
  podSelector:
    matchLabels:
      app: api-gateway
  policyTypes:
  - Egress
  egress:
  # Allow DNS
  - to:
    - namespaceSelector:
        matchLabels:
          kubernetes.io/metadata.name: kube-system
      podSelector:
        matchLabels:
          k8s-app: kube-dns
    ports:
    - protocol: UDP
      port: 53
    - protocol: TCP
      port: 53
  # Allow to Worker service
  - to:
    - podSelector:
        matchLabels:
          app: worker
    ports:
    - protocol: TCP
      port: 4000
  # Allow to PostgreSQL
  - to:
    - podSelector:
        matchLabels:
          app: postgres
    ports:
    - protocol: TCP
      port: 5432
  # Allow to Redis
  - to:
    - podSelector:
        matchLabels:
          app: redis
    ports:
    - protocol: TCP
      port: 6379
```

---

### CIDR-Based Rules

For controlling traffic to/from external networks:

```yaml
# Allow Worker pods to reach external webhook endpoints
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: worker-external-egress
  namespace: deployforge
spec:
  podSelector:
    matchLabels:
      app: worker
  policyTypes:
  - Egress
  egress:
  # Allow HTTPS to external services
  - to:
    - ipBlock:
        cidr: 0.0.0.0/0             # All external IPs
        except:
        - 10.0.0.0/8                # Block private ranges
        - 172.16.0.0/12             # (force internal traffic through
        - 192.168.0.0/16            #  services, not direct pod IPs)
    ports:
    - protocol: TCP
      port: 443
```

---

### Namespace Selectors

Control cross-namespace traffic by labeling namespaces:

```bash
# Label the monitoring namespace
kubectl label namespace monitoring purpose=monitoring

# Label the deployforge namespace
kubectl label namespace deployforge purpose=application
```

```yaml
# Allow Prometheus (monitoring namespace) to scrape DeployForge pods
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: allow-prometheus-scrape
  namespace: deployforge
spec:
  podSelector: {}                    # All pods in deployforge
  policyTypes:
  - Ingress
  ingress:
  - from:
    - namespaceSelector:
        matchLabels:
          purpose: monitoring
      podSelector:
        matchLabels:
          app: prometheus
    ports:
    - protocol: TCP
      port: 9090                     # Metrics endpoint
```

---

### Complete DeployForge Microsegmentation

Here's the full NetworkPolicy set implementing three-tier microsegmentation:

```
┌─────────────────────────────────────────────────────────────────────┐
│         DeployForge NetworkPolicy Architecture                       │
│                                                                     │
│  ┌─── External ──────┐                                              │
│  │ Ingress Controller │                                              │
│  └────────┬──────────┘                                              │
│           │ TCP 3000 ✅                                              │
│           ▼                                                         │
│  ┌─── Frontend Tier ─────────────────────────────────────────┐      │
│  │  api-gateway pods                                          │      │
│  │  Ingress: from ingress-nginx namespace only                │      │
│  │  Egress: to worker (4000), postgres (5432), redis (6379)  │      │
│  └────────┬───────────────────────────────────────────────────┘      │
│           │ TCP 4000 ✅                                              │
│           ▼                                                         │
│  ┌─── Backend Tier ──────────────────────────────────────────┐      │
│  │  worker pods                                               │      │
│  │  Ingress: from api-gateway pods only                       │      │
│  │  Egress: to postgres (5432), redis (6379), external (443) │      │
│  └────────┬───────────────────────────────────────────────────┘      │
│           │ TCP 5432, 6379 ✅                                        │
│           ▼                                                         │
│  ┌─── Data Tier ─────────────────────────────────────────────┐      │
│  │  postgres + redis pods                                     │      │
│  │  Ingress: from api-gateway and worker pods only            │      │
│  │  Egress: DNS only (no external access)                     │      │
│  └────────────────────────────────────────────────────────────┘      │
│                                                                     │
│  ❌ Blocked: frontend → frontend (no lateral movement)               │
│  ❌ Blocked: data tier → external (data exfiltration prevention)     │
│  ❌ Blocked: any other namespace → deployforge                       │
└─────────────────────────────────────────────────────────────────────┘
```

```yaml
# deployforge-network-policies.yaml

# 1. Default deny all ingress and egress
---
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: default-deny-all
  namespace: deployforge
spec:
  podSelector: {}
  policyTypes:
  - Ingress
  - Egress

# 2. Allow DNS for all pods
---
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: allow-dns
  namespace: deployforge
spec:
  podSelector: {}
  policyTypes:
  - Egress
  egress:
  - to:
    - namespaceSelector:
        matchLabels:
          kubernetes.io/metadata.name: kube-system
    ports:
    - protocol: UDP
      port: 53
    - protocol: TCP
      port: 53

# 3. API Gateway: allow ingress from Ingress controller
---
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: api-gateway-ingress
  namespace: deployforge
spec:
  podSelector:
    matchLabels:
      app: api-gateway
  policyTypes:
  - Ingress
  ingress:
  - from:
    - namespaceSelector:
        matchLabels:
          kubernetes.io/metadata.name: ingress-nginx
    ports:
    - protocol: TCP
      port: 3000

# 4. API Gateway: allow egress to worker, postgres, redis
---
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: api-gateway-egress
  namespace: deployforge
spec:
  podSelector:
    matchLabels:
      app: api-gateway
  policyTypes:
  - Egress
  egress:
  - to:
    - podSelector:
        matchLabels:
          app: worker
    ports:
    - protocol: TCP
      port: 4000
  - to:
    - podSelector:
        matchLabels:
          app: postgres
    ports:
    - protocol: TCP
      port: 5432
  - to:
    - podSelector:
        matchLabels:
          app: redis
    ports:
    - protocol: TCP
      port: 6379

# 5. Worker: allow ingress from API Gateway
---
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: worker-ingress
  namespace: deployforge
spec:
  podSelector:
    matchLabels:
      app: worker
  policyTypes:
  - Ingress
  ingress:
  - from:
    - podSelector:
        matchLabels:
          app: api-gateway
    ports:
    - protocol: TCP
      port: 4000

# 6. Worker: allow egress to postgres, redis, external HTTPS
---
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: worker-egress
  namespace: deployforge
spec:
  podSelector:
    matchLabels:
      app: worker
  policyTypes:
  - Egress
  egress:
  - to:
    - podSelector:
        matchLabels:
          app: postgres
    ports:
    - protocol: TCP
      port: 5432
  - to:
    - podSelector:
        matchLabels:
          app: redis
    ports:
    - protocol: TCP
      port: 6379
  - to:
    - ipBlock:
        cidr: 0.0.0.0/0
        except:
        - 10.0.0.0/8
        - 172.16.0.0/12
        - 192.168.0.0/16
    ports:
    - protocol: TCP
      port: 443

# 7. Data tier: allow ingress from api-gateway and worker only
---
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: data-tier-ingress
  namespace: deployforge
spec:
  podSelector:
    matchLabels:
      tier: data
  policyTypes:
  - Ingress
  ingress:
  - from:
    - podSelector:
        matchLabels:
          app: api-gateway
    - podSelector:
        matchLabels:
          app: worker
    ports:
    - protocol: TCP
      port: 5432
    - protocol: TCP
      port: 6379

# 8. Allow Prometheus scraping from monitoring namespace
---
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: allow-monitoring-scrape
  namespace: deployforge
spec:
  podSelector: {}
  policyTypes:
  - Ingress
  ingress:
  - from:
    - namespaceSelector:
        matchLabels:
          purpose: monitoring
    ports:
    - protocol: TCP
      port: 9090
```

---

### Service Mesh Architecture

A service mesh adds a **sidecar proxy** to every pod, creating a network of proxies that handle all inter-service communication. The application code doesn't change — the proxy intercepts traffic transparently.

```
┌─────────────────────────────────────────────────────────────────────┐
│              Service Mesh Architecture                                │
│                                                                     │
│  ┌──── Control Plane ──────────────────────────────────────────┐    │
│  │                                                              │    │
│  │  ┌─────────────┐  ┌──────────────┐  ┌────────────────┐     │    │
│  │  │  Pilot /     │  │  Citadel /    │  │  Galley /       │    │    │
│  │  │  istiod      │  │  cert mgr    │  │  config mgr    │    │    │
│  │  │              │  │              │  │                 │    │    │
│  │  │ Pushes proxy │  │ Issues mTLS  │  │ Validates and  │    │    │
│  │  │ config (xDS) │  │ certificates │  │ distributes    │    │    │
│  │  │              │  │              │  │ mesh config    │    │    │
│  │  └──────────────┘  └──────────────┘  └────────────────┘    │    │
│  └──────────────────────────────────────────────────────────────┘    │
│                           │ xDS API                                 │
│                           ▼                                         │
│  ┌──── Data Plane ─────────────────────────────────────────────┐    │
│  │                                                              │    │
│  │  ┌──── Pod A ────────────────┐  ┌──── Pod B ──────────────┐ │    │
│  │  │                           │  │                          │ │    │
│  │  │  ┌─────────┐ ┌─────────┐ │  │ ┌─────────┐ ┌─────────┐│ │    │
│  │  │  │  App    │ │ Envoy   │ │  │ │ Envoy   │ │  App    ││ │    │
│  │  │  │ :3000   │→│ Proxy   │─┼──┼─│ Proxy   │→│ :4000   ││ │    │
│  │  │  │         │ │ :15001  │ │  │ │ :15001  │ │         ││ │    │
│  │  │  └─────────┘ └─────────┘ │  │ └─────────┘ └─────────┘│ │    │
│  │  │     All traffic goes     │  │    mTLS encrypted       │ │    │
│  │  │     through the proxy    │  │    between proxies      │ │    │
│  │  └──────────────────────────┘  └─────────────────────────┘ │    │
│  └──────────────────────────────────────────────────────────────┘    │
│                                                                     │
│  The proxy handles: mTLS, retries, timeouts, circuit breaking,     │
│  load balancing, observability (metrics, traces, access logs)       │
└─────────────────────────────────────────────────────────────────────┘
```

---

### Istio vs Linkerd

| Feature | Istio | Linkerd |
|---------|-------|---------|
| **Sidecar proxy** | Envoy (C++) | linkerd2-proxy (Rust) |
| **Resource overhead** | ~50MB RAM per sidecar | ~10MB RAM per sidecar |
| **Complexity** | High (many CRDs, steep learning curve) | Low (simple install, minimal config) |
| **mTLS** | ✅ Automatic (PeerAuthentication) | ✅ Automatic (on by default) |
| **Traffic management** | VirtualService, DestinationRule | TrafficSplit, ServiceProfile |
| **Observability** | Kiali dashboard, Jaeger, Prometheus | Linkerd Viz dashboard |
| **Multi-cluster** | ✅ (complex setup) | ✅ (simpler) |
| **WASM extensions** | ✅ (EnvoyFilter) | ❌ |
| **Best for** | Large enterprise, complex routing | Simplicity, lower overhead |

---

### mTLS Between Services

Both Istio and Linkerd provide automatic mutual TLS — every service-to-service call is encrypted and authenticated without any application code changes.

```yaml
# Istio: Enable strict mTLS for the namespace
apiVersion: security.istio.io/v1beta1
kind: PeerAuthentication
metadata:
  name: strict-mtls
  namespace: deployforge
spec:
  mtls:
    mode: STRICT           # Reject any non-mTLS traffic
```

```
┌─────────────────────────────────────────────────────────────────────┐
│                mTLS Flow (Istio / Linkerd)                           │
│                                                                     │
│  Pod A (api-gateway)              Pod B (worker)                    │
│  ┌──────────┐  ┌─────────────┐   ┌─────────────┐  ┌──────────┐   │
│  │ App      │→ │ Envoy Proxy │──▶│ Envoy Proxy │→ │ App      │   │
│  │ (HTTP)   │  │             │   │             │  │ (HTTP)   │   │
│  └──────────┘  │ 1. Gets cert│   │ 4. Verifies │  └──────────┘   │
│                │    from      │   │    client   │                  │
│                │    control   │   │    cert     │                  │
│                │    plane     │   │             │                  │
│                │ 2. TLS       │   │ 5. Decrypts │                  │
│                │    handshake │   │    and       │                  │
│                │    (mTLS)    │   │    forwards  │                  │
│                │ 3. Encrypts  │   │    to app   │                  │
│                │    request   │   │             │                  │
│                └─────────────┘   └─────────────┘                  │
│                                                                     │
│  The app sends plain HTTP to localhost:4000                         │
│  The proxy intercepts, encrypts, and sends mTLS to the other proxy │
│  The remote proxy decrypts and forwards plain HTTP to its app      │
│  Zero application code changes required                             │
└─────────────────────────────────────────────────────────────────────┘
```

---

### Traffic Management with Service Mesh

```yaml
# Istio: Canary deployment with traffic splitting
apiVersion: networking.istio.io/v1beta1
kind: VirtualService
metadata:
  name: api-gateway
  namespace: deployforge
spec:
  hosts:
  - api-gateway
  http:
  - route:
    - destination:
        host: api-gateway
        subset: stable
      weight: 90
    - destination:
        host: api-gateway
        subset: canary
      weight: 10
    retries:
      attempts: 3
      perTryTimeout: 2s
      retryOn: 5xx,reset,connect-failure
    timeout: 10s

---
apiVersion: networking.istio.io/v1beta1
kind: DestinationRule
metadata:
  name: api-gateway
  namespace: deployforge
spec:
  host: api-gateway
  trafficPolicy:
    connectionPool:
      tcp:
        maxConnections: 100
      http:
        h2UpgradePolicy: DEFAULT
        http1MaxPendingRequests: 100
    outlierDetection:              # Circuit breaker
      consecutive5xxErrors: 5
      interval: 30s
      baseEjectionTime: 30s
      maxEjectionPercent: 50
  subsets:
  - name: stable
    labels:
      version: v1
  - name: canary
    labels:
      version: v2
```

---

## Code Examples

### Applying and Testing NetworkPolicies

```bash
#!/bin/bash
set -euo pipefail

NS="deployforge"

echo "=== Step 1: Apply default-deny policies ==="
cat <<'EOF' | kubectl apply -f -
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: default-deny-all
  namespace: deployforge
spec:
  podSelector: {}
  policyTypes:
  - Ingress
  - Egress
---
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: allow-dns
  namespace: deployforge
spec:
  podSelector: {}
  policyTypes:
  - Egress
  egress:
  - to:
    - namespaceSelector:
        matchLabels:
          kubernetes.io/metadata.name: kube-system
    ports:
    - protocol: UDP
      port: 53
    - protocol: TCP
      port: 53
EOF

echo ""
echo "=== Step 2: Test that pods can't communicate ==="
# This should FAIL (timeout) because ingress is denied
kubectl exec -n $NS api-gateway-xxx -- \
  curl -s --connect-timeout 3 http://worker:4000/health 2>&1 || \
  echo "✅ Connection blocked as expected"

echo ""
echo "=== Step 3: Allow API Gateway → Worker ==="
cat <<'EOF' | kubectl apply -f -
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: allow-api-to-worker
  namespace: deployforge
spec:
  podSelector:
    matchLabels:
      app: worker
  policyTypes:
  - Ingress
  ingress:
  - from:
    - podSelector:
        matchLabels:
          app: api-gateway
    ports:
    - protocol: TCP
      port: 4000
---
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: api-gateway-to-worker-egress
  namespace: deployforge
spec:
  podSelector:
    matchLabels:
      app: api-gateway
  policyTypes:
  - Egress
  egress:
  - to:
    - podSelector:
        matchLabels:
          app: worker
    ports:
    - protocol: TCP
      port: 4000
EOF

echo ""
echo "=== Step 4: Verify API Gateway can now reach Worker ==="
kubectl exec -n $NS api-gateway-xxx -- \
  curl -s --connect-timeout 3 http://worker:4000/health && \
  echo "✅ Connection allowed" || echo "❌ Still blocked"

echo ""
echo "=== Step 5: Verify Worker still can't reach API Gateway ==="
kubectl exec -n $NS worker-xxx -- \
  curl -s --connect-timeout 3 http://api-gateway:3000/health 2>&1 || \
  echo "✅ Reverse direction correctly blocked"
```

### Visualizing NetworkPolicies

```bash
# List all policies
kubectl get networkpolicies -n deployforge

# Describe a specific policy (shows formatted rules)
kubectl describe networkpolicy api-gateway-ingress -n deployforge

# Check which pods are selected by a policy
kubectl get pods -n deployforge -l app=api-gateway
```

---

## Try It Yourself

### Challenge 1: Implement Three-Tier Microsegmentation

Starting from a default-deny baseline, create NetworkPolicies that implement:
1. Ingress controller → API Gateway (TCP 3000) only
2. API Gateway → Worker (TCP 4000) only
3. API Gateway and Worker → PostgreSQL (TCP 5432) only
4. API Gateway and Worker → Redis (TCP 6379) only
5. No pod can reach the internet except Workers on TCP 443
6. All pods can resolve DNS

Verify each rule by testing allowed and blocked connections.

<details>
<summary>Show solution</summary>

```bash
#!/bin/bash
set -euo pipefail

NS="deployforge"

echo "=== Applying complete microsegmentation ==="
cat <<'EOF' | kubectl apply -f -
# Default deny
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: default-deny-all
  namespace: deployforge
spec:
  podSelector: {}
  policyTypes:
  - Ingress
  - Egress
---
# DNS for all pods
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: allow-dns
  namespace: deployforge
spec:
  podSelector: {}
  policyTypes:
  - Egress
  egress:
  - to:
    - namespaceSelector:
        matchLabels:
          kubernetes.io/metadata.name: kube-system
    ports:
    - protocol: UDP
      port: 53
    - protocol: TCP
      port: 53
---
# Ingress controller → API Gateway
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: ingress-to-api-gw
  namespace: deployforge
spec:
  podSelector:
    matchLabels:
      app: api-gateway
  policyTypes:
  - Ingress
  ingress:
  - from:
    - namespaceSelector:
        matchLabels:
          kubernetes.io/metadata.name: ingress-nginx
    ports:
    - protocol: TCP
      port: 3000
---
# API Gateway egress to worker, postgres, redis
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: api-gw-egress
  namespace: deployforge
spec:
  podSelector:
    matchLabels:
      app: api-gateway
  policyTypes:
  - Egress
  egress:
  - to:
    - podSelector:
        matchLabels:
          app: worker
    ports:
    - protocol: TCP
      port: 4000
  - to:
    - podSelector:
        matchLabels:
          app: postgres
    ports:
    - protocol: TCP
      port: 5432
  - to:
    - podSelector:
        matchLabels:
          app: redis
    ports:
    - protocol: TCP
      port: 6379
---
# Worker ingress from API Gateway
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: worker-ingress
  namespace: deployforge
spec:
  podSelector:
    matchLabels:
      app: worker
  policyTypes:
  - Ingress
  ingress:
  - from:
    - podSelector:
        matchLabels:
          app: api-gateway
    ports:
    - protocol: TCP
      port: 4000
---
# Worker egress to data tier + external HTTPS
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: worker-egress
  namespace: deployforge
spec:
  podSelector:
    matchLabels:
      app: worker
  policyTypes:
  - Egress
  egress:
  - to:
    - podSelector:
        matchLabels:
          app: postgres
    ports:
    - protocol: TCP
      port: 5432
  - to:
    - podSelector:
        matchLabels:
          app: redis
    ports:
    - protocol: TCP
      port: 6379
  - to:
    - ipBlock:
        cidr: 0.0.0.0/0
        except:
        - 10.0.0.0/8
        - 172.16.0.0/12
        - 192.168.0.0/16
    ports:
    - protocol: TCP
      port: 443
---
# Data tier ingress from api-gateway and worker
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: data-tier-ingress
  namespace: deployforge
spec:
  podSelector:
    matchLabels:
      tier: data
  policyTypes:
  - Ingress
  ingress:
  - from:
    - podSelector:
        matchLabels:
          app: api-gateway
    - podSelector:
        matchLabels:
          app: worker
    ports:
    - protocol: TCP
      port: 5432
    - protocol: TCP
      port: 6379
EOF

echo ""
echo "=== Verification ==="

# Deploy a test pod
kubectl run policy-test --image=nicolaka/netshoot -n $NS \
  --labels="app=api-gateway,tier=frontend" \
  --restart=Never -- sleep 3600
kubectl wait --for=condition=Ready pod/policy-test -n $NS --timeout=60s

echo "1. API Gateway → Worker (should succeed):"
kubectl exec -n $NS policy-test -- \
  curl -s --connect-timeout 3 http://worker:4000/ 2>&1 && \
  echo "✅ Allowed" || echo "✅ Timeout (worker may not have HTTP server)"

echo "2. API Gateway → PostgreSQL (should succeed):"
kubectl exec -n $NS policy-test -- \
  nc -zv -w 3 postgres 5432 2>&1 && \
  echo "✅ Allowed" || echo "❌ Blocked"

echo "3. API Gateway → External HTTPS (should be blocked):"
kubectl exec -n $NS policy-test -- \
  curl -s --connect-timeout 3 https://example.com 2>&1 || \
  echo "✅ Correctly blocked"

kubectl delete pod policy-test -n $NS --grace-period=0
```

</details>

### Challenge 2: Debug a NetworkPolicy Issue

A team member reports that the monitoring system can't scrape metrics from DeployForge pods. The Prometheus pod is in the `monitoring` namespace with label `app: prometheus`. Create a NetworkPolicy that allows it.

<details>
<summary>Show solution</summary>

```bash
#!/bin/bash
set -euo pipefail

# First, label the monitoring namespace
kubectl label namespace monitoring purpose=monitoring --overwrite 2>/dev/null || \
  kubectl create namespace monitoring && \
  kubectl label namespace monitoring purpose=monitoring

# Create the allow rule
cat <<'EOF' | kubectl apply -f -
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: allow-prometheus-scrape
  namespace: deployforge
spec:
  podSelector: {}
  policyTypes:
  - Ingress
  ingress:
  - from:
    - namespaceSelector:
        matchLabels:
          purpose: monitoring
      podSelector:
        matchLabels:
          app: prometheus
    ports:
    - protocol: TCP
      port: 9090
    - protocol: TCP
      port: 3000    # API Gateway metrics
    - protocol: TCP
      port: 4000    # Worker metrics
EOF

echo "✅ Prometheus scraping rule applied"
kubectl get networkpolicy allow-prometheus-scrape -n deployforge
```

</details>

---

## Capstone Connection

**DeployForge** implements defense-in-depth networking through NetworkPolicies and is designed for service mesh adoption:

- **Default deny** — The DeployForge namespace starts with a default-deny-all policy. Every communication path is explicitly allowed, creating a zero-trust network within the cluster. A compromised API Gateway pod can only reach the Worker, PostgreSQL, and Redis — never the internet or other namespaces.
- **Three-tier microsegmentation** — NetworkPolicies enforce the frontend → backend → data tier architecture. The data tier (PostgreSQL, Redis) only accepts connections from application pods, never from external sources. Workers can reach external HTTPS endpoints for webhook delivery, but the data tier has no egress to the internet — preventing data exfiltration.
- **Monitoring integration** — A dedicated policy allows Prometheus (in the `monitoring` namespace) to scrape metrics from all DeployForge pods. In Module 08 (Observability), you'll see this in action with Grafana dashboards.
- **Service mesh readiness** — DeployForge's architecture is designed for Istio or Linkerd adoption. In a production deployment, you'd add the service mesh for automatic mTLS (replacing the need to configure TLS in application code), traffic management (canary deployments via VirtualService instead of Ingress annotations), and distributed tracing.
- **mTLS** — Once a service mesh is installed, all DeployForge service-to-service calls are automatically encrypted with mTLS. The API Gateway → Worker → PostgreSQL path becomes fully encrypted without changing any application code or connection strings.
- **Traffic management** — Istio's VirtualService enables sophisticated deployment strategies for DeployForge: 90/10 canary splits, header-based routing for A/B testing, and automatic retries with circuit breaking for resilience.
