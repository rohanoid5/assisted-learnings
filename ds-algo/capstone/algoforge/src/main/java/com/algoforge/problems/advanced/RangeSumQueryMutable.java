package com.algoforge.problems.advanced;

import com.algoforge.datastructures.advanced.SegmentTree;

/**
 * LC #307 — Range Sum Query — Mutable
 *
 * <p>Design a data structure that supports:
 *   - update(index, val): update nums[index] = val
 *   - sumRange(left, right): return the sum of nums[left..right]
 * Both operations must run in O(log n).</p>
 *
 * <b>Data Structure:</b> Segment Tree — O(log n) point update and range sum query.
 *
 * <pre>
 * Comparison of approaches:
 *   Naive array: O(1) update, O(n) query
 *   Prefix sum:  O(n) update, O(1) query
 *   Segment tree: O(log n) both ← best for mixed read/write workloads
 *
 * This class delegates to the AlgoForge SegmentTree implementation.
 * </pre>
 *
 * Time: O(n) build, O(log n) update/query   Space: O(n)
 */
public class RangeSumQueryMutable {

    private final SegmentTree segTree;

    public RangeSumQueryMutable(int[] nums) {
        segTree = new SegmentTree(nums);
    }

    public void update(int index, int val) {
        segTree.update(index, val);
    }

    public int sumRange(int left, int right) {
        return segTree.query(left, right);
    }
}
