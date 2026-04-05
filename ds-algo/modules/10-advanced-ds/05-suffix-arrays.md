# 10.5 — Suffix Arrays

## What Is a Suffix Array?

A **suffix array** is a sorted array of all suffixes of a string, represented by their starting indices.

```
String: "banana"
Index:   012345

Suffixes:
  0: "banana"
  1: "anana"
  2: "nana"
  3: "ana"
  4: "na"
  5: "a"

Sorted suffixes:
  5: "a"
  3: "ana"
  1: "anana"
  0: "banana"
  4: "na"
  2: "nana"

Suffix Array (SA): [5, 3, 1, 0, 4, 2]
```

---

## Why Suffix Arrays?

| Problem | Naive | Suffix Array |
|---------|-------|-------------|
| Find pattern P in text T | O(nm) | O(m log n) |
| Count occurrences of P | O(nm) | O(m log n) |
| Longest repeated substring | O(n²) | O(n) with LCP |
| Number of distinct substrings | O(n³) | O(n) with LCP |

---

## Building Suffix Array — O(n log n)

The efficient algorithm uses a **doubling** approach:

1. Sort by single character (rank by char)
2. Double the comparison window: rank by pair (rank[i], rank[i+k])
3. Repeat until all ranks are unique or window ≥ n

```java
public int[] buildSuffixArray(String s) {
    int n = s.length();
    Integer[] sa = new Integer[n];
    int[] rank = new int[n];
    int[] tmp  = new int[n];

    // Initial rank = character value
    for (int i = 0; i < n; i++) { sa[i] = i; rank[i] = s.charAt(i); }

    for (int k = 1; k < n; k <<= 1) {
        final int[] r = rank, step = {k};

        Comparator<Integer> comp = (a, b) -> {
            if (r[a] != r[b]) return r[a] - r[b];
            int ra = (a + step[0] < n) ? r[a + step[0]] : -1;
            int rb = (b + step[0] < n) ? r[b + step[0]] : -1;
            return ra - rb;
        };

        Arrays.sort(sa, comp);

        // Reassign ranks
        tmp[sa[0]] = 0;
        for (int i = 1; i < n; i++) {
            tmp[sa[i]] = tmp[sa[i-1]] + (comp.compare(sa[i], sa[i-1]) != 0 ? 1 : 0);
        }
        rank = tmp.clone();
        if (rank[sa[n-1]] == n-1) break; // all unique, done
    }

    int[] result = new int[n];
    for (int i = 0; i < n; i++) result[i] = sa[i];
    return result;
}
```

---

## LCP Array (Longest Common Prefix)

The **LCP array** `lcp[i]` stores the length of the longest common prefix between `SA[i]` and `SA[i-1]` (adjacent suffixes in sorted order).

```
String: "banana", SA = [5, 3, 1, 0, 4, 2]

SA[0]=5: "a"
SA[1]=3: "ana"     LCP with "a"      = 1  → lcp[1]=1
SA[2]=1: "anana"   LCP with "ana"    = 3  → lcp[2]=3
SA[3]=0: "banana"  LCP with "anana"  = 0  → lcp[3]=0
SA[4]=4: "na"      LCP with "banana" = 0  → lcp[4]=0
SA[5]=2: "nana"    LCP with "na"     = 2  → lcp[5]=2

LCP = [0, 1, 3, 0, 0, 2]
```

---

## Kasai's Algorithm — Build LCP in O(n)

Uses the inverse suffix array:

```java
public int[] buildLCP(String s, int[] sa) {
    int n = s.length();
    int[] rank = new int[n];
    int[] lcp  = new int[n];

    for (int i = 0; i < n; i++) rank[sa[i]] = i; // inverse SA

    int h = 0;
    for (int i = 0; i < n; i++) {
        if (rank[i] > 0) {
            int j = sa[rank[i] - 1]; // previous in sorted order
            while (i + h < n && j + h < n && s.charAt(i+h) == s.charAt(j+h)) h++;
            lcp[rank[i]] = h;
            if (h > 0) h--;
        }
    }
    return lcp;
}
```

---

## Applications

### Longest Repeated Substring

The LCP array directly gives this:

