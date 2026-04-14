# 2.4 — Docker Compose for Development

## Concept

Running five `docker run` commands with the right flags, networks, volumes, and environment variables every time you start your dev environment is tedious and error-prone. Docker Compose defines your entire multi-container application in a single YAML file. One command — `docker compose up` — and everything starts, connected, with proper dependencies, health checks, and persistent storage.

---

## Deep Dive

### Compose File Structure

A `docker-compose.yml` has four top-level sections:

```yaml
# docker-compose.yml

services:     # The containers that make up your application
  api:
    ...
  worker:
    ...
  postgres:
    ...

networks:     # Custom networks for service communication
  app-net:
    ...

volumes:      # Named volumes for persistent data
  pgdata:
    ...

configs:      # External configuration files (Swarm/advanced)
  nginx-conf:
    ...
```

### Service Configuration

Every service maps to one container. Here's a complete reference for common options:

```yaml
services:
  api:
    # Build from Dockerfile
    build:
      context: .
      dockerfile: docker/api-gateway.Dockerfile
      args:
        NODE_VERSION: "20"
      target: production            # target a specific stage in multi-stage build

    # OR use a pre-built image
    image: deployforge/api:1.0.0

    container_name: deployforge-api   # explicit name (otherwise compose generates one)

    # Port mapping
    ports:
      - "3000:3000"                   # host:container
      - "127.0.0.1:9229:9229"        # bind to localhost only (debug port)

    # Environment variables
    environment:
      NODE_ENV: production
      DATABASE_URL: postgres://deployforge:secret@postgres:5432/deployforge_dev
      REDIS_URL: redis://redis:6379

    # OR load from file
    env_file:
      - .env
      - .env.local                    # overrides .env

    # Volumes
    volumes:
      - ./src:/app/src:ro             # bind mount (development)
      - node_modules:/app/node_modules # named volume (performance)

    # Networking
    networks:
      - app-net

    # Dependencies
    depends_on:
      postgres:
        condition: service_healthy
      redis:
        condition: service_healthy

    # Resource limits
    deploy:
      resources:
        limits:
          cpus: "1.0"
          memory: 512M
        reservations:
          cpus: "0.25"
          memory: 128M

    # Health check
    healthcheck:
      test: ["CMD", "wget", "--no-verbose", "--tries=1", "--spider", "http://localhost:3000/health"]
      interval: 15s
      timeout: 5s
      retries: 3
      start_period: 30s

    # Restart policy
    restart: unless-stopped
```

### Service Dependencies with depends_on

`depends_on` controls startup order, but by default only waits for the container to _start_, not to be _ready_:

```yaml
services:
  api:
    depends_on:
      postgres:
        condition: service_healthy      # Wait for health check to pass
      redis:
        condition: service_healthy
      worker:
        condition: service_started      # Just wait for container start (default)

  postgres:
    image: postgres:15-alpine
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U deployforge"]
      interval: 5s
      timeout: 3s
      retries: 10
      start_period: 10s

  redis:
    image: redis:7-alpine
    healthcheck:
      test: ["CMD", "redis-cli", "ping"]
      interval: 5s
      timeout: 3s
      retries: 10
```

```
┌─────────────────────────────────────────────────────┐
│            Startup Dependency Graph                   │
│                                                       │
│  postgres ──(healthy)──┐                              │
│                        ├──▶ api ──(started)──▶ nginx  │
│  redis ────(healthy)──┘                               │
│                                                       │
│  worker (independent, also waits for redis+postgres)  │
└─────────────────────────────────────────────────────┘
```

> **Important:** `depends_on` with `condition: service_healthy` requires the dependency to have a `healthcheck` defined. Without it, Compose can't determine if the service is ready and will error.

### Environment Variables and .env Files

Compose supports multiple ways to inject environment variables:

```yaml
services:
  api:
    # Inline (visible in docker-compose.yml)
    environment:
      NODE_ENV: production
      PORT: "3000"

    # From file
    env_file:
      - .env            # defaults
      - .env.local      # overrides (gitignored)
```

