# Module 13 — Capstone Integration

Twelve modules of building blocks. Dozens of YAML manifests, Helm charts, CI
pipelines, autoscalers, and monitoring rules — all constructed in isolation.
Now it's time to connect every wire and throw the switch. This module is the
final exam and the graduation ceremony rolled into one: you will take DeployForge
from a cold start to a fully operational production deployment, verify that
every subsystem works together under real conditions, and prove you can operate
the system on day two and beyond.

This isn't a tutorial. It's an integration challenge. You'll hit the gaps in
your understanding — the ones that only surface when systems interact — and
you'll close them. When you finish this module you'll have deployed, monitored,
broken, fixed, and documented a production-grade platform end-to-end.

---

## Learning Objectives

- [ ] Deploy a complete microservices application to Kubernetes end-to-end
- [ ] Implement the full CI/CD → GitOps → progressive delivery pipeline
- [ ] Verify the observability stack (metrics, traces, logs, dashboards)
- [ ] Run chaos experiments and validate SLO compliance under failure
- [ ] Perform a production readiness review against an industry-standard checklist
- [ ] Write operational runbooks and architecture decision records
- [ ] Present system architecture and reliability posture to engineering leadership
- [ ] Demonstrate day-2 operations: upgrades, backup/restore, incident response

---

## Topics

| # | File | Topic | Est. Time |
|---|------|-------|-----------|
| 1 | [01-production-readiness.md](01-production-readiness.md) | Production Readiness Review | 60 min |
| 2 | [02-end-to-end-deployment.md](02-end-to-end-deployment.md) | End-to-End Deployment Walkthrough | 90 min |
| 3 | [03-operational-excellence.md](03-operational-excellence.md) | Operational Excellence & Day-2 Operations | 60 min |
| 4 | [exercises/README.md](exercises/README.md) | Final capstone exercises | 120 min |

**Total estimated time: 5–6 hours**

---

## Prerequisites

- All previous modules (01–12) completed
- A running `kind` cluster with DeployForge deployed (from Modules 04–12)
- The following tools installed and configured:
  ```bash
  kind get clusters         # → deployforge
  kubectl cluster-info      # → Kubernetes control plane is running
  helm version --short      # → v3.x.x
  docker info               # → Docker daemon running
  argocd version --client   # → argocd: v2.x.x
  terraform version         # → Terraform v1.x.x
  ```
- ArgoCD, Prometheus, Grafana, and Argo Rollouts deployed in the cluster (Modules 08–10)
- CI pipeline configured (Module 10) — at minimum a local simulation
- Terraform modules defined (Module 11)
- HPA and resource quotas applied (Module 12)

---

## Capstone Milestone

**Goal:** DeployForge is fully operational: a code push triggers CI → image build
→ vulnerability scan → registry push → ArgoCD sync → canary rollout → Prometheus
verification → full promotion. The observability stack confirms SLO compliance,
alerts fire on anomalies, and the system auto-scales under load.

| Artifact | Description |
|----------|-------------|
| `k8s/overlays/prod/` | Production Kustomize overlay with all resources |
| `.github/workflows/ci.yml` | CI pipeline: lint → test → build → scan → push |
| `deploy/argocd/application.yaml` | ArgoCD Application pointing at prod overlay |
| `deploy/rollouts/rollout.yaml` | Argo Rollout with canary strategy + analysis |
| `monitoring/dashboards/overview.json` | Grafana dashboard: SLO budget + service health |
| `monitoring/alerts/slo-burn-rate.yaml` | Multi-burn-rate PrometheusRules |
| `docs/architecture.md` | Architecture decision records |
| `docs/runbook.md` | Operational runbook for DeployForge |
| `docs/production-readiness.md` | Completed production readiness checklist |

```
┌──────────────────────────────────────────────────────────────────────────┐
│                     DeployForge — Fully Integrated                       │
│                                                                          │
│  Developer ──▶ git push ──▶ GitHub Actions CI                            │
│                              │                                           │
│                              ▼                                           │
│                         Build + Scan ──▶ Push Image ──▶ Registry         │
│                                                           │              │
│                                                           ▼              │
│                                                      ArgoCD Sync         │
│                                                           │              │
│                              ┌────────────────────────────┘              │
│                              ▼                                           │
│  ┌──────────────────────────────────────────────────────────────────┐    │
│  │  Kubernetes Cluster                                              │    │
│  │                                                                  │    │
│  │  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐        │    │
│  │  │  Nginx   │  │   API    │  │  Worker  │  │ PostgreSQL│        │    │
│  │  │ (Ingress)│─▶│ Gateway  │─▶│ Service  │  │ + Redis   │        │    │
│  │  └──────────┘  └────┬─────┘  └────┬─────┘  └──────────┘        │    │
│  │                     │             │                              │    │
│  │                     ▼             ▼                              │    │
│  │              Argo Rollout   BullMQ Jobs                          │    │
│  │              (canary 10%→50%→100%)                               │    │
│  │                                                                  │    │
│  │  ┌──────────────────────────────────────────────────────────┐   │    │
│  │  │  Observability: Prometheus → Grafana → Alertmanager      │   │    │
│  │  │  Tracing: OTel Collector → Jaeger                        │   │    │
│  │  │  Logging: Fluent Bit → stdout aggregation                │   │    │
│  │  └──────────────────────────────────────────────────────────┘   │    │
│  │                                                                  │    │
│  │  ┌──────────────────────────────────────────────────────────┐   │    │
│  │  │  Scaling: HPA (API) + VPA (Worker) + ResourceQuotas      │   │    │
│  │  └──────────────────────────────────────────────────────────┘   │    │
│  └──────────────────────────────────────────────────────────────────┘    │
└──────────────────────────────────────────────────────────────────────────┘
```

---

## How to Work Through This Module

1. **Read** the production readiness review first — it gives you the checklist
   against which you'll validate everything else.
2. **Walk through** the end-to-end deployment file step by step. Don't skip
   commands. Every failure you encounter is a learning opportunity.
3. **Read** the day-2 operations file to understand what happens *after* the
   deploy succeeds.
4. **Complete** the exercises — they are the final proof that you can operate
   this system independently.
5. **Check** your work against the graduation checklist at the bottom of the
   exercises file.
6. **Celebrate.** You've earned it.
