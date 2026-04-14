# 6.3 — Ingress Controllers & Load Balancing

## Concept

Exposing each service individually via LoadBalancer is expensive and unmanageable — you'd have a separate cloud load balancer (and external IP) for every service. Ingress solves this by providing a single entry point that routes HTTP/HTTPS traffic to different backend services based on the request's hostname or path.

But here's the thing that trips people up: an Ingress _resource_ does nothing on its own. It's just a routing declaration. You need an Ingress _controller_ — a pod running a reverse proxy (Nginx, Traefik, Envoy, HAProxy) — to actually read those declarations and configure routing rules. The Ingress controller is where TLS termination, rate limiting, authentication, and rewriting happen.

Kubernetes is also evolving beyond Ingress with the Gateway API — a more expressive, role-oriented resource model that addresses Ingress's limitations around TCP/UDP routing, traffic splitting, and header-based routing.

---

## Deep Dive

### Ingress Resource vs Ingress Controller

```
┌─────────────────────────────────────────────────────────────────────┐
│           Ingress Architecture                                       │
│                                                                     │
│  You create:                   The controller does:                 │
│                                                                     │
│  ┌─────────────────────┐      ┌──────────────────────────────┐     │
│  │  Ingress Resource    │      │  Ingress Controller Pod       │     │
│  │  (YAML manifest)     │─────▶│  (nginx / traefik / envoy)   │     │
│  │                      │      │                               │     │
│  │  rules:              │      │  1. Watches API for Ingress   │     │
│  │  - host: app.com     │      │     resources                 │     │
│  │    paths:             │      │  2. Generates nginx.conf      │     │
│  │    - /api → api-svc  │      │     (or equivalent config)    │     │
│  │    - /web → web-svc  │      │  3. Reloads reverse proxy     │     │
│  └─────────────────────┘      │  4. Terminates TLS             │     │
│                                │  5. Routes traffic to backends │     │
│  Just data — does              └──────────────────────────────┘     │
│  nothing without a                                                  │
│  controller!                   This is the actual reverse proxy      │
│                                running in your cluster               │
└─────────────────────────────────────────────────────────────────────┘
```

> **Key insight:** Many teams create Ingress resources and wonder why nothing works — they forgot to install an Ingress controller. `kubectl get ingressclass` will show you what's available.

---

### Installing Nginx Ingress Controller

The Nginx Ingress Controller is the most common choice. For kind clusters:

```bash
# Install Nginx Ingress Controller for kind
kubectl apply -f https://raw.githubusercontent.com/kubernetes/ingress-nginx/main/deploy/static/provider/kind/deploy.yaml

# Wait for the controller to be ready
kubectl wait --namespace ingress-nginx \
  --for=condition=ready pod \
  --selector=app.kubernetes.io/component=controller \
  --timeout=120s

# Verify the IngressClass is registered
kubectl get ingressclass
# NAME    CONTROLLER                      PARAMETERS   AGE
# nginx   k8s.io/ingress-nginx            <none>       1m
```

For cloud providers:

```bash
# AWS — provisions an NLB
kubectl apply -f https://raw.githubusercontent.com/kubernetes/ingress-nginx/main/deploy/static/provider/aws/deploy.yaml

# GCP — provisions a GCP LB
kubectl apply -f https://raw.githubusercontent.com/kubernetes/ingress-nginx/main/deploy/static/provider/cloud/deploy.yaml

# Get the external IP/hostname (takes 1-2 minutes on cloud)
kubectl get svc -n ingress-nginx ingress-nginx-controller
# TYPE           CLUSTER-IP    EXTERNAL-IP      PORT(S)
# LoadBalancer   10.96.0.100   a1b2c3.elb...    80:30080/TCP,443:30443/TCP
```

---

### Ingress Controller Comparison

