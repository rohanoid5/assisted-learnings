# Module 1 — Exercises

## Overview

These exercises reinforce complexity analysis and set up your AlgoForge project. Complete them before moving to Module 2.

---

## Exercise 1: Set Up AlgoForge

**Goal:** Get the AlgoForge capstone project compiling and your first test passing.

1. Navigate to the capstone directory:
   ```bash
   cd ds-algo/capstone/algoforge
   ```

2. Build the project and download dependencies:
   ```bash
   mvn clean compile
   ```
   Expected output: `BUILD SUCCESS`

3. Run the existing smoke test:
   ```bash
   mvn test
   ```
   Expected: `Tests run: 1, Failures: 0, Errors: 0`

4. Run the complexity benchmark:
   ```bash
   mvn exec:java -Dexec.mainClass="com.algoforge.ComplexityBenchmark"
   ```
   Observe the output — note how O(n²) bubble sort time grows much faster than O(n log n) `Arrays.sort`.

**Verification:** Both `mvn test` and `mvn exec:java` complete without errors.

---

## Exercise 2: Complexity Analysis — 5 Code Snippets

**Goal:** Practice analyzing complexity mechanically using the four rules from Topic 1.4.

Analyze each snippet. Write: Time complexity, Space complexity, and one sentence of explanation.

```java
// Snippet 1
public int sumMatrix(int[][] matrix) {
    int sum = 0;
    for (int[] row : matrix) {
        for (int val : row) sum += val;
    }
    return sum;
}
// matrix is n × m. Write complexity in terms of n and m.

// Snippet 2
public List<Integer> twoSumAllPairs(int[] nums, int target) {
    List<Integer> result = new ArrayList<>();
    for (int i = 0; i < nums.length; i++)
        for (int j = i + 1; j < nums.length; j++)
            if (nums[i] + nums[j] == target)
                result.add(i);
    return result;
}

// Snippet 3
public int search(int[] sortedArr, int target) {
    int lo = 0, hi = sortedArr.length - 1;
    while (lo <= hi) {
        int mid = lo + (hi - lo) / 2;
        if (sortedArr[mid] == target) return mid;
        if (sortedArr[mid] < target) lo = mid + 1;
        else hi = mid - 1;
    }
    return -1;
}

// Snippet 4
public List<String> allSubstrings(String s) {
    List<String> result = new ArrayList<>();
    for (int i = 0; i < s.length(); i++)
        for (int j = i + 1; j <= s.length(); j++)
            result.add(s.substring(i, j));
    return result;
}
// Note: String.substring() in Java is O(j - i) — NOT O(1)

// Snippet 5
public int countBits(int n) {
    int count = 0;
    while (n > 0) {
        count += (n & 1);
        n >>= 1;
    }
    return count;
}
// n here is the numeric VALUE of the integer, not array length.
```

<details>
<summary>Show answers</summary>

**Snippet 1: sumMatrix**
- Time: **O(n × m)** — two nested loops, outer runs n times, inner runs m times
- Space: **O(1)** auxiliary — only `sum` variable

**Snippet 2: twoSumAllPairs**
- Time: **O(n²)** — nested loop, ~n(n-1)/2 iterations
- Space: **O(n²)** worst case — result list can contain O(n²) pairs (if every pair sums to target)

**Snippet 3: search (Binary Search)**
- Time: **O(log n)** — halves search space each iteration
- Space: **O(1)** auxiliary — iterative, no recursion stack

**Snippet 4: allSubstrings**
- Time: **O(n³)** — O(n²) pairs (i,j) × O(n) substring copy per pair
- Space: **O(n³)** — result stores O(n²) substrings averaging O(n) length each
- This is a **common trap**: `substring()` is not free!

**Snippet 5: countBits**
- Time: **O(log n)** — `n >>= 1` halves the value each iteration; takes log₂(n) iterations to reach 0
- Space: **O(1)**
- Important: here n is the *value*, not an array length — O(log n) in terms of value = O(B) where B is the number of bits

</details>

---

## Exercise 3: Empirical Verification — Observe Growth Rates

**Goal:** Add a new benchmark to `ComplexityBenchmark.java` that measures an O(n²) vs O(n) solution to the same problem.

**Problem:** Given an array, check if any two elements sum to `target`.

1. Add this method to `ComplexityBenchmark.java`:

```java
private static void benchmarkTwoSum() {
    System.out.println("\n--- Two Sum: O(n²) vs O(n) ---");
    int[] sizes = {1_000, 5_000, 10_000, 50_000};
    Random rng = new Random(42);

    for (int n : sizes) {
        int[] arr = rng.ints(n, 0, n * 2).toArray();
        int target = n;  // unlikely to be found — forces full scan

        // O(n²) brute force
        long t1 = System.nanoTime();
        boolean found1 = hasPairBrute(arr, target);
        long elapsed1 = System.nanoTime() - t1;

        // O(n) HashSet
        long t2 = System.nanoTime();
        boolean found2 = hasPairHash(arr, target);
        long elapsed2 = System.nanoTime() - t2;

        System.out.printf("  n=%-6d  Brute: %,10d ns  Hash: %,8d ns  speedup: %.0fx\n",
                n, elapsed1, elapsed2, (double) elapsed1 / elapsed2);
    }
}

private static boolean hasPairBrute(int[] arr, int target) {
    for (int i = 0; i < arr.length; i++)
        for (int j = i + 1; j < arr.length; j++)
            if (arr[i] + arr[j] == target) return true;
    return false;
}

private static boolean hasPairHash(int[] arr, int target) {
    Set<Integer> seen = new HashSet<>();
    for (int x : arr) {
        if (seen.contains(target - x)) return true;
        seen.add(x);
    }
    return false;
}
```

2. Call `benchmarkTwoSum()` from `main()`.
3. Run: `mvn exec:java -Dexec.mainClass="com.algoforge.ComplexityBenchmark"`
4. Record the speedup factor at each n. For n=50,000 you should see 100-1000x speedup.

**Verification:** The speedup factor grows roughly linearly with n (each doubling of n roughly doubles the speedup — because O(n²)/O(n) = O(n)).

---

## Exercise 4: Write the Java Collections Cheat Sheet From Memory

**Goal:** Memorize the complexity table from Topic 1.7 by reproducing it.

Without looking at your notes, fill in this table:

| Operation | ArrayList | HashMap | TreeMap | PriorityQueue |
|-----------|-----------|---------|---------|---------------|
| get(index)/get(key) | | | | — |
| add/put | | | | |
| remove | | | | |
| contains/containsKey | | | | |
| peek | — | — | O(log n) | |
| poll | — | — | O(log n) | |

Check your answers against Topic 1.7. Repeat until you get it right from memory — you *will* be asked about this in interviews.

---

## Exercise 5: Complexity Annotation Practice

**Goal:** Write proper complexity Javadoc on three methods.

Add these methods to a new file `src/main/java/com/algoforge/problems/arrays/TwoSum.java` with correct complexity annotations:

```java
package com.algoforge.problems.arrays;

import java.util.HashMap;
import java.util.Map;

public class TwoSum {

    /**
     * Brute force: check all pairs.
     *
     * Time: O(???)
     * Space: O(???)
     */
    public int[] twoSumBrute(int[] nums, int target) {
        for (int i = 0; i < nums.length; i++)
            for (int j = i + 1; j < nums.length; j++)
                if (nums[i] + nums[j] == target) return new int[]{i, j};
        return new int[]{};
    }

    /**
     * HashMap: single pass.
     *
     * Time: O(???)
     * Space: O(???)
     */
    public int[] twoSumHash(int[] nums, int target) {
        Map<Integer, Integer> map = new HashMap<>();
        for (int i = 0; i < nums.length; i++) {
            int complement = target - nums[i];
            if (map.containsKey(complement)) return new int[]{map.get(complement), i};
            map.put(nums[i], i);
        }
        return new int[]{};
    }
}
```

Fill in the `???` annotations, then add a matching test file:

```java
// src/test/java/com/algoforge/problems/arrays/TwoSumTest.java
package com.algoforge.problems.arrays;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class TwoSumTest {

    private final TwoSum solution = new TwoSum();

    @Test
    void brute_findsCorrectIndices() {
        assertThat(solution.twoSumBrute(new int[]{2, 7, 11, 15}, 9)).containsExactly(0, 1);
        assertThat(solution.twoSumBrute(new int[]{3, 2, 4}, 6)).containsExactly(1, 2);
    }

    @Test
    void hash_findsCorrectIndices() {
        assertThat(solution.twoSumHash(new int[]{2, 7, 11, 15}, 9)).containsExactly(0, 1);
        assertThat(solution.twoSumHash(new int[]{3, 3}, 6)).containsExactly(0, 1);
    }
}
```

Run `mvn test` — both tests should pass.

<details>
<summary>Show complexity answers</summary>

**twoSumBrute:**
- Time: **O(n²)** — nested loops
- Space: **O(1)** auxiliary

**twoSumHash:**
- Time: **O(n)** — single pass, O(1) HashMap operations
- Space: **O(n)** — HashMap stores up to n entries

</details>

---

## Capstone Checkpoint ✅

By completing these exercises you have:
- [x] AlgoForge compiling and tests passing
- [x] `ComplexityBenchmark` running with empirical proof of growth rates
- [x] First problem class (`TwoSum`) with test coverage
- [x] Ability to analyze any code snippet for time and space complexity

Move to Module 2 when all five exercises are complete.
