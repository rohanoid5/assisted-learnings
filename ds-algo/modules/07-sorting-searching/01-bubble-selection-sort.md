# 7.1 — Bubble Sort & Selection Sort

## Concept

Bubble Sort and Selection Sort are O(n²) algorithms — too slow for large inputs but important to know because interviewers use them as baselines. Understanding why they're slow (and why even "simple" fixes don't help much) builds intuition for why Merge Sort and Quick Sort are better. Both algorithms are also approachable enough to implement correctly in 5 minutes without thinking hard.

---

## Deep Dive

### Bubble Sort — Pass-by-Pass Trace

Repeatedly step through the list, compare adjacent elements, and swap if out of order. After each pass, the largest unsorted element "bubbles" to its correct position.

```
Array: [5, 3, 8, 1, 2]

Pass 1: compare adjacent pairs, largest bubbles to end
  [5,3] → swap → [3,5,8,1,2]
  [5,8] → ok   → [3,5,8,1,2]
  [8,1] → swap → [3,5,1,8,2]
  [8,2] → swap → [3,5,1,2,8]  ← 8 settled ✓
  
Pass 2: [3,5,1,2,|8]  (ignore settled 8)
  [3,5] → ok
  [5,1] → swap → [3,1,5,2,8]
  [5,2] → swap → [3,1,2,5,8]  ← 5 settled ✓
  
Pass 3: [3,1,2,|5,8]
  [3,1] → swap → [1,3,2,5,8]
  [3,2] → swap → [1,2,3,5,8]  ← 3 settled ✓
  
Pass 4: [1,2,|3,5,8]  → no swaps → early exit ✓

Sorted: [1, 2, 3, 5, 8]
```

**Key property:** if a full pass makes no swaps, the array is sorted. This gives O(n) best case on already-sorted input.

---

### Selection Sort — Trace

Find the minimum element in the unsorted portion and swap it into the correct position.

```
Array: [5, 3, 8, 1, 2]

Pass 1: min of [5,3,8,1,2] = 1 at index 3 → swap with index 0
  → [1, 3, 8, 5, 2]

Pass 2: min of [3,8,5,2] = 2 at index 4 → swap with index 1
  → [1, 2, 8, 5, 3]

Pass 3: min of [8,5,3] = 3 at index 4 → swap with index 2
  → [1, 2, 3, 5, 8]

Pass 4: min of [5,8] = 5 → already at index 3, no swap needed
  → [1, 2, 3, 5, 8]

Sorted: [1, 2, 3, 5, 8]
```

**Key property:** always makes exactly n-1 swaps (at most one per pass). Good for minimizing writes to memory (e.g., flash storage).

---

### Comparison

| | Bubble Sort | Selection Sort |
|---|------------|----------------|
| Best case | O(n) — with early exit | O(n²) — always scans |
| Average case | O(n²) | O(n²) |
| Worst case | O(n²) | O(n²) |
| Space | O(1) in-place | O(1) in-place |
| Stable | Yes ✓ | No — swaps can skip over equal elements |
| Swaps | O(n²) | O(n) |

---

## Code Examples

### Bubble Sort with Early Exit

```java
public void bubbleSort(int[] arr) {
    int n = arr.length;
    for (int i = 0; i < n - 1; i++) {
        boolean swapped = false;
        for (int j = 0; j < n - 1 - i; j++) {  // inner bound shrinks each pass
            if (arr[j] > arr[j + 1]) {
                int tmp = arr[j]; arr[j] = arr[j+1]; arr[j+1] = tmp;
                swapped = true;
            }
        }
        if (!swapped) break;  // already sorted — O(n) best case
    }
}
```

### Selection Sort

```java
public void selectionSort(int[] arr) {
    int n = arr.length;
    for (int i = 0; i < n - 1; i++) {
        int minIdx = i;
        for (int j = i + 1; j < n; j++) {
            if (arr[j] < arr[minIdx]) minIdx = j;
        }
        if (minIdx != i) {
            int tmp = arr[i]; arr[i] = arr[minIdx]; arr[minIdx] = tmp;
        }
    }
}
```

---

## Try It Yourself

**Exercise:** Modify Bubble Sort so it sorts in descending order. Then trace through `[3, 1, 4, 1, 5, 9]`. What is the state after pass 1?

<details>
<summary>Solution</summary>

```java
public void bubbleSortDesc(int[] arr) {
    int n = arr.length;
    for (int i = 0; i < n - 1; i++) {
        boolean swapped = false;
        for (int j = 0; j < n - 1 - i; j++) {
            if (arr[j] < arr[j + 1]) {  // flip comparison
                int tmp = arr[j]; arr[j] = arr[j+1]; arr[j+1] = tmp;
                swapped = true;
            }
        }
        if (!swapped) break;
    }
}

// Trace [3,1,4,1,5,9] descending, Pass 1:
// [3,1] → 3>1? No (desc: want larger first), 1<3 so no swap → wait, desc: swap if arr[j] < arr[j+1]
// [3,1]: 3 < 1? No → no swap
// [1,4]: 1 < 4? Yes → swap → [3,4,1,1,5,9]
// [4,1]: 4 < 1? No → no swap
// [1,1]: 1 < 1? No → no swap
// [1,5]: 1 < 5? Yes → swap → [3,4,1,5,1,9]  — wait let's redo from beginning:
//
// Start: [3,1,4,1,5,9]
// pos0-1: arr[0]=3 < arr[1]=1? No → [3,1,4,1,5,9]
// pos1-2: arr[1]=1 < arr[2]=4? Yes → swap → [3,4,1,1,5,9]
// pos2-3: arr[2]=1 < arr[3]=1? No → [3,4,1,1,5,9]
// pos3-4: arr[3]=1 < arr[4]=5? Yes → swap → [3,4,1,5,1,9]
// pos4-5: arr[4]=1 < arr[5]=9? Yes → swap → [3,4,1,5,9,1]
//
// After pass 1: [3, 4, 1, 5, 9, 1]  — "1" (the smallest) has bubbled to the end ✓
```

</details>

---

## Capstone Connection

Bubble and Selection Sort are the "control" in any benchmarking comparison. `ComplexityBenchmark.java` in AlgoForge already measures them against `Arrays.sort()` — revisit that benchmark and add a chart of the results divided by n² to confirm O(n²) behaviour.
