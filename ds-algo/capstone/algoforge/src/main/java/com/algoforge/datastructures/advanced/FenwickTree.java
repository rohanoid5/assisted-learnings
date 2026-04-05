package com.algoforge.datastructures.advanced;

/**
 * FenwickTree (Binary Indexed Tree) — Module 10 capstone deliverable.
 *
 * Space-efficient structure for prefix sums with point updates.
 * Uses 1-based indexing internally.
 *
 * Complexity:
 *   update (point update)     O(log n)
 *   prefixSum (range sum)     O(log n)
 *   build from array          O(n log n)
 *
 * Key trick: i's parent in the BIT = i + (i & -i)  (lowest set bit)
 *            i's responsible range = i - (i & -i) + 1 to i
 *
 * LeetCode #307 (alternate to SegmentTree), #315
 */
public class FenwickTree {

    private final int[] tree;  // 1-indexed; tree[0] unused
    private final int n;

    public FenwickTree(int n) {
        this.n = n;
        this.tree = new int[n + 1];
    }

    public FenwickTree(int[] nums) {
        this.n = nums.length;
        this.tree = new int[n + 1];
        for (int i = 0; i < n; i++) update(i, nums[i]);
    }

    // O(log n) — add delta to position index (0-based)
    public void update(int index, int delta) {
        index++;   // convert to 1-based
        while (index <= n) {
            tree[index] += delta;
            index += index & (-index);   // move to next responsible node
        }
    }

    // O(log n) — prefix sum from 0 to index (0-based, inclusive)
    public int prefixSum(int index) {
        int sum = 0;
        index++;   // convert to 1-based
        while (index > 0) {
            sum += tree[index];
            index -= index & (-index);   // move to parent
        }
        return sum;
    }

    // O(log n) — range sum from left to right (0-based, inclusive)
    public int rangeSum(int left, int right) {
        if (left == 0) return prefixSum(right);
        return prefixSum(right) - prefixSum(left - 1);
    }
}
