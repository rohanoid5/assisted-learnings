package com.algoforge.problems.dp;

/**
 * LC #139 — Word Break
 *
 * <p>Given a string s and a dictionary wordDict, return true if s can be segmented
 * into a space-separated sequence of dictionary words.</p>
 *
 * <b>Pattern:</b> 1D DP — dp[i] = can we form s[0..i-1] from the dictionary?
 *
 * <pre>
 * dp[0] = true (empty string)
 * dp[i] = any j < i where dp[j]=true AND s[j..i-1] in dict
 *
 * Trace: s="leetcode", wordDict=["leet","code"]
 *   dp[0]=T
 *   dp[4]: j=0, dp[0]=T, s[0..3]="leet" ∈ dict → dp[4]=T
 *   dp[8]: j=4, dp[4]=T, s[4..7]="code" ∈ dict → dp[8]=T → return true
 * </pre>
 *
 * Time: O(n² * m) where m = avg word length   Space: O(n)
 */
public class WordBreak {
    public static boolean wordBreak(String s, java.util.List<String> wordDict) {
        java.util.Set<String> dict = new java.util.HashSet<>(wordDict);
        int n = s.length();
        boolean[] dp = new boolean[n + 1];
        dp[0] = true;
        for (int i = 1; i <= n; i++)
            for (int j = 0; j < i; j++)
                if (dp[j] && dict.contains(s.substring(j, i))) { dp[i] = true; break; }
        return dp[n];
    }
}
