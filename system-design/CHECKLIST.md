# System Design Knowledge Checklist

Use this file to periodically self-assess. Review it monthly and update your ratings.

**Legend:** `[ ]` Not yet · `[~]` In progress · `[x]` Confident

---

## Module 1 — Foundations of System Design

### 1.1 What Is System Design?
- [x] Can describe the goal of system design: translate requirements into architecture
- [x] Knows the two phases: high-level design (components) and low-level design (internals)
- [x] Understands why there is no single "correct" answer — trade-offs are the answer

### 1.2 How to Approach System Design
- [x] Can follow the RESHADED or similar structured framework in an interview/discussion
- [x] Clarifies functional and non-functional requirements before starting
- [x] Estimates scale (users, QPS, data size) before choosing components
- [x] Knows to start with a simple design and iterate toward complexity

### 1.3 Performance vs Scalability
- [x] Understands: performance = fast for a single user; scalability = stays fast as users grow
- [x] Can give an example of a system that is performant but not scalable
- [x] Knows vertical vs horizontal scaling trade-offs

### 1.4 Latency vs Throughput
- [x] Can define latency (time per request) and throughput (requests per unit time)
- [x] Understands the latency numbers every engineer should know (L1/L2 cache, RAM, disk, network)
- [x] Knows that optimizing for max throughput can increase tail latency and vice versa

### 1.5 Availability vs Consistency
- [x] Understands base availability math: 99.9% SLA = ~8.7 hours downtime/year; 99.99% = ~52 minutes
- [x] Knows that combining SLAs multiplies availability: two 99.9% services in series = 99.8%
- [x] Can explain the consistency–availability tension and give a real trade-off example

### 1.6 CAP Theorem
- [x] Can state the CAP theorem correctly: in a partition, choose consistency OR availability
- [x] Knows CP vs AP systems with real examples (ZooKeeper = CP; DynamoDB = AP by default)
- [x] Understands that network partitions are rare but must be assumed in distributed systems

### 1.7 Consistency Patterns
- [x] Knows strong consistency, weak consistency, and eventual consistency with examples
- [x] Understands causal consistency and read-your-writes consistency
- [x] Can choose the right consistency level for a given feature (e.g., banking vs social feed)

---

## Module 2 — Networking & Communication

### 2.1 HTTP Fundamentals
- [ ] Understands HTTP/1.1 (keep-alive), HTTP/2 (multiplexing, header compression), HTTP/3 (QUIC)
- [ ] Knows all meaningful status code classes and common codes: 200, 201, 204, 301, 302, 400, 401, 403, 404, 409, 429, 500, 502, 503, 504
- [ ] Understands idempotency and safety of HTTP methods

### 2.2 REST vs GraphQL vs gRPC
- [ ] Can compare all three on: schema, transport, coupling, over/under-fetching, tooling
- [ ] Knows when gRPC excels (service-to-service, streaming, strict contracts)
- [ ] Knows when GraphQL excels (flexible client-driven queries, aggregating multiple services)
- [ ] Can explain REST's resource-oriented design and HATEOAS principle

### 2.3 WebSockets & Server-Sent Events
- [ ] Understands the HTTP Upgrade to WebSocket and the full-duplex nature
- [ ] Knows SSE is a one-way persistent stream (server → client) over plain HTTP
- [ ] Can choose between WebSockets, SSE, and polling for given real-time use cases

### 2.4 DNS & Routing
- [ ] Can trace a DNS resolution chain: browser cache → OS → recursive resolver → root → TLD → authoritative
- [ ] Understands A, AAAA, CNAME, MX, TXT, NS record types
- [ ] Knows DNS-based load balancing (round-robin DNS) and its limitations (client caching, no health checks)
- [ ] Understands AnyCast routing and how CDNs use it

### 2.5 TCP Connections & Pooling
- [ ] Knows the TCP 3-way handshake and 4-way teardown; knows TIME_WAIT and why it matters
- [ ] Understands TCP slow start, congestion control (AIMD), and how it affects throughput
- [ ] Can explain why connection pooling amortizes handshake cost and reduces operational overhead

