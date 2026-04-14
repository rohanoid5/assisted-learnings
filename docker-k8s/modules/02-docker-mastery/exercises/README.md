# Module 02 — Exercises

Hands-on practice with Dockerfiles, multi-stage builds, networking, volumes, and Docker Compose. These exercises build toward the DeployForge capstone.

> **Prerequisites:** Docker 24+ installed and running. Familiarity with Module 02 concept files (01–04).

---

## Exercise 1: Optimize a Dockerfile

**Goal:** Take a naive 1.2GB Node.js Dockerfile and optimize it to under 150MB using best practices from topics 2.1 and 2.2.

### The Bad Dockerfile

```dockerfile
FROM node:20
WORKDIR /app
ADD . .
RUN npm install
RUN npm run build
RUN apt-get update && apt-get install -y curl
EXPOSE 3000
ENV SECRET_API_KEY=sk-1234567890abcdef
CMD npm start
```

### Problems to Fix

Apply these optimizations:
1. Use a smaller base image
2. Multi-stage build (separate builder from runtime)
3. Fix instruction ordering for layer caching
4. Replace `ADD` with `COPY`
5. Use `npm ci` with `--only=production` for runtime
6. Remove the baked-in secret
7. Add a non-root user
8. Add a health check
9. Use exec form for CMD
10. Add a `.dockerignore`

### Steps

1. Create a project directory with a minimal `package.json` and `src/server.ts`:

```bash
mkdir -p optimize-exercise/src
cd optimize-exercise

cat > package.json << 'EOF'
{
  "name": "optimize-exercise",
  "version": "1.0.0",
  "scripts": {
    "build": "tsc",
    "start": "node dist/server.js"
  },
  "dependencies": {
    "express": "^4.18.2"
  },
  "devDependencies": {
    "typescript": "^5.3.0",
    "@types/express": "^4.17.21",
    "@types/node": "^20.10.0"
  }
}
EOF

cat > tsconfig.json << 'EOF'
{
  "compilerOptions": {
    "target": "ES2022",
    "module": "commonjs",
    "outDir": "./dist",
    "rootDir": "./src",
    "strict": true,
    "esModuleInterop": true,
    "skipLibCheck": true
  }
}
EOF

cat > src/server.ts << 'EOF'
import express from "express";
const app = express();
const PORT = process.env.PORT || 3000;

app.get("/health", (_req, res) => {
  res.json({ status: "ok", timestamp: new Date().toISOString() });
});

app.listen(PORT, () => {
  console.log(`Server running on port ${PORT}`);
});
EOF
```

2. Build with the bad Dockerfile and note the image size.
3. Create an optimized Dockerfile and `.dockerignore`.
4. Build the optimized version and compare sizes.

### Verification

```bash
# Compare the two images
docker images optimize-exercise
# REPOSITORY           TAG          SIZE
# optimize-exercise    naive        ~1.2GB
# optimize-exercise    optimized    < 150MB

# Verify the optimized image works
docker run --rm -d -p 3000:3000 --name opt-test optimize-exercise:optimized
curl http://localhost:3000/health
# → {"status":"ok","timestamp":"..."}

# Verify non-root user
docker exec opt-test whoami
# → appuser (NOT root)

# Verify health check is configured
docker inspect opt-test --format='{{.Config.Healthcheck}}'

# Clean up
docker stop opt-test
```

<details>
<summary>Show solution</summary>

**.dockerignore:**
```dockerignore
node_modules
npm-debug.log*
dist
.git
.gitignore
.env*
*.md
Dockerfile*
docker-compose*.yml
.dockerignore
coverage
.nyc_output
__tests__
*.test.ts
*.spec.ts
.vscode
.idea
```

**Optimized Dockerfile:**
```dockerfile
# syntax=docker/dockerfile:1

# ─── Stage 1: Build ───
FROM node:20-alpine AS builder
WORKDIR /app

COPY package.json package-lock.json ./
RUN npm ci

COPY tsconfig.json ./
COPY src/ ./src/
RUN npm run build

# ─── Stage 2: Production ───
FROM node:20-alpine AS production
WORKDIR /app

RUN addgroup --system --gid 1001 appgroup && \
    adduser --system --uid 1001 --ingroup appgroup appuser

COPY package.json package-lock.json ./
RUN npm ci --only=production && npm cache clean --force

COPY --from=builder --chown=appuser:appgroup /app/dist ./dist

USER appuser

ENV NODE_ENV=production
EXPOSE 3000

HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:3000/health || exit 1

ENTRYPOINT ["node"]
CMD ["dist/server.js"]
```

