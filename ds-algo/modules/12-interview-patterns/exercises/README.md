# Module 12 — Interview Patterns Exercises

## Overview

These final exercises bring together patterns from across the entire tutorial. Each problem requires you to first identify the pattern, then implement the solution using the UMPIRE framework. The last exercise produces `PatternIndex.md` — your permanent interview reference document.

---

## Exercise 1: Find Median from Data Stream (LC #295)

**Goal:** Implement `MedianFinder` using the Two Heaps pattern with full test coverage.

**Problem:** Design a data structure that supports adding integers from a stream and retrieving the current median in O(1).

1. Implement `addNum(int num)` — O(log n)
2. Implement `findMedian()` — O(1)
3. Write **at least 4 test cases** in `MedianFinderTest.java`, including:
   - single element
   - even count (fractional median)
   - reverse-sorted insertion order
   - all same values

```java
// Skeleton
public class MedianFinder {
    private PriorityQueue<Integer> maxHeap;  // lower half
    private PriorityQueue<Integer> minHeap;  // upper half

    public MedianFinder() { ... }
    public void addNum(int num) { ... }
    public double findMedian() { ... }
}
```

**Verification:**
```
add(1) → median = 1.0
add(2) → median = 1.5
add(3) → median = 2.0
add(4) → median = 2.5
add(5) → median = 3.0
```

<details>
<summary>Show solution</summary>

```java
public class MedianFinder {
    private PriorityQueue<Integer> maxHeap = new PriorityQueue<>(Collections.reverseOrder());
    private PriorityQueue<Integer> minHeap = new PriorityQueue<>();

    public void addNum(int num) {
        maxHeap.offer(num);
        minHeap.offer(maxHeap.poll());
        if (minHeap.size() > maxHeap.size())
            maxHeap.offer(minHeap.poll());
    }

    public double findMedian() {
        if (maxHeap.size() == minHeap.size())
            return (maxHeap.peek() + minHeap.peek()) / 2.0;
        return maxHeap.peek();
    }
}
```

```java
// MedianFinderTest.java
class MedianFinderTest {
    @Test void singleElement() {
        MedianFinder mf = new MedianFinder();
        mf.addNum(1);
        assertThat(mf.findMedian()).isEqualTo(1.0);
    }

    @Test void evenCount() {
        MedianFinder mf = new MedianFinder();
        mf.addNum(1); mf.addNum(2);
        assertThat(mf.findMedian()).isEqualTo(1.5);
    }

    @Test void reverseOrder() {
        MedianFinder mf = new MedianFinder();
        mf.addNum(5); mf.addNum(3); mf.addNum(1);
        assertThat(mf.findMedian()).isEqualTo(3.0);
    }

    @Test void allSame() {
        MedianFinder mf = new MedianFinder();
        mf.addNum(7); mf.addNum(7); mf.addNum(7); mf.addNum(7);
        assertThat(mf.findMedian()).isEqualTo(7.0);
    }
}
```
</details>

---

## Exercise 2: Non-Overlapping Intervals (LC #435)

**Goal:** Apply the Merge Intervals pattern in a greedy context to find the minimum number of intervals to remove.

**Problem:** Given an array of intervals, return the minimum number of intervals you need to remove to make the rest non-overlapping.

1. Apply UMPIRE out loud (or in comments) before coding.
2. Hint: sort by **end time** (greedy: always keep the interval that ends earliest — leaves the most room).
3. Count how many intervals you skip (those that overlap with the last kept interval).

```java
// File: problems/patterns/NonOverlappingIntervals.java
public int eraseOverlapIntervals(int[][] intervals) { ... }
```

**Verification:**
```
eraseOverlapIntervals([[1,2],[2,3],[3,4],[1,3]]) == 1  (remove [1,3])
eraseOverlapIntervals([[1,2],[1,2],[1,2]])         == 2
eraseOverlapIntervals([[1,2],[2,3]])               == 0
```

<details>
<summary>Show solution</summary>

