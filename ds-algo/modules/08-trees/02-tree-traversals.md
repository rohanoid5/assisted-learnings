# 8.2 — Tree Traversals

## The Four Traversals

Given a binary tree, there are four fundamental ways to visit every node:

| Traversal | Order | Key Use |
|-----------|-------|---------|
| **Preorder** | Root → Left → Right | Copy tree, serialize |
| **Inorder** | Left → Root → Right | Sorted output from BST |
| **Postorder** | Left → Right → Root | Delete tree, compute sizes |
| **Level-order** | Level by level (BFS) | Next topic — Module 8.3 |

---

## Visual Walkthrough

```
           1
          / \
         2   3
        / \ / \
       4  5 6  7
```

**Preorder:** 1, 2, 4, 5, 3, 6, 7  (visit root *before* children)
**Inorder:** 4, 2, 5, 1, 6, 3, 7  (visit root *between* children)
**Postorder:** 4, 5, 2, 6, 7, 3, 1 (visit root *after* children)

---

## Recursive Implementations

```java
// Preorder: Root → Left → Right
public void preorder(TreeNode node, List<Integer> result) {
    if (node == null) return;
    result.add(node.val);          // visit ROOT first
    preorder(node.left, result);
    preorder(node.right, result);
}

// Inorder: Left → Root → Right
public void inorder(TreeNode node, List<Integer> result) {
    if (node == null) return;
    inorder(node.left, result);
    result.add(node.val);          // visit ROOT between
    inorder(node.right, result);
}

// Postorder: Left → Right → Root
public void postorder(TreeNode node, List<Integer> result) {
    if (node == null) return;
    postorder(node.left, result);
    postorder(node.right, result);
    result.add(node.val);          // visit ROOT last
}
```

All three: **O(n) time, O(h) space** where h = height.

---

## Iterative Implementations

Iterative = explicit stack instead of call stack. Required for very deep trees (avoid StackOverflowError) or when you need fine-grained control.

### Iterative Preorder

```java
public List<Integer> preorderIterative(TreeNode root) {
    List<Integer> result = new ArrayList<>();
    if (root == null) return result;

    Deque<TreeNode> stack = new ArrayDeque<>();
    stack.push(root);

    while (!stack.isEmpty()) {
        TreeNode node = stack.pop();
        result.add(node.val);           // visit

        // Push right first so left is processed first
        if (node.right != null) stack.push(node.right);
        if (node.left  != null) stack.push(node.left);
    }
    return result;
}
```

```
Stack trace for tree 1→{2,3}→{4,5,6,7}:

Push [1]
Pop  1,  result=[1],  push 3, push 2  → stack=[3,2]
Pop  2,  result=[1,2], push 5, push 4 → stack=[3,5,4]
Pop  4,  result=[1,2,4]               → stack=[3,5]
Pop  5,  result=[1,2,4,5]             → stack=[3]
Pop  3,  result=[1,2,4,5,3], push 7, push 6 → stack=[7,6]
Pop  6,  result=[...,3,6]             → stack=[7]
Pop  7,  result=[...,6,7]             → stack=[]
```

### Iterative Inorder

```java
public List<Integer> inorderIterative(TreeNode root) {
    List<Integer> result = new ArrayList<>();
    Deque<TreeNode> stack = new ArrayDeque<>();
    TreeNode curr = root;

    while (curr != null || !stack.isEmpty()) {
        // Go as far left as possible
        while (curr != null) {
            stack.push(curr);
            curr = curr.left;
        }
        // Backtrack: visit and go right
        curr = stack.pop();
        result.add(curr.val);
        curr = curr.right;
    }
    return result;
}
```

```
Trace for 1→{2,3}, 2→{4,5}:

curr=1: push 1, curr=2; push 2, curr=4; push 4, curr=null
Pop 4, visit 4, curr=null (no right)
Pop 2, visit 2, curr=5
  push 5, curr=null
  Pop 5, visit 5, curr=null
Pop 1, visit 1, curr=3
  push 3, curr=null
  Pop 3, visit 3, curr=null
Result: [4, 2, 5, 1, 3]   ✓ (sorted for a BST)
```

### Iterative Postorder

One clean approach: reverse of a modified preorder (Root→Right→Left → reversed = Left→Right→Root).

