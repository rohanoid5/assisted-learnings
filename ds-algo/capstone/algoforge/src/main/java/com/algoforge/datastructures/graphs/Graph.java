package com.algoforge.datastructures.graphs;

import java.util.*;

/**
 * Graph<T> — Module 09 capstone deliverable.
 *
 * Adjacency list graph supporting both directed and undirected,
 * and both weighted and unweighted edges.
 *
 * Complexity:
 *   addVertex / addEdge   O(1)
 *   BFS / DFS             O(V + E)
 *   neighbors of v        O(degree(v))
 */
public class Graph<T> {

    public static class Edge<T> {
        public final T to;
        public final int weight;
        public Edge(T to, int weight) { this.to = to; this.weight = weight; }
    }

    private final Map<T, List<Edge<T>>> adjList = new HashMap<>();
    private final boolean directed;

    public Graph(boolean directed) {
        this.directed = directed;
    }

    public void addVertex(T v) {
        adjList.putIfAbsent(v, new ArrayList<>());
    }

    public void addEdge(T from, T to) {
        addEdge(from, to, 1);   // unweighted = weight 1
    }

    public void addEdge(T from, T to, int weight) {
        adjList.computeIfAbsent(from, k -> new ArrayList<>()).add(new Edge<>(to, weight));
        adjList.computeIfAbsent(to,   k -> new ArrayList<>());   // ensure to exists
        if (!directed) {
            adjList.get(to).add(new Edge<>(from, weight));
        }
    }

    public List<Edge<T>> neighbors(T v) {
        return adjList.getOrDefault(v, Collections.emptyList());
    }

    public Set<T> vertices() {
        return adjList.keySet();
    }

    // ---------------------------------------------------------------
    // BFS — returns visit order starting from `start`. O(V + E)
    public List<T> bfs(T start) {
        List<T> order = new ArrayList<>();
        Set<T> visited = new HashSet<>();
        Queue<T> queue = new LinkedList<>();
        queue.offer(start);
        visited.add(start);
        while (!queue.isEmpty()) {
            T v = queue.poll();
            order.add(v);
            for (Edge<T> e : neighbors(v)) {
                if (!visited.contains(e.to)) {
                    visited.add(e.to);
                    queue.offer(e.to);
                }
            }
        }
        return order;
    }

    // DFS — returns visit order starting from `start`. O(V + E)
    public List<T> dfs(T start) {
        List<T> order = new ArrayList<>();
        Set<T> visited = new HashSet<>();
        dfsRec(start, visited, order);
        return order;
    }

    private void dfsRec(T v, Set<T> visited, List<T> order) {
        visited.add(v);
        order.add(v);
        for (Edge<T> e : neighbors(v)) {
            if (!visited.contains(e.to)) dfsRec(e.to, visited, order);
        }
    }

    // Topological sort (Kahn's algorithm — BFS based). O(V + E)
    // Only valid for directed acyclic graphs (DAGs).
    public List<T> topologicalSort() {
        Map<T, Integer> inDegree = new HashMap<>();
        for (T v : adjList.keySet()) inDegree.put(v, 0);
        for (T v : adjList.keySet())
            for (Edge<T> e : adjList.get(v))
                inDegree.merge(e.to, 1, Integer::sum);

        Queue<T> queue = new LinkedList<>();
        for (T v : inDegree.keySet())
            if (inDegree.get(v) == 0) queue.offer(v);

        List<T> order = new ArrayList<>();
        while (!queue.isEmpty()) {
            T v = queue.poll();
            order.add(v);
            for (Edge<T> e : neighbors(v)) {
                int deg = inDegree.merge(e.to, -1, Integer::sum);
                if (deg == 0) queue.offer(e.to);
            }
        }
        return order;
    }
}
