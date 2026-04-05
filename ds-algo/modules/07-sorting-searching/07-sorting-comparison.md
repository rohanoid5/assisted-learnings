# 7.7 — Algorithm Comparison & Applications

## Concept

Knowing algorithms is half the battle. The other half is knowing **which algorithm to reach for** given the input characteristics. This topic crystallises the comparison into a reference table and covers three non-obvious but important sorting applications: counting sort, radix sort, and the decision of when NOT to sort.

---

## Deep Dive

### The Comparison Table

| Algorithm | Best | Average | Worst | Space | Stable | In-Place | Notes |
|-----------|------|---------|-------|-------|--------|----------|-------|
| Bubble Sort | O(n) | O(n²) | O(n²) | O(1) | ✓ | ✓ | Early exit on sorted; educational only |
| Selection Sort | O(n²) | O(n²) | O(n²) | O(1) | ✗ | ✓ | Min swaps; good for minimizing writes |
| Insertion Sort | O(n) | O(n²) | O(n²) | O(1) | ✓ | ✓ | Best for tiny or nearly-sorted arrays |
| Merge Sort | O(n log n) | O(n log n) | O(n log n) | O(n) | ✓ | ✗ | Guaranteed O(n log n); sort linked lists |
| Quick Sort | O(n log n) | O(n log n) | O(n²) | O(log n) | ✗ | ✓ | Fastest in practice; random pivot for safety |
| Heap Sort | O(n log n) | O(n log n) | O(n log n) | O(1) | ✗ | ✓ | Guaranteed time + in-place; poor cache perf |
| Counting Sort | O(n+k) | O(n+k) | O(n+k) | O(k) | ✓ | ✗ | Only for integers in a known range [0..k] |
| Radix Sort | O(nk) | O(nk) | O(nk) | O(n+k) | ✓ | ✗ | For fixed-width integers; k = number of digits |
| TimSort (Java) | O(n) | O(n log n) | O(n log n) | O(n) | ✓ | ✗ | Hybrid Merge+Insertion; Java's `Arrays.sort(Object[])` |

---

### Decision Guide

```
Input characteristics → best algorithm:

Tiny array (n < 16):        Insertion Sort (lowest constant factor)
Nearly sorted:              Insertion Sort (O(n) for few inversions)
Guaranteed O(n log n)?      Merge Sort or Heap Sort
Minimum extra space?        Quick Sort (avg) or Heap Sort (worst)
Stability required?         Merge Sort or TimSort
Linked list:                Merge Sort (no random access needed)
Integers, small range:      Counting Sort (O(n+k))
String/fixed-width keys:    Radix Sort
Production Java:            Arrays.sort() — already optimal
```

---

### Counting Sort

```java
// Sort array of integers in range [0..k]
public void countingSort(int[] arr, int k) {
    int[] count = new int[k + 1];
    for (int n : arr) count[n]++;           // count occurrences
    int idx = 0;
    for (int i = 0; i <= k; i++)
        while (count[i]-- > 0) arr[idx++] = i;  // reconstruct
    // Time: O(n + k),  Space: O(k)
    // Extremely fast when k is small (e.g., sort by age, grade, digit)
}
```

### Sort Colors (Dutch National Flag — LC #75)

```java
// Sort [2,0,2,1,1,0] → [0,0,1,1,2,2]  in O(n), O(1) space
// Three-way partition (Dijkstra's algorithm)
public void sortColors(int[] nums) {
    int low = 0, mid = 0, high = nums.length - 1;
    while (mid <= high) {
        if      (nums[mid] == 0) { swap(nums, low++, mid++); }
        else if (nums[mid] == 1) { mid++; }
        else                     { swap(nums, mid, high--); }
        // Note: when swapping with high, don't increment mid (unexamined)
    }
}
```

---

## Try It Yourself

**Exercise:** **Merge Intervals** (LC #56). Given an array of intervals, merge all overlapping intervals.

```java
// Input:  [[1,3],[2,6],[8,10],[15,18]]  →  [[1,6],[8,10],[15,18]]
// Input:  [[1,4],[4,5]]                 →  [[1,5]]

public int[][] merge(int[][] intervals) {
    // Hint: sort by start time, then greedily merge
}
```

<details>
<summary>Solution</summary>

```java
public int[][] merge(int[][] intervals) {
    Arrays.sort(intervals, (a, b) -> a[0] - b[0]);  // sort by start

    List<int[]> merged = new ArrayList<>();
    for (int[] interval : intervals) {
        if (merged.isEmpty() || merged.get(merged.size()-1)[1] < interval[0]) {
            merged.add(interval);  // no overlap — add new interval
        } else {
            // Overlap — extend the last interval's end if needed
            merged.get(merged.size()-1)[1] = Math.max(merged.get(merged.size()-1)[1], interval[1]);
        }
    }
    return merged.toArray(new int[0][]);
    // Time: O(n log n) — dominated by sort.  Space: O(n) for output.
}
```

</details>

---

## Capstone Connection

This comparison table is placed in AlgoForge's root `README.md` as a quick reference. Before any interview, re-read the decision guide — "which sort would you use and why?" is a very common follow-up question that this topic trains you to answer immediately.
