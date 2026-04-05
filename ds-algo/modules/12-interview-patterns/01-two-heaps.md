# 12.1 — Two Heaps Pattern

## Concept

The **Two Heaps** pattern maintains two complementary heaps simultaneously — a **max-heap** for the lower half of a stream and a **min-heap** for the upper half. This lets you access the median (or any split statistic) in O(1) at any point while supporting O(log n) insertions. It appears in interviews whenever you see the words "stream", "median", or "continuously add elements and query".

---

## Deep Dive

### The Core Invariant

The two heaps must always satisfy:
1. Every element in `maxHeap` (lower half) ≤ every element in `minHeap` (upper half)
2. Their sizes differ by at most 1 — always balanced

```
Stream so far: [2, 7, 5, 1, 8]

         maxHeap (lower half)          minHeap (upper half)
              max-heap                     min-heap
           ┌──────────┐                ┌──────────┐
           │    5     │  ← max of lower│    7     │  ← min of upper
           ├──────────┤                ├──────────┤
           │   2  1   │                │    8     │
           └──────────┘                └──────────┘
           size = 3                    size = 2

Median = maxHeap.peek() = 5   (odd total → take from larger heap)
```

### Insertion Algorithm

```
Insert x:
  Step 1: Add x to maxHeap (always insert lower half first)
  Step 2: Move maxHeap.peek() to minHeap (maintain lower ≤ upper invariant)
  Step 3: Rebalance sizes:
            if minHeap.size() > maxHeap.size():
                move minHeap.peek() to maxHeap

Example: insert 3 into stream [2, 5, 7]
  Before: maxHeap=[5,2], minHeap=[7]
  After step 1: maxHeap=[5,3,2]
  After step 2: maxHeap=[3,2], minHeap=[5,7]
  After step 3: sizes 2 and 2 — balanced ✓
```

### Median Extraction

```
if sizes equal:    median = (maxHeap.peek() + minHeap.peek()) / 2.0
if maxHeap larger: median = maxHeap.peek()
```

---

## Code Examples

### Example 1: Find Median from Data Stream (LC #295)

```java
import java.util.PriorityQueue;
import java.util.Collections;

public class MedianFinder {
    // max-heap: lower half — Java PQ is a min-heap, negate values for max-heap
    private PriorityQueue<Integer> maxHeap = new PriorityQueue<>(Collections.reverseOrder());
    // min-heap: upper half
    private PriorityQueue<Integer> minHeap = new PriorityQueue<>();

    public void addNum(int num) {
        // Step 1: always push to lower half first
        maxHeap.offer(num);

        // Step 2: enforce lower ≤ upper
        minHeap.offer(maxHeap.poll());

        // Step 3: rebalance — maxHeap should have >= elements
        if (minHeap.size() > maxHeap.size()) {
            maxHeap.offer(minHeap.poll());
        }
    }

    public double findMedian() {
        if (maxHeap.size() == minHeap.size()) {
            return (maxHeap.peek() + minHeap.peek()) / 2.0;
        }
        return maxHeap.peek();  // maxHeap is always larger or equal
    }
}

// Usage:
// MedianFinder mf = new MedianFinder();
// mf.addNum(1);  mf.findMedian() → 1.0
// mf.addNum(2);  mf.findMedian() → 1.5
// mf.addNum(3);  mf.findMedian() → 2.0
```

### Example 2: IPO — Maximize Capital (LC #502)

The Two Heaps pattern also applies to optimization with constraints: use a max-heap to always pick the most profitable available task, and a min-heap to surface tasks that have become available as capital grows.

```java
public int findMaximizedCapital(int k, int w, int[] profits, int[] capital) {
    int n = profits.length;

    // Min-heap sorted by capital requirement: [capital, profit]
    PriorityQueue<int[]> available = new PriorityQueue<>(Comparator.comparingInt(a -> a[0]));
    // Max-heap sorted by profit
    PriorityQueue<int[]> maxProfit = new PriorityQueue<>((a, b) -> b[1] - a[1]);

    for (int i = 0; i < n; i++)
        available.offer(new int[]{capital[i], profits[i]});

    for (int i = 0; i < k; i++) {
        // Unlock all projects we can afford
        while (!available.isEmpty() && available.peek()[0] <= w)
            maxProfit.offer(available.poll());

        if (maxProfit.isEmpty()) break;   // no affordable projects left
        w += maxProfit.poll()[1];         // pick the most profitable
    }
    return w;
}
```

---

## Try It Yourself

**Exercise:** Given the stream `[5, 15, 1, 3]`, trace the state of both heaps after each insertion and compute the median at each step.

```java
// Trace template:
// After addNum(5):  maxHeap=[?], minHeap=[?], median=?
// After addNum(15): maxHeap=[?], minHeap=[?], median=?
// After addNum(1):  maxHeap=[?], minHeap=[?], median=?
// After addNum(3):  maxHeap=[?], minHeap=[?], median=?
```

<details>
<summary>Show expected trace</summary>

```
After addNum(5):
  Step 1: maxHeap=[5]
  Step 2: minHeap=[5], maxHeap=[]
  Step 3: maxHeap=[5], minHeap=[]   (rebalance: minHeap had 1, maxHeap 0)
  median = 5.0

After addNum(15):
  Step 1: maxHeap=[15,5] (15 goes into lower first)
  Step 2: maxHeap=[5], minHeap=[15]  (move max of lower to upper)
  Step 3: sizes 1,1 — balanced
  median = (5 + 15) / 2.0 = 10.0

After addNum(1):
  Step 1: maxHeap=[5,1]
  Step 2: maxHeap=[1], minHeap=[5,15]
  Step 3: minHeap.size()=2 > maxHeap.size()=1 → move 5 to maxHeap
          maxHeap=[5,1], minHeap=[15]
  median = 5.0

After addNum(3):
  Step 1: maxHeap=[5,3,1]
  Step 2: maxHeap=[3,1], minHeap=[5,15]
  Step 3: sizes 2,2 — balanced
  median = (3 + 5) / 2.0 = 4.0
```
</details>

---

## Capstone Connection

Add `MedianFinder.java` to `AlgoForge/problems/patterns/`. This class exercises everything from Module 04 (Priority Queue mechanics) and Module 08 (heap internals) in a single, interview-ready implementation. It also forms the core of Exercise 1 in this module's exercises.
