# FlowForge — Distributed Notification & Event Processing

FlowForge is the advanced capstone project for the System Design tutorial. It is a **multi-channel notification routing platform** — an event-driven system that routes producer events to subscriber channels (email, SMS, webhook, push) with guaranteed delivery semantics. Built across Modules 06–11.

---

## What You're Building

A system that:
- Accepts events from producers via REST API with API key authentication
- Routes events to matching subscribers based on event type filters
- Delivers notifications via multiple channels (webhook, stub email/SMS/push)
- Retries failed deliveries with exponential backoff
- Provides delivery receipts and audit logs
- Scales delivery workers horizontally via competing consumers

---

## Setup

```bash
# 1. Copy environment config
cp .env.example .env

# 2. Start all infrastructure (PostgreSQL, Redis, RabbitMQ)
docker compose up -d

# 3. Install dependencies
npm install

# 4. Run database migrations
npm run db:migrate

# 5. Start the event service (producer API)
npm run dev:event-service

# 6. In another terminal, start the delivery worker
npm run dev:delivery-worker
```

Verify the service is running:
```bash
curl http://localhost:3002/health
# → {"status":"ok","version":"0.1.0","services":{"db":"up","redis":"up","queue":"up"}}
```

---

## Architecture (Final State — Module 11)

```
  Producer                  FlowForge Core
  ─────────                 ─────────────────────────────────────────────
  curl/API ──POST /events──▶ Event Service (port 3002)
                                    │
                              ┌─────▼──────┐
                              │  BullMQ /  │    ← Event queue
                              │  RabbitMQ  │
                              └─────┬──────┘
                                    │
              ┌─────────────────────┼──────────────────────┐
              │                     │                      │
       ┌──────▼──────┐      ┌───────▼──────┐      ┌───────▼──────┐
       │  Delivery   │      │  Delivery    │      │  Delivery    │   ← Competing Consumers
       │  Worker #1  │      │  Worker #2   │      │  Worker #3   │   (Module 09)
       └──────┬──────┘      └───────┬──────┘      └───────┬──────┘
              └──────────────────────┴──────────────────────┘
                                    │
                         ┌──────────▼──────────┐
                         │  Channel Dispatchers │
                         │  ┌────────────────┐ │
                         │  │ Webhook        │ │  ← Circuit breaker (Module 10)
                         │  │ Email (stub)   │ │
                         │  │ SMS (stub)     │ │
                         │  └────────────────┘ │
                         └──────────┬──────────┘
                                    │
                            ┌───────▼──────┐
                            │  PostgreSQL  │   ← Delivery + DeliveryLog
                            └──────────────┘
```

---

## Module-by-Module Milestones

| Module | What to Build |
|--------|---------------|
| 06 | Bootstrap: `event-service` API, BullMQ queue, delivery worker skeleton |
| 07 | Split into separate services, add API gateway, service health checks |
| 09 | Pub/Sub routing via channel subscriptions, Competing Consumers pattern |
| 10 | Circuit breaker for webhook delivery, exponential backoff, dead-letter queue |
| 11 | Full Docker Compose orchestration, delivery audit, architecture review |

---

## Project Structure

```
flowforge/
├── package.json
├── tsconfig.json
├── docker-compose.yml
├── .env.example
├── src/
│   ├── event-service/
│   │   ├── server.ts               ← Express app (producer REST API)
│   │   ├── routes/
│   │   │   ├── events.routes.ts    ← POST /events
│   │   │   └── subscriptions.routes.ts
│   │   └── services/
│   │       └── event.service.ts
│   ├── delivery-worker/
│   │   ├── worker.ts               ← BullMQ worker entry point
│   │   └── dispatchers/
│   │       ├── webhook.dispatcher.ts
│   │       ├── email.dispatcher.ts
│   │       └── sms.dispatcher.ts
│   ├── shared/
│   │   ├── db/
│   │   │   ├── schema.prisma
│   │   │   └── client.ts
│   │   ├── queue/
│   │   │   └── queue.client.ts     ← BullMQ queue setup
│   │   └── resilience/
│   │       ├── circuit-breaker.ts
│   │       └── retry.ts
└── tests/
    ├── unit/
    └── integration/
```
