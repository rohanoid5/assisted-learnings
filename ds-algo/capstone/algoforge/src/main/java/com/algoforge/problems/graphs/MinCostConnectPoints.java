package com.algoforge.problems.graphs;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.PriorityQueue;

/**
 * LC #1584 — Min Cost to Connect All Points
 *
 * <p>Given an array of points where points[i] = [xi, yi], return the minimum cost
 * to connect all points. The cost of connecting two points is their Manhattan distance.
 * All points must be connected (minimum spanning tree).</p>
 *
 * <b>Pattern:</b> Prim's MST algorithm — greedily pick the cheapest edge to an unvisited node.
 *
 * <pre>
 * Points = [[0,0],[2,2],[3,10],[5,2],[7,0]]
 *
 * Start at point 0. Add its neighbors to the min-heap by Manhattan distance.
 * Always pick the cheapest edge to an unvisited node.
 * Total MST cost = 20
 * </pre>
 *
 * Time: O(n^2 log n)  Space: O(n^2)
 */
public class MinCostConnectPoints {

    public static int minCostConnectPoints(int[][] points) {
        int n = points.length;
        boolean[] visited = new boolean[n];
        PriorityQueue<int[]> pq = new PriorityQueue<>((a, b) -> a[0] - b[0]); // [cost, point]
        pq.offer(new int[]{0, 0}); // start at point 0 with cost 0
        int totalCost = 0;
        int edgesUsed = 0;

        while (edgesUsed < n) {
            int[] curr = pq.poll();
            int cost = curr[0], u = curr[1];
            if (visited[u]) continue;
            visited[u] = true;
            totalCost += cost;
            edgesUsed++;
            for (int v = 0; v < n; v++) {
                if (!visited[v]) {
                    int dist = Math.abs(points[u][0] - points[v][0])
                             + Math.abs(points[u][1] - points[v][1]);
                    pq.offer(new int[]{dist, v});
                }
            }
        }
        return totalCost;
    }
}
