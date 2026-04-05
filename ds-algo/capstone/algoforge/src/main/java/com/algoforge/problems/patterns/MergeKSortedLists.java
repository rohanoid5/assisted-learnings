package com.algoforge.problems.patterns;

import java.util.ArrayList;
import java.util.List;
import java.util.PriorityQueue;

/**
 * LC #23 — Merge K Sorted Lists
 *
 * <p>Merge k sorted linked lists and return a single sorted list.</p>
 *
 * <b>Pattern:</b> MinHeap — always pull the global minimum across all lists.
 *
 * <pre>
 * Algorithm:
 *   1. Initialize min-heap with the head of each list.
 *   2. Poll minimum node, add to result, push its next (if any) to the heap.
 *
 * Time: O(N log k) where N = total nodes, k = number of lists
 * </pre>
 *
 * Also demonstrates the Divide & Conquer approach (merge pairs repeatedly):
 * Time: O(N log k)  Space: O(log k) stack
 */
public class MergeKSortedLists {

    public static class ListNode {
        public int val;
        public ListNode next;
        public ListNode(int val) { this.val = val; }
    }

    // MinHeap approach
    public static ListNode mergeKLists(ListNode[] lists) {
        PriorityQueue<ListNode> minHeap = new PriorityQueue<>((a, b) -> a.val - b.val);
        for (ListNode head : lists)
            if (head != null) minHeap.offer(head);

        ListNode dummy = new ListNode(0), curr = dummy;
        while (!minHeap.isEmpty()) {
            ListNode node = minHeap.poll();
            curr.next = node;
            curr = curr.next;
            if (node.next != null) minHeap.offer(node.next);
        }
        return dummy.next;
    }

    // Divide & Conquer approach
    public static ListNode mergeKListsDivConq(ListNode[] lists) {
        if (lists.length == 0) return null;
        int end = lists.length - 1;
        while (end > 0) {
            int start = 0;
            while (start < end) {
                lists[start] = mergeTwoSorted(lists[start], lists[end]);
                start++; end--;
            }
        }
        return lists[0];
    }

    private static ListNode mergeTwoSorted(ListNode l1, ListNode l2) {
        ListNode dummy = new ListNode(0), curr = dummy;
        while (l1 != null && l2 != null) {
            if (l1.val <= l2.val) { curr.next = l1; l1 = l1.next; }
            else                   { curr.next = l2; l2 = l2.next; }
            curr = curr.next;
        }
        curr.next = (l1 != null) ? l1 : l2;
        return dummy.next;
    }
}
