package com.algoforge.problems.hashtables;

import java.util.HashMap;
import java.util.Map;

/**
 * LC #560 — Subarray Sum Equals K
 *
 * <p>Given an array of integers and an integer k, return the number of
 * subarrays whose sum equals k.</p>
 *
 * <b>Pattern:</b> Prefix sum + HashMap.
 *
 * <pre>
 * Key insight: if prefixSum[j] - prefixSum[i] = k, then subarray [i+1..j] sums to k.
 * Equivalently: prefixSum[i] = prefixSum[j] - k.
 * Store counts of prefix sums seen so far.
 *
 * Trace: nums=[1,1,1], k=2
 *   prefixSum=0 → map={0:1}
 *   i=0: sum=1, need 1-2=-1 → 0 times. map={0:1, 1:1}
 *   i=1: sum=2, need 2-2=0  → 1 time (count=1). map={0:1, 1:2}   (subarray [0,1])
 *   i=2: sum=3, need 3-2=1  → 2 times (count=3). map={0:1,1:2,2:1,3:1}
 *                                                    (subarrays [0,1],[1,2])
 *   Answer: wait, recounting:
 *     count = 0 initially
 *     i=0: sum=1. map doesn't have -1. map={0:1,1:1}. count=0
 *     i=1: sum=2. map has 0 → count+=1 (1). map={0:1,1:2,2:1}
 *     i=2: sum=3. map has 1 → count+=2 (3). map={0:1,1:2,2:1,3:1}
 *     Answer: 2 (subarrays [0..1] and [1..2])... Actually 3 times map has 1? No: map.get(1)=2
 *     Let me re-trace: i=1, sum=2, k=2, need=0, map.get(0)=1, count=1
 *                      i=2, sum=3, k=2, need=1, map.get(1)=1 (only one 1 added so far at i=0), count=2
 *     Then at i=1 we also add 2 to the map. Wait...
 *     i=0: sum=1, put(1,1). map={0:1,1:1}
 *     i=1: sum=2, get(0)=1, count=1. put(2,1). map={0:1,1:1,2:1}
 *     i=2: sum=3, get(1)=1, count=2. put(3,1).
 *     Answer: 2
 * </pre>
 *
 * Time: O(n)  Space: O(n)
 */
public class SubarraySumEqualsK {

    public static int subarraySum(int[] nums, int k) {
        Map<Integer, Integer> prefixCount = new HashMap<>();
        prefixCount.put(0, 1); // empty prefix
        int sum = 0;
        int count = 0;
        for (int num : nums) {
            sum += num;
            count += prefixCount.getOrDefault(sum - k, 0);
            prefixCount.merge(sum, 1, Integer::sum);
        }
        return count;
    }
}
