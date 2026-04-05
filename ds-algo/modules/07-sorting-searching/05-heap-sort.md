# 7.5 — Heap Sort

## Concept

**Heap Sort** achieves O(n log n) in all cases (like Merge Sort) but sorts **in-place** with O(1) extra space (like Quick Sort). It works by building a max-heap from the array, then repeatedly extracting the maximum element into the sorted portion. The trade-off: it's not stable and has worse cache performance than Quick Sort, so it's rarely the fastest in practice — but it's an important interview topic and the basis for understanding the heap data structure (covered fully in Module 08).

---

## Deep Dive

### Phase 1: Heapify — Build a Max-Heap

A max-heap stored as an array: for node at index `i`, left child is at `2i+1`, right child at `2i+2`, parent at `(i-1)/2`.

```
Build max-heap from [4, 10, 3, 5, 1]:

  Start heapifying from last non-leaf = index 1:
  Index 0: 4     Index 1: 10    Index 2: 3
  Index 3: 5     Index 4: 1

  Heapify(1): children are 3 at idx3=5, 4 at idx4=1. max child = 5 > 10? No → no swap.
  Heapify(0): children are 10 at idx1, 3 at idx2. max child = 10 > 4 → swap(0,1).
    [10, 4, 3, 5, 1]
  Heapify(1): children are 5 at idx3, 1 at idx4. max child = 5 > 4 → swap(1,3).
    [10, 5, 3, 4, 1]

Max-heap: [10, 5, 3, 4, 1]
```

### Phase 2: Extract Max Repeatedly

```
[10, 5, 3, 4, 1]  ← max-heap

Step 1: swap root with last → [1, 5, 3, 4, | 10]  heapify([1,5,3,4]) → [5,4,3,1]
  →  [5, 4, 3, 1, | 10]

Step 2: swap root with last → [1, 4, 3, | 5, 10]  heapify([1,4,3]) → [4,1,3]
  →  [4, 1, 3, | 5, 10]

Step 3: swap root with last → [3, 1, | 4, 5, 10]  heapify([3,1]) → no swap
  →  [3, 1, | 4, 5, 10]

Step 4: swap root with last → [1, | 3, 4, 5, 10]
  →  [1, 3, 4, 5, 10]

Sorted: [1, 3, 4, 5, 10] ✓
```

### Complexity

```
Phase 1 (Build Heap): O(n)  — not O(n log n)! (Floyd's algorithm, each node sifts down at most h)
Phase 2 (n extractions, each O(log n)): O(n log n)
Total: O(n log n)

Space: O(1) extra (sorts in-place — the heap IS the array)
The log-factor from sifting down gives us:
  Best, average, worst = O(n log n)  ← no bad pivot luck like Quick Sort
```

---

## Code Examples

### Heap Sort

```java
public void heapSort(int[] arr) {
    int n = arr.length;

    // Phase 1: Build max-heap (start from last non-leaf, work up to root)
    for (int i = n / 2 - 1; i >= 0; i--) {
        heapify(arr, n, i);
    }

    // Phase 2: Extract max one by one
    for (int i = n - 1; i > 0; i--) {
        // Move current root (max) to end
        int tmp = arr[0]; arr[0] = arr[i]; arr[i] = tmp;
        // Heapify the reduced heap
        heapify(arr, i, 0);
    }
}

// Heapify subtree rooted at index i, with heap of size n
private void heapify(int[] arr, int n, int i) {
    int largest = i;
    int left  = 2 * i + 1;
    int right = 2 * i + 2;

    if (left  < n && arr[left]  > arr[largest]) largest = left;
    if (right < n && arr[right] > arr[largest]) largest = right;

    if (largest != i) {
        int tmp = arr[i]; arr[i] = arr[largest]; arr[largest] = tmp;
        heapify(arr, n, largest);  // recursively heapify the affected subtree
    }
}
```

### Array-to-Tree Visualization Helper

```java
// Useful for debugging: print heap as indented tree
public void printHeap(int[] arr, int n) {
    for (int i = 0; i < n; i++) {
        int level = (int)(Math.log(i + 1) / Math.log(2));
        if (i == 0 || Integer.bitCount(i + 1) == 1) System.out.println();
        System.out.print("  ".repeat(n / (i + 1)) + arr[i] + " ");
    }
    System.out.println();
}
```

---

## Try It Yourself

**Exercise:** After building a max-heap from `[3, 1, 4, 1, 5, 9, 2, 6]`, what does the array look like? Walk through the build phase step by step (last non-leaf = index 3).

<details>
<summary>Solution</summary>

```
Input: [3, 1, 4, 1, 5, 9, 2, 6]  (n=8, last non-leaf = n/2-1 = 3)

Tree structure before heapify:
          3
        /   \
       1     4
      / \   / \
     1   5 9   2
    /
   6

Heapify(3): children=none valid (left=7=6, right=8 out of bounds... wait)
  Actually idx3=1, left=7th=6, right=8th=out. 6>1 → swap(3,7).
  [3, 1, 4, 6, 5, 9, 2, 1]

Heapify(2): idx2=4, left=5th=9, right=6th=2. 9>4 → swap(2,5).
  [3, 1, 9, 6, 5, 4, 2, 1]

Heapify(1): idx1=1, left=3rd=6, right=4th=5. 6>1 → swap(1,3).
  [3, 6, 9, 1, 5, 4, 2, 1]   now heapify(3): idx3=1, left=7th=1, no swap.

Heapify(0): idx0=3, left=1st=6, right=2nd=9. 9>3 → swap(0,2).
  [9, 6, 3, 1, 5, 4, 2, 1]   now heapify(2): idx2=3, left=5th=4, right=6th=2. 4>3 → swap(2,5).
  [9, 6, 4, 1, 5, 3, 2, 1]

Max-heap: [9, 6, 4, 1, 5, 3, 2, 1]
Verify: 9 > 6 ✓, 9 > 4 ✓, 6 > 1 ✓, 6 > 5 ✓, 4 > 3 ✓, 4 > 2 ✓, 1 > 1 ✓
```

</details>

---

## Capstone Connection

Heap Sort and the `heapify` function are the building blocks of the `MinHeap<T>` and `MaxHeap<T>` you'll implement in AlgoForge's Module 08. Once you understand `heapify`, implementing `siftDown` in the heap is trivial — it's the exact same operation.
