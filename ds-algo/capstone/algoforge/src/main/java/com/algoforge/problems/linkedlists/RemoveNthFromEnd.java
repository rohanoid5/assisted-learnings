package com.algoforge.problems.linkedlists;

import com.algoforge.problems.common.ListNode;

/**
 * LC #19 — Remove Nth Node From End of List
 *
 * <b>Pattern:</b> Two Pointer with a fixed gap (one-pass)
 *
 * <pre>
 * List: 1→2→3→4→5, n=2 (remove 4th from start = 2nd from end)
 *
 * Use two pointers with a gap of n+1 between them.
 * When fast reaches the end, slow is just before the target node.
 *
 *  Start: dummy→1→2→3→4→5
 *         fast    slow = dummy
 *
 *  Advance fast n+1=3 steps: fast=3, slow=dummy
 *  Move both until fast=null:
 *    Step 1: fast=4, slow=1
 *    Step 2: fast=5, slow=2
 *    Step 3: fast=null, slow=3  ← slow is just before the node to remove
 *
 *  slow.next = slow.next.next → 3→5 (skip 4)
 *
 *  Result: 1→2→3→5
 * </pre>
 *
 * Time: O(n)  Space: O(1)
 */
public class RemoveNthFromEnd {

    public static ListNode removeNthFromEnd(ListNode head, int n) {
        ListNode dummy = new ListNode(0, head);
        ListNode fast  = dummy;
        ListNode slow  = dummy;

        // Advance fast n+1 steps ahead
        for (int i = 0; i <= n; i++) fast = fast.next;

        // Move both until fast reaches end
        while (fast != null) {
            fast = fast.next;
            slow = slow.next;
        }

        // slow is now just before the node to remove
        slow.next = slow.next.next;
        return dummy.next;
    }
}
