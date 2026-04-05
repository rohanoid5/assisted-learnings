# 8.5 — AVL Trees

## The Problem with Unbalanced BSTs

A regular BST degenerates to a linked list when values are inserted in sorted order:

```
Insert 1, 2, 3, 4, 5 into BST:

1
 \
  2
   \
    3
     \
      4
       \
        5

Height = 4 → O(n) operations, not O(log n)
```

The solution: **self-balancing trees** that restructure themselves to maintain O(log n) height. The AVL tree (Adelson-Velsky and Landis, 1962) is the original and simplest.

---

## Balance Factor

Every node tracks a **balance factor (BF)**:

```
BF(node) = height(left subtree) - height(right subtree)
```

AVL invariant: **-1 ≤ BF ≤ 1** for every node.

```
BF = 0:   balanced nicely
BF = 1:   left-heavy by 1 (acceptable)
BF = -1:  right-heavy by 1 (acceptable)
BF = 2:   left-heavy by 2 → VIOLATION → rotate right
BF = -2:  right-heavy by 2 → VIOLATION → rotate left
```

---

## The Four Rotation Cases

### Case 1: Left-Left (LL) — Rotate Right

Imbalance caused by insertion in the LEFT subtree of the LEFT child.

```
Before:      z                After:      y
            / \                          / \
           y   T4                       x   z
          / \                          / \ / \
         x   T3             →        T1 T2 T3 T4
        / \
       T1  T2

BF(z) = +2, BF(y) = +1 → LL case
```

```java
private TreeNode rotateRight(TreeNode z) {
    TreeNode y = z.left;
    TreeNode T3 = y.right;

    y.right = z;       // y becomes new root
    z.left  = T3;      // T3 moves to z's left

    // Update heights (z first, since it's now lower)
    z.height = 1 + Math.max(height(z.left), height(z.right));
    y.height = 1 + Math.max(height(y.left), height(y.right));

    return y;
}
```

### Case 2: Right-Right (RR) — Rotate Left

Mirror of LL.

```
Before:    z                After:       y
          / \                           / \
         T1   y                        z   x
             / \                      / \ / \
            T2   x          →        T1 T2 T3 T4
                / \
               T3  T4

BF(z) = -2, BF(y) = -1 → RR case
```

```java
private TreeNode rotateLeft(TreeNode z) {
    TreeNode y = z.right;
    TreeNode T2 = y.left;

    y.left  = z;
    z.right = T2;

    z.height = 1 + Math.max(height(z.left),  height(z.right));
    y.height = 1 + Math.max(height(y.left),  height(y.right));

    return y;
}
```

### Case 3: Left-Right (LR) — Rotate Left then Right

Imbalance in RIGHT subtree of LEFT child.

```
Before:    z                Step 1: rotateLeft(y)       Step 2: rotateRight(z)
          / \
         y   T4             z                           x
        / \                / \                         / \
       T1   x             x   T4                      y   z
           / \           / \                         / \ / \
          T2  T3        y   T3                      T1 T2 T3 T4
                       / \
                      T1  T2

BF(z) = +2, BF(y) = -1 → LR case
```

```java
// In the rebalance logic:
// LR case: first rotate y left, then z right
if (bf > 1 && getBalanceFactor(node.left) < 0) {
    node.left = rotateLeft(node.left);
    return rotateRight(node);
}
```

### Case 4: Right-Left (RL) — Rotate Right then Left

Mirror of LR. BF(z) = -2, BF(y) = +1.

```java
if (bf < -1 && getBalanceFactor(node.right) > 0) {
    node.right = rotateRight(node.right);
    return rotateLeft(node);
}
```

---

## Full AVL Insert

