# Module 03 — Exercises

## Overview

These exercises complete the ScaleForge load balancing milestone: your app runs behind Nginx with multiple replicas, passes health checks, survives replica failure, and has a measurable SLA.

**Time estimate:** 3–4 hours  
**Tools needed:** Docker Desktop, Node.js 20+, `autocannon` (`npm i -g autocannon`)

---

## Exercise 1 — Run ScaleForge with 3 Replicas Behind Nginx

**Goal:** Get the full stack running locally with Nginx as the load balancer and 3 app replicas.

**Steps:**

```bash
# 1. Navigate to the ScaleForge capstone
cd capstone/scaleforge

# 2. Copy the example environment file
cp .env.example .env

# 3. Build the app image
docker compose build

# 4. Start all services with 3 app replicas
docker compose up --scale app=3 -d

# 5. Verify all services are running
docker compose ps

# Expected output:
# scaleforge-app-1     running   (no ports exposed directly)
# scaleforge-app-2     running
# scaleforge-app-3     running
# scaleforge-nginx-1   running   0.0.0.0:8080->80/tcp
# scaleforge-postgres  running
# scaleforge-redis     running

# 6. Verify Nginx is routing to all 3 replicas
# (each request may hit a different replica)
for i in {1..9}; do curl -s http://localhost:8080/worker-id; echo; done

# Expected: you should see 3 different PIDs across the 9 requests
```

**Checkpoint:** `curl http://localhost:8080/health` returns `200` and:  
`curl http://localhost:8080/ready` returns `{"status":"ready","checks":{"postgres":true,"redis":true}}`

---

## Exercise 2 — Benchmark Throughput and Establish Your SLA

**Goal:** Measure the baseline throughput and p99 latency for the redirect path at 3 replicas.

```bash
# 1. Create a test short URL (or seed one in the DB)
curl -s -X POST http://localhost:8080/api/v1/urls \
  -H "Content-Type: application/json" \
  -d '{"url": "https://example.com", "code": "bench01"}'

# 2. Benchmark with 50 concurrent connections for 30 seconds
autocannon http://localhost:8080/bench01 \
  --duration 30 \
  --connections 50 \
  --pipelining 1

# 3. Record results in this table:
# Replicas | Connections | Req/sec | p50  | p99  | Errors
# ─────────┼─────────────┼─────────┼──────┼──────┼───────
#    3     |    50       |         |      |      |

# 4. Repeat with --connections 100 and --connections 200
# At what concurrency does the p99 exceed 20ms?
```

**Acceptance criteria:** At 50 concurrent connections, p99 < 20ms, zero errors.

---

## Exercise 3 — Simulate Replica Failure and Nginx Failover

**Goal:** Verify that killing one replica mid-traffic causes no visible errors.

```bash
# Terminal 1: Start a sustained load test
autocannon http://localhost:8080/bench01 \
  --duration 60 \
  --connections 50 \
  --renderResultsTable

# Terminal 2 (while Terminal 1 is running): Kill one replica
docker compose stop app  # This stops app-1 first

# OR: Kill by container ID for a specific replica:
docker ps | grep scaleforge-app   # Find container IDs
docker stop <container-id-for-app-2>

# What to observe in Terminal 1:
# - A brief increase in latency (Nginx detects failure via max_fails=3)
# - After ~30 seconds: latency returns to baseline (Nginx re-distributes)
# - Ideally: zero errors (Nginx should retry on the next healthy upstream)
```

**Expected behaviour:** After killing one of 3 replicas:
- Nginx serves existing in-flight requests from the killed replica via timeout/retry
- New requests route to remaining 2 healthy replicas
- Total throughput drops by ~33% (from 3 to 2 replicas)
- Error count: 0 (Nginx `max_fails` passive health check handles the failover)

**Nginx configuration to verify this works:**

```nginx
upstream app_backend {
    least_conn;
    server app:3001 max_fails=3 fail_timeout=30s;  # ← This is the key directive
    keepalive 32;
}
```

---

## Exercise 4 — Enable Proxy Cache for Hot URLs

**Goal:** Configure Nginx proxy cache for the redirect endpoint and measure the speedup.

```bash
# Baseline: benchmark without cache
autocannon http://localhost:8080/bench01 -d 10 -c 100
# Record: req/sec and p99

# Step 1: Edit nginx/nginx.conf to add proxy caching
# (See 03-nginx-configuration.md for the proxy_cache_path + proxy_cache directives)

# Step 2: Reload Nginx without downtime
docker compose exec nginx nginx -s reload

# Step 3: Warm the cache
curl -v http://localhost:8080/bench01
# Look for: X-Cache-Status: MISS (first hit fills cache)
curl -v http://localhost:8080/bench01
# Look for: X-Cache-Status: HIT (subsequent hits served from cache)

# Step 4: Benchmark with cache enabled
autocannon http://localhost:8080/bench01 -d 10 -c 100
# Record: req/sec and p99

# Compare:
# Without cache:  ~9,000 req/sec, p99 ~12ms
# With cache:     ~20,000+ req/sec, p99 ~2ms  (served by Nginx, app not involved)
```

**Note on analytics tradeoff:** With Nginx proxy cache enabled, the redirect handler in your app is **not** called for cached responses — so click events are not recorded for cached redirects. This is the fundamental tradeoff between caching for performance and accuracy of analytics. Options:
1. Don't cache the redirect path (chosen by ScaleForge for correctness)
2. Cache with a very short TTL (5s) to limit analytics loss
3. Use edge logic (Cloudflare Workers / Lambda@Edge) to cache + log at the CDN layer

---

## Summary Checklist

- [ ] ScaleForge runs with 3 replicas behind Nginx (`docker compose up --scale app=3`)
- [ ] `/health` and `/ready` endpoints respond correctly 
- [ ] Benchmark at 50 concurrent connections shows p99 < 20ms
- [ ] Killing one replica causes zero user-visible errors
- [ ] Proxy cache config added and cache hit verified via `X-Cache-Status: HIT`
- [ ] Understanding of analytics tradeoff when caching the redirect path

---

## Module 03 Capstone Milestone ✓

After completing these exercises, ScaleForge satisfies:

| Requirement | Implementation |
|---|---|
| 10,000 req/s redirect throughput | 3+ replicas, Nginx least_conn |
| p99 < 20ms | Validated by autocannon benchmark |
| Zero-downtime replica failure | Nginx passive health check + retry |
| Zero-downtime deploys | `/ready` + SIGTERM graceful shutdown |
| TLS termination | Nginx (single cert for all replicas) |

**Next:** Module 04 will add the database layer — indexes, query planning, read replicas, and connection pooling with PgBouncer so ScaleForge can scale Postgres connections beyond the `max_connections` ceiling you hit in Exercise 2.
