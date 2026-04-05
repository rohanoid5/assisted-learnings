# Module 6 — Networking & HTTP

## Overview

Node.js was built for networking. This module goes deep on how HTTP works under the hood, how to build a production-grade Express.js API (routing, middleware, authentication, rate limiting), how WebSockets enable real-time features, and how to make reliable outbound HTTP requests. You'll understand what happens at the socket level, not just at the framework level.

---

## Learning Objectives

By the end of this module you will be able to:

- [ ] Explain TCP connections, sockets, and the HTTP request/response lifecycle at the socket level
- [ ] Build a production-grade Express.js API with security middleware, authentication, and rate limiting
- [ ] Design RESTful routes following industry conventions (versioning, HATEOAS links, pagination)
- [ ] Implement **WebSockets** with the `ws` library for real-time job progress updates
- [ ] Make reliable outbound HTTP requests with retry, timeout, and circuit-breaker patterns
- [ ] Apply **security headers** (CORS, Helmet, CSP, CSRF) correctly

---

## Topics

| # | File | Concept |
|---|------|---------|
| 1 | [01-tcp-basics.md](01-tcp-basics.md) | TCP, sockets, HTTP/1.1 lifecycle, keep-alive |
| 2 | [02-http-module.md](02-http-module.md) | node:http internals, raw request/response |
| 3 | [03-express-deep-dive.md](03-express-deep-dive.md) | Express middleware chain, routing, security |
| 4 | [04-rest-api-design.md](04-rest-api-design.md) | REST conventions, pagination, versioning |
| 5 | [05-websockets.md](05-websockets.md) | ws library, broadcast, rooms, auth |
| 6 | [06-http-client.md](06-http-client.md) | fetch, retry, timeout, circuit breaker |

---

## Estimated Time

**6–8 hours** (including exercises)

---

## Prerequisites

- Module 03 — Error Handling (Express error middleware)
- Module 04 — Streams & Buffers (streaming responses)
- Module 05 — File System & CLI (env config)

---

## Capstone Milestone

By the end of this module you will have:

- A complete PipeForge REST API (`/api/v1/pipelines`, `/api/v1/jobs`, `/api/v1/users`)
- JWT authentication middleware and role-based access control
- A WebSocket endpoint that broadcasts real-time job progress to connected clients
- A rate limiter on the trigger endpoint (`POST /api/v1/jobs`)

See [exercises/README.md](exercises/README.md) for the step-by-step tasks.
