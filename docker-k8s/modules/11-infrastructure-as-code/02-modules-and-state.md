# 11.2 — Modules, State & Workspaces

## Concept

A single flat directory of `.tf` files works for small projects. It falls apart
the moment you need the same networking stack in dev, staging, and production — or
when two engineers run `terraform apply` at the same time and corrupt the state
file. Terraform **modules** solve the reuse problem; **remote state** with locking
solves the collaboration problem; **workspaces** and directory-based environments
solve the multi-environment problem. Together they transform Terraform from a
scripting tool into a team-scale infrastructure platform.

---

## Deep Dive

### Module Structure

A Terraform module is just a directory of `.tf` files with a clean interface.
The convention that's stood the test of time:

```
modules/networking/
├── main.tf           # ← resource definitions
├── variables.tf      # ← input interface
├── outputs.tf        # ← output interface
├── versions.tf       # ← provider and terraform version constraints
├── locals.tf         # ← computed values (optional)
└── README.md         # ← usage docs (terraform-docs can generate this)
```

```hcl
# modules/networking/variables.tf — the module's input contract

variable "environment" {
  description = "Environment name (dev, staging, prod)"
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
  description = "Deploy one NAT gateway per AZ (true for prod)"
  type        = bool
  default     = false
}

variable "tags" {
  description = "Additional tags to merge with defaults"
  type        = map(string)
  default     = {}
}
```

```hcl
# modules/networking/outputs.tf — the module's output contract

output "vpc_id" {
  description = "ID of the VPC"
  value       = aws_vpc.main.id
}

output "public_subnet_ids" {
  description = "List of public subnet IDs"
  value       = [for s in aws_subnet.public : s.id]
}

output "private_subnet_ids" {
  description = "List of private subnet IDs"
  value       = [for s in aws_subnet.private : s.id]
}

output "nat_gateway_ids" {
  description = "List of NAT gateway IDs"
  value       = aws_nat_gateway.main[*].id
}
```

> **Key insight:** Think of `variables.tf` as function parameters and `outputs.tf`
> as return values. If you wouldn't put something in a function signature, don't
> put it in the module interface. Keep the surface area small — expose only what
> consumers actually need.

### Calling Modules

```hcl
# envs/dev/main.tf — composing modules into an environment

module "networking" {
  source = "../../modules/networking"

  environment   = "dev"
  vpc_cidr      = "10.0.0.0/16"
  az_count      = 2                 # ← dev only needs 2 AZs
  enable_nat_ha = false             # ← single NAT in dev (cost savings)

  tags = {
    CostCenter = "engineering"
  }
}

module "cluster" {
  source = "../../modules/cluster"

  environment        = "dev"
  vpc_id             = module.networking.vpc_id
  private_subnet_ids = module.networking.private_subnet_ids
  node_instance_type = "t3.medium"
  node_count         = 2
}

module "database" {
  source = "../../modules/database"

  environment        = "dev"
  vpc_id             = module.networking.vpc_id
  private_subnet_ids = module.networking.private_subnet_ids
  instance_class     = "db.t3.micro"
  multi_az           = false        # ← single AZ in dev
}
```

```
┌────────────────────────────────────────────────────────┐
│                 envs/dev/main.tf                       │
│                                                        │
│  ┌──────────────┐   ┌──────────────┐   ┌────────────┐ │
│  │  module.      │   │  module.      │   │  module.   │ │
│  │  networking   │──▶│  cluster      │   │  database  │ │
│  │              │   │              │   │            │ │
│  │ vpc_id ──────┼──▶│ vpc_id       │   │            │ │
│  │ subnet_ids ──┼──▶│ subnet_ids   │   │            │ │
│  │              │   └──────────────┘   │            │ │
│  │ vpc_id ──────┼──────────────────────▶│ vpc_id     │ │
│  │ subnet_ids ──┼──────────────────────▶│ subnet_ids │ │
│  └──────────────┘                       └────────────┘ │
└────────────────────────────────────────────────────────┘
```

### Module Sources

Modules can be sourced from multiple locations:

```hcl
# Local path — during development
module "networking" {
  source = "../../modules/networking"
}

# Terraform Registry — public or private
module "vpc" {
  source  = "terraform-aws-modules/vpc/aws"
  version = "~> 5.0"
}

# Git repository — with tag pinning
module "networking" {
  source = "git::https://github.com/acme/terraform-modules.git//networking?ref=v2.3.1"
}

# S3 bucket — for private module distribution
module "networking" {
  source = "s3::https://s3-us-west-2.amazonaws.com/acme-tf-modules/networking.zip"
}
```

