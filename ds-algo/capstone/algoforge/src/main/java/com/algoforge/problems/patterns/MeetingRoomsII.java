package com.algoforge.problems.patterns;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.PriorityQueue;

/**
 * LC #253 — Meeting Rooms II
 *
 * <p>Given meeting time intervals [start, end], find the minimum number of conference
 * rooms required so all meetings can take place simultaneously as needed.</p>
 *
 * <b>Pattern:</b> MinHeap of end times — greedily reuse a room when a meeting ends.
 *
 * <pre>
 * Algorithm:
 *   Sort intervals by start time.
 *   For each meeting, if its start >= earliest-ending ongoing meeting → reuse room.
 *   Otherwise, open a new room.
 *   Heap size at the end = number of rooms required.
 *
 * Trace: [[0,30],[5,10],[15,20]]
 *   Sorted: same.
 *   [0,30]: heap=[] empty → new room. heap=[30]
 *   [5,10]: 5 < 30 → new room. heap=[10,30]
 *   [15,20]:15 ≥ 10 → reuse. poll 10, offer 20. heap=[20,30]
 *   Answer: heap.size()=2
 * </pre>
 *
 * Time: O(n log n)  Space: O(n)
 */
public class MeetingRoomsII {

    public static int minMeetingRooms(int[][] intervals) {
        Arrays.sort(intervals, (a, b) -> a[0] - b[0]);
        PriorityQueue<Integer> endTimes = new PriorityQueue<>(); // min-heap of room end times
        for (int[] interval : intervals) {
            if (!endTimes.isEmpty() && endTimes.peek() <= interval[0])
                endTimes.poll(); // reuse room (meeting ended)
            endTimes.offer(interval[1]); // occupy room until interval[1]
        }
        return endTimes.size();
    }
}
