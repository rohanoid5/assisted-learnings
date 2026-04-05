package com.algoforge.problems.sorting;

/**
 * LC #74 — Search a 2D Matrix
 *
 * <p>Given an m×n matrix where each row is sorted left-to-right and the first integer
 * of each row is greater than the last integer of the previous row,
 * return true if target is in the matrix. Must run in O(log(m*n)).</p>
 *
 * <b>Pattern:</b> Binary search treating the 2D matrix as a flat sorted array.
 *
 * <pre>
 * Key insight: flatten the matrix index: mid maps to (mid/n, mid%n).
 *
 * Trace: matrix=[[1,3,5,7],[10,11,16,20],[23,30,34,60]], target=3
 *   lo=0, hi=11 (4*3-1), mid=5 → row=5/4=1, col=5%4=1 → matrix[1][1]=11 > 3 → hi=4
 *   lo=0, hi=4, mid=2 → row=0, col=2 → matrix[0][2]=5 > 3 → hi=1
 *   lo=0, hi=1, mid=0 → matrix[0][0]=1 < 3 → lo=1
 *   lo=1, hi=1, mid=1 → matrix[0][1]=3 == 3 → return true
 * </pre>
 *
 * Time: O(log(m*n))  Space: O(1)
 */
public class Search2DMatrix {

    public static boolean searchMatrix(int[][] matrix, int target) {
        int m = matrix.length, n = matrix[0].length;
        int lo = 0, hi = m * n - 1;
        while (lo <= hi) {
            int mid = lo + (hi - lo) / 2;
            int val = matrix[mid / n][mid % n];
            if (val == target) return true;
            if (val < target)  lo = mid + 1;
            else               hi = mid - 1;
        }
        return false;
    }
}
