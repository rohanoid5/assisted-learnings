# 3.3 — Nginx Configuration

## Concept

Nginx is a high-performance web server, reverse proxy, and load balancer. For ScaleForge, Nginx is the single entry point for all traffic — it terminates TLS, routes requests, balances load, enforces rate limits, and serves static assets. This topic covers production-ready Nginx configuration for a Node.js backend.

---

## Deep Dive

### Nginx Architecture — Why It's Fast

```
Apache (older approach):               Nginx (event-driven approach):
  Per-request threads or processes       Master process + worker processes
                                         Each worker = single thread, event loop
  Request 1 → Thread 1 (blocks on I/O)  Worker 1 handles 10,000+ concurrent
  Request 2 → Thread 2 (blocks on I/O)  connections with zero blocking
  Request N → Thread N (OOM risk)
  
  1000 concurrent connections =         1000 concurrent connections =
  1000 threads × 8MB stack =            2-4 worker processes × event loop
  8 GB RAM for stacks alone!            ~50 MB RAM total
  
Nginx worker count: set to CPU core count (worker_processes auto)
Each worker: handles all I/O with epoll (Linux) / kqueue (BSD/macOS)
```

### Key Configuration Directives

```nginx
# Performance tuning
worker_processes auto;        # One per CPU core
worker_connections 1024;      # Max connections per worker
                              # Total max = worker_processes × 1024

# Request handling
client_max_body_size 10m;    # Max POST body (prevent large upload DoS)
client_body_timeout 10s;     # Timeout reading client body
client_header_timeout 10s;   # Timeout reading client headers
keepalive_timeout 65s;       # How long to keep idle client connections open
keepalive_requests 1000;     # Max requests per keepalive connection

# Buffer sizes — critical for performance
proxy_buffer_size 4k;         # Size of proxy response header buffer
proxy_buffers 4 8k;           # Number and size of proxy response buffers
client_header_buffer_size 1k; # Sufficient for most request headers
```

### Proxy Caching for the Redirect Path

```nginx
# nginx.conf — cache redirect lookups at the Nginx layer
# (If the redirect is cached in Nginx, the app server is never hit)

http {
    # Define cache zone in shared memory
    proxy_cache_path /var/cache/nginx
        levels=1:2
        keys_zone=redirect_cache:10m   # 10MB metadata cache (holds ~80K keys)
        max_size=1g                    # Max 1GB of cached responses on disk
        inactive=5m                   # Evict cached items not accessed for 5 min
        use_temp_path=off;

    server {
        location ~ ^/[a-zA-Z0-9_\-]{4,12}$ {
            proxy_pass http://app_backend;
            
            # Enable caching for this location
            proxy_cache redirect_cache;
            proxy_cache_key "$uri";   # Cache key = URL path (not query params)
            
            # Cache 302 responses for 5 minutes
            proxy_cache_valid 302 5m;
            
            # Don't cache 404s (URL may be created soon)
            proxy_cache_valid 404 0;
            
            # Serve stale cached response while revalidating (async refresh)
            proxy_cache_use_stale updating;
            
            # Header indicating cache hit/miss (useful for debugging)
            add_header X-Cache-Status $upstream_cache_status;
        }
    }
}
```

**Impact**: With the top 1000 URLs cached in Nginx (which fits entirely in the 10MB memory zone), those redirects never touch the app server → latency drops from ~5ms to ~0.1ms, throughput cap is Nginx's own rate (~20k req/s on a single core).

---

## Code Examples

### Health Check Endpoints