```java
public int eraseOverlapIntervals(int[][] intervals) {
    // Sort by END time (greedy: keep intervals that end earliest)
    Arrays.sort(intervals, (a, b) -> a[1] - b[1]);

    int removed = 0;
    int lastEnd = intervals[0][1];   // end time of last kept interval

    for (int i = 1; i < intervals.length; i++) {
        if (intervals[i][0] < lastEnd) {
            // Overlap with last kept interval — remove current
            removed++;
        } else {
            // No overlap — keep this interval, update lastEnd
            lastEnd = intervals[i][1];
        }
    }
    return removed;
}

/*
 Why sort by end? We want to keep as many intervals as possible.
 The interval that ends earliest leaves the most room for future intervals.
 This is the same greedy insight as the Activity Selection problem.
*/
```
</details>

---

## Exercise 3: First Missing Positive — Full Implementation (LC #41 — Hard)

**Goal:** Implement the full Cyclic Sort solution for First Missing Positive with complete edge case coverage.

**Problem:** Given an unsorted integer array `nums`, return the smallest missing positive integer. Must run in O(n) time and O(1) space.

1. Apply cyclic sort (place each value `v` in `[1..n]` at index `v-1`).
2. Scan for the first misplaced index.
3. Handle edge cases: all negatives, all positives already correct, gap in middle.

**Verification:**
```
firstMissingPositive([1,2,0])          == 3
firstMissingPositive([3,4,-1,1])       == 2
firstMissingPositive([7,8,9,11,12])    == 1
firstMissingPositive([1,2,3,4,5])      == 6
firstMissingPositive([-1,-2,-3])       == 1
```

<details>
<summary>Show solution</summary>

```java
public int firstMissingPositive(int[] nums) {
    int n = nums.length;
    int i = 0;

    while (i < n) {
        int correct = nums[i] - 1;
        // Only place if:  (1) in range [1..n]  (2) not already at correct position
        if (nums[i] > 0 && nums[i] <= n && nums[i] != nums[correct]) {
            int tmp = nums[i];
            nums[i] = nums[correct];
            nums[correct] = tmp;
        } else {
            i++;
        }
    }

    // First index where value doesn't match
    for (int j = 0; j < n; j++) {
        if (nums[j] != j + 1)
            return j + 1;
    }
    return n + 1;   // [1..n] are all present → answer is n+1
}
```
</details>

---

## Exercise 4: Kth Largest Element in a Stream (LC #703)

**Goal:** Implement a streaming Kth largest with `add(val)` that returns the current Kth largest after each insertion.

**Problem:** Design a class with constructor `KthLargest(int k, int[] nums)` and method `add(int val)` that returns the Kth largest element after each addition.

1. Initialize the class with the K-size min-heap.
2. In `add`, offer the new value, then trim the heap if its size exceeds K.
3. Return `heap.peek()` — that is the Kth largest.

```java
public class KthLargest {
    private final int k;
    private final PriorityQueue<Integer> heap;

    public KthLargest(int k, int[] nums) { ... }
    public int add(int val) { ... }
}
```

**Verification:**
```
k=3, nums=[4,5,8,2]
add(3)  → 4
add(5)  → 5
add(10) → 5
add(9)  → 8
add(4)  → 8
```

<details>
<summary>Show solution</summary>

```java
public class KthLargest {
    private final int k;
    private final PriorityQueue<Integer> heap;

    public KthLargest(int k, int[] nums) {
        this.k = k;
        this.heap = new PriorityQueue<>(k);   // min-heap
        for (int num : nums) add(num);
    }

    public int add(int val) {
        heap.offer(val);
        if (heap.size() > k) heap.poll();     // remove smallest — keep top K
        return heap.peek();
    }
}

/*
 Trace for k=3, init with [4,5,8,2]:
   add(4): heap=[4]
   add(5): heap=[4,5]
   add(8): heap=[4,5,8]
   add(2): size=4>3 → remove 2 → heap=[4,5,8]  (2 is smallest, gone)

 add(3):  offer 3 → heap=[3,5,8] (4 was polled? no: heap=[3,4,5,8] → size=4→poll 3)
          heap=[4,5,8] → peek=4 ✓
*/
```
</details>

