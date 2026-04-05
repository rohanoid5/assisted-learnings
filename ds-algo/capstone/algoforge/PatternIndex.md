# PatternIndex — AlgoForge Pattern-to-Problem Reference

The fastest path to interview success is **pattern recognition**: given a problem, identify which pattern applies, then apply the template. This index maps every pattern to its signal, the AlgoForge implementation, and representative LeetCode problems.

---

## How to Use

1. Read the problem. Notice the **signal** (sorted array? linked list? count ways?).
2. Match the signal to a pattern in this index.
3. Apply the template from the corresponding AlgoForge file.

---

## Pattern Catalog

### 1. Two Pointers
**Signal:** Sorted array, pair/triplet sum, palindrome check, removing duplicates.

| Problem | File | LC # |
|---------|------|------|
| Container With Most Water | `problems/arrays/ContainerWithMostWater.java` | 11 |
| Three Sum | `problems/arrays/ThreeSum.java` | 15 |
| Remove Nth From End | `problems/linkedlists/RemoveNthFromEnd.java` | 19 |

**Template:**
```java
int lo = 0, hi = n - 1;
while (lo < hi) {
    if (condition_met) return result;
    else if (too_small) lo++;
    else hi--;
}
```

---

### 2. Sliding Window
**Signal:** Contiguous subarray/substring with a constraint (max, min, equals k).

| Problem | File | LC # |
|---------|------|------|
| Longest Substring Without Repeating | `problems/arrays/LongestSubstringWithoutRepeating.java` | 3 |
| Sliding Window Maximum | `problems/stacksqueues/SlidingWindowMaximum.java` | 239 |
| Subarray Sum Equals K | `problems/hashtables/SubarraySumEqualsK.java` | 560 |

**Template:**
```java
int lo = 0, maxLen = 0;
Map<Character, Integer> window = new HashMap<>();
for (int hi = 0; hi < n; hi++) {
    // expand: add s[hi] to window
    while (window_invalid) {
        // shrink: remove s[lo] from window
        lo++;
    }
    maxLen = Math.max(maxLen, hi - lo + 1);
}
```

---

### 3. Fast & Slow Pointer
**Signal:** Cycle detection, middle of linked list, happy number.

| Problem | File | LC # |
|---------|------|------|
| Detect Cycle | `problems/linkedlists/DetectCycle.java` | 141 |
| Reorder List | `problems/linkedlists/ReorderList.java` | 143 |
| Find Duplicate Number | `problems/patterns/FindDuplicateNumber.java` | 287 |

**Template:**
```java
int slow = nums[0], fast = nums[0];
do { slow = nums[slow]; fast = nums[nums[fast]]; } while (slow != fast);
// Phase 2: find entry
slow = nums[0];
while (slow != fast) { slow = nums[slow]; fast = nums[fast]; }
// slow == fast == entry point
```

---

### 4. Monotonic Stack
**Signal:** Next greater/smaller element, largest rectangle, temperatures.

| Problem | File | LC # |
|---------|------|------|
| Daily Temperatures | `problems/stacksqueues/DailyTemperatures.java` | 739 |
| Sliding Window Maximum | `problems/stacksqueues/SlidingWindowMaximum.java` | 239 |
| Largest Rectangle in Histogram | `problems/stacksqueues/LargestRectangleHistogram.java` | 84 |

**Template (monotonic decreasing):**
```java
Deque<Integer> stack = new ArrayDeque<>(); // indices
for (int i = 0; i < n; i++) {
    while (!stack.isEmpty() && nums[i] > nums[stack.peek()])
        processPopped(stack.pop(), i);
    stack.push(i);
}
```

---

### 5. Binary Search
**Signal:** Sorted input, "find minimum X such that...", rotated array, 2D matrix.

| Problem | File | LC # |
|---------|------|------|
| Search Rotated Array | `problems/sorting/SearchRotatedArray.java` | 33 |
| Find Min in Rotated Array | `problems/sorting/FindMinInRotatedArray.java` | 153 |
| Search 2D Matrix | `problems/sorting/Search2DMatrix.java` | 74 |
| Find Peak Element | `problems/sorting/FindPeakElement.java` | 162 |
| Median of Two Sorted Arrays | `problems/sorting/MedianOfTwoSortedArrays.java` | 4 |

**Template:**
```java
int lo = 0, hi = n - 1;
while (lo <= hi) {
    int mid = lo + (hi - lo) / 2;
    if (nums[mid] == target) return mid;
    if (nums[mid] < target)  lo = mid + 1;
    else                     hi = mid - 1;
}
return -1;
```

---

### 6. BFS (Breadth-First Search)
**Signal:** Shortest path, level-by-level traversal, grid problems, word ladder.

