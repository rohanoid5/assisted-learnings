# Module 2: Spring Boot Core

## Overview

Module 1 gave you the conceptual foundations. Now we look at what makes Spring **Boot** special — the things that differentiate it from plain Spring Framework and make it the productivity powerhouse it is.

Spring Boot is built on three pillars: **opinionated starters**, **autoconfiguration**, and an **embedded server**. Together they eliminate the XML config nightmare that plagued earlier Spring and let you go from zero to running HTTP server with a single `main()` method.

---

## Learning Objectives

- [ ] Understand what a **Spring Boot Starter** is and how to pick the right ones for your project
- [ ] Explain how **Autoconfiguration** works under the hood (`@Conditional` beans)
- [ ] Understand the **Embedded Server** (Tomcat/Jetty/Undertow) and how it compares to Node's built-in HTTP server
- [ ] Use **Spring Boot Actuator** to expose health, metrics, and info endpoints
- [ ] Write a **custom health indicator**

---

## Topics

| # | File | Concept |
|---|------|---------|
| 1 | [01-spring-boot-starters.md](01-spring-boot-starters.md) | Starter dependencies — the opinionated meta-packages |
| 2 | [02-autoconfiguration.md](02-autoconfiguration.md) | How Spring Boot wires itself up automatically |
| 3 | [03-embedded-server.md](03-embedded-server.md) | Tomcat inside the JAR — no external server setup |
| 4 | [04-actuators.md](04-actuators.md) | Built-in operational endpoints for health, metrics, info |

---

## Estimated Time

**2–3 hours** (including exercises)

---

## Prerequisites

- [Module 1 — Introduction](../01-introduction/) completed

---

## Capstone Milestone

By the end of this module you will add **operational visibility** to TaskForge:

- Configure Actuator with `/health`, `/info`, and `/metrics` endpoints
- Write a custom `HealthIndicator` that checks database connectivity
- Add build info to the `/info` endpoint
- Understand the `spring-boot-starter-*` dependencies in TaskForge's `pom.xml`

See [exercises/README.md](exercises/README.md) for the step-by-step capstone tasks.