```typescript
// src/routes/health.router.ts
// Liveness: is the process alive? Readiness: is it ready to accept traffic?

import { Router } from 'express';
import { pool } from '../db/pool.js';
import { redisClient } from '../cache/redis.client.js';

export const healthRouter = Router();

// GET /health — liveness probe
// Returns 200 as long as the process is running and event loop is not blocked.
// Nginx and Kubernetes use this to decide if the process should be RESTARTED.
healthRouter.get('/health', (_req, res) => {
  res.json({
    status: 'ok',
    uptime: Math.floor(process.uptime()),
    timestamp: new Date().toISOString(),
  });
});

// GET /ready — readiness probe
// Returns 200 only if ALL dependencies are reachable.
// Nginx/Kubernetes use this to decide if the instance should RECEIVE TRAFFIC.
// An instance that is "alive" but "not ready" is excluded from the LB pool.
healthRouter.get('/ready', async (_req, res) => {
  const checks = {
    postgres: false,
    redis: false,
  };

  // Check PostgreSQL
  try {
    await pool.query('SELECT 1');
    checks.postgres = true;
  } catch {
    // Postgres not reachable
  }

  // Check Redis
  try {
    await redisClient.ping();
    checks.redis = true;
  } catch {
    // Redis not reachable
  }

  const allReady = Object.values(checks).every(Boolean);
  res.status(allReady ? 200 : 503).json({
    status: allReady ? 'ready' : 'not_ready',
    checks,
  });
});
```

### Nginx Active Health Check Configuration

```nginx
# nginx.conf — active health checks poll /ready every 5 seconds
# Note: Active health checks require Nginx Plus (commercial) or
#       the nginx_upstream_check_module (open-source alternative).
# For open-source Nginx, use passive health checks (max_fails / fail_timeout).

upstream app_backend {
    least_conn;

    server app_1:3001 max_fails=3 fail_timeout=30s;
    server app_2:3001 max_fails=3 fail_timeout=30s;
    server app_3:3001 max_fails=3 fail_timeout=30s;

    # Passive health check:
    # - After 3 failures within 30 seconds, remove server from pool for 30s
    # - Server is re-added automatically after fail_timeout expires

    # For proactive active checks, use a separate nginx_upstream_check block:
    # check interval=5000 rise=2 fall=3 timeout=1000 type=http;
    # check_http_send "GET /ready HTTP/1.0\r\n\r\n";
    # check_http_expect_alive http_2xx;

    keepalive 32;
}
```

---

## Try It Yourself

**Exercise:** Configure Nginx rate limiting per client IP.

```nginx
# In nginx.conf http block:
# Define a rate limit zone: 10MB shared memory, 100 req/sec per IP

# TODO:
# 1. Define limit_req_zone for redirect path (allow burst of 50, no delay)
# 2. Define limit_req_zone for API path (stricter: 10 req/sec, burst 20)
# 3. Configure limit_req in the appropriate location blocks
# 4. Return 429 with JSON body on rate limit (not Nginx default HTML)
#
# Test with: for i in {1..200}; do curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8080/test01; done
# You should see 200s then 429s
```

<details>
<summary>Show solution</summary>

```nginx
http {
    # Define shared memory zones for rate tracking
    # $binary_remote_addr is more memory-efficient than $remote_addr
    limit_req_zone $binary_remote_addr zone=redirect_limit:10m rate=100r/s;
    limit_req_zone $binary_remote_addr zone=api_limit:10m rate=10r/s;

    # Return JSON on rate limit (instead of default HTML page)
    limit_req_status 429;

    server {
        # Custom error page for rate limit
        error_page 429 /rate-limited.json;
        location = /rate-limited.json {
            internal;
            add_header Content-Type application/json;
            return 429 '{"error":"Too many requests","retryAfter":1}';
        }

        location ~ ^/[a-zA-Z0-9_\-]{4,12}$ {
            limit_req zone=redirect_limit burst=50 nodelay;
            # burst=50: allow up to 50 extra requests instantly
            # nodelay: don't queue burst — serve immediately then cut off
            proxy_pass http://app_backend;
        }

        location /api/ {
            limit_req zone=api_limit burst=20 delay=10;
            # delay=10: first 10 burst requests are served immediately,
            # remaining are delayed to enforce the rate
            proxy_pass http://app_backend;
        }
    }
}
```

</details>

---

## Capstone Connection

The Nginx config here is `capstone/scaleforge/nginx/nginx.conf`. Add the `proxy_cache` section for the stretch goal to serve the top 1000 URLs without touching the app server — this directly demonstrates the difference between **edge caching** (in Nginx) and **application caching** (Redis in Module 05). In Module 08, you'll add Nginx's `stub_status` module output to Prometheus using `nginx-prometheus-exporter`, tracking hit/miss rates for the proxy cache.
