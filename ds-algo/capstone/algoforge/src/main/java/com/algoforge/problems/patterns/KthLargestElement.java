package com.algoforge.problems.patterns;

import java.util.PriorityQueue;

/**
 * LC #215 — Kth Largest Element in an Array
 *
 * <p>Given an integer array nums and an integer k, return the kth largest element.
 * The kth largest means the kth largest in sorted order, not the kth distinct.</p>
 *
 * <b>Approach 1:</b> MinHeap of size k — O(n log k)
 * <b>Approach 2:</b> QuickSelect — O(n) average, O(n²) worst
 *
 * <pre>
 * MinHeap approach:
 *   Maintain a heap of size k. When size > k, poll (remove minimum).
 *   After processing all elements, heap.peek() = kth largest.
 *
 * Trace: [3,2,1,5,6,4], k=2
 *   heap=[3], [3,2], [3,1]→poll1=[3,2]? No: min-heap.
 *   Actually: after each insert, if size>k, poll min.
 *   insert 3→[3], insert 2→[2,3], insert 1: size=3>2→poll 1=[2,3]
 *   insert 5: [2,3,5]→poll 2=[3,5]
 *   insert 6: [3,5,6]→poll 3=[5,6]
 *   insert 4: [4,5,6]→poll 4=[5,6]
 *   peek()=5 ← 2nd largest ✓
 *
 * QuickSelect: partition around pivot; recurse only on relevant half.
 * </pre>
 *
 * Time: O(n log k) — MinHeap  |  O(n) avg — QuickSelect   Space: O(k) | O(1)
 */
public class KthLargestElement {

    // MinHeap approach — O(n log k)
    public static int findKthLargest(int[] nums, int k) {
        PriorityQueue<Integer> minHeap = new PriorityQueue<>();
        for (int n : nums) {
            minHeap.offer(n);
            if (minHeap.size() > k) minHeap.poll();
        }
        return minHeap.peek();
    }

    // QuickSelect — O(n) average
    public static int findKthLargestQuickSelect(int[] nums, int k) {
        int target = nums.length - k; // kth largest = (n-k)th smallest (0-indexed)
        int lo = 0, hi = nums.length - 1;
        while (true) {
            int pivot = partition(nums, lo, hi);
            if (pivot == target) return nums[pivot];
            if (pivot < target)  lo = pivot + 1;
            else                 hi = pivot - 1;
        }
    }

    private static int partition(int[] nums, int lo, int hi) {
        int pivot = nums[hi], i = lo;
        for (int j = lo; j < hi; j++)
            if (nums[j] <= pivot) { int tmp = nums[i]; nums[i] = nums[j]; nums[j] = tmp; i++; }
        int tmp = nums[i]; nums[i] = nums[hi]; nums[hi] = tmp;
        return i;
    }
}
