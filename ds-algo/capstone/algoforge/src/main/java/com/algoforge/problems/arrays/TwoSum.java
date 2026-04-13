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

    /**
     * Brute force: check all pairs.
     *
     * Time: O(n²)
     * Space: O(1)
     */
    public int[] twoSumBrute(int[] nums, int target) {
        for (int i = 0; i < nums.length; i++)
            for (int j = i + 1; j < nums.length; j++)
                if (nums[i] + nums[j] == target) return new int[]{i, j};
        return new int[]{};
    }

    /**
     * HashMap: single pass.
     *
     * Time: O(n)
     * Space: O(n)
     */
    public int[] twoSumHash(int[] nums, int target) {
        Map<Integer, Integer> map = new HashMap<>();
        for (int i = 0; i < nums.length; i++) {
            int complement = target - nums[i];
            if (map.containsKey(complement)) return new int[]{map.get(complement), i};
            map.put(nums[i], i);
        }
        return new int[]{};
    }
}
