package com.algoforge.problems.common;

/**
 * Shared ListNode used across all linked list problem solutions in Part B.
 *
 * <p>Matches the standard LeetCode linked list node definition.</p>
 */
public class ListNode {
    public int val;
    public ListNode next;

    public ListNode(int val) { this.val = val; }
    public ListNode(int val, ListNode next) { this.val = val; this.next = next; }

    /** Convenience factory: build a list from an array. [1,2,3] → 1→2→3 */
    public static ListNode of(int... vals) {
        ListNode dummy = new ListNode(0);
        ListNode cur   = dummy;
        for (int v : vals) { cur.next = new ListNode(v); cur = cur.next; }
        return dummy.next;
    }

    /** Convert list to array for easy assertion in tests. */
    public static int[] toArray(ListNode head) {
        java.util.List<Integer> list = new java.util.ArrayList<>();
        while (head != null) { list.add(head.val); head = head.next; }
        return list.stream().mapToInt(i -> i).toArray();
    }

    @Override public String toString() {
        StringBuilder sb = new StringBuilder();
        ListNode cur = this;
        while (cur != null) { sb.append(cur.val); if (cur.next != null) sb.append("→"); cur = cur.next; }
        return sb.toString();
    }
}
