# 11.3 — IaC for Kubernetes Infrastructure

## Concept

Terraform doesn't just provision the cloud resources **around** Kubernetes — it
can provision the cluster itself, install Helm charts, and even manage Kubernetes
resources directly. But knowing **when** to use Terraform for K8s resources versus
when to hand off to GitOps tools (ArgoCD, Flux) is the difference between a clean
architecture and an unmaintainable mess. This section covers cluster provisioning
across all three major clouds, the Kubernetes and Helm Terraform providers, and
alternative IaC approaches — Crossplane and Pulumi — that challenge Terraform's
dominance in the cloud-native space.

---

## Deep Dive

### Provisioning Kubernetes Clusters with Terraform

#### EKS (Amazon Elastic Kubernetes Service)

```hcl
# modules/cluster/main.tf — EKS cluster

resource "aws_eks_cluster" "main" {
  name     = "deployforge-${var.environment}"
  role_arn = aws_iam_role.cluster.arn
  version  = var.kubernetes_version

  vpc_config {
    subnet_ids              = var.private_subnet_ids
    endpoint_private_access = true
    endpoint_public_access  = var.environment != "prod"  # ← public API in dev only
    security_group_ids      = [aws_security_group.cluster.id]
  }

  encryption_config {
    provider {
      key_arn = aws_kms_key.eks.arn       # ← encrypt secrets at rest
    }
    resources = ["secrets"]
  }

  enabled_cluster_log_types = [
    "api", "audit", "authenticator",       # ← ship control plane logs to CloudWatch
    "controllerManager", "scheduler"
  ]

  tags = var.tags

  depends_on = [
    aws_iam_role_policy_attachment.cluster_policy,
    aws_iam_role_policy_attachment.cluster_vpc_policy,
  ]
}

# Managed node group — Terraform manages the ASG, AWS manages the nodes
resource "aws_eks_node_group" "main" {
  cluster_name    = aws_eks_cluster.main.name
  node_group_name = "deployforge-workers-${var.environment}"
  node_role_arn   = aws_iam_role.node.arn
  subnet_ids      = var.private_subnet_ids
  instance_types  = [var.node_instance_type]

  scaling_config {
    desired_size = var.node_desired_count
    min_size     = var.node_min_count
    max_size     = var.node_max_count
  }

  update_config {
    max_unavailable_percentage = 25       # ← rolling update: 25% at a time
  }

  labels = {
    role        = "worker"
    environment = var.environment
  }

  tags = var.tags

  depends_on = [
    aws_iam_role_policy_attachment.node_policy,
    aws_iam_role_policy_attachment.node_cni_policy,
    aws_iam_role_policy_attachment.node_ecr_policy,
  ]
}
```

```hcl
# modules/cluster/iam.tf — IAM roles for EKS

# Cluster IAM role
resource "aws_iam_role" "cluster" {
  name = "deployforge-eks-cluster-${var.environment}"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Action = "sts:AssumeRole"
      Effect = "Allow"
      Principal = {
        Service = "eks.amazonaws.com"
      }
    }]
  })
}

resource "aws_iam_role_policy_attachment" "cluster_policy" {
  policy_arn = "arn:aws:iam::aws:policy/AmazonEKSClusterPolicy"
  role       = aws_iam_role.cluster.name
}

resource "aws_iam_role_policy_attachment" "cluster_vpc_policy" {
  policy_arn = "arn:aws:iam::aws:policy/AmazonEKSVPCResourceController"
  role       = aws_iam_role.cluster.name
}

# Node IAM role
resource "aws_iam_role" "node" {
  name = "deployforge-eks-node-${var.environment}"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Action = "sts:AssumeRole"
      Effect = "Allow"
      Principal = {
        Service = "ec2.amazonaws.com"
      }
    }]
  })
}

resource "aws_iam_role_policy_attachment" "node_policy" {
  policy_arn = "arn:aws:iam::aws:policy/AmazonEKSWorkerNodePolicy"
  role       = aws_iam_role.node.name
}

resource "aws_iam_role_policy_attachment" "node_cni_policy" {
  policy_arn = "arn:aws:iam::aws:policy/AmazonEKS_CNI_Policy"
  role       = aws_iam_role.node.name
}

resource "aws_iam_role_policy_attachment" "node_ecr_policy" {
  policy_arn = "arn:aws:iam::aws:policy/AmazonEC2ContainerRegistryReadOnly"
  role       = aws_iam_role.node.name
}

# IRSA — IAM Roles for Service Accounts
data "tls_certificate" "eks" {
  url = aws_eks_cluster.main.identity[0].oidc[0].issuer
}

resource "aws_iam_openid_connect_provider" "eks" {
  client_id_list  = ["sts.amazonaws.com"]
  thumbprint_list = [data.tls_certificate.eks.certificates[0].sha1_fingerprint]
  url             = aws_eks_cluster.main.identity[0].oidc[0].issuer
}
```