```bash
# .env (committed to repo — no secrets)
COMPOSE_PROJECT_NAME=deployforge
POSTGRES_USER=deployforge
POSTGRES_DB=deployforge_dev
NODE_ENV=development

# .env.local (gitignored — secrets and overrides)
POSTGRES_PASSWORD=my-local-password
JWT_SECRET=dev-jwt-secret-change-in-prod
```

**Variable substitution** in `docker-compose.yml`:

```yaml
services:
  postgres:
    image: postgres:${POSTGRES_VERSION:-15}    # default if unset
    environment:
      POSTGRES_PASSWORD: ${POSTGRES_PASSWORD:?Error: set POSTGRES_PASSWORD}  # required
```

### Build Configuration in Compose

```yaml
services:
  api:
    build:
      context: .                            # build context path
      dockerfile: docker/api-gateway.Dockerfile
      args:
        NODE_VERSION: "20"
        APP_VERSION: "${APP_VERSION:-dev}"
      target: production                    # multi-stage target
      cache_from:
        - deployforge/api:latest            # use remote image as cache
      labels:
        com.deployforge.service: api-gateway
      secrets:
        - npmrc                             # BuildKit secret

    image: deployforge/api:${TAG:-latest}   # tag the built image

secrets:
  npmrc:
    file: ~/.npmrc                          # secret source
```

```bash
# Build all services
docker compose build

# Build a specific service
docker compose build api

# Build with no cache
docker compose build --no-cache

# Build and start
docker compose up --build
```

### Profiles for Optional Services

Profiles let you define services that only start when explicitly requested — perfect for optional tooling:

```yaml
services:
  api:
    # No profile — always starts
    build: .

  postgres:
    # No profile — always starts
    image: postgres:15-alpine

  # Only starts with --profile debug
  pgadmin:
    image: dpage/pgadmin4
    profiles: ["debug"]
    ports:
      - "5050:80"
    environment:
      PGADMIN_DEFAULT_EMAIL: admin@local.dev
      PGADMIN_DEFAULT_PASSWORD: admin

  # Only starts with --profile monitoring
  prometheus:
    image: prom/prometheus
    profiles: ["monitoring"]
    ports:
      - "9090:9090"

  grafana:
    image: grafana/grafana
    profiles: ["monitoring"]
    ports:
      - "3001:3000"
```

```bash
# Start core services only
docker compose up

# Start with database admin
docker compose --profile debug up

# Start with monitoring
docker compose --profile monitoring up

# Start everything
docker compose --profile debug --profile monitoring up
```

### Watch Mode and Live Reloading (Compose Watch)

Docker Compose 2.22+ supports `watch` for automatic rebuild and sync:

```yaml
services:
  api:
    build:
      context: .
      dockerfile: docker/api-gateway.Dockerfile
      target: development
    develop:
      watch:
        # Sync source files into container (fast, no rebuild)
        - action: sync
          path: ./src
          target: /app/src
          ignore:
            - node_modules/

        # Rebuild when dependencies change
        - action: rebuild
          path: ./package.json

        # Rebuild when Dockerfile changes
        - action: rebuild
          path: ./docker/api-gateway.Dockerfile
```

```bash
# Start with watch mode
docker compose watch

# Or with up
docker compose up --watch
```

Watch actions:
| Action | Trigger | Effect |
|--------|---------|--------|
| `sync` | File change matches path | Files synced into running container (no restart) |
| `rebuild` | File change matches path | Service is rebuilt and restarted |
| `sync+restart` | File change matches path | Files synced + container restarted |

### Override Files

Compose automatically loads `docker-compose.override.yml` if it exists. Use this for development-specific config:

```yaml
# docker-compose.yml — base (committed)
services:
  api:
    build:
      context: .
      target: production
    ports:
      - "3000:3000"

  postgres:
    image: postgres:15-alpine
    volumes:
      - pgdata:/var/lib/postgresql/data
```

```yaml
# docker-compose.override.yml — development overrides (committed or gitignored)
services:
  api:
    build:
      target: development              # use dev stage instead
    ports:
      - "3000:3000"
      - "9229:9229"                    # Node.js debugger port
    volumes:
      - ./src:/app/src                 # bind mount for hot reload
    environment:
      NODE_ENV: development
      LOG_LEVEL: debug
    command: ["npx", "tsx", "watch", "src/server.ts"]

  postgres:
    ports:
      - "5432:5432"                    # expose to host for local tooling
    environment:
      POSTGRES_PASSWORD: dev-password
```

