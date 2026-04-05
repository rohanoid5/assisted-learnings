# 3.4 — Merge and Reverse Patterns

## Concept

Two canonical linked list operations that appear constantly in interviews: **reversing** a list (or a portion of it) and **merging** two sorted lists. Mastering the pointer manipulation for both unlocks a large class of medium-hard problems.

---

## Deep Dive

### In-Place Reversal

```
Original:   1 → 2 → 3 → 4 → null
              prev  cur  next

Step 1: save next, flip pointer, advance
        null ← 1  |  2 → 3 → 4 → null
              prev  cur

Step 2: null ← 1 ← 2  |  3 → 4 → null
                   prev  cur

Step 3: null ← 1 ← 2 ← 3  |  4 → null

Step 4: null ← 1 ← 2 ← 3 ← 4
                           prev  cur=null  → DONE, return prev
```

### Merging Two Sorted Lists

```
L1: 1 → 3 → 5
L2: 2 → 4 → 6

Use a dummy head, greedily pick the smaller node:

dummy → ?
p1=1, p2=2  → pick 1.  dummy → 1
p1=3, p2=2  → pick 2.  dummy → 1 → 2
p1=3, p2=4  → pick 3.  dummy → 1 → 2 → 3
p1=5, p2=4  → pick 4.  dummy → 1 → 2 → 3 → 4
p1=5, p2=6  → pick 5.  dummy → 1 → 2 → 3 → 4 → 5
p1=null     → append 6. dummy → 1 → 2 → 3 → 4 → 5 → 6
```

---

## Code Examples

### Reverse Linked List — Iterative

```java
public ListNode reverse(ListNode head) {
    ListNode prev = null, curr = head;
    while (curr != null) {
        ListNode next = curr.next;   // save
        curr.next = prev;            // flip
        prev = curr;                 // advance prev
        curr = next;                 // advance curr
    }
    return prev;                     // new head
    // Time: O(n), Space: O(1)
}
```

### Reverse Linked List — Recursive

```java
public ListNode reverseRecursive(ListNode head) {
    if (head == null || head.next == null) return head;
    ListNode newHead = reverseRecursive(head.next);
    head.next.next = head;   // point next node back at head
    head.next = null;        // cut forward pointer
    return newHead;
    // Time: O(n), Space: O(n) call stack
}
```

### Merge Two Sorted Lists

```java
public ListNode mergeTwoLists(ListNode l1, ListNode l2) {
    ListNode dummy = new ListNode(0), tail = dummy;
    while (l1 != null && l2 != null) {
        if (l1.val <= l2.val) { tail.next = l1; l1 = l1.next; }
        else                  { tail.next = l2; l2 = l2.next; }
        tail = tail.next;
    }
    tail.next = (l1 != null) ? l1 : l2;  // append remaining
    return dummy.next;
    // Time: O(m+n), Space: O(1)
}
```

### Reorder List (LC #143)

```java
// Given: 1→2→3→4→5
// Want:  1→5→2→4→3
// Strategy: find mid, reverse second half, merge the two halves

public void reorderList(ListNode head) {
    if (head == null || head.next == null) return;

    // 1. Find middle (fast/slow)
    ListNode slow = head, fast = head;
    while (fast.next != null && fast.next.next != null) {
        slow = slow.next;
        fast = fast.next.next;
    }

    // 2. Reverse second half
    ListNode secondHalf = reverse(slow.next);
    slow.next = null;   // cut list in half

    // 3. Merge both halves by interleaving
    ListNode first = head, second = secondHalf;
    while (second != null) {
        ListNode next1 = first.next, next2 = second.next;
        first.next = second;
        second.next = next1;
        first = next1;
        second = next2;
    }
}
```

---

## Try It Yourself

**Exercise:** Reverse nodes in k-groups (LC #25). Given a linked list, reverse every k consecutive nodes. If the remaining nodes are fewer than k, leave them as-is.

Input: `1→2→3→4→5`, k=2 → `2→1→4→3→5`
Input: `1→2→3→4→5`, k=3 → `3→2→1→4→5`

<details>
<summary>Show solution</summary>

```java
public ListNode reverseKGroup(ListNode head, int k) {
    // Check if there are at least k nodes remaining
    ListNode check = head;
    for (int i = 0; i < k; i++) {
        if (check == null) return head;  // fewer than k remain — leave as-is
        check = check.next;
    }

    // Reverse k nodes starting at head
    ListNode prev = null, curr = head;
    for (int i = 0; i < k; i++) {
        ListNode next = curr.next;
        curr.next = prev;
        prev = curr;
        curr = next;
    }

    // head is now the tail of the reversed group;
    // recursively connect it to the reversed remainder
    head.next = reverseKGroup(curr, k);
    return prev;   // prev is the new head of this group
    // Time: O(n), Space: O(n/k) recursion stack
}
```

</details>

---

## Capstone Connection

`reverseKGroup` is considered one of the harder standard linked list problems. The dual-operation pattern (reverse + merge) also underlies **Merge Sort on linked lists** — a classic algorithm where linked lists actually beat arrays (no need for extra storage).