> **Key insight:** **IRSA** (IAM Roles for Service Accounts) lets Kubernetes pods
> assume fine-grained IAM roles without sharing node-level credentials. This is
> the gold standard for AWS workload identity — every DeployForge microservice gets
> its own IAM role scoped to exactly the AWS resources it needs.

#### GKE (Google Kubernetes Engine)

```hcl
# modules/cluster/gke.tf — GKE Autopilot cluster

resource "google_container_cluster" "main" {
  name     = "deployforge-${var.environment}"
  location = var.gcp_region

  # Autopilot mode — Google manages nodes
  enable_autopilot = true

  network    = var.vpc_id
  subnetwork = var.subnet_id

  ip_allocation_policy {
    cluster_secondary_range_name  = "pods"
    services_secondary_range_name = "services"
  }

  private_cluster_config {
    enable_private_nodes    = true
    enable_private_endpoint = var.environment == "prod"
    master_ipv4_cidr_block  = "172.16.0.0/28"
  }

  release_channel {
    channel = var.environment == "prod" ? "STABLE" : "REGULAR"
  }

  workload_identity_config {
    workload_pool = "${var.project_id}.svc.id.goog"
  }
}
```

#### AKS (Azure Kubernetes Service)

```hcl
# modules/cluster/aks.tf — AKS cluster

resource "azurerm_kubernetes_cluster" "main" {
  name                = "deployforge-${var.environment}"
  location            = var.azure_region
  resource_group_name = var.resource_group_name
  dns_prefix          = "deployforge-${var.environment}"
  kubernetes_version  = var.kubernetes_version

  default_node_pool {
    name                = "default"
    vm_size             = var.node_vm_size
    min_count           = var.node_min_count
    max_count           = var.node_max_count
    enable_auto_scaling = true
    vnet_subnet_id      = var.subnet_id
    os_disk_size_gb     = 100

    node_labels = {
      role        = "worker"
      environment = var.environment
    }
  }

  identity {
    type = "SystemAssigned"
  }

  network_profile {
    network_plugin    = "azure"          # ← Azure CNI for native pod networking
    network_policy    = "calico"
    load_balancer_sku = "standard"
  }

  oms_agent {
    log_analytics_workspace_id = var.log_analytics_workspace_id
  }
}
```

### Cloud Comparison

| Feature | **EKS** (AWS) | **GKE** (Google) | **AKS** (Azure) |
|---------|-----------|------------|-----------|
| **Terraform resource** | `aws_eks_cluster` | `google_container_cluster` | `azurerm_kubernetes_cluster` |
| **Managed node pools** | `aws_eks_node_group` | `google_container_node_pool` | `azurerm_kubernetes_cluster_node_pool` |
| **Serverless nodes** | Fargate profiles | Autopilot | Virtual nodes (ACI) |
| **Workload identity** | IRSA (OIDC) | Workload Identity | Workload Identity |
| **Control plane cost** | $0.10/hr (~$73/mo) | Free (Autopilot), $0.10/hr (Standard) | Free |
| **Node OS patching** | User manages (or use managed node groups) | Auto-upgrade channels | Auto-upgrade channels |
| **Terraform provider** | `hashicorp/aws` | `hashicorp/google` | `hashicorp/azurerm` |

### Kubernetes and Helm Terraform Providers

Once the cluster exists, Terraform can manage resources **inside** it:

```hcl
# Configure the Kubernetes provider using EKS cluster data
provider "kubernetes" {
  host                   = aws_eks_cluster.main.endpoint
  cluster_ca_certificate = base64decode(aws_eks_cluster.main.certificate_authority[0].data)

  exec {
    api_version = "client.authentication.k8s.io/v1beta1"
    command     = "aws"
    args        = ["eks", "get-token", "--cluster-name", aws_eks_cluster.main.name]
  }
}

# Configure the Helm provider
provider "helm" {
  kubernetes {
    host                   = aws_eks_cluster.main.endpoint
    cluster_ca_certificate = base64decode(aws_eks_cluster.main.certificate_authority[0].data)

    exec {
      api_version = "client.authentication.k8s.io/v1beta1"
      command     = "aws"
      args        = ["eks", "get-token", "--cluster-name", aws_eks_cluster.main.name]
    }
  }
}
```

