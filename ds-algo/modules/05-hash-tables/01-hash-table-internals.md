# 5.1 — Hash Table Internals

## Concept

A **hash table** achieves O(1) average-case lookup, insertion, and deletion by turning a key into an array index in one step. That single operation — the hash function — is the core of what makes hash tables powerful. But "average case" hides the worst case: every key hashing to the same slot degrades to O(n). Understanding the internals lets you avoid that trap.

---

## Deep Dive

### The Core Mechanism

```
Key "alice"
    │
    ▼
Hash Function h(k) = hash("alice") mod capacity
    │
    ▼
Index = 3
    │
    ▼
┌───┬───┬───┬───────────────────┬───┬───┐
│   │   │   │ "alice" → 25000   │   │   │  ← bucket array
└───┴───┴───┴───────────────────┴───┴───┘
  0   1   2         3             4   5
```

Three components:
1. **Hash function** — maps key to an integer
2. **Compression** — maps that integer to a valid index (`% capacity`)
3. **Collision resolution** — handles two keys mapping to the same index

---

### What Makes a Good Hash Function?

A good hash function is:
- **Deterministic** — same key always produces same hash
- **Uniform** — distributes keys evenly across all buckets
- **Fast** — ideally O(1) or O(key length)
- **Avalanche effect** — small change in key → large change in hash

Java's `String.hashCode()` uses a polynomial rolling hash:

```java
// Java's String.hashCode() — simplified
int hash = 0;
for (char c : s.toCharArray()) {
    hash = 31 * hash + c;
}
// 31 is prime — reduces collisions, fits nicely in CPU multiply
```

---

### Collision Resolution: Chaining

Each bucket holds a linked list (or tree in Java 8+) of all entries that hash to that slot:

```
Bucket array:
[0] → null
[1] → ("bob", 300) → null
[2] → ("alice", 100) → ("charlie", 200) → null  ← collision!
[3] → null
[4] → ("diana", 400) → null
```

- **Lookup:** hash key → go to bucket → linear search in list
- **Worst case (all keys in one bucket):** O(n) lookup
- **Java 8+ optimization:** when a bucket's list exceeds 8 entries, it becomes a **Red-Black Tree** → O(log n) worst case

---

### Collision Resolution: Open Addressing

All entries live in the array itself — no linked lists. On collision, probe for the next empty slot:

```
Linear probing: try index+1, index+2, index+3, ...
Quadratic probing: try index+1², index+2², index+3², ...
Double hashing: try index + k*h₂(key) for k = 1,2,3,...

Insert "frank" → hashes to index 2 (occupied) → try 3 (occupied) → try 4 (empty) ✓
```

**Primary clustering** is the main weakness of linear probing: collisions create runs of filled slots, making new collisions more likely.

---

### Load Factor and Resizing

```
load factor α = (number of entries) / (capacity)

α < 0.75  →  performance acceptable
α ≥ 0.75  →  Java's HashMap triggers resize (doubles capacity, rehashes all)

Default initial capacity: 16
Default load factor: 0.75
First resize at: 12 entries (16 × 0.75)
```

**Why resizing is amortized O(1):** doubling means each entry is rehashed at most once per doubling event. The cost is spread over all insertions.

```
Insertions:  1  2  3  ... 12 [RESIZE: 12 rehashes] 13 ... 24 [RESIZE: 24 rehashes] ...
Rehash cost: 0  0  0  ...  0            12          0 ...  0              24

Total cost for n insertions ≈ n + n/2 + n/4 + ... ≈ 2n → amortized O(1)
```

---

### Java HashMap vs Hashtable vs ConcurrentHashMap

| | `HashMap` | `Hashtable` | `ConcurrentHashMap` |
|---|-----------|-------------|---------------------|
| Thread-safe | No | Yes (all ops synchronized) | Yes (segment locking) |
| Null keys | One null key allowed | Not allowed | Not allowed |
| Performance | Best for single thread | Slow (coarse lock) | Best for concurrency |
| Ordered | No | No | No |
| Use when | Single-threaded | Never (legacy) | Multi-threaded |

> Use `LinkedHashMap` for insertion-order preservation, `TreeMap` for sorted order.

---

## Code Examples

### Example 1: Frequency Counting Pattern

```java
// Count character frequencies — the foundation of dozens of problems
public Map<Character, Integer> charFrequency(String s) {
    Map<Character, Integer> freq = new HashMap<>();
    for (char c : s.toCharArray()) {
        freq.put(c, freq.getOrDefault(c, 0) + 1);
        // getOrDefault avoids NullPointerException on first occurrence
    }
    return freq;
}
// Time: O(n), Space: O(k) where k = distinct characters (≤ 26 for lowercase)
```

### Example 2: Hash Collision Demonstration

```java
// These two strings have the same Java hashCode (a famous collision)
System.out.println("Aa".hashCode());    // 2112
System.out.println("BB".hashCode());    // 2112
// HashMap handles this via chaining — both can coexist in the same bucket
```

### Example 3: Custom hashCode and equals

```java
// For a key to work correctly in HashMap, it MUST override both
class Point {
    int x, y;
    Point(int x, int y) { this.x = x; this.y = y; }

    @Override
    public int hashCode() {
        return 31 * x + y;  // Simple but effective for small coords
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Point)) return false;
        Point p = (Point) o;
        return x == p.x && y == p.y;
    }
}
// Without overriding equals, two Point(1,2) objects are never considered equal
// Without overriding hashCode, a correct equals still breaks HashMap
```

---

## Try It Yourself

**Exercise:** You have an array of integers. Find any pair that sums to a target value. Return their indices.

```java
// Input:  nums = [2, 7, 11, 15], target = 9
// Output: [0, 1]   (nums[0] + nums[1] = 9)

// Brute force is O(n²). Can you do O(n) using a hash table?
// Hint: for each number x, check if (target - x) is already in the map.
public int[] twoSum(int[] nums, int target) {
    // your code here
}
```

<details>
<summary>Solution</summary>

```java
public int[] twoSum(int[] nums, int target) {
    Map<Integer, Integer> seen = new HashMap<>(); // value → index
    for (int i = 0; i < nums.length; i++) {
        int complement = target - nums[i];
        if (seen.containsKey(complement)) {
            return new int[]{seen.get(complement), i};
        }
        seen.put(nums[i], i);
    }
    throw new IllegalArgumentException("No solution"); // problem guarantees one exists
}
// Time: O(n) — one pass.  Space: O(n) — stores up to n entries.
// The insight: instead of asking "does any previous element pair with me?",
// we store "here I am — come find me" and check on each step.
```

</details>

---

## Capstone Connection

`MyHashMap.java` in AlgoForge implements chaining with a fixed array of `LinkedList<Entry<K,V>>`. You'll add it in the Module 05 exercises. This file gives you the theory to make informed decisions about initial capacity, load factor threshold, and whether to use `Objects.hashCode()` or a custom function.
