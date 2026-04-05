# Module 05: Hash Tables — Exercises

## Overview

These exercises cement the frequency counting, grouping, complement lookup, and design patterns from all four topic files. Attempt each problem independently before revealing the solution.

---

## Exercise 1: Two Sum (LC #1)

**Goal:** Given an array of integers `nums` and an integer `target`, return the indices of the two numbers that add up to `target`. Assume exactly one solution exists.

```
Input:  nums = [2, 7, 11, 15], target = 9  →  Output: [0, 1]
Input:  nums = [3, 2, 4],      target = 6  →  Output: [1, 2]
Input:  nums = [3, 3],         target = 6  →  Output: [0, 1]
```

1. Implement the O(n) one-pass HashMap solution.
2. Walk through the example `[3, 2, 4], target = 6` with pencil: after processing 3, the map is `{3:0}`. Processing 2: complement is 4, not in map, add `{3:0, 2:1}`. Processing 4: complement is 2, found at index 1 → `[1, 2]`. ✓
3. Add a JUnit test that covers: normal case, same element twice (`[3,3]`), negative numbers (`[-1,-2,-3,-4,-5], target=-8`).

<details>
<summary>Solution</summary>

```java
public int[] twoSum(int[] nums, int target) {
    Map<Integer, Integer> seen = new HashMap<>(); // value → index
    for (int i = 0; i < nums.length; i++) {
        int complement = target - nums[i];
        if (seen.containsKey(complement)) {
            return new int[]{seen.get(complement), i};
        }
        seen.put(nums[i], i);
    }
    throw new IllegalArgumentException("No solution found");
}

@Test
void testTwoSum() {
    assertArrayEquals(new int[]{0, 1}, twoSum(new int[]{2, 7, 11, 15}, 9));
    assertArrayEquals(new int[]{1, 2}, twoSum(new int[]{3, 2, 4}, 6));
    assertArrayEquals(new int[]{0, 1}, twoSum(new int[]{3, 3}, 6));
    assertArrayEquals(new int[]{1, 4}, twoSum(new int[]{-1, -2, -3, -4, -5}, -8)); // -2 + -6 nope
    // -2 + -6 = -8? No: -2 is at 1, -6 doesn't exist. Correct: -3 (idx 2) + -5 (idx 4) = -8
    assertArrayEquals(new int[]{2, 4}, twoSum(new int[]{-1, -2, -3, -4, -5}, -8));
}
```

</details>

---

## Exercise 2: Group Anagrams (LC #49)

**Goal:** Given an array of strings, group them such that all anagrams are together.

```
Input:  ["eat","tea","tan","ate","nat","bat"]
Output: [["bat"],["nat","tan"],["ate","eat","tea"]]  (order within groups doesn't matter)
```

1. Implement using sorted-string as canonical key.
2. Consider an alternative: a 26-element frequency count as the key (faster for long strings with repeated chars).
3. Write a test that includes single-character strings, empty strings, and strings with duplicate characters.

<details>
<summary>Solution</summary>

```java
// Approach A: sorted string as key — O(n * k log k)
public List<List<String>> groupAnagrams(String[] strs) {
    Map<String, List<String>> map = new HashMap<>();
    for (String s : strs) {
        char[] chars = s.toCharArray();
        Arrays.sort(chars);
        String key = new String(chars);
        map.computeIfAbsent(key, k -> new ArrayList<>()).add(s);
    }
    return new ArrayList<>(map.values());
}

// Approach B: frequency array as key — O(n * k), better for long strings
public List<List<String>> groupAnagrams2(String[] strs) {
    Map<String, List<String>> map = new HashMap<>();
    for (String s : strs) {
        int[] count = new int[26];
        for (char c : s.toCharArray()) count[c - 'a']++;
        // Build key like "1#0#0#...#2#..." to avoid collisions between [1,10] and [11,0]
        StringBuilder sb = new StringBuilder();
        for (int n : count) sb.append(n).append('#');
        String key = sb.toString();
        map.computeIfAbsent(key, k -> new ArrayList<>()).add(s);
    }
    return new ArrayList<>(map.values());
}
```

</details>

---

## Exercise 3: Longest Consecutive Sequence (LC #128)

**Goal:** Given an unsorted array of integers, return the length of the longest consecutive elements sequence. Must run in O(n).

```
Input:  [100, 4, 200, 1, 3, 2]   →  4   (sequence: 1,2,3,4)
Input:  [0, 3, 7, 2, 5, 8, 4, 6, 0, 1]  →  9
```

1. Build a HashSet from the array.
2. Only start counting from sequence starts (where `n-1` is NOT in the set).
3. Trace through `[100, 4, 200, 1, 3, 2]`: starts are 100 (len 1), 200 (len 1), 1 (len 4 → 1,2,3,4). Answer: 4.

<details>
<summary>Solution</summary>

