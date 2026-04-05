package com.algoforge.problems.hashtables;

import java.util.HashSet;
import java.util.Set;

/**
 * LC #128 — Longest Consecutive Sequence
 *
 * <p>Given an unsorted array of integers, return the length of the longest
 * consecutive elements sequence. Must run in O(n).</p>
 *
 * <b>Pattern:</b> HashSet — start sequences only from their lowest element.
 *
 * <pre>
 * Key insight: only begin counting from an element n where (n-1) is NOT in the set.
 * This ensures each sequence is counted exactly once.
 *
 * Trace: [100, 4, 200, 1, 3, 2]
 *   set = {100, 4, 200, 1, 3, 2}
 *   n=100: 99 not in set → count 100,101? 101 not in set → length=1
 *   n=4:   3 IS in set   → skip
 *   n=200: 199 not in set → length=1
 *   n=1:   0 not in set  → count 1,2,3,4 → length=4 ← longest
 *   n=3:   2 IS in set   → skip
 *   n=2:   1 IS in set   → skip
 *   Answer: 4
 * </pre>
 *
 * Time: O(n)  Space: O(n)
 */
public class LongestConsecutiveSequence {

    public static int longestConsecutive(int[] nums) {
        Set<Integer> set = new HashSet<>();
        for (int n : nums) set.add(n);

        int longest = 0;
        for (int n : set) {
            if (!set.contains(n - 1)) { // start of a new sequence
                int length = 1;
                while (set.contains(n + length)) length++;
                longest = Math.max(longest, length);
            }
        }
        return longest;
    }
}