---

## Exercise 5: Capstone — Build PatternIndex.md

**Goal:** Create the master pattern reference that serves as your permanent interview cheat sheet.

1. In `capstone/algoforge/`, create `PatternIndex.md`.
2. For **every problem** you added to AlgoForge across all 12 modules, add a row to the table below.
3. Tag each problem with one or more patterns.

```markdown
# AlgoForge — Pattern Index

| Problem | LC # | Difficulty | Pattern(s) | Time | Space | File |
|---------|------|-----------|----------- |------|-------|------|
| Two Sum | 1 | Easy | HashMap | O(n) | O(n) | problems/arrays/TwoSum.java |
| Climbing Stairs | 70 | Easy | 1D DP | O(n) | O(1) | problems/dp/ClimbingStairs.java |
| ...   | ... | ...  | ...    | ...  | ...  | ... |
```

**Minimum requirement:** At least 40 problems documented (the tutorial covers 50+).

**Verification:**
- `mvn test` → all tests pass
- `PatternIndex.md` has ≥ 40 rows
- Every pattern from Module 12 ("Two Heaps", "Merge Intervals", "Cyclic Sort", "K-th Element") appears at least twice

<details>
<summary>Show starter PatternIndex.md template</summary>

```markdown
# AlgoForge — Pattern Index

> Your personal interview cheat sheet. Update after solving each problem.

## Legend

| Difficulty | Label |
|-----------|-------|
| Easy      | E     |
| Medium    | M     |
| Hard      | H     |

## Problems by Module

### Module 01 — Foundations
| Problem | LC # | Diff | Pattern | Time | Space | File |
|---------|------|------|---------|------|-------|------|
| Complexity Benchmark | — | — | Analysis | — | — | ComplexityBenchmark.java |

### Module 02 — Arrays & Strings
| Problem | LC # | Diff | Pattern | Time | Space | File |
|---------|------|------|---------|------|-------|------|
| Two Sum | 1 | E | HashMap | O(n) | O(n) | problems/arrays/TwoSum.java |
| Best Time to Buy/Sell | 121 | E | Sliding Window | O(n) | O(1) | problems/arrays/BestTime.java |
| Container With Most Water | 11 | M | Two Pointer | O(n) | O(1) | problems/arrays/ContainerWater.java |
| Longest Substring No Repeat | 3 | M | Sliding Window+Set | O(n) | O(k) | problems/arrays/LongestSubstring.java |
| Maximum Subarray | 53 | M | DP / Kadane | O(n) | O(1) | problems/arrays/MaxSubarray.java |

### Module 03 — Linked Lists
| Problem | LC # | Diff | Pattern | Time | Space | File |
|---------|------|------|---------|------|-------|------|
| Reverse Linked List | 206 | E | Iterative/Recursive | O(n) | O(1) | problems/linkedlists/ReverseList.java |
| Merge Two Sorted Lists | 21 | E | Two Pointer | O(n+m) | O(1) | problems/linkedlists/MergeSorted.java |
| Linked List Cycle | 141 | E | Fast/Slow Pointer | O(n) | O(1) | problems/linkedlists/DetectCycle.java |
| Remove Nth From End | 19 | M | Two Pointer | O(n) | O(1) | problems/linkedlists/RemoveNth.java |
| LRU Cache | 146 | M | HashMap+DLL | O(1) | O(n) | datastructures/linear/LRUCache.java |

### Module 04 — Stacks & Queues
| Problem | LC # | Diff | Pattern | Time | Space | File |
|---------|------|------|---------|------|-------|------|
| Valid Parentheses | 20 | E | Stack | O(n) | O(n) | problems/arrays/ValidParentheses.java |
| Min Stack | 155 | M | Auxiliary Stack | O(1) | O(n) | datastructures/linear/MinStack.java |
| Daily Temperatures | 739 | M | Monotonic Stack | O(n) | O(n) | problems/arrays/DailyTemps.java |
| Sliding Window Maximum | 239 | H | Monotonic Deque | O(n) | O(k) | problems/arrays/SlidingWindowMax.java |

### Module 05 — Hash Tables
| Problem | LC # | Diff | Pattern | Time | Space | File |
|---------|------|------|---------|------|-------|------|
| Group Anagrams | 49 | M | HashMap+Sort | O(nk log k) | O(nk) | problems/arrays/GroupAnagrams.java |
| Longest Consecutive Sequence | 128 | M | HashSet | O(n) | O(n) | problems/arrays/LongestConsecutive.java |
| Subarray Sum Equals K | 560 | M | Prefix Sum+HashMap | O(n) | O(n) | problems/arrays/SubarraySum.java |

### Module 06 — Recursion & Backtracking
| Problem | LC # | Diff | Pattern | Time | Space | File |
|---------|------|------|---------|------|-------|------|
| Generate Parentheses | 22 | M | Backtracking | O(4^n/√n) | O(n) | problems/arrays/GenParentheses.java |
| Letter Combinations | 17 | M | Backtracking | O(4^n) | O(n) | problems/arrays/LetterCombinations.java |
| Subsets | 78 | M | Backtracking | O(n·2^n) | O(n) | problems/arrays/Subsets.java |
| Word Search | 79 | M | DFS+Backtracking | O(m·n·4^L) | O(L) | problems/graphs/WordSearch.java |
| N-Queens | 51 | H | Backtracking+Pruning | O(n!) | O(n) | problems/arrays/NQueens.java |

### Module 07 — Sorting & Searching
| Problem | LC # | Diff | Pattern | Time | Space | File |
|---------|------|------|---------|------|-------|------|
| Merge Intervals | 56 | M | Sort+Greedy | O(n log n) | O(n) | problems/patterns/MergeIntervals.java |
| Search in Rotated Sorted Array | 33 | M | Modified Binary Search | O(log n) | O(1) | problems/arrays/SearchRotated.java |
| Find First and Last Position | 34 | M | Binary Search (x2) | O(log n) | O(1) | problems/arrays/FirstLastPosition.java |
| Koko Eating Bananas | 875 | M | Binary Search on Answer | O(n log w) | O(1) | problems/arrays/KokoEating.java |
| Median of Two Sorted Arrays | 4 | H | Binary Search | O(log(m+n)) | O(1) | problems/arrays/MedianTwoArrays.java |

### Module 08 — Trees
| Problem | LC # | Diff | Pattern | Time | Space | File |
|---------|------|------|---------|------|-------|------|
| Maximum Depth | 104 | E | DFS | O(n) | O(h) | problems/trees/MaxDepth.java |
| Invert Binary Tree | 226 | E | DFS | O(n) | O(h) | problems/trees/InvertTree.java |
| Binary Tree Level Order | 102 | M | BFS | O(n) | O(n) | problems/trees/LevelOrder.java |
| Validate BST | 98 | M | DFS+Bounds | O(n) | O(h) | problems/trees/ValidateBST.java |
| Lowest Common Ancestor | 236 | M | DFS | O(n) | O(h) | problems/trees/LCA.java |
| Serialize/Deserialize | 297 | H | BFS/DFS | O(n) | O(n) | problems/trees/SerializeTree.java |

### Module 09 — Graphs
| Problem | LC # | Diff | Pattern | Time | Space | File |
|---------|------|------|---------|------|-------|------|
| Number of Islands | 200 | M | DFS/BFS Grid | O(m·n) | O(m·n) | problems/graphs/NumberIslands.java |
| Clone Graph | 133 | M | BFS+HashMap | O(V+E) | O(V) | problems/graphs/CloneGraph.java |
| Course Schedule | 207 | M | Topo Sort / DFS | O(V+E) | O(V+E) | problems/graphs/CourseSchedule.java |
| Network Delay Time | 743 | M | Dijkstra | O((V+E) log V) | O(V+E) | problems/graphs/NetworkDelay.java |
| Word Ladder | 127 | H | BFS | O(m²·n) | O(m·n) | problems/graphs/WordLadder.java |

### Module 10 — Advanced DS
| Problem | LC # | Diff | Pattern | Time | Space | File |
|---------|------|------|---------|------|-------|------|
| Implement Trie | 208 | M | Trie | O(m) per op | O(m·n) | datastructures/trees/Trie.java |
| Accounts Merge | 721 | M | Union-Find | O(n log n) | O(n) | problems/graphs/AccountsMerge.java |
| Range Sum Query Mutable | 307 | M | Fenwick Tree | O(log n) | O(n) | datastructures/advanced/FenwickTree.java |
| Count of Smaller After Self | 315 | H | Fenwick/Merge Sort | O(n log n) | O(n) | problems/arrays/CountSmaller.java |

### Module 11 — Dynamic Programming
| Problem | LC # | Diff | Pattern | Time | Space | File |
|---------|------|------|---------|------|-------|------|
| Climbing Stairs | 70 | E | 1D DP | O(n) | O(1) | problems/dp/ClimbingStairs.java |
| Coin Change | 322 | M | Unbounded Knapsack | O(n·m) | O(n) | problems/dp/CoinChange.java |
| Longest Common Subsequence | 1143 | M | 2D DP | O(m·n) | O(m·n) | problems/dp/LCS.java |
| Partition Equal Subset | 416 | M | 0/1 Knapsack | O(n·sum) | O(sum) | problems/dp/PartitionSubset.java |
| Edit Distance | 72 | M | 2D DP | O(m·n) | O(m·n) | problems/dp/EditDistance.java |
| Best Time Stocks III | 123 | H | State Machine DP | O(n) | O(1) | problems/dp/StocksIII.java |

### Module 12 — Interview Patterns
| Problem | LC # | Diff | Pattern | Time | Space | File |
|---------|------|------|---------|------|-------|------|
| Find Median from Stream | 295 | H | Two Heaps | O(log n) add | O(n) | problems/patterns/MedianFinder.java |
| Non-Overlapping Intervals | 435 | M | Merge Intervals+Greedy | O(n log n) | O(1) | problems/patterns/NonOverlapping.java |
| First Missing Positive | 41 | H | Cyclic Sort | O(n) | O(1) | problems/patterns/FirstMissingPositive.java |
| Kth Largest in Stream | 703 | E | K-Size Min Heap | O(log k) | O(k) | problems/patterns/KthLargest.java |
| Top K Frequent | 347 | M | Heap / Bucket Sort | O(n log k) | O(n) | problems/patterns/TopKFrequent.java |
```
</details>

