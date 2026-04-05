# Module 04: Stacks & Queues — Exercises

---

## Exercise 1: Valid Parentheses (LC #20)

**Goal:** Determine if a string of `()[]{}` characters is valid (properly closed and nested).

```
"()"      → true
"()[]{}"  → true
"(]"      → false
"([)]"    → false
"{[]}"    → true
```

1. Use a stack. Push opening brackets; when you see a closing bracket, check it matches the stack's top.
2. Handle edge cases: empty string, leading close bracket, trailing open bracket.
3. Extend to validate an HTML-like language where tags must be properly nested (e.g., `<div><p></p></div>`).

<details>
<summary>Solution</summary>

```java
public boolean isValid(String s) {
    Deque<Character> stack = new ArrayDeque<>();
    for (char c : s.toCharArray()) {
        if (c == '(' || c == '[' || c == '{') {
            stack.push(c);
        } else {
            if (stack.isEmpty()) return false;
            char top = stack.pop();
            if (c == ')' && top != '(') return false;
            if (c == ']' && top != '[') return false;
            if (c == '}' && top != '{') return false;
        }
    }
    return stack.isEmpty();
}
```

</details>

---

## Exercise 2: Min Stack (LC #155)

**Goal:** Design a stack that supports `push`, `pop`, `top`, and `getMin` in O(1) time.

```
MinStack s = new MinStack();
s.push(-2); s.push(0); s.push(-3);
s.getMin(); // -3
s.pop();
s.top();    // 0
s.getMin(); // -2
```

1. Implement using two stacks (main stack + min-tracker stack).
2. Be careful: the min-tracker stack must store a new minimum only when the pushed value is `<=` the current min (to handle duplicate minimums correctly on pop).
3. Write a test with duplicate minimums to verify correctness.

<details>
<summary>Solution</summary>

```java
class MinStack {
    private Deque<Integer> stack    = new ArrayDeque<>();
    private Deque<Integer> minStack = new ArrayDeque<>();

    public void push(int val) {
        stack.push(val);
        // Push to minStack if it's empty OR if new val <= current min
        if (minStack.isEmpty() || val <= minStack.peek())
            minStack.push(val);
    }

    public void pop() {
        int top = stack.pop();
        if (top == minStack.peek()) minStack.pop();
    }

    public int top()    { return stack.peek(); }
    public int getMin() { return minStack.peek(); }
}
```

**Test with duplicates:**
```java
@Test
void testMinStackDuplicates() {
    MinStack s = new MinStack();
    s.push(1); s.push(1);
    assertEquals(1, s.getMin());
    s.pop();
    assertEquals(1, s.getMin());  // must still be 1 (duplicate stays)
    s.pop();
    // stack is now empty
}
```

</details>

---

## Exercise 3: Daily Temperatures (LC #739)

**Goal:** Given a list of daily temperatures, for each day find the number of days until a warmer temperature. Return 0 if no warmer day exists.

```
Input:  [73,74,75,71,69,72,76,73]
Output: [1, 1, 4, 2, 1, 1, 0, 0]
```

1. Solve naively O(n²) first to confirm understanding.
2. Refactor to O(n) using a monotonic decreasing stack of indices.
3. Explain why each element is pushed and popped at most once → O(n) total.

<details>
<summary>Solution</summary>

```java
public int[] dailyTemperatures(int[] temperatures) {
    int n = temperatures.length;
    int[] answer = new int[n];
    Deque<Integer> stack = new ArrayDeque<>();  // indices

    for (int i = 0; i < n; i++) {
        // Current temperature is warmer than what's on the stack
        while (!stack.isEmpty() && temperatures[i] > temperatures[stack.peek()]) {
            int idx = stack.pop();
            answer[idx] = i - idx;
        }
        stack.push(i);
    }
    // Remaining indices in stack: no warmer day, answer stays 0
    return answer;
    // Time: O(n) — each index pushed once, popped once
    // Space: O(n) for stack
}
```

