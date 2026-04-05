package com.algoforge.datastructures.linear;

/**
 * DoublyLinkedList<T> — Module 03 capstone deliverable.
 *
 * Bidirectional linked list with O(1) add/remove at both ends.
 * Foundation for LRUCache (HashMap + DLL).
 *
 * Complexity:
 *   addFirst / addLast / removeFirst / removeLast  O(1)
 *   get(i) / contains                              O(n)
 */
public class DoublyLinkedList<T> {

    // ---- Node ----
    public static class Node<T> {
        public T data;
        public Node<T> prev, next;

        public Node(T data) {
            this.data = data;
        }
    }

    private final Node<T> sentinel;   // dummy head — simplifies boundary conditions
    private int size;

    public DoublyLinkedList() {
        sentinel = new Node<>(null);
        sentinel.next = sentinel;
        sentinel.prev = sentinel;
    }

    // O(1)
    public void addFirst(T data) {
        insertAfter(sentinel, new Node<>(data));
    }

    // O(1)
    public void addLast(T data) {
        insertAfter(sentinel.prev, new Node<>(data));
    }

    // O(1)
    public T removeFirst() {
        if (isEmpty()) throw new java.util.NoSuchElementException();
        return unlink(sentinel.next);
    }

    // O(1)
    public T removeLast() {
        if (isEmpty()) throw new java.util.NoSuchElementException();
        return unlink(sentinel.prev);
    }

    // O(1) — used by LRUCache (direct node reference)
    public void removeNode(Node<T> node) {
        unlink(node);
    }

    // O(1) — used by LRUCache to move node to front
    public void moveToFront(Node<T> node) {
        unlink(node);
        insertAfter(sentinel, node);
        size++;   // unlink decrements, we want net-zero change
    }

    public T peekFirst() {
        if (isEmpty()) throw new java.util.NoSuchElementException();
        return sentinel.next.data;
    }

    public T peekLast() {
        if (isEmpty()) throw new java.util.NoSuchElementException();
        return sentinel.prev.data;
    }

    public int size() { return size; }
    public boolean isEmpty() { return size == 0; }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("HEAD <-> ");
        Node<T> curr = sentinel.next;
        while (curr != sentinel) {
            sb.append(curr.data);
            if (curr.next != sentinel) sb.append(" <-> ");
            curr = curr.next;
        }
        return sb.append(" <-> TAIL").toString();
    }

    // ---------------------------------------------------------------
    private void insertAfter(Node<T> dest, Node<T> newNode) {
        newNode.prev = dest;
        newNode.next = dest.next;
        dest.next.prev = newNode;
        dest.next = newNode;
        size++;
    }

    private T unlink(Node<T> node) {
        node.prev.next = node.next;
        node.next.prev = node.prev;
        node.prev = node.next = null;
        size--;
        return node.data;
    }
}