```bash
# Build the naive version
docker build -f Dockerfile.naive -t optimize-exercise:naive .

# Build the optimized version
docker build -f Dockerfile -t optimize-exercise:optimized .

# Compare
docker images optimize-exercise --format "table {{.Tag}}\t{{.Size}}"
# TAG         SIZE
# naive       ~1.2GB
# optimized   ~95MB
```

**What each fix achieves:**
| Fix | Impact |
|-----|--------|
| `node:20-alpine` instead of `node:20` | -900MB (base image) |
| Multi-stage build | -200MB (dev deps, TS source not in final) |
| `npm ci --only=production` | -50MB (no devDependencies in runtime) |
| `.dockerignore` | Faster builds (smaller context) |
| Layer ordering | Faster rebuilds on code changes |
| Non-root user | Security hardening |
| Health check | Container orchestration readiness |
| Exec form CMD | Proper signal handling |
| No baked secrets | No credential leaks in image layers |

</details>

---

## Exercise 2: Multi-Stage TypeScript Build

**Goal:** Create a multi-stage Dockerfile for a TypeScript Express API with separate build and production stages.

### Requirements

- **Builder stage:** Install all dependencies, compile TypeScript, run type checking
- **Production stage:** Only production node_modules and compiled JavaScript
- **Final image:** Under 120MB, non-root user, health check, exec form entrypoint
- **Bonus:** Use `tini` as PID 1 for proper zombie process reaping

### Steps

1. Use the same project from Exercise 1 (or create a new one).
2. Write a multi-stage Dockerfile with at least 2 stages.
3. Build and verify the type checking works (introduce a type error and confirm the build fails).
4. Measure the final image size.

### Verification

```bash
# Build succeeds with valid TypeScript
docker build -t ts-build:latest .

# Image is under 120MB
docker images ts-build:latest --format "{{.Size}}"

# Introduce a type error in src/server.ts (e.g., const x: number = "string")
# Build should FAIL at the type checking step
docker build -t ts-build:broken .
# → error TS2322: Type 'string' is not assignable to type 'number'

# Verify the image runs correctly
docker run --rm -d -p 3000:3000 --name ts-test ts-build:latest
curl http://localhost:3000/health
docker stop ts-test
```

<details>
<summary>Show solution</summary>

```dockerfile
# syntax=docker/dockerfile:1

# ─── Stage 1: Dependencies ───
FROM node:20-alpine AS deps
WORKDIR /app
COPY package.json package-lock.json ./
RUN --mount=type=cache,target=/root/.npm \
    npm ci

# ─── Stage 2: Build & Type Check ───
FROM deps AS builder
COPY tsconfig.json ./
COPY src/ ./src/

# Type check first (fail fast on type errors)
RUN npx tsc --noEmit

# Then build
RUN npm run build

# ─── Stage 3: Production Dependencies ───
FROM node:20-alpine AS prod-deps
WORKDIR /app
COPY package.json package-lock.json ./
RUN --mount=type=cache,target=/root/.npm \
    npm ci --only=production && npm cache clean --force

# ─── Stage 4: Runtime ───
FROM node:20-alpine AS runtime

# Install tini for proper PID 1 behavior
RUN apk add --no-cache tini

WORKDIR /app

RUN addgroup --system --gid 1001 app && \
    adduser --system --uid 1001 --ingroup app app

# Copy production dependencies and compiled JavaScript
COPY --from=prod-deps --chown=app:app /app/node_modules ./node_modules
COPY --from=builder --chown=app:app /app/dist ./dist
COPY --from=builder --chown=app:app /app/package.json ./

USER app

ENV NODE_ENV=production
EXPOSE 3000

HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:3000/health || exit 1

ENTRYPOINT ["/sbin/tini", "--"]
CMD ["node", "dist/server.js"]
```

```bash
# Build
docker build -t ts-build:latest .

# Check size
docker images ts-build:latest --format "{{.Size}}"
# → ~100MB

# Verify tini is PID 1
docker run --rm ts-build:latest ps aux
# PID   USER  COMMAND
# 1     app   /sbin/tini -- node dist/server.js
# 7     app   node dist/server.js

# Verify type checking catches errors
# Add this to src/server.ts: const x: number = "not a number";
docker build -t ts-build:broken . 2>&1 | tail -5
# → error TS2322: Type 'string' is not assignable to type 'number'.
```

