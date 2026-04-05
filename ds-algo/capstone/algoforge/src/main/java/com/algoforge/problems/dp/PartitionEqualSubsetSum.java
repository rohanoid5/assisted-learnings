package com.algoforge.problems.dp;

/**
 * LC #416 — Partition Equal Subset Sum
 *
 * <p>Given a non-empty integer array nums, determine if it can be partitioned into
 * two subsets with equal sum.</p>
 *
 * <b>Pattern:</b> 0/1 Knapsack (boolean DP) — can we reach exactly sum/2?
 *
 * <pre>
 * dp[j] = true if we can form sum j using elements seen so far.
 *
 * For each number, update dp backwards (avoid using same element twice):
 *   dp[j] = dp[j] OR dp[j - num]
 *
 * Trace: nums=[1,5,11,5]
 *   total=22, target=11
 *   Start dp=[T,F,F,F,F,F,F,F,F,F,F,F] (T at index 0)
 *   num=1:  dp[1]=dp[0]=T  → dp=[T,T,F,F,F,F,F,F,F,F,F,F]
 *   num=5:  dp[6]=dp[1]=T, dp[5]=dp[0]=T → [...T at 5,6]
 *   num=11: dp[11]=dp[0]=T → found!
 *   return true
 * </pre>
 *
 * Time: O(n * sum)  Space: O(sum)
 */
public class PartitionEqualSubsetSum {
    public static boolean canPartition(int[] nums) {
        int total = 0;
        for (int n : nums) total += n;
        if (total % 2 != 0) return false;
        int target = total / 2;
        boolean[] dp = new boolean[target + 1];
        dp[0] = true;
        for (int num : nums)
            for (int j = target; j >= num; j--)
                dp[j] = dp[j] || dp[j - num];
        return dp[target];
    }
}
