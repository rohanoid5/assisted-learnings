# Module 01 — Introduction to PostgreSQL

## Overview

Before writing a single SQL statement, it's worth understanding *what* PostgreSQL is at a conceptual level — and *why* relational databases are designed the way they are. This module builds the mental models you'll rely on for every design decision throughout the tutorial.

You'll learn what makes relational databases fundamentally different from other data stores, how PostgreSQL's object-relational model extends standard SQL, and what concepts like ACID and MVCC mean in practice (not just in theory).

---

## Learning Objectives

By the end of this module, you should be able to:

- [ ] Explain what a relational database is and when to use one vs. alternatives
- [ ] Describe PostgreSQL's object-relational model (tables, schemas, databases)
- [ ] Define relations, tuples, attributes, domains, and constraints in relational theory terms
- [ ] Explain ACID properties and identify which property prevents which class of problems
- [ ] Describe what MVCC is and why PostgreSQL uses it instead of locking for reads
- [ ] Explain the purpose of the Write-Ahead Log (WAL)
- [ ] Position PostgreSQL against MySQL, SQL Server, and document databases like MongoDB

---

## Topics

| # | File | Concept |
|---|------|---------|
| 1 | [01-what-are-relational-databases.md](01-what-are-relational-databases.md) | Relational database fundamentals, when to use them |
| 2 | [02-rdbms-benefits-limitations.md](02-rdbms-benefits-limitations.md) | Strengths, weaknesses, and trade-offs |
| 3 | [03-object-model.md](03-object-model.md) | Queries, data types, rows, columns, tables, schemas, databases |
| 4 | [04-relational-model.md](04-relational-model.md) | Domains, attributes, tuples, relations, constraints, NULL |
| 5 | [05-postgresql-overview.md](05-postgresql-overview.md) | PostgreSQL vs other RDBMS (MySQL, SQL Server) and vs NoSQL |
| 6 | [06-high-level-concepts.md](06-high-level-concepts.md) | ACID, MVCC, Transactions, Write-Ahead Log, Query Processing |

---

## Estimated Time

**3–4 hours** (including exercises)

---

## Prerequisites

- Basic understanding of what a database is
- No prior PostgreSQL experience required
- PostgreSQL does not need to be installed yet (Module 02 covers that)

---

## Capstone Milestone

This is a **conceptual module** — no SQL is written yet. By the end, you should be able to:

1. Sketch the StoreForge entity-relationship diagram from memory
2. Explain why the `order` table needs a foreign key to `customer` rather than embedding customer data
3. Identify which ACID property guarantees that a payment and inventory decrement either both happen or neither does
4. Explain why two customers browsing the same product page simultaneously don't block each other (MVCC)
