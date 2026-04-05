package com.algoforge.problems.patterns;

/**
 * LC #287 — Find the Duplicate Number
 *
 * <p>Given an integer array nums of size n+1 where each integer is in [1, n],
 * there is exactly one duplicate number. Find it without modifying the array
 * and using only O(1) extra space.</p>
 *
 * <b>Pattern:</b> Fast & Slow Pointer (Floyd's Cycle Detection)
 *
 * <pre>
 * Key insight: treat the array as a linked list where index i points to nums[i].
 * Because there's a duplicate, the list must contain a cycle.
 * The duplicate number = the entry point of the cycle.
 *
 * Phase 1: detect cycle
 *   slow = nums[slow], fast = nums[nums[fast]]
 *   They meet inside the cycle.
 *
 * Phase 2: find entry point
 *   Reset slow to 0. Move both slow and fast by 1 step.
 *   They meet at the cycle entry = duplicate number.
 *
 * Trace: nums=[1,3,4,2,2]
 *   slow=1,fast=1 → slow=3,fast=4 → slow=2,fast=2 → meet
 *   slow=0,fast=2 → slow=1,fast=4 → slow=3,fast=2 → slow=2,fast=2 → answer=2
 * </pre>
 *
 * Time: O(n)  Space: O(1)
 */
public class FindDuplicateNumber {

    public static int findDuplicate(int[] nums) {
        // Phase 1: find intersection inside cycle
        int slow = nums[0], fast = nums[0];
        do {
            slow = nums[slow];
            fast = nums[nums[fast]];
        } while (slow != fast);

        // Phase 2: find cycle entry (= duplicate)
        slow = nums[0];
        while (slow != fast) {
            slow = nums[slow];
            fast = nums[fast];
        }
        return slow;
    }
}
