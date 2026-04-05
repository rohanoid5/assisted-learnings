package com.algoforge.problems.patterns;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * LC #452 — Non-overlapping Intervals
 *
 * <p>Given an array of intervals, return the minimum number of intervals you need
 * to remove to make the rest non-overlapping.</p>
 *
 * <b>Pattern:</b> Greedy — sort by end time; greedily keep intervals with earliest end.
 *
 * <pre>
 * Key insight: always keep the interval that ends earliest — this leaves maximum room for subsequent intervals.
 * Count the number of overlapping intervals to remove.
 *
 * Greedy algorithm:
 *   Sort by end time.
 *   Track prevEnd = end of last kept interval.
 *   If current start >= prevEnd → no overlap → update prevEnd
 *   Else → overlap → remove current (count++)
 *
 * Trace: [[1,2],[2,3],[3,4],[1,3]]
 *   Sorted by end: [[1,2],[2,3],[1,3],[3,4]]
 *   prevEnd=2, keep [1,2]
 *   [2,3]: start=2 >= prevEnd=2 → keep, prevEnd=3
 *   [1,3]: start=1 < prevEnd=3 → remove! count=1
 *   [3,4]: start=3 >= prevEnd=3 → keep
 *   Answer: 1
 * </pre>
 *
 * Time: O(n log n)  Space: O(1)
 */
public class NonOverlappingIntervals {

    public static int eraseOverlapIntervals(int[][] intervals) {
        if (intervals.length == 0) return 0;
        Arrays.sort(intervals, (a, b) -> a[1] - b[1]); // sort by end time
        int removed = 0, prevEnd = intervals[0][1];
        for (int i = 1; i < intervals.length; i++) {
            if (intervals[i][0] < prevEnd) {
                removed++; // overlap: remove current
            } else {
                prevEnd = intervals[i][1]; // no overlap: keep current
            }
        }
        return removed;
    }
}
