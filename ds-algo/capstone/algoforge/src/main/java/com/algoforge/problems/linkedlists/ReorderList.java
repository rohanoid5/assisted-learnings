package com.algoforge.problems.linkedlists;

import com.algoforge.problems.common.ListNode;

/**
 * LC #143 — Reorder List
 *
 * <p>Reorder: L0→L1→...→Ln-1→Ln  to  L0→Ln→L1→Ln-1→L2→Ln-2→...</p>
 *
 * <b>Pattern:</b> Find Middle + Reverse Second Half + Merge
 *
 * <pre>
 * Input: 1→2→3→4→5
 *
 * Step 1 — Find middle with slow/fast:  mid = 3
 *   First half:  1→2→3
 *   Second half: 4→5
 *
 * Step 2 — Reverse second half:  5→4
 *
 * Step 3 — Interleave:
 *   Take 1 from first, 5 from second → 1→5
 *   Take 2 from first, 4 from second → 1→5→2→4
 *   Take 3 (remaining)               → 1→5→2→4→3
 *
 * Output: 1→5→2→4→3
 * </pre>
 *
 * Time: O(n)  Space: O(1)
 */
public class ReorderList {

    public static void reorderList(ListNode head) {
        if (head == null || head.next == null) return;

        // Step 1: Find the middle
        ListNode slow = head, fast = head;
        while (fast.next != null && fast.next.next != null) {
            slow = slow.next;
            fast = fast.next.next;
        }

        // Step 2: Reverse the second half
        ListNode secondHalf = reverseList(slow.next);
        slow.next = null; // cut the list in half

        // Step 3: Merge the two halves
        ListNode first = head, second = secondHalf;
        while (second != null) {
            ListNode nextFirst  = first.next;
            ListNode nextSecond = second.next;
            first.next  = second;
            second.next = nextFirst;
            first  = nextFirst;
            second = nextSecond;
        }
    }

    private static ListNode reverseList(ListNode head) {
        ListNode prev = null, cur = head;
        while (cur != null) {
            ListNode next = cur.next;
            cur.next = prev;
            prev     = cur;
            cur      = next;
        }
        return prev;
    }
}
