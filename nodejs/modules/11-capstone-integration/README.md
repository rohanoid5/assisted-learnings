# Module 11 — Capstone Integration

## Overview

This module ties every previous module together into a production-ready PipeForge deployment. There are no new concepts — only integration: wiring all the pieces, hardening security, writing the final test suite, and shipping with Docker and CI/CD.

---

## Learning Objectives

- [ ] Run PipeForge end-to-end: API server + worker pool + PostgreSQL + WebSocket
- [ ] Containerize the application with a production-grade multi-stage Dockerfile
- [ ] Write a `docker-compose.yml` for local development (app + postgres + pgadmin)
- [ ] Apply the OWASP Top 10 security checklist to PipeForge
- [ ] Set up a CI/CD pipeline (GitHub Actions) that lints, tests, and builds on every push
- [ ] Walk through the complete PipeForge feature set as a working application

---

## Full System Architecture

```
┌──────────────────────────────────────────────────┐
│                   Client / Browser               │
│    REST (HTTP/1.1)          WebSocket (WS)        │
└────────────┬────────────────────────┬────────────┘
             ↓                        ↓
┌─────────────────────────────────────────────────┐
│              cluster.ts (Primary)               │
│   ┌─────────────┐  ┌─────────────┐  ┌────────┐ │
│   │  Worker 1   │  │  Worker 2   │  │  ...   │ │
│   │  HTTP + WS  │  │  HTTP + WS  │  │        │ │
│   │  WorkerPool │  │             │  │        │ │
│   └──────┬──────┘  └─────────────┘  └────────┘ │
└──────────┼──────────────────────────────────────┘
           ↓
┌──────────────────────────────────────────────────┐
│              Job Worker Threads                  │
│   ┌──────────────────────────────────────────┐  │
│   │  job-runner.ts × WORKER_POOL_SIZE threads │  │
│   │  Plugin execution, CSV/DB/WEBHOOK steps   │  │
│   └──────────────────────────────────────────┘  │
└──────────────────────┬───────────────────────────┘
                       ↓
        ┌──────────────────────────┐
        │  PostgreSQL 15           │
        │  Prisma (connection pool)│
        └──────────────────────────┘
```

---

## Docker Setup

### Multi-Stage Dockerfile

```dockerfile
# ---- Build stage ----
FROM node:20-alpine AS builder
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . .
RUN npx prisma generate && npm run build

# ---- Production stage ----
FROM node:20-alpine AS runner
WORKDIR /app

# Non-root user for security
RUN addgroup -S pipeforge && adduser -S pipeforge -G pipeforge

COPY --from=builder /app/dist ./dist
COPY --from=builder /app/node_modules ./node_modules
COPY --from=builder /app/prisma ./prisma
COPY --from=builder /app/package.json ./

USER pipeforge
EXPOSE 3000

# Migrate then start
CMD ["sh", "-c", "npx prisma migrate deploy && node dist/cluster.js"]
```

### docker-compose.yml (Local Development)

```yaml
services:
  app:
    build: .
    ports:
      - "3000:3000"
    environment:
      DATABASE_URL: postgresql://pipeforge:secret@postgres:5432/pipeforge
      JWT_SECRET: local-dev-secret-change-in-prod
      NODE_ENV: development
    depends_on:
      postgres:
        condition: service_healthy

  postgres:
    image: postgres:15-alpine
    environment:
      POSTGRES_USER: pipeforge
      POSTGRES_PASSWORD: secret
      POSTGRES_DB: pipeforge
    volumes:
      - postgres-data:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U pipeforge"]
      interval: 5s
      timeout: 5s
      retries: 10

  pgadmin:
    image: dpage/pgadmin4:latest
    ports:
      - "5050:80"
    environment:
      PGADMIN_DEFAULT_EMAIL: admin@pipeforge.io
      PGADMIN_DEFAULT_PASSWORD: admin
    depends_on: [postgres]

volumes:
  postgres-data:
```

---

## Security Hardening Checklist

Work through each item against PipeForge's codebase:

### Authentication & Authorization
- [ ] JWT tokens expire (30m access / 7d refresh) — `JWT_EXPIRES_IN` env var
- [ ] Passwords hashed with bcrypt, minimum rounds = 12
- [ ] `requireRole('ADMIN')` guards all admin endpoints including `/metrics`
- [ ] JWT secret validated at startup (min 32 chars), `process.exit(1)` if missing

### Input Validation
- [ ] All request bodies validated with Zod before touching the DB
- [ ] Path parameters (UUIDs) validated with `z.string().uuid()`
- [ ] `Prisma.sql` tagged template used for all raw queries (no string concatenation)
- [ ] File upload types validated before stream processing (MIME + magic bytes)

### HTTP Security
- [ ] `helmet()` applied globally (CSP, HSTS, X-Frame-Options)
- [ ] CORS locked to explicit origin list (`ALLOWED_ORIGINS` env var)
- [ ] Rate limiting on auth endpoints (5 req/min) and all endpoints (100 req/min)
- [ ] Request size limit: `express.json({ limit: '1mb' })`

