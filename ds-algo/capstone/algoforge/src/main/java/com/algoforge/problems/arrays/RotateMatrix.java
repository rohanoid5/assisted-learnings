package com.algoforge.problems.arrays;

/**
 * LC #48 — Rotate Image (90° clockwise, in-place)
 *
 * <p>Given an n×n matrix, rotate it 90 degrees clockwise in-place.</p>
 *
 * <b>Pattern:</b> Matrix manipulation — transpose then reverse rows
 *
 * <pre>
 * Original:          Transposed:         Rotated (rows reversed):
 * 1  2  3            1  4  7             7  4  1
 * 4  5  6    →→      2  5  8    →→       8  5  2
 * 7  8  9            3  6  9             9  6  3
 *
 * Step 1 — Transpose: swap matrix[i][j] with matrix[j][i]
 * Step 2 — Reverse each row: swap the left and right halves
 *
 * Why does this work?
 * A 90° clockwise rotation maps (i,j) → (j, n-1-i).
 * Transpose maps (i,j) → (j,i).
 * Reversing rows maps (j,i) → (j, n-1-i). ✓
 * </pre>
 *
 * Time: O(n²)  Space: O(1)
 */
public class RotateMatrix {

    public static void rotate(int[][] matrix) {
        int n = matrix.length;

        // Step 1: Transpose — reflect over the main diagonal
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                int tmp           = matrix[i][j];
                matrix[i][j]      = matrix[j][i];
                matrix[j][i]      = tmp;
            }
        }

        // Step 2: Reverse each row
        for (int i = 0; i < n; i++) {
            int lo = 0, hi = n - 1;
            while (lo < hi) {
                int tmp          = matrix[i][lo];
                matrix[i][lo++]  = matrix[i][hi];
                matrix[i][hi--]  = tmp;
            }
        }
    }
}
