# Module 06 — Exercises

Hands-on practice with Kubernetes networking, services, Ingress controllers, and NetworkPolicies. These exercises build the complete networking layer for DeployForge, from internal services through external access with TLS and microsegmentation.

> **⚠️ Prerequisite:** You need a running kind cluster with the `deployforge` namespace and workloads from Module 05. If you don't have one, run:
> ```bash
> cat <<EOF | kind create cluster --config=-
> kind: Cluster
> apiVersion: kind.x-k8s.io/v1alpha4
> name: deployforge
> networking:
>   disableDefaultCNI: true
>   podSubnet: "10.244.0.0/16"
> nodes:
> - role: control-plane
>   kubeadmConfigPatches:
>   - |
>     kind: InitConfiguration
>     nodeRegistration:
>       kubeletExtraArgs:
>         node-labels: "ingress-ready=true"
>   extraPortMappings:
>   - containerPort: 80
>     hostPort: 80
>     protocol: TCP
>   - containerPort: 443
>     hostPort: 443
>     protocol: TCP
> - role: worker
> - role: worker
> EOF
> # Install Calico (for NetworkPolicy support)
> kubectl apply -f https://raw.githubusercontent.com/projectcalico/calico/v3.27.0/manifests/calico.yaml
> kubectl wait --for=condition=Ready pods -l k8s-app=calico-node -n kube-system --timeout=120s
> kubectl create namespace deployforge
> kubectl config set-context --current --namespace=deployforge
> ```

---

## Exercise 1: Debug Network Connectivity Between Pods

**Goal:** Use standard networking tools to diagnose and verify pod-to-pod communication across nodes in your kind cluster. Build the debugging muscle memory you'll need when production networking breaks.

### Steps

1. **Deploy two network debug pods on different nodes:**

```yaml
# net-debug-pods.yaml
apiVersion: v1
kind: Pod
metadata:
  name: debug-frontend
  namespace: deployforge
  labels:
    app: debug
    tier: frontend
spec:
  nodeSelector:
    kubernetes.io/hostname: deployforge-worker
  containers:
  - name: netshoot
    image: nicolaka/netshoot:latest
    command: ['sleep', '3600']
---
apiVersion: v1
kind: Pod
metadata:
  name: debug-backend
  namespace: deployforge
  labels:
    app: debug
    tier: backend
spec:
  nodeSelector:
    kubernetes.io/hostname: deployforge-worker2
  containers:
  - name: netshoot
    image: nicolaka/netshoot:latest
    command: ['sleep', '3600']
    ports:
    - containerPort: 8080
```

2. **Verify pod placement and IP assignment:**

```bash
kubectl get pods -n deployforge -l app=debug -o wide
# Confirm pods are on different nodes with different IPs
```

3. **Test basic connectivity (ping, traceroute):**

From `debug-frontend`, ping `debug-backend` by its pod IP. Then trace the route — how many hops are there? What does this tell you about the CNI overlay?

4. **Test DNS resolution:**

From `debug-frontend`, resolve the following and explain each result:
- `kubernetes.default.svc.cluster.local`
- `kube-dns.kube-system.svc.cluster.local`
- `api-gateway.deployforge.svc.cluster.local` (if services exist from Module 05)

5. **Inspect the network namespace:**

From inside each debug pod, run `ip addr`, `ip route`, and `cat /etc/resolv.conf`. Compare the outputs.

6. **Test port connectivity:**

Start a listener on `debug-backend` (`nc -l 8080`) and connect from `debug-frontend` (`nc debug-backend-ip 8080`). Send a message both ways.

7. **Clean up:**

```bash
kubectl delete pods debug-frontend debug-backend -n deployforge
```

### Verification

```bash
# Ping should succeed across nodes (flat network)
kubectl exec -n deployforge debug-frontend -- ping -c 3 <debug-backend-ip>
# → 3 packets transmitted, 3 received, 0% packet loss

# Traceroute should show 1-2 hops (depending on CNI)
kubectl exec -n deployforge debug-frontend -- traceroute -n <debug-backend-ip>

# DNS should resolve cluster services
kubectl exec -n deployforge debug-frontend -- nslookup kubernetes.default
# → Address: 10.96.0.1
```

<details>
<summary>Show solution</summary>

