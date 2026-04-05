# 3.2 — L4 vs. L7 Load Balancing

## Concept

Load balancers operate at different network layers. **L4 load balancers** work at the TCP/UDP level — they forward packets based on IP and port without understanding application content. **L7 load balancers** understand HTTP — they can route based on URL, headers, cookies, or request body. Most modern production systems use L7, but L4 is useful for database traffic, TLS offloading in depth, and ultra-high performance scenarios.

---

## Deep Dive

### Layer 4 — TCP Load Balancing

```
Client → L4 LB → Server

L4 sees only:
  - Source IP:Port
  - Destination IP:Port
  - TCP state (SYN, ACK, FIN)

L4 does NOT see:
  - HTTP method, URL, headers
  - Cookies, JWT tokens
  - Request body

Process:
  1. Client connects to LB IP (e.g. 10.0.0.1:80)
  2. LB picks backend server (e.g. 10.0.0.10:3001)
  3. LB transparently forwards ALL packets from that TCP connection
  4. Connection-pinned: all packets in a TCP connection go to the same backend

Examples: AWS NLB (Network Load Balancer), HAProxy (TCP mode), Nginx (stream module)

Best for:
  • Database connections (TCP, not HTTP)
  • Non-HTTP protocols (gRPC, MQTT, raw TCP)
  • Ultra-low latency (no packet inspection overhead)
  • TLS passthrough (LB doesn't decrypt, backend handles TLS)
```

### Layer 7 — HTTP Load Balancing

```
Client → L7 LB → Server

L7 sees:
  - HTTP method, URL path, query string
  - Request headers (Authorization, Host, User-Agent)
  - Cookies, session IDs
  - Response status codes, headers

L7 can:
  ✓ Route /api/* to app servers, /static/* to object storage
  ✓ Extract JWT and route VIP users to premium server pool
  ✓ A/B test based on User-Agent or custom header
  ✓ Terminate TLS, then forward plain HTTP internally
  ✓ Return cached responses without touching backends
  ✓ Add/remove headers (X-Forwarded-For, X-Request-Id)
  ✓ Rate limit by URL + IP + authenticated user

Examples: Nginx, HAProxy (HTTP mode), AWS ALB, Cloudflare Workers, Envoy

Best for:
  • Web applications (the default for HTTP APIs)
  • Microservices routing (by path prefix, hostname, or header)
  • Authentication offloading
  • DDoS mitigation (understand and drop malicious HTTP patterns)
```

### SSL/TLS Termination

```
Option A: Terminate at L7 LB (most common)
  Client ──[HTTPS]──► LB ──[HTTP]──► App Server
  
  ✓ App servers don't manage certs
  ✓ LB can inspect + modify HTTP content
  ✓ Single cert to manage
  ✗ Traffic from LB to app is unencrypted (safe in private VPC)

Option B: Pass-through at L4, terminate at app (TLS end-to-end)
  Client ──[HTTPS]──► L4 LB ──[HTTPS]──► App Server (terminates TLS)
  
  ✓ Full end-to-end encryption
  ✗ LB can't inspect content (no L7 features)
  ✗ Each app server manages certs

Option C: Re-encrypt (TLS all the way)
  Client ──[HTTPS]──► LB ──[HTTPS]──► App Server
  
  ✓ Encrypted everywhere
  ✓ LB has full L7 capabilities
  ✗ Performance overhead of double encryption/decryption
  Use case: PCI compliance, regulated industries (banking, healthcare)

ScaleForge uses: Option A — terminate at Nginx, plain HTTP to app containers in Docker network
```

### Content-Based Routing

```
L7 routing rules (Nginx pseudo-config):

  # Route based on path prefix
  /api/v1/*    → api_upstream (app servers)
  /static/*    → cdn_upstream (or object storage)
  /health      → local (Nginx handles directly, no backend needed)

  # Route based on host header  
  app.scaleforge.io    → frontend_upstream
  api.scaleforge.io    → api_upstream
  metrics.scaleforge.io → internal (blocked from public, VPN only)

  # Route based on request header (A/B test or canary)
  X-Feature-Version: v2  → v2_upstream (new version)
  (default)              → v1_upstream (stable)
```

