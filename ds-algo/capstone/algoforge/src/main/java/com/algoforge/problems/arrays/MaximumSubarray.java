package com.algoforge.problems.arrays;

/**
 * LC #53 — Maximum Subarray (Kadane's Algorithm)
 *
 * <p>Find the contiguous subarray with the largest sum.</p>
 *
 * <b>Pattern:</b> Greedy / DP (1D)
 *
 * <pre>
 * nums = [-2,1,-3,4,-1,2,1,-5,4]
 *
 * Key insight: at each position, decide whether to extend the current subarray
 * or start a new one. Start a new subarray whenever the current sum goes negative.
 *
 *  i=0: curSum = max(-2,    -2) = -2,  maxSum=-2
 *  i=1: curSum = max(1,  -2+1) = 1,   maxSum=1
 *  i=2: curSum = max(-3,  1-3) = -2,  maxSum=1
 *  i=3: curSum = max(4,  -2+4) = 4,   maxSum=4
 *  i=4: curSum = max(-1,  4-1) = 3,   maxSum=4
 *  i=5: curSum = max(2,   3+2) = 5,   maxSum=5
 *  i=6: curSum = max(1,   5+1) = 6,   maxSum=6
 *  i=7: curSum = max(-5,  6-5) = 1,   maxSum=6
 *  i=8: curSum = max(4,   1+4) = 5,   maxSum=6
 *
 *  Answer: 6 (subarray [4,-1,2,1])
 * </pre>
 *
 * Time: O(n)  Space: O(1)
 */
public class MaximumSubarray {

    /** Returns the maximum subarray sum. */
    public static int maxSubArray(int[] nums) {
        int curSum = nums[0];
        int maxSum = nums[0];

        for (int i = 1; i < nums.length; i++) {
            // Either extend or start fresh at nums[i]
            curSum = Math.max(nums[i], curSum + nums[i]);
            maxSum = Math.max(maxSum, curSum);
        }
        return maxSum;
    }

    /** Returns the actual subarray [start, end] indices (inclusive) for the maximum sum. */
    public static int[] maxSubArrayIndices(int[] nums) {
        int curSum = nums[0], maxSum = nums[0];
        int start = 0, end = 0, tempStart = 0;

        for (int i = 1; i < nums.length; i++) {
            if (nums[i] > curSum + nums[i]) {
                curSum    = nums[i];
                tempStart = i;
            } else {
                curSum += nums[i];
            }
            if (curSum > maxSum) {
                maxSum = curSum;
                start  = tempStart;
                end    = i;
            }
        }
        return new int[]{start, end};
    }
}
