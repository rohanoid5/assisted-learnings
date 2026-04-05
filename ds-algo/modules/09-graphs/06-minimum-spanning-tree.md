# 9.6 — Minimum Spanning Tree

## What Is a Spanning Tree?

A **spanning tree** of a connected, undirected graph contains all V vertices and exactly V−1 edges (enough to keep it connected), with no cycles.

A **minimum spanning tree (MST)** is the spanning tree with the smallest total edge weight.

```
Graph:
  A --4-- B
  |     / |
  2   3   5
  |  /    |
  C --6-- D

MST (total weight 9):
  A --2-- C
  C --3-- B    (not A-B-4 because 3 < 4)
  B --4-- A    wait, recalculate...

Actually:
  A-C: 2
  B-C: 3
  B-D: 5
  Total = 10 ✓  (leaves out A-B:4 and C-D:6)
```

---

## Kruskal's Algorithm

**Sort edges by weight. Add edge if it doesn't create a cycle.**

Uses **Union-Find** (see Module 10) to efficiently check cycle creation.

```java
public int[][] kruskalMST(int n, int[][] edges) {
    // Sort edges by weight
    Arrays.sort(edges, (a, b) -> a[2] - b[2]);

    int[] parent = new int[n];
    int[] rank   = new int[n];
    for (int i = 0; i < n; i++) parent[i] = i;

    List<int[]> mst = new ArrayList<>();
    int totalWeight = 0;

    for (int[] edge : edges) {
        int u = edge[0], v = edge[1], w = edge[2];
        int pu = find(parent, u);
        int pv = find(parent, v);

        if (pu != pv) {             // different components → safe to add
            union(parent, rank, pu, pv);
            mst.add(edge);
            totalWeight += w;
            if (mst.size() == n - 1) break; // MST complete
        }
    }
    return mst.toArray(new int[0][]);
}

private int find(int[] parent, int x) {
    if (parent[x] != x) parent[x] = find(parent, parent[x]); // path compression
    return parent[x];
}

private void union(int[] parent, int[] rank, int x, int y) {
    if (rank[x] < rank[y]) parent[x] = y;
    else if (rank[x] > rank[y]) parent[y] = x;
    else { parent[y] = x; rank[x]++; }
}
```

**Complexity:** O(E log E) for sorting + O(E α(V)) for Union-Find ≈ O(E log E) total.

---

## Kruskal's Trace

```
Graph edges (sorted): [(A,C,2), (B,C,3), (A,B,4), (B,D,5), (C,D,6)]
Nodes: A=0, B=1, C=2, D=3
parent = [0,1,2,3]

Process (A,C,2): find(A)=0, find(C)=2. Different → union. MST: {AC:2}
  parent = [0,1,0,3]  (C's parent = A)

Process (B,C,3): find(B)=1, find(C)→find(0)=0. Different → union. MST: {AC:2, BC:3}
  parent = [0,0,0,3]  (B's parent = A)

Process (A,B,4): find(A)=0, find(B)→find(0)=0. SAME component, skip (would create cycle)

Process (B,D,5): find(B)=0, find(D)=3. Different → union. MST: {AC:2, BC:3, BD:5}
  parent = [0,0,0,0]  (D's parent = A component)

MST has n-1=3 edges → done. Total weight = 2+3+5 = 10 ✓
```

---

## Prim's Algorithm

**Grow the MST one vertex at a time**: start from any vertex, always add the minimum-weight edge that connects a new vertex to the current MST.

```java
public int primMST(List<List<int[]>> adj, int n) {
    boolean[] inMST = new boolean[n];
    // Min-heap: {weight, node}
    PriorityQueue<int[]> pq = new PriorityQueue<>((a, b) -> a[0] - b[0]);
    pq.offer(new int[]{0, 0}); // start from node 0, weight 0
    int totalWeight = 0;
    int edgesAdded = 0;

    while (!pq.isEmpty() && edgesAdded < n) {
        int[] curr = pq.poll();
        int weight = curr[0], node = curr[1];

        if (inMST[node]) continue; // already in MST
        inMST[node] = true;
        totalWeight += weight;
        edgesAdded++;

        for (int[] edge : adj.get(node)) {
            int neighbor = edge[0], edgeWeight = edge[1];
            if (!inMST[neighbor]) {
                pq.offer(new int[]{edgeWeight, neighbor});
            }
        }
    }
    return totalWeight;
}
```

**Complexity:** O((V + E) log V) using a binary heap.  
Same as Dijkstra in structure — just different semantics.

---

## Kruskal's vs Prim's

| | Kruskal's | Prim's |
|--|-----------|--------|
| Strategy | Edge-based (sort all edges) | Vertex-based (grow from source) |
| Data structure | Union-Find | Priority queue |
| Best for | Sparse graphs | Dense graphs |
| Complexity | O(E log E) | O((V+E) log V) |
| Preferred when | E is small | V is small |

---

## Connecting Cities (LC #1135)

```
n cities, connections[i] = [city1, city2, cost]
Return min cost to connect all cities (MST).
```

```java
public int minimumCost(int n, int[][] connections) {
    Arrays.sort(connections, (a, b) -> a[2] - b[2]);

    int[] parent = new int[n + 1];
    for (int i = 0; i <= n; i++) parent[i] = i;
    int[] rank = new int[n + 1];

    int totalCost = 0;
    int connected = 0;

    for (int[] conn : connections) {
        int u = conn[0], v = conn[1], cost = conn[2];
        int pu = find(parent, u), pv = find(parent, v);
        if (pu != pv) {
            union(parent, rank, pu, pv);
            totalCost += cost;
            connected++;
        }
        if (connected == n - 1) return totalCost;
    }
    return -1; // can't connect all cities
}
```

---

## Try It Yourself

**Problem:** You are given an array `points` where `points[i] = [xi, yi]`. Return the minimum cost to connect all points, where the cost of connecting two points is the Manhattan distance: `|xi - xj| + |yi - yj|`. (LC #1584)

<details>
<summary>Solution</summary>

```java
public int minCostConnectPoints(int[][] points) {
    int n = points.length;
    // Prim's: no need to store all edges (dense graph)
    int[] minDist = new int[n]; // min edge weight to reach each node from MST
    boolean[] inMST = new boolean[n];
    Arrays.fill(minDist, Integer.MAX_VALUE);
    minDist[0] = 0;

    int totalCost = 0;

    for (int i = 0; i < n; i++) {
        // Find unvisited node with min distance to MST
        int u = -1;
        for (int j = 0; j < n; j++) {
            if (!inMST[j] && (u == -1 || minDist[j] < minDist[u])) u = j;
        }

        inMST[u] = true;
        totalCost += minDist[u];

        // Update distances of unvisited neighbors
        for (int v = 0; v < n; v++) {
            if (!inMST[v]) {
                int dist = Math.abs(points[u][0] - points[v][0])
                         + Math.abs(points[u][1] - points[v][1]);
                minDist[v] = Math.min(minDist[v], dist);
            }
        }
    }
    return totalCost;
}
```

**Why Prim's:** Dense graph (all O(n²) pairs of edges) — Prim's with O(V²) simple implementation outperforms Kruskal's O(V² log V²) sorting approach here.

**Complexity:** O(V²) — vertex-scan variant of Prim's ideal for dense graphs.

</details>

---

## Capstone Connection

Add to AlgoForge:
- `datastructures/graphs/KruskalMST.java`
- `datastructures/graphs/PrimMST.java`
- Both should expose `int totalWeight()` and `List<int[]> mstEdges()`
