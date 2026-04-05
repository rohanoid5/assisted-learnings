# Module 2 — Networking & Communication

## Overview

Distributed systems communicate over networks. To design systems that perform well and fail gracefully, you need to understand **how data moves** — from HTTP semantics through TCP connection management to long-lived real-time connections. This module covers the protocols, patterns, and tradeoffs that govern system-to-system and client-to-server communication.

---

## Learning Objectives

By the end of this module, you will be able to:

- [ ] Explain why HTTP/2 improves on HTTP/1.1 for API-heavy systems
- [ ] Choose between REST, GraphQL, and gRPC for a given communication use case
- [ ] Describe how WebSockets and SSE differ and when to use each
- [ ] Implement a long-polling endpoint, a WebSocket handler, and an SSE stream
- [ ] Explain DNS resolution, CDN routing, and what "anycast" means
- [ ] Calculate the impact of connection pooling on PostgreSQL throughput limits
- [ ] Design ScaleForge's redirect with the correct HTTP status code, caching headers, and keep-alive strategy

---

## Topics

| # | File | Summary | Estimated Time |
|---|------|---------|---------------|
| 01 | [01-http-fundamentals.md](./01-http-fundamentals.md) | HTTP/1.1 vs HTTP/2 vs HTTP/3, methods, status codes, headers | 45 min |
| 02 | [02-rest-vs-graphql-vs-grpc.md](./02-rest-vs-graphql-vs-grpc.md) | API design paradigms, when to use each | 40 min |
| 03 | [03-websockets-and-sse.md](./03-websockets-and-sse.md) | Real-time communication: WebSockets, SSE, long polling | 45 min |
| 04 | [04-dns-and-routing.md](./04-dns-and-routing.md) | DNS resolution, TTLs, CDN routing, BGP anycast | 30 min |
| 05 | [05-tcp-connections-and-pooling.md](./05-tcp-connections-and-pooling.md) | TCP handshake, keep-alive, connection pooling | 35 min |
| 06 | [06-network-security-basics.md](./06-network-security-basics.md) | TLS, HTTPS, certificates, CORS, security headers | 35 min |
| — | [exercises/README.md](./exercises/README.md) | Hands-on exercises | 45 min |

**Estimated total time:** 4–5 hours

---

## Prerequisites

- [Module 01 — Foundations](../01-foundations/README.md) completed
- Familiarity with `fetch` or `axios` in TypeScript

---

## Capstone Milestone

By the end of this module, ScaleForge should have:

1. **Proper HTTP semantics** for the redirect endpoint: `302 Found` with correct `Location` header, `Cache-Control: no-store`, and a security header set
2. **Connection pooling** configured for PostgreSQL — calculate the optimal pool size for your hardware
3. **CORS configured** for the ScaleForge API to allow only trusted origins
4. **Stretch goal**: Implement an SSE endpoint at `/api/urls/:code/analytics/live` that streams click events in real time to the URL owner's dashboard