| Feature | Nginx Ingress | Traefik | Envoy (Contour/Emissary) | HAProxy |
|---------|--------------|---------|--------------------------|---------|
| **Config model** | Annotations + ConfigMap | CRDs + annotations | CRDs (HTTPProxy) | ConfigMap |
| **TLS** | cert-manager integration | Built-in ACME | cert-manager | cert-manager |
| **Rate limiting** | Annotation-based | Middleware CRDs | RateLimitPolicy CRD | ConfigMap |
| **Auth** | External auth, basic auth | ForwardAuth middleware | ext_authz filter | Basic auth |
| **TCP/UDP** | ConfigMap-based | IngressRouteTCP CRD | TCPProxy CRD | ConfigMap |
| **Canary/traffic split** | `nginx.ingress.kubernetes.io/canary` | TraefikService CRD | HTTPProxy weights | N/A |
| **Performance** | High | High | Highest (C++) | Very High |
| **Complexity** | Low | Low-Medium | Medium | Low |
| **Community** | Largest | Growing | Enterprise-focused | Mature |

---

### Path-Based Routing

Route different URL paths to different backend services:

```yaml
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: deployforge-ingress
  namespace: deployforge
  annotations:
    nginx.ingress.kubernetes.io/rewrite-target: /$2
spec:
  ingressClassName: nginx
  rules:
  - host: deployforge.local
    http:
      paths:
      # /api/v1/deployments → api-gateway:3000/v1/deployments
      - path: /api(/|$)(.*)
        pathType: ImplementationSpecific
        backend:
          service:
            name: api-gateway
            port:
              number: 3000
      # /health → api-gateway:3000/health
      - path: /health
        pathType: Exact
        backend:
          service:
            name: api-gateway
            port:
              number: 3000
      # / → nginx:80/ (static frontend)
      - path: /
        pathType: Prefix
        backend:
          service:
            name: nginx
            port:
              number: 80
```

```
┌─────────────────────────────────────────────────────────────────┐
│                   Path-Based Routing                              │
│                                                                 │
│  Client Request                                                 │
│       │                                                         │
│       ▼                                                         │
│  ┌──────────────────────────────────┐                           │
│  │  Nginx Ingress Controller         │                           │
│  │  deployforge.local                │                           │
│  │                                   │                           │
│  │  /api/*  ──────────▶ api-gateway:3000                        │
│  │  /health ──────────▶ api-gateway:3000                        │
│  │  /*      ──────────▶ nginx:80 (frontend)                     │
│  └──────────────────────────────────┘                           │
│                                                                 │
│  pathType values:                                               │
│  • Exact:                  /health matches ONLY /health          │
│  • Prefix:                 / matches /, /anything, /a/b/c       │
│  • ImplementationSpecific: regex support (controller-dependent) │
└─────────────────────────────────────────────────────────────────┘
```

---

### Host-Based Routing

Route different hostnames to different services (virtual hosting):

```yaml
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: deployforge-multi-host
  namespace: deployforge
spec:
  ingressClassName: nginx
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
  - host: dashboard.deployforge.local
    http:
      paths:
      - path: /
        pathType: Prefix
        backend:
          service:
            name: nginx
            port:
              number: 80
  - host: metrics.deployforge.local
    http:
      paths:
      - path: /
        pathType: Prefix
        backend:
          service:
            name: prometheus
            port:
              number: 9090
```

```
┌──────────────────────────────────────────────────────────────┐
│                   Host-Based Routing                          │
│                                                              │
│  api.deployforge.local ──────────▶ api-gateway:3000          │
│  dashboard.deployforge.local ────▶ nginx:80                  │
│  metrics.deployforge.local ──────▶ prometheus:9090           │
│                                                              │
│  All share the same external IP / load balancer              │
│  The Host header in the HTTP request determines routing      │
└──────────────────────────────────────────────────────────────┘
```

---

### TLS Termination

The Ingress controller terminates TLS so backend services receive plain HTTP. You provide the certificate as a Kubernetes Secret.

#### Manual TLS Configuration

