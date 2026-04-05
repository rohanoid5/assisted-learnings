package com.algoforge.problems.dp;

/**
 * LC #5 — Longest Palindromic Substring
 *
 * <p>Given a string s, return the longest palindromic substring.</p>
 *
 * <b>Pattern:</b> Expand Around Center — O(n²) time, O(1) space.
 *
 * <pre>
 * For each character (and each gap between characters), expand outward while palindrome holds.
 * Track the longest found.
 *
 * Two types of centers:
 *   Odd palindrome:  single character center (n centers)
 *   Even palindrome: gap between two characters (n-1 centers)
 *
 * Trace: s="babad"
 *   center at 'b'(0): "b"(len 1)
 *   center at 'a'(1): "bab"(len 3) ← longest so far
 *   center at 'b'(2): "aba"(len 3) or "b"
 *   center at 'a'(3): "a"
 *   center at 'd'(4): "d"
 *   Answer: "bab" (or "aba")
 * </pre>
 *
 * Time: O(n²)  Space: O(1)
 */
public class LongestPalindromicSubstring {

    public static String longestPalindrome(String s) {
        int start = 0, maxLen = 1;
        for (int i = 0; i < s.length(); i++) {
            // Odd length
            int len1 = expand(s, i, i);
            // Even length
            int len2 = expand(s, i, i + 1);
            int len = Math.max(len1, len2);
            if (len > maxLen) {
                maxLen = len;
                start = i - (len - 1) / 2;
            }
        }
        return s.substring(start, start + maxLen);
    }

    private static int expand(String s, int l, int r) {
        while (l >= 0 && r < s.length() && s.charAt(l) == s.charAt(r)) { l--; r++; }
        return r - l - 1;
    }
}
