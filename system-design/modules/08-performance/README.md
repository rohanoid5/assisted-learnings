# Module 08 — Performance & Monitoring

> "You can't optimize what you can't measure."

---

## What This Module Covers

This module is about finding and fixing real bottlenecks in a running Node.js system, rather than pre-optimizing by instinct. The tools and techniques here apply directly to ScaleForge (redirect throughput) and FlowForge (notification worker throughput).

| Topic | File |
|---|---|
| Node.js Performance Profiling | [01-nodejs-profiling.md](./01-nodejs-profiling.md) |
| Connection Pool Tuning | [02-connection-pool-tuning.md](./02-connection-pool-tuning.md) |
| Query Performance Analysis | [03-query-performance.md](./03-query-performance.md) |
| Prometheus & Grafana Dashboards | [04-prometheus-grafana.md](./04-prometheus-grafana.md) |
| Load Testing with Autocannon | [05-load-testing.md](./05-load-testing.md) |
| Exercises | [exercises/README.md](./exercises/README.md) |

---

## The ScaleForge Performance Target

```
  ScaleForge redirect SLO:
  
    p50 latency:  <  5ms  (Redis cache HIT + 302 response)
    p99 latency:  < 50ms  (Redis cache MISS → Postgres → 302 response)
    throughput:   > 5000 req/s on a single Node.js process
    error rate:   < 0.1%
  
  Current production path for a redirect (cache HIT):
  
    Client → Nginx → Node.js → Redis GET → 302 Found
                      ↑
              This is the hot path.
              Everything here must be sub-millisecond.
  
  Cache MISS path:
  
    Client → Nginx → Node.js → Redis GET (miss) → Postgres SELECT → Redis SET → 302
                                                     ↑
                                             This can be 10-30ms
                                             depending on query plan + pool contention
```

---

## Capstone Services Used

| Service | Performance concern |
|---|---|
| ScaleForge redirect | Latency under high concurrency — pool exhaustion, event loop lag |
| ScaleForge URL create | Write throughput — Postgres INSERT + Redis SET atomicity |
| FlowForge notification worker | Worker concurrency — CPU vs I/O bound, job processing rate |
| Shared Redis | Connection reuse — pipeline vs individual commands |

---

## Prerequisites

- Module 05 (Caching) — understanding of multi-tier cache
- Module 06 (Asynchronism) — BullMQ worker concurrency model
- Module 07 (Microservices) — Prometheus metrics setup from `06-observability.md`

---

## Stack Additions This Module

```bash
npm install autocannon          # HTTP load testing
npm install 0x                  # Flame graph profiler for Node.js
npm install clinic              # Clinic.js profiling suite
```

Docker additions:
```yaml
# Grafana + Prometheus compose snippet
grafana:
  image: grafana/grafana:10-ubuntu
  ports: ["3000:3000"]

prometheus:
  image: prom/prometheus:v2.48.0
  volumes:
    - ./prometheus.yml:/etc/prometheus/prometheus.yml
  ports: ["9090:9090"]
```
