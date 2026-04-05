package com.algoforge.problems.stacksqueues;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * LC #239 — Sliding Window Maximum
 *
 * <p>Given an array and a window size k, return the maximum value in each
 * sliding window of size k as the window moves left to right.</p>
 *
 * <b>Pattern:</b> Monotonic Deque — maintain decreasing deque of indices.
 *
 * <pre>
 * Key insight: We never need an element that is both older AND smaller than a newer element,
 * because it can never be the window maximum.
 *
 * Trace: nums=[1,3,-1,-3,5,3,6,7], k=3
 *   i=0: deque=[0]
 *   i=1: 3>1 → remove 0 → deque=[1]
 *   i=2: -1<3           deque=[1,2]   window full → result[0]=nums[1]=3
 *   i=3: -3<-1          deque=[1,2,3] window full → result[1]=nums[1]=3
 *   i=4: front 1 out of window(4-3=1) → remove. 5>-3,-1,3 → deque=[4]. result[2]=5
 *   i=5: 3<5            deque=[4,5]   result[3]=5
 *   i=6: front 4 out of window → remove. 6>3,5 → deque=[6]. result[4]=6
 *   i=7: 7>6            deque=[7]     result[5]=7
 *   output: [3,3,5,5,6,7]
 * </pre>
 *
 * Time: O(n)  Space: O(k)
 */
public class SlidingWindowMaximum {

    public static int[] maxSlidingWindow(int[] nums, int k) {
        int n = nums.length;
        int[] result = new int[n - k + 1];
        Deque<Integer> deque = new ArrayDeque<>(); // monotonic decreasing deque of indices

        for (int i = 0; i < n; i++) {
            // Remove elements outside the window
            while (!deque.isEmpty() && deque.peekFirst() < i - k + 1)
                deque.pollFirst();
            // Maintain decreasing invariant: remove smaller elements from the back
            while (!deque.isEmpty() && nums[deque.peekLast()] < nums[i])
                deque.pollLast();
            deque.offerLast(i);
            // Record result once first full window is reached
            if (i >= k - 1)
                result[i - k + 1] = nums[deque.peekFirst()];
        }
        return result;
    }
}
