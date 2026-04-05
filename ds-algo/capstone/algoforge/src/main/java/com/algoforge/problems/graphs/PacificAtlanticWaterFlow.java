package com.algoforge.problems.graphs;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;

/**
 * LC #417 — Pacific Atlantic Water Flow
 *
 * <p>Given an m×n matrix of heights, water can flow to adjacent cells (4 directions)
 * if the neighboring height is ≤ current height. Water can flow into the Pacific ocean
 * (top/left borders) and the Atlantic ocean (bottom/right borders).
 * Return all cells from which water can flow to BOTH oceans.</p>
 *
 * <b>Pattern:</b> Reverse BFS from each ocean's border — find cells reachable FROM ocean
 * by going uphill (reverse of actual water flow).
 *
 * <pre>
 * Key insight: instead of simulating water flowing downhill from each cell (O(n^2 * mn)),
 * do BFS from ocean borders going uphill — a cell reachable from both borders is an answer.
 *
 * Initialize:
 *   Pacific queue: top row + left col
 *   Atlantic queue: bottom row + right col
 *
 * BFS visits neighbor if height[neighbor] >= height[curr] (going uphill — reverse flow)
 * </pre>
 *
 * Time: O(m*n)  Space: O(m*n)
 */
public class PacificAtlanticWaterFlow {

    private static final int[][] DIRS = {{0,1},{0,-1},{1,0},{-1,0}};

    public static List<List<Integer>> pacificAtlantic(int[][] heights) {
        int m = heights.length, n = heights[0].length;
        boolean[][] pacific  = new boolean[m][n];
        boolean[][] atlantic = new boolean[m][n];

        Queue<int[]> pacQueue = new ArrayDeque<>();
        Queue<int[]> atlQueue = new ArrayDeque<>();

        for (int r = 0; r < m; r++) {
            pacQueue.offer(new int[]{r, 0});     pacific[r][0] = true;
            atlQueue.offer(new int[]{r, n - 1}); atlantic[r][n - 1] = true;
        }
        for (int c = 0; c < n; c++) {
            pacQueue.offer(new int[]{0, c});     pacific[0][c] = true;
            atlQueue.offer(new int[]{m - 1, c}); atlantic[m - 1][c] = true;
        }

        bfs(pacQueue, pacific, heights);
        bfs(atlQueue, atlantic, heights);

        List<List<Integer>> result = new ArrayList<>();
        for (int r = 0; r < m; r++)
            for (int c = 0; c < n; c++)
                if (pacific[r][c] && atlantic[r][c])
                    result.add(List.of(r, c));
        return result;
    }

    private static void bfs(Queue<int[]> queue, boolean[][] visited, int[][] heights) {
        while (!queue.isEmpty()) {
            int[] cell = queue.poll();
            int r = cell[0], c = cell[1];
            for (int[] d : DIRS) {
                int nr = r + d[0], nc = c + d[1];
                if (nr >= 0 && nr < heights.length && nc >= 0 && nc < heights[0].length
                        && !visited[nr][nc] && heights[nr][nc] >= heights[r][c]) {
                    visited[nr][nc] = true;
                    queue.offer(new int[]{nr, nc});
                }
            }
        }
    }
}
