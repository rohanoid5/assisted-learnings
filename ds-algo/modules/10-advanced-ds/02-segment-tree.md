# 10.2 — Segment Tree

## The Problem: Range Queries with Updates

**Scenario:** Given an array, answer repeated queries like:
- "What is the sum of elements from index l to r?"
- "Update the value at index i to x."

| Approach | Query | Update |
|----------|-------|--------|
| Brute force | O(n) | O(1) |
| Prefix sum array | O(1) | O(n) — must rebuild |
| **Segment Tree** | **O(log n)** | **O(log n)** |

---

## Structure

A segment tree is a binary tree where:
- Each **leaf** stores a single array element
- Each **internal node** stores the aggregate (sum, min, max) of its range

```
Array:  [1, 3, 5, 7, 9, 11]
Indices: 0  1  2  3  4   5

Segment Tree (sum):
                  [0-5: 36]
               /              \
         [0-2: 9]           [3-5: 27]
         /      \           /        \
     [0-1: 4]  [2-2: 5] [3-4: 16]  [5-5: 11]
     /     \              /     \
[0-0:1] [1-1:3]     [3-3:7] [4-4:9]
```

**Array representation:** Store as array where:
- Root at index 1
- Left child of node i → `2*i`
- Right child → `2*i + 1`
- Leaf nodes at indices n to 2n-1

---

## Implementation (Sum Segment Tree)

```java
public class SegmentTree {
    private int[] tree;
    private int n;

    public SegmentTree(int[] nums) {
        n = nums.length;
        tree = new int[4 * n]; // safe upper bound
        build(nums, 0, 0, n - 1);
    }

    // Build tree: node covers range [l, r]
    private void build(int[] nums, int node, int l, int r) {
        if (l == r) {
            tree[node] = nums[l]; // leaf
            return;
        }
        int mid = (l + r) / 2;
        build(nums, 2*node+1, l, mid);
        build(nums, 2*node+2, mid+1, r);
        tree[node] = tree[2*node+1] + tree[2*node+2]; // internal = sum of children
    }

    // Update index i to val
    public void update(int i, int val) {
        update(0, 0, n-1, i, val);
    }

    private void update(int node, int l, int r, int i, int val) {
        if (l == r) {
            tree[node] = val; // update leaf
            return;
        }
        int mid = (l + r) / 2;
        if (i <= mid) update(2*node+1, l, mid, i, val);
        else          update(2*node+2, mid+1, r, i, val);
        tree[node] = tree[2*node+1] + tree[2*node+2]; // recompute internal
    }

    // Query sum from index l to r (inclusive)
    public int query(int l, int r) {
        return query(0, 0, n-1, l, r);
    }

    private int query(int node, int nodeL, int nodeR, int l, int r) {
        if (l > nodeR || r < nodeL) return 0;       // out of range
        if (l <= nodeL && nodeR <= r) return tree[node]; // full overlap

        int mid = (nodeL + nodeR) / 2;
        return query(2*node+1, nodeL, mid, l, r)
             + query(2*node+2, mid+1, nodeR, l, r);
    }
}
```

---

## Query Trace

```
Array: [1, 3, 5, 7, 9, 11], n=6
Query sum [1, 4] (indices 1 to 4):

query(root [0-5], l=1, r=4):
  Not full overlap, split:
  Left child [0-2]:
    Not full overlap (node [0-2], l=1, r=4 → partial):
    Left child [0-1]: partial, split further:
      [0-0]: l=1 > nodeR=0 → return 0 (out of range)
      [1-1]: full overlap → return 3
    Right child [2-2]: full overlap → return 5
    Total: 0 + 3 + 5 = 8
  Right child [3-5]: partial, split:
    [3-4]: full overlap → return 16
    [5-5]: r=4 < nodeL=5 → return 0
    Total: 16 + 0 = 16
  Total: 8 + 16 = 24  ✓ (3+5+7+9=24)
```

---

