# 5.4 — Design Problems: HashMap & HashSet from Scratch

## Concept

"Design a HashMap" and "Design a HashSet" are explicit LeetCode problems (LC #706, #705) — but more importantly, implementing them from scratch cements your understanding of bucket arrays, chaining, load factor, and the `hashCode`/`equals` contract. You can't explain what you haven't built.

---

## Deep Dive

### Implementation Strategy: Chaining

Use an array of linked lists (or `ArrayList`). Each bucket holds a list of `(key, value)` pairs:

```
Capacity = 8 (always use a prime or power of 2 for index uniformity)
Load factor threshold = 0.75 → resize when size > capacity * 0.75

buckets[]
  [0] → null
  [1] → (5, "five") → (13, "thirteen") → null   ← collision: 5%8=5, 13%8=5
  [2] → null
  [3] → (3, "three") → null
  ...

get(13): hash(13) → bucket[1] → chain search → key==13 found → "thirteen"
```

---

### The Index Calculation

```java
private int getIndex(int key) {
    return Math.abs(key % capacity);
    // Math.abs handles negative hash codes
    // For String keys: Math.abs(key.hashCode() % capacity)
}
```

---

### When to Resize

```
After each put: if (size > capacity * LOAD_FACTOR) resize()

resize():
  - Double capacity
  - Create new bucket array
  - Re-insert every existing entry (must re-hash — index changes!)
  - This is O(n) but amortized O(1) per insert
```

---

## Code Examples

### Design HashMap (LC #706) — Integer keys, integer values

```java
class MyHashMap {
    private static final int INITIAL_CAPACITY = 16;
    private static final double LOAD_FACTOR = 0.75;

    private int capacity;
    private int size;
    private List<int[]>[] buckets;  // each int[] is [key, value]

    @SuppressWarnings("unchecked")
    public MyHashMap() {
        capacity = INITIAL_CAPACITY;
        buckets = new List[capacity];
    }

    private int idx(int key) {
        return key % capacity;  // keys are non-negative per problem constraints
    }

    public void put(int key, int value) {
        int i = idx(key);
        if (buckets[i] == null) buckets[i] = new LinkedList<>();

        for (int[] pair : buckets[i]) {
            if (pair[0] == key) { pair[1] = value; return; }  // update
        }
        buckets[i].add(new int[]{key, value});  // insert
        size++;

        if ((double) size / capacity > LOAD_FACTOR) resize();
    }

    public int get(int key) {
        int i = idx(key);
        if (buckets[i] == null) return -1;
        for (int[] pair : buckets[i]) {
            if (pair[0] == key) return pair[1];
        }
        return -1;
    }

    public void remove(int key) {
        int i = idx(key);
        if (buckets[i] == null) return;
        buckets[i].removeIf(pair -> pair[0] == key);
        // Note: we don't decrement size here to keep the example simple
    }

    @SuppressWarnings("unchecked")
    private void resize() {
        capacity *= 2;
        List<int[]>[] old = buckets;
        buckets = new List[capacity];
        size = 0;
        for (List<int[]> bucket : old) {
            if (bucket == null) continue;
            for (int[] pair : bucket) put(pair[0], pair[1]);  // re-hash
        }
    }
}
```

### Design HashSet (LC #705)

```java
// Simplified: no values, just keys. Backed by a boolean-valued HashMap-like structure.
class MyHashSet {
    private static final int CAPACITY = 1024;
    private List<Integer>[] buckets;

    @SuppressWarnings("unchecked")
    public MyHashSet() {
        buckets = new List[CAPACITY];
    }

    private int idx(int key) { return key % CAPACITY; }

    public void add(int key) {
        int i = idx(key);
        if (buckets[i] == null) buckets[i] = new LinkedList<>();
        if (!contains(key)) buckets[i].add(key);
    }

    public void remove(int key) {
        int i = idx(key);
        if (buckets[i] != null) buckets[i].remove(Integer.valueOf(key));
    }

    public boolean contains(int key) {
        int i = idx(key);
        return buckets[i] != null && buckets[i].contains(key);
    }
}
```

---

### Generic HashMap Implementation (AlgoForge Part A)

```java
// The full generic version lives in AlgoForge:
// src/main/java/com/algoforge/datastructures/hashing/MyHashMap.java

public class MyHashMap<K, V> {
    private static final int DEFAULT_CAPACITY = 16;
    private static final float LOAD_FACTOR = 0.75f;

    private Object[] table;  // array of Entry<K,V>[] chains
    private int size;
    private int capacity;

    static class Entry<K, V> {
        K key; V value; Entry<K, V> next;
        Entry(K key, V value) { this.key = key; this.value = value; }
    }

    @SuppressWarnings("unchecked")
    public MyHashMap() {
        capacity = DEFAULT_CAPACITY;
        table = new Object[capacity];
    }

    private int hash(K key) {
        return key == null ? 0 : Math.abs(key.hashCode() % capacity);
    }

    public V put(K key, V value) {
        int i = hash(key);
        @SuppressWarnings("unchecked")
        Entry<K, V> head = (Entry<K, V>) table[i];

        for (Entry<K, V> e = head; e != null; e = e.next) {
            if (e.key == key || (key != null && key.equals(e.key))) {
                V old = e.value;
                e.value = value;
                return old;
            }
        }
        // Prepend new entry (O(1) vs appending which requires traversal)
        Entry<K, V> newEntry = new Entry<>(key, value);
        newEntry.next = head;
        table[i] = newEntry;
        if (++size > capacity * LOAD_FACTOR) resize();
        return null;
    }

    public V get(K key) {
        int i = hash(key);
        @SuppressWarnings("unchecked")
        Entry<K, V> e = (Entry<K, V>) table[i];
        while (e != null) {
            if (e.key == key || (key != null && key.equals(e.key))) return e.value;
            e = e.next;
        }
        return null;
    }

    // resize() — doubles capacity and rehashes all entries
    @SuppressWarnings("unchecked")
    private void resize() {
        capacity *= 2;
        Object[] old = table;
        table = new Object[capacity];
        size = 0;
        for (Object obj : old) {
            for (Entry<K,V> e = (Entry<K,V>) obj; e != null; e = e.next)
                put(e.key, e.value);
        }
    }
}
```

---

## Try It Yourself

**Exercise:** Implement a simple LRU (Least Recently Used) cache with O(1) get and put.

```java
// LRUCache(int capacity) — initialize with a positive capacity
// int get(int key)        — return value if key exists, else -1
// void put(int key, int value) — insert or update, evict LRU if over capacity

// Hint: combine a HashMap (O(1) lookup) and a doubly linked list (O(1) move-to-front + evict)
class LRUCache {
    // your code here
}
```

<details>
<summary>Solution</summary>

```java
class LRUCache {
    private final int capacity;
    private final Map<Integer, Node> map = new HashMap<>();
    private final Node head = new Node(0, 0); // dummy head (most recent)
    private final Node tail = new Node(0, 0); // dummy tail (least recent)

    static class Node {
        int key, val;
        Node prev, next;
        Node(int key, int val) { this.key = key; this.val = val; }
    }

    public LRUCache(int capacity) {
        this.capacity = capacity;
        head.next = tail;
        tail.prev = head;
    }

    public int get(int key) {
        if (!map.containsKey(key)) return -1;
        Node node = map.get(key);
        remove(node);
        insertFront(node);  // mark as recently used
        return node.val;
    }

    public void put(int key, int value) {
        if (map.containsKey(key)) {
            Node node = map.get(key);
            node.val = value;
            remove(node);
            insertFront(node);
        } else {
            if (map.size() == capacity) {
                Node lru = tail.prev;  // least recently used
                remove(lru);
                map.remove(lru.key);
            }
            Node node = new Node(key, value);
            insertFront(node);
            map.put(key, node);
        }
    }

    private void remove(Node n) {
        n.prev.next = n.next;
        n.next.prev = n.prev;
    }

    private void insertFront(Node n) {
        n.next = head.next;
        n.prev = head;
        head.next.prev = n;
        head.next = n;
    }
    // Time: O(1) for both get and put.  Space: O(capacity)
}
```

The doubly linked list maintains recency order — head side is most recent, tail side is least recent. HashMap gives O(1) access to any node so we can reposition it in O(1).

</details>

---

## Capstone Connection

`MyHashMap.java` is the first tree-free data structure you implement in AlgoForge's `datastructures/hashing/` package. The LRU Cache problem is already solved in `problems/hashtables/LRUCache.java` — it's a prime example of combining two data structures (HashMap + LinkedList) to hit O(1) on multiple operations simultaneously.
