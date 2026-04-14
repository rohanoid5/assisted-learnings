# Module 07 — Storage & Configuration

## Overview

Containers are ephemeral by design — but your data shouldn't be. Kubernetes provides a rich storage subsystem that decouples the lifecycle of data from the lifecycle of pods, letting you run stateful workloads (databases, message queues, file stores) alongside stateless services. Meanwhile, configuration management — how you inject environment-specific settings, credentials, and feature flags into your pods — is what separates "works on my machine" from "works in every environment."

This module takes you from the fundamentals of PersistentVolumes and PersistentVolumeClaims through StorageClasses and dynamic provisioning, then into ConfigMaps, Secrets, and external secrets management. You'll graduate to Helm charts for packaging and templating your Kubernetes manifests, and finally Kustomize for overlay-based environment management. By the end, you'll understand exactly how data persists across pod restarts, how secrets flow from a vault into a container, and how a single chart definition renders into dev, staging, and production manifests.

By the end of this module, DeployForge will have persistent storage for its PostgreSQL and Redis StatefulSets, externalized configuration and secrets, a fully functional Helm chart, and Kustomize overlays for dev/staging/prod — the configuration management backbone of a production deployment.

---

## Learning Objectives

After completing this module, you should be able to:

- [ ] Explain the PersistentVolume → PersistentVolumeClaim → Pod binding lifecycle
- [ ] Configure PVs with appropriate access modes (RWO, ROX, RWX) and reclaim policies
- [ ] Define StorageClasses for dynamic provisioning with different backends
- [ ] Use CSI drivers to integrate cloud and third-party storage systems
- [ ] Create and restore VolumeSnapshots for backup and migration
- [ ] Inject configuration into pods using ConfigMaps (literals, files, envFrom)
- [ ] Manage sensitive data with Kubernetes Secrets (Opaque, TLS, docker-registry)
- [ ] Explain the security implications of base64-encoded Secrets and mitigations
- [ ] Set up External Secrets Operator to sync secrets from AWS SM, Vault, or GCP SM
- [ ] Use sealed-secrets for GitOps-safe secret management
- [ ] Create a Helm chart with values.yaml, Go templates, named templates, and helpers
- [ ] Manage chart dependencies and lifecycle hooks
- [ ] Use `helm install`, `upgrade`, `rollback`, and `test` workflows
- [ ] Build Kustomize bases and overlays for multi-environment deployments
- [ ] Use configMapGenerator, secretGenerator, and patches in kustomization.yaml
- [ ] Compare Helm and Kustomize and choose the right tool for each scenario

---

## Topics

| # | File | Topic | Est. Time |
|---|------|-------|-----------|
| 1 | [01-persistent-storage.md](01-persistent-storage.md) | PersistentVolumes, PVCs & StorageClasses | 50 min |
| 2 | [02-configmaps-and-secrets.md](02-configmaps-and-secrets.md) | ConfigMaps, Secrets & External Secrets | 50 min |
| 3 | [03-helm-charts.md](03-helm-charts.md) | Helm Charts: Templating & Package Management | 50 min |
| 4 | [04-kustomize.md](04-kustomize.md) | Kustomize: Overlay-Based Configuration | 40 min |
| 5 | [exercises/README.md](exercises/README.md) | Hands-on Exercises | 90 min |

**Total estimated time: 4–5 hours**

---

## Prerequisites

- [Module 06 — Networking & Services](../06-networking-and-services/) (Services, Ingress, NetworkPolicies, DNS)
- A running `kind` cluster with the `deployforge` namespace and workloads from Module 06
- kubectl installed (`brew install kubectl`)
- Helm 3.x installed (`brew install helm`)
- Kustomize installed (`brew install kustomize`) or use `kubectl -k`
- Basic familiarity with Go template syntax (for Helm)

---

## Capstone Milestone

> **Goal:** DeployForge backed by persistent storage, externalized config/secrets, a Helm chart for packaging, and Kustomize overlays for per-environment configuration.

By the end of this module you'll have:

