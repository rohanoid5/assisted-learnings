# 1.5 — Big-O Notation

## Concept

Big-O notation describes the **upper bound** of an algorithm's growth rate as input size n approaches infinity. It answers the question: *"In the worst case, how does my algorithm scale?"* — ignoring constants and hardware differences.

---

## Deep Dive

### Formal Definition

f(n) is O(g(n)) if there exist constants c > 0 and n₀ > 0 such that:

```
f(n) ≤ c · g(n)   for all n ≥ n₀
```

In plain English: after some threshold n₀, g(n) is always an upper bound on f(n) (up to a constant multiplier).

**Example:** Is 3n² + 5n + 2 = O(n²)?

```
Pick c = 4, n₀ = 100:
3n² + 5n + 2  ≤  4n²  for all n ≥ 100?
LHS = 3(100²) + 5(100) + 2 = 30,502
RHS = 4(100²) = 40,000  ✓

Yes — the quadratic term dominates. The rest is noise.
```

---

### Simplification Rules

```
Rule                              Example
─────────────────────────────     ──────────────────────────
1. Drop multiplicative constants  5n → O(n)
2. Drop lower-order terms         n² + n + 1 → O(n²)
3. Addition: keep dominant        O(n) + O(n²) = O(n²)
4. Multiplication: keep all       O(n) × O(n) = O(n²)
5. Logs are equal (base doesn't   O(log₂n) = O(log₁₀n) = O(log n)
   matter for Big-O)
```

---

### Visualizing Growth Rates

```
n = 1,000    (rough relative operations, rounded)

O(1)         │█                                  1
O(log n)     │██                                 10
O(n)         │████████████████████               1,000
O(n log n)   │████████████████████ x10           10,000
O(n²)        │████... (much longer bar)          1,000,000
O(2ⁿ)        │████... (longer than observable)  10^301

           0                                     →  operations
```

The exponential curve dwarfs everything else. Never accept an exponential solution in an interview without immediately identifying why a memoized/DP version isn't possible.

---

### Amortized Analysis

Some operations are sometimes expensive but *cheap on average over many calls*. Java's `ArrayList.add()` is the canonical example:

```
Capacity: 1 → 2 → 4 → 8 → 16 ...
Add ops:  1    1   1   1    1   (O(1) most of the time)
          ↑ occasional O(n) copy when capacity doubles

Cost of n additions = n + (n/2 + n/4 + ... + 1) = n + n = 2n
Amortized cost per add = 2n / n = O(1)
```

**Other amortized O(1) operations to know:**
- `HashMap.put()` — O(1) amortized, O(n) worst case on rehash
- `Stack.push()` on a dynamic array
- `ArrayDeque.offer()`

---

### Common Big-O Classified by Code Pattern

| Code pattern | Complexity |
|---|---|
| `int x = arr[0]` | O(1) |
| Single `for` loop from 0 to n | O(n) |
| Two nested loops (both 0 to n) | O(n²) |
| Loop that halves (`i /= 2`) | O(log n) |
| Loop 0..n with inner binary search | O(n log n) |
| Recursive with 2 calls, halving each | O(n log n) merge sort |
| Recursive with 2 calls, reducing by 1 | O(2ⁿ) |
| Iterating all subsets | O(2ⁿ) |
| Iterating all permutations | O(n!) |

---

## Code Examples

### Example 1: Recognizing Hidden Complexity

```java
// What is the complexity of this "simple looking" function?
public boolean containsDuplicate(String[] words) {
    for (int i = 0; i < words.length; i++) {
        for (int j = 0; j < words.length; j++) {
            if (i != j && words[i].equals(words[j])) return true;
        }
    }
    return false;
}
// Outer: O(n), Inner: O(n), .equals(): O(L) per word of length L
// Total: O(n² · L) — the string comparison is hidden work!

// Fixed version:
public boolean containsDuplicateFast(String[] words) {
    return new HashSet<>(Arrays.asList(words)).size() < words.length;
    // HashSet.add(): O(L) to hash, n words → O(n · L) total
}
```

### Example 2: Two Pointers — How O(2n) Becomes O(n)

```java
// Is this O(2n) or O(n)?
public boolean isPalindrome(int[] arr) {
    int left = 0, right = arr.length - 1;
    while (left < right) {    // at most n/2 iterations
        if (arr[left] != arr[right]) return false;
        left++;
        right--;
    }
    return true;
}
// Each iteration: O(1). Total iterations: n/2.
// O(n/2) = O(n) — constants are dropped.
// Both pointers together move at most n steps total.
```

---

## Try It Yourself

**Exercise:** For each line, state the Big-O without simplifying first, then simplify.

```java
// Function A: what's the exact expression, then simplified Big-O?
public void process(int n) {
    // Part 1
    for (int i = 0; i < n; i++) doWork();        // doWork() = O(1)

    // Part 2
    for (int i = 0; i < n; i++)
        for (int j = 0; j < n; j++) doWork();

    // Part 3
    for (int i = n; i > 1; i /= 2) doWork();
}
```

<details>
<summary>Show answer</summary>

- Part 1: n operations → O(n)
- Part 2: n × n operations → O(n²)
- Part 3: log₂n operations → O(log n)

Total expression: **O(n + n² + log n)**

Simplified: **O(n²)** — the quadratic term dominates all others for large n.

In an interview: say "this function is O(n²) due to the nested loop in Part 2; the linear and log-linear phases are swallowed by the dominant term."

</details>

---

## Capstone Connection

Every `problems/` package in AlgoForge has a Javadoc comment above the solution method with `// Time: O(...), Space: O(...)`. It is part of the submission requirement — solutions without complexity annotations are considered incomplete.
