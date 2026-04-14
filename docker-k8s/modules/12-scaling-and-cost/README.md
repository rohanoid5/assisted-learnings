# Module 12 — Scaling & Cost Optimization

A Kubernetes cluster that never scales is either wasting money or about to fall over.
Over-provisioned clusters burn budget on idle CPU and memory; under-provisioned ones
crumble the moment a traffic spike hits. The gap between "it works in dev" and
"it survives Black Friday" is filled by autoscaling — letting the platform itself
match capacity to demand, pod by pod and node by node.

This module takes you through every layer of Kubernetes autoscaling — from the
Horizontal Pod Autoscaler that adds replicas under load, to the Vertical Pod
Autoscaler that right-sizes individual containers, to the Cluster Autoscaler (and
Karpenter) that provisions the underlying nodes. You'll then shift to the financial
side: resource quotas, limit ranges, priority classes, and FinOps practices that
keep costs visible and accountable. By the end, you'll be able to design a system
that scales precisely, recovers gracefully, and doesn't surprise anyone with the
cloud bill.

---

## Learning Objectives

- [ ] Configure HPA v2 with CPU, memory, and custom metrics
- [ ] Tune HPA scaling behavior policies (stabilization windows, rate limits)
- [ ] Deploy metrics-server and Prometheus adapter for custom metrics
- [ ] Implement VPA in recommendation, initial, and auto modes
- [ ] Understand HPA + VPA compatibility constraints and workarounds
- [ ] Describe the Cluster Autoscaler algorithm (scale-up triggers, scale-down heuristics)
- [ ] Compare Cluster Autoscaler with Karpenter for node provisioning
- [ ] Design node pool strategies with spot/preemptible instances
- [ ] Set ResourceQuotas and LimitRanges for multi-tenant namespaces
- [ ] Apply PriorityClasses to protect critical workloads during resource pressure
- [ ] Use Kubecost / OpenCost for cost allocation and idle-resource detection
- [ ] Build a capacity-planning model using request-based and utilization-based data

---

## Topics

| # | File | Topic | Est. Time |
|---|------|-------|-----------|
| 1 | [01-horizontal-and-vertical-autoscaling.md](01-horizontal-and-vertical-autoscaling.md) | HPA & VPA: Pod Autoscaling | 55 min |
| 2 | [02-cluster-autoscaling.md](02-cluster-autoscaling.md) | Cluster Autoscaler & Node Management | 45 min |
| 3 | [03-resource-management-and-finops.md](03-resource-management-and-finops.md) | Resource Management & FinOps | 50 min |
| 4 | [exercises/README.md](exercises/README.md) | Hands-on practice | 75 min |

**Total estimated time: 3.5–4 hours**

---

## Prerequisites

- [Module 11 — Infrastructure as Code](../11-infrastructure-as-code/) completed
- A running Kubernetes cluster (minikube, kind, or cloud-managed)
- `kubectl` configured and pointing at the cluster
- `metrics-server` installed (comes by default on most managed clusters):
  ```bash
  kubectl top nodes
  # → should return CPU/memory usage, not an error
  ```
- Helm 3 installed (for Prometheus adapter and Kubecost exercises):
  ```bash
  helm version --short
  # → v3.x.x
  ```

---

## Capstone Milestone

**Goal:** DeployForge auto-scales at every layer. The API gateway uses HPA with
custom metrics (requests-per-second) so replicas match real traffic, not just CPU.
Workers use VPA to right-size memory requests based on job profiles. Resource quotas
enforce per-namespace budgets so a runaway staging deployment can't starve production.

| Artifact | Description |
|----------|-------------|
| `k8s/scaling/hpa-api.yaml` | HPA v2 targeting custom `http_requests_per_second` metric |
| `k8s/scaling/vpa-worker.yaml` | VPA in `Auto` mode for background workers |
| `k8s/scaling/resource-quota.yaml` | ResourceQuota per environment namespace |
| `k8s/scaling/limit-range.yaml` | LimitRange defaults for all pods in namespace |
| `k8s/scaling/priority-classes.yaml` | PriorityClasses for critical vs best-effort workloads |

```
┌─────────────────────────────────────────────────────────────┐
│                    DeployForge Scaling                       │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  Prometheus ──▶ Prometheus Adapter ──▶ Custom Metrics API   │
│       │                                       │             │
│       ▼                                       ▼             │
│  ┌──────────┐    ┌──────────────┐    ┌──────────────┐      │
│  │ Kubecost │    │ HPA v2       │    │ VPA          │      │
│  │ (costs)  │    │ (API pods)   │    │ (workers)    │      │
│  └──────────┘    └──────┬───────┘    └──────┬───────┘      │
│                         │                   │               │
│                         ▼                   ▼               │
│              ┌─────────────────────────────────────┐        │
│              │  Namespace: deployforge-prod         │        │
│              │  ┌─────────┐  ┌─────────┐           │        │
│              │  │ API ×3  │  │ Worker  │           │        │
│              │  │ (HPA)   │  │ (VPA)   │           │        │
│              │  └─────────┘  └─────────┘           │        │
│              │  ResourceQuota: 8 CPU / 16Gi mem    │        │
│              │  LimitRange:   default 500m / 512Mi │        │
│              └─────────────────────────────────────┘        │
│                         │                                   │
│              ┌──────────▼──────────┐                        │
│              │  Cluster Autoscaler │                        │
│              │  (node provisioning)│                        │
│              └─────────────────────┘                        │
└─────────────────────────────────────────────────────────────┘
```

---

## How to Work Through This Module

1. **Read** each concept file in order — HPA/VPA before cluster autoscaling, cluster
   autoscaling before FinOps — because each layer builds on the one below it.
2. **Run** every `kubectl` command and YAML manifest on a local cluster; autoscaling
   behavior only makes sense when you see it react.
3. **Complete** the Try It Yourself challenges in each concept file to build muscle
   memory before the exercises.
4. **Build** all four exercises — they progressively add scaling to DeployForge.
5. **Check** your progress against the learning objectives above.
6. **Move on** to [Module 13 — Capstone Integration](../13-capstone-integration/)
   once all objectives are checked.