> **Production note:** Always pin module versions. Using `source = "../../modules/networking"`
> is fine in a monorepo, but for cross-repo modules use Git tags (`?ref=v2.3.1`)
> or registry versions. Unpinned modules are unpinned dependencies — they will
> break on a Friday afternoon.

### Module Composition Patterns

#### Pattern 1: Flat Composition

Each environment file calls all modules directly. Simple, explicit, but repetitive.

```hcl
# envs/prod/main.tf
module "networking" { source = "../../modules/networking" ... }
module "cluster"    { source = "../../modules/cluster"    ... }
module "database"   { source = "../../modules/database"   ... }
module "monitoring" { source = "../../modules/monitoring"  ... }
```

#### Pattern 2: Environment Wrapper Module

A meta-module that composes all sub-modules for a standard environment:

```hcl
# modules/environment/main.tf
module "networking" {
  source        = "../networking"
  environment   = var.environment
  vpc_cidr      = var.vpc_cidr
  az_count      = var.az_count
  enable_nat_ha = var.environment == "prod"
}

module "cluster" {
  source             = "../cluster"
  environment        = var.environment
  vpc_id             = module.networking.vpc_id
  private_subnet_ids = module.networking.private_subnet_ids
  node_instance_type = var.node_instance_type
  node_count         = var.node_count
}

# ... database, monitoring, etc.
```

```hcl
# envs/prod/main.tf — one module call per environment
module "env" {
  source = "../../modules/environment"

  environment        = "prod"
  vpc_cidr           = "10.0.0.0/16"
  az_count           = 3
  node_instance_type = "m5.xlarge"
  node_count         = 6
}
```

> **Key insight:** Flat composition works for teams up to ~5 engineers. Beyond
> that, the wrapper pattern reduces drift between environments. The tradeoff is
> flexibility — a wrapper forces consistency, which is usually what you want in
> production.

---

### State File Anatomy

Every `terraform apply` writes a **state file** — a JSON document that maps
your HCL resources to real cloud resources.

```json
{
  "version": 4,
  "terraform_version": "1.6.4",
  "serial": 42,
  "lineage": "a1b2c3d4-e5f6-...",
  "outputs": {
    "vpc_id": {
      "value": "vpc-0abc123",
      "type": "string"
    }
  },
  "resources": [
    {
      "mode": "managed",
      "type": "aws_vpc",
      "name": "main",
      "provider": "provider[\"registry.terraform.io/hashicorp/aws\"]",
      "instances": [
        {
          "attributes": {
            "id": "vpc-0abc123",
            "cidr_block": "10.0.0.0/16",
            "tags": { "Name": "deployforge-prod" }
          }
        }
      ]
    }
  ]
}
```

| Field | Purpose |
|-------|---------|
| `version` | State file format version |
| `serial` | Incremented on every write — used for conflict detection |
| `lineage` | Unique ID for this state — prevents accidental cross-state operations |
| `outputs` | Cached output values (used by `terraform output` and remote state data sources) |
| `resources` | Full attribute dump of every managed resource |

> **Caution:** State files contain **secrets** — database passwords, API keys,
> TLS certificates. Never commit `terraform.tfstate` to Git. Use a remote backend
> with encryption at rest.

### Remote Backends

```hcl
# backend.tf — S3 backend with DynamoDB locking (AWS)

terraform {
  backend "s3" {
    bucket         = "deployforge-terraform-state"
    key            = "prod/networking/terraform.tfstate"
    region         = "us-west-2"
    encrypt        = true                  # ← AES-256 encryption at rest
    dynamodb_table = "deployforge-tf-locks" # ← distributed locking
  }
}
```

```hcl
# backend.tf — GCS backend (Google Cloud)

terraform {
  backend "gcs" {
    bucket = "deployforge-terraform-state"
    prefix = "prod/networking"
  }
}
```

```hcl
# backend.tf — Terraform Cloud backend

terraform {
  cloud {
    organization = "deployforge"

    workspaces {
      name = "deployforge-prod-networking"
    }
  }
}
```

### State Locking

