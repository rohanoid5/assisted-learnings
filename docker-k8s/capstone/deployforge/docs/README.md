# DeployForge — Capstone Project

> A sample microservices application for learning Docker, Kubernetes, and SRE
> practices end-to-end. Each module in the `docker-k8s/` track adds a new layer
> to this project, culminating in a production-grade deployment pipeline.

---

## Architecture Overview

```
                        ┌──────────────────────────────────┐
                        │          Nginx Ingress            │
                        │        (Module 05 / 09)           │
                        └──────────┬───────────────────────┘
                                   │
                    ┌──────────────▼──────────────┐
                    │       API Gateway            │
                    │   (Express + TypeScript)     │
                    │   /health  /ready  /metrics  │
                    └──┬───────────────────────┬───┘
                       │                       │
          ┌────────────▼────────┐   ┌──────────▼──────────┐
          │    PostgreSQL 15    │   │      Redis 7         │
          │   (primary store)   │   │  (cache + queues)    │
          └─────────────────────┘   └──────────┬──────────┘
                                               │
                                    ┌──────────▼──────────┐
                                    │   Worker Service     │
                                    │   (BullMQ consumer)  │
                                    └─────────────────────┘

          ┌──────────────────────────────────────────────┐
          │              Observability Stack              │
          │  Prometheus · Grafana · OpenTelemetry (M08)   │
          └──────────────────────────────────────────────┘
```

## Components

| Component        | Description                                | Introduced |
|------------------|--------------------------------------------|------------|
| API Gateway      | Express HTTP server, REST endpoints        | Module 02  |
| Worker Service   | BullMQ job processor                       | Module 03  |
| PostgreSQL       | Relational data store                      | Module 02  |
| Redis            | Caching layer and job queue broker         | Module 03  |
| Nginx Ingress    | Reverse proxy / Ingress controller         | Module 05  |
| Prometheus       | Metrics collection                         | Module 08  |
| Grafana          | Dashboards and alerting                    | Module 08  |
| Helm Chart       | Templated Kubernetes manifests             | Module 06  |
| Terraform        | Infrastructure-as-code for cloud resources | Module 10  |
| GitHub Actions   | CI/CD pipeline                             | Module 09  |

## Tech Stack

- **Runtime:** Node.js 20 LTS, TypeScript 5.x
- **Framework:** Express 4.x
- **Queue:** BullMQ + Redis 7
- **Database:** PostgreSQL 15
- **Container:** Docker (multi-stage builds)
- **Orchestration:** Kubernetes 1.28+, Kind (local), Helm 3
- **IaC:** Terraform 1.5+
- **Monitoring:** Prometheus, Grafana, prom-client
- **CI/CD:** GitHub Actions

## Module Progression

Each module layer is additive — nothing is thrown away:

1. **Module 01** — Docker fundamentals (images, containers, volumes)
2. **Module 02** — Building the API Gateway container (multi-stage Dockerfile)
3. **Module 03** — Docker Compose for local development stack
4. **Module 04** — Kubernetes fundamentals (pods, deployments, services)
5. **Module 05** — Ingress, ConfigMaps, Secrets
6. **Module 06** — Helm charts and templating
7. **Module 07** — Security (RBAC, NetworkPolicies, external secrets)
8. **Module 08** — Observability (Prometheus, Grafana, OpenTelemetry)
9. **Module 09** — CI/CD pipelines (GitHub Actions, ArgoCD)
10. **Module 10** — Infrastructure-as-Code with Terraform

## Getting Started

```bash
# 1. Clone and install dependencies
cd docker-k8s/capstone/deployforge
npm install
cp .env.example .env

# 2. Run locally with Docker Compose
docker compose up -d

# 3. Verify
curl http://localhost:3000/health

# 4. (Later modules) Deploy to Kind cluster
./scripts/setup-kind.sh
./scripts/deploy.sh dev
```

## Links

- [Track README](../../README.md)
- [Architecture Decisions](./architecture.md)
- [Checklist](../../CHECKLIST.md)
