# 2.4 — Sliding Window Technique

## Concept

A sliding window maintains a contiguous subarray (or substring) that satisfies a condition. Instead of re-examining elements from scratch for each position — O(n²) — you *slide* the window by adding an element to the right and removing one from the left. Most sliding window problems run in **O(n) time**.

---

## Deep Dive

### Two Variants

```
Fixed-size window (k=3):
arr = [2, 1, 5, 1, 3, 2]
      [──────]              sum=8
         [──────]           sum=7
            [──────]        sum=9  ← max
               [──────]    sum=6

Window moves right: add arr[right], remove arr[right - k]
No shrink needed — window size is constant.

Variable-size window:
s = "aabcbbb"  find longest substring with at most 2 distinct chars
    [a]                 1 distinct
    [aa]                1 distinct
    [aab]               2 distinct ← valid
    [aabc]              3 distinct ← INVALID → shrink from left
     [abc]              3 distinct ← still invalid
      [bc]              2 distinct
      [bcb]             2 distinct
      [bcbb]            2 distinct
      [bcbbb]           2 distinct ← window = 5, answer!

When to shrink: when constraint violated, move left pointer right.
```

---

### The Sliding Window Template

```java
// Template for VARIABLE-size window
int left = 0, maxLen = 0;
Map<Character, Integer> window = new HashMap<>();  // or frequency array

for (int right = 0; right < s.length(); right++) {
    // 1. EXPAND: add s[right] to window
    char c = s.charAt(right);
    window.merge(c, 1, Integer::sum);

    // 2. SHRINK: while constraint violated, remove s[left]
    while (/* constraint violated */) {
        char leftChar = s.charAt(left);
        window.merge(leftChar, -1, Integer::sum);
        if (window.get(leftChar) == 0) window.remove(leftChar);
        left++;
    }

    // 3. UPDATE answer (window is now valid)
    maxLen = Math.max(maxLen, right - left + 1);
}
return maxLen;
```

---

### Fixed-Size Window Template

```java
// Template for FIXED-size window of k
int windowSum = 0;
// Build initial window
for (int i = 0; i < k; i++) windowSum += arr[i];

int maxSum = windowSum;
for (int i = k; i < arr.length; i++) {
    windowSum += arr[i];           // add new right element
    windowSum -= arr[i - k];       // remove leftmost element
    maxSum = Math.max(maxSum, windowSum);
}
return maxSum;
```

---

## Code Examples

### Example 1: Longest Substring Without Repeating Characters

```java
// "abcabcbb" → 3 ("abc")
// Variable window: shrink whenever duplicate appears

public int lengthOfLongestSubstring(String s) {
    Map<Character, Integer> lastSeen = new HashMap<>();  // char → last index
    int maxLen = 0;
    int left = 0;

    for (int right = 0; right < s.length(); right++) {
        char c = s.charAt(right);
        // If c was seen within [left..right-1], jump left past its last occurrence
        if (lastSeen.containsKey(c) && lastSeen.get(c) >= left) {
            left = lastSeen.get(c) + 1;
        }
        lastSeen.put(c, right);
        maxLen = Math.max(maxLen, right - left + 1);
    }
    return maxLen;
    // Time: O(n), Space: O(min(n, charset_size))
}
```

### Example 2: Minimum Window Substring

```java
// Find the smallest window in s that contains all characters of t.
// "ADOBECODEBANC", t="ABC" → "BANC"
// Variable window: shrink as soon as all t chars are covered.

public String minWindow(String s, String t) {
    if (s.isEmpty() || t.isEmpty()) return "";

    Map<Character, Integer> need = new HashMap<>();
    for (char c : t.toCharArray()) need.merge(c, 1, Integer::sum);

    int required = need.size();  // distinct chars needed
    int formed = 0;              // distinct chars in window with required frequency
    Map<Character, Integer> window = new HashMap<>();

    int left = 0;
    int minLen = Integer.MAX_VALUE;
    String result = "";

    for (int right = 0; right < s.length(); right++) {
        char c = s.charAt(right);
        window.merge(c, 1, Integer::sum);
        if (need.containsKey(c) && window.get(c).intValue() == need.get(c).intValue()) {
            formed++;
        }

        // Shrink: we have all required chars, try to minimize
        while (formed == required) {
            if (right - left + 1 < minLen) {
                minLen = right - left + 1;
                result = s.substring(left, right + 1);
            }
            char leftChar = s.charAt(left);
            window.merge(leftChar, -1, Integer::sum);
            if (need.containsKey(leftChar) && window.get(leftChar) < need.get(leftChar)) {
                formed--;
            }
            left++;
        }
    }
    return result;
    // Time: O(|s| + |t|), Space: O(|s| + |t|)
}
```

---

## Try It Yourself

**Exercise:** Find the maximum sum of any subarray of exactly **k** elements.

Input: `arr = [2, 1, 5, 1, 3, 2]`, `k = 3` → `9` (subarray `[5,1,3]`)

```java
public int maxSumSubarrayK(int[] arr, int k) {
    // Use the fixed-size window template
}
```

<details>
<summary>Show solution</summary>

```java
public int maxSumSubarrayK(int[] arr, int k) {
    if (arr.length < k) return -1;

    // Build initial window
    int windowSum = 0;
    for (int i = 0; i < k; i++) windowSum += arr[i];

    int maxSum = windowSum;
    for (int i = k; i < arr.length; i++) {
        windowSum += arr[i];        // add right
        windowSum -= arr[i - k];   // remove left (element k steps behind)
        maxSum = Math.max(maxSum, windowSum);
    }
    return maxSum;
    // Time: O(n), Space: O(1)
}
```

The key: instead of summing k elements from scratch each time, maintain a running sum and swap out one element per step — O(1) per position instead of O(k).

</details>

---

## Capstone Connection

Sliding window is one of the highest-frequency interview patterns. AlgoForge has eight sliding window problems in `src/main/java/com/algoforge/problems/arrays/`. Master the template and you can solve any of them within minutes.
