package com.algoforge.problems.dp;

import java.util.Arrays;

/**
 * LC #518 — Coin Change II (Count Combinations)
 *
 * <p>Given an amount and a list of coin denominations, return the number of combinations
 * that make up the amount. Unlimited coins of each denomination.</p>
 *
 * <b>Pattern:</b> Unbounded Knapsack — count combinations (not permutations).
 *
 * <pre>
 * Key difference from counting permutations: iterate coins in outer loop so each
 * coin is "considered as a whole" → no duplicate orderings.
 *
 * dp[j] = number of ways to make amount j
 * dp[0] = 1 (one way to make 0: use no coins)
 *
 * For each coin: dp[j] += dp[j - coin]
 *
 * Trace: amount=5, coins=[1,2,5]
 *   Start: dp=[1,0,0,0,0,0]
 *   coin=1: dp=[1,1,1,1,1,1]
 *   coin=2: dp=[1,1,2,2,3,3]
 *   coin=5: dp=[1,1,2,2,3,4]
 *   Answer: dp[5]=4 : {5},{2,2,1},{2,1,1,1},{1,1,1,1,1}
 * </pre>
 *
 * Time: O(n * amount)  Space: O(amount)
 */
public class CoinChangeII {
    public static int change(int amount, int[] coins) {
        int[] dp = new int[amount + 1];
        dp[0] = 1;
        for (int coin : coins)
            for (int j = coin; j <= amount; j++)
                dp[j] += dp[j - coin];
        return dp[amount];
    }
}