| Artifact | Description |
|----------|-------------|
| PersistentVolumeClaims | Dynamic PVCs for PostgreSQL WAL/data and Redis AOF persistence |
| StorageClass | Custom `deployforge-fast` StorageClass for SSD-backed database volumes |
| ConfigMaps | Application config (feature flags, API URLs, log levels) per environment |
| Secrets | Database credentials, API keys, TLS certs managed via External Secrets Operator |
| Helm Chart | `deployforge/` chart with values.yaml, templates, helpers, hooks, and tests |
| Kustomize Overlays | `base/`, `overlays/dev/`, `overlays/staging/`, `overlays/prod/` |
| External Secrets | ExternalSecret CRs syncing from HashiCorp Vault to K8s Secrets |

```
┌─────────────────────────────────────────────────────────────────────┐
│              kind Cluster — DeployForge Storage & Config             │
│                                                                     │
│  ┌──── Helm Chart (deployforge/) ─────────────────────────────────┐ │
│  │                                                                 │ │
│  │  values.yaml ──▶ templates/ ──▶ rendered manifests              │ │
│  │  (dev / staging / prod values)                                  │ │
│  └─────────────────────────────────────────────────────────────────┘ │
│                           │                                         │
│  ┌────────────────────────▼────────────────────────────────────────┐ │
│  │              Kustomize Overlays                                  │ │
│  │  base/ ──▶ overlays/dev/   (1 replica, debug logging)           │ │
│  │        ──▶ overlays/staging/ (2 replicas, INFO logging)         │ │
│  │        ──▶ overlays/prod/  (3 replicas, WARN logging, HA)      │ │
│  └─────────────────────────────────────────────────────────────────┘ │
│                                                                     │
│  ┌──── Config Layer ──────────────────────────────────────────────┐ │
│  │                                                                 │ │
│  │  ConfigMap: deployforge-config     Secret: deployforge-secrets   │ │
│  │  ┌─────────────────────┐          ┌─────────────────────────┐   │ │
│  │  │ LOG_LEVEL=info       │          │ DB_PASSWORD=•••••        │   │ │
│  │  │ API_URL=http://...   │          │ REDIS_PASSWORD=•••••     │   │ │
│  │  │ FEATURE_FLAGS=...    │          │ API_KEY=•••••            │   │ │
│  │  └─────────────────────┘          └──────────┬──────────────┘   │ │
│  │                                              │                  │ │
│  │                        External Secrets Operator                 │ │
│  │                        ┌──────────────┐                         │ │
│  │                        │ HashiCorp    │                         │ │
│  │                        │ Vault        │ ◀── source of truth     │ │
│  │                        └──────────────┘                         │ │
│  └─────────────────────────────────────────────────────────────────┘ │
│                                                                     │
│  ┌──── Storage Layer ─────────────────────────────────────────────┐ │
│  │                                                                 │ │
│  │  StorageClass: deployforge-fast (provisioner: rancher.io/...)   │ │
│  │                                                                 │ │
│  │  ┌─── PostgreSQL STS ────────┐  ┌─── Redis STS ─────────────┐  │ │
│  │  │  Pod: postgres-0           │  │  Pod: redis-0              │  │ │
│  │  │  ┌──────────────────────┐  │  │  ┌──────────────────────┐  │  │ │
│  │  │  │ PVC: postgres-data    │  │  │  │ PVC: redis-data       │  │  │ │
│  │  │  │ 10Gi RWO             │  │  │  │ 5Gi RWO               │  │  │ │
│  │  │  │ ──▶ PV (dynamic)     │  │  │  │ ──▶ PV (dynamic)      │  │  │ │
│  │  │  └──────────────────────┘  │  │  └──────────────────────┘  │  │ │
│  │  └────────────────────────────┘  └────────────────────────────┘  │ │
│  │                                                                 │ │
│  │  VolumeSnapshots: nightly backups of postgres-data PVC          │ │
│  └─────────────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────────┘
```

---

## How to Work Through This Module

1. Read each concept file in order (01 → 02 → 03 → 04).
2. Apply every YAML manifest to your kind cluster and observe the results.
3. Use `kubectl describe`, `kubectl get events`, and `helm status` to verify behavior.
4. Complete the exercises in `exercises/README.md`.
5. Check off the learning objectives above as you master each one.
6. Move to [Module 08 — Observability](../08-observability/) when ready.
