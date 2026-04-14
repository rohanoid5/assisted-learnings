# Architecture Decision Records — DeployForge

> This document captures the key architectural decisions for the DeployForge
> capstone project and the reasoning behind each choice.

---

## ADR-001: Express + TypeScript for application services

**Status:** Accepted

**Context:**
The `docker-k8s/` track focuses on infrastructure, containers, and
orchestration — not on learning a new application framework. Learners arriving
from the `nodejs/` track already know Express and TypeScript.

**Decision:**
Use Express 4.x with TypeScript 5.x for both the API Gateway and Worker
service.

**Consequences:**
- Minimal ramp-up time on application code; focus stays on DevOps concepts.
- Shared `tsconfig.json` and dependency tree across services.
- Multi-stage Docker builds handle TypeScript compilation cleanly.

---

## ADR-002: PostgreSQL 15 + Redis 7 as data tier

**Status:** Accepted

**Context:**
A realistic microservices deployment needs both a relational store and a
caching/queue layer. PostgreSQL and Redis are industry-standard choices that
appear across the `postgres/` and `system-design/` tracks.

**Decision:**
Use PostgreSQL 15 as the primary data store and Redis 7 for caching and as the
BullMQ job queue broker.

**Consequences:**
- Learners practice managing stateful workloads in Kubernetes (PVCs, StatefulSets).
- Docker Compose and Kubernetes manifests both include health checks for these
  services.
- Aligns with the `postgres/` track's StoreForge capstone for cross-track
  learning.

---

## ADR-003: Three-tier architecture (Gateway → Queue → Worker)

**Status:** Accepted

**Context:**
A single monolith does not expose enough Kubernetes concepts (service
discovery, scaling individual components, inter-pod communication). A full
microservices mesh is overkill for a learning project.

**Decision:**
Adopt a three-tier architecture:
1. **API Gateway** — handles HTTP traffic, validates input, enqueues work.
2. **Redis Queue** — decouples request handling from processing.
3. **Worker Service** — consumes jobs asynchronously.

**Consequences:**
- Two independently deployable services to practice rolling updates, scaling,
  and resource tuning.
- Redis acts as a natural boundary for demonstrating Kubernetes Services and
  NetworkPolicies.
- Simple enough to reason about; complex enough to demonstrate real patterns.

---

## ADR-004: Kustomize + Helm (not either/or)

**Status:** Accepted

**Context:**
Both Kustomize and Helm are widely used in production Kubernetes environments.
The track covers both tools in separate modules.

**Decision:**
Maintain both Kustomize bases/overlays and a Helm chart for DeployForge.
Kustomize is introduced first (Module 04-05), Helm follows (Module 06).

**Consequences:**
- The `k8s/` directory contains plain manifests managed by Kustomize.
- The `helm/` directory contains a fully templated Helm chart.
- The deploy script (`scripts/deploy.sh`) supports either strategy.
- Learners see the trade-offs between patching (Kustomize) and templating (Helm).

---

## ADR-005: Rolling deployments as default strategy

**Status:** Accepted

**Context:**
Deployment strategies (rolling, blue-green, canary) are a core learning
objective. A sensible default is needed before advanced strategies are
explored.

**Decision:**
Use Kubernetes rolling deployments with `maxSurge: 1` and `maxUnavailable: 0`
as the default strategy.

**Consequences:**
- Zero-downtime deploys out of the box.
- Health probes (`/health`, `/ready`) gate pod readiness.
- Module 09 extends this with blue-green and canary using Argo Rollouts.

---

## ADR-006: prom-client for application metrics

**Status:** Accepted

**Context:**
The monitoring module (Module 08) requires application-level metrics exported
in Prometheus format.

**Decision:**
Use the `prom-client` npm package to expose a `/metrics` endpoint on the API
Gateway and Worker.

**Consequences:**
- Standard Prometheus scraping; no sidecars needed initially.
- Default metrics (event loop lag, heap usage, GC) are collected automatically.
- Custom business metrics (request count, job duration) are added progressively.

---

## ADR-007: Kind for local Kubernetes development

**Status:** Accepted

**Context:**
Learners need a local multi-node Kubernetes cluster that is lightweight,
ephemeral, and runs inside Docker.

**Decision:**
Use [Kind](https://kind.sigs.k8s.io/) (Kubernetes in Docker) as the local
cluster tool. Provide a setup script (`scripts/setup-kind.sh`).

**Consequences:**
- Works on macOS, Linux, and WSL2 without a hypervisor.
- Multi-node cluster simulates realistic scheduling and affinity.
- Pairs with Docker Desktop; no conflicting kubeconfig when managed carefully.

---

## ADR-008: Terraform for cloud infrastructure

**Status:** Accepted

**Context:**
Module 10 introduces Infrastructure-as-Code. Terraform is the dominant
multi-cloud IaC tool.

**Decision:**
Use Terraform 1.5+ with a modular layout under `terraform/modules/` and
per-environment configs under `terraform/environments/`.

**Consequences:**
- Cloud-provider-agnostic module structure (can target AWS, GCP, or Azure).
- State management and locking are covered as learning objectives.
- Learners can run `terraform plan` against the dev environment without
  incurring cloud costs if they use local or free-tier providers.
