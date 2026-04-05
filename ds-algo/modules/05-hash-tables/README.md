# Module 05 — Hash Tables

A hash table maps keys to values in O(1) average time for lookup, insertion, and deletion. It underpins the solution to countless interview problems — frequency counting, deduplication, two-sum patterns, and grouping. Understanding the internals explains when performance degrades and why Java's `HashMap` behaves the way it does.

---

## Learning Objectives

- [ ] Explain how a hash function works and what makes a good one
- [ ] Describe chaining and open addressing for collision resolution
- [ ] Understand load factor and when Java's HashMap resizes
- [ ] Solve frequency counting and grouping problems using a HashMap
- [ ] Use HashSet for O(1) membership, deduplication, and set operations
- [ ] Implement a HashMap and HashSet from scratch

---

## Topics

| # | Topic | Key Concepts |
|---|-------|--------------|
| 01 | [Hash Table Internals](01-hash-table-internals.md) | Hash function, chaining vs open addressing, load factor, resize |
| 02 | [HashMap Patterns](02-hashmap-patterns.md) | Frequency counting, anagram grouping, two-sum pattern |
| 03 | [HashSet Patterns](03-hashset-patterns.md) | Deduplication, contains duplicate, longest consecutive sequence |
| 04 | [Design Problems](04-design-problems.md) | Design HashMap, Design HashSet from scratch |
| Exercises | [Exercises](exercises/README.md) | Two Sum, Group Anagrams, Top K Frequent, Longest Consecutive, Design HashMap |

---

## Estimated Time

3–4 hours (topics) + 2–3 hours (exercises) = **5–7 hours total**

---

## Prerequisites

- Module 01 (complexity analysis — average vs worst case)
- Module 02 (arrays — hash tables back their buckets with arrays)

---

## Capstone Milestone

By the end of this module, add to AlgoForge:
- `MyHashMap.java` — open-addressing or chaining implementation
- `MyHashSet.java` — backed by MyHashMap
- Solutions to Two Sum, Group Anagrams, and Longest Consecutive Sequence