```bash
# Loads docker-compose.yml + docker-compose.override.yml automatically
docker compose up

# Use a specific override file
docker compose -f docker-compose.yml -f docker-compose.prod.yml up

# Skip override file
docker compose -f docker-compose.yml up
```

### Resource Limits in Compose

```yaml
services:
  api:
    deploy:
      resources:
        limits:
          cpus: "1.0"        # max 1 CPU core
          memory: 512M       # max 512MB RAM
        reservations:
          cpus: "0.25"       # guaranteed 0.25 CPU cores
          memory: 128M       # guaranteed 128MB RAM

  postgres:
    deploy:
      resources:
        limits:
          cpus: "2.0"
          memory: 1G
    shm_size: 256m            # shared memory for PostgreSQL
```

> **Note:** `deploy.resources` works with `docker compose up` since Compose V2. In V1, you needed `mem_limit` and `cpus` at the service level.

### Networking in Compose

By default, Compose creates a single network named `<project>_default` and attaches all services to it:

```yaml
# Explicit network configuration
services:
  api:
    networks:
      - frontend
      - backend

  nginx:
    networks:
      - frontend

  postgres:
    networks:
      - backend

  redis:
    networks:
      - backend

networks:
  frontend:
    driver: bridge
  backend:
    driver: bridge
    internal: true            # no external access — containers only
```

```
┌─────────────────────────────────────────────────────┐
│                 Network Isolation                     │
│                                                       │
│  frontend network                backend network      │
│  ┌─────────────────────┐  ┌─────────────────────────┐│
│  │                     │  │                         ││
│  │  nginx ◄──── api ────────▶ postgres             ││
│  │              │      │  │   redis                 ││
│  │              │      │  │                         ││
│  └──────────────┼──────┘  └─────────────────────────┘│
│                 │                                     │
│           Host :80                                    │
│  (postgres/redis NOT reachable from host)             │
└─────────────────────────────────────────────────────┘
```

Network aliases are automatic — each service is reachable by its service name within its networks.

---

## Code Examples

### Example 1: Complete DeployForge docker-compose.yml

```yaml
# docker-compose.yml
name: deployforge

services:
  # ─── API Gateway ───
  api:
    build:
      context: .
      dockerfile: docker/api-gateway.Dockerfile
      target: production
    image: deployforge/api:${TAG:-latest}
    ports:
      - "3000:3000"
    environment:
      NODE_ENV: ${NODE_ENV:-production}
      PORT: "3000"
      DATABASE_URL: postgres://${POSTGRES_USER:-deployforge}:${POSTGRES_PASSWORD:?Set POSTGRES_PASSWORD}@postgres:5432/${POSTGRES_DB:-deployforge_dev}
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
      dockerfile: docker/worker.Dockerfile
      target: production
    image: deployforge/worker:${TAG:-latest}
    environment:
      NODE_ENV: ${NODE_ENV:-production}
      DATABASE_URL: postgres://${POSTGRES_USER:-deployforge}:${POSTGRES_PASSWORD:?Set POSTGRES_PASSWORD}@postgres:5432/${POSTGRES_DB:-deployforge_dev}
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
      POSTGRES_PASSWORD: ${POSTGRES_PASSWORD:?Set POSTGRES_PASSWORD}
      POSTGRES_DB: ${POSTGRES_DB:-deployforge_dev}
    volumes:
      - pgdata:/var/lib/postgresql/data
      - ./scripts/init-db.sql:/docker-entrypoint-initdb.d/01-init.sql:ro
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
          cpus: "2.0"
          memory: 1G
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
      test: ["CMD", "wget", "--no-verbose", "--tries=1", "--spider", "http://localhost:80/health"]
      interval: 15s
      timeout: 3s
      retries: 3
    restart: unless-stopped

networks:
  frontend:
    driver: bridge
  backend:
    driver: bridge
    internal: true

volumes:
  pgdata:
    driver: local
  redisdata:
    driver: local
```

