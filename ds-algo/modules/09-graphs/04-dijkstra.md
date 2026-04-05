# 9.4 — Dijkstra's Algorithm

## The Problem: Weighted Shortest Path

BFS gives shortest path by **edge count**. When edges have different weights (costs), BFS no longer works — we need Dijkstra's algorithm.

```
Graph:
    A ---1--- B
    |         |
    4         2
    |         |
    C ---1--- D

BFS from A says: A→B→D (2 hops) = 3 cost
Dijkstra says:   A→B→D (actual cost 1+2=3) vs A→C→D (4+1=5)
Correct path:    A→B→D with cost 3  ✓
```

---

## Core Idea

Dijkstra's is **greedy BFS with a priority queue**: always process the node with the smallest known distance first.

```
dist[] = {A:0, B:∞, C:∞, D:∞, E:∞}
MinHeap: [(0, A)]

Step 1: Pop (0, A). Process A's neighbors:
  B: 0+1=1 < ∞ → update dist[B]=1, push (1,B)
  C: 0+4=4 < ∞ → update dist[C]=4, push (4,C)
  MinHeap: [(1,B), (4,C)]

Step 2: Pop (1, B). Process B's neighbors:
  D: 1+2=3 < ∞ → update dist[D]=3, push (3,D)
  (A already visited or dist[A] unchanged)
  MinHeap: [(3,D), (4,C)]

Step 3: Pop (3, D). Process D's neighbors:
  C: 3+1=4 = dist[C] → no update (tie, can skip)
  MinHeap: [(4,C)]

Step 4: Pop (4, C). No better paths found.

Final: dist = {A:0, B:1, C:4, D:3}
```

---

## Implementation

```java
public int[] dijkstra(List<List<int[]>> adj, int src, int n) {
    int[] dist = new int[n];
    Arrays.fill(dist, Integer.MAX_VALUE);
    dist[src] = 0;

    // Min-heap: {distance, node}
    PriorityQueue<int[]> pq = new PriorityQueue<>((a, b) -> a[0] - b[0]);
    pq.offer(new int[]{0, src});

    while (!pq.isEmpty()) {
        int[] curr = pq.poll();
        int d = curr[0], node = curr[1];

        // Skip if we already found a shorter path to this node
        if (d > dist[node]) continue;

        for (int[] edge : adj.get(node)) {
            int neighbor = edge[0], weight = edge[1];
            int newDist = dist[node] + weight;
            if (newDist < dist[neighbor]) {
                dist[neighbor] = newDist;
                pq.offer(new int[]{newDist, neighbor});
            }
        }
    }
    return dist;
}
```

**Complexity:**  
- Time: O((V + E) log V) with a binary heap
- Space: O(V + E) for adjacency list + O(V) for dist array

---

## Dijkstra with Path Reconstruction

Track each node's predecessor to reconstruct the actual shortest path:

```java
public List<Integer> shortestPath(List<List<int[]>> adj, int src, int dst, int n) {
    int[] dist = new int[n];
    int[] prev = new int[n];
    Arrays.fill(dist, Integer.MAX_VALUE);
    Arrays.fill(prev, -1);
    dist[src] = 0;

    PriorityQueue<int[]> pq = new PriorityQueue<>((a, b) -> a[0] - b[0]);
    pq.offer(new int[]{0, src});

    while (!pq.isEmpty()) {
        int[] curr = pq.poll();
        int d = curr[0], node = curr[1];
        if (d > dist[node]) continue;

        for (int[] edge : adj.get(node)) {
            int neighbor = edge[0], weight = edge[1];
            int newDist = dist[node] + weight;
            if (newDist < dist[neighbor]) {
                dist[neighbor] = newDist;
                prev[neighbor] = node; // track predecessor
                pq.offer(new int[]{newDist, neighbor});
            }
        }
    }

    // Reconstruct path from dst to src
    List<Integer> path = new ArrayList<>();
    for (int at = dst; at != -1; at = prev[at]) path.add(at);
    Collections.reverse(path);
    return (path.get(0) == src) ? path : new ArrayList<>(); // empty if unreachable
}
```

---

## Network Delay Time (LC #743)

