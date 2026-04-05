package com.algoforge.problems.dp;

/**
 * LC #91 — Decode Ways
 *
 * <p>A message containing letters A-Z is encoded as numbers 1-26.
 * Given a string of digits, return the number of ways to decode it.</p>
 *
 * <b>Pattern:</b> 1D DP — similar to climbing stairs but with validity checks.
 *
 * <pre>
 * dp[i] = number of ways to decode s[0..i-1]
 * dp[0] = 1 (empty string), dp[1] = 1 if s[0]!='0' else 0
 *
 * For each position i (1-indexed):
 *   oneDigit = s[i-1] alone → valid if s[i-1] != '0'
 *   twoDigit = s[i-2..i-1] → valid if 10 ≤ value ≤ 26
 *
 * Trace: s="226"
 *   dp=[1,1,2,3]
 *   dp[1]: '2'→1 way. dp[1]=1
 *   dp[2]: '2'→1 way + "22"→1 way. dp[2]=2
 *   dp[3]: '6'→1 way (dp[2]=2) + "26"→1 way (dp[1]=1). dp[3]=3
 * </pre>
 *
 * Time: O(n)  Space: O(1)
 */
public class DecodeWays {
    public static int numDecodings(String s) {
        if (s.isEmpty() || s.charAt(0) == '0') return 0;
        int prev2 = 1, prev1 = 1;
        for (int i = 1; i < s.length(); i++) {
            int curr = 0;
            if (s.charAt(i) != '0')                          curr += prev1; // one-digit
            int twoDigit = Integer.parseInt(s.substring(i-1, i+1));
            if (twoDigit >= 10 && twoDigit <= 26)             curr += prev2; // two-digit
            prev2 = prev1;
            prev1 = curr;
        }
        return prev1;
    }
}
