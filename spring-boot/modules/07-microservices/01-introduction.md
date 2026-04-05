# 01 — Microservices Introduction

## Monolith vs Microservices

TaskForge is built as a **monolith** — one deployable unit with all features. That's the right starting point. But it's worth understanding when microservices make sense.

```
Monolith:                          Microservices:
┌──────────────────────┐           ┌──────────┐  ┌──────────┐  ┌──────────┐
│     TaskForge        │           │   Auth   │  │ Projects │  │  Tasks   │
│  ┌────┐ ┌────┐       │           │ Service  │  │ Service  │  │ Service  │
│  │Auth│ │Proj│ ┌────┐│           └────┬─────┘  └────┬─────┘  └────┬─────┘
│  └────┘ └────┘ │Task││                │              │              │
│  ┌────┐ ┌────┐ └────┘│                └──────────────┴──────────────┘
│  │Repo│ │Test│       │                           Message Bus
│  └────┘ └────┘       │
└──────────────────────┘
         │
    One deployable
       artifact
```

**Analogy (Node.js):** Moving from a single Express app to separate services each with their own `node_modules`, Docker containers, and databases.

---

## When to Split

| Signal | Meaning |
|--------|---------|
| Team > 8 engineers | Conway's Law — separate services = separate teams |
| Independent scaling needs | Task search needs 10x more CPU than Auth |
| Independent deployment needed | Feature releases blocked by unrelated teams |
| Different tech stacks | Auth in Go, ML in Python, API in Java |
| Different SLAs | 99.999% for Auth, 99.9% for reporting |

**Don't split prematurely.** The monolith lets you discover the right boundaries first.

---

## Spring Cloud Overview

Spring Cloud is a collection of tools for building distributed systems on top of Spring Boot:

| Component | Purpose |
|-----------|---------|
| **Spring Cloud Gateway** | API Gateway — single entry point, routing, rate limiting |
| **Spring Cloud Config** | Centralized configuration server |
| **Netflix Eureka** | Service registry and discovery |
| **OpenFeign** | Declarative HTTP client for service-to-service calls |
| **Resilience4j** | Circuit breaker, retry, rate limiter |
| **Micrometer + Zipkin** | Distributed tracing |

---

## Dependency: Spring Cloud BOM

When using Spring Cloud, add the BOM to manage versions:

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-dependencies</artifactId>
            <version>2023.0.0</version>    <!-- compatible with Spring Boot 3.2.x -->
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

Then add individual starters without versions:
```xml
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-gateway</artifactId>
</dependency>
```

---

## Hypothetical TaskForge Decomposition

If TaskForge were split into microservices:

```
Client
  │
  ▼
API Gateway (port 8080)
  ├─► Auth Service (port 8081)       — JWT issuance and validation
  ├─► Project Service (port 8082)    — projects and membership
  ├─► Task Service (port 8083)       — tasks and assignments
  └─► Notification Service (port 8084) — email/in-app notifications
       └─► Kafka: task-assigned, comment-added events

Shared:
  ├─► Config Server (port 8888)      — YAML configs in Git
  ├─► Eureka Server (port 8761)      — service registry
  └─► Zipkin (port 9411)             — distributed tracing
```

---

## Next

[02 — Spring Cloud Gateway](./02-gateway.md) — route traffic and enforce cross-cutting concerns.
