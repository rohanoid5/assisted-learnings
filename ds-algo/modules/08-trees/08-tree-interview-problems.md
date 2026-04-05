# 8.8 — Tree Interview Problem Patterns

## Pattern Recognition Guide

Before coding any tree problem, identify which category it falls into:

| Pattern | Signal Words | Traversal | Examples |
|---------|-------------|-----------|---------|
| **Subtree property** | "path sum", "diameter", "height" | Postorder DFS | LC 112, 124, 543 |
| **BST property** | "validate", "kth smallest", "sorted" | Inorder | LC 98, 230, 173 |
| **Level structure** | "by level", "right side", "zigzag" | BFS | LC 102, 103, 199 |
| **Path from root** | "root-to-leaf path", "serialize" | Preorder | LC 112, 113, 297 |
| **LCA** | "lowest common ancestor" | Postorder DFS | LC 236, 235 |
| **Construct tree** | "build from traversal" | Preorder+Inorder | LC 105, 106 |

---

## Pattern 1: Path Sum Problems

Use **postorder DFS** — compute subtree result bottom-up.

```java
// LC #112 - Path Sum (root-to-leaf sum equals target)
public boolean hasPathSum(TreeNode root, int targetSum) {
    if (root == null) return false;
    if (root.left == null && root.right == null) // leaf
        return root.val == targetSum;
    return hasPathSum(root.left,  targetSum - root.val) ||
           hasPathSum(root.right, targetSum - root.val);
}

// LC #124 - Binary Tree Maximum Path Sum (any path, can go through root)
private int maxSum = Integer.MIN_VALUE;

public int maxPathSum(TreeNode root) {
    maxGain(root);
    return maxSum;
}

private int maxGain(TreeNode node) {
    if (node == null) return 0;
    // Only include subtree if it adds value (max with 0)
    int left  = Math.max(maxGain(node.left),  0);
    int right = Math.max(maxGain(node.right), 0);

    // Update global max (path through this node)
    maxSum = Math.max(maxSum, node.val + left + right);

    // Return max gain if this node were part of a path going up
    return node.val + Math.max(left, right);
}
```

---

## Pattern 2: Lowest Common Ancestor

