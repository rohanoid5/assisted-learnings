# 11.6 — Bitmask DP

## What Is Bitmask DP?

Bitmask DP represents the "which items have been included" state as a bitmask integer. Suitable when n ≤ 20 (2^20 = ~1 million states).

```
n = 4 items, bitmask = 0101 (binary)
               → items 0 and 2 are included (bit 0 and bit 2 are set)

Operations:
  Check if item i is in mask:  (mask >> i) & 1  == 1
  Add item i to mask:          mask | (1 << i)
  Remove item i from mask:     mask & ~(1 << i)
  Is mask full (all n items)?  mask == (1 << n) - 1
```

## Problem 1: Traveling Salesman Problem (TSP/Hamiltonian Path)

Find the shortest path visiting all nodes exactly once and returning to start.

```
Graph (4 nodes):
  0 ─3─ 1
  │     │
  6     4
  │     │
  2 ─2─ 3

dist matrix:
    0  1  2  3
  0[0  3  6  ∞]
  1[3  0  ∞  4]
  2[6  ∞  0  2]
  3[∞  4  2  0]

Optimal: 0→1→3→2→0 = 3+4+2+6 = 15
```

**State:** `dp[mask][i]` = min cost to visit all nodes in `mask`, ending at node i

**Recurrence:**
```
For each node j not in mask:
  dp[mask | (1<<j)][j] = min(dp[mask | (1<<j)][j], dp[mask][i] + dist[i][j])
```

```java
int tsp(int[][] dist) {
    int n = dist.length;
    int FULL = (1 << n) - 1;
    int INF = Integer.MAX_VALUE / 2;
    int[][] dp = new int[1 << n][n];

    for (int[] row : dp) Arrays.fill(row, INF);
    dp[1][0] = 0; // start at node 0, only node 0 visited (mask=0001)

    for (int mask = 1; mask <= FULL; mask++) {
        for (int u = 0; u < n; u++) {
            if ((mask & (1 << u)) == 0) continue; // u not in mask
            if (dp[mask][u] == INF) continue;

            for (int v = 0; v < n; v++) {
                if ((mask & (1 << v)) != 0) continue; // v already visited
                if (dist[u][v] == INF) continue;

                int newMask = mask | (1 << v);
                dp[newMask][v] = Math.min(dp[newMask][v], dp[mask][u] + dist[u][v]);
            }
        }
    }

    // Find min cost to return to node 0 after visiting all
    int ans = INF;
    for (int u = 1; u < n; u++) {
        if (dp[FULL][u] < INF && dist[u][0] < INF)
            ans = Math.min(ans, dp[FULL][u] + dist[u][0]);
    }
    return ans;
}
```

**Complexity:** O(2^n × n²) time, O(2^n × n) space.

---

## Problem 2: Shortest Path Visiting All Nodes (LC #847)

Undirected graph, find shortest path visiting all nodes (start anywhere, can revisit).

**State:** `dp[mask][i]` or BFS with state `(node, visitedMask)`.

BFS approach (finds shortest path directly):

```java
int shortestPathLength(int[][] graph) {
    int n = graph.length;
    int full = (1 << n) - 1;

    // BFS: (node, visitedMask) → distance
    int[][] dist = new int[1 << n][n];
    for (int[] row : dist) Arrays.fill(row, Integer.MAX_VALUE);

    Queue<int[]> q = new LinkedList<>();
    for (int i = 0; i < n; i++) {
        dist[1 << i][i] = 0;
        q.offer(new int[]{i, 1 << i});
    }

    while (!q.isEmpty()) {
        int[] curr = q.poll();
        int node = curr[0], mask = curr[1];
        int d = dist[mask][node];

        if (mask == full) return d;

        for (int nei : graph[node]) {
            int newMask = mask | (1 << nei);
            if (dist[newMask][nei] == Integer.MAX_VALUE) {
                dist[newMask][nei] = d + 1;
                q.offer(new int[]{nei, newMask});
            }
        }
    }
    return -1; // unreachable
}
```

---

## Problem 3: Minimum XOR Sum of Two Arrays (LC #1879)

Given two arrays, assign elements of nums2 to nums1 (bijection) to minimize sum of `nums1[i] XOR nums2[j]`.

**State:** `dp[mask]` = min XOR sum when we've matched first `popcount(mask)` elements of nums1 with the elements of nums2 indicated by mask.