| Problem | File | LC # |
|---------|------|------|
| Number of Islands | `problems/graphs/NumberOfIslands.java` | 200 |
| Word Ladder | `problems/graphs/WordLadder.java` | 127 |
| Pacific Atlantic Water Flow | `problems/graphs/PacificAtlanticWaterFlow.java` | 417 |
| Level Order Traversal | (via `Graph.bfs()`) | 102 |

**Template:**
```java
Queue<T> queue = new LinkedList<>();
Set<T> visited = new HashSet<>();
queue.offer(start); visited.add(start);
while (!queue.isEmpty()) {
    T node = queue.poll();
    for (T neighbor : neighbors(node))
        if (!visited.contains(neighbor)) { visited.add(neighbor); queue.offer(neighbor); }
}
```

---

### 7. DFS / Backtracking
**Signal:** All combinations/permutations/paths, constraint satisfaction, grid paths.

| Problem | File | LC # |
|---------|------|------|
| Subsets | `problems/backtracking/Subsets.java` | 78 |
| Permutations | `problems/backtracking/Permutations.java` | 46 |
| Combination Sum | `problems/backtracking/CombinationSum.java` | 39 |
| N-Queens | `problems/backtracking/NQueens.java` | 51 |
| Word Search | `problems/backtracking/WordSearch.java` | 79 |
| Word Search II | `problems/advanced/WordSearchII.java` | 212 |

**Template:**
```java
void backtrack(State state, List<Result> results) {
    if (isComplete(state)) { results.add(snapshot(state)); return; }
    for (Choice c : choices(state)) {
        makeChoice(state, c);   // choose
        backtrack(state, results);
        undoChoice(state, c);   // unchoose
    }
}
```

---

### 8. Dynamic Programming
**Signal:** "How many ways", optimal substructure, overlapping subproblems, "min/max cost".

#### 1D DP
| Problem | File | LC # | Pattern |
|---------|------|------|---------|
| Climbing Stairs | `problems/dp/ClimbingStairs.java` | 70 | Fibonacci |
| House Robber | `problems/dp/HouseRobber.java` | 198 | Skip adjacent |
| Coin Change | `problems/dp/CoinChange.java` | 322 | Unbounded knapsack |
| Jump Game | `problems/dp/JumpGame.java` | 55 | Greedy/DP |
| Decode Ways | `problems/dp/DecodeWays.java` | 91 | Fibonacci variant |
| Word Break | `problems/dp/WordBreak.java` | 139 | Reachability |
| Max Product Subarray | `problems/dp/MaximumProductSubarray.java` | 152 | Min/max tracking |
| Best Time to Buy Sell | `problems/dp/BestTimeToBuyAndSellStock.java` | 121 | One-pass |

#### 2D DP
| Problem | File | LC # | Pattern |
|---------|------|------|---------|
| Longest Common Subsequence | `problems/dp/LongestCommonSubsequence.java` | 1143 | String DP |
| Edit Distance | `problems/dp/EditDistance.java` | 72 | String DP |
| Unique Paths (with Obstacles) | `problems/dp/UniquePaths.java` | 63 | Grid DP |
| Maximal Square | `problems/dp/MaximalSquare.java` | 221 | Grid DP |
| Regex Matching | `problems/dp/RegularExpressionMatching.java` | 10 | String match |
| Burst Balloons | `problems/dp/BurstBalloons.java` | 312 | Interval DP |
| LPS | `problems/dp/LongestPalindromicSubsequence.java` | 516 | Interval DP |

#### Knapsack
| Problem | File | Pattern |
|---------|------|---------|
| 0/1 Knapsack | `problems/dp/ZeroOneKnapsack.java` | O/W choose |
| Partition Equal Subset Sum | `problems/dp/PartitionEqualSubsetSum.java` | 0/1 boolean |
| Coin Change II | `problems/dp/CoinChangeII.java` | Unbounded |
| Combination Sum IV | `problems/dp/CombinationSumIV.java` | Permutation count |

#### Binary Search + DP
| Problem | File | LC # |
|---------|------|------|
| LIS | `problems/dp/LongestIncreasingSubsequence.java` | 300 |

---

### 9. Union-Find (Disjoint Set)
**Signal:** Connected components, cycle detection in undirected graph.

| Problem | File | LC # |
|---------|------|------|
| Number of Provinces (component count) | (via `UnionFind`) | 547 |
| Min Cost to Connect Points | `problems/graphs/MinCostConnectPoints.java` | 1584 |

**Template:**
```java
UnionFind uf = new UnionFind(n);
for (int[] edge : edges) uf.union(edge[0], edge[1]);
return uf.componentCount();
```

---

### 10. Two Heaps
**Signal:** Median of stream, split elements into two halves, Kth smallest/largest dynamically.

