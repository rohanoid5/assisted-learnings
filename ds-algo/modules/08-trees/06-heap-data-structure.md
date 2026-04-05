# 8.6 — Heap Data Structure

## What Is a Heap?

A **heap** is a complete binary tree that satisfies the **heap property**:

- **Min-Heap:** Parent ≤ both children → root is always the minimum
- **Max-Heap:** Parent ≥ both children → root is always the maximum

```
Min-Heap:              Max-Heap:
       1                     9
      / \                   / \
     3   2                 7   6
    / \ / \               / \ /
   8  5 4  6             3  2 5
```

Heaps power priority queues, heap sort, Dijkstra's algorithm, and the "top K" pattern.

---

## Array Representation

The elegance of a heap: store it in an array using index arithmetic — no pointers needed!

```
Array: [1, 3, 2, 8, 5, 4, 6]
Index:  0  1  2  3  4  5  6

       i=0 → 1
       i=1 → 3          i=2 → 2
       i=3 → 8  i=4 → 5  i=5 → 4  i=6 → 6

For 0-indexed array:
  Parent of i    = (i - 1) / 2
  Left child     = 2*i + 1
  Right child    = 2*i + 2
```

---

## Core Operations

### 1. Insertion (siftUp)

Add to the end, then bubble up:

```
Insert 0 into min-heap [1, 3, 2, 8, 5, 4, 6]:

Step 1: Append → [1, 3, 2, 8, 5, 4, 6, 0]
                                          ^
Step 2: 0 at index 7, parent = index 3 (value 8)
        0 < 8 → swap → [1, 3, 2, 0, 5, 4, 6, 8]
Step 3: 0 at index 3, parent = index 1 (value 3)
        0 < 3 → swap → [1, 0, 2, 3, 5, 4, 6, 8]
Step 4: 0 at index 1, parent = index 0 (value 1)
        0 < 1 → swap → [0, 1, 2, 3, 5, 4, 6, 8]
Step 5: 0 at index 0, no parent → done
```

```java
private void siftUp(int[] heap, int index) {
    while (index > 0) {
        int parent = (index - 1) / 2;
        if (heap[index] < heap[parent]) {   // min-heap
            swap(heap, index, parent);
            index = parent;
        } else break;
    }
}
```

### 2. Extract Min (siftDown)

Swap root with last element, remove last, then bubble down:

```
Extract min from [1, 3, 2, 8, 5, 4, 6]:

Step 1: Save min = 1, move last to root → [6, 3, 2, 8, 5, 4]
Step 2: 6 at index 0. Children: 3 (idx 1), 2 (idx 2). Min child = 2.
        6 > 2 → swap → [2, 3, 6, 8, 5, 4]
Step 3: 6 at index 2. Children: 4 (idx 5). 
        6 > 4 → swap → [2, 3, 4, 8, 5, 6]
Step 4: 6 at index 5. No children (leaf) → done
Result: min=1, heap=[2,3,4,8,5,6]
```

```java
private void siftDown(int[] heap, int index, int size) {
    while (true) {
        int smallest = index;
        int left  = 2 * index + 1;
        int right = 2 * index + 2;

        if (left  < size && heap[left]  < heap[smallest]) smallest = left;
        if (right < size && heap[right] < heap[smallest]) smallest = right;

        if (smallest != index) {
            swap(heap, index, smallest);
            index = smallest;
        } else break;
    }
}
```

### 3. Heapify — Build Heap from Array in O(n)

Naive approach: insert n elements one by one = O(n log n).
`heapify` approach: sift down from the last internal node up = O(n).

```java
public void heapify(int[] arr) {
    int n = arr.length;
    // Start from last non-leaf node = (n/2 - 1), down to root
    for (int i = n / 2 - 1; i >= 0; i--) {
        siftDown(arr, i, n);
    }
}
```

**Why O(n)?** Most nodes are near the bottom and need few swaps. The sum of work across all levels telescopes to O(n). (Proof: $\sum_{h=0}^{\log n} \frac{n}{2^{h+1}} \cdot h = O(n)$)

---

## Full MinHeap Implementation

