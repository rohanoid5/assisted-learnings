package com.algoforge.datastructures.graphs;

/**
 * UnionFind — Module 09/10 capstone deliverable.
 *
 * Disjoint Set data structure with:
 *   - Union by rank (keeps trees short)
 *   - Path compression (flattens trees on find)
 *
 * Together these give near-O(1) amortized operations — formally
 * O(α(n)) per operation where α is the inverse Ackermann function.
 *
 * Used for: connected components, cycle detection, Kruskal's MST,
 *           Accounts Merge, Number of Provinces.
 */
public class UnionFind {

    private final int[] parent;
    private final int[] rank;
    private int components;

    public UnionFind(int n) {
        parent = new int[n];
        rank   = new int[n];
        components = n;
        for (int i = 0; i < n; i++) parent[i] = i;   // each node is its own root
    }

    // Find with path compression — O(α(n)) amortized
    public int find(int x) {
        if (parent[x] != x)
            parent[x] = find(parent[x]);   // path compression: point directly to root
        return parent[x];
    }

    // Union by rank — O(α(n)) amortized
    // Returns true if x and y were in different components (i.e., a union happened).
    public boolean union(int x, int y) {
        int rootX = find(x), rootY = find(y);
        if (rootX == rootY) return false;   // already in same component

        // Attach smaller-rank tree under larger-rank root
        if (rank[rootX] < rank[rootY]) {
            parent[rootX] = rootY;
        } else if (rank[rootX] > rank[rootY]) {
            parent[rootY] = rootX;
        } else {
            parent[rootY] = rootX;
            rank[rootX]++;
        }
        components--;
        return true;
    }

    public boolean connected(int x, int y) {
        return find(x) == find(y);
    }

    public int componentCount() {
        return components;
    }
}
