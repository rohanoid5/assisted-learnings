package com.algoforge.problems.patterns;

import java.util.PriorityQueue;

/**
 * LC #295 — Find Median from Data Stream
 *
 * <p>Design a data structure that supports:
 *   - addNum(int num): add a number to the data stream
 *   - findMedian(): return the median of all added numbers
 * </p>
 *
 * <b>Pattern:</b> Two Heaps — MaxHeap for lower half, MinHeap for upper half.
 *
 * <pre>
 * Invariant:
 *   - maxHeap (lower half) size = minHeap (upper half) size, OR
 *   - maxHeap has one more element (odd total → median is maxHeap.peek())
 *
 * addNum:
 *   1. Push to maxHeap (lower half)
 *   2. Move maxHeap.top to minHeap (ensures all lower ≤ all upper)
 *   3. If minHeap is larger, move minHeap.top to maxHeap (balance)
 *
 * findMedian:
 *   - Equal sizes: (maxHeap.peek() + minHeap.peek()) / 2.0
 *   - MaxHeap has one more: maxHeap.peek()
 *
 * Trace: addNum(1), addNum(2)
 *   After 1: maxHeap=[1], minHeap=[]   → median=1
 *   After 2: maxHeap=[1], minHeap=[2]  → median=1.5
 *   addNum(3): maxHeap=[2,1], minHeap=[3] → median=2
 * </pre>
 *
 * Time: O(log n) add, O(1) median   Space: O(n)
 */
public class MedianFinder {

    private final PriorityQueue<Integer> maxHeap = new PriorityQueue<>((a, b) -> b - a); // lower half
    private final PriorityQueue<Integer> minHeap = new PriorityQueue<>();               // upper half

    public void addNum(int num) {
        maxHeap.offer(num);
        minHeap.offer(maxHeap.poll()); // ensure all lower ≤ all upper
        if (minHeap.size() > maxHeap.size())
            maxHeap.offer(minHeap.poll()); // balance sizes
    }

    public double findMedian() {
        if (maxHeap.size() > minHeap.size()) return maxHeap.peek();
        return (maxHeap.peek() + minHeap.peek()) / 2.0;
    }
}