| Problem | File | LC # |
|---------|------|------|
| Find Median from Data Stream | `problems/patterns/MedianFinder.java` | 295 |
| Kth Largest Element | `problems/patterns/KthLargestElement.java` | 215 |
| Top K Frequent Elements | `problems/hashtables/TopKFrequent.java` | 347 |
| Merge K Sorted Lists | `problems/patterns/MergeKSortedLists.java` | 23 |

**Two-Heaps Template:**
```java
PriorityQueue<Integer> maxHeap = new PriorityQueue<>(Collections.reverseOrder()); // lower half
PriorityQueue<Integer> minHeap = new PriorityQueue<>();                           // upper half
// Invariant: |maxHeap.size() - minHeap.size()| <= 1
//            max(maxHeap) <= min(minHeap)
```

---

### 11. Topological Sort
**Signal:** Ordering with dependencies, "can you finish all courses?", DAG.

| Problem | File | LC # |
|---------|------|------|
| Course Schedule | `problems/graphs/CourseSchedule.java` | 207 |
| Topological order | (via `Graph.topologicalSort()`) | 210 |

**Template (Kahn's BFS):**
```java
int[] indegree = computeIndegrees();
Queue<Integer> queue = getAllZeroIndegree();
List<Integer> order = new ArrayList<>();
while (!queue.isEmpty()) {
    int node = queue.poll();
    order.add(node);
    for (int neighbor : adj.get(node))
        if (--indegree[neighbor] == 0) queue.offer(neighbor);
}
return order.size() == numNodes; // false → cycle exists
```

---

### 12. Trie
**Signal:** Prefix search, word dictionary, autocomplete, word existence in grid.

| Problem | File | LC # |
|---------|------|------|
| Implement Trie | `datastructures/trees/Trie.java` | 208 |
| Add and Search Words | `problems/advanced/TrieProblems.java` | 211 |
| Word Search II | `problems/advanced/WordSearchII.java` | 212 |

---

### 13. Merge Intervals
**Signal:** Overlapping intervals, "minimum rooms", "meeting schedule".

| Problem | File | LC # |
|---------|------|------|
| Merge Intervals | `problems/patterns/MergeIntervals.java` | 56 |
| Insert Interval | `problems/patterns/InsertInterval.java` | 57 |
| Non-Overlapping Intervals | `problems/patterns/NonOverlappingIntervals.java` | 435 |
| Meeting Rooms II | `problems/patterns/MeetingRoomsII.java` | 253 |

---

### 14. Cyclic Sort
**Signal:** Array of integers in range [1, n], find missing/duplicate/all missing.

| Problem | File | LC # |
|---------|------|------|
| First Missing Positive | `problems/patterns/FirstMissingPositive.java` | 41 |
| Find Duplicate Number | `problems/patterns/FindDuplicateNumber.java` | 287 |

**Template:**
```java
for (int i = 0; i < n; i++)
    while (nums[i] > 0 && nums[i] <= n && nums[nums[i]-1] != nums[i]) {
        swap(nums, i, nums[i]-1);
    }
```

---

### 15. Segment Tree & Fenwick Tree
**Signal:** Range queries with point updates; immutable range queries.

| Problem | File | LC # |
|---------|------|------|
| Range Sum Query Mutable | `problems/advanced/RangeSumQueryMutable.java` | 307 |
| Count of Smaller Numbers | `problems/advanced/CountSmallerNumbersAfterSelf.java` | 315 |

---

## Quick Decision Flowchart

```
Is input SORTED?
├── Yes → Binary Search or Two Pointers
└── No  → Is it a graph/tree?
          ├── Yes → BFS (shortest path) or DFS (all paths/combinations)
          └── No  → Is it about COUNTING / MIN-MAX?
                    ├── Yes → Dynamic Programming
                    │         ├── 1D DP  (linear state)
                    │         ├── 2D DP  (two strings / grid)
                    │         └── Knapsack (subset selection)
                    └── No  → HashMap (frequency/complement)
                              Stack   (nested/matching structure)
                              Heap    (Kth element / stream median)
```

---

## Complexity Cheat Sheet

| Pattern | Time | Space |
|---------|------|-------|
| Two Pointer | O(n) | O(1) |
| Sliding Window | O(n) | O(k) |
| Fast & Slow | O(n) | O(1) |
| Monotonic Stack | O(n) | O(n) |
| Binary Search | O(log n) | O(1) |
| BFS | O(V+E) | O(V) |
| DFS/Backtracking | O(2^n) or O(n!) | O(n) |
| 1D DP | O(n) | O(n) or O(1) |
| 2D DP | O(m*n) | O(m*n) or O(n) |
| Union-Find | O(α(n)) per op | O(n) |
| Two Heaps | O(log n) per op | O(n) |
| Trie | O(m) per op | O(alphabet * chars) |
| Segment Tree | O(log n) query/update | O(n) |
| Fenwick Tree | O(log n) query/update | O(n) |
