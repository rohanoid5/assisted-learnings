package com.algoforge.problems.patterns;

/**
 * LC #41 — First Missing Positive
 *
 * <p>Given an unsorted integer array nums, return the smallest missing positive integer.
 * Must run in O(n) time and O(1) extra space.</p>
 *
 * <b>Pattern:</b> Cyclic Sort — place each number in its "correct" index position.
 *
 * <pre>
 * Key insight: the answer is in range [1, n+1] (pigeonhole principle).
 * Use the array itself as a hash map: place num i at index i-1.
 *
 * Algorithm:
 *   1. Cyclic sort: while nums[i] is in [1,n] and not already in correct place,
 *      swap nums[i] to its correct position nums[nums[i]-1].
 *   2. Scan: first index i where nums[i] != i+1 is the answer (i+1).
 *   3. If all placed correctly, answer is n+1.
 *
 * Trace: [3,4,-1,1]
 *   i=0: nums[0]=3, swap→[nums[2],4,-1,1]=[−1,4,3,1]. nums[0]=−1, skip.
 *   i=1: nums[1]=4, swap→[−1,1,3,4]. nums[1]=1, swap→[1,−1,3,4].  nums[1]=−1, skip.
 *   i=2: nums[2]=3, swap (already in place: nums[2]=3 at index 2). Skip.
 *   i=3: nums[3]=4, already at index 3. Skip.
 *   Scan: index 1: nums[1]=-1 ≠ 2 → answer = 2
 * </pre>
 *
 * Time: O(n)  Space: O(1)
 */
public class FirstMissingPositive {

    public static int firstMissingPositive(int[] nums) {
        int n = nums.length;
        // Cyclic sort: place nums[i] at index nums[i]-1 if in [1,n]
        for (int i = 0; i < n; i++)
            while (nums[i] > 0 && nums[i] <= n && nums[nums[i] - 1] != nums[i]) {
                int j = nums[i] - 1;
                int tmp = nums[j]; nums[j] = nums[i]; nums[i] = tmp;
            }
        // Find first position where index doesn't match
        for (int i = 0; i < n; i++)
            if (nums[i] != i + 1) return i + 1;
        return n + 1;
    }
}
