# 2.1 — Array Fundamentals & ArrayList

## Concept

A Java array is a **contiguous block of memory** holding elements of a single type. Random access is O(1) because the address of `arr[i]` is simply `baseAddress + i * elementSize`. But insertion and deletion at arbitrary positions are O(n) because everything must shift. Understanding this memory model — and when `ArrayList`'s dynamic resizing helps — is foundational.

---

## Deep Dive

### Memory Layout

```
int[] arr = {10, 20, 30, 40, 50};

Memory (each int = 4 bytes):
┌──────┬──────┬──────┬──────┬──────┐
│  10  │  20  │  30  │  40  │  50  │
└──────┴──────┴──────┴──────┴──────┘
  [0]    [1]    [2]    [3]    [4]
  addr   +4     +8     +12    +16

arr[3] = *(base + 3 * 4) = *(base + 12)  → O(1) regardless of array size
```

This is why arrays beat linked lists for random access: no pointer chasing, direct calculation.

---

### Java Arrays vs ArrayList

| Feature | `int[]` / `String[]` | `ArrayList<Integer>` |
|---------|---------------------|---------------------|
| Size | Fixed at creation | Dynamic (resizes) |
| Type | Primitive or reference | Reference only (autoboxing) |
| Access | O(1) | O(1) |
| Add at tail | N/A (size fixed) | O(1) amortized |
| Add at index i | N/A | O(n) — shift right |
| Remove at index i | N/A | O(n) — shift left |
| `contains()` | N/A (manual loop) | O(n) — linear scan |
| Memory | Minimal | ~2x due to object overhead |

Use a plain array when: size is known, primitives needed, minimum memory overhead required.  
Use `ArrayList` when: size unknown, frequent add/remove at tail, convenient methods needed.

---

### How ArrayList Resizes (Amortized O(1))

```java
// Simplified ArrayList internals
class DynamicArrayDemo {
    private Object[] data;
    private int size;

    DynamicArrayDemo() {
        data = new Object[1];  // start capacity = 1
        size = 0;
    }

    void add(Object item) {
        if (size == data.length) {
            // Double the capacity — O(n) copy, but happens rarely
            Object[] newData = new Object[data.length * 2];
            System.arraycopy(data, 0, newData, 0, size);
            data = newData;
        }
        data[size++] = item;   // O(1)
    }
}
// Resize happens at sizes 1, 2, 4, 8, 16, 32, ...
// Total copies for n adds: 1 + 2 + 4 + ... + n/2 = n - 1 < n
// Amortized cost per add = n / n = O(1)
```

---

### Common Java Array Operations

```java
// Declaration and initialization
int[] arr = new int[5];                     // {0,0,0,0,0}
int[] arr2 = {1, 2, 3, 4, 5};
int[][] matrix = new int[3][4];             // 3 rows, 4 cols

// Copy
int[] copy = Arrays.copyOf(arr2, arr2.length);
int[] slice = Arrays.copyOfRange(arr2, 1, 4);  // [2, 3, 4]

// Sort
Arrays.sort(arr2);                          // O(n log n) in-place
int[] sorted = arr2.clone(); Arrays.sort(sorted);  // non-destructive

// Binary search (only on sorted arrays!)
int idx = Arrays.binarySearch(arr2, 3);    // O(log n)

// Fill
Arrays.fill(arr, -1);                      // O(n)

// Compare
Arrays.equals(arr, copy);                  // O(n) element-wise

// Convert to list (boxed types only)
List<Integer> list = new ArrayList<>(Arrays.asList(1, 2, 3));

// Convert list back to array
Integer[] back = list.toArray(new Integer[0]);

// Sort with custom comparator (2D arrays)
int[][] intervals = {{3,4},{1,2},{5,6}};
Arrays.sort(intervals, (a, b) -> a[0] - b[0]);  // sort by first element
```

---

## Code Examples

### Example 1: Rotate Array In-Place

```java
// Rotate array right by k positions: [1,2,3,4,5], k=2 → [4,5,1,2,3]
// Key insight: reverse three times → O(n) time, O(1) space

public void rotate(int[] nums, int k) {
    int n = nums.length;
    k = k % n;              // handle k > n
    reverse(nums, 0, n - 1);        // [5,4,3,2,1]
    reverse(nums, 0, k - 1);        // [4,5,3,2,1]
    reverse(nums, k, n - 1);        // [4,5,1,2,3]
}

private void reverse(int[] arr, int lo, int hi) {
    while (lo < hi) {
        int tmp = arr[lo]; arr[lo++] = arr[hi]; arr[hi--] = tmp;
    }
}
```

### Example 2: Find All Duplicates

```java
// Given array of n integers where each element is in [1..n],
// find all duplicates — O(n) time, O(1) space (in-place marking)

public List<Integer> findDuplicates(int[] nums) {
    List<Integer> result = new ArrayList<>();
    for (int x : nums) {
        int idx = Math.abs(x) - 1;       // convert value to 0-based index
        if (nums[idx] < 0) {             // already visited this index
            result.add(Math.abs(x));
        } else {
            nums[idx] = -nums[idx];      // mark as visited
        }
    }
    // Restore array (optional)
    for (int i = 0; i < nums.length; i++) nums[i] = Math.abs(nums[i]);
    return result;
}
```

---

## Try It Yourself

**Exercise:** Remove duplicates from a sorted array **in-place**, returning the new length. Do not use extra arrays — O(1) auxiliary space.

Input: `[1,1,2,3,3,3,4]` → return `4`, and `nums[:4]` should be `[1,2,3,4]`.

```java
public int removeDuplicates(int[] nums) {
    // Your solution here
}
```

<details>
<summary>Show solution</summary>

```java
public int removeDuplicates(int[] nums) {
    if (nums.length == 0) return 0;
    int k = 1;  // k is the "write pointer" — position for next unique element
    for (int i = 1; i < nums.length; i++) {
        if (nums[i] != nums[i - 1]) {   // new unique element found
            nums[k++] = nums[i];         // write it to position k
        }
    }
    return k;
    // Time: O(n), Space: O(1) auxiliary
}
```

This is the "Two Pointer / Write Pointer" pattern: one pointer reads, another writes. LC #26.

</details>

---

## Capstone Connection

In AlgoForge Module 2, you will implement `DynamicArray<T>` — a generic resizable array that mirrors Java's `ArrayList` from scratch, including the doubling resize strategy. This gives you a deep understanding of what `ArrayList.add()` actually does.
