package com.algoforge.datastructures.linear;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Monotonic Stack — a stack that maintains a monotonically increasing or
 * decreasing order of elements. Elements that would violate the order are
 * popped before the new element is pushed.
 *
 * <pre>
 * Example — Decreasing Monotonic Stack processing [3, 1, 4, 1, 5]:
 *
 * Push 3:  stack = [3]
 * Push 1:  1 < 3, so just push → stack = [3, 1]
 * Push 4:  4 > 1 → pop 1 (answer for 1 is 4)
 *          4 > 3 → pop 3 (answer for 3 is 4)
 *          stack = [4]
 * Push 1:  1 < 4 → stack = [4, 1]
 * Push 5:  5 > 1 → pop 1 (answer for 1 is 5)
 *          5 > 4 → pop 4 (answer for 4 is 5)
 *          stack = [5]
 *
 * Result (Next Greater Element): [4, 4, 5, 5, -1]
 * </pre>
 *
 * <p>Key patterns:</p>
 * <ul>
 *   <li><b>Next Greater Element</b> — decreasing stack</li>
 *   <li><b>Next Smaller Element</b> — increasing stack</li>
 *   <li><b>Largest Rectangle in Histogram</b> — increasing stack on heights</li>
 *   <li><b>Daily Temperatures</b> — decreasing stack of indices</li>
 * </ul>
 *
 * Time:  O(n) for a full pass (each element pushed and popped at most once)
 * Space: O(n)
 */
public class MonotonicStack {

    // ── Next Greater Element ─────────────────────────────────────────────────

    /**
     * For each element, find the index of the next element that is strictly greater.
     * Returns -1 if no such element exists.
     *
     * Uses a <em>decreasing</em> monotonic stack of indices.
     *
     * Example: [2, 1, 4, 3] → [2, 2, -1, -1]
     * (next greater of 2 is at index 2 (value 4), of 1 is also at index 2, etc.)
     */
    public static int[] nextGreaterIndex(int[] nums) {
        int n = nums.length;
        int[] result = new int[n];
        java.util.Arrays.fill(result, -1);
        Deque<Integer> stack = new ArrayDeque<>(); // stores indices

        for (int i = 0; i < n; i++) {
            // While the current element is greater than the element at stack's top index
            while (!stack.isEmpty() && nums[i] > nums[stack.peek()]) {
                result[stack.pop()] = i;
            }
            stack.push(i);
        }
        return result;
    }

    /**
     * For each element, find the value of the next element that is strictly greater.
     * Returns -1 if no such element exists.
     *
     * Example: [2, 1, 4, 3] → [4, 4, -1, -1]
     */
    public static int[] nextGreaterValue(int[] nums) {
        int n = nums.length;
        int[] result = new int[n];
        java.util.Arrays.fill(result, -1);
        Deque<Integer> stack = new ArrayDeque<>();

        for (int i = 0; i < n; i++) {
            while (!stack.isEmpty() && nums[i] > nums[stack.peek()]) {
                result[stack.pop()] = nums[i];
            }
            stack.push(i);
        }
        return result;
    }

    // ── Daily Temperatures ───────────────────────────────────────────────────

    /**
     * Given daily temperatures, return an array where each element is the number
     * of days until a warmer temperature. 0 if no warmer day exists.
     *
     * Example: [73, 74, 75, 71, 69, 72, 76, 73]
     *       →  [ 1,  1,  4,  2,  1,  1,  0,  0]
     *
     * Uses a decreasing monotonic stack of indices.
     * Time: O(n)  Space: O(n)
     */
    public static int[] dailyTemperatures(int[] temperatures) {
        int n = temperatures.length;
        int[] result = new int[n];
        Deque<Integer> stack = new ArrayDeque<>();

        for (int i = 0; i < n; i++) {
            while (!stack.isEmpty() && temperatures[i] > temperatures[stack.peek()]) {
                int prevDay = stack.pop();
                result[prevDay] = i - prevDay;
            }
            stack.push(i);
        }
        return result;
    }

    // ── Largest Rectangle in Histogram ─────────────────────────────────────

    /**
     * Find the area of the largest rectangle that can be formed in a histogram.
     *
     * <pre>
     * heights = [2, 1, 5, 6, 2, 3]
     *
     *       ┌─┐
     *     ┌─┤ ├─┐
     *     │ │ │ │
     * ┌─┐ │ │ │ ├─┐
     * │ ├─┤ │ │ │ │
     * │ │ │ │ │ │ │
     *  2  1  5  6  2  3
     *
     * Largest rectangle = 10 (height 2, width 5 → bars 0–4)
     * </pre>
     *
     * Uses an increasing monotonic stack to find, for each bar,
     * the nearest shorter bar on its left and right.
     * Time: O(n)  Space: O(n)
     */
    public static int largestRectangleInHistogram(int[] heights) {
        int n = heights.length;
        int maxArea = 0;
        Deque<Integer> stack = new ArrayDeque<>(); // increasing stack of indices

        for (int i = 0; i <= n; i++) {
            int h = (i == n) ? 0 : heights[i]; // sentinel 0 at the end flushes the stack

            while (!stack.isEmpty() && h < heights[stack.peek()]) {
                int height = heights[stack.pop()];
                int width  = stack.isEmpty() ? i : i - stack.peek() - 1;
                maxArea = Math.max(maxArea, height * width);
            }
            stack.push(i);
        }
        return maxArea;
    }

    // ── Trapping Rain Water ─────────────────────────────────────────────────

    /**
     * Calculate how much rainwater can be trapped between elevation bars.
     *
     * <pre>
     * height = [0,1,0,2,1,0,1,3,2,1,2,1]
     *                  ┌─┐
     *         ┌─┐      │ │┌─┐   ┌─┐
     *  ┌─┐    │ │┌─┐┌─┤ │├─┤   ├─┤
     *  │ │┌─┐│ ││ ││ │ ││ │      │ │
     *  0 1 0 2 1 0 1 3 2 1 2 1   → 6 units of water
     * </pre>
     *
     * Time: O(n)  Space: O(n)
     */
    public static int trapRainWater(int[] height) {
        int water = 0;
        Deque<Integer> stack = new ArrayDeque<>();

        for (int i = 0; i < height.length; i++) {
            while (!stack.isEmpty() && height[i] > height[stack.peek()]) {
                int bottom = stack.pop();
                if (stack.isEmpty()) break;
                int left  = stack.peek();
                int width = i - left - 1;
                int h     = Math.min(height[left], height[i]) - height[bottom];
                water += h * width;
            }
            stack.push(i);
        }
        return water;
    }
}
