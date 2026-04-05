# AlgoForge — Capstone Project

AlgoForge is the hands-on deliverable for the DS&A tutorial. It has two parts built incrementally over 12 modules:

- **Part A:** Custom implementations of every major data structure (from scratch, in Java)
- **Part B:** 50+ curated interview problem solutions organized by pattern

By completing AlgoForge, you will have both a reference library you understand deeply and a pattern catalog you can draw on in any technical interview.

---

## Quick Start

```bash
# Build the project (first time: downloads dependencies ~30s)
mvn clean compile

# Run all 122 tests
mvn test

# Run a specific test class
mvn test -Dtest=DPProblemsTest

# Run the complexity benchmark (Module 01)
mvn exec:java -Dexec.mainClass="com.algoforge.ComplexityBenchmark"

# Run the app overview banner
mvn exec:java -Dexec.mainClass="com.algoforge.AlgoForgeApp"
```

---

## Project Structure

```
algoforge/
├── pom.xml
└── src/
    ├── main/java/com/algoforge/
    │   ├── ComplexityBenchmark.java        ← Module 01: runtime measurement utility
    │   ├── datastructures/
    │   │   ├── linear/
    │   │   │   ├── DynamicArray.java       ← Module 02
    │   │   │   ├── SinglyLinkedList.java   ← Module 03
    │   │   │   ├── DoublyLinkedList.java   ← Module 03
    │   │   │   ├── Stack.java              ← Module 04
    │   │   │   ├── Queue.java              ← Module 04
    │   │   │   ├── MinStack.java           ← Module 04
    │   │   │   └── MonotonicStack.java     ← Module 04
    │   │   ├── hashing/
    │   │   │   └── HashMap.java            ← Module 05
    │   │   ├── trees/
    │   │   │   ├── BST.java                ← Module 08
    │   │   │   ├── AVLTree.java            ← Module 08
    │   │   │   ├── MinHeap.java            ← Module 08
    │   │   │   └── MaxHeap.java            ← Module 08
    │   │   ├── graphs/
    │   │   │   ├── Graph.java              ← Module 09
    │   │   │   ├── Dijkstra.java           ← Module 09
    │   │   │   ├── BellmanFord.java        ← Module 09
    │   │   │   └── UnionFind.java          ← Module 09/10
    │   │   └── advanced/
    │   │       ├── Trie.java               ← Module 10
    │   │       ├── SegmentTree.java        ← Module 10
    │   │       ├── FenwickTree.java        ← Module 10
    │   │       └── SkipList.java           ← Module 10
    │   └── problems/
    │       ├── arrays/                     ← Module 02 problems
    │       ├── linkedlists/               ← Module 03 problems
    │       ├── stacksqueues/               ← Module 04 problems
    │       ├── hashtables/                 ← Module 05 problems
    │       ├── backtracking/               ← Module 06 problems
    │       ├── sorting/                    ← Module 07 problems
    │       ├── trees/                      ← Module 08 problems
    │       ├── graphs/                     ← Module 09 problems
    │       ├── advanced/                   ← Module 10 problems
    │       ├── dp/                         ← Module 11 problems
    │       └── patterns/                   ← Module 12 patterns
    └── test/java/com/algoforge/
        ├── datastructures/
        └── problems/
```

---

## Using as a Reference Library

- **Data structures** — `src/main/java/com/algoforge/datastructures/`: from-scratch implementations of `MinHeap`, `Trie`, `SegmentTree`, `AVLTree`, and more. Each class has Javadoc with complexity analysis.
- **Problem solutions** — `src/main/java/com/algoforge/problems/`: organized by module/pattern. Each class has Javadoc with the pattern name, complexity, and a step-by-step trace.
- **Pattern cheat sheet** — [PatternIndex.md](src/main/java/com/algoforge/problems/patterns/PatternIndex.md): maps 15 patterns (two-pointer, sliding window, monotonic stack, interval merge, etc.) to problems with decision signals and code templates.

### Problems by Module

| Module | Package | Example Problems |
|--------|---------|-----------------|
| 02 Arrays/Strings | `problems.arrays` | `TwoSum`, `MaxSlidingWindow` |
| 03 Linked Lists | `problems.linkedlists` | `ReverseLinkedList`, `LinkedListCycle` |
| 04 Stacks/Queues | `problems.stacksqueues` | `ValidParentheses`, `SlidingWindowMaximum` |
| 05 Hash Tables | `problems.hashtables` | `GroupAnagrams`, `SubarraySumEqualsK` |
| 06 Backtracking | `problems.backtracking` | `NQueens`, `WordSearch` |
| 07 Sorting/Search | `problems.sorting` | `MedianOfTwoSortedArrays`, `SearchRotatedArray` |
| 08 Trees | `problems.trees` | `ConstructBinaryTree`, `BinaryTreeMaxPathSum` |
| 09 Graphs | `problems.graphs` | `CourseSchedule`, `NetworkDelayTime` |
| 10 Advanced DS | `problems.advanced` | `WordSearchII`, `CountSmallerNumbersAfterSelf` |
| 11 Dynamic Programming | `problems.dp` | `BurstBalloons`, `RegularExpressionMatching` |
| 12 Patterns | `problems.patterns` | `MedianFinder`, `MergeKSortedLists` |

---

## Module-by-Module Build Plan

| Module | What Gets Added |
|--------|----------------|
| 01 | `pom.xml` scaffold, `ComplexityBenchmark.java`, first test run |
| 02 | `DynamicArray<T>`, 5 array/string problems |
| 03 | `SinglyLinkedList<T>`, `DoublyLinkedList<T>`, 5 linked list problems |
| 04 | `Stack<T>`, `Queue<T>`, `MinStack<T>`, `MonotonicStack<T>`, 5 solutions |
| 05 | `HashMap<K,V>` (separate chaining), 5 hash table problems |
| 06 | Backtracking framework classes, 5 recursion/backtracking problems |
| 07 | `MergeSort`, `QuickSort`, `HeapSort`, `BinarySearch`, 5 sorting problems |
| 08 | `BST<T>`, `AVLTree<T>`, `MinHeap<T>`, `MaxHeap<T>`, 6 tree problems |
| 09 | `Graph<T>`, `Dijkstra`, `BellmanFord`, `UnionFind`, 6 graph problems |
| 10 | `Trie`, `SegmentTree`, `FenwickTree`, `SkipList`, 4 advanced DS problems |
| 11 | 20+ DP solution templates across all major patterns |
| 12 | Final 10+ solutions, `PatternIndex.md` |

---

## Pattern Index (built through Module 12)

See [PatternIndex.md](src/main/java/com/algoforge/problems/patterns/PatternIndex.md) after completing Module 12.
