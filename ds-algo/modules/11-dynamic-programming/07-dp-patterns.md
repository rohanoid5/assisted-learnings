# 11.7 — DP Patterns & Problem Recognition

## The DP Decision Tree

Use this flowchart when you see a candidate DP problem:

```
Is the answer the BEST of something?
(min/max/count/exists)
            │
            ▼
Can you express the answer in terms
of answers to SMALLER subproblems?
            │
     ┌──────┴──────┐
    YES             NO → Not DP (greedy/math?)
     │
     ▼
Do subproblems OVERLAP?
     │
     ├── No → Divide & Conquer (e.g., merge sort)
     │
     └── Yes → DP ✓
          │
          ▼
    How many state dimensions?
          │
     ┌────┴──────────────────┐
    1D                       2D
     │                        │
  Linear path,         Two sequences,
  array index          grid, interval
     │                        │
     ▼                        ▼
Subsequence?          Two strings? → LCS/Edit Distance
  → LIS family        Grid? → Unique Paths, Maximal Square
Item selection?       Interval [i,j]? → Burst Balloons, Palindrome
  → Knapsack           
                     
    Subset selection?
    Assign items?
    Visit all nodes?
         │
         ▼
    Bitmask DP (n ≤ 20)
```

---

## Pattern Catalog

### 1. Counting Paths / Ways

**Signals:** "How many ways", "count the number of..."  
**State:** `dp[i]` = ways to reach state i  
**Recurrence:** `dp[i] += dp[j]` for all valid predecessors j  

