package com.algoforge.problems.backtracking;

/**
 * LC #79 — Word Search
 *
 * <p>Given an m×n board of characters and a word, return true if the word exists
 * in the board. The word must be constructed from sequentially adjacent cells
 * (horizontal/vertical). The same cell cannot be used twice.</p>
 *
 * <b>Pattern:</b> Backtracking DFS on a grid — mark visited in-place, restore after.
 *
 * <pre>
 * Key choices:
 *   - From each cell matching word[0], start a DFS
 *   - Mark visited with '#' to avoid revisiting; restore on backtrack
 *
 * Trace: board=[["A","B","C","E"],["S","F","C","S"],["A","D","E","E"]], word="ABCCED"
 *   Start at (0,0)='A'=word[0]
 *   → (0,1)='B'=word[1] → (0,2)='C'=word[2] → (1,2)='C'=word[3]
 *   → (2,2)='E'=word[4] → (2,1)='D'=word[5] → found!
 * </pre>
 *
 * Time: O(m * n * 4^L) where L = word length   Space: O(L) recursion depth
 */
public class WordSearch {

    private static final int[][] DIRS = {{0,1},{0,-1},{1,0},{-1,0}};

    public static boolean exist(char[][] board, String word) {
        int m = board.length, n = board[0].length;
        for (int r = 0; r < m; r++)
            for (int c = 0; c < n; c++)
                if (dfs(board, r, c, word, 0)) return true;
        return false;
    }

    private static boolean dfs(char[][] board, int r, int c, String word, int idx) {
        if (idx == word.length()) return true;
        if (r < 0 || r >= board.length || c < 0 || c >= board[0].length) return false;
        if (board[r][c] != word.charAt(idx)) return false;

        char saved = board[r][c];
        board[r][c] = '#'; // mark visited
        for (int[] d : DIRS)
            if (dfs(board, r + d[0], c + d[1], word, idx + 1)) {
                board[r][c] = saved; // restore before returning true
                return true;
            }
        board[r][c] = saved; // restore (backtrack)
        return false;
    }
}