```hcl
# Namespace management
resource "kubernetes_namespace" "deployforge" {
  metadata {
    name = "deployforge"
    labels = {
      "app.kubernetes.io/managed-by" = "terraform"
      environment                     = var.environment
    }
  }
}

# Install monitoring stack via Helm
resource "helm_release" "prometheus" {
  name       = "prometheus"
  repository = "https://prometheus-community.github.io/helm-charts"
  chart      = "kube-prometheus-stack"
  version    = "55.5.0"                  # ← pin chart version
  namespace  = "monitoring"
  create_namespace = true

  values = [
    templatefile("${path.module}/helm-values/prometheus.yaml", {
      environment    = var.environment
      retention_days = var.environment == "prod" ? 30 : 7
      storage_class  = "gp3"
      storage_size   = var.environment == "prod" ? "100Gi" : "20Gi"
    })
  ]

  set_sensitive {
    name  = "grafana.adminPassword"
    value = var.grafana_password
  }
}

# Install ingress controller
resource "helm_release" "ingress_nginx" {
  name       = "ingress-nginx"
  repository = "https://kubernetes.github.io/ingress-nginx"
  chart      = "ingress-nginx"
  version    = "4.9.0"
  namespace  = "ingress-system"
  create_namespace = true

  set {
    name  = "controller.service.type"
    value = "LoadBalancer"
  }

  set {
    name  = "controller.service.annotations.service\\.beta\\.kubernetes\\.io/aws-load-balancer-type"
    value = "nlb"
  }

  set {
    name  = "controller.metrics.enabled"
    value = "true"
  }
}
```

### When to Use Terraform vs. kubectl / GitOps

This is the **most important architectural decision** in IaC for Kubernetes:

```
┌─────────────────────────────────────────────────────────────────┐
│              IaC Responsibility Boundary                         │
│                                                                 │
│  ┌───────────────────────────────────┐                          │
│  │        TERRAFORM MANAGES          │                          │
│  │                                   │                          │
│  │  ✅ Cloud infrastructure          │                          │
│  │     VPC, subnets, IAM, DNS        │                          │
│  │                                   │                          │
│  │  ✅ Cluster lifecycle             │                          │
│  │     EKS/GKE/AKS, node pools      │                          │
│  │                                   │                          │
│  │  ✅ Cluster addons (via Helm)     │                          │
│  │     Ingress, cert-manager,        │                          │
│  │     monitoring, CSI drivers       │                          │
│  │                                   │                          │
│  │  ✅ Namespaces, RBAC, quotas      │                          │
│  │     Platform-level K8s resources  │                          │
│  └───────────────────┬───────────────┘                          │
│                      │                                          │
│                      │ Cluster ready, addons installed          │
│                      ▼                                          │
│  ┌───────────────────────────────────┐                          │
│  │       GITOPS (ArgoCD/Flux)        │                          │
│  │                                   │                          │
│  │  ✅ Application Deployments       │                          │
│  │     Deployments, Services,        │                          │
│  │     ConfigMaps, Secrets           │                          │
│  │                                   │                          │
│  │  ✅ Application config changes    │                          │
│  │     Rollouts, scaling, env vars   │                          │
│  │                                   │                          │
│  │  ✅ Rapid iteration               │                          │
│  │     Multiple deploys per day      │                          │
│  └───────────────────────────────────┘                          │
└─────────────────────────────────────────────────────────────────┘
```

| Aspect | Terraform | GitOps (ArgoCD/Flux) |
|--------|-----------|---------------------|
| **Change frequency** | Weekly/monthly (infra changes) | Daily (app deploys) |
| **Blast radius** | High (cluster-level) | Low (namespace-scoped) |
| **Drift detection** | On `terraform plan` | Continuous reconciliation |
| **Rollback** | `terraform apply` previous state | Git revert + auto-sync |
| **Who uses it** | Platform / SRE team | Application developers |
| **State** | Explicit state file | Git repo = state |

> **Key insight:** The boundary is **rate of change**. Infrastructure changes
> slowly and has high blast radius — Terraform's plan-review-apply workflow fits.
> Application deployments happen daily and need instant rollback — GitOps fits.
> Don't use Terraform for things that change 10 times a day.

### Crossplane — Cloud-Native IaC

