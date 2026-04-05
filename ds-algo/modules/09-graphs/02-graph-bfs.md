# 9.2 — Graph BFS

## BFS on Graphs

Graph BFS is the same as tree BFS — use a queue, process level by level — **except** you must track visited nodes to avoid revisiting (cycles exist in graphs).

```
Graph:  0 -- 1 -- 3
        |    |
        2 -- 4

BFS from 0:
Level 0: [0]
Level 1: [1, 2]       (neighbors of 0)
Level 2: [3, 4]       (new neighbors of 1, 2)
```

---

## Standard Graph BFS Template

```java
public void bfs(List<List<Integer>> adj, int start, int n) {
    boolean[] visited = new boolean[n];
    Queue<Integer> queue = new LinkedList<>();

    queue.offer(start);
    visited[start] = true;

    while (!queue.isEmpty()) {
        int node = queue.poll();
        System.out.print(node + " ");

        for (int neighbor : adj.get(node)) {
            if (!visited[neighbor]) {
                visited[neighbor] = true;
                queue.offer(neighbor);
            }
        }
    }
}
```

**Key:** Mark `visited` when you **enqueue**, not when you **dequeue** — prevents adding the same node multiple times.

---

## Shortest Path in Unweighted Graph

BFS gives shortest path (minimum edge count) from source to all nodes:

```java
public int[] shortestPath(List<List<Integer>> adj, int src, int n) {
    int[] dist = new int[n];
    Arrays.fill(dist, -1);
    dist[src] = 0;

    Queue<Integer> queue = new LinkedList<>();
    queue.offer(src);

    while (!queue.isEmpty()) {
        int node = queue.poll();
        for (int neighbor : adj.get(node)) {
            if (dist[neighbor] == -1) {          // unvisited
                dist[neighbor] = dist[node] + 1; // one edge further
                queue.offer(neighbor);
            }
        }
    }
    return dist; // dist[i] = shortest distance from src to i, -1 if unreachable
}
```

---

## Number of Islands (LC #200)

Classic grid BFS: treat each cell as a node, edges connect adjacent land cells.

```
Grid:
1 1 1 1 0
1 1 0 1 0
1 1 0 0 0
0 0 0 0 0

Islands: 1  (one connected component of '1's)
```

```java
public int numIslands(char[][] grid) {
    if (grid == null || grid.length == 0) return 0;
    int rows = grid.length, cols = grid[0].length;
    int islands = 0;

    for (int r = 0; r < rows; r++) {
        for (int c = 0; c < cols; c++) {
            if (grid[r][c] == '1') {
                islands++;
                bfsIsland(grid, r, c, rows, cols);
            }
        }
    }
    return islands;
}

private void bfsIsland(char[][] grid, int r, int c, int rows, int cols) {
    Queue<int[]> queue = new LinkedList<>();
    queue.offer(new int[]{r, c});
    grid[r][c] = '0'; // mark visited by modifying grid

    int[] dr = {-1, 1, 0, 0};
    int[] dc = { 0, 0,-1, 1};

    while (!queue.isEmpty()) {
        int[] cell = queue.poll();
        for (int d = 0; d < 4; d++) {
            int nr = cell[0] + dr[d];
            int nc = cell[1] + dc[d];
            if (nr >= 0 && nr < rows && nc >= 0 && nc < cols && grid[nr][nc] == '1') {
                grid[nr][nc] = '0';
                queue.offer(new int[]{nr, nc});
            }
        }
    }
}
```

---

## Bipartite Check (LC #785)

A graph is **bipartite** if you can color nodes with two colors such that no two adjacent nodes share a color. Equivalent to: it contains no odd-length cycle.

BFS approach: try to 2-color the graph. If a conflict arises, it's not bipartite.

