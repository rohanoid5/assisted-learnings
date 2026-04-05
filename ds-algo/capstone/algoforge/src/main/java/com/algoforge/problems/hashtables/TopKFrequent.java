package com.algoforge.problems.hashtables;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * LC #347 — Top K Frequent Elements
 *
 * <p>Given an integer array nums and integer k, return the k most frequent elements.
 * Answers can be in any order.</p>
 *
 * <b>Approach 1:</b> HashMap + MinHeap of size k → O(n log k)
 * <b>Approach 2:</b> HashMap + Bucket Sort → O(n) — preferred (shown as primary)
 *
 * <pre>
 * Bucket sort insight: frequency can be at most n (all same element).
 * Create n+1 buckets indexed by frequency; collect top k from the end.
 *
 * Trace: nums=[1,1,1,2,2,3], k=2
 *   freq: {1:3, 2:2, 3:1}
 *   buckets: [[], [3], [2], [1], [], [], []]
 *   Scan from end: bucket[3]=[1] → size=1 < k=2
 *                  bucket[2]=[2] → size=2 = k → return [1,2]
 * </pre>
 *
 * Time: O(n)  Space: O(n)
 */
public class TopKFrequent {

    public static int[] topKFrequent(int[] nums, int k) {
        Map<Integer, Integer> freq = new HashMap<>();
        for (int n : nums) freq.merge(n, 1, Integer::sum);

        // Bucket sort by frequency
        @SuppressWarnings("unchecked")
        List<Integer>[] buckets = new List[nums.length + 1];
        for (int num : freq.keySet()) {
            int f = freq.get(num);
            if (buckets[f] == null) buckets[f] = new ArrayList<>();
            buckets[f].add(num);
        }

        List<Integer> result = new ArrayList<>();
        for (int i = buckets.length - 1; i >= 1 && result.size() < k; i--) {
            if (buckets[i] != null) result.addAll(buckets[i]);
        }
        return result.stream().mapToInt(Integer::intValue).toArray();
    }

    // Alternative: MinHeap — O(n log k)
    public static int[] topKFrequentHeap(int[] nums, int k) {
        Map<Integer, Integer> freq = new HashMap<>();
        for (int n : nums) freq.merge(n, 1, Integer::sum);

        // Min-heap keyed by frequency (evict least frequent when size > k)
        PriorityQueue<Integer> heap = new PriorityQueue<>((a, b) -> freq.get(a) - freq.get(b));
        for (int num : freq.keySet()) {
            heap.offer(num);
            if (heap.size() > k) heap.poll();
        }
        return heap.stream().mapToInt(Integer::intValue).toArray();
    }
}
