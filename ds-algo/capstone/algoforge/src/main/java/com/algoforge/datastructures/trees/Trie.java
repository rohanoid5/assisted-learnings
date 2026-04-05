package com.algoforge.datastructures.trees;

/**
 * Trie — Module 10 capstone deliverable.
 *
 * A prefix tree that stores strings character-by-character.
 * Each node has up to 26 children (lowercase a-z).
 *
 * Complexity:
 *   insert / search / startsWith   O(m), where m = word length
 *   Space: O(alphabet * total characters)
 *
 * LeetCode #208
 */
public class Trie {

    private static class TrieNode {
        TrieNode[] children = new TrieNode[26];
        boolean isEnd;
    }

    private final TrieNode root = new TrieNode();

    // O(m)
    public void insert(String word) {
        TrieNode curr = root;
        for (char c : word.toCharArray()) {
            int idx = c - 'a';
            if (curr.children[idx] == null)
                curr.children[idx] = new TrieNode();
            curr = curr.children[idx];
        }
        curr.isEnd = true;
    }

    // O(m) — exact full-word match
    public boolean search(String word) {
        TrieNode node = findNode(word);
        return node != null && node.isEnd;
    }

    // O(m) — prefix match (autocomplete use case)
    public boolean startsWith(String prefix) {
        return findNode(prefix) != null;
    }

    // O(m) — returns all words with the given prefix
    public java.util.List<String> autocomplete(String prefix) {
        java.util.List<String> result = new java.util.ArrayList<>();
        TrieNode node = findNode(prefix);
        if (node != null) dfs(node, new StringBuilder(prefix), result);
        return result;
    }

    // ---------------------------------------------------------------
    private TrieNode findNode(String s) {
        TrieNode curr = root;
        for (char c : s.toCharArray()) {
            int idx = c - 'a';
            if (curr.children[idx] == null) return null;
            curr = curr.children[idx];
        }
        return curr;
    }

    private void dfs(TrieNode node, StringBuilder curr, java.util.List<String> result) {
        if (node.isEnd) result.add(curr.toString());
        for (int i = 0; i < 26; i++) {
            if (node.children[i] != null) {
                curr.append((char)('a' + i));
                dfs(node.children[i], curr, result);
                curr.deleteCharAt(curr.length() - 1);
            }
        }
    }
}
