package com.algoforge.problems.dp;

/**
 * LC #152 — Maximum Product Subarray
 *
 * <p>Given an integer array nums, find a subarray that has the largest product
 * and return the product.</p>
 *
 * <b>Pattern:</b> DP — track both min and max at each position (negatives can flip).
 *
 * <pre>
 * Key insight: a negative number can turn a small (most negative) product into the largest.
 * At each step keep track of BOTH the max and min product ending at current index.
 *
 * curMax = max(nums[i], curMax * nums[i], curMin * nums[i])
 * curMin = min(nums[i], curMax * nums[i], curMin * nums[i])
 *
 * Trace: nums=[2,3,-2,4]
 *   i=0: curMax=2, curMin=2, ans=2
 *   i=1: curMax=max(3,6,6)=6, curMin=min(3,6,6)=3, ans=6
 *   i=2: curMax=max(-2,-12,-6)=-2, curMin=min(-2,-12,-6)=-12, ans=6
 *   i=3: curMax=max(4,-8,-48)=4, curMin=min(4,-8,-48)=-48, ans=6
 *   Answer: 6
 * </pre>
 *
 * Time: O(n)  Space: O(1)
 */
public class MaximumProductSubarray {
    public static int maxProduct(int[] nums) {
        int curMax = nums[0], curMin = nums[0], ans = nums[0];
        for (int i = 1; i < nums.length; i++) {
            int n = nums[i];
            int tempMax = Math.max(n, Math.max(curMax * n, curMin * n));
            curMin = Math.min(n, Math.min(curMax * n, curMin * n));
            curMax = tempMax;
            ans = Math.max(ans, curMax);
        }
        return ans;
    }
}
