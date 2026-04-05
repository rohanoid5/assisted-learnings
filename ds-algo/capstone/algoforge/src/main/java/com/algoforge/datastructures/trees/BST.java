package com.algoforge.datastructures.trees;

import java.util.NoSuchElementException;

/**
 * BST<T> — Module 08 capstone deliverable (Part 1).
 *
 * Generic Binary Search Tree. T must implement Comparable<T>.
 *
 * Complexity (average / worst with balanced / degenerate input):
 *   insert / search / delete   O(log n) / O(n)
 *   inorder traversal          O(n)
 */
public class BST<T extends Comparable<T>> {

    public static class Node<T> {
        public T data;
        public Node<T> left, right;
        public Node(T data) { this.data = data; }
    }

    private Node<T> root;
    private int size;

    // O(log n) average
    public void insert(T data) {
        root = insertRec(root, data);
        size++;
    }

    // O(log n) average
    public boolean contains(T data) {
        Node<T> curr = root;
        while (curr != null) {
            int cmp = data.compareTo(curr.data);
            if      (cmp < 0) curr = curr.left;
            else if (cmp > 0) curr = curr.right;
            else    return true;
        }
        return false;
    }

    // O(log n) average
    public void delete(T data) {
        if (!contains(data)) throw new NoSuchElementException();
        root = deleteRec(root, data);
        size--;
    }

    // O(n) — returns sorted sequence for BST
    public java.util.List<T> inorder() {
        java.util.List<T> result = new java.util.ArrayList<>();
        inorderRec(root, result);
        return result;
    }

    // O(log n)
    public T min() {
        if (root == null) throw new NoSuchElementException();
        Node<T> curr = root;
        while (curr.left != null) curr = curr.left;
        return curr.data;
    }

    // O(log n)
    public T max() {
        if (root == null) throw new NoSuchElementException();
        Node<T> curr = root;
        while (curr.right != null) curr = curr.right;
        return curr.data;
    }

    public int size() { return size; }
    public boolean isEmpty() { return size == 0; }
    public Node<T> getRoot() { return root; }

    // ---------------------------------------------------------------
    private Node<T> insertRec(Node<T> node, T data) {
        if (node == null) return new Node<>(data);
        int cmp = data.compareTo(node.data);
        if      (cmp < 0) node.left  = insertRec(node.left, data);
        else if (cmp > 0) node.right = insertRec(node.right, data);
        // equal: no duplicate insertion
        return node;
    }

    private Node<T> deleteRec(Node<T> node, T data) {
        if (node == null) return null;
        int cmp = data.compareTo(node.data);
        if      (cmp < 0) node.left  = deleteRec(node.left, data);
        else if (cmp > 0) node.right = deleteRec(node.right, data);
        else {
            // Found — three cases
            if (node.left  == null) return node.right;
            if (node.right == null) return node.left;
            // Two children: replace with in-order successor (min of right subtree)
            Node<T> successor = node.right;
            while (successor.left != null) successor = successor.left;
            node.data  = successor.data;
            node.right = deleteRec(node.right, successor.data);
            size++;  // deleteRec will decrement again — compensate
        }
        return node;
    }

    private void inorderRec(Node<T> node, java.util.List<T> result) {
        if (node == null) return;
        inorderRec(node.left, result);
        result.add(node.data);
        inorderRec(node.right, result);
    }
}
