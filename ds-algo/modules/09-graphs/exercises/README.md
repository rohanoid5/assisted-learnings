# Module 09 — Graphs: Exercises

## Overview

These exercises progress from foundational graph traversal to advanced shortest-path and connectivity problems. Each is a real LC problem — solve within the time limit before checking the solution.

| # | Problem | Difficulty | Time Limit | LC # |
|---|---------|-----------|------------|------|
| 1 | Number of Islands | Medium | 15 min | 200 |
| 2 | Clone Graph | Medium | 20 min | 133 |
| 3 | Course Schedule | Medium | 20 min | 207 |
| 4 | Network Delay Time | Medium | 25 min | 743 |
| 5 | Word Ladder | Hard | 30 min | 127 |

---

## Exercise 1 — Number of Islands (LC #200)

**Difficulty:** Medium  
**Topic:** BFS/DFS on grid

**Goal:** Given a 2D binary grid of 1s (land) and 0s (water), return the number of islands.

**Example:**
```
Grid:
  1 1 1 1 0
  1 1 0 1 0
  1 1 0 0 0
  0 0 0 0 0

Output: 1
```

**Steps:**
1. Iterate over all cells. When you find an unvisited `'1'`, start BFS/DFS.
2. "Sink" all connected land cells during traversal (mark as `'0'` or use visited array).
3. Count each BFS/DFS launch as one island.

<details>
<summary>Solution</summary>

```java
public int numIslands(char[][] grid) {
    if (grid == null || grid.length == 0) return 0;
    int rows = grid.length, cols = grid[0].length;
    int count = 0;

    for (int r = 0; r < rows; r++) {
        for (int c = 0; c < cols; c++) {
            if (grid[r][c] == '1') {
                count++;
                dfs(grid, r, c, rows, cols);
            }
        }
    }
    return count;
}

private void dfs(char[][] grid, int r, int c, int rows, int cols) {
    if (r < 0 || r >= rows || c < 0 || c >= cols || grid[r][c] != '1') return;
    grid[r][c] = '0'; // mark visited
    dfs(grid, r-1, c, rows, cols);
    dfs(grid, r+1, c, rows, cols);
    dfs(grid, r, c-1, rows, cols);
    dfs(grid, r, c+1, rows, cols);
}
```

**Complexity:** O(rows × cols) time and space.

**Variation — BFS:**
```java
private void bfs(char[][] grid, int r, int c, int rows, int cols) {
    Queue<int[]> queue = new LinkedList<>();
    queue.offer(new int[]{r, c});
    grid[r][c] = '0';
    int[] dr = {-1,1,0,0}, dc = {0,0,-1,1};
    while (!queue.isEmpty()) {
        int[] cell = queue.poll();
        for (int d = 0; d < 4; d++) {
            int nr = cell[0]+dr[d], nc = cell[1]+dc[d];
            if (nr>=0&&nr<rows&&nc>=0&&nc<cols&&grid[nr][nc]=='1') {
                grid[nr][nc] = '0';
                queue.offer(new int[]{nr,nc});
            }
        }
    }
}
```

</details>

---

## Exercise 2 — Clone Graph (LC #133)

**Difficulty:** Medium  
**Topic:** BFS/DFS, HashMap

**Goal:** Given a reference to a node in a connected undirected graph, return a **deep copy** of the graph.

```java
class Node {
    public int val;
    public List<Node> neighbors;
}
```

**Example:**
```
Input: [[2,4],[1,3],[2,4],[1,3]]
(Node 1's neighbors are 2,4; Node 2's neighbors are 1,3; etc.)
```

**Steps:**
1. Use a HashMap to map original nodes to their clones.
2. BFS/DFS — when visiting a neighbor, check if clone exists; if not, create and add to queue.

<details>
<summary>Solution</summary>