Crossplane extends Kubernetes itself to manage cloud resources using Custom
Resource Definitions (CRDs). Instead of HCL, you write YAML:

```yaml
# crossplane/vpc.yaml — Crossplane Composition
apiVersion: ec2.aws.upbound.io/v1beta1
kind: VPC
metadata:
  name: deployforge-vpc
spec:
  forProvider:
    region: us-west-2
    cidrBlock: 10.0.0.0/16
    enableDnsHostnames: true
    enableDnsSupport: true
    tags:
      Name: deployforge-vpc
      ManagedBy: crossplane
  providerConfigRef:
    name: aws-provider
```

```yaml
# crossplane/composition.yaml — Reusable infrastructure template
apiVersion: apiextensions.crossplane.io/v1
kind: Composition
metadata:
  name: cluster-aws
spec:
  compositeTypeRef:
    apiVersion: deployforge.io/v1alpha1
    kind: KubernetesCluster
  resources:
    - name: vpc
      base:
        apiVersion: ec2.aws.upbound.io/v1beta1
        kind: VPC
        spec:
          forProvider:
            cidrBlock: 10.0.0.0/16
            enableDnsHostnames: true
      patches:
        - fromFieldPath: spec.region
          toFieldPath: spec.forProvider.region
    - name: cluster
      base:
        apiVersion: eks.aws.upbound.io/v1beta1
        kind: Cluster
        spec:
          forProvider:
            version: "1.28"
      patches:
        - fromFieldPath: spec.region
          toFieldPath: spec.forProvider.region
```

| Aspect | **Terraform** | **Crossplane** |
|--------|-----------|------------|
| **Language** | HCL | Kubernetes YAML |
| **Execution** | CLI (`terraform apply`) | Kubernetes controller (continuous) |
| **State** | State file (S3, GCS, etc.) | Kubernetes etcd (CRD status) |
| **Drift detection** | Manual (`terraform plan`) | Continuous reconciliation |
| **Learning curve** | HCL + provider docs | K8s CRDs + composition API |
| **Best for** | Platform teams, multi-cloud | K8s-native teams, self-service |

> **Production note:** Crossplane shines when you want to offer
> **self-service infrastructure** to developers: "create a `KubernetesCluster`
> CRD and the platform provisions everything." It's more complex to set up than
> Terraform but integrates natively with K8s RBAC and GitOps.

### Pulumi — Infrastructure as Real Code

Pulumi replaces HCL with general-purpose programming languages:

```typescript
// pulumi/index.ts — EKS cluster with Pulumi (TypeScript)
import * as pulumi from "@pulumi/pulumi";
import * as aws from "@pulumi/aws";
import * as eks from "@pulumi/eks";

const config = new pulumi.Config();
const environment = config.require("environment");

// Create VPC
const vpc = new aws.ec2.Vpc("deployforge-vpc", {
  cidrBlock: "10.0.0.0/16",
  enableDnsHostnames: true,
  tags: {
    Name: `deployforge-${environment}`,
    ManagedBy: "pulumi",
  },
});

// Create EKS cluster (high-level component)
const cluster = new eks.Cluster("deployforge-cluster", {
  vpcId: vpc.id,
  privateSubnetIds: privateSubnets.map(s => s.id),
  instanceType: environment === "prod" ? "m5.xlarge" : "t3.medium",
  desiredCapacity: environment === "prod" ? 6 : 2,
  minSize: environment === "prod" ? 3 : 1,
  maxSize: 10,
  version: "1.28",
  tags: {
    Environment: environment,
  },
});

// Export outputs
export const kubeconfig = cluster.kubeconfig;
export const clusterName = cluster.eksCluster.name;
```

| Aspect | **Terraform** | **Pulumi** |
|--------|-----------|--------|
| **Language** | HCL (DSL) | TypeScript, Python, Go, C#, Java |
| **State** | S3, GCS, TF Cloud | Pulumi Cloud, S3, local |
| **Testing** | Terratest (Go), `terraform validate` | Native unit testing (`jest`, `pytest`) |
| **Loops/conditionals** | `count`, `for_each`, ternary | Native language constructs |
| **IDE support** | Limited (HCL plugins) | Full (TypeScript = first-class) |
| **Best for** | Teams standardizing on IaC DSL | Teams with strong programming backgrounds |

### GitOps + Terraform Workflow

