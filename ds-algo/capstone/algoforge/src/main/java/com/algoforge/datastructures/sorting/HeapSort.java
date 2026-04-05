package com.algoforge.datastructures.sorting;

/**
 * Heap Sort — an in-place, comparison-based sort that uses an implicit max-heap.
 *
 * <p>Two phases:</p>
 * <ol>
 *   <li><b>Heapify</b> — build a max-heap from the array in O(n) using Floyd's algorithm
 *       (start from the last non-leaf and sift down).</li>
 *   <li><b>Sort</b> — repeatedly swap the root (max) with the last element,
 *       reduce heap size by 1, and sift the new root down.</li>
 * </ol>
 *
 * <pre>
 * Input: [4, 10, 3, 5, 1]
 *
 * Phase 1 — Build max-heap:
 *   Start at index 1 (last non-leaf):
 *   siftDown(1): 10 > 5,1 → no swap
 *   siftDown(0): 4  → swap with 10 → [10, 5, 3, 4, 1]
 *   Max-heap:       10
 *                  /  \
 *                 5    3
 *                / \
 *               4   1
 *
 * Phase 2 — Sort:
 *   Swap 10 with last: [1, 5, 3, 4, 10] → siftDown(0) → [5, 4, 3, 1, 10]
 *   Swap 5  with last: [1, 4, 3, 5, 10] → siftDown(0) → [4, 1, 3, 5, 10]
 *   ...
 *   Final: [1, 3, 4, 5, 10]
 * </pre>
 *
 * Time:  O(n log n) — guaranteed best/average/worst
 * Space: O(1) — fully in-place (unlike merge sort)
 * Stable: NO
 *
 * <p><b>When to use:</b> When O(1) extra space is required and stability is not
 * needed. In practice, quicksort is faster due to better cache behaviour.</p>
 */
public class HeapSort {

    /** Sorts the array in-place in ascending order. */
    public static void sort(int[] arr) {
        if (arr == null || arr.length < 2) return;
        int n = arr.length;

        // Phase 1: Build max-heap — O(n)
        // Start from last non-leaf: index (n/2 - 1)
        for (int i = n / 2 - 1; i >= 0; i--) {
            siftDown(arr, n, i);
        }

        // Phase 2: Extract elements one by one — O(n log n)
        for (int end = n - 1; end > 0; end--) {
            // Move current root (maximum) to the end
            swap(arr, 0, end);
            // Restore heap property for heap of size `end`
            siftDown(arr, end, 0);
        }
    }

    /**
     * Sifts down element at index {@code i} within a heap of size {@code heapSize}.
     *
     * <p>Finds the largest child and swaps if the child > parent, then continues
     * downward until the heap property is restored.</p>
     *
     * @param arr      the array representing the heap
     * @param heapSize the number of elements in the active heap
     * @param i        index of the element to sift down
     */
    private static void siftDown(int[] arr, int heapSize, int i) {
        while (true) {
            int largest = i;
            int left    = 2 * i + 1;
            int right   = 2 * i + 2;

            if (left  < heapSize && arr[left]  > arr[largest]) largest = left;
            if (right < heapSize && arr[right] > arr[largest]) largest = right;

            if (largest == i) break; // heap property satisfied

            swap(arr, i, largest);
            i = largest;
        }
    }

    // ── Partial Heap Sort (Top-K) ────────────────────────────────────────────

    /**
     * Returns the k largest elements in descending order without fully sorting.
     *
     * <p>Build max-heap then extract k times — O(n + k log n).</p>
     *
     * Example: topK([3,2,1,5,6,4], k=2) → [6, 5]
     */
    public static int[] topK(int[] arr, int k) {
        if (arr == null || k <= 0 || k > arr.length) throw new IllegalArgumentException("invalid k");
        int[] heap = arr.clone();
        int n = heap.length;

        // Build max-heap
        for (int i = n / 2 - 1; i >= 0; i--) siftDown(heap, n, i);

        // Extract top k
        int[] result = new int[k];
        for (int i = 0; i < k; i++) {
            result[i] = heap[0];
            heap[0]   = heap[n - 1 - i];
            siftDown(heap, n - 1 - i, 0);
        }
        return result;
    }

    private static void swap(int[] arr, int i, int j) {
        int tmp = arr[i]; arr[i] = arr[j]; arr[j] = tmp;
    }
}
