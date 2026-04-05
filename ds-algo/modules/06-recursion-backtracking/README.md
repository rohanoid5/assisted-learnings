# Module 06 — Recursion & Backtracking

## Overview

Recursion is the foundation of a large fraction of interview problems — trees, graphs, divide-and-conquer, and dynamic programming all rely on it. Backtracking is a disciplined form of recursion that explores a decision tree while pruning dead branches early. This module builds both skills from the ground up.

---

## Learning Objectives

By the end of this module you will be able to:

- [ ] Identify the **base case** and **recursive case** in any recursive function
- [ ] Trace **call stack frames** for recursive algorithms
- [ ] Analyze recursion with **recurrence relations** and the Master Theorem
- [ ] Apply the **backtracking framework** (choose → explore → unchoose) to any constraint satisfaction problem
- [ ] Generate **permutations, combinations, and subsets** using decision trees
- [ ] Solve classic constraint satisfaction problems: N-Queens, Sudoku Solver

---

## Topics

| # | File | Concept |
|---|------|---------|
| 1 | [01-recursion-fundamentals.md](01-recursion-fundamentals.md) | Call stack, base case, recursive case, stack overflow |
| 2 | [02-recurrence-relations.md](02-recurrence-relations.md) | Recurrences, Master Theorem, recursion tree method |
| 3 | [03-backtracking-framework.md](03-backtracking-framework.md) | Choose-Explore-Unchoose, decision tree, pruning |
| 4 | [04-subsets-permutations.md](04-subsets-permutations.md) | Subsets, permutations, combinations, phone number letters |
| 5 | [05-constraint-satisfaction.md](05-constraint-satisfaction.md) | N-Queens, Sudoku Solver, Word Search |
| Exercises | [Exercises](exercises/README.md) | Generate Parentheses, Subsets, Permutations, N-Queens, Word Search |

---

## Estimated Time

**4–5 hours** (including exercises)

---

## Prerequisites

- Modules 01–04: complexity analysis and Java fundamentals
- Understanding of the call stack (from any prior programming experience)

---

## Capstone Milestone

By the end of this module, add to AlgoForge:
- `problems/backtracking/GenerateParentheses.java`
- `problems/backtracking/Subsets.java`
- `problems/backtracking/Permutations.java`
- `problems/backtracking/NQueens.java`
- `problems/backtracking/WordSearch.java`