```bash
#!/bin/bash
set -euo pipefail

NS="deployforge"

echo "=== Step 1: Deploy debug pods ==="
cat <<'EOF' | kubectl apply -f -
apiVersion: v1
kind: Pod
metadata:
  name: debug-frontend
  namespace: deployforge
  labels:
    app: debug
    tier: frontend
spec:
  containers:
  - name: netshoot
    image: nicolaka/netshoot:latest
    command: ['sleep', '3600']
---
apiVersion: v1
kind: Pod
metadata:
  name: debug-backend
  namespace: deployforge
  labels:
    app: debug
    tier: backend
spec:
  containers:
  - name: netshoot
    image: nicolaka/netshoot:latest
    command: ['sleep', '3600']
EOF

kubectl wait --for=condition=Ready pod/debug-frontend pod/debug-backend \
  -n $NS --timeout=60s

echo ""
echo "=== Step 2: Check placement ==="
kubectl get pods -n $NS -l app=debug -o wide

BACKEND_IP=$(kubectl get pod debug-backend -n $NS -o jsonpath='{.status.podIP}')
echo "Backend IP: $BACKEND_IP"

echo ""
echo "=== Step 3: Ping test ==="
kubectl exec -n $NS debug-frontend -- ping -c 3 "$BACKEND_IP"

echo ""
echo "=== Step 4: Traceroute ==="
kubectl exec -n $NS debug-frontend -- traceroute -n "$BACKEND_IP"

echo ""
echo "=== Step 5: DNS resolution ==="
echo "--- kubernetes API server ---"
kubectl exec -n $NS debug-frontend -- nslookup kubernetes.default.svc.cluster.local

echo "--- CoreDNS ---"
kubectl exec -n $NS debug-frontend -- nslookup kube-dns.kube-system.svc.cluster.local

echo ""
echo "=== Step 6: Network namespace inspection ==="
echo "--- Frontend: IP addresses ---"
kubectl exec -n $NS debug-frontend -- ip addr show eth0
echo "--- Frontend: Routes ---"
kubectl exec -n $NS debug-frontend -- ip route
echo "--- Frontend: DNS config ---"
kubectl exec -n $NS debug-frontend -- cat /etc/resolv.conf

echo ""
echo "=== Step 7: Port connectivity test ==="
# Start listener in background
kubectl exec -n $NS debug-backend -- sh -c 'echo "Hello from backend" | nc -l -p 8080' &
sleep 2

# Connect from frontend
kubectl exec -n $NS debug-frontend -- sh -c "echo 'Hello from frontend' | nc -w 3 $BACKEND_IP 8080"

echo ""
echo "=== Cleanup ==="
kubectl delete pods debug-frontend debug-backend -n $NS --grace-period=0
```

</details>

---

## Exercise 2: Create the Complete DeployForge Service Layer

**Goal:** Create all Services for the DeployForge platform — ClusterIP for stateless services, Headless for StatefulSets, and verify service discovery via DNS.

### Steps

1. **Create the API Gateway ClusterIP Service:**

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
  type: ClusterIP
  selector:
    app: api-gateway
  ports:
  - name: http
    port: 3000
    targetPort: 3000
```

2. **Create the Worker ClusterIP Service:**

Create a ClusterIP Service named `worker` in the `deployforge` namespace. It should select pods with label `app: worker` and expose port 4000.

3. **Create the PostgreSQL Headless Service:**

Create a headless Service (`clusterIP: None`) named `postgres` that selects `app: postgres` on port 5432. This enables stable DNS for StatefulSet pods.

4. **Create the Redis Headless Service:**

Same pattern as PostgreSQL — headless Service for Redis on port 6379.

5. **Create an Nginx NodePort Service for development access:**

Create a NodePort Service named `nginx-external` that exposes the Nginx DaemonSet on NodePort 30080.

6. **Verify all services and endpoints:**

```bash
# List all services
kubectl get svc -n deployforge

# Check endpoints (should match running pod IPs)
kubectl get endpoints -n deployforge

