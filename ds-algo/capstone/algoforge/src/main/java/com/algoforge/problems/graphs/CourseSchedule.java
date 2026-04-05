package com.algoforge.problems.graphs;

import java.util.ArrayList;
import java.util.List;

/**
 * LC #207 — Course Schedule
 *
 * <p>Given numCourses and a list of prerequisite pairs [a, b] (to take a, must take b first),
 * return true if you can finish all courses (i.e., no cycle exists).</p>
 *
 * <b>Pattern:</b> Directed graph cycle detection via DFS with 3-state coloring.
 *
 * <pre>
 * Node states:
 *   0 = unvisited
 *   1 = in current DFS path (gray) — if we revisit a gray node, cycle detected!
 *   2 = fully processed (black) — safe
 *
 * Trace: numCourses=2, prerequisites=[[1,0],[0,1]]
 *   edges: 1→0, 0→1 (cycle!)
 *   DFS from 0: visit 0 (gray), visit 1 (gray), back to 0 which is gray → cycle → return false
 *
 * Trace: prerequisites=[[1,0]]
 *   edge: 1→0
 *   DFS from 0: visit 0, visit 1 (no outgoing neighbors), mark both black → no cycle → true
 * </pre>
 *
 * Time: O(V+E)  Space: O(V+E)
 */
public class CourseSchedule {

    public static boolean canFinish(int numCourses, int[][] prerequisites) {
        List<List<Integer>> adj = new ArrayList<>();
        for (int i = 0; i < numCourses; i++) adj.add(new ArrayList<>());
        for (int[] pre : prerequisites) adj.get(pre[0]).add(pre[1]);

        int[] state = new int[numCourses]; // 0=unvisited, 1=in-path, 2=done
        for (int i = 0; i < numCourses; i++)
            if (state[i] == 0 && hasCycle(i, adj, state)) return false;
        return true;
    }

    private static boolean hasCycle(int node, List<List<Integer>> adj, int[] state) {
        state[node] = 1; // mark in-path
        for (int neighbor : adj.get(node)) {
            if (state[neighbor] == 1) return true;  // back edge → cycle
            if (state[neighbor] == 0 && hasCycle(neighbor, adj, state)) return true;
        }
        state[node] = 2; // fully processed
        return false;
    }
}
