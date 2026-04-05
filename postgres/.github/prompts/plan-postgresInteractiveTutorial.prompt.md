# Plan: PostgreSQL Interactive Tutorial (StoreForge)

## TL;DR
Create a modular, exercise-driven PostgreSQL tutorial in `/postgres/` mirroring the Spring Boot tutorial's structure. 10 modules + capstone integration, targeting general application developers with SQL familiarity. Capstone project "StoreForge" (e-commerce database) grows incrementally across modules. Each module has: README.md (overview, objectives, topics table, time estimate, capstone milestone), individual lesson files, and exercises/README.md.

---

## Capstone Project: StoreForge

An **e-commerce database** built incrementally across all modules.

### Domain Model
```
Customer ──places──▶ Order ──contains──▶ OrderItem ──references──▶ Product
 │                     │                                            │
 │                     └──ships to──▶ Address          Category ◀──┘
 │                                     │                 │
 └──has──▶ Address ◀──────────────────┘    └──parent──▶ Category (self-ref)
 │
 └──writes──▶ Review ──about──▶ Product
```

### Entities
| Entity      | Key Fields |
|-------------|-----------|
| `customer`  | id, email, name, password_hash, created_at |
| `category`  | id, name, slug, parent_id (self-ref), description |
| `product`   | id, name, description, price, stock_quantity, category_id, attributes (JSONB), search_vector (tsvector), created_at |
| `address`   | id, customer_id, street, city, state, postal_code, country, is_default |
| `order`     | id, customer_id, status (PENDING/CONFIRMED/SHIPPED/DELIVERED/CANCELLED), total_amount, shipping_address_id, created_at |
| `order_item`| id, order_id, product_id, quantity, unit_price |
| `review`    | id, product_id, customer_id, rating (1-5), comment, created_at |

### What gets built module-by-module
| Module | What gets added to StoreForge |
|--------|-------------------------------|
| 01     | Understand RDBMS concepts that underpin every design decision |
| 02     | PostgreSQL running locally, `psql` connected, database created |
| 03     | Core schema: customers, products, categories, orders, order_items |
| 04     | Complex queries: sales reports, inventory analytics, customer insights |
| 05     | Normalized schema, constraints, views, proper schema design |
| 06     | Stored functions, triggers (inventory, audit log), procedures |
| 07     | Roles (admin/staff/customer), RLS for multi-tenant access, SSL |
| 08     | EXPLAIN analysis, index strategy, vacuum tuning, postgresql.conf |
| 09     | pg_dump backups, streaming replication, PgBouncer, monitoring |
| 10     | Full production-ready database with all optimizations applied |

---

## Module Structure (10 modules + capstone)

### Module 01: Introduction to PostgreSQL
**Est. Time:** 3–4 hours | **Capstone Milestone:** Understand the RDBMS foundations

| # | File | Concept |
|---|------|---------|
| 1 | 01-what-are-relational-databases.md | Relational database fundamentals, when to use them |
| 2 | 02-rdbms-benefits-limitations.md | Strengths, weaknesses, and trade-offs |
| 3 | 03-object-model.md | Queries, data types, rows, columns, tables, schemas, databases |
| 4 | 04-relational-model.md | Domains, attributes, tuples, relations, constraints, NULL |
| 5 | 05-postgresql-overview.md | PostgreSQL vs other RDBMS (MySQL, SQL Server) and vs NoSQL |
| 6 | 06-high-level-concepts.md | ACID, MVCC, Transactions, Write-Ahead Log, Query Processing |

### Module 02: Installation & Setup
**Est. Time:** 1–2 hours | **Capstone Milestone:** PostgreSQL running, `storeforge` database created

| # | File | Concept |
|---|------|---------|
| 1 | 01-installation.md | Using Docker, package managers, cloud deployment |
| 2 | 02-psql-basics.md | Connecting with `psql`, essential commands, meta-commands |
| 3 | 03-managing-postgres.md | Using systemd, pg_ctl, pg_ctlcluster |
| 4 | 04-gui-tools.md | pgAdmin, DBeaver — visual management |

### Module 03: SQL Fundamentals
**Est. Time:** 5–7 hours | **Capstone Milestone:** Core StoreForge schema created with sample data

