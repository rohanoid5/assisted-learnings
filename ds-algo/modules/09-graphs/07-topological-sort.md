# 9.7 — Topological Sort

## What Is Topological Sort?

**Topological sort** orders the nodes of a **Directed Acyclic Graph (DAG)** such that for every directed edge `u → v`, node `u` comes before node `v` in the ordering.

```
Course prerequisites: 
  Course 0 ← Course 1
  Course 1 ← Course 2
  Course 2 ← Course 3

Topological order: [3, 2, 1, 0]
(Must take 3 before 2, 2 before 1, 1 before 0)
```

**Topological sort only exists for DAGs** — graphs with cycles have no valid ordering.

---

## Algorithm 1: Kahn's Algorithm (BFS-based)

**Idea:** Repeatedly remove nodes with in-degree 0 (no prerequisites), as they can be completed first.

```java
public int[] topoSortKahn(List<List<Integer>> adj, int n) {
    int[] indegree = new int[n];
    for (int u = 0; u < n; u++)
        for (int v : adj.get(u)) indegree[v]++;

    Queue<Integer> queue = new LinkedList<>();
    for (int i = 0; i < n; i++)
        if (indegree[i] == 0) queue.offer(i); // nodes with no prerequisites

    int[] order = new int[n];
    int idx = 0;

    while (!queue.isEmpty()) {
        int node = queue.poll();
        order[idx++] = node;

        for (int neighbor : adj.get(node)) {
            indegree[neighbor]--;
            if (indegree[neighbor] == 0) queue.offer(neighbor);
        }
    }

    if (idx != n) return new int[0]; // cycle exists — no valid toposort
    return order;
}
```

---

## Kahn's Trace

```
DAG:  5 → 2 → 3
      5 → 0
      4 → 0
      4 → 1
      2 → 3
      3 → 1

In-degrees: 0→2, 1→2, 2→1, 3→1, 4→0, 5→0

Start queue: [4, 5] (in-degree 0)

Process 4: reduce in-degree of 0,1 → indegree: 0→1, 1→1
Process 5: reduce in-degree of 2,0 → indegree: 0→0, 2→0
           queue adds 0, 2
Process 0: no outgoing edges
Process 2: reduce in-degree of 3 → indegree: 3→0; add 3
Process 3: reduce in-degree of 1 → indegree: 1→0; add 1
Process 1: done

Order: [4, 5, 0, 2, 3, 1]  ✓
```

---

## Algorithm 2: DFS-based Topological Sort

After DFS finishes a node (all descendants processed), add it to the **front** of the result list:

```java
public List<Integer> topoSortDFS(List<List<Integer>> adj, int n) {
    boolean[] visited = new boolean[n];
    LinkedList<Integer> result = new LinkedList<>();
    // Stack of: mark visited + recurse → finished → addFirst

    for (int i = 0; i < n; i++) {
        if (!visited[i]) dfsTopoSort(adj, i, visited, result);
    }
    return result;
}

private void dfsTopoSort(List<List<Integer>> adj, int node,
                          boolean[] visited, LinkedList<Integer> result) {
    visited[node] = true;
    for (int neighbor : adj.get(node)) {
        if (!visited[neighbor]) dfsTopoSort(adj, neighbor, visited, result);
    }
    result.addFirst(node); // add AFTER all descendants are processed
}
```

---

## Course Schedule (LC #207)

Can you finish all courses given prerequisites? = Is the directed graph a DAG (acyclic)?

```
numCourses = 4
prerequisites = [[1,0],[2,0],[3,1],[3,2]]
→ Must take 0 before 1 and 2; must take 1,2 before 3.
→ Valid order: 0,1,2,3  → true
```

```java
public boolean canFinish(int numCourses, int[][] prerequisites) {
    List<List<Integer>> adj = new ArrayList<>();
    for (int i = 0; i < numCourses; i++) adj.add(new ArrayList<>());
    int[] indegree = new int[numCourses];

    for (int[] pre : prerequisites) {
        adj.get(pre[1]).add(pre[0]); // pre[1] must come before pre[0]
        indegree[pre[0]]++;
    }

    Queue<Integer> queue = new LinkedList<>();
    for (int i = 0; i < numCourses; i++)
        if (indegree[i] == 0) queue.offer(i);

    int processed = 0;
    while (!queue.isEmpty()) {
        int course = queue.poll();
        processed++;
        for (int next : adj.get(course)) {
            if (--indegree[next] == 0) queue.offer(next);
        }
    }
    return processed == numCourses; // false if cycle exists
}
```

---

## Course Schedule II (LC #210)