```
┌─────────────────────────────────────────────────────────────────┐
│           GitOps Workflow for Terraform                          │
│                                                                 │
│  1. Engineer opens PR with Terraform changes                    │
│     ┌──────────┐                                                │
│     │ PR opened │                                               │
│     └─────┬────┘                                                │
│           ▼                                                     │
│  2. CI runs terraform plan                                      │
│     ┌────────────────────────────────────────┐                  │
│     │ terraform init                         │                  │
│     │ terraform validate                     │                  │
│     │ tflint --recursive                     │                  │
│     │ terraform plan -out=tfplan             │                  │
│     │ Post plan diff as PR comment           │                  │
│     └─────┬──────────────────────────────────┘                  │
│           ▼                                                     │
│  3. Reviewer approves PR (reviews plan output)                  │
│     ┌──────────────┐                                            │
│     │ PR approved   │                                           │
│     └─────┬────────┘                                            │
│           ▼                                                     │
│  4. PR merged → CI runs terraform apply                         │
│     ┌────────────────────────────────────────┐                  │
│     │ terraform apply tfplan                 │                  │
│     │ (uses saved plan — no surprises)       │                  │
│     └─────┬──────────────────────────────────┘                  │
│           ▼                                                     │
│  5. State updated, infrastructure converged                     │
│     ┌──────────────────┐                                        │
│     │ State in S3       │                                       │
│     │ Infra matches code│                                       │
│     └──────────────────┘                                        │
└─────────────────────────────────────────────────────────────────┘
```

```yaml
# .github/workflows/terraform.yml — CI/CD for Terraform
name: Terraform

on:
  pull_request:
    paths: ['infra/**']
  push:
    branches: [main]
    paths: ['infra/**']

permissions:
  contents: read
  pull-requests: write              # ← to post plan comments
  id-token: write                   # ← for OIDC auth to AWS

jobs:
  plan:
    if: github.event_name == 'pull_request'
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - uses: aws-actions/configure-aws-credentials@v4
        with:
          role-to-assume: arn:aws:iam::123456789012:role/terraform-ci
          aws-region: us-west-2

      - uses: hashicorp/setup-terraform@v3
        with:
          terraform_version: 1.6.4

      - name: Terraform Init
        run: terraform init -no-color
        working-directory: infra/envs/prod

      - name: Terraform Validate
        run: terraform validate -no-color
        working-directory: infra/envs/prod

      - name: Terraform Plan
        id: plan
        run: terraform plan -no-color -out=tfplan
        working-directory: infra/envs/prod
        continue-on-error: true

      - name: Post Plan to PR
        uses: actions/github-script@v7
        with:
          script: |
            const plan = `${{ steps.plan.outputs.stdout }}`;
            const truncated = plan.length > 60000
              ? plan.substring(0, 60000) + '\n\n... truncated ...'
              : plan;
            github.rest.issues.createComment({
              owner: context.repo.owner,
              repo: context.repo.repo,
              issue_number: context.issue.number,
              body: `## Terraform Plan\n\`\`\`\n${truncated}\n\`\`\``
            });

  apply:
    if: github.ref == 'refs/heads/main' && github.event_name == 'push'
    runs-on: ubuntu-latest
    environment: production           # ← requires manual approval
    steps:
      - uses: actions/checkout@v4

      - uses: aws-actions/configure-aws-credentials@v4
        with:
          role-to-assume: arn:aws:iam::123456789012:role/terraform-ci
          aws-region: us-west-2

      - uses: hashicorp/setup-terraform@v3
        with:
          terraform_version: 1.6.4

      - name: Terraform Apply
        run: |
          terraform init -no-color
          terraform apply -auto-approve -no-color
        working-directory: infra/envs/prod
```

### Testing IaC

#### Static Analysis

```bash
# terraform validate — built-in syntax and type checking
terraform validate
# → Success! The configuration is valid.

# tflint — linter with provider-aware rules
tflint --init
tflint --recursive
# → Checks for deprecated attributes, invalid instance types, naming conventions

# checkov — security scanning
checkov -d infra/
# → CKV_AWS_79: Ensure EKS Cluster has Secrets Encryption Enabled: PASSED
# → CKV_AWS_58: Ensure EKS Cluster has Control Plane Logging: PASSED
```

#### Terraform Plan Tests

```bash
# Plan and check for expected behavior
terraform plan -detailed-exitcode
# Exit code 0: No changes
# Exit code 1: Error
# Exit code 2: Changes present

