# ScaleForge — Distributed URL Shortener & Analytics

ScaleForge is the primary capstone project for the System Design tutorial. It is a **production-grade URL shortening service with click analytics** — built incrementally as you progress through Modules 01–11.

---

## What You're Building

A system that:
- Shortens long URLs to compact codes (`https://sf.io/abc123`)
- Redirects visitors at high throughput with minimal latency
- Tracks every click (country, device, timestamp) asynchronously
- Generates traffic reports per URL
- Scales horizontally behind a load balancer
- Handles 100M+ redirects/day at p99 < 20ms

---

## Setup

```bash
# 1. Copy environment config
cp .env.example .env

# 2. Start all infrastructure (PostgreSQL, Redis, BullMQ dashboard, etc.)
docker compose up -d

# 3. Install dependencies
npm install

# 4. Run database migrations
npm run db:migrate

# 5. Start the development server
npm run dev
```

Verify the service is running:
```bash
curl http://localhost:3001/health
# → {"status":"ok","version":"0.1.0","timestamp":"..."}
```

---

## Architecture (Final State — Module 11)

```
          ┌──────────────┐
          │    Client    │
          └──────┬───────┘
                 │ HTTP
        ┌────────▼────────┐
        │   Nginx (LB)    │  ← Added in Module 03
        └─┬──────┬──────┬─┘
          │      │      │
    ┌─────▼──┐ ┌─▼──┐ ┌─▼──┐
    │  URL   │ │URL │ │URL │   ← 3×  url-service replicas
    │Service │ │Svc │ │Svc │
    └────┬───┘ └────┘ └────┘
         │                        ┌─────────────────┐
    ┌────▼────┐   BullMQ queue     │ Analytics       │
    │  Redis  │ ←─────────────────▶│ Service         │  ← Module 07
    │  Cache  │                    └───────┬─────────┘
    └─────────┘                            │
                                   ┌───────▼──────┐
    ┌──────────────┐               │  PostgreSQL  │
    │  PostgreSQL  │               │  (read DB)   │  ← CQRS read replica, Module 09
    │  (write DB)  │               └──────────────┘
    └──────────────┘
         │
    ┌────▼────────────────────┐
    │  Prometheus + Grafana   │  ← Module 08
    └─────────────────────────┘
```

---

## Module-by-Module Milestones

| Module | Branch / Tag | What to Build |
|--------|-------------|---------------|
| 01 | `module-01` | `docs/architecture.md` — requirements, estimates, sketch |
| 02 | `module-02` | `src/routes/`, `src/services/url.service.ts` — REST API |
| 03 | `module-03` | `nginx/` config, Docker Compose with 3 replicas |
| 04 | `module-04` | `src/db/` — Prisma schema, migrations, Redis client |
| 05 | `module-05` | `src/cache/` — Cache-aside layer, HTTP headers |
| 06 | `module-06` | `src/workers/` — BullMQ click-tracking worker |
| 07 | `module-07` | `src/gateway/` — Separate url-service + analytics-service |
| 08 | `module-08` | `src/telemetry/` — OTel traces, Prometheus metrics |
| 09 | `module-09` | `src/read-models/` — CQRS projections |
| 10 | `module-10` | `src/resilience/` — Circuit breaker, rate limiter |
| 11 | `module-11` | Full Docker Compose, production hardening |

---

## Project Structure

```
scaleforge/
├── package.json
├── tsconfig.json
├── docker-compose.yml
├── .env.example
├── nginx/
│   └── nginx.conf                  ← Module 03
├── docs/
│   └── architecture.md             ← Module 01
├── src/
│   ├── server.ts                   ← Express app entry point
│   ├── config.ts                   ← Environment config (zod-validated)
│   ├── routes/
│   │   ├── url.routes.ts           ← POST /shorten, GET /:code
│   │   └── stats.routes.ts         ← GET /stats/:code
│   ├── services/
│   │   ├── url.service.ts          ← URL creation & lookup logic
│   │   └── analytics.service.ts    ← Click recording & reporting
│   ├── cache/
│   │   └── redis.client.ts         ← Redis connection + cache-aside helpers
│   ├── db/
│   │   ├── schema.prisma           ← Prisma schema
│   │   └── client.ts               ← Prisma client singleton
│   ├── workers/
│   │   └── click-tracker.worker.ts ← BullMQ worker for async click analytics
│   ├── resilience/
│   │   ├── circuit-breaker.ts      ← Circuit breaker implementation
│   │   └── rate-limiter.ts         ← Sliding-window rate limiter
│   ├── telemetry/
│   │   └── otel.ts                 ← OpenTelemetry setup
│   └── gateway/
│       └── proxy.ts                ← API gateway (Module 07)
└── tests/
    ├── unit/
    ├── integration/
    └── e2e/
```