```java
int minimumXORSum(int[] nums1, int[] nums2) {
    int n = nums1.length;
    int[] dp = new int[1 << n];
    Arrays.fill(dp, Integer.MAX_VALUE);
    dp[0] = 0;

    for (int mask = 0; mask < (1 << n); mask++) {
        if (dp[mask] == Integer.MAX_VALUE) continue;
        int i = Integer.bitCount(mask); // current index in nums1
        if (i == n) continue;

        for (int j = 0; j < n; j++) {
            if ((mask & (1 << j)) != 0) continue; // j already used
            int newMask = mask | (1 << j);
            dp[newMask] = Math.min(dp[newMask], dp[mask] + (nums1[i] ^ nums2[j]));
        }
    }
    return dp[(1 << n) - 1];
}
```

---

## Problem 4: Distribute Repeating Integers (LC #1655)

Given quantities for each customer, and quantities of each integer, determine if customer demands can be satisfied.

```java
boolean canDistribute(int[] nums, int[] quantity) {
    int m = quantity.length;
    int full = (1 << m) - 1;

    // Precompute sum for each subset of customers
    int[] subsetSum = new int[1 << m];
    for (int mask = 1; mask <= full; mask++) {
        for (int j = 0; j < m; j++) {
            if ((mask & (1 << j)) != 0) {
                subsetSum[mask] = subsetSum[mask ^ (1 << j)] + quantity[j];
                break; // only need one bit to extend from
            }
        }
    }

    // Count distinct integers
    Map<Integer, Integer> freq = new HashMap<>();
    for (int n : nums) freq.merge(n, 1, Integer::sum);
    int[] counts = freq.values().stream().mapToInt(Integer::intValue).toArray();
    int k = counts.length;

    // dp[mask] = can we satisfy customers in mask using first i integer types
    boolean[] dp = new boolean[1 << m];
    dp[0] = true;

    for (int i = 0; i < k; i++) {
        // traverse masks backwards (0/1 item = each integer type used once)
        boolean[] newDp = dp.clone();
        for (int mask = full; mask >= 1; mask--) {
            if (!dp[mask]) continue; // only extend reachable states... wait
            // Actually we want to ADD customer subsets, not remove
            // Iterate submasks of complement
            for (int sub = (full ^ mask); sub > 0; sub = (sub - 1) & (full ^ mask)) {
                if (subsetSum[sub] <= counts[i]) {
                    newDp[mask | sub] = true;
                }
            }
        }
        dp = newDp;
    }
    return dp[full];
}
```

---

## Bitmask Operations Reference

```java
// Number of set bits
Integer.bitCount(mask)

// Check if bit i is set
(mask >> i) & 1

// Set bit i
mask | (1 << i)

// Clear bit i
mask & ~(1 << i)

// Toggle bit i
mask ^ (1 << i)

// Lowest set bit
mask & (-mask)   // same as mask & ~(mask-1)

// Remove lowest set bit
mask & (mask - 1)

// Iterate all subsets of mask (descending, excluding 0)
for (int sub = mask; sub > 0; sub = (sub - 1) & mask) { ... }

// Iterate all subsets including 0
for (int sub = mask; ; sub = (sub - 1) & mask) {
    // process sub
    if (sub == 0) break;
}
```

---

## When to Use Bitmask DP

| Condition | Verdict |
|-----------|---------|
| n ≤ 20, question about "visiting all" or "assigning all" | Use bitmask DP |
| Need to track which subset has been selected | Use bitmask DP |
| n > 20 | Too slow — find a different approach |
| States are intervals, not subsets | Interval DP instead |

## Try It Yourself

**1.** Count number of Hamiltonian paths in a directed graph (n ≤ 15).

<details>
<summary>Solution</summary>

```java
int countHamiltonianPaths(int[][] graph) {
    int n = graph.length;
    int full = (1 << n) - 1;
    long[][] dp = new long[1 << n][n];

    for (int i = 0; i < n; i++) dp[1 << i][i] = 1;

    for (int mask = 1; mask <= full; mask++) {
        for (int u = 0; u < n; u++) {
            if ((mask & (1 << u)) == 0 || dp[mask][u] == 0) continue;
            for (int v = 0; v < n; v++) {
                if ((mask & (1 << v)) != 0) continue;
                if (graph[u][v] == 1) {
                    dp[mask | (1 << v)][v] += dp[mask][u];
                }
            }
        }
    }

    long total = 0;
    for (int u = 0; u < n; u++) total += dp[full][u];
    return (int) total;
}
```

</details>

## Capstone Connection

AlgoForge includes bitmask DP in the "Hard — Graph Paths" category. The state `(mask, node)` pattern appears in TSP, robot path coverage, and task assignment problems.
