# 10.3 — Fenwick Tree (Binary Indexed Tree)

## What Is a Fenwick Tree?

A **Fenwick Tree** (or Binary Indexed Tree, BIT) answers **prefix sum** queries and supports **point updates** in O(log n) time with only O(n) space and simpler code than a Segment Tree.

```
Given: [3, 2, -1, 6, 5, 4, -3, 3, 7, 2]

Prefix sum query:  sum(1, 7) = 3+2-1+6+5+4-3 = 16
Point update:      arr[4] += 10 → update BIT
Re-query:          sum(1, 7) = 26
```

---

## The Key Insight: lowbit

Each index i in the BIT is responsible for a range of elements determined by `lowbit(i) = i & (-i)` (the lowest set bit of i).

```
i = 6 → binary: 110 → lowbit = 010 = 2 → covers indices [5,6]
i = 4 → binary: 100 → lowbit = 100 = 4 → covers indices [1,4]
i = 8 → binary: 1000 → lowbit = 8 → covers indices [1,8]

Fenwick Tree visualization (1-indexed):
Index:  1  2  3  4  5  6  7  8
Range:  1 1-2 3 1-4 5 5-6 7 1-8

bit[1]  = arr[1]
bit[2]  = arr[1] + arr[2]
bit[3]  = arr[3]
bit[4]  = arr[1] + arr[2] + arr[3] + arr[4]
bit[6]  = arr[5] + arr[6]
bit[8]  = arr[1] + ... + arr[8]
```

---

## Implementation (1-indexed)

```java
public class FenwickTree {
    private int[] bit;
    private int n;

    public FenwickTree(int n) {
        this.n = n;
        this.bit = new int[n + 1]; // 1-indexed
    }

    // Build from array in O(n)
    public FenwickTree(int[] nums) {
        this(nums.length);
        for (int i = 0; i < nums.length; i++) {
            update(i + 1, nums[i]);
        }
    }

    // Add delta to index i (1-indexed) — O(log n)
    public void update(int i, int delta) {
        for (; i <= n; i += i & (-i)) { // traverse up: add lowbit
            bit[i] += delta;
        }
    }

    // Prefix sum [1..i] — O(log n)
    public int prefixSum(int i) {
        int sum = 0;
        for (; i > 0; i -= i & (-i)) { // traverse down: remove lowbit
            sum += bit[i];
        }
        return sum;
    }

    // Range sum [l..r] (1-indexed) — O(log n)
    public int rangeSum(int l, int r) {
        return prefixSum(r) - prefixSum(l - 1);
    }
}
```

---

## Update and Query Traces

**Update index 3, add 5:**
```
i=3 (011), bit[3] += 5
next: i = 3 + (3 & -3) = 3 + 1 = 4, bit[4] += 5
next: i = 4 + (4 & -4) = 4 + 4 = 8, bit[8] += 5
next: i = 8 + 8 = 16 > n → stop
```

**Query prefix sum [1..7]:**
```
i=7 (111): sum += bit[7], i = 7 - (7&-7) = 7-1 = 6
i=6 (110): sum += bit[6], i = 6 - (6&-6) = 6-2 = 4
i=4 (100): sum += bit[4], i = 4 - (4&-4) = 4-4 = 0
Stop. Sum = bit[7] + bit[6] + bit[4]
         = arr[7] + (arr[5]+arr[6]) + (arr[1]+arr[2]+arr[3]+arr[4])
         = sum of arr[1..7] ✓
```

---

## Range Sum Query — Immutable Variant

For immutable arrays, prefix sums (`int[] prefix`) work in O(1) per query. Fenwick tree adds the ability to update efficiently.

---

## Fenwick vs Segment Tree

| | Fenwick Tree | Segment Tree |
|--|---|---|
| Space | O(n) | O(4n) |
| Code complexity | ~10 lines | ~40 lines |
| Point update | O(log n) | O(log n) |
| Range query | O(log n) | O(log n) |
| Range update | Needs two BITs | With lazy propagation |
| Range max/min | ✗ | ✓ |
| Arbitrary aggregates | ✗ | ✓ |
| Use | Prefix sum, order stats | Anything range-based |