```java
public int longestConsecutive(int[] nums) {
    Set<Integer> set = new HashSet<>();
    for (int n : nums) set.add(n);

    int longest = 0;
    for (int n : set) {
        if (set.contains(n - 1)) continue;  // not a start point, skip
        int len = 1;
        while (set.contains(n + len)) len++;
        longest = Math.max(longest, len);
    }
    return longest;
    // Why O(n)? Each number is "visited" in the while loop at most once — only when
    // we enter from its strictly-smallest start. Total while-loop iterations ≤ n.
}

@Test
void testLongestConsecutive() {
    assertEquals(4, longestConsecutive(new int[]{100, 4, 200, 1, 3, 2}));
    assertEquals(9, longestConsecutive(new int[]{0, 3, 7, 2, 5, 8, 4, 6, 0, 1}));
    assertEquals(1, longestConsecutive(new int[]{1}));
    assertEquals(0, longestConsecutive(new int[]{}));
}
```

</details>

---

## Exercise 4: Subarray Sum Equals K (LC #560)

**Goal:** Given an integer array `nums` and an integer `k`, return the total number of subarrays whose sum equals `k`.

```
Input:  nums = [1, 1, 1], k = 2  →  2
Input:  nums = [1, 2, 3], k = 3  →  2   ([1,2] and [3])
```

1. Implement the prefix sum + HashMap approach in O(n).
2. Trace through `[1,1,1], k=2`:
   - Start: `{0:1}`, sum=0
   - i=0: sum=1, needed=-1 (not in map), add {0:1, 1:1}
   - i=1: sum=2, needed=0 (in map, count=1), count=1, add {0:1, 1:1, 2:1}
   - i=2: sum=3, needed=1 (in map, count=1), count=2 ✓
3. Test with negative numbers: `[-1, -1, 1], k=0` → 1.

<details>
<summary>Solution</summary>

```java
public int subarraySum(int[] nums, int k) {
    Map<Integer, Integer> prefixCount = new HashMap<>();
    prefixCount.put(0, 1);  // empty prefix with sum 0 exists once

    int count = 0, sum = 0;
    for (int num : nums) {
        sum += num;
        // If (sum - k) was a previous prefix sum, then subarray from there to here has sum k
        count += prefixCount.getOrDefault(sum - k, 0);
        prefixCount.merge(sum, 1, Integer::sum);
    }
    return count;
}

@Test
void testSubarraySum() {
    assertEquals(2, subarraySum(new int[]{1, 1, 1}, 2));
    assertEquals(2, subarraySum(new int[]{1, 2, 3}, 3));
    assertEquals(1, subarraySum(new int[]{-1, -1, 1}, 0));
    assertEquals(3, subarraySum(new int[]{1, -1, 1, -1, 1}, 0));
}
```

</details>

---

## Exercise 5: Design HashMap from Scratch (LC #706)

**Goal:** Implement `MyHashMap` without using any built-in hash table libraries. Support `put(key, value)`, `get(key)`, and `remove(key)`. Keys and values are non-negative integers.

1. Use an array of `LinkedList<int[]>` as buckets.
2. Use `capacity = 1009` (a prime number — reduces collisions more than a power of 2 for the given key distribution).
3. In `put`, iterate the bucket's list first — if key exists, update it. Otherwise append.
4. Verify with: `put(1,1); put(2,2); get(1)→1; get(3)→-1; put(2,1); get(2)→1; remove(2); get(2)→-1`.

<details>
<summary>Solution</summary>

```java
class MyHashMap {
    private static final int CAPACITY = 1009; // prime for better distribution
    private LinkedList<int[]>[] table;

    @SuppressWarnings("unchecked")
    public MyHashMap() {
        table = new LinkedList[CAPACITY];
    }

    private int idx(int key) { return key % CAPACITY; }

    public void put(int key, int value) {
        int i = idx(key);
        if (table[i] == null) table[i] = new LinkedList<>();
        for (int[] pair : table[i]) {
            if (pair[0] == key) { pair[1] = value; return; }
        }
        table[i].add(new int[]{key, value});
    }

    public int get(int key) {
        int i = idx(key);
        if (table[i] == null) return -1;
        for (int[] pair : table[i]) {
            if (pair[0] == key) return pair[1];
        }
        return -1;
    }

    public void remove(int key) {
        int i = idx(key);
        if (table[i] != null) table[i].removeIf(pair -> pair[0] == key);
    }
}
```

</details>

---

## Capstone Checkpoint ✅

By completing these exercises you have:
- [x] Solved the canonical **Two Sum** in O(n) — the most common hash table pattern
- [x] Applied **grouping by canonical key** (Group Anagrams)
- [x] Used the **HashSet start-of-sequence trick** for O(n) consecutive sequence
- [x] Built the **prefix sum + frequency map** pattern for subarray problems
- [x] Implemented a **HashMap from scratch** with chaining

Add all five solutions to `capstone/algoforge/src/main/java/com/algoforge/problems/hashtables/`.
