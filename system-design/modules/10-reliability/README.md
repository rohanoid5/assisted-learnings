# Module 10 — Reliability Engineering

## What You'll Learn

Every distributed system fails. Reliability engineering isn't about preventing failures — it's about knowing your failure budget, designing for graceful degradation so partial failures don't become total outages, and building confidence through deliberate chaos.

This module covers the language of reliability (SLO, SLA, SLI, error budgets), the three-tier degradation hierarchy that applies to both ScaleForge and FlowForge, and the principles behind chaos engineering.

---

## Why This Module Matters

Modern systems run on the premise that something will always be broken. A Redis node flaps. A downstream returns 504s for 90 seconds. A deploy causes a brief database connection storm. The question isn't "will this happen?" but "what does the system do when it happens?"

```
  Without reliability design:
    Redis fails → redirect fails → 503 to user
  
  With reliability design:
    Redis fails → read from DB → add to cache → redirect succeeds
    (user notices nothing; on-call sees a Redis alert, not a user complaint)
```

---

## Module Topics

| # | Topic | Core Idea |
|---|---|---|
| 01 | SLOs, SLAs, SLIs | Defining what "reliable" means numerically |
| 02 | Error Budgets | Reliability as a finite resource to spend deliberately |
| 03 | Graceful Degradation | Tiered fallbacks so partial failures don't become full outages |
| 04 | Chaos Engineering | Proving your reliability claims with controlled experiments |
| Exercises | — | SLO definition, error budget burn rate, degradation walkthrough |

---

## Capstone Context

**ScaleForge's reliability targets:**

| Endpoint | SLO | Error budget (30 days) |
|---|---|---|
| `GET /r/:code` (redirect) | 99.9% availability, p99 < 50ms | 43.2 min downtime |
| `POST /api/v1/urls` | 99.5% availability, p99 < 500ms | 216 min downtime |
| `GET /api/v1/stats/:code` | 99.0% availability, p99 < 2s | 432 min downtime |

**FlowForge's reliability targets:**

| Endpoint | SLO | Measurement |
|---|---|---|
| Notification delivery | 99.5% delivered within 5 minutes | BullMQ job success rate |
| API availability | 99.9% | HTTP error rate |

---

## Concepts Preview

```
  SLI → what you measure (e.g., error rate)
   │
   ▼
  SLO → what you promise to yourself (e.g., error rate < 0.1% over 30 days)
   │
   ▼
  SLA → what you promise to your customer (e.g., 99.9% or credit issued)
   │
   ▼
  Error Budget → 1 − SLO = budget to spend on risk and deployment
  
  Spending the error budget:
    Planned: canary deploys, chaos experiments, risky migrations
    Unplanned: incidents, unexpected failures
  
  If budget runs out:
    Freeze feature work
    Focus 100% on reliability improvements
```
