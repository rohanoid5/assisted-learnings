package com.algoforge.datastructures.trees;

/**
 * AVL Tree — a self-balancing Binary Search Tree.
 *
 * <p>Every node maintains a <em>balance factor</em> = height(left) - height(right).
 * After every insert or delete, the tree re-balances itself via rotations so that
 * the balance factor of every node stays in {-1, 0, 1}.</p>
 *
 * <p>This guarantees O(log n) worst-case for insert, delete, and search —
 * unlike a plain BST which degrades to O(n) on sorted input.</p>
 *
 * <pre>
 * Rotations (triggered when |balance| > 1):
 *
 *  Left-Left (LL) case  →  Right Rotation
 *      z                      y
 *     / \                   /   \
 *    y   T4    ──►         x     z
 *   / \                   / \   / \
 *  x   T3                T1 T2 T3 T4
 *
 *  Right-Right (RR) case → Left Rotation
 *    z                          y
 *   / \                       /   \
 *  T1   y       ──►          z     x
 *      / \                  / \   / \
 *     T2   x               T1 T2 T3 T4
 *
 *  Left-Right (LR) case  → Left rotate on child, then Right rotate on root
 *  Right-Left (RL) case  → Right rotate on child, then Left rotate on root
 * </pre>
 *
 * Time:  O(log n) insert / delete / search
 * Space: O(n)
 */
public class AVLTree {

    // ── Node ────────────────────────────────────────────────────────────────

    private static class Node {
        int key;
        Node left, right;
        int height;

        Node(int key) {
            this.key = key;
            this.height = 1; // new node is a leaf
        }
    }

    // ── State ────────────────────────────────────────────────────────────────

    private Node root;
    private int size;

    // ── Public API ──────────────────────────────────────────────────────────

    /** Insert a key. Duplicate keys are ignored. */
    public void insert(int key) {
        root = insertRec(root, key);
    }

    /** Delete a key. No-op if not present. */
    public void delete(int key) {
        root = deleteRec(root, key);
    }

    /** Returns true if the key exists in the tree. O(log n). */
    public boolean contains(int key) {
        Node cur = root;
        while (cur != null) {
            if (key == cur.key) return true;
            cur = key < cur.key ? cur.left : cur.right;
        }
        return false;
    }

    /** Returns the minimum key, or Integer.MIN_VALUE if empty. */
    public int min() {
        if (root == null) throw new java.util.NoSuchElementException("Tree is empty");
        return minNode(root).key;
    }

    /** Returns the maximum key, or throws if empty. */
    public int max() {
        if (root == null) throw new java.util.NoSuchElementException("Tree is empty");
        Node cur = root;
        while (cur.right != null) cur = cur.right;
        return cur.key;
    }

    public int size()   { return size; }
    public boolean isEmpty() { return size == 0; }

    /** Returns the height of the tree root (0 for a single node). */
    public int height() { return height(root); }

    // ── Private Helpers ─────────────────────────────────────────────────────

    private Node insertRec(Node node, int key) {
        // Standard BST insert
        if (node == null) { size++; return new Node(key); }
        if      (key < node.key) node.left  = insertRec(node.left,  key);
        else if (key > node.key) node.right = insertRec(node.right, key);
        else return node; // duplicate — ignore

        // Update height, then rebalance
        updateHeight(node);
        return rebalance(node, key);
    }

    private Node deleteRec(Node node, int key) {
        if (node == null) return null;

        if (key < node.key) {
            node.left = deleteRec(node.left, key);
        } else if (key > node.key) {
            node.right = deleteRec(node.right, key);
        } else {
            // Found the node to delete
            size--;
            if (node.left == null)  return node.right;
            if (node.right == null) return node.left;

            // Node has two children: replace with in-order successor
            Node successor = minNode(node.right);
            node.key = successor.key;
            size++; // will be decremented again in recursive delete
            node.right = deleteRec(node.right, successor.key);
        }

        updateHeight(node);
        return rebalanceAfterDelete(node);
    }

    /**
     * Restores AVL property at `node` after an INSERT.
     * We pass the newly inserted key to determine which rotation case applies.
     */
    private Node rebalance(Node node, int key) {
        int balance = balanceFactor(node);

        // LL case: inserted into left subtree of left child
        if (balance > 1 && key < node.left.key)
            return rotateRight(node);

        // RR case: inserted into right subtree of right child
        if (balance < -1 && key > node.right.key)
            return rotateLeft(node);

        // LR case: inserted into right subtree of left child
        if (balance > 1 && key > node.left.key) {
            node.left = rotateLeft(node.left);
            return rotateRight(node);
        }

        // RL case: inserted into left subtree of right child
        if (balance < -1 && key < node.right.key) {
            node.right = rotateRight(node.right);
            return rotateLeft(node);
        }

        return node; // already balanced
    }

    /** Rebalance after DELETE — uses balance factor only (no key hint). */
    private Node rebalanceAfterDelete(Node node) {
        int balance = balanceFactor(node);

        if (balance > 1) {
            if (balanceFactor(node.left) >= 0) return rotateRight(node);
            node.left = rotateLeft(node.left);
            return rotateRight(node);
        }
        if (balance < -1) {
            if (balanceFactor(node.right) <= 0) return rotateLeft(node);
            node.right = rotateRight(node.right);
            return rotateLeft(node);
        }
        return node;
    }

    /*
     * Right rotation around `y`:
     *
     *      y                x
     *     / \             /   \
     *    x   T3   →      T1    y
     *   / \                   / \
     *  T1  T2                T2  T3
     */
    private Node rotateRight(Node y) {
        Node x  = y.left;
        Node t2 = x.right;

        x.right = y;
        y.left  = t2;

        updateHeight(y);
        updateHeight(x);
        return x;
    }

    /*
     * Left rotation around `x`:
     *
     *   x                    y
     *  / \                 /   \
     * T1   y      →       x    T3
     *     / \            / \
     *    T2  T3         T1  T2
     */
    private Node rotateLeft(Node x) {
        Node y  = x.right;
        Node t2 = y.left;

        y.left  = x;
        x.right = t2;

        updateHeight(x);
        updateHeight(y);
        return y;
    }

    private void updateHeight(Node n) {
        n.height = 1 + Math.max(height(n.left), height(n.right));
    }

    private int height(Node n)        { return n == null ? 0 : n.height; }
    private int balanceFactor(Node n) { return n == null ? 0 : height(n.left) - height(n.right); }

    private Node minNode(Node n) {
        while (n.left != null) n = n.left;
        return n;
    }
}
