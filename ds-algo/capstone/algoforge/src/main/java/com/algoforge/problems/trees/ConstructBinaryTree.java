package com.algoforge.problems.trees;

import com.algoforge.problems.common.TreeNode;

/**
 * LC #105 — Construct Binary Tree from Preorder and Inorder Traversal
 *
 * <p>Given two integer arrays preorder and inorder, construct and return the binary tree.</p>
 *
 * <b>Pattern:</b> Divide & Conquer — root = preorder[0]; split inorder at root.
 *
 * <pre>
 * Key observations:
 *   preorder[0]         = root
 *   inorder[0..index-1] = left subtree nodes
 *   inorder[index+1..]  = right subtree nodes
 *   leftSize = index    (number of left subtree nodes)
 *   preorder[1..leftSize] = left subtree preorder
 *   preorder[leftSize+1..] = right subtree preorder
 *
 * Trace: preorder=[3,9,20,15,7], inorder=[9,3,15,20,7]
 *   root=3, index=1 (3 is at pos 1 in inorder)
 *   left: preorder=[9], inorder=[9] → leaf node 9
 *   right: preorder=[20,15,7], inorder=[15,20,7]
 *     root=20, index=1 → left:15, right:7
 *
 *       3
 *      / \
 *     9  20
 *       /  \
 *      15   7
 * </pre>
 *
 * Time: O(n)  Space: O(n) for the HashMap + O(h) recursion stack
 */
public class ConstructBinaryTree {

    public static TreeNode buildTree(int[] preorder, int[] inorder) {
        java.util.Map<Integer, Integer> inorderMap = new java.util.HashMap<>();
        for (int i = 0; i < inorder.length; i++) inorderMap.put(inorder[i], i);
        return build(preorder, 0, 0, inorder.length - 1, inorderMap);
    }

    private static TreeNode build(int[] preorder, int preStart, int inStart, int inEnd,
                                   java.util.Map<Integer, Integer> inorderMap) {
        if (preStart >= preorder.length || inStart > inEnd) return null;
        int rootVal = preorder[preStart];
        TreeNode root = new TreeNode(rootVal);
        int inIdx = inorderMap.get(rootVal);
        int leftSize = inIdx - inStart;
        root.left  = build(preorder, preStart + 1,            inStart,    inIdx - 1, inorderMap);
        root.right = build(preorder, preStart + 1 + leftSize, inIdx + 1,  inEnd,     inorderMap);
        return root;
    }
}
