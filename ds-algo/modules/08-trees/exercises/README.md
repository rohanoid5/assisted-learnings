# Module 08 — Trees: Exercises

## Overview

These exercises cover the full tree module. Complete them in order — they build on each other. Each has a difficulty rating and expected complexity. Aim to solve each within the time limit before checking the solution.

| # | Problem | Difficulty | Time Limit | LC # |
|---|---------|-----------|------------|------|
| 1 | Maximum Depth of Binary Tree | Easy | 10 min | 104 |
| 2 | Validate Binary Search Tree | Medium | 15 min | 98 |
| 3 | Binary Tree Level Order Traversal | Medium | 15 min | 102 |
| 4 | Lowest Common Ancestor of a Binary Tree | Medium | 20 min | 236 |
| 5 | Serialize and Deserialize Binary Tree | Hard | 30 min | 297 |

---

## Exercise 1 — Maximum Depth of Binary Tree (LC #104)

**Difficulty:** Easy  
**Topic:** DFS, recursion

**Goal:** Given the root of a binary tree, return its maximum depth (number of nodes along the longest root-to-leaf path).

**Example:**
```
Input:         3
              / \
             9  20
               /  \
              15   7

Output: 3
```

**Steps:**
1. What is the base case? (What does an empty tree return?)
2. How do you combine results from left and right children?
3. Write the recursive solution first, then try iterative BFS.

<details>
<summary>Solution</summary>

```java
// Recursive DFS — O(n) time, O(h) space
public int maxDepth(TreeNode root) {
    if (root == null) return 0;
    return 1 + Math.max(maxDepth(root.left), maxDepth(root.right));
}

// Iterative BFS — O(n) time, O(w) space (w = max width)
public int maxDepthBFS(TreeNode root) {
    if (root == null) return 0;
    Queue<TreeNode> queue = new LinkedList<>();
    queue.offer(root);
    int depth = 0;
    while (!queue.isEmpty()) {
        int size = queue.size();
        depth++;
        for (int i = 0; i < size; i++) {
            TreeNode node = queue.poll();
            if (node.left  != null) queue.offer(node.left);
            if (node.right != null) queue.offer(node.right);
        }
    }
    return depth;
}
```

**Complexity:** O(n) time, O(h) space for DFS (call stack), O(w) for BFS (queue).

</details>

---

## Exercise 2 — Validate Binary Search Tree (LC #98)

**Difficulty:** Medium  
**Topic:** BST, DFS

**Goal:** Given the root of a binary tree, determine if it is a valid binary search tree.

**Example:**
```
Input:    5
         / \
        1   4
           / \
          3   6
Output: false
Reason: Root is 5, but right child's subtree contains 3 < 5.
```

**Steps:**
1. Why can't you just check `node.left.val < node.val < node.right.val`?
2. What additional information must you thread through the recursion?
3. Handle the edge case where node values equal `Integer.MIN_VALUE` or `Integer.MAX_VALUE`.

<details>
<summary>Solution</summary>

```java
public boolean isValidBST(TreeNode root) {
    return validate(root, Long.MIN_VALUE, Long.MAX_VALUE);
}

private boolean validate(TreeNode node, long min, long max) {
    if (node == null) return true;
    // Node must be strictly between min and max
    if (node.val <= min || node.val >= max) return false;
    // Left subtree: all values must be < node.val
    // Right subtree: all values must be > node.val
    return validate(node.left,  min,      node.val) &&
           validate(node.right, node.val, max);
}
```

**Alternative — inorder must be strictly increasing:**
```java
public boolean isValidBST(TreeNode root) {
    long prev = Long.MIN_VALUE;
    Deque<TreeNode> stack = new ArrayDeque<>();
    TreeNode curr = root;
    while (curr != null || !stack.isEmpty()) {
        while (curr != null) { stack.push(curr); curr = curr.left; }
        curr = stack.pop();
        if (curr.val <= prev) return false;
        prev = curr.val;
        curr = curr.right;
    }
    return true;
}
```

**Complexity:** O(n) time, O(h) space.

</details>

---

## Exercise 3 — Binary Tree Level Order Traversal (LC #102)

**Difficulty:** Medium  
**Topic:** BFS

**Goal:** Return the level order traversal of a binary tree's values as `List<List<Integer>>`.

**Example:**
```
Input:         3
              / \
             9  20
               /  \
              15   7

Output: [[3], [9, 20], [15, 7]]
```

**Steps:**
1. Use a queue. When do you snapshot the level size?
2. What's the inner loop responsibility vs the outer loop?

<details>
<summary>Solution</summary>

```java
public List<List<Integer>> levelOrder(TreeNode root) {
    List<List<Integer>> result = new ArrayList<>();
    if (root == null) return result;

    Queue<TreeNode> queue = new LinkedList<>();
    queue.offer(root);

    while (!queue.isEmpty()) {
        int levelSize = queue.size();         // snapshot before adding children
        List<Integer> level = new ArrayList<>();
        for (int i = 0; i < levelSize; i++) {
            TreeNode node = queue.poll();
            level.add(node.val);
            if (node.left  != null) queue.offer(node.left);
            if (node.right != null) queue.offer(node.right);
        }
        result.add(level);
    }
    return result;
}
```

**Complexity:** O(n) time, O(w) space where w = max width (can be O(n) for a perfect tree bottom level).

</details>

---

## Exercise 4 — Lowest Common Ancestor of a Binary Tree (LC #236)

