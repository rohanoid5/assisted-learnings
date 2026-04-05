# 8.4 — Binary Search Trees

## BST Invariant

A Binary Search Tree enforces one rule at every node:

```
   left subtree values < node.val < right subtree values
```

```
        8
       / \
      3   10
     / \    \
    1   6    14
       / \   /
      4   7 13

For every node, all values in left subtree < node.val < all values in right subtree.
Inorder traversal of a BST gives sorted output: 1, 3, 4, 6, 7, 8, 10, 13, 14
```

---

## Core Operations

### Search

```java
public TreeNode search(TreeNode root, int target) {
    if (root == null || root.val == target) return root;
    if (target < root.val) return search(root.left, target);
    else                   return search(root.right, target);
}

// Iterative version (preferred for large trees)
public TreeNode searchIterative(TreeNode root, int target) {
    while (root != null && root.val != target) {
        root = (target < root.val) ? root.left : root.right;
    }
    return root;
}
```

**Complexity:** O(h) — where h = height. For balanced BST h = O(log n), for skewed h = O(n).

### Insert

```java
public TreeNode insert(TreeNode root, int val) {
    if (root == null) return new TreeNode(val);  // found insertion point
    if (val < root.val) root.left  = insert(root.left,  val);
    else if (val > root.val) root.right = insert(root.right, val);
    // val == root.val → duplicate, ignore (or handle per requirements)
    return root;
}
```

```
Insert 5 into:    8
                 / \
                3  10

5 > 3, go right → 3's right is null → insert here

Result:   8
         / \
        3  10
         \
          5
```

### Delete — The Tricky One

Three cases:

```
Case 1: Deleting a LEAF — simply remove it.

Case 2: Deleting a node with ONE child — replace with that child.

Case 3: Deleting a node with TWO children — replace with INORDER SUCCESSOR
        (smallest value in right subtree), then delete the successor.
```

```
Delete 3 from:      8               →      8
                   / \                    / \
                  3  10                  4  10
                 / \                    / \   \
                1   6                  1   6  14
                   / \                    / \
                  4   7                  5   7
                   \
                    5

Step 1: Node 3 has two children.
Step 2: Find inorder successor = 4 (leftmost in right subtree of 3).
Step 3: Replace 3's value with 4.
Step 4: Delete 4 from 3's right subtree (it's a case 1 or 2 deletion).
```

```java
public TreeNode delete(TreeNode root, int key) {
    if (root == null) return null;

    if (key < root.val) {
        root.left = delete(root.left, key);
    } else if (key > root.val) {
        root.right = delete(root.right, key);
    } else {
        // Found the node to delete
        if (root.left == null)  return root.right; // Case 1 or 2
        if (root.right == null) return root.left;  // Case 2

        // Case 3: find inorder successor (min of right subtree)
        TreeNode successor = findMin(root.right);
        root.val = successor.val;                  // copy value
        root.right = delete(root.right, successor.val); // delete successor
    }
    return root;
}

private TreeNode findMin(TreeNode node) {
    while (node.left != null) node = node.left;
    return node;
}
```

---

## Validate BST (LC #98)

Common mistake: just checking `node.left.val < node.val < node.right.val` is wrong.

```
       5
      / \
     1   4
        / \
       3   6

Node 4 has node.left=3 < 4, but 3 < 5 violates the BST invariant!
```

Correct approach: pass valid range down the recursion:

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

---

## Kth Smallest in BST (LC #230)

Inorder traversal of BST gives sorted order → kth smallest = kth element of inorder:

```java
public int kthSmallest(TreeNode root, int k) {
    Deque<TreeNode> stack = new ArrayDeque<>();
    TreeNode curr = root;
    int count = 0;

    while (curr != null || !stack.isEmpty()) {
        while (curr != null) {
            stack.push(curr);
            curr = curr.left;
        }
        curr = stack.pop();
        if (++count == k) return curr.val;
        curr = curr.right;
    }
    return -1;
}
```

---

## Lowest Common Ancestor of BST (LC #235)

Use the BST property — no need to track paths:

```java
public TreeNode lowestCommonAncestor(TreeNode root, TreeNode p, TreeNode q) {
    // If both p and q are less than root, LCA is in left subtree
    if (p.val < root.val && q.val < root.val) {
        return lowestCommonAncestor(root.left, p, q);
    }
    // If both are greater, LCA is in right subtree
    if (p.val > root.val && q.val > root.val) {
        return lowestCommonAncestor(root.right, p, q);
    }
    // Otherwise, root is the LCA (one on each side, or one equals root)
    return root;
}
```

---

## BST vs Sorted Array

| Operation | Sorted Array | BST (balanced) | BST (skewed) |
|-----------|-------------|----------------|-------------|
| Search | O(log n) | O(log n) | O(n) |
| Insert | O(n) | O(log n) | O(n) |
| Delete | O(n) | O(log n) | O(n) |
| Min/Max | O(1) | O(log n) | O(n) |
| Predecessor/Successor | O(1) | O(log n) | O(n) |
| Range queries | O(k) | O(log n + k) | O(n + k) |

BST wins on insert/delete when the tree is balanced. A sorted array is better for static data with frequent binary search.

---

## Try It Yourself

**Problem:** Convert a sorted array to a height-balanced BST. (LC #108)

```
Input: [-10, -3, 0, 5, 9]
One valid output:
     0
    / \
  -3   9
  /   /
-10  5
```

<details>
<summary>Solution</summary>

```java
public TreeNode sortedArrayToBST(int[] nums) {
    return build(nums, 0, nums.length - 1);
}

private TreeNode build(int[] nums, int left, int right) {
    if (left > right) return null;
    int mid = left + (right - left) / 2;
    TreeNode node = new TreeNode(nums[mid]);
    node.left  = build(nums, left, mid - 1);
    node.right = build(nums, mid + 1, right);
    return node;
}
```

**Why it works:** Always picking the middle element as root guarantees the left and right subtrees have equal (or off-by-one) sizes, ensuring height balance. The BST invariant is maintained since the array is sorted.

**Complexity:** O(n) time — every element visited once. O(log n) space (call stack for balanced tree).

</details>

---

## Capstone Connection

Implement `datastructures/trees/BST.java` in AlgoForge with:
- `insert(int val)`: returns the new root
- `delete(int val)`: returns the new root  
- `search(int val)`: returns the node or null
- `findMin()` / `findMax()`
- `isValid()`: validates the BST invariant

Write unit tests for all methods including edge cases: empty tree, delete root, delete node with two children.
