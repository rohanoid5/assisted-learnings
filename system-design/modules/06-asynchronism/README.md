# Module 06 — Asynchronism

> **FlowForge introduced here.** This module builds the notification delivery pipeline that runs alongside ScaleForge. All async patterns are implemented through FlowForge's job queue architecture.

## What You'll Learn

| Topic | Key Idea |
|---|---|
| 6.1 Message Queues | Decouple producers from consumers |
| 6.2 BullMQ Deep Dive | Redis-backed jobs, retries, concurrency |
| 6.3 Pub/Sub Patterns | Fan-out to multiple subscribers |
| 6.4 Event Sourcing | Immutable event log as source of truth |
| 6.5 Backpressure & Flow Control | Don't let slow consumers crash the system |
| 6.6 Dead Letter Queues | Handle poison messages gracefully |

---

## The Core Problem

```
  Synchronous request-response:
  
    User ──► POST /send-notification ──► Send email ──► Wait 800ms ──► 200 OK
    
    Problems:
      1. User waits 800ms for a non-critical operation
      2. Email provider is down → 500 error → user sees failure
      3. 10k notifications/min → email API rate limited → all 500
      4. Sending process crashes → notification lost (no retry)
  
  Asynchronous with queue:
  
    User ──► POST /send-notification ──► Enqueue job ──► 5ms ──► 202 Accepted
    
    Background worker:
      Queue ──► Dequeue job ──► Send email ──► Retry on failure
      
    Benefits:
      1. User gets instant acknowledgment
      2. Email provider down → job stays in queue → retried in 30s
      3. Rate limited → backoff strategy → processed when limit lifts
      4. Worker crash → job returns to queue → processed by next worker
```

---

## FlowForge Architecture

```
  ┌─────────────────────────────────────────────────────────┐
  │                      FlowForge                          │
  │                                                         │
  │  HTTP API                                               │
  │  POST /notifications ──► BullMQ                         │
  │                          notification-queue             │
  │                              │                          │
  │                    ┌─────────┴─────────┐                │
  │                    ▼                   ▼                │
  │              Email Worker        Webhook Worker         │
  │              (MailHog SMTP)      (HTTP POST)            │
  │                    │                   │                │
  │              Dead Letter         Dead Letter            │
  │              Queue               Queue                  │
  │                    │                                    │
  │              Postgres (audit log)                       │
  │                                                         │
  │  Components:                                            │
  │    ioredis — BullMQ backend                             │
  │    BullMQ  — job queue + worker orchestration           │
  │    nodemailer — email delivery via MailHog              │
  │    Axios  — webhook HTTP delivery                       │
  └─────────────────────────────────────────────────────────┘
```

---

## Capstone Milestones

| Milestone | After Module |
|---|---|
| Basic queue: enqueue + process | 6.2 |
| Pub/Sub fan-out (email + webhook) | 6.3 |
| Event sourcing audit log | 6.4 |
| Dead letter queue + alerting | 6.6 |

---

## How to Run FlowForge

```bash
cd capstone/flowforge

# Install dependencies (already done during capstone setup)
npm install

# Start workers + API
npm run dev

# Monitor BullMQ dashboard (Bull Board)
# http://localhost:3002/admin/queues

# View email delivery via MailHog
# http://localhost:8025
```
