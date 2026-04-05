package com.algoforge.problems.dp;

/**
 * LC #10 — Regular Expression Matching
 *
 * <p>Implement regex matching with '.' (any single char) and '*' (zero or more of preceding).
 * The entire string must match the pattern.</p>
 *
 * <b>Pattern:</b> 2D DP.
 *
 * <pre>
 * dp[i][j] = true if s[0..i-1] matches p[0..j-1]
 *
 * Base:
 *   dp[0][0] = true (empty matches empty)
 *   dp[0][j] = dp[0][j-2] if p[j-1]=='*' (eliminate preceding char + *)
 *
 * Recurrence:
 *   if p[j-1] == s[i-1] or p[j-1] == '.':
 *     dp[i][j] = dp[i-1][j-1]
 *   if p[j-1] == '*':
 *     dp[i][j] = dp[i][j-2]                             (zero occurrences of p[j-2])
 *             OR (p[j-2]=='.' OR p[j-2]==s[i-1])
 *                    AND dp[i-1][j]                      (one+ occurrences)
 *
 * Trace: s="aa", p="a*"
 *   dp[0][2]=dp[0][0]=true (a* matches empty)
 *   dp[1][2]: p[1]='*', dp[1][0]=false; p[0]='a'=s[0]='a' AND dp[0][2]=true → true
 *   dp[2][2]: p[1]='*', dp[2][0]=false; p[0]='a'=s[1]='a' AND dp[1][2]=true → true
 *   Answer: true
 * </pre>
 *
 * Time: O(m*n)  Space: O(m*n)
 */
public class RegularExpressionMatching {
    public static boolean isMatch(String s, String p) {
        int m = s.length(), n = p.length();
        boolean[][] dp = new boolean[m + 1][n + 1];
        dp[0][0] = true;
        for (int j = 2; j <= n; j++)
            if (p.charAt(j - 1) == '*') dp[0][j] = dp[0][j - 2];

        for (int i = 1; i <= m; i++)
            for (int j = 1; j <= n; j++) {
                char pc = p.charAt(j - 1), sc = s.charAt(i - 1);
                if (pc == sc || pc == '.') {
                    dp[i][j] = dp[i-1][j-1];
                } else if (pc == '*') {
                    dp[i][j] = dp[i][j-2]; // zero occurrences
                    if (p.charAt(j-2) == sc || p.charAt(j-2) == '.')
                        dp[i][j] = dp[i][j] || dp[i-1][j]; // one+ occurrences
                }
            }
        return dp[m][n];
    }
}
