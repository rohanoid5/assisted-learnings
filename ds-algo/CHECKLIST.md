# Data Structures & Algorithms — Knowledge Checklist

Use this file to periodically self-assess. Review it after each module and update your ratings.

**Legend:** `[ ]` Not yet · `[~]` In progress · `[x]` Confident

---

## Module 1 — Foundations & Complexity

### 1.1 What Are Data Structures?
- [x] Can explain the difference between a data structure and an algorithm
- [x] Can classify DS as linear (Array, LL, Stack, Queue) vs non-linear (Tree, Graph, Hash Table)
- [x] Knows when to choose each major data structure and why

### 1.2 Why Data Structures Matter
- [x] Can articulate how the choice of DS affects time and space complexity
- [x] Can map common problem types to the appropriate starting DS

### 1.3 Time vs Space Complexity
- [x] Understands the time-space trade-off (caching trades space for time, in-place sorts trade time for space)
- [x] Can identify the dominant term in a multi-operation algorithm

### 1.4 How to Calculate Complexity
- [x] Can analyze single loops, nested loops, and recursive calls for Big-O
- [x] Knows how to handle logarithmic steps (e.g., halving the input)
- [x] Can determine best, average, and worst case complexity separately

### 1.5 Big-O Notation
- [x] Can define O(f(n)) formally (upper bound definition)
- [x] Knows the rules: drop constants, drop lower-order terms
- [x] Can rank these in order: O(1), O(log n), O(n), O(n log n), O(n²), O(2ⁿ), O(n!)

### 1.6 Big-Θ and Big-Ω Notation
- [x] Can explain the difference between O (upper), Θ (tight), and Ω (lower) bounds
- [x] Can give an example where O and Ω differ (e.g., binary search)

### 1.7 Common Runtimes
- [x] Knows the runtime of standard operations: HashMap get/put, binary search, sorting, BFS/DFS
- [x] Can identify which runtime class a given algorithm belongs to by inspection

---

## Module 2 — Arrays & Strings

### 2.1 Array Fundamentals
- [ ] Understands contiguous memory layout and O(1) random access
- [ ] Knows the difference between Java arrays and `ArrayList` (resizing, capacity)
- [ ] Can explain amortized O(1) insertion for dynamic arrays

### 2.2 String Manipulation
- [ ] Understands Java String immutability and the string pool
- [ ] Knows when to use `StringBuilder` over `+` concatenation
- [ ] Can reverse, check palindromes, and count character frequencies efficiently

### 2.3 Two Pointer Technique
- [ ] Can identify problems that fit the two-pointer pattern (sorted array, pair sum)
- [ ] Can implement both converging pointers and same-direction pointers
- [ ] Solved: Two Sum (sorted), Container With Most Water, Valid Palindrome

### 2.4 Sliding Window Technique
- [ ] Can identify fixed-size and variable-size window problems
- [ ] Can implement a sliding window with a HashMap frequency counter
- [ ] Solved: Longest Substring Without Repeating Characters, Maximum Subarray

### 2.5 Prefix Sum & Difference Arrays
- [ ] Can build a prefix sum array and answer range sum queries in O(1)
- [ ] Knows the difference array technique for range updates

### 2.6 Matrix Traversal Patterns
- [ ] Can traverse a 2D matrix in spiral, diagonal, and row-major order
- [ ] Can perform BFS/DFS on a grid (treating cells as graph nodes)

---

## Module 3 — Linked Lists

### 3.1 Singly Linked List
- [ ] Can implement a singly linked list with insert, delete, and search
- [ ] Knows why access is O(n) but head insertion is O(1)
- [ ] Can draw the pointer reassignment for inserting/deleting a node

### 3.2 Doubly & Circular Linked Lists
- [ ] Can explain the trade-offs of doubly linked lists (extra memory, bidirectional traversal)
- [ ] Knows where circular linked lists are used (e.g., round-robin scheduling, LRU cache)

### 3.3 Fast and Slow Pointers
- [ ] Can detect a cycle in a linked list using Floyd's algorithm
- [ ] Can find the middle of a linked list in one pass
- [ ] Solved: Linked List Cycle, Happy Number, Find Duplicate Number

### 3.4 Merge & Reverse Patterns
- [ ] Can reverse a linked list in-place iteratively and recursively
- [ ] Can merge two sorted linked lists in O(n + m)
- [ ] Can reverse a sublist in-place without extra storage

### 3.5 Common Linked List Problems
- [ ] Solved: Merge Two Sorted Lists, Remove Nth Node From End, LRU Cache
- [ ] Can implement an LRU Cache using a HashMap + doubly linked list

