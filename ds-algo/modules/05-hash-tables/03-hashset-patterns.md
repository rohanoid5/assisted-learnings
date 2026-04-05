# 5.3 — HashSet Patterns

## Concept

A **HashSet** is a HashMap where only keys matter — the value is always a placeholder. It gives you O(1) average-case `add`, `contains`, and `remove`. The key interview insight: whenever a problem asks "have I seen this before?" or "does this element exist in some set?", a HashSet is likely the right tool.

---

## Deep Dive

### HashSet vs HashMap: When to Use Each

```
HashSet                               HashMap
──────────────────────────────────    ──────────────────────────────────
Membership query: "is X in set?"      Association: "what is the value of X?"
Deduplication: remove duplicates      Counting: "how many times did X appear?"
Set operations: union, intersect      Grouping: "which items share property X?"

Examples:                             Examples:
- Contains Duplicate (LC #217)        - Two Sum (LC #1)
- Longest Consecutive Sequence        - Group Anagrams (LC #49)
- Happy Number (cycle detection)      - Subarray Sum Equals K (LC #560)
```

---

### Set Operations in Java

```java
Set<Integer> a = new HashSet<>(Arrays.asList(1, 2, 3, 4));
Set<Integer> b = new HashSet<>(Arrays.asList(3, 4, 5, 6));

// Union: all elements in either set
Set<Integer> union = new HashSet<>(a);
union.addAll(b);           // {1, 2, 3, 4, 5, 6}

// Intersection: elements in both sets
Set<Integer> intersect = new HashSet<>(a);
intersect.retainAll(b);    // {3, 4}

// Difference: elements in a but not b
Set<Integer> diff = new HashSet<>(a);
diff.removeAll(b);         // {1, 2}
```

---

### Graph-Like Usage: Cycle Detection with a HashSet

When traversing sequences that might loop (linked list cycle, happy number), a HashSet of visited states detects cycles:

```
Happy Number algorithm:
  19 → 1² + 9² = 82
  82 → 8² + 2² = 68
  68 → 6² + 8² = 100
  100 → 1² + 0² + 0² = 1  ✓ HAPPY

Not-happy (cycles back to a seen value):
  4 → 16 → 37 → 58 → 89 → 145 → 42 → 20 → [4] ← seen! cycle
```

---

## Code Examples

### Longest Consecutive Sequence (LC #128)

```java
// Key insight: only start counting from the BEGINNING of a sequence
// "beginning" = n is in set but (n-1) is NOT in set
// This makes each sequence counted exactly once → O(n) overall

public int longestConsecutive(int[] nums) {
    Set<Integer> set = new HashSet<>();
    for (int n : nums) set.add(n);

    int longest = 0;
    for (int n : set) {
        if (!set.contains(n - 1)) {  // n is a sequence start
            int length = 1;
            while (set.contains(n + length)) length++;
            longest = Math.max(longest, length);
        }
    }
    return longest;
    // Time: O(n) — each number visited at most twice (once in outer loop, once in while)
    // Space: O(n)
}
```

### Contains Duplicate II (LC #219) — sliding window with HashSet

```java
// Question: are there indices i,j such that nums[i]==nums[j] and |i-j|<=k?
// Maintain a HashSet window of size k — if the incoming element is already there, found.

public boolean containsNearbyDuplicate(int[] nums, int k) {
    Set<Integer> window = new HashSet<>();
    for (int i = 0; i < nums.length; i++) {
        if (window.contains(nums[i])) return true;
        window.add(nums[i]);
        if (window.size() > k) window.remove(nums[i - k]);  // shrink window
    }
    return false;
    // Time: O(n), Space: O(min(n, k))
}
```

### Happy Number (LC #202)

```java
public boolean isHappy(int n) {
    Set<Integer> seen = new HashSet<>();
    while (n != 1) {
        n = sumOfSquares(n);
        if (seen.contains(n)) return false;  // cycle detected
        seen.add(n);
    }
    return true;
}

private int sumOfSquares(int n) {
    int sum = 0;
    while (n > 0) {
        int d = n % 10;
        sum += d * d;
        n /= 10;
    }
    return sum;
}
// Alternative (O(1) space): fast/slow pointer — same cycle detection idea as LC #141
```

---

## Try It Yourself

**Exercise:** Find the intersection of two integer arrays. Return an array of unique common elements.

```java
// Input:  nums1 = [1,2,2,1], nums2 = [2,2]  → [2]
// Input:  nums1 = [4,9,5],   nums2 = [9,4,9,8,4]  → [9,4] (order doesn't matter)

// Follow-up: What if each array is already sorted? (use two pointers instead)
// Follow-up: What if nums2 is very large (on disk)? (stream through, query set from nums1)

public int[] intersection(int[] nums1, int[] nums2) {
    // your code here
}
```

<details>
<summary>Solution</summary>

```java
public int[] intersection(int[] nums1, int[] nums2) {
    Set<Integer> set1 = new HashSet<>();
    for (int n : nums1) set1.add(n);

    Set<Integer> resultSet = new HashSet<>();
    for (int n : nums2) {
        if (set1.contains(n)) resultSet.add(n);
    }

    int[] result = new int[resultSet.size()];
    int i = 0;
    for (int n : resultSet) result[i++] = n;
    return result;
    // Time: O(n + m), Space: O(n) for set1 + O(min(n,m)) for result
}

// Sorted follow-up (O(1) extra space):
public int[] intersectionSorted(int[] nums1, int[] nums2) {
    Arrays.sort(nums1);
    Arrays.sort(nums2);
    List<Integer> res = new ArrayList<>();
    int i = 0, j = 0;
    while (i < nums1.length && j < nums2.length) {
        if (nums1[i] == nums2[j]) {
            if (res.isEmpty() || res.get(res.size()-1) != nums1[i])
                res.add(nums1[i]);
            i++; j++;
        } else if (nums1[i] < nums2[j]) i++;
        else j++;
    }
    return res.stream().mapToInt(Integer::intValue).toArray();
}
```

</details>

---

## Capstone Connection

In AlgoForge, `MyHashSet.java` is a thin wrapper around `MyHashMap` — the values are dummy `Boolean.TRUE` objects. This reinforces that a HashSet is conceptually and practically just a restricted HashMap. The `problems/hashtables/` directory contains the complete solutions for all problems in this topic.
