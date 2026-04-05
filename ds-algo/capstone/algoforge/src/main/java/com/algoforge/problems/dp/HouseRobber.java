package com.algoforge.problems.dp;

/**
 * LC #198 — House Robber
 *
 * <p>Given an array of non-negative integers representing the amount of money in each
 * house, return the maximum amount you can rob without robbing two adjacent houses.</p>
 *
 * <b>Pattern:</b> 1D DP — at each house, choose to rob it or skip it.
 *
 * <pre>
 * dp[i] = max money robbing houses 0..i
 * dp[i] = max(dp[i-1],           ← skip house i
 *             dp[i-2] + nums[i]) ← rob house i (can't rob i-1)
 *
 * Space optimization: only need last two dp values.
 *
 * Trace: [2,7,9,3,1]
 *   rob1=0, rob2=0
 *   i=0: rob=max(0,0+2)=2.  rob1=0, rob2=2
 *   i=1: rob=max(2,0+7)=7.  rob1=2, rob2=7
 *   i=2: rob=max(7,2+9)=11. rob1=7, rob2=11
 *   i=3: rob=max(11,7+3)=11. rob1=11, rob2=11
 *   i=4: rob=max(11,11+1)=12. Answer: 12
 * </pre>
 *
 * Time: O(n)  Space: O(1)
 */
public class HouseRobber {
    public static int rob(int[] nums) {
        int rob1 = 0, rob2 = 0;
        for (int n : nums) {
            int newRob = Math.max(rob2, rob1 + n);
            rob1 = rob2;
            rob2 = newRob;
        }
        return rob2;
    }
}