### Example 2: Development Override File

```yaml
# docker-compose.override.yml — loaded automatically, for local development
services:
  api:
    build:
      target: development
    ports:
      - "3000:3000"
      - "127.0.0.1:9229:9229"      # Node.js debugger
    volumes:
      - ./src:/app/src
      - ./package.json:/app/package.json:ro
      - api_node_modules:/app/node_modules  # avoid slow host mount
    environment:
      NODE_ENV: development
      LOG_LEVEL: debug
    command: ["npx", "tsx", "watch", "--inspect=0.0.0.0:9229", "src/server.ts"]

  worker:
    build:
      target: development
    volumes:
      - ./src:/app/src
      - worker_node_modules:/app/node_modules
    environment:
      NODE_ENV: development
      LOG_LEVEL: debug
      CONCURRENCY: "1"
    command: ["npx", "tsx", "watch", "src/worker.ts"]

  postgres:
    ports:
      - "127.0.0.1:5432:5432"      # accessible from host for DB tooling

  redis:
    ports:
      - "127.0.0.1:6379:6379"      # accessible from host for redis-cli

volumes:
  api_node_modules:
  worker_node_modules:
```

### Example 3: Common Compose Commands

```bash
# Start everything (detached)
docker compose up -d

# Start and rebuild if Dockerfiles changed
docker compose up -d --build

# View running services
docker compose ps

# View logs (follow mode)
docker compose logs -f api worker

# View logs for a specific service
docker compose logs --tail 50 postgres

# Execute a command in a running service
docker compose exec api sh
docker compose exec postgres psql -U deployforge deployforge_dev

# Run a one-off command (starts a new container)
docker compose run --rm api npm test

# Stop services (keeps volumes)
docker compose stop

# Stop and remove containers + networks (keeps volumes)
docker compose down

# Stop and remove everything INCLUDING volumes
docker compose down -v

# Restart a single service
docker compose restart api

# Scale a service (if no port conflicts)
docker compose up -d --scale worker=3

# View resource usage
docker compose top
docker stats
```

---

## Try It Yourself

### Challenge 1: Fix the Broken Compose File

This `docker-compose.yml` has several issues. Find and fix them:

```yaml
version: "3"
services:
  app:
    build: .
    ports:
      - "3000"
    depends_on:
      - db
    environment:
      DB_HOST: localhost
      DB_PORT: 5432

  db:
    image: postgres
    environment:
      - POSTGRES_PASSWORD
    volumes:
      - /var/lib/postgresql/data
```

<details>
<summary>Show solution</summary>

Issues and fixes:

```yaml
# ❌ "version" is obsolete in Compose V2+ (not an error, but unnecessary)
# ❌ "3000" without host port means random mapping — probably not intended
# ❌ depends_on without condition doesn't wait for db readiness
# ❌ DB_HOST: localhost — the db service isn't on localhost, it's on the "db" hostname
# ❌ POSTGRES_PASSWORD not set — will fail
# ❌ image: postgres — no version tag, not reproducible
# ❌ volume is anonymous (not named) — harder to manage and identify

# Fixed:
services:
  app:
    build: .
    ports:
      - "3000:3000"
    depends_on:
      db:
        condition: service_healthy
    environment:
      DB_HOST: db                         # ← service name, not localhost
      DB_PORT: "5432"
    networks:
      - app-net

  db:
    image: postgres:15-alpine             # ← pinned version
    environment:
      POSTGRES_PASSWORD: ${POSTGRES_PASSWORD:?Set POSTGRES_PASSWORD in .env}
    volumes:
      - pgdata:/var/lib/postgresql/data   # ← named volume
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U postgres"]
      interval: 5s
      timeout: 3s
      retries: 10
    networks:
      - app-net

networks:
  app-net:
    driver: bridge

volumes:
  pgdata:                                 # ← declared named volume
```

Key lessons:
1. Service names ARE the hostnames inside the network — use `db`, not `localhost`
2. Always pin image versions for reproducibility
3. Use named volumes so you can find and manage them
4. Add health checks + `condition: service_healthy` for proper startup ordering
5. Remove the obsolete `version` field

