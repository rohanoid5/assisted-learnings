package com.algoforge.datastructures.linear;

/**
 * SinglyLinkedList<T> — Module 03 capstone deliverable.
 *
 * A classic singly linked list with head/tail pointers.
 *
 * Complexity:
 *   addFirst / addLast   O(1)
 *   removeFirst          O(1)
 *   removeLast           O(n)  — no prev pointer
 *   get(i)               O(n)
 *   contains             O(n)
 */
public class SinglyLinkedList<T> {

    // ---- Node ----
    public static class Node<T> {
        public T data;
        public Node<T> next;

        public Node(T data) {
            this.data = data;
        }
    }

    private Node<T> head;
    private Node<T> tail;
    private int size;

    // O(1)
    public void addFirst(T data) {
        Node<T> node = new Node<>(data);
        if (head == null) {
            head = tail = node;
        } else {
            node.next = head;
            head = node;
        }
        size++;
    }

    // O(1)
    public void addLast(T data) {
        Node<T> node = new Node<>(data);
        if (tail == null) {
            head = tail = node;
        } else {
            tail.next = node;
            tail = node;
        }
        size++;
    }

    // O(1)
    public T removeFirst() {
        if (head == null) throw new java.util.NoSuchElementException();
        T val = head.data;
        head = head.next;
        if (head == null) tail = null;
        size--;
        return val;
    }

    // O(n)
    public T removeLast() {
        if (head == null) throw new java.util.NoSuchElementException();
        if (head == tail) {
            T val = head.data;
            head = tail = null;
            size--;
            return val;
        }
        Node<T> curr = head;
        while (curr.next != tail) curr = curr.next;
        T val = tail.data;
        curr.next = null;
        tail = curr;
        size--;
        return val;
    }

    // O(n)
    public T get(int index) {
        checkIndex(index);
        Node<T> curr = head;
        for (int i = 0; i < index; i++) curr = curr.next;
        return curr.data;
    }

    // O(n)
    public boolean contains(T data) {
        Node<T> curr = head;
        while (curr != null) {
            if ((data == null && curr.data == null) ||
                (data != null && data.equals(curr.data))) return true;
            curr = curr.next;
        }
        return false;
    }

    // O(n) — iterative in-place reversal
    public void reverse() {
        Node<T> prev = null, curr = head;
        tail = head;
        while (curr != null) {
            Node<T> next = curr.next;
            curr.next = prev;
            prev = curr;
            curr = next;
        }
        head = prev;
    }

    public Node<T> getHead() { return head; }
    public int size() { return size; }
    public boolean isEmpty() { return size == 0; }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("HEAD -> ");
        Node<T> curr = head;
        while (curr != null) {
            sb.append(curr.data);
            if (curr.next != null) sb.append(" -> ");
            curr = curr.next;
        }
        return sb.append(" -> NULL").toString();
    }

    private void checkIndex(int index) {
        if (index < 0 || index >= size)
            throw new IndexOutOfBoundsException("Index " + index + ", size " + size);
    }
}