```java
public List<Integer> postorderIterative(TreeNode root) {
    LinkedList<Integer> result = new LinkedList<>(); // addFirst to reverse
    if (root == null) return result;

    Deque<TreeNode> stack = new ArrayDeque<>();
    stack.push(root);

    while (!stack.isEmpty()) {
        TreeNode node = stack.pop();
        result.addFirst(node.val);      // prepend instead of append

        if (node.left  != null) stack.push(node.left);
        if (node.right != null) stack.push(node.right);
    }
    return result;
}
```

---

## Morris Traversal — O(1) Space

For inorder traversal without any stack (threads predecessor's right pointer back to current):

```java
public List<Integer> morrisInorder(TreeNode root) {
    List<Integer> result = new ArrayList<>();
    TreeNode curr = root;

    while (curr != null) {
        if (curr.left == null) {
            result.add(curr.val);
            curr = curr.right;
        } else {
            // Find inorder predecessor
            TreeNode pred = curr.left;
            while (pred.right != null && pred.right != curr) {
                pred = pred.right;
            }
            if (pred.right == null) {
                pred.right = curr;    // thread it
                curr = curr.left;
            } else {
                pred.right = null;    // remove thread
                result.add(curr.val);
                curr = curr.right;
            }
        }
    }
    return result;
}
```

**Complexity:** O(n) time, O(1) space.

---

## Choosing the Right Traversal

| Goal | Traversal |
|------|-----------|
| Reconstruct tree from serialization | Preorder (root first = known root) |
| Get BST elements in sorted order | Inorder |
| Compute subtree property bottom-up | Postorder (children before parent) |
| Find level structure, shortest path | Level-order (BFS) |
| Print path from root | Preorder |
| Evaluate expression tree | Postorder |

---

## Reconstruct Tree from Traversals

Given preorder + inorder (or postorder + inorder), you can reconstruct the unique tree:

```
Preorder: [3, 9, 20, 15, 7]
Inorder:  [9, 3, 15, 20, 7]

Preorder[0] = 3 is root
Inorder: 9 is left of 3, {15, 20, 7} is right
Recurse: build left from preorder[1..1], inorder[0..0]
         build right from preorder[2..4], inorder[2..4]
```

```java
// LC #105 - Construct Binary Tree from Preorder and Inorder Traversal
public TreeNode buildTree(int[] preorder, int[] inorder) {
    Map<Integer, Integer> indexMap = new HashMap<>();
    for (int i = 0; i < inorder.length; i++) {
        indexMap.put(inorder[i], i);
    }
    return build(preorder, 0, preorder.length - 1,
                 inorder,  0, inorder.length - 1, indexMap);
}

private TreeNode build(int[] pre, int preL, int preR,
                       int[] in,  int inL,  int inR,
                       Map<Integer, Integer> map) {
    if (preL > preR) return null;

    int rootVal = pre[preL];
    int inRoot  = map.get(rootVal);
    int leftSize = inRoot - inL;

    TreeNode root = new TreeNode(rootVal);
    root.left  = build(pre, preL + 1, preL + leftSize,
                       in,  inL,      inRoot - 1, map);
    root.right = build(pre, preL + leftSize + 1, preR,
                       in,  inRoot + 1, inR, map);
    return root;
}
```

---

## Try It Yourself

**Problem:** Return the inorder traversal of a binary tree without using recursion. (LC #94)

```
Input:    1
           \
            2
           /
          3
Expected: [1, 3, 2]
```

<details>
<summary>Solution</summary>

```java
public List<Integer> inorderTraversal(TreeNode root) {
    List<Integer> result = new ArrayList<>();
    Deque<TreeNode> stack = new ArrayDeque<>();
    TreeNode curr = root;

    while (curr != null || !stack.isEmpty()) {
        while (curr != null) {
            stack.push(curr);
            curr = curr.left;
        }
        curr = stack.pop();
        result.add(curr.val);
        curr = curr.right;
    }
    return result;
}
```

**Trace:**
```
curr=1: push 1, curr=null (no left)
Pop 1, visit 1, curr=2
  curr=2: push 2, curr=3; push 3, curr=null
  Pop 3, visit 3, curr=null
  Pop 2, visit 2, curr=null
Result: [1, 3, 2]  ✓
```

</details>

---

## Capstone Connection

Add to AlgoForge `datastructures/trees/TreeTraversals.java` — all four traversal methods, both recursive and iterative, with uniform interface. Demonstrate with a test showing all produce correct output for a given tree.