| # | File | Concept |
|---|------|---------|
| 1 | 01-ddl-schemas-tables.md | CREATE, ALTER, DROP for schemas and tables |
| 2 | 02-data-types.md | PostgreSQL data types (numeric, text, temporal, boolean, UUID, JSONB, arrays) |
| 3 | 03-querying-data.md | SELECT, WHERE, ORDER BY, LIMIT, OFFSET |
| 4 | 04-filtering-data.md | Operators, LIKE/ILIKE, IN, BETWEEN, IS NULL, pattern matching |
| 5 | 05-modifying-data.md | INSERT, UPDATE, DELETE, UPSERT (ON CONFLICT), RETURNING |
| 6 | 06-joining-tables.md | INNER, LEFT, RIGHT, FULL, CROSS, SELF joins |
| 7 | 07-import-export-copy.md | COPY and \copy for bulk data import/export |

### Module 04: Advanced SQL
**Est. Time:** 5–7 hours | **Capstone Milestone:** Complex analytics queries for StoreForge

| # | File | Concept |
|---|------|---------|
| 1 | 01-transactions.md | BEGIN, COMMIT, ROLLBACK, SAVEPOINT, isolation levels |
| 2 | 02-subqueries.md | Scalar, row, table subqueries; correlated subqueries; EXISTS |
| 3 | 03-grouping-aggregation.md | GROUP BY, HAVING, COUNT, SUM, AVG, aggregate functions |
| 4 | 04-common-table-expressions.md | WITH queries, recursive CTE (category hierarchies) |
| 5 | 05-window-functions.md | ROW_NUMBER, RANK, DENSE_RANK, LAG, LEAD, PARTITION BY, frames |
| 6 | 06-set-operations.md | UNION, INTERSECT, EXCEPT |
| 7 | 07-lateral-joins.md | LATERAL subqueries, top-N-per-group patterns |

### Module 05: Database Design & Normalization
**Est. Time:** 4–6 hours | **Capstone Milestone:** Fully normalized StoreForge schema with constraints and views

| # | File | Concept |
|---|------|---------|
| 1 | 01-normalization.md | 1NF through 5NF with practical examples |
| 2 | 02-constraints.md | PRIMARY KEY, FOREIGN KEY, UNIQUE, CHECK, NOT NULL, EXCLUDE |
| 3 | 03-schema-design-patterns.md | EAV, polymorphic associations, temporal tables, audit tables |
| 4 | 04-schema-anti-patterns.md | God tables, implicit columns, overusing NULLs, EAV abuse |
| 5 | 05-views-materialized-views.md | CREATE VIEW, materialized views, REFRESH, indexing mat views |

### Module 06: PL/pgSQL & Programmability
**Est. Time:** 4–5 hours | **Capstone Milestone:** StoreForge with stored functions, triggers, audit logging

| # | File | Concept |
|---|------|---------|
| 1 | 01-plpgsql-basics.md | PL/pgSQL syntax, variables, control flow, RAISE |
| 2 | 02-functions.md | CREATE FUNCTION, parameters, return types, RETURNS TABLE |
| 3 | 03-procedures.md | CREATE PROCEDURE, transaction control in procedures |
| 4 | 04-triggers.md | BEFORE/AFTER triggers, row/statement level, trigger functions |
| 5 | 05-advanced-functions.md | Custom aggregates, recursive CTE in functions, dynamic SQL |

### Module 07: Security & Access Control
**Est. Time:** 3–4 hours | **Capstone Milestone:** Role-based access with RLS for StoreForge

| # | File | Concept |
|---|------|---------|
| 1 | 01-roles-and-users.md | CREATE ROLE, role attributes, role membership, inheritance |
| 2 | 02-authentication.md | pg_hba.conf, authentication methods (md5, scram-sha-256, cert) |
| 3 | 03-privileges.md | GRANT, REVOKE, default privileges, schema-level permissions |
| 4 | 04-row-level-security.md | CREATE POLICY, ENABLE RLS, per-role policies |
| 5 | 05-ssl-encryption.md | SSL certificate setup, enforcing encrypted connections |

### Module 08: Performance Tuning & Indexing
**Est. Time:** 5–7 hours | **Capstone Milestone:** Optimized StoreForge with index strategy and tuned config

