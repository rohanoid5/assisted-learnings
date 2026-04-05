# Module 04 — Advanced SQL

## Overview

Module 03 gave you the vocabulary. This module gives you the grammar for complex data problems. You'll learn how to write queries that would require application-side loops in naive implementations — and push that work into the database where it belongs.

The StoreForge capstone exercises in this module build a full analytics layer: monthly revenue reports, top-selling products, customer lifetime value, category hierarchy traversal, and rolling averages. All in pure SQL.

---

## Learning Objectives

By the end of this module, you should be able to:

- [ ] Manage transactions with `BEGIN`, `COMMIT`, `ROLLBACK`, and `SAVEPOINT`
- [ ] Explain isolation levels and the anomalies they prevent (dirty reads, non-repeatable reads, phantoms)
- [ ] Write scalar, row, and table subqueries; apply correlated subqueries with `EXISTS`
- [ ] Aggregate data with `GROUP BY`, `HAVING`, and aggregate functions
- [ ] Write CTEs with `WITH` and recursive CTEs for hierarchical data
- [ ] Apply window functions (`ROW_NUMBER`, `RANK`, `LAG`, `LEAD`, `SUM OVER`) for running totals and rankings
- [ ] Combine result sets with `UNION`, `INTERSECT`, and `EXCEPT`
- [ ] Use `LATERAL` joins for top-N-per-group and computed subqueries

---

## Topics

| # | File | Concept |
|---|------|---------|
| 1 | [01-transactions.md](01-transactions.md) | BEGIN, COMMIT, ROLLBACK, SAVEPOINT, isolation levels, locking basics |
| 2 | [02-subqueries.md](02-subqueries.md) | Scalar, row, table subqueries; correlated subqueries; EXISTS / NOT EXISTS |
| 3 | [03-grouping-aggregation.md](03-grouping-aggregation.md) | GROUP BY, HAVING, COUNT, SUM, AVG, MIN, MAX, FILTER, GROUPING SETS |
| 4 | [04-common-table-expressions.md](04-common-table-expressions.md) | WITH queries (non-recursive + recursive CTE for category hierarchy) |
| 5 | [05-window-functions.md](05-window-functions.md) | ROW_NUMBER, RANK, DENSE_RANK, LAG, LEAD, NTILE, PARTITION BY, frames |
| 6 | [06-set-operations.md](06-set-operations.md) | UNION, UNION ALL, INTERSECT, EXCEPT — with practical StoreForge use cases |
| 7 | [07-lateral-joins.md](07-lateral-joins.md) | LATERAL subqueries, top-N-per-group, dependent subquery optimization |

---

## Estimated Time

**5–7 hours** (including exercises)

---

## Prerequisites

- Module 03 completed — StoreForge schema created and populated with sample data

---

## Capstone Milestone

By the end of this module you should be able to run these queries against StoreForge:

1. **Monthly revenue report** — total revenue and order count per month, year-over-year comparison
2. **Top-10 products by revenue** — with category name, using window functions
3. **Category hierarchy** — full tree from root to leaf using recursive CTE
4. **Customer cohort analysis** — customers grouped by first-order month, with repeat purchase rates
5. **Running total** — cumulative revenue per day using `SUM() OVER (ORDER BY ...)`
6. **Rank products within category** — `RANK()` with `PARTITION BY category_id`
