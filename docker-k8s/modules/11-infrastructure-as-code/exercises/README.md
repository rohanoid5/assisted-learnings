# Module 11 — Exercises

Build DeployForge's infrastructure layer entirely in Terraform — from networking
and Kubernetes clusters to databases and remote state — so that any environment
can be reproduced with a single `terraform apply`.

> **Prerequisites:**
> - Terraform CLI >= 1.6 installed
> - AWS CLI configured (or [LocalStack](https://localstack.cloud/) for local simulation)
> - Concepts from [01-terraform-fundamentals.md](../01-terraform-fundamentals.md),
>   [02-modules-and-state.md](../02-modules-and-state.md), and
>   [03-iac-for-kubernetes.md](../03-iac-for-kubernetes.md) read and understood
>
> ```bash
> # Verify prerequisites
> terraform version
> # → Terraform v1.6.x
> aws sts get-caller-identity
> # → shows your AWS account (or use LocalStack)
> ```

---

## Exercise 1: Write a VPC and Kubernetes Cluster Configuration

**Goal:** Create a Terraform configuration that provisions a VPC with public and
private subnets across multiple AZs, and an EKS cluster with a managed node group.

### Steps

1. **Create the project structure:**

   ```bash
   mkdir -p infra/{modules/networking,modules/cluster,envs/dev}
   ```

2. **Write the networking module** (`infra/modules/networking/`):
   - `variables.tf` — accept `environment`, `vpc_cidr`, `az_count`, `enable_nat_ha`, and `tags`
   - `main.tf` — create VPC, public/private subnets (using `for_each`), internet gateway, NAT gateway(s), and route tables
   - `outputs.tf` — expose `vpc_id`, `public_subnet_ids`, `private_subnet_ids`
   - `versions.tf` — require Terraform >= 1.6 and AWS provider ~> 5.0

3. **Write the cluster module** (`infra/modules/cluster/`):
   - `variables.tf` — accept `environment`, `vpc_id`, `private_subnet_ids`, `kubernetes_version`, node sizing variables, and `tags`
   - `main.tf` — create EKS cluster with secrets encryption, control plane logging, managed node group with scaling config
   - `iam.tf` — create cluster and node IAM roles with required policy attachments, plus OIDC provider for IRSA
   - `outputs.tf` — expose `cluster_name`, `cluster_endpoint`, `cluster_ca_certificate`, `oidc_provider_arn`
   - `versions.tf` — require Terraform >= 1.6 and AWS provider ~> 5.0

4. **Compose into a dev environment** (`infra/envs/dev/main.tf`):
   - Call both modules with dev-appropriate values (2 AZs, `t3.medium` nodes, single NAT, 2 desired nodes)
   - Add a `terraform {}` block with version constraints

5. **Validate and plan:**

   ```bash
   cd infra/envs/dev
   terraform init
   terraform validate
   terraform plan
   ```

<details>
<summary>Show solution</summary>

```hcl
# infra/modules/networking/versions.tf
terraform {
  required_version = ">= 1.6"
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }
}
```

```hcl
# infra/modules/networking/variables.tf
variable "environment" {
  description = "Environment name"
  type        = string
}

variable "vpc_cidr" {
  description = "CIDR block for the VPC"
  type        = string
  default     = "10.0.0.0/16"
}

variable "az_count" {
  description = "Number of availability zones"
  type        = number
  default     = 3

  validation {
    condition     = var.az_count >= 2 && var.az_count <= 4
    error_message = "az_count must be between 2 and 4."
  }
}

variable "enable_nat_ha" {
  description = "One NAT gateway per AZ (true for prod)"
  type        = bool
  default     = false
}

variable "tags" {
  description = "Tags to apply"
  type        = map(string)
  default     = {}
}
```

```hcl
# infra/modules/networking/main.tf
data "aws_availability_zones" "available" {
  state = "available"
}

locals {
  azs = slice(data.aws_availability_zones.available.names, 0, var.az_count)
  nat_count = var.enable_nat_ha ? var.az_count : 1
}

resource "aws_vpc" "main" {
  cidr_block           = var.vpc_cidr
  enable_dns_hostnames = true
  enable_dns_support   = true

  tags = merge(var.tags, {
    Name = "deployforge-${var.environment}"
  })
}

resource "aws_subnet" "public" {
  for_each = { for i, az in local.azs : az => i }

  vpc_id                  = aws_vpc.main.id
  cidr_block              = cidrsubnet(var.vpc_cidr, 8, each.value)
  availability_zone       = each.key
  map_public_ip_on_launch = true

  tags = merge(var.tags, {
    Name                     = "deployforge-public-${each.key}"
    "kubernetes.io/role/elb" = "1"
  })
}

resource "aws_subnet" "private" {
  for_each = { for i, az in local.azs : az => i }

  vpc_id            = aws_vpc.main.id
  cidr_block        = cidrsubnet(var.vpc_cidr, 8, each.value + 10)
  availability_zone = each.key

  tags = merge(var.tags, {
    Name                              = "deployforge-private-${each.key}"
    "kubernetes.io/role/internal-elb" = "1"
  })
}

resource "aws_internet_gateway" "main" {
  vpc_id = aws_vpc.main.id
  tags   = merge(var.tags, { Name = "deployforge-igw-${var.environment}" })
}

resource "aws_eip" "nat" {
  count  = local.nat_count
  domain = "vpc"
  tags   = merge(var.tags, { Name = "deployforge-nat-eip-${count.index}" })
}

resource "aws_nat_gateway" "main" {
  count         = local.nat_count
  allocation_id = aws_eip.nat[count.index].id
  subnet_id     = values(aws_subnet.public)[count.index].id
  tags          = merge(var.tags, { Name = "deployforge-nat-${count.index}" })

  depends_on = [aws_internet_gateway.main]
}

resource "aws_route_table" "public" {
  vpc_id = aws_vpc.main.id

  route {
    cidr_block = "0.0.0.0/0"
    gateway_id = aws_internet_gateway.main.id
  }

  tags = merge(var.tags, { Name = "deployforge-public-rt" })
}

resource "aws_route_table_association" "public" {
  for_each       = aws_subnet.public
  subnet_id      = each.value.id
  route_table_id = aws_route_table.public.id
}

resource "aws_route_table" "private" {
  count  = local.nat_count
  vpc_id = aws_vpc.main.id

  route {
    cidr_block     = "0.0.0.0/0"
    nat_gateway_id = aws_nat_gateway.main[count.index].id
  }

  tags = merge(var.tags, { Name = "deployforge-private-rt-${count.index}" })
}

resource "aws_route_table_association" "private" {
  for_each       = aws_subnet.private
  subnet_id      = each.value.id
  route_table_id = aws_route_table.private[
    var.enable_nat_ha ? index(local.azs, each.key) : 0
  ].id
}
```

```hcl
# infra/modules/networking/outputs.tf
output "vpc_id" {
  description = "VPC ID"
  value       = aws_vpc.main.id
}

output "public_subnet_ids" {
  description = "Public subnet IDs"
  value       = [for s in aws_subnet.public : s.id]
}

output "private_subnet_ids" {
  description = "Private subnet IDs"
  value       = [for s in aws_subnet.private : s.id]
}
```

```hcl
# infra/modules/cluster/versions.tf
terraform {
  required_version = ">= 1.6"
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
    tls = {
      source  = "hashicorp/tls"
      version = "~> 4.0"
    }
  }
}
```

```hcl
# infra/modules/cluster/variables.tf
variable "environment" {
  type = string
}

variable "kubernetes_version" {
  type    = string
  default = "1.28"
}

variable "vpc_id" {
  type = string
}

variable "private_subnet_ids" {
  type = list(string)
}

variable "node_instance_type" {
  type    = string
  default = "t3.medium"
}

variable "node_min_count" {
  type    = number
  default = 1
}

variable "node_max_count" {
  type    = number
  default = 10
}

variable "node_desired_count" {
  type    = number
  default = 2
}

variable "tags" {
  type    = map(string)
  default = {}
}
```

```hcl
# infra/modules/cluster/main.tf
resource "aws_kms_key" "eks" {
  description         = "EKS secret encryption key"
  enable_key_rotation = true
  tags                = var.tags
}

resource "aws_security_group" "cluster" {
  name   = "deployforge-eks-cluster-${var.environment}"
  vpc_id = var.vpc_id

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = merge(var.tags, { Name = "deployforge-eks-cluster-sg" })
}

resource "aws_eks_cluster" "main" {
  name     = "deployforge-${var.environment}"
  role_arn = aws_iam_role.cluster.arn
  version  = var.kubernetes_version

  vpc_config {
    subnet_ids              = var.private_subnet_ids
    endpoint_private_access = true
    endpoint_public_access  = var.environment != "prod"
    security_group_ids      = [aws_security_group.cluster.id]
  }

  encryption_config {
    provider {
      key_arn = aws_kms_key.eks.arn
    }
    resources = ["secrets"]
  }

  enabled_cluster_log_types = [
    "api", "audit", "authenticator",
    "controllerManager", "scheduler"
  ]

  tags = var.tags

  depends_on = [
    aws_iam_role_policy_attachment.cluster_policy,
    aws_iam_role_policy_attachment.cluster_vpc_policy,
  ]
}

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
    max_unavailable_percentage = 25
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
# infra/modules/cluster/iam.tf
resource "aws_iam_role" "cluster" {
  name = "deployforge-eks-cluster-${var.environment}"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Action    = "sts:AssumeRole"
      Effect    = "Allow"
      Principal = { Service = "eks.amazonaws.com" }
    }]
  })

  tags = var.tags
}

resource "aws_iam_role_policy_attachment" "cluster_policy" {
  policy_arn = "arn:aws:iam::aws:policy/AmazonEKSClusterPolicy"
  role       = aws_iam_role.cluster.name
}

resource "aws_iam_role_policy_attachment" "cluster_vpc_policy" {
  policy_arn = "arn:aws:iam::aws:policy/AmazonEKSVPCResourceController"
  role       = aws_iam_role.cluster.name
}

resource "aws_iam_role" "node" {
  name = "deployforge-eks-node-${var.environment}"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Action    = "sts:AssumeRole"
      Effect    = "Allow"
      Principal = { Service = "ec2.amazonaws.com" }
    }]
  })

  tags = var.tags
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

data "tls_certificate" "eks" {
  url = aws_eks_cluster.main.identity[0].oidc[0].issuer
}

resource "aws_iam_openid_connect_provider" "eks" {
  client_id_list  = ["sts.amazonaws.com"]
  thumbprint_list = [data.tls_certificate.eks.certificates[0].sha1_fingerprint]
  url             = aws_eks_cluster.main.identity[0].oidc[0].issuer

  tags = var.tags
}
```

```hcl
# infra/modules/cluster/outputs.tf
output "cluster_name" {
  value = aws_eks_cluster.main.name
}

output "cluster_endpoint" {
  value = aws_eks_cluster.main.endpoint
}

output "cluster_ca_certificate" {
  value = aws_eks_cluster.main.certificate_authority[0].data
}

output "cluster_security_group_id" {
  value = aws_security_group.cluster.id
}

output "oidc_provider_arn" {
  value = aws_iam_openid_connect_provider.eks.arn
}
```

```hcl
# infra/envs/dev/main.tf
terraform {
  required_version = ">= 1.6"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }
}

provider "aws" {
  region = "us-west-2"

  default_tags {
    tags = {
      Project     = "DeployForge"
      Environment = "dev"
      ManagedBy   = "terraform"
    }
  }
}

module "networking" {
  source = "../../modules/networking"

  environment   = "dev"
  vpc_cidr      = "10.0.0.0/16"
  az_count      = 2
  enable_nat_ha = false

  tags = { CostCenter = "engineering" }
}

module "cluster" {
  source = "../../modules/cluster"

  environment        = "dev"
  vpc_id             = module.networking.vpc_id
  private_subnet_ids = module.networking.private_subnet_ids
  node_instance_type = "t3.medium"
  node_desired_count = 2
  node_min_count     = 1
  node_max_count     = 4
}
```

Verify:
```bash
cd infra/envs/dev
terraform init
# → Initializing modules... Initializing provider plugins...

terraform validate
# → Success! The configuration is valid.

terraform plan
# → Plan: ~20 resources to add (VPC, subnets, IGW, NAT, EKS, node group, IAM roles)
```

</details>

---

## Exercise 2: Create Reusable Modules with Clean Interfaces

**Goal:** Refactor the Exercise 1 configuration into properly encapsulated modules
with validation, documentation, and a monitoring module that installs Prometheus
via the Helm provider.

### Steps

1. **Add input validation** to all module variables:
   - `environment` must be one of `dev`, `staging`, `prod`
   - `vpc_cidr` must match a CIDR pattern
   - `node_min_count` must be less than `node_max_count`

2. **Create a monitoring module** (`infra/modules/monitoring/`):
   - Accept `cluster_name`, `cluster_endpoint`, `cluster_ca_certificate`, and `environment`
   - Configure the `helm` provider using cluster credentials
   - Install the `kube-prometheus-stack` Helm chart with environment-aware values
   - Install `ingress-nginx` Helm chart
   - Output the Grafana load balancer hostname

3. **Add `README.md`** to each module using this template:
   ```markdown
   # Module: <name>
   ## Usage
   ## Inputs
   ## Outputs
   ```

4. **Wire the monitoring module** into `envs/dev/main.tf`

<details>
<summary>Show solution</summary>

```hcl
# infra/modules/monitoring/variables.tf
variable "environment" {
  type = string
  validation {
    condition     = contains(["dev", "staging", "prod"], var.environment)
    error_message = "Must be dev, staging, or prod."
  }
}

variable "cluster_name" {
  description = "EKS cluster name"
  type        = string
}

variable "cluster_endpoint" {
  description = "EKS cluster API endpoint"
  type        = string
}

variable "cluster_ca_certificate" {
  description = "EKS cluster CA certificate (base64)"
  type        = string
}

variable "tags" {
  type    = map(string)
  default = {}
}
```

```hcl
# infra/modules/monitoring/versions.tf
terraform {
  required_version = ">= 1.6"
  required_providers {
    helm = {
      source  = "hashicorp/helm"
      version = "~> 2.12"
    }
    kubernetes = {
      source  = "hashicorp/kubernetes"
      version = "~> 2.25"
    }
  }
}
```

```hcl
# infra/modules/monitoring/main.tf
resource "helm_release" "prometheus_stack" {
  name             = "prometheus"
  repository       = "https://prometheus-community.github.io/helm-charts"
  chart            = "kube-prometheus-stack"
  version          = "55.5.0"
  namespace        = "monitoring"
  create_namespace = true

  set {
    name  = "prometheus.prometheusSpec.retention"
    value = var.environment == "prod" ? "30d" : "7d"
  }

  set {
    name  = "prometheus.prometheusSpec.storageSpec.volumeClaimTemplate.spec.resources.requests.storage"
    value = var.environment == "prod" ? "100Gi" : "20Gi"
  }

  set {
    name  = "grafana.persistence.enabled"
    value = "true"
  }

  set {
    name  = "grafana.persistence.size"
    value = "10Gi"
  }

  set {
    name  = "alertmanager.alertmanagerSpec.retention"
    value = "120h"
  }
}

resource "helm_release" "ingress_nginx" {
  name             = "ingress-nginx"
  repository       = "https://kubernetes.github.io/ingress-nginx"
  chart            = "ingress-nginx"
  version          = "4.9.0"
  namespace        = "ingress-system"
  create_namespace = true

  set {
    name  = "controller.service.type"
    value = "LoadBalancer"
  }

  set {
    name  = "controller.metrics.enabled"
    value = "true"
  }

  set {
    name  = "controller.metrics.serviceMonitor.enabled"
    value = "true"                        # ← Prometheus auto-discovers
  }
}
```

```hcl
# infra/modules/monitoring/outputs.tf
output "prometheus_namespace" {
  description = "Namespace where Prometheus is installed"
  value       = helm_release.prometheus_stack.namespace
}

output "ingress_namespace" {
  description = "Namespace where ingress-nginx is installed"
  value       = helm_release.ingress_nginx.namespace
}
```

Wire into dev environment:
```hcl
# Add to infra/envs/dev/main.tf

provider "helm" {
  kubernetes {
    host                   = module.cluster.cluster_endpoint
    cluster_ca_certificate = base64decode(module.cluster.cluster_ca_certificate)

    exec {
      api_version = "client.authentication.k8s.io/v1beta1"
      command     = "aws"
      args        = ["eks", "get-token", "--cluster-name", module.cluster.cluster_name]
    }
  }
}

provider "kubernetes" {
  host                   = module.cluster.cluster_endpoint
  cluster_ca_certificate = base64decode(module.cluster.cluster_ca_certificate)

  exec {
    api_version = "client.authentication.k8s.io/v1beta1"
    command     = "aws"
    args        = ["eks", "get-token", "--cluster-name", module.cluster.cluster_name]
  }
}

module "monitoring" {
  source = "../../modules/monitoring"

  environment            = "dev"
  cluster_name           = module.cluster.cluster_name
  cluster_endpoint       = module.cluster.cluster_endpoint
  cluster_ca_certificate = module.cluster.cluster_ca_certificate
}
```

Verify:
```bash
terraform init    # ← downloads helm and kubernetes providers
terraform validate
terraform plan
# → Plan: adds helm_release.prometheus_stack, helm_release.ingress_nginx
```

</details>

---

## Exercise 3: Implement Remote State with Locking

**Goal:** Configure an S3 + DynamoDB remote backend for DeployForge's Terraform
state, with separate state files per layer (networking, cluster, database) to
reduce blast radius.

### Steps

1. **Write a bootstrap script** that creates:
   - An S3 bucket (`deployforge-terraform-state`) with versioning, encryption, and public access block
   - A DynamoDB table (`deployforge-tf-locks`) with `LockID` as the partition key

2. **Configure backends** for each layer:
   - `infra/envs/dev/networking/backend.tf` → key: `dev/networking/terraform.tfstate`
   - `infra/envs/dev/cluster/backend.tf` → key: `dev/cluster/terraform.tfstate`
   - `infra/envs/dev/database/backend.tf` → key: `dev/database/terraform.tfstate`

3. **Use `terraform_remote_state`** in the cluster layer to read VPC outputs from the networking state

4. **Test the locking** by running `terraform plan` in two terminals simultaneously — one should block

<details>
<summary>Show solution</summary>

```bash
#!/usr/bin/env bash
# scripts/bootstrap-state-backend.sh
set -euo pipefail

BUCKET="deployforge-terraform-state"
TABLE="deployforge-tf-locks"
REGION="us-west-2"

echo "=== Creating S3 bucket ==="
aws s3api create-bucket \
  --bucket "$BUCKET" \
  --region "$REGION" \
  --create-bucket-configuration LocationConstraint="$REGION"

aws s3api put-bucket-versioning \
  --bucket "$BUCKET" \
  --versioning-configuration Status=Enabled

aws s3api put-bucket-encryption \
  --bucket "$BUCKET" \
  --server-side-encryption-configuration '{
    "Rules": [{"ApplyServerSideEncryptionByDefault": {"SSEAlgorithm": "aws:kms"}}]
  }'

aws s3api put-public-access-block \
  --bucket "$BUCKET" \
  --public-access-block-configuration \
    BlockPublicAcls=true,IgnorePublicAcls=true,BlockPublicPolicy=true,RestrictPublicBuckets=true

echo "=== Creating DynamoDB lock table ==="
aws dynamodb create-table \
  --table-name "$TABLE" \
  --attribute-definitions AttributeName=LockID,AttributeType=S \
  --key-schema AttributeName=LockID,KeyType=HASH \
  --billing-mode PAY_PER_REQUEST \
  --region "$REGION"

echo "✅ Backend ready: s3://$BUCKET, lock table: $TABLE"
```

```hcl
# infra/envs/dev/networking/backend.tf
terraform {
  backend "s3" {
    bucket         = "deployforge-terraform-state"
    key            = "dev/networking/terraform.tfstate"
    region         = "us-west-2"
    encrypt        = true
    dynamodb_table = "deployforge-tf-locks"
  }
}
```

```hcl
# infra/envs/dev/cluster/backend.tf
terraform {
  backend "s3" {
    bucket         = "deployforge-terraform-state"
    key            = "dev/cluster/terraform.tfstate"
    region         = "us-west-2"
    encrypt        = true
    dynamodb_table = "deployforge-tf-locks"
  }
}
```

```hcl
# infra/envs/dev/cluster/main.tf — reads networking remote state
data "terraform_remote_state" "networking" {
  backend = "s3"
  config = {
    bucket = "deployforge-terraform-state"
    key    = "dev/networking/terraform.tfstate"
    region = "us-west-2"
  }
}

module "cluster" {
  source = "../../../modules/cluster"

  environment        = "dev"
  vpc_id             = data.terraform_remote_state.networking.outputs.vpc_id
  private_subnet_ids = data.terraform_remote_state.networking.outputs.private_subnet_ids
  node_instance_type = "t3.medium"
  node_desired_count = 2
  node_min_count     = 1
  node_max_count     = 4
}
```

```hcl
# infra/envs/dev/database/backend.tf
terraform {
  backend "s3" {
    bucket         = "deployforge-terraform-state"
    key            = "dev/database/terraform.tfstate"
    region         = "us-west-2"
    encrypt        = true
    dynamodb_table = "deployforge-tf-locks"
  }
}
```

Test locking:
```bash
# Terminal 1:
cd infra/envs/dev/networking
terraform init
terraform plan    # ← acquires lock

# Terminal 2 (while Terminal 1 is running):
cd infra/envs/dev/networking
terraform plan
# → Error: Error acquiring the state lock
# → Lock Info:
# →   ID:        a1b2c3d4-...
# →   Path:      dev/networking/terraform.tfstate
# →   Operation: OperationTypePlan
# →   Who:       engineer@laptop
# →   Created:   2024-01-15 10:30:00 UTC
```

</details>

---

## Exercise 4: Provision Complete DeployForge Infrastructure

**Goal:** Combine all modules into a complete DeployForge infrastructure stack
with networking, Kubernetes cluster, database, and monitoring — plus a CI pipeline
that runs `terraform plan` on PRs and `terraform apply` on merge.

### Steps

1. **Create a database module** (`infra/modules/database/`):
   - PostgreSQL RDS instance in private subnets
   - Security group allowing access from the cluster's security group only
   - Encrypted storage, automated backups, multi-AZ option
   - Random password generation (mark as sensitive)

2. **Create a prod environment** (`infra/envs/prod/main.tf`):
   - 3 AZs, HA NAT gateways, `m5.xlarge` nodes (3–10), multi-AZ database
   - All four modules wired together

3. **Write the CI/CD pipeline** (`.github/workflows/terraform.yml`):
   - On PR: `terraform validate` → `tflint` → `terraform plan` → comment plan on PR
   - On merge to main: `terraform apply -auto-approve` with saved plan
   - Use OIDC authentication to AWS (no static credentials)
   - Require manual approval for production

4. **Validate everything:**

   ```bash
   cd infra/envs/prod
   terraform init
   terraform validate
   terraform plan
   # → Review the full plan for networking + cluster + database + monitoring
   ```

<details>
<summary>Show solution</summary>

```hcl
# infra/modules/database/variables.tf
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
  description = "K8s cluster security group (allowed to connect)"
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
# infra/modules/database/main.tf
resource "aws_db_subnet_group" "main" {
  name       = "deployforge-${var.environment}"
  subnet_ids = var.private_subnet_ids
  tags       = merge(var.tags, { Name = "deployforge-db-subnet-group" })
}

resource "aws_security_group" "db" {
  name   = "deployforge-db-${var.environment}"
  vpc_id = var.vpc_id

  ingress {
    from_port       = 5432
    to_port         = 5432
    protocol        = "tcp"
    security_groups = [var.cluster_security_group_id]
    description     = "PostgreSQL from K8s cluster only"
  }

  tags = merge(var.tags, { Name = "deployforge-db-sg" })
}

resource "random_password" "master" {
  length  = 32
  special = false
}

resource "aws_db_instance" "main" {
  identifier            = "deployforge-${var.environment}"
  engine                = "postgres"
  engine_version        = "15.4"
  instance_class        = var.instance_class
  allocated_storage     = 20
  max_allocated_storage = 100
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
  final_snapshot_identifier = var.environment == "prod" ? "deployforge-final" : null
  deletion_protection       = var.environment == "prod"

  tags = var.tags
}
```

```hcl
# infra/modules/database/outputs.tf
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

output "security_group_id" {
  value = aws_security_group.db.id
}
```

```hcl
# infra/envs/prod/main.tf — complete production environment
terraform {
  required_version = ">= 1.6"

  backend "s3" {
    bucket         = "deployforge-terraform-state"
    key            = "prod/main/terraform.tfstate"
    region         = "us-west-2"
    encrypt        = true
    dynamodb_table = "deployforge-tf-locks"
  }

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
    helm = {
      source  = "hashicorp/helm"
      version = "~> 2.12"
    }
    kubernetes = {
      source  = "hashicorp/kubernetes"
      version = "~> 2.25"
    }
    random = {
      source  = "hashicorp/random"
      version = "~> 3.6"
    }
  }
}

provider "aws" {
  region = "us-west-2"

  default_tags {
    tags = {
      Project     = "DeployForge"
      Environment = "prod"
      ManagedBy   = "terraform"
    }
  }
}

provider "helm" {
  kubernetes {
    host                   = module.cluster.cluster_endpoint
    cluster_ca_certificate = base64decode(module.cluster.cluster_ca_certificate)

    exec {
      api_version = "client.authentication.k8s.io/v1beta1"
      command     = "aws"
      args        = ["eks", "get-token", "--cluster-name", module.cluster.cluster_name]
    }
  }
}

provider "kubernetes" {
  host                   = module.cluster.cluster_endpoint
  cluster_ca_certificate = base64decode(module.cluster.cluster_ca_certificate)

  exec {
    api_version = "client.authentication.k8s.io/v1beta1"
    command     = "aws"
    args        = ["eks", "get-token", "--cluster-name", module.cluster.cluster_name]
  }
}

# --- Networking ---
module "networking" {
  source = "../../modules/networking"

  environment   = "prod"
  vpc_cidr      = "10.0.0.0/16"
  az_count      = 3
  enable_nat_ha = true              # ← one NAT per AZ in production
}

# --- Kubernetes Cluster ---
module "cluster" {
  source = "../../modules/cluster"

  environment        = "prod"
  vpc_id             = module.networking.vpc_id
  private_subnet_ids = module.networking.private_subnet_ids
  kubernetes_version = "1.28"
  node_instance_type = "m5.xlarge"
  node_min_count     = 3
  node_max_count     = 10
  node_desired_count = 6
}

# --- Database ---
module "database" {
  source = "../../modules/database"

  environment               = "prod"
  vpc_id                    = module.networking.vpc_id
  private_subnet_ids        = module.networking.private_subnet_ids
  cluster_security_group_id = module.cluster.cluster_security_group_id
  instance_class            = "db.r6g.large"
  multi_az                  = true
  backup_retention          = 30
}

# --- Monitoring ---
module "monitoring" {
  source = "../../modules/monitoring"

  environment            = "prod"
  cluster_name           = module.cluster.cluster_name
  cluster_endpoint       = module.cluster.cluster_endpoint
  cluster_ca_certificate = module.cluster.cluster_ca_certificate
}

# --- Outputs ---
output "cluster_name" {
  value = module.cluster.cluster_name
}

output "cluster_endpoint" {
  value = module.cluster.cluster_endpoint
}

output "database_endpoint" {
  value = module.database.endpoint
}

output "database_password" {
  value     = module.database.master_password
  sensitive = true
}
```

```yaml
# .github/workflows/terraform.yml
name: DeployForge Terraform

on:
  pull_request:
    paths: ['infra/**']
  push:
    branches: [main]
    paths: ['infra/**']

permissions:
  contents: read
  pull-requests: write
  id-token: write

env:
  TF_VERSION: "1.6.4"
  AWS_REGION: "us-west-2"

jobs:
  validate:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: hashicorp/setup-terraform@v3
        with:
          terraform_version: ${{ env.TF_VERSION }}

      - name: Terraform Format Check
        run: terraform fmt -check -recursive infra/

      - name: Validate All Modules
        run: |
          for dir in infra/modules/*/; do
            echo "=== Validating $dir ==="
            cd "$dir"
            terraform init -backend=false
            terraform validate
            cd -
          done

  plan:
    if: github.event_name == 'pull_request'
    needs: validate
    runs-on: ubuntu-latest
    strategy:
      matrix:
        environment: [dev, staging, prod]
    steps:
      - uses: actions/checkout@v4

      - uses: aws-actions/configure-aws-credentials@v4
        with:
          role-to-assume: arn:aws:iam::123456789012:role/terraform-ci
          aws-region: ${{ env.AWS_REGION }}

      - uses: hashicorp/setup-terraform@v3
        with:
          terraform_version: ${{ env.TF_VERSION }}

      - name: Terraform Plan
        id: plan
        working-directory: infra/envs/${{ matrix.environment }}
        run: |
          terraform init -no-color
          terraform plan -no-color -out=tfplan 2>&1 | tee plan.txt

      - name: Comment Plan on PR
        uses: actions/github-script@v7
        with:
          script: |
            const fs = require('fs');
            const plan = fs.readFileSync(
              'infra/envs/${{ matrix.environment }}/plan.txt', 'utf8'
            );
            const truncated = plan.length > 60000
              ? plan.substring(0, 60000) + '\n... truncated ...'
              : plan;
            github.rest.issues.createComment({
              owner: context.repo.owner,
              repo: context.repo.repo,
              issue_number: context.issue.number,
              body: `## 🏗️ Terraform Plan — ${{ matrix.environment }}\n\`\`\`\n${truncated}\n\`\`\``
            });

  apply-prod:
    if: github.ref == 'refs/heads/main' && github.event_name == 'push'
    needs: validate
    runs-on: ubuntu-latest
    environment: production           # ← requires manual approval
    steps:
      - uses: actions/checkout@v4

      - uses: aws-actions/configure-aws-credentials@v4
        with:
          role-to-assume: arn:aws:iam::123456789012:role/terraform-ci
          aws-region: ${{ env.AWS_REGION }}

      - uses: hashicorp/setup-terraform@v3
        with:
          terraform_version: ${{ env.TF_VERSION }}

      - name: Terraform Apply
        working-directory: infra/envs/prod
        run: |
          terraform init -no-color
          terraform apply -auto-approve -no-color
```

Verify:
```bash
cd infra/envs/prod
terraform init
# → Initializing modules... Initializing provider plugins...

terraform validate
# → Success! The configuration is valid.

terraform plan
# → Plan: ~35 resources to add
# → (VPC, subnets, IGW, NATs, route tables, EKS, node group,
# →  IAM roles, RDS, security groups, Helm releases)
```

</details>

---

## Checklist

- [ ] Wrote a networking module with VPC, subnets, and NAT gateways
- [ ] Wrote a cluster module with EKS, node groups, and IRSA
- [ ] Composed modules into a dev environment with `terraform plan` succeeding
- [ ] Created a monitoring module with Prometheus and ingress-nginx Helm releases
- [ ] Configured S3 + DynamoDB remote backend with state locking
- [ ] Split state by layer (networking, cluster, database) for blast radius reduction
- [ ] Used `terraform_remote_state` for cross-layer references
- [ ] Built a complete production environment with all four modules
- [ ] Wrote a CI/CD pipeline with plan-on-PR and apply-on-merge workflow
- [ ] All `terraform validate` checks pass across modules and environments
