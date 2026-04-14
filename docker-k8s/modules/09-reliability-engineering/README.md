# Module 09 — Reliability Engineering

## Overview

Reliability isn't about eliminating failure — it's about engineering systems that fail gracefully, recover quickly, and meet the expectations you've explicitly defined. This module introduces the discipline of Site Reliability Engineering (SRE) as practiced at companies like Google, Netflix, and Amazon: translating business requirements into measurable reliability targets, then building the engineering practices to hit those targets consistently.

You'll start by learning to express reliability in precise, measurable terms — Service Level Indicators (SLIs), Service Level Objectives (SLOs), and error budgets. Then you'll move to proactive reliability practices: chaos engineering experiments that find weaknesses before your users do. Finally, you'll build the human processes that complete the reliability picture — structured incident response and blameless postmortems that turn every outage into an improvement opportunity.

By the end of this module you'll think about reliability the way senior SREs do: not as a binary "up or down" state, but as a budget you spend deliberately to balance reliability against velocity.

---

## Learning Objectives

After completing this module, you should be able to:

- [ ] Define SLIs, SLOs, and SLAs and explain how they relate to each other
- [ ] Identify the four golden SLI types: availability, latency, throughput, and correctness
- [ ] Write SLO specifications with precise measurement windows and thresholds
- [ ] Calculate error budgets and explain how they gate feature velocity
- [ ] Implement multi-window, multi-burn-rate alerting in Prometheus
- [ ] Describe the principles of chaos engineering and the steady-state hypothesis
- [ ] Design and run chaos experiments with safety abort conditions
- [ ] Use Kubernetes-native chaos tools (Chaos Mesh) to inject failures
- [ ] Plan and execute a GameDay exercise
- [ ] Define incident severity levels and assign roles (IC, Comms Lead, Ops Lead)
- [ ] Lead an incident using a structured response framework
- [ ] Write a blameless postmortem with timeline, root cause, and action items
- [ ] Identify toil and propose automation to reduce it
- [ ] Build an incident response runbook for common failure modes

---

## Topics

| # | File | Topic | Est. Time |
|---|------|-------|-----------|
| 1 | [01-slos-slis-error-budgets.md](01-slos-slis-error-budgets.md) | SLOs, SLIs & Error Budgets | 60 min |
| 2 | [02-chaos-engineering.md](02-chaos-engineering.md) | Chaos Engineering: Breaking Things on Purpose | 55 min |
| 3 | [03-incident-response-and-postmortems.md](03-incident-response-and-postmortems.md) | Incident Response & Blameless Postmortems | 50 min |
| 4 | [exercises/README.md](exercises/README.md) | Hands-on Exercises | 75 min |

**Total estimated time: 3–4 hours**

---

## Prerequisites

- [Module 08 — Observability](../08-observability/) (Prometheus, Grafana, alerting fundamentals)
- A running `kind` cluster with the `deployforge` namespace and workloads from Module 08
- Prometheus and Grafana deployed in the cluster (from Module 08)
- `kubectl` and `helm` installed and configured
- Basic familiarity with PromQL queries and alerting rules

---

## Capstone Milestone

> **Goal:** Define DeployForge SLOs with error budget tracking, build a chaos experiment suite, and create an incident response runbook.

By the end of this module you'll have:

| Artifact | Description |
|----------|-------------|
| SLO Specification | Formal SLO document for DeployForge API Gateway and Worker Service |
| Error Budget Dashboard | Grafana dashboard tracking SLO compliance and remaining error budget |
| Multi-burn-rate Alerts | PrometheusRules that fire at different urgencies based on budget consumption rate |
| Chaos Experiment Suite | Chaos Mesh experiments for pod failure, network latency, and CPU stress |
| GameDay Runbook | Structured plan for running chaos experiments against DeployForge |
| Incident Response Runbook | Step-by-step guide for handling DeployForge incidents by severity |
| Postmortem Template | Blameless postmortem template pre-filled with DeployForge context |

```
┌─────────────────────────────────────────────────────────────────────────┐
│                kind Cluster — DeployForge Reliability                    │
│                                                                         │
│  ┌──── SLO Layer ──────────────────────────────────────────────────┐   │
│  │                                                                  │   │
│  │  API Gateway SLO ──────────┐    Worker Service SLO ──────┐     │   │
│  │  • Availability ≥ 99.9%    │    • Job success rate ≥ 99%  │     │   │
│  │  • p99 latency < 500ms     │    • p99 duration < 30s      │     │   │
│  │  └────────┬────────────────┘    └──────────┬─────────────┘     │   │
│  │           │                                 │                    │   │
│  │           ▼                                 ▼                    │   │
│  │  ┌─ Error Budget Tracker ──────────────────────────────────┐    │   │
│  │  │  Budget = (1 − SLO) × window                            │    │   │
│  │  │  Remaining: ██████████░░ 72% (31.1 min of 43.2 min)     │    │   │
│  │  └──────────────┬──────────────────────────────────────────┘    │   │
│  │                 │                                                │   │
│  │                 ▼                                                │   │
│  │  ┌─ Multi-Burn-Rate Alerts ────────────────────────────────┐    │   │
│  │  │  1h  window × 14.4 burn ──▶ Page (critical)             │    │   │
│  │  │  6h  window × 6.0  burn ──▶ Page (high)                 │    │   │
│  │  │  3d  window × 1.0  burn ──▶ Ticket (warning)            │    │   │
│  │  └─────────────────────────────────────────────────────────┘    │   │
│  └──────────────────────────────────────────────────────────────────┘   │
│                                                                         │
│  ┌──── Chaos Layer ────────────────────────────────────────────────┐   │
│  │                                                                  │   │
│  │  Chaos Mesh ──┬── PodChaos (kill gateway pod)                   │   │
│  │               ├── NetworkChaos (inject 200ms latency)           │   │
│  │               ├── StressChaos (CPU spike on worker)             │   │
│  │               └── Abort conditions (auto-halt if SLO breached)  │   │
│  │                                                                  │   │
│  └──────────────────────────────────────────────────────────────────┘   │
│                                                                         │
│  ┌──── Incident Response ──────────────────────────────────────────┐   │
│  │                                                                  │   │
│  │  Severity Levels ──▶ Runbooks ──▶ Postmortem ──▶ Action Items   │   │
│  │  SEV1 / SEV2 / SEV3    per-service    blameless     tracked     │   │
│  │                                                                  │   │
│  └──────────────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────────────┘
```

---

## How to Work Through This Module

1. Read each concept file in order (01 → 02 → 03).
2. Apply the SLO definitions and Prometheus rules to your `kind` cluster.
3. Install Chaos Mesh and run experiments against DeployForge workloads.
4. Use `kubectl` and Grafana to observe the impact of chaos experiments on your SLOs.
5. Complete the exercises in `exercises/README.md`.
6. Check off the learning objectives above as you master each one.
7. Move to [Module 10 — CI/CD & GitOps](../10-cicd-gitops/) when ready.
