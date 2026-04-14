# Module 11 — Infrastructure as Code

Clicking through a cloud console to provision infrastructure is like deploying code
by SSHing into prod and editing files — it works once, can't be reviewed, can't be
reproduced, and leaves zero audit trail. Infrastructure as Code (IaC) treats your
servers, networks, and clusters as declarative artifacts that live in version control,
go through pull-request review, and converge toward a desired state on every apply.

This module focuses on **Terraform** — the dominant multi-cloud IaC tool — and
explores how it fits into the Kubernetes ecosystem alongside alternatives like
**Pulumi** and **Crossplane**. You'll learn to write HCL configurations, design
reusable modules, manage state safely across teams, and provision production-grade
Kubernetes clusters entirely from code.

By the end of this module you'll be able to `terraform plan` with confidence,
knowing exactly what will change before it changes — and `terraform apply` knowing
you can reproduce the same infrastructure in any environment.

---

## Learning Objectives

- [ ] Write Terraform configurations with providers, resources, and data sources
- [ ] Use variables, outputs, locals, `count`, and `for_each` effectively
- [ ] Understand the `plan → apply → destroy` lifecycle and state mechanics
- [ ] Design reusable Terraform modules with clean interfaces
- [ ] Manage Terraform state safely (remote backends, locking, workspaces)
- [ ] Reduce blast radius through state separation strategies
- [ ] Provision Kubernetes clusters (EKS / GKE / AKS) with Terraform
- [ ] Compare Terraform with Pulumi and Crossplane for K8s infrastructure
- [ ] Integrate IaC into GitOps workflows with automated plan/apply
- [ ] Test IaC with `terraform validate`, `tflint`, and Terratest

---

## Topics

| # | File | Topic | Est. Time |
|---|------|-------|-----------|
| 1 | [01-terraform-fundamentals.md](01-terraform-fundamentals.md) | Terraform Fundamentals | 60 min |
| 2 | [02-modules-and-state.md](02-modules-and-state.md) | Modules, State & Workspaces | 50 min |
| 3 | [03-iac-for-kubernetes.md](03-iac-for-kubernetes.md) | IaC for Kubernetes Infrastructure | 50 min |
| 4 | [exercises/README.md](exercises/README.md) | Hands-on practice | 75 min |

**Total estimated time: 3.5–4 hours**

---

## Prerequisites

- [Module 10 — CI/CD & GitOps](../10-ci-cd-and-gitops/) completed
- Basic cloud concepts (VPCs, subnets, IAM roles)
- Terraform CLI installed (`brew install terraform` or [terraform.io](https://developer.hashicorp.com/terraform/install))
- A cloud provider account (AWS Free Tier, GCP Free Tier, or Azure trial) — or use [LocalStack](https://localstack.cloud/) for local simulation
- `kind` cluster running for Kubernetes-provider exercises

---

## Capstone Milestone

**Goal:** Define the entire DeployForge infrastructure as Terraform modules — networking, Kubernetes cluster, databases, and monitoring — so that any environment can be reproduced with a single `terraform apply`.

| Artifact | Description |
|----------|-------------|
| `infra/modules/networking/` | VPC, subnets, NAT gateway, security groups |
| `infra/modules/cluster/` | EKS/GKE cluster with managed node groups |
| `infra/modules/database/` | PostgreSQL RDS/Cloud SQL with backups |
| `infra/modules/monitoring/` | Prometheus + Grafana Helm releases via Terraform |
| `infra/envs/dev/` | Dev environment composition |
| `infra/envs/staging/` | Staging environment composition |
| `infra/envs/prod/` | Production environment composition |
| `infra/backend.tf` | Remote state with S3 + DynamoDB locking |

```
┌─────────────────────────────────────────────────────────────────┐
│                    DeployForge IaC Layout                        │
│                                                                 │
│  infra/                                                         │
│  ├── modules/                                                   │
│  │   ├── networking/   ─── VPC, subnets, SGs, NAT              │
│  │   ├── cluster/      ─── EKS + node groups + IRSA            │
│  │   ├── database/     ─── RDS PostgreSQL + secrets             │
│  │   └── monitoring/   ─── Helm: Prometheus, Grafana, Loki     │
│  │                                                              │
│  ├── envs/                                                      │
│  │   ├── dev/          ─── terraform.tfvars (small instances)   │
│  │   ├── staging/      ─── terraform.tfvars (mid instances)     │
│  │   └── prod/         ─── terraform.tfvars (HA, multi-AZ)     │
│  │                                                              │
│  └── backend.tf        ─── S3 + DynamoDB state backend          │
│                                                                 │
│  CI Pipeline:                                                   │
│  PR opened ──▶ terraform plan ──▶ comment diff ──▶ approve      │
│                                   ──▶ terraform apply           │
└─────────────────────────────────────────────────────────────────┘
```

---

## How to Work Through This Module

1. **Read** each concept file in order — they build on each other.
2. **Run** every code example locally (Terraform init/plan against LocalStack or a free-tier account).
3. **Complete** the Try It Yourself challenges in each concept file.
4. **Build** the exercises to assemble DeployForge's IaC layer.
5. **Check** your progress against the learning objectives above.
6. **Move on** to [Module 12 — Scaling & Cost](../12-scaling-and-cost/) once all objectives are checked.
