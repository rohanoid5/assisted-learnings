package com.algoforge.problems.backtracking;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * LC #51 — N-Queens
 *
 * <p>Place n queens on an n×n chessboard such that no two queens attack each other.
 * Return all distinct solutions. Each solution is a board where 'Q' = queen, '.' = empty.</p>
 *
 * <b>Pattern:</b> Backtracking with three attack sets (columns, diagonals).
 *
 * <pre>
 * Attack vectors for a queen at (row, col):
 *   - Same column:           col
 *   - Same '/' diagonal:     row + col (constant along /)
 *   - Same '\' diagonal:     row - col (constant along \)
 *
 * Algorithm: place queen row by row; track forbidden cols and diagonals.
 *
 * n=4 solution 1:        n=4 solution 2:
 *   . Q . .                . . Q .
 *   . . . Q                Q . . .
 *   Q . . .                . . . Q
 *   . . Q .                . Q . .
 * </pre>
 *
 * Time: O(n!)   Space: O(n)
 */
public class NQueens {

    public static List<List<String>> solveNQueens(int n) {
        List<List<String>> result = new ArrayList<>();
        boolean[] cols = new boolean[n];
        boolean[] diag1 = new boolean[2 * n]; // row + col
        boolean[] diag2 = new boolean[2 * n]; // row - col + n (offset for negative values)
        char[][] board = new char[n][n];
        for (char[] row : board) Arrays.fill(row, '.');
        backtrack(board, 0, cols, diag1, diag2, result);
        return result;
    }

    private static void backtrack(char[][] board, int row, boolean[] cols,
                                   boolean[] diag1, boolean[] diag2, List<List<String>> result) {
        if (row == board.length) {
            result.add(boardToList(board));
            return;
        }
        int n = board.length;
        for (int col = 0; col < n; col++) {
            if (cols[col] || diag1[row + col] || diag2[row - col + n]) continue;
            board[row][col] = 'Q';
            cols[col] = diag1[row + col] = diag2[row - col + n] = true;
            backtrack(board, row + 1, cols, diag1, diag2, result);
            board[row][col] = '.';
            cols[col] = diag1[row + col] = diag2[row - col + n] = false;
        }
    }

    private static List<String> boardToList(char[][] board) {
        List<String> rows = new ArrayList<>();
        for (char[] row : board) rows.add(new String(row));
        return rows;
    }
}
