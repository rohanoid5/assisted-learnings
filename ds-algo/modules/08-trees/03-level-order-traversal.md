# 8.3 — Level-Order Traversal (BFS on Trees)

## Concept

Level-order traversal visits nodes level by level, left to right — this is Breadth-First Search (BFS) applied to a tree.

```
           3
          / \
         9  20
           /  \
          15   7

Level 0:  [3]
Level 1:  [9, 20]
Level 2:  [15, 7]

Output: [[3], [9, 20], [15, 7]]
```

The key mechanism: **a queue**. Enqueue children before processing the next level.

---

## Standard Template

```java
public List<List<Integer>> levelOrder(TreeNode root) {
    List<List<Integer>> result = new ArrayList<>();
    if (root == null) return result;

    Queue<TreeNode> queue = new LinkedList<>();
    queue.offer(root);

    while (!queue.isEmpty()) {
        int levelSize = queue.size();          // snapshot: nodes at this level
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

**Complexity:** O(n) time, O(w) space where w = maximum tree width.

---

## Queue Trace

```
Tree:    3
        / \
       9  20
         /  \
        15   7

Queue contents after each phase:
Start:    [3]
Level 0:  poll 3 → add 9, 20 → queue=[9,20], level=[3]
Level 1:  poll 9 (no children), poll 20 → add 15, 7
          queue=[15,7], level=[9,20]
Level 2:  poll 15 (leaf), poll 7 (leaf) → queue=[]
          level=[15,7]

Result: [[3],[9,20],[15,7]]
```

---

## Common Variations

### Zigzag Level Order (LC #103)

Alternate direction: left→right on even levels, right→left on odd.

```java
public List<List<Integer>> zigzagLevelOrder(TreeNode root) {
    List<List<Integer>> result = new ArrayList<>();
    if (root == null) return result;

    Queue<TreeNode> queue = new LinkedList<>();
    queue.offer(root);
    boolean leftToRight = true;

    while (!queue.isEmpty()) {
        int size = queue.size();
        LinkedList<Integer> level = new LinkedList<>();

        for (int i = 0; i < size; i++) {
            TreeNode node = queue.poll();
            if (leftToRight) level.addLast(node.val);
            else             level.addFirst(node.val);  // prepend for reverse

            if (node.left  != null) queue.offer(node.left);
            if (node.right != null) queue.offer(node.right);
        }
        result.add(level);
        leftToRight = !leftToRight;
    }
    return result;
}
```

### Right Side View (LC #199)

Return the last node of each level (what you'd see from the right side).

```java
public List<Integer> rightSideView(TreeNode root) {
    List<Integer> result = new ArrayList<>();
    if (root == null) return result;

    Queue<TreeNode> queue = new LinkedList<>();
    queue.offer(root);

    while (!queue.isEmpty()) {
        int size = queue.size();
        for (int i = 0; i < size; i++) {
            TreeNode node = queue.poll();
            if (i == size - 1) result.add(node.val); // last at each level

            if (node.left  != null) queue.offer(node.left);
            if (node.right != null) queue.offer(node.right);
        }
    }
    return result;
}
```

### Average of Levels (LC #637)

```java
public List<Double> averageOfLevels(TreeNode root) {
    List<Double> result = new ArrayList<>();
    Queue<TreeNode> queue = new LinkedList<>();
    queue.offer(root);

    while (!queue.isEmpty()) {
        int size = queue.size();
        double sum = 0;
        for (int i = 0; i < size; i++) {
            TreeNode node = queue.poll();
            sum += node.val;
            if (node.left  != null) queue.offer(node.left);
            if (node.right != null) queue.offer(node.right);
        }
        result.add(sum / size);
    }
    return result;
}
```

### Minimum Depth (LC #111)

BFS naturally finds shortest path — stop as soon as you hit the first leaf:

```java
public int minDepth(TreeNode root) {
    if (root == null) return 0;
    Queue<TreeNode> queue = new LinkedList<>();
    queue.offer(root);
    int depth = 0;

    while (!queue.isEmpty()) {
        depth++;
        int size = queue.size();
        for (int i = 0; i < size; i++) {
            TreeNode node = queue.poll();
            if (node.left == null && node.right == null) return depth; // first leaf
            if (node.left  != null) queue.offer(node.left);
            if (node.right != null) queue.offer(node.right);
        }
    }
    return depth;
}
```

---

## BFS vs DFS Decision Guide

| Situation | Choose |
|-----------|--------|
| Find shortest path root-to-leaf | BFS |
| Check level-by-level properties | BFS |
| Process subtrees bottom-up | DFS (postorder) |
| Find any path meeting a condition | DFS |
| Count nodes / depths in full tree | Either |
| Very tall tree (depth >> width) | BFS (less memory) |
| Very wide tree (width >> depth) | DFS (less memory) |

---

## Try It Yourself

**Problem:** Find the largest value in each row of a binary tree. (LC #515)

```
Input:
          1
         / \
        3   2
       / \   \
      5   3   9

Expected: [1, 3, 9]
```

<details>
<summary>Solution</summary>

```java
public List<Integer> largestValues(TreeNode root) {
    List<Integer> result = new ArrayList<>();
    if (root == null) return result;

    Queue<TreeNode> queue = new LinkedList<>();
    queue.offer(root);

    while (!queue.isEmpty()) {
        int size = queue.size();
        int max = Integer.MIN_VALUE;

        for (int i = 0; i < size; i++) {
            TreeNode node = queue.poll();
            max = Math.max(max, node.val);
            if (node.left  != null) queue.offer(node.left);
            if (node.right != null) queue.offer(node.right);
        }
        result.add(max);
    }
    return result;
}
```

**Key Insight:** Track max per level using `levelSize` snapshot pattern — same as standard level-order, just accumulate max instead of a list.

**Complexity:** O(n) time, O(w) space (w = max width of tree, can be O(n) for perfect trees).

</details>

---

## Capstone Connection

Add to AlgoForge `datastructures/trees/LevelOrderTraversal.java`. Implement all variants: basic, zigzag, and right-side-view. Use `ArrayDeque` instead of `LinkedList` for the queue (faster in Java).