```bash
# Create a self-signed certificate for development
openssl req -x509 -nodes -days 365 -newkey rsa:2048 \
  -keyout tls.key -out tls.crt \
  -subj "/CN=deployforge.local/O=DeployForge"

# Create the TLS Secret
kubectl create secret tls deployforge-tls \
  --cert=tls.crt --key=tls.key \
  -n deployforge
```

```yaml
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: deployforge-tls
  namespace: deployforge
  annotations:
    nginx.ingress.kubernetes.io/ssl-redirect: "true"
    nginx.ingress.kubernetes.io/force-ssl-redirect: "true"
spec:
  ingressClassName: nginx
  tls:
  - hosts:
    - deployforge.local
    - api.deployforge.local
    secretName: deployforge-tls       # References the TLS Secret
  rules:
  - host: deployforge.local
    http:
      paths:
      - path: /
        pathType: Prefix
        backend:
          service:
            name: api-gateway
            port:
              number: 3000
```

#### Automated TLS with cert-manager

cert-manager automates certificate issuance and renewal. It integrates with Let's Encrypt (production) and self-signed issuers (development).

```bash
# Install cert-manager
kubectl apply -f https://github.com/cert-manager/cert-manager/releases/download/v1.14.0/cert-manager.yaml

# Wait for cert-manager to be ready
kubectl wait --for=condition=Available deployment/cert-manager \
  -n cert-manager --timeout=120s
```

```yaml
# ClusterIssuer for Let's Encrypt (production)
apiVersion: cert-manager.io/v1
kind: ClusterIssuer
metadata:
  name: letsencrypt-prod
spec:
  acme:
    server: https://acme-v02.api.letsencrypt.org/directory
    email: admin@deployforge.io
    privateKeySecretRef:
      name: letsencrypt-prod-key
    solvers:
    - http01:
        ingress:
          class: nginx

---
# ClusterIssuer for self-signed (development / kind clusters)
apiVersion: cert-manager.io/v1
kind: ClusterIssuer
metadata:
  name: selfsigned-issuer
spec:
  selfSigned: {}
```

```yaml
# Ingress with cert-manager annotation — certificate is auto-created
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: deployforge-auto-tls
  namespace: deployforge
  annotations:
    cert-manager.io/cluster-issuer: "letsencrypt-prod"
    nginx.ingress.kubernetes.io/ssl-redirect: "true"
spec:
  ingressClassName: nginx
  tls:
  - hosts:
    - deployforge.io
    secretName: deployforge-auto-tls    # cert-manager creates this
  rules:
  - host: deployforge.io
    http:
      paths:
      - path: /
        pathType: Prefix
        backend:
          service:
            name: api-gateway
            port:
              number: 3000
```

```
┌──────────────────────────────────────────────────────────────────┐
│              cert-manager TLS Flow                                │
│                                                                  │
│  1. You create Ingress with annotation:                          │
│     cert-manager.io/cluster-issuer: "letsencrypt-prod"           │
│                                                                  │
│  2. cert-manager sees the annotation, creates a Certificate      │
│     resource and an ACME Order                                   │
│                                                                  │
│  3. cert-manager creates a temporary Ingress for the ACME        │
│     HTTP-01 challenge (/.well-known/acme-challenge/xxx)          │
│                                                                  │
│  4. Let's Encrypt validates domain ownership                     │
│                                                                  │
│  5. cert-manager stores the signed certificate in the            │
│     Kubernetes Secret referenced by secretName                   │
│                                                                  │
│  6. Nginx Ingress Controller reads the Secret and configures     │
│     TLS termination                                              │
│                                                                  │
│  7. cert-manager renews before expiry (30 days before)           │
│                                                                  │
│  ┌─────────┐    ┌──────────────┐    ┌────────────────┐           │
│  │ Ingress  │───▶│ cert-manager │───▶│ Let's Encrypt  │           │
│  │ resource │    │ controller   │    │ ACME server    │           │
│  └─────────┘    └──────┬───────┘    └────────────────┘           │
│                        │                                         │
│                        ▼                                         │
│                 ┌──────────────┐                                  │
│                 │ TLS Secret   │  ← Auto-created & renewed       │
│                 │ (cert + key) │                                  │
│                 └──────────────┘                                  │
└──────────────────────────────────────────────────────────────────┘
```

