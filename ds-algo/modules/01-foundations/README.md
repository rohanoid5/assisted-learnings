# Module 1 — Foundations & Complexity Analysis

## Overview

Before writing a single line of algorithm code, you need a precise language to *measure* and *compare* algorithms. This module gives you that language. Big-O notation, complexity analysis, and understanding common runtime classes are the vocabulary you will use every single day in interviews — every time you say "my solution is O(n log n)" you are applying these concepts.

This is not abstract mathematics. Every statement here will be grounded in real Java code you can measure.

---

## Learning Objectives

By the end of this module you will be able to:

- [ ] Explain what a data structure is and why the choice of DS matters more than the algorithm
- [ ] Calculate the Big-O time and space complexity of any given code snippet
- [ ] Distinguish between Big-O (upper bound), Big-Θ (tight bound), and Big-Ω (lower bound)
- [ ] Rank the common runtime classes from fastest to slowest and know a canonical example of each
- [ ] State the time complexity of standard Java Collections operations from memory

---

## Topics

| # | File | Concept |
|---|------|---------|
| 1 | [01-what-are-data-structures.md](01-what-are-data-structures.md) | Definition, classification, and choosing the right DS |
| 2 | [02-why-ds-matter.md](02-why-ds-matter.md) | Impact on performance, problem-to-DS mapping |
| 3 | [03-time-vs-space-complexity.md](03-time-vs-space-complexity.md) | The trade-off, dominant terms, auxiliary space |
| 4 | [04-how-to-calculate-complexity.md](04-how-to-calculate-complexity.md) | Step-by-step analysis of loops, recursion, and built-in calls |
| 5 | [05-big-o-notation.md](05-big-o-notation.md) | Formal definition, simplification rules, amortized analysis |
| 6 | [06-big-theta-omega.md](06-big-theta-omega.md) | Upper, tight, and lower bounds — when they differ |
| 7 | [07-common-runtimes.md](07-common-runtimes.md) | O(1) through O(n!) — examples, growth chart, Java Collections cheat sheet |

---

## Estimated Time

**3–4 hours** (including exercises)

---

## Prerequisites

- Java 17+ installed (`java --version`)
- Basic OOP: classes, methods, generics
- AlgoForge project compiled (`mvn compile` passes)

---

## Capstone Milestone

By the end of this module you will have:

- A working Maven project at `capstone/algoforge/`
- A running `ComplexityBenchmark` that empirically confirms Big-O growth rates
- Your first test passing with `mvn test`

See [exercises/README.md](exercises/README.md) for hands-on tasks.
