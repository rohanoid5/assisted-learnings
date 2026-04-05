package com.algoforge.problems.linkedlists;

import com.algoforge.problems.common.ListNode;

/**
 * LC #206 — Reverse Linked List
 *
 * <b>Pattern:</b> Iterative pointer manipulation / Recursion
 *
 * <pre>
 * Iterative trace: 1→2→3→4→5
 *
 *  prev=null, cur=1
 *  Iteration 1: next=2, 1→null, prev=1, cur=2
 *  Iteration 2: next=3, 2→1,    prev=2, cur=3
 *  Iteration 3: next=4, 3→2,    prev=3, cur=4
 *  Iteration 4: next=5, 4→3,    prev=4, cur=5
 *  Iteration 5: next=null, 5→4, prev=5, cur=null
 *
 *  Result: 5→4→3→2→1
 * </pre>
 *
 * Time: O(n)  Space: O(1) iterative / O(n) recursive (call stack)
 */
public class ReverseLinkedList {

    /** Iterative: O(1) space. */
    public static ListNode reverse(ListNode head) {
        ListNode prev = null, cur = head;
        while (cur != null) {
            ListNode next = cur.next;
            cur.next = prev;
            prev     = cur;
            cur      = next;
        }
        return prev;
    }

    /** Recursive: elegant but O(n) stack space. */
    public static ListNode reverseRecursive(ListNode head) {
        if (head == null || head.next == null) return head;
        ListNode newHead = reverseRecursive(head.next);
        head.next.next = head;
        head.next      = null;
        return newHead;
    }

    /** Reverse only the sublist from left to right (1-indexed). LC #92. */
    public static ListNode reverseBetween(ListNode head, int left, int right) {
        ListNode dummy = new ListNode(0, head);
        ListNode pre   = dummy;

        // Advance pre to the node just before position `left`
        for (int i = 1; i < left; i++) pre = pre.next;

        ListNode cur = pre.next;
        // Reverse `right - left` times using the "head insertion" technique
        for (int i = 0; i < right - left; i++) {
            ListNode next = cur.next;
            cur.next      = next.next;
            next.next     = pre.next;
            pre.next      = next;
        }
        return dummy.next;
    }
}
