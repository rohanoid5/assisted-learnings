# 12.3 — Cyclic Sort Pattern

## Concept

The **Cyclic Sort** pattern places each number at its **correct index** in O(n) time and O(1) space — no extra array, no sorting. It works exclusively on arrays containing numbers in the range `[1, n]` or `[0, n-1]`. Once sorted this way, any number at the wrong index (or any missing index) reveals a duplicate, missing number, or corruption instantly.

---

## Deep Dive

### The Core Idea

If the input is `[3, 1, 5, 4, 2]` and values are in `[1..5]`, value `v` belongs at index `v - 1`.

```
Cyclic Sort: while the number at index i is not at its correct spot, swap it there.

arr = [3, 1, 5, 4, 2]
       ↑
  i=0: arr[0]=3, correct index=2, arr[2]=5 → not 3 → swap(0, 2)
arr = [5, 1, 3, 4, 2]
  i=0: arr[0]=5, correct index=4, arr[4]=2 → not 5 → swap(0, 4)
arr = [2, 1, 3, 4, 5]
  i=0: arr[0]=2, correct index=1, arr[1]=1 → not 2 → swap(0, 1)
arr = [1, 2, 3, 4, 5]
  i=0: arr[0]=1, correct index=0 → correct! advance i
  i=1: arr[1]=2, correct index=1 → correct! advance i
  ... (all correct)

Result: [1, 2, 3, 4, 5]
```

### The Algorithm Template

```java
int i = 0;
while (i < nums.length) {
    int correct = nums[i] - 1;            // where nums[i] should go
    if (nums[i] != nums[correct]) {
        swap(nums, i, correct);            // place it at its home
    } else {
        i++;                               // it's already home (or duplicate) — advance
    }
}
// After this loop, scan for anomalies
```

The key: `i` only advances when `nums[i]` is already in the right place. A swap never moves an element into a permanent wrong position — each swap places at least one element correctly.

### Detecting Missing & Duplicate Numbers

```
After cyclic sort:
  nums[i] != i + 1  →  index i is missing value i+1
                         ─── and nums[i] is a duplicate

Example: [4, 3, 2, 7, 8, 2, 3, 1] with range [1..8]
After sort: [1, 2, 3, 4, 3, 2, 7, 8]   (can't place duplicates correctly)
                         ↑ ↑
  indices 4,5 have wrong values → missing: 5 and 6
```

---

## Code Examples

### Example 1: Find All Duplicates in an Array (LC #442)

```java
public List<Integer> findDuplicates(int[] nums) {
    int i = 0;
    while (i < nums.length) {
        int correct = nums[i] - 1;
        if (nums[i] != nums[correct]) {
            swap(nums, i, correct);
        } else {
            i++;
        }
    }

    List<Integer> result = new ArrayList<>();
    for (int j = 0; j < nums.length; j++) {
        if (nums[j] != j + 1)
            result.add(nums[j]);   // this value is the duplicate
    }
    return result;
}

private void swap(int[] arr, int i, int j) {
    int temp = arr[i]; arr[i] = arr[j]; arr[j] = temp;
}
```

### Example 2: First Missing Positive (LC #41) — Hard

The hardest variant: values may be negative, zero, or larger than n.

```java
public int firstMissingPositive(int[] nums) {
    int n = nums.length;
    int i = 0;
    while (i < n) {
        int correct = nums[i] - 1;
        // Only sort if value is in range [1..n] and not already placed
        if (nums[i] > 0 && nums[i] <= n && nums[i] != nums[correct]) {
            swap(nums, i, correct);
        } else {
            i++;
        }
    }

    // First index where value doesn't match → that is the answer
    for (int j = 0; j < n; j++) {
        if (nums[j] != j + 1)
            return j + 1;
    }
    return n + 1;   // all positions [1..n] are filled
}
```

### Example 3: Find the Duplicate Number (LC #287)

```java
// Approach A: Cyclic Sort — modifies input, O(n) time, O(1) space
public int findDuplicate(int[] nums) {
    int i = 0;
    while (i < nums.length) {
        if (nums[i] != i) {
            int correct = nums[i];
            if (nums[correct] != nums[i]) {
                swap(nums, i, correct);
            } else {
                return nums[i];   // found the duplicate — two values want same home
            }
        } else {
            i++;
        }
    }
    return -1;
}

// Approach B: Floyd's Cycle Detection — does NOT modify input, O(n) time, O(1) space
public int findDuplicateFloyd(int[] nums) {
    int slow = nums[0], fast = nums[0];
    do {
        slow = nums[slow];
        fast = nums[nums[fast]];
    } while (slow != fast);

    slow = nums[0];
    while (slow != fast) {
        slow = nums[slow];
        fast = nums[fast];
    }
    return slow;
}
```

---

## Try It Yourself

**Exercise:** Trace the cyclic sort on `[3, 4, -1, 1]`. After sorting, identify the first missing positive.

```java
// Expected: firstMissingPositive([3,4,-1,1]) == 2
// Trace step by step starting at i=0
```

<details>
<summary>Show trace</summary>

```
Initial: [3, 4, -1, 1],  n=4
Valid range: [1..4]

i=0: nums[0]=3, correct=2, nums[2]=-1 ≠ 3 → swap(0,2) → [-1, 4, 3, 1]
i=0: nums[0]=-1, not in [1..4] → i++
i=1: nums[1]=4, correct=3, nums[3]=1 ≠ 4 → swap(1,3) → [-1, 1, 3, 4]
i=1: nums[1]=1, correct=0, nums[0]=-1 ≠ 1 → swap(1,0) → [1, -1, 3, 4]
i=1: nums[1]=-1, not in [1..4] → i++
i=2: nums[2]=3, correct=2 → nums[2]==3 ✓ → i++
i=3: nums[3]=4, correct=3 → nums[3]==4 ✓ → i++

Final array: [1, -1, 3, 4]

Scan: j=0: nums[0]=1 == 1 ✓
      j=1: nums[1]=-1 ≠ 2 → return j+1 = 2 ✓
```
</details>

---

## Capstone Connection

Add `FindDuplicates.java`, `FirstMissingPositive.java`, and `FindDuplicate.java` to `AlgoForge/problems/patterns/`. This pattern shows up in data integrity checks, memory allocators, and any system that needs to detect corruption or gaps in a bounded ID space.