**Difficulty:** Medium  
**Topic:** DFS, postorder

**Goal:** Find the lowest common ancestor (LCA) of two nodes p and q in a binary tree.

**Example:**
```
         3
        / \
       5   1
      / \ / \
     6  2 0  8
       / \
      7   4

LCA(5, 1) = 3  (5 and 1 are in different subtrees of 3)
LCA(5, 4) = 5  (4 is a descendant of 5, so 5 itself is LCA)
```

**Steps:**
1. What does it mean to "find" p or q — what should the function return?
2. When does the current node become the LCA?
3. What does the algorithm return when neither p nor q is in a subtree?

<details>
<summary>Solution</summary>

```java
public TreeNode lowestCommonAncestor(TreeNode root, TreeNode p, TreeNode q) {
    // Base cases: empty node or found one of the targets
    if (root == null || root == p || root == q) return root;

    TreeNode left  = lowestCommonAncestor(root.left,  p, q);
    TreeNode right = lowestCommonAncestor(root.right, p, q);

    // If both sides returned non-null, p and q are on opposite sides
    if (left != null && right != null) return root;

    // Otherwise, LCA is in the non-null side
    return (left != null) ? left : right;
}
```

**Trace for LCA(5, 4):**
```
LCA(3, 5, 4):
  left  = LCA(5, 5, 4) → returns 5 (root == p)  [does not recurse further]
  right = LCA(1, 5, 4) → returns null (neither found)
  one side null → return left = 5  ✓
```

**Complexity:** O(n) time — may need to visit every node. O(h) space.

</details>

---

## Exercise 5 — Serialize and Deserialize Binary Tree (LC #297)

**Difficulty:** Hard  
**Topic:** Preorder DFS, string manipulation

**Goal:** Design an algorithm to serialize a binary tree to a string and deserialize that string back to the original tree.

**Example:**
```
Input:    1
         / \
        2   3
           / \
          4   5

Serialized: "1,2,null,null,3,4,null,null,5,null,null,"
Deserialized: same tree
```

**Steps:**
1. What traversal preserves enough information to reconstruct the tree uniquely?
2. How do you represent null nodes?
3. How do you consume tokens during deserialization in the correct order?

<details>
<summary>Solution</summary>

```java
// Preorder traversal with null markers
public String serialize(TreeNode root) {
    StringBuilder sb = new StringBuilder();
    serializeHelper(root, sb);
    return sb.toString();
}

private void serializeHelper(TreeNode node, StringBuilder sb) {
    if (node == null) {
        sb.append("null,");
        return;
    }
    sb.append(node.val).append(',');       // visit root FIRST
    serializeHelper(node.left,  sb);      // then left
    serializeHelper(node.right, sb);      // then right
}

public TreeNode deserialize(String data) {
    Queue<String> tokens = new LinkedList<>(Arrays.asList(data.split(",")));
    return deserializeHelper(tokens);
}

private TreeNode deserializeHelper(Queue<String> tokens) {
    String token = tokens.poll();
    if ("null".equals(token)) return null;
    TreeNode node = new TreeNode(Integer.parseInt(token));
    node.left  = deserializeHelper(tokens); // consume left subtree tokens
    node.right = deserializeHelper(tokens); // consume right subtree tokens
    return node;
}
```

**Why preorder:** The first token is always the root. Knowing the root, we then recursively rebuild left and right subtrees by consuming the remaining tokens in order.

**Complexity:**
- Serialize: O(n) time, O(n) space (string + call stack)
- Deserialize: O(n) time, O(n) space (queue + call stack)

**Follow-up:** Can you use level order instead? Yes — encode using BFS and decode using BFS. It's more verbose but equally correct.

</details>

---

## Bonus Challenge

**Problem:** Given a binary tree, find the path between two nodes (not necessarily root-to-leaf). Return the list of values on that path. (Combination of LCA + path tracking)

**Hint:** Find LCA first. Then build path from each node to LCA using DFS. Reverse one and concatenate.

<details>
<summary>Solution</summary>

```java
public List<Integer> pathBetweenNodes(TreeNode root, int src, int dst) {
    // Find LCA
    TreeNode lca = findLCA(root, src, dst);

    // Build path from LCA to src (reverse for src→LCA)
    List<Integer> pathToSrc = new ArrayList<>();
    dfsPath(lca, src, pathToSrc);
    Collections.reverse(pathToSrc);  // now src → LCA

    // Build path from LCA to dst (forward for LCA→dst)
    List<Integer> pathToDst = new ArrayList<>();
    dfsPath(lca, dst, pathToDst);

    // Don't double-count LCA: combine pathToSrc + pathToDst[1:]
    pathToSrc.addAll(pathToDst.subList(1, pathToDst.size()));
    return pathToSrc;
}

private boolean dfsPath(TreeNode node, int target, List<Integer> path) {
    if (node == null) return false;
    path.add(node.val);
    if (node.val == target) return true;
    if (dfsPath(node.left, target, path) || dfsPath(node.right, target, path))
        return true;
    path.remove(path.size() - 1); // backtrack
    return false;
}

private TreeNode findLCA(TreeNode root, int p, int q) {
    if (root == null || root.val == p || root.val == q) return root;
    TreeNode left  = findLCA(root.left,  p, q);
    TreeNode right = findLCA(root.right, p, q);
    return (left != null && right != null) ? root : (left != null ? left : right);
}
```

</details>