```
┌───────────────────────────────────────────────────────────────┐
│                    State Locking Flow                          │
│                                                               │
│  Engineer A                          Engineer B                │
│  ─────────                          ─────────                 │
│  terraform apply                                              │
│       │                                                       │
│       ▼                                                       │
│  ┌─────────────┐                                              │
│  │ Acquire Lock│ ──▶ DynamoDB: LockID = "prod/networking"     │
│  │   ✅ OK     │                                              │
│  └──────┬──────┘                                              │
│         │                             terraform apply          │
│         │                                  │                   │
│         │                                  ▼                   │
│         │                          ┌─────────────┐            │
│         │                          │ Acquire Lock│            │
│         │                          │   ❌ LOCKED │            │
│         │                          └─────────────┘            │
│         │                          Error: state locked by A    │
│         ▼                                                     │
│  ┌─────────────┐                                              │
│  │ Write State │                                              │
│  │ Release Lock│                                              │
│  └─────────────┘                                              │
│                                                               │
│         Now Engineer B can acquire the lock and apply.         │
└───────────────────────────────────────────────────────────────┘
```

> **Production note:** If a lock gets stuck (e.g., CI runner crashed mid-apply),
> use `terraform force-unlock <LOCK_ID>`. But first verify no apply is actually
> running — force-unlocking during an active apply causes state corruption.

### Workspaces for Environment Management

Terraform workspaces store separate state files under the same configuration:

```bash
# Create workspaces
terraform workspace new dev
terraform workspace new staging
terraform workspace new prod

# Switch workspace
terraform workspace select prod

# List workspaces
terraform workspace list
# * dev
#   staging
#   prod
```

```hcl
# Use workspace name in configuration
locals {
  environment = terraform.workspace

  config = {
    dev = {
      instance_type = "t3.small"
      node_count    = 2
      multi_az      = false
    }
    staging = {
      instance_type = "t3.medium"
      node_count    = 3
      multi_az      = false
    }
    prod = {
      instance_type = "m5.xlarge"
      node_count    = 6
      multi_az      = true
    }
  }

  env = local.config[local.environment]
}

module "cluster" {
  source             = "../../modules/cluster"
  environment        = local.environment
  node_instance_type = local.env.instance_type
  node_count         = local.env.node_count
}
```

