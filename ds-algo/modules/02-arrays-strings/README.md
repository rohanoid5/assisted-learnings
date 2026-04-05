# Module 2 — Arrays & Strings

## Overview

Arrays are the most fundamental data structure in programming and the most common substrate for interview problems. Most "easy" and many "medium" problems reduce to recognizing one of five patterns: two pointer, sliding window, prefix sum, matrix traversal, or simple frequency counting. This module teaches you to see those patterns instantly.

---

## Learning Objectives

By the end of this module you will be able to:

- [ ] Explain Java array vs `ArrayList` trade-offs and know when to use each
- [ ] Implement the two-pointer technique for both converging and same-direction variants
- [ ] Implement fixed-size and variable-size sliding windows with a HashMap
- [ ] Build prefix sum arrays and answer range queries in O(1)
- [ ] Traverse 2D matrices in spiral, diagonal, and BFS/DFS order
- [ ] Solve any "subarray sum" problem using prefix sums + HashMap in O(n)

---

## Topics

| # | File | Concept |
|---|------|---------|
| 1 | [01-array-fundamentals.md](01-array-fundamentals.md) | Memory layout, ArrayList internals, amortized resizing |
| 2 | [02-string-manipulation.md](02-string-manipulation.md) | Java String immutability, StringBuilder, common operations |
| 3 | [03-two-pointer-technique.md](03-two-pointer-technique.md) | Converging pointers, same-direction pointers, pattern recognition |
| 4 | [04-sliding-window.md](04-sliding-window.md) | Fixed-size window, variable-size window, shrink condition |
| 5 | [05-prefix-sum.md](05-prefix-sum.md) | Prefix sum array, range queries, difference array, 2D prefix sum |
| 6 | [06-matrix-traversal.md](06-matrix-traversal.md) | Row/col traversal, spiral order, diagonal, BFS/DFS on grids |

---

## Estimated Time

**4–5 hours** (including exercises)

---

## Prerequisites

- Module 1 complete (Big-O analysis)
- Java arrays, `ArrayList`, `String`, `StringBuilder`
- Basic `HashMap` usage

---

## Capstone Milestone

By the end of this module you will have added to AlgoForge:
- `DynamicArray<T>` — a hand-rolled resizable array with amortized O(1) add
- 5 problem solutions in `src/main/java/com/algoforge/problems/arrays/`
- Tests for all of the above passing under `mvn test`

See [exercises/README.md](exercises/README.md) for hands-on tasks.
