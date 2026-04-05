package com.algoforge.problems.graphs;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.PriorityQueue;

/**
 * LC #743 — Network Delay Time
 *
 * <p>There are n nodes in a network labeled 1..n. Given a list of travel times
 * as directed edges [u, v, w], and a starting node k, return the minimum time
 * it takes for all nodes to receive the signal. Return -1 if not possible.</p>
 *
 * <b>Pattern:</b> Dijkstra's shortest path from source k.
 *
 * <pre>
 * Answer = max(shortest paths from k to all nodes).
 * If any node is unreachable, return -1.
 *
 * Trace: times=[[2,1,1],[2,3,1],[3,4,1]], n=4, k=2
 *   Start at 2 (dist=0). Process neighbors: 1 (dist=1), 3 (dist=1)
 *   Process 1 (no outgoing). Process 3 (4 at dist=2)
 *   Process 4. All nodes reached: max dist = 2 → return 2
 * </pre>
 *
 * Time: O((V+E) log V)  Space: O(V+E)
 */
public class NetworkDelayTime {

    public static int networkDelayTime(int[][] times, int n, int k) {
        List<int[]>[] adj = new List[n + 1];
        for (int i = 1; i <= n; i++) adj[i] = new ArrayList<>();
        for (int[] t : times) adj[t[0]].add(new int[]{t[1], t[2]});

        int[] dist = new int[n + 1];
        Arrays.fill(dist, Integer.MAX_VALUE);
        dist[k] = 0;

        PriorityQueue<int[]> pq = new PriorityQueue<>((a, b) -> a[1] - b[1]); // [node, dist]
        pq.offer(new int[]{k, 0});

        while (!pq.isEmpty()) {
            int[] curr = pq.poll();
            int u = curr[0], d = curr[1];
            if (d > dist[u]) continue; // stale entry
            for (int[] edge : adj[u]) {
                int v = edge[0], w = edge[1];
                if (dist[u] + w < dist[v]) {
                    dist[v] = dist[u] + w;
                    pq.offer(new int[]{v, dist[v]});
                }
            }
        }

        int ans = 0;
        for (int i = 1; i <= n; i++) {
            if (dist[i] == Integer.MAX_VALUE) return -1;
            ans = Math.max(ans, dist[i]);
        }
        return ans;
    }
}
