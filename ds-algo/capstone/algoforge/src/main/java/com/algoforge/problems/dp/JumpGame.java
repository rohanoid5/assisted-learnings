package com.algoforge.problems.dp;

/**
 * LC #55 — Jump Game
 *
 * <p>Given an integer array nums where nums[i] is the maximum jump length from index i,
 * return true if you can reach the last index starting from index 0.</p>
 *
 * <b>Pattern:</b> Greedy (linear) — track the furthest reachable index.
 *
 * <pre>
 * At each index i, if i > maxReach, we're stuck → return false.
 * Otherwise update maxReach = max(maxReach, i + nums[i]).
 *
 * Trace: nums=[2,3,1,1,4]
 *   maxReach=0
 *   i=0: 0≤0 → maxReach=max(0,2)=2
 *   i=1: 1≤2 → maxReach=max(2,4)=4
 *   i=2: 2≤4 → maxReach=max(4,3)=4
 *   i=3: 3≤4 → maxReach=max(4,4)=4
 *   i=4: 4≤4 → reached the end → return true
 *
 * Trace: nums=[3,2,1,0,4]
 *   i=3: maxReach=3. i=4: 4>3 → false
 * </pre>
 *
 * Time: O(n)  Space: O(1)
 */
public class JumpGame {
    public static boolean canJump(int[] nums) {
        int maxReach = 0;
        for (int i = 0; i < nums.length; i++) {
            if (i > maxReach) return false;
            maxReach = Math.max(maxReach, i + nums[i]);
        }
        return true;
    }
}
