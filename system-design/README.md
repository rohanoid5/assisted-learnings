# System Design Interactive Tutorial

A hands-on, modular system design learning guide for backend developers who want to master distributed systems, scalability patterns, and architecture fundamentals. Every concept is grounded in practical **TypeScript + Docker** demos — not just theory.

---

## How to Use This Tutorial

1. Work through modules **in order** — each builds on the previous.
2. After each concept, **apply it to the capstone projects** using the _Capstone Connection_ section at the bottom of every topic file.
3. Complete the **exercises** before moving to the next module.
4. The `capstone/` folder holds two working projects — built incrementally.

> System design is a skill learned by doing. Read the theory, then immediately apply it to real infrastructure.

---

## Prerequisites

| Requirement | Version | Notes |
|-------------|---------|-------|
| Node.js | 20+ | LTS; use [nvm](https://github.com/nvm-sh/nvm) to manage versions |
| TypeScript | 5+ | `npm install -g typescript` |
| Docker | Latest | Required for all infrastructure demos (Nginx, Redis, PostgreSQL, RabbitMQ, Prometheus) |
| PostgreSQL | 15+ | Provided via Docker in capstone projects |
| Redis | 7+ | Provided via Docker in capstone projects |
| VS Code | Latest | With REST Client extension for API testing |

> **Assumed knowledge:** TypeScript fundamentals, async/await, basic REST APIs (Express or similar). If you can build a CRUD API, you're ready.

---

## Learning Path

| Module | Topic | Est. Time | Capstone Milestone |
|--------|-------|-----------|-------------------|
| [01 — Foundations](modules/01-foundations/) | Design approach, scalability, latency, availability, CAP, consistency | 4–5 hrs | ScaleForge requirements & architecture sketch |
| [02 — Networking & Communication](modules/02-networking-communication/) | DNS, HTTP/TCP/UDP, REST, gRPC, GraphQL, idempotency | 4–5 hrs | ScaleForge REST API skeleton with idempotency |
| [03 — Load Balancing & CDN](modules/03-load-balancing-cdn/) | LB algorithms, L4/L7, horizontal scaling, CDN | 4–5 hrs | ScaleForge behind Nginx with 3 replicas |
| [04 — Databases & Storage](modules/04-databases-storage/) | SQL/NoSQL, key-value, replication, sharding, consistent hashing | 6–8 hrs | ScaleForge with PostgreSQL + Redis |
| [05 — Caching](modules/05-caching/) | Cache strategies, distributed caching, invalidation, thundering herd | 4–5 hrs | ScaleForge multi-layer caching |
| [06 — Asynchronism](modules/06-asynchronism/) | Message queues, task queues, back pressure, event-driven, scheduling | 4–5 hrs | ScaleForge async analytics; FlowForge bootstrap |
| [07 — Microservices & Architecture](modules/07-microservices-architecture/) | Decomposition, service discovery, API gateway, failover | 4–5 hrs | ScaleForge split into 2 services |
| [08 — Performance & Monitoring](modules/08-performance-monitoring/) | Antipatterns, instrumentation, OpenTelemetry, Grafana | 5–6 hrs | Full observability stack for ScaleForge |
| [09 — Cloud Design Patterns](modules/09-cloud-design-patterns/) | CQRS, event sourcing, messaging patterns, gateway patterns | 5–7 hrs | ScaleForge CQRS analytics; FlowForge pub/sub |
| [10 — Reliability Patterns](modules/10-reliability-patterns/) | Circuit breaker, bulkhead, retry, leader election, throttling | 4–5 hrs | Both capstones hardened for production |
| [11 — Capstone Integration](modules/11-capstone-integration/) | Architecture reviews, production readiness, interview practice | 4–5 hrs | Both capstones production-ready |

**Total estimated time: 52–66 hours**

---

## Capstone Projects

### ScaleForge — Distributed URL Shortener & Analytics (Primary)

ScaleForge is a **production-grade URL shortening service with analytics** — a platform for creating short links, tracking clicks with metadata, and generating traffic reports. It exercises every core system design concept across Modules 01–11.

```
User ──creates──▶ ShortURL ──receives──▶ Click ──aggregates──▶ Report
                     │
                     └── code (e.g. "abc123"), longUrl, expiresAt, clickCount
                                                │
                                     Click: ip, userAgent, country, device, timestamp
```

#### ScaleForge Domain Entities

| Entity | Key Fields |
|--------|-----------|
| `User` | id, email, passwordHash, tier (`FREE`/`PRO`), createdAt |
| `ShortURL` | id, code, longUrl, userId, expiresAt, clickCount, createdAt |
| `Click` | id, shortUrlId, ipAddress, userAgent, country, city, device, timestamp |
| `Report` | id, shortUrlId, period, totalClicks, uniqueVisitors, topCountries, createdAt |

#### What Gets Built Module-by-Module

| Module | What Gets Added to ScaleForge |
|--------|-------------------------------|
| 01 | Requirements doc, capacity estimation, high-level architecture sketch |
| 02 | Express REST API (`POST /shorten`, `GET /:code`, `GET /stats/:code`) |
| 03 | Nginx reverse proxy, 3-instance Docker Compose, health checks |
| 04 | PostgreSQL schema (URLs + clicks), Redis for `code→URL` lookup cache |
| 05 | Multi-layer caching (Redis cache-aside + HTTP cache headers for CDN) |
| 06 | BullMQ async click tracking (decouple redirect path from analytics writes) |
| 07 | Split into `url-service` + `analytics-service` with Express API gateway |
| 08 | OpenTelemetry traces, Prometheus metrics, Grafana dashboard |
| 09 | CQRS for analytics (separate write model vs. read projections) |
| 10 | Circuit breaker, per-tier rate limiting (throttling), retry with backoff |
| 11 | Full Docker Compose orchestration, security hardening, production review |

---

### FlowForge — Distributed Notification & Event Processing (Advanced)

FlowForge is a **multi-channel notification routing system** — a platform for routing events to subscribers via configurable channels (email, SMS, webhook, push). It exercises advanced async, microservices, and reliability patterns introduced in Modules 06–11.

```
Producer ──emits──▶ Event ──routed-to──▶ Channel ──delivers-to──▶ Subscriber
                                               │
                                         Delivery ──logs──▶ DeliveryLog
                                         status: PENDING → IN_FLIGHT → DELIVERED / FAILED
```

#### FlowForge Domain Entities

| Entity | Key Fields |
|--------|-----------|
| `Producer` | id, name, apiKey, createdAt |
| `Event` | id, producerId, type, payload (JSON), timestamp |
| `Channel` | id, type (`EMAIL`/`SMS`/`WEBHOOK`/`PUSH`), config (JSON), createdAt |
| `Subscriber` | id, channelId, target (email/phone/url), filters (JSON), active |
| `Delivery` | id, eventId, subscriberId, status, attempts, nextRetryAt, deliveredAt |
| `DeliveryLog` | id, deliveryId, attempt, statusCode, responseBody, timestamp |

#### What Gets Built Module-by-Module

| Module | What Gets Added to FlowForge |
|--------|------------------------------|
| 06 | Bootstrap: event bus with BullMQ, producer API, consumer worker |
| 07 | Separate services: `event-service`, `delivery-worker`, `webhook-service` with API gateway |
| 09 | Publisher/Subscriber pattern; Competing Consumers for parallel delivery |
| 10 | Circuit breaker for webhook calls, exponential backoff retry, dead-letter queue |
| 11 | Full architecture review, retry audit, guaranteed delivery checklist |

---

## Project Structure

```
system-design/
├── README.md                               ← You are here
├── Roadmap.png                             ← Visual learning roadmap
├── .github/
│   └── prompts/
│       └── plan-systemDesign.prompt.md
├── modules/
│   ├── 01-foundations/
│   ├── 02-networking-communication/
│   ├── 03-load-balancing-cdn/
│   ├── 04-databases-storage/
│   ├── 05-caching/
│   ├── 06-asynchronism/
│   ├── 07-microservices-architecture/
│   ├── 08-performance-monitoring/
│   ├── 09-cloud-design-patterns/
│   ├── 10-reliability-patterns/
│   └── 11-capstone-integration/
└── capstone/
    ├── scaleforge/                         ← Primary capstone (Modules 01–11)
    └── flowforge/                          ← Advanced capstone (Modules 06–11)
```

---

## Quick Start

### Start ScaleForge infrastructure

```bash
cd capstone/scaleforge
cp .env.example .env
docker compose up -d
npm install
npm run dev
```

### Start FlowForge infrastructure

```bash
cd capstone/flowforge
cp .env.example .env
docker compose up -d
npm install
npm run dev
```

### Verify both are running

```bash
curl http://localhost:3001/health   # ScaleForge → {"status":"ok"}
curl http://localhost:3002/health   # FlowForge  → {"status":"ok"}
```
