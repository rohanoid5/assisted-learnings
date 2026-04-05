package com.algoforge.problems.trees;

import com.algoforge.problems.common.TreeNode;

/**
 * LC #98 — Validate Binary Search Tree
 *
 * <b>Pattern:</b> DFS with valid range [min, max] (top-down)
 *
 * <pre>
 * BST property: for each node N,
 *   all nodes in left subtree < N.val
 *   all nodes in right subtree > N.val
 *
 * Common mistake: only comparing with direct parent is WRONG.
 *
 * Invalid BST:     5
 *                 / \
 *                1   4     ← 4 < 5 ✓ locally, but...
 *               / \
 *              3   6       ← 3 > 1 ✓, 6 > 1 ✓ locally, but 6 > 5! INVALID
 *
 * Correct approach: pass down valid (min, max) range.
 *   isValid(5, -∞, +∞)
 *   isValid(1, -∞, 5)           ← left child must be < 5
 *   isValid(4, 5, +∞)           ← right child must be > 5: 4 < 5 → FALSE
 * </pre>
 *
 * Time: O(n)  Space: O(h)
 */
public class ValidateBST {

    public static boolean isValidBST(TreeNode root) {
        return validate(root, Long.MIN_VALUE, Long.MAX_VALUE);
    }

    private static boolean validate(TreeNode node, long min, long max) {
        if (node == null) return true;
        if (node.val <= min || node.val >= max) return false;
        return validate(node.left,  min,      node.val)
            && validate(node.right, node.val, max);
    }

    /**
     * Alternate approach: in-order traversal of a valid BST must be strictly increasing.
     * Time: O(n)  Space: O(h)
     */
    public static boolean isValidBSTInorder(TreeNode root) {
        long[] prev = {Long.MIN_VALUE};
        return inorder(root, prev);
    }

    private static boolean inorder(TreeNode node, long[] prev) {
        if (node == null) return true;
        if (!inorder(node.left, prev))       return false;
        if (node.val <= prev[0])             return false; // not strictly increasing
        prev[0] = node.val;
        return inorder(node.right, prev);
    }
}
