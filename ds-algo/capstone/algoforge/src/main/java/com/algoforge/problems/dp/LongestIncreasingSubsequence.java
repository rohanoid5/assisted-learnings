package com.algoforge.problems.dp;

/**
 * LC #300 — Longest Increasing Subsequence
 *
 * <p>Given an integer array nums, return the length of the longest strictly
 * increasing subsequence.</p>
 *
 * <b>Approach 1:</b> DP — O(n²)
 * <b>Approach 2:</b> Patience sorting with binary search — O(n log n) ← primary
 *
 * <pre>
 * Patience sort approach: maintain a "tails" array where tails[i] is the smallest
 * tail element for increasing subsequences of length i+1.
 * Binary search to find where to place each number.
 *
 * Trace: [10,9,2,5,3,7,101,18]
 *   tails=[]
 *   10 → [10]
 *    9 → replace 10 → [9]
 *    2 → replace 9  → [2]
 *    5 → append     → [2,5]
 *    3 → replace 5  → [2,3]
 *    7 → append     → [2,3,7]
 *  101 → append     → [2,3,7,101]
 *   18 → replace 101→ [2,3,7,18]
 *   Answer: len(tails)=4
 * </pre>
 *
 * Time: O(n log n)  Space: O(n)
 */
public class LongestIncreasingSubsequence {

    public static int lengthOfLIS(int[] nums) {
        int[] tails = new int[nums.length];
        int size = 0;
        for (int num : nums) {
            int lo = 0, hi = size;
            while (lo < hi) {
                int mid = lo + (hi - lo) / 2;
                if (tails[mid] < num) lo = mid + 1;
                else hi = mid;
            }
            tails[lo] = num;
            if (lo == size) size++;
        }
        return size;
    }

    // O(n²) DP version — easier to understand
    public static int lengthOfLISDp(int[] nums) {
        int n = nums.length;
        int[] dp = new int[n];
        java.util.Arrays.fill(dp, 1);
        int ans = 1;
        for (int i = 1; i < n; i++) {
            for (int j = 0; j < i; j++)
                if (nums[j] < nums[i]) dp[i] = Math.max(dp[i], dp[j] + 1);
            ans = Math.max(ans, dp[i]);
        }
        return ans;
    }
}
