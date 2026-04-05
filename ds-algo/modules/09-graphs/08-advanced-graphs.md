# 9.8 — Advanced Graph Algorithms

## Overview

This topic covers four advanced areas tested at top-tier companies: Union-Find for connectivity, A* for heuristic-guided pathfinding, bridges/articulation points for network reliability, and Strongly Connected Components (Kosaraju's/Tarjan's).

---

## Union-Find (Disjoint Set Union)

Union-Find efficiently answers "are these two nodes in the same component?" and "merge these two components."

Covered in depth in **Module 10 (Advanced DS)**, but introduced here because of its graph applications.

```java
class UnionFind {
    private int[] parent, rank;

    public UnionFind(int n) {
        parent = new int[n];
        rank   = new int[n];
        for (int i = 0; i < n; i++) parent[i] = i;
    }

    public int find(int x) {
        if (parent[x] != x) parent[x] = find(parent[x]); // path compression
        return parent[x];
    }

    public boolean union(int x, int y) {
        int px = find(x), py = find(y);
        if (px == py) return false; // already same component
        if (rank[px] < rank[py]) { int tmp = px; px = py; py = tmp; }
        parent[py] = px;
        if (rank[px] == rank[py]) rank[px]++;
        return true;
    }
}
```

**Graph use cases:**
- Kruskal's MST (already seen)
- Detecting cycle in undirected graph (union returns false if same component)
- Number of islands / connected components
- Redundant connection (LC #684)

---

## Redundant Connection (LC #684)

Find the edge that creates a cycle in an undirected graph (return it to make the graph a tree):

```java
public int[] findRedundantConnection(int[][] edges) {
    int n = edges.length;
    UnionFind uf = new UnionFind(n + 1);

    for (int[] edge : edges) {
        if (!uf.union(edge[0], edge[1])) {
            return edge; // this edge created a cycle
        }
    }
    return new int[0];
}
```

---

## Bridges in a Graph

A **bridge** is an edge whose removal disconnects the graph. Critical for network reliability analysis.

DFS-based algorithm using discovery time and "low" values:

```
low[v] = minimum discovery time reachable from v's subtree via back edges

If low[neighbor] > disc[node] → edge (node, neighbor) is a bridge
(neighbor cannot reach node or its ancestors without using this edge)
```

```java
public List<List<Integer>> criticalConnections(int n, List<List<Integer>> connections) {
    List<List<Integer>> adj = new ArrayList<>();
    for (int i = 0; i < n; i++) adj.add(new ArrayList<>());
    for (List<Integer> c : connections) {
        adj.get(c.get(0)).add(c.get(1));
        adj.get(c.get(1)).add(c.get(0));
    }

    List<List<Integer>> bridges = new ArrayList<>();
    int[] disc = new int[n], low = new int[n];
    Arrays.fill(disc, -1);
    int[] timer = {0};

    for (int i = 0; i < n; i++) {
        if (disc[i] == -1) dfsBridge(adj, i, -1, disc, low, timer, bridges);
    }
    return bridges;
}

private void dfsBridge(List<List<Integer>> adj, int u, int parent,
                        int[] disc, int[] low, int[] timer,
                        List<List<Integer>> bridges) {
    disc[u] = low[u] = timer[0]++;

    for (int v : adj.get(u)) {
        if (v == parent) continue;         // don't go back on same edge
        if (disc[v] == -1) {
            dfsBridge(adj, v, u, disc, low, timer, bridges);
            low[u] = Math.min(low[u], low[v]); // update from child
            if (low[v] > disc[u]) {
                bridges.add(Arrays.asList(u, v)); // bridge found
            }
        } else {
            low[u] = Math.min(low[u], disc[v]); // back edge: update low
        }
    }
}
```

**LC #1192** — Critical Connections in a Network.

---

## Strongly Connected Components (Kosaraju's Algorithm)

An **SCC** is a maximal subset of vertices in a directed graph where every vertex can reach every other vertex.

```
Directed Graph:
  A → B → C → A   (one SCC: {A,B,C})
  B → D            (D is its own SCC)
```

Kosaraju's algorithm:
1. Run DFS on original graph, push nodes to stack in **finish order**
2. Transpose the graph (reverse all edges)
3. Pop from stack, run DFS on transposed graph — each DFS reveals one SCC

```java
public int countSCCs(List<List<Integer>> adj, List<List<Integer>> radj, int n) {
    boolean[] visited = new boolean[n];
    Deque<Integer> stack = new ArrayDeque<>();

    // Step 1: DFS on original, push in finish order
    for (int i = 0; i < n; i++) {
        if (!visited[i]) dfsFinish(adj, i, visited, stack);
    }

    // Step 2: DFS on reversed graph following stack order
    visited = new boolean[n];
    int components = 0;

    while (!stack.isEmpty()) {
        int node = stack.pop();
        if (!visited[node]) {
            dfsVisit(radj, node, visited);
            components++;
        }
    }
    return components;
}

private void dfsFinish(List<List<Integer>> adj, int node, boolean[] vis, Deque<Integer> stack) {
    vis[node] = true;
    for (int nb : adj.get(node)) if (!vis[nb]) dfsFinish(adj, nb, vis, stack);
    stack.push(node); // push after fully exploring
}

private void dfsVisit(List<List<Integer>> adj, int node, boolean[] vis) {
    vis[node] = true;
    for (int nb : adj.get(node)) if (!vis[nb]) dfsVisit(adj, nb, vis);
}
```

---

## A* (A-Star) Search

A* combines Dijkstra with a **heuristic** to guide search towards the goal faster.

```
f(n) = g(n) + h(n)
g(n) = actual cost from start to n
h(n) = heuristic estimate of cost from n to goal
```

Uses a min-heap ordered by f(n). When h(n) = 0, A* reduces to Dijkstra.

For grids, common heuristic is **Manhattan distance** or **Euclidean distance**:

```java
// h = Manhattan distance to goal (for 4-directional grids)
int h = Math.abs(nr - goalRow) + Math.abs(nc - goalCol);

// PQ ordered by g + h
PriorityQueue<int[]> pq = new PriorityQueue<>((a,b) -> (a[2]+a[3]) - (b[2]+b[3]));
// {row, col, g, h}
```

A* is used in game pathfinding (Unity, Unreal), GPS navigation, and robotics.

---

## Algorithm Reference Summary

| Problem Type | Algorithm |
|-------------|-----------|
| Shortest path (unweighted) | BFS |
| Shortest path (non-negative weights) | Dijkstra |
| Shortest path (negative weights) | Bellman-Ford |
| All-pairs shortest path | Floyd-Warshall |
| Minimum spanning tree | Kruskal's or Prim's |
| Task ordering / dependency | Topological Sort (Kahn's) |
| Connected components | BFS/DFS or Union-Find |
| Strongly connected components | Kosaraju's or Tarjan's |
| Critical edges (bridges) | DFS with low/disc arrays |
| Heuristic-guided shortest path | A* |

---

## Try It Yourself

**Problem:** There are n servers, some connected by cables. Find and return all critical connections (bridges) — connections that, if removed, will disconnect the servers. (LC #1192)

Already covered with `criticalConnections` above — implement it from scratch.

<details>
<summary>Solution Reminder</summary>

The key insight: edge (u,v) is a bridge if and only if `low[v] > disc[u]` — meaning v (and its subtree) cannot reach u or any of u's ancestors without using the edge (u,v) itself.

Review the full implementation in the Bridges section above. Test with:
```
n=4, connections=[[0,1],[1,2],[2,0],[1,3]]
Expected: [[1,3]]  (removing 1-3 disconnects node 3)
```

</details>

---

## Capstone Connection

Add to AlgoForge:
- `datastructures/graphs/UnionFind.java` — full implementation with path compression + union by rank (also used in Module 10)
- `problems/graphs/Bridges.java` — critical connections (LC #1192)
- `problems/graphs/SCCKosaraju.java` — strongly connected components
- `problems/graphs/RedundantConnection.java` — LC #684
