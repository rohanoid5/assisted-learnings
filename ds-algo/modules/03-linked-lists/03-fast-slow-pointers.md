# 3.3 — Fast and Slow Pointers

## Concept

Two pointers moving at different speeds through a linked list. The slow pointer moves one step at a time; the fast pointer moves two. When fast reaches the end, slow is at the midpoint. If the list has a cycle, fast will eventually lap slow and they will meet. This is **Floyd's Cycle Detection Algorithm** — O(n) time, O(1) space.

---

## Deep Dive

### Why They Meet in a Cycle

```
Cycle detection intuition:

List with cycle:  1 → 2 → 3 → 4 → 5
                              ↑       |
                              └───────┘

slow: 1  2  3  4  5  3  4  ...
fast: 1  3  5  4  3  5  4  ...

Both enter the cycle. Inside the cycle of length L:
slow moves 1/tick, fast moves 2/tick, so they close at 1 step/tick.
They MUST meet within L steps of both entering the cycle.
→ O(n) total
```

### Three Applications

```
1. Detect cycle:    Do slow and fast ever meet?
2. Find cycle start: Where does the cycle begin?
3. Find middle:     Where is slow when fast reaches the end?
```

---

## Code Examples

### Application 1: Detect Cycle

```java
public boolean hasCycle(ListNode head) {
    ListNode slow = head, fast = head;
    while (fast != null && fast.next != null) {
        slow = slow.next;
        fast = fast.next.next;
        if (slow == fast) return true;   // they've met — cycle exists
    }
    return false;
    // Time: O(n), Space: O(1)
}
```

### Application 2: Find Cycle Start Node

```java
// After they meet, reset slow to head. Move both one step at a time.
// They will meet again at the cycle entry node.
// Mathematical proof: distance(head→entry) = distance(meeting→entry)

public ListNode detectCycle(ListNode head) {
    ListNode slow = head, fast = head;
    while (fast != null && fast.next != null) {
        slow = slow.next;
        fast = fast.next.next;
        if (slow == fast) {
            slow = head;                 // reset one pointer to head
            while (slow != fast) {
                slow = slow.next;
                fast = fast.next;        // both move one step now
            }
            return slow;                 // cycle entry node
        }
    }
    return null;
    // Time: O(n), Space: O(1)
}
```

### Application 3: Happy Number

```java
// A "happy number": repeatedly sum squares of digits.
// Number 1 means happy. Any other cycle means not happy.
// Treat digit-sum operation as "next node" — apply Floyd's to this sequence.

public boolean isHappy(int n) {
    int slow = n, fast = digitSumSquares(n);
    while (fast != 1 && slow != fast) {
        slow = digitSumSquares(slow);
        fast = digitSumSquares(digitSumSquares(fast));
    }
    return fast == 1;
}

private int digitSumSquares(int n) {
    int sum = 0;
    while (n > 0) {
        int d = n % 10;
        sum += d * d;
        n /= 10;
    }
    return sum;
}
```

---

## Try It Yourself

**Exercise:** Find the duplicate number in `[1..n+1]` — an array of n+1 integers where each is in `[1..n]`, one value appears twice. Use O(1) space — no HashSet, no sorting.

Hint: treat the array as a linked list where `arr[i]` is the "next pointer" from index i.

<details>
<summary>Show solution</summary>

```java
public int findDuplicate(int[] nums) {
    // Phase 1: detect cycle (like Floyd's)
    int slow = nums[0], fast = nums[0];
    do {
        slow = nums[slow];
        fast = nums[nums[fast]];
    } while (slow != fast);

    // Phase 2: find cycle entry = duplicate value
    slow = nums[0];
    while (slow != fast) {
        slow = nums[slow];
        fast = nums[fast];
    }
    return slow;
    // Time: O(n), Space: O(1)
    // LC #287 — beautiful application of Floyd's to a non-list problem
}
```

The array `[1,3,4,2,2]` creates the implicit graph: 0→1→3→2→4→2 (cycle). The duplicate is the entry point of the cycle.

</details>

---

## Capstone Connection

Fast/slow pointers are used in AlgoForge's linked list problems folder and also appear in the graph problems (cycle detection). The pattern is one of the hardest to invent but easiest to recognize once you've seen it — practice until it's automatic.
