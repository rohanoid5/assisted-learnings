package com.algoforge.problems.linkedlists;

import com.algoforge.problems.common.ListNode;

/**
 * LC #21 — Merge Two Sorted Lists
 *
 * <b>Pattern:</b> Two Pointer with dummy head
 *
 * <pre>
 * l1: 1→2→4
 * l2: 1→3→4
 *
 * Dummy→?
 * Compare l1(1) vs l2(1): equal, take l1 → Dummy→1, l1→2
 * Compare l1(2) vs l2(1): l2 smaller → Dummy→1→1, l2→3
 * Compare l1(2) vs l2(3): l1 smaller → Dummy→1→1→2, l1→4
 * Compare l1(4) vs l2(3): l2 smaller → Dummy→1→1→2→3, l2→4
 * Compare l1(4) vs l2(4): equal, take l1 → Dummy→1→1→2→3→4, l1=null
 * Append remaining l2: Dummy→1→1→2→3→4→4
 *
 * Result: 1→1→2→3→4→4
 * </pre>
 *
 * Time: O(m + n)  Space: O(1)
 */
public class MergeTwoSortedLists {

    public static ListNode mergeTwoLists(ListNode l1, ListNode l2) {
        ListNode dummy = new ListNode(0);
        ListNode cur   = dummy;

        while (l1 != null && l2 != null) {
            if (l1.val <= l2.val) { cur.next = l1; l1 = l1.next; }
            else                  { cur.next = l2; l2 = l2.next; }
            cur = cur.next;
        }
        cur.next = (l1 != null) ? l1 : l2; // append the remaining list
        return dummy.next;
    }

    /**
     * LC #23 — Merge K Sorted Lists.
     * Uses a min-heap for O(N log k) total time where N = total nodes, k = number of lists.
     */
    public static ListNode mergeKLists(ListNode[] lists) {
        java.util.PriorityQueue<ListNode> pq =
            new java.util.PriorityQueue<>((a, b) -> a.val - b.val);

        for (ListNode head : lists) {
            if (head != null) pq.offer(head);
        }

        ListNode dummy = new ListNode(0);
        ListNode cur   = dummy;

        while (!pq.isEmpty()) {
            ListNode node = pq.poll();
            cur.next = node;
            cur      = cur.next;
            if (node.next != null) pq.offer(node.next);
        }
        return dummy.next;
    }
}
