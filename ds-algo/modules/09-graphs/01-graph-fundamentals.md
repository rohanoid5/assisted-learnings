# 9.1 — Graph Fundamentals

## What Is a Graph?

A **graph G = (V, E)** consists of a set of **vertices** (nodes) V and a set of **edges** E connecting pairs of vertices.

```
Undirected Graph:           Directed Graph (Digraph):
    A --- B                     A ──→ B
    |   / |                     ↑     |
    |  /  |                     |     ↓
    | /   |                     D ←── C
    C --- D

Edge (A,B) = Edge (B,A)     Edge A→B ≠ Edge B→A
```

---

## Graph Vocabulary

| Term | Meaning |
|------|---------|
| **Vertex / Node** | A point in the graph |
| **Edge** | A connection between two vertices |
| **Directed / Undirected** | Edges have direction (one-way) or not |
| **Weighted** | Edges have a numeric cost/weight |
| **Degree** | Number of edges incident to a vertex |
| **In-degree / Out-degree** | For directed graphs: edges coming in / going out |
| **Path** | A sequence of vertices connected by edges |
| **Cycle** | A path that starts and ends at the same vertex |
| **Connected** | Undirected graph where every vertex is reachable |
| **Strongly Connected** | Directed graph where every vertex reaches every other |
| **DAG** | Directed Acyclic Graph — directed, no cycles |
| **Dense / Sparse** | Close to V² edges / much fewer than V² edges |

---

## Graph Representations

### Adjacency List (preferred for sparse graphs)

```
Graph:   0 -- 1 -- 2
              |
              3

Adjacency List:
  0: [1]
  1: [0, 2, 3]
  2: [1]
  3: [1]
```

```java
// adjacency list using array of lists
int n = 4;
List<List<Integer>> adj = new ArrayList<>();
for (int i = 0; i < n; i++) adj.add(new ArrayList<>());

// Add undirected edge 0-1
adj.get(0).add(1);
adj.get(1).add(0);

// Weighted variant: List<List<int[]>> where int[] = {neighbor, weight}
List<List<int[]>> weightedAdj = new ArrayList<>();
for (int i = 0; i < n; i++) weightedAdj.add(new ArrayList<>());
weightedAdj.get(0).add(new int[]{1, 5}); // edge 0→1 with weight 5
```

**Space:** O(V + E)  
**Edge lookup:** O(degree(v))

### Adjacency Matrix

```
Graph:   0 -- 1 -- 2
              |
              3

    0  1  2  3
  0[0, 1, 0, 0]
  1[1, 0, 1, 1]
  2[0, 1, 0, 0]
  3[0, 1, 0, 0]
```

```java
int n = 4;
int[][] matrix = new int[n][n];
matrix[0][1] = matrix[1][0] = 1; // undirected edge 0-1
```

**Space:** O(V²)  
**Edge lookup:** O(1)  
**Use when:** Dense graphs, need fast edge existence checks, graph given as grid/matrix

### Edge List

Simplest: just a list of edges. Used in Kruskal's algorithm.

```java
int[][] edges = {{0,1,5}, {1,2,3}, {1,3,7}}; // [from, to, weight]
```

---

## Building a Graph from Interview Input

Many LC graph problems give edges as `int[][] edges` or `int n` + edge list:

```java
// LC style: n nodes, edges = [[0,1],[1,2],[1,3]]
public List<List<Integer>> buildAdjList(int n, int[][] edges) {
    List<List<Integer>> adj = new ArrayList<>();
    for (int i = 0; i < n; i++) adj.add(new ArrayList<>());
    for (int[] edge : edges) {
        adj.get(edge[0]).add(edge[1]);
        adj.get(edge[1]).add(edge[0]); // undirected
    }
    return adj;
}
```

### Grid as Graph

Many problems use a 2D grid where each cell is a node and edges connect adjacent cells:

```java
// 4-directional neighbors
int[] dr = {-1, 1, 0, 0}; // row offsets
int[] dc = { 0, 0,-1, 1}; // col offsets

for (int d = 0; d < 4; d++) {
    int nr = r + dr[d];
    int nc = c + dc[d];
    if (nr >= 0 && nr < rows && nc >= 0 && nc < cols) {
        // valid neighbor (nr, nc)
    }
}
```

---

## Complexity Comparison

| Operation | Adjacency List | Adjacency Matrix |
|-----------|---------------|-----------------|
| Space | O(V + E) | O(V²) |
| Add edge | O(1) | O(1) |
| Remove edge | O(degree) | O(1) |
| Edge exists? | O(degree) | O(1) |
| List neighbors | O(degree) | O(V) |
| Best for | Sparse graphs | Dense graphs |

For most interview problems: **adjacency list is the default choice**.

---

## Try It Yourself

**Problem:** Given `n` nodes and a list of `edges`, determine if the graph is a valid tree (connected + no cycles). (LC #261)

```
Input: n=5, edges=[[0,1],[0,2],[0,3],[1,4]]
Output: true  (forms a tree)

Input: n=5, edges=[[0,1],[1,2],[2,3],[1,3],[1,4]]
Output: false (has a cycle: 1-2-3-1)
```

<details>
<summary>Solution</summary>

A valid tree has exactly n−1 edges and is connected.

```java
public boolean validTree(int n, int[][] edges) {
    // A tree must have exactly n-1 edges
    if (edges.length != n - 1) return false;

    // Build adjacency list
    List<List<Integer>> adj = new ArrayList<>();
    for (int i = 0; i < n; i++) adj.add(new ArrayList<>());
    for (int[] e : edges) {
        adj.get(e[0]).add(e[1]);
        adj.get(e[1]).add(e[0]);
    }

    // BFS from node 0, check all nodes reachable (connected)
    boolean[] visited = new boolean[n];
    Queue<Integer> queue = new LinkedList<>();
    queue.offer(0);
    visited[0] = true;
    int count = 1;

    while (!queue.isEmpty()) {
        int node = queue.poll();
        for (int neighbor : adj.get(node)) {
            if (!visited[neighbor]) {
                visited[neighbor] = true;
                queue.offer(neighbor);
                count++;
            }
        }
    }
    return count == n; // all nodes reached = connected
}
```

**Why n−1 edges check first:** Any graph with n nodes and n−1 edges that is connected must be a tree (no cycles). The connectivity check validates the "connected" part.

</details>

---

## Capstone Connection

Implement `datastructures/graphs/Graph.java` in AlgoForge as a reusable adjacency-list graph that supports both directed and undirected graphs, weighted and unweighted edges:

```java
public class Graph {
    private final int vertices;
    private final boolean directed;
    private final List<List<int[]>> adj; // {neighbor, weight}

    public Graph(int vertices, boolean directed) { ... }
    public void addEdge(int u, int v) { addEdge(u, v, 1); }
    public void addEdge(int u, int v, int weight) { ... }
    public List<int[]> neighbors(int v) { ... }
    public int vertices() { return vertices; }
}
```
