package com.algoforge.datastructures.sorting;

/**
 * Binary Search — six essential variations that cover every interview pattern.
 *
 * <pre>
 * Core idea: eliminate half the search space each iteration.
 *
 *  lo = 0, hi = n-1
 *  while (lo <= hi):
 *      mid = lo + (hi - lo) / 2   ← avoids integer overflow vs (lo+hi)/2
 *      if arr[mid] == target → found
 *      if arr[mid] < target  → search right: lo = mid + 1
 *      if arr[mid] > target  → search left:  hi = mid - 1
 * </pre>
 *
 * <p><b>The Template Trick:</b> For "find minimum X that satisfies condition",
 * use the pattern: binary search on the answer space, not the array index.</p>
 *
 * Time:  O(log n)
 * Space: O(1)
 */
public class BinarySearch {

    // ── 1. Classic: Find exact target ────────────────────────────────────────

    /**
     * Returns the index of {@code target} in a sorted array, or -1 if not found.
     *
     * Example: search([1,3,5,7,9], 7) → 3
     */
    public static int search(int[] arr, int target) {
        int lo = 0, hi = arr.length - 1;
        while (lo <= hi) {
            int mid = lo + (hi - lo) / 2;
            if      (arr[mid] == target) return mid;
            else if (arr[mid] <  target) lo = mid + 1;
            else                         hi = mid - 1;
        }
        return -1;
    }

    // ── 2. Left boundary: First occurrence ──────────────────────────────────

    /**
     * Returns the index of the first (leftmost) occurrence of {@code target},
     * or -1 if not found.
     *
     * <pre>
     * arr = [1, 2, 2, 2, 3, 4]
     * leftBound(arr, 2) → 1
     * </pre>
     */
    public static int leftBound(int[] arr, int target) {
        int lo = 0, hi = arr.length - 1, result = -1;
        while (lo <= hi) {
            int mid = lo + (hi - lo) / 2;
            if (arr[mid] == target) { result = mid; hi = mid - 1; } // keep searching left
            else if (arr[mid] < target) lo = mid + 1;
            else                        hi = mid - 1;
        }
        return result;
    }

    // ── 3. Right boundary: Last occurrence ──────────────────────────────────

    /**
     * Returns the index of the last (rightmost) occurrence of {@code target},
     * or -1 if not found.
     *
     * <pre>
     * arr = [1, 2, 2, 2, 3, 4]
     * rightBound(arr, 2) → 3
     * </pre>
     */
    public static int rightBound(int[] arr, int target) {
        int lo = 0, hi = arr.length - 1, result = -1;
        while (lo <= hi) {
            int mid = lo + (hi - lo) / 2;
            if (arr[mid] == target) { result = mid; lo = mid + 1; } // keep searching right
            else if (arr[mid] < target) lo = mid + 1;
            else                        hi = mid - 1;
        }
        return result;
    }

    // ── 4. Rotated sorted array ──────────────────────────────────────────────

    /**
     * Search in a rotated sorted array (no duplicates).
     *
     * <pre>
     * Original: [1,2,3,4,5,6,7]
     * Rotated:  [4,5,6,7,0,1,2]  (rotated at index 3)
     *
     * searchRotated([4,5,6,7,0,1,2], 0) → 4
     * </pre>
     *
     * Key insight: one half of the array is always sorted.
     * Determine which half, check if target falls in it, then binary search.
     */
    public static int searchRotated(int[] arr, int target) {
        int lo = 0, hi = arr.length - 1;
        while (lo <= hi) {
            int mid = lo + (hi - lo) / 2;
            if (arr[mid] == target) return mid;

            if (arr[lo] <= arr[mid]) { // left half is sorted
                if (arr[lo] <= target && target < arr[mid]) hi = mid - 1;
                else                                         lo = mid + 1;
            } else { // right half is sorted
                if (arr[mid] < target && target <= arr[hi]) lo = mid + 1;
                else                                         hi = mid - 1;
            }
        }
        return -1;
    }

    // ── 5. Find peak element ─────────────────────────────────────────────────

    /**
     * Returns the index of any peak element (greater than its neighbours).
     * Works on unsorted arrays — we only compare arr[mid] with arr[mid+1].
     *
     * <pre>
     * [1,2,3,1] → peak at index 2 (value 3)
     * [1,2,1,3,5,6,4] → any of index 5 (value 6) or index 1 is valid
     * </pre>
     *
     * Insight: if arr[mid] < arr[mid+1], a peak exists to the right.
     * Otherwise, a peak exists at or to the left of mid.
     */
    public static int findPeak(int[] arr) {
        int lo = 0, hi = arr.length - 1;
        while (lo < hi) {
            int mid = lo + (hi - lo) / 2;
            if (arr[mid] < arr[mid + 1]) lo = mid + 1; // ascending → peak is to the right
            else                          hi = mid;      // not ascending → peak is at mid or left
        }
        return lo;
    }

    // ── 6. Binary search on answer space ────────────────────────────────────

    /**
     * Classic "binary search on answer" example:
     * Given sorted matrix (rows and cols sorted), find if target exists.
     * Treats the 2D matrix as a virtual 1D sorted array.
     *
     * <pre>
     * Matrix:
     *  1  3  5  7
     *  10 11 16 20
     *  23 30 34 60
     *
     * searchMatrix(matrix, 3) → true
     * </pre>
     *
     * Time: O(log(m * n))
     */
    public static boolean searchMatrix(int[][] matrix, int target) {
        int m = matrix.length, n = matrix[0].length;
        int lo = 0, hi = m * n - 1;
        while (lo <= hi) {
            int mid = lo + (hi - lo) / 2;
            int val = matrix[mid / n][mid % n];
            if      (val == target) return true;
            else if (val <  target) lo = mid + 1;
            else                    hi = mid - 1;
        }
        return false;
    }

    /**
     * Minimum capacity to ship packages within {@code days} days.
     *
     * <p>Pattern: "find the minimum X such that feasible(X) is true".<br>
     * Binary search the answer space [max(weights), sum(weights)].</p>
     *
     * Example: weights=[1,2,3,4,5,6,7,8,9,10], days=5 → capacity=15
     *
     * Time: O(n log(sum)) where sum is the total weight
     */
    public static int shipWithinDays(int[] weights, int days) {
        int lo = 0, hi = 0;
        for (int w : weights) { lo = Math.max(lo, w); hi += w; }

        while (lo < hi) {
            int mid = lo + (hi - lo) / 2;
            if (canShip(weights, days, mid)) hi = mid;
            else                             lo = mid + 1;
        }
        return lo;
    }

    private static boolean canShip(int[] weights, int days, int capacity) {
        int daysNeeded = 1, load = 0;
        for (int w : weights) {
            if (load + w > capacity) { daysNeeded++; load = 0; }
            load += w;
        }
        return daysNeeded <= days;
    }
}
