package com.algoforge.problems.graphs;

/**
 * LC #200 — Number of Islands
 *
 * <p>Given an m×n grid of '1' (land) and '0' (water), count the number of islands.
 * An island is surrounded by water and formed by connecting adjacent '1's horizontally/vertically.</p>
 *
 * <b>Pattern:</b> DFS flood-fill — sink each island as we discover it.
 *
 * <pre>
 * Key insight: whenever we find a '1', increment counter and DFS-flood the entire island,
 * changing cells to '0' so they are never counted twice.
 *
 * Grid:
 *   1 1 0 0 0
 *   1 1 0 0 0
 *   0 0 1 0 0
 *   0 0 0 1 1
 *
 * islands=3 (top-left 2×2 cluster, single cell (2,2), bottom-right pair)
 * </pre>
 *
 * Time: O(m*n)  Space: O(m*n) worst-case recursion
 */
public class NumberOfIslands {

    private static final int[][] DIRS = {{0,1},{0,-1},{1,0},{-1,0}};

    public static int numIslands(char[][] grid) {
        int count = 0;
        for (int r = 0; r < grid.length; r++)
            for (int c = 0; c < grid[0].length; c++)
                if (grid[r][c] == '1') { count++; sink(grid, r, c); }
        return count;
    }

    private static void sink(char[][] grid, int r, int c) {
        if (r < 0 || r >= grid.length || c < 0 || c >= grid[0].length || grid[r][c] != '1') return;
        grid[r][c] = '0';
        for (int[] d : DIRS) sink(grid, r + d[0], c + d[1]);
    }
}