Examples: Climbing Stairs (LC #70), Unique Paths (LC #62), Decode Ways (LC #91), Coin Change II (LC #518)

```java
// Template
for (int i = 1; i <= n; i++)
    for each valid prev state j:
        dp[i] += dp[j]
```

### 2. Optimal on Linear Sequence

**Signals:** "Maximum sum", "minimum cost", given array, choose elements  
**State:** `dp[i]` = optimal for first i elements  
**Recurrence:** Depends on what "choosing" means  

Examples: House Robber (LC #198), Max Subarray (LC #53), Jump Game II (LC #45)

```java
// 1-back dependency
dp[i] = max/min(dp[i-1] + something, dp[i-2] + something_else)
```

### 3. LCS / Edit Distance Family

**Signals:** "Two strings", "minimum operations to transform"  
**State:** `dp[i][j]` = answer for first i chars of s1, first j chars of s2  
**Recurrence:** Character match → diagonal; else → left/up/diagonal + 1  

Examples: LCS (LC #1143), Edit Distance (LC #72), Distinct Subsequences (LC #115), Wildcard Match (LC #44)

### 4. Knapsack / Subset Sum

**Signals:** "Select items", "partition", "subset with target sum"  
**State:** `dp[i][w]` or `dp[w]` after considering i items  
**Key:** Backward = 0/1, forward = unbounded, outer=target+inner=items = permutations  

Examples: 0/1 Knapsack, Coin Change (LC #322), Partition Equal Subset (LC #416), Target Sum (LC #494)

### 5. Interval DP

**Signals:** "Optimal way to split/merge a sequence", interval-based optimization  
**State:** `dp[i][j]` = answer for subsequence from i to j  
**Key:** Fill by increasing length (bottom-up)  

Examples: Burst Balloons (LC #312), Palindrome Partition II (LC #132), Matrix Chain, Merge Stones (LC #1000)

### 6. Bitmask DP

**Signals:** "Visit all nodes", "assign all items", n ≤ 20  
**State:** `dp[mask][i]` or `dp[mask]`  

Examples: TSP, Shortest Path All Nodes (LC #847), Minimum XOR Sum (LC #1879)

### 7. State Machine DP

**Signals:** Multiple "modes" at each step (buy/sell/rest), choose which to transition to  
**State:** `dp[i][state]` = optimal at position i in state  

Examples: Best Time to Buy/Sell Stock (LC #121-188), House Robber II

```java
// Stock buy-sell with cooldown (LC #309)
int[] hold  = new int[n+1]; // holding stock
int[] sold  = new int[n+1]; // just sold
int[] rest  = new int[n+1]; // cooldown

for (int i = 0; i < n; i++) {
    hold[i+1] = Math.max(hold[i], rest[i]  - prices[i]);
    sold[i+1] = hold[i] + prices[i];
    rest[i+1] = Math.max(rest[i], sold[i]);
}
return Math.max(sold[n], rest[n]);
```

---

## Stock Trading Problems (State Machine)

This family (LC #121-188) all share the same state machine structure:

```
LC #121: 1 transaction:
  profit = max(prices[i] - min price so far)

LC #122: Unlimited transactions:
  profit at each day: if prices[i] > prices[i-1] take the diff

LC #123: 2 transactions:
  dp[k][0] = max cash with k transactions used, not holding
  dp[k][1] = max cash with k transactions used, holding

LC #188: k transactions: same as #123 generalized

LC #309: +Cooldown:   sold → rest → buying allowed again
LC #714: +Fee:        dp[hold][notHold] with fee subtracted on sell
```

---

## Optimization Techniques

### Space Optimization

```
1D DP on 1D sequence:
  dp[i] depends on dp[i-1]:         → keep prev only
  dp[i] depends on dp[i-1], dp[i-2]: → keep prev, prev2

2D DP:
  dp[i][j] depends on dp[i-1][j] and dp[i][j-1]:
  → reduce to 1D array, traverse correctly

Knapsack:
  0/1:      traverse j backwards
  Unbounded: traverse j forwards
```

### Memoization vs Tabulation Tradeoff

```
Memoization:
  + Easy to write (follow the recursion)
  + Only fills needed cells (sparse DP)
  - Function call overhead
  - Risk of stack overflow for deep recursion

Tabulation:
  + No stack risk
  + Cache-friendly access patterns
  + Easy to spot space optimizations
  - Must fill all cells in correct order
```

---

## Quick Reference: LeetCode Problem → Pattern

| Problem | LC # | Pattern |
|---------|------|---------|
| Climbing Stairs | 70 | 1D counting |
| House Robber | 198 | 1D linear opt |
| Coin Change | 322 | Knapsack (unbounded) |
| Partition Equal Subset Sum | 416 | 0/1 Knapsack |
| Longest Common Subsequence | 1143 | 2D two strings |
| Edit Distance | 72 | 2D two strings |
| Unique Paths | 62 | 2D grid |
| Burst Balloons | 312 | Interval DP |
| Shortest Path All Nodes | 847 | Bitmask DP + BFS |
| Best Time Buy Sell Stock w/ cooldown | 309 | State machine |
| Longest Increasing Subsequence | 300 | 1D subsequence |
| Word Break | 139 | 1D, interval-like |
| Interleaving String | 97 | 2D two strings |
| Wildcard Matching | 44 | 2D matching |
| Regular Expression Matching | 10 | 2D matching |
| Palindromic Substrings | 647 | Expand around center / DP |

---

## The Hardest DP Problems (FAANG)

These require combining multiple techniques:

1. **LC #10 — Regular Expression Matching:** `dp[i][j]` with `*` matching 0 or more
2. **LC #312 — Burst Balloons:** Last-burst interval DP (counter-intuitive)
3. **LC #188 — Best Time Buy Sell Stock k transactions:** State machine + k dimension
4. **LC #1335 — Minimum Difficulty Job Scheduler:** 2D DP with day/job constraints
5. **LC #1547 — Minimum Cost to Cut a Stick:** Interval DP with subset of cut points

## Try It Yourself

**1.** Word Break (LC #139): Given dictionary, determine if string can be segmented.

<details>
<summary>Solution</summary>

```java
boolean wordBreak(String s, List<String> wordDict) {
    Set<String> dict = new HashSet<>(wordDict);
    int n = s.length();
    boolean[] dp = new boolean[n + 1];
    dp[0] = true; // empty string

    for (int i = 1; i <= n; i++) {
        for (int j = 0; j < i; j++) {
            if (dp[j] && dict.contains(s.substring(j, i))) {
                dp[i] = true;
                break;
            }
        }
    }
    return dp[n];
}
```

</details>

## Capstone Connection

AlgoForge's `DPProblems.java` organizes all solutions by pattern. The pattern recognition skill trained in this module is what separates candidates who "know" DP from candidates who can *apply* DP to new problems under pressure.