**Why 4 stages?**
1. `deps` — All npm dependencies (shared base for builder)
2. `builder` — Type check + compile (inherits from deps, has devDependencies)
3. `prod-deps` — Production dependencies only (clean install, no devDeps)
4. `runtime` — Final image with only prod deps + compiled JS + tini

BuildKit can parallelize stages 2 and 3 since they're independent, speeding up builds.

</details>

---

## Exercise 3: Network Debugging

**Goal:** Set up containers on a custom bridge network, verify DNS resolution, inspect network traffic, and troubleshoot a connectivity issue.

### Steps

1. **Create a custom bridge network:**

```bash
docker network create --subnet 172.28.0.0/16 debug-exercise-net
```

2. **Start three services:**

```bash
# Web server
docker run -d --name web \
  --network debug-exercise-net \
  nginx:alpine

# Database
docker run -d --name db \
  --network debug-exercise-net \
  -e POSTGRES_PASSWORD=secret \
  postgres:15-alpine

# Client container with networking tools
docker run -d --name client \
  --network debug-exercise-net \
  alpine sleep infinity
```

3. **From the client, verify:**
   - DNS resolution for both `web` and `db`
   - HTTP connectivity to `web` on port 80
   - TCP connectivity to `db` on port 5432
   - The network interfaces and routing table

4. **Troubleshoot:** Disconnect `db` from the network and verify that `client` can still reach `web` but not `db`. Then reconnect and verify.

### Verification

```bash
# All DNS lookups resolve
docker exec client nslookup web
docker exec client nslookup db

# HTTP request succeeds
docker exec client wget -qO- http://web:80 | head -5

# PostgreSQL port is reachable
docker exec client nc -zv db 5432

# After disconnect:
docker network disconnect debug-exercise-net db
docker exec client nc -zv db 5432    # Should fail
docker exec client wget -qO- http://web:80 | head -5   # Should still work

# After reconnect:
docker network connect debug-exercise-net db
docker exec client nc -zv db 5432    # Should work again
```

<details>
<summary>Show solution</summary>

```bash
#!/bin/bash
set -euo pipefail

echo "=== Setup ==="
docker network create --subnet 172.28.0.0/16 debug-exercise-net

docker run -d --name web \
  --network debug-exercise-net \
  nginx:alpine

docker run -d --name db \
  --network debug-exercise-net \
  -e POSTGRES_PASSWORD=secret \
  postgres:15-alpine

docker run -d --name client \
  --network debug-exercise-net \
  alpine sleep infinity

# Install tools in client
docker exec client apk add --no-cache curl bind-tools postgresql-client netcat-openbsd

sleep 5  # let services start

echo ""
echo "=== DNS Resolution ==="
echo "--- web ---"
docker exec client nslookup web
echo "--- db ---"
docker exec client nslookup db

echo ""
echo "=== Network Interfaces ==="
docker exec client ip addr show
echo ""
echo "=== Routing Table ==="
docker exec client ip route show

echo ""
echo "=== HTTP Connectivity (web:80) ==="
docker exec client curl -s -o /dev/null -w "HTTP Status: %{http_code}\n" http://web:80

echo ""
echo "=== TCP Connectivity (db:5432) ==="
docker exec client nc -zv db 5432 2>&1

echo ""
echo "=== PostgreSQL Ready Check ==="
docker exec client pg_isready -h db -U postgres

echo ""
echo "=== Test: Disconnect db from network ==="
docker network disconnect debug-exercise-net db

echo "Attempting to reach db (should fail)..."
docker exec client nc -zv -w 3 db 5432 2>&1 || echo "  ✅ Expected: db unreachable"

echo "Attempting to reach web (should succeed)..."
docker exec client curl -s -o /dev/null -w "  HTTP Status: %{http_code} ✅\n" http://web:80

echo ""
echo "=== Test: Reconnect db ==="
docker network connect debug-exercise-net db
sleep 2

echo "Attempting to reach db (should succeed)..."
docker exec client nc -zv db 5432 2>&1

echo ""
echo "=== Network Inspect ==="
docker network inspect debug-exercise-net --format \
  '{{range .Containers}}  {{.Name}}: {{.IPv4Address}}{{"\n"}}{{end}}'

echo ""
echo "=== Cleanup ==="
docker rm -f web db client
docker network rm debug-exercise-net
echo "Done."
```

**Key learnings:**
1. User-defined bridge networks provide automatic DNS resolution by container name.
2. `docker network disconnect/connect` can modify networking on-the-fly — useful for testing failure scenarios.
3. Network inspection shows all connected containers and their IPs.
4. DNS resolution fails immediately for disconnected containers, while IP-based connections time out — DNS is the right abstraction.

