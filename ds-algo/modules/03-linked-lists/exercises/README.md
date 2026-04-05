# Module 03: Linked Lists — Exercises

Work through these exercises in order. Each one builds on the concepts from this module. Try to solve each problem independently before opening the solution.

---

## Exercise 1: Reverse a Linked List (LC #206)

**Goal:** Reverse a singly linked list in-place using O(1) extra space.

```
Input:  1 → 2 → 3 → 4 → 5 → null
Output: 5 → 4 → 3 → 2 → 1 → null
```

1. Start by solving it iteratively with three pointers: `prev`, `curr`, `next`.
2. Confirm your solution handles `null` (empty list) and a single-node list.
3. Then write the recursive version using the same logic from topic 04.
4. Write a JUnit test that constructs the list, reverses it, and asserts the result.

```java
// Starter scaffold
class ListNode {
    int val;
    ListNode next;
    ListNode(int val) { this.val = val; }
}

class Solution {
    public ListNode reverseList(ListNode head) {
        // your implementation
    }
}
```

<details>
<summary>Solution</summary>

```java
public ListNode reverseList(ListNode head) {
    ListNode prev = null, curr = head;
    while (curr != null) {
        ListNode next = curr.next;
        curr.next = prev;
        prev = curr;
        curr = next;
    }
    return prev;
}

// Recursive
public ListNode reverseListRec(ListNode head) {
    if (head == null || head.next == null) return head;
    ListNode newHead = reverseListRec(head.next);
    head.next.next = head;
    head.next = null;
    return newHead;
}
```

**Test:**
```java
@Test
void testReverseList() {
    ListNode head = buildList(new int[]{1, 2, 3, 4, 5});
    ListNode reversed = new Solution().reverseList(head);
    assertArrayEquals(new int[]{5, 4, 3, 2, 1}, toArray(reversed));
}
```

</details>

---

## Exercise 2: Detect Cycle (LC #141 & #142)

**Goal:** Use Floyd's algorithm to (a) detect cycle presence and (b) find the cycle's entry node.

```
List: 3 → 2 → 0 → -4 (tail's next points back to index 1, node with value 2)
LC #141: hasCycle → true
LC #142: detectCycle → node with value 2
```

1. Implement `hasCycle` — return `true` if a cycle exists.
2. Implement `detectCycle` — return the node where the cycle begins, or `null` if none.
3. Explain in a comment *why* resetting `slow` to `head` and running both pointers at speed 1 finds the entry.

```java
public boolean hasCycle(ListNode head) { /* ... */ }
public ListNode detectCycle(ListNode head) { /* ... */ }
```

<details>
<summary>Solution</summary>

```java
public boolean hasCycle(ListNode head) {
    ListNode slow = head, fast = head;
    while (fast != null && fast.next != null) {
        slow = slow.next;
        fast = fast.next.next;
        if (slow == fast) return true;
    }
    return false;
}

public ListNode detectCycle(ListNode head) {
    ListNode slow = head, fast = head;
    while (fast != null && fast.next != null) {
        slow = slow.next;
        fast = fast.next.next;
        if (slow == fast) {
            // Mathematical invariant: dist(head→entry) == dist(meeting→entry)
            slow = head;
            while (slow != fast) {
                slow = slow.next;
                fast = fast.next;
            }
            return slow;
        }
    }
    return null;
}
```

**Why it works:**
Let F = distance from head to cycle entry, C = cycle length, k = where they meet inside the cycle.

- When they meet: slow has moved F+k, fast has moved F+k+C (lapped once).
- fast = 2×slow → F+k+C = 2*(F+k) → C = F+k → F = C-k.
- After reset, slow starts at head (distance F to entry), fast starts at meeting point (distance C-k = F to entry going around). They meet at the entry.

</details>

---

## Exercise 3: Find the Middle of a Linked List (LC #876)

**Goal:** Return the *second* middle node when the list has even length.

```
1 → 2 → 3 → 4 → 5     → return node 3
1 → 2 → 3 → 4 → 5 → 6  → return node 4 (second middle)
```

1. Implement using fast/slow pointers. Trace through both examples manually.
2. Identify exactly what `while` condition gives you the *second* middle vs the *first* middle.
3. Write an alternate version using list length (O(n) two-pass) to validate the pointer version.

<details>
<summary>Solution</summary>

```java
public ListNode middleNode(ListNode head) {
    ListNode slow = head, fast = head;
    // fast.next != null  → first middle for even length
    // fast != null       → second middle for even length
    while (fast != null && fast.next != null) {
        slow = slow.next;
        fast = fast.next.next;
    }
    return slow;
    // Condition: fast.next!=null stops at first middle for even (1 2 3 4 → node 2)
    // Condition: fast!=null stops at second middle for even   (1 2 3 4 → node 3)
    // For odd length (1 2 3 4 5) both return centre node 3.
}
```

**Two-pass validation:**
```java
public ListNode middleTwoPass(ListNode head) {
    int len = 0;
    ListNode curr = head;
    while (curr != null) { len++; curr = curr.next; }
    curr = head;
    for (int i = 0; i < len / 2; i++) curr = curr.next;
    return curr;
}
```

