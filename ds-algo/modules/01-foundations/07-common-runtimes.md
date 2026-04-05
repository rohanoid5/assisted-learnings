# 1.7 — Common Runtimes

## Concept

Seven runtime classes cover nearly every algorithm you will encounter in interviews. Knowing a canonical example and the *feel* of each (is n=1000 instant or too slow?) lets you instantly sanity-check your solutions.

---

## Deep Dive

### The Runtime Ladder

```
Runtime      Name           Example algorithm                n=20 ops   n=1000 ops  n=10^6 ops
───────────  ─────────────  ──────────────────────────────  ─────────  ──────────  ──────────
O(1)         Constant       HashMap get, array index         1          1           1
O(log n)     Logarithmic    Binary search, BST search        4          10          20
O(n)         Linear         Linear scan, sum array           20         1,000       10^6
O(n log n)   Linearithmic   Merge sort, heap sort            86         10,000      2×10^7
O(n²)        Quadratic      Bubble sort, nested loop         400        10^6        10^12 ❌
O(2ⁿ)        Exponential    Naive Fibonacci, subsets         10^6       10^301 ❌   —
O(n!)        Factorial      All permutations                 2.4×10^18❌ —          —
```

❌ = unacceptably slow for that input size in any real system.

---

### O(1) — Constant

Operations that do not grow with input:

```java
int first = arr[0];                  // array index
map.get("key");                      // HashMap lookup
pq.peek();                           // heap peek (not poll)
deque.peekFirst();                   // ArrayDeque head read
stack.push(x);                       // Stack push
```

**Signal in interviews:** No loops, no recursion proportional to n, only direct reads/writes.

---

### O(log n) — Logarithmic

Each step *halves* (or otherwise divides) the problem size:

```java
// Binary search — classic O(log n)
int lo = 0, hi = arr.length - 1;
while (lo <= hi) {
    int mid = lo + (hi - lo) / 2;     // mid divides search space
    if (arr[mid] == target) return mid;
    if (arr[mid] < target) lo = mid + 1;
    else hi = mid - 1;
}

// BST traversal — each comparison goes left or right
TreeNode node = root;
while (node != null) {
    if (target < node.val) node = node.left;
    else if (target > node.val) node = node.right;
    else return node;
}

// Power by squaring (1.4 — How to Calculate Complexity, Example C)
int power(int base, int exp) {  // O(log exp)
    // ...
}
```

---

### O(n) — Linear

Single pass through the input:

```java
// Sum of array
int sum = 0;
for (int x : arr) sum += x;      // exactly n iterations

// Find max
int max = Integer.MIN_VALUE;
for (int x : arr) max = Math.max(max, x);

// Build frequency map
Map<Character, Integer> freq = new HashMap<>();
for (char c : s.toCharArray()) freq.merge(c, 1, Integer::sum);
```

---

### O(n log n) — Linearithmic

Sorting, heap operations on n elements, divide-and-conquer with O(n) merge:

```java
Arrays.sort(arr);               // O(n log n) — Timsort
Collections.sort(list);         // O(n log n)

// Manual merge sort
mergeSort(arr, 0, arr.length - 1);  // n log n

// n heap operations
PriorityQueue<Integer> pq = new PriorityQueue<>();
for (int x : arr) pq.offer(x);     // n × O(log n) = O(n log n)
```

---

### O(n²) — Quadratic

Nested loops over the same input:

```java
// All pairs
for (int i = 0; i < n; i++)
    for (int j = 0; j < n; j++)
        process(arr[i], arr[j]);

// Bubble sort
for (int i = 0; i < n; i++)
    for (int j = 0; j < n - i - 1; j++)
        if (arr[j] > arr[j + 1]) swap(arr, j, j + 1);

// DANGER: list.contains() inside a loop!
for (int x : arr)
    if (seen.contains(x)) ...   // O(n) scan × O(n) loop = O(n²) total
    // Fix: use HashSet
```

---

### O(2ⁿ) — Exponential

Generating all subsets, naive recursive Fibonacci:

```java
// All subsets of n elements — 2ⁿ subsets
public List<List<Integer>> subsets(int[] nums) {
    List<List<Integer>> result = new ArrayList<>();
    backtrack(nums, 0, new ArrayList<>(), result);
    return result;  // Size of result = 2ⁿ
}

// Naive Fibonacci — each call spawns 2, depth n → O(2ⁿ)
// (Fix with memoization → O(n))
```

---

### O(n!) — Factorial

Generating all permutations:

```java
// All permutations of n elements — n! total
public List<List<Integer>> permute(int[] nums) {
    // backtracking with n! leaves in decision tree
}
// n=10 → 3.6M leaves. n=12 → 479M. n=20 → 2.4 quintillion. 
```

---

### Java Collections Quick Reference (Memorize This)

| Operation | ArrayList | LinkedList | HashMap | TreeMap | PriorityQueue |
|-----------|-----------|------------|---------|---------|---------------|
| get(index) | O(1) | O(n) | — | — | — |
| add(tail) | O(1)* | O(1) | — | — | O(log n) |
| add(head) | O(n) | O(1) | — | — | — |
| remove(index) | O(n) | O(n) | — | — | — |
| contains | O(n) | O(n) | O(1)* | O(log n) | O(n) |
| put/get | — | — | O(1)* | O(log n) | — |
| poll/peek | — | — | — | O(log n) | O(log n)/O(1) |

*amortized or average

---

## Try It Yourself

**Exercise:** Without running anything, predict which runtimes are acceptable for **n = 10,000** with a 1-second deadline (assume ~10⁸ simple ops/sec).

Rate each as ✅ (fine), ⚠️ (borderline), or ❌ (too slow):

1. O(n) algorithm on n=10,000
2. O(n²) algorithm on n=10,000
3. O(n log n) algorithm on n=10,000
4. O(2ⁿ) algorithm on n=10,000
5. O(n log n) algorithm on n=10,000,000

<details>
<summary>Show answers</summary>

1. O(n), n=10,000 → 10,000 ops → ✅ extremely fast (~0.1ms)
2. O(n²), n=10,000 → 10⁸ ops → ⚠️ right at the 1-second boundary — needs optimization
3. O(n log n), n=10,000 → ~130,000 ops → ✅ very fast
4. O(2ⁿ), n=10,000 → 2^10,000 ops → ❌ not even theoretically possible in the lifetime of the universe
5. O(n log n), n=10M → ~2.3×10⁸ ops → ⚠️ borderline ~2-3 seconds; may need O(n) algorithm

The key threshold: If n ≤ ~10,000, O(n²) *might* pass. If n ≤ ~100, even O(2ⁿ) might work. Always check the constraint in the problem before optimizing.

</details>

---

## Capstone Connection

`ComplexityBenchmark.java` in AlgoForge measures O(1) through O(n²) empirically. Run it and observe: the time for O(n²) at n=10,000 compared to O(n log n) at n=1,000,000. The growth rates become visceral when you see actual milliseconds.
