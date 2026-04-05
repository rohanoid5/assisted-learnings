# Module 06 — PL/pgSQL & Programmability

## Overview

SQL is declarative — you describe *what* you want, not *how* to get it. PL/pgSQL gives you the procedural programming constructs (variables, loops, conditionals, error handling) to write logic that lives inside the database.

This module teaches you when to push logic into the database (triggers enforcing invariants, functions encapsulating complex calculations) and when not to (application-level business logic belongs in your application). Done correctly, database-side programmability makes your schema self-enforcing and dramatically reduces the surface area for data corruption.

---

## Learning Objectives

By the end of this module, you should be able to:

- [ ] Write PL/pgSQL blocks with variables, control flow (`IF`, `LOOP`, `FOR`), and error handling (`EXCEPTION`)
- [ ] Create functions that accept parameters, return scalars, and return table result sets
- [ ] Create stored procedures with explicit transaction control
- [ ] Write `BEFORE` and `AFTER` triggers at row and statement level
- [ ] Build trigger functions that enforce business invariants (inventory checks, audit logging)
- [ ] Implement dynamic SQL with `EXECUTE` safely (avoiding SQL injection)

---

## Topics

| # | File | Concept |
|---|------|---------|
| 1 | [01-plpgsql-basics.md](01-plpgsql-basics.md) | Block structure, variables, `IF/ELSIF/ELSE`, `LOOP`, `FOR`, `RAISE`, `EXCEPTION` |
| 2 | [02-functions.md](02-functions.md) | `CREATE FUNCTION`, parameter modes, return types, `RETURNS TABLE`, `RETURNS SETOF` |
| 3 | [03-procedures.md](03-procedures.md) | `CREATE PROCEDURE`, `CALL`, transaction control inside procedures |
| 4 | [04-triggers.md](04-triggers.md) | `BEFORE`/`AFTER`/`INSTEAD OF`, row/statement level, `NEW`/`OLD`, `CREATE TRIGGER` |
| 5 | [05-advanced-functions.md](05-advanced-functions.md) | Custom aggregates, dynamic SQL with `EXECUTE`, recursive functions |

---

## Estimated Time

**4–5 hours** (including exercises)

---

## Prerequisites

- Modules 03–05 completed — solid understanding of StoreForge schema and constraints

---

## Capstone Milestone

By the end of this module you should have added to StoreForge:

1. **Inventory trigger** — `BEFORE INSERT OR UPDATE` on `order_item` that decrements `product.stock_quantity` and raises an exception if stock is insufficient
2. **Audit log trigger** — `AFTER INSERT OR UPDATE OR DELETE` on `product` that writes changes to `audit_log`
3. **`place_order` function** — accepts customer_id and a JSON cart, creates the order and order_items in a single transaction
4. **`product_search` function** — full-text search on products returning a ranked result set
5. **`calculate_order_total` function** — recalculates and updates `order.total_amount` from its order_items