| # | File | Concept |
|---|------|---------|
| 1 | 01-postgresql-conf.md | Key configuration parameters, tuning methodology |
| 2 | 02-resource-usage.md | shared_buffers, work_mem, effective_cache_size, maintenance_work_mem |
| 3 | 03-explain-analyze.md | EXPLAIN, EXPLAIN ANALYZE, reading query plans, common patterns |
| 4 | 04-indexes.md | B-Tree, Hash, GiST, GIN, SP-GiST, BRIN — when to use each |
| 5 | 05-vacuums.md | Autovacuum, dead tuples, bloat, vacuum tuning |
| 6 | 06-query-optimization.md | Query patterns, anti-patterns, rewriting for performance |
| 7 | 07-partitioning.md | Range, list, hash partitioning; partition pruning |

### Module 09: Infrastructure & Operations
**Est. Time:** 5–7 hours | **Capstone Milestone:** Production-ready StoreForge with backup, replication, monitoring

| # | File | Concept |
|---|------|---------|
| 1 | 01-backup-recovery.md | pg_dump, pg_dumpall, pg_restore, pg_basebackup, WAL archiving |
| 2 | 02-streaming-replication.md | Primary/replica setup, synchronous vs asynchronous |
| 3 | 03-logical-replication.md | Publications, subscriptions, use cases |
| 4 | 04-connection-pooling.md | PgBouncer setup, pool modes, configuration |
| 5 | 05-monitoring.md | pg_stat_activity, pg_stat_statements, pg_stat_user_tables, Prometheus |
| 6 | 06-high-availability.md | Patroni, failover, load balancing with HAProxy |
| 7 | 07-upgrade-procedures.md | pg_upgrade, logical replication for upgrades |

### Module 10: Capstone Integration
**Est. Time:** 3–4 hours | **Capstone Milestone:** Full production-ready StoreForge

README.md only — ties everything together:
- Full schema DDL script
- Migration strategy
- Backup schedule
- Monitoring dashboard setup
- Performance benchmark
- Production deployment checklist

---

## Steps

### Phase 1: Scaffold (Steps 1–3) — *parallel*

1. **Create top-level README.md** at `/postgres/README.md`
   - Overview, how to use, prerequisites (PostgreSQL 15+, Docker, psql, any SQL GUI)
   - Learning Path table (10 modules + capstone, time estimates, milestones)
   - StoreForge Domain Model diagram + entity table
   - Module-by-module build table
   - Quick Start (Docker command, psql connection)
   - Comparison table: MySQL → PostgreSQL mental model (AUTO_INCREMENT ↔ SERIAL/IDENTITY, SHOW TABLES ↔ \dt, etc.)
   - Project structure tree

2. **Create all module directories** — 10 module folders + exercises subdirectories
   ```
   postgres/modules/01-introduction/exercises/
   postgres/modules/02-installation-setup/exercises/
   postgres/modules/03-sql-fundamentals/exercises/
   postgres/modules/04-advanced-sql/exercises/
   postgres/modules/05-database-design/exercises/
   postgres/modules/06-plpgsql/exercises/
   postgres/modules/07-security/exercises/
   postgres/modules/08-performance-tuning/exercises/
   postgres/modules/09-infrastructure/exercises/
   postgres/modules/10-capstone-integration/
   ```

3. **Create capstone directory** at `postgres/capstone/storeforge/`
   - `schema.sql` — full DDL (empty initially, grows with modules)
   - `seed.sql` — sample data
   - `README.md` — capstone overview

### Phase 2: Module READMEs (Step 4) — *all parallel*