---

### Common Nginx Ingress Annotations

Annotations are the primary configuration mechanism for the Nginx Ingress Controller:

```yaml
metadata:
  annotations:
    # TLS
    nginx.ingress.kubernetes.io/ssl-redirect: "true"
    nginx.ingress.kubernetes.io/force-ssl-redirect: "true"

    # Rate limiting
    nginx.ingress.kubernetes.io/limit-rps: "50"
    nginx.ingress.kubernetes.io/limit-burst-multiplier: "5"
    nginx.ingress.kubernetes.io/limit-connections: "10"

    # Timeouts
    nginx.ingress.kubernetes.io/proxy-connect-timeout: "10"
    nginx.ingress.kubernetes.io/proxy-read-timeout: "60"
    nginx.ingress.kubernetes.io/proxy-send-timeout: "60"

    # Body size (for file uploads)
    nginx.ingress.kubernetes.io/proxy-body-size: "50m"

    # CORS
    nginx.ingress.kubernetes.io/enable-cors: "true"
    nginx.ingress.kubernetes.io/cors-allow-origin: "https://dashboard.deployforge.io"
    nginx.ingress.kubernetes.io/cors-allow-methods: "GET, POST, PUT, DELETE, OPTIONS"

    # Canary deployment (traffic splitting)
    nginx.ingress.kubernetes.io/canary: "true"
    nginx.ingress.kubernetes.io/canary-weight: "20"     # 20% to canary

    # URL rewriting
    nginx.ingress.kubernetes.io/rewrite-target: /$2

    # Custom headers
    nginx.ingress.kubernetes.io/configuration-snippet: |
      more_set_headers "X-Request-ID: $req_id";
      more_set_headers "Strict-Transport-Security: max-age=31536000";

    # External authentication
    nginx.ingress.kubernetes.io/auth-url: "https://auth.deployforge.io/verify"
    nginx.ingress.kubernetes.io/auth-signin: "https://auth.deployforge.io/login"
```

---

### Gateway API (The Future of Ingress)

The Gateway API is a collection of CRDs that provide a more expressive, role-oriented alternative to Ingress. It addresses key Ingress limitations:

```
┌─────────────────────────────────────────────────────────────────────┐
│         Ingress vs Gateway API                                       │
│                                                                     │
│  Ingress (current):           Gateway API (future):                 │
│                                                                     │
│  ┌─────────────────┐          ┌───────────────────┐                 │
│  │ Ingress          │          │ GatewayClass       │  ← Infra team  │
│  │ (one resource    │          │ (what controller)  │                │
│  │  does everything)│          └─────────┬─────────┘                │
│  │                  │                    ▼                          │
│  │ - routes         │          ┌───────────────────┐                │
│  │ - TLS            │          │ Gateway            │  ← Platform   │
│  │ - all annotations│          │ (listeners, TLS)   │    team       │
│  └─────────────────┘          └─────────┬─────────┘                │
│                                         ▼                          │
│                               ┌───────────────────┐                │
│                               │ HTTPRoute          │  ← App team   │
│                               │ (routing rules)    │                │
│                               └───────────────────┘                │
│                                                                     │
│  Advantages:                                                        │
│  ✅ Role-based: infra, platform, app teams manage their part        │
│  ✅ TCP/UDP/gRPC routing (not just HTTP)                            │
│  ✅ Traffic splitting built-in (no annotations)                     │
│  ✅ Header-based routing                                            │
│  ✅ Cross-namespace references                                      │
│  ✅ Portable across implementations                                 │
└─────────────────────────────────────────────────────────────────────┘
```

