package com.algoforge.problems.dp;

import java.util.Arrays;

/**
 * 0/1 Knapsack — Classic DP Template
 *
 * <p>Given weights[] and values[] arrays and a capacity W, find the maximum value
 * achievable by choosing items such that total weight ≤ W.
 * Each item can be chosen at most once.</p>
 *
 * <b>Pattern:</b> 0/1 Knapsack — the template underlying PartitionEqualSubsetSum, etc.
 *
 * <pre>
 * dp[j] = max value achievable with capacity j
 *
 * Key: iterate capacity from right to left to avoid using an item twice.
 *
 * Classic table form (rows=items, cols=capacity):
 *   dp[i][j] = max(dp[i-1][j], dp[i-1][j-w[i]] + v[i])   if j >= w[i]
 *            = dp[i-1][j]                                   otherwise
 *
 * Space-optimized to 1D:
 *   for each item: for j from W down to w[i]: dp[j] = max(dp[j], dp[j-w[i]] + v[i])
 *
 * Trace: weights=[1,3,4,5], values=[1,4,5,7], W=7
 *   Answer: 9 (items with weight 3+4=7, value 4+5=9)
 * </pre>
 *
 * Time: O(n * W)  Space: O(W)
 */
public class ZeroOneKnapsack {
    public static int knapsack(int[] weights, int[] values, int capacity) {
        int[] dp = new int[capacity + 1];
        for (int i = 0; i < weights.length; i++)
            for (int j = capacity; j >= weights[i]; j--)
                dp[j] = Math.max(dp[j], dp[j - weights[i]] + values[i]);
        return dp[capacity];
    }
}
