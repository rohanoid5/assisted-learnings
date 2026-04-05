package com.algoforge.problems.dp;

/**
 * LC #70 — Climbing Stairs
 *
 * <p>You are climbing a staircase with n steps. Each move you can take 1 or 2 steps.
 * How many distinct ways can you climb to the top?</p>
 *
 * <b>Pattern:</b> 1D DP (Fibonacci) — dp[i] = dp[i-1] + dp[i-2]
 *
 * <pre>
 * dp[0]=1 (empty path to step 0), dp[1]=1, dp[2]=2
 * dp[n] = ways to reach n = (ways via 1-step from n-1) + (ways via 2-steps from n-2)
 *
 * Space-optimized: only need previous two values → O(1) space
 * </pre>
 *
 * Time: O(n)  Space: O(1)
 */
public class ClimbingStairs {
    public static int climbStairs(int n) {
        if (n <= 2) return n;
        int prev2 = 1, prev1 = 2;
        for (int i = 3; i <= n; i++) {
            int curr = prev1 + prev2;
            prev2 = prev1;
            prev1 = curr;
        }
        return prev1;
    }
}
