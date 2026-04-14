# 2.2 — Multi-Stage Builds & Image Optimization

## Concept

A production container doesn't need your compiler, your dev dependencies, or your test framework. Multi-stage builds let you use one stage to _build_ your app and a separate stage to _run_ it — the final image contains only what's needed at runtime. For a TypeScript project, this means the TypeScript compiler, `@types/*` packages, and source `.ts` files never ship to production.

---

## Deep Dive

### Why Multi-Stage Builds Exist

Without multi-stage builds, you have two bad options:

1. **Fat image** — install everything in one Dockerfile. The production image contains compilers, dev dependencies, source files, and build artifacts. 800MB+ for a simple Node.js app.
2. **External build** — compile outside Docker, then `COPY` the output. Breaks reproducibility — your laptop's Node version might differ from CI. "Works on my machine."

Multi-stage builds solve both: build inside Docker for reproducibility, copy only artifacts to the final image for size.

```
┌──────────────────────────────────────────────────────────────┐
│                   Multi-Stage Build Flow                      │
│                                                               │
│  Stage 1: builder                    Stage 2: production      │
│  ┌───────────────────────┐          ┌───────────────────────┐ │
│  │ FROM node:20-alpine   │          │ FROM node:20-alpine   │ │
│  │                       │          │                       │ │
│  │ package.json           │          │ package.json           │ │
│  │ package-lock.json      │          │ node_modules/ (prod)   │ │
│  │ tsconfig.json          │   COPY   │ dist/                  │ │
│  │ src/*.ts               │ ──────▶ │   server.js            │ │
│  │ node_modules/ (all)    │ (only   │   routes/              │ │
│  │   typescript            │  dist/) │                       │ │
│  │   @types/*              │          │ Size: ~90MB            │ │
│  │   eslint               │          └───────────────────────┘ │
│  │ dist/                   │                                    │
│  │   server.js             │          ❌ NOT in final image:    │
│  │   routes/               │            typescript, @types/*,  │
│  │                       │            eslint, src/*.ts,       │
│  │ Size: ~350MB            │            tsconfig.json           │
│  └───────────────────────┘                                    │
└──────────────────────────────────────────────────────────────┘
```

### Named Stages and Copying Artifacts

```dockerfile
# Stage 1: Build
FROM node:20-alpine AS builder
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY tsconfig.json ./
COPY src/ ./src/
RUN npm run build

# Stage 2: Production
FROM node:20-alpine AS production
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci --only=production && npm cache clean --force
COPY --from=builder /app/dist ./dist

USER node
EXPOSE 3000
CMD ["node", "dist/server.js"]
```

Key mechanics:
- `AS builder` names the stage so you can reference it later
- `COPY --from=builder /app/dist ./dist` copies only the compiled output
- The `production` stage starts fresh from `node:20-alpine` — nothing from the builder stage leaks in
- `npm ci --only=production` installs runtime dependencies without devDependencies

### Builder Pattern for TypeScript

A more complete pattern with dependency caching, type checking, and linting:

```dockerfile
# ─── Stage 1: Dependencies ───
FROM node:20-alpine AS deps
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci

# ─── Stage 2: Build ───
FROM deps AS builder
COPY tsconfig.json ./
COPY src/ ./src/

# Type check (fail fast)
RUN npx tsc --noEmit

# Build production output
RUN npm run build

# ─── Stage 3: Production dependencies ───
FROM node:20-alpine AS prod-deps
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci --only=production && npm cache clean --force

# ─── Stage 4: Runtime ───
FROM node:20-alpine AS runtime
WORKDIR /app

RUN addgroup --system --gid 1001 app && \
    adduser --system --uid 1001 --ingroup app app

COPY --from=prod-deps --chown=app:app /app/node_modules ./node_modules
COPY --from=builder --chown=app:app /app/dist ./dist
COPY --from=builder --chown=app:app /app/package.json ./

USER app

ENV NODE_ENV=production
EXPOSE 3000

HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:3000/health || exit 1

ENTRYPOINT ["node"]
CMD ["dist/server.js"]
```

> **Why separate `prod-deps` stage?** If you do `npm ci` (all deps) in the builder and `npm ci --only=production` in the runtime, you run `npm ci` twice. By separating production dependencies into their own stage, Docker can cache and parallelize them with BuildKit.