# Check EndpointSlices
kubectl get endpointslices -n deployforge
```

7. **Test DNS resolution for every service:**

Deploy a `busybox` pod and verify DNS resolution:
- `api-gateway` → ClusterIP
- `postgres` → Pod IPs (headless)
- `postgres-0.postgres.deployforge.svc.cluster.local` → Specific pod IP
- Cross-namespace: `kube-dns.kube-system.svc.cluster.local`

8. **Test the `ndots:5` behavior:**

From the busybox pod, resolve `example.com` (an external domain). How many DNS queries does it make? How do you optimize this?

### Verification

```bash
# All 5 services created
kubectl get svc -n deployforge
# NAME             TYPE        CLUSTER-IP     EXTERNAL-IP   PORT(S)
# api-gateway      ClusterIP   10.96.x.x      <none>       3000/TCP
# worker           ClusterIP   10.96.x.x      <none>       4000/TCP
# postgres         ClusterIP   None            <none>       5432/TCP
# redis            ClusterIP   None            <none>       6379/TCP
# nginx-external   NodePort    10.96.x.x      <none>       80:30080/TCP

# Endpoints populated for services with running pods
kubectl get endpoints -n deployforge
# Non-empty ENDPOINTS for services with matching pods

# DNS resolution works
kubectl exec -n deployforge dns-test -- nslookup api-gateway
# → Server: 10.96.0.10
# → Address: 10.96.x.x

kubectl exec -n deployforge dns-test -- nslookup postgres
# → Returns individual pod IPs (headless)
```

<details>
<summary>Show solution</summary>

```bash
#!/bin/bash
set -euo pipefail

NS="deployforge"

echo "=== Creating all DeployForge services ==="
cat <<'EOF' | kubectl apply -f -
# 1. API Gateway — ClusterIP
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
# 2. Worker — ClusterIP
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
# 3. PostgreSQL — Headless
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
# 4. Redis — Headless
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
# 5. Nginx — NodePort for dev access
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
echo "=== Checking endpoints ==="
kubectl get endpoints -n $NS

echo ""
echo "=== Testing DNS resolution ==="
kubectl run dns-test --image=busybox:1.36 -n $NS \
  --restart=Never -- sleep 3600
kubectl wait --for=condition=Ready pod/dns-test -n $NS --timeout=60s

echo "--- api-gateway (ClusterIP) ---"
kubectl exec -n $NS dns-test -- nslookup api-gateway 2>&1 || true

echo "--- postgres (Headless → pod IPs) ---"
kubectl exec -n $NS dns-test -- nslookup postgres.$NS.svc.cluster.local 2>&1 || true

echo "--- StatefulSet pod DNS ---"
kubectl exec -n $NS dns-test -- \
  nslookup postgres-0.postgres.$NS.svc.cluster.local 2>&1 || true

echo "--- Cross-namespace ---"
kubectl exec -n $NS dns-test -- \
  nslookup kube-dns.kube-system.svc.cluster.local 2>&1 || true

echo ""
echo "=== Testing ndots:5 behavior ==="
echo "DNS config:"
kubectl exec -n $NS dns-test -- cat /etc/resolv.conf

echo "Resolving external domain (watch query count):"
kubectl exec -n $NS dns-test -- nslookup example.com 2>&1 || true

echo "Optimized with trailing dot (skips search domains):"
kubectl exec -n $NS dns-test -- nslookup example.com. 2>&1 || true

echo ""
echo "=== Cleanup ==="
kubectl delete pod dns-test -n $NS --grace-period=0
```

</details>

---

## Exercise 3: Set Up Nginx Ingress with TLS

**Goal:** Install the Nginx Ingress Controller, create a TLS certificate, and configure Ingress resources for path-based and host-based routing to DeployForge services.

### Steps

1. **Install the Nginx Ingress Controller for kind:**

```bash
kubectl apply -f https://raw.githubusercontent.com/kubernetes/ingress-nginx/main/deploy/static/provider/kind/deploy.yaml

kubectl wait --namespace ingress-nginx \
  --for=condition=ready pod \
  --selector=app.kubernetes.io/component=controller \
  --timeout=120s
