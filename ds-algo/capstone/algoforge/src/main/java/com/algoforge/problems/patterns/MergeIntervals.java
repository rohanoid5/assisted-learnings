package com.algoforge.problems.patterns;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * LC #56 — Merge Intervals
 *
 * <p>Given an array of intervals, merge all overlapping intervals
 * and return an array of non-overlapping intervals.</p>
 *
 * <b>Pattern:</b> Sort + merge — sort by start, merge when current start ≤ prev end.
 *
 * <pre>
 * Trace: [[1,3],[2,6],[8,10],[15,18]]
 *   Sorted: same (already sorted by start)
 *   [1,3]: start merged=[1,3]
 *   [2,6]: 2≤3 → merge → [1,6]
 *   [8,10]: 8>6 → new interval → result=[[1,6]], start=[8,10]
 *   [15,18]: 15>10 → result=[[1,6],[8,10]], add [15,18]
 *   Final: [[1,6],[8,10],[15,18]]
 * </pre>
 *
 * Time: O(n log n)  Space: O(n)
 */
public class MergeIntervals {

    public static int[][] merge(int[][] intervals) {
        Arrays.sort(intervals, (a, b) -> a[0] - b[0]);
        List<int[]> merged = new ArrayList<>();
        int[] current = intervals[0];
        for (int i = 1; i < intervals.length; i++) {
            if (intervals[i][0] <= current[1]) {          // overlap
                current[1] = Math.max(current[1], intervals[i][1]);
            } else {
                merged.add(current);
                current = intervals[i];
            }
        }
        merged.add(current);
        return merged.toArray(new int[0][]);
    }
}