### Distroless and Scratch Base Images

For maximum security and minimal size, use images with no shell, no package manager, and no OS utilities:

```dockerfile
# Distroless — no shell, no package manager, just Node.js runtime
FROM gcr.io/distroless/nodejs20-debian12 AS runtime
WORKDIR /app
COPY --from=builder /app/dist ./dist
COPY --from=prod-deps /app/node_modules ./node_modules
CMD ["dist/server.js"]
```

```dockerfile
# Scratch — absolutely nothing, only for static binaries (Go, Rust)
FROM scratch
COPY --from=builder /app/myapp /myapp
ENTRYPOINT ["/myapp"]
```

| Base | Shell? | Package Mgr? | Size | Debug? | Use For |
|------|--------|-------------|------|--------|---------|
| alpine | ✅ (sh) | ✅ (apk) | ~5MB | ✅ Easy | Most apps |
| distroless | ❌ | ❌ | ~20MB | ❌ Hard | High-security production |
| scratch | ❌ | ❌ | 0MB | ❌ Very hard | Static Go/Rust binaries |

> **Distroless debugging tip:** Google provides debug variants with a busybox shell:
> ```bash
> docker run --entrypoint sh gcr.io/distroless/nodejs20-debian12:debug
> ```

### Image Size Analysis

#### `docker history`

Shows layers and their sizes. The `CREATED BY` column reveals the instruction that created each layer:

```bash
docker history deployforge-api:latest --no-trunc
```

#### `dive` — Interactive Layer Explorer