```java
public boolean isBipartite(int[][] graph) {
    int n = graph.length;
    int[] color = new int[n]; // 0=uncolored, 1=color1, -1=color2
    // graph[i] = list of neighbors of node i

    for (int start = 0; start < n; start++) {
        if (color[start] != 0) continue; // already colored component

        Queue<Integer> queue = new LinkedList<>();
        queue.offer(start);
        color[start] = 1;

        while (!queue.isEmpty()) {
            int node = queue.poll();
            for (int neighbor : graph[node]) {
                if (color[neighbor] == 0) {
                    color[neighbor] = -color[node]; // opposite color
                    queue.offer(neighbor);
                } else if (color[neighbor] == color[node]) {
                    return false; // same color clash → not bipartite
                }
            }
        }
    }
    return true;
}
```

---

## Multi-Source BFS

Start BFS from multiple sources simultaneously — useful for "spreading" problems.

**Walls and Gates (LC #286):**

```
INF  -1   0  INF
INF INF INF  -1
INF  -1 INF  -1
  0  -1 INF INF

Fill each INF cell with distance to nearest 0 (gate).
```

```java
public void wallsAndGates(int[][] rooms) {
    int rows = rooms.length, cols = rooms[0].length;
    Queue<int[]> queue = new LinkedList<>();

    // Add ALL gates (0) to queue simultaneously
    for (int r = 0; r < rows; r++)
        for (int c = 0; c < cols; c++)
            if (rooms[r][c] == 0) queue.offer(new int[]{r, c});

    int[] dr = {-1,1,0,0}, dc = {0,0,-1,1};
    while (!queue.isEmpty()) {
        int[] cell = queue.poll();
        for (int d = 0; d < 4; d++) {
            int nr = cell[0]+dr[d], nc = cell[1]+dc[d];
            if (nr>=0 && nr<rows && nc>=0 && nc<cols && rooms[nr][nc] == Integer.MAX_VALUE) {
                rooms[nr][nc] = rooms[cell[0]][cell[1]] + 1;
                queue.offer(new int[]{nr, nc});
            }
        }
    }
}
```

---

## BFS Complexity

| | Graph BFS |
|--|-----------|
| Time | O(V + E) |
| Space | O(V) — visited array + queue |

For grid problems: V = rows × cols, E ≤ 4V (4-directional) → O(rows × cols).

---

## Try It Yourself

**Problem:** Find the shortest path from start to end in a binary matrix (only 0-cells can be traversed). (LC #1091)

```
Grid:   [[0,0,0],
          [1,1,0],
          [1,1,0]]
Start: (0,0), End: (2,2)
Output: 4 (path: (0,0)→(0,1)→(0,2)→(1,2)→(2,2))
```

<details>
<summary>Solution</summary>

```java
public int shortestPathBinaryMatrix(int[][] grid) {
    int n = grid.length;
    if (grid[0][0] == 1 || grid[n-1][n-1] == 1) return -1;
    if (n == 1) return 1;

    Queue<int[]> queue = new LinkedList<>();
    queue.offer(new int[]{0, 0, 1}); // {row, col, path_length}
    grid[0][0] = 1; // mark visited

    int[] dr = {-1,-1,-1,0,0,1,1,1};
    int[] dc = {-1, 0, 1,-1,1,-1,0,1}; // 8-directional

    while (!queue.isEmpty()) {
        int[] curr = queue.poll();
        for (int d = 0; d < 8; d++) {
            int nr = curr[0]+dr[d], nc = curr[1]+dc[d];
            if (nr<0 || nr>=n || nc<0 || nc>=n || grid[nr][nc]==1) continue;
            if (nr == n-1 && nc == n-1) return curr[2] + 1;
            grid[nr][nc] = 1;
            queue.offer(new int[]{nr, nc, curr[2]+1});
        }
    }
    return -1;
}
```

**Key:** BFS guarantees the first time we reach `(n-1, n-1)` is the shortest path. 8-directional means diagonal moves are allowed.

</details>

---

## Capstone Connection

Add `datastructures/graphs/GraphBFS.java` to AlgoForge with:
- `bfs(int start)` — returns BFS order
- `shortestPath(int src, int dst)` — returns distance
- `connectedComponents()` — returns count of components
