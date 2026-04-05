package com.algoforge.problems.trees;

import com.algoforge.problems.common.TreeNode;

/**
 * LC #236 — Lowest Common Ancestor of a Binary Tree
 *
 * <b>Pattern:</b> DFS post-order — the LCA is the first node
 * where p and q are found in different subtrees (or the node itself is p or q).
 *
 * <pre>
 * Tree:       3
 *            / \
 *           5   1
 *          / \ / \
 *         6  2 0  8
 *           / \
 *          7   4
 *
 * LCA(5, 1) = 3   ← 5 found in left, 1 found in right
 * LCA(5, 4) = 5   ← 5 is an ancestor of 4; 5 itself is returned immediately
 *
 * Algorithm:
 *   If root is null, or root == p, or root == q → return root
 *   Recursively search left and right subtrees.
 *   If both return non-null → current node is the LCA.
 *   If only one returns non-null → propagate that result upward.
 * </pre>
 *
 * Time: O(n)  Space: O(h)
 */
public class LowestCommonAncestor {

    public static TreeNode lowestCommonAncestor(TreeNode root, TreeNode p, TreeNode q) {
        if (root == null || root == p || root == q) return root;

        TreeNode left  = lowestCommonAncestor(root.left,  p, q);
        TreeNode right = lowestCommonAncestor(root.right, p, q);

        // p found on one side, q on the other → this node is the LCA
        if (left != null && right != null) return root;

        // Both in the same subtree → propagate the non-null result
        return (left != null) ? left : right;
    }

    /**
     * LC #235 — LCA of a BST (optimised using BST properties).
     * No need for full DFS — use the BST ordering to navigate.
     *
     * Time: O(h)  Space: O(1) iterative
     */
    public static TreeNode lcaBST(TreeNode root, TreeNode p, TreeNode q) {
        int min = Math.min(p.val, q.val);
        int max = Math.max(p.val, q.val);

        while (root != null) {
            if      (root.val < min) root = root.right; // both nodes in right subtree
            else if (root.val > max) root = root.left;  // both nodes in left subtree
            else                     return root;        // root is between p and q → LCA
        }
        return null;
    }
}