```java
public String longestRepeatedSubstring(String s) {
    int[] sa  = buildSuffixArray(s);
    int[] lcp = buildLCP(s, sa);

    int maxLen = 0, maxIdx = 0;
    for (int i = 1; i < s.length(); i++) {
        if (lcp[i] > maxLen) {
            maxLen = lcp[i];
            maxIdx = sa[i];
        }
    }
    return s.substring(maxIdx, maxIdx + maxLen);
}
```

### Count of Distinct Substrings

```
Total substrings = n*(n+1)/2
Subtract duplicate substrings = sum of LCP array
Distinct = n*(n+1)/2 - sum(lcp)
```

### Pattern Search with Suffix Array

Binary search for pattern P in sorted suffixes:

```java
public int countOccurrences(String text, String pattern) {
    int[] sa = buildSuffixArray(text);
    int n = text.length(), m = pattern.length();

    // Binary search for leftmost occurrence
    int lo = 0, hi = n;
    while (lo < hi) {
        int mid = (lo + hi) / 2;
        String suffix = text.substring(sa[mid], Math.min(sa[mid] + m, n));
        if (suffix.compareTo(pattern) < 0) lo = mid + 1;
        else hi = mid;
    }
    int left = lo;

    hi = n;
    while (lo < hi) {
        int mid = (lo + hi) / 2;
        String suffix = text.substring(sa[mid], Math.min(sa[mid] + m, n));
        if (suffix.startsWith(pattern)) lo = mid + 1;
        else hi = mid;
    }
    return lo - left; // number of occurrences
}
```

---

## Interview Perspective

Suffix arrays are rarely asked to implement from scratch in interviews. However, know:

1. **What they are** and when to use them (string pattern matching, repeated substrings)
2. **Conceptual complexity:** O(n log n) build, O(m log n) search
3. **LCP array** gives longest repeated substring in O(n)
4. **Z-array** and **KMP** are more commonly asked alternatives for string matching

For most LC string problems, KMP (O(n+m)) or the Z-function suffices and is more commonly tested.

---

## Try It Yourself

**Problem (Conceptual):** Given a string, find the length of the longest duplicate substring (a substring that appears at least twice). (LC #1044)

<details>
<summary>Solution</summary>

Approach 1: Suffix Array + LCP (O(n log n)):
```java
public String longestDupSubstring(String s) {
    int[] sa  = buildSuffixArray(s);
    int[] lcp = buildLCP(s, sa);

    int maxLen = 0, idx = 0;
    for (int i = 1; i < s.length(); i++) {
        if (lcp[i] > maxLen) {
            maxLen = lcp[i];
            idx = sa[i];
        }
    }
    return s.substring(idx, idx + maxLen);
}
```

Approach 2: Binary search + Rolling hash (O(n log n), more commonly asked):
```java
// Binary search on length L: does any substring of length L repeat?
// Use Rabin-Karp rolling hash to check in O(n)
// Total: O(n log n)
public String longestDupSubstring(String s) {
    int lo = 1, hi = s.length() - 1;
    String result = "";
    while (lo <= hi) {
        int mid = (lo + hi) / 2;
        String found = search(s, mid);
        if (found != null) { result = found; lo = mid + 1; }
        else hi = mid - 1;
    }
    return result;
}

private String search(String s, int len) {
    long base = 31, mod = (long)1e9 + 7;
    long h = 0, power = 1;
    for (int i = 0; i < len; i++) {
        h = (h * base + s.charAt(i) - 'a' + 1) % mod;
        if (i < len - 1) power = power * base % mod;
    }
    Map<Long, List<Integer>> seen = new HashMap<>();
    seen.computeIfAbsent(h, k -> new ArrayList<>()).add(0);
    for (int i = len; i < s.length(); i++) {
        h = ((h - (s.charAt(i-len)-'a'+1) * power % mod + mod) * base + s.charAt(i)-'a'+1) % mod;
        if (seen.containsKey(h)) {
            String sub = s.substring(i - len + 1, i + 1);
            for (int start : seen.get(h)) {
                if (s.substring(start, start+len).equals(sub)) return sub; // hash collision check
            }
        }
        seen.computeIfAbsent(h, k -> new ArrayList<>()).add(i - len + 1);
    }
    return null;
}
```

</details>

---

## Capstone Connection

For AlgoForge, implement a `problems/advanced/LongestDupSubstring.java` using the rolling hash approach (binary search + Rabin-Karp). Include both the suffix array conceptual implementation and the rolling hash practical solution.
