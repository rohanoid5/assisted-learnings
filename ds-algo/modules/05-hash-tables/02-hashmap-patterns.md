# 5.2 — HashMap Patterns

## Concept

The HashMap's O(1) lookup makes it the go-to tool for a core family of interview problems: **frequency counting**, **grouping by computed key**, and the **complement look-up** (two-sum family). Recognizing which pattern applies is more than half the battle — the implementation follows naturally.

---

## Deep Dive

### Pattern 1: Frequency Counting

Count how many times each element appears. Appears in: anagram detection, majority element, top-K frequent, first unique character.

```
Template:
  Map<T, Integer> freq = new HashMap<>();
  for (T item : input) freq.merge(item, 1, Integer::sum);

Equivalent one-liners:
  freq.put(k, freq.getOrDefault(k, 0) + 1);   // explicit
  freq.merge(k, 1, Integer::sum);              // compact
  freq.compute(k, (key, v) -> v == null ? 1 : v + 1);  // compute API
```

---

### Pattern 2: Grouping by Canonical Key

Transform each element into a canonical form, then bucket elements that share the same canonical form.

```
Group anagrams: canonical key = sorted characters
  "eat" → "aet"
  "tea" → "aet"   ← same group as "eat"
  "tan" → "ant"

Map<String, List<String>> groups = new HashMap<>();
for (String word : words) {
    char[] chars = word.toCharArray();
    Arrays.sort(chars);
    String key = new String(chars);
    groups.computeIfAbsent(key, k -> new ArrayList<>()).add(word);
}
```

---

### Pattern 3: Complement / Seen-So-Far

For each element, check if the element "needed" to complete a pair has already been seen.

```
Two Sum: need target - nums[i]
Two Sum II (sorted): use two pointers instead
3Sum: fix one element, two-pointer for the rest
Subarray sum = k: prefix sum + frequency map
```

---

### Pattern 4: Sliding Window + HashMap

Track element counts in the current window. Expand right, shrink left when constraint violated.

```
Minimum window substring: need all chars from t within s
  - freq map of t's chars
  - sliding window over s, decrement when char found
  - shrink window when all t chars satisfied
```

---

## Code Examples

### Group Anagrams (LC #49)

```java
public List<List<String>> groupAnagrams(String[] strs) {
    Map<String, List<String>> map = new HashMap<>();
    for (String s : strs) {
        char[] chars = s.toCharArray();
        Arrays.sort(chars);
        String key = new String(chars);  // canonical sorted form
        map.computeIfAbsent(key, k -> new ArrayList<>()).add(s);
    }
    return new ArrayList<>(map.values());
    // Time: O(n * k log k) where k = max string length
    // Space: O(n * k)
}
```

### Subarray Sum Equals K (LC #560)

```java
// Key insight: sum[i..j] = prefixSum[j] - prefixSum[i-1]
// We want prefixSum[j] - prefixSum[i-1] == k
// → We want prefixSum[i-1] == prefixSum[j] - k
// → Check if (currentSum - k) has been seen as a prefix sum before

public int subarraySum(int[] nums, int k) {
    Map<Integer, Integer> prefixCount = new HashMap<>();
    prefixCount.put(0, 1);  // empty prefix has sum 0 (count = 1)

    int count = 0, sum = 0;
    for (int num : nums) {
        sum += num;
        int needed = sum - k;
        count += prefixCount.getOrDefault(needed, 0);
        prefixCount.merge(sum, 1, Integer::sum);
    }
    return count;
    // Time: O(n), Space: O(n)
}
```

### Top K Frequent Elements (LC #347)

```java
public int[] topKFrequent(int[] nums, int k) {
    // Step 1: frequency map
    Map<Integer, Integer> freq = new HashMap<>();
    for (int n : nums) freq.merge(n, 1, Integer::sum);

    // Step 2: bucket sort by frequency (O(n) instead of O(n log n) heap)
    // freq can be at most nums.length
    List<Integer>[] buckets = new List[nums.length + 1];
    for (Map.Entry<Integer, Integer> e : freq.entrySet()) {
        int f = e.getValue();
        if (buckets[f] == null) buckets[f] = new ArrayList<>();
        buckets[f].add(e.getKey());
    }

    // Step 3: collect top k from highest-frequency buckets
    int[] result = new int[k];
    int idx = 0;
    for (int f = buckets.length - 1; f >= 0 && idx < k; f--) {
        if (buckets[f] == null) continue;
        for (int num : buckets[f]) {
            if (idx == k) break;
            result[idx++] = num;
        }
    }
    return result;
    // Time: O(n), Space: O(n)  — better than O(n log k) heap approach
}
```

---

## Try It Yourself

**Exercise:** Given two strings `s` and `t`, determine if `t` is an anagram of `s`. Return `true` or `false`.

```java
// Input:  s = "anagram", t = "nagaram"  → true
// Input:  s = "rat",     t = "car"      → false

// Approach A: sort both → compare  (O(n log n))
// Approach B: frequency map         (O(n))

public boolean isAnagram(String s, String t) {
    // your code here
}
```

<details>
<summary>Solution</summary>

```java
public boolean isAnagram(String s, String t) {
    if (s.length() != t.length()) return false;

    int[] count = new int[26]; // only lowercase letters
    for (char c : s.toCharArray()) count[c - 'a']++;
    for (char c : t.toCharArray()) count[c - 'a']--;

    for (int n : count) if (n != 0) return false;
    return true;
    // Time: O(n), Space: O(1) — fixed 26-element array
}

// For unicode strings (follow-up): use HashMap<Character, Integer>
public boolean isAnagramUnicode(String s, String t) {
    if (s.length() != t.length()) return false;
    Map<Character, Integer> count = new HashMap<>();
    for (char c : s.toCharArray()) count.merge(c, 1, Integer::sum);
    for (char c : t.toCharArray()) {
        int newVal = count.merge(c, -1, Integer::sum);
        if (newVal < 0) return false;
    }
    return true;
}
```

</details>

---

## Capstone Connection

The frequency counting and complement patterns are used in AlgoForge's `problems/hashtables/` directory. Each of the exercise problems in this module is a solved entry there — notice how the same `getOrDefault` + `merge` idioms appear again and again.
