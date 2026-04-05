# PostgreSQL Interactive Tutorial

A hands-on, modular PostgreSQL learning guide built for **application developers** who already know the basics of SQL and want to master PostgreSQL — from foundations to production-grade database design, performance tuning, and infrastructure operations.

Every concept is taught with real examples drawn from **StoreForge**, an e-commerce database you build incrementally across all modules.

---

## How to Use This Tutorial

1. Work through modules **in order** — each builds on the previous.
2. After each concept, **apply it to the capstone project** (StoreForge) using the _Capstone Connection_ section at the bottom of every topic file.
3. Complete the **exercises** before moving to the next module.
4. The `capstone/storeforge/` folder has reference SQL files (schema, seed data, functions, indexes) if you get stuck.

> You don't need to memorize every syntax detail. Focus on understanding **when** and **why** to use each feature — the syntax comes with practice.

---

## Prerequisites

| Requirement      | Version | Notes                                                                      |
|------------------|---------|----------------------------------------------------------------------------|
| PostgreSQL       | 15+     | Via Docker (recommended), Homebrew, or package manager                     |
| Docker           | Latest  | For running PostgreSQL without a local install                              |
| `psql`           | 15+     | Ships with PostgreSQL; the primary CLI tool used throughout                |
| SQL GUI tool     | Any     | DBeaver (free, cross-platform) or pgAdmin 4 — pick one for visual work     |
| Basic SQL        | —       | You should know SELECT, INSERT, JOIN. Module 03 reviews these with depth.  |

> **Not required:** Knowledge of any specific programming language. This tutorial is pure SQL and PostgreSQL — no Java, Python, or Node.js code.

---

## Learning Path

| Module | Topic | Est. Time | Capstone Milestone |
|--------|-------|-----------|-------------------|
| [01 — Introduction](modules/01-introduction/) | What are relational databases, object/relational model, ACID, MVCC, WAL | 3–4 hrs | Understand the RDBMS foundations |
| [02 — Installation & Setup](modules/02-installation-setup/) | Docker, psql, pg_ctl, GUI tools | 1–2 hrs | PostgreSQL running, `storeforge` DB created |
| [03 — SQL Fundamentals](modules/03-sql-fundamentals/) | DDL, data types, SELECT, filtering, DML, JOINs, COPY | 5–7 hrs | Core schema + sample data |
| [04 — Advanced SQL](modules/04-advanced-sql/) | Transactions, subqueries, CTE, window functions, LATERAL | 5–7 hrs | Complex analytics queries |
| [05 — Database Design](modules/05-database-design/) | Normalization, constraints, schema patterns/anti-patterns, views | 4–6 hrs | Fully normalized schema with constraints |
| [06 — PL/pgSQL](modules/06-plpgsql/) | Functions, procedures, triggers, custom aggregates | 4–5 hrs | Inventory triggers + audit log |
| [07 — Security](modules/07-security/) | Roles, pg_hba.conf, GRANT/REVOKE, RLS, SSL | 3–4 hrs | Role-based access + row-level security |
| [08 — Performance & Indexing](modules/08-performance-tuning/) | postgresql.conf, EXPLAIN, indexes, vacuum, partitioning | 5–7 hrs | Optimized schema with index strategy |
| [09 — Infrastructure](modules/09-infrastructure/) | Backup, replication, PgBouncer, monitoring, HA | 5–7 hrs | Production-ready operations |
| [10 — Capstone Integration](modules/10-capstone-integration/) | Full production-ready StoreForge | 3–4 hrs | Complete deployment checklist |

**Total estimated time:** 39–53 hours

---

## Capstone Project: StoreForge

StoreForge is a **simplified e-commerce database** — think a stripped-down Shopify or WooCommerce backend — that you build incrementally throughout this tutorial. Each module adds a new layer: from a bare schema to a fully tuned, secured, production-ready PostgreSQL database.

### Domain Model

```
Customer ──places──▶ Order ──contains──▶ OrderItem ──references──▶ Product
 │                     │                                            │
 │                     └──ships to──▶ Address          Category ◀──┘
 │                                                         │
 └──has──▶ Address                             └──parent──▶ Category (self-ref)
 │
 └──writes──▶ Review ──about──▶ Product
```

### Entities

| Entity       | Key Fields |
|--------------|-----------|
| `customer`   | id, email, name, password_hash, created_at |
| `category`   | id, name, slug, parent_id (self-ref), description |
| `product`    | id, name, description, price, stock_quantity, category_id, attributes (JSONB), search_vector (tsvector), created_at |
| `address`    | id, customer_id, street, city, state, postal_code, country, is_default |
| `order`      | id, customer_id, status (PENDING/CONFIRMED/SHIPPED/DELIVERED/CANCELLED), total_amount, shipping_address_id, created_at |
| `order_item` | id, order_id, product_id, quantity, unit_price |
| `review`     | id, product_id, customer_id, rating (1–5), comment, created_at |

