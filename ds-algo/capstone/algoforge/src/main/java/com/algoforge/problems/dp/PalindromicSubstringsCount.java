package com.algoforge.problems.dp;

/**
 * LC #647 — Palindromic Substrings (Count)
 *
 * <p>Given a string s, return how many palindromic substrings it has.</p>
 *
 * <b>Pattern:</b> Expand Around Center — same as Longest Palindromic Substring, but count.
 *
 * <pre>
 * For each center (character or gap between characters), expand and count palindromes.
 *
 * Trace: s="aaa"
 *   Center 0 (char 'a'):    "a"
 *   Center 0-1 (gap):       "aa"
 *   Center 1 (char 'a'):    "a", "aaa"
 *   Center 1-2 (gap):       "aa"
 *   Center 2 (char 'a'):    "a"
 *   Total: 6
 * </pre>
 *
 * Time: O(n²)  Space: O(1)
 */
public class PalindromicSubstringsCount {
    public static int countSubstrings(String s) {
        int count = 0;
        for (int i = 0; i < s.length(); i++) {
            count += expandAndCount(s, i, i);     // odd length
            count += expandAndCount(s, i, i + 1); // even length
        }
        return count;
    }

    private static int expandAndCount(String s, int l, int r) {
        int count = 0;
        while (l >= 0 && r < s.length() && s.charAt(l) == s.charAt(r)) {
            count++; l--; r++;
        }
        return count;
    }
}