# JSON plan output for programmatic assertions
terraform plan -out=tfplan
terraform show -json tfplan | jq '.resource_changes[] | select(.change.actions[] == "delete")'
# → Catch accidental deletions in CI
```

#### Integration Testing with Terratest

```go
// test/eks_cluster_test.go — Terratest integration test
package test

import (
    "testing"
    "time"

    "github.com/gruntwork-io/terratest/modules/terraform"
    "github.com/gruntwork-io/terratest/modules/k8s"
    "github.com/stretchr/testify/assert"
)

func TestEksCluster(t *testing.T) {
    t.Parallel()

    terraformOptions := terraform.WithDefaultRetryableErrors(t, &terraform.Options{
        TerraformDir: "../infra/envs/test",
        Vars: map[string]interface{}{
            "environment":        "test",
            "node_instance_type": "t3.small",
            "node_desired_count": 1,
        },
    })

    // Clean up after test
    defer terraform.Destroy(t, terraformOptions)

    // Deploy infrastructure
    terraform.InitAndApply(t, terraformOptions)

    // Verify outputs
    clusterName := terraform.Output(t, terraformOptions, "cluster_name")
    assert.Contains(t, clusterName, "deployforge-test")

    // Verify cluster is accessible
    kubeconfig := terraform.Output(t, terraformOptions, "kubeconfig_path")
    options := k8s.NewKubectlOptions("", kubeconfig, "default")

    // Wait for nodes to be ready
    k8s.WaitUntilAllNodesReady(t, options, 10, 30*time.Second)

    // Verify node count
    nodes := k8s.GetNodes(t, options)
    assert.GreaterOrEqual(t, len(nodes), 1)
}
```

> **Production note:** Terratest creates **real infrastructure** in a real cloud
> account. Always use a dedicated test account with budget alerts, tag test
> resources for cleanup, and run `defer terraform.Destroy()` to avoid orphaned
> resources. A forgotten EKS cluster costs ~$73/month in control plane fees alone.

---

## Code Examples

### Complete DeployForge Cluster Module

```hcl
# modules/cluster/variables.tf

variable "environment" {
  description = "Environment name"
  type        = string
}

variable "kubernetes_version" {
  description = "Kubernetes version"
  type        = string
  default     = "1.28"
}

variable "vpc_id" {
  description = "VPC ID from networking module"
  type        = string
}

variable "private_subnet_ids" {
  description = "Private subnet IDs for worker nodes"
  type        = list(string)
}

variable "node_instance_type" {
  description = "EC2 instance type for worker nodes"
  type        = string
  default     = "t3.medium"
}

variable "node_min_count" {
  description = "Minimum number of worker nodes"
  type        = number
  default     = 1
}

variable "node_max_count" {
  description = "Maximum number of worker nodes"
  type        = number
  default     = 10
}

variable "node_desired_count" {
  description = "Desired number of worker nodes"
  type        = number
  default     = 2
}

variable "tags" {
  description = "Tags to apply to all resources"
  type        = map(string)
  default     = {}
}
```

```hcl
# modules/cluster/outputs.tf

output "cluster_name" {
  description = "EKS cluster name"
  value       = aws_eks_cluster.main.name
}

output "cluster_endpoint" {
  description = "EKS cluster API endpoint"
  value       = aws_eks_cluster.main.endpoint
}

output "cluster_ca_certificate" {
  description = "EKS cluster CA certificate (base64)"
  value       = aws_eks_cluster.main.certificate_authority[0].data
}

output "cluster_security_group_id" {
  description = "Security group attached to the cluster"
  value       = aws_security_group.cluster.id
}

output "oidc_provider_arn" {
  description = "OIDC provider ARN for IRSA"
  value       = aws_iam_openid_connect_provider.eks.arn
}

output "oidc_provider_url" {
  description = "OIDC provider URL (without https://)"
  value       = replace(aws_eks_cluster.main.identity[0].oidc[0].issuer, "https://", "")
}
```

### IRSA Role for a DeployForge Service

```hcl
# modules/irsa/main.tf — create an IAM role assumable by a K8s service account

variable "role_name" {
  type = string
}

variable "namespace" {
  type = string
}

variable "service_account_name" {
  type = string
}

variable "oidc_provider_arn" {
  type = string
}

variable "oidc_provider_url" {
  type = string
}

variable "policy_arns" {
  type    = list(string)
  default = []
}

