# 6.2 — Recurrence Relations & Master Theorem

## Concept

A **recurrence relation** expresses the time complexity of a recursive algorithm in terms of itself. Solving the recurrence gives you Big-O. The **Master Theorem** is a shortcut that handles the most common divide-and-conquer pattern in one step — but only when the recurrence fits its form.

---

## Deep Dive

### Writing a Recurrence

For each recursive algorithm, ask:
1. How many recursive calls are made? → `a`
2. What size is each subproblem? → `n/b`
3. How much non-recursive work is done at each level? → `f(n)`

```
T(n) = a · T(n/b) + f(n)

a = number of subproblems
b = factor by which input shrinks
f(n) = cost of work outside the recursive calls
```

**Examples:**

| Algorithm | Recurrence | a | b | f(n) |
|-----------|-----------|---|---|------|
| Merge Sort | T(n) = 2·T(n/2) + O(n) | 2 | 2 | n |
| Binary Search | T(n) = 1·T(n/2) + O(1) | 1 | 2 | 1 |
| Fibonacci (naive) | T(n) = 2·T(n-1) + O(1) | 2 | — | 1 |
| Quick Sort (avg) | T(n) = 2·T(n/2) + O(n) | 2 | 2 | n |
| Naive Matrix Mult | T(n) = 8·T(n/2) + O(n²) | 8 | 2 | n² |

---

### The Master Theorem

Applies to recurrences of the form: **T(n) = a·T(n/b) + O(n^d)**

```
Let p = log_b(a)  (the "critical exponent")

Case 1: d < p   →  T(n) = O(n^p)         [recursion-dominated]
Case 2: d = p   →  T(n) = O(n^p · log n) [balanced]
Case 3: d > p   →  T(n) = O(n^d)         [work-dominated]
```

**Worked examples:**

```
Merge Sort: a=2, b=2, d=1
  p = log₂(2) = 1
  d = p → Case 2 → O(n¹ · log n) = O(n log n) ✓

Binary Search: a=1, b=2, d=0
  p = log₂(1) = 0
  d = p → Case 2 → O(n⁰ · log n) = O(log n) ✓

Fibonacci (naive): NOT a divide-and-conquer recurrence (n-1 not n/b)
  Use the recursion tree method instead → O(2^n) via geometric series
```

---

### The Recursion Tree Method

When Master Theorem doesn't apply, draw the call tree:

```
T(n) = 2·T(n/2) + n  (Merge Sort)

Level 0:         n                          ← work = n
Level 1:     n/2   n/2                      ← work = n/2 + n/2 = n
Level 2:  n/4 n/4 n/4 n/4                  ← work = n
...
Level k:  n/2^k ... (2^k nodes each doing 1 work) ← base case when n/2^k = 1, k = log n

Total = n × (log n levels) = O(n log n)
```

---

## Code Examples

### Counting Calls in Fibonacci

```java
// Instrument to see the exponential blow-up
int calls = 0;
public int fib(int n) {
    calls++;
    if (n <= 1) return n;
    return fib(n - 1) + fib(n - 2);
}
// fib(10): ~177 calls
// fib(20): ~21,891 calls
// fib(30): ~2,692,537 calls
// fib(40): ~331,160,281 calls — do NOT run fib(50) without memoization
```

### Divide and Conquer: Closest Pair of Points

```java
// T(n) = 2·T(n/2) + O(n log n)  →  O(n log² n) via Master Theorem Case 2 adjusted
// (f(n) = n log n, 'n^d' = n log n, slightly above n^p = n → "Case 3-ish")
// Full implementation is in Module 07 exercises
```

---

## Try It Yourself

**Exercise:** Identify the recurrence and apply the Master Theorem (or recursion tree) to find Big-O for each:

```
A. T(n) = 3·T(n/3) + O(n)
B. T(n) = 4·T(n/2) + O(n)
C. T(n) = T(n-1) + O(1)   ← note: this is NOT a/b form
D. T(n) = 2·T(n/4) + O(√n)
```

<details>
<summary>Solutions</summary>

```
A. a=3, b=3, d=1. p = log₃(3) = 1. d = p → Case 2 → O(n log n)

B. a=4, b=2, d=1. p = log₂(4) = 2. d < p → Case 1 → O(n²)
   [recursion dominates — branching factor grows faster than work reduction]

C. NOT Master Theorem (not n/b form — decrements by 1 each time).
   Recursion tree: n levels, O(1) per level → O(n) total.
   This is the pattern for linear-scan recursion.

D. a=2, b=4, d=0.5 (since √n = n^0.5). p = log₄(2) = 0.5.
   d = p → Case 2 → O(√n · log n)
```

</details>

---

## Capstone Connection

When you implement `MergeSort.java` in Module 07's AlgoForge deliverable, you can apply T(n) = 2·T(n/2) + O(n) → O(n log n) from memory. When an interviewer asks "why is your algorithm O(n log n)?", this is the answer — not intuition, but a provable argument from the recursion tree.
