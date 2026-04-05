package com.algoforge.datastructures.linear;

/**
 * DynamicArray<T> — Module 02 capstone deliverable.
 *
 * A generic resizable array backed by a raw Object array.
 * Implements the same contract as java.util.ArrayList but built from scratch.
 *
 * Complexity:
 *   get(i)     O(1)
 *   add(x)     O(1) amortized  (O(n) on resize)
 *   remove(i)  O(n)
 *   contains   O(n)
 */
@SuppressWarnings("unchecked")
public class DynamicArray<T> {

    private static final int DEFAULT_CAPACITY = 10;
    private Object[] data;
    private int size;

    public DynamicArray() {
        data = new Object[DEFAULT_CAPACITY];
        size = 0;
    }

    public DynamicArray(int initialCapacity) {
        if (initialCapacity <= 0)
            throw new IllegalArgumentException("Capacity must be positive");
        data = new Object[initialCapacity];
        size = 0;
    }

    // O(1) amortized
    public void add(T item) {
        ensureCapacity();
        data[size++] = item;
    }

    // O(n) — shifts elements
    public void add(int index, T item) {
        checkIndexForAdd(index);
        ensureCapacity();
        System.arraycopy(data, index, data, index + 1, size - index);
        data[index] = item;
        size++;
    }

    // O(1)
    public T get(int index) {
        checkIndex(index);
        return (T) data[index];
    }

    // O(1)
    public T set(int index, T item) {
        checkIndex(index);
        T old = (T) data[index];
        data[index] = item;
        return old;
    }

    // O(n) — shifts elements
    public T remove(int index) {
        checkIndex(index);
        T removed = (T) data[index];
        System.arraycopy(data, index + 1, data, index, size - index - 1);
        data[--size] = null;    // help GC
        return removed;
    }

    // O(n)
    public boolean contains(T item) {
        for (int i = 0; i < size; i++) {
            if ((item == null && data[i] == null) ||
                (item != null && item.equals(data[i])))
                return true;
        }
        return false;
    }

    public int size() { return size; }
    public boolean isEmpty() { return size == 0; }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < size; i++) {
            sb.append(data[i]);
            if (i < size - 1) sb.append(", ");
        }
        return sb.append("]").toString();
    }

    // ---------------------------------------------------------------
    // Private helpers

    private void ensureCapacity() {
        if (size == data.length) {
            int newCapacity = data.length * 2;
            Object[] newData = new Object[newCapacity];
            System.arraycopy(data, 0, newData, 0, size);
            data = newData;
        }
    }

    private void checkIndex(int index) {
        if (index < 0 || index >= size)
            throw new IndexOutOfBoundsException("Index " + index + ", size " + size);
    }

    private void checkIndexForAdd(int index) {
        if (index < 0 || index > size)
            throw new IndexOutOfBoundsException("Index " + index + ", size " + size);
    }
}
