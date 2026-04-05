# Plan: System Design Interactive Tutorial

## TL;DR
Create a comprehensive, modular system design tutorial at `/system-design/` following the established pattern from the nodejs/spring-boot/postgres tutorials. Uses **TypeScript (Node.js)** for code examples with **Docker Compose** for infrastructure demos. Deep theory with architecture diagrams AND full runnable exercises for every topic. Two progressive capstone projects: **ScaleForge** (distributed URL shortener with analytics) and **FlowForge** (distributed notification/event processing system).

---

## Structure

```
system-design/
├── README.md
├── Roadmap.png                        ← existing
├── modules/
│   ├── 01-foundations/
│   ├── 02-networking-communication/
│   ├── 03-load-balancing-cdn/
│   ├── 04-databases-storage/
│   ├── 05-caching/
│   ├── 06-asynchronism/
│   ├── 07-microservices-architecture/
│   ├── 08-performance-monitoring/
│   ├── 09-cloud-design-patterns/
│   ├── 10-reliability-patterns/
│   └── 11-capstone-integration/
├── capstone/
│   ├── scaleforge/                    ← URL shortener + analytics
│   └── flowforge/                     ← Notification/event processing
```

---

## Capstone Projects

### ScaleForge — Distributed URL Shortener & Analytics (Primary)
**Domain model**: User → ShortURL → Click → Report
- Progressively built through Modules 01–08
- Covers: load balancing, caching (Redis), databases (PostgreSQL + Redis), CDN, monitoring, horizontal scaling

### FlowForge — Distributed Notification & Event Processing (Advanced)
**Domain model**: Producer → Event → Channel → Subscriber → Delivery → DeliveryLog
- Introduced from Module 06 onwards
- Covers: message queues (RabbitMQ/BullMQ), pub/sub, event-driven architecture, microservices, reliability patterns

---

## Steps

### Phase 1: Scaffolding (Steps 1–2)

1. **Create root `README.md`** — Curriculum overview, prerequisites (Node 20+, Docker, TS 5+, PostgreSQL 15+, Redis), learning path table (11 modules, ~50–65 hrs), both capstone domain models with entity diagrams, project structure.

2. **Create capstone scaffolding** — `capstone/scaleforge/` and `capstone/flowforge/` with `package.json`, `tsconfig.json`, `docker-compose.yml`, `.env.example`, `README.md`, `src/` structure.

### Phase 2: Core Modules (Steps 3–7) — *sequential*

3. **Module 01: Foundations** (4–5 hrs, 7 topic files) — What is System Design, approach framework, Performance vs Scalability (Node.js benchmarks), Latency vs Throughput, Availability vs Consistency, CAP Theorem, Consistency Patterns (Weak/Eventual/Strong with demos).

4. **Module 02: Networking & Communication** (4–5 hrs, 6 topic files) — DNS resolution, HTTP/TCP/UDP (Node.js `net` module demos), REST API design, RPC & gRPC (with Node.js example), GraphQL, Idempotent Operations.

5. **Module 03: Load Balancing & CDNs** (4–5 hrs, 6 topic files) — Load Balancers (Nginx Docker demo), LB vs Reverse Proxy, Load Balancing Algorithms (code implementations), Layer 4 vs Layer 7, Horizontal Scaling (Docker Compose multi-instance), CDNs (Pull vs Push).

6. **Module 04: Databases & Storage** (6–8 hrs, 9 topic files) — RDBMS/ACID, SQL vs NoSQL, Key-Value (Redis deep dive), Document (MongoDB), Wide Column (Cassandra), Graph (Neo4j), Replication (PostgreSQL streaming replication Docker), Sharding (consistent hashing TypeScript impl), Federation/Denormalization/SQL Tuning.

7. **Module 05: Caching** (4–5 hrs, 5 topic files) — Strategies (Cache Aside/Write-through/Write-behind/Refresh Ahead), Types of Caching (5 layers), Distributed Caching (Redis clustering), Cache Invalidation, Caching at Scale (thundering herd, stampede prevention).

