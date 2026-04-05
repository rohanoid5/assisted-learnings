# Module 11 — Dynamic Programming

## Overview

Dynamic Programming (DP) is the single most important topic for FAANG interviews. It builds on recursive thinking but eliminates redundant computation through memoization or tabulation.

> "Recognize the optimal substructure. Define the state. Write the recurrence." — DP in three steps.

## Topics

| # | Topic | Key Concepts | Time |
|---|-------|-------------|------|
| 1 | DP Fundamentals | Memoization vs tabulation, state design | 1.5h |
| 2 | 1D DP | Climbing stairs, house robber, coin change | 2h |
| 3 | 2D DP | Edit distance, LCS, unique paths, grid DP | 2h |
| 4 | Knapsack DP | 0/1, unbounded, subset sum, partitioning | 2h |
| 5 | Interval DP | Burst balloons, palindrome partition, matrix chain | 2h |
| 6 | Bitmask DP | TSP, shortest path visiting all nodes | 1.5h |
| 7 | DP Patterns | Pattern recognition, when to use which DP | 1h |

## Prerequisites

- Module 06 — Recursion & Backtracking (understand recursive decomposition)
- Module 02 — Arrays & Strings (array manipulation)

## Learning Path

```
DP Fundamentals
       │
  ┌────┴─────┐
  ▼          ▼
1D DP      2D DP
  │          │
  └────┬─────┘
       ▼
  Knapsack DP
       │
  ┌────┴──────┐
  ▼           ▼
Interval DP  Bitmask DP
       │
       ▼
  DP Patterns
```

## Capstone Milestone

After this module, AlgoForge gains:
- `DPProblems.java` — canonical implementations of 20+ DP patterns
- Full solutions for LC #70, #198, #322, #300, #1143, #72, #416, #312, #847

## Module Completion Checklist

- [ ] 11.1 — Can explain memoization vs tabulation tradeoffs
- [ ] 11.2 — Can solve 1D DP problems on first attempt
- [ ] 11.3 — Can set up 2D DP state table
- [ ] 11.4 — Can reduce knapsack space from O(n×W) to O(W)
- [ ] 11.5 — Can identify when to use interval DP (gaps/ranges)
- [ ] 11.6 — Can write bitmask DP for small n (≤20)
- [ ] 11.7 — Can identify DP pattern from problem description
