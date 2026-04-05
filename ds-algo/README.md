# Data Structures & Algorithms — Interactive Tutorial

A hands-on, modular DSA learning guide targeting **FAANG-level software engineering interviews**. Every concept is taught with detailed visual diagrams, step-by-step traces, and Java implementations — from the ground up to advanced. Exercises mirror real LeetCode Medium/Hard patterns with full solutions.

---

## How to Use This Tutorial

1. Work through modules **in order** — each builds on the previous.
2. After each concept, **apply it to the capstone project** (AlgoForge) using the _Capstone Connection_ section at the bottom of every topic file.
3. Complete the **exercises** before moving to the next module — they match real interview patterns.
4. The `capstone/algoforge/` folder holds two deliverables built incrementally:
   - **Part A:** A custom data structures library (implement from scratch)
   - **Part B:** A curated problem collection (50+ solutions organized by pattern)

> The goal is not to memorize solutions — it's to recognize patterns instantly and explain your reasoning clearly under pressure.

---

## Prerequisites

| Requirement | Version | Notes                                                                   |
|-------------|---------|-------------------------------------------------------------------------|
| Java        | 17+     | LTS; use [SDKMAN](https://sdkman.io/) to manage versions               |
| Maven       | 3.8+    | `brew install maven` on macOS                                           |
| JUnit 5     | 5.10+   | Bundled in AlgoForge's `pom.xml`                                        |
| IDE         | Latest  | IntelliJ IDEA (recommended) or VS Code with Java Extension Pack         |
| LeetCode    | Free    | [leetcode.com](https://leetcode.com) account for optional live practice |

> **Assumed knowledge:** Java syntax, OOP (classes, inheritance, interfaces), basic generics, and the Java Collections framework (`List`, `Map`, `Set`). If you can write a `Comparator`, you're ready.

---

## Learning Path

| Module | Topic | Est. Time | Capstone Milestone |
|--------|-------|-----------|-------------------|
| [01 — Foundations & Complexity](modules/01-foundations/) | What are DS, Big-O/Θ/Ω, runtime analysis, common runtimes | 3–4 hrs | AlgoForge project scaffolded + `ComplexityBenchmark` |
| [02 — Arrays & Strings](modules/02-arrays-strings/) | Array internals, two pointer, sliding window, prefix sum, matrix | 4–5 hrs | `DynamicArray<T>` implementation |
| [03 — Linked Lists](modules/03-linked-lists/) | Singly/doubly, fast-slow pointers, merge/reverse patterns | 3–4 hrs | `SinglyLinkedList<T>` + `DoublyLinkedList<T>` |
| [04 — Stacks & Queues](modules/04-stacks-queues/) | Monotonic stack, PQ, expression evaluation | 3–4 hrs | `Stack<T>`, `Queue<T>`, `MinStack<T>` |
| [05 — Hash Tables](modules/05-hash-tables/) | Internals, collision resolution, Java HashMap, patterns | 3 hrs | `HashMap<K,V>` with chaining |
| [06 — Recursion & Backtracking](modules/06-recursion-backtracking/) | Call stack, recurrences, backtracking framework, N-Queens | 4–5 hrs | Backtracking solver utilities |
| [07 — Sorting & Searching](modules/07-sorting-searching/) | 6 sorts with visualizations, binary search variations | 5–6 hrs | `MergeSort`, `QuickSort`, `BinarySearch` utilities |
| [08 — Trees](modules/08-trees/) | Binary trees, traversals, BST, AVL, heap, B-trees | 6–7 hrs | `BST<T>`, `AVLTree<T>`, `MinHeap<T>` |
| [09 — Graphs](modules/09-graphs/) | Representations, BFS/DFS, Dijkstra, Bellman-Ford, MST, topo sort | 6–7 hrs | `Graph<T>`, `Dijkstra`, `UnionFind` |
| [10 — Advanced Data Structures](modules/10-advanced-ds/) | Trie, Union-Find, Segment Tree, Fenwick Tree, Skip List | 5–6 hrs | `Trie`, `SegmentTree`, `FenwickTree` |
| [11 — Dynamic Programming](modules/11-dynamic-programming/) | Memoization, tabulation, 1D/2D patterns, knapsack, bitmask DP | 7–8 hrs | 20+ DP solution templates |
| [12 — Interview Patterns](modules/12-interview-patterns/) | Two heaps, merge intervals, cyclic sort, Kth element, mock guide | 4–5 hrs | 50+ curated solutions + pattern index |

**Total estimated time: 55–66 hours**

---

## Capstone Project: AlgoForge

AlgoForge is a **dual-purpose Java library** — it is both a custom implementation of every major data structure and a curated collection of 50+ interview problems organized by pattern. It exercises every concept in the tutorial in a way that mirrors real FAANG technical interviews.

### Project Structure

```
capstone/algoforge/
├── pom.xml                                 ← Maven project (Java 17, JUnit 5)
├── README.md                               ← Capstone guide
└── src/
    ├── main/java/com/algoforge/
    │   ├── datastructures/                 ← Part A: library implementations
    │   │   ├── linear/                     ← Array, LinkedList, Stack, Queue
    │   │   ├── trees/                      ← BST, AVL, Heap, Trie
    │   │   ├── graphs/                     ← Graph, UnionFind
    │   │   └── advanced/                   ← SegmentTree, FenwickTree, SkipList
    │   └── problems/                       ← Part B: pattern-organized solutions
    │       ├── arrays/
    │       ├── linkedlists/
    │       ├── trees/
    │       ├── graphs/
    │       ├── dp/
    │       └── patterns/                   ← Two heaps, sliding window, etc.
    └── test/java/com/algoforge/
        ├── datastructures/                 ← Unit tests for each DS
        └── problems/                       ← Test-driven problem solutions
```

### What Gets Built Module-by-Module

| Module | What Gets Added to AlgoForge |
|--------|-------------------------------|
| 01 | Maven project scaffold, `ComplexityBenchmark` utility, `AlgoForgeApp` entry point |
| 02 | `DynamicArray<T>` (resizable array), 5 array/string problem solutions |
| 03 | `SinglyLinkedList<T>`, `DoublyLinkedList<T>`, 5 linked list problem solutions |
| 04 | `Stack<T>`, `Queue<T>`, `MinStack<T>`, `MonotonicStack<T>`, 5 stack/queue solutions |
| 05 | `HashMap<K,V>` with separate chaining, 5 hash table problem solutions |
| 06 | Backtracking solver framework, 5 recursion/backtracking problem solutions |
| 07 | `MergeSort`, `QuickSort`, `HeapSort`, `BinarySearch` (6 variants), 5 search problem solutions |
| 08 | `BST<T>`, `AVLTree<T>`, `MinHeap<T>`, `MaxHeap<T>`, 6 tree problem solutions |
| 09 | `Graph<T>` (directed + undirected), `Dijkstra`, `BellmanFord`, `UnionFind`, 6 graph problem solutions |
| 10 | `Trie`, `SegmentTree`, `FenwickTree`, `SkipList`, 4 advanced DS problem solutions |
| 11 | 20+ DP solution templates across all major patterns |
| 12 | Final 10+ solutions, `PatternIndex.md` — the complete pattern-to-problem reference |

---

## Quick Start

```bash
# Navigate to capstone project
cd capstone/algoforge

# Build the project (downloads dependencies)
mvn clean compile

# Run all tests
mvn test

# Run a specific test class
mvn test -Dtest=DynamicArrayTest

# Run the benchmark utility (Module 01)
mvn exec:java -Dexec.mainClass="com.algoforge.ComplexityBenchmark"
```

---

## Pattern Recognition Guide

The fastest way to improve at interviews is to recognize *which pattern* a problem belongs to.

| Pattern | Signature signal | Key DS/Algorithm |
|---------|-----------------|-----------------|
| Two Pointer | Sorted array, pair sum, palindrome | Array (two indices) |
| Sliding Window | Subarray/substring with constraint | Array + HashMap |
| Fast & Slow Pointer | Cycle detection, middle of list | Linked List |
| Monotonic Stack | Next greater/smaller element | Stack |
| Binary Search | Sorted input, "find minimum X such that..." | Array + midpoint logic |
| BFS | Shortest path, level-by-level, grids | Queue + visited set |
| DFS / Backtracking | All combinations, permutations, paths | Recursion + choice tree |
| Dynamic Programming | "How many ways", optimal substructure, overlapping subproblems | Memo / DP table |
| Union-Find | Connected components, cycle detection in graph | Disjoint Set |
| Two Heaps | Median of stream, split into halves | MinHeap + MaxHeap |
| Topological Sort | Ordering with dependencies | DFS + in-degree map |
| Trie | Prefix search, word dictionary | Trie node tree |

---

## Related Tutorials

- [Spring Boot Tutorial](../spring-boot/README.md) — put these algorithms to work in production Java APIs
- [PostgreSQL Tutorial](../postgres/README.md) — understand *why* B-trees and hash indexes exist
- [Node.js Tutorial](../nodejs/README.md) — see how event loop and async I/O relate to algorithmic thinking
- [System Design Tutorial](../system-design/README.md) — apply Big-O thinking to large-scale architecture decisions
