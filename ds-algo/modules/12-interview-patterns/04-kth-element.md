# 12.4 — Kth Element Pattern

## Concept

Finding the **Kth largest or smallest element** is a recurring interview theme with multiple solutions at different complexity trade-offs. The two canonical approaches are **QuickSelect** (expected O(n), in-place) and a **K-size heap** (O(n log K), streaming-compatible). Knowing both and when to choose each is what separates junior from senior candidates.

---

## Deep Dive

### Approach Comparison

```
                  QuickSelect       K-Size Heap       Sorting
Time (average)    O(n)              O(n log K)         O(n log n)
Time (worst)      O(n²)             O(n log K)         O(n log n)
Space             O(1) in-place     O(K)               O(1) – O(n)
Streaming?        No (need all      Yes (process        No
                  data in memory)   one at a time)
Modifies input?   Yes               No                  Yes/No
```

**Rule of thumb:**
- Need K-th from a complete array → QuickSelect
- Elements arrive as a stream or array is huge → K-size heap
- Already sorted, small K → just index directly

### QuickSelect Algorithm

QuickSelect adapts the partitioning step from QuickSort to avoid sorting the parts you don't care about.

```
Goal: find the K-th largest in [3,2,1,5,6,4], K=2

After Lomuto partition around pivot=4:
  [3,2,1,4, 5,6]   pivot lands at index 3 (0-based)
  Left side: 3 elements < 4
  Right side: 2 elements > 4

  We want K=2 largest → K-th largest = (n-K)-th smallest = index (6-2)=4 in 0-based sorted order
  pivot is at index 3, target is 4 → recurse right

After partition of [5,6] around pivot=6:
  pivot lands at index 5 → target is 4 → recurse left into [5]
  → return 5 ✓

Key: only recurse into the half that contains index (n-K)
     Expected depth: O(log n), total work: O(n) + O(n/2) + O(n/4) + ... = O(2n) = O(n)
```

### K-Size Heap Approach

Maintain a **min-heap of size K**. After processing all elements, the heap top is the K-th largest.

```
K=2, stream=[3,2,1,5,6,4]:

Add 3: heap=[3]          (size < K, just add)
Add 2: heap=[2,3]        (size < K, just add)
Add 1: heap=[2,3]        1 < heap.peek()=2 → discard 1 (can't be in top-K)
Add 5: heap=[3,5]        5 > heap.peek()=2 → remove 2, add 5
Add 6: heap=[5,6]        6 > 3 → remove 3, add 6
Add 4: heap=[5,6]        4 < 5 → discard 4

heap.peek() = 5 → K-th largest ✓
```

---

## Code Examples

### Example 1: Kth Largest Element (LC #215) — QuickSelect

```java
public int findKthLargest(int[] nums, int k) {
    // K-th largest = (n-k)-th smallest (0-based index)
    return quickSelect(nums, 0, nums.length - 1, nums.length - k);
}

private int quickSelect(int[] nums, int left, int right, int targetIdx) {
    if (left == right) return nums[left];

    int pivotIdx = partition(nums, left, right);

    if (pivotIdx == targetIdx)      return nums[pivotIdx];
    else if (pivotIdx < targetIdx)  return quickSelect(nums, pivotIdx + 1, right, targetIdx);
    else                            return quickSelect(nums, left, pivotIdx - 1, targetIdx);
}

// Lomuto partition with random pivot (avoids O(n²) worst case on sorted input)
private int partition(int[] nums, int left, int right) {
    // Randomize to avoid worst case
    int randIdx = left + (int)(Math.random() * (right - left + 1));
    swap(nums, randIdx, right);   // move pivot to end

    int pivot = nums[right], storeIdx = left;
    for (int i = left; i < right; i++) {
        if (nums[i] <= pivot)
            swap(nums, i, storeIdx++);
    }
    swap(nums, storeIdx, right);
    return storeIdx;
}

private void swap(int[] arr, int i, int j) {
    int tmp = arr[i]; arr[i] = arr[j]; arr[j] = tmp;
}
```

### Example 2: Kth Largest Element — K-Size Min-Heap

```java
public int findKthLargestHeap(int[] nums, int k) {
    PriorityQueue<Integer> minHeap = new PriorityQueue<>(k);

    for (int num : nums) {
        minHeap.offer(num);
        if (minHeap.size() > k)
            minHeap.poll();    // remove the smallest — keep only top K
    }
    return minHeap.peek();     // smallest of the top K = K-th largest
}
```

### Example 3: K Closest Points to Origin (LC #973)

Extend to objects using a max-heap of size K (opposite polarity — keep K smallest distances).