```

2. **Verify the IngressClass is registered:**

```bash
kubectl get ingressclass
# Should show: nginx
```

3. **Generate a self-signed TLS certificate:**

Create a certificate with SANs for `deployforge.local` and `api.deployforge.local`. Store it as a Kubernetes TLS Secret.

4. **Create a path-based Ingress resource:**

Configure routing so:
- `deployforge.local/api/*` → `api-gateway:3000`
- `deployforge.local/health` → `api-gateway:3000`
- `deployforge.local/*` → `nginx:80`

Enable TLS with the certificate you created and add annotations for SSL redirect and rate limiting (50 req/sec).

5. **Add `/etc/hosts` entries and test:**

```bash
echo "127.0.0.1 deployforge.local api.deployforge.local" | sudo tee -a /etc/hosts
```

Test each route with `curl -k` (self-signed cert) and verify correct routing.

6. **Create a host-based Ingress for the API subdomain:**

Configure `api.deployforge.local` → `api-gateway:3000` as a separate Ingress resource sharing the same TLS Secret.

7. **Check the Nginx Ingress Controller's generated config:**

```bash
# See the actual nginx.conf the controller generated
kubectl exec -n ingress-nginx deploy/ingress-nginx-controller -- cat /etc/nginx/nginx.conf | head -100
```

8. **Verify TLS is working:**

```bash
curl -kv https://deployforge.local 2>&1 | grep -E "subject:|issuer:|SSL"
```

### Verification

```bash
# Ingress resources created
kubectl get ingress -n deployforge
# NAME                    CLASS   HOSTS                                    ADDRESS     PORTS
# deployforge-ingress     nginx   deployforge.local                        localhost   80, 443
# deployforge-api         nginx   api.deployforge.local                    localhost   80, 443

# Path-based routing works
curl -k https://deployforge.local/health        # → api-gateway response
curl -k https://deployforge.local/api/v1/status  # → api-gateway response
curl -k https://deployforge.local/               # → nginx response

# Host-based routing works
curl -k https://api.deployforge.local/health     # → api-gateway response

# TLS is active
curl -kv https://deployforge.local 2>&1 | grep "subject:"
# → subject: CN=deployforge.local; O=DeployForge
```

<details>
<summary>Show solution</summary>

```bash
#!/bin/bash
set -euo pipefail

NS="deployforge"

echo "=== Step 1: Install Nginx Ingress Controller ==="
kubectl apply -f https://raw.githubusercontent.com/kubernetes/ingress-nginx/main/deploy/static/provider/kind/deploy.yaml

kubectl wait --namespace ingress-nginx \
  --for=condition=ready pod \
  --selector=app.kubernetes.io/component=controller \
  --timeout=120s

echo ""
echo "=== Step 2: Verify IngressClass ==="
kubectl get ingressclass

echo ""
echo "=== Step 3: Create TLS certificate ==="
openssl req -x509 -nodes -days 365 -newkey rsa:2048 \
  -keyout tls.key -out tls.crt \
  -subj "/CN=deployforge.local/O=DeployForge" \
  -addext "subjectAltName=DNS:deployforge.local,DNS:api.deployforge.local" \
  2>/dev/null

kubectl create secret tls deployforge-tls \
  --cert=tls.crt --key=tls.key \
  -n $NS --dry-run=client -o yaml | kubectl apply -f -

rm tls.key tls.crt

echo ""
echo "=== Step 4: Create path-based Ingress ==="
cat <<'EOF' | kubectl apply -f -
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: deployforge-ingress
  namespace: deployforge
  annotations:
    nginx.ingress.kubernetes.io/ssl-redirect: "true"
    nginx.ingress.kubernetes.io/limit-rps: "50"
    nginx.ingress.kubernetes.io/limit-burst-multiplier: "5"
spec:
  ingressClassName: nginx
  tls:
  - hosts:
    - deployforge.local
    - api.deployforge.local
    secretName: deployforge-tls
  rules:
  - host: deployforge.local
    http:
      paths:
      - path: /api
        pathType: Prefix
        backend:
          service:
            name: api-gateway
            port:
              number: 3000
      - path: /health
        pathType: Exact
        backend:
          service:
            name: api-gateway
            port:
              number: 3000
      - path: /
        pathType: Prefix
        backend:
          service:
            name: nginx-external
            port:
              number: 80
EOF

echo ""
echo "=== Step 5: Add hosts entries ==="
grep -q "deployforge.local" /etc/hosts || \
  echo "127.0.0.1 deployforge.local api.deployforge.local" | sudo tee -a /etc/hosts

echo ""
echo "=== Step 6: Create host-based Ingress for API subdomain ==="
cat <<'EOF' | kubectl apply -f -
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: deployforge-api
  namespace: deployforge
  annotations:
    nginx.ingress.kubernetes.io/ssl-redirect: "true"
spec:
  ingressClassName: nginx
  tls:
  - hosts:
    - api.deployforge.local
    secretName: deployforge-tls
  rules:
  - host: api.deployforge.local
    http:
      paths:
      - path: /
        pathType: Prefix
        backend:
          service:
            name: api-gateway
            port:
              number: 3000
EOF

echo ""
echo "=== Step 7: Verify ==="
kubectl get ingress -n $NS

echo ""
echo "Testing routes:"
echo "--- /health ---"
curl -sk https://deployforge.local/health 2>&1 | head -5 || echo "(Service may not be running)"

echo "--- /api ---"
curl -sk https://deployforge.local/api/ 2>&1 | head -5 || echo "(Service may not be running)"

echo "--- / (default) ---"
curl -sk https://deployforge.local/ 2>&1 | head -5 || echo "(Service may not be running)"

echo "--- api.deployforge.local ---"
curl -sk https://api.deployforge.local/health 2>&1 | head -5 || echo "(Service may not be running)"

echo ""
echo "=== Step 8: Verify TLS ==="
curl -kv https://deployforge.local 2>&1 | grep -E "subject:|issuer:" || true
```

</details>

---

## Exercise 4: Implement NetworkPolicies for DeployForge Microsegmentation

**Goal:** Implement a complete zero-trust network for DeployForge using NetworkPolicies. Start with default-deny, then explicitly allow only the required communication paths between tiers.

> **⚠️ Important:** This exercise requires a CNI plugin that supports NetworkPolicies (Calico, Cilium). The default `kindnet` CNI does NOT enforce NetworkPolicies — they'll be silently ignored. See the prerequisite setup at the top of this file.

### Steps

1. **Verify your CNI supports NetworkPolicies:**

```bash
# Check for Calico or Cilium
kubectl get pods -n kube-system | grep -E 'calico|cilium'
# Should see calico-node or cilium pods
```

2. **Apply default-deny for all ingress and egress:**

Create a NetworkPolicy that denies all traffic (both directions) for every pod in the `deployforge` namespace.

3. **Allow DNS egress for all pods:**

Without DNS, nothing works. Create a policy allowing all pods to reach CoreDNS on UDP/TCP port 53.

4. **Verify that pods are now isolated:**

```bash
# This should timeout — all traffic is denied except DNS
kubectl exec -n deployforge api-gateway-xxx -- \
  curl -s --connect-timeout 3 http://worker:4000/ || echo "Blocked as expected"
```

5. **Create tier-by-tier allow rules:**

Build the following policies:

| Policy | Source | Destination | Port |
|--------|--------|-------------|------|
| Ingress → API GW | ingress-nginx namespace | api-gateway pods | TCP 3000 |
| API GW → Worker | api-gateway pods | worker pods | TCP 4000 |
| API GW → Data | api-gateway pods | postgres, redis pods | TCP 5432, 6379 |
| Worker → Data | worker pods | postgres, redis pods | TCP 5432, 6379 |
| Worker → External | worker pods | 0.0.0.0/0 (except private) | TCP 443 |
| Monitoring → All | monitoring namespace | all pods | TCP 9090 |

6. **Test each allowed path:**

For each rule, verify the connection works. Then test a connection that should be blocked (e.g., worker → api-gateway, data tier → external).

7. **Verify lateral movement is blocked:**

Confirm that:
- Worker pods cannot reach each other on arbitrary ports
- Data tier pods cannot initiate connections to the internet
- Pods in other namespaces cannot reach DeployForge pods

8. **List and describe all policies:**

```bash
kubectl get networkpolicies -n deployforge
kubectl describe networkpolicy -n deployforge
```

### Verification

```bash
# All policies created
kubectl get networkpolicies -n deployforge
# NAME                    POD-SELECTOR        AGE
# default-deny-all        <none>              ...
# allow-dns               <none>              ...
# ingress-to-api-gw       app=api-gateway     ...
# api-gw-egress           app=api-gateway     ...
# worker-ingress          app=worker          ...
# worker-egress           app=worker          ...
# data-tier-ingress       tier=data           ...
# allow-monitoring        <none>              ...

# Allowed paths work
kubectl exec -n deployforge api-gateway-xxx -- \
  nc -zv -w 3 postgres 5432
# → Connection to postgres 5432 port [tcp/postgresql] succeeded!

# Blocked paths fail
kubectl exec -n deployforge worker-xxx -- \
  curl -s --connect-timeout 3 http://api-gateway:3000/ || echo "Correctly blocked"
```

<details>
<summary>Show solution</summary>

```bash
#!/bin/bash
set -euo pipefail

NS="deployforge"

echo "=== Step 1: Verify CNI ==="
kubectl get pods -n kube-system | grep -E 'calico|cilium'

echo ""
echo "=== Step 2-3: Default deny + DNS allow ==="
cat <<'EOF' | kubectl apply -f -
# Default deny all
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
# Allow DNS
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
echo "=== Step 4: Verify isolation ==="
APIGW_POD=$(kubectl get pods -n $NS -l app=api-gateway -o jsonpath='{.items[0].metadata.name}' 2>/dev/null || echo "")
if [ -n "$APIGW_POD" ]; then
  kubectl exec -n $NS "$APIGW_POD" -- \
    curl -s --connect-timeout 3 http://worker:4000/ 2>&1 || \
    echo "✅ Traffic correctly blocked"
else
  echo "No api-gateway pod found — deploy Module 05 workloads first"
fi

echo ""
echo "=== Step 5: Create tier-by-tier allow rules ==="
cat <<'EOF' | kubectl apply -f -
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
# API Gateway egress: worker, postgres, redis
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
# Worker egress: postgres, redis, external HTTPS
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
# Data tier ingress: api-gateway and worker
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
---
# Monitoring namespace → all DeployForge pods
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: allow-monitoring
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
EOF

echo ""
echo "=== Step 6: Test allowed paths ==="
if [ -n "$APIGW_POD" ]; then
  echo "API Gateway → PostgreSQL (TCP 5432):"
  kubectl exec -n $NS "$APIGW_POD" -- \
    nc -zv -w 3 postgres 5432 2>&1 && echo "✅ Allowed" || echo "❌ Blocked"

  echo "API Gateway → Redis (TCP 6379):"
  kubectl exec -n $NS "$APIGW_POD" -- \
    nc -zv -w 3 redis 6379 2>&1 && echo "✅ Allowed" || echo "❌ Blocked"

  echo "API Gateway → Worker (TCP 4000):"
  kubectl exec -n $NS "$APIGW_POD" -- \
    nc -zv -w 3 worker 4000 2>&1 && echo "✅ Allowed" || echo "❌ Blocked"
fi

echo ""
echo "=== Step 7: Test blocked paths ==="
WORKER_POD=$(kubectl get pods -n $NS -l app=worker -o jsonpath='{.items[0].metadata.name}' 2>/dev/null || echo "")
if [ -n "$WORKER_POD" ]; then
  echo "Worker → API Gateway (should be blocked):"
  kubectl exec -n $NS "$WORKER_POD" -- \
    nc -zv -w 3 api-gateway 3000 2>&1 || echo "✅ Correctly blocked"
fi

echo ""
echo "=== Step 8: List all policies ==="
kubectl get networkpolicies -n $NS
echo ""
echo "=== Policy details ==="
kubectl get networkpolicies -n $NS -o wide
```

</details>

---

## Knowledge Check

Test your understanding of networking concepts:

### Networking Model & CNI

1. What are the three Kubernetes networking requirements?
2. What is a veth pair and how does it connect a pod to the host?
3. How does Calico's BGP mode differ from Flannel's VXLAN overlay?
4. What does kube-proxy do, and what are its three operating modes?
5. Why is Cilium's eBPF data plane faster than iptables?

### Services & DNS

6. What's the difference between ClusterIP and Headless services? When do you use each?
7. Why does a StatefulSet need a headless Service?
8. What does `ndots:5` in `/etc/resolv.conf` mean? How does it affect external DNS lookups?
9. What happens when you create a Service without a selector?
10. What's the difference between `externalTrafficPolicy: Cluster` and `Local`?

### Ingress & Load Balancing

11. What's the difference between an Ingress resource and an Ingress controller?
12. Why is Ingress preferred over multiple LoadBalancer Services?
13. How does cert-manager automate TLS certificate management?
14. What's the difference between `pathType: Exact`, `Prefix`, and `ImplementationSpecific`?
15. How does the Gateway API improve on Ingress?

### NetworkPolicies & Service Mesh

16. What happens if your CNI plugin doesn't support NetworkPolicies?
17. Explain the AND vs OR logic in NetworkPolicy `from` selectors.
18. Why must you allow DNS egress when using default-deny egress policies?
19. What's the difference between a service mesh's data plane and control plane?
20. How does mTLS work in Istio without changing application code?
21. Describe the complete network path of an external HTTPS request reaching a DeployForge API Gateway pod (from DNS resolution through Ingress, Service, and NetworkPolicy).
