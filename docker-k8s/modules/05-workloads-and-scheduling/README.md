# Module 05 — Workloads & Scheduling

## Overview

Master every Kubernetes workload type and understand how the scheduler places pods. This module takes you beyond `kubectl create deployment` to a deep understanding of when to use Deployments vs StatefulSets vs DaemonSets, how rolling updates actually work, and how the scheduler decides which node runs your pod.

You'll learn the trade-offs between workload types, how to configure update strategies that avoid downtime, and how to use Jobs and CronJobs for batch processing. On the scheduling side, you'll master node affinity, pod affinity/anti-affinity, taints and tolerations, and topology spread constraints — the tools that let you control _exactly_ where your pods land.

By the end of this module, you'll have DeployForge's core services deployed as the correct workload types with proper scheduling constraints, probes, and disruption budgets — the foundation of a production-grade deployment.

---

## Learning Objectives

After completing this module, you should be able to:

- [ ] Explain the relationship between Pods, ReplicaSets, and Deployments
- [ ] Choose the right workload type for each use case (stateless, stateful, per-node, batch)
- [ ] Configure Deployment update strategies (RollingUpdate, Recreate) with tuned parameters
- [ ] Perform rollbacks using revision history and `kubectl rollout`
- [ ] Explain resource requests, limits, and the three QoS classes
- [ ] Deploy stateful applications with StatefulSets and headless services
- [ ] Use DaemonSets for node-level agents (log collectors, monitoring)
- [ ] Configure Jobs with completions, parallelism, and backoff policies
- [ ] Use CronJobs with schedule expressions and concurrency policies
- [ ] Control pod placement with nodeSelector, node affinity, and pod affinity/anti-affinity
- [ ] Apply taints and tolerations to dedicate or repel nodes
- [ ] Use topology spread constraints for even distribution across zones
- [ ] Configure startup, liveness, and readiness probes correctly
- [ ] Implement graceful shutdown with preStop hooks and SIGTERM handling
- [ ] Define PodDisruptionBudgets for safe cluster maintenance

---

## Topics

| # | File | Topic | Est. Time |
|---|------|-------|-----------|
| 1 | [01-pods-and-deployments.md](01-pods-and-deployments.md) | Pods, ReplicaSets & Deployments | 60 min |
| 2 | [02-statefulsets-and-daemonsets.md](02-statefulsets-and-daemonsets.md) | StatefulSets, DaemonSets & Jobs | 50 min |
| 3 | [03-scheduling-and-affinity.md](03-scheduling-and-affinity.md) | Scheduling: Affinity, Taints & Tolerations | 45 min |
| 4 | [04-pod-lifecycle.md](04-pod-lifecycle.md) | Pod Lifecycle, Probes & Disruption Budgets | 45 min |
| 5 | [exercises/README.md](exercises/README.md) | Hands-on Exercises | 90 min |

**Total estimated time: 4–5 hours**

---

## Prerequisites

- [Module 04 — Kubernetes Architecture](../04-kubernetes-architecture/) (control plane, node architecture, kubectl)
- A running `kind` cluster with the DeployForge namespace (from Module 04 exercises)
- kubectl installed (`brew install kubectl`)
- Basic YAML syntax (`key: value`, lists, nested objects)

---

## Capstone Milestone

> **Goal:** All DeployForge core services deployed as the correct workload types with proper scheduling constraints, health probes, and disruption budgets.

By the end of this module you'll have:

| Artifact | Description |
|----------|-------------|
| API Gateway Deployment | Express/TypeScript gateway with RollingUpdate strategy, resource limits, and probes |
| Worker Deployment | BullMQ worker service with separate resource profile and scaling config |
| PostgreSQL StatefulSet | Stateful database with stable network identity and persistent volume claims |
| Redis StatefulSet | Stateful cache with ordered startup and persistent storage |
| Nginx DaemonSet | Reverse proxy running on every worker node |
| Scheduling constraints | Node affinity, pod anti-affinity, and topology spread for HA |
| PodDisruptionBudgets | PDBs protecting API Gateway and database during maintenance |

```
┌──────────────────────────────────────────────────────────────────┐
│                    kind Cluster — DeployForge                     │
│                                                                  │
│  ┌──────────────── Worker Node 1 ──────────────────┐             │
│  │  ┌──────────────┐  ┌────────────────────────┐   │             │
│  │  │ Nginx (DS)   │  │ API Gateway (Deploy)   │   │             │
│  │  │ port 80/443  │  │ replicas: 2            │   │             │
│  │  └──────────────┘  └────────────────────────┘   │             │
│  │  ┌──────────────┐  ┌────────────────────────┐   │             │
│  │  │ PostgreSQL   │  │ Worker (Deploy)         │   │             │
│  │  │ (STS pod-0)  │  │ replicas: 2            │   │             │
│  │  └──────────────┘  └────────────────────────┘   │             │
│  └─────────────────────────────────────────────────┘             │
│                                                                  │
│  ┌──────────────── Worker Node 2 ──────────────────┐             │
│  │  ┌──────────────┐  ┌────────────────────────┐   │             │
│  │  │ Nginx (DS)   │  │ API Gateway (Deploy)   │   │             │
│  │  │ port 80/443  │  │ replicas: 2            │   │             │
│  │  └──────────────┘  └────────────────────────┘   │             │
│  │  ┌──────────────┐  ┌────────────────────────┐   │             │
│  │  │ Redis        │  │ Worker (Deploy)         │   │             │
│  │  │ (STS pod-0)  │  │ replicas: 2            │   │             │
│  │  └──────────────┘  └────────────────────────┘   │             │
│  └─────────────────────────────────────────────────┘             │
│                                                                  │
│  Pod Anti-Affinity: API Gateway pods spread across nodes         │
│  Topology Spread: Workers distributed evenly                     │
│  PDBs: API Gateway minAvailable=1, PostgreSQL minAvailable=1     │
└──────────────────────────────────────────────────────────────────┘
```

---

## How to Work Through This Module

1. Read each concept file in order (01 → 02 → 03 → 04).
2. Run every code example — apply manifests to your kind cluster and observe the results.
3. Complete the exercises in `exercises/README.md`.
4. Check off the learning objectives above as you master each one.
5. Move to [Module 06 — Networking & Services](../06-networking-and-services/) when ready.
