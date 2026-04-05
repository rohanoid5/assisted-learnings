package com.algoforge.problems.sorting;

/**
 * LC #162 — Find Peak Element
 *
 * <p>A peak element is an element strictly greater than its neighbors.
 * Given an integer array nums, find a peak element and return its index.
 * Assume nums[-1] = nums[n] = -∞. Must run in O(log n).</p>
 *
 * <b>Pattern:</b> Binary Search — always move toward the uphill direction.
 *
 * <pre>
 * Key insight: if nums[mid] < nums[mid+1], then a peak exists to the right
 * (either mid+1 itself, or something further right). The reverse is also true.
 *
 * Trace: nums=[1,2,3,1]
 *   lo=0, hi=3, mid=1, nums[1]=2 < nums[2]=3 → move right: lo=2
 *   lo=2, hi=3, mid=2, nums[2]=3 > nums[3]=1 → peak could be mid or left: hi=2
 *   lo=2=hi → return 2 (nums[2]=3 is a peak)
 * </pre>
 *
 * Time: O(log n)  Space: O(1)
 */
public class FindPeakElement {

    public static int findPeakElement(int[] nums) {
        int lo = 0, hi = nums.length - 1;
        while (lo < hi) {
            int mid = lo + (hi - lo) / 2;
            if (nums[mid] < nums[mid + 1])
                lo = mid + 1; // rising slope to the right → peak is to the right
            else
                hi = mid;     // falling slope → peak is at mid or to the left
        }
        return lo;
    }
}
