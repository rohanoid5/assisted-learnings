# 8.7 — B-Trees

## Motivation

Binary trees work well in RAM, but databases store data on disk. A disk read fetches an entire **page** (typically 4–16 KB). With a binary tree, each node is tiny — you'd need O(log₂ n) disk reads. A **B-Tree** packs many keys per node (one per page), reducing height to O(log_t n) where t is the minimum number of keys per node — dramatically fewer disk reads.

```
Binary Tree (n=1M, height=20):      B-Tree (t=500, n=1M, height=4):
20 disk reads per search            4 disk reads per search
```

---

## B-Tree Structure

A **B-Tree of order m** (or degree t) satisfies:

1. Every node has at most **2t − 1 keys** (and 2t children)
2. Every non-root node has at least **t − 1 keys** (and t children)
3. The root has at least 1 key
4. All leaves are at the **same depth**
5. A node with k keys has **k + 1 children** (internal node)

```
B-Tree of order 3 (t=2, up to 3 keys per node):

              [30 | 70]
             /    |    \
        [10|20]  [40|50|60]  [80|90]
```

---

## B-Tree Search

Similar to BST but linear search through keys in each node (or binary search for large nodes):

```java
// Pseudocode
BTreeSearch(node, key):
  i = 0
  while i < node.numKeys and key > node.keys[i]:
    i++
  if i < node.numKeys and key == node.keys[i]:
    return (node, i)  // found
  if node.isLeaf:
    return NOT_FOUND
  else:
    return BTreeSearch(node.children[i], key)
```

---

## B-Tree Insertion — Node Splitting

When a node is full (2t−1 keys) before insertion, it splits into two nodes of t−1 keys each, pushing the **median key** up to the parent.

```
Insert 65 into B-Tree (t=2):

Before:        [30 | 70]
              /    |    \
        [10|20]  [40|50|60]  [80|90]

        Right child [40|50|60] is full! Split before going down:
        Median = 50 → push up to parent
        Split: [40] and [60]

After split:  [30 | 50 | 70]
             /    |    |    \
       [10|20]  [40]  [60]  [80|90]

Now insert 65: goes into [60] → [60|65]

Final:  [30 | 50 | 70]
       /    |    |    \
 [10|20]  [40]  [60|65]  [80|90]
```

---

## B+ Tree — What Databases Actually Use

A **B+ Tree** is a variation where:
- **All actual data** lives in the **leaf nodes**
- Internal nodes hold only keys as **routing information**
- Leaf nodes are **linked together** as a sorted linked list

```
B+ Tree:
           [20 | 50]
          /    |    \
      [10|15] [20|30|40] [50|70|80]
         ↓        ↓            ↓
      linked ——→ linked ——→ linked

Range scan:  find first leaf, then follow links → no backtracking
```

| Feature | B-Tree | B+ Tree |
|---------|--------|--------|
| Data location | Internal + leaves | Leaves only |
| Range queries | Slow (scattered) | Fast (linked leaves) |
| Space usage | Less (fewer records in leaves) | More (data duplicated at root paths) |
| Used in | Filesystem inodes | MySQL InnoDB, PostgreSQL |

---

## B-Tree Complexity

| Operation | B-Tree (height h) |
|-----------|-------------------|
| Search | O(t · log_t n) |
| Insert | O(t · log_t n) |
| Delete | O(t · log_t n) |
| Height | O(log_t n) |

With t=500 (typical page size), $\log_{500}(10^9) \approx 3.4$ — a billion records in 4 levels.

---

## B-Tree in Practice (Java)

Java does not have a built-in B-Tree, but `TreeMap` uses a Red-Black Tree. Actual B-Tree implementations are found in:
- Embedded databases: SQLite (B+ Tree pages)
- JVM internals: certain file system adapters

For interview purposes, knowing the **concept and invariants** matters more than implementation:

```
Key facts to know:
  1. B-Tree keeps all leaves at the same depth
  2. Node splits ensure minimum occupancy ≥ 50%
  3. B+ Tree leaves form a linked list → efficient range scans
  4. Page size determines t (order of tree)
  5. B-Trees minimize I/O by maximizing branching factor
```

---

## Try It Yourself

**Problem (conceptual):** A B-Tree of order t=3 has height h. What is the minimum and maximum number of keys the tree can hold?

<details>
<summary>Solution</summary>

**Maximum keys** (all nodes packed full):
- Each node holds 2t−1 = 5 keys
- Root has 2t = 6 children, each internal node has 2t = 6 children
- At height h: max nodes = $1 + 6 + 6^2 + ... + 6^h = \frac{6^{h+1}-1}{5}$
- Max keys = $\frac{6^{h+1}-1}{5} \times 5 = 6^{h+1} - 1$

**Minimum keys** (all nodes at minimum occupancy):
- Root: 1 key (minimum)
- Every other internal node: t−1 = 2 keys
- At height h: min nodes = $1 + 2 + 2t + 2t^2 + ... + 2t^{h-1} = 1 + 2\frac{t^h-1}{t-1}$
- With t=3: min nodes = $1 + 2 \times \frac{3^h-1}{2} = 3^h$
- Min keys at height 3: $3^3 = 27$ minimum keys

**For h=3, t=3:**
- Minimum: 27 keys
- Maximum: $6^4 - 1 = 1295$ keys

</details>

---

## Capstone Connection

You won't implement a full B-Tree in AlgoForge (it's a storage engine concern), but understanding B-Trees is tested at companies like Google, Amazon, and Microsoft when discussing database design. Add a `notes/b-tree-interview-notes.md` to your AlgoForge project summarizing:
- Why databases use B+ Trees
- How splitting works
- Why all leaves must be at the same depth
- Time complexity and why log base t matters
