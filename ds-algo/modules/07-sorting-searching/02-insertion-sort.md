# 7.2 — Insertion Sort

## Concept

**Insertion Sort** builds the sorted array one element at a time by inserting each new element into its correct position among the already-sorted elements to its left. Think of sorting a hand of playing cards: you pick up each card and slide it into the right place in your held hand. It's O(n²) worst case but O(n) best case on nearly-sorted data — and it's the algorithm Java uses internally for small arrays (< 32 elements).

---

## Deep Dive

### Step-by-Step Trace

```
Array: [5, 3, 8, 1, 2]

i=1: key=3. Compare 5>3 → shift 5 right. Insert 3.
  [3, 5, 8, 1, 2]

i=2: key=8. Compare 5<8 → stop. Insert 8 in place.
  [3, 5, 8, 1, 2]   (no change)

i=3: key=1. Compare 8>1 → shift. 5>1 → shift. 3>1 → shift. Insert 1.
  [1, 3, 5, 8, 2]

i=4: key=2. Compare 8>2 → shift. 5>2 → shift. 3>2 → shift. 1<2 → stop. Insert 2.
  [1, 2, 3, 5, 8]

Sorted: [1, 2, 3, 5, 8]
```

**Invariant:** after processing index `i`, elements `arr[0..i]` are sorted.

---

### Why It's Good for Nearly-Sorted Data

```
Nearly sorted: [1, 2, 4, 3, 5]
Only one inversion (4,3). The inner while loop runs at most once for i=3.

Formally: Insertion Sort runs in O(n + d) where d = number of inversions.
Sorted array:     d = 0          → O(n)
Reverse sorted:   d = n(n-1)/2  → O(n²)
Random:           d ≈ n²/4      → O(n²)
```

---

### Comparison with Bubble Sort

| | Insertion Sort | Bubble Sort |
|---|---------------|-------------|
| Best case | O(n) | O(n) with early exit |
| Average | O(n²) | O(n²) |
| Stable | Yes | Yes |
| In-place | Yes | Yes |
| Cache friendly | More — sequential reads | Less — many swaps |
| Real-world use | Java's small-array sort | Rarely |

---

## Code Examples

### Insertion Sort

```java
public void insertionSort(int[] arr) {
    for (int i = 1; i < arr.length; i++) {
        int key = arr[i];
        int j = i - 1;
        // Shift elements of arr[0..i-1] that are greater than key one position to the right
        while (j >= 0 && arr[j] > key) {
            arr[j + 1] = arr[j];
            j--;
        }
        arr[j + 1] = key;  // insert key into its correct position
    }
}
```

### Binary Insertion Sort (reduces comparisons but not swaps)

```java
// For large objects where comparison is expensive:
// Use binary search to find insertion position — O(log i) comparisons per element
// But still O(n²) shifts/writes

public void binaryInsertionSort(int[] arr) {
    for (int i = 1; i < arr.length; i++) {
        int key = arr[i];
        int pos = binarySearch(arr, 0, i - 1, key);
        // Shift elements to make room
        System.arraycopy(arr, pos, arr, pos + 1, i - pos);
        arr[pos] = key;
    }
}

private int binarySearch(int[] arr, int left, int right, int target) {
    while (left <= right) {
        int mid = left + (right - left) / 2;
        if (arr[mid] > target) right = mid - 1;
        else left = mid + 1;
    }
    return left;  // insertion position
}
```

---

## Try It Yourself

**Exercise:** Trace Insertion Sort on `[9, 7, 5, 3, 1]` (reverse sorted). How many comparisons and how many shifts happen total?

<details>
<summary>Solution</summary>

```
i=1: key=7. 9>7 → shift(1). Insert 7. → [7,9,5,3,1]   comparisons: 1, shifts: 1
i=2: key=5. 9>5 → shift. 7>5 → shift. Insert 5. → [5,7,9,3,1]  comparisons: 2, shifts: 2
i=3: key=3. 9>3, 7>3, 5>3 → 3 shifts. → [3,5,7,9,1]   comparisons: 3, shifts: 3
i=4: key=1. 9>1, 7>1, 5>1, 3>1 → 4 shifts. → [1,3,5,7,9]  comparisons: 4, shifts: 4

Total comparisons: 1+2+3+4 = 10 = n(n-1)/2 for n=5 → O(n²) confirmed
Total shifts:      1+2+3+4 = 10

For reverse sorted input, Insertion Sort does the maximum possible work.
```

</details>

---

## Capstone Connection

Java's `Arrays.sort()` uses **TimSort** — a hybrid of Merge Sort and Insertion Sort. For runs of < 32 elements (called "minruns"), it falls back to Insertion Sort exactly as written here. Knowing this lets you answer "how does Java sort?" at a level that impresses interviewers.
