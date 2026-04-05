# Module 3 — Linked Lists

## Overview

Linked lists appear in ~15% of FAANG interviews — not because they're common in production code, but because pointer manipulation tests precise thinking. The fast/slow pointer pattern (Floyd's cycle detection) is one of the most elegant algorithms in computer science and appears in problems far beyond simple cycle detection.

---

## Learning Objectives

By the end of this module you will be able to:

- [ ] Implement a singly linked list with all standard operations from memory
- [ ] Explain the memory trade-offs of linked lists vs arrays
- [ ] Detect cycles, find midpoints, and detect intersections using fast/slow pointers
- [ ] Reverse a linked list iteratively and recursively
- [ ] Implement an LRU Cache using a HashMap + doubly linked list

---

## Topics

| # | File | Concept |
|---|------|---------|
| 1 | [01-singly-linked-list.md](01-singly-linked-list.md) | Node structure, insert/delete, time complexity analysis |
| 2 | [02-doubly-circular-linked-list.md](02-doubly-circular-linked-list.md) | Doubly linked list, circular list, `LinkedHashMap` internals |
| 3 | [03-fast-slow-pointers.md](03-fast-slow-pointers.md) | Floyd's cycle detection, find middle, detect intersection |
| 4 | [04-merge-reverse-patterns.md](04-merge-reverse-patterns.md) | In-place reversal, merge two sorted lists, reorder list |
| 5 | [05-linked-list-problems.md](05-linked-list-problems.md) | Pattern catalog: remove Nth, palindrome, skip pointers |

---

## Estimated Time

**3–4 hours** (including exercises)

---

## Prerequisites

- Module 1 (Big-O) and Module 2 (arrays/two pointers)
- Java generics, inner classes

---

## Capstone Milestone

By the end of this module you will have added to AlgoForge:
- `SinglyLinkedList<T>` implementation with all operations
- `DoublyLinkedList<T>` implementation
- 5 linked list problem solutions

See [exercises/README.md](exercises/README.md) for hands-on tasks.
