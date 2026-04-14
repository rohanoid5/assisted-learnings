# Module 04 — Kubernetes Architecture

## Overview

Understand how Kubernetes works from the inside out. This module takes you beyond "just apply the YAML" to a deep understanding of the control plane components, node architecture, and the machinery that turns your declarative manifests into running containers.

You'll learn how the API server processes every request, why etcd is the most critical component in the cluster, how the scheduler decides where to place your pods, and how the kubelet actually starts containers on each node. This understanding is what separates "I use Kubernetes" from "I can debug and operate Kubernetes."

By the end of this module, you'll have a local `kind` cluster running and understand the complete request flow from `kubectl apply` to a container running on a node.

---

## Learning Objectives

After completing this module, you should be able to:

- [ ] Diagram the Kubernetes control plane and explain each component's role
- [ ] Explain how the API server processes requests (authentication → authorization → admission → persistence)
- [ ] Describe etcd's role as the source of truth and why it's the most critical component
- [ ] Explain how the watch mechanism and informers drive the control loop
- [ ] Compare HA control plane topologies (stacked etcd vs external etcd)
- [ ] Describe the kubelet's responsibilities: pod lifecycle, probes, resource management
- [ ] Explain the Container Runtime Interface (CRI) and how containerd/CRI-O fit in
- [ ] Compare kube-proxy modes: iptables vs IPVS
- [ ] Use kubectl effectively for cluster exploration and debugging
- [ ] Explain the API request flow from kubectl to etcd and back
- [ ] Navigate API groups, versions, and resources (GVR)
- [ ] Set up a local multi-node Kubernetes cluster with kind

---

## Topics

| # | File | Topic | Est. Time |
|---|------|-------|-----------|
| 1 | [01-control-plane-components.md](01-control-plane-components.md) | Control Plane Deep Dive | 60 min |
| 2 | [02-node-architecture.md](02-node-architecture.md) | Node Architecture: Kubelet, Kube-Proxy & CRI | 45 min |
| 3 | [03-kubectl-and-api.md](03-kubectl-and-api.md) | kubectl, API Server & Resource Model | 45 min |
| 4 | [exercises/README.md](exercises/README.md) | Hands-on Exercises | 60 min |

**Total estimated time: 3–4 hours**

---

## Prerequisites

- [Module 01 — Container Fundamentals](../01-container-fundamentals/) (namespaces, cgroups, OCI)
- [Module 02 — Docker Mastery](../02-docker-mastery/) (Dockerfiles, Compose, networking)
- [Module 03 — Container Security](../03-container-security/) (image scanning, runtime security)
- Basic YAML syntax (`key: value`, lists, nested objects)
- Docker running locally (`docker info` works)
- kind installed (`brew install kind`)
- kubectl installed (`brew install kubectl`)

---

## Capstone Milestone

> **Goal:** Local `kind` cluster running with a DeployForge namespace created. Understand the full request flow from `kubectl apply` to a container running on a node.

By the end of this module you'll have:

| Artifact | Description |
|----------|-------------|
| `kind` cluster | Multi-node cluster (1 control-plane, 2 workers) for local development |
| DeployForge namespace | Isolated namespace for all DeployForge resources |
| Mental model | Complete understanding of the control plane → node → container pipeline |

```
┌──────────────────────────────────────────────────────────────┐
│                    kind Cluster                               │
│                                                              │
│  ┌─────────────────────────────────────────────────────┐     │
│  │              Control Plane Node                      │     │
│  │  ┌──────────┐ ┌───────────┐ ┌────────────────────┐ │     │
│  │  │API Server│ │ Scheduler │ │Controller Manager   │ │     │
│  │  └──────────┘ └───────────┘ └────────────────────┘ │     │
│  │  ┌──────────┐                                       │     │
│  │  │  etcd    │                                       │     │
│  │  └──────────┘                                       │     │
│  └─────────────────────────────────────────────────────┘     │
│                                                              │
│  ┌────────────────────┐     ┌────────────────────┐           │
│  │  Worker Node 1      │     │  Worker Node 2      │          │
│  │  ┌───────┐ ┌──────┐│     │  ┌───────┐ ┌──────┐│          │
│  │  │kubelet│ │proxy ││     │  │kubelet│ │proxy ││          │
│  │  └───────┘ └──────┘│     │  └───────┘ └──────┘│          │
│  │  ┌─────────────────┐│     │  ┌─────────────────┐│          │
│  │  │  DeployForge    ││     │  │  DeployForge    ││          │
│  │  │  Pods           ││     │  │  Pods           ││          │
│  │  └─────────────────┘│     │  └─────────────────┘│          │
│  └────────────────────┘     └────────────────────┘           │
└──────────────────────────────────────────────────────────────┘
```

---

## How to Work Through This Module

1. Read each concept file in order (01 → 02 → 03).
2. Run every code example — spin up the kind cluster and explore it hands-on.
3. Complete the exercises in `exercises/README.md`.
4. Check off the learning objectives above as you master each one.
5. Move to [Module 05 — Workloads & Scheduling](../05-workloads-and-scheduling/) when ready.
