package com.algoforge.problems.trees;

import com.algoforge.problems.common.TreeNode;

/**
 * LC #230 — Kth Smallest Element in a BST
 *
 * <p>Given the root of a BST and an integer k, return the kth smallest value
 * (1-indexed) of all the values in the tree.</p>
 *
 * <b>Pattern:</b> In-order traversal — BST in-order gives sorted ascending sequence.
 *
 * <pre>
 * BST:     3
 *         / \
 *        1   4
 *         \
 *          2
 *
 * In-order: [1, 2, 3, 4]
 * k=1 → 1,  k=2 → 2,  k=3 → 3
 *
 * Iterative approach: use a stack to simulate in-order without storing all values.
 * Stop as soon as the kth element is reached.
 * </pre>
 *
 * Time: O(H + k) where H = tree height   Space: O(H)
 */
public class KthSmallestBST {

    public static int kthSmallest(TreeNode root, int k) {
        java.util.ArrayDeque<TreeNode> stack = new java.util.ArrayDeque<>();
        TreeNode curr = root;
        int count = 0;
        while (curr != null || !stack.isEmpty()) {
            while (curr != null) {   // go as far left as possible
                stack.push(curr);
                curr = curr.left;
            }
            curr = stack.pop();
            if (++count == k) return curr.val;
            curr = curr.right;
        }
        throw new IllegalArgumentException("k is out of range");
    }
}
