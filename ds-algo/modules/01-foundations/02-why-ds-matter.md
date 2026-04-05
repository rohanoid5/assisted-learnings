# 1.2 — Why Data Structures Matter

## Concept

An algorithm is only as fast as the data structure it operates on. This topic makes that concrete with a canonical example: finding the intersection of two lists. We will see the exact same logical operation run in O(n²), O(n log n), and O(n) — purely because of the data structure choice.

---

## Deep Dive

### The Performance Gap Is Not Theoretical

For n = 1,000,000 items on a modern computer running ~10⁸ operations/second:

```
Runtime      Operations   Time estimate
──────────   ──────────   ─────────────
O(1)         1            < 1 µs
O(log n)     ~20          < 1 µs
O(n)         1,000,000    ~10 ms
O(n log n)   20,000,000   ~200 ms
O(n²)        10¹²         ~2.8 hours  ← interviewer is long gone
O(2ⁿ)        10³⁰⁰⁰⁰⁰     heat death of the universe
```

The jump from O(n) to O(n²) is not "a bit slower" — it means unusable for any non-trivial input.

---

### Problem-to-Data-Structure Mapping

This table is the most practically useful thing in this module. Memorize it.

```
Problem signal                          → First DS to reach for
──────────────────────────────────────────────────────────────────
"Does X exist?" / "Have I seen X?"      → HashSet
"How many times does X appear?"         → HashMap<X, Integer>
"What is X mapped to?"                  → HashMap<K, V>
"Shortest path between A and B"         → BFS (Queue) + visited Set
"All possible combinations/paths"       → DFS/Backtracking (Stack/recursion)
"Always give me the min/max"            → PriorityQueue (Heap)
"Maintain sorted order + predecessor"   → TreeMap / TreeSet
"Process in arrival order"              → Queue (ArrayDeque)
"Process in reverse of arrival"         → Stack (ArrayDeque)
"Words with common prefix"              → Trie
"Range queries (sum/min from i to j)"   → Segment Tree / Fenwick Tree
"Connected components"                  → Union-Find
"Ordered key-value, fast floor/ceil"    → TreeMap
```

---

### Case Study: Two-List Intersection

**Problem:** Given two lists of integers, find all elements that appear in both.

#### Approach A — Brute Force (O(n × m))

```java
// DS: List — wrong tool for membership testing
public List<Integer> intersectBrute(List<Integer> a, List<Integer> b) {
    List<Integer> result = new ArrayList<>();
    for (int x : a) {
        if (b.contains(x)) result.add(x);  // b.contains = O(m) scan
    }
    return result;  // Total: O(n × m) — for n=m=10,000 that's 100M ops
}
```

#### Approach B — Sort + Two Pointer (O(n log n + m log m))

```java
// DS: Sorted arrays — enables linear merge
public List<Integer> intersectSorted(int[] a, int[] b) {
    Arrays.sort(a); // O(n log n)
    Arrays.sort(b); // O(m log m)
    List<Integer> result = new ArrayList<>();
    int i = 0, j = 0;
    while (i < a.length && j < b.length) {
        if (a[i] == b[j]) {
            result.add(a[i]);
            i++; j++;
        } else if (a[i] < b[j]) {
            i++;
        } else {
            j++;
        }
    }
    return result;  // Merge: O(n + m). Total: O(n log n + m log m)
}
```

#### Approach C — HashSet (O(n + m))

```java
// DS: HashSet — O(1) membership, that's the whole trick
public List<Integer> intersectHash(int[] a, int[] b) {
    Set<Integer> setA = new HashSet<>();
    for (int x : a) setA.add(x);          // O(n)

    List<Integer> result = new ArrayList<>();
    for (int x : b) {
        if (setA.contains(x)) result.add(x);  // O(1) per lookup
    }
    return result;  // Total: O(n + m) — optimal
}
```

---

### When "Better" Is Not Always Better

More efficient is not always the right choice:

| Situation | Use |
|-----------|-----|
| n is always ≤ 50 | Brute force is fine — simpler code, fewer bugs |
| Memory is extremely limited | O(n log n) sort+merge over O(n) HashSet |
| Keys must stay sorted | TreeMap O(log n) over HashMap O(1) |
| Multithreaded access | `ConcurrentHashMap`, not `HashMap` |

> **Interview rule:** always state your assumptions. "I'll use a HashSet for O(1) lookup — assuming we have O(n) extra space available."

---

## Code Examples

### Java Collections Big-O Cheat Sheet

```java
// Array / ArrayList
arr[i]                    // O(1) random access
list.add(item)            // O(1) amortized (occasional O(n) resize)
list.add(0, item)         // O(n) — shifts all elements right
list.remove(0)            // O(n) — shifts all elements left
list.contains(item)       // O(n) — linear scan
Collections.sort(list)    // O(n log n) — Timsort

// LinkedList
deque.addFirst/addLast    // O(1)
deque.get(index)          // O(n) — must traverse from head

// HashMap / HashSet
map.put / map.get         // O(1) average, O(n) worst (all collisions)
map.containsKey           // O(1) average
map.remove                // O(1) average
new TreeMap(map)          // O(n log n) to build sorted map from unsorted

// TreeMap / TreeSet (Red-Black Tree)
treeMap.put / get         // O(log n)
treeMap.floorKey(k)       // O(log n)
treeMap.headMap(k)        // O(log n) to get view

// PriorityQueue (Min-Heap)
pq.offer(item)            // O(log n) — sift up
pq.poll()                 // O(log n) — sift down
pq.peek()                 // O(1) — just read the root
```

---

## Try It Yourself

**Exercise:** You have two arrays of integers, each of length n. For each of the three approaches below, state the time and space complexity *before* running them.

```java
int[] a = {1, 3, 5, 7, 9};  // assume n = 5 here, generalize to n
int[] b = {2, 3, 6, 7, 10};

// Method 1: nested loop
// Method 2: sort both + two pointer
// Method 3: HashSet of a, scan b
```

Then verify: what is the crossover point where Method 3 beats Method 2? (Hint: small n has overhead from HashSet allocation.)

<details>
<summary>Show analysis</summary>

| Method | Time | Space |
|--------|------|-------|
| Nested loop | O(n²) | O(1) |
| Sort + two pointer | O(n log n) | O(1) for in-place sort |
| HashSet | O(n) avg | O(n) for the set |

**Crossover:** For tiny n (< ~20), the constant factor of HashSet creation makes Method 2 competitive or faster. For n ≥ 50, HashSet wins clearly. For n ≥ 1000, the gap is dramatic.

In interviews, always go with the asymptotically better solution unless you are explicitly asked to minimize memory.

</details>

---

## Capstone Connection

`ComplexityBenchmark.java` in AlgoForge benchmarks exactly these scenarios — HashMap `get` vs linear scan, `Arrays.sort` vs bubble sort. Run it after completing this module to see the growth rates in action on your own machine.
