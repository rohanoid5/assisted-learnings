# 9.5 — Bellman-Ford Algorithm

## Why Bellman-Ford?

Dijkstra's algorithm **fails with negative edge weights** — it assumes that once a node is settled, its distance is final. A negative edge can later provide a shorter path.

```
Graph with negative edge:
    A --5→ B
    A --3→ C --(-4)→ B

Dijkstra pops A, settles B at dist=5 first.
Later finds B via C: 3 + (-4) = -1 < 5.
But Dijkstra already "settled" B → incorrect!

Bellman-Ford: relaxes all edges V-1 times → correctly finds dist[B]=-1.
```

---

## Core Idea

Relax every edge up to **V−1 times**. After V−1 rounds, the shortest path (which can use at most V−1 edges in a V-node graph) is guaranteed to be found.

```java
public int[] bellmanFord(int n, int[][] edges, int src) {
    // edges[i] = {from, to, weight}
    int[] dist = new int[n];
    Arrays.fill(dist, Integer.MAX_VALUE);
    dist[src] = 0;

    // Relax all edges V-1 times
    for (int i = 0; i < n - 1; i++) {
        for (int[] edge : edges) {
            int u = edge[0], v = edge[1], w = edge[2];
            if (dist[u] != Integer.MAX_VALUE && dist[u] + w < dist[v]) {
                dist[v] = dist[u] + w;
            }
        }
    }

    // Check for negative cycles
    for (int[] edge : edges) {
        int u = edge[0], v = edge[1], w = edge[2];
        if (dist[u] != Integer.MAX_VALUE && dist[u] + w < dist[v]) {
            // If we can still relax, there's a negative cycle
            System.out.println("Negative cycle detected!");
            return null;
        }
    }

    return dist;
}
```

---

## Trace Example

```
Graph: A=0, B=1, C=2, D=3
Edges: (A→B,4), (A→C,2), (B→C,-3), (C→D,2), (B→D,3)

Initial: dist = [0, ∞, ∞, ∞]

Round 1 (relax all edges):
  A→B: dist[B] = min(∞, 0+4) = 4
  A→C: dist[C] = min(∞, 0+2) = 2
  B→C: dist[C] = min(2, 4+(-3)) = 1
  C→D: dist[D] = min(∞, 1+2) = 3  (using updated dist[C])
  B→D: dist[D] = min(3, 4+3) = 3

Round 2:
  B→C: dist[C] = min(1, 4-3) = 1  (no change)
  Others: no improvement
  (converged, remaining rounds won't change anything)

Final: dist = [0, 4, 1, 3]
Path: A→C=2, A→B→C=1
```

---

## Negative Cycle Detection

If on the **Vth relaxation** (after V−1 rounds) any edge can still be relaxed, a negative cycle exists:

```
Negative cycle: A --3→ B --(-5)→ A
dist[A] = 0
Round 1: A→B: dist[B]=3; B→A: dist[A]=3+(-5)=-2
Round 2: A→B: dist[B]=-2+3=1; B→A: dist[A]=1-5=-4
...keeps decreasing forever
```

---

## Complexity

| | Bellman-Ford |
|--|---|
| Time | O(V × E) |
| Space | O(V) |
| Negative weights | ✅ Handles |
| Negative cycles | ✅ Detects |
| vs Dijkstra | Slower but more general |

---

## When to Use Bellman-Ford

1. Negative edge weights present (Dijkstra won't work)
2. Need to detect negative cycles
3. The problem limits hops (K stops) → run K rounds
4. Distributed shortest path (OSPF protocol uses Bellman-Ford variant)

---

## LC #787 — Cheapest Flights Within K Stops

This is Bellman-Ford with K rounds (not V−1):

```java
public int findCheapestPrice(int n, int[][] flights, int src, int dst, int k) {
    int[] costs = new int[n];
    Arrays.fill(costs, Integer.MAX_VALUE);
    costs[src] = 0;

    // Run exactly k+1 rounds (k stops = k+1 edges)
    for (int i = 0; i <= k; i++) {
        int[] temp = costs.clone(); // snapshot: prevent using edges added THIS round
        for (int[] flight : flights) {
            int u = flight[0], v = flight[1], w = flight[2];
            if (costs[u] != Integer.MAX_VALUE) {
                temp[v] = Math.min(temp[v], costs[u] + w);
            }
        }
        costs = temp;
    }
    return costs[dst] == Integer.MAX_VALUE ? -1 : costs[dst];
}
```

**Why clone:** Without cloning, a single relaxation round might use 2 edges from the same round, violating the "K stops" constraint.

---

## Floyd-Warshall (All-Pairs Shortest Path)

When you need shortest paths **between all pairs** of nodes:

```java
public int[][] floydWarshall(int n, int[][] edges) {
    int[][] dist = new int[n][n];
    for (int[] row : dist) Arrays.fill(row, Integer.MAX_VALUE / 2);
    for (int i = 0; i < n; i++) dist[i][i] = 0;
    for (int[] e : edges) dist[e[0]][e[1]] = e[2];

    for (int k = 0; k < n; k++) {          // try each intermediate node k
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                if (dist[i][j] > dist[i][k] + dist[k][j]) {
                    dist[i][j] = dist[i][k] + dist[k][j];
                }
            }
        }
    }
    return dist;
}
```

**Complexity:** O(V³) time, O(V²) space — good for dense graphs with dozens of nodes.

---

## Algorithm Comparison

| Algorithm | Weights | Directed | Complexity | Use Case |
|-----------|---------|----------|-----------|---------|
| BFS | Unweighted | Both | O(V+E) | Shortest hops |
| Dijkstra | Non-negative | Both | O((V+E) log V) | Most weighted SSSP |
| Bellman-Ford | Any (with neg) | Both | O(V·E) | Negative edges, K-hop |
| Floyd-Warshall | Any | Both | O(V³) | All-pairs |

---

## Try It Yourself

**Problem:** There are n cities connected by some flights. Find the cheapest price from city `src` to city `dst` with at most `k` stops. If there's no such route, return -1. (LC #787)

Already covered above — try implementing it from memory, then verify against the solution.

<details>
<summary>Full Solution Reminder</summary>

```java
public int findCheapestPrice(int n, int[][] flights, int src, int dst, int k) {
    int[] prices = new int[n];
    Arrays.fill(prices, Integer.MAX_VALUE);
    prices[src] = 0;

    for (int i = 0; i <= k; i++) {
        int[] temp = prices.clone();
        for (int[] f : flights) {
            int from = f[0], to = f[1], price = f[2];
            if (prices[from] != Integer.MAX_VALUE) {
                temp[to] = Math.min(temp[to], prices[from] + price);
            }
        }
        prices = temp;
    }
    return prices[dst] == Integer.MAX_VALUE ? -1 : prices[dst];
}
```

</details>

---

## Capstone Connection

Add `datastructures/graphs/BellmanFord.java` to AlgoForge with:
- `shortestPath(int src)` — returns distance array
- `hasNegativeCycle()` — boolean
- Negative cycle detection path tracing (bonus)
