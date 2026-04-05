package com.algoforge.problems.dp;

/**
 * LC #1143 — Longest Common Subsequence
 *
 * <p>Given two strings text1 and text2, return the length of their longest common subsequence.
 * A subsequence is a sequence derived from the original by deleting some characters
 * without changing the relative order.</p>
 *
 * <b>Pattern:</b> 2D DP table.
 *
 * <pre>
 * dp[i][j] = LCS length of text1[0..i-1] and text2[0..j-1]
 *
 * Recurrence:
 *   if text1[i-1] == text2[j-1]: dp[i][j] = dp[i-1][j-1] + 1
 *   else:                         dp[i][j] = max(dp[i-1][j], dp[i][j-1])
 *
 * Trace: text1="abcde", text2="ace"
 *        ""  a  c  e
 *   ""  [ 0, 0, 0, 0]
 *    a  [ 0, 1, 1, 1]
 *    b  [ 0, 1, 1, 1]
 *    c  [ 0, 1, 2, 2]
 *    d  [ 0, 1, 2, 2]
 *    e  [ 0, 1, 2, 3] ← LCS = 3 ("ace")
 * </pre>
 *
 * Time: O(m*n)  Space: O(m*n) — can optimize to O(min(m,n))
 */
public class LongestCommonSubsequence {
    public static int longestCommonSubsequence(String text1, String text2) {
        int m = text1.length(), n = text2.length();
        int[][] dp = new int[m + 1][n + 1];
        for (int i = 1; i <= m; i++)
            for (int j = 1; j <= n; j++)
                dp[i][j] = text1.charAt(i - 1) == text2.charAt(j - 1)
                    ? dp[i-1][j-1] + 1
                    : Math.max(dp[i-1][j], dp[i][j-1]);
        return dp[m][n];
    }
}
