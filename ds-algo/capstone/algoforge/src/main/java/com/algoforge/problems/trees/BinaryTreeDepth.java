package com.algoforge.problems.trees;

import com.algoforge.problems.common.TreeNode;

/**
 * LC #104 — Maximum Depth of Binary Tree
 * LC #543 — Diameter of Binary Tree (bonus)
 *
 * <b>Pattern:</b> DFS post-order
 *
 * <pre>
 * Tree:       3
 *            / \
 *           9  20
 *             /  \
 *            15   7
 *
 * maxDepth:
 *   depth(null) = 0
 *   depth(9) = 1 + max(depth(null), depth(null)) = 1
 *   depth(15) = 1, depth(7) = 1
 *   depth(20) = 1 + max(1, 1) = 2
 *   depth(3)  = 1 + max(1, 2) = 3  ← answer
 *
 * Diameter:
 *   For each node, the diameter through it = leftHeight + rightHeight.
 *   Track the global maximum.
 * </pre>
 *
 * Time: O(n)  Space: O(h) where h = tree height (call stack)
 */
public class BinaryTreeDepth {

    /** Returns the maximum depth (number of nodes along the longest root-to-leaf path). */
    public static int maxDepth(TreeNode root) {
        if (root == null) return 0;
        return 1 + Math.max(maxDepth(root.left), maxDepth(root.right));
    }

    /** Returns the diameter (longest path between any two nodes, in edges). LC #543. */
    public static int diameterOfBinaryTree(TreeNode root) {
        int[] maxDiameter = {0};
        heightAndDiameter(root, maxDiameter);
        return maxDiameter[0];
    }

    private static int heightAndDiameter(TreeNode node, int[] maxDiameter) {
        if (node == null) return 0;
        int leftH  = heightAndDiameter(node.left,  maxDiameter);
        int rightH = heightAndDiameter(node.right, maxDiameter);
        maxDiameter[0] = Math.max(maxDiameter[0], leftH + rightH);
        return 1 + Math.max(leftH, rightH);
    }
}
