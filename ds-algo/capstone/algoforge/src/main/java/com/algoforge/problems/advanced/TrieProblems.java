package com.algoforge.problems.advanced;

import java.util.ArrayList;
import java.util.List;

/**
 * LC #208 / Module 10 — Implement Trie (Prefix Tree) problem wrapper
 *
 * <p>This is a demonstration of the AlgoForge Trie in action, covering
 * the full LeetCode #208 interface plus the autocomplete extension.</p>
 *
 * <b>Data Structure:</b> Trie (see com.algoforge.datastructures.trees.Trie)
 *
 * Also demonstrates: LC #211 — Design Add and Search Words Data Structure
 * (wildcard '.' matching via DFS)
 *
 * <pre>
 * Trie node: 26 children (one per lowercase letter) + isEnd flag
 *
 * insert("apple"):  root → a → p → p → l → e(isEnd=true)
 * search("apple"):  traverse to 'e', isEnd=true → true
 * search("app"):    traverse to 'p', isEnd=false → false
 * startsWith("app"):traverse to 'p', exists → true
 * </pre>
 *
 * Time: O(m) per operation   Space: O(26 * total chars)
 */
public class TrieProblems {

    // Trie node with wildcard search support (LC #211)
    private static class WildcardTrieNode {
        WildcardTrieNode[] children = new WildcardTrieNode[26];
        boolean isEnd;
    }

    private final WildcardTrieNode root = new WildcardTrieNode();

    public void addWord(String word) {
        WildcardTrieNode curr = root;
        for (char c : word.toCharArray()) {
            int idx = c - 'a';
            if (curr.children[idx] == null) curr.children[idx] = new WildcardTrieNode();
            curr = curr.children[idx];
        }
        curr.isEnd = true;
    }

    // '.' matches any single character — uses DFS
    public boolean searchWithWildcard(String word) {
        return dfsSearch(root, word, 0);
    }

    private boolean dfsSearch(WildcardTrieNode node, String word, int idx) {
        if (idx == word.length()) return node.isEnd;
        char c = word.charAt(idx);
        if (c == '.') {
            for (WildcardTrieNode child : node.children)
                if (child != null && dfsSearch(child, word, idx + 1)) return true;
            return false;
        } else {
            WildcardTrieNode next = node.children[c - 'a'];
            return next != null && dfsSearch(next, word, idx + 1);
        }
    }
}
