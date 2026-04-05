# 3.5 — LRU Cache

## Concept

An **LRU (Least Recently Used) Cache** keeps the most-recently-accessed items. When the cache is full and a new item must be inserted, the least-recently-used item is evicted. The classic interview target is LC #146: implement LRU cache with O(1) `get` and `put`.

The trick: combine a **HashMap** (for O(1) lookup) with a **doubly linked list** (for O(1) eviction and promotion).

---

## Deep Dive

### Why a Doubly Linked List?

```
We need O(1) for three operations:
  1. Look up any key         → HashMap<key, node>
  2. Move a node to "most recent" position → doubly linked list (remove + re-add at tail)
  3. Evict the "least recent" node         → remove from head

Doubly linked list lets us remove a node in O(1) given a direct reference.
A singly linked list would need O(n) to find the predecessor.

Structure:
  dummy_head ↔ node_A ↔ node_B ↔ node_C ↔ dummy_tail
  (LRU end)                              (MRU end)

  HashMap: { key_A → node_A, key_B → node_B, ... }
```

### get / put Operations

```
get(key):
  - If not in map → return -1
  - Move node to tail (= most recently used)
  - Return value

put(key, value):
  - If key exists → update value, move to tail
  - If key new:
    - If at capacity → remove node at head (LRU), delete from map
    - Create new node, add to tail, put in map
```

---

## Code Examples

### Full LRU Cache Implementation

```java
class LRUCache {

    // Doubly linked list node
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
        head = new Node(0, 0);  // LRU end
        tail = new Node(0, 0);  // MRU end
        head.next = tail;
        tail.prev = head;
    }

    public int get(int key) {
        if (!map.containsKey(key)) return -1;
        Node node = map.get(key);
        moveToTail(node);
        return node.val;
    }

    public void put(int key, int value) {
        if (map.containsKey(key)) {
            Node node = map.get(key);
            node.val = value;
            moveToTail(node);
        } else {
            if (map.size() == capacity) evictLRU();
            Node node = new Node(key, value);
            insertAtTail(node);
            map.put(key, node);
        }
    }

    // ---- internal helpers ----

    private void remove(Node node) {
        node.prev.next = node.next;
        node.next.prev = node.prev;
    }

    private void insertAtTail(Node node) {
        node.prev = tail.prev;
        node.next = tail;
        tail.prev.next = node;
        tail.prev = node;
    }

    private void moveToTail(Node node) {
        remove(node);
        insertAtTail(node);
    }

    private void evictLRU() {
        Node lru = head.next;   // first real node after dummy head
        remove(lru);
        map.remove(lru.key);
    }
}
```

### Usage

```java
LRUCache cache = new LRUCache(2);
cache.put(1, 1);  // cache: {1=1}
cache.put(2, 2);  // cache: {1=1, 2=2}
cache.get(1);     // returns 1, promotes key 1 to MRU
cache.put(3, 3);  // evicts key 2 (LRU), cache: {1=1, 3=3}
cache.get(2);     // returns -1 (evicted)
```

---

## Try It Yourself

**Exercise (extension):** Implement an **LFU Cache** (Least Frequently Used). When evicting, remove the key with the lowest access frequency. On a tie in frequency, remove the least recently used among ties.

Still O(1) get and put. Hint: you need a `Map<freq, DoublyLinkedList>` and a `minFreq` pointer.

<details>
<summary>Show solution outline</summary>

```java
class LFUCache {
    // Data structures:
    //   Map<key, value>           → O(1) value lookup
    //   Map<key, freq>            → O(1) frequency lookup  
    //   Map<freq, LinkedHashSet>  → preserves insertion order within a frequency
    //   int minFreq               → track minimum frequency for eviction

    private final int capacity;
    private int minFreq;
    private final Map<Integer, Integer> keyVal;
    private final Map<Integer, Integer> keyFreq;
    private final Map<Integer, LinkedHashSet<Integer>> freqKeys;

    public LFUCache(int capacity) {
        this.capacity = capacity;
        keyVal = new HashMap<>();
        keyFreq = new HashMap<>();
        freqKeys = new HashMap<>();
    }

    public int get(int key) {
        if (!keyVal.containsKey(key)) return -1;
        incrementFreq(key);
        return keyVal.get(key);
    }

    public void put(int key, int value) {
        if (capacity <= 0) return;
        if (keyVal.containsKey(key)) {
            keyVal.put(key, value);
            incrementFreq(key);
        } else {
            if (keyVal.size() == capacity) evict();
            keyVal.put(key, value);
            keyFreq.put(key, 1);
            freqKeys.computeIfAbsent(1, k -> new LinkedHashSet<>()).add(key);
            minFreq = 1;
        }
    }

    private void incrementFreq(int key) {
        int freq = keyFreq.get(key);
        keyFreq.put(key, freq + 1);
        freqKeys.get(freq).remove(key);
        if (freqKeys.get(freq).isEmpty()) {
            freqKeys.remove(freq);
            if (minFreq == freq) minFreq++;
        }
        freqKeys.computeIfAbsent(freq + 1, k -> new LinkedHashSet<>()).add(key);
    }

    private void evict() {
        LinkedHashSet<Integer> minSet = freqKeys.get(minFreq);
        int evictKey = minSet.iterator().next();  // LRU among min-freq
        minSet.remove(evictKey);
        if (minSet.isEmpty()) freqKeys.remove(minFreq);
        keyVal.remove(evictKey);
        keyFreq.remove(evictKey);
    }
}
```

</details>

---

## Capstone Connection

LRU Cache is a system design staple — CDNs, database buffer pools, OS page caches, and CPU L1/L2 caches all use LRU-like eviction policies. In AlgoForge, build it in the design problems folder and write tests that exercise eviction edge cases.
