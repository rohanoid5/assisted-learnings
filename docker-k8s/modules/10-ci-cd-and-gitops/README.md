# Module 10 — CI/CD & GitOps

## Overview

Shipping code manually is how outages start. Every `kubectl apply` from a laptop is an unaudited mutation — no review, no rollback story, no proof it ever happened. Production-grade teams treat their deployment pipeline with the same rigour they treat their application code: every change is committed, reviewed, tested, and automatically reconciled against the running cluster.

This module closes the loop from code commit to running pod. You'll build CI pipelines with GitHub Actions that lint, test, scan, build multi-arch images, and push them to a registry — all before a human approves the merge. Then you'll implement GitOps with ArgoCD, where a Git repository becomes the single source of truth for cluster state. Finally, you'll graduate beyond `kubectl rollout` to progressive delivery: canary deployments with automated metric analysis, blue-green switches, and feature flags that decouple *deploy* from *release*.

By the end of this module, your DeployForge capstone will have a fully automated path from pull request to production — the kind of deployment infrastructure that lets you ship on Friday afternoon without breaking a sweat.

---

## Learning Objectives

After completing this module, you should be able to:

- [ ] Design a multi-stage CI pipeline that lints, tests, builds, scans, and pushes container images
- [ ] Write GitHub Actions workflows with matrix builds, caching, and security scanning
- [ ] Implement a container image tagging strategy using semver, Git SHA, and branch metadata
- [ ] Explain the four GitOps principles: declarative, versioned, automated, auditable
- [ ] Deploy ArgoCD and configure Application CRDs for automated sync
- [ ] Structure a GitOps repository using the app-of-apps pattern
- [ ] Manage secrets in a GitOps workflow using Sealed Secrets or SOPS
- [ ] Configure ArgoCD drift detection and self-healing
- [ ] Implement canary deployments with Argo Rollouts and traffic splitting
- [ ] Write AnalysisTemplates that query Prometheus to automate canary promotion/rollback
- [ ] Configure blue-green deployments with instant cutover and rollback
- [ ] Integrate feature flags for dark launches and A/B testing
- [ ] Design deployment strategies that achieve zero-downtime releases

---

## Topics

| # | File | Topic | Est. Time |
|---|------|-------|-----------|
| 1 | [01-ci-pipelines.md](01-ci-pipelines.md) | CI Pipelines: Build, Test, Push | 55 min |
| 2 | [02-gitops-with-argocd.md](02-gitops-with-argocd.md) | GitOps with ArgoCD | 55 min |
| 3 | [03-progressive-delivery.md](03-progressive-delivery.md) | Progressive Delivery: Canary, Blue-Green & Feature Flags | 55 min |
| 4 | [exercises/README.md](exercises/README.md) | Hands-on Exercises | 90 min |

**Total estimated time: 4–4.5 hours**

---

## Prerequisites

- [Module 09 — Reliability Engineering](../09-reliability-engineering/) (SLOs, error budgets, Prometheus metrics)
- A running `kind` cluster with `kubectl` context configured
- Docker Desktop or equivalent container runtime
- A GitHub account (free tier is sufficient for Actions)
- `helm` CLI installed (`brew install helm`)

---

## Capstone Milestone

> **Goal:** Give DeployForge a fully automated CI/CD pipeline with GitOps reconciliation and progressive delivery.

By the end of this module you'll have:

| Artifact | Description |
|----------|-------------|
| `.github/workflows/ci.yml` | Multi-stage GitHub Actions pipeline — lint, test, build, scan, push |
| `deploy/argocd/application.yaml` | ArgoCD Application CRD pointing at DeployForge's GitOps repo |
| `deploy/argocd/appproject.yaml` | Scoped ArgoCD AppProject with RBAC and allowed resources |
| `deploy/rollouts/rollout.yaml` | Argo Rollout with canary strategy and traffic splitting |
| `deploy/rollouts/analysis.yaml` | AnalysisTemplate querying Prometheus for canary success rate |
| `deploy/sealed-secrets/` | SealedSecret manifests for GitOps-safe secret management |
| `deploy/feature-flags/` | Feature flag configuration for dark launches |

```
┌─────────────────────────────────────────────────────────────────────┐
│                     DeployForge CI/CD Pipeline                      │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│   Developer                                                         │
│      │                                                              │
│      ▼                                                              │
│   ┌──────────┐    ┌──────────────────────────────────────────┐     │
│   │  Git Push │───▶│  GitHub Actions CI                       │     │
│   └──────────┘    │  ┌──────┐ ┌──────┐ ┌─────┐ ┌──────────┐│     │
│                    │  │ Lint │→│ Test │→│Build│→│Scan+Push ││     │
│                    │  └──────┘ └──────┘ └─────┘ └──────────┘│     │
│                    └──────────────────────┬───────────────────┘     │
│                                           │ image:sha-abc123        │
│                                           ▼                         │
│   ┌──────────────┐    ┌──────────────────────────────────┐         │
│   │ GitOps Repo  │◀───│  Image Updater / PR Bot          │         │
│   │ (manifests)  │    └──────────────────────────────────┘         │
│   └──────┬───────┘                                                  │
│          │ git commit (new image tag)                               │
│          ▼                                                          │
│   ┌──────────────────────────────────────────────┐                 │
│   │  ArgoCD                                       │                 │
│   │  ┌────────────────┐  ┌─────────────────────┐ │                 │
│   │  │ App Controller  │  │ Drift Detection     │ │                 │
│   │  │ (sync loop)     │  │ (self-healing)      │ │                 │
│   │  └───────┬────────┘  └─────────────────────┘ │                 │
│   └──────────┼────────────────────────────────────┘                 │
│              ▼                                                      │
│   ┌──────────────────────────────────────────────┐                 │
│   │  Argo Rollouts — Canary Strategy              │                 │
│   │  ┌──────────┐  ┌─────────┐  ┌─────────────┐ │                 │
│   │  │ Stable    │  │ Canary  │  │ Analysis    │ │                 │
│   │  │ (90%)     │  │ (10%)   │  │ (Prometheus)│ │                 │
│   │  └──────────┘  └─────────┘  └─────────────┘ │                 │
│   └──────────────────────────────────────────────┘                 │
│              │                                                      │
│              ▼                                                      │
│   ┌──────────────────────────────────────────────┐                 │
│   │  Production Cluster                           │                 │
│   │  ┌──────────┐  ┌───────────┐  ┌───────────┐ │                 │
│   │  │ Services  │  │ Feature   │  │ Monitoring│ │                 │
│   │  │          │  │ Flags     │  │ + Alerts  │ │                 │
│   │  └──────────┘  └───────────┘  └───────────┘ │                 │
│   └──────────────────────────────────────────────┘                 │
└─────────────────────────────────────────────────────────────────────┘
```

---

## How to Work Through This Module

1. **Read each concept file in order** — CI pipelines → GitOps → Progressive delivery. Each builds on the previous.
2. **Run the code examples** against your kind cluster. Copy-paste the YAMLs; don't just read them.
3. **Complete all four exercises** — they progressively build DeployForge's deployment infrastructure.
4. **Check off the learning objectives** above as you gain confidence.
5. Move to the next module when all objectives are checked.
