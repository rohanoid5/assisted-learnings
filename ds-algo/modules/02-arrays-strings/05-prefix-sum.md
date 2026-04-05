# 2.5 — Prefix Sum & Difference Arrays

## Concept

A prefix sum array enables **O(1) range sum queries** after an O(n) precomputation. The key insight: `sum(i, j) = prefix[j+1] - prefix[i]`. This trick transforms O(n) per query into O(1), and it extends to 2D matrices, difference arrays for range updates, and subarray sum / modular arithmetic problems.

---

## Deep Dive

### Building a Prefix Sum

```
Original:  arr = [2,  3,  1,  5,  4,  7]
                  ↑   ↑   ↑   ↑   ↑   ↑
Indices:          0   1   2   3   4   5

Prefix:  pre = [0, 2,  5,  6, 11, 15, 22]
                ↑  ↑   ↑   ↑   ↑   ↑   ↑
Indices:       [0] [1] [2] [3] [4] [5] [6]

pre[i] = sum of arr[0..i-1]   (pre[0] = 0 — sentinel for easy math)
pre[i] = pre[i-1] + arr[i-1]

Range sum from index l to r (inclusive):
sum(l, r) = pre[r+1] - pre[l]

Example: sum(1, 4) = pre[5] - pre[1] = 15 - 2 = 13
         Verify: 3+1+5+4 = 13  ✓
```

---

### Implementation

```java
// Build prefix sum — O(n) time, O(n) space
int[] prefix = new int[arr.length + 1];
for (int i = 0; i < arr.length; i++) {
    prefix[i + 1] = prefix[i] + arr[i];
}

// Query range [l, r] — O(1) per query
int rangeSum = prefix[r + 1] - prefix[l];
```

---

### Subarray Sum Equals K — The Advanced Pattern

Prefix sum + HashMap = O(n) solution to "count subarrays summing to k":

```
For each index j, we want the number of l's where prefix[j+1] - prefix[l] = k
→ prefix[l] = prefix[j+1] - k
→ Count how many previous prefix values equal (current prefix - k)
```

```java
public int subarraySum(int[] nums, int k) {
    Map<Integer, Integer> count = new HashMap<>();
    count.put(0, 1);  // empty prefix has sum 0

    int prefixSum = 0, result = 0;
    for (int x : nums) {
        prefixSum += x;
        // How many previous prefix values equal prefixSum - k?
        result += count.getOrDefault(prefixSum - k, 0);
        count.merge(prefixSum, 1, Integer::sum);
    }
    return result;
    // Time: O(n), Space: O(n)
}
```

---

### Difference Array — Range Updates in O(1)

A difference array lets you apply multiple range update operations in O(1) each, then reconstruct in O(n):

```
diff[i] = arr[i] - arr[i-1]  (first difference)

To add val to arr[l..r]:
  diff[l]   += val   (where the increase starts)
  diff[r+1] -= val   (where the increase ends)

After all updates, reconstruct arr by prefix-summing diff.
```

```java
// Apply m range updates all at once
public int[] applyUpdates(int n, int[][] updates) {
    int[] diff = new int[n + 1];

    for (int[] update : updates) {
        int l = update[0], r = update[1], val = update[2];
        diff[l] += val;         // O(1) per update
        if (r + 1 <= n) diff[r + 1] -= val;
    }

    // Reconstruct using prefix sum of diff
    int[] result = new int[n];
    int running = 0;
    for (int i = 0; i < n; i++) {
        running += diff[i];
        result[i] = running;
    }
    return result;
    // Total: O(m + n) — far better than O(m*n) brute force
}
```

---

### 2D Prefix Sum

```
For matrix of n rows and m columns:
pre[i][j] = sum of rectangle from (0,0) to (i-1, j-1)
           = pre[i-1][j] + pre[i][j-1] - pre[i-1][j-1] + matrix[i-1][j-1]

Query: sum of sub-rectangle from (r1,c1) to (r2,c2) (0-indexed):
= pre[r2+1][c2+1] - pre[r1][c2+1] - pre[r2+1][c1] + pre[r1][c1]
```

---

## Code Examples

### Example 1: Product of Array Except Self (No Division)

```java
// result[i] = product of all elements except arr[i]
// Key: prefix products from left, suffix products from right
// Time: O(n), Space: O(1) auxiliary (output array not counted)

public int[] productExceptSelf(int[] nums) {
    int n = nums.length;
    int[] result = new int[n];

    // Left pass: result[i] = product of nums[0..i-1]
    result[0] = 1;
    for (int i = 1; i < n; i++) result[i] = result[i - 1] * nums[i - 1];

    // Right pass: multiply by product of nums[i+1..n-1]
    int right = 1;
    for (int i = n - 1; i >= 0; i--) {
        result[i] *= right;
        right *= nums[i];
    }
    return result;
}
```

---

## Try It Yourself

**Exercise:** Given an integer array `nums`, return `true` if there is a subarray (of length ≥ 2) whose sum is a multiple of `k`.

Input: `nums = [23, 2, 4, 6, 7]`, `k = 6` → `true` (subarray `[2,4]` sums to 6)

Hint: Use prefix sums modulo k. Two prefix sums with the same remainder mean the subarray between them is divisible by k.

<details>
<summary>Show solution</summary>

```java
public boolean checkSubarraySum(int[] nums, int k) {
    // prefixMod → earliest index where this remainder occurred
    Map<Integer, Integer> firstSeen = new HashMap<>();
    firstSeen.put(0, -1);  // remainder 0 seen before index 0

    int prefixSum = 0;
    for (int i = 0; i < nums.length; i++) {
        prefixSum += nums[i];
        int mod = prefixSum % k;
        if (firstSeen.containsKey(mod)) {
            if (i - firstSeen.get(mod) >= 2) return true;  // length ≥ 2
        } else {
            firstSeen.put(mod, i);  // only store first occurrence
        }
    }
    return false;
    // Time: O(n), Space: O(k) LC #523
}
```

The insight: `sum(l+1..r) % k == 0` iff `prefix[r] % k == prefix[l] % k`. Storing the first index of each remainder lets us check if the subarray is long enough.

</details>

---

## Capstone Connection

`RangeSum.java` and `SubarraySum.java` in AlgoForge's `problems/arrays/` demonstrate prefix sum in both its classic and advanced (modular) forms. Look for the 2D prefix sum implementation in Module 08 (Trees) when working with matrix queries.
