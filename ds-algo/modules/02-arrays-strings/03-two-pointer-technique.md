# 2.3 — Two Pointer Technique

## Concept

The two pointer technique uses two indices (or references) that move through a data structure. Instead of comparing every pair — O(n²) — you converge on the answer using the structure of the data. Most two-pointer problems run in **O(n) time and O(1) space**.

---

## Deep Dive

### Two Variants

```
Variant A: Converging (opposite ends)          Variant B: Same direction (fast/slow)
─────────────────────────────────────          ──────────────────────────────────────

arr = [1, 2, 3, 4, 5, 6, 7]                   arr = [0, 1, 1, 2, 2, 3, 3, 4]
       ↑                   ↑                          ↑  ↑
      left               right                       slow fast

left moves right →                             slow stays, fast probes ahead
right moves left ←                             slow advances only when condition met
until left >= right                            (used for deduplication, partition)
```

**When to use converging:** sorted array, palindrome check, pair sum, container problems.  
**When to use same-direction:** remove duplicates, partition, sliding window (→ Module 4).

---

### Decision Flowchart

```
Input is sorted?
  ├── YES → Try converging two pointers first
  │         If sorted + pair sum: move toward each other based on comparison
  └── NO  → Sort it first (O(n log n)) then try two pointers
             OR use HashMap O(n) if you need O(n) total

Problem asks for pairs/triplets?   → Two pointers after sort
Problem asks for palindrome/cycle? → Converging or fast/slow
Problem asks for subarrays?        → Sliding window (Topic 2.4)
```

---

### Three Canonical Patterns

#### Pattern 1: Pair Sum in Sorted Array

```java
// Given sorted array, find two indices that sum to target
public int[] twoSumSorted(int[] numbers, int target) {
    int left = 0, right = numbers.length - 1;
    while (left < right) {
        int sum = numbers[left] + numbers[right];
        if (sum == target) return new int[]{left + 1, right + 1};  // 1-indexed
        if (sum < target) left++;    // need larger value → move left up
        else right--;                // need smaller value → move right down
    }
    return new int[]{};
    // Correctness: If answer exists at (i,j), we converge to it.
    //   Any move that skips a position eliminates possibilities provably invalid.
    // Time: O(n), Space: O(1)
}
```

#### Pattern 2: Three Sum

```java
// Find all unique triplets [a,b,c] in nums such that a+b+c = 0
// Classic: sort + fix one element + two pointer for rest

public List<List<Integer>> threeSum(int[] nums) {
    Arrays.sort(nums);   // O(n log n)
    List<List<Integer>> result = new ArrayList<>();

    for (int i = 0; i < nums.length - 2; i++) {
        if (i > 0 && nums[i] == nums[i - 1]) continue;  // skip duplicate i

        int left = i + 1, right = nums.length - 1;
        while (left < right) {
            int sum = nums[i] + nums[left] + nums[right];
            if (sum == 0) {
                result.add(Arrays.asList(nums[i], nums[left], nums[right]));
                while (left < right && nums[left] == nums[left + 1]) left++;
                while (left < right && nums[right] == nums[right - 1]) right--;
                left++; right--;
            } else if (sum < 0) {
                left++;
            } else {
                right--;
            }
        }
    }
    return result;
    // Time: O(n²) — O(n log n) sort + O(n) per element outer loop
    // Space: O(1) auxiliary (not counting output)
}
```

#### Pattern 3: Remove Duplicates / Partition

```java
// Partition array so all zeros come last — O(n) time, O(1) space
// [0,1,0,3,12] → [1,3,12,0,0]

public void moveZeroes(int[] nums) {
    int slow = 0;        // "write" pointer: position for next non-zero
    for (int fast = 0; fast < nums.length; fast++) {
        if (nums[fast] != 0) {
            nums[slow++] = nums[fast];   // copy non-zero to slow position
        }
    }
    while (slow < nums.length) nums[slow++] = 0;  // fill rest with zeros
    // Time: O(n), Space: O(1)
}
```

---

## Code Examples

### Example 1: Container With Most Water

```java
// n vertical lines at each index with height height[i].
// Find two lines that form container with most water.
// Greedy: start with widest container, move the shorter line inward.

public int maxArea(int[] height) {
    int left = 0, right = height.length - 1;
    int max = 0;
    while (left < right) {
        int area = Math.min(height[left], height[right]) * (right - left);
        max = Math.max(max, area);
        // Move the shorter line — a taller line might give more area
        if (height[left] <= height[right]) left++;
        else right--;
    }
    return max;
    // Time: O(n), Space: O(1)
}
```

### Example 2: Trapping Rain Water

```java
// Classic hard problem: how much water can be trapped between bars?
// Key: water at position i = min(maxLeft[i], maxRight[i]) - height[i]
// Two pointer: track running max from each side

public int trap(int[] height) {
    int left = 0, right = height.length - 1;
    int leftMax = 0, rightMax = 0, water = 0;
    while (left < right) {
        if (height[left] <= height[right]) {
            if (height[left] >= leftMax) leftMax = height[left];
            else water += leftMax - height[left];  // water above this position
            left++;
        } else {
            if (height[right] >= rightMax) rightMax = height[right];
            else water += rightMax - height[right];
            right--;
        }
    }
    return water;
    // Time: O(n), Space: O(1) — no prefix sum arrays needed
}
```

---

## Try It Yourself

**Exercise:** Given a string `s`, return `true` if it is possible to make it a palindrome by removing **at most one** character.

Input: `"abca"` → `true` (remove 'b' or 'c')  
Input: `"abc"` → `false`

```java
public boolean validPalindrome(String s) {
    // Hint: use converging two pointers
    // when mismatch: try skipping s[left] OR s[right], check if remainder is palindrome
}
```

<details>
<summary>Show solution</summary>

```java
public boolean validPalindrome(String s) {
    int left = 0, right = s.length() - 1;
    while (left < right) {
        if (s.charAt(left) != s.charAt(right)) {
            // Try skipping left OR right — only one chance to skip
            return isPalin(s, left + 1, right) || isPalin(s, left, right - 1);
        }
        left++; right--;
    }
    return true;
}

private boolean isPalin(String s, int left, int right) {
    while (left < right) {
        if (s.charAt(left) != s.charAt(right)) return false;
        left++; right--;
    }
    return true;
}
// Time: O(n), Space: O(1). LC #680.
```

The key insight: when you hit a mismatch, you have exactly two choices — skip the left character or skip the right character. If either choice leads to a palindrome, the answer is true.

</details>

---

## Capstone Connection

Two pointers power many AlgoForge problems. LC #1, #11, #15, #42, #167, #680 are all solved in `src/main/java/com/algoforge/problems/arrays/` using this pattern. The exercises build these.
