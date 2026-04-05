# Module 10 — Advanced Data Structures: Exercises

## Overview

These exercises apply the advanced data structures from this module. They represent the "hard" tier of LC problems that become tractable once you know the right data structure.

| # | Problem | Difficulty | Key DS | LC # |
|---|---------|-----------|--------|------|
| 1 | Word Search II | Hard | Trie + DFS | 212 |
| 2 | Range Sum Query — Mutable | Medium | Segment Tree / BIT | 307 |
| 3 | Accounts Merge | Medium | Union-Find | 721 |
| 4 | Count of Smaller Numbers After Self | Hard | BIT + coord compress | 315 |
| 5 | Longest Duplicate Substring | Hard | Suffix Array / Rolling Hash | 1044 |

---

## Exercise 1 — Word Search II (LC #212)

**Difficulty:** Hard  
**Topic:** Trie + DFS backtracking

**Goal:** Given an m×n board of letters and a list of words, find all words in the board (letters must be adjacent, no cell reused).

**Example:**
```
Board:
  o a a n
  e t a e
  i h k r
  i f l v

Words: ["oath","pea","eat","rain"]
Output: ["eat","oath"]
```

**Steps:**
1. Build a Trie from all words.
2. DFS from each cell; use Trie to prune paths with no valid prefix.
3. Mark cells visited during DFS, restore after backtrack.

<details>
<summary>Solution</summary>

```java
class TrieNode {
    TrieNode[] ch = new TrieNode[26];
    String word = null; // stores word at this endpoint
}

public List<String> findWords(char[][] board, String[] words) {
    TrieNode root = new TrieNode();
    for (String w : words) {
        TrieNode curr = root;
        for (char c : w.toCharArray()) {
            int i = c - 'a';
            if (curr.ch[i] == null) curr.ch[i] = new TrieNode();
            curr = curr.ch[i];
        }
        curr.word = w; // store word at end node
    }

    List<String> result = new ArrayList<>();
    int rows = board.length, cols = board[0].length;

    for (int r = 0; r < rows; r++)
        for (int c = 0; c < cols; c++)
            dfs(board, r, c, root, result, rows, cols);

    return result;
}

private void dfs(char[][] board, int r, int c, TrieNode node,
                  List<String> result, int rows, int cols) {
    if (r<0||r>=rows||c<0||c>=cols||board[r][c]=='#') return;
    char ch = board[r][c];
    TrieNode next = node.ch[ch - 'a'];
    if (next == null) return; // prune: no word with this prefix

    if (next.word != null) {
        result.add(next.word);
        next.word = null; // prevent duplicates
    }

    board[r][c] = '#'; // mark visited
    dfs(board, r-1, c, next, result, rows, cols);
    dfs(board, r+1, c, next, result, rows, cols);
    dfs(board, r, c-1, next, result, rows, cols);
    dfs(board, r, c+1, next, result, rows, cols);
    board[r][c] = ch;  // restore
}
```

**Complexity:** O(M × 4 × 3^(L-1)) where M = board cells, L = word length. Trie pruning drastically reduces exploration.

</details>

---

## Exercise 2 — Range Sum Query – Mutable (LC #307)

**Difficulty:** Medium  
**Topic:** Segment Tree or Fenwick Tree

**Goal:** Given an integer array nums, handle `update(index, val)` and `sumRange(left, right)` queries efficiently.

**Example:**
```
nums = [1, 3, 5]
sumRange(0, 2) → 9
update(1, 2)   → nums = [1, 2, 5]
sumRange(0, 2) → 8
```

**Try implementing with both Segment Tree and Fenwick Tree.** Compare code complexity.

<details>
<summary>Segment Tree Solution</summary>

