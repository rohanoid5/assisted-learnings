# Module 11 — Dynamic Programming Exercises

## Overview

These exercises mirror the hardest DP patterns you will encounter in FAANG interviews. The goal is not to memorize solutions — it is to build the muscle memory for **defining state, writing the recurrence, and handling the base cases** without looking anything up. Implement every solution in `capstone/algoforge/src/main/java/com/algoforge/problems/dp/`.

---

## Exercise 1: Climbing Stairs — 1D DP Warm-Up (LC #70)

**Goal:** Implement three approaches (naive recursion → memoization → tabulation → space-optimized) to fully internalize the DP transformation process.

**Problem:** You are climbing a staircase with `n` steps. Each time you can climb 1 or 2 steps. How many distinct ways can you climb to the top?

1. Implement naive recursion. Run it for `n = 40` and observe the slowdown.
2. Add a memoization cache (`int[] memo`). Verify it returns the same answer instantly.
3. Convert to bottom-up tabulation (`int[] dp`).
4. Optimize space from O(n) to O(1) by keeping only the last two values.
5. Verify: `n=1 → 1`, `n=2 → 2`, `n=5 → 8`, `n=10 → 89`.

```java
// Skeleton in problems/dp/ClimbingStairs.java
public class ClimbingStairs {
    // Approach 1: naive
    public int climbNaive(int n) { ... }

    // Approach 2: memoized
    public int climbMemo(int n) { ... }

    // Approach 3: tabulation
    public int climbDP(int n) { ... }

    // Approach 4: space-optimized
    public int climbOptimal(int n) { ... }
}
```

**Verification:**
```
climbOptimal(1)  == 1
climbOptimal(5)  == 8
climbOptimal(10) == 89
climbOptimal(45) == 1836311903
```

<details>
<summary>Show solution</summary>

```java
public class ClimbingStairs {

    // Approach 1: naive — O(2^n)
    public int climbNaive(int n) {
        if (n <= 1) return 1;
        return climbNaive(n - 1) + climbNaive(n - 2);
    }

    // Approach 2: memoized — O(n) time, O(n) space
    public int climbMemo(int n) {
        int[] memo = new int[n + 1];
        return helper(n, memo);
    }
    private int helper(int n, int[] memo) {
        if (n <= 1) return 1;
        if (memo[n] != 0) return memo[n];
        return memo[n] = helper(n - 1, memo) + helper(n - 2, memo);
    }

    // Approach 3: tabulation — O(n) time, O(n) space
    public int climbDP(int n) {
        if (n <= 1) return 1;
        int[] dp = new int[n + 1];
        dp[0] = 1; dp[1] = 1;
        for (int i = 2; i <= n; i++)
            dp[i] = dp[i - 1] + dp[i - 2];
        return dp[n];
    }

    // Approach 4: space-optimized — O(n) time, O(1) space
    public int climbOptimal(int n) {
        if (n <= 1) return 1;
        int prev2 = 1, prev1 = 1;
        for (int i = 2; i <= n; i++) {
            int curr = prev1 + prev2;
            prev2 = prev1;
            prev1 = curr;
        }
        return prev1;
    }
}
```
</details>

---

## Exercise 2: Coin Change — Unbounded Knapsack (LC #322)

**Goal:** Implement the canonical unbounded knapsack problem and trace the DP table for a small example.

**Problem:** Given an array of coin denominations and a target amount, return the fewest number of coins needed to make up that amount. Return `-1` if it is not possible.

1. Define the state: what does `dp[i]` mean?
2. Write the recurrence: for each coin c, if `i >= c`, consider `dp[i - c] + 1`.
3. Set the base case and check the boundary condition after the loop.
4. Trace the DP table for `coins = [1, 5, 6]`, `amount = 11` by hand before coding.

**Trace (fill in yourself first):**
```
coins = [1, 5, 6], amount = 11

i:    0  1  2  3  4  5  6  7  8  9  10  11
dp:   0  _  _  _  _  _  _  _  _  _   _   _

Expected: dp[11] = 2 (6 + 5)
```

**Verification:**
```
coinChange([1,5,6], 11)   == 2
coinChange([2], 3)         == -1
coinChange([1,2,5], 11)   == 3
```

<details>
<summary>Show solution</summary>

