package com.algoforge.problems.stacksqueues;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * LC #84 — Largest Rectangle in Histogram
 *
 * <p>Given an array of non-negative integers representing the heights of bars in a histogram
 * where each bar has width 1, find the area of the largest rectangle.</p>
 *
 * <b>Pattern:</b> Monotonic Stack (increasing) — for each bar, find the left and right
 * boundaries where it is the shortest bar.
 *
 * <pre>
 * Key insight: For bar i with height h[i], the largest rectangle using h[i] as its
 * limiting height extends left to the first shorter bar and right to the first shorter bar.
 *
 * Trace: heights = [2, 1, 5, 6, 2, 3]
 *   Use a stack of indices in increasing height order.
 *   When we find a bar shorter than stack top, pop and compute rectangle.
 *
 *   Result: 10 (bars at indices 2,3 with height 5: width=2, area=10)
 * </pre>
 *
 * Time: O(n)  Space: O(n)
 */
public class LargestRectangleHistogram {

    public static int largestRectangleArea(int[] heights) {
        int n = heights.length;
        Deque<Integer> stack = new ArrayDeque<>(); // monotonic increasing stack of indices
        int maxArea = 0;

        for (int i = 0; i <= n; i++) {
            int currHeight = (i == n) ? 0 : heights[i]; // sentinel 0 flushes the stack
            while (!stack.isEmpty() && heights[stack.peek()] > currHeight) {
                int h = heights[stack.pop()];
                int width = stack.isEmpty() ? i : i - stack.peek() - 1;
                maxArea = Math.max(maxArea, h * width);
            }
            stack.push(i);
        }
        return maxArea;
    }
}