```java
public Node cloneGraph(Node node) {
    if (node == null) return null;

    Map<Node, Node> cloned = new HashMap<>();
    Queue<Node> queue = new LinkedList<>();
    queue.offer(node);
    cloned.put(node, new Node(node.val));

    while (!queue.isEmpty()) {
        Node curr = queue.poll();
        for (Node neighbor : curr.neighbors) {
            if (!cloned.containsKey(neighbor)) {
                cloned.put(neighbor, new Node(neighbor.val));
                queue.offer(neighbor);
            }
            // Link the clone of curr to the clone of neighbor
            cloned.get(curr).neighbors.add(cloned.get(neighbor));
        }
    }
    return cloned.get(node);
}
```

**Key:** The HashMap serves as both a visited set (preventing revisiting) and a clone registry (so nodes are cloned exactly once).

**Complexity:** O(V + E) time and space.

</details>

---

## Exercise 3 — Course Schedule (LC #207)

**Difficulty:** Medium  
**Topic:** Topological sort, cycle detection

**Goal:** There are `numCourses` courses (0 to numCourses-1). `prerequisites[i] = [a, b]` means you must take course b before a. Return true if you can finish all courses.

**Example:**
```
Input: numCourses=2, prerequisites=[[1,0]]
Output: true  (take 0 then 1)

Input: numCourses=2, prerequisites=[[1,0],[0,1]]
Output: false  (cycle: 0 requires 1, 1 requires 0)
```

**Steps:**
1. Build directed graph from prerequisites.
2. Use either Kahn's algorithm or DFS cycle detection.

<details>
<summary>Solution</summary>

```java
// Kahn's Algorithm
public boolean canFinish(int numCourses, int[][] prerequisites) {
    List<List<Integer>> adj = new ArrayList<>();
    int[] indegree = new int[numCourses];
    for (int i = 0; i < numCourses; i++) adj.add(new ArrayList<>());

    for (int[] pre : prerequisites) {
        adj.get(pre[1]).add(pre[0]); // pre[1] → pre[0] (take pre[1] first)
        indegree[pre[0]]++;
    }

    Queue<Integer> queue = new LinkedList<>();
    for (int i = 0; i < numCourses; i++)
        if (indegree[i] == 0) queue.offer(i);

    int completed = 0;
    while (!queue.isEmpty()) {
        int course = queue.poll();
        completed++;
        for (int next : adj.get(course))
            if (--indegree[next] == 0) queue.offer(next);
    }
    return completed == numCourses;
}
```

**Why it works:** If a cycle exists, nodes in the cycle will have indegree that never reaches 0 → they're never added to the queue → `completed < numCourses`.

**Complexity:** O(V + E) time and space.

</details>

---

## Exercise 4 — Network Delay Time (LC #743)

**Difficulty:** Medium  
**Topic:** Dijkstra's algorithm

**Goal:** `n` network nodes, `times[i] = [ui, vi, wi]` means there's a directed edge from ui to vi with travel time wi. Send a signal from node k. Return the time until all nodes receive the signal, or -1 if impossible.

**Example:**
```
times = [[2,1,1],[2,3,1],[3,4,1]], n=4, k=2
Output: 2

Node 2 sends to 1 (time 1) and 3 (time 1)
Node 3 sends to 4 (time 1+1=2)
Max time = 2
```

**Steps:**
1. Dijkstra from source k.
2. Return max of all dist[].

<details>
<summary>Solution</summary>

```java
public int networkDelayTime(int[][] times, int n, int k) {
    List<List<int[]>> adj = new ArrayList<>();
    for (int i = 0; i <= n; i++) adj.add(new ArrayList<>());
    for (int[] t : times) adj.get(t[0]).add(new int[]{t[1], t[2]});

    int[] dist = new int[n + 1];
    Arrays.fill(dist, Integer.MAX_VALUE);
    dist[k] = 0;

    PriorityQueue<int[]> pq = new PriorityQueue<>((a, b) -> a[0] - b[0]);
    pq.offer(new int[]{0, k});

    while (!pq.isEmpty()) {
        int[] curr = pq.poll();
        int d = curr[0], node = curr[1];
        if (d > dist[node]) continue;
        for (int[] edge : adj.get(node)) {
            int newDist = dist[node] + edge[1];
            if (newDist < dist[edge[0]]) {
                dist[edge[0]] = newDist;
                pq.offer(new int[]{newDist, edge[0]});
            }
        }
    }

    int maxDist = 0;
    for (int i = 1; i <= n; i++) {
        if (dist[i] == Integer.MAX_VALUE) return -1;
        maxDist = Math.max(maxDist, dist[i]);
    }
    return maxDist;
}
```

