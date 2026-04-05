package com.algoforge.datastructures.linear;

import java.util.EmptyStackException;

/**
 * Stack<T> — Module 04 capstone deliverable (Part 1).
 *
 * LIFO structure backed by a singly linked list (avoids array resizing).
 *
 * Complexity: all operations O(1).
 */
public class Stack<T> {

    private SinglyLinkedList.Node<T> top;
    private int size;

    public void push(T item) {
        SinglyLinkedList.Node<T> node = new SinglyLinkedList.Node<>(item);
        node.next = top;
        top = node;
        size++;
    }

    public T pop() {
        if (isEmpty()) throw new EmptyStackException();
        T val = top.data;
        top = top.next;
        size--;
        return val;
    }

    public T peek() {
        if (isEmpty()) throw new EmptyStackException();
        return top.data;
    }

    public boolean isEmpty() { return size == 0; }
    public int size() { return size; }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("TOP -> [");
        SinglyLinkedList.Node<T> curr = top;
        while (curr != null) {
            sb.append(curr.data);
            if (curr.next != null) sb.append(", ");
            curr = curr.next;
        }
        return sb.append("]").toString();
    }
}
