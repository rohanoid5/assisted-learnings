package com.algoforge.problems.dp;

import java.util.Arrays;

/**
 * LC #322 — Coin Change
 *
 * <p>Given coins of different denominations and a total amount, return the fewest number
 * of coins needed to make up the amount. Return -1 if impossible.</p>
 *
 * <b>Pattern:</b> Unbounded Knapsack / 1D DP bottom-up.
 *
 * <pre>
 * dp[i] = minimum coins needed to make amount i
 * dp[0] = 0 (base case: 0 coins for amount 0)
 * dp[i] = 1 + min(dp[i - coin]) for each coin ≤ i
 *
 * Trace: coins=[1,5,11], amount=15
 *   dp = [0, ∞, ∞, ∞, ∞, ∞, ∞, ∞, ∞, ∞, ∞, ∞, ∞, ∞, ∞, ∞]
 *   dp[1]=1 (coin 1), dp[5]=1 (coin 5), dp[10]=2 (5+5), dp[11]=1 (coin 11)
 *   dp[15]=3 (5+5+5) or dp[15]=dp[4]+dp[11]=... let's see dp[4]=4, so 5 total? No.
 *   Actually dp[15]=min(dp[14]+1, dp[10]+1, dp[4]+1)=min(dp[14]+1, 3, 5)
 *   Result: 3 (5+5+5)
 * </pre>
 *
 * Time: O(n * amount)  Space: O(amount)
 */
public class CoinChange {
    public static int coinChange(int[] coins, int amount) {
        int[] dp = new int[amount + 1];
        Arrays.fill(dp, amount + 1); // sentinel "infinity"
        dp[0] = 0;
        for (int i = 1; i <= amount; i++)
            for (int coin : coins)
                if (coin <= i) dp[i] = Math.min(dp[i], dp[i - coin] + 1);
        return dp[amount] > amount ? -1 : dp[amount];
    }
}
