# Module 2 — Exercises

## Overview

These exercises implement the `DynamicArray<T>` library class and solve five real interview problems using Module 2 patterns. Complete them before moving to Module 3.

---

## Exercise 1: Implement DynamicArray\<T\>

**Goal:** Build a generic resizable array from scratch to understand `ArrayList` internals.

Create `src/main/java/com/algoforge/datastructures/linear/DynamicArray.java`:

```java
package com.algoforge.datastructures.linear;

import java.util.Arrays;
import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * A generic resizable array — mirrors Java's ArrayList from scratch.
 *
 * Time:  get/set O(1), add(tail) O(1) amortized, add(index) O(n), remove O(n)
 * Space: O(n)
 */
@SuppressWarnings("unchecked")
public class DynamicArray<T> implements Iterable<T> {

    private static final int DEFAULT_CAPACITY = 4;
    private Object[] data;
    private int size;

    public DynamicArray() {
        data = new Object[DEFAULT_CAPACITY];
        size = 0;
    }

    /** Return element at index in O(1). */
    public T get(int index) {
        checkIndex(index);
        return (T) data[index];
    }

    /** Set element at index in O(1), return old value. */
    public T set(int index, T value) {
        checkIndex(index);
        T old = (T) data[index];
        data[index] = value;
        return old;
    }

    /** Append to tail in O(1) amortized. */
    public void add(T value) {
        ensureCapacity();
        data[size++] = value;
    }

    /** Insert at arbitrary index in O(n). */
    public void add(int index, T value) {
        if (index < 0 || index > size) throw new IndexOutOfBoundsException();
        ensureCapacity();
        System.arraycopy(data, index, data, index + 1, size - index);
        data[index] = value;
        size++;
    }

    /** Remove element at index in O(n). */
    public T remove(int index) {
        checkIndex(index);
        T removed = (T) data[index];
        System.arraycopy(data, index + 1, data, index, size - index - 1);
        data[--size] = null;    // help GC
        return removed;
    }

    public int size() { return size; }
    public boolean isEmpty() { return size == 0; }

    private void ensureCapacity() {
        if (size == data.length) {
            data = Arrays.copyOf(data, data.length * 2);
        }
    }

    private void checkIndex(int index) {
        if (index < 0 || index >= size)
            throw new IndexOutOfBoundsException("Index " + index + ", size " + size);
    }

    @Override
    public Iterator<T> iterator() {
        return new Iterator<>() {
            int cursor = 0;
            public boolean hasNext() { return cursor < size; }
            public T next() {
                if (!hasNext()) throw new NoSuchElementException();
                return (T) data[cursor++];
            }
        };
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < size; i++) {
            sb.append(data[i]);
            if (i < size - 1) sb.append(", ");
        }
        return sb.append("]").toString();
    }
}
```

Now create the test:

```java
// src/test/java/com/algoforge/datastructures/linear/DynamicArrayTest.java
package com.algoforge.datastructures.linear;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class DynamicArrayTest {

    private DynamicArray<Integer> arr;

    @BeforeEach
    void setUp() { arr = new DynamicArray<>(); }

    @Test
    void add_and_get() {
        arr.add(10); arr.add(20); arr.add(30);
        assertThat(arr.get(0)).isEqualTo(10);
        assertThat(arr.get(2)).isEqualTo(30);
        assertThat(arr.size()).isEqualTo(3);
    }

    @Test
    void resize_triggers_on_overflow() {
        for (int i = 0; i < 100; i++) arr.add(i);  // exceeds initial capacity of 4
        assertThat(arr.size()).isEqualTo(100);
        assertThat(arr.get(99)).isEqualTo(99);
    }

    @Test
    void remove_shifts_elements() {
        arr.add(1); arr.add(2); arr.add(3);
        arr.remove(1);   // removes 2
        assertThat(arr.get(1)).isEqualTo(3);
        assertThat(arr.size()).isEqualTo(2);
    }

    @Test
    void addAtIndex_shifts_elements() {
        arr.add(1); arr.add(3);
        arr.add(1, 2);  // insert 2 at index 1
        assertThat(arr.get(1)).isEqualTo(2);
        assertThat(arr.get(2)).isEqualTo(3);
    }

    @Test
    void outOfBoundsThrows() {
        assertThatThrownBy(() -> arr.get(0)).isInstanceOf(IndexOutOfBoundsException.class);
    }
}
```

Run `mvn test` — all tests should pass.

---

## Exercise 2: Maximum Subarray (Kadane's Algorithm)

**Goal:** Find the contiguous subarray with the largest sum. This is the most important greedy/DP array problem.