### Phase 3: Advanced Modules (Steps 8–10) — *sequential*

8. **Module 06: Asynchronism & Background Jobs** (4–5 hrs, 5 files) — Message Queues (BullMQ/RabbitMQ), Task Queues (job scheduling, DLQ), Back Pressure, Event-Driven Architecture (Redis Pub/Sub), Schedule-Driven Systems. *FlowForge introduced here.*

9. **Module 07: Microservices & Architecture** (4–5 hrs, 5 files) — Microservices (monolith vs micro, decomposition), Service Discovery, Application Layer / API Gateway / BFF, Availability Patterns (Active-Active/Passive, Failover), Designing for Failure.

10. **Module 08: Performance & Monitoring** (5–6 hrs, 7 files) — 10 Performance Antipatterns (anti-pattern → fix code pairs), Health/Availability/Performance/Security/Usage Monitoring, Instrumentation (OpenTelemetry + pino), Visualization & Alerts (Prometheus + Grafana Docker).

### Phase 4: Patterns Modules (Steps 11–12) — *can run in parallel*

11. **Module 09: Cloud Design Patterns** (5–7 hrs, 6 files) — Data Management Patterns (Valet Key, Materialized View, Index Table), Event Sourcing & CQRS (TypeScript impl), Messaging Patterns (Pub/Sub, Competing Consumers, Choreography, etc.), Design & Implementation Patterns (Strangler Fig, Sidecar, Ambassador, Gateway patterns, BFF).

12. **Module 10: Reliability Patterns** (4–5 hrs, 5 files) — Availability (Deployment Stamps, Geodes, Throttling), High Availability (Bulkhead, Circuit Breaker), Resiliency (Retry with backoff, Compensating Transaction, Leader Election), Security (Federated Identity, Gatekeeper, Valet Key), Pattern selection matrix.

### Phase 5: Integration (Step 13) — *depends on all prior*

13. **Module 11: Capstone Integration** (4–5 hrs, 4 files) — ScaleForge architecture review, FlowForge architecture review, Production readiness checklist, System design interview practice.

---

## Writing Convention (per topic file)

Following the pattern from the existing tutorials:
1. **Concept** — 2-3 sentence thesis
2. **Deep Dive** — Detailed explanation + ASCII architecture diagrams
3. **Code Examples** — Runnable TypeScript + Docker Compose; show anti-pattern → correct pattern
4. **Try It Yourself** — Code stub with TODO, `<details>` collapsible solutions
5. **Capstone Connection** — Ties concept to ScaleForge/FlowForge

Each module README: Overview, Learning Objectives (checkboxes), Topics table, Estimated time, Prerequisites, Capstone Milestone.

---

## Relevant Files (for reference)

- `nodejs/README.md` — Reference for README structure and learning path table
- `nodejs/modules/01-internals/01-what-is-nodejs.md` — Reference for topic file format
- `postgres/README.md` — Reference for capstone domain model presentation
- `spring-boot/README.md` — Reference for mental model mapping table

---

## Verification

1. Every module folder has `README.md`, numbered topic files, `exercises/README.md`
2. All internal links between modules resolve correctly
3. Code examples are syntactically valid TypeScript
4. Docker Compose files start successfully
5. **Topic coverage matches Roadmap.png** — every item in the roadmap appears in a module
6. Capstone milestones build progressively (no forward dependencies)

---

## Decisions

- **Two capstones**: ScaleForge (primary, Modules 01–08) and FlowForge (advanced, Modules 06–11)
- **TypeScript + Docker Compose** for all demos (Nginx, Redis, PostgreSQL, RabbitMQ, Prometheus, Grafana)
- **~100 markdown files + ~10 config/scaffold files** total
- **Modules 09 & 10 can be done in parallel** — independent pattern categories
- **Naming**: `{NN}-{topic-slug}` pattern, `{Name}Forge` capstone naming
