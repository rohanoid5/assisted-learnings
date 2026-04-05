# 4.3 — Monotonic Stack

## Concept

A **monotonic stack** maintains elements in strictly increasing or strictly decreasing order. When a new element violates the order, we pop elements from the stack until the order is restored (processing them on the way out). This lets us answer "what is the next greater/smaller element on the right?" for every index in O(n) — instead of O(n²) brute force.

---

## Deep Dive

### Invariant Visualization

```
Monotonic decreasing stack — "Next Greater Element" pattern:

Array: [2, 1, 5, 6, 4, 3]

i=0: push 2.    stack: [2]
i=1: push 1.    stack: [2, 1]          (1 ≤ 2 — maintains decreasing order)
i=2: 5 > 1 →  pop 1, answer[1]=5.
              5 > 2 →  pop 2, answer[0]=5.
              push 5.  stack: [5]
i=3: 6 > 5 →  pop 5, answer[2]=6.
              push 6.  stack: [6]
i=4: push 4.    stack: [6, 4]
i=5: push 3.    stack: [6, 4, 3]
End: remaining elements have no next greater → answer = -1.

Result: [5, 5, 6, -1, -1, -1]

Key insight: when we pop index j because nums[i] > nums[j],
            we've found the NEXT GREATER element for j.
```

---

## Code Examples

### Next Greater Element I (LC #496)

```java
public int[] nextGreaterElement(int[] nums1, int[] nums2) {
    // Build NGE map for nums2
    Map<Integer, Integer> nge = new HashMap<>();
    Deque<Integer> stack = new ArrayDeque<>();

    for (int num : nums2) {
        while (!stack.isEmpty() && num > stack.peek())
            nge.put(stack.pop(), num);
        stack.push(num);
    }
    while (!stack.isEmpty()) nge.put(stack.pop(), -1);

    int[] result = new int[nums1.length];
    for (int i = 0; i < nums1.length; i++)
        result[i] = nge.get(nums1[i]);
    return result;
    // Time: O(m+n), Space: O(n)
}
```

### Largest Rectangle in Histogram (LC #84)

```java
// For each bar, find how far left and right it can extend as the shortest bar.
// Stack stores indices of bars in increasing height order.
// When we pop idx, the current bar (i) is its right boundary,
// and the new stack top is its left boundary.

public int largestRectangleArea(int[] heights) {
    int n = heights.length, maxArea = 0;
    Deque<Integer> stack = new ArrayDeque<>();

    for (int i = 0; i <= n; i++) {
        int h = (i == n) ? 0 : heights[i];   // sentinel 0 flushes the stack
        while (!stack.isEmpty() && h < heights[stack.peek()]) {
            int height = heights[stack.pop()];
            int width  = stack.isEmpty() ? i : i - stack.peek() - 1;
            maxArea = Math.max(maxArea, height * width);
        }
        stack.push(i);
    }
    return maxArea;
    // Time: O(n), Space: O(n)
}
```

### Trapping Rain Water (LC #42)

```java
// Stack variation: pop and calculate trapped water between walls.
// Popped bar is the "floor", current bar is the right wall,
// new stack top is the left wall.

public int trap(int[] height) {
    int water = 0;
    Deque<Integer> stack = new ArrayDeque<>();
    for (int i = 0; i < height.length; i++) {
        while (!stack.isEmpty() && height[i] > height[stack.peek()]) {
            int bottom = stack.pop();
            if (stack.isEmpty()) break;
            int left  = stack.peek();
            int h     = Math.min(height[left], height[i]) - height[bottom];
            int w     = i - left - 1;
            water += h * w;
        }
        stack.push(i);
    }
    return water;
    // Time: O(n), Space: O(n)
}
```

---

## Try It Yourself

**Exercise:** Given an integer array `nums` and an integer `k`, return the `k`th largest element in the array (1-indexed, k=1 means largest).

Can you do it in O(n) average time? What approach uses a monotonic/heap property?

<details>
<summary>Show solution</summary>

```java
// Using a min-heap of size k: keep the k largest elements seen so far.
// The heap's root (minimum) is the kth largest.
public int findKthLargest(int[] nums, int k) {
    PriorityQueue<Integer> minHeap = new PriorityQueue<>();
    for (int num : nums) {
        minHeap.offer(num);
        if (minHeap.size() > k) minHeap.poll();  // evict the smallest
    }
    return minHeap.peek();
    // Time: O(n log k), Space: O(k)
}

// For O(n) average: QuickSelect (partition-based)
public int findKthLargestQuickSelect(int[] nums, int k) {
    return quickSelect(nums, 0, nums.length - 1, nums.length - k);
}

private int quickSelect(int[] nums, int lo, int hi, int k) {
    int pivot = nums[hi], ptr = lo;
    for (int i = lo; i < hi; i++)
        if (nums[i] <= pivot) swap(nums, ptr++, i);
    swap(nums, ptr, hi);
    if (ptr == k)      return nums[ptr];
    else if (ptr < k)  return quickSelect(nums, ptr + 1, hi, k);
    else               return quickSelect(nums, lo, ptr - 1, k);
}

private void swap(int[] a, int i, int j) { int t = a[i]; a[i] = a[j]; a[j] = t; }
```

</details>

---

## Capstone Connection

The monotonic stack pattern is one of the most powerful O(n) tricks. Largest Rectangle in Histogram is also a subproblem of Maximal Rectangle (LC #85, 2D grid). Practice until you can reconstruct the solution from memory.
