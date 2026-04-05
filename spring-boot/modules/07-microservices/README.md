# Module 7: Microservices & Spring Cloud *(Advanced)*

## Overview

> **Prerequisites:** Complete Modules 1–6 and have a fully working, tested TaskForge monolith before starting this module.

This is the advanced module. You'll take the TaskForge monolith you've built and decompose it into a microservices architecture using **Spring Cloud** — the ecosystem of tools built on top of Spring Boot for distributed systems.

This module covers the same problems you'd solve with a Node.js microservices stack (Kong/nginx-based gateway, Consul for service discovery, opossum for circuit breaking) — but using Spring's integrated, opinionated toolkit.

---

## Learning Objectives

- [ ] Understand the **tradeoffs** of microservices vs monolith (and why a monolith is often better to start with)
- [ ] Route traffic with **Spring Cloud Gateway** (≈ nginx or Kong)
- [ ] Manage centralized configuration with **Spring Cloud Config Server**
- [ ] Implement **service discovery** with Eureka (≈ Consul)
- [ ] Make type-safe inter-service HTTP calls with **OpenFeign** (≈ axios with service registry integration)
- [ ] Handle downstream failures with **Circuit Breaker** via Resilience4j (≈ opossum)
- [ ] Expose application metrics with **Micrometer** + Prometheus

---

## Topics

| # | File | Concept |
|---|------|---------|
| 1 | [01-intro-microservices.md](01-intro-microservices.md) | Monolith vs microservices, decomposition strategy |
| 2 | [02-spring-cloud-gateway.md](02-spring-cloud-gateway.md) | API Gateway — single entry point, routing, filters |
| 3 | [03-cloud-config.md](03-cloud-config.md) | Centralized configuration for multiple services |
| 4 | [04-eureka-service-discovery.md](04-eureka-service-discovery.md) | Service registry and discovery |
| 5 | [05-open-feign.md](05-open-feign.md) | Declarative HTTP client for inter-service calls |
| 6 | [06-circuit-breaker.md](06-circuit-breaker.md) | Resilience4j circuit breaker pattern |
| 7 | [07-micrometer-observability.md](07-micrometer-observability.md) | Metrics, tracing, and observability |

---

## Estimated Time

**6–8 hours** (including exercises)

---

## Prerequisites

- [Modules 1–6](../01-introduction/) fully completed
- Docker installed and running (required for multi-service local setup)
- TaskForge monolith working and tested

---

## Capstone Milestone

By the end of this module you'll have a **TaskForge microservices architecture** (optional — the monolith is production-ready on its own):

```
                        ┌─────────────────────┐
                        │   Spring Cloud       │
                        │   Gateway (:8080)    │
                        └────────┬────────────┘
                                 │
           ┌─────────────────────┼──────────────────┐
           ▼                     ▼                   ▼
   ┌──────────────┐    ┌──────────────────┐  ┌──────────────┐
   │ user-service  │    │  project-service  │  │ task-service  │
   │   (:8081)    │    │     (:8082)       │  │   (:8083)    │
   └──────────────┘    └──────────────────┘  └──────────────┘
           │                     │                   │
           └─────────────────────┴───────────────────┘
                                 │
                        ┌────────▼────────┐
                        │  Eureka Server  │
                        │    (:8761)      │
                        └─────────────────┘
```

Docker Compose spins up all services with one command.

See [exercises/README.md](exercises/README.md) for the step-by-step capstone tasks.
