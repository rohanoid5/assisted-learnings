# 4.1 — Stack Fundamentals

## Concept

A **stack** is a LIFO (Last-In First-Out) collection. The last element pushed is the first one popped. Think: a stack of plates, or the call stack of a running program. Stacks are the natural tool for problems that need to "remember what came before" while processing left-to-right.

---

## Deep Dive

### Call Stack Analogy

```
Running factorial(3):

┌──────────────────────┐
│  factorial(1) → 1    │  ← top of stack (most recent call)
├──────────────────────┤
│  factorial(2)        │
├──────────────────────┤
│  factorial(3)        │
├──────────────────────┤
│  main()              │  ← bottom (first call)
└──────────────────────┘

Returns: 1 → (2×1=2) → (3×2=6)
Items are removed in reverse order of insertion — LIFO.
```

### Java: Use `ArrayDeque` not `Stack`

```
Java's Stack class extends Vector — synchronized, slow, legacy.
Prefer: Deque<T> stack = new ArrayDeque<>();

operation     Stack (legacy)   ArrayDeque (preferred)
─────────────────────────────────────────────────────
push          push(x)          push(x)  / offerFirst(x)
pop           pop()            pop()    / pollFirst()
peek          peek()           peek()   / peekFirst()
isEmpty       isEmpty()        isEmpty()
```

---

## Code Examples

### Valid Parentheses (LC #20)

```java
public boolean isValid(String s) {
    Deque<Character> stack = new ArrayDeque<>();
    for (char c : s.toCharArray()) {
        if (c == '(' || c == '[' || c == '{') {
            stack.push(c);
        } else {
            if (stack.isEmpty()) return false;
            char top = stack.pop();
            if (c == ')' && top != '(') return false;
            if (c == ']' && top != '[') return false;
            if (c == '}' && top != '{') return false;
        }
    }
    return stack.isEmpty();
    // Time: O(n), Space: O(n)
}
```

### Min Stack (LC #155) — O(1) getMin

```java
// Key insight: maintain a second stack of "minimums so far".
// Whenever a new minimum is pushed, push it onto the min-stack too.
// On pop, if the popped value equals the top of min-stack, pop that too.

class MinStack {
    private Deque<Integer> stack = new ArrayDeque<>();
    private Deque<Integer> minStack = new ArrayDeque<>();

    public void push(int val) {
        stack.push(val);
        if (minStack.isEmpty() || val <= minStack.peek())
            minStack.push(val);
    }

    public void pop() {
        int top = stack.pop();
        if (top == minStack.peek()) minStack.pop();
    }

    public int top()    { return stack.peek(); }
    public int getMin() { return minStack.peek(); }
}
```

---

## Try It Yourself

**Exercise:** Given an array of daily temperatures, return an array `answer` where `answer[i]` is the number of days until a warmer temperature. If no warmer day exists, `answer[i] = 0`.

Input: `[73,74,75,71,69,72,76,73]`
Output: `[1,1,4,2,1,1,0,0]`

<details>
<summary>Show solution</summary>

```java
public int[] dailyTemperatures(int[] temperatures) {
    int n = temperatures.length;
    int[] answer = new int[n];
    Deque<Integer> stack = new ArrayDeque<>();  // stores indices
    for (int i = 0; i < n; i++) {
        while (!stack.isEmpty() && temperatures[i] > temperatures[stack.peek()]) {
            int idx = stack.pop();
            answer[idx] = i - idx;
        }
        stack.push(i);
    }
    return answer;
    // Time: O(n), Space: O(n)
    // This is a monotonic decreasing stack by temperature value.
}
```

</details>

---

## Capstone Connection

Min Stack is a classic design question in phone screens. The pattern — maintaining auxiliary state alongside the main stack — reappears in the Monotonic Stack topic and in the "maximum depth with state" family of problems.