### Infrastructure
- [ ] Secrets in environment variables, never in source code
- [ ] `.env` in `.gitignore`; `.env.example` committed with dummy values
- [ ] Non-root Docker user (`pipeforge`)
- [ ] Dependencies audited: `npm audit --audit-level=high`

---

## CI/CD Pipeline (GitHub Actions)

```yaml
# .github/workflows/ci.yml
name: CI

on:
  push:
    branches: [main, develop]
  pull_request:
    branches: [main]

jobs:
  test:
    runs-on: ubuntu-latest

    services:
      postgres:
        image: postgres:15-alpine
        env:
          POSTGRES_USER: pipeforge
          POSTGRES_PASSWORD: secret
          POSTGRES_DB: pipeforge_test
        ports: ["5432:5432"]
        options: >-
          --health-cmd pg_isready
          --health-interval 10s
          --health-timeout 5s
          --health-retries 5

    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-node@v4
        with:
          node-version: '20'
          cache: 'npm'

      - run: npm ci
      - run: npx prisma migrate deploy
        env:
          DATABASE_URL: postgresql://pipeforge:secret@localhost:5432/pipeforge_test

      - run: npm run lint
      - run: npm run type-check
      - run: npx vitest run --coverage
        env:
          DATABASE_URL: postgresql://pipeforge:secret@localhost:5432/pipeforge_test
          JWT_SECRET: ci-test-secret-do-not-use-in-production-must-be-32-chars

      - uses: codecov/codecov-action@v4
        with:
          files: ./coverage/lcov.info
```

---

## Full Feature Walkthrough

Run PipeForge end-to-end and verify each feature from every module:

```bash
# 1. Start the stack
docker compose up -d

# 2. Register a user
curl -X POST http://localhost:3000/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"alice@test.io","password":"SecurePass123!","name":"Alice"}'

# 3. Login and capture the token
TOKEN=$(curl -s -X POST http://localhost:3000/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"alice@test.io","password":"SecurePass123!"}' | jq -r '.data.token')

# 4. Create a pipeline
PIPELINE_ID=$(curl -s -X POST http://localhost:3000/api/v1/pipelines \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Demo ETL",
    "steps": [
      {"name": "Ingest CSV", "type": "CSV_IMPORT", "order": 0, "config": {"path": "sample.csv"}},
      {"name": "Notify",     "type": "WEBHOOK",     "order": 1, "config": {"url": "https://httpbin.org/post"}}
    ]
  }' | jq -r '.data.id')

# 5. Open WebSocket in another terminal (watch job progress)
# wscat -c "ws://localhost:3000?token=$TOKEN" → subscribe to jobId once created

# 6. Trigger a job
JOB_ID=$(curl -s -X POST http://localhost:3000/api/v1/pipelines/$PIPELINE_ID/jobs \
  -H "Authorization: Bearer $TOKEN" | jq -r '.data.id')

# 7. Watch live logs
curl -N http://localhost:3000/api/v1/jobs/$JOB_ID/logs/stream \
  -H "Authorization: Bearer $TOKEN"

# 8. Check metrics
curl http://localhost:3000/api/v1/metrics \
  -H "Authorization: Bearer $TOKEN"
```

---

## Module Concepts in PipeForge

| Module | Where it appears in PipeForge |
|--------|-------------------------------|
| 01 — Internals | Event loop, ESM imports, `node:` built-ins throughout |
| 02 — Async | Every async service function, `async/await` patterns |
| 03 — Error Handling | Custom error hierarchy, `asyncHandler`, graceful shutdown |
| 04 — Streams | CSV import Transform, NDJSON log streaming, gzip artifact export |
| 05 — File System & CLI | `pipeforge` CLI, env config (Zod), `--watch` import mode |
| 06 — Networking | REST API, JWT middleware, WebSocket rooms, `CircuitBreaker` |
| 07 — Design Patterns | Plugin registry, typed EventEmitter, `Pipeline<T>`, DI container |
| 08 — Databases | Prisma CRUD, migrations, transactions, N+1 fix, raw SQL analytics |
| 09 — Testing | Vitest unit + integration tests, mocked DB, 70% coverage CI |
| 10 — Process & Performance | WorkerPool, cluster mode, memory monitor, event loop metrics |

---

## Capstone Checkpoint ✅ — Final

You have completed PipeForge! Verify the following to consider the tutorial done:

- [ ] `docker compose up` starts all services cleanly
- [ ] Full feature walkthrough curl script completes without errors
- [ ] `npx vitest run --coverage` shows ≥ 70% coverage across all metrics
- [ ] `npm audit` reports zero high/critical vulnerabilities
- [ ] Security checklist above is fully checked off
- [ ] CI pipeline (GitHub Actions or local `act`) passes green

Congratulations — you've built a production-grade Node.js application covering the complete advanced Node.js curriculum.