```yaml
# Gateway API example (requires a Gateway API-compatible controller)
apiVersion: gateway.networking.k8s.io/v1
kind: GatewayClass
metadata:
  name: nginx
spec:
  controllerName: gateway.nginx.org/nginx-gateway-controller

---
apiVersion: gateway.networking.k8s.io/v1
kind: Gateway
metadata:
  name: deployforge-gateway
  namespace: deployforge
spec:
  gatewayClassName: nginx
  listeners:
  - name: https
    protocol: HTTPS
    port: 443
    tls:
      mode: Terminate
      certificateRefs:
      - name: deployforge-tls
  - name: http
    protocol: HTTP
    port: 80

---
apiVersion: gateway.networking.k8s.io/v1
kind: HTTPRoute
metadata:
  name: api-route
  namespace: deployforge
spec:
  parentRefs:
  - name: deployforge-gateway
  hostnames:
  - "api.deployforge.local"
  rules:
  - matches:
    - path:
        type: PathPrefix
        value: /api
    backendRefs:
    - name: api-gateway
      port: 3000
      weight: 80
    - name: api-gateway-canary
      port: 3000
      weight: 20            # Built-in traffic splitting!
```

---

## Code Examples

### Complete DeployForge Ingress Setup

```bash
#!/bin/bash
set -euo pipefail

echo "=== Step 1: Install Nginx Ingress Controller (kind) ==="
kubectl apply -f https://raw.githubusercontent.com/kubernetes/ingress-nginx/main/deploy/static/provider/kind/deploy.yaml

kubectl wait --namespace ingress-nginx \
  --for=condition=ready pod \
  --selector=app.kubernetes.io/component=controller \
  --timeout=120s

echo "=== Step 2: Create TLS Secret ==="
openssl req -x509 -nodes -days 365 -newkey rsa:2048 \
  -keyout tls.key -out tls.crt \
  -subj "/CN=deployforge.local/O=DeployForge" 2>/dev/null

kubectl create secret tls deployforge-tls \
  --cert=tls.crt --key=tls.key \
  -n deployforge --dry-run=client -o yaml | kubectl apply -f -

rm tls.key tls.crt

echo "=== Step 3: Create Ingress Resource ==="
cat <<'EOF' | kubectl apply -f -
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: deployforge-ingress
  namespace: deployforge
  annotations:
    nginx.ingress.kubernetes.io/ssl-redirect: "true"
    nginx.ingress.kubernetes.io/proxy-read-timeout: "60"
    nginx.ingress.kubernetes.io/proxy-body-size: "10m"
    nginx.ingress.kubernetes.io/limit-rps: "50"
spec:
  ingressClassName: nginx
  tls:
  - hosts:
    - deployforge.local
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
            name: nginx
            port:
              number: 80
EOF

echo "=== Step 4: Verify ==="
kubectl get ingress -n deployforge
kubectl describe ingress deployforge-ingress -n deployforge
```

### Testing Ingress Routing

```bash
# Add to /etc/hosts for local testing
echo "127.0.0.1 deployforge.local" | sudo tee -a /etc/hosts

# Test HTTPS (self-signed cert, so use -k to skip verification)
curl -k https://deployforge.local/health
curl -k https://deployforge.local/api/v1/status
curl -k https://deployforge.local/

# Verify TLS certificate
curl -kv https://deployforge.local 2>&1 | grep "subject:"
# → subject: CN=deployforge.local; O=DeployForge

# Check Nginx Ingress Controller logs for routing
kubectl logs -n ingress-nginx -l app.kubernetes.io/component=controller --tail=20
```

---

## Try It Yourself

### Challenge 1: Set Up Multi-Host Ingress

Configure host-based routing so that:
- `api.deployforge.local` → api-gateway service (port 3000)
- `dashboard.deployforge.local` → nginx service (port 80)

Both should have TLS enabled with a self-signed certificate.

<details>
<summary>Show solution</summary>