---

## Module 4 — Stacks & Queues

### 4.1 Stack Fundamentals
- [ ] Can implement a stack using both an array and a linked list
- [ ] Knows all use cases: function call stack, undo operations, expression parsing

### 4.2 Monotonic Stack
- [ ] Can recognize when a problem requires a monotonic stack (next greater/smaller element)
- [ ] Can trace through a monotonic stack state step-by-step
- [ ] Solved: Daily Temperatures, Largest Rectangle in Histogram, Next Greater Element

### 4.3 Queue & Deque
- [ ] Can implement a queue using two stacks
- [ ] Knows how a circular buffer works and its advantages
- [ ] Can use `ArrayDeque` as both a stack and a queue in Java

### 4.4 Priority Queue & Comparators
- [ ] Understands the heap-based implementation of `PriorityQueue` in Java
- [ ] Can write a custom `Comparator` for complex ordering
- [ ] Solved: Merge K Sorted Lists, Top K Frequent Elements

### 4.5 Expression Evaluation
- [ ] Can evaluate arithmetic expressions using two stacks (operands + operators)
- [ ] Solved: Valid Parentheses, Min Stack, Evaluate Reverse Polish Notation

---

## Module 5 — Hash Tables

### 5.1 Hash Table Internals
- [ ] Can explain how a hash function maps keys to bucket indices
- [ ] Understands the load factor and when rehashing is triggered
- [ ] Knows the average O(1) get/put and worst-case O(n) for hash tables

### 5.2 Collision Resolution
- [ ] Can explain separate chaining (linked list buckets)
- [ ] Can explain open addressing (linear probing, quadratic probing, double hashing)
- [ ] Knows that Java's `HashMap` uses chaining (switches to tree when bucket length ≥ 8)

### 5.3 Java Hash Collections
- [ ] Knows when to use `HashMap` vs `LinkedHashMap` vs `TreeMap`
- [ ] Understands how `hashCode()` and `equals()` must be implemented together
- [ ] Can explain why mutable keys in a HashMap cause silent bugs

### 5.4 Hash Table Problem Patterns
- [ ] Can use a frequency map to solve anagram/permutation problems
- [ ] Can use a HashMap to solve Two Sum in O(n)
- [ ] Solved: Group Anagrams, Longest Consecutive Sequence, Subarray Sum Equals K

---

## Module 6 — Recursion & Backtracking

### 6.1 Recursion Fundamentals
- [ ] Can identify base case and recursive case for any recursive function
- [ ] Can trace a recursion call stack frame-by-frame
- [ ] Understands stack overflow risk and tail-call optimization

### 6.2 Recurrence Relations
- [ ] Can write a recurrence relation for a recursive algorithm (T(n) = 2T(n/2) + O(n))
- [ ] Can apply the Master Theorem to determine the class of a recurrence
- [ ] Can draw and analyze a recursion tree

### 6.3 Backtracking Framework
- [ ] Can apply the "choose → explore → unchoose" framework to any backtracking problem
- [ ] Understands pruning and why it improves performance
- [ ] Can visualize the decision tree for a given problem

### 6.4 Subsets, Permutations, Combinations
- [ ] Can generate all subsets, permutations, and combinations of a given set
- [ ] Solved: Generate Parentheses, Letter Combinations of Phone Number, Subsets, Permutations

### 6.5 Constraint Satisfaction
- [ ] Can implement N-Queens with backtracking and column/diagonal conflict checks
- [ ] Can solve Sudoku using constraint propagation + backtracking
- [ ] Solved: N-Queens, Sudoku Solver, Word Search

---

## Module 7 — Sorting & Searching

### 7.1 Bubble & Selection Sort
- [ ] Can implement both and explain their O(n²) worst case
- [ ] Knows that bubble sort is stable, selection sort is not

### 7.2 Insertion Sort
- [ ] Can implement insertion sort and explain why it is O(n) on nearly-sorted input
- [ ] Knows it is used as the base case in Timsort and introsort

### 7.3 Merge Sort
- [ ] Can implement merge sort recursively and explain the divide-conquer-merge steps
- [ ] Knows it is O(n log n) always and O(n) space

### 7.4 Quick Sort
- [ ] Can implement Lomuto and Hoare partition schemes
- [ ] Knows the pivot selection strategies and worst-case O(n²) conditions
- [ ] Understands why QuickSort is faster in practice despite same asymptotic complexity as MergeSort

