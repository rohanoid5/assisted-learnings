# Module 3: Spring MVC — Building REST APIs

## Overview

This is where you build things you can actually call. Spring MVC is the web layer of Spring Boot — it handles incoming HTTP requests, routes them to the right handler, validates input, and returns structured responses.

If you're coming from Express, this will feel the most familiar. The concepts map almost 1:1 — the key difference is that Spring MVC uses annotations instead of function registration, and strong typing replaces `req.body.whatever`.

---

## Learning Objectives

- [ ] Understand what the **Servlet container** is and how it fits into Spring MVC
- [ ] Explain the **DispatcherServlet** request lifecycle (the Spring equivalent of Express's routing pipeline)
- [ ] Build **REST controllers** using `@RestController`, `@GetMapping`, `@PostMapping`, etc.
- [ ] Handle **path variables** and **query parameters**
- [ ] Validate **request bodies** with Bean Validation (`@Valid`, `@NotNull`, `@Size`, …)
- [ ] Write **global exception handling** with `@ControllerAdvice`
- [ ] Use **DTOs** to decouple your API contract from your domain model
- [ ] Return proper **HTTP status codes** with `ResponseEntity`

---

## Topics

| # | File | Concept |
|---|------|---------|
| 1 | [01-servlet-basics.md](01-servlet-basics.md) | What a Servlet is, how Tomcat invokes Spring MVC |
| 2 | [02-architecture.md](02-architecture.md) | DispatcherServlet request lifecycle, MVC components |
| 3 | [03-components.md](03-components.md) | Controllers, Services, Repositories — the layered pattern |
| 4 | [04-rest-controllers.md](04-rest-controllers.md) | Building REST endpoints, validation, DTOs, error handling |

---

## Estimated Time

**3–5 hours** (including exercises)

---

## Prerequisites

- [Module 1 — Introduction](../01-introduction/) completed
- [Module 2 — Spring Boot Core](../02-spring-boot-core/) completed

---

## Capstone Milestone

By the end of this module TaskForge will have a **fully working REST API** (in-memory, no database yet):

- `POST /api/auth/register` — register a new user
- `GET/POST/PUT/DELETE /api/projects` — project CRUD
- `GET/POST/PUT/DELETE /api/tasks` — task CRUD
- `GET/POST/DELETE /api/comments` — comment CRUD
- Global error handler returning consistent JSON error responses
- Request validation on all input DTOs

See [exercises/README.md](exercises/README.md) for the step-by-step capstone tasks.
