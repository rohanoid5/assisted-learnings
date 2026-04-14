# 2.1 — Dockerfile Best Practices

## Concept

A Dockerfile is deceptively simple — it's just a list of instructions. But the difference between a naive Dockerfile and a production-grade one is the difference between a 1.2GB image that takes 10 minutes to build and a 90MB image that builds in seconds (on cache hit). This topic covers the patterns senior engineers use to write Dockerfiles that are small, fast to build, secure, and debuggable.

---

## Deep Dive

### Instruction Ordering for Layer Caching

Docker builds images layer by layer. Each instruction creates a new layer. **If a layer's inputs haven't changed, Docker reuses the cached version.** The cache is invalidated from the first changed layer onward.

```
┌─────────────────────────────────────────────────────────┐
│                    Build Cache Flow                      │
│                                                          │
│  FROM node:20-alpine          ← cached (base image)     │
│  WORKDIR /app                 ← cached                   │
│  COPY package*.json ./        ← cached (if unchanged)   │
│  RUN npm ci                   ← cached (if pkg unchanged)│
│  COPY . .                     ← INVALIDATED (code change)│
│  RUN npm run build            ← INVALIDATED (rebuild)    │
│                                                          │
│  Rule: Put things that change LEAST at the TOP.          │
└─────────────────────────────────────────────────────────┘
```

**The golden rule:** order instructions from least-frequently-changed to most-frequently-changed. Dependencies change less often than application code, so install dependencies _before_ copying source code.

### Choosing Base Images

| Base Image | Size | Use Case | Security Posture |
|-----------|------|----------|-----------------|
| `ubuntu:22.04` | ~77MB | Familiar environment, needs apt packages | Larger attack surface |
| `node:20` | ~1.1GB | Full development environment | Huge attack surface |
| `node:20-slim` | ~220MB | Node.js without extras | Moderate |
| `node:20-alpine` | ~130MB | Minimal Node.js | Small attack surface, musl libc |
| `gcr.io/distroless/nodejs20` | ~130MB | No shell, no package manager | Minimal attack surface |
| `scratch` | 0MB | Static binaries only | Zero attack surface |

```
┌─────────────────────────────────────────────────────┐
│               Image Size Comparison                  │
│                                                      │
│  node:20          ███████████████████████████ 1.1GB  │
│  node:20-slim     █████░░░░░░░░░░░░░░░░░░░░  220MB  │
│  node:20-alpine   ███░░░░░░░░░░░░░░░░░░░░░░  130MB  │
│  distroless       ███░░░░░░░░░░░░░░░░░░░░░░  130MB  │
│  scratch          ░░░░░░░░░░░░░░░░░░░░░░░░░    0MB  │
└─────────────────────────────────────────────────────┘
```

> **Alpine caveat:** Alpine uses musl libc instead of glibc. Most Node.js apps work fine, but native modules (e.g., `bcrypt`, `sharp`) may need recompilation or `--platform linux/amd64` builds. If you hit segfaults or missing symbols, try `-slim` instead.

### The .dockerignore File

Like `.gitignore` for Docker builds. Without it, `COPY . .` sends everything — including `node_modules`, `.git`, and test files — to the Docker daemon as build context.

```dockerignore
# .dockerignore
node_modules
npm-debug.log*
.git
.gitignore
.env
.env.*
*.md
!README.md
docker-compose*.yml
Dockerfile*
.dockerignore
coverage/
.nyc_output/
test/
tests/
__tests__/
*.test.ts
*.spec.ts
.vscode/
.idea/
```

**Impact:** A typical Node.js project with a 500MB `node_modules` and a 200MB `.git` folder sends 700MB+ of useless data to the daemon without `.dockerignore`. With it, the build context drops to a few MB.

### COPY vs ADD

