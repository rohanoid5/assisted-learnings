package com.algoforge.datastructures.sorting;

/**
 * Merge Sort — a stable, divide-and-conquer sorting algorithm.
 *
 * <pre>
 * Divide-and-conquer on [38, 27, 43, 3, 9, 82, 10]:
 *
 * Split:
 *   [38,27,43,3]         [9,82,10]
 *   [38,27] [43,3]       [9,82] [10]
 *   [38][27] [43][3]     [9][82] [10]
 *
 * Merge (bottom-up):
 *   [27,38] [3,43]       [9,82] [10]
 *   [3,27,38,43]         [9,10,82]
 *   [3,9,10,27,38,43,82]
 * </pre>
 *
 * Time:  O(n log n) — guaranteed best/average/worst
 * Space: O(n) auxiliary (the merge step needs a temporary array)
 * Stable: YES — equal elements maintain their original relative order
 *
 * <p><b>When to use:</b> When stability is required, or when working with
 * linked lists (merge sort is optimal for linked lists — no extra space needed).
 * Java's {@code Arrays.sort(Object[])} uses TimSort, a hybrid of merge sort.</p>
 */
public class MergeSort {

    /** Sorts the array in-place in ascending order. */
    public static void sort(int[] arr) {
        if (arr == null || arr.length < 2) return;
        int[] aux = new int[arr.length]; // auxiliary array for merging
        sortRec(arr, aux, 0, arr.length - 1);
    }

    // ── Recursive top-down implementation ───────────────────────────────────

    private static void sortRec(int[] arr, int[] aux, int lo, int hi) {
        if (lo >= hi) return;          // base case: single element
        int mid = lo + (hi - lo) / 2; // avoids integer overflow
        sortRec(arr, aux, lo, mid);
        sortRec(arr, aux, mid + 1, hi);
        merge(arr, aux, lo, mid, hi);
    }

    /**
     * Merges two sorted subarrays arr[lo..mid] and arr[mid+1..hi].
     *
     * <pre>
     * Before: aux = [3, 27, 38, | 43, 82]  (two sorted halves)
     * After:  arr = [3, 27, 38, 43, 82]
     * </pre>
     */
    private static void merge(int[] arr, int[] aux, int lo, int mid, int hi) {
        // Copy to auxiliary
        System.arraycopy(arr, lo, aux, lo, hi - lo + 1);

        int i = lo;       // pointer into left half
        int j = mid + 1;  // pointer into right half
        int k = lo;       // write position in arr

        while (i <= mid && j <= hi) {
            // <= preserves stability (left element chosen on tie)
            arr[k++] = (aux[i] <= aux[j]) ? aux[i++] : aux[j++];
        }
        while (i <= mid)  arr[k++] = aux[i++];
        while (j <= hi)   arr[k++] = aux[j++];
    }

    // ── Bottom-up iterative implementation (no recursion) ───────────────────

    /**
     * Bottom-up merge sort — avoids recursion stack overhead.
     * Merges runs of size 1, then 2, then 4, etc.
     */
    public static void sortBottomUp(int[] arr) {
        if (arr == null || arr.length < 2) return;
        int n   = arr.length;
        int[] aux = new int[n];

        for (int size = 1; size < n; size *= 2) {
            for (int lo = 0; lo < n - size; lo += 2 * size) {
                int mid = lo + size - 1;
                int hi  = Math.min(lo + 2 * size - 1, n - 1);
                merge(arr, aux, lo, mid, hi);
            }
        }
    }

    /**
     * Merge Sort on a singly linked list. O(n log n) time, O(log n) stack space.
     * Demonstrates why merge sort is preferred over quicksort for linked lists.
     */
    public static ListNode sortList(ListNode head) {
        if (head == null || head.next == null) return head;
        ListNode mid  = getMid(head);
        ListNode right = mid.next;
        mid.next = null; // split list in two

        ListNode l = sortList(head);
        ListNode r = sortList(right);
        return mergeList(l, r);
    }

    private static ListNode getMid(ListNode head) {
        ListNode slow = head, fast = head;
        while (fast.next != null && fast.next.next != null) {
            slow = slow.next;
            fast = fast.next.next;
        }
        return slow;
    }

    private static ListNode mergeList(ListNode l, ListNode r) {
        ListNode dummy = new ListNode(0);
        ListNode cur   = dummy;
        while (l != null && r != null) {
            if (l.val <= r.val) { cur.next = l; l = l.next; }
            else                { cur.next = r; r = r.next; }
            cur = cur.next;
        }
        cur.next = (l != null) ? l : r;
        return dummy.next;
    }

    /** Minimal ListNode for the linked-list sort demonstration. */
    public static class ListNode {
        public int val;
        public ListNode next;
        public ListNode(int val) { this.val = val; }
    }
}
