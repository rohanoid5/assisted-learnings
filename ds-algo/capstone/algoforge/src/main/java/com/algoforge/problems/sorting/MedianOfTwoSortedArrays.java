package com.algoforge.problems.sorting;

/**
 * LC #4 — Median of Two Sorted Arrays
 *
 * <p>Given two sorted arrays nums1 and nums2 of sizes m and n, return the median
 * of the two sorted arrays. Must run in O(log(min(m,n))).</p>
 *
 * <b>Pattern:</b> Binary search on partition — find the correct cut in the smaller array.
 *
 * <pre>
 * Key insight: if we partition both arrays into left and right halves such that:
 *   1. |leftTotal| = |rightTotal| (or |leftTotal| = |rightTotal| + 1 for odd total)
 *   2. max(leftA, leftB) ≤ min(rightA, rightB)
 * then the median is:
 *   - odd total:  max(leftA, leftB)
 *   - even total: (max(leftA, leftB) + min(rightA, rightB)) / 2.0
 *
 * Binary search partition index in the smaller array.
 *
 * Trace: nums1=[1,3], nums2=[2]  → merged=[1,2,3] → median=2
 *   Binary search partition of nums1 (size 2):
 *   lo=0, hi=2, halfLen=(2+1+1)/2=2
 *   partition1=1, partition2=2-1=1
 *   maxLeft1=1, minRight1=3, maxLeft2=-∞(none left), minRight2=2
 *   maxLeft1=1 ≤ minRight2=2? yes. maxLeft2=-∞ ≤ minRight1=3? yes → correct partition
 *   total=3 (odd): median=max(1,-∞)=1? Wait... recalculate:
 *   Actually median = max(maxLeft1, maxLeft2) = max(1, -INF) = 1? No that's wrong.
 *   Let me retrace: nums1=[1,3], nums2=[2], total=3, halfLen=(3+1)/2=2
 *   partition1=1 (split nums1 into [1] and [3])
 *   partition2=2-1=1 (split nums2 into [2] and [])  ← wait nums2 has 1 element
 *   Actually partition2 = halfLen - partition1 = 2 - 1 = 1
 *   maxLeft1=nums1[0]=1, minRight1=nums1[1]=3
 *   maxLeft2=nums2[0]=2, minRight2=+INF (nothing on right of nums2)
 *   maxLeft1=1 ≤ minRight2=+INF ✓, maxLeft2=2 ≤ minRight1=3 ✓
 *   median = max(1,2)=2 ✓
 * </pre>
 *
 * Time: O(log(min(m,n)))  Space: O(1)
 */
public class MedianOfTwoSortedArrays {

    public static double findMedianSortedArrays(int[] nums1, int[] nums2) {
        // Always binary-search the smaller array
        if (nums1.length > nums2.length) return findMedianSortedArrays(nums2, nums1);

        int m = nums1.length, n = nums2.length;
        int lo = 0, hi = m;
        int halfLen = (m + n + 1) / 2;

        while (lo <= hi) {
            int p1 = lo + (hi - lo) / 2; // partition index in nums1
            int p2 = halfLen - p1;        // partition index in nums2

            int maxLeft1  = (p1 == 0) ? Integer.MIN_VALUE : nums1[p1 - 1];
            int minRight1 = (p1 == m) ? Integer.MAX_VALUE : nums1[p1];
            int maxLeft2  = (p2 == 0) ? Integer.MIN_VALUE : nums2[p2 - 1];
            int minRight2 = (p2 == n) ? Integer.MAX_VALUE : nums2[p2];

            if (maxLeft1 <= minRight2 && maxLeft2 <= minRight1) {
                // Correct partition
                if ((m + n) % 2 == 1)
                    return Math.max(maxLeft1, maxLeft2);
                else
                    return (Math.max(maxLeft1, maxLeft2) + Math.min(minRight1, minRight2)) / 2.0;
            } else if (maxLeft1 > minRight2) {
                hi = p1 - 1; // move partition1 left
            } else {
                lo = p1 + 1; // move partition1 right
            }
        }
        throw new IllegalArgumentException("Input arrays are not sorted");
    }
}