</details>

---

## Exercise 4: Complete Docker Compose Stack

**Goal:** Write a `docker-compose.yml` for the DeployForge stack with API, Worker, PostgreSQL, Redis, and Nginx — including health checks, volume persistence, environment config, and proper startup ordering.

### Requirements

| Service | Image / Build | Ports | Depends On |
|---------|--------------|-------|-----------|
| **nginx** | `nginx:1.25-alpine` | `80:80` | api (healthy) |
| **api** | Build from Dockerfile | `3000:3000` | postgres (healthy), redis (healthy) |
| **worker** | Build from Dockerfile | none | postgres (healthy), redis (healthy) |
| **postgres** | `postgres:15-alpine` | `5432` (internal) | — |
| **redis** | `redis:7-alpine` | `6379` (internal) | — |

Additional:
- Named volumes for PostgreSQL and Redis data
- All services on a custom network
- Health checks on all services
- Environment variables from `.env` file
- Resource limits on all services
- Nginx config mounted as a bind mount

### Steps

1. Create the `docker-compose.yml` from scratch.
2. Create a `.env` file with required variables.
3. Create a minimal `nginx.conf` that proxies to the API.
4. Verify the startup order: postgres/redis start first, then api/worker, then nginx.
5. Verify data persists across `docker compose down` and `docker compose up`.

### Verification

```bash
# Start the stack
docker compose up -d

# All services healthy
docker compose ps
# NAME       SERVICE    STATUS
# nginx      nginx      Up (healthy)
# api        api        Up (healthy)
# worker     worker     Up
# postgres   postgres   Up (healthy)
# redis      redis      Up (healthy)

# Health endpoint responds through nginx
curl http://localhost/health
# → {"status":"ok"}

# Direct API access
curl http://localhost:3000/health

# Data persists
docker compose down
docker compose up -d
docker compose exec postgres psql -U deployforge deployforge_dev -c "SELECT 1;"
# → should work (data volume preserved)

# Full cleanup
docker compose down -v
```

<details>
<summary>Show solution</summary>

**docker-compose.yml:**
```yaml
services:
  # ─── Nginx Reverse Proxy ───
  nginx:
    image: nginx:1.25-alpine
    ports:
      - "80:80"
    volumes:
      - ./config/nginx.conf:/etc/nginx/nginx.conf:ro
    depends_on:
      api:
        condition: service_healthy
    networks:
      - frontend
    healthcheck:
      test: ["CMD", "wget", "--no-verbose", "--tries=1", "--spider", "http://localhost/health"]
      interval: 15s
      timeout: 3s
      retries: 3
      start_period: 10s
    restart: unless-stopped
    deploy:
      resources:
        limits:
          cpus: "0.5"
          memory: 128M

  # ─── API Gateway ───
  api:
    build:
      context: .
      dockerfile: Dockerfile
    ports:
      - "3000:3000"
    env_file:
      - .env
    environment:
      DATABASE_URL: postgres://${POSTGRES_USER:-deployforge}:${POSTGRES_PASSWORD}@postgres:5432/${POSTGRES_DB:-deployforge_dev}
      REDIS_URL: redis://redis:6379
    depends_on:
      postgres:
        condition: service_healthy
      redis:
        condition: service_healthy
    networks:
      - frontend
      - backend
    healthcheck:
      test: ["CMD", "wget", "--no-verbose", "--tries=1", "--spider", "http://localhost:3000/health"]
      interval: 15s
      timeout: 5s
      retries: 3
      start_period: 30s
    restart: unless-stopped
    deploy:
      resources:
        limits:
          cpus: "1.0"
          memory: 512M

  # ─── Worker Service ───
  worker:
    build:
      context: .
      dockerfile: Dockerfile.worker
    env_file:
      - .env
    environment:
      DATABASE_URL: postgres://${POSTGRES_USER:-deployforge}:${POSTGRES_PASSWORD}@postgres:5432/${POSTGRES_DB:-deployforge_dev}
      REDIS_URL: redis://redis:6379
      CONCURRENCY: "5"
    depends_on:
      postgres:
        condition: service_healthy
      redis:
        condition: service_healthy
    networks:
      - backend
    restart: unless-stopped
    deploy:
      resources:
        limits:
          cpus: "1.0"
          memory: 1G

  # ─── PostgreSQL ───
  postgres:
    image: postgres:15-alpine
    environment:
      POSTGRES_USER: ${POSTGRES_USER:-deployforge}
      POSTGRES_PASSWORD: ${POSTGRES_PASSWORD:?Set POSTGRES_PASSWORD in .env}
      POSTGRES_DB: ${POSTGRES_DB:-deployforge_dev}
    volumes:
      - pgdata:/var/lib/postgresql/data
    networks:
      - backend
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U ${POSTGRES_USER:-deployforge}"]
      interval: 5s
      timeout: 3s
      retries: 10
      start_period: 10s
    restart: unless-stopped
    deploy:
      resources:
        limits:
          cpus: "1.0"
          memory: 512M
    shm_size: 256m

  # ─── Redis ───
  redis:
    image: redis:7-alpine
    command: redis-server --appendonly yes --maxmemory 256mb --maxmemory-policy allkeys-lru
    volumes:
      - redisdata:/data
    networks:
      - backend
    healthcheck:
      test: ["CMD", "redis-cli", "ping"]
      interval: 5s
      timeout: 3s
      retries: 10
    restart: unless-stopped
    deploy:
      resources:
        limits:
          cpus: "0.5"
          memory: 512M

networks:
  frontend:
    driver: bridge
  backend:
    driver: bridge
    internal: true

volumes:
  pgdata:
  redisdata:
```

