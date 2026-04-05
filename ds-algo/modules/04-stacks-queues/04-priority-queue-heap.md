# 4.4 — Priority Queue & Heap

## Concept

A **heap** is a complete binary tree satisfying the heap property: in a **min-heap**, every parent ≤ its children; in a **max-heap**, every parent ≥ its children. It gives O(log n) insert and O(log n) remove-min/max, with O(1) peek at the extremum. Java's `PriorityQueue` is a min-heap by default.

---

## Deep Dive

### Heap Structure (Min-Heap)

```
Min-Heap for: [1, 3, 5, 7, 9, 8, 6]

          1         ← index 0 (root = minimum)
        /   \
       3     5      ← indices 1, 2
      / \   / \
     7   9 8   6    ← indices 3,4,5,6

Parent of i:       (i-1) / 2
Left child of i:    2i + 1
Right child of i:   2i + 2

Insert 2: add at end, then "bubble up" (sift up):
          1
        /   \
       2     5      (2 swapped up past 3)
      / \   / \
     3   9 8   6
    /
   7              ← 7 pushed down one level
```

### Sift Up / Sift Down

```
Sift up (after insert): compare with parent, swap if smaller.
Sift down (after remove root): move last element to root, compare with
                                children, swap with smallest child.
Both operations: O(log n) — tree height.
```

---

## Code Examples

### Java PriorityQueue

```java
// Min-heap (default)
PriorityQueue<Integer> minPQ = new PriorityQueue<>();
minPQ.offer(5); minPQ.offer(1); minPQ.offer(3);
minPQ.peek();   // 1 — peek minimum
minPQ.poll();   // 1 — remove minimum

// Max-heap: reverse the comparator
PriorityQueue<Integer> maxPQ = new PriorityQueue<>(Comparator.reverseOrder());

// Custom objects: sort by absolute value
PriorityQueue<int[]> pq = new PriorityQueue<>((a, b) -> a[0] - b[0]);
```

### Top K Frequent Elements (LC #347)

```java
public int[] topKFrequent(int[] nums, int k) {
    // Count frequencies
    Map<Integer, Integer> freq = new HashMap<>();
    for (int n : nums) freq.merge(n, 1, Integer::sum);

    // Min-heap of size k keyed on frequency
    PriorityQueue<Map.Entry<Integer, Integer>> pq =
        new PriorityQueue<>(Comparator.comparingInt(Map.Entry::getValue));

    for (Map.Entry<Integer, Integer> e : freq.entrySet()) {
        pq.offer(e);
        if (pq.size() > k) pq.poll();   // evict least frequent
    }

    int[] result = new int[k];
    for (int i = k - 1; i >= 0; i--) result[i] = pq.poll().getKey();
    return result;
    // Time: O(n log k), Space: O(n)
}
```

### Find Median from Data Stream (LC #295)

```java
// Two heaps: maxHeap for lower half, minHeap for upper half.
// Invariant: maxHeap.size() >= minHeap.size(), and max(maxHeap) <= min(minHeap)

class MedianFinder {
    private PriorityQueue<Integer> lower = new PriorityQueue<>(Comparator.reverseOrder());
    private PriorityQueue<Integer> upper = new PriorityQueue<>();

    public void addNum(int num) {
        lower.offer(num);                          // always add to lower first
        upper.offer(lower.poll());                 // balance: push lower's max to upper
        if (lower.size() < upper.size())           // keep lower ≥ upper in size
            lower.offer(upper.poll());
    }

    public double findMedian() {
        if (lower.size() > upper.size()) return lower.peek();
        return (lower.peek() + upper.peek()) / 2.0;
    }
    // Time: O(log n) per addNum, O(1) per findMedian
}
```

---

## Try It Yourself

**Exercise:** Given an array of integers, return the "K closest points to the origin" (LC #973). Euclidean distance: `sqrt(x²+y²)` — but you can compare `x²+y²` directly to avoid the sqrt.

Input: `[[1,3],[-2,2]]`, k=1 → `[[-2,2]]`

<details>
<summary>Show solution</summary>

```java
public int[][] kClosest(int[][] points, int k) {
    // Max-heap of size k: evict the farthest point so far
    PriorityQueue<int[]> maxHeap = new PriorityQueue<>(
        (a, b) -> (b[0]*b[0] + b[1]*b[1]) - (a[0]*a[0] + a[1]*a[1]));

    for (int[] p : points) {
        maxHeap.offer(p);
        if (maxHeap.size() > k) maxHeap.poll();
    }

    return maxHeap.toArray(new int[0][]);
    // Time: O(n log k), Space: O(k)
}
```

</details>

---

## Capstone Connection

The two-heaps pattern for median is one of the most reusable templates you'll encounter. `PriorityQueue` is also central to Dijkstra's algorithm (Module 09) and Merge K Sorted Lists (Module 03 exercises). Build a solid intuition for when to use max-heap vs min-heap.
