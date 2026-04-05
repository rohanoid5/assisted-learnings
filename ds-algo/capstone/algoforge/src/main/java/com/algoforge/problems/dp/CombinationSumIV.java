package com.algoforge.problems.dp;

/**
 * LC #377 — Combination Sum IV (Count Ordered Combinations — Permutation Count)
 *
 * <p>Given an array of distinct positive integers and a target, return the number of
 * possible combinations that add up to target. Different orderings count as different.</p>
 *
 * <b>Pattern:</b> DP counting permutations — coin change but with coins in inner loop.
 *
 * <pre>
 * Contrast with Coin Change II (combinations):
 *   Coins outer loop → each coin considered in fixed order → no duplicate orderings
 *   Target outer loop → coins can be in any order → counts permutations (this problem)
 *
 * dp[0]=1, dp[j] = sum of dp[j-coin] for all valid coins
 *
 * Trace: nums=[1,2,3], target=4
 *   dp=[1,0,0,0,0]
 *   j=1: dp[1]=dp[0]=1
 *   j=2: dp[2]=dp[1]+dp[0]=2
 *   j=3: dp[3]=dp[2]+dp[1]+dp[0]=4
 *   j=4: dp[4]=dp[3]+dp[2]+dp[1]=4+2+1=7
 *   Answer: 7
 * </pre>
 *
 * Time: O(n * target)  Space: O(target)
 */
public class CombinationSumIV {
    public static int combinationSum4(int[] nums, int target) {
        int[] dp = new int[target + 1];
        dp[0] = 1;
        for (int j = 1; j <= target; j++)
            for (int num : nums)
                if (num <= j) dp[j] += dp[j - num];
        return dp[target];
    }
}
