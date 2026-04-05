package com.algoforge.problems.stacksqueues;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * LC #150 — Evaluate Reverse Polish Notation
 *
 * <p>Evaluate an expression in Reverse Polish Notation (postfix).
 * Valid operators: +, -, *, /. Each operand may be an integer or another expression.
 * Division truncates toward zero.</p>
 *
 * <b>Pattern:</b> Stack — push operands, apply operator to top two on operator token.
 *
 * <pre>
 * Trace: ["2","1","+","3","*"]
 *   "2" → push 2. stack=[2]
 *   "1" → push 1. stack=[2,1]
 *   "+" → pop 1, pop 2, push 3. stack=[3]
 *   "3" → push 3. stack=[3,3]
 *   "*" → pop 3, pop 3, push 9. stack=[9]
 *   result: 9
 * </pre>
 *
 * Time: O(n)  Space: O(n)
 */
public class EvaluateRPN {

    public static int evalRPN(String[] tokens) {
        Deque<Integer> stack = new ArrayDeque<>();
        for (String token : tokens) {
            switch (token) {
                case "+" -> { int b = stack.pop(); stack.push(stack.pop() + b); }
                case "-" -> { int b = stack.pop(); stack.push(stack.pop() - b); }
                case "*" -> { int b = stack.pop(); stack.push(stack.pop() * b); }
                case "/" -> { int b = stack.pop(); stack.push(stack.pop() / b); }
                default  -> stack.push(Integer.parseInt(token));
            }
        }
        return stack.pop();
    }
}
