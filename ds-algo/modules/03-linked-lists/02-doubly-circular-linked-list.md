# 3.2 — Doubly & Circular Linked Lists

## Concept

A doubly linked list adds a `prev` pointer to each node. This makes backward traversal O(1) and deletion of a **given node** O(1) without the predecessor search that singly linked lists require. Circular linked lists close the tail back to the head — used in operating system schedulers and LRU caches.

---

## Deep Dive

### Doubly Linked List Structure

```
null ←── [prev|10|next] ←──▶ [prev|20|next] ←──▶ [prev|30|next] ──▶ null
         ↑ head                                    ↑ tail
```

```java
class DoublyNode<T> {
    T val;
    DoublyNode<T> prev, next;
    DoublyNode(T val) { this.val = val; }
}
```

**O(1) delete given node** (the key advantage over singly):
```java
void deleteNode(DoublyNode<T> node) {
    if (node.prev != null) node.prev.next = node.next;
    if (node.next != null) node.next.prev = node.prev;
    // No traversal needed — O(1)
}
```

### Circular Linked List

```
┌──────────────────────────────────────────────────────┐
│                                                      │
└──▶ [10|next] ──▶ [20|next] ──▶ [30|next] ──▶ [40|next]
```

The tail's `next` points back to the head. Useful when traversal wraps around (round-robin scheduling).

```java
// Detect if a list is circular
boolean isCircular(ListNode head) {
    if (head == null) return false;
    ListNode slow = head, fast = head.next;
    while (fast != null && fast.next != null) {
        if (slow == fast) return true;
        slow = slow.next;
        fast = fast.next.next;
    }
    return false;
}
```

### Java's LinkedHashMap — Doubly Linked + HashMap

`LinkedHashMap` combines a `HashMap` with a doubly linked list to maintain *insertion order* or *access order*. This is exactly what powers an LRU Cache:

```java
// LRU Cache using LinkedHashMap with access-order mode
int capacity = 3;
Map<Integer, Integer> cache = new LinkedHashMap<>(capacity, 0.75f, true) {
    protected boolean removeEldestEntry(Map.Entry e) {
        return size() > capacity;  // auto-evict when over capacity
    }
};

cache.put(1, 10); cache.put(2, 20); cache.put(3, 30);
cache.get(1);      // marks 1 as most recently used
cache.put(4, 40);  // evicts least recently used (2)
// cache = {3→30, 1→10, 4→40}
```

---

## Code Examples

### Implement LRU Cache From Scratch

```java
// The interview-correct implementation: HashMap + custom DLL
// (Not using LinkedHashMap — interviewers want to see the DLL)

class LRUCache {
    private static class Node {
        int key, val;
        Node prev, next;
        Node(int k, int v) { key = k; val = v; }
    }

    private final int capacity;
    private final Map<Integer, Node> map;
    private final Node head, tail;  // dummy sentinels

    public LRUCache(int capacity) {
        this.capacity = capacity;
        map = new HashMap<>();
        head = new Node(0, 0);
        tail = new Node(0, 0);
        head.next = tail;
        tail.prev = head;
    }

    public int get(int key) {
        Node node = map.get(key);
        if (node == null) return -1;
        moveToFront(node);    // most recently used
        return node.val;
    }

    public void put(int key, int value) {
        if (map.containsKey(key)) {
            Node node = map.get(key);
            node.val = value;
            moveToFront(node);
        } else {
            Node node = new Node(key, value);
            map.put(key, node);
            addToFront(node);
            if (map.size() > capacity) {
                Node evicted = removeLast();
                map.remove(evicted.key);
            }
        }
    }

    private void addToFront(Node node) {
        node.prev = head;
        node.next = head.next;
        head.next.prev = node;
        head.next = node;
    }

    private void remove(Node node) {
        node.prev.next = node.next;
        node.next.prev = node.prev;
    }

    private void moveToFront(Node node) { remove(node); addToFront(node); }
    private Node removeLast() { Node last = tail.prev; remove(last); return last; }
}
// get/put: O(1) — HashMap lookup + O(1) DLL operations
```

---

## Try It Yourself

**Exercise:** Implement a doubly linked list's `addFirst`, `addLast`, `removeFirst`, `removeLast` using a sentinel head and tail (as in the LRU Cache above).

<details>
<summary>Show skeleton + explanation</summary>

Using sentinel nodes (dummy head and tail) eliminates all null checks for edge cases:

```java
class Deque<T> {
    private Node head = new Node(null), tail = new Node(null);

    Deque() { head.next = tail; tail.prev = head; }

    void addFirst(T val) {
        Node node = new Node(val);
        node.next = head.next;
        node.prev = head;
        head.next.prev = node;
        head.next = node;
    }

    void addLast(T val) {
        Node node = new Node(val);
        node.prev = tail.prev;
        node.next = tail;
        tail.prev.next = node;
        tail.prev = node;
    }

    T removeFirst() {
        if (head.next == tail) throw new NoSuchElementException();
        Node node = head.next;
        head.next = node.next;
        node.next.prev = head;
        return node.val;
    }

    T removeLast() {
        if (tail.prev == head) throw new NoSuchElementException();
        Node node = tail.prev;
        tail.prev = node.prev;
        node.prev.next = tail;
        return node.val;
    }
}
```

The sentinel pattern eliminates "is head/tail null?" — the DLL is never truly empty from the perspective of pointer manipulation.

</details>

---

## Capstone Connection

`DoublyLinkedList<T>` in AlgoForge uses the sentinel pattern. The LRU Cache implementation is in `problems/linkedlists/LRUCache.java` — one of the most commonly asked design problems at FAANG. LC #146.
