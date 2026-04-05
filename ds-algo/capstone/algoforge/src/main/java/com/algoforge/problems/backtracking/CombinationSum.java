package com.algoforge.problems.backtracking;

import java.util.ArrayList;
import java.util.List;

/**
 * LC #39 — Combination Sum
 *
 * <p>Given an array of distinct positive integers (candidates) and a target,
 * return all unique combinations where the chosen numbers sum to target.
 * The same number may be chosen unlimited times.</p>
 *
 * <b>Pattern:</b> Backtracking with remain constraint — prune when remain < 0.
 *
 * <pre>
 * Trace: candidates=[2,3,6,7], target=7
 *   start at 2, remain=7:
 *     pick 2, remain=5:
 *       pick 2, remain=3:
 *         pick 2, remain=1:
 *           pick 2, remain=-1 → prune
 *           pick 3, remain=-2 → prune
 *         pick 3, remain=0 → [2,2,3] ✓
 *         pick 6 → prune ... pick 7 → prune
 *       pick 3, remain=2:
 *         pick 3, remain=-1 → prune
 *       ...
 *     pick 7, remain=0 → [7] ✓
 *   Result: [[2,2,3],[7]]
 * </pre>
 *
 * Time: O(n^(T/min))   Space: O(T/min) recursion depth
 */
public class CombinationSum {

    public static List<List<Integer>> combinationSum(int[] candidates, int target) {
        List<List<Integer>> result = new ArrayList<>();
        backtrack(candidates, 0, target, new ArrayList<>(), result);
        return result;
    }

    private static void backtrack(int[] candidates, int start, int remain,
                                   List<Integer> current, List<List<Integer>> result) {
        if (remain == 0) {
            result.add(new ArrayList<>(current));
            return;
        }
        for (int i = start; i < candidates.length; i++) {
            if (candidates[i] > remain) continue; // prune
            current.add(candidates[i]);
            backtrack(candidates, i, remain - candidates[i], current, result); // i not i+1: reuse allowed
            current.remove(current.size() - 1);
        }
    }
}