**.env:**
```bash
COMPOSE_PROJECT_NAME=deployforge
POSTGRES_USER=deployforge
POSTGRES_PASSWORD=local-dev-password
POSTGRES_DB=deployforge_dev
NODE_ENV=development
```

**config/nginx.conf:**
```nginx
events {
    worker_connections 1024;
}

http {
    upstream api {
        server api:3000;
    }

    server {
        listen 80;

        location / {
            proxy_pass http://api;
            proxy_set_header Host $host;
            proxy_set_header X-Real-IP $remote_addr;
            proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
            proxy_set_header X-Forwarded-Proto $scheme;
        }

        location /health {
            proxy_pass http://api/health;
        }
    }
}
```

**Startup order verification:**
```bash
docker compose up -d 2>&1
# → Creating network deployforge_backend
# → Creating network deployforge_frontend
# → Creating deployforge-postgres-1  (starts first, no deps)
# → Creating deployforge-redis-1     (starts first, no deps)
# → Waiting for postgres to be healthy...
# → Waiting for redis to be healthy...
# → Creating deployforge-api-1       (after pg+redis healthy)
# → Creating deployforge-worker-1    (after pg+redis healthy)
# → Waiting for api to be healthy...
# → Creating deployforge-nginx-1     (after api healthy)

# Verify all healthy
docker compose ps --format "table {{.Name}}\t{{.Status}}"
```

</details>

---

## Capstone Checkpoint

Before moving to Module 03, verify the following:

### Build Verification

```bash
cd capstone/deployforge

# Build all images
docker compose build

# Start the stack
docker compose up -d

# All services should be healthy within 60 seconds
docker compose ps

# Health endpoints respond
curl http://localhost/health        # through nginx
curl http://localhost:3000/health   # direct API
```

### Data Persistence

```bash
# Create some data
docker compose exec postgres psql -U deployforge deployforge_dev -c "
  CREATE TABLE IF NOT EXISTS checkpoint (msg TEXT);
  INSERT INTO checkpoint VALUES ('Module 02 complete');
"

# Restart everything
docker compose down
docker compose up -d

# Data survived
docker compose exec postgres psql -U deployforge deployforge_dev -c "
  SELECT * FROM checkpoint;
"
# → Module 02 complete
```

### Image Size

```bash
# Check image sizes
docker images deployforge/* --format "table {{.Repository}}\t{{.Tag}}\t{{.Size}}"
# → API Gateway: < 150MB
# → Worker: < 150MB
```

### Cleanup

```bash
# Stop everything and remove volumes
docker compose down -v
```

### Knowledge Check

Before proceeding to Module 03, make sure you can answer:

1. Why should `COPY package*.json` come before `COPY . .` in a Dockerfile?
2. What's the difference between a named volume and a bind mount? When would you use each?
3. Why does `depends_on` with `condition: service_healthy` require a healthcheck on the dependency?
4. How does DNS resolution work between containers on a user-defined bridge network?
5. What happens to data in a named volume when you run `docker compose down`? What about `docker compose down -v`?
6. Why should you use exec form (`["node", "server.js"]`) instead of shell form (`node server.js`) for CMD/ENTRYPOINT?
7. How do multi-stage builds reduce image size without losing build reproducibility?
8. What is the purpose of `tini` or `--init` when running Node.js in a container?
