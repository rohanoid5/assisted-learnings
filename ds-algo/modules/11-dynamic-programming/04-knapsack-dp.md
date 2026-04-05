# 11.4 — Knapsack DP

## The Knapsack Family

Knapsack problems involve selecting items with a weight/cost constraint to maximize value. Understanding the three variants unlocks many interview problems.

```
ITEMS: [weight, value]
  [2, 6]  [2, 10]  [3, 12]

CAPACITY: 5

0/1 Knapsack:   each item used 0 or 1 time
Unbounded:      each item used any number of times
Fractional:     can take fractions (greedy — NOT DP)
```

---

## 0/1 Knapsack

**State:** `dp[i][w]` = max value using first i items with capacity w

**Recurrence:**
```
dp[i][w] = dp[i-1][w]              (skip item i)
dp[i][w] = dp[i-1][w-wt[i]] + v[i] if wt[i] <= w   (take item i)
```

```
Items: [(w=2,v=6), (w=2,v=10), (w=3,v=12)], capacity=5

     0  1  2  3  4  5
  0  0  0  0  0  0  0
  1  0  0  6  6  6  6   item 1: (2, 6)
  2  0  0 10 10 16 16   item 2: (2,10)
  3  0  0 10 12 16 22   item 3: (3,12)
  
Answer: dp[3][5] = 22
```

**Full implementation:**
```java
int knapsack(int[] weights, int[] values, int capacity) {
    int n = weights.length;
    // Space-optimized: 1D dp, traverse weight BACKWARDS
    int[] dp = new int[capacity + 1];

    for (int i = 0; i < n; i++) {
        for (int w = capacity; w >= weights[i]; w--) {
            dp[w] = Math.max(dp[w], dp[w - weights[i]] + values[i]);
        }
    }
    return dp[capacity];
}
```

> **Critical:** Traverse weights backwards in 0/1 knapsack to prevent using an item twice. Forward traversal = unbounded knapsack.

---

## Unbounded Knapsack

Each item can be used unlimited times.

```
Coins = [1, 2, 5], amount = 11

dp[11] = min coins = 3  (5+5+1)
```

**Traverse weight FORWARDS (allows reuse):**

```java
int unboundedKnapsack(int[] weights, int[] values, int capacity) {
    int[] dp = new int[capacity + 1];
    for (int w = 1; w <= capacity; w++) {
        for (int i = 0; i < weights.length; i++) {
            if (weights[i] <= w) {
                dp[w] = Math.max(dp[w], dp[w - weights[i]] + values[i]);
            }
        }
    }
    return dp[capacity];
}
```

---

## Problem 1: Partition Equal Subset Sum (LC #416)

Given an array, determine if it can be partitioned into two subsets with equal sum.

```
nums = [1, 5, 11, 5]
Sum = 22, target = 11
Partition: {1, 5, 5} and {11}  ✓
```

**This is 0/1 knapsack asking: "Can we fill capacity exactly = target?"**

```java
boolean canPartition(int[] nums) {
    int sum = 0;
    for (int n : nums) sum += n;
    if (sum % 2 != 0) return false;

    int target = sum / 2;
    boolean[] dp = new boolean[target + 1];
    dp[0] = true; // empty subset sums to 0

    for (int num : nums) {
        for (int w = target; w >= num; w--) { // backwards = 0/1
            dp[w] |= dp[w - num];
        }
    }
    return dp[target];
}
```

---

## Problem 2: Target Sum (LC #494)

Add + or − to each number, count assignments equaling target.

```
nums = [1,1,1,1,1], target = 3
Answer: 5
  -1+1+1+1+1 = 3
  +1-1+1+1+1 = 3
  +1+1-1+1+1 = 3
  +1+1+1-1+1 = 3
  +1+1+1+1-1 = 3
```

**Mathematical reduction:** Let P = sum of positive, N = sum of negative.  
`P - N = target` and `P + N = total` → `P = (total + target) / 2`  
So: count subsets that sum to `(total + target) / 2`.

