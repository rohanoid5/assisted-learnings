# 10.6 — Skip List

## What Is a Skip List?

A **skip list** is a probabilistic data structure that maintains a sorted linked list with multiple layers of "express lanes" that skip over many elements, enabling O(log n) average search, insert, and delete.

```
Level 3:  1 ─────────────────────────── 9
Level 2:  1 ──────── 5 ──────── 7 ─── 9
Level 1:  1 ─── 3 ─ 5 ─ 6 ─── 7 ─── 9
Level 0:  1 ─ 2 ─ 3 ─ 4 ─ 5 ─ 6 ─ 7 ─ 8 ─ 9

Search for 7:
  Start at top-left (Level 3): 1 → 9 (overshot!) go down
  Level 2: 1 → 5 → 7 ✓ (found at level 2)
  Only 3 comparisons instead of 7!
```

---

## Node Structure

```java
static class SkipListNode {
    int val;
    SkipListNode[] next; // pointers at each level

    SkipListNode(int val, int levels) {
        this.val = val;
        this.next = new SkipListNode[levels];
    }
}
```

---

## Key Operations

### Level Assignment (Probabilistic)

When inserting, flip a coin to decide how many levels the new node gets:

```java
private int randomLevel() {
    int level = 1;
    // Each level exists with probability P (usually 0.5 or 0.25)
    while (Math.random() < 0.5 && level < MAX_LEVEL) level++;
    return level;
}
```

Expected number of levels per node: **1/(1-P)** = 2 for P=0.5.

### Search

```java
public boolean search(int target) {
    SkipListNode curr = head;
    for (int i = maxLevel - 1; i >= 0; i--) {
        while (curr.next[i] != null && curr.next[i].val < target) {
            curr = curr.next[i];
        }
    }
    curr = curr.next[0];
    return curr != null && curr.val == target;
}
```

### Insert

```java
public void add(int val) {
    SkipListNode[] update = new SkipListNode[MAX_LEVEL];
    SkipListNode curr = head;

    // Find insertion points at each level
    for (int i = maxLevel - 1; i >= 0; i--) {
        while (curr.next[i] != null && curr.next[i].val < val) {
            curr = curr.next[i];
        }
        update[i] = curr; // predecessor at level i
    }

    int newLevel = randomLevel();
    SkipListNode newNode = new SkipListNode(val, newLevel);

    for (int i = 0; i < newLevel; i++) {
        newNode.next[i] = update[i].next[i];
        update[i].next[i] = newNode;
    }
}
```

---

## Complexity Analysis

| Operation | Average | Worst Case |
|-----------|---------|-----------|
| Search | O(log n) | O(n) |
| Insert | O(log n) | O(n) |
| Delete | O(log n) | O(n) |
| Space | O(n log n) expected | O(n log n) |

The worst case (all nodes at level 1) is extremely unlikely — probability decreases geometrically.

---

## Skip List vs Balanced BST

| | Skip List | AVL / Red-Black Tree |
|--|-----------|---------------------|
| Average complexity | O(log n) | O(log n) |
| Worst case | O(n) (probabilistic) | O(log n) guaranteed |
| Implementation | Simpler | Complex rotations |
| Cache behavior | Poor (random pointers) | Better |
| Concurrent access | Easier to make lock-free | Harder |
| Range queries | Efficient (linked bottom level) | Moderate |

**Real world usage:**
- Redis sorted sets use a skip list
- ConcurrentSkipListMap in Java's `java.util.concurrent`
- LevelDB uses skip lists for in-memory memtable

---

## Design Skiplist (LC #1206)

Full implementation matching LeetCode requirements:

```java
class Skiplist {
    private static final int MAX_LEVEL = 16;
    private static final double P = 0.5;
    private final Node head = new Node(Integer.MIN_VALUE, MAX_LEVEL);

    static class Node {
        int val;
        Node[] next;
        Node(int val, int levels) {
            this.val = val;
            this.next = new Node[levels];
        }
    }

    private int randomLevel() {
        int level = 1;
        while (Math.random() < P && level < MAX_LEVEL) level++;
        return level;
    }

    private Node[] findUpdate(int num) {
        Node[] update = new Node[MAX_LEVEL];
        Node curr = head;
        for (int i = MAX_LEVEL - 1; i >= 0; i--) {
            while (curr.next[i] != null && curr.next[i].val < num) curr = curr.next[i];
            update[i] = curr;
        }
        return update;
    }

    public boolean search(int num) {
        Node[] update = findUpdate(num);
        Node candidate = update[0].next[0];
        return candidate != null && candidate.val == num;
    }

    public void add(int num) {
        Node[] update = findUpdate(num);
        int level = randomLevel();
        Node newNode = new Node(num, level);
        for (int i = 0; i < level; i++) {
            newNode.next[i] = update[i].next[i];
            update[i].next[i] = newNode;
        }
    }

    public boolean erase(int num) {
        Node[] update = findUpdate(num);
        Node target = update[0].next[0];
        if (target == null || target.val != num) return false;
        for (int i = 0; i < MAX_LEVEL; i++) {
            if (update[i].next[i] != target) break;
            update[i].next[i] = target.next[i];
        }
        return true;
    }
}
```

---

## Try It Yourself

**Problem:** Implement the `Skiplist` class with `search(int num)`, `add(int num)`, and `erase(int num)`. (LC #1206)

<details>
<summary>Key Concepts Reminder</summary>

1. Use a sentinel `head` node with all levels initialized
2. `findUpdate()` finds the predecessor at each level → reuse for search, add, erase
3. `search`: check `update[0].next[0]` for the target value
4. `add`: randomLevel() + wire new node into each level
5. `erase`: find target, unlink from each level it participates in

The full implementation is given above. Test with:
```
add(1), add(2), add(3)
search(0) → false
add(4)
search(1) → true
erase(1)
search(1) → false  (now gone)
```

</details>

---

## Capstone Connection

Skip lists are less commonly tested than the other data structures in this module, but knowing how they work demonstrates deep data structure knowledge. Add a `notes/skip-list.md` to AlgoForge with implementation notes, or implement `datastructures/advanced/Skiplist.java` if time permits.
