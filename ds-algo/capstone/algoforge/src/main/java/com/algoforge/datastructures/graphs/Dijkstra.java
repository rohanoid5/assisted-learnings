package com.algoforge.datastructures.graphs;

import java.util.*;

/**
 * Dijkstra's Algorithm — finds the shortest path from a source vertex to all
 * other vertices in a weighted graph with non-negative edge weights.
 *
 * <pre>
 * Graph:
 *   0 ──(4)── 1 ──(1)── 2
 *   |                   |
 *  (2)                 (5)
 *   |                   |
 *   3 ──────(8)──────── 4
 *
 * dijkstra(graph, src=0):
 *   dist = [0, 4, 7, 2, 12]
 *                 ↑
 *   Shortest 0→2 = 0→3 via edge? No.
 *   0→1=4, 1→2=4+1=5? Actually with above edges: 0→1=4, 1→2=5, 0→3=2, 3→4=10, 2→4=12
 * </pre>
 *
 * <p>Algorithm trace:</p>
 * <ol>
 *   <li>Initialise dist[src]=0, all others=∞</li>
 *   <li>Use a min-heap (priority queue) — always process the vertex with smallest dist</li>
 *   <li>For each neighbour v of u: if dist[u] + w(u,v) &lt; dist[v], relax the edge</li>
 *   <li>Repeat until queue is empty</li>
 * </ol>
 *
 * Time:  O((V + E) log V) with a binary heap
 * Space: O(V + E)
 *
 * <p><b>Limitation:</b> Does NOT work with negative edge weights.
 * Use {@link BellmanFord} for graphs with negative edges.</p>
 */
public class Dijkstra {

    /**
     * Result container: shortest distances + predecessor map for path reconstruction.
     */
    public static class Result {
        public final int[]   dist;  // dist[v] = shortest distance from src to v
        public final int[]   prev;  // prev[v] = predecessor of v on shortest path
        public final int     src;
        public final int     n;

        Result(int[] dist, int[] prev, int src) {
            this.dist = dist;
            this.prev = prev;
            this.src  = src;
            this.n    = dist.length;
        }

        /** Reconstructs the shortest path from src to dst. Returns empty list if unreachable. */
        public List<Integer> pathTo(int dst) {
            if (dist[dst] == Integer.MAX_VALUE) return Collections.emptyList();
            Deque<Integer> path = new ArrayDeque<>();
            for (int v = dst; v != src; v = prev[v]) {
                path.addFirst(v);
                if (v == prev[v]) return Collections.emptyList(); // cycle guard
            }
            path.addFirst(src);
            return new ArrayList<>(path);
        }
    }

    /**
     * Runs Dijkstra from {@code src} on the given adjacency list.
     *
     * @param graph adjacency list: graph[u] = list of int[]{v, weight}
     * @param src   source vertex (0-indexed)
     * @return Result with shortest distances and predecessor map
     */
    public static Result shortestPath(List<int[]>[] graph, int src) {
        int n = graph.length;
        int[] dist = new int[n];
        int[] prev = new int[n];
        Arrays.fill(dist, Integer.MAX_VALUE);
        Arrays.fill(prev, -1);
        dist[src] = 0;
        prev[src] = src;

        // Min-heap: int[]{distance, vertex}
        PriorityQueue<int[]> pq = new PriorityQueue<>(Comparator.comparingInt(e -> e[0]));
        pq.offer(new int[]{0, src});

        while (!pq.isEmpty()) {
            int[] cur  = pq.poll();
            int d      = cur[0];
            int u      = cur[1];

            // Skip stale entries (a shorter path to u was already found)
            if (d > dist[u]) continue;

            for (int[] edge : graph[u]) {
                int v      = edge[0];
                int weight = edge[1];
                long newDist = (long) dist[u] + weight; // use long to avoid int overflow

                if (newDist < dist[v]) {
                    dist[v] = (int) newDist;
                    prev[v] = u;
                    pq.offer(new int[]{dist[v], v});
                }
            }
        }
        return new Result(dist, prev, src);
    }

    /**
     * LC #743 — Network Delay Time.
     *
     * <p>Given a network of n nodes, times[i] = [ui, vi, wi] (directed edge u→v
     * with travel time w), return the time it takes for all nodes to receive a
     * signal sent from node {@code k}. Return -1 if not all nodes are reached.</p>
     *
     * Time: O((V+E) log V)
     */
    @SuppressWarnings("unchecked")
    public static int networkDelayTime(int[][] times, int n, int k) {
        List<int[]>[] graph = new List[n + 1];
        for (int i = 1; i <= n; i++) graph[i] = new ArrayList<>();
        for (int[] t : times) graph[t[0]].add(new int[]{t[1], t[2]});

        Result result = shortestPath(graph, k);

        int maxDist = 0;
        for (int i = 1; i <= n; i++) {
            if (result.dist[i] == Integer.MAX_VALUE) return -1;
            maxDist = Math.max(maxDist, result.dist[i]);
        }
        return maxDist;
    }

    /**
     * LC #787 — Cheapest Flights Within K Stops.
     *
     * <p>Modified Dijkstra (or use Bellman-Ford) — state is (cost, node, stopsLeft).
     * Unlike classic Dijkstra, we cannot skip a node just because we've visited it
     * with a lower cost — the number of stops also matters.</p>
     *
     * Time: O(E * K * log(E * K))
     */
    @SuppressWarnings("unchecked")
    public static int findCheapestPrice(int n, int[][] flights, int src, int dst, int k) {
        List<int[]>[] graph = new List[n];
        for (int i = 0; i < n; i++) graph[i] = new ArrayList<>();
        for (int[] f : flights) graph[f[0]].add(new int[]{f[1], f[2]});

        // dist[node][stops] = min cost to reach node using exactly `stops` stops
        int[][] dist = new int[n][k + 2];
        for (int[] row : dist) Arrays.fill(row, Integer.MAX_VALUE);
        dist[src][0] = 0;

        // PQ: {cost, node, stops used}
        PriorityQueue<int[]> pq = new PriorityQueue<>(Comparator.comparingInt(e -> e[0]));
        pq.offer(new int[]{0, src, 0});

        while (!pq.isEmpty()) {
            int[] cur    = pq.poll();
            int cost     = cur[0], u = cur[1], stops = cur[2];
            if (u == dst) return cost;
            if (stops > k) continue;

            for (int[] edge : graph[u]) {
                int v = edge[0], w = edge[1];
                int newCost = cost + w;
                if (newCost < dist[v][stops + 1]) {
                    dist[v][stops + 1] = newCost;
                    pq.offer(new int[]{newCost, v, stops + 1});
                }
            }
        }
        return -1;
    }
}
