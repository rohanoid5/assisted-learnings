package com.algoforge.problems.arrays;

/**
 * LC #11 — Container With Most Water
 *
 * <p>Given n non-negative integers representing vertical lines at x-positions 0..n-1,
 * find two lines that together with the x-axis form a container that holds the most water.</p>
 *
 * <b>Pattern:</b> Two Pointer (converging from both ends)
 *
 * <pre>
 * height = [1,8,6,2,5,4,8,3,7]
 *
 *   lo=0 (h=1), hi=8 (h=7) → area = min(1,7)*(8-0) = 8
 *   Move the shorter pointer inward (lo++ since h[0]<h[8]):
 *   lo=1 (h=8), hi=8 (h=7) → area = min(8,7)*(8-1) = 49
 *   Move hi-- → lo=1, hi=7 (h=3) → area = min(8,3)*6 = 18
 *   ...
 *   Maximum = 49
 *
 * Why move the shorter side?
 * Moving the taller side can only decrease or maintain the height cap.
 * Moving the shorter side gives a chance to find a taller line.
 * </pre>
 *
 * Time: O(n)  Space: O(1)
 */
public class ContainerWithMostWater {

    public static int maxArea(int[] height) {
        int lo = 0, hi = height.length - 1;
        int maxWater = 0;

        while (lo < hi) {
            int water = Math.min(height[lo], height[hi]) * (hi - lo);
            maxWater = Math.max(maxWater, water);

            // Move the shorter wall inward — moving the taller wall can only hurt
            if (height[lo] < height[hi]) lo++;
            else                          hi--;
        }
        return maxWater;
    }
}
