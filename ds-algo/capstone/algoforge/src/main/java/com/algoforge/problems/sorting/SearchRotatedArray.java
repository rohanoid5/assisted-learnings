package com.algoforge.problems.sorting;

/**
 * LC #33 — Search in Rotated Sorted Array
 *
 * <p>An integer array was sorted in ascending order, then rotated at some pivot index.
 * Given the array and a target, return the index if target is found, else -1.
 * Must run in O(log n).</p>
 *
 * <b>Pattern:</b> Modified Binary Search — at each step determine which half is sorted.
 *
 * <pre>
 * Key insight: even in a rotated array, one of the two halves around mid is always sorted.
 * Check if target falls within the sorted half; search there. Otherwise search the other half.
 *
 * Trace: nums=[4,5,6,7,0,1,2], target=0
 *   lo=0, hi=6, mid=3, nums[3]=7
 *   Left half [4,5,6,7] is sorted. target=0 not in [4,7] → search right.
 *   lo=4, hi=6, mid=5, nums[5]=1
 *   Right half [1,2] is sorted. target=0 not in [1,2] → search left.
 *   lo=4, hi=4, mid=4, nums[4]=0 == target → return 4
 * </pre>
 *
 * Time: O(log n)  Space: O(1)
 */
public class SearchRotatedArray {

    public static int search(int[] nums, int target) {
        int lo = 0, hi = nums.length - 1;
        while (lo <= hi) {
            int mid = lo + (hi - lo) / 2;
            if (nums[mid] == target) return mid;

            if (nums[lo] <= nums[mid]) {     // left half is sorted
                if (nums[lo] <= target && target < nums[mid])
                    hi = mid - 1;            // target in sorted left half
                else
                    lo = mid + 1;
            } else {                         // right half is sorted
                if (nums[mid] < target && target <= nums[hi])
                    lo = mid + 1;            // target in sorted right half
                else
                    hi = mid - 1;
            }
        }
        return -1;
    }
}
