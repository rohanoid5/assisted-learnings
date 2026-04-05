package com.algoforge.datastructures.advanced;

import java.util.Random;

/**
 * Skip List — a probabilistic data structure that provides O(log n) average-case
 * insert, delete, and search by layering multiple sorted linked lists.
 *
 * <pre>
 * Conceptual structure (4 levels, keys: 3 7 12 17 19 26):
 *
 * Level 3: ─∞ ──────────────────────────── 17 ──────────────── +∞
 * Level 2: ─∞ ──────── 7 ──────────────── 17 ─────────── 26 ─ +∞
 * Level 1: ─∞ ──── 3 ─ 7 ──── 12 ──────── 17 ─── 19 ─── 26 ─ +∞
 * Level 0: ─∞ ── 3 ─ 7 ─ 12 ─ 17 ─ 19 ─ 26 ── +∞   (base list)
 * </pre>
 *
 * <p>Search starts at the top-left. At each level, advance right until the next
 * key exceeds the target, then drop down one level. This skips large sections
 * of the list, achieving O(log n) expected time.</p>
 *
 * Time:  O(log n) expected for insert / delete / search
 * Space: O(n log n) expected
 */
public class SkipList {

    private static final int  MAX_LEVEL = 16;
    private static final double PROBABILITY = 0.5;

    // ── Node ────────────────────────────────────────────────────────────────

    private static class Node {
        final int key;
        final Node[] forward; // forward[i] = next node at level i

        Node(int key, int level) {
            this.key     = key;
            this.forward = new Node[level + 1];
        }
    }

    // ── State ────────────────────────────────────────────────────────────────

    private final Node  head;    // sentinel head (−∞)
    private final Random rng = new Random();
    private int   level;         // current highest level in use
    private int   size;

    public SkipList() {
        head  = new Node(Integer.MIN_VALUE, MAX_LEVEL);
        level = 0;
    }

    // ── Public API ──────────────────────────────────────────────────────────

    /** Returns true if key is present. O(log n) expected. */
    public boolean contains(int key) {
        Node cur = head;
        for (int i = level; i >= 0; i--) {
            while (cur.forward[i] != null && cur.forward[i].key < key)
                cur = cur.forward[i];
        }
        cur = cur.forward[0];
        return cur != null && cur.key == key;
    }

    /** Insert key. Duplicate keys are ignored. O(log n) expected. */
    public void insert(int key) {
        @SuppressWarnings("unchecked")
        Node[] update = new Node[MAX_LEVEL + 1];
        Node cur = head;

        // Find insertion point at each level
        for (int i = level; i >= 0; i--) {
            while (cur.forward[i] != null && cur.forward[i].key < key)
                cur = cur.forward[i];
            update[i] = cur;
        }

        cur = cur.forward[0];
        if (cur != null && cur.key == key) return; // duplicate

        int newLevel = randomLevel();
        if (newLevel > level) {
            for (int i = level + 1; i <= newLevel; i++) update[i] = head;
            level = newLevel;
        }

        Node newNode = new Node(key, newLevel);
        for (int i = 0; i <= newLevel; i++) {
            newNode.forward[i]  = update[i].forward[i];
            update[i].forward[i] = newNode;
        }
        size++;
    }

    /** Delete key. No-op if not present. O(log n) expected. */
    public void delete(int key) {
        @SuppressWarnings("unchecked")
        Node[] update = new Node[MAX_LEVEL + 1];
        Node cur = head;

        for (int i = level; i >= 0; i--) {
            while (cur.forward[i] != null && cur.forward[i].key < key)
                cur = cur.forward[i];
            update[i] = cur;
        }

        cur = cur.forward[0];
        if (cur == null || cur.key != key) return; // not found

        for (int i = 0; i <= level; i++) {
            if (update[i].forward[i] != cur) break;
            update[i].forward[i] = cur.forward[i];
        }

        while (level > 0 && head.forward[level] == null) level--;
        size--;
    }

    public int  size()    { return size; }
    public boolean isEmpty() { return size == 0; }

    // ── Private Helpers ─────────────────────────────────────────────────────

    /**
     * Generates a random level for a new node.
     * Each level is included with probability 0.5, capped at MAX_LEVEL.
     * On average, 50% of nodes exist at level 1, 25% at level 2, etc.
     */
    private int randomLevel() {
        int lvl = 0;
        while (rng.nextDouble() < PROBABILITY && lvl < MAX_LEVEL) lvl++;
        return lvl;
    }
}
