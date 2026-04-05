package com.algoforge.problems.advanced;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

/**
 * LC #315 — Count of Smaller Numbers After Self
 *
 * <p>Given an integer array nums, return a count array where count[i] is the number
 * of elements to the right of nums[i] that are smaller than nums[i].</p>
 *
 * <b>Pattern:</b> Modified Merge Sort — count inversions during the merge step.
 *
 * <pre>
 * Key insight: during merge sort, when we merge two halves, every time we pick an element
 * from the RIGHT half before an element from the LEFT half, all remaining left elements
 * are larger than that right element → add the remaining-left count to each left element's count.
 *
 * Trace: nums=[5,2,6,1]
 *   Initial indexed pairs: [(5,0),(2,1),(6,2),(1,3)]
 *   After merge sort (sorted by value), counts accumulate inversions:
 *     count[0]=2 (2 and 1 are right of 5 and smaller)
 *     count[1]=1 (1 is right of 2 and smaller)
 *     count[2]=1 (1 is right of 6 and smaller)
 *     count[3]=0
 *   Result: [2,1,1,0]
 * </pre>
 *
 * Time: O(n log n)  Space: O(n)
 */
public class CountSmallerNumbersAfterSelf {

    private int[] counts;

    public List<Integer> countSmaller(int[] nums) {
        int n = nums.length;
        counts = new int[n];
        int[][] indexed = new int[n][2]; // [value, original index]
        for (int i = 0; i < n; i++) indexed[i] = new int[]{nums[i], i};
        mergeSort(indexed, 0, n - 1);
        List<Integer> result = new ArrayList<>();
        for (int c : counts) result.add(c);
        return result;
    }

    private void mergeSort(int[][] arr, int lo, int hi) {
        if (lo >= hi) return;
        int mid = lo + (hi - lo) / 2;
        mergeSort(arr, lo, mid);
        mergeSort(arr, mid + 1, hi);
        merge(arr, lo, mid, hi);
    }

    private void merge(int[][] arr, int lo, int mid, int hi) {
        int[][] tmp = new int[hi - lo + 1][2];
        int l = lo, r = mid + 1, k = 0;
        while (l <= mid && r <= hi) {
            if (arr[l][0] <= arr[r][0]) {
                // All elements already merged from right side (k - (r - mid - 1)) are smaller
                counts[arr[l][1]] += r - (mid + 1);
                tmp[k++] = arr[l++];
            } else {
                tmp[k++] = arr[r++];
            }
        }
        while (l <= mid) {
            counts[arr[l][1]] += r - (mid + 1);
            tmp[k++] = arr[l++];
        }
        while (r <= hi) tmp[k++] = arr[r++];
        System.arraycopy(tmp, 0, arr, lo, tmp.length);
    }
}