```java
class NumArray {
    private int[] tree;
    private int n;

    public NumArray(int[] nums) {
        n = nums.length;
        tree = new int[4 * n];
        build(nums, 0, 0, n-1);
    }

    private void build(int[] nums, int node, int l, int r) {
        if (l == r) { tree[node] = nums[l]; return; }
        int mid = (l+r)/2;
        build(nums, 2*node+1, l, mid);
        build(nums, 2*node+2, mid+1, r);
        tree[node] = tree[2*node+1] + tree[2*node+2];
    }

    public void update(int index, int val) {
        update(0, 0, n-1, index, val);
    }

    private void update(int node, int l, int r, int i, int val) {
        if (l == r) { tree[node] = val; return; }
        int mid = (l+r)/2;
        if (i <= mid) update(2*node+1, l, mid, i, val);
        else          update(2*node+2, mid+1, r, i, val);
        tree[node] = tree[2*node+1] + tree[2*node+2];
    }

    public int sumRange(int left, int right) {
        return query(0, 0, n-1, left, right);
    }

    private int query(int node, int nodeL, int nodeR, int l, int r) {
        if (l > nodeR || r < nodeL) return 0;
        if (l <= nodeL && nodeR <= r) return tree[node];
        int mid = (nodeL+nodeR)/2;
        return query(2*node+1, nodeL, mid, l, r)
             + query(2*node+2, mid+1, nodeR, l, r);
    }
}
```

</details>

<details>
<summary>Fenwick Tree Solution</summary>

```java
class NumArray {
    private int[] bit, nums;
    private int n;

    public NumArray(int[] nums) {
        this.n = nums.length;
        this.nums = nums.clone();
        this.bit = new int[n + 1];
        for (int i = 0; i < n; i++) update(i, nums[i]);
    }

    public void update(int index, int val) {
        int delta = val - nums[index];
        nums[index] = val;
        for (int i = index + 1; i <= n; i += i & (-i)) bit[i] += delta;
    }

    public int sumRange(int left, int right) {
        return prefix(right + 1) - prefix(left);
    }

    private int prefix(int i) {
        int sum = 0;
        for (; i > 0; i -= i & (-i)) sum += bit[i];
        return sum;
    }
}
```

**Fenwick is ~2x simpler code and same performance.**

</details>

---

## Exercise 3 — Accounts Merge (LC #721)

**Difficulty:** Medium  
**Topic:** Union-Find or DFS

**Goal:** Given a list of accounts where each account is `[name, email1, email2, ...]`, merge accounts that share an email. Return merged accounts sorted.

**Example:**
```
Input: 
[["John","johnsmith@mail.com","john_newyork@mail.com"],
 ["John","johnsmith@mail.com","john00@mail.com"],
 ["Mary","mary@mail.com"],
 ["John","johnnybravo@mail.com"]]

Output:
[["John","john00@mail.com","john_newyork@mail.com","johnsmith@mail.com"],
 ["John","johnnybravo@mail.com"],
 ["Mary","mary@mail.com"]]
```

<details>
<summary>Union-Find Solution</summary>

```java
public List<List<String>> accountsMerge(List<List<String>> accounts) {
    Map<String, String> emailToName = new HashMap<>();
    Map<String, String> parent = new HashMap<>();

    // Initialize: each email is its own parent
    for (List<String> acc : accounts) {
        String name = acc.get(0);
        for (int i = 1; i < acc.size(); i++) {
            emailToName.put(acc.get(i), name);
            parent.put(acc.get(i), acc.get(i));
        }
    }

    // Union emails within same account
    for (List<String> acc : accounts) {
        String first = acc.get(1);
        for (int i = 2; i < acc.size(); i++) union(parent, first, acc.get(i));
    }

    // Group by root
    Map<String, List<String>> groups = new HashMap<>();
    for (String email : parent.keySet()) {
        String root = find(parent, email);
        groups.computeIfAbsent(root, k -> new ArrayList<>()).add(email);
    }

    List<List<String>> result = new ArrayList<>();
    for (Map.Entry<String, List<String>> e : groups.entrySet()) {
        List<String> emails = e.getValue();
        Collections.sort(emails);
        emails.add(0, emailToName.get(e.getKey())); // prepend name
        result.add(emails);
    }
    return result;
}

private String find(Map<String, String> parent, String x) {
    if (!parent.get(x).equals(x)) parent.put(x, find(parent, parent.get(x)));
    return parent.get(x);
}

private void union(Map<String, String> parent, String x, String y) {
    parent.put(find(parent, x), find(parent, y));
}
```