```java
int findTargetSumWays(int[] nums, int target) {
    int total = 0;
    for (int n : nums) total += n;
    if ((total + target) % 2 != 0 || Math.abs(target) > total) return 0;

    int goal = (total + target) / 2;
    int[] dp = new int[goal + 1];
    dp[0] = 1;

    for (int num : nums) {
        for (int w = goal; w >= num; w--) {
            dp[w] += dp[w - num];
        }
    }
    return dp[goal];
}
```

---

## Problem 3: Ones and Zeroes (LC #474)

Given strings of 0s and 1s, find the largest subset where total 0s ≤ m and total 1s ≤ n.

**2D 0/1 knapsack:** `dp[i][j]` = max strings using at most i zeros and j ones.

```java
int findMaxForm(String[] strs, int m, int n) {
    int[][] dp = new int[m+1][n+1];

    for (String s : strs) {
        int zeros = 0, ones = 0;
        for (char c : s.toCharArray()) {
            if (c == '0') zeros++; else ones++;
        }
        // traverse backwards: 0/1 knapsack
        for (int i = m; i >= zeros; i--)
            for (int j = n; j >= ones; j--)
                dp[i][j] = Math.max(dp[i][j], dp[i-zeros][j-ones] + 1);
    }
    return dp[m][n];
}
```

---

## Problem 4: Last Stone Weight II (LC #1049)

Smash stones together pairwise, minimize last remaining stone.

**Key insight:** This is the same as "partition into two groups, minimize difference = partition equal subset sum variant."

Minimize |S1 - S2| where S1 + S2 = total → maximize S1 ≤ total/2 (0/1 knapsack).

```java
int lastStoneWeightII(int[] stones) {
    int total = 0;
    for (int s : stones) total += s;
    int target = total / 2;

    boolean[] dp = new boolean[target + 1];
    dp[0] = true;

    for (int s : stones)
        for (int w = target; w >= s; w--)
            dp[w] |= dp[w - s];

    for (int w = target; w >= 0; w--)
        if (dp[w]) return total - 2 * w;

    return 0; // unreachable
}
```

---

## The Knapsack Cheat Sheet

| Variant | Loop order | Use case |
|---------|-----------|---------|
| 0/1 Knapsack | Outer: items, Inner: caps **backwards** | Each item once |
| Unbounded | Outer: caps **forwards**, Inner: items | Each item many times |
| 0/1 Combinations | Outer: items, Inner: caps backwards | Count ways, each once |
| Unbounded Combinations | Outer: items, Inner: caps forwards | Count ways, unlimited |
| Permutations | Outer: **caps**, Inner: items | Order matters |

**Permutation example (LC #377 — Combination Sum IV):**
```java
int combinationSum4(int[] nums, int target) {
    int[] dp = new int[target + 1];
    dp[0] = 1;
    for (int i = 1; i <= target; i++)       // outer = target
        for (int num : nums)                 // inner = items
            if (num <= i) dp[i] += dp[i-num];
    return dp[target];
}
```

---

## Try It Yourself

**1.** Coin Change II (LC #518): Count combinations to make amount. Is this combination (order irrelevant) or permutation (order matters)?

<details>
<summary>Solution</summary>

```java
// Combination count (order irrelevant) = outer: items, inner: caps
int change(int amount, int[] coins) {
    int[] dp = new int[amount + 1];
    dp[0] = 1;
    for (int coin : coins)           // outer: items
        for (int i = coin; i <= amount; i++)  // inner: forwards (unbounded)
            dp[i] += dp[i - coin];
    return dp[amount];
}
// Note: if you swap the loops (outer: amount, inner: coins) 
// you get permutation count instead!
```

</details>

## Capstone Connection

The subset sum check `dp[w] |= dp[w - num]` is one of the most frequently reused patterns in AlgoForge's contest problem set. Knowing the loop order (forward vs backward) determines whether you get the right answer.