| Feature | `COPY` | `ADD` |
|---------|--------|-------|
| Copy files from build context | ✅ | ✅ |
| Auto-extract tar archives | ❌ | ✅ |
| Fetch remote URLs | ❌ | ✅ (but don't use this) |
| Predictable behavior | ✅ | ❌ |

**Rule:** Always use `COPY` unless you specifically need tar extraction. `ADD` has surprising behaviors (auto-extracting `.tar.gz` files) that can cause hard-to-debug issues.

```dockerfile
# ✅ Good — explicit and predictable
COPY package.json package-lock.json ./
COPY src/ ./src/

# ❌ Bad — might auto-extract if file looks like an archive
ADD app.tar.gz /app/

# The one valid ADD use case: extracting a local archive
ADD rootfs.tar.gz /
```

### RUN Instruction Optimization

Each `RUN` creates a layer. Files deleted in a later `RUN` still exist in the previous layer's storage. Combine related commands and clean up in the same `RUN`.

```dockerfile
# ❌ Bad — 3 layers, apt cache persists in layer 1
RUN apt-get update
RUN apt-get install -y curl wget
RUN rm -rf /var/lib/apt/lists/*

# ✅ Good — 1 layer, cache cleaned in the same layer
RUN apt-get update && \
    apt-get install -y --no-install-recommends \
      curl \
      wget \
    && rm -rf /var/lib/apt/lists/*
```

> **Why `--no-install-recommends`?** Debian/Ubuntu packages have "Recommends" dependencies that get installed by default. For `curl`, that might pull in `libsasl2-modules`, `publicsuffix`, and other packages you don't need. `--no-install-recommends` can cut hundreds of MB.

### Health Checks with HEALTHCHECK

Docker can check if your container is healthy, not just running. This is critical for orchestrators (Compose, Swarm, Kubernetes liveness probes).

```dockerfile
HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
  CMD curl -f http://localhost:3000/health || exit 1
```

| Parameter | Default | Description |
|-----------|---------|-------------|
| `--interval` | 30s | Time between checks |
| `--timeout` | 30s | Max time for a single check |
| `--start-period` | 0s | Grace period for container startup |
| `--retries` | 3 | Consecutive failures before "unhealthy" |
| `--start-interval` | 5s | (Docker 25+) Interval during start period |

```bash
# Check health status
docker inspect --format='{{.State.Health.Status}}' my-container
# → healthy | unhealthy | starting

# View health check logs
docker inspect --format='{{json .State.Health}}' my-container | jq .
```

> **Tip for Node.js:** Avoid `curl` in health checks for alpine/distroless images — it might not be installed. Use a Node.js script or `wget` instead:
> ```dockerfile
> HEALTHCHECK CMD node -e "fetch('http://localhost:3000/health').then(r => {if(!r.ok) throw r.status})" || exit 1
> ```

### Labels and Metadata (OCI Annotations)

Labels attach metadata to images for tracking, filtering, and compliance. The OCI annotation standard provides well-known keys.

```dockerfile
LABEL org.opencontainers.image.title="DeployForge API Gateway"
LABEL org.opencontainers.image.description="REST API for deployment management"
LABEL org.opencontainers.image.version="1.0.0"
LABEL org.opencontainers.image.authors="team@deployforge.dev"
LABEL org.opencontainers.image.source="https://github.com/deployforge/api-gateway"
LABEL org.opencontainers.image.created="${BUILD_DATE}"
LABEL org.opencontainers.image.revision="${VCS_REF}"
```

### USER Instruction for Non-Root Execution

Running as root inside a container is a security risk. If an attacker escapes the container, they're root on the host (without user namespaces). Always drop to a non-root user.

```dockerfile
# Create a non-root user
RUN addgroup --system --gid 1001 appgroup && \
    adduser --system --uid 1001 --ingroup appgroup appuser

# Set ownership of app files
COPY --chown=appuser:appgroup . .

# Switch to non-root user — all subsequent instructions run as this user
USER appuser

# Now the process runs as UID 1001
CMD ["node", "dist/server.js"]
```

```bash
# Verify
docker run --rm my-image whoami
# → appuser

docker run --rm my-image id
# → uid=1001(appuser) gid=1001(appgroup) groups=1001(appgroup)
```

### ARG vs ENV

| Feature | `ARG` | `ENV` |
|---------|-------|-------|
| Available during build | ✅ | ✅ |
| Available at runtime | ❌ | ✅ |
| Visible in `docker inspect` | ❌ (after build) | ✅ |
| Can be overridden | `--build-arg` | `-e` / `--env` |
| Persists in image | ❌ | ✅ |

```dockerfile
# ARG — build-time only (e.g., version to install)
ARG NODE_VERSION=20

# Use in FROM
FROM node:${NODE_VERSION}-alpine

# ENV — persists at runtime
ENV NODE_ENV=production
ENV PORT=3000

# Common pattern: ARG to inject, ENV to persist
ARG APP_VERSION=0.0.0
ENV APP_VERSION=${APP_VERSION}
```

> **Security warning:** Never pass secrets via `ARG`. Build args are visible in `docker history`. Use BuildKit secret mounts instead (covered in 02-multi-stage-builds.md).

### ENTRYPOINT vs CMD

| Pattern | `ENTRYPOINT` | `CMD` | `docker run my-image` | `docker run my-image /bin/sh` |
|---------|-------------|-------|----------------------|-------------------------------|
| CMD only | — | `["node", "server.js"]` | `node server.js` | `/bin/sh` (CMD replaced) |
| ENTRYPOINT only | `["node"]` | — | `node` | `node /bin/sh` (appended) |
| Both (recommended) | `["node"]` | `["server.js"]` | `node server.js` | `node /bin/sh` (CMD replaced) |

```dockerfile
# ❌ Shell form — runs via /bin/sh -c, PID 1 is the shell, signals don't propagate
ENTRYPOINT node server.js

# ✅ Exec form — node IS PID 1, handles SIGTERM for graceful shutdown
ENTRYPOINT ["node", "server.js"]

# ✅ Best pattern — ENTRYPOINT for the executable, CMD for default args
ENTRYPOINT ["node"]
CMD ["dist/server.js"]
```

> **Critical for production:** If PID 1 is a shell (`/bin/sh -c`), it won't forward signals like SIGTERM. Your app won't shut down gracefully, and Kubernetes will have to `SIGKILL` it after the grace period. Always use exec form.

---

## Code Examples

### Example 1: Naive Dockerfile → Optimized Dockerfile

**Before (1.2GB, slow builds, root user, no health check):**

```dockerfile
# ❌ Bad: node:20 is 1.1GB, plus node_modules adds another 200MB+
FROM node:20

WORKDIR /app

# ❌ Bad: copies everything including node_modules, .git, tests
COPY . .

# ❌ Bad: npm install is non-deterministic, includes devDependencies
RUN npm install

# ❌ Bad: compiles on every build even if only README changed
RUN npm run build

# ❌ Bad: running as root
# ❌ Bad: no health check
# ❌ Bad: shell form CMD
CMD npm start
```

**After (< 100MB, fast builds, non-root, health checked):**

```dockerfile
FROM node:20-alpine AS base

# Labels for image metadata
LABEL org.opencontainers.image.title="DeployForge API Gateway"
LABEL org.opencontainers.image.version="1.0.0"

WORKDIR /app

# Non-root user
RUN addgroup --system --gid 1001 appgroup && \
    adduser --system --uid 1001 --ingroup appgroup appuser

# Install dependencies first (cached unless package*.json changes)
COPY package.json package-lock.json ./
RUN npm ci --only=production && npm cache clean --force

# Copy source code (changes frequently — cache busted here)
COPY --chown=appuser:appgroup src/ ./src/
COPY --chown=appuser:appgroup tsconfig.json ./

# Build TypeScript
RUN npm run build

# Drop to non-root
USER appuser

# Expose port (documentation, not enforcement)
EXPOSE 3000

# Health check
HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:3000/health || exit 1

# Exec form for proper signal handling
ENTRYPOINT ["node"]
CMD ["dist/server.js"]
```

### Example 2: .dockerignore in Practice

```bash
# See the impact of .dockerignore
# Without .dockerignore:
docker build --no-cache -t test-no-ignore .
# Sending build context to Docker daemon  723.4MB  ← yikes

# Create .dockerignore (see the list above)
# With .dockerignore:
docker build --no-cache -t test-with-ignore .
# Sending build context to Docker daemon  2.1MB    ← much better
```

### Example 3: Health Check Patterns

```dockerfile
# HTTP health check (most common for web services)
HEALTHCHECK --interval=15s --timeout=3s --start-period=30s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:3000/health || exit 1

# TCP health check (for services without HTTP endpoints)
HEALTHCHECK --interval=15s --timeout=3s --retries=3 \
  CMD nc -z localhost 5432 || exit 1

# Custom script health check (complex readiness logic)
COPY healthcheck.sh /usr/local/bin/
HEALTHCHECK --interval=30s --timeout=10s --retries=3 \
  CMD ["healthcheck.sh"]
```

### Example 4: ARG for Flexible Builds

```dockerfile
ARG NODE_VERSION=20
ARG ALPINE_VERSION=3.19
FROM node:${NODE_VERSION}-alpine${ALPINE_VERSION}

ARG APP_VERSION=0.0.0
ARG BUILD_DATE
ARG VCS_REF

LABEL org.opencontainers.image.version="${APP_VERSION}"
LABEL org.opencontainers.image.created="${BUILD_DATE}"
LABEL org.opencontainers.image.revision="${VCS_REF}"

ENV APP_VERSION=${APP_VERSION}

WORKDIR /app
COPY . .
RUN npm ci && npm run build

CMD ["node", "dist/server.js"]
```

```bash
# Build with custom args
docker build \
  --build-arg APP_VERSION=2.1.0 \
  --build-arg BUILD_DATE=$(date -u +'%Y-%m-%dT%H:%M:%SZ') \
  --build-arg VCS_REF=$(git rev-parse --short HEAD) \
  -t deployforge-api:2.1.0 .

# Verify labels
docker inspect deployforge-api:2.1.0 | jq '.[0].Config.Labels'
```

---

## Try It Yourself

### Challenge 1: Fix the Bad Dockerfile

This Dockerfile has at least 8 problems. Find and fix all of them:

```dockerfile
FROM node:latest
ADD . /app
WORKDIR /app
RUN npm install
RUN apt-get update
RUN apt-get install -y curl
ENV SECRET_KEY=super-secret-123
EXPOSE 3000
CMD node server.js
```

<details>
<summary>Show solution</summary>

Problems:
1. `node:latest` — unpinned tag, non-reproducible builds, huge image
2. `ADD` instead of `COPY` — unpredictable behavior
3. `ADD . /app` before `WORKDIR` — no `.dockerignore`, copies everything
4. `npm install` — non-deterministic, includes devDependencies
5. Separate `RUN` for apt commands — extra layers, no cleanup
6. Dependencies installed after app code — cache busted on every code change
7. `SECRET_KEY` in ENV — visible in image history
8. Shell form CMD — signals won't propagate
9. Running as root
10. No health check

Fixed:

```dockerfile
FROM node:20-alpine

WORKDIR /app

RUN addgroup --system --gid 1001 appgroup && \
    adduser --system --uid 1001 --ingroup appgroup appuser

# Install curl if needed for health checks (alpine uses apk, not apt)
RUN apk add --no-cache curl

# Dependencies first for layer caching
COPY package.json package-lock.json ./
RUN npm ci --only=production && npm cache clean --force

# App code last
COPY --chown=appuser:appgroup . .

USER appuser

EXPOSE 3000

HEALTHCHECK --interval=30s --timeout=5s --retries=3 \
  CMD curl -f http://localhost:3000/health || exit 1

# Pass secrets at runtime via -e or secrets management, not baked into the image
ENTRYPOINT ["node"]
CMD ["server.js"]
```

</details>

### Challenge 2: Layer Cache Detective

Build the optimized Dockerfile from Example 1. Then:
1. Change a line in `src/server.ts` and rebuild — which layers were cached?
2. Change a dependency in `package.json` and rebuild — which layers were cached?
3. Use `docker history` to see each layer's size.

<details>
<summary>Show solution</summary>

```bash
# Initial build
docker build -t cache-test:v1 .
# → All layers built from scratch

# After changing src/server.ts:
docker build -t cache-test:v2 .
# → FROM, WORKDIR, RUN addgroup, COPY package*.json, RUN npm ci — all CACHED
# → COPY src/ — INVALIDATED (source changed)
# → RUN npm run build — REBUILT
# → Everything below — REBUILT

# After changing package.json:
docker build -t cache-test:v3 .
# → FROM, WORKDIR, RUN addgroup — CACHED
# → COPY package*.json — INVALIDATED (dependency changed)
# → RUN npm ci — REBUILT (new dependencies)
# → Everything below — REBUILT

# Inspect layer sizes
docker history cache-test:v1

# IMAGE          CREATED          SIZE     COMMENT
# abc123         2 min ago        0B       CMD ["dist/server.js"]
# def456         2 min ago        0B       HEALTHCHECK ...
# 789abc         2 min ago        4.2MB    RUN npm run build
# ...            3 min ago        45MB     RUN npm ci
# ...            3 min ago        234B     COPY package*.json
# ...            5 min ago        1.4kB    RUN addgroup ...

# Key insight: npm ci (45MB) is only rebuilt when dependencies change.
# Source code changes only rebuild the small build layer (4.2MB).
```

</details>

---

## Capstone Connection

**DeployForge's API Gateway Dockerfile** uses every practice from this topic:

- **Layer ordering** — `COPY package*.json` before `COPY src/` so dependency installs are cached across code changes. The API Gateway has ~150 npm dependencies; reinstalling them on every code change would add 60+ seconds to builds.
- **Alpine base image** — `node:20-alpine` keeps the base image under 130MB. The final production image with compiled TypeScript and production dependencies is under 100MB.
- **Non-root user** — The API Gateway runs as `appuser:appgroup` (UID 1001). When we deploy to Kubernetes in Module 05, the `securityContext.runAsNonRoot: true` policy will enforce this.
- **HEALTHCHECK** — `GET /health` returns service status and dependency connectivity. Docker Compose `depends_on` with `condition: service_healthy` uses this to ensure PostgreSQL and Redis are ready before starting the API.
- **Exec form ENTRYPOINT** — `["node", "dist/server.js"]` ensures Node.js is PID 1 and receives SIGTERM for graceful shutdown. This matters in Module 09 (Reliability Engineering) when we implement graceful drain on deployment rollouts.

In the next topic (2.2), we'll add multi-stage builds so the TypeScript compiler and dev dependencies never make it into the production image.