```java
public class CoinChange {

    // O(amount * coins.length) time, O(amount) space
    public int coinChange(int[] coins, int amount) {
        int[] dp = new int[amount + 1];
        Arrays.fill(dp, amount + 1);   // fill with "infinity"
        dp[0] = 0;                     // base case: 0 coins to make amount 0

        for (int i = 1; i <= amount; i++) {
            for (int coin : coins) {
                if (i >= coin) {
                    dp[i] = Math.min(dp[i], dp[i - coin] + 1);
                }
            }
        }
        return dp[amount] > amount ? -1 : dp[amount];
    }
}

/*
 Trace for coins=[1,5,6], amount=11:
 dp[0]  = 0
 dp[1]  = dp[0]+1 = 1  (coin=1)
 dp[2]  = 2
 dp[3]  = 3
 dp[4]  = 4
 dp[5]  = min(dp[4]+1, dp[0]+1) = 1  (coin=5)
 dp[6]  = min(dp[5]+1, dp[1]+1, dp[0]+1) = 1  (coin=6)
 dp[7]  = min(dp[6]+1, dp[2]+1, dp[1]+1) = 2
 dp[8]  = 2
 dp[9]  = 2
 dp[10] = 2
 dp[11] = min(dp[10]+1, dp[6]+1, dp[5]+1) = 2  (6+5)
*/
```
</details>

---

## Exercise 3: Longest Common Subsequence — 2D DP (LC #1143)

**Goal:** Master the canonical 2D DP table construction for two-sequence problems.

**Problem:** Given two strings `text1` and `text2`, return the length of their longest common subsequence. A subsequence does not need to be contiguous.

1. Define the state: what does `dp[i][j]` represent?
2. Write the recurrence:
   - If `text1[i-1] == text2[j-1]`: `dp[i][j] = dp[i-1][j-1] + 1`
   - Otherwise: `dp[i][j] = max(dp[i-1][j], dp[i][j-1])`
3. Trace the table for `"abcde"` and `"ace"` before coding.
4. Implement the solution and then optimize space from O(m*n) to O(min(m,n)).

**Trace:**
```
text1 = "abcde", text2 = "ace"
Expected LCS = 3 ("ace")

    ""  a  c  e
""   0  0  0  0
a    0  _  _  _
b    0  _  _  _
c    0  _  _  _
d    0  _  _  _
e    0  _  _  _
```

**Verification:**
```
lcs("abcde", "ace")  == 3
lcs("abc", "abc")    == 3
lcs("abc", "def")    == 0
```

<details>
<summary>Show solution</summary>

```java
public class LongestCommonSubsequence {

    // O(m*n) time, O(m*n) space
    public int longestCommonSubsequence(String text1, String text2) {
        int m = text1.length(), n = text2.length();
        int[][] dp = new int[m + 1][n + 1];

        for (int i = 1; i <= m; i++) {
            for (int j = 1; j <= n; j++) {
                if (text1.charAt(i - 1) == text2.charAt(j - 1)) {
                    dp[i][j] = dp[i - 1][j - 1] + 1;
                } else {
                    dp[i][j] = Math.max(dp[i - 1][j], dp[i][j - 1]);
                }
            }
        }
        return dp[m][n];
    }

    // Space-optimized: O(min(m,n)) space — roll two rows
    public int lcsOptimal(String text1, String text2) {
        if (text1.length() < text2.length()) {
            String tmp = text1; text1 = text2; text2 = tmp;
        }
        int m = text1.length(), n = text2.length();
        int[] prev = new int[n + 1], curr = new int[n + 1];

        for (int i = 1; i <= m; i++) {
            for (int j = 1; j <= n; j++) {
                if (text1.charAt(i - 1) == text2.charAt(j - 1)) {
                    curr[j] = prev[j - 1] + 1;
                } else {
                    curr[j] = Math.max(prev[j], curr[j - 1]);
                }
            }
            int[] temp = prev; prev = curr; curr = temp;
            Arrays.fill(curr, 0);
        }
        return prev[n];
    }
}

/*
 Filled table for "abcde" / "ace":
     ""  a  c  e
 ""   0  0  0  0
 a    0  1  1  1
 b    0  1  1  1
 c    0  1  2  2
 d    0  1  2  2
 e    0  1  2  3   ← answer
*/
```
</details>

---

## Exercise 4: 0/1 Knapsack — Partition Equal Subset Sum (LC #416)

**Goal:** Recognize the partition problem as a 0/1 knapsack variant and implement the space-optimized boolean DP.

**Problem:** Given an integer array `nums`, return `true` if you can partition the array into two subsets such that the sum of elements in both subsets is equal.

1. Compute the total sum. An equal partition is only possible if the sum is even.
2. Reduce to: "can any subset sum to `total/2`?" — this is 0/1 knapsack.
3. Define state: `dp[j]` = can we reach sum `j` using some subset?
4. Iterate through nums, update `dp` from right-to-left (to avoid using the same item twice).

