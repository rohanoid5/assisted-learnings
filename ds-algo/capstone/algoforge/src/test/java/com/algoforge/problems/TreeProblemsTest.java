package com.algoforge.problems;

import com.algoforge.problems.common.TreeNode;
import com.algoforge.problems.trees.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class TreeProblemsTest {

    // Helper: build tree from level-order array (null = absent)
    private TreeNode build(Integer... vals) {
        if (vals.length == 0 || vals[0] == null) return null;
        TreeNode root = new TreeNode(vals[0]);
        java.util.Queue<TreeNode> q = new java.util.LinkedList<>();
        q.offer(root);
        int i = 1;
        while (!q.isEmpty() && i < vals.length) {
            TreeNode curr = q.poll();
            if (i < vals.length && vals[i] != null) { curr.left = new TreeNode(vals[i]); q.offer(curr.left); }
            i++;
            if (i < vals.length && vals[i] != null) { curr.right = new TreeNode(vals[i]); q.offer(curr.right); }
            i++;
        }
        return root;
    }

    @Test void binaryTreeDepth() {
        TreeNode root = build(3, 9, 20, null, null, 15, 7);
        assertThat(BinaryTreeDepth.maxDepth(root)).isEqualTo(3);
        assertThat(BinaryTreeDepth.maxDepth(null)).isEqualTo(0);
    }

    @Test void lowestCommonAncestor() {
        TreeNode root = build(3, 5, 1, 6, 2, 0, 8, null, null, 7, 4);
        TreeNode p = root.left;        // 5
        TreeNode q = root.left.right;  // 2 (child of 5)

        // Validate the build: just check root value
        assertThat(root.val).isEqualTo(3);
        TreeNode lca = LowestCommonAncestor.lowestCommonAncestor(root, p, q);
        assertThat(lca.val).isEqualTo(5);
    }

    @Test void validateBSTValid() {
        TreeNode root = build(2, 1, 3);
        assertThat(ValidateBST.isValidBST(root)).isTrue();
    }

    @Test void validateBSTInvalid() {
        TreeNode root = build(5, 1, 4, null, null, 3, 6);
        assertThat(ValidateBST.isValidBST(root)).isFalse();
    }

    @Test void constructBinaryTree() {
        TreeNode root = ConstructBinaryTree.buildTree(
            new int[]{3,9,20,15,7},
            new int[]{9,3,15,20,7});
        assertThat(root.val).isEqualTo(3);
        assertThat(root.left.val).isEqualTo(9);
        assertThat(root.right.val).isEqualTo(20);
        assertThat(root.right.left.val).isEqualTo(15);
        assertThat(root.right.right.val).isEqualTo(7);
    }

    @Test void kthSmallestBST() {
        TreeNode bst = build(3, 1, 4, null, 2);
        assertThat(KthSmallestBST.kthSmallest(bst, 1)).isEqualTo(1);
        assertThat(KthSmallestBST.kthSmallest(bst, 2)).isEqualTo(2);
        assertThat(KthSmallestBST.kthSmallest(bst, 3)).isEqualTo(3);
    }

    @Test void binaryTreeMaxPathSum() {
        TreeNode root = build(-10, 9, 20, null, null, 15, 7);
        assertThat(BinaryTreeMaxPathSum.maxPathSum(root)).isEqualTo(42);
    }

    @Test void binaryTreeMaxPathSumSingleNode() {
        assertThat(BinaryTreeMaxPathSum.maxPathSum(new TreeNode(-3))).isEqualTo(-3);
    }
}
