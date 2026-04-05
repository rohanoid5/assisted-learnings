package com.algoforge.datastructures.graphs;

import java.util.Arrays;

/**
 * Bellman-Ford Algorithm — finds shortest paths from a source vertex, even
 * when the graph contains <em>negative</em> edge weights.
 *
 * <pre>
 * Key difference vs Dijkstra:
 *
 *   Dijkstra: greedy, O((V+E) log V), NO negative edges
 *   Bellman-Ford: DP/relaxation, O(VE), handles negative edges + detects
 *                 negative-weight cycles (where shortest path = -∞)
 * </pre>
 *
 * <p>Algorithm:</p>
 * <ol>
 *   <li>Initialise dist[src]=0, all others=∞</li>
 *   <li>Relax ALL edges V-1 times: dist[v] = min(dist[v], dist[u] + w)</li>
 *   <li>On the Vth pass, if any edge still relaxes → negative cycle detected</li>
 * </ol>
 *
 * <pre>
 * Why V-1 iterations?
 * A shortest path in a graph of V vertices has at most V-1 edges.
 * After iteration i, we know the shortest paths using at most i edges.
 * After V-1 iterations, all shortest paths are finalised.
 * </pre>
 *
 * Time:  O(V * E)
 * Space: O(V)
 */
public class BellmanFord {

    /** Sentinel value for "unreachable". */
    public static final int INF = Integer.MAX_VALUE / 2;

    /**
     * Result: shortest distances and predecessor map.
     */
    public static class Result {
        public final int[]   dist;
        public final int[]   prev;
        public final boolean hasNegativeCycle;

        Result(int[] dist, int[] prev, boolean hasNegativeCycle) {
            this.dist             = dist;
            this.prev             = prev;
            this.hasNegativeCycle = hasNegativeCycle;
        }
    }

    /**
     * Runs Bellman-Ford from {@code src}.
     *
     * @param n     number of vertices (0-indexed: 0..n-1)
     * @param edges array of int[]{u, v, weight}
     * @param src   source vertex
     * @return Result with distances, predecessors, and negative-cycle flag
     */
    public static Result shortestPath(int n, int[][] edges, int src) {
        int[] dist = new int[n];
        int[] prev = new int[n];
        Arrays.fill(dist, INF);
        Arrays.fill(prev, -1);
        dist[src] = 0;

        // V-1 relaxation passes
        for (int i = 0; i < n - 1; i++) {
            boolean updated = false;
            for (int[] edge : edges) {
                int u = edge[0], v = edge[1], w = edge[2];
                if (dist[u] != INF && dist[u] + w < dist[v]) {
                    dist[v] = dist[u] + w;
                    prev[v] = u;
                    updated = true;
                }
            }
            if (!updated) break; // early exit: no further relaxation possible
        }

        // Vth pass: detect negative cycles
        boolean hasNegativeCycle = false;
        for (int[] edge : edges) {
            int u = edge[0], v = edge[1], w = edge[2];
            if (dist[u] != INF && dist[u] + w < dist[v]) {
                hasNegativeCycle = true;
                break;
            }
        }

        return new Result(dist, prev, hasNegativeCycle);
    }

    // ── SPFA (Shortest Path Faster Algorithm) ────────────────────────────────

    /**
     * SPFA — a queue-based optimisation of Bellman-Ford.
     * Only re-relaxes edges from vertices whose distance was recently updated.
     *
     * <p>Average case: O(E), worst case still O(VE).
     * Practical speedup: 3–5x over classic Bellman-Ford on sparse graphs.</p>
     *
     * @param graph adjacency list: graph[u] = int[][]{v, weight}
     * @param src   source vertex
     * @return int[] of shortest distances (INF if unreachable)
     */
    public static int[] spfa(java.util.List<int[]>[] graph, int src) {
        int n = graph.length;
        int[] dist    = new int[n];
        boolean[] inQueue = new boolean[n];
        Arrays.fill(dist, INF);
        dist[src] = 0;

        java.util.Deque<Integer> queue = new java.util.ArrayDeque<>();
        queue.offer(src);
        inQueue[src] = true;

        while (!queue.isEmpty()) {
            int u = queue.poll();
            inQueue[u] = false;

            for (int[] edge : graph[u]) {
                int v = edge[0], w = edge[1];
                if (dist[u] + w < dist[v]) {
                    dist[v] = dist[u] + w;
                    if (!inQueue[v]) {
                        queue.offer(v);
                        inQueue[v] = true;
                    }
                }
            }
        }
        return dist;
    }

    // ── LC Pattern: LC #743 variant with negative weights ───────────────────

    /**
     * Finds shortest distances from {@code src} using edge list format.
     * Returns INF for unreachable vertices, throws if a negative cycle is detected.
     *
     * Matches the standard LC edge-list problem format.
     */
    public static int[] solve(int n, int[][] edges, int src) {
        Result result = shortestPath(n, edges, src);
        if (result.hasNegativeCycle)
            throw new IllegalStateException("Graph contains a negative-weight cycle");
        return result.dist;
    }
}
