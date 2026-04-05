# 1.3 — Time vs Space Complexity

## Concept

Every algorithm makes a trade-off between **time** (how long it runs) and **space** (how much memory it uses). Understanding this trade-off — and being able to articulate it explicitly in an interview — is what separates good answers from great ones.

---

## Deep Dive

### Two Independent Dimensions

```
        SPACE
          │
   O(n)   │  ████████████  Memoized recursion (fast + uses n extra memory)
          │  ████████████
   O(log  │       ████████  Iterative + stack simulation
   n)     │       ████████
          │
   O(1)   │           ████  In-place algorithms (slow or fast with no extra memory)
          │           ████
          └────────────────────────────────────────────────────────  TIME
                    O(n²)        O(n log n)       O(n)
```

Neither dimension is inherently better. The *right* trade-off depends on:
1. The constraints of the problem (memory limit? time limit?)
2. The input size (n = 10 vs n = 10⁷)
3. The production environment (embedded device vs. distributed cloud system)

---

### Auxiliary Space vs Total Space

**Total space = input space + auxiliary space**

When we say an algorithm has O(1) space complexity, we almost always mean **O(1) auxiliary space** — the extra memory used beyond the input itself.

```java
// Total space O(n + n) = O(n) — array input + equal-size copy
// Auxiliary space: O(n)
public int[] copyAndSort(int[] arr) {
    int[] copy = Arrays.copyOf(arr, arr.length); // O(n) extra
    Arrays.sort(copy);                            // O(log n) stack
    return copy;
}

// Auxiliary space: O(1) — sorts in-place, only a few variables
public void sortInPlace(int[] arr) {
    Arrays.sort(arr);  // Dual-Pivot Quicksort, O(log n) stack space
}
```

---

### The Three Classic Trade-offs

#### 1. Cache/Memoize (Space for Time)

```java
// Without cache: recomputes Fibonacci(n) exponentially — O(2ⁿ) time, O(n) stack
public int fibSlow(int n) {
    if (n <= 1) return n;
    return fibSlow(n - 1) + fibSlow(n - 2);  // 2 recursive calls each time
}

// With cache: each subproblem solved once — O(n) time, O(n) space
public int fibFast(int n, int[] memo) {
    if (n <= 1) return n;
    if (memo[n] != 0) return memo[n];         // already solved
    return memo[n] = fibFast(n - 1, memo) + fibFast(n - 2, memo);
}

// Space-optimized: O(n) time, O(1) space (only need last 2 values)
public int fibOptimal(int n) {
    if (n <= 1) return n;
    int prev2 = 0, prev1 = 1;
    for (int i = 2; i <= n; i++) {
        int curr = prev1 + prev2;
        prev2 = prev1;
        prev1 = curr;
    }
    return prev1;
}
```

#### 2. Precompute (Space for Time)

```java
// Query: is nums[i..j] sum divisible by k?
// Without precompute: O(n) per query
public int rangeSum(int[] nums, int i, int j) {
    int sum = 0;
    for (int x = i; x <= j; x++) sum += nums[x];  // O(n) per call
    return sum;
}

// With prefix sum: O(n) precompute, O(1) per query — O(n) space
public static class PrefixSum {
    private int[] prefix;
    public PrefixSum(int[] nums) {
        prefix = new int[nums.length + 1];
        for (int i = 0; i < nums.length; i++)
            prefix[i + 1] = prefix[i] + nums[i];
    }
    public int query(int i, int j) {
        return prefix[j + 1] - prefix[i];  // O(1)
    }
}
```

#### 3. In-Place (Time for Space)

```java
// Reverse an array
// With extra array: O(n) time, O(n) space
public int[] reverseNew(int[] arr) {
    int[] result = new int[arr.length];
    for (int i = 0; i < arr.length; i++)
        result[arr.length - 1 - i] = arr[i];
    return result;
}

// In-place: O(n) time, O(1) space
public void reverseInPlace(int[] arr) {
    int left = 0, right = arr.length - 1;
    while (left < right) {
        int tmp = arr[left];
        arr[left++] = arr[right];
        arr[right--] = tmp;
    }
}
```

---

### Identifying the Dominant Term

When an algorithm has multiple phases, the **dominant term** determines overall complexity. Drop constants and lower-order terms:

```
O(n² + n)          → O(n²)        [ n² dominates n ]
O(n log n + n)     → O(n log n)   [ n log n dominates n ]
O(2n + 3)          → O(n)         [ drop constants ]
O(n + m)           → O(n + m)     [ two independent inputs, keep both ]
O(n · m)           → O(n · m)     [ nested loop over two different sizes ]
```

---

## Try It Yourself

**Exercise:** For each function below, state time complexity AND auxiliary space complexity.

```java
// A
public List<Integer> twoSum(int[] nums, int target) {
    Map<Integer, Integer> map = new HashMap<>();
    for (int i = 0; i < nums.length; i++) {
        int complement = target - nums[i];
        if (map.containsKey(complement)) return List.of(map.get(complement), i);
        map.put(nums[i], i);
    }
    return List.of();
}

// B
public boolean isPalindrome(String s) {
    int l = 0, r = s.length() - 1;
    while (l < r) {
        if (s.charAt(l) != s.charAt(r)) return false;
        l++; r--;
    }
    return true;
}

// C
public int[][] merge(int[][] intervals) {
    Arrays.sort(intervals, (a, b) -> a[0] - b[0]);
    List<int[]> result = new ArrayList<>();
    // ... (assume O(n) pass after sorting)
    return result.toArray(new int[0][]);
}
```

<details>
<summary>Show answers</summary>

**A — Two Sum:**
- Time: **O(n)** — single pass, O(1) HashMap operations
- Space: **O(n)** auxiliary — HashMap stores at most n entries

**B — isPalindrome:**
- Time: **O(n)** — two pointers, at most n/2 iterations
- Space: **O(1)** auxiliary — only two integer variables

**C — Merge Intervals:**
- Time: **O(n log n)** — dominated by the sort; the merge pass is O(n)
- Space: **O(n)** auxiliary — output list holds at most n intervals; `Arrays.sort` uses O(log n) stack

</details>

---

## Capstone Connection

Every implementation in AlgoForge has a comment block specifying time and space complexity. Make this a habit: before writing any method, write the expected complexity as a comment, then verify it matches your analysis after coding.
