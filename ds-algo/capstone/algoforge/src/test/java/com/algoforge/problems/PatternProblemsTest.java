package com.algoforge.problems;

import com.algoforge.problems.patterns.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class PatternProblemsTest {

    @Test void medianFinderOdd() {
        MedianFinder mf = new MedianFinder();
        mf.addNum(1); mf.addNum(2); mf.addNum(3);
        assertThat(mf.findMedian()).isEqualTo(2.0);
    }

    @Test void medianFinderEven() {
        MedianFinder mf = new MedianFinder();
        mf.addNum(1); mf.addNum(2);
        assertThat(mf.findMedian()).isEqualTo(1.5);
    }

    @Test void medianFinderStream() {
        MedianFinder mf = new MedianFinder();
        mf.addNum(-1);
        assertThat(mf.findMedian()).isEqualTo(-1.0);
        mf.addNum(-2);
        assertThat(mf.findMedian()).isEqualTo(-1.5);
        mf.addNum(-3);
        assertThat(mf.findMedian()).isEqualTo(-2.0);
    }

    @Test void mergeIntervalsBasic() {
        int[][] result = MergeIntervals.merge(new int[][]{{1,3},{2,6},{8,10},{15,18}});
        assertThat(result.length).isEqualTo(3);
        assertThat(result[0]).containsExactly(1,6);
        assertThat(result[1]).containsExactly(8,10);
        assertThat(result[2]).containsExactly(15,18);
    }

    @Test void mergeIntervalsTouchingBoundary() {
        int[][] result = MergeIntervals.merge(new int[][]{{1,4},{4,5}});
        assertThat(result.length).isEqualTo(1);
        assertThat(result[0]).containsExactly(1,5);
    }

    @Test void kthLargestElement() {
        assertThat(KthLargestElement.findKthLargest(new int[]{3,2,1,5,6,4}, 2)).isEqualTo(5);
        assertThat(KthLargestElement.findKthLargest(new int[]{3,2,3,1,2,4,5,5,6}, 4)).isEqualTo(4);
    }

    @Test void kthLargestQuickSelect() {
        assertThat(KthLargestElement.findKthLargestQuickSelect(new int[]{3,2,1,5,6,4}, 2)).isEqualTo(5);
    }

    @Test void nonOverlappingIntervals() {
        assertThat(NonOverlappingIntervals.eraseOverlapIntervals(
            new int[][]{{1,2},{2,3},{3,4},{1,3}})).isEqualTo(1);
        assertThat(NonOverlappingIntervals.eraseOverlapIntervals(
            new int[][]{{1,2},{1,2},{1,2}})).isEqualTo(2);
        assertThat(NonOverlappingIntervals.eraseOverlapIntervals(
            new int[][]{{1,2},{2,3}})).isEqualTo(0);
    }

    @Test void findDuplicateNumber() {
        assertThat(FindDuplicateNumber.findDuplicate(new int[]{1,3,4,2,2})).isEqualTo(2);
        assertThat(FindDuplicateNumber.findDuplicate(new int[]{3,1,3,4,2})).isEqualTo(3);
    }

    @Test void firstMissingPositive() {
        assertThat(FirstMissingPositive.firstMissingPositive(new int[]{1,2,0})).isEqualTo(3);
        assertThat(FirstMissingPositive.firstMissingPositive(new int[]{3,4,-1,1})).isEqualTo(2);
        assertThat(FirstMissingPositive.firstMissingPositive(new int[]{7,8,9,11,12})).isEqualTo(1);
    }

    @Test void insertIntervalBasic() {
        int[][] result = InsertInterval.insert(new int[][]{{1,3},{6,9}}, new int[]{2,5});
        assertThat(result.length).isEqualTo(2);
        assertThat(result[0]).containsExactly(1,5);
        assertThat(result[1]).containsExactly(6,9);
    }

    @Test void insertIntervalNoOverlap() {
        int[][] result = InsertInterval.insert(new int[][]{{1,2},{3,5},{6,7},{8,10},{12,16}}, new int[]{4,8});
        assertThat(result.length).isEqualTo(3);
        assertThat(result[0]).containsExactly(1,2);
        assertThat(result[1]).containsExactly(3,10);
        assertThat(result[2]).containsExactly(12,16);
    }

    @Test void meetingRoomsII() {
        assertThat(MeetingRoomsII.minMeetingRooms(new int[][]{{0,30},{5,10},{15,20}})).isEqualTo(2);
        assertThat(MeetingRoomsII.minMeetingRooms(new int[][]{{7,10},{2,4}})).isEqualTo(1);
    }

    @Test void mergeKSortedLists() {
        MergeKSortedLists.ListNode l1 = new MergeKSortedLists.ListNode(1);
        l1.next = new MergeKSortedLists.ListNode(4);
        l1.next.next = new MergeKSortedLists.ListNode(5);
        MergeKSortedLists.ListNode l2 = new MergeKSortedLists.ListNode(1);
        l2.next = new MergeKSortedLists.ListNode(3);
        l2.next.next = new MergeKSortedLists.ListNode(4);
        MergeKSortedLists.ListNode l3 = new MergeKSortedLists.ListNode(2);
        l3.next = new MergeKSortedLists.ListNode(6);

        MergeKSortedLists.ListNode result = MergeKSortedLists.mergeKLists(
            new MergeKSortedLists.ListNode[]{l1, l2, l3});
        // Verify result is sorted
        MergeKSortedLists.ListNode curr = result;
        int prev = Integer.MIN_VALUE;
        while (curr != null) {
            assertThat(curr.val).isGreaterThanOrEqualTo(prev);
            prev = curr.val;
            curr = curr.next;
        }
    }
}
