# 7.3 — Merge Sort

## Concept

**Merge Sort** is the canonical divide-and-conquer sort. It splits the array in half, recursively sorts each half, then merges the two sorted halves. It's O(n log n) in all cases — best, average, and worst — and it's stable. The trade-off: it requires O(n) auxiliary space. It's the algorithm of choice when stability matters or when you can't afford O(n log n) worst case.

---

## Deep Dive

### Divide-Conquer-Merge Tree

```
[38, 27, 43, 3, 9, 82, 10]

DIVIDE (split until size 1):
        [38, 27, 43, 3, 9, 82, 10]
           /                 \
    [38, 27, 43]          [3, 9, 82, 10]
      /        \            /          \
  [38, 27]    [43]      [3, 9]      [82, 10]
   /    \               /    \       /     \
[38]   [27]           [3]   [9]   [82]   [10]

MERGE (combine sorted halves bottom-up):
[27, 38]    [43]   →  [27, 38, 43]
               [3, 9]    [10, 82]  →  [3, 9, 10, 82]
                    [3, 9, 10, 27, 38, 43, 82]
```

### The Merge Step

```
Merge [27, 38, 43] and [3, 9, 10, 82]:

Left pointer  → [27, 38, 43]
Right pointer → [3, 9, 10, 82]
Output buffer → []

Step 1: 27 vs 3  → take 3  → result: [3]
Step 2: 27 vs 9  → take 9  → result: [3, 9]
Step 3: 27 vs 10 → take 10 → result: [3, 9, 10]
Step 4: 27 vs 82 → take 27 → result: [3, 9, 10, 27]
Step 5: 38 vs 82 → take 38 → result: [3, 9, 10, 27, 38]
Step 6: 43 vs 82 → take 43 → result: [3, 9, 10, 27, 38, 43]
Step 7: right exhausted → copy rest of left: [3, 9, 10, 27, 38, 43, 82]
```

### Complexity Analysis

```
Recursion tree: log₂(n) levels
Work per level: O(n) total (all merge calls combined touch each element once)
Total: O(n log n)

T(n) = 2·T(n/2) + O(n) → Master Theorem Case 2 → O(n log n) ✓

Space: O(n) for the auxiliary merge buffer (allocated once and reused)
Stack: O(log n) recursive call frames
```

---

## Code Examples

### Merge Sort — Top-Down Recursive

```java
public void mergeSort(int[] arr, int left, int right) {
    if (left >= right) return;  // base case: 0 or 1 element

    int mid = left + (right - left) / 2;  // avoids overflow vs (left+right)/2
    mergeSort(arr, left, mid);
    mergeSort(arr, mid + 1, right);
    merge(arr, left, mid, right);
}

private void merge(int[] arr, int left, int mid, int right) {
    // Copy both halves to a temporary array
    int[] tmp = new int[right - left + 1];
    int i = left, j = mid + 1, k = 0;

    while (i <= mid && j <= right) {
        if (arr[i] <= arr[j]) tmp[k++] = arr[i++];  // <=  preserves stability
        else                   tmp[k++] = arr[j++];
    }
    while (i <= mid)    tmp[k++] = arr[i++];
    while (j <= right)  tmp[k++] = arr[j++];

    // Copy back
    System.arraycopy(tmp, 0, arr, left, tmp.length);
}
```

### Merge Sort — Bottom-Up Iterative (no recursion stack)

```java
public void mergeSortIterative(int[] arr) {
    int n = arr.length;
    // Start with subarrays of size 1, double each iteration
    for (int size = 1; size < n; size *= 2) {
        for (int left = 0; left < n - size; left += 2 * size) {
            int mid   = left + size - 1;
            int right = Math.min(left + 2 * size - 1, n - 1);
            merge(arr, left, mid, right);
        }
    }
}
```

### Count Inversions (classic Merge Sort application)

```java
// An inversion is a pair (i, j) where i < j but arr[i] > arr[j]
// Merge Sort can count them in O(n log n)

int inversions = 0;
private void merge(int[] arr, int left, int mid, int right) {
    int[] tmp = new int[right - left + 1];
    int i = left, j = mid + 1, k = 0;
    while (i <= mid && j <= right) {
        if (arr[i] <= arr[j]) {
            tmp[k++] = arr[i++];
        } else {
            // arr[i..mid] are all greater than arr[j] (since left half is sorted)
            inversions += (mid - i + 1);  // all remaining left elements form inversions with arr[j]
            tmp[k++] = arr[j++];
        }
    }
    // ... (same as before)
}
```

---

## Try It Yourself

**Exercise:** Given two sorted arrays `nums1` and `nums2`, merge `nums2` into `nums1` in-place (LC #88). `nums1` has enough space at the end (extra zeros).

```java
// nums1 = [1,2,3,0,0,0], m = 3
// nums2 = [2,5,6],        n = 3
// Output: [1,2,2,3,5,6]
// Hint: start merging from the END to avoid overwriting unprocessed elements

public void merge(int[] nums1, int m, int[] nums2, int n) {
    // your code here
}
```

<details>
<summary>Solution</summary>

```java
public void merge(int[] nums1, int m, int[] nums2, int n) {
    int i = m - 1, j = n - 1, k = m + n - 1;
    // Fill from the back — larger element goes to position k
    while (i >= 0 && j >= 0) {
        if (nums1[i] > nums2[j]) nums1[k--] = nums1[i--];
        else                      nums1[k--] = nums2[j--];
    }
    // If nums2 has remaining elements (nums1 remaining are already in place)
    while (j >= 0) nums1[k--] = nums2[j--];
    // Time: O(m+n), Space: O(1)
}
```

</details>

---

## Capstone Connection

`MergeSort.java` in AlgoForge's `datastructures/sorting/` is the first O(n log n) sort you implement. Write both the recursive and iterative versions — the iterative version is a useful pattern since it avoids stack overflow on very large arrays. The Count Inversions application is a bonus problem in `problems/sorting/`.