**Complexity:** O((V+E) log V)

</details>

---

## Exercise 5 — Word Ladder (LC #127)

**Difficulty:** Hard  
**Topic:** BFS, string transformation

**Goal:** Given `beginWord`, `endWord`, and a `wordList`, return the minimum number of transformations from beginWord to endWord, changing one letter at a time, with each intermediate word in wordList.

**Example:**
```
beginWord = "hit"
endWord   = "cog"
wordList  = ["hot","dot","dog","lot","log","cog"]

hit → hot → dot → dog → cog  = 5 transformations
Output: 5
```

**Steps:**
1. BFS from beginWord — each level represents one transformation.
2. For each word, try changing each character to a-z and check if it's in the word set.
3. Stop when you reach endWord.

<details>
<summary>Solution</summary>

```java
public int ladderLength(String beginWord, String endWord, List<String> wordList) {
    Set<String> wordSet = new HashSet<>(wordList);
    if (!wordSet.contains(endWord)) return 0;

    Queue<String> queue = new LinkedList<>();
    queue.offer(beginWord);
    wordSet.remove(beginWord); // treat as visited

    int steps = 1;

    while (!queue.isEmpty()) {
        int size = queue.size();
        steps++;
        for (int i = 0; i < size; i++) {
            String word = queue.poll();
            char[] chars = word.toCharArray();

            for (int j = 0; j < chars.length; j++) {
                char original = chars[j];
                for (char c = 'a'; c <= 'z'; c++) {
                    if (c == original) continue;
                    chars[j] = c;
                    String newWord = new String(chars);
                    if (newWord.equals(endWord)) return steps;
                    if (wordSet.contains(newWord)) {
                        wordSet.remove(newWord); // mark visited
                        queue.offer(newWord);
                    }
                }
                chars[j] = original; // restore
            }
        }
    }
    return 0; // endWord not reachable
}
```

**Key:** Remove words from the set when visited (instead of separate visited set) — more memory efficient. BFS guarantees minimum steps.

**Complexity:** O(M² × N) where M = word length, N = number of words. Each word can generate M×26 neighbors, string creation is O(M).

</details>

---

## Bonus Challenge

**Problem:** Given a 2D grid with 0 (empty), 1 (fresh orange), 2 (rotten orange): each minute a rotten orange infects adjacent fresh ones. Return minutes until no fresh oranges remain, or -1 if impossible. (LC #994)

**Hint:** Multi-source BFS from all rotten oranges simultaneously.

<details>
<summary>Solution</summary>

```java
public int orangesRotting(int[][] grid) {
    int rows = grid.length, cols = grid[0].length;
    Queue<int[]> queue = new LinkedList<>();
    int fresh = 0;

    for (int r = 0; r < rows; r++)
        for (int c = 0; c < cols; c++) {
            if (grid[r][c] == 2) queue.offer(new int[]{r, c});
            if (grid[r][c] == 1) fresh++;
        }

    if (fresh == 0) return 0;
    int[] dr = {-1,1,0,0}, dc = {0,0,-1,1};
    int minutes = 0;

    while (!queue.isEmpty() && fresh > 0) {
        minutes++;
        int size = queue.size();
        for (int i = 0; i < size; i++) {
            int[] cell = queue.poll();
            for (int d = 0; d < 4; d++) {
                int nr = cell[0]+dr[d], nc = cell[1]+dc[d];
                if (nr>=0&&nr<rows&&nc>=0&&nc<cols&&grid[nr][nc]==1) {
                    grid[nr][nc] = 2;
                    fresh--;
                    queue.offer(new int[]{nr, nc});
                }
            }
        }
    }
    return fresh == 0 ? minutes : -1;
}
```

</details>
