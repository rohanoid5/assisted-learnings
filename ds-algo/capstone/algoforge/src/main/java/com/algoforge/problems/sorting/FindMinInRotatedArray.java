package com.algoforge.problems.sorting;

/**
 * LC #153 — Find Minimum in Rotated Sorted Array
 *
 * <p>Given a sorted array rotated between 1 and n times, find the minimum element.
 * Must run in O(log n). All values are unique.</p>
 *
 * <b>Pattern:</b> Binary Search — compare mid with hi to detect which side the minimum is on.
 *
 * <pre>
 * Key insight: the minimum is at the "inflection point" where the array wraps.
 *   If nums[mid] > nums[hi]: minimum must be in the right half (mid+1..hi).
 *   If nums[mid] < nums[hi]: minimum is in the left half including mid.
 *
 * Trace: [3,4,5,1,2]
 *   lo=0, hi=4, mid=2, nums[2]=5 > nums[4]=2 → min in right → lo=3
 *   lo=3, hi=4, mid=3, nums[3]=1 < nums[4]=2 → min in left (incl mid) → hi=3
 *   lo=3=hi → nums[3]=1 is the answer
 * </pre>
 *
 * Time: O(log n)  Space: O(1)
 */
public class FindMinInRotatedArray {

    public static int findMin(int[] nums) {
        int lo = 0, hi = nums.length - 1;
        while (lo < hi) {
            int mid = lo + (hi - lo) / 2;
            if (nums[mid] > nums[hi])
                lo = mid + 1; // min is in right half
            else
                hi = mid;     // min is in left half (including mid)
        }
        return nums[lo];
    }
}
