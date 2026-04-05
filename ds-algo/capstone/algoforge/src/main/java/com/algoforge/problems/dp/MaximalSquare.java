package com.algoforge.problems.dp;

/**
 * LC #221 — Maximal Square
 *
 * <p>Given an m×n matrix of '0's and '1's, find the largest square containing only '1's
 * and return its area.</p>
 *
 * <b>Pattern:</b> 2D DP — dp[r][c] = side length of largest square with bottom-right at (r,c).
 *
 * <pre>
 * dp[r][c] = min(dp[r-1][c], dp[r][c-1], dp[r-1][c-1]) + 1  if matrix[r][c]='1'
 *          = 0                                                 if matrix[r][c]='0'
 *
 * Intuition: the square ending at (r,c) is limited by the smallest of the three neighbors.
 *
 * Matrix:
 *   1 0 1 0 0
 *   1 0 1 1 1
 *   1 1 1 1 1
 *   1 0 0 1 0
 *
 * dp:
 *   1 0 1 0 0
 *   1 0 1 1 1
 *   1 1 1 2 2    ← largest square side=2, area=4
 *   1 0 0 1 0
 * </pre>
 *
 * Time: O(m*n)  Space: O(n) — 1D rolling array
 */
public class MaximalSquare {
    public static int maximalSquare(char[][] matrix) {
        int m = matrix.length, n = matrix[0].length;
        int[] dp = new int[n + 1];
        int maxSide = 0, prev = 0;
        for (int r = 1; r <= m; r++) {
            for (int c = 1; c <= n; c++) {
                int temp = dp[c];
                if (matrix[r-1][c-1] == '1') {
                    dp[c] = Math.min(dp[c], Math.min(dp[c-1], prev)) + 1;
                    maxSide = Math.max(maxSide, dp[c]);
                } else {
                    dp[c] = 0;
                }
                prev = temp;
            }
        }
        return maxSide * maxSide;
    }
}
