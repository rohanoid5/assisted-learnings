package com.algoforge.datastructures.trees;

/**
 * Max-Heap backed by an array.
 *
 * <p>The maximum element is always at the root (index 0).
 * Internally the elements are stored in a resizable array where,
 * for any node at index i:</p>
 * <pre>
 *   parent(i)      = (i - 1) / 2
 *   leftChild(i)   = 2 * i + 1
 *   rightChild(i)  = 2 * i + 2
 * </pre>
 *
 * <pre>
 * Example — MaxHeap of [3, 1, 6, 5, 2, 4]:
 *
 *            6
 *          /   \
 *         5     4
 *        / \   /
 *       1   2 3
 *
 * Array: [6, 5, 4, 1, 2, 3]
 *         0  1  2  3  4  5
 * </pre>
 *
 * Time:  O(log n) offer/poll · O(1) peek
 * Space: O(n)
 */
public class MaxHeap {

    private int[] data;
    private int size;

    public MaxHeap() { this(16); }

    public MaxHeap(int capacity) {
        data = new int[capacity];
    }

    // ── Public API ──────────────────────────────────────────────────────────

    /** Insert a value. Amortised O(log n). */
    public void offer(int val) {
        ensureCapacity();
        data[size] = val;
        siftUp(size);
        size++;
    }

    /** Remove and return the maximum element. O(log n). */
    public int poll() {
        if (isEmpty()) throw new java.util.NoSuchElementException("Heap is empty");
        int max = data[0];
        data[0] = data[size - 1];
        size--;
        siftDown(0);
        return max;
    }

    /** Return the maximum element without removing it. O(1). */
    public int peek() {
        if (isEmpty()) throw new java.util.NoSuchElementException("Heap is empty");
        return data[0];
    }

    public int    size()    { return size; }
    public boolean isEmpty() { return size == 0; }

    /**
     * Build a max-heap from an arbitrary array in O(n) using Floyd's algorithm.
     * Starts from the last non-leaf and sifts down towards the root.
     */
    public static MaxHeap heapify(int[] arr) {
        MaxHeap h = new MaxHeap(arr.length);
        System.arraycopy(arr, 0, h.data, 0, arr.length);
        h.size = arr.length;
        // last non-leaf index = (n/2) - 1
        for (int i = (arr.length / 2) - 1; i >= 0; i--) {
            h.siftDown(i);
        }
        return h;
    }

    // ── Private Helpers ─────────────────────────────────────────────────────

    /** Bubbles element at index i upward until the heap property is restored. */
    private void siftUp(int i) {
        while (i > 0) {
            int parent = (i - 1) / 2;
            if (data[i] > data[parent]) {
                swap(i, parent);
                i = parent;
            } else {
                break;
            }
        }
    }

    /** Pushes element at index i downward until the heap property is restored. */
    private void siftDown(int i) {
        while (true) {
            int largest = i;
            int left    = 2 * i + 1;
            int right   = 2 * i + 2;

            if (left  < size && data[left]  > data[largest]) largest = left;
            if (right < size && data[right] > data[largest]) largest = right;

            if (largest == i) break;
            swap(i, largest);
            i = largest;
        }
    }

    private void swap(int a, int b) {
        int tmp = data[a]; data[a] = data[b]; data[b] = tmp;
    }

    private void ensureCapacity() {
        if (size == data.length) {
            data = java.util.Arrays.copyOf(data, data.length * 2);
        }
    }
}