---

## Code Examples

### Nginx L7 Configuration for ScaleForge

```nginx
# capstone/scaleforge/nginx/nginx.conf

events {
    worker_connections 1024;
    use epoll;   # Linux kernel I/O event notification (more efficient than select)
}

http {
    # Upstream group: 3 app replicas with least_conn algorithm
    upstream app_backend {
        least_conn;  # L7 feature: Nginx counts active HTTP requests

        server app_1:3001 weight=1 max_fails=3 fail_timeout=30s;
        server app_2:3001 weight=1 max_fails=3 fail_timeout=30s;
        server app_3:3001 weight=1 max_fails=3 fail_timeout=30s;

        keepalive 32;  # Maintain 32 keepalive connections to upstream
        keepalive_requests 1000;  # Reuse keepalive connection for 1000 requests
    }

    # Upstream for health checks only (bypasses app logic)
    upstream health_backend {
        server app_1:3001;
    }

    server {
        listen 80;
        server_name _;

        # Logging with request id
        log_format main '$remote_addr - $request_id "$request" $status $body_bytes_sent';
        access_log /var/log/nginx/access.log main;

        # Pass unique request ID to upstream
        add_header X-Request-Id $request_id always;

        # The redirect path: fast, minimal processing
        location ~ ^/[a-zA-Z0-9_\-]{4,12}$ {
            proxy_pass http://app_backend;
            proxy_http_version 1.1;
            proxy_set_header Connection '';
            proxy_set_header Host $host;
            proxy_set_header X-Real-IP $remote_addr;
            proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
            proxy_set_header X-Forwarded-Proto $scheme;
            proxy_set_header X-Request-Id $request_id;

            # Timeouts — fail fast if backend is slow
            proxy_connect_timeout 2s;
            proxy_read_timeout 5s;
            proxy_send_timeout 5s;
        }

        # API routes
        location /api/ {
            proxy_pass http://app_backend;
            proxy_http_version 1.1;
            proxy_set_header Connection '';
            proxy_set_header Host $host;
            proxy_set_header X-Real-IP $remote_addr;
            proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
            proxy_read_timeout 30s;  # API allows slower responses
        }

        # Block direct access to metrics endpoint from public
        location /metrics {
            deny all;
            return 403;
        }
    }
}
```

---

## Try It Yourself

**Exercise:** Add content-based routing to send `/api/v2/*` to a canary server.

```nginx
# In the nginx.conf upstream block, add:
# upstream canary_backend {
#   server app_canary:3001;
# }

# In the server block, add a location before /api/:
# location /api/v2/ {
#   # TODO: route to canary_backend
#   # Also: add a response header X-Version: canary so you can verify
# }

# Test with:
# curl -v http://localhost:8080/api/v2/urls
# curl -v http://localhost:8080/api/v1/urls
# Both should work, but response headers differ
```

<details>
<summary>Show solution</summary>

```nginx
upstream canary_backend {
    server app_canary:3001;
}

location /api/v2/ {
    add_header X-Version canary always;
    proxy_pass http://canary_backend;
    proxy_http_version 1.1;
    proxy_set_header Connection '';
    proxy_set_header Host $host;
}

location /api/ {
    add_header X-Version stable always;
    proxy_pass http://app_backend;
    proxy_http_version 1.1;
    proxy_set_header Connection '';
    proxy_set_header Host $host;
}
```

</details>

---

## Capstone Connection

ScaleForge's Docker Compose uses Nginx as an L7 load balancer in front of 3 `app` containers. The `least_conn` directive (L7 feature) prevents a slow GC pause on one replica from accepting new connections during the pause. The `max_fails=3 fail_timeout=30s` passive health check removes a failed replica from the pool for 30 seconds — this is why Module 03 also adds an active health check endpoint (`/ready`) that Nginx polls every 5 seconds to detect failure faster than the passive approach.
