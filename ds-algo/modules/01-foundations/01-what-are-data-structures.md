# 1.1 — What Are Data Structures?

## Concept

A **data structure** is a way of organizing data in memory so that it can be accessed and modified efficiently. The choice of data structure is often *more impactful than the algorithm itself* — a great algorithm on the wrong data structure is still slow.

---

## Deep Dive

### The Classification Tree

Data structures split into two families based on how they organize elements in memory:

```
                    Data Structures
                   /               \
            Linear                Non-Linear
           /  |  |  \               /       \
        Array LL Stk Que         Trees     Graphs
               |                /  |  \
              Hash          BST  Heap  Trie
             Tables
```

**Linear** data structures arrange elements sequentially — each element has at most one predecessor and one successor.

**Non-linear** data structures allow elements to have multiple predecessors or successors (trees, graphs).

---

### Linear Data Structures

| DS | Underlying Storage | Access | Insert (head) | Insert (tail) | Search |
|----|-------------------|--------|--------------|--------------|--------|
| Array | Contiguous block | O(1) | O(n) | O(1) amortized | O(n) |
| Linked List | Scattered nodes + pointers | O(n) | O(1) | O(n) or O(1) with tail ptr | O(n) |
| Stack | Array or LL | O(1) top | O(1) push | — | O(n) |
| Queue | Array or LL | O(1) front | — | O(1) enqueue | O(n) |
| Hash Table | Array of buckets | O(1) avg | O(1) avg | — | O(1) avg |

---

### Non-Linear Data Structures

| DS | Key Property | Primary Use |
|----|-------------|------------|
| Binary Tree | Hierarchical, each node ≤ 2 children | Expression parsing, file systems |
| BST | Left < root < right | Ordered data, predecessor/successor queries |
| Heap | Parent ≤ children (min-heap) | Priority queue, Dijkstra, Top-K |
| Graph | Nodes + edges (directed or undirected) | Networks, shortest paths, scheduling |
| Trie | Prefix-organized character tree | Autocomplete, dictionary search |

---

### Choosing the Right Data Structure

The first question in any interview should be: _"What operations do I need, and how frequently?"_

```
Need fast lookup by key?         → HashMap / HashSet
Need sorted order?               → TreeMap / Array (sorted)
Need LIFO (undo, call stack)?    → Stack
Need FIFO (BFS, task queue)?     → Queue / ArrayDeque
Need min/max efficiently?        → PriorityQueue (Heap)
Need prefix search?              → Trie
Need parent-child relationships? → Tree
Need relationships + paths?      → Graph
```

---

## Code Examples

### Example 1: The Cost of the Wrong Data Structure

```java
import java.util.*;

public class WrongVsRightDS {

    // ❌ O(n) contains check — wrong DS for membership testing
    public static boolean hasDuplicateList(int[] nums) {
        List<Integer> seen = new ArrayList<>();
        for (int n : nums) {
            if (seen.contains(n)) return true;  // O(n) per call → O(n²) total
            seen.add(n);
        }
        return false;
    }

    // ✅ O(1) amortized contains check — right DS
    public static boolean hasDuplicateSet(int[] nums) {
        Set<Integer> seen = new HashSet<>();
        for (int n : nums) {
            if (seen.contains(n)) return true;  // O(1) avg → O(n) total
            seen.add(n);
        }
        return false;
    }

    public static void main(String[] args) {
        int[] data = new int[50_000];
        for (int i = 0; i < data.length; i++) data[i] = i;

        long t1 = System.currentTimeMillis();
        hasDuplicateList(data);
        System.out.println("List:    " + (System.currentTimeMillis() - t1) + " ms");

        long t2 = System.currentTimeMillis();
        hasDuplicateSet(data);
        System.out.println("HashSet: " + (System.currentTimeMillis() - t2) + " ms");
        // HashSet is ~100-1000x faster for n=50,000
    }
}
```

### Example 2: Java's Built-in Data Structures

```java
import java.util.*;

public class JavaCollectionsDemo {

    public static void main(String[] args) {
        // ArrayList — dynamic array, O(1) access, O(n) insert-at-index
        List<String> list = new ArrayList<>();
        list.add("apple");
        list.add("banana");
        System.out.println(list.get(0));    // O(1) random access

        // LinkedList — doubly linked, O(1) add/remove at ends
        Deque<String> deque = new ArrayDeque<>();
        deque.addFirst("first");
        deque.addLast("last");

        // HashMap — O(1) avg get/put, O(n) worst case (all hash collisions)
        Map<String, Integer> freq = new HashMap<>();
        freq.put("hello", 1);
        freq.merge("hello", 1, Integer::sum);   // word frequency increment
        System.out.println(freq.get("hello"));  // 2

        // TreeMap — sorted by key, O(log n) all operations
        TreeMap<String, Integer> sorted = new TreeMap<>(freq);
        System.out.println(sorted.firstKey()); // lexicographically smallest key

        // PriorityQueue — min-heap by default
        PriorityQueue<Integer> minHeap = new PriorityQueue<>();
        minHeap.offer(5); minHeap.offer(1); minHeap.offer(3);
        System.out.println(minHeap.poll()); // 1 — always the minimum
    }
}
```

---

## Try It Yourself

**Exercise:** Given the following operations, identify the best Java built-in data structure for each and explain why.

1. You need to check if a username already exists (1M users, instant check required)
2. You need to process tasks in the order they arrive (FIFO)
3. You need to always retrieve the highest-priority task next
4. You need to maintain a list of recently visited URLs and be able to undo (remove last)
5. You need to count the frequency of words in a document

<details>
<summary>Show answers</summary>

1. **`HashSet<String>`** — O(1) average `contains()`. A `List` would be O(n) per check.
2. **`ArrayDeque<Task>`** used as a Queue — O(1) `offer()` and `poll()`. (`LinkedList` also works but `ArrayDeque` is preferred.)
3. **`PriorityQueue<Task>`** with a custom `Comparator` — O(log n) insert, O(1) peek, O(log n) remove.
4. **`ArrayDeque<String>`** used as a Stack — O(1) `push()` and `pop()`. (`Stack` class is legacy; use `ArrayDeque`.)
5. **`HashMap<String, Integer>`** — O(1) average per word. `map.merge(word, 1, Integer::sum)` increments counts efficiently.

</details>

---

## Capstone Connection

In AlgoForge, you will implement several of these data structures from scratch to understand what *actually happens* inside `ArrayList`, `HashMap`, and `PriorityQueue`. Starting with Module 02, every data structure you use in an interview will have a matching hand-rolled implementation in `src/main/java/com/algoforge/datastructures/`.
