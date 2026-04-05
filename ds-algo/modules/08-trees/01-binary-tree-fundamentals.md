# 8.1 — Binary Tree Fundamentals

## What Is a Tree?

A **tree** is a connected, acyclic, undirected graph. In CS, we root it: one node is designated the **root**, and every other node is reachable from it via exactly one path going downward.

```
          1          ← root (depth 0, level 1)
        /   \
       2     3       ← depth 1
      / \     \
     4   5     6    ← depth 2
        /
       7             ← depth 3  (leaf)
```

---

## Essential Vocabulary

| Term | Meaning |
|------|---------|
| **Root** | Topmost node with no parent |
| **Leaf** | Node with no children |
| **Height of a node** | Longest path from that node down to any leaf |
| **Depth of a node** | Distance from root to that node |
| **Height of tree** | Height of root node |
| **Level** | depth + 1 |
| **Subtree** | A node plus all its descendants |
| **Ancestor / Descendant** | Nodes above / below on the same path |
| **Diameter** | Longest path between any two nodes (may not pass through root) |

> **Height of empty tree = -1. Height of a single node = 0.**  
> (Some books define height of empty = 0; be consistent within a problem.)

---

## Tree Shape Properties

```
Full Binary Tree:          Perfect Binary Tree:       Complete Binary Tree:
Every node has 0 or 2      All leaves at same level,  All levels full except
children                   every internal node has 2  possibly last, filled
                           children                   left to right

    1                          1                          1
   / \                        / \                        / \
  2   3                      2   3                      2   3
 / \   \   ✗              / \ / \                    / \ /
4   5   6               4  5 6   7                  4  5 6
```

### Key Relationships in a Perfect Tree of Height h

- Total nodes: $2^{h+1} - 1$
- Leaves: $2^h$
- Internal nodes: $2^h - 1$

### Balanced Binary Tree

A binary tree where, for every node, the height difference between its left and right subtrees is at most 1.

```
Balanced:            Not balanced:
     1                    1
    / \                  /
   2   3                2
  / \                  /
 4   5                3
```

---

## TreeNode Definition

The `TreeNode` class used in almost all LeetCode tree problems:

```java
public class TreeNode {
    int val;
    TreeNode left;
    TreeNode right;

    TreeNode(int val) {
        this.val = val;
    }
}
```

---

## Computing Height

```java
// Height: longest path from node to a leaf
// Base case: empty node → -1
public int height(TreeNode node) {
    if (node == null) return -1;
    int leftH  = height(node.left);
    int rightH = height(node.right);
    return 1 + Math.max(leftH, rightH);
}
```

**Trace on the opening tree:**
```
height(4) = 1 + max(-1,-1) = 0
height(7) = 0
height(5) = 1 + max(0,-1) = 1
height(2) = 1 + max(0,1)  = 2
height(6) = 0
height(3) = 1 + max(-1,0) = 1
height(1) = 1 + max(2,1)  = 3
```

---

## Computing Diameter

The diameter can pass through the root or entirely within a subtree:

```java
private int maxDiameter = 0;

public int diameterOfBinaryTree(TreeNode root) {
    height(root);
    return maxDiameter;
}

private int height(TreeNode node) {
    if (node == null) return -1;
    int left  = height(node.left);
    int right = height(node.right);
    // Path through this node = left edges + right edges
    maxDiameter = Math.max(maxDiameter, (left + 1) + (right + 1));
    return 1 + Math.max(left, right);
}
```

```
Tree:        1
            / \
           2   3
          / \
         4   5

Diameter = 3 (path 4→2→5→1→3 OR 3→1→2→4/5)
At node 2: (0+1) + (0+1) = 2
At node 1: (1+1) + (0+1) = 4   → but 4 isn't right...
Actually at node 1: left depth=1, right depth=0
  path length = (1+1) + (0+1) = 3  ✓
```

---

## Is It Balanced?

LC #110 — Balanced Binary Tree

```java
public boolean isBalanced(TreeNode root) {
    return checkHeight(root) != -2;
}

// Returns height if balanced, -2 if unbalanced
private int checkHeight(TreeNode node) {
    if (node == null) return -1;
    int left = checkHeight(node.left);
    if (left == -2) return -2;
    int right = checkHeight(node.right);
    if (right == -2) return -2;
    if (Math.abs(left - right) > 1) return -2;
    return 1 + Math.max(left, right);
}
```

This is O(n) — one pass instead of the naive O(n²) approach.

---

## Try It Yourself

**Problem:** Given the root of a binary tree, find the maximum depth (number of nodes on the longest root-to-leaf path). (LC #104)

```
Input:     3
          / \
         9  20
           /  \
          15   7

Expected: 3
```

<details>
<summary>Solution</summary>

```java
public int maxDepth(TreeNode root) {
    if (root == null) return 0;
    return 1 + Math.max(maxDepth(root.left), maxDepth(root.right));
}
```

**Why it works:** At each node we recurse on both subtrees, take the deeper one, and add 1 for the current node. Leaf nodes return 1 (1 + max(0, 0)). Empty returns 0.

**Complexity:** O(n) time (visit every node), O(h) space (call stack = height).

</details>

---

## Capstone Connection

In AlgoForge, add `datastructures/trees/TreeUtils.java`:

```java
package com.algoforge.datastructures.trees;

public class TreeUtils {
    public static int height(TreeNode node) { ... }
    public static int diameter(TreeNode root) { ... }
    public static boolean isBalanced(TreeNode root) { ... }
    public static int maxDepth(TreeNode root) { ... }
}
```