---

## Capstone Checkpoint ✅

Final AlgoForge completion checklist:

**Part A — Data Structures Library**
- [ ] `datastructures/linear/DynamicArray.java`
- [ ] `datastructures/linear/SinglyLinkedList.java`
- [ ] `datastructures/linear/DoublyLinkedList.java`
- [ ] `datastructures/linear/Stack.java`
- [ ] `datastructures/linear/Queue.java`
- [ ] `datastructures/linear/MinStack.java`
- [ ] `datastructures/linear/LRUCache.java`
- [ ] `datastructures/linear/HashMap.java`
- [ ] `datastructures/trees/BST.java`
- [ ] `datastructures/trees/AVLTree.java`
- [ ] `datastructures/trees/MinHeap.java`
- [ ] `datastructures/trees/Trie.java`
- [ ] `datastructures/graphs/Graph.java`
- [ ] `datastructures/graphs/UnionFind.java`
- [ ] `datastructures/advanced/SegmentTree.java`
- [ ] `datastructures/advanced/FenwickTree.java`

**Part B — Problem Collection**
- [ ] ≥ 50 problem files across `problems/`
- [ ] All problem files have a corresponding test in `src/test/`
- [ ] `PatternIndex.md` in project root with ≥ 40 rows

**Final Build:**
```bash
cd capstone/algoforge
mvn clean test
# Expected: BUILD SUCCESS, all tests pass
```

🎓 **AlgoForge complete.** You are FAANG-interview ready.
