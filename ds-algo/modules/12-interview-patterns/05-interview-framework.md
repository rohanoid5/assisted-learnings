# 12.5 — Problem-Solving Framework & Interview Guide

## Concept

Technical interviews are not just about correctness — they are about **communicating your thought process clearly** under time pressure. The UMPIRE framework gives you a repeatable mental structure so that even when you don't immediately know the answer, you can navigate toward it systematically. Interviewers care more about how you think than whether you nail the optimal solution in 3 minutes.

---

## The UMPIRE Framework

```
U — Understand the problem
M — Match to a known pattern
P — Plan your solution
I — Implement
R — Review for bugs
E — Evaluate complexity
```

### U — Understand (2–3 minutes)

Before writing a single line of code:

1. **Restate the problem** in your own words out loud.
2. **Clarify ambiguities** — never assume:
   - Input range: "Can the array be empty? Can values be negative?"
   - Edge cases: "What if there are duplicates? What if n=1?"
   - Output format: "Do I return indices or values?"
3. **Work a small example** by hand.

```
Problem: "Find the two numbers in the array that add up to target."

Bad: immediately start coding HashMap solution.
Good: "So I'm given an unsorted array of integers, values could be
      negative, and I need to return the indices of two distinct
      elements. Can the same index appear twice? Can there be
      multiple valid pairs?"
```

### M — Match to a Pattern (1–2 minutes)

Map the problem to one or more of these signal words:

```
Signal                          Pattern to Consider
─────────────────────────────────────────────────────
"sorted array / two numbers"  → Two Pointer
"subarray with constraint"    → Sliding Window
"contiguous subarray sum"     → Prefix Sum
"all combinations/subsets"    → Backtracking
"shortest path"               → BFS / Dijkstra
"all paths / components"      → DFS
"next greater/smaller"        → Monotonic Stack
"intervals / overlapping"     → Merge Intervals
"Kth largest/smallest"        → Heap / QuickSelect
"minimum/maximum of sums"     → DP
"string of characters"        → HashMap (frequency)
"linked list in O(1) space"   → Fast/Slow Pointers
"stream / online"             → Heap / Sliding Window
"missing/duplicate in [1..n]" → Cyclic Sort
"median"                      → Two Heaps
"prefix string matching"      → Trie
"spanning tree / union"       → Union-Find
```

### P — Plan (2–3 minutes)

State your approach before coding. Include:

- **Data structure(s):** "I'll use a HashMap to store..."
- **Algorithm steps** (numbered): "First I'll sort by... then iterate and..."
- **Complexity target:** "I expect O(n log n) time and O(n) space."
- **Ask:** "Does this approach make sense before I start?"

> _Never start coding without the interviewer's buy-in on your approach._

### I — Implement (10–15 minutes)

```
Writing guidelines:
  ✓ Write clean, readable code (meaningful variable names)
  ✓ Talk through what each block does
  ✓ Handle edge cases as you encounter them (or call them out)
  ✓ Don't optimize prematurely — get a correct solution first
  ✗ Don't go silent for more than 30 seconds
  ✗ Don't erase and restart without explaining why
```

### R — Review (2–3 minutes)

After finishing, trace through your code with the example from step U:

1. **Dry-run** the main logic on the small example.
2. **Check edge cases:** empty input, single element, all duplicates, negative numbers.
3. Check for **off-by-one errors** in loops, especially `i < n` vs `i <= n`.

### E — Evaluate (1 minute)

Always state complexity without being asked:

```
"The time complexity is O(n log n) due to the sort.
 The space complexity is O(n) for the output list.
 A follow-up optimization would be X, which would reduce space to O(1)."
```

---

## Pattern Decision Flowchart

```
New problem: read the description
         │
         ▼
Input is a GRAPH / GRID?
         │
    ┌────┴────┐
   YES         NO
    │          │
    ▼          ▼
Shortest path? Linear structure (array/string/list)?
    │                   │
  ┌─┴─┐           ┌─────┴───────┐
Neg  Non-neg    Sorted?        Unsorted?
edges edges      │                 │
  │      │      Two           Subarray
Bellman Dijkstra pointer /    (sliding window /
-Ford            Binary       prefix sum)
                 search
                              Pairs / K elements?
                              → Heap or Sorting
Tree structure?
  → Recursive DFS / BFS / Tree DP

Optimization (min/max/count)?
  Overlapping subproblems? → DP
  Greedy choice provable? → Greedy
  Enumerate all? → Backtracking
```