resource "aws_iam_role" "this" {
  name = var.role_name

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect = "Allow"
      Principal = {
        Federated = var.oidc_provider_arn
      }
      Action = "sts:AssumeRoleWithWebIdentity"
      Condition = {
        StringEquals = {
          "${var.oidc_provider_url}:sub" = "system:serviceaccount:${var.namespace}:${var.service_account_name}"
          "${var.oidc_provider_url}:aud" = "sts.amazonaws.com"
        }
      }
    }]
  })
}

resource "aws_iam_role_policy_attachment" "this" {
  for_each   = toset(var.policy_arns)
  policy_arn = each.value
  role       = aws_iam_role.this.name
}

output "role_arn" {
  value = aws_iam_role.this.arn
}
```

Usage for DeployForge's API service:
```hcl
module "api_irsa" {
  source = "../../modules/irsa"

  role_name            = "deployforge-api-${var.environment}"
  namespace            = "deployforge"
  service_account_name = "deployforge-api"
  oidc_provider_arn    = module.cluster.oidc_provider_arn
  oidc_provider_url    = module.cluster.oidc_provider_url

  policy_arns = [
    "arn:aws:iam::aws:policy/AmazonS3ReadOnlyAccess",
    aws_iam_policy.api_secrets.arn,
  ]
}
```

---

## Try It Yourself

### Challenge 1: Add a Database Module

Write a `modules/database/` module that creates an RDS PostgreSQL instance in
private subnets with encryption, automated backups, and a security group that
only allows access from the cluster's security group.

<details>
<summary>Show solution</summary>

```hcl
# modules/database/variables.tf
variable "environment" {
  type = string
}

variable "vpc_id" {
  type = string
}

variable "private_subnet_ids" {
  type = list(string)
}

variable "cluster_security_group_id" {
  description = "Security group of the K8s cluster (allowed to connect)"
  type        = string
}

variable "instance_class" {
  type    = string
  default = "db.t3.micro"
}

variable "multi_az" {
  type    = bool
  default = false
}

variable "backup_retention" {
  type    = number
  default = 7
}

variable "tags" {
  type    = map(string)
  default = {}
}
```

```hcl
# modules/database/main.tf
resource "aws_db_subnet_group" "main" {
  name       = "deployforge-${var.environment}"
  subnet_ids = var.private_subnet_ids

  tags = merge(var.tags, { Name = "deployforge-db-subnet-group" })
}

resource "aws_security_group" "db" {
  name   = "deployforge-db-${var.environment}"
  vpc_id = var.vpc_id

  ingress {
    from_port       = 5432
    to_port         = 5432
    protocol        = "tcp"
    security_groups = [var.cluster_security_group_id]
    description     = "PostgreSQL from K8s cluster"
  }

  tags = merge(var.tags, { Name = "deployforge-db-sg" })
}

resource "random_password" "master" {
  length  = 32
  special = false
}

resource "aws_db_instance" "main" {
  identifier     = "deployforge-${var.environment}"
  engine         = "postgres"
  engine_version = "15.4"
  instance_class = var.instance_class

  allocated_storage     = 20
  max_allocated_storage = 100          # ← autoscaling storage
  storage_encrypted     = true

  db_name  = "deployforge"
  username = "deployforge"
  password = random_password.master.result

  multi_az               = var.multi_az
  db_subnet_group_name   = aws_db_subnet_group.main.name
  vpc_security_group_ids = [aws_security_group.db.id]

  backup_retention_period = var.backup_retention
  backup_window           = "03:00-04:00"
  maintenance_window      = "Mon:04:00-Mon:05:00"

  skip_final_snapshot       = var.environment != "prod"
  final_snapshot_identifier = var.environment == "prod" ? "deployforge-final-${formatdate("YYYY-MM-DD", timestamp())}" : null
  deletion_protection       = var.environment == "prod"

  tags = var.tags
}
```

```hcl
# modules/database/outputs.tf
output "endpoint" {
  value = aws_db_instance.main.endpoint
}

output "database_name" {
  value = aws_db_instance.main.db_name
}

output "master_password" {
  value     = random_password.master.result
  sensitive = true
}
```

Verify:
```bash
terraform validate
# → Success! The configuration is valid.
terraform plan
# → Plan: 4 to add (subnet group, SG, password, RDS instance)
```

</details>

### Challenge 2: Write a Terratest Skeleton

Write a Go test file that initializes Terraform for the networking module,
applies it, verifies the VPC ID output is non-empty, and destroys everything.

<details>
<summary>Show solution</summary>

```go
// test/networking_test.go
package test

import (
    "testing"

    "github.com/gruntwork-io/terratest/modules/terraform"
    "github.com/stretchr/testify/assert"
    "github.com/stretchr/testify/require"
)

