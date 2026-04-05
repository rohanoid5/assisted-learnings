# Module 03 — SQL Fundamentals

## Overview

This module covers the SQL foundations you need to build and populate the StoreForge schema. Even if you've used SQL before, PostgreSQL has important differences and extensions worth knowing: rich data types (UUID, JSONB, arrays), powerful `RETURNING` clauses, `ON CONFLICT` upserts, and the `COPY` command for bulk loading.

By the end you'll have a fully created, fully populated StoreForge schema running in your local PostgreSQL instance.

---

## Learning Objectives

By the end of this module, you should be able to:

- [ ] Create schemas, tables, and alter their structure with DDL statements
- [ ] Choose the right PostgreSQL data type for any attribute (including JSONB and arrays)
- [ ] Query data with `SELECT`, filtering, ordering, limiting, and pagination
- [ ] Filter with `LIKE/ILIKE`, `IN`, `BETWEEN`, `IS NULL`, and regular expressions
- [ ] Insert, update, delete, and upsert data using `ON CONFLICT`
- [ ] Use `RETURNING` to get data back from write operations
- [ ] Write `INNER`, `LEFT`, `RIGHT`, `FULL`, `CROSS`, and self-joins
- [ ] Bulk import and export data using `COPY` and `\copy`

---

## Topics

| # | File | Concept |
|---|------|---------|
| 1 | [01-ddl-schemas-tables.md](01-ddl-schemas-tables.md) | CREATE, ALTER, DROP for schemas and tables; column definitions |
| 2 | [02-data-types.md](02-data-types.md) | Numeric, text, temporal, boolean, UUID, JSONB, arrays, enums |
| 3 | [03-querying-data.md](03-querying-data.md) | SELECT, WHERE, ORDER BY, LIMIT, OFFSET — with StoreForge examples |
| 4 | [04-filtering-data.md](04-filtering-data.md) | Operators, LIKE/ILIKE, IN, BETWEEN, IS NULL, pattern matching, SIMILAR TO |
| 5 | [05-modifying-data.md](05-modifying-data.md) | INSERT, UPDATE, DELETE, UPSERT (ON CONFLICT), RETURNING |
| 6 | [06-joining-tables.md](06-joining-tables.md) | INNER, LEFT, RIGHT, FULL, CROSS, SELF joins with visual diagrams |
| 7 | [07-import-export-copy.md](07-import-export-copy.md) | COPY and `\copy` for bulk import/export, CSV handling |

---

## Estimated Time

**5–7 hours** (including exercises)

---

## Prerequisites

- Module 02 completed — PostgreSQL running and `storeforge_dev` database created
- Familiarity with basic SQL concepts (tables, rows, columns)

---

## Capstone Milestone

By the end of this module you should have:

1. All 7 StoreForge tables created (`customer`, `category`, `product`, `address`, `order`, `order_item`, `review`)
2. Appropriate data types chosen for every column (using `UUID`, `NUMERIC`, `JSONB`, `TEXT`, `TIMESTAMPTZ`, enums)
3. At least 4 categories, 20 products, 10 customers, 30 orders, and 60 order_items inserted
4. Verified data with `SELECT` queries, filtered product lists, and customer order history using JOINs
5. (Optional) Loaded sample data using `\copy` from a CSV file