```java
// src/main/java/com/algoforge/problems/arrays/MaxSubarray.java
package com.algoforge.problems.arrays;

/**
 * LC #53 — Maximum Subarray
 * Time: O(n), Space: O(1)
 */
public class MaxSubarray {
    public int maxSubArray(int[] nums) {
        int currentSum = nums[0];
        int maxSum = nums[0];

        for (int i = 1; i < nums.length; i++) {
            // Either extend current subarray or start fresh at nums[i]
            currentSum = Math.max(nums[i], currentSum + nums[i]);
            maxSum = Math.max(maxSum, currentSum);
        }
        return maxSum;
    }
}
```

Write a test that covers: all negatives, single element, mixed positive/negative. Run `mvn test`.

<details>
<summary>Show test</summary>

```java
@Test
void maxSubArray_mixedInput() {
    assertThat(new MaxSubarray().maxSubArray(new int[]{-2,1,-3,4,-1,2,1,-5,4})).isEqualTo(6);
    assertThat(new MaxSubarray().maxSubArray(new int[]{1})).isEqualTo(1);
    assertThat(new MaxSubarray().maxSubArray(new int[]{-1,-2,-3})).isEqualTo(-1);
}
```
</details>

---

## Exercise 3: Container With Most Water

**Goal:** Implement the two-pointer approach from Topic 2.3. Verify your intuition: why is it safe to move the shorter side?

```java
// src/main/java/com/algoforge/problems/arrays/ContainerWithMostWater.java
// LC #11 — Time: O(n), Space: O(1)
public class ContainerWithMostWater {
    public int maxArea(int[] height) {
        // Your implementation using converging two pointers
    }
}
```

Write tests for: increasing sequence, decreasing sequence, two equal elements.

<details>
<summary>Show solution + explanation</summary>

```java
public int maxArea(int[] height) {
    int left = 0, right = height.length - 1, max = 0;
    while (left < right) {
        int area = Math.min(height[left], height[right]) * (right - left);
        max = Math.max(max, area);
        if (height[left] <= height[right]) left++;
        else right--;
    }
    return max;
}
```

**Why move the shorter side?** The area is limited by `min(left, right)`. Moving the longer side cannot increase `min(left, right)` (it can only stay same or decrease), and `right - left` decreases. So the product definitely decreases or stays same. Moving the shorter side gives a *chance* to find a taller line that increases `min`. Hence moving the shorter side is the only potentially improving action.

</details>

---

## Exercise 4: Longest Substring Without Repeating Characters

**Goal:** Implement the variable sliding window from Topic 2.4.

```java
// src/main/java/com/algoforge/problems/arrays/LongestSubstringNoRepeat.java
// LC #3 — Time: O(n), Space: O(min(n, alphabet))
public class LongestSubstringNoRepeat {
    public int lengthOfLongestSubstring(String s) {
        // Use a HashMap storing char → last-seen index
        // Jump left pointer to lastSeen[c]+1 on duplicate
    }
}
```

Test cases: `""` → `0`, `"bbbbb"` → `1`, `"pwwkew"` → `3`, `"abcabcbb"` → `3`.

<details>
<summary>Show solution</summary>

```java
public int lengthOfLongestSubstring(String s) {
    Map<Character, Integer> lastSeen = new HashMap<>();
    int max = 0, left = 0;
    for (int right = 0; right < s.length(); right++) {
        char c = s.charAt(right);
        if (lastSeen.containsKey(c) && lastSeen.get(c) >= left) {
            left = lastSeen.get(c) + 1;
        }
        lastSeen.put(c, right);
        max = Math.max(max, right - left + 1);
    }
    return max;
}
```
</details>

---

## Exercise 5: Subarray Sum Equals K

**Goal:** Implement the prefix sum + HashMap pattern from Topic 2.5. This is the hardest problem in Module 2 — get it right.

```java
// src/main/java/com/algoforge/problems/arrays/SubarraySum.java
// LC #560 — Time: O(n), Space: O(n)
public class SubarraySum {
    public int subarraySum(int[] nums, int k) {
        // prefixSum frequency map
        // count += freq.getOrDefault(prefixSum - k, 0)
    }
}
```

Test: `[1,1,1], k=2` → `2` · `[1,2,3], k=3` → `2` · `[1,-1,1,-1], k=0` → `4`

<details>
<summary>Show solution</summary>

```java
public int subarraySum(int[] nums, int k) {
    Map<Integer, Integer> freq = new HashMap<>();
    freq.put(0, 1);
    int sum = 0, count = 0;
    for (int x : nums) {
        sum += x;
        count += freq.getOrDefault(sum - k, 0);
        freq.merge(sum, 1, Integer::sum);
    }
    return count;
}
```
</details>

---

## Capstone Checkpoint ✅

By completing these exercises you have:
- [x] `DynamicArray<T>` implemented and tested in AlgoForge
- [x] 4 classic array/string problems solved with full tests
- [x] Two pointer, sliding window, and prefix sum in your muscle memory

Run `mvn test` — all tests should be green before moving to Module 3.