### 2.6 Network Security Basics
- [ ] Understands TLS handshake: certificate exchange, key exchange, symmetric session key
- [ ] Knows HSTS, CORS, CSP headers and their purpose
- [ ] Can explain man-in-the-middle attacks and how certificate pinning mitigates them
- [ ] Understands DDoS mitigation strategies: rate limiting, IP filtering, SYN cookies, CDN scrubbing

---

## Module 3 — Load Balancing & CDNs

### 3.1 Load Balancing Algorithms
- [ ] Knows Round Robin, Weighted Round Robin, Least Connections, IP Hash, Least Response Time
- [ ] Can reason about session affinity (sticky sessions) trade-offs with horizontal scaling
- [ ] Understands consistent hashing and why it minimizes cache misses during node changes

### 3.2 L4 vs L7 Load Balancing
- [ ] Can explain L4 (TCP/UDP level) vs L7 (HTTP level) and their respective capabilities
- [ ] Knows when L4 is better (raw throughput, any TCP protocol) vs L7 (HTTP routing, SSL termination, WAF)
- [ ] Understands that L7 proxies (Nginx, HAProxy, Envoy) can inspect and rewrite requests

### 3.3 Nginx Configuration
- [ ] Can write an `upstream` block with multiple servers and `server` blocks for proxy pass
- [ ] Can configure rate limiting with `limit_req_zone` and `limit_req`
- [ ] Can configure SSL termination (certificate, key, ciphers, HSTS header)
- [ ] Understands `worker_processes`, `worker_connections`, and tuning `events` block for concurrency

### 3.4 Health Checks & Circuit Breakers
- [ ] Understands active (periodic ping) vs passive (observed failure) health checks
- [ ] Knows Nginx `health_check` directive (Plus) and open-source workarounds
- [ ] Can implement a circuit breaker (CLOSED → OPEN → HALF-OPEN) for inter-service calls

### 3.5 CDNs & Edge Caching
- [ ] Understands Points of Presence (PoPs) and how a CDN routes to the nearest edge
- [ ] Knows origin pull vs origin push CDN models; knows cache TTL and `Cache-Control` headers
- [ ] Can explain edge-side includes, edge compute (Cloudflare Workers, Lambda@Edge)
- [ ] Knows when to NOT cache: authenticated responses, highly dynamic personalized content

### 3.6 Horizontal vs Vertical Scaling
- [ ] Can quantify vertical scaling limits and cost curves vs horizontal scaling
- [ ] Knows what makes horizontal scaling hard: state (session, cache), distributed locks, file uploads
- [ ] Understands stateless service design as the prerequisite for horizontal scale

---

## Module 4 — Databases & Storage

### 4.1 Relational vs NoSQL vs NewSQL
- [ ] Can categorize: key-value (Redis, DynamoDB), document (MongoDB), column-family (Cassandra), graph (Neo4j)
- [ ] Knows the access pattern differences: SQL strong queries vs NoSQL access-pattern-driven design
- [ ] Understands NewSQL (CockroachDB, Spanner) as distributed SQL with horizontal scaling

### 4.2 Indexes & Query Optimization
- [ ] Understands B-tree, LSM tree (used in LevelDB/Cassandra), and their read/write trade-offs
- [ ] Can choose a composite index column order based on query selectivity
- [ ] Knows covering indexes (index-only scan) and their storage vs speed trade-off
- [ ] Understands that too many indexes slow writes and increase storage

### 4.3 Replication & Read Replicas
- [ ] Understands synchronous vs asynchronous replication and their durability/latency trade-offs
- [ ] Knows primary-replica (single-leader) and multi-leader and leaderless replication models
- [ ] Can describe replication lag and how to handle stale reads in the application layer

### 4.4 Sharding Strategies
- [ ] Knows range, hash, and directory-based (lookup table) sharding
- [ ] Understands hot-spot problem in range sharding and how to mitigate with key salting
- [ ] Knows cross-shard joins and transactions are complex — design to minimize them