### 7.5 Heap Sort
- [ ] Can build a max-heap in O(n) using heapify
- [ ] Can perform heap sort and explain why it is O(n log n) in-place

### 7.6 Binary Search & Variations
- [ ] Can implement binary search iteratively and recursively
- [ ] Can apply binary search on the answer space ("find minimum X such that...")
- [ ] Solved: Search in Rotated Sorted Array, Find First and Last Position, Koko Eating Bananas

### 7.7 Sorting Algorithm Comparison
- [ ] Can compare all 6 algorithms by time complexity (best/avg/worst), space, and stability
- [ ] Knows when to use counting sort / radix sort for O(n) sorting

---

## Module 8 — Trees

### 8.1 Binary Tree Fundamentals
- [ ] Can define: root, leaf, height, depth, diameter, balanced tree, complete tree, full tree
- [ ] Can calculate the height and number of nodes of a complete binary tree

### 8.2 Tree Traversals
- [ ] Can implement inorder, preorder, and postorder iteratively (using a stack)
- [ ] Can implement all three recursively from memory
- [ ] Knows: inorder of BST = sorted sequence

### 8.3 Level-Order Traversal (BFS)
- [ ] Can implement BFS on a tree using a queue
- [ ] Can collect nodes level-by-level in a list of lists
- [ ] Solved: Binary Tree Level Order, Zigzag Level Order, Maximum Width

### 8.4 Binary Search Trees
- [ ] Can implement insert, delete, and search in a BST
- [ ] Can verify if a tree is a valid BST (inorder traversal or bounds-based DFS)
- [ ] Solved: Validate BST, Kth Smallest in BST, BST to Greater Sum Tree

### 8.5 AVL Trees & Rotations
- [ ] Can explain balance factor and the 4 rotation cases (LL, RR, LR, RL)
- [ ] Understands why AVL trees guarantee O(log n) operations

### 8.6 Heaps
- [ ] Can implement a min-heap and max-heap from scratch using an array
- [ ] Understands the array representation: parent at (i-1)/2, children at 2i+1 and 2i+2
- [ ] Solved: Kth Largest Element, Find Median from Data Stream

### 8.7 B-Trees & B+ Trees
- [ ] Can explain why B-trees are used in database indexes and filesystems
- [ ] Understands the multi-key, multi-child node structure and split operations

### 8.8 Common Tree Problems
- [ ] Solved: Maximum Depth, Lowest Common Ancestor, Diameter of Binary Tree, Serialize/Deserialize Binary Tree, Path Sum II

---

## Module 9 — Graphs

### 9.1 Graph Representation
- [ ] Can implement adjacency list and adjacency matrix representations
- [ ] Knows the space and time trade-offs for each
- [ ] Can represent both directed and undirected, weighted and unweighted graphs

### 9.2 BFS & DFS on Graphs
- [ ] Can implement iterative BFS using a queue with a visited set
- [ ] Can implement iterative and recursive DFS
- [ ] Knows: BFS finds shortest path (unweighted), DFS explores all paths

### 9.3 Topological Sort
- [ ] Can implement Kahn's algorithm (BFS-based, in-degree counting)
- [ ] Can implement DFS-based topological sort
- [ ] Solved: Course Schedule, Course Schedule II, Alien Dictionary

### 9.4 Dijkstra's Algorithm
- [ ] Can implement Dijkstra using a priority queue
- [ ] Knows it requires non-negative edge weights
- [ ] Solved: Network Delay Time, Cheapest Flights Within K Stops

### 9.5 Bellman-Ford & A\*
- [ ] Can implement Bellman-Ford and explain why it detects negative cycles
- [ ] Knows A\* uses a heuristic to guide search toward the goal
- [ ] Knows when to prefer Bellman-Ford over Dijkstra (negative weights)

### 9.6 Minimum Spanning Tree
- [ ] Can implement Prim's algorithm using a priority queue
- [ ] Can implement Kruskal's algorithm using Union-Find
- [ ] Solved: Min Cost to Connect All Points, Redundant Connection

### 9.7 Island Traversal & Components
- [ ] Can apply BFS or DFS flood fill to a grid
- [ ] Can count connected components using Union-Find or DFS
- [ ] Solved: Number of Islands, Max Area of Island, Surrounded Regions

### 9.8 Graph Problem Patterns
- [ ] Solved: Clone Graph, Word Ladder, Pacific Atlantic Water Flow, Walls and Gates

---

## Module 10 — Advanced Data Structures

