# Module 02 — Docker Mastery

## Overview

Go beyond `docker run` to master Dockerfiles, multi-stage builds, networking, volumes, and Docker Compose. This module takes you from running other people's containers to building production-quality images and composing multi-service environments.

You'll learn the patterns that separate "it works on my machine" from "it works in production." By the end, every DeployForge service will have an optimized Dockerfile and you'll have a complete `docker-compose.yml` for local development.

---

## Learning Objectives

After completing this module, you should be able to:

- [ ] Write optimized Dockerfiles with proper layer caching
- [ ] Implement multi-stage builds to minimize image size
- [ ] Choose appropriate base images (alpine, distroless, scratch)
- [ ] Configure Docker networking (bridge, host, overlay)
- [ ] Manage persistent data with volumes and bind mounts
- [ ] Orchestrate multi-container apps with Docker Compose
- [ ] Use health checks to verify container readiness
- [ ] Debug containerized applications effectively
- [ ] Configure Compose profiles and override files for different environments

---

## Topics

| # | File | Topic | Est. Time |
|---|------|-------|-----------|
| 1 | [01-dockerfile-best-practices.md](01-dockerfile-best-practices.md) | Dockerfile Best Practices | 60 min |
| 2 | [02-multi-stage-builds.md](02-multi-stage-builds.md) | Multi-Stage Builds & Image Optimization | 45 min |
| 3 | [03-networking-and-volumes.md](03-networking-and-volumes.md) | Docker Networking & Volumes | 45 min |
| 4 | [04-docker-compose.md](04-docker-compose.md) | Docker Compose for Development | 45 min |
| 5 | [exercises/README.md](exercises/README.md) | Hands-on Exercises | 90 min |

**Total estimated time: 4–5 hours**

---

## Prerequisites

- [Module 01 — Container Fundamentals](../01-container-fundamentals/) (namespaces, cgroups, OCI)
- Docker 24+ installed and running (`docker info` works)
- Basic command-line proficiency
- Familiarity with Node.js/TypeScript is helpful but not required

---

## Capstone Milestone

> **Goal:** All DeployForge services containerized with optimized Dockerfiles and a complete `docker-compose.yml` for local development.

By the end of this module you'll have:

| Artifact | Description |
|----------|-------------|
| `docker/api-gateway.Dockerfile` | Multi-stage build: TypeScript → optimized production image (< 100MB) |
| `docker/worker.Dockerfile` | Multi-stage build for BullMQ worker service |
| `docker/nginx.Dockerfile` | Nginx reverse proxy with custom config |
| `docker-compose.yml` | Full stack: API, Worker, PostgreSQL, Redis, Nginx |
| `docker-compose.override.yml` | Development overrides with hot-reload and debug ports |
| `.dockerignore` | Excludes node_modules, .git, test files from build context |

```
┌──────────────────────────────────────────────────────────────┐
│                 docker compose up                            │
│                                                              │
│  ┌────────────┐     ┌────────────┐     ┌────────────┐       │
│  │   Nginx    │────▶│ API Gateway│────▶│   Worker   │       │
│  │   :80      │     │   :3000    │     │  (BullMQ)  │       │
│  └────────────┘     └─────┬──────┘     └─────┬──────┘       │
│                           │                   │              │
│                     ┌─────┴──────┐     ┌─────┴──────┐       │
│                     │ PostgreSQL │     │   Redis    │       │
│                     │   :5432    │     │   :6379    │       │
│                     │  (volume)  │     │  (volume)  │       │
│                     └────────────┘     └────────────┘       │
└──────────────────────────────────────────────────────────────┘
```

---

## How to Work Through This Module

1. Read each concept file in order (01 → 02 → 03 → 04).
2. Run every code example — build the images, inspect the layers, break things.
3. Complete the exercises in `exercises/README.md`.
4. Check off the learning objectives above as you master each one.
5. Move to [Module 03 — Container Security](../03-container-security/) when ready.
