# Module 08 — Trees

## Overview

Trees are the data structure that appears in more interview problems than any other — binary trees, BSTs, heaps, and their traversals show up across problems about searching, sorting, and hierarchical data. This module builds from basic binary tree vocabulary up through AVL rotations, heap internals, and B-trees, giving you both implementation fluency and problem-solving pattern recognition.

---

## Learning Objectives

By the end of this module you will be able to:

- [ ] Define tree vocabulary: root, leaf, height, depth, diameter, balanced
- [ ] Implement all four tree traversals (inorder, preorder, postorder, level-order) both recursively and iteratively
- [ ] Implement a Binary Search Tree with insert, search, and delete
- [ ] Understand and implement AVL tree rotations (LL, RR, LR, RL)
- [ ] Implement a Min-Heap and Max-Heap with siftUp and siftDown
- [ ] Understand B-Tree and B+ Tree structure (node splits, number of keys)
- [ ] Pattern-match tree problems to the right traversal (DFS vs BFS, pre vs post)

---

## Topics

| # | File | Concept |
|---|------|---------|
| 1 | [01-binary-tree-fundamentals.md](01-binary-tree-fundamentals.md) | Tree vocabulary, height, diameter, full/complete/perfect |
| 2 | [02-tree-traversals.md](02-tree-traversals.md) | Inorder, preorder, postorder — recursive & iterative |
| 3 | [03-level-order-traversal.md](03-level-order-traversal.md) | BFS with a queue, level-by-level processing |
| 4 | [04-binary-search-trees.md](04-binary-search-trees.md) | BST invariant, insert, search, delete |
| 5 | [05-avl-trees.md](05-avl-trees.md) | Balance factor, LL/RR/LR/RL rotations |
| 6 | [06-heap-data-structure.md](06-heap-data-structure.md) | Min-heap, max-heap, siftUp, siftDown, heapify |
| 7 | [07-b-trees.md](07-b-trees.md) | B-Tree structure, splits, B+ Tree for databases |
| 8 | [08-tree-interview-problems.md](08-tree-interview-problems.md) | Pattern guide: which traversal, DFS vs BFS |
| Exercises | [Exercises](exercises/README.md) | MaxDepth, ValidateBST, LCA, LevelOrder, Serialize/Deserialize |

---

## Estimated Time

**6–7 hours** (including exercises)

---

## Prerequisites

- Module 06: recursion (all tree traversals are recursive at core)
- Module 04: queues (BFS uses a queue)

---

## Capstone Milestone

By the end of this module, add to AlgoForge:
- `datastructures/trees/BST.java`
- `datastructures/trees/AVLTree.java`
- `datastructures/trees/MinHeap.java`
- `datastructures/trees/MaxHeap.java`
- `problems/trees/` — all exercise solutions