```java
public class AVLTree {
    private static class Node {
        int val, height;
        Node left, right;
        Node(int val) { this.val = val; this.height = 1; }
    }

    private int height(Node n) {
        return (n == null) ? 0 : n.height;
    }

    private int getBalanceFactor(Node n) {
        return (n == null) ? 0 : height(n.left) - height(n.right);
    }

    private Node rotateRight(Node z) {
        Node y = z.left;
        Node T3 = y.right;
        y.right = z;
        z.left = T3;
        z.height = 1 + Math.max(height(z.left), height(z.right));
        y.height = 1 + Math.max(height(y.left), height(y.right));
        return y;
    }

    private Node rotateLeft(Node z) {
        Node y = z.right;
        Node T2 = y.left;
        y.left = z;
        z.right = T2;
        z.height = 1 + Math.max(height(z.left), height(z.right));
        y.height = 1 + Math.max(height(y.left), height(y.right));
        return y;
    }

    public Node insert(Node node, int val) {
        // 1. Standard BST insert
        if (node == null) return new Node(val);
        if (val < node.val) node.left  = insert(node.left,  val);
        else if (val > node.val) node.right = insert(node.right, val);
        else return node;  // duplicate

        // 2. Update height
        node.height = 1 + Math.max(height(node.left), height(node.right));

        // 3. Get balance factor and rebalance if needed
        int bf = getBalanceFactor(node);

        // LL
        if (bf > 1 && val < node.left.val)
            return rotateRight(node);
        // RR
        if (bf < -1 && val > node.right.val)
            return rotateLeft(node);
        // LR
        if (bf > 1 && val > node.left.val) {
            node.left = rotateLeft(node.left);
            return rotateRight(node);
        }
        // RL
        if (bf < -1 && val < node.right.val) {
            node.right = rotateRight(node.right);
            return rotateLeft(node);
        }

        return node;  // already balanced
    }
}
```

---

## Example: Insert 1, 2, 3 (Would Degenerate in Plain BST)

```
Insert 1:         Insert 2:         Insert 3:         After RR rotation:
  1                 1                  1                   2
                     \                  \                  / \
                      2                  2                1   3
                                          \
                                           3
                                    BF(1) = -2 → RR Case
                                    rotateLeft(1) → 2 becomes root
```

---

## AVL Height Guarantee

An AVL tree with n nodes has height at most $1.44 \cdot \log_2 n$.

This guarantees O(log n) for all operations, unlike an unbalanced BST.

---

## AVL vs Red-Black Trees

| Property | AVL | Red-Black |
|----------|-----|-----------|
| Height guarantee | ≤ 1.44 log n | ≤ 2 log n |
| Lookup performance | Slightly faster (shorter) | Slightly slower |
| Insert/Delete performance | More rotations | Fewer rotations |
| Used in | Databases (read-heavy) | Java TreeMap, Linux kernel |
| Complexity | Simpler to understand | More complex invariants |

---

## Try It Yourself

**Problem:** Insert the following values into an AVL tree and show the final shape: 10, 20, 30, 40, 50, 25.

<details>
<summary>Solution</summary>

```
Insert 10: [10]
Insert 20: [10 → 20] (BF=-1, OK)
Insert 30: [10 → 20 → 30] BF(10)=-2, RR case → rotateLeft(10)
  Result: [20, 10, 30]
Insert 40: [20, 10, 30 → 40] (BF(30)=-1, BF(20)=-1, OK)
Insert 50: [20, 10, 30 → 40 → 50] BF(30)=-2, RR → rotateLeft(30)
  Result: [20, 10, 40, 30, 50]
Insert 25: [20, 10, 40, 30, 50] insert 25 under 30
  25 < 30, goes left of 30
  BF(40) = +1 (left heavy), BF(20) = -2... 
  Actually: BF(20) = h(left=10)=1 - h(right subtree rooted 40)=2 = -1, OK
  Let check BF(40): h(left=30 w/ 25)=2, h(right=50)=1 → BF=+1, OK

Final tree:
         20
        /  \
       10  40
          /  \
         30  50
         /
        25
```

**Trace code (Java):**
```java
AVLTree avl = new AVLTree();
Node root = null;
int[] vals = {10, 20, 30, 40, 50, 25};
for (int v : vals) root = avl.insert(root, v);
// Inorder should give: [10, 20, 25, 30, 40, 50]
```

</details>

---

## Capstone Connection

Implement `datastructures/trees/AVLTree.java` in AlgoForge. Include all four rotation types. Write tests that:
1. Insert sorted values and verify tree stays balanced
2. Verify height ≤ 1.44 * log2(n) after n insertions
3. Exercise LL, RR, LR, and RL rotations explicitly