4. **Create all 10 module README.md files** following the Spring Boot pattern:
   - Overview paragraph
   - Learning Objectives (checkbox list)
   - Topics table (# | File | Concept)
   - Estimated Time
   - Prerequisites
   - Capstone Milestone

### Phase 3: Lesson Files (Steps 5–14) — *modules can be written in parallel*

5. **Module 01 lessons** (6 files) — conceptual, mostly text + diagrams
6. **Module 02 lessons** (4 files) — hands-on installation, psql tutorial
7. **Module 03 lessons** (7 files) — SQL fundamentals with StoreForge examples
8. **Module 04 lessons** (7 files) — advanced SQL with analytics queries
9. **Module 05 lessons** (5 files) — database design theory + StoreForge normalization
10. **Module 06 lessons** (5 files) — PL/pgSQL with inventory/audit triggers
11. **Module 07 lessons** (5 files) — roles, pg_hba.conf, RLS examples
12. **Module 08 lessons** (7 files) — EXPLAIN, indexes, vacuum, partitioning
13. **Module 09 lessons** (7 files) — backup, replication, PgBouncer, monitoring
14. **Module 10 README.md** — capstone integration guide

### Phase 4: Exercises (Steps 15–23) — *parallel with Phase 3*

15. **Module 01 exercises** — conceptual (diagram ACID scenario, identify normal forms)
16. **Module 02 exercises** — setup verification, psql exploration
17. **Module 03 exercises** — create StoreForge schema, insert data, basic queries
18. **Module 04 exercises** — analytics queries (top products, revenue by month, category hierarchy)
19. **Module 05 exercises** — normalize a denormalized table, add constraints, create views
20. **Module 06 exercises** — write inventory trigger, audit function, order processing procedure
21. **Module 07 exercises** — create roles, set up RLS policies, test access
22. **Module 08 exercises** — EXPLAIN queries, create indexes, measure improvement, partition orders
23. **Module 09 exercises** — pg_dump backup/restore, set up replica, configure PgBouncer

### Phase 5: Capstone Files (Step 24)

24. **Create capstone SQL files** — *depends on Steps 7–13*
    - `capstone/storeforge/schema.sql` — complete DDL
    - `capstone/storeforge/seed.sql` — realistic sample data
    - `capstone/storeforge/functions.sql` — all stored functions/triggers
    - `capstone/storeforge/security.sql` — roles, RLS policies
    - `capstone/storeforge/indexes.sql` — optimized index set
    - `capstone/storeforge/README.md` — setup and usage guide

---

## Lesson Format (per file)

Each lesson file follows this structure (mirroring Spring Boot tutorial):
1. **Concept header** — definition + why it matters
2. **Real-world analogy** or comparison (MySQL/general SQL equivalents where relevant)
3. **Core content** — theory with SQL examples using StoreForge tables
4. **Diagrams/tables** — ASCII diagrams, comparison tables
5. **Try It Yourself** — hands-on exercise with SQL to run
6. **`<details>` callout** — expected output / solution
7. **Capstone Connection** — how this applies to StoreForge

---

## Relevant Files

- `postgres/README.md` — top-level tutorial entry point (create new)
- `postgres/modules/01-introduction/README.md` through `10-capstone-integration/README.md` — module overviews
- `postgres/modules/*/exercises/README.md` — exercise files per module
- `postgres/modules/01-introduction/01-what-are-relational-databases.md` etc. — 53 lesson files total
- `postgres/capstone/storeforge/` — capstone SQL files (schema.sql, seed.sql, etc.)
- **Reference:** `spring-boot/modules/*/README.md` — reuse structure pattern
- **Reference:** `spring-boot/modules/*/exercises/README.md` — reuse exercise pattern

---

## Verification

1. Every module README has: Overview, Learning Objectives, Topics table, Time estimate, Prerequisites, Capstone Milestone
2. Every lesson file has: Concept header, SQL examples using StoreForge, Try It Yourself section, `<details>` solution, Capstone Connection
3. Every exercises/README.md has: 4-6 numbered exercises with SQL commands and expected output
4. `capstone/storeforge/schema.sql` runs cleanly on PostgreSQL 15+ and creates all tables
5. `capstone/storeforge/seed.sql` populates realistic sample data
6. All internal links between modules are valid (relative paths)
7. Module progression is coherent (no forward references to unlearned concepts)

---

## Decisions

- **Target audience:** General application developers with basic SQL familiarity (not specific to MySQL/MongoDB)
- **Capstone:** StoreForge (e-commerce) — naturally exercises joins, transactions, JSONB, partitioning, full-text search, window functions, recursive CTE, triggers, RLS
- **Infrastructure depth:** Full coverage including backup, replication, PgBouncer, monitoring, HA
- **Analogies:** MySQL ↔ PostgreSQL comparisons where syntax differs; general SQL references where universal
- **No application code:** This is a pure PostgreSQL/SQL tutorial — no application language (Java, Python, etc.) code
- **Scope exclusions:** No Kubernetes deployment details, no specific cloud provider setup (mentioned but not walked through), no MongoDB/NoSQL migration guides

---

## File Count Summary

| Category | Count |
|----------|-------|
| Top-level README | 1 |
| Module READMEs | 10 |
| Lesson files | 53 |
| Exercise READMEs | 9 (Module 10 has no separate exercises) |
| Capstone files | 6 |
| **Total files** | **79** |
