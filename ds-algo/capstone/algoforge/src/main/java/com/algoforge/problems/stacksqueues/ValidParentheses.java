package com.algoforge.problems.stacksqueues;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * LC #20 — Valid Parentheses
 *
 * <p>Given a string of brackets '(', ')', '{', '}', '[', ']',
 * determine if the input string is valid.</p>
 *
 * <b>Pattern:</b> Stack — match opening with most recent closing.
 *
 * <pre>
 * Trace: s = "({[]})"
 *   '('  → push
 *   '{'  → push
 *   '['  → push
 *   ']'  → top is '[' → match! pop
 *   '}'  → top is '{' → match! pop
 *   ')'  → top is '(' → match! pop
 *   stack empty → true ✓
 *
 * Trace: s = "([)]"
 *   '('  → push
 *   '['  → push
 *   ')'  → top is '[' → mismatch! → false
 * </pre>
 *
 * Time: O(n)  Space: O(n)
 */
public class ValidParentheses {

    public static boolean isValid(String s) {
        Deque<Character> stack = new ArrayDeque<>();
        for (char c : s.toCharArray()) {
            if (c == '(' || c == '{' || c == '[') {
                stack.push(c);
            } else {
                if (stack.isEmpty()) return false;
                char top = stack.pop();
                if (c == ')' && top != '(') return false;
                if (c == '}' && top != '{') return false;
                if (c == ']' && top != '[') return false;
            }
        }
        return stack.isEmpty();
    }
}
