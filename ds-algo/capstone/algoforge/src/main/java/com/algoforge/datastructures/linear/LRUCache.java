package com.algoforge.datastructures.linear;

import java.util.NoSuchElementException;

/**
 * LRUCache — Module 03 capstone deliverable (bonus).
 *
 * Least Recently Used cache with O(1) get and put.
 *
 * Strategy: HashMap<key, Node> for O(1) lookup +
 *           DoublyLinkedList for O(1) move-to-front and remove-from-tail.
 *
 *   Most Recently Used (MRU) ← front of DLL ← sentinel.next
 *   Least Recently Used (LRU) ← back of DLL ← sentinel.prev
 *
 * LeetCode #146
 */
public class LRUCache {

    private static class Node {
        int key, val;
        Node prev, next;
        Node(int key, int val) { this.key = key; this.val = val; }
    }

    private final int capacity;
    private final java.util.HashMap<Integer, Node> map;
    private final Node head, tail;   // dummy sentinels

    public LRUCache(int capacity) {
        this.capacity = capacity;
        this.map = new java.util.HashMap<>(capacity);
        head = new Node(0, 0);
        tail = new Node(0, 0);
        head.next = tail;
        tail.prev = head;
    }

    // O(1)
    public int get(int key) {
        Node node = map.get(key);
        if (node == null) return -1;
        moveToFront(node);
        return node.val;
    }

    // O(1)
    public void put(int key, int value) {
        Node node = map.get(key);
        if (node != null) {
            node.val = value;
            moveToFront(node);
        } else {
            if (map.size() == capacity) {
                Node lru = tail.prev;
                remove(lru);
                map.remove(lru.key);
            }
            Node newNode = new Node(key, value);
            addToFront(newNode);
            map.put(key, newNode);
        }
    }

    // ---------------------------------------------------------------
    private void addToFront(Node node) {
        node.next = head.next;
        node.prev = head;
        head.next.prev = node;
        head.next = node;
    }

    private void remove(Node node) {
        node.prev.next = node.next;
        node.next.prev = node.prev;
    }

    private void moveToFront(Node node) {
        remove(node);
        addToFront(node);
    }
}
