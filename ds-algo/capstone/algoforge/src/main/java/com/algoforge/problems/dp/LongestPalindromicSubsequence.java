package com.algoforge.problems.dp;

/**
 * LC #516 — Longest Palindromic Subsequence
 *
 * <p>Given a string s, find the longest palindromic subsequence length.
 * A subsequence does not need to be contiguous.</p>
 *
 * <b>Pattern:</b> 2D DP interval DP.
 *
 * <pre>
 * dp[i][j] = LPS length in s[i..j]
 *
 * Base cases:
 *   dp[i][i] = 1 (single char is a palindrome)
 *
 * Recurrence:
 *   if s[i]==s[j]: dp[i][j] = dp[i+1][j-1] + 2
 *   else:          dp[i][j] = max(dp[i+1][j], dp[i][j-1])
 *
 * Trace: s="bbbab"
 *   dp[0][4]:
 *     s[0]='b', s[4]='b' → dp[1][3] + 2
 *     dp[1][3]: s[1]='b', s[3]='a' → max(dp[2][3], dp[1][2])
 *     ... ultimately dp[0][4] = 4 ("bbbb" is not a subsequence, "bbb" is — so answer=4: "bbab"? No)
 *     Actually LPS is "bbbb"? s="bbbab" → subsequences: "bbb"(len3), "bbbb"? s has three b's and one a
 *     LPS = "bbb" or "bbab"? "bbab" isn't palindrome. "bbb" is length 3.
 *     Wait: "bbbab" → reverse = "babbb" → LCS("bbbab","babbb") = 4. So answer = 4. (e.g. "bbbb" skipping the 'a')
 *     Indeed s has b at indices 0,1,2,4 → pick "bbbb" skipping 'a' at 3 → nope, must maintain order.
 *     Subsequence "bbbb": positions 0,1,2,4 → valid! s[0]s[1]s[2]s[4] = bbbb ✓ palindrome!
 *     Answer: 4 ✓
 * </pre>
 *
 * Time: O(n²)  Space: O(n²)
 */
public class LongestPalindromicSubsequence {
    public static int longestPalindromeSubseq(String s) {
        int n = s.length();
        int[][] dp = new int[n][n];
        for (int i = n - 1; i >= 0; i--) {
            dp[i][i] = 1;
            for (int j = i + 1; j < n; j++) {
                if (s.charAt(i) == s.charAt(j))
                    dp[i][j] = (j - i == 1 ? 0 : dp[i+1][j-1]) + 2;
                else
                    dp[i][j] = Math.max(dp[i+1][j], dp[i][j-1]);
            }
        }
        return dp[0][n-1];
    }
}
