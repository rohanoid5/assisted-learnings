# 3.1 — Singly Linked Lists

## Concept

A singly linked list is a chain of **nodes**, each holding a value and a pointer to the next node. There's no random access — to reach element i you must traverse from the head. But head insertions and deletions are O(1), which arrays cannot match.

---

## Deep Dive

### Node Structure & Memory Layout

```
Array:                         Linked List:
┌──┬──┬──┬──┬──┐              ┌──────┐    ┌──────┐    ┌──────┐
│10│20│30│40│50│              │10│ ──┼──▶ │20│ ──┼──▶ │30│null│
└──┴──┴──┴──┴──┘              └──────┘    └──────┘    └──────┘
 contiguous memory              scattered memory
 O(1) access by index           O(n) access by index
 O(n) insert/delete             O(1) insert/delete at head

head ──▶ [10|next] ──▶ [20|next] ──▶ [30|null]
```

### Core Operations

```java
public class ListNode<T> {
    T val;
    ListNode<T> next;
    ListNode(T val) { this.val = val; }
}

// Insert at head — O(1)
ListNode<T> insertHead(ListNode<T> head, T val) {
    ListNode<T> node = new ListNode<>(val);
    node.next = head;
    return node;          // new head
}

// Insert at tail — O(n) without tail pointer
void insertTail(ListNode<T> head, T val) {
    ListNode<T> curr = head;
    while (curr.next != null) curr = curr.next;
    curr.next = new ListNode<>(val);
}

// Delete by value — O(n) search + O(1) removal
ListNode<T> delete(ListNode<T> head, T val) {
    if (head == null) return null;
    if (head.val.equals(val)) return head.next;  // delete head

    ListNode<T> curr = head;
    while (curr.next != null && !curr.next.val.equals(val)) {
        curr = curr.next;
    }
    if (curr.next != null) curr.next = curr.next.next;
    return head;
}
```

### Complexity Summary

| Operation | Time | Notes |
|-----------|------|-------|
| access(i) | O(n) | Must traverse from head |
| insertHead | O(1) | Just reassign head |
| insertTail | O(n) or O(1) | O(1) with tail pointer |
| delete(node) | O(1) | Given node reference |
| delete(val) | O(n) | Search is O(n) |
| search | O(n) | No random access |
| size | O(1) | With size counter; O(n) else |

---

## Code Examples

### Example: Remove Nth Node From End of List

```java
// Two-pass: find length, then delete position (length - n).
// One-pass: use two pointers n apart.

public ListNode removeNthFromEnd(ListNode head, int n) {
    // Dummy head simplifies edge case where head itself is deleted
    ListNode dummy = new ListNode(0);
    dummy.next = head;

    ListNode fast = dummy, slow = dummy;

    // Advance fast n+1 steps
    for (int i = 0; i <= n; i++) fast = fast.next;

    // Move both until fast hits end
    while (fast != null) {
        fast = fast.next;
        slow = slow.next;
    }

    // slow is now just before the node to delete
    slow.next = slow.next.next;
    return dummy.next;
    // Time: O(L) one pass, Space: O(1)
}
```

---

## Try It Yourself

**Exercise:** Given a linked list, return the value at the middle node. If two middles exist (even length), return the second one.

Input: `1→2→3→4→5` → `3` · `1→2→3→4` → `3`

<details>
<summary>Show solution</summary>

```java
public int middleNode(ListNode head) {
    ListNode slow = head, fast = head;
    while (fast != null && fast.next != null) {
        slow = slow.next;
        fast = fast.next.next;
    }
    return slow.val;  // when fast reaches end, slow is at middle
    // Time: O(n), Space: O(1)
}
```

When `fast` reaches the end, `slow` has moved exactly half as far — placing it at the middle. For even-length lists, `fast.next != null` guard makes `slow` land on the second middle.

</details>

---

## Capstone Connection

`SinglyLinkedList<T>` in AlgoForge implements the complete linked list with a size counter and tail pointer for O(1) tail insertion. The inner `Node<T>` class models exactly the `ListNode` pattern used in all LeetCode problems.
