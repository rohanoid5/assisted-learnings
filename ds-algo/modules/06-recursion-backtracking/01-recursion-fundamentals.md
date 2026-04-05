# 6.1 — Recursion Fundamentals

## Concept

A **recursive function** calls itself with a smaller version of the original problem. Every recursive solution has two parts: a **base case** (the smallest problem with a direct answer) and a **recursive case** (break the problem down, call yourself, assemble the result). Get both right and recursion becomes natural. Get either wrong and you get wrong answers or a stack overflow.

---

## Deep Dive

### The Call Stack

Each function call pushes a new **stack frame** onto the call stack. Recursive calls push many frames:

```
factorial(4):

┌──────────────────────────┐
│  factorial(1) → returns 1 │  ← top of stack, resolves first
├──────────────────────────┤
│  factorial(2) → 2 * ???   │  waiting for factorial(1)
├──────────────────────────┤
│  factorial(3) → 3 * ???   │  waiting for factorial(2)
├──────────────────────────┤
│  factorial(4) → 4 * ???   │  waiting for factorial(3)
├──────────────────────────┤
│  main()                   │  ← bottom of stack
└──────────────────────────┘

Unwinds bottom-up: 1 → (2×1=2) → (3×2=6) → (4×6=24)
```

### Stack Overflow

```
factorial(-1):
  → calls factorial(-2)
  → calls factorial(-3)
  → ... forever → StackOverflowError

Fix: always validate your base case covers all possible inputs.
if (n <= 0) return 1;   // handles n=0 and negative inputs
```

---

### The Recursion Template

```
solve(problem):
  if problem is trivially solvable:
    return base_case_answer

  smaller = reduce(problem)
  result_of_smaller = solve(smaller)
  return combine(result_of_smaller, current_context)
```

---

### Recursion vs Iteration

Many recursive algorithms have iterative equivalents, usually using an explicit stack:

```
Recursive DFS:                  Iterative DFS (explicit stack):
────────────────────            ────────────────────────────────
void dfs(Node n) {              void dfs(Node root) {
  if (n == null) return;          Deque<Node> stack = new ArrayDeque<>();
  visit(n);                       stack.push(root);
  dfs(n.left);                    while (!stack.isEmpty()) {
  dfs(n.right);                     Node n = stack.pop();
}                                   visit(n);
                                    if (n.right != null) stack.push(n.right);
                                    if (n.left  != null) stack.push(n.left);
                                  }
                                }
Rule of thumb: keep recursive when the code is clearer. Switch to iterative
when depth is unbounded (risk of stack overflow on large inputs).
```

---

## Code Examples

### Fibonacci — Naive Recursion vs Memoization

```java
// Naive: O(2^n) time — exponential, recalculates the same subproblems
public int fib(int n) {
    if (n <= 1) return n;
    return fib(n - 1) + fib(n - 2);
}

// With memoization: O(n) time, O(n) space
private Map<Integer, Integer> memo = new HashMap<>();
public int fibMemo(int n) {
    if (n <= 1) return n;
    if (memo.containsKey(n)) return memo.get(n);
    int result = fibMemo(n - 1) + fibMemo(n - 2);
    memo.put(n, result);
    return result;
}
// Memoization = recursion + caching = top-down DP (covered fully in Module 11)
```

### Power Function (Fast Exponentiation)

```java
// x^n  by halving the problem each time → O(log n) instead of O(n)
public double myPow(double x, int n) {
    if (n == 0) return 1;
    if (n < 0) {
        x = 1 / x;
        n = -n;  // handle n = Integer.MIN_VALUE carefully in production
    }
    if (n % 2 == 0) {
        double half = myPow(x, n / 2);
        return half * half;   // critical: compute once, multiply — avoids calling twice
    }
    return x * myPow(x, n - 1);
}
```

### Merge Sort (Recursion on Array Halves)

```java
public void mergeSort(int[] arr, int left, int right) {
    if (left >= right) return;  // base case: 0 or 1 element

    int mid = left + (right - left) / 2;
    mergeSort(arr, left, mid);      // sort left half
    mergeSort(arr, mid + 1, right); // sort right half
    merge(arr, left, mid, right);   // combine sorted halves
    // Recursion tree has O(log n) levels, O(n) work per level → O(n log n)
}
```

---

## Try It Yourself

**Exercise:** Implement `reverseString(String s)` recursively. No loops allowed.

```java
// "hello" → "olleh"
// "" → ""
// "a" → "a"

public String reverseString(String s) {
    // your code here
}
```

<details>
<summary>Solution</summary>

```java
public String reverseString(String s) {
    if (s.length() <= 1) return s;  // base case
    // Recursive case: last char + reverse of everything before it
    return s.charAt(s.length() - 1) + reverseString(s.substring(0, s.length() - 1));
    // Time: O(n²) — substring creates a new string each call.
    // O(n) alternative: use char array + swap recursion
}

// O(n) time, O(n) stack space:
public void reverseArray(char[] s, int left, int right) {
    if (left >= right) return;
    char tmp = s[left]; s[left] = s[right]; s[right] = tmp;
    reverseArray(s, left + 1, right - 1);
}
```

</details>

---

## Capstone Connection

Every backtracking solution in AlgoForge uses recursive call stack management. When you implement `GenerateParentheses.java`, you'll see the exact template from this topic in action: base case (length reached), recursive case (add `(` or `)`), and automatic state cleanup on return.
