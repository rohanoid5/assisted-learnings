# Module 07 — Microservices

> Both ScaleForge and FlowForge serve as concrete examples of microservices interacting across service boundaries.

## What You'll Learn

| Topic | Key Idea |
|---|---|
| 7.1 Monolith vs. Microservices | When to split, when to stay |
| 7.2 Service Communication | REST, gRPC, async messaging |
| 7.3 API Gateway Pattern | Single entry point for all clients |
| 7.4 Service Discovery | How services find each other |
| 7.5 Distributed Transactions | Saga pattern, 2PC tradeoffs |
| 7.6 Observability | Distributed tracing, correlation IDs |

---

## The Starting Point: A Monolith

```
  ScaleForge v1 (monolith):
  
  ┌─────────────────────────────────────────────────┐
  │  Single Node.js process                         │
  │                                                 │
  │  POST /api/v1/urls     → createUrl()            │
  │  GET  /:code           → redirect()             │
  │  POST /api/v1/notify   → sendNotification()     │
  │  GET  /admin/metrics   → getMetrics()           │
  │                                                 │
  │  +-- Database pool                              │
  │  +-- Redis client                               │
  │  +-- MailHog SMTP                               │
  └─────────────────────────────────────────────────┘
  
  Problems at scale:
    - One slow feature (notification sending) slows the whole app
    - Cannot scale URL redirect service independently from admin dashboard
    - Single deployment unit: notification bug = full outage
    - All teams must coordinate deployments
```

---

## The Microservices Architecture

```
  ┌─────────────────────────┐
  │    API Gateway          │ ← single external entry point
  │    (nginx + routing)    │   routes by path prefix
  └─────────┬───────────────┘
            │
  ┌─────────┼─────────────────────────────────────────┐
  │         │                                         │
  ▼         ▼                         ▼               ▼
┌────────┐ ┌──────────┐         ┌──────────┐  ┌────────────┐
│Redirect│ │  URL Mgmt │         │FlowForge │  │  Analytics │
│Service │ │  Service  │         │Notif Svc │  │  Service   │
│(3 pods)│ │(2 pods)   │         │(2 pods)  │  │(1 pod)     │
└────────┘ └──────────┘         └──────────┘  └────────────┘
    │           │                     │              │
    ▼           ▼                     ▼              ▼
  Redis     Postgres              Postgres         ClickHouse
  (cache)   (urls db)             (notif db)      (analytics)
  
  Each service:
    - Owns its own data store (database per service)
    - Deployed and scaled independently
    - Communicates via HTTP or message queue
    - Has its own retry/circuit breaker configuration
```

---

## Capstone Context

| Service | Responsibility | Database |
|---|---|---|
| ScaleForge Redirect | Handle GET /:code, record clicks | Redis (hot), Postgres (cold) |
| ScaleForge URL Mgmt | CRUD for URLs, user auth | Postgres |
| FlowForge | Enqueue and deliver notifications | Postgres (events), Redis (queue) |
| Analytics | Aggregate click data, dashboards | ClickHouse (or Postgres with partitions) |

---

## How to Run

```bash
# All services share one Docker Compose file
docker compose up -d

# Individual services
cd capstone/scaleforge && npm run dev        # port 3001
cd capstone/flowforge  && npm run dev        # port 3002
```
