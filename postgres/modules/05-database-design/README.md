# Module 05 — Database Design & Normalization

## Overview

A schema that works today can become a maintenance nightmare tomorrow if it wasn't designed well from the start. This module teaches the principles behind good relational schema design — normalization, constraint engineering, and the patterns that make schemas resilient to change.

You'll also learn to recognize and avoid the anti-patterns that plague real production databases: God tables, implicit schemas stored in string columns, overusing NULLs, and EAV abuse.

Finally, you'll create views and materialized views that simplify query interfaces without duplicating data.

---

## Learning Objectives

By the end of this module, you should be able to:

- [ ] Normalize a denormalized table through 1NF, 2NF, 3NF, and BCNF
- [ ] Define and enforce PRIMARY KEY, FOREIGN KEY, UNIQUE, CHECK, NOT NULL, and EXCLUDE constraints
- [ ] Recognize and refactor common anti-patterns (God tables, EAV abuse, implicit columns)
- [ ] Apply design patterns: audit tables, temporal tables, polymorphic associations, soft deletion
- [ ] Create views and understand their limitations
- [ ] Create materialized views, refresh them, and create indexes on them

---

## Topics

| # | File | Concept |
|---|------|---------|
| 1 | [01-normalization.md](01-normalization.md) | 1NF through BCNF/4NF with StoreForge examples, denormalization trade-offs |
| 2 | [02-constraints.md](02-constraints.md) | PRIMARY KEY, FOREIGN KEY, UNIQUE, CHECK, NOT NULL, EXCLUDE constraints |
| 3 | [03-schema-design-patterns.md](03-schema-design-patterns.md) | Audit tables, temporal tables, soft deletion, polymorphic associations, JSONB for sparse attributes |
| 4 | [04-schema-anti-patterns.md](04-schema-anti-patterns.md) | God tables, implicit columns, EAV abuse, overusing NULLs, premature denormalization |
| 5 | [05-views-materialized-views.md](05-views-materialized-views.md) | CREATE VIEW, updatable views, CREATE MATERIALIZED VIEW, REFRESH, indexing |

---

## Estimated Time

**4–6 hours** (including exercises)

---

## Prerequisites

- Modules 03–04 completed — familiarity with StoreForge schema and JOIN-based queries

---

## Capstone Milestone

By the end of this module you should have:

1. Reviewed the StoreForge schema against normal forms and documented any deviations (documenting *why* you're violating a normal form is often the right call)
2. Added `NOT NULL`, `CHECK`, and `UNIQUE` constraints to the schema where missing
3. Created an `audit_log` table and soft-delete pattern on `product`
4. Created a `product_summary` view combining product, category, and average rating
5. Created a `monthly_revenue` materialized view and indexed it on `month`
