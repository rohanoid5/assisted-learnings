package com.algoforge.problems.dp;

/**
 * LC #62 — Unique Paths (II variant: with obstacles — LC #63)
 *
 * <p>A robot on an m×n grid moves only right or down. An obstacle (1) blocks movement.
 * Return the number of unique paths from top-left to bottom-right.</p>
 *
 * <b>Pattern:</b> 2D DP grid.
 *
 * <pre>
 * dp[r][c] = number of paths to reach (r, c)
 * dp[r][c] = 0 if obstacle; else dp[r-1][c] + dp[r][c-1]
 *
 * Space optimization: flat 1D array suffices since we only look at prev row.
 *
 * Grid (0=free, 1=obstacle):
 *   0 0 0        dp: 1 1 1
 *   0 1 0            1 0 1
 *   0 0 0            1 1 2  ← answer: 2
 * </pre>
 *
 * Time: O(m*n)  Space: O(n)
 */
public class UniquePaths {
    public static int uniquePathsWithObstacles(int[][] obstacleGrid) {
        int m = obstacleGrid.length, n = obstacleGrid[0].length;
        if (obstacleGrid[0][0] == 1 || obstacleGrid[m-1][n-1] == 1) return 0;
        int[] dp = new int[n];
        dp[0] = 1;
        for (int r = 0; r < m; r++)
            for (int c = 0; c < n; c++) {
                if (obstacleGrid[r][c] == 1) { dp[c] = 0; continue; }
                if (c > 0) dp[c] += dp[c - 1];
            }
        return dp[n - 1];
    }

    // No-obstacle version (LC #62)
    public static int uniquePaths(int m, int n) {
        int[] dp = new int[n];
        java.util.Arrays.fill(dp, 1);
        for (int r = 1; r < m; r++)
            for (int c = 1; c < n; c++)
                dp[c] += dp[c - 1];
        return dp[n - 1];
    }
}