</details>

---

## Exercise 4: Sliding Window Maximum (LC #239)

**Goal:** Given an array and window size `k`, return the maximum value in each sliding window.

```
nums = [1,3,-1,-3,5,3,6,7], k = 3
Output: [3, 3, 5, 5, 6, 7]
```

1. Implement in O(n) using a monotonic decreasing deque (stores indices).
2. Trace through the example manually, noting which indices are added to and removed from the deque at each step.
3. Write a brute-force O(nk) solution to verify your O(n) version.

<details>
<summary>Solution</summary>

```java
public int[] maxSlidingWindow(int[] nums, int k) {
    int n = nums.length;
    int[] result = new int[n - k + 1];
    Deque<Integer> dq = new ArrayDeque<>();  // decreasing by value, stores indices

    for (int i = 0; i < n; i++) {
        // Remove indices out of window bounds (from front)
        while (!dq.isEmpty() && dq.peekFirst() <= i - k)
            dq.pollFirst();
        // Remove smaller elements (will never be max while current exists)
        while (!dq.isEmpty() && nums[dq.peekLast()] < nums[i])
            dq.pollLast();
        dq.offerLast(i);
        if (i >= k - 1)
            result[i - k + 1] = nums[dq.peekFirst()];
    }
    return result;
}
```

</details>

---

## Exercise 5: Task Scheduler (LC #621)

**Goal:** Given a list of CPU tasks (characters A-Z) and a cooldown `n`, find the minimum number of CPU intervals to execute all tasks (CPU can be idle to satisfy cooldown).

```
tasks = ["A","A","A","B","B","B"], n = 2
Output: 8    (A→B→idle→A→B→idle→A→B)
```

1. Find the task with the highest frequency.
2. Formula: `max(tasks.length, (maxFreq - 1) * (n + 1) + countOfMaxFreqTasks)`.
3. Derive the formula by visualizing the "slots" layout, then verify with examples.
4. Also implement the heap-based greedy simulation for deeper understanding.

<details>
<summary>Solution</summary>

```java
// Formula approach — O(n)
public int leastInterval(char[] tasks, int n) {
    int[] freq = new int[26];
    for (char t : tasks) freq[t - 'A']++;
    int maxFreq = 0;
    for (int f : freq) maxFreq = Math.max(maxFreq, f);
    int maxCount = 0;
    for (int f : freq) if (f == maxFreq) maxCount++;

    // Visualization:
    //   Frame size: (n+1)  →  one execution slot + n cooldown slots
    //   Frames:     (maxFreq - 1)  →  all but the last batch
    //   Last row:   maxCount tasks all at the same max frequency
    //
    //   A B _ | A B _ | A B     (maxFreq=3, n=2, maxCount=2)
    //   Slots = (3-1)*(2+1) + 2 = 8

    int minTime = (maxFreq - 1) * (n + 1) + maxCount;
    return Math.max(minTime, tasks.length);
    // tasks.length: if tasks are dense enough, no idle is needed
}

// Heap simulation — O(t * n) where t = total tasks
public int leastIntervalHeap(char[] tasks, int n) {
    int[] freq = new int[26];
    for (char t : tasks) freq[t - 'A']++;

    PriorityQueue<Integer> pq = new PriorityQueue<>(Comparator.reverseOrder());
    for (int f : freq) if (f > 0) pq.offer(f);

    int time = 0;
    while (!pq.isEmpty()) {
        List<Integer> temp = new ArrayList<>();
        for (int i = 0; i <= n; i++) {
            if (!pq.isEmpty()) temp.add(pq.poll() - 1);
            time++;
            if (pq.isEmpty() && temp.stream().allMatch(x -> x == 0)) break;
        }
        for (int f : temp) if (f > 0) pq.offer(f);
    }
    return time;
}
```

</details>

---

*Completing all five exercises puts you at LC Medium-Hard confidence for the stack/queue family.*