```java
public int[][] kClosest(int[][] points, int k) {
    // Max-heap by distance (to keep the K smallest distances)
    PriorityQueue<int[]> maxHeap = new PriorityQueue<>(
        (a, b) -> dist(b) - dist(a)
    );

    for (int[] p : points) {
        maxHeap.offer(p);
        if (maxHeap.size() > k)
            maxHeap.poll();    // remove the farthest
    }

    return maxHeap.toArray(new int[0][]);
}

private int dist(int[] p) {
    return p[0]*p[0] + p[1]*p[1];   // squared distance (avoids sqrt, same ordering)
}
```

### Example 4: Top K Frequent Elements (LC #347)

Combine frequency counting with a K-size min-heap.

```java
public int[] topKFrequent(int[] nums, int k) {
    // Step 1: count frequencies
    Map<Integer, Integer> freq = new HashMap<>();
    for (int n : nums) freq.merge(n, 1, Integer::sum);

    // Step 2: min-heap of size k, keyed by frequency
    PriorityQueue<Map.Entry<Integer,Integer>> minHeap = new PriorityQueue<>(
        Comparator.comparingInt(Map.Entry::getValue)
    );

    for (Map.Entry<Integer,Integer> e : freq.entrySet()) {
        minHeap.offer(e);
        if (minHeap.size() > k) minHeap.poll();
    }

    // Step 3: extract
    return minHeap.stream().mapToInt(Map.Entry::getKey).toArray();
}

// O(n) alternative: Bucket Sort — if you know values are in [1..n]
public int[] topKFrequentBucket(int[] nums, int k) {
    Map<Integer, Integer> freq = new HashMap<>();
    for (int n : nums) freq.merge(n, 1, Integer::sum);

    @SuppressWarnings("unchecked")
    List<Integer>[] buckets = new List[nums.length + 1];
    for (int key : freq.keySet()) {
        int f = freq.get(key);
        if (buckets[f] == null) buckets[f] = new ArrayList<>();
        buckets[f].add(key);
    }

    List<Integer> result = new ArrayList<>();
    for (int i = buckets.length - 1; i >= 0 && result.size() < k; i--)
        if (buckets[i] != null) result.addAll(buckets[i]);

    return result.stream().mapToInt(Integer::intValue).toArray();
}
```

---

## Try It Yourself

**Exercise:** Trace QuickSelect on `[7, 10, 4, 3, 20, 15]`, finding the 3rd largest (K=3). Use the last element as the pivot each time.

```java
// Array: [7, 10, 4, 3, 20, 15]
// Target: 3rd largest = index 3 (0-based) in sorted order
// Expected: 10
```

<details>
<summary>Show trace</summary>

```
Target index = 6 - 3 = 3 (0-based, in sorted order)

Partition [7,10,4,3,20,15], pivot=15:
  i=0: 7  ≤ 15 → swap with storeIdx=0, storeIdx=1
  i=1: 10 ≤ 15 → swap with storeIdx=1, storeIdx=2
  i=2: 4  ≤ 15 → swap with storeIdx=2, storeIdx=3
  i=3: 3  ≤ 15 → swap with storeIdx=3, storeIdx=4
  i=4: 20 > 15 → no swap
  swap(storeIdx=4, right=5): [7,10,4,3,15,20] → nope, let me re-trace:
  
  After loop: elements ≤ 15 at indices 0..3 = [7,10,4,3], pivot=15 at index 4
  Array: [7,10,4,3,15,20], pivotIdx=4

  targetIdx=3 < pivotIdx=4 → recurse left on [7,10,4,3]

Partition [7,10,4,3], pivot=3:
  No elements ≤ 3 except 3 itself
  After loop: [3,10,4,7], pivotIdx=0

  targetIdx=3 > pivotIdx=0 → recurse right on [10,4,7]
  (now working on indices 1..3 of original array with targetIdx still 3)

Partition [10,4,7], pivot=7:
  i: 10>7 → skip, 4≤7 → swap
  After: [4,7,10] but in original: [3,4,7,10,...], pivotIdx=2

  targetIdx=3 > pivotIdx=2 → recurse right on [10]
  → return 10 ✓

3rd largest = 10
Sorted: [3,4,7,10,15,20] → index 3 = 10 ✓
```
</details>

---

## Capstone Connection

Add `KthLargest.java`, `KClosestPoints.java`, and `TopKFrequent.java` to `AlgoForge/problems/patterns/`. The heap-based version is essential for streaming systems (e.g., finding top-K searches in real time), a common system design interview scenario.
