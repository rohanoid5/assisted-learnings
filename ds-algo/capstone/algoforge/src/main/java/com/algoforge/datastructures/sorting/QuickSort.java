package com.algoforge.datastructures.sorting;

import java.util.Random;

/**
 * Quick Sort — a divide-and-conquer algorithm based on partitioning.
 *
 * <pre>
 * Lomuto partition on [3, 6, 8, 10, 1, 2, 1], pivot = arr[hi] = 1:
 *
 *  i=-1  j=0:  arr[0]=3 > 1 → skip
 *  i=-1  j=1:  arr[1]=6 > 1 → skip
 *  i=-1  j=2:  arr[2]=8 > 1 → skip
 *  i=-1  j=3:  arr[3]=10 > 1 → skip
 *  i=-1  j=4:  arr[4]=1 ≤ 1 → i=0, swap(0,4) → [1,6,8,10,3,2,1]
 *  i=0   j=5:  arr[5]=2 > 1 → skip
 *  End: swap pivot with arr[i+1=1] → [1,1,8,10,3,2,6]
 *  pivot index = 1
 * </pre>
 *
 * Time:  O(n log n) average · O(n²) worst (avoided by randomising pivot)
 * Space: O(log n) average recursion stack · O(n) worst
 * Stable: NO
 *
 * <p><b>When to use:</b> When average-case performance matters and stability is
 * not required. Java's {@code Arrays.sort(int[])} uses a dual-pivot quicksort
 * (Yaroslavskiy's algorithm) — faster in practice than classical quicksort.</p>
 */
public class QuickSort {

    private static final Random RNG = new Random();

    // ── Lomuto Partition Scheme ──────────────────────────────────────────────

    /** Sorts the array in-place using Lomuto partitioning with random pivot. */
    public static void sort(int[] arr) {
        if (arr == null || arr.length < 2) return;
        sortLomuto(arr, 0, arr.length - 1);
    }

    private static void sortLomuto(int[] arr, int lo, int hi) {
        if (lo >= hi) return;
        int pi = partitionLomuto(arr, lo, hi);
        sortLomuto(arr, lo, pi - 1);
        sortLomuto(arr, pi + 1, hi);
    }

    /**
     * Lomuto partition — pivot is always the last element (after randomisation).
     * Returns the final position of the pivot.
     *
     * Invariant: arr[lo..i] ≤ pivot < arr[i+1..j-1]
     */
    private static int partitionLomuto(int[] arr, int lo, int hi) {
        // Random pivot → swap it to the end to avoid O(n²) on sorted input
        int randomIdx = lo + RNG.nextInt(hi - lo + 1);
        swap(arr, randomIdx, hi);

        int pivot = arr[hi];
        int i = lo - 1; // boundary: everything ≤ pivot ends up at i

        for (int j = lo; j < hi; j++) {
            if (arr[j] <= pivot) {
                i++;
                swap(arr, i, j);
            }
        }
        swap(arr, i + 1, hi); // place pivot in its final position
        return i + 1;
    }

    // ── Hoare Partition Scheme ───────────────────────────────────────────────

    /**
     * Hoare partition — uses two pointers converging from both ends.
     * Fewer swaps than Lomuto on average; pivot ends up at an arbitrary position
     * within [lo, pi] (not necessarily arr[pi]).
     */
    public static void sortHoare(int[] arr) {
        if (arr == null || arr.length < 2) return;
        sortHoareRec(arr, 0, arr.length - 1);
    }

    private static void sortHoareRec(int[] arr, int lo, int hi) {
        if (lo >= hi) return;
        int pi = partitionHoare(arr, lo, hi);
        sortHoareRec(arr, lo, pi);
        sortHoareRec(arr, pi + 1, hi);
    }

    private static int partitionHoare(int[] arr, int lo, int hi) {
        // Randomise pivot → swap to lo so it acts as the pivot value
        int randomIdx = lo + RNG.nextInt(hi - lo + 1);
        swap(arr, randomIdx, lo);
        int pivot = arr[lo];

        int i = lo - 1;
        int j = hi + 1;

        while (true) {
            do { i++; } while (arr[i] < pivot);
            do { j--; } while (arr[j] > pivot);
            if (i >= j) return j;
            swap(arr, i, j);
        }
    }

    // ── 3-Way Partition (Dutch National Flag) ───────────────────────────────

    /**
     * 3-way partition (Dijkstra's Dutch National Flag) — splits arr[lo..hi] into
     * three regions: < pivot | == pivot | > pivot.
     * Optimal when the array has many duplicate elements.
     *
     * <pre>
     * Input:  [3, 3, 1, 3, 2, 3, 4], pivot = 3
     * Output: [1, 2,  3, 3, 3, 3,  4]
     *          ↑     ↑           ↑
     *         lt    partitioned  gt
     * </pre>
     */
    public static void sort3Way(int[] arr) {
        if (arr == null || arr.length < 2) return;
        sort3WayRec(arr, 0, arr.length - 1);
    }

    private static void sort3WayRec(int[] arr, int lo, int hi) {
        if (lo >= hi) return;

        int randomIdx = lo + RNG.nextInt(hi - lo + 1);
        swap(arr, randomIdx, lo);
        int pivot = arr[lo];

        int lt = lo;      // arr[lo..lt-1] < pivot
        int gt = hi;      // arr[gt+1..hi] > pivot
        int i  = lo + 1;  // arr[lt..i-1] == pivot

        while (i <= gt) {
            if      (arr[i] < pivot) swap(arr, lt++, i++);
            else if (arr[i] > pivot) swap(arr, i,  gt--);
            else                     i++;
        }
        // Recursion only on the < and > regions (== region is already sorted)
        sort3WayRec(arr, lo, lt - 1);
        sort3WayRec(arr, gt + 1, hi);
    }

    // ── QuickSelect ──────────────────────────────────────────────────────────

    /**
     * QuickSelect — finds the kth smallest element (0-indexed) in O(n) average.
     * Partitions like quicksort but only recurses on one side.
     *
     * Example: kthSmallest([7,10,4,3,20,15], k=2) → 7
     *
     * Time: O(n) average · O(n²) worst (randomised pivot avoids this)
     */
    public static int kthSmallest(int[] arr, int k) {
        if (k < 0 || k >= arr.length) throw new IllegalArgumentException("k out of range");
        int[] copy = arr.clone();
        return quickSelect(copy, 0, copy.length - 1, k);
    }

    private static int quickSelect(int[] arr, int lo, int hi, int k) {
        if (lo == hi) return arr[lo];
        int pi = partitionLomuto(arr, lo, hi);
        if      (pi == k) return arr[pi];
        else if (pi >  k) return quickSelect(arr, lo, pi - 1, k);
        else              return quickSelect(arr, pi + 1, hi, k);
    }

    // ── Utility ──────────────────────────────────────────────────────────────

    private static void swap(int[] arr, int i, int j) {
        int tmp = arr[i]; arr[i] = arr[j]; arr[j] = tmp;
    }
}
