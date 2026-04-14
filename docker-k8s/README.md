# Docker, Kubernetes & SRE Interactive Tutorial

Master containerization, orchestration, and site reliability engineering — the skills that separate senior engineers from the rest. Build a production-grade deployment platform from scratch.

---

## How to Use This Tutorial

1. Work through modules **in order** — each builds on the previous.
2. After each concept, **apply it to the capstone project** using the _Capstone Connection_ section at the bottom of every topic file.
3. Complete the **exercises** before moving to the next module.
4. The `capstone/` folder holds your working project — built incrementally.

> **Philosophy:** You don't learn infrastructure by reading — you learn by breaking things in a safe environment and fixing them. Every module has you deploying, debugging, and iterating on real systems.

---

## Prerequisites

| Requirement | Version | Notes |
|-------------|---------|-------|
| Docker | 24+ | `brew install --cask docker` or [docker.com](https://www.docker.com/) |
| kubectl | 1.28+ | `brew install kubectl` |
| kind | 0.20+ | `brew install kind` (local K8s clusters) |
| Helm | 3.13+ | `brew install helm` |
| Terraform | 1.6+ | `brew install terraform` |
| Node.js | 20 LTS | `nvm install 20` (for sample app) |
| k9s | Latest | `brew install k9s` (optional but highly recommended) |

> **Assumed knowledge:** Linux command line, basic networking (TCP/IP, DNS, HTTP), and experience with at least one programming language. If you can SSH into a server and deploy an app manually, you're ready.

---

## Learning Path

| Module | Topic | Est. Time | Capstone Milestone |
|--------|-------|-----------|-------------------|
| [01 — Container Fundamentals](modules/01-container-fundamentals/) | Namespaces, cgroups, OCI, runtimes | 3–4 hrs | Understand what containers really are |
| [02 — Docker Mastery](modules/02-docker-mastery/) | Dockerfiles, multi-stage builds, Compose | 4–5 hrs | Containerize DeployForge services |
| [03 — Container Security](modules/03-container-security/) | Image scanning, rootless, supply chain | 3–4 hrs | Harden DeployForge images |
| [04 — Kubernetes Architecture](modules/04-kubernetes-architecture/) | Control plane, etcd, API server | 3–4 hrs | Set up local K8s cluster |
| [05 — Workloads & Scheduling](modules/05-workloads-and-scheduling/) | Pods, Deployments, StatefulSets, Jobs | 4–5 hrs | Deploy DeployForge to K8s |
| [06 — Networking & Services](modules/06-networking-and-services/) | Services, Ingress, DNS, NetworkPolicies | 4–5 hrs | Expose DeployForge services |
| [07 — Storage & Configuration](modules/07-storage-and-configuration/) | PVs, ConfigMaps, Secrets, Helm, Kustomize | 4–5 hrs | Manage DeployForge config |
| [08 — Observability](modules/08-observability/) | Prometheus, Grafana, OpenTelemetry, logging | 4–5 hrs | Add monitoring to DeployForge |
| [09 — Reliability Engineering](modules/09-reliability-engineering/) | SLOs/SLIs, error budgets, chaos engineering | 3–4 hrs | Define DeployForge SLOs |
| [10 — CI/CD & GitOps](modules/10-ci-cd-and-gitops/) | Pipelines, ArgoCD, canary, blue-green | 4–5 hrs | Automate DeployForge deployments |
| [11 — Infrastructure as Code](modules/11-infrastructure-as-code/) | Terraform, modules, state management | 3–4 hrs | IaC for DeployForge infra |
| [12 — Scaling & Cost](modules/12-scaling-and-cost/) | HPA/VPA, cluster autoscaler, FinOps | 3–4 hrs | Auto-scale DeployForge |
| [13 — Capstone Integration](modules/13-capstone-integration/) | End-to-end production deployment | 5–6 hrs | Full DeployForge pipeline |

**Total estimated time: 47–60 hours**

---

## Capstone Project — DeployForge

A production-grade deployment platform that takes a sample microservices application through the full lifecycle: containerization → orchestration → observability → reliability → automated delivery.

```
┌─────────────────────────────────────────────────────────────────┐
│                        DeployForge                              │
│                                                                 │
│  ┌──────────┐    ┌──────────┐    ┌──────────┐                  │
│  │  API      │    │  Worker   │    │  Frontend │                 │
│  │  Gateway  │───▶│  Service  │    │  (Nginx)  │                 │
│  └────┬─────┘    └────┬─────┘    └──────────┘                  │
│       │               │                                         │
│       ▼               ▼                                         │
│  ┌──────────┐    ┌──────────┐                                  │
│  │ PostgreSQL│    │  Redis    │                                  │
│  │ (Primary) │    │ (Cache)   │                                  │
│  └──────────┘    └──────────┘                                  │
│                                                                 │
│  ┌──────────────────────────────────────────────┐              │
│  │  Observability: Prometheus │ Grafana │ OTel   │              │
│  └──────────────────────────────────────────────┘              │
│                                                                 │
│  ┌──────────────────────────────────────────────┐              │
│  │  Delivery: ArgoCD │ GitHub Actions │ Helm     │              │
│  └──────────────────────────────────────────────┘              │
└─────────────────────────────────────────────────────────────────┘
```

### Domain Components

| Component | Purpose | Tech |
|-----------|---------|------|
| API Gateway | REST API + health checks | Node.js, Express, TypeScript |
| Worker Service | Background job processing | Node.js, BullMQ |
| PostgreSQL | Persistent data store | PostgreSQL 15 |
| Redis | Caching + job queue | Redis 7 |
| Nginx | Static frontend + reverse proxy | Nginx |
| Prometheus | Metrics collection | Prometheus |
| Grafana | Dashboards + alerting | Grafana |

### What Gets Built Module-by-Module

| Module | What Gets Added |
|--------|-----------------|
| 01 | Understanding of Linux primitives beneath Docker |
| 02 | Dockerfiles + docker-compose.yml for all services |
| 03 | Hardened images, vulnerability scanning in CI |
| 04 | Local `kind` cluster with DeployForge deployed |
| 05 | K8s Deployments, StatefulSets for databases, Jobs for migrations |
| 06 | Services, Ingress controller, NetworkPolicies isolating tiers |
| 07 | PersistentVolumes for databases, Helm chart, Kustomize overlays |
| 08 | Prometheus + Grafana stack, custom metrics, OTel tracing |
| 09 | SLO definitions, error budget dashboard, chaos experiments |
| 10 | GitHub Actions CI, ArgoCD GitOps, canary deployments |
| 11 | Terraform modules for cloud infrastructure |
| 12 | HPA for API, VPA for workers, resource quotas, cost dashboards |
| 13 | Full production pipeline: push → build → test → deploy → monitor |

---

## Project Structure

```
docker-k8s/
├── README.md
├── CHECKLIST.md
├── modules/
│   ├── 01-container-fundamentals/
│   ├── 02-docker-mastery/
│   ├── 03-container-security/
│   ├── 04-kubernetes-architecture/
│   ├── 05-workloads-and-scheduling/
│   ├── 06-networking-and-services/
│   ├── 07-storage-and-configuration/
│   ├── 08-observability/
│   ├── 09-reliability-engineering/
│   ├── 10-ci-cd-and-gitops/
│   ├── 11-infrastructure-as-code/
│   ├── 12-scaling-and-cost/
│   └── 13-capstone-integration/
└── capstone/
    └── deployforge/
        ├── src/              # Sample microservices app
        ├── docker/           # Dockerfiles
        ├── k8s/              # Raw manifests + Kustomize
        ├── helm/             # Helm charts
        ├── terraform/        # IaC definitions
        ├── monitoring/       # Prometheus + Grafana configs
        ├── scripts/          # Helper scripts
        └── docs/             # Architecture docs
```

---

## Quick Start

```bash
# Module 01-03: Docker phase
cd capstone/deployforge
docker compose up -d
curl http://localhost:3000/health

# Module 04+: Kubernetes phase
kind create cluster --name deployforge
kubectl apply -k k8s/overlays/dev/
kubectl port-forward svc/api-gateway 3000:3000
curl http://localhost:3000/health
```
