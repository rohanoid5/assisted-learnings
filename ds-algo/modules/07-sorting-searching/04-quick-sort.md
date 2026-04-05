# 7.4 — Quick Sort

## Concept

**Quick Sort** is the fastest sorting algorithm in practice for large random datasets — Java uses a variant (dual-pivot Quicksort) for primitive arrays, and most real-world sorting libraries do too. It's O(n log n) average but O(n²) worst case. The key is the **partition** step: choosing a pivot, placing it in its final position, and ensuring all smaller elements are on the left and all larger on the right.

---

## Deep Dive

### Lomuto Partition — Step-by-Step

Choose the last element as pivot. Maintain a pointer `i` that separates the "≤ pivot" zone from the "> pivot" zone.

```
Array: [3, 6, 8, 10, 1, 2, 1]    pivot = 1 (last element)

i = -1  (points to end of ≤-pivot zone, initially before array)
j scans from 0 to n-2:

j=0: arr[0]=3 > 1 → skip.          i=-1  [3, 6, 8, 10, 1, 2 | 1]
j=1: arr[1]=6 > 1 → skip.          i=-1
j=2: arr[2]=8 > 1 → skip.          i=-1
j=3: arr[3]=10> 1 → skip.          i=-1
j=4: arr[4]=1 ≤ 1 → i++, swap(0,4). i=0  [1, 6, 8, 10, 3, 2 | 1]
j=5: arr[5]=2 > 1 → skip.          i=0

End: swap pivot (index 6) with arr[i+1] (index 1):
  [1, 1, 8, 10, 3, 2, 6]
       ↑
     pivot in final position (index 1)

Recurse: sort [0..0] and [2..6]
```

### Hoare Partition (original, faster in practice)

```
Array: [3, 6, 8, 10, 1, 2, 1]    pivot = arr[left] = 3

Two pointers: i = left-1, j = right+1
Loop:
  advance i until arr[i] >= pivot
  retreat j until arr[j] <= pivot
  if i < j: swap(arr[i], arr[j])
  else: return j (partition point)

Hoare uses fewer swaps on average and handles duplicates better.
```

---

### Pivot Selection Strategies

```
Last element: simple but O(n²) on already-sorted input (worst case)
First element: same problem
Random: eliminates worst case in practice — O(n log n) expected
Median-of-three: arr[left], arr[mid], arr[right] → pick the median
  → practical gold standard for competitive use
```

---

### Worst Case: Already Sorted Input with Last-Element Pivot

```
[1, 2, 3, 4, 5] with pivot = last element (5):
  After partition: [1,2,3,4] | [5]  (pivot at end, nothing on right)
  Recurse on [1,2,3,4] → same problem
  T(n) = T(n-1) + O(n) → O(n²)

Fix: shuffle array before sorting, or use random pivot.
```

---

## Code Examples

### Quick Sort — Lomuto Partition

```java
public void quickSort(int[] arr, int low, int high) {
    if (low >= high) return;
    int pivotIdx = partition(arr, low, high);
    quickSort(arr, low, pivotIdx - 1);
    quickSort(arr, pivotIdx + 1, high);
}

private int partition(int[] arr, int low, int high) {
    // Random pivot: swap a random element with the last to eliminate O(n²) worst case
    int randIdx = low + (int)(Math.random() * (high - low + 1));
    swap(arr, randIdx, high);

    int pivot = arr[high];
    int i = low - 1;
    for (int j = low; j < high; j++) {
        if (arr[j] <= pivot) { i++; swap(arr, i, j); }
    }
    swap(arr, i + 1, high);
    return i + 1;
}

private void swap(int[] arr, int i, int j) {
    int tmp = arr[i]; arr[i] = arr[j]; arr[j] = tmp;
}
```

### QuickSelect — Find Kth Smallest in O(n) Average

```java
// Same partition logic, but only recurse into the side that contains k
public int findKthSmallest(int[] arr, int k) {
    return quickSelect(arr, 0, arr.length - 1, k - 1);  // k-1 for 0-indexed
}

private int quickSelect(int[] arr, int low, int high, int k) {
    if (low == high) return arr[low];
    int pivotIdx = partition(arr, low, high);
    if (pivotIdx == k) return arr[pivotIdx];
    else if (pivotIdx < k) return quickSelect(arr, pivotIdx + 1, high, k);
    else                    return quickSelect(arr, low, pivotIdx - 1, k);
    // Average: O(n) — each level we halve the problem. Worst: O(n²) without random pivot.
}
```

---

## Try It Yourself

**Exercise:** **Kth Largest Element in an Array** (LC #215). Use QuickSelect.

```java
// Input: nums = [3,2,1,5,6,4], k = 2  → 5
// Input: nums = [3,2,3,1,2,4,5,5,6], k = 4  → 4

public int findKthLargest(int[] nums, int k) {
    // Hint: kth largest = (n-k)th smallest (0-indexed)
}
```

<details>
<summary>Solution</summary>

```java
public int findKthLargest(int[] nums, int k) {
    return quickSelect(nums, 0, nums.length - 1, nums.length - k);
}

private int quickSelect(int[] nums, int low, int high, int targetIdx) {
    int pivotIdx = partition(nums, low, high);
    if (pivotIdx == targetIdx) return nums[pivotIdx];
    if (pivotIdx < targetIdx)  return quickSelect(nums, pivotIdx + 1, high, targetIdx);
    return quickSelect(nums, low, pivotIdx - 1, targetIdx);
}

private int partition(int[] nums, int low, int high) {
    int rand = low + (int)(Math.random() * (high - low + 1));
    int tmp = nums[rand]; nums[rand] = nums[high]; nums[high] = tmp;

    int pivot = nums[high], i = low - 1;
    for (int j = low; j < high; j++)
        if (nums[j] <= pivot) { i++; int t=nums[i]; nums[i]=nums[j]; nums[j]=t; }
    int t = nums[i+1]; nums[i+1] = nums[high]; nums[high] = t;
    return i + 1;
}
// Average: O(n). Worst (no randomization): O(n²).
// Alternative: use a min-heap of size k → always O(n log k), no worst case.
```

</details>

---

## Capstone Connection

`QuickSort.java` in AlgoForge implements random-pivot Lomuto partition. `problems/sorting/KthLargest.java` uses QuickSelect — one of the most practical O(n) algorithms. Both are commonly asked in Google/Meta interviews.
