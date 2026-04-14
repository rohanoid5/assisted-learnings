# Module 06 — Networking & Services

## Overview

Kubernetes networking is notoriously complex — and for good reason. The platform provides a flat, routable network where every pod gets its own IP address, services abstract away pod churn, and Ingress controllers terminate TLS at the edge. Understanding how packets actually flow through a cluster is what separates "it works in dev" from "it's production-ready."

This module takes you from the fundamental networking model (how pods talk to each other across nodes) through the service abstraction layer (ClusterIP, NodePort, LoadBalancer), up to Ingress controllers with TLS termination, and finally into NetworkPolicies for microsegmentation and service mesh architecture. You'll learn how kube-proxy programs iptables rules, why headless services exist, how CoreDNS resolves service names, and what happens when a packet travels from an external client to a pod.

By the end of this module, DeployForge's services will be exposed via a properly configured Ingress with TLS, and NetworkPolicies will restrict traffic between the frontend, backend, and data tiers — the networking foundation of a production deployment.

---

## Learning Objectives

After completing this module, you should be able to:

- [ ] Explain the Kubernetes flat networking model and its three requirements
- [ ] Describe how CNI plugins (Calico, Cilium, Flannel) implement pod networking
- [ ] Trace the packet path from one pod to another on a different node
- [ ] Configure ClusterIP, NodePort, LoadBalancer, ExternalName, and Headless services
- [ ] Explain how kube-proxy uses iptables or IPVS to implement service routing
- [ ] Use CoreDNS to resolve services within and across namespaces
- [ ] Configure Endpoints and EndpointSlices for service discovery
- [ ] Set up an Nginx Ingress controller with path-based and host-based routing
- [ ] Configure TLS termination with cert-manager and Let's Encrypt
- [ ] Implement default-deny NetworkPolicies for microsegmentation
- [ ] Write ingress and egress NetworkPolicy rules with pod, namespace, and CIDR selectors
- [ ] Explain service mesh architecture (data plane vs control plane)
- [ ] Describe how Istio/Linkerd provide mTLS, traffic management, and observability
- [ ] Debug network issues with `kubectl exec`, `nslookup`, `curl`, and `tcpdump`

---

## Topics

| # | File | Topic | Est. Time |
|---|------|-------|-----------|
| 1 | [01-kubernetes-networking-model.md](01-kubernetes-networking-model.md) | K8s Networking Model & CNI | 50 min |
| 2 | [02-services-and-discovery.md](02-services-and-discovery.md) | Services, DNS & Service Discovery | 50 min |
| 3 | [03-ingress-and-load-balancing.md](03-ingress-and-load-balancing.md) | Ingress Controllers & Load Balancing | 50 min |
| 4 | [04-network-policies-and-service-mesh.md](04-network-policies-and-service-mesh.md) | NetworkPolicies & Service Mesh | 50 min |
| 5 | [exercises/README.md](exercises/README.md) | Hands-on Exercises | 90 min |

**Total estimated time: 4–5 hours**

---

## Prerequisites

- [Module 05 — Workloads & Scheduling](../05-workloads-and-scheduling/) (Deployments, StatefulSets, DaemonSets, pod lifecycle)
- A running `kind` cluster with the `deployforge` namespace and services from Module 05
- TCP/IP networking fundamentals (IP addressing, ports, DNS, HTTP)
- kubectl installed (`brew install kubectl`)

---

## Capstone Milestone

> **Goal:** DeployForge services exposed via Ingress with TLS termination, and NetworkPolicies enforcing microsegmentation between tiers.

By the end of this module you'll have:

| Artifact | Description |
|----------|-------------|
| ClusterIP Services | Internal services for API Gateway, Worker, PostgreSQL, Redis |
| Headless Service | Stable DNS for PostgreSQL StatefulSet pods |
| Nginx Ingress Controller | Edge proxy routing external traffic into the cluster |
| Ingress Resource | Path-based routing to API Gateway with TLS termination |
| TLS Certificate | cert-manager issuing certificates for `deployforge.local` |
| NetworkPolicies | Default-deny + allow rules for frontend → backend → data tiers |
| CoreDNS Config | Service discovery verified across namespaces |

```
┌─────────────────────────────────────────────────────────────────────┐
│                   kind Cluster — DeployForge Networking              │
│                                                                     │
│  External Traffic                                                   │
│       │                                                             │
│       ▼                                                             │
│  ┌──────────────────────────────────────────────────┐               │
│  │          Nginx Ingress Controller (DaemonSet)     │               │
│  │          TLS termination + path routing            │               │
│  │          deployforge.local → /api, /health        │               │
│  └──────────────┬───────────────────────────────────┘               │
│                 │                                                    │
│    ┌────────────┴────────────┐                                      │
│    ▼                         ▼                                      │
│  ┌──────────────┐   ┌──────────────────┐                            │
│  │  api-gateway  │   │  api-gateway      │  ← ClusterIP Service     │
│  │  (Pod)        │   │  (Pod)            │     :3000                 │
│  └──────┬───────┘   └────────┬─────────┘                            │
│         │                    │                                       │
│         │    NetworkPolicy: frontend → backend only                  │
│         ▼                    ▼                                       │
│  ┌────────────────────────────────────┐                             │
│  │       worker ClusterIP Service      │                             │
│  │       :4000                         │                             │
│  │  ┌──────────┐  ┌──────────┐        │                             │
│  │  │ Worker   │  │ Worker   │        │                             │
│  │  │ (Pod)    │  │ (Pod)    │        │                             │
│  │  └────┬─────┘  └────┬─────┘       │                             │
│  └───────┼──────────────┼─────────────┘                             │
│          │              │                                            │
│          │   NetworkPolicy: backend → data only                      │
│          ▼              ▼                                            │
│  ┌──────────────┐  ┌──────────────┐                                 │
│  │  PostgreSQL   │  │  Redis        │  ← Headless + ClusterIP       │
│  │  (STS pod-0)  │  │  (STS pod-0)  │     Services                  │
│  └──────────────┘  └──────────────┘                                 │
│                                                                     │
│  NetworkPolicy: data tier denies all ingress except from backend    │
└─────────────────────────────────────────────────────────────────────┘
```

---

## How to Work Through This Module

1. Read each concept file in order (01 → 02 → 03 → 04).
2. Run every code example — apply manifests to your kind cluster and observe the results.
3. Use `kubectl exec` and `nslookup` to verify networking concepts hands-on.
4. Complete the exercises in `exercises/README.md`.
5. Check off the learning objectives above as you master each one.
6. Move to [Module 07 — Storage & Configuration](../07-storage-and-configuration/) when ready.