---

## Common Mistakes to Avoid

| Mistake | Fix |
|---------|-----|
| Starting to code immediately | Always clarify + plan first |
| Jumping to the optimal solution | Start brute force, then optimize — shows thinking |
| Going silent | Narrate your thought process constantly |
| Abandoning a correct approach for a bug | Debug in-place, don't restart |
| Forgetting to handle `null` / empty | Check after step U |
| Not stating complexity at the end | Always — it's a signal of seniority |
| Using `int` when values can overflow | Use `long` or check constraints |

---

## Code Examples

### Example 1: Applying UMPIRE to Two Sum

```java
// U: find two indices in an unsorted array that sum to target
//    constraints: exactly one solution, can't use same element twice
//
// M: "two numbers in unsorted array" → HashMap pattern
//    (Two Pointer requires sorted array)
//
// P: iterate array, for each value check if (target - value) is in map,
//    if yes return [map.get(complement), i], else put (value, i) in map
//    O(n) time, O(n) space
//
// I: (below)
//
// R: test [2,7,11,15], target=9:
//    i=0: map empty, put 2→0
//    i=1: complement=2 in map → return [0,1] ✓
//
// E: O(n) time, O(n) space

public int[] twoSum(int[] nums, int target) {
    Map<Integer, Integer> seen = new HashMap<>();
    for (int i = 0; i < nums.length; i++) {
        int complement = target - nums[i];
        if (seen.containsKey(complement))
            return new int[]{seen.get(complement), i};
        seen.put(nums[i], i);
    }
    throw new IllegalArgumentException("No valid pair found");
}
```

### Example 2: Mock Interview Walkthrough — Longest Substring Without Repeating Characters (LC #3)

```
U: "Given string s, find the length of the longest substring without repeating characters."
   Edge cases: empty string → 0, all unique → len(s), all same → 1

M: "substring without repeating" + "maximize length" → Sliding Window + HashSet

P: maintain a window [left, right] where all chars are unique.
   Shrink left when we see a duplicate.
   Time: O(n), Space: O(min(n, alphabet_size))

I:
```

```java
public int lengthOfLongestSubstring(String s) {
    Set<Character> window = new HashSet<>();
    int left = 0, maxLen = 0;

    for (int right = 0; right < s.length(); right++) {
        char c = s.charAt(right);
        while (window.contains(c)) {
            window.remove(s.charAt(left));
            left++;
        }
        window.add(c);
        maxLen = Math.max(maxLen, right - left + 1);
    }
    return maxLen;
}
```

```
R: "abcabcbb"
   right=0 (a): add → window={a}, max=1
   right=1 (b): add → window={a,b}, max=2
   right=2 (c): add → window={a,b,c}, max=3
   right=3 (a): a in window → remove a, left=1 → add a → window={b,c,a}, max=3
   right=4 (b): b in window → remove b, left=2 → add b → window={c,a,b}, max=3
   ... final max=3 ✓

E: O(n) time — each character is added and removed at most once.
   O(min(n, 26)) space for the window set.
   Follow-up: can optimize with HashMap<char, int> to jump left pointer directly.
```

---

## 30-Day Interview Prep Checklist

Use this after completing all 12 modules:

**Week 1: Patterns**
- [ ] Re-solve one problem from each of the 12 modules without looking at notes
- [ ] Time yourself: Easy ≤ 15 min, Medium ≤ 25 min, Hard ≤ 45 min

**Week 2: Mixed Practice**
- [ ] LC Blind 75 — categorized problem set
- [ ] 2 problems/day minimum; one Easy, one Medium

**Week 3: Mock Interviews**
- [ ] Do 3 mock interviews with a partner (use LeetCode contest mode)
- [ ] Record yourself — watch for silent gaps and unclear reasoning

**Week 4: Weak Points**
- [ ] Revisit modules where checklist items are still `[ ]`
- [ ] Review top system design questions (SD track) to complement

---

## Capstone Connection

This topic has no AlgoForge implementation — the deliverable is `PatternIndex.md` (created in the exercises). It maps every problem in your AlgoForge collection to its patterns, time/space complexity, and difficulty, becoming your personal interview cheat sheet.
