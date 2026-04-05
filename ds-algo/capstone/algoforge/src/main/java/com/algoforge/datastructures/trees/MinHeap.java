package com.algoforge.datastructures.trees;

import java.util.NoSuchElementException;

/**
 * MinHeap — Module 08 capstone deliverable (Part 2).
 *
 * Array-backed min-heap. The root is always the minimum element.
 *
 * Array representation:
 *   parent of i:     (i - 1) / 2
 *   left child of i: 2*i + 1
 *   right child of i:2*i + 2
 *
 * Complexity:
 *   offer (insert)  O(log n)
 *   poll  (remove)  O(log n)
 *   peek  (min)     O(1)
 *   build from array: O(n)
 */
public class MinHeap {

    private int[] data;
    private int size;

    public MinHeap(int capacity) {
        data = new int[capacity];
    }

    // Build heap from existing array in O(n)
    public MinHeap(int[] arr) {
        data = arr.clone();
        size = arr.length;
        // Heapify from last non-leaf down to root
        for (int i = (size / 2) - 1; i >= 0; i--)
            siftDown(i);
    }

    // O(log n)
    public void offer(int val) {
        if (size == data.length) grow();
        data[size++] = val;
        siftUp(size - 1);
    }

    // O(log n)  — removes and returns the minimum
    public int poll() {
        if (isEmpty()) throw new NoSuchElementException();
        int min = data[0];
        data[0] = data[--size];
        siftDown(0);
        return min;
    }

    // O(1)
    public int peek() {
        if (isEmpty()) throw new NoSuchElementException();
        return data[0];
    }

    public int size() { return size; }
    public boolean isEmpty() { return size == 0; }

    // ---------------------------------------------------------------
    private void siftUp(int i) {
        while (i > 0) {
            int parent = (i - 1) / 2;
            if (data[i] < data[parent]) {
                swap(i, parent);
                i = parent;
            } else break;
        }
    }

    private void siftDown(int i) {
        while (true) {
            int smallest = i;
            int left  = 2 * i + 1;
            int right = 2 * i + 2;
            if (left  < size && data[left]  < data[smallest]) smallest = left;
            if (right < size && data[right] < data[smallest]) smallest = right;
            if (smallest == i) break;
            swap(i, smallest);
            i = smallest;
        }
    }

    private void swap(int i, int j) {
        int tmp = data[i]; data[i] = data[j]; data[j] = tmp;
    }

    private void grow() {
        int[] newData = new int[data.length * 2];
        System.arraycopy(data, 0, newData, 0, size);
        data = newData;
    }
}