```bash
#!/bin/bash
set -euo pipefail

# Generate a certificate with SANs for both hosts
openssl req -x509 -nodes -days 365 -newkey rsa:2048 \
  -keyout tls.key -out tls.crt \
  -subj "/CN=deployforge.local/O=DeployForge" \
  -addext "subjectAltName=DNS:api.deployforge.local,DNS:dashboard.deployforge.local" \
  2>/dev/null

kubectl create secret tls deployforge-multi-tls \
  --cert=tls.crt --key=tls.key \
  -n deployforge --dry-run=client -o yaml | kubectl apply -f -

rm tls.key tls.crt

cat <<'EOF' | kubectl apply -f -
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: deployforge-multi-host
  namespace: deployforge
  annotations:
    nginx.ingress.kubernetes.io/ssl-redirect: "true"
spec:
  ingressClassName: nginx
  tls:
  - hosts:
    - api.deployforge.local
    - dashboard.deployforge.local
    secretName: deployforge-multi-tls
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
  - host: dashboard.deployforge.local
    http:
      paths:
      - path: /
        pathType: Prefix
        backend:
          service:
            name: nginx
            port:
              number: 80
EOF

# Add hosts entries
echo "127.0.0.1 api.deployforge.local dashboard.deployforge.local" | \
  sudo tee -a /etc/hosts

# Test
curl -k https://api.deployforge.local/health
curl -k https://dashboard.deployforge.local/
```

</details>

### Challenge 2: Configure Rate Limiting and Canary Routing

Set up two Ingress resources: one for the stable API Gateway, and a canary Ingress that sends 10% of traffic to a canary version.

<details>
<summary>Show solution</summary>

```bash
#!/bin/bash
set -euo pipefail

# Main ingress with rate limiting
cat <<'EOF' | kubectl apply -f -
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: deployforge-stable
  namespace: deployforge
  annotations:
    nginx.ingress.kubernetes.io/limit-rps: "100"
    nginx.ingress.kubernetes.io/limit-burst-multiplier: "5"
spec:
  ingressClassName: nginx
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
---
# Canary ingress — 10% of traffic goes to canary service
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: deployforge-canary
  namespace: deployforge
  annotations:
    nginx.ingress.kubernetes.io/canary: "true"
    nginx.ingress.kubernetes.io/canary-weight: "10"
spec:
  ingressClassName: nginx
  rules:
  - host: deployforge.local
    http:
      paths:
      - path: /api
        pathType: Prefix
        backend:
          service:
            name: api-gateway-canary
            port:
              number: 3000
EOF

# Verify both ingresses
kubectl get ingress -n deployforge

# Test — roughly 10% of requests should hit the canary
for i in $(seq 1 20); do
  curl -sk https://deployforge.local/api/version 2>/dev/null
done
```

</details>

---

## Capstone Connection

**DeployForge** uses Ingress as its single external entry point, centralizing TLS, routing, and rate limiting:

- **Nginx Ingress Controller** — DeployForge installs the Nginx Ingress Controller as a DaemonSet (from Module 05), ensuring every worker node can accept external traffic. The Ingress resource routes `/api` to the API Gateway and `/` to the static frontend served by Nginx.
- **TLS termination** — In development (kind), DeployForge uses a self-signed certificate. In production, cert-manager with the `letsencrypt-prod` ClusterIssuer automates certificate issuance for `deployforge.io` and `api.deployforge.io`. Backend services receive plain HTTP, simplifying application code.
- **Rate limiting** — The Ingress annotation `limit-rps: 50` protects the API Gateway from abuse. In Module 09 (Reliability Engineering), you'll tune this based on load testing results.
- **Canary deployments** — When deploying a new API Gateway version, DeployForge uses a canary Ingress to send 10% of traffic to the new version. If error rates stay low (monitored in Module 08 — Observability), the canary weight gradually increases to 100%.
- **Path-based routing** — The `/api` prefix routes to the API Gateway, while `/` serves the dashboard. This single-entry-point pattern avoids provisioning multiple load balancers and simplifies DNS management.
- **Gateway API migration** — As the Gateway API matures, DeployForge will migrate from Ingress to HTTPRoute resources, gaining native traffic splitting and header-based routing without annotation hacks.
