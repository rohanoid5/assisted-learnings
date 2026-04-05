# Module 10 — Advanced Data Structures

## Overview

This module covers the data structures that separate good engineers from great ones in interviews. Tries, Segment Trees, Fenwick Trees, and Union-Find each unlock entire categories of problems that would otherwise require O(n²) or worse solutions. These appear in hard LC problems, competitive programming, and system design discussions.

---

## Learning Objectives

By the end of this module you will be able to:

- [ ] Implement a Trie (prefix tree) with insert, search, startsWith
- [ ] Use Trie for word dictionary, autocomplete, and prefix matching
- [ ] Implement a Segment Tree for range queries and point updates
- [ ] Implement a Fenwick Tree (BIT) for prefix sum queries
- [ ] Implement Union-Find with path compression and union by rank
- [ ] Apply Union-Find to connected component, cycle detection, and MST problems
- [ ] Understand Suffix Arrays conceptually and their use for pattern matching

---

## Topics

| # | File | Concept |
|---|------|---------|
| 1 | [01-trie.md](01-trie.md) | Prefix tree, insert/search/startsWith, word dictionary |
| 2 | [02-segment-tree.md](02-segment-tree.md) | Range max/min/sum queries, point updates |
| 3 | [03-fenwick-tree.md](03-fenwick-tree.md) | Binary Indexed Tree, prefix sums, range updates |
| 4 | [04-union-find.md](04-union-find.md) | DSU, path compression, union by rank |
| 5 | [05-suffix-arrays.md](05-suffix-arrays.md) | Suffix arrays, LCP array, pattern matching |
| 6 | [06-skip-list.md](06-skip-list.md) | Probabilistic data structure, O(log n) ops |
| Exercises | [Exercises](exercises/README.md) | Word Search II, Range Sum, Accounts Merge |

---

## Estimated Time

**6–7 hours** (including exercises)

---

## Prerequisites

- Module 01: complexity analysis
- Module 06: recursion (segment tree is recursive)
- Module 09: Union-Find introduced in graph context

---

## Capstone Milestone

By the end of this module, add to AlgoForge:
- `datastructures/advanced/Trie.java`
- `datastructures/advanced/SegmentTree.java`
- `datastructures/advanced/FenwickTree.java`
- `datastructures/advanced/UnionFind.java`
- `problems/advanced/` — all exercise solutions
