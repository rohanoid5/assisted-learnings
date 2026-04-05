# Module 07 — Security & Access Control

## Overview

A PostgreSQL database without proper security configuration is a liability. This module covers the entire security surface of PostgreSQL: authentication (who can connect and how), authorization (what they can do once connected), row-level security (what rows they can see), and transport encryption.

PostgreSQL's security model is both powerful and fine-grained. You'll create separate roles for different application components and enforce that a customer-facing API can only see its own data — without any application-level filtering.

---

## Learning Objectives

By the end of this module, you should be able to:

- [ ] Create roles with specific attributes (`LOGIN`, `SUPERUSER`, `CREATEROLE`, `REPLICATION`)
- [ ] Configure `pg_hba.conf` to control which users can connect from which hosts
- [ ] Grant and revoke object-level privileges (`SELECT`, `INSERT`, `UPDATE`, `DELETE`, `EXECUTE`)
- [ ] Set default privileges for future objects in a schema
- [ ] Enable Row-Level Security and write `CREATE POLICY` statements
- [ ] Configure SSL/TLS for encrypted client connections

---

## Topics

| # | File | Concept |
|---|------|---------|
| 1 | [01-roles-and-users.md](01-roles-and-users.md) | `CREATE ROLE`, role attributes, role membership, inheritance, `SET ROLE` |
| 2 | [02-authentication.md](02-authentication.md) | `pg_hba.conf` structure, authentication methods: trust, md5, scram-sha-256, cert |
| 3 | [03-privileges.md](03-privileges.md) | `GRANT`/`REVOKE`, object privileges, schema privileges, `ALTER DEFAULT PRIVILEGES` |
| 4 | [04-row-level-security.md](04-row-level-security.md) | `ENABLE ROW LEVEL SECURITY`, `CREATE POLICY`, `USING` vs `WITH CHECK`, bypass |
| 5 | [05-ssl-encryption.md](05-ssl-encryption.md) | SSL certificate configuration, `sslmode` in connection strings, enforcing encryption |

---

## Estimated Time

**3–4 hours** (including exercises)

---

## Prerequisites

- Modules 03–06 completed — full StoreForge schema in place

---

## Capstone Milestone

By the end of this module you should have configured StoreForge with:

1. **Three roles:**
   - `storeforge_admin` — full access (for DBA operations)
   - `storeforge_api` — can INSERT/UPDATE/SELECT on application tables, no DELETE on orders
   - `storeforge_readonly` — SELECT only, for reporting/analytics pipelines
2. **Row-Level Security on `order` and `review`** — customers can only see their own records
3. **`pg_hba.conf`** updated to require `scram-sha-256` password auth for all non-superuser logins on all connections
4. **SSL enabled** in the Docker container and verified with `\conninfo` in psql
