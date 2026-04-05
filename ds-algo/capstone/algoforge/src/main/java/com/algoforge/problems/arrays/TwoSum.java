package com.algoforge.problems.arrays;

import java.util.HashMap;
import java.util.Map;

/**
 * LC #1 — Two Sum
 *
 * <p>Given an array of integers and a target, return indices of the two numbers
 * that add up to target. Exactly one solution guaranteed. Cannot use same element twice.</p>
 *
 * <b>Pattern:</b> Hash Map complement lookup
 *
 * <pre>
 * Brute force:  O(n²) — check every pair
 * Hash map:     O(n)  — for each x, check if (target - x) is in the map
 *
 * Trace: nums=[2,7,11,15], target=9
 *   i=0: complement=7, map={2:0}
 *   i=1: complement=2, found at index 0 → return [0,1]
 * </pre>
 *
 * Time: O(n)  Space: O(n)
 */
public class TwoSum {

    public static int[] twoSum(int[] nums, int target) {
        Map<Integer, Integer> seen = new HashMap<>(); // value → index
        for (int i = 0; i < nums.length; i++) {
            int complement = target - nums[i];
            if (seen.containsKey(complement)) {
                return new int[]{seen.get(complement), i};
            }
            seen.put(nums[i], i);
        }
        throw new IllegalArgumentException("No solution exists");
    }
}
