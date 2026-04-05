# Module 11 — Capstone Integration

## What You'll Learn

This module doesn't introduce new concepts. It applies every pattern from Modules 01–10 to produce complete, production-ready implementations of ScaleForge (URL shortener) and FlowForge (notification dispatcher). Work through these files in order — each builds on the last and references the module where the pattern was first introduced.

---

## The Full Picture

You've built towards this throughout the course. Now everything assembles.

```
  ┌─────────────────────────────────────────────────────────────────────────────┐
  │                          ScaleForge Production Architecture                 │
  │                                                                             │
  │   Browser / Client                                                          │
  │        │                                                                    │
  │        ▼                                                                    │
  │   Nginx (Module 07)                                                         │
  │   • TLS termination                                                         │
  │   • Rate limiting: 100 req/s per IP, 20 req/s per user (Module 09.5)       │
  │   • Routing: /r/* → ScaleForge, /api/flow/* → FlowForge                   │
  │        │                                                                    │
  │        ▼                                                                    │
  │   ScaleForge API (Node.js / Express)                                        │
  │   • Request deadline middleware (Module 09.4)                               │
  │   • Auth middleware → X-User-Id header                                      │
  │   • Per-user rate limit in Redis (Module 09.5)                              │
  │        │                                                                    │
  │        ├─── GET /r/:code ──────────────────────────────────────────────┐   │
  │        │    Tier 0: Redis cache (<1ms)         (Module 05)             │   │
  │        │    Tier 1: Postgres primary (5ms)     (Module 04)             │   │
  │        │    Tier 2: Read replica (10ms)        (Module 10.3)           │   │
  │        │    Tier 3: In-memory hot cache        (Module 10.3)           │   │
  │        │                                                                │   │
  │        ├─── POST /api/v1/urls ──────────────────────────────────────┐  │   │
  │        │    Validate (Zod)                                           │  │   │
  │        │    Write to Postgres writePool                              │  │   │
  │        │    Set Redis cache                                          │  │   │
  │        │    Enqueue FlowForge notification (circuit breaker)         │  │   │
  │        │    Saga compensation on failure (Module 07.5)               │  │   │
  │        │                                                             │  │   │
  │        └─── GET /metrics ──────────────────────────────────────────┘  │   │
  │             prom-client registry (Module 08.4)          (scrape)  302 │   │
  │                                                                      ▼ │   │
  │   ┌──────────────────────────────────────────────────────────────┐    │   │
  │   │  Redis Cluster                         (Modules 05, 06, 09)  │    │   │
  │   │  • URL redirect cache (key: url:<code>)                      │    │   │
  │   │  • BullMQ backing store (queues: email, webhook, analytics)  │    │   │
  │   │  • Rate limit sliding windows (key: ratelimit:user:<id>)     │    │   │
  │   └──────────────────────────────────────────────────────────────┘    │   │
  │                                                                         │   │
  │   ┌──────────────────────────────────────────────────────────────┐    │   │
  │   │  Postgres (primary + read replica)           (Module 04)     │    │   │
  │   │  • urls table (id, short_code, long_url, user_id, ...)       │    │   │
  │   │  • analytics table (url_id, clicked_at, ip_country, ...)    │    │   │
  │   └──────────────────────────────────────────────────────────────┘    │   │
  │                                                                         │   │
  │   ┌──────────────────────────────────────────────────────────────┐    │   │
  │   │  FlowForge (notification service)       (Modules 07, 09)     │◄───┘   │
  │   │  • POST /api/v1/notifications                                │        │
  │   │  • BullMQ workers: email, webhook, analytics                 │        │
  │   │  • Graceful degradation to queue-only (Module 10.3)         │        │
  │   └──────────────────────────────────────────────────────────────┘        │
  │                                                                             │
  │   ┌──────────────────────────────────────────────────────────────┐        │
  │   │  Prometheus + Grafana                    (Module 08)          │        │
  │   │  • Scrapes /metrics from ScaleForge + FlowForge              │        │
  │   │  • SLO alerting (Module 10)                                  │        │
  │   │  • Error budget burn rate alerts (Module 10.2)               │        │
  │   └──────────────────────────────────────────────────────────────┘        │
  └─────────────────────────────────────────────────────────────────────────────┘
```

---

## Module Topics

| # | Topic | Purpose |
|---|---|---|
| 01 | ScaleForge: full source walkthrough | Every pattern applied, annotated |
| 02 | FlowForge: full source walkthrough | Queue, notification dispatch, retry |
| 03 | docker-compose.yml: full stack | Run everything with one command |
| 04 | Production readiness checklist | What's left before deploying |
| Exercises | end-to-end integration tests | Prove the whole system works together |

---

## Prerequisites

Before starting this module, you should have completed:

- Module 05 (Caching) — Redis, cache invalidation
- Module 06 (Asynchronism) — BullMQ, worker patterns
- Module 07 (Microservices) — service boundaries, sagas
- Module 08 (Performance) — profiling, pools, Prometheus
- Module 09 (Cloud Patterns) — circuit breaker, retry, bulkhead, timeout, rate limiting
- Module 10 (Reliability) — SLOs, error budgets, graceful degradation

---

## Learning Objectives

By the end of this module, you should be able to:

1. Trace a single URL redirect request through every layer of ScaleForge — from the client's HTTP request to the final 302 response — and identify where each pattern applies.
2. Explain what happens when FlowForge is unavailable during URL creation, and trace the full saga compensation flow.
3. Run the full stack locally with `docker compose up` and reproduce at least one degradation scenario from Module 10.
4. Identify which production concerns are not addressed by this codebase (honest enumeration of what's left out).
