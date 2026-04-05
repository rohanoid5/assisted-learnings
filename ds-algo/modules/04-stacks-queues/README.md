# Module 04 — Stacks & Queues

Stacks and queues are restricted-access sequences. They underlie almost every algorithm involving order-of-processing: expression evaluation, BFS, DFS, monotonic optimization, and scheduling. This module covers the fundamentals and three powerful patterns: monotonic stack, priority queue, and expression evaluation.

---

## Learning Objectives

By the end of this module you should be able to:

- [ ] Explain LIFO and FIFO semantics and choose the right structure
- [ ] Use Java's `Deque` interface (`ArrayDeque`) correctly for both stack and queue
- [ ] Solve "next greater element" problems with a monotonic stack
- [ ] Use a min/max heap (`PriorityQueue`) for top-K and streaming median problems
- [ ] Implement a Min Stack with O(1) `getMin`
- [ ] Evaluate arithmetic expressions with two stacks

---

## Topics

| # | Topic | Key Concepts |
|---|-------|--------------|
| 01 | [Stack Fundamentals](01-stack-fundamentals.md) | LIFO, Java Deque, call stack, Valid Parentheses, Min Stack |
| 02 | [Queue Fundamentals](02-queue-fundamentals.md) | FIFO, ArrayDeque, circular buffer, BFS connection |
| 03 | [Monotonic Stack](03-monotonic-stack.md) | Next Greater Element, Daily Temperatures, Largest Rectangle |
| 04 | [Priority Queue & Heap](04-priority-queue-heap.md) | Min-heap, max-heap, PriorityQueue, Top K, streaming median |
| 05 | [Expression Evaluation](05-expression-evaluation.md) | Infix→postfix, two-stack calculator, Polish notation |
| Exercises | [Exercises](exercises/README.md) | Valid Parentheses, Min Stack, Daily Temperatures, Sliding Window Maximum, Task Scheduler |

---

## Estimated Time

3–4 hours (topics) + 2–3 hours (exercises) = **5–7 hours total**

---

## Prerequisites

- Module 01 (complexity analysis)
- Module 03 (linked lists — doubly linked list used internally by ArrayDeque)

---

## Capstone Milestone

By the end of this module, add to AlgoForge:
- `MinStack.java` — stack with O(1) `getMin`
- A monotonic stack solution for "Next Greater Element"
- A sliding window maximum solution using a monotonic deque
