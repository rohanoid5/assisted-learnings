# 9.3 — Graph DFS

## DFS on Graphs

Graph DFS uses a stack (either explicit or the call stack via recursion) and a visited set. Unlike tree DFS, you must check for visited nodes.

```
Graph:  0 -- 1 -- 3
        |    |
        2 -- 4

DFS from 0 (one possible order):
Visit 0 → Visit 1 → Visit 3 → backtrack → Visit 4 → backtrack
         → Visit 2 → backtrack
Order: [0, 1, 3, 4, 2]
```

---

## Recursive DFS Template

```java
public void dfs(List<List<Integer>> adj, int node, boolean[] visited) {
    visited[node] = true;
    System.out.print(node + " ");

    for (int neighbor : adj.get(node)) {
        if (!visited[neighbor]) {
            dfs(adj, neighbor, visited);
        }
    }
}

// Call for all nodes (handles disconnected graphs)
boolean[] visited = new boolean[n];
for (int i = 0; i < n; i++) {
    if (!visited[i]) dfs(adj, i, visited);
}
```

---

## Iterative DFS Template

```java
public void dfsIterative(List<List<Integer>> adj, int start, int n) {
    boolean[] visited = new boolean[n];
    Deque<Integer> stack = new ArrayDeque<>();
    stack.push(start);

    while (!stack.isEmpty()) {
        int node = stack.pop();
        if (visited[node]) continue; // may be added multiple times
        visited[node] = true;
        System.out.print(node + " ");

        for (int neighbor : adj.get(node)) {
            if (!visited[neighbor]) stack.push(neighbor);
        }
    }
}
```

**Note:** Order differs from recursive DFS depending on push order. For exact recursive-equivalent order, push neighbors in reverse.

---

## Connected Components

Count isolated "islands" in an undirected graph:

```java
public int countComponents(int n, int[][] edges) {
    List<List<Integer>> adj = new ArrayList<>();
    for (int i = 0; i < n; i++) adj.add(new ArrayList<>());
    for (int[] e : edges) {
        adj.get(e[0]).add(e[1]);
        adj.get(e[1]).add(e[0]);
    }

    boolean[] visited = new boolean[n];
    int components = 0;

    for (int i = 0; i < n; i++) {
        if (!visited[i]) {
            dfs(adj, i, visited);
            components++;
        }
    }
    return components;
}
```

---

## Cycle Detection in Undirected Graph

DFS: a node is part of a cycle if we reach a visited node that is NOT the immediate parent.

```java
public boolean hasCycle(List<List<Integer>> adj, int n) {
    boolean[] visited = new boolean[n];
    for (int i = 0; i < n; i++) {
        if (!visited[i] && dfsHasCycle(adj, i, -1, visited)) return true;
    }
    return false;
}

private boolean dfsHasCycle(List<List<Integer>> adj, int node, int parent, boolean[] visited) {
    visited[node] = true;
    for (int neighbor : adj.get(node)) {
        if (!visited[neighbor]) {
            if (dfsHasCycle(adj, neighbor, node, visited)) return true;
        } else if (neighbor != parent) {
            return true; // back edge to non-parent → cycle
        }
    }
    return false;
}
```

---

## Cycle Detection in Directed Graph

Use three states: WHITE (unvisited), GRAY (in current DFS path), BLACK (fully processed).

```
Directed cycle:    A → B → C → A  (back edge C→A)
Not a cycle:       A → B, A → C, B → C  (cross edge, no cycle)
```

```java
// 0=unvisited, 1=in-stack(gray), 2=done(black)
public boolean hasCycleDirected(List<List<Integer>> adj, int n) {
    int[] state = new int[n];
    for (int i = 0; i < n; i++) {
        if (state[i] == 0 && dfsCycleDirected(adj, i, state)) return true;
    }
    return false;
}

private boolean dfsCycleDirected(List<List<Integer>> adj, int node, int[] state) {
    state[node] = 1; // mark GRAY (in current path)
    for (int neighbor : adj.get(node)) {
        if (state[neighbor] == 1) return true;  // back edge → cycle
        if (state[neighbor] == 0 && dfsCycleDirected(adj, neighbor, state)) return true;
    }
    state[node] = 2; // mark BLACK (done)
    return false;
}
```

---

## DFS on Grids — Flood Fill (LC #733)

```
Image:
  1 1 1
  1 1 0
  1 0 1

floodFill(sr=1, sc=1, newColor=2)

Result:
  2 2 2
  2 2 0
  2 0 1
```

