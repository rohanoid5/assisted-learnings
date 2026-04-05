package com.algoforge.problems.common;

/**
 * Shared TreeNode used across all tree problem solutions in Part B.
 *
 * <p>Matches the standard LeetCode binary tree node definition.</p>
 */
public class TreeNode {
    public int val;
    public TreeNode left, right;

    public TreeNode(int val) { this.val = val; }
    public TreeNode(int val, TreeNode left, TreeNode right) {
        this.val   = val;
        this.left  = left;
        this.right = right;
    }

    /**
     * Builds a tree from level-order array (null = missing node).
     * Example: of(1, 2, 3, null, 4) →
     *
     *       1
     *      / \
     *     2   3
     *      \
     *       4
     */
    public static TreeNode of(Integer... vals) {
        if (vals == null || vals.length == 0 || vals[0] == null) return null;
        TreeNode root = new TreeNode(vals[0]);
        java.util.Queue<TreeNode> queue = new java.util.LinkedList<>();
        queue.offer(root);
        int i = 1;
        while (!queue.isEmpty() && i < vals.length) {
            TreeNode cur = queue.poll();
            if (i < vals.length && vals[i] != null) {
                cur.left = new TreeNode(vals[i]);
                queue.offer(cur.left);
            }
            i++;
            if (i < vals.length && vals[i] != null) {
                cur.right = new TreeNode(vals[i]);
                queue.offer(cur.right);
            }
            i++;
        }
        return root;
    }
}
