# Cloud Deployment Options

## Concept

Running PostgreSQL in the cloud means choosing between **self-managed** (you install and operate PostgreSQL on virtual machines) and **fully managed** services (the cloud provider handles installation, patching, backups, HA, and in some cases, scaling). Managed services trade control for operational simplicity. This lesson surveys the major options, their tradeoffs, and where each fits.

---

## Comparison Matrix

| Service | Provider | Engine | HA Built-In | PITR | Extensions | Logical Rep. | Notes |
|---------|----------|--------|-------------|------|------------|--------------|-------|
| **RDS for PostgreSQL** | AWS | OSS PG | Multi-AZ | Yes | Limited | Yes (PG13+) | Most widely used managed PG |
| **Aurora PostgreSQL** | AWS | Modified PG | Yes (6 copies) | Yes | Limited | Yes | 3-5× faster writes than RDS |
| **Cloud SQL** | GCP | OSS PG | Yes | Yes | Limited | Yes | Good GCP-native integration |
| **AlloyDB** | GCP | Modified PG | Yes | Yes | Limited | Yes | HTAP: analytics + OLTP together |
| **Azure Database for PostgreSQL** | Azure | OSS PG | Yes | Yes | Limited | Yes | Flexible Server recommended |
| **Supabase** | Multi-cloud | OSS PG | Yes | Yes | Many | Yes | Developer-friendly, open source |
| **Neon** | Multi-cloud | Modified PG | Yes | Yes | Limited | No | Serverless, branching |
| **Self-managed on EC2/GCE/Azure VM** | Any | OSS PG | You build it | You build it | All | Yes | Full control, full responsibility |

---

## AWS RDS for PostgreSQL

```bash
# Create an RDS instance via AWS CLI:
aws rds create-db-instance \
    --db-instance-identifier storeforge-prod \
    --db-instance-class db.r6g.large \
    --engine postgres \
    --engine-version 16.2 \
    --master-username storeforge \
    --master-user-password 'changeme' \
    --allocated-storage 100 \
    --storage-type gp3 \
    --multi-az \
    --backup-retention-period 7 \
    --auto-minor-version-upgrade \
    --deletion-protection
```

```sql
-- RDS-specific views:
SELECT * FROM pg_stat_activity;  -- standard, works the same
-- rds_superuser role (not actual postgres superuser):
-- You cannot call pg_reload_conf() — use AWS Console/CLI to update parameters

-- RDS Parameter Groups replace postgresql.conf:
-- Changes via Console: "Parameter Groups" → modify → apply immediately or next reboot

-- Extensions available on RDS (subset of full PostgreSQL):
SELECT name FROM pg_available_extensions ORDER BY name;
-- Available: pg_stat_statements, uuid-ossp, pgcrypto, pg_trgm, ltree, pg_cron
-- NOT available: pg_repack (use vacuum instead), some PostGIS features

-- Enable an extension:
CREATE EXTENSION IF NOT EXISTS pg_stat_statements;

-- RDS Performance Insights (built-in slow query analysis):
-- Console → Database → Performance Insights → Top SQL
```

---

## AWS Aurora PostgreSQL

Aurora uses a distributed storage layer (6 copies across 3 AZs) decoupled from the compute:

```sql
-- Aurora-specific: fast cloning (zero-copy clone of the database):
-- Done via Console or: aws rds restore-db-cluster-to-point-in-time \
--     --source-db-cluster-identifier storeforge-prod \
--     --restore-to-time "2024-05-15T14:00:00Z"

-- Aurora Global Database: replicate to another region with < 1 second lag
-- Useful for: geo-distributed reads, disaster recovery across regions

-- Connection endpoints:
-- Writer endpoint:    storeforge-cluster.cluster-xxx.us-east-1.rds.amazonaws.com
-- Reader endpoint:    storeforge-cluster.cluster-ro-xxx.us-east-1.rds.amazonaws.com
-- (load-balanced across all Aurora Replicas)

-- Aurora PostgreSQL limitations vs standard PG:
-- - Some extensions not available
-- - pg_upgrade not supported — must use logical replication to upgrade major versions
-- - Shared storage means VACUUM is different (Aurora handles cleanup internally)
```

---

## Google Cloud SQL for PostgreSQL

```bash
# Create a Cloud SQL instance:
gcloud sql instances create storeforge-prod \
    --database-version=POSTGRES_16 \
    --tier=db-custom-4-15360 \
    --region=us-central1 \
    --availability-type=REGIONAL \
    --backup-start-time=02:00 \
    --retained-backups-count=7 \
    --retained-transaction-log-days=7 \
    --storage-type=SSD \
    --storage-size=100GB \
    --storage-auto-increase
```

```sql
-- Cloud SQL uses Cloud SQL Auth Proxy for secure connections:
-- ./cloud_sql_proxy storeforge-project:us-central1:storeforge-prod &
-- psql -h 127.0.0.1 -U storeforge -d storeforge_dev

-- Database flags configure postgresql.conf settings:
-- gcloud sql instances patch storeforge-prod \
--     --database-flags shared_buffers=512MB,max_connections=200
```

---

## Supabase

Supabase provides PostgreSQL with a batteries-included developer platform:

```sql
-- Supabase automatically enables: uuid-ossp, pgcrypto, pg_trgm, pgjwt, pgsodium,
-- pg_stat_statements, and many more extensions.

-- RLS is a first-class feature — Supabase JS/Python clients pass JWT claims
-- that map to PostgreSQL session variables for RLS policies.

-- Supabase-specific: pg_graphql extension for auto-generated GraphQL API:
CREATE EXTENSION IF NOT EXISTS pg_graphql;

-- PostgREST: automatic REST API from your PostgreSQL schema
-- Just grant privileges to the anon or authenticated role.
GRANT SELECT ON product TO anon;
GRANT SELECT, INSERT ON "order" TO authenticated;
-- Now: GET https://your-project.supabase.co/rest/v1/product works automatically.

-- Storage buckets, Auth, Realtime (listen to table changes via websockets)
-- are all built on top of PostgreSQL.
```

