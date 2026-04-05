package com.algoforge.datastructures.linear;

import java.util.EmptyStackException;

/**
 * MinStack — Module 04 capstone deliverable (Part 3).
 *
 * A stack that supports push, pop, peek, and getMin in O(1).
 *
 * Strategy: maintain a parallel "min stack" that stores the running minimum
 * at the time each element was pushed. When we pop, we pop both stacks.
 *
 * LeetCode #155
 */
public class MinStack {

    private final java.util.ArrayDeque<Integer> stack    = new java.util.ArrayDeque<>();
    private final java.util.ArrayDeque<Integer> minStack = new java.util.ArrayDeque<>();

    public void push(int val) {
        stack.push(val);
        // Push the new minimum: either val, or the current min if val is larger
        int newMin = minStack.isEmpty() ? val : Math.min(val, minStack.peek());
        minStack.push(newMin);
    }

    public void pop() {
        if (stack.isEmpty()) throw new EmptyStackException();
        stack.pop();
        minStack.pop();
    }

    public int top() {
        if (stack.isEmpty()) throw new EmptyStackException();
        return stack.peek();
    }

    public int getMin() {
        if (minStack.isEmpty()) throw new EmptyStackException();
        return minStack.peek();
    }

    public boolean isEmpty() { return stack.isEmpty(); }
}
