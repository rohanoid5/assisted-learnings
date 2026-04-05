# Module 09 — Graphs

## Overview

Graphs are the most general data structure — trees are just graphs without cycles. Nearly every "network" problem (social graphs, routing, dependencies, maps) becomes a graph problem at its core. This module covers graph representation, traversal, shortest paths, minimum spanning trees, topological sort, and advanced algorithms seen in top-tier interviews.

---

## Learning Objectives

By the end of this module you will be able to:

- [ ] Represent graphs as adjacency lists and adjacency matrices
- [ ] Implement BFS and DFS for graphs (including cycle detection)
- [ ] Find shortest path in unweighted graphs (BFS) and weighted (Dijkstra)
- [ ] Handle negative weights with Bellman-Ford
- [ ] Find minimum spanning trees with Prim's and Kruskal's
- [ ] Perform topological sort using Kahn's algorithm and DFS
- [ ] Detect cycles in directed and undirected graphs
- [ ] Identify connected components and strongly connected components

---

## Topics

| # | File | Concept |
|---|------|---------|
| 1 | [01-graph-fundamentals.md](01-graph-fundamentals.md) | Directed/undirected, representations, terminology |
| 2 | [02-graph-bfs.md](02-graph-bfs.md) | BFS, shortest path unweighted, bipartite check |
| 3 | [03-graph-dfs.md](03-graph-dfs.md) | DFS, connected components, cycle detection |
| 4 | [04-dijkstra.md](04-dijkstra.md) | Dijkstra's algorithm, single-source shortest path |
| 5 | [05-bellman-ford.md](05-bellman-ford.md) | Bellman-Ford, negative weights, negative cycles |
| 6 | [06-minimum-spanning-tree.md](06-minimum-spanning-tree.md) | Prim's and Kruskal's algorithms |
| 7 | [07-topological-sort.md](07-topological-sort.md) | Kahn's BFS algorithm, DFS-based toposort |
| 8 | [08-advanced-graphs.md](08-advanced-graphs.md) | Union-Find (preview), A*, bridges, SCC |
| Exercises | [Exercises](exercises/README.md) | Number of Islands, Course Schedule, Clone Graph |

---

## Estimated Time

**7–8 hours** (including exercises)

---

## Prerequisites

- Module 04: queues (BFS uses a queue)
- Module 08: trees (trees are a special case of graphs)

---

## Capstone Milestone

By the end of this module, add to AlgoForge:
- `datastructures/graphs/Graph.java` — adjacency list representation
- `datastructures/graphs/GraphBFS.java`
- `datastructures/graphs/GraphDFS.java`
- `datastructures/graphs/Dijkstra.java`
- `problems/graphs/` — all exercise solutions