### 4.5 Schema Migrations
- [ ] Understands expand/contract (blue-green migration) pattern for zero-downtime changes
- [ ] Knows why `ALTER TABLE ADD COLUMN` is safe but `ALTER TABLE DROP COLUMN` needs a cleanup phase
- [ ] Can design a migration strategy for a table with 100M+ rows without locking

### 4.6 Connection Pooling with PgBouncer
- [ ] Knows PgBouncer session / transaction / statement modes and which is safe for each ORM
- [ ] Understands `default_pool_size`, `max_client_conn`, `server_idle_timeout`
- [ ] Can explain why serverless functions need external pooling (PgBouncer / Prisma Accelerate)

---

## Module 5 — Caching

### 5.1 Caching Strategies
- [ ] Knows Cache-Aside (Lazy Loading), Read-Through, Write-Through, Write-Back (Write-Behind)
- [ ] Can select a strategy based on read/write ratio and consistency requirements
- [ ] Understands cache warming vs cold start and mitigation

### 5.2 Redis Deep Dive
- [ ] Knows Redis data structures: string, list, set, sorted set, hash, stream, HyperLogLog, Bloom filter module
- [ ] Can implement a sorted-set leaderboard, session store, rate limiter, pub/sub with Redis
- [ ] Understands Redis persistence: RDB snapshots vs AOF (append-only file) vs no persistence
- [ ] Knows Redis Sentinel (HA) vs Redis Cluster (sharding) and when to use each

### 5.3 Cache Invalidation
- [ ] Understands TTL-based, event-driven, and write-through invalidation strategies
- [ ] Knows the cache invalidation problem: "one of the two hard things in computer science"
- [ ] Can design a cache invalidation scheme for a social feed (fan-out write vs fan-out read)

### 5.4 Cache Stampede
- [ ] Can explain the thundering herd / cache stampede scenario
- [ ] Knows mitigation strategies: mutex lock on miss, probabilistic early expiration (XFetch), background refresh

### 5.5 Multi-Tier Caching
- [ ] Understands L1 (in-process), L2 (Redis cluster), L3 (CDN) cache hierarchies
- [ ] Can reason about consistency across tiers and cache invalidation propagation order
- [ ] Knows when to skip L1 (concurrent writes from multiple instances invalidate local state)

---

## Module 6 — Asynchronism

### 6.1 Message Queues
- [ ] Understands producer/consumer decoupling and the durability guarantee
- [ ] Knows at-most-once, at-least-once, and exactly-once delivery semantics
- [ ] Can compare Kafka (log-based, replay) vs RabbitMQ (broker-based, routing) vs SQS (managed, simple)

### 6.2 BullMQ Deep Dive
- [ ] Can define a job queue, add jobs with options (`delay`, `attempts`, `backoff`, `priority`)
- [ ] Can implement a `Worker` with concurrency and job lifecycle hooks
- [ ] Knows `QueueScheduler` / `QueueEvents` for delayed jobs and stalled job handling
- [ ] Can design a rate-limited, retried background job pipeline

### 6.3 Pub/Sub Patterns
- [ ] Understands fan-out: one message delivered to all subscribers
- [ ] Knows Redis Pub/Sub (ephemeral, no persistence) vs Kafka topics (durable, replayable)
- [ ] Can implement notifications / event broadcasting with pub/sub

### 6.4 Event Sourcing
- [ ] Can explain: state is derived from an immutable, ordered log of events
- [ ] Understands CQRS (Command Query Responsibility Segregation) as a natural pair for event sourcing
- [ ] Knows trade-offs: eventual consistency of projections, event schema evolution, large event stores

### 6.5 Backpressure & Flow Control
- [ ] Understands that a faster producer than consumer causes queue overflow or unbounded memory growth
- [ ] Knows strategies: drop (lossy), block producer, reject with 429, flow control signals
- [ ] Can implement backpressure in a Node.js stream pipeline

### 6.6 Dead Letter Queues
- [ ] Understands DLQ as a holding area for messages that failed all retries
- [ ] Can configure BullMQ / SQS DLQs and set up alerts on DLQ depth
- [ ] Knows the debugging + replay workflow for DLQ messages