---

## Neon — Serverless PostgreSQL

Neon separates storage from compute, enabling:
- **Branching**: create a copy of your database in < 1 second with zero data copying (copy-on-write)
- **Scale to zero**: compute pauses when idle, resumes in ~100ms
- **Autoscaling**: compute scales with load

```bash
# Neon CLI:
neonctl project create --name storeforge

# Create a branch (instant — copy-on-write of production data):
neonctl branch create --name feature/new-pricing --parent main

# Connect to branch:
psql "$(neonctl connection-string --branch feature/new-pricing)"

# Test schema changes on the branch, then delete it when done:
neonctl branch delete feature/new-pricing
```

---

## Self-Managed on VMs

When you need maximum control — custom extensions, specific configurations, regulatory requirements:

```bash
# Install PostgreSQL 16 on Ubuntu 22.04:
sudo apt install -y postgresql-16 postgresql-client-16 \
    postgresql-16-pg-stat-statements \
    postgresql-16-pgcrypto

# Service management:
sudo systemctl start postgresql
sudo systemctl enable postgresql

# Configure:
sudo -u postgres psql -c "ALTER USER postgres PASSWORD 'changeme';"
sudo nano /etc/postgresql/16/main/postgresql.conf
sudo nano /etc/postgresql/16/main/pg_hba.conf
sudo systemctl reload postgresql

# With Ansible/Terraform for infrastructure-as-code:
# module "postgres" {
#   source = "terraform-aws-modules/rds/aws"
#   engine = "postgres"
#   engine_version = "16.2"
#   ...
# }
```

---

## Decision Guide

```
Is data regulation preventing third-party hosting? → Self-managed on VMs
Do you need ALL PostgreSQL extensions (pg_repack, custom extensions)? → Self-managed
Do you want zero ops overhead and are on AWS? → RDS or Aurora
Do you need > 99.99% availability with multi-region? → Aurora Global
Are you on GCP? → Cloud SQL or AlloyDB
Do you want an open-source alternative with developer tools? → Supabase
Do you need instant database branching for dev/test? → Neon
```

---

## Try It Yourself

```sql
-- 1. What extensions does Supabase enable by default that standard RDS does not?
--    Look up the Supabase docs extension list and compare.

-- 2. Your team is migrating from RDS PG 15 to PG 16.
--    Can you use pg_upgrade? What approach should you use instead?

-- 3. What is the purpose of the Cloud SQL Auth Proxy?
--    What security problem does it solve?

-- 4. Rank these managed services for a startup with a small team,
--    optimizing for minimal operations burden and developer velocity:
--    RDS, Aurora, Supabase, Neon, Self-managed.
```

<details>
<summary>Show solutions</summary>

```text
-- 1. Supabase default extensions (partial list):
psql: uuid-ossp, pgcrypto, pg_trgm, ltree, pg_stat_statements,
      pgjwt (JWT functions), pgsodium (libsodium encryption), pg_graphql,
      plpgsql, plv8 (JavaScript in DB), PostGIS, pg_cron, pg_net (HTTP from SQL)

RDS default extensions: fewer bundled, but you can enable most via CREATE EXTENSION.
Key missing from RDS: pg_graphql, pgjwt, pg_net, plv8 without custom parameter groups.

-- 2. RDS PG 15 → PG 16 upgrade:
pg_upgrade is NOT supported on RDS — AWS manages the underlying binaries.
Use either:
  a) AWS-managed upgrade: Console → Modify → Engine Version → Apply Immediately
     (AWS does the upgrade for you; minimal downtime with Multi-AZ for the failover)
  b) Logical replication: set up a PG 16 RDS instance, create subscription,
     migrate traffic, decommission PG 15 (best for near-zero downtime)
  c) AWS Database Migration Service (DMS): for cross-engine or cross-region migrations.

-- 3. Cloud SQL Auth Proxy purpose:
The Auth Proxy establishes an encrypted, IAM-authenticated tunnel between your
application and Cloud SQL. It eliminates the need to:
- Whitelist IP addresses in Cloud SQL firewall rules
- Manage SSL certificates manually
- Expose the Cloud SQL instance to the public internet
Instead: app connects to 127.0.0.1:5432 → proxy → Cloud SQL (over TLS, verified via IAM).

-- 4. Ranking for startup (least ops burden first):
1. Supabase — managed PG, built-in auth, storage, REST API, Realtime, dashboard
2. Neon — serverless (scale to zero saves money), branching for dev
3. RDS — solid, well-documented, but more setup than Supabase
4. Aurora — more expensive, more powerful, overkill for a startup initially
5. Self-managed — maximum control, but your team now owns backups, HA, upgrades
```

</details>

---

## Capstone Connection

StoreForge cloud journey:
- **Phase 1 (MVP)**: Supabase — zero ops, instant REST API, RLS integration with the frontend JWT auth
- **Phase 2 (Growth)**: Migrated to RDS PostgreSQL 16 (Multi-AZ) for more control over extensions, access to pg_cron and custom parameter groups
- **Phase 3 (Scale)**: Aurora PostgreSQL for the primary OLTP workload (3× write throughput needed); RDS read replicas retained for analytics
- **Dev/test workflow**: Neon branches — each PR gets a Neon branch database seeded from production anonymized data, deleted when the PR closes
