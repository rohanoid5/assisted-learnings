# 3.6 — Horizontal vs Vertical Scaling

## Concept

Vertical scaling means upgrading to a more powerful single machine. Horizontal scaling means adding more machines. For Node.js services, the two strategies are not mutually exclusive — **vertical scaling first** gets you more CPU cores, and **horizontal scaling next** lets you spread across multiple physical machines. Node.js's single-threaded event loop requires an extra step: the `cluster` module or a process manager to actually use those additional cores.

---

## Deep Dive

### The Two Approaches

```
Vertical Scaling (Scale Up):             Horizontal Scaling (Scale Out):

  t3.small:  2 vCPU, 2GB RAM              ┌────────┐  ┌────────┐  ┌────────┐
       │                                   │ app:1  │  │ app:2  │  │ app:3  │
       ▼                                   └────────┘  └────────┘  └────────┘
  t3.2xlarge: 8 vCPU, 32GB RAM                         ▲
       │                                         ┌──────┘
       ▼                                    Load Balancer (Nginx)
  r5.4xlarge: 16 vCPU, 128GB RAM
  
  Advantages:                             Advantages:
  - Simple (no code changes)              - Unlimited scale (add more nodes)
  - No distributed system complexity      - Fault tolerant (one dies, others serve)
  - Better single-thread performance      - Zero-downtime deploys (rolling updates)
  
  Limits:                                 Limits:
  - Hard ceiling (biggest machine         - Session/state must be external (Redis)
    on AWS is 448 vCPU / 24TB RAM)        - More operational complexity
  - Single point of failure              - Network calls between nodes
  - Expensive per unit at high end        - Distributed debugging is harder
```

### Why Node.js Needs Special Treatment

```
Most languages (Java, Go) use thread pools:
  1 CPU core = 1 thread = 1 concurrent CPU-bound operation
  Add a core → add a thread → linear throughput increase

Node.js uses a single-threaded event loop:
  1 Node.js process ≈ 1 CPU core used (even on a 32-core machine)
  
  The event loop:
    poll for I/O → execute callbacks → check timers → poll for I/O → ...
    
    While a callback runs, NOTHING ELSE runs.
    
  This means: Node is excellent for I/O-bound work (DB queries, HTTP calls)
              but terrible for CPU-bound work (image encoding, crypto, ML)
              
  Solution A: Node.js cluster module → one process per core, same port
  Solution B: Worker threads (worker_threads module) → CPU-bound in parallel
  Solution C: Horizontal scaling → multiple VMs/containers via LB

  For ScaleForge (pure I/O-bound), cluster or horizontal scaling is correct.
  For CPU-bound tasks (e.g., generating QR codes for short URLs), Worker threads.
```

### Capacity Planning

```
ScaleForge capacity calculation:

  Single replica benchmarks (from Module 03 exercises):
    Redirect path: ~3,500 req/s sustained (p99 < 10ms)
    Postgres pool: max 10 connections per replica
    Redis: single connection, pipelined, ~50k ops/s
    
  Target: 10,000 req/s peak, p99 < 20ms
  
  Math:
    10,000 req/s ÷ 3,500 req/s per replica = 2.86 → 3 replicas
    Safety margin (never run > 70% capacity): 3 ÷ 0.7 = 4.3 → 5 replicas
    
  DB connection budget:
    5 replicas × 10 connections = 50 connections to Postgres
    Postgres default max_connections = 100 → fine
    
  Redis connections:
    5 replicas × 1 persistent connection = 5 connections
    Redis easily handles 10k+ connections → fine
    
  When to add more replicas: p99 latency > 15ms OR CPU > 70%
  When to scale vertically first: single-core throughput is the bottleneck
```

### Auto-Scaling Triggers

```
  Metric              Scale Out Trigger    Scale In Trigger
  ──────────────────  ───────────────────  ─────────────────
  CPU utilization     > 70% for 5 min      < 30% for 15 min
  Request rate        > 80% of capacity    < 40% of capacity  
  p99 latency         > 15ms (our SLA)     —
  Queue depth         > 1000 jobs pending  < 100 jobs
  Error rate          > 1%                 —

  AWS CloudWatch / GCP Autoscaler / k8s HPA can trigger on any of these.
  Always use a cooldown period (e.g., 10 min) to prevent thrash.
```

---

## Code Examples

### Node.js Cluster Module

```typescript
// src/cluster.ts — use all available CPU cores

import cluster from 'node:cluster';
import os from 'node:os';
import process from 'node:process';

const NUM_WORKERS = os.cpus().length;

if (cluster.isPrimary) {
  console.log(`Primary ${process.pid} starting ${NUM_WORKERS} workers`);

  for (let i = 0; i < NUM_WORKERS; i++) {
    cluster.fork();
  }

  cluster.on('exit', (worker, code, signal) => {
    // Auto-restart crashed workers (not during deliberate shutdown)
    if (signal !== 'SIGTERM' && signal !== 'SIGINT') {
      console.warn(`Worker ${worker.process.pid} died (${signal || code}). Restarting...`);
      cluster.fork();
    }
  });

  // Handle graceful shutdown of all workers on SIGTERM
  process.on('SIGTERM', () => {
    console.log('Primary received SIGTERM — shutting down workers');
    for (const worker of Object.values(cluster.workers ?? {})) {
      worker?.process.kill('SIGTERM');
    }
  });

} else {
  // Each worker runs the full Express app independently
  // Nginx (or the OS) distributes TCP connections across workers
  import('./server.js');
  console.log(`Worker ${process.pid} started`);
}
```

