package com.algoforge.datastructures.linear;

import java.util.NoSuchElementException;

/**
 * Queue<T> — Module 04 capstone deliverable (Part 2).
 *
 * FIFO structure backed by a doubly linked list.
 * Enqueue at tail, dequeue from head — both O(1).
 */
public class Queue<T> {

    private final DoublyLinkedList<T> list = new DoublyLinkedList<>();

    public void enqueue(T item) {
        list.addLast(item);
    }

    public T dequeue() {
        if (isEmpty()) throw new NoSuchElementException();
        return list.removeFirst();
    }

    public T peek() {
        if (isEmpty()) throw new NoSuchElementException();
        return list.peekFirst();
    }

    public boolean isEmpty() { return list.isEmpty(); }
    public int size() { return list.size(); }

    @Override
    public String toString() {
        return "FRONT -> " + list.toString();
    }
}