---

## Module 7 — Microservices

### 7.1 Monolith vs Microservices
- [ ] Can list 4 benefits of microservices: independent deploy, scale, tech stack, fault isolation
- [ ] Can list 4 challenges: distributed system complexity, network latency, data consistency, operational overhead
- [ ] Knows the "monolith first" principle (Martin Fowler) and when to NOT split

### 7.2 Service Communication
- [ ] Can compare synchronous (REST, gRPC) vs asynchronous (queue, event bus) communication
- [ ] Knows request-reply over async (correlation ID pattern)
- [ ] Understands service mesh (Istio, Linkerd) features: mTLS, observability, traffic management

### 7.3 API Gateway Pattern
- [ ] Can describe the responsibilities: auth, rate limiting, routing, SSL termination, response aggregation
- [ ] Knows BFF (Backend for Frontend) pattern and when it's useful
- [ ] Understands the API gateway as a single point of failure and how to make it HA

### 7.4 Service Discovery
- [ ] Can explain client-side vs server-side discovery
- [ ] Knows tools: Consul (KV + health checks + DNS), etcd, Eureka, Kubernetes built-in DNS
- [ ] Understands health check registration and deregistration lifecycle

### 7.5 Distributed Transactions
- [ ] Knows that 2PC (Two-Phase Commit) is slow and locks resources
- [ ] Can explain the Saga pattern: choreography vs orchestration variants
- [ ] Understands compensating transactions and their idempotency requirements
- [ ] Knows the Outbox Pattern for reliable event publishing with database writes

### 7.6 Observability
- [ ] Understands the three pillars: metrics, logs, traces
- [ ] Can describe the difference between structured logging (JSON), correlation IDs, and distributed traces
- [ ] Knows OpenTelemetry as the standard for instrumenting services
- [ ] Can design an alerting strategy: error rate, latency p99, saturation (USE method), traffic (RED method)

---

## Module 8 — Performance & Monitoring

### 8.1 Node.js Performance Profiling
- [ ] Can use V8 `--prof` + `--prof-process` to identify hot functions
- [ ] Can generate and analyze a heap snapshot in Chrome DevTools
- [ ] Knows `clinic.js` flame for CPU profiling and `clinic.js` doctor for event loop blockage

### 8.2 Connection Pool Tuning
- [ ] Knows the formula: pool_size ≈ (core_count * 2) + effective_spindle_count
- [ ] Can measure queue depth and timeout rate to find optimal pool size empirically
- [ ] Understands that too large a pool can overwhelm the DB; starts with conservative sizes

### 8.3 Query Performance Analysis
- [ ] Can interpret `EXPLAIN (ANALYZE, BUFFERS)` output and identify Seq Scans, Hash Joins
- [ ] Can use `pg_stat_statements` to find top slow and top-called queries
- [ ] Can add a missing index or rewrite a query to fix a slow execution plan

### 8.4 Prometheus & Grafana
- [ ] Can instrument a Node.js service with `prom-client` (counter, gauge, histogram, summary)
- [ ] Knows PromQL: `rate()`, `increase()`, `histogram_quantile()`, `by` clause
- [ ] Can build a Grafana dashboard with RED metrics: Request rate, Error rate, Duration (p50/p95/p99)
- [ ] Can write a Prometheus alert rule for error rate > threshold for 5 minutes

### 8.5 Load Testing with Autocannon
- [ ] Can run `autocannon -c 100 -d 30 http://localhost:3000/api` and interpret the output
- [ ] Knows what to look for: latency percentiles (p99), throughput (req/sec), error rate
- [ ] Can identify the saturation point (throughput plateaus, latency spikes) in a load test

---

## Module 9 — Cloud Design Patterns

### 9.1 Circuit Breaker
- [ ] Can implement CLOSED → OPEN → HALF-OPEN state machine
- [ ] Knows thresholds: failure rate threshold to open, probe period, success threshold to close
- [ ] Can integrate with Prometheus to emit circuit state as a metric and alert on OPEN state

