package com.algoforge.problems.arrays;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * LC #15 — Three Sum
 *
 * <p>Find all unique triplets that sum to zero.</p>
 *
 * <b>Pattern:</b> Sort + Two Pointer (extends Two Sum)
 *
 * <pre>
 * nums = [-4,-1,-1,0,1,2]  (sorted)
 *
 * Fix nums[i] as the first element, then use two pointers lo/hi on the rest:
 *
 * i=0 (-4): lo=1 hi=5 → -4+-1+2=-3<0 lo++ → -4+0+2=-2<0 lo++ → -4+1+2=-1<0 lo++
 *           → lo≥hi, no triplet starting with -4
 * i=1 (-1): lo=2 hi=5 → -1+-1+2=0 ✓ add [-1,-1,2], skip dups: lo++ hi--
 *           lo=3 hi=4 → -1+0+1=0 ✓ add [-1,0,1]
 * i=2 (-1): skip (duplicate of i=1)
 * i=3 (0):  lo=4 hi=5 → 0+1+2=3>0 hi--
 *           → lo≥hi, done
 *
 * Result: [[-1,-1,2],[-1,0,1]]
 * </pre>
 *
 * Time: O(n²)  Space: O(n) for output (sorting is O(n log n))
 */
public class ThreeSum {

    public static List<List<Integer>> threeSum(int[] nums) {
        List<List<Integer>> result = new ArrayList<>();
        Arrays.sort(nums);

        for (int i = 0; i < nums.length - 2; i++) {
            // Skip duplicate values for the first element
            if (i > 0 && nums[i] == nums[i - 1]) continue;
            // Optimisation: if smallest possible sum > 0, all subsequent sums are also > 0
            if (nums[i] > 0) break;

            int lo = i + 1, hi = nums.length - 1;
            while (lo < hi) {
                int sum = nums[i] + nums[lo] + nums[hi];
                if (sum == 0) {
                    result.add(Arrays.asList(nums[i], nums[lo], nums[hi]));
                    // Skip duplicates for lo and hi
                    while (lo < hi && nums[lo] == nums[lo + 1]) lo++;
                    while (lo < hi && nums[hi] == nums[hi - 1]) hi--;
                    lo++; hi--;
                } else if (sum < 0) {
                    lo++;
                } else {
                    hi--;
                }
            }
        }
        return result;
    }
}
