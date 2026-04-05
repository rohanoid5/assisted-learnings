# Module 07: Sorting & Searching — Exercises

## Overview

These exercises cover the most-asked sorting and searching patterns in FAANG interviews. Each problem is chosen because it requires more than "just sort it" — you need to understand the algorithm to solve it efficiently.

---

## Exercise 1: Sort Colors (LC #75)

**Goal:** Sort an array containing only 0s, 1s, and 2s in-place using O(n) time and O(1) space.

```
Input:  [2,0,2,1,1,0]  →  [0,0,1,1,2,2]
Input:  [2,0,1]        →  [0,1,2]
```

1. Implement Dijkstra's Dutch National Flag algorithm (three pointers: `low`, `mid`, `high`).
2. Key invariant: `arr[0..low-1]` = 0s, `arr[low..mid-1]` = 1s, `arr[mid..high]` = unseen, `arr[high+1..n-1]` = 2s.
3. Be careful: when swapping with `high`, don't advance `mid` (the swapped value is unseen).

<details>
<summary>Solution</summary>

```java
public void sortColors(int[] nums) {
    int low = 0, mid = 0, high = nums.length - 1;
    while (mid <= high) {
        if (nums[mid] == 0) {
            int tmp = nums[low]; nums[low] = nums[mid]; nums[mid] = tmp;
            low++; mid++;
        } else if (nums[mid] == 1) {
            mid++;
        } else {
            int tmp = nums[mid]; nums[mid] = nums[high]; nums[high] = tmp;
            high--;
            // DO NOT increment mid — newly swapped value at mid is unseen
        }
    }
}
```

</details>

---

## Exercise 2: Merge Intervals (LC #56)

**Goal:** Given an array of intervals, merge all overlapping intervals and return the result.

```
Input:  [[1,3],[2,6],[8,10],[15,18]]  →  [[1,6],[8,10],[15,18]]
Input:  [[1,4],[4,5]]                  →  [[1,5]]
```

1. Sort by start time.
2. Iterate — if the current interval overlaps with the last merged interval (cur.start ≤ last.end), extend last.end. Otherwise, add a new interval.
3. Test edge cases: single interval, all intervals overlapping, no intervals overlapping.

<details>
<summary>Solution</summary>

```java
public int[][] merge(int[][] intervals) {
    Arrays.sort(intervals, (a, b) -> a[0] - b[0]);
    List<int[]> result = new ArrayList<>();
    for (int[] cur : intervals) {
        if (result.isEmpty() || result.get(result.size()-1)[1] < cur[0]) {
            result.add(cur);
        } else {
            result.get(result.size()-1)[1] = Math.max(result.get(result.size()-1)[1], cur[1]);
        }
    }
    return result.toArray(new int[0][]);
}
```

</details>

---

## Exercise 3: Kth Largest Element (LC #215)

**Goal:** Find the kth largest element in an unsorted array in O(n) average time.

```
Input:  [3,2,1,5,6,4], k=2  →  5
Input:  [3,2,3,1,2,4,5,5,6], k=4  →  4
```

1. Implement using QuickSelect (random pivot for O(n) average case).
2. The kth largest has index `n-k` in a 0-indexed sorted array.
3. Compare with the heap approach: O(n log k) time, O(k) space using a min-heap of size k.

<details>
<summary>Solution</summary>

```java
public int findKthLargest(int[] nums, int k) {
    return quickSelect(nums, 0, nums.length - 1, nums.length - k);
}

private int quickSelect(int[] nums, int lo, int hi, int targetIdx) {
    int p = partition(nums, lo, hi);
    if (p == targetIdx) return nums[p];
    return p < targetIdx ? quickSelect(nums, p+1, hi, targetIdx)
                         : quickSelect(nums, lo, p-1, targetIdx);
}

private int partition(int[] nums, int lo, int hi) {
    int rand = lo + (int)(Math.random() * (hi - lo + 1));
    swap(nums, rand, hi);
    int pivot = nums[hi], i = lo - 1;
    for (int j = lo; j < hi; j++)
        if (nums[j] <= pivot) swap(nums, ++i, j);
    swap(nums, i+1, hi);
    return i + 1;
}

private void swap(int[] a, int i, int j) { int t=a[i]; a[i]=a[j]; a[j]=t; }

// Heap alternative (always O(n log k)):
public int findKthLargestHeap(int[] nums, int k) {
    PriorityQueue<Integer> minHeap = new PriorityQueue<>(k);
    for (int n : nums) {
        minHeap.offer(n);
        if (minHeap.size() > k) minHeap.poll();
    }
    return minHeap.peek();
}
```