</details>

---

## Exercise 4 — Count of Smaller Numbers After Self (LC #315)

**Difficulty:** Hard  
**Topic:** Fenwick Tree + coordinate compression

**Goal:** For each element `nums[i]`, count how many elements to its right are smaller than it.

**Example:**
```
Input:  [5, 2, 6, 1]
Output: [2, 1, 1, 0]
  5: two elements smaller to its right (2, 1)
  2: one element smaller (1)
  6: one element smaller (1)
  1: zero
```

<details>
<summary>Solution</summary>

```java
public List<Integer> countSmaller(int[] nums) {
    int n = nums.length;

    // Coordinate compression: map values to ranks 1..n
    int[] sorted = nums.clone();
    Arrays.sort(sorted);
    Map<Integer, Integer> rank = new HashMap<>();
    int r = 1;
    for (int v : sorted) rank.putIfAbsent(v, r++);

    int[] bit = new int[n + 1];
    int[] result = new int[n];

    for (int i = n - 1; i >= 0; i--) {
        int rnk = rank.get(nums[i]);
        // Count elements in BIT with rank < rnk
        result[i] = queryBIT(bit, rnk - 1);
        updateBIT(bit, rnk, 1, n);
    }

    List<Integer> ans = new ArrayList<>();
    for (int v : result) ans.add(v);
    return ans;
}

private void updateBIT(int[] bit, int i, int delta, int n) {
    for (; i <= n; i += i & (-i)) bit[i] += delta;
}

private int queryBIT(int[] bit, int i) {
    int sum = 0;
    for (; i > 0; i -= i & (-i)) sum += bit[i];
    return sum;
}
```

**Key:** Process right to left. For each element, query how many smaller elements are already in the BIT (= to its right in original array), then add it.

</details>

---

## Exercise 5 — Bonus: LRU Cache (LC #146)

**Difficulty:** Medium  
**Topic:** HashMap + Doubly Linked List

**Goal:** Design a data structure that follows LRU (Least Recently Used) eviction policy with O(1) `get` and `put`.

<details>
<summary>Solution</summary>

```java
class LRUCache {
    private final int capacity;
    private final Map<Integer, Node> map = new HashMap<>();
    private final Node head = new Node(0, 0), tail = new Node(0, 0);

    static class Node {
        int key, val;
        Node prev, next;
        Node(int k, int v) { key=k; val=v; }
    }

    public LRUCache(int capacity) {
        this.capacity = capacity;
        head.next = tail;
        tail.prev = head;
    }

    public int get(int key) {
        if (!map.containsKey(key)) return -1;
        Node node = map.get(key);
        moveToFront(node);
        return node.val;
    }

    public void put(int key, int value) {
        if (map.containsKey(key)) {
            Node node = map.get(key);
            node.val = value;
            moveToFront(node);
        } else {
            if (map.size() == capacity) {
                Node lru = tail.prev;
                remove(lru);
                map.remove(lru.key);
            }
            Node newNode = new Node(key, value);
            addToFront(newNode);
            map.put(key, newNode);
        }
    }

    private void remove(Node n) {
        n.prev.next = n.next;
        n.next.prev = n.prev;
    }

    private void addToFront(Node n) {
        n.next = head.next;
        n.prev = head;
        head.next.prev = n;
        head.next = n;
    }

    private void moveToFront(Node n) { remove(n); addToFront(n); }
}
```

</details>
