package com.algoforge.problems.stacksqueues;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;

/**
 * LC #739 — Daily Temperatures
 *
 * <p>Given an array of daily temperatures, return an array where each element is
 * the number of days you have to wait until a warmer temperature.
 * If no future day is warmer, that element is 0.</p>
 *
 * <b>Pattern:</b> Monotonic Stack (decreasing) — find next greater element.
 *
 * <pre>
 * Trace: temps = [73, 74, 75, 71, 69, 72, 76, 73]
 *   i=0: stack=[0]
 *   i=1: 74>73 → pop 0, ans[0]=1-0=1. stack=[1]
 *   i=2: 75>74 → pop 1, ans[1]=2-1=1. stack=[2]
 *   i=3: 71<75 → stack=[2,3]
 *   i=4: 69<71 → stack=[2,3,4]
 *   i=5: 72>69 → pop 4, ans[4]=5-4=1. 72>71 → pop 3, ans[3]=5-3=2. stack=[2,5]
 *   i=6: 76>72 → pop 5, ans[5]=6-5=1. 76>75 → pop 2, ans[2]=6-2=4. stack=[6]
 *   i=7: 73<76 → stack=[6,7]
 *   result: [1, 1, 4, 2, 1, 1, 0, 0]
 * </pre>
 *
 * Time: O(n)  Space: O(n)
 */
public class DailyTemperatures {

    public static int[] dailyTemperatures(int[] temperatures) {
        int n = temperatures.length;
        int[] answer = new int[n];
        Deque<Integer> stack = new ArrayDeque<>(); // stores indices, monotonically decreasing temps

        for (int i = 0; i < n; i++) {
            while (!stack.isEmpty() && temperatures[i] > temperatures[stack.peek()]) {
                int idx = stack.pop();
                answer[idx] = i - idx;
            }
            stack.push(i);
        }
        return answer; // remaining indices in stack stay 0 (no warmer day)
    }
}
