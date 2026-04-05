# 11.5 — Interval DP

## What Is Interval DP?

Interval DP operates on subproblems defined by intervals `[i, j]` of a sequence. The key pattern: solve smaller intervals first, then combine to solve larger ones.

```
Interval DP template:

for (int len = 2; len <= n; len++) {       // interval length
    for (int i = 0; i <= n - len; i++) {   // left endpoint
        int j = i + len - 1;               // right endpoint
        for (int k = i; k < j; k++) {      // split point
            dp[i][j] = optimize(dp[i][j], combine(dp[i][k], dp[k+1][j]));
        }
    }
}
```

**When to use:** Problems about merging/splitting sequences optimally — matrix chain, burst balloons, palindrome partition, optimal BST.

---

## Problem 1: Burst Balloons (LC #312)

Given an array of balloons, bursting balloon i scores `nums[left] * nums[i] * nums[right]`. Maximize total coins.

```
nums = [3, 1, 5, 8]

Burst order: 1, 5, 3, 8
  burst 1: 3*1*5   = 15
  burst 5: 3*5*8   = 120
  burst 3: 1*3*8   = 24
  burst 8: 1*8*1   = 8
  Total: 167

Optimal order: 1, 5, 3, 8 → answer: 167
```

**Insight:** Think LAST balloon to burst in range `[i,j]`, not first. When balloon k is last in [i,j], its left/right are `nums[i-1]` and `nums[j+1]` (the borders, which haven't been burst yet).

**State:** `dp[i][j]` = max coins bursting all balloons in `(i, j)` exclusive (balloons i and j are kept as "walls")

**Recurrence:** For each k in (i, j) as the LAST burst:  
`dp[i][j] = max(dp[i][k] + nums[i]*nums[k]*nums[j] + dp[k][j])`

```java
int maxCoins(int[] nums) {
    int n = nums.length;
    // Add boundary balloons (value 1)
    int[] arr = new int[n + 2];
    arr[0] = arr[n + 1] = 1;
    for (int i = 0; i < n; i++) arr[i + 1] = nums[i];
    int m = n + 2;

    int[][] dp = new int[m][m];

    // intervals of length 2 → len=2 means only the walls (no interior)
    for (int len = 2; len < m; len++) {
        for (int i = 0; i < m - len; i++) {
            int j = i + len;
            for (int k = i + 1; k < j; k++) {
                dp[i][j] = Math.max(dp[i][j],
                    dp[i][k] + arr[i] * arr[k] * arr[j] + dp[k][j]);
            }
        }
    }
    return dp[0][m - 1];
}
```

**Complexity:** O(n³) time, O(n²) space.

---

## Problem 2: Minimum Cost to Merge Stones (LC #1000)

Merge k consecutive piles into one. Cost = sum of merged piles. Find minimum cost to merge all into one pile.

```
stones = [3, 2, 4, 1], k = 2

Merge all pairs:
  [3,2,4,1] → [5,4,1] cost=5
  [5,4,1]   → [9,1]   cost=9
  [9,1]     → [10]    cost=10
  Total: 24

Or:
  [3,2,4,1] → [3,2,5] cost=5
  [3,2,5]   → [3,7]   cost=7
  [3,7]     → [10]    cost=10
  Total: 22
```

**Feasibility check:** `(n-1) % (k-1) == 0` must hold to reduce to 1 pile.

```java
int mergeStones(int[] stones, int k) {
    int n = stones.length;
    if ((n - 1) % (k - 1) != 0) return -1;

    int[] prefix = new int[n + 1];
    for (int i = 0; i < n; i++) prefix[i + 1] = prefix[i] + stones[i];

    int[][] dp = new int[n][n]; // dp[i][j] = min cost to merge stones[i..j]

    for (int len = k; len <= n; len++) {
        for (int i = 0; i <= n - len; i++) {
            int j = i + len - 1;
            dp[i][j] = Integer.MAX_VALUE;
            for (int mid = i; mid < j; mid += k - 1) {
                dp[i][j] = Math.min(dp[i][j], dp[i][mid] + dp[mid + 1][j]);
            }
            // If this interval can be merged into one pile
            if ((len - 1) % (k - 1) == 0) {
                dp[i][j] += prefix[j + 1] - prefix[i];
            }
        }
    }
    return dp[0][n - 1];
}
```

---

## Problem 3: Palindrome Partitioning II (LC #132)

Find the minimum number of cuts to partition a string such that every substring is a palindrome.

```
s = "aab"
Cuts: ["aa","b"] → 1 cut
```

**Two-pass approach:**
1. Precompute `isPalin[i][j]` — O(n²)
2. `dp[i]` = min cuts for `s[0..i]` — O(n²)

```java
int minCut(String s) {
    int n = s.length();

    // Precompute palindrome table
    boolean[][] isPalin = new boolean[n][n];
    for (int i = n - 1; i >= 0; i--) {
        for (int j = i; j < n; j++) {
            if (s.charAt(i) == s.charAt(j) && (j - i <= 2 || isPalin[i+1][j-1])) {
                isPalin[i][j] = true;
            }
        }
    }

    // dp[i] = min cuts for s[0..i]
    int[] dp = new int[n];
    for (int i = 0; i < n; i++) {
        if (isPalin[0][i]) { dp[i] = 0; continue; }
        dp[i] = i; // max cuts
        for (int j = 1; j <= i; j++) {
            if (isPalin[j][i]) {
                dp[i] = Math.min(dp[i], dp[j - 1] + 1);
            }
        }
    }
    return dp[n - 1];
}
```

---

## Problem 4: Strange Printer (LC #664)

A printer can only print a sequence of the same character, overwriting. Find minimum turns to print string s.

```
s = "abad"
Turns: 4 → print "aaaa", "bbbb" → Wait, overlap is the key:
  print "aaa" → aaa_
  print "b"   → ab__
  print "d"   → abad
  Actually: 3 turns
```

**State:** `dp[i][j]` = min turns to print `s[i..j]`

```java
int strangePrinter(String s) {
    int n = s.length();
    int[][] dp = new int[n][n];

    for (int i = n - 1; i >= 0; i--) {
        dp[i][i] = 1;
        for (int j = i + 1; j < n; j++) {
            dp[i][j] = dp[i][j - 1] + 1; // print s[j] separately
            // If s[k] == s[j], printing s[k..j] extends the print of s[k]
            for (int k = i; k < j; k++) {
                if (s.charAt(k) == s.charAt(j)) {
                    dp[i][j] = Math.min(dp[i][j], dp[i][k] + (k + 1 <= j - 1 ? dp[k + 1][j - 1] : 0));
                }
            }
        }
    }
    return dp[0][n - 1];
}
```

---

## Interval DP Summary

| Problem | Interval meaning | Split point k |
|---------|-----------------|--------------|
| Burst Balloons | Balloons in range | Last to burst |
| Merge Stones | Piles in range | Merge midpoint |
| Palindrome Partition | Substring [i,j] | First cut position |
| Matrix Chain | Matrices i to j | Where to split multiply |
| Strange Printer | Characters i to j | Same-char optimization |

## Common Mistakes

1. **Wrong loop direction:** Always iterate by **increasing interval length**, not by endpoint.
2. **Wrong base case:** `dp[i][i]` (single element) vs `dp[i][i+1]` (two elements).
3. **Off-by-one in Burst Balloons:** The boundary indices are exclusive — `dp[i][j]` means burst everything strictly between i and j.

## Try It Yourself

**1.** Matrix Chain Multiplication: Given dimensions `dims[]` where matrix i has size `dims[i-1] × dims[i]`, find minimum multiplications.

<details>
<summary>Solution</summary>

```java
int matrixChainOrder(int[] dims) {
    int n = dims.length - 1; // number of matrices
    int[][] dp = new int[n][n];

    for (int len = 2; len <= n; len++) {
        for (int i = 0; i <= n - len; i++) {
            int j = i + len - 1;
            dp[i][j] = Integer.MAX_VALUE;
            for (int k = i; k < j; k++) {
                int cost = dp[i][k] + dp[k+1][j] + dims[i] * dims[k+1] * dims[j+1];
                dp[i][j] = Math.min(dp[i][j], cost);
            }
        }
    }
    return dp[0][n-1];
}
```

</details>

## Capstone Connection

Interval DP is used in AlgoForge's "Compiler Problems" category — expression evaluation, optimal parenthesization, and code generation are all interval DP at heart.