</details>

---

## Exercise 4: Merge Two Sorted Lists (LC #21)

**Goal:** Merge two sorted singly linked lists into one sorted list. Return the head of the merged list.

```
L1: 1 → 2 → 4
L2: 1 → 3 → 4
Result: 1 → 1 → 2 → 3 → 4 → 4
```

1. Use a dummy sentinel node to avoid special-casing the head.
2. Extend to **Merge K sorted lists** (LC #23): use a `PriorityQueue` of ListNode heads.
3. Write a test for both, including edge cases: one empty list, both empty.

<details>
<summary>Solution</summary>

```java
// Merge two lists — O(m+n) time, O(1) space
public ListNode mergeTwoLists(ListNode l1, ListNode l2) {
    ListNode dummy = new ListNode(0), tail = dummy;
    while (l1 != null && l2 != null) {
        if (l1.val <= l2.val) { tail.next = l1; l1 = l1.next; }
        else                  { tail.next = l2; l2 = l2.next; }
        tail = tail.next;
    }
    tail.next = (l1 != null) ? l1 : l2;
    return dummy.next;
}

// Merge K lists — O(N log k) where N=total nodes, k=number of lists
public ListNode mergeKLists(ListNode[] lists) {
    PriorityQueue<ListNode> pq = new PriorityQueue<>(
        Comparator.comparingInt(n -> n.val));
    for (ListNode node : lists)
        if (node != null) pq.offer(node);

    ListNode dummy = new ListNode(0), tail = dummy;
    while (!pq.isEmpty()) {
        ListNode node = pq.poll();
        tail.next = node;
        tail = tail.next;
        if (node.next != null) pq.offer(node.next);
    }
    return dummy.next;
}
```

</details>

---

## Exercise 5: LRU Cache (LC #146)

**Goal:** Implement a data structure that supports `get(key)` and `put(key, value)` in O(1) time, evicting the least recently used item when the cache is at capacity.

```
LRUCache cache = new LRUCache(2);
cache.put(1, 1);  // {1=1}
cache.put(2, 2);  // {1=1, 2=2}
cache.get(1);     // 1   (promotes key 1 to MRU)
cache.put(3, 3);  // evicts key 2 → {1=1, 3=3}
cache.get(2);     // -1  (evicted)
cache.put(4, 4);  // evicts key 1 → {3=3, 4=4}
cache.get(1);     // -1
cache.get(3);     // 3
cache.get(4);     // 4
```

1. Implement using a `HashMap<Integer, Node>` and a custom doubly linked list with dummy head/tail sentinels (as shown in topic 05).
2. Do NOT use Java's `LinkedHashMap` — the goal is to understand the mechanics.
3. Write at least 3 separate test scenarios that exercise the eviction logic.

<details>
<summary>Solution</summary>

```java
class LRUCache {
    private static class Node {
        int key, val;
        Node prev, next;
        Node(int k, int v) { key = k; val = v; }
    }

    private final int capacity;
    private final Map<Integer, Node> map = new HashMap<>();
    private final Node head = new Node(0, 0);  // LRU sentinel
    private final Node tail = new Node(0, 0);  // MRU sentinel

    public LRUCache(int capacity) {
        this.capacity = capacity;
        head.next = tail;
        tail.prev = head;
    }

    public int get(int key) {
        if (!map.containsKey(key)) return -1;
        Node n = map.get(key);
        remove(n); insertAtTail(n);
        return n.val;
    }

    public void put(int key, int value) {
        if (map.containsKey(key)) {
            Node n = map.get(key);
            n.val = value;
            remove(n); insertAtTail(n);
        } else {
            if (map.size() == capacity) {
                Node lru = head.next;
                remove(lru); map.remove(lru.key);
            }
            Node n = new Node(key, value);
            insertAtTail(n); map.put(key, n);
        }
    }

    private void remove(Node n) {
        n.prev.next = n.next;
        n.next.prev = n.prev;
    }

    private void insertAtTail(Node n) {
        n.prev = tail.prev; n.next = tail;
        tail.prev.next = n; tail.prev = n;
    }
}
```

**Tests:**
```java
@Test
void testLRUBasic() {
    LRUCache c = new LRUCache(2);
    c.put(1, 1); c.put(2, 2);
    assertEquals(1, c.get(1));
    c.put(3, 3);
    assertEquals(-1, c.get(2));   // evicted
    assertEquals(3, c.get(3));
}

@Test
void testLRUUpdateExisting() {
    LRUCache c = new LRUCache(2);
    c.put(1, 1); c.put(2, 2);
    c.put(1, 10);                  // update promotes key 1
    c.put(3, 3);                   // should evict key 2 (LRU), not key 1
    assertEquals(10, c.get(1));
    assertEquals(-1, c.get(2));
}

@Test
void testLRUCapacityOne() {
    LRUCache c = new LRUCache(1);
    c.put(1, 1); c.put(2, 2);
    assertEquals(-1, c.get(1));   // evicted
    assertEquals(2,  c.get(2));
}
```

</details>

---

*Completing all five exercises puts you at LC Medium confidence for the full linked list problem set.*
