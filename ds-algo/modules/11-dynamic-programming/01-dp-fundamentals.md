# 11.1 — DP Fundamentals

## What Is Dynamic Programming?

Dynamic programming solves problems by:
1. **Breaking them into overlapping subproblems** (like recursion)
2. **Storing results** to avoid recomputation (unlike naive recursion)

DP applies when two properties hold:
- **Optimal substructure**: The optimal solution to a problem contains optimal solutions to its subproblems
- **Overlapping subproblems**: The same subproblems recur many times

## Memoization vs Tabulation

```
  MEMOIZATION                      TABULATION
  (Top-Down)                       (Bottom-Up)

  fib(5)                           dp[0] = 0
    fib(4)                         dp[1] = 1
      fib(3)                       dp[2] = dp[0]+dp[1] = 1
        fib(2) ── cached           dp[3] = dp[1]+dp[2] = 2
        fib(1) ── cached           dp[4] = dp[2]+dp[3] = 3
      fib(2) ── cache hit!         dp[5] = dp[3]+dp[4] = 5
    fib(3) ── cache hit!
```

| Property | Memoization | Tabulation |
|----------|------------|-----------|
| Style | Recursive + cache | Iterative table |
| Call overhead | Yes (stack frames) | No |
| Space | Call stack + cache | Just DP table |
| Fills only needed states | Yes | No (fills all) |
| Easier to write | Usually | Requires ordering |
| Risk | Stack overflow (deep recursion) | None |

## The DP Framework

```
Step 1: Define the state
  dp[i]     = "answer for subproblem of size i"
  dp[i][j]  = "answer for subproblem i..j or first i items with capacity j"

Step 2: Base case
  dp[0] = 0  (empty problem has known answer)

Step 3: Recurrence (transition)
  dp[i] = f(dp[i-1], dp[i-2], ...)

Step 4: Answer
  Often dp[n] or max/min over dp
```

## Example: Fibonacci Number (LC #509)

### Naive Recursion — O(2^n)
```java
int fib(int n) {
    if (n <= 1) return n;
    return fib(n-1) + fib(n-2);
}
```

The problem: `fib(5)` recomputes `fib(3)` twice, `fib(2)` three times...

### Memoization — O(n)
```java
int fib(int n, int[] memo) {
    if (n <= 1) return n;
    if (memo[n] != 0) return memo[n];
    return memo[n] = fib(n-1, memo) + fib(n-2, memo);
}
```

### Tabulation — O(n)
```java
int fib(int n) {
    if (n <= 1) return n;
    int[] dp = new int[n+1];
    dp[0] = 0; dp[1] = 1;
    for (int i = 2; i <= n; i++) dp[i] = dp[i-1] + dp[i-2];
    return dp[n];
}
```

### Space-Optimized Tabulation — O(1) space
```java
int fib(int n) {
    if (n <= 1) return n;
    int prev2 = 0, prev1 = 1;
    for (int i = 2; i <= n; i++) {
        int curr = prev1 + prev2;
        prev2 = prev1;
        prev1 = curr;
    }
    return prev1;
}
```

> **Key insight:** When `dp[i]` only depends on `dp[i-1]` and `dp[i-2]`, we don't need to store the whole table.

## Identifying DP Problems

**Signals that suggest DP:**
- "Find the minimum/maximum..."
- "Count the number of ways..."
- "Is it possible to...?"
- The answer for input n uses the answer for smaller inputs
- Problem asks about subsequences, subsets, or intervals

**Common misidentifications:**
- Greedy can sometimes solve what looks like DP — try greedy first for optimization problems
- If subproblems don't overlap, it's just recursion (divide and conquer)

## Top-Down Template

```java
Map<Integer, Integer> memo = new HashMap<>();

int solve(int state) {
    // Base case
    if (state == 0) return BASE_VALUE;

    // Cache check
    if (memo.containsKey(state)) return memo.get(state);

    // Recurrence
    int result = /* combinations of solve(smaller states) */;

    // Store and return
    memo.put(state, result);
    return result;
}
```

## Bottom-Up Template

```java
int bottomUp(int n) {
    int[] dp = new int[n+1];
    dp[0] = BASE_VALUE;

    for (int i = 1; i <= n; i++) {
        dp[i] = /* function of dp[i-1], dp[i-2], etc. */;
    }

    return dp[n];
}
```

## Try It Yourself

**1.** Rewrite Fibonacci using a 2D array where `dp[i][j]` = fib(i) for all j. Is this useful? What does this tell you about state design?

**2.** Given `int[] cost` where `cost[i]` is the cost of step i on a staircase, find min cost to reach top. You can take 1 or 2 steps. This is LC #746 — Min Cost Climbing Stairs.

<details>
<summary>Solution to #2</summary>

```java
int minCostClimbingStairs(int[] cost) {
    int n = cost.length;
    // dp[i] = min cost to reach step i
    int prev2 = cost[0], prev1 = cost[1];
    for (int i = 2; i < n; i++) {
        int curr = cost[i] + Math.min(prev1, prev2);
        prev2 = prev1;
        prev1 = curr;
    }
    return Math.min(prev1, prev2); // can stop at last or second-to-last
}
```

</details>

## Capstone Connection

The memoization pattern appears in AlgoForge's `DPProblems.java`. All DP problems in the collection follow the 4-step framework: state definition → base case → recurrence → answer extraction.
