# 2.2 — String Manipulation in Java

## Concept

Strings are immutable in Java. Every "modification" creates a new object. This single fact causes O(n²) bugs in string manipulation code that looks linear. Knowing when to use `String`, `StringBuilder`, or a `char[]` is a prerequisite for writing efficient string algorithms.

---

## Deep Dive

### Java String Immutability

```java
String s = "hello";
s = s + " world";   // does NOT modify the original "hello" object
                     // creates a NEW String "hello world", reassigns s
                     // "hello" gets garbage collected

// Consequence: string concatenation in a loop is O(n²)
String result = "";
for (String word : words) {
    result += word;   // each += creates a new String, copies all previous chars
}
// For n words of avg length L: O(1·L + 2·L + 3·L + ... + n·L) = O(n²·L)

// Fix: StringBuilder
StringBuilder sb = new StringBuilder();
for (String word : words) {
    sb.append(word);  // appends to an internal char[] buffer — amortized O(L)
}
String result = sb.toString();  // one final copy — O(n·L) total
```

### The String Pool

```
String a = "hello";         ← stored in string pool (heap area)
String b = "hello";         ← points to SAME object in pool
String c = new String("hello"); ← new object on heap (NOT pooled)

a == b    → true  (same reference from pool)
a == c    → false (different objects)
a.equals(c) → true (same characters)

// ⚠️ NEVER use == to compare String content. Always use .equals()
```

### Key String Methods

```java
String s = "Hello, World!";

// Length and access
s.length()              // 13
s.charAt(0)             // 'H'
s.toCharArray()         // ['H','e','l','l','o',',',...]  — O(n) copy

// Searching
s.indexOf('o')          // 4
s.lastIndexOf('o')      // 8
s.contains("World")     // true  — O(n)
s.startsWith("Hello")   // true
s.endsWith("!")         // true

// Substrings — O(end - start) in Java 8+
s.substring(7, 12)      // "World"
s.substring(7)          // "World!"

// Transformation — ALL return new String
s.toLowerCase()         // "hello, world!"
s.toUpperCase()         // "HELLO, WORLD!"
s.trim()                // removes leading/trailing whitespace
s.replace('l', 'r')     // "Herro, Worrd!"
s.replaceAll("\\s+", "_") // regex replace

// Split
String[] parts = s.split(", "); // ["Hello", "World!"]

// Convert
String.valueOf(42)       // "42"
Integer.parseInt("42")   // 42

// Comparison
s.equals("Hello, World!")     // true
s.equalsIgnoreCase("hello, world!") // true
s.compareTo("Apple")           // positive (H > A)
```

---

### Frequency Counting — The #1 String Trick

Most string problems reduce to: *count character frequencies and compare*.

```java
// Are two strings anagrams of each other?
public boolean isAnagram(String s, String t) {
    if (s.length() != t.length()) return false;

    int[] freq = new int[26];                    // only lowercase letters
    for (char c : s.toCharArray()) freq[c - 'a']++;
    for (char c : t.toCharArray()) freq[c - 'a']--;

    for (int count : freq) {
        if (count != 0) return false;
    }
    return true;
    // Time: O(n), Space: O(1) — array of fixed size 26
}
```

For unicode or case-insensitive:
```java
Map<Character, Integer> freq = new HashMap<>();
for (char c : s.toCharArray()) freq.merge(c, 1, Integer::sum);
```

---

## Code Examples

### Example 1: Reverse Words in a String

```java
// "  the sky  is  blue  " → "blue is sky the"
// Trim extra spaces, reverse word order

public String reverseWords(String s) {
    // Split by any whitespace (handles multiple spaces)
    String[] words = s.trim().split("\\s+");
    StringBuilder sb = new StringBuilder();
    for (int i = words.length - 1; i >= 0; i--) {
        sb.append(words[i]);
        if (i > 0) sb.append(' ');
    }
    return sb.toString();
    // Time: O(n), Space: O(n) for array + StringBuilder
}
```

### Example 2: Longest Common Prefix

```java
// ["flower","flow","flight"] → "fl"
// Vertical scanning: compare column by column

public String longestCommonPrefix(String[] strs) {
    if (strs == null || strs.length == 0) return "";

    for (int col = 0; col < strs[0].length(); col++) {
        char c = strs[0].charAt(col);
        for (int row = 1; row < strs.length; row++) {
            if (col == strs[row].length() || strs[row].charAt(col) != c) {
                return strs[0].substring(0, col);  // mismatch at col
            }
        }
    }
    return strs[0];  // all strings start with strs[0]
    // Time: O(S) where S = total characters across all strings
}
```

---

## Try It Yourself

**Exercise:** Check if a string is a valid palindrome, ignoring non-alphanumeric characters and case. Do it in O(n) time and O(1) space (no extra string/array).

Input: `"A man, a plan, a canal: Panama"` → `true`  
Input: `"race a car"` → `false`

```java
public boolean isPalindrome(String s) {
    // Your solution here — no toCharArray(), no filtered string
}
```

<details>
<summary>Show solution</summary>

```java
public boolean isPalindrome(String s) {
    int left = 0, right = s.length() - 1;
    while (left < right) {
        while (left < right && !Character.isLetterOrDigit(s.charAt(left))) left++;
        while (left < right && !Character.isLetterOrDigit(s.charAt(right))) right--;
        if (Character.toLowerCase(s.charAt(left))
                != Character.toLowerCase(s.charAt(right))) return false;
        left++; right--;
    }
    return true;
    // Time: O(n), Space: O(1) — two pointers, no extra storage
}
```

Key insight: `Character.isLetterOrDigit()` and `Character.toLowerCase()` are O(1) — they just do arithmetic on char values. LC #125.

</details>

---

## Capstone Connection

String problems make up ~20% of interview questions at top companies. Every string problem in AlgoForge is solved using an approach from this topic: frequency arrays, two pointers, or `StringBuilder`. No "clever" regex one-liners — write what you can explain on a whiteboard.