### What you'll build module-by-module

| Module | What gets added to StoreForge |
|--------|-------------------------------|
| 01–02  | Understanding + environment setup, `storeforge` database created |
| 03     | Core schema: all 7 tables, sample data loaded |
| 04     | Analytics queries: revenue reports, top products, customer insights |
| 05     | Constraints hardened, schema normalized, reporting views created |
| 06     | Inventory trigger, audit log table+trigger, order processing procedure |
| 07     | Roles (admin/staff/api), RLS for customer data isolation, SSL enforced |
| 08     | Indexes optimized, partitioned orders table, EXPLAIN-verified queries |
| 09     | pg_dump automation, streaming replica, PgBouncer, Prometheus metrics |
| 10     | Final hardened database — production deployment checklist completed |

The capstone lives in [`capstone/storeforge/`](capstone/storeforge/).

---

## Quick Start

```bash
# Start PostgreSQL via Docker
docker run --name storeforge-db \
  -e POSTGRES_USER=storeforge \
  -e POSTGRES_PASSWORD=password \
  -e POSTGRES_DB=storeforge_dev \
  -p 5432:5432 \
  -d postgres:15

# Connect with psql
psql -h localhost -U storeforge -d storeforge_dev

# Or start with Module 1
open modules/01-introduction/README.md
```

---

## MySQL → PostgreSQL Mental Model

If you're coming from MySQL (or any SQL database), here's how key concepts map:

| MySQL / Standard SQL | PostgreSQL |
|---------------------|-----------|
| `AUTO_INCREMENT` | `SERIAL` / `BIGSERIAL` / `GENERATED ALWAYS AS IDENTITY` |
| `SHOW TABLES` | `\dt` in psql / `SELECT tablename FROM pg_tables` |
| `SHOW DATABASES` | `\l` in psql / `SELECT datname FROM pg_database` |
| `DESCRIBE table` | `\d table` in psql |
| `TINYINT` / `MEDIUMINT` | `SMALLINT` / `INTEGER` |
| `DATETIME` | `TIMESTAMP WITH TIME ZONE` |
| `VARCHAR(n)` | `VARCHAR(n)` or `TEXT` (no performance difference) |
| `UNSIGNED` integer | `CHECK (col >= 0)` or domain type |
| `IFNULL(a, b)` | `COALESCE(a, b)` |
| `NOW()` | `NOW()` or `CURRENT_TIMESTAMP` |
| `LIMIT n OFFSET m` | `LIMIT n OFFSET m` (same) |
| `IF(condition, a, b)` | `CASE WHEN condition THEN a ELSE b END` |
| `GROUP_CONCAT()` | `STRING_AGG(col, ',')` |
| `JSON_EXTRACT()` | `col->>'key'` or `col#>>'{a,b}'` |
| `fulltext index` | `GIN` index on `tsvector` column |
| `EXPLAIN` (basic) | `EXPLAIN (ANALYZE, BUFFERS)` (much richer) |
| `SHOW PROCESSLIST` | `SELECT * FROM pg_stat_activity` |
| `INFORMATION_SCHEMA` | `INFORMATION_SCHEMA` + richer `pg_catalog` views |
| `mysqldump` | `pg_dump` |
| `mysql` CLI | `psql` CLI |

---

## Project Structure

```
postgres/
├── README.md                              ← You are here
├── modules/
│   ├── 01-introduction/
│   │   ├── README.md
│   │   ├── 01-what-are-relational-databases.md
│   │   ├── 02-rdbms-benefits-limitations.md
│   │   ├── 03-object-model.md
│   │   ├── 04-relational-model.md
│   │   ├── 05-postgresql-overview.md
│   │   ├── 06-high-level-concepts.md
│   │   └── exercises/README.md
│   ├── 02-installation-setup/
│   ├── 03-sql-fundamentals/
│   ├── 04-advanced-sql/
│   ├── 05-database-design/
│   ├── 06-plpgsql/
│   ├── 07-security/
│   ├── 08-performance-tuning/
│   ├── 09-infrastructure/
│   └── 10-capstone-integration/
└── capstone/
    └── storeforge/
        ├── README.md          ← Capstone setup guide
        ├── schema.sql         ← Complete DDL
        ├── seed.sql           ← Realistic sample data
        ├── functions.sql      ← Stored functions + triggers
        ├── security.sql       ← Roles + RLS policies
        └── indexes.sql        ← Optimized index set
```