### 10.1 Trie (Prefix Tree)
- [ ] Can implement insert, search, and startsWith in a Trie
- [ ] Knows time complexity: O(m) per operation where m = word length
- [ ] Solved: Implement Trie, Word Search II, Design Add and Search Words

### 10.2 Disjoint Set (Union-Find)
- [ ] Can implement Union-Find with union by rank and path compression
- [ ] Knows the near-O(1) amortized time with both optimizations (inverse Ackermann)
- [ ] Solved: Accounts Merge, Number of Provinces, Redundant Connection

### 10.3 Segment Trees
- [ ] Can build a segment tree for range sum/min/max queries
- [ ] Can perform point updates and range queries in O(log n)
- [ ] Solved: Range Sum Query — Mutable, Count of Range Sum

### 10.4 Fenwick Trees (BIT)
- [ ] Can implement a Fenwick tree for prefix sums and point updates
- [ ] Understands the binary index parent traversal pattern
- [ ] Solved: Range Sum Query — Mutable (Fenwick), Count of Smaller Numbers After Self

### 10.5 Suffix Structures
- [ ] Can explain what a suffix array is and how it enables O(m log n) substring search
- [ ] Understands the relationship between suffix trees and Aho-Corasick automaton

### 10.6 Skip List
- [ ] Can explain the probabilistic layered linked list structure
- [ ] Knows expected O(log n) search, insert, and delete operations
- [ ] Understands that Redis Sorted Sets use skip lists internally

---

## Module 11 — Dynamic Programming

### 11.1 DP Fundamentals
- [ ] Can identify overlapping subproblems and optimal substructure
- [ ] Can convert a top-down memoized solution into a bottom-up tabulation
- [ ] Knows when to use DP vs greedy (DP when greedy choice can't be proven locally optimal)

### 11.2 1D DP Patterns
- [ ] Solved: Climbing Stairs, House Robber, Min Cost Climbing Stairs, Jump Game
- [ ] Can space-optimize 1D DP from O(n) table to O(1) variables

### 11.3 2D DP Patterns
- [ ] Can build a 2D DP table for grid path and string comparison problems
- [ ] Solved: Unique Paths, Minimum Path Sum, Edit Distance, Longest Common Subsequence

### 11.4 Knapsack Patterns
- [ ] Can implement 0/1 Knapsack, Unbounded Knapsack, and Partition Equal Subset Sum
- [ ] Knows that the partition problem reduces to a subset sum (0/1 knapsack variant)
- [ ] Solved: Coin Change, Target Sum, Coin Change II

### 11.5 Interval & String DP
- [ ] Can solve palindrome-related DP problems (longest palindromic subsequence, palindrome partitioning)
- [ ] Solved: Longest Palindromic Substring, Palindrome Partitioning, Burst Balloons

### 11.6 DP on Trees & Graphs
- [ ] Can compute tree DP (e.g., max path sum using post-order traversal)
- [ ] Understands re-rooting technique for tree DP problems
- [ ] Solved: Binary Tree Maximum Path Sum, House Robber III, Diameter of Binary Tree

### 11.7 Bitmask DP
- [ ] Understands how to represent subsets as bitmasks
- [ ] Can implement bitmask DP for small-n state enumerations
- [ ] Solved: Traveling Salesman (small n), Partition to K Equal Sum Subsets

---

## Module 12 — Interview Patterns & Practice

### 12.1 Two Heaps Pattern
- [ ] Can split a stream into two halves using a min-heap and max-heap
- [ ] Solved: Find Median from Data Stream, Sliding Window Median

### 12.2 Merge Intervals Pattern
- [ ] Can detects overlap: `a.start <= b.end`
- [ ] Solved: Merge Intervals, Insert Interval, Meeting Rooms II, Non-overlapping Intervals

### 12.3 Cyclic Sort Pattern
- [ ] Can place elements at their correct index in O(n) without extra space
- [ ] Solved: Find All Duplicates in an Array, First Missing Positive, Find the Duplicate Number

### 12.4 Kth Element Pattern
- [ ] Can select the Kth element using QuickSelect in expected O(n)
- [ ] Solved: Kth Largest Element in an Array, K Closest Points to Origin

### 12.5 Problem-Solving Framework
- [ ] Can apply the UMPIRE framework (Understand, Match, Plan, Implement, Review, Evaluate)
- [ ] Can verbalize a solution clearly before coding under interview pressure
- [ ] Can estimate time/space complexity for any solution on the fly

---

## Review Log

| Date | Modules Reviewed | Notes |
|------|-----------------|-------|
|      |                 |       |
|      |                 |       |
|      |                 |       |
