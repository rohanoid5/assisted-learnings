package com.algoforge.problems.patterns;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * LC #57 — Insert Interval
 *
 * <p>Given a sorted list of non-overlapping intervals and a new interval,
 * insert the new interval and merge any overlapping intervals.</p>
 *
 * <b>Pattern:</b> Linear scan — skip non-overlapping left, merge overlapping, append rest.
 *
 * <pre>
 * Three phases:
 *   1. Add all intervals that end before newInterval starts
 *   2. Merge all intervals that overlap with newInterval
 *   3. Add all remaining intervals
 *
 * Trace: intervals=[[1,3],[6,9]], newInterval=[2,5]
 *   Phase 1: [1,3] ends at 3≥2 → skip (overlaps) → result=[]
 *   Phase 2: overlap with [1,3] → merged=[min(1,2),max(3,5)]=[1,5]
 *            [6,9] starts at 6>5 → stop merging
 *   Phase 3: result=[[1,5],[6,9]]
 * </pre>
 *
 * Time: O(n)  Space: O(n)
 */
public class InsertInterval {

    public static int[][] insert(int[][] intervals, int[] newInterval) {
        List<int[]> result = new ArrayList<>();
        int i = 0, n = intervals.length;
        // Phase 1: add all intervals ending before newInterval starts
        while (i < n && intervals[i][1] < newInterval[0])
            result.add(intervals[i++]);
        // Phase 2: merge overlapping intervals
        while (i < n && intervals[i][0] <= newInterval[1]) {
            newInterval[0] = Math.min(newInterval[0], intervals[i][0]);
            newInterval[1] = Math.max(newInterval[1], intervals[i][1]);
            i++;
        }
        result.add(newInterval);
        // Phase 3: add remaining
        while (i < n) result.add(intervals[i++]);
        return result.toArray(new int[0][]);
    }
}