```java
public class MinHeap {
    private int[] data;
    private int size;
    private final int capacity;

    public MinHeap(int capacity) {
        this.capacity = capacity;
        this.data = new int[capacity];
        this.size = 0;
    }

    public void insert(int val) {
        if (size == capacity) throw new RuntimeException("Heap full");
        data[size] = val;
        siftUp(size++);
    }

    public int extractMin() {
        if (size == 0) throw new RuntimeException("Heap empty");
        int min = data[0];
        data[0] = data[--size];
        siftDown(0);
        return min;
    }

    public int peekMin() {
        if (size == 0) throw new RuntimeException("Heap empty");
        return data[0];
    }

    private void siftUp(int i) {
        while (i > 0) {
            int parent = (i - 1) / 2;
            if (data[i] < data[parent]) { swap(i, parent); i = parent; }
            else break;
        }
    }

    private void siftDown(int i) {
        while (true) {
            int smallest = i;
            int l = 2*i+1, r = 2*i+2;
            if (l < size && data[l] < data[smallest]) smallest = l;
            if (r < size && data[r] < data[smallest]) smallest = r;
            if (smallest != i) { swap(i, smallest); i = smallest; }
            else break;
        }
    }

    private void swap(int i, int j) {
        int tmp = data[i]; data[i] = data[j]; data[j] = tmp;
    }
}
```

---

## Java's PriorityQueue

Java's `PriorityQueue` is a min-heap by default:

```java
// Min-heap
PriorityQueue<Integer> minHeap = new PriorityQueue<>();

// Max-heap
PriorityQueue<Integer> maxHeap = new PriorityQueue<>(Collections.reverseOrder());

// Custom comparator (e.g., sort by second element of int[])
PriorityQueue<int[]> pq = new PriorityQueue<>((a, b) -> a[1] - b[1]);

// Operations
minHeap.offer(5);          // insert, O(log n)
int min = minHeap.peek();  // view min, O(1)
int min2 = minHeap.poll(); // remove min, O(log n)
```

---

## Classic Heap Problems

### Kth Largest Element (LC #215)

Use a min-heap of size k — keep the k largest seen so far. Root = kth largest.

```java
public int findKthLargest(int[] nums, int k) {
    PriorityQueue<Integer> minHeap = new PriorityQueue<>();
    for (int num : nums) {
        minHeap.offer(num);
        if (minHeap.size() > k) minHeap.poll(); // evict smallest
    }
    return minHeap.peek(); // root = kth largest
}
// O(n log k) time — much better than sorting O(n log n) when k << n
```

### Top K Frequent Elements (LC #347)

```java
public int[] topKFrequent(int[] nums, int k) {
    Map<Integer, Integer> count = new HashMap<>();
    for (int n : nums) count.merge(n, 1, Integer::sum);

    // Min-heap by frequency → evict least frequent
    PriorityQueue<Map.Entry<Integer,Integer>> pq =
        new PriorityQueue<>((a, b) -> a.getValue() - b.getValue());

    for (var entry : count.entrySet()) {
        pq.offer(entry);
        if (pq.size() > k) pq.poll();
    }

    int[] result = new int[k];
    for (int i = k - 1; i >= 0; i--) result[i] = pq.poll().getKey();
    return result;
}
```

---

## Try It Yourself

**Problem:** Merge k sorted lists. (LC #23)

Use a min-heap to always pick the smallest across all k heads.

<details>
<summary>Solution</summary>

```java
public ListNode mergeKLists(ListNode[] lists) {
    // Min-heap ordered by node value
    PriorityQueue<ListNode> pq = new PriorityQueue<>((a, b) -> a.val - b.val);

    // Initialize with head of each list
    for (ListNode head : lists) {
        if (head != null) pq.offer(head);
    }

    ListNode dummy = new ListNode(0);
    ListNode curr = dummy;

    while (!pq.isEmpty()) {
        ListNode node = pq.poll();     // always the current minimum
        curr.next = node;
        curr = curr.next;
        if (node.next != null) pq.offer(node.next); // add next from same list
    }
    return dummy.next;
}
```

**Complexity:** O(N log k) where N = total nodes, k = number of lists.
- Each of N nodes is inserted/extracted from heap of size ≤ k → O(log k) each.

**Why min-heap works:** At any moment, the heap holds at most one node from each list — the current front. The minimum across all fronts is always at the heap root.

</details>

---

## Capstone Connection

Implement `datastructures/trees/MinHeap.java` and `MaxHeap.java` in AlgoForge. Add to problems:
- `problems/heap/KthLargest.java` — Kth Largest in stream (LC #703)
- `problems/heap/MedianFinder.java` — two-heap median (Module 12)
