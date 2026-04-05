package com.algoforge.datastructures.linear;

import java.util.LinkedList;

/**
 * HashMap<K,V> — Module 05 capstone deliverable.
 *
 * A generic hash map using separate chaining collision resolution.
 *
 * Complexity (average):
 *   put / get / remove   O(1) amortized
 *   Worst case (bad hash): O(n) per operation
 *
 * Implementation notes:
 *   - Initial capacity: 16 buckets
 *   - Load factor threshold: 0.75
 *   - Doubles capacity on resize
 */
public class HashMap<K, V> {

    private static final int    DEFAULT_CAPACITY   = 16;
    private static final double DEFAULT_LOAD_FACTOR = 0.75;

    private static class Entry<K, V> {
        final K key;
        V value;
        Entry(K key, V value) { this.key = key; this.value = value; }
    }

    @SuppressWarnings("unchecked")
    private LinkedList<Entry<K, V>>[] buckets = new LinkedList[DEFAULT_CAPACITY];
    private int size;
    private int capacity;

    public HashMap() {
        this.capacity = DEFAULT_CAPACITY;
    }

    // O(1) amortized
    public void put(K key, V value) {
        int idx = bucketIndex(key);
        if (buckets[idx] == null) buckets[idx] = new LinkedList<>();
        for (Entry<K, V> e : buckets[idx]) {
            if (keysEqual(e.key, key)) {
                e.value = value;    // update existing key
                return;
            }
        }
        buckets[idx].add(new Entry<>(key, value));
        size++;
        if ((double) size / capacity > DEFAULT_LOAD_FACTOR) resize();
    }

    // O(1) average
    public V get(K key) {
        int idx = bucketIndex(key);
        if (buckets[idx] == null) return null;
        for (Entry<K, V> e : buckets[idx]) {
            if (keysEqual(e.key, key)) return e.value;
        }
        return null;
    }

    // O(1) average
    public boolean remove(K key) {
        int idx = bucketIndex(key);
        if (buckets[idx] == null) return false;
        var it = buckets[idx].iterator();
        while (it.hasNext()) {
            if (keysEqual(it.next().key, key)) {
                it.remove();
                size--;
                return true;
            }
        }
        return false;
    }

    public boolean containsKey(K key) { return get(key) != null; }
    public int size() { return size; }
    public boolean isEmpty() { return size == 0; }

    // ---------------------------------------------------------------
    private int bucketIndex(K key) {
        if (key == null) return 0;
        int h = key.hashCode();
        // Spread high bits to avoid clustering
        h = h ^ (h >>> 16);
        return Math.abs(h % capacity);
    }

    private boolean keysEqual(K a, K b) {
        return (a == null && b == null) || (a != null && a.equals(b));
    }

    @SuppressWarnings("unchecked")
    private void resize() {
        capacity *= 2;
        LinkedList<Entry<K, V>>[] newBuckets = new LinkedList[capacity];
        for (LinkedList<Entry<K, V>> bucket : buckets) {
            if (bucket == null) continue;
            for (Entry<K, V> e : bucket) {
                int idx = bucketIndex(e.key);
                if (newBuckets[idx] == null) newBuckets[idx] = new LinkedList<>();
                newBuckets[idx].add(e);
            }
        }
        buckets = newBuckets;
    }
}