</details>

### Challenge 2: Write a Compose File from Scratch

Create a `docker-compose.yml` for a simple web app with:
- A Node.js API service (build from Dockerfile)
- A PostgreSQL database with a health check
- A Redis cache with a health check
- Named volumes for both data stores
- A custom bridge network
- The API waits for both dependencies to be healthy
- Environment variables loaded from `.env`
- Resource limits on all services

<details>
<summary>Show solution</summary>

```yaml
# docker-compose.yml
services:
  api:
    build:
      context: .
      dockerfile: Dockerfile
    ports:
      - "${API_PORT:-3000}:3000"
    env_file:
      - .env
    environment:
      DATABASE_URL: postgres://${DB_USER:-app}:${DB_PASSWORD:?Set DB_PASSWORD}@postgres:5432/${DB_NAME:-app_dev}
      REDIS_URL: redis://redis:6379
    depends_on:
      postgres:
        condition: service_healthy
      redis:
        condition: service_healthy
    networks:
      - app-net
    healthcheck:
      test: ["CMD", "wget", "--no-verbose", "--tries=1", "--spider", "http://localhost:3000/health"]
      interval: 15s
      timeout: 5s
      retries: 3
      start_period: 20s
    restart: unless-stopped
    deploy:
      resources:
        limits:
          cpus: "1.0"
          memory: 512M
        reservations:
          cpus: "0.25"
          memory: 128M

  postgres:
    image: postgres:15-alpine
    environment:
      POSTGRES_USER: ${DB_USER:-app}
      POSTGRES_PASSWORD: ${DB_PASSWORD:?Set DB_PASSWORD}
      POSTGRES_DB: ${DB_NAME:-app_dev}
    volumes:
      - pgdata:/var/lib/postgresql/data
    networks:
      - app-net
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U ${DB_USER:-app}"]
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

  redis:
    image: redis:7-alpine
    command: redis-server --appendonly yes
    volumes:
      - redisdata:/data
    networks:
      - app-net
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
          memory: 256M

networks:
  app-net:
    driver: bridge

volumes:
  pgdata:
  redisdata:
```

```bash
# .env
DB_USER=app
DB_PASSWORD=local-dev-password
DB_NAME=app_dev
API_PORT=3000
NODE_ENV=development
```

```bash
# Test it
docker compose up -d
docker compose ps          # all services should be healthy
curl http://localhost:3000/health
docker compose logs -f
docker compose down
docker compose down -v     # remove volumes too
```

</details>

---

## Capstone Connection

**DeployForge's `docker-compose.yml`** is the backbone of local development:

- **Full stack in one command** — `docker compose up` starts API Gateway, Worker, PostgreSQL, Redis, and Nginx. New team members go from `git clone` to a running app in under 2 minutes. No "install PostgreSQL on your machine" or "make sure you have Redis 7."
- **Health-based startup ordering** — The API Gateway won't start until PostgreSQL passes `pg_isready` and Redis responds to `ping`. No more race conditions where the API crashes because the database isn't ready yet.
- **Development override** — `docker-compose.override.yml` swaps production targets for development targets, adds bind mounts for hot-reload, exposes debug ports (`:9229` for Node.js inspector), and lowers concurrency for easier debugging.
- **Profiles** — `--profile debug` adds pgAdmin for database inspection. `--profile monitoring` adds Prometheus and Grafana (used in Module 08). These don't start by default, keeping the base `docker compose up` fast and lightweight.
- **Environment management** — `.env` has defaults, `.env.local` (gitignored) has overrides and secrets. The `${VAR:?error}` syntax ensures required variables are set before starting.
- **Named volumes** — `pgdata` and `redisdata` survive `docker compose down`. Your test data persists across restarts. `docker compose down -v` gives you a clean slate when needed.

This Compose setup is the foundation for everything that follows. In Module 03 (Container Security), we'll harden the images. In Module 04 (Kubernetes Architecture), we'll translate this Compose file into Kubernetes manifests. The service names, networking patterns, and health checks carry forward directly.