```
n nodes, times[i] = [source, target, time]
You send signal from node k. Find time until all nodes receive signal.
= single-source shortest path, answer = max of all dist[]

Input: times = [[2,1,1],[2,3,1],[3,4,1]], n=4, k=2
Output: 2
```

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
        if (curr[0] > dist[curr[1]]) continue;
        for (int[] edge : adj.get(curr[1])) {
            int newDist = dist[curr[1]] + edge[1];
            if (newDist < dist[edge[0]]) {
                dist[edge[0]] = newDist;
                pq.offer(new int[]{newDist, edge[0]});
            }
        }
    }

    int maxDist = 0;
    for (int i = 1; i <= n; i++) {
        if (dist[i] == Integer.MAX_VALUE) return -1; // unreachable
        maxDist = Math.max(maxDist, dist[i]);
    }
    return maxDist;
}
```

---

## Cheapest Flights Within K Stops (LC #787)

Dijkstra variant with a constraint — at most k stops. Use state `(node, stops)`.

```java
public int findCheapestPrice(int n, int[][] flights, int src, int dst, int k) {
    int[] dist = new int[n];
    Arrays.fill(dist, Integer.MAX_VALUE);
    dist[src] = 0;

    List<List<int[]>> adj = new ArrayList<>();
    for (int i = 0; i < n; i++) adj.add(new ArrayList<>());
    for (int[] f : flights) adj.get(f[0]).add(new int[]{f[1], f[2]});

    // {cost, node, stopsUsed}
    Queue<int[]> queue = new LinkedList<>();
    queue.offer(new int[]{0, src, 0});

    // Use BFS/Bellman-Ford style (not Dijkstra) for k-stops constraint
    int[] costs = new int[n];
    Arrays.fill(costs, Integer.MAX_VALUE);
    costs[src] = 0;

    for (int i = 0; i <= k; i++) {       // k+1 rounds (k stops = k+1 edges)
        int[] temp = costs.clone();
        for (int[] f : flights) {
            int u = f[0], v = f[1], w = f[2];
            if (costs[u] != Integer.MAX_VALUE && costs[u] + w < temp[v]) {
                temp[v] = costs[u] + w;
            }
        }
        costs = temp;
    }
    return costs[dst] == Integer.MAX_VALUE ? -1 : costs[dst];
}
```

---

## Dijkstra Limitations

1. **No negative weights** — if a negative edge exists, the greedy assumption (once settled, distance is final) breaks.
2. Use **Bellman-Ford** for negative weights (see 9.5).
3. Use **BFS** for unweighted graphs (simpler, same O(V+E)).

---

## Try It Yourself

**Problem:** Find the path with the minimum effort in a 2D grid where effort = max absolute height difference along a path. (LC #1631)

<details>
<summary>Solution</summary>

```java
public int minimumEffortPath(int[][] heights) {
    int rows = heights.length, cols = heights[0].length;
    int[][] effort = new int[rows][cols];
    for (int[] row : effort) Arrays.fill(row, Integer.MAX_VALUE);
    effort[0][0] = 0;

    // Min-heap: {effort, row, col}
    PriorityQueue<int[]> pq = new PriorityQueue<>((a, b) -> a[0] - b[0]);
    pq.offer(new int[]{0, 0, 0});

    int[] dr = {-1,1,0,0}, dc = {0,0,-1,1};

    while (!pq.isEmpty()) {
        int[] curr = pq.poll();
        int e = curr[0], r = curr[1], c = curr[2];

        if (r == rows-1 && c == cols-1) return e; // reached destination

        if (e > effort[r][c]) continue;

        for (int d = 0; d < 4; d++) {
            int nr = r+dr[d], nc = c+dc[d];
            if (nr<0||nr>=rows||nc<0||nc>=cols) continue;
            int newEffort = Math.max(e, Math.abs(heights[r][c] - heights[nr][nc]));
            if (newEffort < effort[nr][nc]) {
                effort[nr][nc] = newEffort;
                pq.offer(new int[]{newEffort, nr, nc});
            }
        }
    }
    return effort[rows-1][cols-1];
}
```

**Key:** Replace "sum of weights" with "max edge weight" — still works with Dijkstra's greedy priority queue approach.

</details>

---

## Capstone Connection

Add `datastructures/graphs/Dijkstra.java` to AlgoForge. Also add `problems/graphs/NetworkDelay.java` and `problems/graphs/CheapestFlights.java`.
