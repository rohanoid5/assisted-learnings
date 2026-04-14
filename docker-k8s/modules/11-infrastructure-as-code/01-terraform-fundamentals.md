# 11.1 — Terraform Fundamentals

## Concept

Terraform is a declarative infrastructure provisioning tool. You describe **what**
you want — a VPC, a Kubernetes cluster, a DNS record — and Terraform figures out
**how** to create, update, or destroy it. The core workflow is deceptively simple:
`init → plan → apply`. But mastering Terraform means understanding the dependency
graph it builds, the state it tracks, and the provider ecosystem that makes it
cloud-agnostic without being cloud-ignorant.

---

## Deep Dive

### HCL — HashiCorp Configuration Language

HCL is not YAML, not JSON, not a general-purpose programming language. It's a
**declarative DSL** purpose-built for infrastructure. Every `.tf` file in a
directory is loaded and merged — there's no import system, no execution order
between files.

```hcl
# main.tf — a minimal Terraform configuration

terraform {
  required_version = ">= 1.6"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"          # ← pin major, allow patch updates
    }
  }
}

provider "aws" {
  region = var.aws_region
}
```

> **Key insight:** The `~> 5.0` version constraint means `>= 5.0, < 6.0`. This
> is called the **pessimistic constraint operator** — it prevents surprise major
> version upgrades while still picking up bug fixes.

### The Terraform Lifecycle

```
┌──────────┐     ┌──────────┐     ┌──────────┐     ┌──────────┐
│   init   │────▶│   plan   │────▶│  apply   │────▶│ destroy  │
│          │     │          │     │          │     │          │
│ Download │     │ Diff     │     │ Execute  │     │ Tear     │
│ providers│     │ desired  │     │ changes  │     │ down all │
│ + modules│     │ vs actual│     │ from plan│     │ resources│
└──────────┘     └──────────┘     └──────────┘     └──────────┘
     │                │                │                │
     ▼                ▼                ▼                ▼
  .terraform/    Plan file or     State file        State file
  lock file      stdout diff      updated           emptied
```

| Command | What It Does | Safe to Run? |
|---------|-------------|--------------|
| `terraform init` | Downloads providers and modules, initializes backend | Always safe |
| `terraform plan` | Computes diff between desired state and actual state | Always safe (read-only) |
| `terraform apply` | Executes the plan — creates, updates, or destroys resources | **Mutates infrastructure** |
| `terraform destroy` | Destroys all managed resources | **Deletes everything** |
| `terraform validate` | Syntax and type checking (no API calls) | Always safe |
| `terraform fmt` | Canonical formatting | Always safe |

> **Production note:** Never run `terraform apply` without reviewing the plan
> output first. In CI, save the plan to a file (`terraform plan -out=tfplan`)
> and apply that exact plan (`terraform apply tfplan`) to prevent drift between
> plan and apply.

### Providers