func TestNetworkingModule(t *testing.T) {
    t.Parallel()

    terraformOptions := terraform.WithDefaultRetryableErrors(t, &terraform.Options{
        TerraformDir: "../modules/networking",
        Vars: map[string]interface{}{
            "environment":   "test",
            "vpc_cidr":      "10.99.0.0/16",
            "az_count":      2,
            "enable_nat_ha": false,
        },
    })

    defer terraform.Destroy(t, terraformOptions)

    terraform.InitAndApply(t, terraformOptions)

    // Verify VPC was created
    vpcId := terraform.Output(t, terraformOptions, "vpc_id")
    require.NotEmpty(t, vpcId, "VPC ID should not be empty")
    assert.Regexp(t, `^vpc-[a-f0-9]+$`, vpcId)

    // Verify subnets
    publicSubnetIds := terraform.OutputList(t, terraformOptions, "public_subnet_ids")
    assert.Len(t, publicSubnetIds, 2, "Should have 2 public subnets")

    privateSubnetIds := terraform.OutputList(t, terraformOptions, "private_subnet_ids")
    assert.Len(t, privateSubnetIds, 2, "Should have 2 private subnets")
}
```

Initialize the Go module:
```bash
cd test/
go mod init github.com/deployforge/infra-tests
go mod tidy
go test -v -timeout 30m ./...
# → TestNetworkingModule: PASS (takes ~3-5 min for VPC + subnets + NAT)
```

</details>

### Challenge 3: Implement a Crossplane XRD

Write a Crossplane `CompositeResourceDefinition` that lets developers request
a Kubernetes cluster by applying a simple YAML manifest.

<details>
<summary>Show solution</summary>

```yaml
# crossplane/xrd.yaml — Define the API
apiVersion: apiextensions.crossplane.io/v1
kind: CompositeResourceDefinition
metadata:
  name: kubernetesclusters.deployforge.io
spec:
  group: deployforge.io
  names:
    kind: KubernetesCluster
    plural: kubernetesclusters
  claimNames:
    kind: ClusterClaim
    plural: clusterclaims
  versions:
    - name: v1alpha1
      served: true
      referenceable: true
      schema:
        openAPIV3Schema:
          type: object
          properties:
            spec:
              type: object
              properties:
                region:
                  type: string
                  default: us-west-2
                nodeCount:
                  type: integer
                  default: 2
                  minimum: 1
                  maximum: 20
                nodeSize:
                  type: string
                  enum: [small, medium, large]
                  default: medium
                version:
                  type: string
                  default: "1.28"
              required: [region]
```

```yaml
# crossplane/claim.yaml — Developer self-service
apiVersion: deployforge.io/v1alpha1
kind: ClusterClaim
metadata:
  name: my-dev-cluster
  namespace: team-alpha
spec:
  region: us-west-2
  nodeCount: 2
  nodeSize: small
  version: "1.28"
```

Verify:
```bash
kubectl apply -f crossplane/xrd.yaml
kubectl apply -f crossplane/claim.yaml
kubectl get clusterclaims -n team-alpha
# → NAME             READY   CONNECTION-SECRET   AGE
# → my-dev-cluster   True    my-dev-cluster      5m
```

</details>

---

## Capstone Connection

**DeployForge** infrastructure is fully defined in Terraform, following every
pattern from this section:

- **EKS cluster module** provisions the Kubernetes control plane, managed node
  groups, and IRSA — enabling fine-grained AWS permissions per microservice.
- **Helm provider** installs cluster addons (ingress-nginx, cert-manager,
  Prometheus stack) as part of the Terraform apply, ensuring every new
  environment comes up with monitoring and ingress ready.
- **IaC responsibility boundary** — Terraform manages cloud resources and
  cluster addons; ArgoCD (from [Module 10](../10-ci-cd-and-gitops/)) manages
  application deployments. The two don't overlap.
- **CI pipeline** runs `terraform plan` on every PR, posts the diff as a
  comment, and applies on merge — no manual `terraform apply` from laptops.
- **Terratest** validates that the networking and cluster modules produce
  working infrastructure in a test account before any change reaches production.
- **IRSA roles** give each DeployForge service (API, worker, scheduler) its own
  IAM role — no shared credentials, no ambient authority.

With infrastructure fully codified, DeployForge can be reproduced in any AWS
region with a single `terraform apply` — enabling disaster recovery, multi-region
deployment, and ephemeral preview environments.
