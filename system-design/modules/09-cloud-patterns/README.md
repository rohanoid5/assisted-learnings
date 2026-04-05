# Module 09 — Cloud Design Patterns

> Build systems that degrade gracefully, not catastrophically.

---

## What This Module Covers

In distributed systems, downstream calls fail: network partitions, service restarts, resource exhaustion, slow dependencies. Cloud design patterns are the defensive programming toolkit that prevents one failing service from cascading into a full system outage. These patterns apply to every HTTP call ScaleForge makes to FlowForge and every Redis operation in the hot path.

| Topic | File |
|---|---|
| Circuit Breaker | [01-circuit-breaker.md](./01-circuit-breaker.md) |
| Retry with Jitter | [02-retry-jitter.md](./02-retry-jitter.md) |
| Bulkhead Pattern | [03-bulkhead.md](./03-bulkhead.md) |
| Timeout & Deadline Propagation | [04-timeouts.md](./04-timeouts.md) |
| Rate Limiting (Server-Side) | [05-rate-limiting.md](./05-rate-limiting.md) |
| Exercises | [exercises/README.md](./exercises/README.md) |

---

## The Failure Cascade Problem

```
  Without protective patterns:
  
    Client → ScaleForge → FlowForge → (FlowForge is slow, 500ms per call)
    
    Timeline:
    
    t=0: 200 concurrent requests arrive at ScaleForge
    Each ties up 1 connection waiting for FlowForge (500ms)
    t=500ms: All 200 connections are still waiting
    t=500ms: Request 201 arrives — no connections available
    ScaleForge: pool exhausted → 503
    
    FlowForge's slowness killed ScaleForge.
    
    That's a cascading failure. One slow service + no circuit breaker
    = the whole system goes down.
  
  ─────────────────────────────────────────────────────────

  With protective patterns:
  
    Circuit breaker + timeout on FlowForge calls:
    
    t=0: First FlowForge call takes 500ms → timeout at 200ms → error
    After 5 failures in 10s: circuit OPENS
    
    t=>>10s: Circuit is open — ScaleForge does NOT call FlowForge
    URL creation still works (notification is enqueued for later)
    ScaleForge stays healthy
    
    t=>>30s: Circuit half-opens, probes FlowForge
    FlowForge recovered → circuit closes → notifications resume
```

---

## Capstone Services Used

| Pattern | ScaleForge usage | FlowForge usage |
|---|---|---|
| Circuit breaker | FlowForgeClient calls | External SMTP calls |
| Retry + jitter | Redis GET on transient errors | BullMQ job retries |
| Bulkhead | Separate pool for redirect vs write paths | Separate worker for each channel |
| Timeout | All external HTTP calls | SMTP send timeout |
| Rate limiting | Protect URL creation endpoint | Protect webhook delivery |

---

## Prerequisites

- Module 07 (Microservices) — service communication patterns
- Module 08 (Performance) — Prometheus metrics (patterns export their state as metrics)
