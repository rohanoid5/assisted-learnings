# 4.2 — Queue Fundamentals

## Concept

A **queue** is a FIFO (First-In First-Out) collection. The first element enqueued is the first dequeued. Think: a checkout line, a printer queue, or BFS exploration layer by layer. A **deque** (double-ended queue) supports O(1) insertion and removal at both ends, making it useful as both a stack and a queue.

---

## Deep Dive

### Circular Buffer Implementation

```
Array-backed queue with head and tail pointers:

  [ _ | A | B | C | _ ]
         ↑           ↑
        head        tail

enqueue D → tail moves right:
  [ _ | A | B | C | D ]
         ↑               ↑
        head             tail (wraps mod capacity)

dequeue → head moves right:
  [ _ | _ | B | C | D ]
                 ↑       ↑
                head     tail

When tail wraps around past head → resize.
Java's ArrayDeque uses this internally — amortized O(1) for all ops.
```

### Java Queue Interface

```java
// Prefer ArrayDeque over LinkedList for queue (better cache locality)
Queue<Integer> queue = new ArrayDeque<>();

queue.offer(1);    // enqueue  (returns false if capacity exceeded)
queue.poll();      // dequeue  (returns null if empty)
queue.peek();      // front element without removing

// For deque (both-ends access):
Deque<Integer> dq = new ArrayDeque<>();
dq.offerFirst(x); dq.offerLast(x);
dq.pollFirst();   dq.pollLast();
dq.peekFirst();   dq.peekLast();
```

### BFS Template Using a Queue

```
BFS explores nodes level by level:

level 0:        1
level 1:      2   3
level 2:    4  5  6  7

Queue starts: [1]
Process 1 → enqueue children → [2, 3]
Process 2 → enqueue children → [3, 4, 5]
Process 3 → enqueue children → [4, 5, 6, 7]
...
```

---

## Code Examples

### BFS Level-Order Traversal

```java
// Generic BFS template — works for trees and graphs alike
public List<List<Integer>> levelOrder(TreeNode root) {
    List<List<Integer>> result = new ArrayList<>();
    if (root == null) return result;

    Queue<TreeNode> queue = new ArrayDeque<>();
    queue.offer(root);

    while (!queue.isEmpty()) {
        int size = queue.size();          // snapshot of current level size
        List<Integer> level = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            TreeNode node = queue.poll();
            level.add(node.val);
            if (node.left  != null) queue.offer(node.left);
            if (node.right != null) queue.offer(node.right);
        }
        result.add(level);
    }
    return result;
}
```

### Queue with Two Stacks

```java
// Implement a queue using only two stacks.
// "Lazy transfer" — only move elements from s1 to s2 when s2 is empty.
class MyQueue {
    private Deque<Integer> inbox  = new ArrayDeque<>();  // for push
    private Deque<Integer> outbox = new ArrayDeque<>();  // for pop/peek

    public void push(int x) { inbox.push(x); }

    public int pop()  { transfer(); return outbox.pop(); }
    public int peek() { transfer(); return outbox.peek(); }
    public boolean empty() { return inbox.isEmpty() && outbox.isEmpty(); }

    private void transfer() {
        if (outbox.isEmpty())
            while (!inbox.isEmpty())
                outbox.push(inbox.pop());
    }
    // Amortized O(1) for each operation — each element crosses once.
}
```

---

## Try It Yourself

**Exercise:** Given a stream of integers, implement a class that returns the maximum value in a sliding window of size `k` in O(n) time.

Input: `nums = [1,3,-1,-3,5,3,6,7]`, k=3 → `[3,3,5,5,6,7]`

Hint: use a monotonic decreasing deque that stores indices. Remove indices outside the window from the front; remove smaller values from the back.

<details>
<summary>Show solution</summary>

```java
public int[] maxSlidingWindow(int[] nums, int k) {
    int n = nums.length;
    int[] result = new int[n - k + 1];
    Deque<Integer> dq = new ArrayDeque<>();  // stores indices, decreasing by value

    for (int i = 0; i < n; i++) {
        // Remove indices outside the window
        while (!dq.isEmpty() && dq.peekFirst() < i - k + 1)
            dq.pollFirst();
        // Remove smaller values at the back (they can never be the max)
        while (!dq.isEmpty() && nums[dq.peekLast()] < nums[i])
            dq.pollLast();
        dq.offerLast(i);
        // Window is full — record the maximum (front of deque)
        if (i >= k - 1)
            result[i - k + 1] = nums[dq.peekFirst()];
    }
    return result;
    // Time: O(n), Space: O(k)
}
```

</details>

---

## Capstone Connection

BFS is the foundation of Level-Order Traversal (Module 08) and graph shortest-path on unweighted graphs (Module 09). The monotonic deque from the "sliding window maximum" problem is an appetizer for the full monotonic stack pattern in topic 03.
