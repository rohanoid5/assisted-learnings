package com.algoforge.problems.trees;

import com.algoforge.problems.common.TreeNode;

/**
 * LC #124 — Binary Tree Maximum Path Sum
 *
 * <p>A path in a binary tree is a sequence of nodes where each pair of adjacent
 * nodes has an edge. A path does not need to go through the root.
 * Return the maximum path sum of any non-empty path.</p>
 *
 * <b>Pattern:</b> Post-order DFS — at each node compute gain, update global max.
 *
 * <pre>
 * Key insight: at each node, the contribution to a path going "through" it is:
 *   node.val + max(0, leftGain) + max(0, rightGain)
 * But a node can only contribute ONE branch (left OR right) to paths that pass
 * through its parent (since a path can't fork).
 *
 * Example:
 *        -10
 *        /  \
 *       9   20
 *          /  \
 *         15   7
 *
 *   At 15: gain=15, at 7: gain=7
 *   At 20: path through 20 = 15+20+7=42 → update maxSum=42
 *          return to parent: 20 + max(15,7) = 35
 *   At  9: gain=9
 *   At -10: path through -10 = 9 + (-10) + 35 = 34 < 42
 *           maxSum stays 42
 * </pre>
 *
 * Time: O(n)  Space: O(h)
 */
public class BinaryTreeMaxPathSum {

    private static int maxSum;

    public static int maxPathSum(TreeNode root) {
        maxSum = Integer.MIN_VALUE;
        gainFrom(root);
        return maxSum;
    }

    private static int gainFrom(TreeNode node) {
        if (node == null) return 0;
        int leftGain  = Math.max(0, gainFrom(node.left));
        int rightGain = Math.max(0, gainFrom(node.right));
        // Path through this node (cannot be used as return value — it forks)
        maxSum = Math.max(maxSum, node.val + leftGain + rightGain);
        // Return the best single-branch gain to the parent
        return node.val + Math.max(leftGain, rightGain);
    }
}
