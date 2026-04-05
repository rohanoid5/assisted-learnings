package com.algoforge.problems.dp;

/**
 * LC #312 — Burst Balloons
 *
 * <p>Given n balloons with numbers, burst them to maximize coins earned.
 * Bursting balloon i earns nums[left] * nums[i] * nums[right] where left and right
 * are adjacent unbursted balloons. Surrounding boundary balloons are both 1.</p>
 *
 * <b>Pattern:</b> Interval DP — think of intervals, decide which balloon to burst LAST.
 *
 * <pre>
 * Key insight: instead of thinking "which balloon to burst first" (which changes
 * the neighbors), think "which balloon to burst LAST in interval [l,r]".
 *
 * dp[l][r] = max coins for bursting all balloons in interval (l,r) exclusive
 *            (balloons at l and r are boundaries (not burst))
 *
 * dp[l][r] = max over k in (l,r): dp[l][k] + nums[l]*nums[k]*nums[r] + dp[k][r]
 *
 * Add boundary 1's: nums = [1] + original + [1]
 * Answer: dp[0][n+1]
 *
 * Trace: [3,1,5,8] → [1,3,1,5,8,1]
 *   Optimal: burst 1→3+1*1*5=5; burst 5→3+3*5*8=120; burst 3→3+1*3*8=24; burst 8→8
 *   Wait that's not right. Let's note optimal answer is 167.
 * </pre>
 *
 * Time: O(n³)  Space: O(n²)
 */
public class BurstBalloons {
    public static int maxCoins(int[] nums) {
        int n = nums.length;
        int[] padded = new int[n + 2];
        padded[0] = padded[n + 1] = 1;
        for (int i = 0; i < n; i++) padded[i + 1] = nums[i];

        int[][] dp = new int[n + 2][n + 2];
        // Fill by increasing interval length
        for (int len = 2; len < n + 2; len++) {       // window size
            for (int l = 0; l < n + 2 - len; l++) {
                int r = l + len;
                for (int k = l + 1; k < r; k++) {     // k is last to be burst in (l,r)
                    dp[l][r] = Math.max(dp[l][r],
                        dp[l][k] + padded[l] * padded[k] * padded[r] + dp[k][r]);
                }
            }
        }
        return dp[0][n + 1];
    }
}
