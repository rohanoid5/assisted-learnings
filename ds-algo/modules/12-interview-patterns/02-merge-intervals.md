# 12.2 — Merge Intervals Pattern

## Concept

The **Merge Intervals** pattern applies whenever a problem involves ranges, windows, or time slots that may **overlap**. The key insight is a single condition: two intervals `[a, b]` and `[c, d]` overlap if and only if `a <= d && c <= b` (equivalently, `c <= b`). After sorting by start time, you can process all overlaps in one pass.

---

## Deep Dive

### The Overlap Condition

```
Interval A:  ├────────┤
Interval B:        ├────────┤    OVERLAP: A.end >= B.start (after sorting A.start <= B.start)

Interval A:  ├──┤
Interval B:          ├────┤     NO OVERLAP: A.end < B.start

  A = [1, 4], B = [3, 6] → overlap (4 >= 3)
  A = [1, 4], B = [2, 3] → B contained in A (merge → [1, 4])
  A = [1, 2], B = [3, 4] → no overlap (2 < 3)
```

### Merge Intervals Algorithm

```
1. Sort intervals by start time: O(n log n)
2. Initialize result with the first interval
3. For each subsequent interval:
     if it overlaps with result.last:
         merge by extending the end: result.last.end = max(result.last.end, current.end)
     else:
         append current to result

Example: [[1,3],[2,6],[8,10],[15,18]]

Sorted: [[1,3],[2,6],[8,10],[15,18]]  (already sorted)

current=[1,3]  → result=[[1,3]]
current=[2,6]  → 2<=3 overlap → extend: result=[[1,6]]
current=[8,10] → 8>6 no overlap → append: result=[[1,6],[8,10]]
current=[15,18]→ 15>10 no overlap → append: result=[[1,6],[8,10],[15,18]]
```

### Insert Interval (no sorting needed)

When inserting a single new interval into a sorted non-overlapping list:

```
Three phases:
  Phase 1: Add all intervals that end before newInterval starts (no overlap)
  Phase 2: Merge all overlapping intervals into newInterval
  Phase 3: Add all remaining intervals
```

---

## Code Examples

### Example 1: Merge Intervals (LC #56)

```java
public int[][] merge(int[][] intervals) {
    // Sort by start time
    Arrays.sort(intervals, (a, b) -> a[0] - b[0]);

    List<int[]> result = new ArrayList<>();
    result.add(intervals[0]);

    for (int i = 1; i < intervals.length; i++) {
        int[] last = result.get(result.size() - 1);
        int[] curr = intervals[i];

        if (curr[0] <= last[1]) {
            // Overlap — extend the end
            last[1] = Math.max(last[1], curr[1]);
        } else {
            result.add(curr);
        }
    }
    return result.toArray(new int[0][]);
}
```

### Example 2: Insert Interval (LC #57)

```java
public int[][] insert(int[][] intervals, int[] newInterval) {
    List<int[]> result = new ArrayList<>();
    int i = 0, n = intervals.length;

    // Phase 1: intervals that end before newInterval starts
    while (i < n && intervals[i][1] < newInterval[0])
        result.add(intervals[i++]);

    // Phase 2: merge overlapping intervals
    while (i < n && intervals[i][0] <= newInterval[1]) {
        newInterval[0] = Math.min(newInterval[0], intervals[i][0]);
        newInterval[1] = Math.max(newInterval[1], intervals[i][1]);
        i++;
    }
    result.add(newInterval);

    // Phase 3: remaining intervals
    while (i < n) result.add(intervals[i++]);

    return result.toArray(new int[0][]);
}
```

### Example 3: Meeting Rooms II — Minimum Rooms Required (LC #253)

Finding the minimum number of meeting rooms is equivalent to finding the maximum number of overlapping intervals at any point.

```java
public int minMeetingRooms(int[][] intervals) {
    int n = intervals.length;
    int[] starts = new int[n], ends = new int[n];

    for (int i = 0; i < n; i++) {
        starts[i] = intervals[i][0];
        ends[i]   = intervals[i][1];
    }
    Arrays.sort(starts);
    Arrays.sort(ends);

    // Two-pointer scan: for each meeting start, check if a room is free
    int rooms = 0, endPtr = 0;
    for (int i = 0; i < n; i++) {
        if (starts[i] < ends[endPtr]) {
            rooms++;          // no room free — need a new room
        } else {
            endPtr++;         // a meeting ended — reuse its room
        }
    }
    return rooms;
}

// Alternative: min-heap approach — O(n log n)
public int minMeetingRoomsHeap(int[][] intervals) {
    Arrays.sort(intervals, (a, b) -> a[0] - b[0]);
    PriorityQueue<Integer> minHeap = new PriorityQueue<>();  // tracks end times

    for (int[] interval : intervals) {
        if (!minHeap.isEmpty() && minHeap.peek() <= interval[0])
            minHeap.poll();   // reuse this room
        minHeap.offer(interval[1]);
    }
    return minHeap.size();
}
```

---

## Try It Yourself

**Exercise:** Trace the insert interval algorithm for:
- `intervals = [[1,2],[3,5],[6,7],[8,10],[12,16]]`
- `newInterval = [4, 8]`

Draw the three phases (no-overlap left, merge, no-overlap right) before running the code.

<details>
<summary>Show answer</summary>

```
Phase 1 (end < 4): [1,2], [3,5]? → 5 >= 4, so only [1,2] qualifies.
  result = [[1,2]], i=1

Phase 2 (start <= 8): [3,5] start=3<=8 ✓, [6,7] start=6<=8 ✓, [8,10] start=8<=8 ✓
  Merge: newInterval = [min(4,3), max(8,5)] = [3,8]
         newInterval = [min(3,6), max(8,7)] = [3,8]
         newInterval = [min(3,8), max(8,10)] = [3,10]
  i=4
  result = [[1,2], [3,10]]

Phase 3: [12,16] → result = [[1,2],[3,10],[12,16]]

Answer: [[1,2],[3,10],[12,16]]
```
</details>

---

## Capstone Connection

Add `MergeIntervals.java`, `InsertInterval.java`, and `MeetingRooms2.java` to `AlgoForge/problems/patterns/`. This pattern appears in calendar applications, CPU scheduling, and network packet coalescing — all common system design discussion topics in interviews.
