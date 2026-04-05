# 1.4 — How to Calculate Complexity

## Concept

Complexity analysis is a mechanical skill — not intuition. Given any piece of code, you can derive the Big-O systematically by following a small set of rules. This topic gives you that step-by-step process.

---

## Deep Dive

### The Four Rules

1. **Single statement:** O(1)
2. **Sequential statements:** Add them, keep the dominant
3. **Loop:** Multiply (iterations × body complexity)
4. **Recursive call:** Use the recurrence relation, solve with substitution or Master Theorem

---

### Rule 1: Single Statement

Any single operation (assignment, comparison, array read, HashMap get) = **O(1)**.

```java
int x = arr[i];          // O(1)
map.put(k, v);           // O(1) average
int max = Integer.MAX_VALUE; // O(1)
```

---

### Rule 2: Sequential Statements — Keep the Dominant

```java
// Phase 1: O(n) — build frequency map
Map<Integer, Integer> freq = new HashMap<>();
for (int x : nums) freq.merge(x, 1, Integer::sum);

// Phase 2: O(n log n) — sort keys
List<Integer> keys = new ArrayList<>(freq.keySet());
Collections.sort(keys);

// Phase 3: O(n) — scan sorted keys
for (int k : keys) System.out.println(k + ": " + freq.get(k));

// Total: O(n) + O(n log n) + O(n) = O(n log n)   ← dominant term
```

---

### Rule 3: Loops — Count Iterations × Body Cost

```java
// Single loop: O(n) × O(1) body = O(n)
for (int i = 0; i < n; i++) {
    sum += arr[i];          // O(1)
}

// Nested loop (both 0..n): O(n) × O(n) = O(n²)
for (int i = 0; i < n; i++) {
    for (int j = 0; j < n; j++) {
        process(arr[i], arr[j]);  // O(1)
    }
}

// Nested loop (inner starts at i): still O(n²) — triangle, n(n-1)/2 iterations
for (int i = 0; i < n; i++) {
    for (int j = i + 1; j < n; j++) {  // n/2 iterations on avg → O(n²) / 2 → O(n²)
        process(arr[i], arr[j]);
    }
}

// Loop that halves: O(log n) iterations
for (int i = n; i > 0; i /= 2) {
    process(i);             // O(1) body
}

// Loop with O(n) body inside O(n) outer loop = O(n²) — watch for this!
for (int i = 0; i < n; i++) {
    list.contains(something);  // O(n) — hidden nested loop!
}
```

---

### Rule 4: Recursion — Recurrence Relations

Write a recurrence T(n) = (number of recursive calls) × T(input size per call) + (work per call).

```java
// Binary search: T(n) = T(n/2) + O(1)
// → Master Theorem case 2 (a=1, b=2, f(n)=O(1), n^log_b(a)=n^0=1 = f(n))
// → T(n) = O(log n)
public int binarySearch(int[] arr, int target, int lo, int hi) {
    if (lo > hi) return -1;
    int mid = lo + (hi - lo) / 2;
    if (arr[mid] == target) return mid;
    if (arr[mid] < target) return binarySearch(arr, target, mid + 1, hi);
    return binarySearch(arr, target, lo, mid - 1);
}

// Merge sort: T(n) = 2T(n/2) + O(n)
// → Master Theorem case 2 (a=2, b=2, f(n)=O(n), n^log_2(2)=n = f(n))
// → T(n) = O(n log n)
public void mergeSort(int[] arr, int lo, int hi) {
    if (lo >= hi) return;
    int mid = lo + (hi - lo) / 2;
    mergeSort(arr, lo, mid);      // T(n/2)
    mergeSort(arr, mid + 1, hi);  // T(n/2)
    merge(arr, lo, mid, hi);      // O(n) merge step
}

// Simple recursion: T(n) = T(n-1) + O(1)
// → Linear recurrence → T(n) = O(n)
public int factorial(int n) {
    if (n == 0) return 1;
    return n * factorial(n - 1);  // T(n-1) + O(1)
}

// Fibonacci (naive): T(n) = 2T(n-1) + O(1) ≈ O(2ⁿ)
// Each call makes 2 calls, depth = n → 2⁰ + 2¹ + ... + 2ⁿ = O(2ⁿ)
public int fib(int n) {
    if (n <= 1) return n;
    return fib(n - 1) + fib(n - 2);  // exponential — never use without memoization
}
```

---

### Worked Analysis: Remove Duplicates from Sorted Array

```java
// Problem: given sorted array, remove duplicates in-place, return new length.
public int removeDuplicates(int[] nums) {
    if (nums.length == 0) return 0;     // O(1)
    int k = 1;                          // O(1)
    for (int i = 1; i < nums.length; i++) {  // O(n) iterations
        if (nums[i] != nums[i - 1]) {         // O(1) comparison
            nums[k++] = nums[i];              // O(1) write
        }
    }
    return k;                           // O(1)
}
// Total: O(n) time, O(1) auxiliary space
```

---

### Visualizing Recursion Trees

For recursive algorithms, drawing the call tree makes analysis immediate:

```
fib(4)  →  2 branches each time, depth = 4

                    fib(4)
                   /       \
             fib(3)         fib(2)
            /     \         /    \
         fib(2)  fib(1)  fib(1)  fib(0)
         /    \
      fib(1) fib(0)

Nodes at depth d: 2^d
Total nodes: 2^0 + 2^1 + ... + 2^4 = 2^5 - 1 = 31 ≈ O(2ⁿ)
```

---

## Try It Yourself

**Exercise:** Determine the time complexity of each function.

```java
// A
public boolean hasPairWithSum(int[] arr, int target) {
    Set<Integer> seen = new HashSet<>();
    for (int x : arr) {
        if (seen.contains(target - x)) return true;
        seen.add(x);
    }
    return false;
}

// B
public int[][] generateMatrix(int n) {
    int[][] mat = new int[n][n];
    for (int i = 0; i < n; i++)
        for (int j = 0; j < n; j++)
            mat[i][j] = i * n + j;
    return mat;
}

// C
public int power(int base, int exp) {
    if (exp == 0) return 1;
    if (exp % 2 == 0) {
        int half = power(base, exp / 2);
        return half * half;
    }
    return base * power(base, exp - 1);
}
```

<details>
<summary>Show answers</summary>

**A:** O(n) time, O(n) space — single pass over array with O(1) HashSet operations, but storing up to n elements.

**B:** O(n²) time, O(n²) space — two nested loops each running n times; the matrix itself is n×n.

**C:** O(log n) time. The recurrence is T(n) = T(n/2) + O(1) for even exponents — each call halves `exp`. The odd case (`base * power(base, exp-1)`) turns odd into even in one step, so it's still O(log n) total recursive calls, each doing O(1) work.

</details>

---

## Capstone Connection

In AlgoForge, every data structure implementation includes a Javadoc comment with `@TimeComplexity` and `@SpaceComplexity` annotations. Practice writing these on all your solutions — it forces you to analyze the code you just wrote, not just the code you planned.