**Why right-to-left?**
```
If we iterated left-to-right, adding num=3 could update dp[3] = true,
and then dp[6] = dp[3] + num = true — using num=3 twice.
Right-to-left ensures each item is counted at most once.
```

**Verification:**
```
canPartition([1,5,11,5]) == true  (subsets: [1,5,5] and [11])
canPartition([1,2,3,5])  == false
canPartition([1,1])      == true
```

<details>
<summary>Show solution</summary>

```java
public class PartitionEqualSubsetSum {

    public boolean canPartition(int[] nums) {
        int total = 0;
        for (int n : nums) total += n;
        if (total % 2 != 0) return false;

        int target = total / 2;
        boolean[] dp = new boolean[target + 1];
        dp[0] = true;  // empty subset sums to 0

        for (int num : nums) {
            // traverse right-to-left to prevent reuse of same item
            for (int j = target; j >= num; j--) {
                dp[j] = dp[j] || dp[j - num];
            }
        }
        return dp[target];
    }
}

/*
 Trace for [1,5,11,5], target=11:

 Initial: dp[0]=T, rest=F

 num=1: dp[1]=dp[0]=T
        dp: T T F F F F F F F F F F

 num=5: dp[6]=dp[1]=T, dp[5]=dp[0]=T
        dp: T T F F F T T F F F F F

 num=11: dp[11]=dp[0]=T
         dp: T T F F F T T F F F F T  ← found target!
*/
```
</details>

---

## Exercise 5: Edit Distance — Full 2D DP with Backtracking (LC #72)

**Goal:** Implement edit distance, trace the DP table, and extend the solution to reconstruct the actual sequence of operations.

**Problem:** Given two strings `word1` and `word2`, return the minimum number of operations (insert, delete, replace) to transform `word1` into `word2`.

1. Define `dp[i][j]` = minimum edits to transform `word1[0..i-1]` to `word2[0..j-1]`.
2. Write the three cases in the recurrence.
3. Trace the table for `"horse"` → `"ros"`.
4. **Bonus:** Backtrack through the filled DP table to reconstruct the operations.

**Trace:**
```
word1 = "horse", word2 = "ros"
Expected: 3 operations

       ""  r  o  s
  ""    0  1  2  3
  h     1  _  _  _
  o     2  _  _  _
  r     3  _  _  _
  s     4  _  _  _
  e     5  _  _  _
```

**Verification:**
```
minDistance("horse", "ros")   == 3
minDistance("intention", "execution") == 5
minDistance("", "abc")        == 3
```

<details>
<summary>Show solution</summary>

```java
public class EditDistance {

    // O(m*n) time and space
    public int minDistance(String word1, String word2) {
        int m = word1.length(), n = word2.length();
        int[][] dp = new int[m + 1][n + 1];

        // Base cases: transform from/to empty string
        for (int i = 0; i <= m; i++) dp[i][0] = i;  // delete i chars
        for (int j = 0; j <= n; j++) dp[0][j] = j;  // insert j chars

        for (int i = 1; i <= m; i++) {
            for (int j = 1; j <= n; j++) {
                if (word1.charAt(i - 1) == word2.charAt(j - 1)) {
                    dp[i][j] = dp[i - 1][j - 1];          // no op needed
                } else {
                    dp[i][j] = 1 + Math.min(
                        dp[i - 1][j - 1],  // replace
                        Math.min(
                            dp[i - 1][j],  // delete from word1
                            dp[i][j - 1]   // insert into word1
                        )
                    );
                }
            }
        }
        return dp[m][n];
    }

    // Bonus: reconstruct the edit operations
    public List<String> getOperations(String word1, String word2) {
        int m = word1.length(), n = word2.length();
        int[][] dp = new int[m + 1][n + 1];
        for (int i = 0; i <= m; i++) dp[i][0] = i;
        for (int j = 0; j <= n; j++) dp[0][j] = j;
        for (int i = 1; i <= m; i++) {
            for (int j = 1; j <= n; j++) {
                if (word1.charAt(i - 1) == word2.charAt(j - 1)) {
                    dp[i][j] = dp[i - 1][j - 1];
                } else {
                    dp[i][j] = 1 + Math.min(dp[i-1][j-1], Math.min(dp[i-1][j], dp[i][j-1]));
                }
            }
        }

        // Backtrack
        List<String> ops = new ArrayList<>();
        int i = m, j = n;
        while (i > 0 || j > 0) {
            if (i > 0 && j > 0 && word1.charAt(i-1) == word2.charAt(j-1)) {
                i--; j--;  // match — no op
            } else if (j > 0 && (i == 0 || dp[i][j-1] < dp[i-1][j] && dp[i][j-1] <= dp[i-1][j-1])) {
                ops.add(0, "Insert '" + word2.charAt(j-1) + "' at pos " + (i));
                j--;
            } else if (i > 0 && (j == 0 || dp[i-1][j] < dp[i][j-1] && dp[i-1][j] <= dp[i-1][j-1])) {
                ops.add(0, "Delete '" + word1.charAt(i-1) + "' at pos " + (i-1));
                i--;
            } else {
                ops.add(0, "Replace '" + word1.charAt(i-1) + "' with '" + word2.charAt(j-1) + "' at pos " + (i-1));
                i--; j--;
            }
        }
        return ops;
    }
}

/*
 Filled table for "horse" → "ros":
        ""  r  o  s
  ""     0  1  2  3
  h      1  1  2  3
  o      2  2  1  2
  r      3  2  2  2
  s      4  3  3  2
  e      5  4  4  3   ← answer: 3

 Operations: replace h→r, delete r, delete e  (or equivalent 3-op sequence)
*/
```
</details>

