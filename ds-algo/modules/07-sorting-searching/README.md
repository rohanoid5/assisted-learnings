# Module 07 — Sorting & Searching

## Overview

Sorting and searching are the most examined algorithm categories in technical interviews. This module covers all six major sorting algorithms with step-by-step visual traces, six variations of binary search (the most bug-prone algorithm to implement correctly), and a complexity comparison that tells you which to reach for in any situation.

---

## Learning Objectives

By the end of this module you will be able to:

- [ ] Implement Bubble, Insertion, Selection, Merge, Quick, and Heap Sort from scratch
- [ ] Analyze the time and space complexity of each, including best/average/worst cases
- [ ] Implement **binary search** correctly using the closed-interval template and apply it to 6 variations
- [ ] Choose the right sorting algorithm for a given scenario (stability, in-place, already-sorted input)
- [ ] Debug off-by-one errors in binary search using the `left <= right` template

---

## Topics

| # | File | Concept |
|---|------|---------|
| 1 | [01-bubble-selection-sort.md](01-bubble-selection-sort.md) | Bubble Sort, Selection Sort — O(n²) baselines |
| 2 | [02-insertion-sort.md](02-insertion-sort.md) | Insertion Sort — best for nearly-sorted data |
| 3 | [03-merge-sort.md](03-merge-sort.md) | Merge Sort — O(n log n) stable, divide & conquer |
| 4 | [04-quick-sort.md](04-quick-sort.md) | Quick Sort — O(n log n) average, Lomuto & Hoare |
| 5 | [05-heap-sort.md](05-heap-sort.md) | Heap Sort — O(n log n) in-place, not stable |
| 6 | [06-binary-search.md](06-binary-search.md) | Binary search template + 6 variations |
| 7 | [07-sorting-comparison.md](07-sorting-comparison.md) | Algorithm comparison table, when to use each |
| Exercises | [Exercises](exercises/README.md) | Sort Colors, Merge Intervals, Kth Largest, Search Rotated Array |

---

## Estimated Time

**5–6 hours** (including exercises)

---

## Prerequisites

- Module 06: recursion and recurrence relations (Merge Sort uses divide & conquer)
- Module 08: heap data structure (Heap Sort uses a max-heap — read Topic 8.6 first if needed)

---

## Capstone Milestone

By the end of this module, add to AlgoForge:
- `datastructures/sorting/MergeSort.java`
- `datastructures/sorting/QuickSort.java`
- `datastructures/searching/BinarySearch.java`
- `problems/sorting/SortColors.java`
- `problems/sorting/SearchRotatedArray.java`
