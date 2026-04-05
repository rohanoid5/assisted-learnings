package com.algoforge.problems.backtracking;

import java.util.ArrayList;
import java.util.List;

/**
 * LC #78 — Subsets
 *
 * <p>Given an integer array nums of unique elements, return all possible subsets
 * (the power set). The solution set must not contain duplicate subsets.</p>
 *
 * <b>Pattern:</b> Backtracking — at each index, choose to include or exclude.
 *
 * <pre>
 * Decision tree for [1,2,3]:
 *   start=0: include 1 or skip
 *     include 1, start=1: include 2 or skip
 *       include 2, start=2: include 3 → [1,2,3]; done → [1,2]
 *       skip   2, start=2: include 3 → [1,3];   done → [1]
 *     skip   1, start=1: ... → [2,3],[2],[3],[]
 *
 * Result: [[],[1],[1,2],[1,2,3],[1,3],[2],[2,3],[3]]
 * </pre>
 *
 * Time: O(2^n * n)   Space: O(n) recursive stack
 */
public class Subsets {

    public static List<List<Integer>> subsets(int[] nums) {
        List<List<Integer>> result = new ArrayList<>();
        backtrack(nums, 0, new ArrayList<>(), result);
        return result;
    }

    private static void backtrack(int[] nums, int start, List<Integer> current, List<List<Integer>> result) {
        result.add(new ArrayList<>(current)); // snapshot before including more
        for (int i = start; i < nums.length; i++) {
            current.add(nums[i]);              // choose
            backtrack(nums, i + 1, current, result);
            current.remove(current.size() - 1); // unchoose
        }
    }
}