> **Key insight:** Workspaces vs. directory-based environments is a
> contentious topic. The consensus among experienced Terraform users:
>
> - **Workspaces** work well for identical infrastructure at different scales
>   (same resources, different sizes).
> - **Directory-based** (`envs/dev/`, `envs/prod/`) works better when
>   environments genuinely differ (prod has WAF, dev doesn't).
>
> DeployForge uses **directory-based environments** because production requires
> resources (WAF, CloudFront, multi-region) that don't exist in dev.

### State Manipulation

Sometimes you need to surgically modify state without destroying resources:

```bash
# List all resources in state
terraform state list
# → aws_vpc.main
# → aws_subnet.public[0]
# → module.cluster.aws_eks_cluster.main

# Show a specific resource
terraform state show aws_vpc.main

# Move a resource (rename or refactor)
terraform state mv aws_vpc.main module.networking.aws_vpc.main
# → resource moves in state without being destroyed/recreated

# Remove from state (Terraform forgets about it, resource still exists)
terraform state rm aws_s3_bucket.legacy
# → useful when migrating ownership to another state

# Import (add existing resource to state)
terraform import aws_vpc.main vpc-0abc123
```

> **Caution:** State manipulation is surgery on a live patient. Always:
> 1. Take a state backup: `terraform state pull > backup.tfstate`
> 2. Run in a maintenance window
> 3. Verify with `terraform plan` — expect **zero changes** after manipulation

### Blast Radius Reduction

One monolithic state file = one blast radius. If `terraform apply` goes wrong,
everything in that state is at risk. Split state to limit damage:

```
┌─────────────────────────────────────────────────────────────┐
│              State Separation Strategy                       │
│                                                             │
│  ❌ Monolith (everything in one state):                     │
│  ┌────────────────────────────────────────────────────────┐ │
│  │ VPC + Subnets + EKS + RDS + S3 + IAM + DNS + CDN     │ │
│  │ Blast radius: EVERYTHING                              │ │
│  └────────────────────────────────────────────────────────┘ │
│                                                             │
│  ✅ Split by layer:                                         │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐     │
│  │  networking   │  │   cluster    │  │   database   │     │
│  │  (VPC, SGs)   │  │  (EKS, nodes)│  │  (RDS, creds)│     │
│  │  Blast: nets  │  │  Blast: K8s  │  │  Blast: DB   │     │
│  └──────┬───────┘  └──────┬───────┘  └──────┬───────┘     │
│         │                  │                  │             │
│         ▼                  ▼                  ▼             │
│  s3://state/net.tfstate   cluster.tfstate   db.tfstate     │
│                                                             │
│  Cross-reference via terraform_remote_state data source     │
└─────────────────────────────────────────────────────────────┘
```

```hcl
# cluster/main.tf — reads networking state without managing it

data "terraform_remote_state" "networking" {
  backend = "s3"

  config = {
    bucket = "deployforge-terraform-state"
    key    = "prod/networking/terraform.tfstate"
    region = "us-west-2"
  }
}

module "cluster" {
  source             = "../../modules/cluster"
  vpc_id             = data.terraform_remote_state.networking.outputs.vpc_id
  private_subnet_ids = data.terraform_remote_state.networking.outputs.private_subnet_ids
}
```

### Sensitive Data in State

```hcl
# Mark variables as sensitive
variable "db_password" {
  description = "Database master password"
  type        = string
  sensitive   = true                  # ← hidden in plan output
}

# Mark outputs as sensitive
output "db_connection_string" {
  description = "Database connection string"
  value       = "postgresql://${var.db_username}:${var.db_password}@${aws_db_instance.main.endpoint}/deployforge"
  sensitive   = true
}
```

> **Production note:** `sensitive = true` hides values in CLI output and plan
> files, but they're still stored **in plaintext** in the state file. This is why
> remote backends with encryption are non-negotiable. For extra security, use
> external secret managers (Vault, AWS Secrets Manager) and reference them via
> data sources instead of passing secrets as variables.

---

## Code Examples

### Bootstrap Script for Remote State Backend

Before you can use an S3 backend, the bucket and DynamoDB table must exist.
This is the classic chicken-and-egg problem — solved with a bootstrap script:

```bash
#!/usr/bin/env bash
# bootstrap-state-backend.sh — run once per AWS account
set -euo pipefail

BUCKET="deployforge-terraform-state"
TABLE="deployforge-tf-locks"
REGION="us-west-2"

echo "Creating S3 bucket for state..."
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

echo "Creating DynamoDB table for locking..."
aws dynamodb create-table \
  --table-name "$TABLE" \
  --attribute-definitions AttributeName=LockID,AttributeType=S \
  --key-schema AttributeName=LockID,KeyType=HASH \
  --billing-mode PAY_PER_REQUEST \
  --region "$REGION"

echo "✅ Backend infrastructure ready"
echo "   Bucket: $BUCKET"
echo "   Lock table: $TABLE"
```

### Complete Environment Composition

```hcl
# envs/prod/main.tf — production environment

terraform {
  required_version = ">= 1.6"

  backend "s3" {
    bucket         = "deployforge-terraform-state"
    key            = "prod/main/terraform.tfstate"
    region         = "us-west-2"
    encrypt        = true
    dynamodb_table = "deployforge-tf-locks"
  }
}

locals {
  environment = "prod"
  common_tags = {
    Project     = "DeployForge"
    Environment = local.environment
    ManagedBy   = "terraform"
  }
}

module "networking" {
  source = "../../modules/networking"

  environment   = local.environment
  vpc_cidr      = "10.0.0.0/16"
  az_count      = 3
  enable_nat_ha = true              # ← HA NAT in production
  tags          = local.common_tags
}

module "cluster" {
  source = "../../modules/cluster"

  environment        = local.environment
  vpc_id             = module.networking.vpc_id
  private_subnet_ids = module.networking.private_subnet_ids
  node_instance_type = "m5.xlarge"
  node_min_count     = 3
  node_max_count     = 10
  node_desired_count = 6
  tags               = local.common_tags
}

module "database" {
  source = "../../modules/database"

  environment        = local.environment
  vpc_id             = module.networking.vpc_id
  private_subnet_ids = module.networking.private_subnet_ids
  instance_class     = "db.r6g.large"
  multi_az           = true          # ← failover replica in production
  backup_retention   = 30
  tags               = local.common_tags
}

module "monitoring" {
  source = "../../modules/monitoring"

  environment   = local.environment
  cluster_name  = module.cluster.cluster_name
  tags          = local.common_tags
}
```

---

## Try It Yourself

### Challenge 1: Create a Reusable Security Group Module

Write a module at `modules/security-group/` that accepts a list of ingress rules
and creates an `aws_security_group`. The module should output the security group ID.

<details>
<summary>Show solution</summary>

```hcl
# modules/security-group/variables.tf
variable "name" {
  description = "Security group name"
  type        = string
}

variable "vpc_id" {
  description = "VPC ID"
  type        = string
}

variable "ingress_rules" {
  description = "List of ingress rules"
  type = list(object({
    from_port   = number
    to_port     = number
    protocol    = string
    cidr_blocks = list(string)
    description = string
  }))
}

variable "tags" {
  description = "Tags to apply"
  type        = map(string)
  default     = {}
}
```

```hcl
# modules/security-group/main.tf
resource "aws_security_group" "this" {
  name   = var.name
  vpc_id = var.vpc_id

  dynamic "ingress" {
    for_each = var.ingress_rules
    content {
      from_port   = ingress.value.from_port
      to_port     = ingress.value.to_port
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
    description = "Allow all outbound"
  }

  tags = merge(var.tags, { Name = var.name })
}
```

```hcl
# modules/security-group/outputs.tf
output "id" {
  description = "Security group ID"
  value       = aws_security_group.this.id
}

output "arn" {
  description = "Security group ARN"
  value       = aws_security_group.this.arn
}
```

Usage:
```hcl
module "web_sg" {
  source = "../../modules/security-group"

  name   = "deployforge-web-prod"
  vpc_id = module.networking.vpc_id

  ingress_rules = [
    { from_port = 443, to_port = 443, protocol = "tcp", cidr_blocks = ["0.0.0.0/0"], description = "HTTPS" },
    { from_port = 80,  to_port = 80,  protocol = "tcp", cidr_blocks = ["0.0.0.0/0"], description = "HTTP"  },
  ]

  tags = local.common_tags
}
```

Verify:
```bash
terraform validate
# → Success! The configuration is valid.
terraform plan
# → Plan: 1 to add, 0 to change, 0 to destroy.
```

</details>

### Challenge 2: Implement State Migration

You have a local state file and need to migrate to an S3 remote backend. Write
the backend configuration and the migration commands.

<details>
<summary>Show solution</summary>

1. Add the backend block:
```hcl
# backend.tf
terraform {
  backend "s3" {
    bucket         = "deployforge-terraform-state"
    key            = "prod/networking/terraform.tfstate"
    region         = "us-west-2"
    encrypt        = true
    dynamodb_table = "deployforge-tf-locks"
  }
}
```

2. Run the migration:
```bash
# Backup local state first
cp terraform.tfstate terraform.tfstate.backup

# Re-initialize — Terraform detects the backend change
terraform init -migrate-state
# → Terraform will ask:
# "Do you want to copy existing state to the new backend?"
# → Type: yes

# Verify state was migrated
terraform state list
# → should show all existing resources

# Verify no changes planned
terraform plan
# → No changes. Your infrastructure matches the configuration.

# Clean up local state (now stored remotely)
rm terraform.tfstate terraform.tfstate.backup
```

> **Caution:** After migration, `.terraform/terraform.tfstate` contains a
> pointer to the remote backend — not the actual state. Don't delete `.terraform/`.

</details>

---

## Capstone Connection

**DeployForge** uses the module and state patterns from this section directly:

- **Module composition** — the `infra/modules/` directory contains four modules
  (networking, cluster, database, monitoring) that compose into complete
  environments through `infra/envs/{dev,staging,prod}/main.tf`.
- **Remote state with locking** — an S3 bucket + DynamoDB table prevents
  concurrent applies and provides state history through S3 versioning.
- **Blast radius reduction** — networking state is separate from cluster state.
  A bad `terraform apply` on the cluster module can't accidentally delete the VPC.
- **Cross-state references** — the cluster module reads VPC and subnet IDs from
  the networking module's remote state via `terraform_remote_state`.
- **Sensitive outputs** — database credentials are marked `sensitive` and
  injected into Kubernetes Secrets via the Terraform Kubernetes provider, never
  appearing in plan output or CI logs.

The next section provisions the Kubernetes cluster itself — the `modules/cluster/`
module that ties networking to compute.