---

## Count Inversions Using BIT

An **inversion** is a pair (i, j) where i < j but arr[i] > arr[j]. Count them in O(n log n):

```java
public int countInversions(int[] nums) {
    // Coordinate compress nums to [1..n]
    int n = nums.length;
    int[] sorted = nums.clone();
    Arrays.sort(sorted);
    Map<Integer, Integer> rank = new HashMap<>();
    for (int i = 0; i < n; i++) rank.put(sorted[i], i + 1);

    FenwickTree bit = new FenwickTree(n);
    int inversions = 0;

    for (int i = n - 1; i >= 0; i--) {
        int r = rank.get(nums[i]);
        // Count elements already seen (to the right) with value < nums[i]
        inversions += bit.prefixSum(r - 1);
        bit.update(r, 1);
    }
    return inversions;
}
```

---

## Range Sum Query — Mutable (Alternative to Segment Tree, LC #307)

```java
class NumArray {
    private FenwickTree bit;
    private int[] nums;

    public NumArray(int[] nums) {
        this.nums = nums.clone();
        bit = new FenwickTree(nums);
    }

    public void update(int index, int val) {
        bit.update(index + 1, val - nums[index]); // add the diff (1-indexed)
        nums[index] = val;
    }

    public int sumRange(int left, int right) {
        return bit.rangeSum(left + 1, right + 1); // convert to 1-indexed
    }
}
```

---

## 2D Fenwick Tree

For 2D range sum queries and point updates:

```java
public class Fenwick2D {
    private int[][] bit;
    private int m, n;

    public Fenwick2D(int m, int n) {
        this.m = m; this.n = n;
        bit = new int[m+1][n+1];
    }

    public void update(int r, int c, int delta) {
        for (int i = r; i <= m; i += i & (-i))
            for (int j = c; j <= n; j += j & (-j))
                bit[i][j] += delta;
    }

    public int query(int r, int c) {
        int sum = 0;
        for (int i = r; i > 0; i -= i & (-i))
            for (int j = c; j > 0; j -= j & (-j))
                sum += bit[i][j];
        return sum;
    }

    public int rangeQuery(int r1, int c1, int r2, int c2) {
        return query(r2,c2) - query(r1-1,c2) - query(r2,c1-1) + query(r1-1,c1-1);
    }
}
```

---

## Try It Yourself

**Problem:** Given a list of numbers, for each number find how many numbers to its right are smaller than itself. Return the result array. (LC #315 — Count of Smaller Numbers After Self)

<details>
<summary>Solution</summary>

```java
public List<Integer> countSmaller(int[] nums) {
    int n = nums.length;
    // Coordinate compress to [1..n]
    int[] sorted = nums.clone();
    Arrays.sort(sorted);
    Map<Integer, Integer> rank = new HashMap<>();
    int r = 1;
    for (int v : sorted) rank.putIfAbsent(v, r++);

    FenwickTree bit = new FenwickTree(n);
    int[] result = new int[n];

    for (int i = n - 1; i >= 0; i--) {
        int rnk = rank.get(nums[i]);
        result[i] = bit.prefixSum(rnk - 1); // count smaller already seen
        bit.update(rnk, 1);
    }

    List<Integer> ans = new ArrayList<>();
    for (int v : result) ans.add(v);
    return ans;
}
```

**How it works:** Process right to left. For each element, query how many elements already in the BIT have a rank less than ours (= are smaller in value). Then add our rank.

**Complexity:** O(n log n) time, O(n) space.

</details>

---

## Capstone Connection

Add `datastructures/advanced/FenwickTree.java` to AlgoForge. Include 1D and 2D variants. Add `problems/advanced/CountInversions.java` and `problems/advanced/CountSmaller.java`.