Providers are plugins that talk to cloud APIs. Terraform has **3,000+** providers
in the [Terraform Registry](https://registry.terraform.io/).

```hcl
# Multi-provider configuration
provider "aws" {
  region = "us-east-1"
  alias  = "us_east"
}

provider "aws" {
  region = "eu-west-1"
  alias  = "eu_west"
}

resource "aws_s3_bucket" "logs_us" {
  provider = aws.us_east              # ← explicit provider selection
  bucket   = "deployforge-logs-us"
}

resource "aws_s3_bucket" "logs_eu" {
  provider = aws.eu_west
  bucket   = "deployforge-logs-eu"
}
```

The **lock file** (`.terraform.lock.hcl`) pins exact provider versions and
checksums. Commit this file — it ensures reproducible builds across machines.

### Resources and Data Sources

**Resources** are things Terraform manages (creates, updates, destroys).
**Data sources** are things Terraform reads but doesn't manage.

```hcl
# Resource — Terraform owns this
resource "aws_vpc" "main" {
  cidr_block           = "10.0.0.0/16"
  enable_dns_hostnames = true

  tags = {
    Name        = "deployforge-${var.environment}"
    ManagedBy   = "terraform"
    Environment = var.environment
  }
}

# Data source — Terraform reads this (created outside TF or by another state)
data "aws_ami" "ubuntu" {
  most_recent = true
  owners      = ["099720109477"]          # ← Canonical's AWS account

  filter {
    name   = "name"
    values = ["ubuntu/images/hvm-ssd/ubuntu-jammy-22.04-amd64-server-*"]
  }
}

# Reference the data source in a resource
resource "aws_instance" "bastion" {
  ami           = data.aws_ami.ubuntu.id  # ← read from data source
  instance_type = "t3.micro"
  subnet_id     = aws_vpc.main.id         # ← implicit dependency
}
```

### Variables, Outputs, and Locals

```hcl
# variables.tf
variable "environment" {
  description = "Deployment environment (dev, staging, prod)"
  type        = string
  validation {
    condition     = contains(["dev", "staging", "prod"], var.environment)
    error_message = "Environment must be dev, staging, or prod."
  }
}

variable "vpc_cidr" {
  description = "CIDR block for the VPC"
  type        = string
  default     = "10.0.0.0/16"
}

variable "instance_types" {
  description = "Map of environment to instance type"
  type        = map(string)
  default = {
    dev     = "t3.small"
    staging = "t3.medium"
    prod    = "t3.large"
  }
}
```

```hcl
# locals.tf — computed values, DRY helpers
locals {
  common_tags = {
    Project     = "DeployForge"
    Environment = var.environment
    ManagedBy   = "terraform"
  }

  # Merge common tags with resource-specific tags
  vpc_tags = merge(local.common_tags, {
    Name = "deployforge-vpc-${var.environment}"
  })
}
```

```hcl
# outputs.tf
output "vpc_id" {
  description = "ID of the created VPC"
  value       = aws_vpc.main.id
}

output "vpc_cidr" {
  description = "CIDR block of the VPC"
  value       = aws_vpc.main.cidr_block
}
```

> **Key insight:** Variables are the module's **input interface**, outputs are the
> **output interface**. Think of a Terraform module like a function: variables are
> parameters, outputs are return values, resources are side effects.

### Resource Dependencies — Implicit and Explicit

Terraform builds a **directed acyclic graph (DAG)** of all resources and their
dependencies. It uses this graph to determine creation order and maximize
parallelism.

```hcl
# Implicit dependency — Terraform infers from attribute references
resource "aws_subnet" "public" {
  vpc_id     = aws_vpc.main.id            # ← VPC must exist first
  cidr_block = "10.0.1.0/24"
}

# Explicit dependency — when there's no attribute reference but order matters
resource "aws_instance" "app" {
  ami           = data.aws_ami.ubuntu.id
  instance_type = "t3.micro"
  subnet_id     = aws_subnet.public.id

  depends_on = [aws_iam_role_policy_attachment.app_policy]
  # ← No attribute reference to the policy, but instance needs it at boot
}
```

```
                    ┌─────────────┐
                    │  aws_vpc    │
                    │  .main      │
                    └──────┬──────┘
                           │ implicit
                    ┌──────▼──────┐
                    │ aws_subnet  │
                    │ .public     │
                    └──────┬──────┘
                           │ implicit
              ┌────────────▼────────────┐
              │    aws_instance.app     │
              └────────────┬────────────┘
                           │ explicit (depends_on)
              ┌────────────▼────────────┐
              │ aws_iam_role_policy_     │
              │ attachment.app_policy    │
              └─────────────────────────┘
```

### count and for_each

Use `count` for identical copies, `for_each` for distinct resources keyed by name.

```hcl
# count — create N identical subnets
variable "az_count" {
  default = 3
}

resource "aws_subnet" "private" {
  count             = var.az_count
  vpc_id            = aws_vpc.main.id
  cidr_block        = cidrsubnet(var.vpc_cidr, 8, count.index + 10)
  availability_zone = data.aws_availability_zones.available.names[count.index]

  tags = merge(local.common_tags, {
    Name = "deployforge-private-${count.index}"
    Tier = "private"
  })
}

# for_each — create resources keyed by map
variable "buckets" {
  type = map(object({
    versioning = bool
    lifecycle_days = number
  }))
  default = {
    logs    = { versioning = true,  lifecycle_days = 90 }
    backups = { versioning = true,  lifecycle_days = 365 }
    tmp     = { versioning = false, lifecycle_days = 7 }
  }
}

resource "aws_s3_bucket" "this" {
  for_each = var.buckets
  bucket   = "deployforge-${each.key}-${var.environment}"

  tags = merge(local.common_tags, {
    Name       = each.key
    Versioning = each.value.versioning
  })
}
```

> **Production note:** Prefer `for_each` over `count` for resources that might be
> added or removed independently. With `count`, removing item `[1]` from a list of
> 3 forces Terraform to destroy and recreate `[2]` because indices shift.
> `for_each` uses stable keys — removing `"logs"` only affects that one resource.

### Dynamic Blocks

When a block inside a resource needs to be repeated, use `dynamic`:

```hcl
variable "ingress_rules" {
  type = list(object({
    port        = number
    protocol    = string
    cidr_blocks = list(string)
    description = string
  }))
  default = [
    { port = 443, protocol = "tcp", cidr_blocks = ["0.0.0.0/0"], description = "HTTPS" },
    { port = 80,  protocol = "tcp", cidr_blocks = ["0.0.0.0/0"], description = "HTTP" },
    { port = 22,  protocol = "tcp", cidr_blocks = ["10.0.0.0/8"], description = "SSH internal" },
  ]
}

resource "aws_security_group" "web" {
  name   = "deployforge-web-${var.environment}"
  vpc_id = aws_vpc.main.id

  dynamic "ingress" {
    for_each = var.ingress_rules
    content {
      from_port   = ingress.value.port
      to_port     = ingress.value.port
      protocol    = ingress.value.protocol
      cidr_blocks = ingress.value.cidr_blocks
      description = ingress.value.description
    }
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = local.common_tags
}
```

### Provisioners — and Why to Avoid Them

Provisioners (`local-exec`, `remote-exec`, `file`) run arbitrary commands
during resource creation. They are the escape hatch — and usually a code smell.

```hcl
# ⚠️ Anti-pattern — prefer cloud-init or user_data instead
resource "aws_instance" "legacy" {
  ami           = data.aws_ami.ubuntu.id
  instance_type = "t3.micro"

  provisioner "remote-exec" {
    inline = [
      "sudo apt-get update",
      "sudo apt-get install -y nginx",
    ]
  }
}
```

| Problem | Why It Hurts |
|---------|-------------|
| Not in state | Terraform can't track what provisioners did |
| Not idempotent | Re-running may fail or produce different results |
| Fragile | SSH connectivity issues cause partial applies |
| Drift-prone | Manual changes after provisioner runs go undetected |

> **Key insight:** If you need to configure a machine, use **cloud-init** /
> `user_data` (baked into the AMI or passed at launch). If you need to install
> software, build a **custom AMI** with Packer. Provisioners exist for legacy
> migration — not as a standard pattern.

### terraform import

Bring existing resources under Terraform management:

```bash
# Import an existing VPC into Terraform state
terraform import aws_vpc.main vpc-0abc123def456

# Terraform 1.5+ — import blocks (declarative, reviewable)
```

```hcl
# import.tf — declarative import (Terraform >= 1.5)
import {
  to = aws_vpc.main
  id = "vpc-0abc123def456"
}

# Then run: terraform plan -generate-config-out=generated.tf
# Terraform generates the HCL for you — review and refine it
```

> **Production note:** After importing, always run `terraform plan` to verify
> the generated config matches reality. Fix any diffs before applying — otherwise
> Terraform will "correct" the resource to match your (possibly incomplete) config.

---

## Code Examples

### Complete VPC Configuration for DeployForge

```hcl
# deployforge/infra/main.tf

terraform {
  required_version = ">= 1.6"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.30"
    }
  }
}

provider "aws" {
  region = var.aws_region

  default_tags {
    tags = {
      Project   = "DeployForge"
      ManagedBy = "terraform"
    }
  }
}

# --- Networking ---

data "aws_availability_zones" "available" {
  state = "available"
}

resource "aws_vpc" "main" {
  cidr_block           = var.vpc_cidr
  enable_dns_hostnames = true
  enable_dns_support   = true

  tags = {
    Name = "deployforge-${var.environment}"
  }
}

resource "aws_subnet" "public" {
  count                   = var.az_count
  vpc_id                  = aws_vpc.main.id
  cidr_block              = cidrsubnet(var.vpc_cidr, 8, count.index)
  availability_zone       = data.aws_availability_zones.available.names[count.index]
  map_public_ip_on_launch = true

  tags = {
    Name                     = "deployforge-public-${count.index}"
    "kubernetes.io/role/elb" = "1"      # ← Required for K8s load balancers
  }
}

resource "aws_subnet" "private" {
  count             = var.az_count
  vpc_id            = aws_vpc.main.id
  cidr_block        = cidrsubnet(var.vpc_cidr, 8, count.index + 10)
  availability_zone = data.aws_availability_zones.available.names[count.index]

  tags = {
    Name                              = "deployforge-private-${count.index}"
    "kubernetes.io/role/internal-elb" = "1"
  }
}

resource "aws_internet_gateway" "main" {
  vpc_id = aws_vpc.main.id
  tags   = { Name = "deployforge-igw" }
}

resource "aws_eip" "nat" {
  count  = var.environment == "prod" ? var.az_count : 1  # ← HA NAT in prod only
  domain = "vpc"
  tags   = { Name = "deployforge-nat-${count.index}" }
}

resource "aws_nat_gateway" "main" {
  count         = var.environment == "prod" ? var.az_count : 1
  allocation_id = aws_eip.nat[count.index].id
  subnet_id     = aws_subnet.public[count.index].id
  tags          = { Name = "deployforge-nat-${count.index}" }
}
```

```hcl
# deployforge/infra/variables.tf

variable "aws_region" {
  description = "AWS region for all resources"
  type        = string
  default     = "us-west-2"
}

variable "environment" {
  description = "Environment name (dev, staging, prod)"
  type        = string

  validation {
    condition     = contains(["dev", "staging", "prod"], var.environment)
    error_message = "Must be dev, staging, or prod."
  }
}

variable "vpc_cidr" {
  description = "CIDR block for the VPC"
  type        = string
  default     = "10.0.0.0/16"
}

variable "az_count" {
  description = "Number of availability zones to use"
  type        = number
  default     = 3
}
```

```hcl
# deployforge/infra/outputs.tf

output "vpc_id" {
  description = "VPC ID"
  value       = aws_vpc.main.id
}

output "public_subnet_ids" {
  description = "Public subnet IDs"
  value       = aws_subnet.public[*].id
}

output "private_subnet_ids" {
  description = "Private subnet IDs"
  value       = aws_subnet.private[*].id
}

output "nat_gateway_ips" {
  description = "NAT Gateway Elastic IPs"
  value       = aws_eip.nat[*].public_ip
}
```

---

## Try It Yourself

### Challenge 1: Add Route Tables

The VPC above has subnets and gateways but no route tables. Add public and private
route tables with appropriate routes.

<details>
<summary>Show solution</summary>

```hcl
# Public route table — routes to internet gateway
resource "aws_route_table" "public" {
  vpc_id = aws_vpc.main.id

  route {
    cidr_block = "0.0.0.0/0"
    gateway_id = aws_internet_gateway.main.id
  }

  tags = {
    Name = "deployforge-public-rt"
  }
}

resource "aws_route_table_association" "public" {
  count          = var.az_count
  subnet_id      = aws_subnet.public[count.index].id
  route_table_id = aws_route_table.public.id
}

# Private route tables — route to NAT gateway
resource "aws_route_table" "private" {
  count  = var.environment == "prod" ? var.az_count : 1
  vpc_id = aws_vpc.main.id

  route {
    cidr_block     = "0.0.0.0/0"
    nat_gateway_id = aws_nat_gateway.main[count.index].id
  }

  tags = {
    Name = "deployforge-private-rt-${count.index}"
  }
}

resource "aws_route_table_association" "private" {
  count          = var.az_count
  subnet_id      = aws_subnet.private[count.index].id
  route_table_id = aws_route_table.private[
    var.environment == "prod" ? count.index : 0
  ].id
}
```

Verify with:
```bash
terraform plan
# → should show route tables and associations being created
terraform graph | dot -Tpng > graph.png
# → visualize the dependency graph
```

</details>

### Challenge 2: Refactor Tags with `default_tags`

The provider block already sets `default_tags`. Some resources still have redundant
`ManagedBy` tags. Remove duplication and ensure `Environment` is set globally.

<details>
<summary>Show solution</summary>

```hcl
provider "aws" {
  region = var.aws_region

  default_tags {
    tags = {
      Project     = "DeployForge"
      ManagedBy   = "terraform"
      Environment = var.environment   # ← moved here from individual resources
    }
  }
}

# Individual resources no longer need Project, ManagedBy, or Environment tags.
# Only resource-specific tags remain:
resource "aws_vpc" "main" {
  cidr_block           = var.vpc_cidr
  enable_dns_hostnames = true
  enable_dns_support   = true

  tags = {
    Name = "deployforge-${var.environment}"
    # Project, ManagedBy, Environment inherited from default_tags
  }
}
```

> **Caution:** `default_tags` merge with resource-level tags but **do not
> appear** in `terraform plan` diffs for individual resources. Always verify
> tags are applied correctly in the cloud console or with `aws resourcegroupstaggingapi`.

</details>

### Challenge 3: Convert Subnets from `count` to `for_each`

The current subnet definitions use `count`. Convert them to `for_each` for stable
resource addressing that won't shift when AZs change.

<details>
<summary>Show solution</summary>

```hcl
locals {
  azs = slice(data.aws_availability_zones.available.names, 0, var.az_count)

  subnets = {
    for i, az in local.azs : az => {
      public_cidr  = cidrsubnet(var.vpc_cidr, 8, i)
      private_cidr = cidrsubnet(var.vpc_cidr, 8, i + 10)
    }
  }
}

resource "aws_subnet" "public" {
  for_each                = local.subnets
  vpc_id                  = aws_vpc.main.id
  cidr_block              = each.value.public_cidr
  availability_zone       = each.key
  map_public_ip_on_launch = true

  tags = {
    Name                     = "deployforge-public-${each.key}"
    "kubernetes.io/role/elb" = "1"
  }
}

resource "aws_subnet" "private" {
  for_each          = local.subnets
  vpc_id            = aws_vpc.main.id
  cidr_block        = each.value.private_cidr
  availability_zone = each.key

  tags = {
    Name                              = "deployforge-private-${each.key}"
    "kubernetes.io/role/internal-elb" = "1"
  }
}

# Reference: values(aws_subnet.public)[*].id or aws_subnet.public["us-west-2a"].id
```

After converting, you'll need to move state:
```bash
# Move count-indexed resources to for_each-keyed resources
terraform state mv 'aws_subnet.public[0]' 'aws_subnet.public["us-west-2a"]'
terraform state mv 'aws_subnet.public[1]' 'aws_subnet.public["us-west-2b"]'
terraform state mv 'aws_subnet.public[2]' 'aws_subnet.public["us-west-2c"]'
# Repeat for private subnets
terraform plan
# → should show no changes (resources moved, not recreated)
```

</details>

---

## Capstone Connection

**DeployForge** infrastructure starts here. Every resource in the capstone —
VPC, subnets, security groups, NAT gateways — is defined as Terraform HCL:

- **Provider configuration** pins AWS provider versions so builds are
  reproducible across the team and CI.
- **Variables with validation** prevent misconfigurations (wrong environment
  name, invalid CIDR) before they reach the cloud API.
- **`for_each` over `count`** ensures subnet resources have stable addresses
  (`aws_subnet.public["us-west-2a"]`) that won't shift when you add or remove AZs.
- **`default_tags`** enforce consistent labeling across all 50+ resources in the
  DeployForge stack, enabling cost tracking and access policies.
- **Data sources** pull in shared resources (account ID, available AZs, existing
  Route 53 zones) without duplicating ownership.

In the next section you'll wrap these resources into reusable **modules** that
compose into full environments — dev, staging, and production — with a single
`terraform apply`.