---

## Exercise 6: Best Time to Buy and Sell Stock III — State Machine DP (LC #123)

**Goal:** Understand multi-dimensional DP state design using a stock trading state machine.

**Problem:** You may complete at most 2 transactions. Find the maximum profit. You must sell before you buy again.

1. State: `dp[k][0]` = max profit with at most `k` transactions, not holding stock; `dp[k][1]` = holding stock.
2. Transitions:
   - `dp[k][0] = max(dp[k][0], dp[k][1] + price)`  (sell today or do nothing)
   - `dp[k][1] = max(dp[k][1], dp[k-1][0] - price)`  (buy today using a new transaction)
3. Implement for exactly 2 transactions without using arrays (just 4 variables).

**Verification:**
```
maxProfit([3,3,5,0,0,3,1,4]) == 6
maxProfit([1,2,3,4,5])       == 4
maxProfit([7,6,4,3,1])       == 0
```

<details>
<summary>Show solution</summary>

```java
public class BestTimeToBuySellStock3 {

    // State machine DP — O(n) time, O(1) space
    public int maxProfit(int[] prices) {
        // buy1: max profit after buying for the 1st time
        // sell1: max profit after selling for the 1st time
        // buy2: max profit after buying for the 2nd time
        // sell2: max profit after selling for the 2nd time
        int buy1 = Integer.MIN_VALUE, sell1 = 0;
        int buy2 = Integer.MIN_VALUE, sell2 = 0;

        for (int price : prices) {
            buy1  = Math.max(buy1,  -price);          // buy 1st stock
            sell1 = Math.max(sell1, buy1 + price);    // sell 1st stock
            buy2  = Math.max(buy2,  sell1 - price);   // buy 2nd stock using profit from 1st
            sell2 = Math.max(sell2, buy2 + price);    // sell 2nd stock
        }
        return sell2;
    }

    // Generalized: at most K transactions — O(nk) time, O(k) space
    public int maxProfitK(int k, int[] prices) {
        int n = prices.length;
        if (k >= n / 2) {
            // unlimited transactions
            int profit = 0;
            for (int i = 1; i < n; i++)
                profit += Math.max(0, prices[i] - prices[i - 1]);
            return profit;
        }
        int[] buy = new int[k + 1], sell = new int[k + 1];
        Arrays.fill(buy, Integer.MIN_VALUE);
        for (int price : prices) {
            for (int j = k; j >= 1; j--) {
                sell[j] = Math.max(sell[j], buy[j] + price);
                buy[j]  = Math.max(buy[j],  sell[j - 1] - price);
            }
        }
        return sell[k];
    }
}
```
</details>

---

## Capstone Checkpoint ✅

After completing these exercises, add the following to AlgoForge:

- [ ] `src/main/java/com/algoforge/problems/dp/ClimbingStairs.java`
- [ ] `src/main/java/com/algoforge/problems/dp/CoinChange.java`
- [ ] `src/main/java/com/algoforge/problems/dp/LongestCommonSubsequence.java`
- [ ] `src/main/java/com/algoforge/problems/dp/PartitionEqualSubsetSum.java`
- [ ] `src/main/java/com/algoforge/problems/dp/EditDistance.java`
- [ ] `src/main/java/com/algoforge/problems/dp/BestTimeToBuySellStock3.java`
- [ ] Corresponding tests in `src/test/java/com/algoforge/problems/dp/`

Run `mvn test` — all 6 problem classes should have passing tests before moving to Module 12.
