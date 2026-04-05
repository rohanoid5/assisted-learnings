package com.algoforge.problems.linkedlists;

import com.algoforge.problems.common.ListNode;

/**
 * LC #141 / #142 — Linked List Cycle Detection & Cycle Start
 *
 * <b>Pattern:</b> Fast and Slow Pointers (Floyd's Cycle Detection / Tortoise and Hare)
 *
 * <pre>
 * List: 3 → 2 → 0 → -4
 *               ↑_______↑  (cycle: -4 points back to 2)
 *
 * Fast pointer moves 2 steps, slow moves 1 step.
 * If they ever meet → cycle exists.
 *
 * To find the start of the cycle:
 *   After meeting, reset one pointer to head.
 *   Move both one step at a time — they meet at the cycle start.
 *
 * Mathematical proof:
 *   Let F = distance from head to cycle start
 *   Let C = cycle length
 *   At meeting point, slow has travelled F + a
 *                     fast has travelled F + a + n*C (lapped n times)
 *   Since fast = 2 * slow: F + a + n*C = 2(F + a)
 *   → F = n*C - a
 *   → Moving head pointer and meeting pointer both F steps lands them at cycle start.
 * </pre>
 *
 * Time: O(n)  Space: O(1)
 */
public class DetectCycle {

    /** Returns true if the list has a cycle. LC #141. */
    public static boolean hasCycle(ListNode head) {
        ListNode slow = head, fast = head;
        while (fast != null && fast.next != null) {
            slow = slow.next;
            fast = fast.next.next;
            if (slow == fast) return true;
        }
        return false;
    }

    /** Returns the node where the cycle begins, or null if no cycle. LC #142. */
    public static ListNode detectCycleStart(ListNode head) {
        ListNode slow = head, fast = head;

        // Phase 1: find meeting point inside the cycle
        while (fast != null && fast.next != null) {
            slow = slow.next;
            fast = fast.next.next;
            if (slow == fast) {
                // Phase 2: find cycle entrance
                ListNode entry = head;
                while (entry != slow) {
                    entry = entry.next;
                    slow  = slow.next;
                }
                return entry;
            }
        }
        return null; // no cycle
    }
}