```java
public int[][] floodFill(int[][] image, int sr, int sc, int color) {
    int oldColor = image[sr][sc];
    if (oldColor == color) return image; // no-op guard
    dfsFlood(image, sr, sc, oldColor, color);
    return image;
}

private void dfsFlood(int[][] img, int r, int c, int oldColor, int newColor) {
    if (r < 0 || r >= img.length || c < 0 || c >= img[0].length) return;
    if (img[r][c] != oldColor) return;
    img[r][c] = newColor;
    dfsFlood(img, r-1, c, oldColor, newColor);
    dfsFlood(img, r+1, c, oldColor, newColor);
    dfsFlood(img, r, c-1, oldColor, newColor);
    dfsFlood(img, r, c+1, oldColor, newColor);
}
```

---

## Pacific Atlantic Water Flow (LC #417)

Reverse the flow direction — instead of checking which cells flow to both oceans, DFS *inward* from ocean borders.

```
Matrix:
  1 2 2 3 5
  3 2 3 4 4
  2 4 5 3 1
  6 7 1 4 5
  5 1 1 2 4

Pacific (top/left border) and Atlantic (bottom/right border).
Find cells where water can flow to BOTH.
```

```java
public List<List<Integer>> pacificAtlantic(int[][] heights) {
    int rows = heights.length, cols = heights[0].length;
    boolean[][] pacific  = new boolean[rows][cols];
    boolean[][] atlantic = new boolean[rows][cols];

    for (int r = 0; r < rows; r++) {
        dfsOcean(heights, r, 0,        pacific,  rows, cols);
        dfsOcean(heights, r, cols - 1, atlantic, rows, cols);
    }
    for (int c = 0; c < cols; c++) {
        dfsOcean(heights, 0,        c, pacific,  rows, cols);
        dfsOcean(heights, rows - 1, c, atlantic, rows, cols);
    }

    List<List<Integer>> result = new ArrayList<>();
    for (int r = 0; r < rows; r++)
        for (int c = 0; c < cols; c++)
            if (pacific[r][c] && atlantic[r][c])
                result.add(Arrays.asList(r, c));
    return result;
}

private void dfsOcean(int[][] h, int r, int c, boolean[][] visited, int rows, int cols) {
    visited[r][c] = true;
    int[] dr = {-1,1,0,0}, dc = {0,0,-1,1};
    for (int d = 0; d < 4; d++) {
        int nr = r+dr[d], nc = c+dc[d];
        if (nr<0||nr>=rows||nc<0||nc>=cols||visited[nr][nc]) continue;
        if (h[nr][nc] >= h[r][c]) // water can flow from (nr,nc) to (r,c)
            dfsOcean(h, nr, nc, visited, rows, cols);
    }
}
```

---

## DFS Complexity

| | Graph DFS |
|--|-----------|
| Time | O(V + E) |
| Space | O(V) — visited + call stack |

---

## Try It Yourself

**Problem:** Given a directed graph, find all nodes that have no path to any node not in the set of safe nodes. A "safe" node eventually leads only to a terminal node (with no outgoing edges). (LC #802)

<details>
<summary>Solution</summary>

```java
// A node is safe if it's NOT part of a cycle and doesn't lead to a cycle
// 0=unvisited, 1=in-progress(gray), 2=safe(black), 3=unsafe
public List<Integer> eventualSafeNodes(int[][] graph) {
    int n = graph.length;
    int[] state = new int[n];
    List<Integer> result = new ArrayList<>();

    for (int i = 0; i < n; i++) {
        if (isSafe(graph, i, state)) result.add(i);
    }
    return result;
}

private boolean isSafe(int[][] graph, int node, int[] state) {
    if (state[node] == 2) return true;
    if (state[node] == 1) return false; // cycle detected

    state[node] = 1; // mark in-progress
    for (int next : graph[node]) {
        if (!isSafe(graph, next, state)) {
            state[node] = 3; // unsafe
            return false;
        }
    }
    state[node] = 2; // all paths lead to terminal → safe
    return true;
}
```

**Key insight:** DFS with 3-state coloring. A node is safe if none of its DFS paths lead to a cycle (back to a gray node).

</details>

---

## Capstone Connection

Add `datastructures/graphs/GraphDFS.java` with:
- Recursive and iterative DFS
- `connectedComponents()` count
- `hasCycle(boolean directed)` — cycle detection for both directed and undirected
