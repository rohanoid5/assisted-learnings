package com.algoforge.problems.backtracking;

import java.util.ArrayList;
import java.util.List;

/**
 * LC #46 — Permutations
 *
 * <p>Given an array nums of distinct integers, return all possible permutations.</p>
 *
 * <b>Pattern:</b> Backtracking — swap elements to build permutation in-place.
 *
 * <pre>
 * Decision tree for [1,2,3]:
 *   swap(0,0)=1: [1|2,3] → swap(1,1)=2: [1,2|3] → [1,2,3] ✓
 *                           swap(1,2)=3: [1,3|2] → [1,3,2] ✓
 *   swap(0,1)=2: [2|1,3] → swap(1,1)=1: [2,1|3] → [2,1,3] ✓
 *                           swap(1,2)=3: [2,3|1] → [2,3,1] ✓
 *   swap(0,2)=3: [3|2,1] → [3,2,1] ✓   [3,1,2] ✓
 *
 * Result: [[1,2,3],[1,3,2],[2,1,3],[2,3,1],[3,2,1],[3,1,2]]
 * </pre>
 *
 * Time: O(n! * n)   Space: O(n)
 */
public class Permutations {

    public static List<List<Integer>> permute(int[] nums) {
        List<List<Integer>> result = new ArrayList<>();
        backtrack(nums, 0, result);
        return result;
    }

    private static void backtrack(int[] nums, int start, List<List<Integer>> result) {
        if (start == nums.length) {
            List<Integer> perm = new ArrayList<>();
            for (int n : nums) perm.add(n);
            result.add(perm);
            return;
        }
        for (int i = start; i < nums.length; i++) {
            swap(nums, start, i);           // choose position start
            backtrack(nums, start + 1, result);
            swap(nums, start, i);           // undo choice
        }
    }

    private static void swap(int[] nums, int i, int j) {
        int tmp = nums[i]; nums[i] = nums[j]; nums[j] = tmp;
    }
}