**General binary tree** (LC #236): Use postorder DFS, return node when found.

```java
public TreeNode lowestCommonAncestor(TreeNode root, TreeNode p, TreeNode q) {
    if (root == null || root == p || root == q) return root;

    TreeNode left  = lowestCommonAncestor(root.left,  p, q);
    TreeNode right = lowestCommonAncestor(root.right, p, q);

    // If found in both subtrees, root is LCA
    if (left != null && right != null) return root;
    // Otherwise, LCA is in whichever side found something
    return (left != null) ? left : right;
}
```

```
Find LCA of 5 and 1 in:
         3
        / \
       5   1
      / \ / \
     6  2 0  8
       / \
      7   4

LCA(3, 5, 1):
  left  = LCA(5, 5, 1) → returns 5 (root == p)
  right = LCA(1, 5, 1) → returns 1 (root == q)
  Both non-null → return 3  ✓
```

---

## Pattern 3: Serialize and Deserialize Binary Tree (LC #297)

Key insight: preorder with `null` markers uniquely encodes any binary tree.

```java
public String serialize(TreeNode root) {
    StringBuilder sb = new StringBuilder();
    serializeHelper(root, sb);
    return sb.toString();
}

private void serializeHelper(TreeNode node, StringBuilder sb) {
    if (node == null) { sb.append("null,"); return; }
    sb.append(node.val).append(',');
    serializeHelper(node.left,  sb);
    serializeHelper(node.right, sb);
}

public TreeNode deserialize(String data) {
    Queue<String> queue = new LinkedList<>(Arrays.asList(data.split(",")));
    return deserializeHelper(queue);
}

private TreeNode deserializeHelper(Queue<String> queue) {
    String token = queue.poll();
    if ("null".equals(token)) return null;
    TreeNode node = new TreeNode(Integer.parseInt(token));
    node.left  = deserializeHelper(queue);
    node.right = deserializeHelper(queue);
    return node;
}
```

---

## Pattern 4: Tree with Parent Pointers — All Nodes at Distance K (LC #863)

When edges can go upward, BFS with visited set:

```java
public List<Integer> distanceK(TreeNode root, TreeNode target, int k) {
    // Step 1: build parent map via DFS
    Map<TreeNode, TreeNode> parent = new HashMap<>();
    dfsParent(root, null, parent);

    // Step 2: BFS from target treating tree as undirected graph
    Queue<TreeNode> queue = new LinkedList<>();
    Set<TreeNode> visited = new HashSet<>();
    queue.offer(target);
    visited.add(target);
    int dist = 0;

    while (!queue.isEmpty()) {
        if (dist == k) {
            // All nodes in current level are at distance k
            return queue.stream().map(n -> n.val).collect(Collectors.toList());
        }
        int size = queue.size();
        for (int i = 0; i < size; i++) {
            TreeNode node = queue.poll();
            if (node.left  != null && !visited.contains(node.left))  { queue.offer(node.left);  visited.add(node.left); }
            if (node.right != null && !visited.contains(node.right)) { queue.offer(node.right); visited.add(node.right); }
            TreeNode par = parent.get(node);
            if (par != null && !visited.contains(par)) { queue.offer(par); visited.add(par); }
        }
        dist++;
    }
    return new ArrayList<>();
}

private void dfsParent(TreeNode node, TreeNode par, Map<TreeNode,TreeNode> map) {
    if (node == null) return;
    map.put(node, par);
    dfsParent(node.left,  node, map);
    dfsParent(node.right, node, map);
}
```

---

## Pattern 5: Tilt, Flatten, Mirror

### Flatten to Linked List (LC #114)

Use **Morris-style** preorder: visit root, right-append left subtree.

```java
public void flatten(TreeNode root) {
    TreeNode curr = root;
    while (curr != null) {
        if (curr.left != null) {
            // Find rightmost of left subtree
            TreeNode rightmost = curr.left;
            while (rightmost.right != null) rightmost = rightmost.right;
            // Splice: rightmost.right → curr.right
            rightmost.right = curr.right;
            curr.right = curr.left;
            curr.left = null;
        }
        curr = curr.right;
    }
}
```

### Invert Binary Tree (LC #226)

```java
public TreeNode invertTree(TreeNode root) {
    if (root == null) return null;
    TreeNode left  = invertTree(root.left);
    TreeNode right = invertTree(root.right);
    root.left  = right;
    root.right = left;
    return root;
}
```

---

## Pattern Recognition Drill

For each problem, identify the pattern before reading the solution:

| Problem (LC #) | Pattern |
|----------------|---------|
| Maximum Depth (#104) | Postorder — compute height |
| Same Tree (#100) | Preorder — compare structure |
| Symmetric Tree (#101) | Mirror DFS — compare left vs right |
| Path Sum II (#113) | Backtracking preorder |
| Count Good Nodes (#1448) | Preorder with running max |
| House Robber III (#337) | Postorder — two states per node (rob/not-rob) |
| Populating Next Right Pointers (#116) | BFS level-order |

---

## Full Problem: House Robber III (LC #337)

```
The thief cannot rob two directly linked nodes.
Return maximum amount robbed.

         3
        / \
       2   3
        \   \
         3   1
Expected: 7 (rob 3 + 3 + 1)
```

```java
public int rob(TreeNode root) {
    int[] result = robHelper(root);
    return Math.max(result[0], result[1]);
}

// Returns [maxIfNotRobbing, maxIfRobbing]
private int[] robHelper(TreeNode node) {
    if (node == null) return new int[]{0, 0};

    int[] left  = robHelper(node.left);
    int[] right = robHelper(node.right);

    // If we rob this node, we cannot rob children
    int robThis    = node.val + left[0] + right[0];
    // If we skip this node, children can be robbed or not (take max)
    int skipThis   = Math.max(left[0], left[1]) + Math.max(right[0], right[1]);

    return new int[]{skipThis, robThis};
}
```

---

## Try It Yourself

**Problem:** Given a binary tree, determine if it is a valid BST. (LC #98)

<details>
<summary>Solution</summary>

```java
public boolean isValidBST(TreeNode root) {
    return validate(root, Long.MIN_VALUE, Long.MAX_VALUE);
}

private boolean validate(TreeNode node, long min, long max) {
    if (node == null) return true;
    if (node.val <= min || node.val >= max) return false;
    return validate(node.left,  min,      node.val) &&
           validate(node.right, node.val, max);
}
```

**Why pass bounds:** Each node must be valid not just relative to its parent, but relative to all ancestors. Passing `min` and `max` carries ancestor constraints down.

**Why Long:** Node values can be `Integer.MIN_VALUE` or `Integer.MAX_VALUE`, so use `Long` to avoid boundary check failures.

</details>

---

## Capstone Connection

Add to AlgoForge `problems/trees/`:
- `PathSum.java` — path sum variants (LC #112, #113, #124)
- `LCA.java` — LCA in general tree and BST (LC #236, #235)
- `Serialize.java` — serialize/deserialize (LC #297)
- `TreePatterns.java` — invert, flatten, house robber III
