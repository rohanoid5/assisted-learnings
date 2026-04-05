# Module 3 — Load Balancing & CDNs

## Overview

A single server has hard limits — CPU, memory, and network bandwidth. Load balancers distribute traffic across multiple servers to increase throughput and availability. CDNs push static assets and even dynamic responses closer to users, reducing latency dramatically. Together, they are the primary mechanisms for achieving the "scale out" half of horizontal scalability.

---

## Learning Objectives

By the end of this module, you will be able to:

- [ ] Explain L4 vs. L7 load balancing and when to use each
- [ ] Compare load balancing algorithms: round-robin, least connections, consistent hashing
- [ ] Configure Nginx as a reverse proxy and upstream load balancer
- [ ] Implement session affinity (sticky sessions) and explain its tradeoffs
- [ ] Explain CDN caching, cache invalidation, and origin shield patterns
- [ ] Design a health check system that accurately reflects application readiness
- [ ] Calculate how many ScaleForge replicas are needed to serve 10k req/s at p99 < 20ms

---

## Topics

| # | File | Summary | Estimated Time |
|---|------|---------|---------------|
| 01 | [01-load-balancing-algorithms.md](./01-load-balancing-algorithms.md) | Round-robin, least connections, consistent hashing | 40 min |
| 02 | [02-l4-vs-l7-load-balancing.md](./02-l4-vs-l7-load-balancing.md) | TCP (L4) vs. HTTP (L7) load balancers, SSL termination | 35 min |
| 03 | [03-nginx-configuration.md](./03-nginx-configuration.md) | Nginx as reverse proxy, upstream groups, caching | 45 min |
| 04 | [04-health-checks-and-circuit-breakers.md](./04-health-checks-and-circuit-breakers.md) | Liveness vs. readiness, health check design | 35 min |
| 05 | [05-cdns-and-edge-caching.md](./05-cdns-and-edge-caching.md) | CDN architecture, cache-control, origin shield | 40 min |
| 06 | [06-horizontal-vs-vertical-scaling.md](./06-horizontal-vs-vertical-scaling.md) | Scale-up vs. scale-out, auto-scaling triggers | 30 min |
| — | [exercises/README.md](./exercises/README.md) | Hands-on exercises | 45 min |

**Estimated total time:** 4–5 hours

---

## Prerequisites

- [Module 02 — Networking & Communication](../02-networking/README.md) completed
- Docker Compose running locally (`docker compose up -d` in `capstone/scaleforge/`)

---

## Capstone Milestone

By the end of this module, ScaleForge should have:

1. **Nginx load balancer** routing traffic across 3 app replicas with:
   - `upstream` group with 3 backends
   - Least-connections algorithm
   - Health check every 5 seconds
   - `keepalive 32` connections to upstream
2. **`/health` vs. `/ready` endpoints** — liveness separate from readiness
3. **Redirect path benchmarked**: use `wrk` or `autocannon` to verify 10k req/s at p99 < 20ms behind the load balancer
4. **Stretch goal**: Configure Nginx to cache the redirect lookup for 5 minutes with proxy_cache, serving top-1000 URLs without hitting the app server at all