[dive](https://github.com/wagoodman/dive) lets you browse each layer's filesystem and identify wasted space:

```bash
# Install
brew install dive

# Analyze an image
dive deployforge-api:latest

# CI mode — fail if image efficiency is below threshold
dive deployforge-api:latest --ci --lowestEfficiency 0.95
```

`dive` shows:
- Each layer's added/removed/modified files
- Total wasted space (files added in one layer and removed in another)
- Image efficiency score (percentage of useful bytes)

### BuildKit Features

BuildKit (default since Docker 23+) adds powerful build capabilities:

#### Cache Mounts

Persist package manager caches across builds. Instead of downloading npm packages from scratch:

```dockerfile
# Without cache mount: npm downloads everything on cache miss
RUN npm ci

# With cache mount: npm cache survives between builds
RUN --mount=type=cache,target=/root/.npm \
    npm ci
```

Package manager cache mount targets:

| Package Manager | Cache Target |
|----------------|-------------|
| npm | `/root/.npm` |
| pip | `/root/.cache/pip` |
| apt | `/var/cache/apt` |
| go | `/root/.cache/go-build` |
| maven | `/root/.m2` |

#### Secret Mounts

Access secrets during build without baking them into layers:

```dockerfile
# Mount a secret file (e.g., .npmrc for private registry auth)
RUN --mount=type=secret,id=npmrc,target=/root/.npmrc \
    npm ci

# Mount an environment secret
RUN --mount=type=secret,id=gh_token \
    GH_TOKEN=$(cat /run/secrets/gh_token) npm ci
```

```bash
# Pass the secret at build time
docker build --secret id=npmrc,src=$HOME/.npmrc .
docker build --secret id=gh_token,env=GITHUB_TOKEN .
```

The secret is available during that `RUN` instruction only and is never stored in the image layers.

#### SSH Forwarding

Clone private repos during build using the host's SSH agent:

```dockerfile
RUN --mount=type=ssh \
    git clone git@github.com:org/private-lib.git
```

```bash
docker build --ssh default .
```

### Build Context Optimization

The build context is everything sent to the Docker daemon. Minimize it:

```bash
# See build context size
docker build --no-cache . 2>&1 | head -1
# "Sending build context to Docker daemon  2.1MB"

# Use a specific context path
docker build -f docker/api-gateway.Dockerfile -t api ./src

# BuildKit: use named contexts to reference other images/paths
docker build \
  --build-context shared=../shared-lib \
  -f Dockerfile .
# In Dockerfile: COPY --from=shared /package.json ./shared/
```

---

## Code Examples

### Example 1: Complete Node.js/TypeScript Multi-Stage Build

```dockerfile
# syntax=docker/dockerfile:1

# ─── Base ───
FROM node:20-alpine AS base
RUN apk add --no-cache tini
WORKDIR /app

# ─── Dependencies ───
FROM base AS deps
COPY package.json package-lock.json ./
RUN --mount=type=cache,target=/root/.npm \
    npm ci

# ─── Builder ───
FROM deps AS builder
COPY tsconfig.json ./
COPY src/ ./src/
RUN npm run build
# Prune dev dependencies after build
RUN npm prune --production

# ─── Production ───
FROM base AS production

LABEL org.opencontainers.image.title="DeployForge API Gateway"

RUN addgroup --system --gid 1001 app && \
    adduser --system --uid 1001 --ingroup app app

# Copy only production node_modules and compiled JS
COPY --from=builder --chown=app:app /app/node_modules ./node_modules
COPY --from=builder --chown=app:app /app/dist ./dist
COPY --from=builder --chown=app:app /app/package.json ./

USER app
ENV NODE_ENV=production
EXPOSE 3000

HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:3000/health || exit 1

# tini as PID 1 — properly handles zombie processes and signal forwarding
ENTRYPOINT ["/sbin/tini", "--"]
CMD ["node", "dist/server.js"]
```

> **Why `tini`?** Node.js doesn't properly handle zombie child processes when running as PID 1. `tini` is a tiny init system that reaps zombies and forwards signals. Docker has `--init` but that's a runtime flag — baking `tini` in makes it consistent.

### Example 2: Comparing Image Sizes

```bash
# Build with different strategies and compare
cat > Dockerfile.naive << 'EOF'
FROM node:20
WORKDIR /app
COPY . .
RUN npm install
RUN npm run build
CMD ["node", "dist/server.js"]
EOF

cat > Dockerfile.optimized << 'EOF'
FROM node:20-alpine AS builder
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . .
RUN npm run build

FROM node:20-alpine
WORKDIR /app
COPY package*.json ./
RUN npm ci --only=production && npm cache clean --force
COPY --from=builder /app/dist ./dist
USER node
CMD ["node", "dist/server.js"]
EOF

# Build both
docker build -f Dockerfile.naive -t demo:naive .
docker build -f Dockerfile.optimized -t demo:optimized .

# Compare sizes
docker images demo
# REPOSITORY   TAG         SIZE
# demo         naive       1.24GB
# demo         optimized   92MB

# That's a 93% size reduction!
echo "Scale: $(echo "scale=0; 1240/92" | bc)x smaller"
```

### Example 3: Analyzing Wasted Space with dive

```bash
# Build an image with known wasted space
cat > Dockerfile.wasteful << 'EOF'
FROM ubuntu:22.04
RUN apt-get update && apt-get install -y curl wget vim
RUN apt-get clean
RUN rm -rf /var/lib/apt/lists/*
EOF

docker build -f Dockerfile.wasteful -t demo:wasteful .

# Analyze with dive
dive demo:wasteful
# → Layer 2 (apt-get install): +150MB
# → Layer 3 (apt-get clean): -10MB saved
# → Layer 4 (rm -rf): -30MB saved
# → BUT: the original 150MB still exists in layer 2!
# → Wasted space: ~40MB

# The fix: combine into one layer
cat > Dockerfile.efficient << 'EOF'
FROM ubuntu:22.04
RUN apt-get update && \
    apt-get install -y --no-install-recommends curl wget vim && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*
EOF

docker build -f Dockerfile.efficient -t demo:efficient .
dive demo:efficient
# → One layer, no wasted space
```

---

## Try It Yourself

### Challenge 1: Build a Minimal TypeScript Image

Create a multi-stage Dockerfile for a TypeScript Express API that:
- Uses `node:20-alpine` for both stages
- Installs ALL dependencies in the builder stage
- Compiles TypeScript with `npm run build`
- Copies only `dist/` and production `node_modules` to the final stage
- Runs as a non-root user
- Includes a health check
- Final image should be under 150MB

<details>
<summary>Show solution</summary>

```dockerfile
# syntax=docker/dockerfile:1

# ─── Builder ───
FROM node:20-alpine AS builder
WORKDIR /app

COPY package.json package-lock.json ./
RUN --mount=type=cache,target=/root/.npm \
    npm ci

COPY tsconfig.json ./
COPY src/ ./src/

RUN npm run build

# Remove devDependencies
RUN npm prune --production

# ─── Runtime ───
FROM node:20-alpine

WORKDIR /app

RUN addgroup --system --gid 1001 appgroup && \
    adduser --system --uid 1001 --ingroup appgroup appuser

COPY --from=builder --chown=appuser:appgroup /app/node_modules ./node_modules
COPY --from=builder --chown=appuser:appgroup /app/dist ./dist
COPY --from=builder --chown=appuser:appgroup /app/package.json ./

USER appuser
ENV NODE_ENV=production
EXPOSE 3000

HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:3000/health || exit 1

ENTRYPOINT ["node"]
CMD ["dist/server.js"]
```

```bash
# Build and check size
docker build -t ts-api:latest .
docker images ts-api:latest
# → Should be under 150MB

# Verify non-root
docker run --rm ts-api:latest whoami
# → appuser

# Verify health check is defined
docker inspect ts-api:latest | jq '.[0].Config.Healthcheck'
```

</details>

### Challenge 2: Measure the Difference

Given a project with ~50 npm dependencies, build three image variants and record the sizes:

1. `node:20` (full Debian, single stage)
2. `node:20-alpine` (Alpine, single stage)
3. `node:20-alpine` (Alpine, multi-stage with production deps only)

Which layers contribute the most to image size?

<details>
<summary>Show solution</summary>

```bash
# Variant 1: Full Debian, single stage
cat > Dockerfile.full << 'EOF'
FROM node:20
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . .
RUN npm run build
CMD ["node", "dist/server.js"]
EOF

# Variant 2: Alpine, single stage
cat > Dockerfile.alpine-single << 'EOF'
FROM node:20-alpine
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . .
RUN npm run build
CMD ["node", "dist/server.js"]
EOF

# Variant 3: Alpine, multi-stage
cat > Dockerfile.alpine-multi << 'EOF'
FROM node:20-alpine AS builder
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . .
RUN npm run build

FROM node:20-alpine
WORKDIR /app
COPY package*.json ./
RUN npm ci --only=production && npm cache clean --force
COPY --from=builder /app/dist ./dist
CMD ["node", "dist/server.js"]
EOF

# Build all three
docker build -f Dockerfile.full -t size-test:full .
docker build -f Dockerfile.alpine-single -t size-test:alpine-single .
docker build -f Dockerfile.alpine-multi -t size-test:alpine-multi .

# Compare
docker images size-test --format "table {{.Tag}}\t{{.Size}}"
# TAG              SIZE
# full             ~1.3GB
# alpine-single    ~350MB
# alpine-multi     ~90-120MB

# Layer breakdown for the multi-stage
docker history size-test:alpine-multi
# Biggest layers:
# 1. Base image (node:20-alpine): ~130MB
# 2. Production node_modules: variable (30-100MB)
# 3. Compiled dist/: usually < 5MB

# Clean up
docker rmi size-test:full size-test:alpine-single size-test:alpine-multi
rm Dockerfile.full Dockerfile.alpine-single Dockerfile.alpine-multi
```

</details>

---

## Capstone Connection

**DeployForge** uses multi-stage builds for every service:

- **API Gateway** — TypeScript compilation happens in the `builder` stage. The `production` stage contains only compiled JavaScript, production `node_modules`, and the Node.js runtime. Build artifacts (`tsconfig.json`, `src/*.ts`, `@types/*`, `typescript`) never ship. Final image: ~90MB.
- **Worker Service** — Same pattern as the API Gateway. The BullMQ worker compiles TypeScript in the builder and runs minimal JavaScript in production. This is important because the worker processes untrusted payloads — a smaller image means a smaller attack surface (Module 03).
- **Nginx** — Uses a two-stage build: first stage generates optimized static assets (if applicable), second stage copies them into the official `nginx:alpine` image with a custom `nginx.conf`.
- **BuildKit cache mounts** — CI pipelines use `--mount=type=cache,target=/root/.npm` to persist the npm cache between builds, cutting CI build times from ~90 seconds to ~15 seconds on dependency cache hit.
- **Secret mounts** — Private npm packages from the DeployForge organization registry are installed using `--mount=type=secret,id=npmrc` so the `.npmrc` auth token never appears in image layers. This pattern is essential for Module 03 (Container Security).

In Module 03, we'll scan these optimized images for vulnerabilities and further harden them with read-only filesystems and seccomp profiles.