Return the actual ordering (not just whether one exists):

```java
public int[] findOrder(int numCourses, int[][] prerequisites) {
    List<List<Integer>> adj = new ArrayList<>();
    int[] indegree = new int[numCourses];
    for (int i = 0; i < numCourses; i++) adj.add(new ArrayList<>());
    for (int[] pre : prerequisites) {
        adj.get(pre[1]).add(pre[0]);
        indegree[pre[0]]++;
    }

    Queue<Integer> queue = new LinkedList<>();
    for (int i = 0; i < numCourses; i++)
        if (indegree[i] == 0) queue.offer(i);

    int[] order = new int[numCourses];
    int idx = 0;
    while (!queue.isEmpty()) {
        int course = queue.poll();
        order[idx++] = course;
        for (int next : adj.get(course))
            if (--indegree[next] == 0) queue.offer(next);
    }
    return (idx == numCourses) ? order : new int[0];
}
```

---

## Alien Dictionary (LC #269)

Given a sorted list of alien words, find the alien alphabet order.

```
Words: ["wrt", "wrf", "er", "ett", "rftt"]
Compare adjacent: "wrt" vs "wrf" → t < f
                  "wrf" vs "er"  → w < e
                  "er"  vs "ett" → r < t
                  "ett" vs "rftt" → e < r
DAG: t→f, w→e, r→t, e→r
Toposort: w, e, r, t, f  (one valid answer)
```

```java
public String alienOrder(String[] words) {
    Map<Character, Set<Character>> adj = new HashMap<>();
    Map<Character, Integer> indegree = new HashMap<>();

    // Initialize all characters
    for (String word : words)
        for (char c : word.toCharArray()) {
            adj.putIfAbsent(c, new HashSet<>());
            indegree.putIfAbsent(c, 0);
        }

    // Build edges by comparing adjacent words
    for (int i = 0; i < words.length - 1; i++) {
        String w1 = words[i], w2 = words[i+1];
        int len = Math.min(w1.length(), w2.length());
        if (w1.length() > w2.length() && w1.startsWith(w2)) return ""; // invalid
        for (int j = 0; j < len; j++) {
            if (w1.charAt(j) != w2.charAt(j)) {
                if (!adj.get(w1.charAt(j)).contains(w2.charAt(j))) {
                    adj.get(w1.charAt(j)).add(w2.charAt(j));
                    indegree.merge(w2.charAt(j), 1, Integer::sum);
                }
                break;
            }
        }
    }

    // Kahn's toposort
    Queue<Character> queue = new LinkedList<>();
    for (char c : indegree.keySet()) if (indegree.get(c) == 0) queue.offer(c);
    StringBuilder sb = new StringBuilder();
    while (!queue.isEmpty()) {
        char c = queue.poll();
        sb.append(c);
        for (char next : adj.get(c)) {
            indegree.merge(next, -1, Integer::sum);
            if (indegree.get(next) == 0) queue.offer(next);
        }
    }
    return sb.length() == indegree.size() ? sb.toString() : "";
}
```

---

## Try It Yourself

**Problem:** Given a list of tasks and dependencies `prerequisites[i] = [task, prerequisite]`, find the minimum number of semesters needed to complete all courses if you can take any number of courses per semester with no dependencies yet. (LC #1136)

<details>
<summary>Solution</summary>

```java
public int minimumSemesters(int n, int[][] relations) {
    List<List<Integer>> adj = new ArrayList<>();
    int[] indegree = new int[n + 1];
    for (int i = 0; i <= n; i++) adj.add(new ArrayList<>());
    for (int[] r : relations) {
        adj.get(r[0]).add(r[1]);
        indegree[r[1]]++;
    }

    Queue<Integer> queue = new LinkedList<>();
    for (int i = 1; i <= n; i++) if (indegree[i] == 0) queue.offer(i);

    int semesters = 0, completed = 0;
    while (!queue.isEmpty()) {
        semesters++;  // take all available courses this semester
        int size = queue.size();
        completed += size;
        for (int i = 0; i < size; i++) {
            int course = queue.poll();
            for (int next : adj.get(course))
                if (--indegree[next] == 0) queue.offer(next);
        }
    }
    return completed == n ? semesters : -1;
}
```

**Key:** Level-by-level BFS (like level-order traversal) — each level = one semester. All nodes available that semester taken simultaneously.

</details>

---

## Capstone Connection

Add to AlgoForge `datastructures/graphs/TopologicalSort.java`:
- `kahnSort()` — BFS-based, returns order or empty if cycle
- `dfsSort()` — DFS-based
- `hasCycle()` — detects cycle by checking if toposort failed