### 9.2 Retry with Jitter
- [ ] Can implement exponential backoff: `delay = base * 2^attempt`
- [ ] Understands why pure exponential backoff causes retry storms and how jitter (random offset) solves it
- [ ] Knows full jitter vs equal jitter vs decorrelated jitter trade-offs

### 9.3 Bulkhead Pattern
- [ ] Can explain bulkhead isolation: separate thread pools or connection pools per downstream service
- [ ] Understands that without bulkheads, a slow downstream can exhaust the shared pool and cascade failures
- [ ] Can implement resource isolation with separate queues or `worker_threads` pools

### 9.4 Timeouts & Deadline Propagation
- [ ] Understands connect timeout vs read timeout vs request timeout
- [ ] Knows deadline propagation: passing the remaining budget as a header (gRPC deadline, `X-Request-Timeout`)
- [ ] Can reason about setting timeouts that are tighter than the upstream SLA

### 9.5 Rate Limiting
- [ ] Knows token bucket, leaky bucket, sliding window log, and fixed window counter algorithms
- [ ] Can implement token bucket rate limiting with Redis
- [ ] Understands per-user, per-IP, and per-endpoint rate limits
- [ ] Knows how to return `429 Too Many Requests` with `Retry-After` header

---

## Module 10 — Reliability Engineering

### 10.1 SLOs, SLAs & SLIs
- [ ] Can define: SLI (the metric), SLO (the target), SLA (the contract with consequences)
- [ ] Can choose good SLIs for a service: latency p99, availability, error rate, throughput
- [ ] Knows the difference between user-facing and internal service SLOs

### 10.2 Error Budgets
- [ ] Understands: error budget = 1 − SLO target per period
- [ ] Knows how to use error budget as a decision lever: freeze deploys when budget is exhausted
- [ ] Can calculate remaining error budget given current error rate and time in period

### 10.3 Graceful Degradation
- [ ] Can design a feature flag / circuit breaker to serve degraded (but functional) responses when a dependency is down
- [ ] Knows patterns: cached response, default value, partial result, "Sorry, unavailable" fallback
- [ ] Understands the difference between graceful degradation and complete failure

### 10.4 Chaos Engineering
- [ ] Understands the Chaos Engineering principles: experiment in production with controlled blast radius
- [ ] Knows Chaos Monkey / Chaos Toolkit / Gremlin as tooling options
- [ ] Can design and run a basic chaos experiment: kill one pod, verify the system degrades gracefully
- [ ] Knows game days and the importance of a hypothesis, observability, and a stop condition

---

## Module 11 — Capstone

### ScaleForge
- [ ] Can describe ScaleForge's architecture: all services, queues, and databases
- [ ] Has run the full `docker-compose` stack and produced load test results
- [ ] Can explain each Prometheus metric emitted and what alert would fire
- [ ] Has tuned at least one parameter (pool size, cache TTL, concurrency) and observed improvement

### FlowForge
- [ ] Can describe FlowForge's pipeline architecture and event flow
- [ ] Has walked through the `docker-compose.yml` and understands each service role
- [ ] Can extend it with a new stage or integration

### Production Readiness
- [ ] Can walk through the production readiness checklist and justify every item
- [ ] Has implemented at least 5 items from the checklist in a personal or capstone project

---

## Systems Design Interview Preparation

- [ ] Can design URL shortener (Bitly) — estimate scale, design DB, handle redirects at scale
- [ ] Can design a news feed (Twitter/Facebook) — fan-out write vs fan-out read trade-offs
- [ ] Can design a rate limiter — algorithm choice, distributed coordination
- [ ] Can design a distributed cache (Redis cluster) — consistent hashing, eviction
- [ ] Can design a notification service — push vs pull, fan-out, reliability
- [ ] Can design a payment system — idempotency, ACID transactions, audit log
- [ ] Can design autocomplete/typeahead — trie vs inverted index, caching, CDN
- [ ] Can design a search engine — web crawling, inverted index, ranking

---

## Review Log

| Date | Topics Reviewed | Gaps Identified |
|------|----------------|-----------------|
| | | |
| | | |
| | | |
