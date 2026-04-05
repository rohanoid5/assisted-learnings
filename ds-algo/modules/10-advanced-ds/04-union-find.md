# 10.4 — Union-Find (Disjoint Set Union)

## What Is Union-Find?

**Union-Find** (or Disjoint Set Union, DSU) maintains a collection of disjoint sets and supports two operations:

- `find(x)` — returns the **representative** (root) of the set containing x
- `union(x, y)` — **merges** the sets containing x and y

```
Initial: {0}, {1}, {2}, {3}, {4}

union(0, 1):  {0, 1}, {2}, {3}, {4}
union(1, 2):  {0, 1, 2}, {3}, {4}
union(3, 4):  {0, 1, 2}, {3, 4}

find(0) == find(2)?  YES → same component
find(0) == find(3)?  NO  → different components
```

---

## Naive Implementation

```java
int[] parent = new int[n];
for (int i = 0; i < n; i++) parent[i] = i; // each node is its own root

int find(int x) {
    while (parent[x] != x) x = parent[x];
    return x;
}

void union(int x, int y) {
    parent[find(x)] = find(y);
}
```

**Problem:** Without optimization, a tree can degenerate into a linked list → O(n) per operation.

---

## Optimization 1: Path Compression

During `find`, flatten the tree — make every node point directly to the root.

```
Before find(5):       After find(5) with path compression:
    0                          0
    |                        ╔═╪═╗
    1                        1 2 5
    |
    2
    |
    5

find(5) traverses: 5→2→1→0 (root)
Path compression: set parent[5]=0, parent[2]=0, parent[1]=0
```

```java
int find(int x) {
    if (parent[x] != x) {
        parent[x] = find(parent[x]); // compress: point directly to root
    }
    return parent[x];
}
```

---

## Optimization 2: Union by Rank

Always attach the smaller tree under the root of the larger tree, keeping the tree flat.

```java
int[] parent, rank;

void union(int x, int y) {
    int px = find(x), py = find(y);
    if (px == py) return;

    if (rank[px] < rank[py]) {
        int tmp = px; px = py; py = tmp; // swap so px has higher rank
    }
    parent[py] = px; // attach smaller to larger
    if (rank[px] == rank[py]) rank[px]++;
}
```

---

## Full Implementation

```java
public class UnionFind {
    private final int[] parent;
    private final int[] rank;
    private int components;

    public UnionFind(int n) {
        parent = new int[n];
        rank   = new int[n];
        components = n;
        for (int i = 0; i < n; i++) parent[i] = i;
    }

    // Returns root with path compression — nearly O(1) amortized
    public int find(int x) {
        if (parent[x] != x) parent[x] = find(parent[x]);
        return parent[x];
    }

    // Returns true if they were in different components (merge happened)
    public boolean union(int x, int y) {
        int px = find(x), py = find(y);
        if (px == py) return false;

        if (rank[px] < rank[py]) { int tmp = px; px = py; py = tmp; }
        parent[py] = px;
        if (rank[px] == rank[py]) rank[px]++;
        components--;
        return true;
    }

    public boolean connected(int x, int y) {
        return find(x) == find(y);
    }

    public int components() { return components; }
}
```

**Complexity:** O(α(n)) per operation amortized — α is the inverse Ackermann function, effectively constant (α(n) ≤ 4 for any n in practice).

---

## Applications

### 1. Number of Connected Components (LC #323)

```java
public int countComponents(int n, int[][] edges) {
    UnionFind uf = new UnionFind(n);
    for (int[] e : edges) uf.union(e[0], e[1]);
    return uf.components();
}
```

### 2. Redundant Connection (LC #684)

```java
public int[] findRedundantConnection(int[][] edges) {
    UnionFind uf = new UnionFind(edges.length + 1);
    for (int[] e : edges) {
        if (!uf.union(e[0], e[1])) return e; // already connected → redundant
    }
    return new int[0];
}
```

### 3. Accounts Merge (LC #721)

Group emails that belong to the same person (by common emails):

```java
public List<List<String>> accountsMerge(List<List<String>> accounts) {
    Map<String, Integer> emailToId = new HashMap<>();
    int id = 0;

    // Assign each unique email an id
    for (List<String> acc : accounts) {
        for (int i = 1; i < acc.size(); i++) {
            emailToId.computeIfAbsent(acc.get(i), k -> emailToId.size());
        }
    }

    UnionFind uf = new UnionFind(emailToId.size());

    // Union all emails in the same account
    for (List<String> acc : accounts) {
        int first = emailToId.get(acc.get(1));
        for (int i = 2; i < acc.size(); i++) {
            uf.union(first, emailToId.get(acc.get(i)));
        }
    }

    // Group emails by root representative
    Map<Integer, List<String>> groups = new HashMap<>();
    for (Map.Entry<String, Integer> e : emailToId.entrySet()) {
        int root = uf.find(e.getValue());
        groups.computeIfAbsent(root, k -> new ArrayList<>()).add(e.getKey());
    }

    // Get the account name for each group
    Map<Integer, String> rootToName = new HashMap<>();
    for (List<String> acc : accounts) {
        int root = uf.find(emailToId.get(acc.get(1)));
        rootToName.putIfAbsent(root, acc.get(0));
    }

    List<List<String>> result = new ArrayList<>();
    for (Map.Entry<Integer, List<String>> e : groups.entrySet()) {
        List<String> group = e.getValue();
        Collections.sort(group);
        group.add(0, rootToName.get(e.getKey())); // prepend name
        result.add(group);
    }
    return result;
}
```

### 4. Making Network Connected (LC #1319)

```java
public int makeConnected(int n, int[][] connections) {
    if (connections.length < n - 1) return -1; // not enough cables

    UnionFind uf = new UnionFind(n);
    int redundant = 0;
    for (int[] c : connections) {
        if (!uf.union(c[0], c[1])) redundant++;
    }
    // Need (components - 1) cables to connect all components
    return uf.components() - 1;
}
```

---

## Visualization of Path Compression

```
Initial state after unions (0-1-2-3 chain):
  parent = [0, 0, 1, 2]
  Tree:  0 ← 1 ← 2 ← 3

find(3):
  parent[3]=2, parent[2]=1, parent[1]=0, parent[0]=0 → root=0
  Path compress: parent[3]=0, parent[2]=0, parent[1]=0

After find(3):
  parent = [0, 0, 0, 0]
  Tree:  0 ← 1
            ← 2
            ← 3
  (flat! future finds are O(1))
```

---

## Try It Yourself

**Problem:** You have `n` computers and a list of `connections` (direct cable links). Return the minimum number of cables you need to rearrange to make all computers connected. Return -1 if impossible. (LC #1319)

<details>
<summary>Solution</summary>

```java
public int makeConnected(int n, int[][] connections) {
    if (connections.length < n - 1) return -1; // definitely impossible

    UnionFind uf = new UnionFind(n);
    for (int[] c : connections) uf.union(c[0], c[1]);

    // We need (components - 1) cable moves to connect all components into one
    return uf.components() - 1;
}
```

**Key:** If we have ≥ n−1 connections total but k connected components, we have enough spare cables (at least k−1 redundant cables exist across components) to connect all k components with k−1 cables.

</details>

---

## Capstone Connection

Add `datastructures/advanced/UnionFind.java` to AlgoForge. This is also used in graph algorithms (Kruskal's, cycle detection). Add problems:
- `problems/advanced/AccountsMerge.java` (LC #721)
- `problems/advanced/RedundantConnection.java` (LC #684)
