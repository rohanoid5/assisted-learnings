# 7.6 — Binary Search & Variations

## Concept

**Binary Search** is the most bug-prone algorithm to implement correctly — most developers have an off-by-one error lurking in their implementation. The power is undeniable: O(log n) search in a sorted array, which means finding a value in 1 billion elements in ≤ 30 comparisons. Beyond the basic form, there are 6 key variations that appear constantly in interviews — all derived from a single template.

---

## Deep Dive

### The Closed-Interval Template

Use `left <= right` and always return `left` when the loop exits. This single template handles all 6 variations.

```
Template:
  left = 0, right = n - 1
  while left <= right:
      mid = left + (right - left) / 2  ← NEVER use (left + right) / 2 (overflow!)
      if arr[mid] == target: ...
      elif arr[mid] < target: left = mid + 1
      else: right = mid - 1
  return left  ← when loop exits, left is where target would be inserted
```

**Why `left + (right - left) / 2`?** When left and right are both INT_MAX / 2, `left + right` overflows. The safe form avoids this.

---

### The 6 Variations

```
1. Find exact value             → return mid if arr[mid] == target, else -1
2. Find leftmost occurrence     → when equal, set right = mid - 1 (keep searching left)
3. Find rightmost occurrence    → when equal, set left = mid + 1 (keep searching right)
4. Find first position ≥ target → (lower_bound) standard template, return left
5. Find first position > target → (upper_bound) treat equal same as "less than"
6. Search in rotated array      → determine which half is sorted, binary search in it
```

---

### Visual: Find Leftmost vs Rightmost in [1,2,2,2,3]

```
Target = 2

Find leftmost:                Find rightmost:
  [1,2,2,2,3]                   [1,2,2,2,3]
  l=0,r=4,mid=2 → arr[2]=2=t    l=0,r=4,mid=2 → arr[2]=2=t
  → equal → r=mid-1=1           → equal → l=mid+1=3
  l=0,r=1,mid=0 → arr[0]=1<2    l=3,r=4,mid=3 → arr[3]=2=t
  → left l=mid+1=1              → equal → l=mid+1=4
  l=1,r=1,mid=1 → arr[1]=2=t    l=4,r=4,mid=4 → arr[4]=3>2
  → equal → r=mid-1=0           → right → r=mid-1=3
  l=1 > r=0 → exit              l=4 > r=3 → exit
  return left=1 ✓               return left-1=3 ✓
                                 (rightmost = left-1 after loop)
```

---

## Code Examples

### Template 1: Find Exact Value

```java
public int binarySearch(int[] arr, int target) {
    int left = 0, right = arr.length - 1;
    while (left <= right) {
        int mid = left + (right - left) / 2;
        if (arr[mid] == target) return mid;
        else if (arr[mid] < target) left = mid + 1;
        else right = mid - 1;
    }
    return -1;
}
```

### Templates 2 & 3: Leftmost & Rightmost

```java
public int leftmostOccurrence(int[] arr, int target) {
    int left = 0, right = arr.length - 1, result = -1;
    while (left <= right) {
        int mid = left + (right - left) / 2;
        if (arr[mid] == target) { result = mid; right = mid - 1; }  // found but keep looking left
        else if (arr[mid] < target) left = mid + 1;
        else right = mid - 1;
    }
    return result;
}

public int rightmostOccurrence(int[] arr, int target) {
    int left = 0, right = arr.length - 1, result = -1;
    while (left <= right) {
        int mid = left + (right - left) / 2;
        if (arr[mid] == target) { result = mid; left = mid + 1; }  // found but keep looking right
        else if (arr[mid] < target) left = mid + 1;
        else right = mid - 1;
    }
    return result;
}
```

### Template 6: Search in Rotated Sorted Array (LC #33)

```java
// [4,5,6,7,0,1,2] is [0,1,2,3,4,5,6] rotated at index 4
// Key insight: one of the two halves is always sorted

public int search(int[] nums, int target) {
    int left = 0, right = nums.length - 1;
    while (left <= right) {
        int mid = left + (right - left) / 2;
        if (nums[mid] == target) return mid;

        if (nums[left] <= nums[mid]) {         // left half is sorted
            if (nums[left] <= target && target < nums[mid])
                right = mid - 1;               // target in sorted left half
            else
                left = mid + 1;
        } else {                               // right half is sorted
            if (nums[mid] < target && target <= nums[right])
                left = mid + 1;               // target in sorted right half
            else
                right = mid - 1;
        }
    }
    return -1;
}
```

---

## Try It Yourself

**Exercise:** Find the peak element (LC #162). A peak element is greater than its neighbors. The array may have multiple peaks — return any peak index. Must run in O(log n).

```java
// Input: [1,2,3,1]  → 2  (index 2, value 3 is a peak)
// Input: [1,2,1,3,5,6,4]  → 5 (index 5, value 6 is a peak)

public int findPeakElement(int[] nums) {
    // Hint: if nums[mid] < nums[mid+1], the peak is to the right (rising slope).
    // If nums[mid] > nums[mid+1], the peak is to the left or at mid.
}
```

<details>
<summary>Solution</summary>

```java
public int findPeakElement(int[] nums) {
    int left = 0, right = nums.length - 1;
    while (left < right) {  // note: left < right, not <=
        int mid = left + (right - left) / 2;
        if (nums[mid] < nums[mid + 1]) {
            left = mid + 1;   // peak is to the right
        } else {
            right = mid;      // peak is at mid or to the left (keep mid in range)
        }
    }
    return left;  // left == right == peak index
    // Time: O(log n), Space: O(1)
}
```

</details>

---

## Capstone Connection

`BinarySearch.java` in AlgoForge implements all 6 variations as static methods with clear names. Before every interview, re-read this file — it's the most likely place to have a latent bug. The rotated array search appears in at least one problem per 10 FAANG interviews.
