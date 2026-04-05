package com.algoforge.problems.dp;

/**
 * LC #11 — Container With Most Water (DP / Two Pointer)
 *
 * Already solved in arrays package. This file shows the 2D DP approach for
 * LC #42 — Trapping Rain Water.
 *
 * <b>LC #42 — Trapping Rain Water</b>
 *
 * <p>Given non-negative integers representing heights, compute how much water
 * can be trapped after raining.</p>
 *
 * <b>Pattern:</b> Prefix/Suffix max arrays (DP precomputation).
 *
 * <pre>
 * Water at index i = min(maxLeft[i], maxRight[i]) - height[i]
 *
 * Precompute:
 *   maxLeft[i]  = max height in heights[0..i]
 *   maxRight[i] = max height in heights[i..n-1]
 *
 * Trace: heights=[0,1,0,2,1,0,1,3,2,1,2,1]
 *   maxLeft = [0,1,1,2,2,2,2,3,3,3,3,3]
 *   maxRight= [3,3,3,3,3,3,3,3,2,2,2,1]
 *   water[i]= min(L,R)-h → [0,0,1,0,1,2,1,0,0,1,0,0] → total=6
 * </pre>
 *
 * Also includes O(1) space two-pointer approach.
 *
 * Time: O(n)  Space: O(n) for DP / O(1) for two-pointer
 */
public class TrappingRainWater {

    // DP approach — O(n) space
    public static int trap(int[] height) {
        int n = height.length;
        if (n == 0) return 0;
        int[] maxLeft = new int[n], maxRight = new int[n];
        maxLeft[0] = height[0];
        for (int i = 1; i < n; i++) maxLeft[i] = Math.max(maxLeft[i-1], height[i]);
        maxRight[n-1] = height[n-1];
        for (int i = n-2; i >= 0; i--) maxRight[i] = Math.max(maxRight[i+1], height[i]);
        int water = 0;
        for (int i = 0; i < n; i++) water += Math.min(maxLeft[i], maxRight[i]) - height[i];
        return water;
    }

    // Two-pointer approach — O(1) space
    public static int trapTwoPointer(int[] height) {
        int lo = 0, hi = height.length - 1, maxL = 0, maxR = 0, water = 0;
        while (lo < hi) {
            if (height[lo] < height[hi]) {
                maxL = Math.max(maxL, height[lo]);
                water += maxL - height[lo++];
            } else {
                maxR = Math.max(maxR, height[hi]);
                water += maxR - height[hi--];
            }
        }
        return water;
    }
}
