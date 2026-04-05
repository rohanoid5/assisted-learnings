package com.algoforge.problems.dp;

/**
 * LC #72 — Edit Distance (Levenshtein Distance)
 *
 * <p>Given two strings word1 and word2, return the minimum number of operations
 * (insert, delete, replace) to convert word1 to word2.</p>
 *
 * <b>Pattern:</b> 2D DP — classic string edit distance.
 *
 * <pre>
 * dp[i][j] = edit distance between word1[0..i-1] and word2[0..j-1]
 *
 * Base cases:
 *   dp[i][0] = i  (delete all i chars)
 *   dp[0][j] = j  (insert all j chars)
 *
 * Recurrence:
 *   if word1[i-1] == word2[j-1]: dp[i][j] = dp[i-1][j-1]   (no op needed)
 *   else: dp[i][j] = 1 + min(dp[i-1][j],    ← delete from word1
 *                             dp[i][j-1],    ← insert into word1
 *                             dp[i-1][j-1])  ← replace
 *
 * Trace: word1="horse", word2="ros"
 *   dp[5][3] = 3  (horse → rorse → rose → ros)
 * </pre>
 *
 * Time: O(m*n)  Space: O(m*n)
 */
public class EditDistance {
    public static int minDistance(String word1, String word2) {
        int m = word1.length(), n = word2.length();
        int[][] dp = new int[m + 1][n + 1];
        for (int i = 0; i <= m; i++) dp[i][0] = i;
        for (int j = 0; j <= n; j++) dp[0][j] = j;
        for (int i = 1; i <= m; i++)
            for (int j = 1; j <= n; j++)
                dp[i][j] = word1.charAt(i-1) == word2.charAt(j-1)
                    ? dp[i-1][j-1]
                    : 1 + Math.min(dp[i-1][j], Math.min(dp[i][j-1], dp[i-1][j-1]));
        return dp[m][n];
    }
}