```typescript
// src/routes/debug.router.ts — expose which worker handled the request
// Useful for verifying load is distributed evenly across workers

import { Router } from 'express';
import process from 'node:process';

export const debugRouter = Router();

debugRouter.get('/worker-id', (_req, res) => {
  res.json({
    pid: process.pid,
    // cluster.worker.id is 1-indexed per primary; useful in logs
    workerId: process.env.WORKER_ID ?? 'single-process',
  });
});
```

### Horizontal Scaling with Docker Compose

```yaml
# capstone/scaleforge/docker-compose.yml — scale app service to N replicas

services:
  app:
    build: .
    environment:
      DATABASE_URL: postgres://user:pass@postgres:5432/scaleforge
      REDIS_URL:    redis://redis:6379
    depends_on:
      postgres:
        condition: service_healthy
      redis:
        condition: service_healthy
    # No fixed ports — Nginx reaches app containers by service name
    # Docker Compose assigns each replica a unique IP in the overlay network
    deploy:
      replicas: 3       # docker compose up --scale app=3

  nginx:
    image: nginx:1.25-alpine
    ports:
      - "8080:80"
    volumes:
      - ./nginx/nginx.conf:/etc/nginx/nginx.conf:ro
    depends_on:
      - app

  postgres:
    image: postgres:16-alpine
    environment:
      POSTGRES_DB: scaleforge
      POSTGRES_USER: user
      POSTGRES_PASSWORD: pass
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U user"]
      interval: 5s
      retries: 5

  redis:
    image: redis:7-alpine
    healthcheck:
      test: ["CMD", "redis-cli", "ping"]
      interval: 5s
      retries: 5
```

```nginx
# nginx/nginx.conf — Nginx resolves the "app" hostname to all replica IPs
# Docker's internal DNS returns multiple A records for scaled services

upstream app_backend {
    least_conn;
    server app:3001;      # Docker DNS expands to all app_* replica IPs
    keepalive 32;
}
```

---

## Try It Yourself

**Exercise:** Measure throughput vs replica count.

```bash
# Prerequisites: Docker Desktop, autocannon installed globally
# npm install -g autocannon

# TODO:
# 1. Start ScaleForge with 1 replica:
#    docker compose up --scale app=1 -d
#
# 2. Benchmark redirect throughput:
#    autocannon http://localhost:8080/test01 -d 10 -c 50
#    Record: req/sec and p99 latency
#
# 3. Scale to 3 replicas (no downtime):
#    docker compose up --scale app=3 -d
#    autocannon http://localhost:8080/test01 -d 10 -c 50
#    Record: req/sec and p99 latency
#
# 4. Scale to 5 replicas and repeat.
#
# 5. Plot the results:
#    Replicas | req/sec | p99
#    ─────────┼─────────┼────
#       1     |         |
#       3     |         |
#       5     |         |
#
# Expected: throughput scales roughly linearly until the Nginx or DB becomes
# the bottleneck. Beyond ~5 replicas with pool.max=10, Postgres max_connections
# may become the limit.
```

<details>
<summary>Show expected results and explanation</summary>

```
Typical results on a 4-core development machine:
  Replicas | req/sec | p99
  ─────────┼─────────┼─────────────
     1     | ~3,500  | ~8ms
     3     | ~9,000  | ~12ms
     5     | ~12,000 | ~18ms
     8     | ~12,500 | ~25ms (Nginx now the bottleneck)

Key observations:
  1. Scaling from 1→3 is nearly linear (constraint was CPU on single instance)
  2. Scaling from 3→5 has diminishing returns (Postgres pool pressure appears)
  3. Beyond 5 replicas: Postgres connections (50) approach max_connections (100)
     Solution: PgBouncer connection pooler in front of Postgres
  4. The Nginx upstream with least_conn distributes unevenly between replicas
     because connection establishment latency varies
     Solution: Use ip_hash for sticky sessions if app state is in-memory
               (ScaleForge uses Redis/Postgres for state so this isn't needed)
```

</details>

---

## Capstone Connection

The Docker Compose `--scale app=3` command is the fastest way to validate ScaleForge's horizontal scalability. The app is stateless by design — all state lives in Postgres or Redis — so any replica can handle any request. This statefulness constraint (no local in-memory state that isn't shared) was baked into the architecture in Module 01 when we chose Redis for the click event buffer.

In a real AWS/GCP/Azure deployment, this becomes an Auto Scaling Group (ASG) or Kubernetes Deployment with Horizontal Pod Autoscaler (HPA). The `SIGTERM` graceful shutdown handler from Module 3.4 is what makes rolling deployments zero-downtime — new pods become ready before old pods receive SIGTERM.