## Range Minimum Segment Tree

Same structure, just change the aggregate:

```java
// Leaf: nums[l]
// Internal: Math.min(left child, right child)
// Query neutral: Integer.MAX_VALUE (instead of 0)
tree[node] = Math.min(tree[2*node+1], tree[2*node+2]);
// In query: if (l > nodeR || r < nodeL) return Integer.MAX_VALUE;
```

---

## Range Sum Query — Mutable (LC #307)

```java
class NumArray {
    private SegmentTree seg;
    
    public NumArray(int[] nums) { seg = new SegmentTree(nums); }
    
    public void update(int index, int val) { seg.update(index, val); }
    
    public int sumRange(int left, int right) { return seg.query(left, right); }
}
```

---

## Lazy Propagation (Range Updates)

When you need to update a **range** of values (not just a single point), a regular segment tree becomes O(n log n). **Lazy propagation** defers updates — stores pending changes and applies them only when needed.

```java
// Lazy tree: tree[] stores current values, lazy[] stores pending adds
private int[] tree, lazy;

private void pushDown(int node) {
    if (lazy[node] != 0) {
        tree[2*node+1] += lazy[node] * leftSize;
        tree[2*node+2] += lazy[node] * rightSize;
        lazy[2*node+1] += lazy[node];
        lazy[2*node+2] += lazy[node];
        lazy[node] = 0; // clear
    }
}

// Range add: add val to all elements in [l,r]
private void rangeUpdate(int node, int nodeL, int nodeR, int l, int r, int val) {
    if (l > nodeR || r < nodeL) return;
    if (l <= nodeL && nodeR <= r) {
        tree[node] += val * (nodeR - nodeL + 1);
        lazy[node] += val;
        return;
    }
    pushDown(node);
    int mid = (nodeL + nodeR) / 2;
    rangeUpdate(2*node+1, nodeL, mid, l, r, val);
    rangeUpdate(2*node+2, mid+1, nodeR, l, r, val);
    tree[node] = tree[2*node+1] + tree[2*node+2];
}
```

---

## Try It Yourself

**Problem:** Implement a class that supports `sumRange(left, right)` and `update(index, val)` on an integer array. (LC #307)

<details>
<summary>Solution</summary>

```java
class NumArray {
    private int[] tree;
    private int n;

    public NumArray(int[] nums) {
        n = nums.length;
        tree = new int[4 * n];
        build(nums, 0, 0, n-1);
    }

    private void build(int[] nums, int node, int l, int r) {
        if (l == r) { tree[node] = nums[l]; return; }
        int mid = (l+r)/2;
        build(nums, 2*node+1, l, mid);
        build(nums, 2*node+2, mid+1, r);
        tree[node] = tree[2*node+1] + tree[2*node+2];
    }

    public void update(int index, int val) {
        update(0, 0, n-1, index, val);
    }

    private void update(int node, int l, int r, int i, int val) {
        if (l == r) { tree[node] = val; return; }
        int mid = (l+r)/2;
        if (i <= mid) update(2*node+1, l, mid, i, val);
        else          update(2*node+2, mid+1, r, i, val);
        tree[node] = tree[2*node+1] + tree[2*node+2];
    }

    public int sumRange(int left, int right) {
        return query(0, 0, n-1, left, right);
    }

    private int query(int node, int nodeL, int nodeR, int l, int r) {
        if (l > nodeR || r < nodeL) return 0;
        if (l <= nodeL && nodeR <= r) return tree[node];
        int mid = (nodeL+nodeR)/2;
        return query(2*node+1, nodeL, mid, l, r)
             + query(2*node+2, mid+1, nodeR, l, r);
    }
}
```

**Complexity:** Build O(n), update O(log n), query O(log n).

</details>

---

## Capstone Connection

Implement `datastructures/advanced/SegmentTree.java` in AlgoForge with configurable aggregate (sum/min/max via lambda or enum). Test with LC #307 and range minimum queries.
