package com.algoforge.datastructures.advanced;

/**
 * SegmentTree — Module 10 capstone deliverable.
 *
 * Supports range sum queries and point updates in O(log n).
 * Built from an input array in O(n).
 *
 * Array representation: node at index i
 *   left child:  2*i + 1
 *   right child: 2*i + 2
 *   Tree size:   4 * n (safe upper bound)
 *
 * LeetCode #307 (Range Sum Query — Mutable)
 */
public class SegmentTree {

    private final int[] tree;
    private final int n;

    public SegmentTree(int[] nums) {
        n = nums.length;
        tree = new int[4 * n];
        if (n > 0) build(nums, 0, 0, n - 1);
    }

    // O(log n) — range sum query [queryLeft, queryRight]
    public int query(int queryLeft, int queryRight) {
        return queryRec(0, 0, n - 1, queryLeft, queryRight);
    }

    // O(log n) — point update: set nums[index] = val
    public void update(int index, int val) {
        updateRec(0, 0, n - 1, index, val);
    }

    // ---------------------------------------------------------------
    private void build(int[] nums, int node, int start, int end) {
        if (start == end) {
            tree[node] = nums[start];
        } else {
            int mid = (start + end) / 2;
            build(nums, 2*node+1, start, mid);
            build(nums, 2*node+2, mid+1, end);
            tree[node] = tree[2*node+1] + tree[2*node+2];
        }
    }

    private int queryRec(int node, int start, int end, int l, int r) {
        if (r < start || end < l) return 0;              // out of range
        if (l <= start && end <= r) return tree[node];   // fully in range
        int mid = (start + end) / 2;
        return queryRec(2*node+1, start, mid, l, r)
             + queryRec(2*node+2, mid+1, end, l, r);
    }

    private void updateRec(int node, int start, int end, int index, int val) {
        if (start == end) {
            tree[node] = val;
        } else {
            int mid = (start + end) / 2;
            if (index <= mid) updateRec(2*node+1, start, mid, index, val);
            else              updateRec(2*node+2, mid+1, end, index, val);
            tree[node] = tree[2*node+1] + tree[2*node+2];
        }
    }
}