</details>

---

## Exercise 4: Search in Rotated Sorted Array (LC #33)

**Goal:** Search for `target` in a rotated sorted array (no duplicates). Return index or -1. Must run in O(log n).

```
Input:  [4,5,6,7,0,1,2], target=0  →  4
Input:  [4,5,6,7,0,1,2], target=3  →  -1
Input:  [1], target=0               →  -1
```

1. Determine which half is sorted by comparing `arr[left]` with `arr[mid]`.
2. Check if `target` falls in the sorted half — if so, search there; otherwise search the other half.
3. Follow-up: **Search in Rotated Sorted Array II** (LC #81) contains duplicates — what changes?

<details>
<summary>Solution</summary>

```java
public int search(int[] nums, int target) {
    int left = 0, right = nums.length - 1;
    while (left <= right) {
        int mid = left + (right - left) / 2;
        if (nums[mid] == target) return mid;
        if (nums[left] <= nums[mid]) {  // left half is sorted
            if (nums[left] <= target && target < nums[mid]) right = mid - 1;
            else left = mid + 1;
        } else {                         // right half is sorted
            if (nums[mid] < target && target <= nums[right]) left = mid + 1;
            else right = mid - 1;
        }
    }
    return -1;
}

// With duplicates (LC #81): when nums[left] == nums[mid] (can't tell which half is sorted)
// → left++  to skip the ambiguous duplicate
// Worst case becomes O(n) (e.g., [1,1,1,1,1,2,1,1])
```

</details>

---

## Exercise 5: Median of Two Sorted Arrays (LC #4)

**Goal:** Find the median of two sorted arrays. Must run in O(log(m+n)).

```
Input:  nums1=[1,3], nums2=[2]  →  2.0
Input:  nums1=[1,2], nums2=[3,4]  →  2.5
```

This is a hard problem. The key insight: binary search for the correct partition point in the smaller array.

1. Ensure `nums1` is the shorter array.
2. Binary search a `cut1` in `nums1` such that elements ≤ cut1 form the "left half" of the merged array.
3. The median is derived from the boundary elements of both partitions.

<details>
<summary>Solution</summary>

```java
public double findMedianSortedArrays(int[] A, int[] B) {
    if (A.length > B.length) return findMedianSortedArrays(B, A);  // ensure A is shorter

    int m = A.length, n = B.length;
    int left = 0, right = m;
    while (left <= right) {
        int cut1 = (left + right) / 2;       // partition in A
        int cut2 = (m + n + 1) / 2 - cut1;  // partition in B

        int maxL1 = (cut1 == 0) ? Integer.MIN_VALUE : A[cut1 - 1];
        int minR1 = (cut1 == m) ? Integer.MAX_VALUE : A[cut1];
        int maxL2 = (cut2 == 0) ? Integer.MIN_VALUE : B[cut2 - 1];
        int minR2 = (cut2 == n) ? Integer.MAX_VALUE : B[cut2];

        if (maxL1 <= minR2 && maxL2 <= minR1) {
            if ((m + n) % 2 == 0) return (Math.max(maxL1,maxL2) + Math.min(minR1,minR2)) / 2.0;
            else return Math.max(maxL1, maxL2);
        } else if (maxL1 > minR2) right = cut1 - 1;
        else left = cut1 + 1;
    }
    throw new IllegalArgumentException();
}
// Time: O(log(min(m,n))), Space: O(1)
```

</details>

---

## Capstone Checkpoint ✅

By completing these exercises you have:
- [x] Applied the **Dutch National Flag** three-pointer pattern
- [x] Used **sort + greedy scan** for interval merging
- [x] Implemented **QuickSelect** for O(n) average order-statistic
- [x] Applied the **rotated-array binary search** discriminant
- [x] Solved the hardest binary search problem (Median of Two Sorted Arrays)

Add all solutions to `capstone/algoforge/src/main/java/com/algoforge/problems/sorting/`.
