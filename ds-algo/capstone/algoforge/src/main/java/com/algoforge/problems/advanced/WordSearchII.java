package com.algoforge.problems.advanced;

import com.algoforge.datastructures.trees.Trie;
import java.util.ArrayList;
import java.util.List;

/**
 * LC #212 — Word Search II
 *
 * <p>Given an m×n board and a list of words, return all words in the list that can
 * be found in the board. Each word must be constructed from sequentially adjacent cells
 * (horizontal/vertical). The same cell cannot be used twice in a word.</p>
 *
 * <b>Pattern:</b> Trie + Backtracking DFS — prune branches with the Trie.
 *
 * <pre>
 * Key optimization over single-word search: build a Trie from all words so the DFS
 * can simultaneously search for all words. When a Trie path leads nowhere, prune early.
 *
 * Algorithm:
 *   1. Insert all words into Trie
 *   2. DFS from each cell, following Trie branches
 *   3. When Trie node isEnd=true, add the word to results
 *   4. Mark cell as visited during DFS; restore on backtrack
 * </pre>
 *
 * Time: O(M * N * 4 * 3^(L-1)) where L = max word length   Space: O(total word chars)
 */
public class WordSearchII {

    private static final int[][] DIRS = {{0,1},{0,-1},{1,0},{-1,0}};

    // Internal Trie node (need access to word stored at leaves)
    private static class TrieNode {
        TrieNode[] children = new TrieNode[26];
        String word; // non-null at leaf = full word found
    }

    public static List<String> findWords(char[][] board, String[] words) {
        TrieNode root = buildTrie(words);
        List<String> result = new ArrayList<>();
        int m = board.length, n = board[0].length;
        for (int r = 0; r < m; r++)
            for (int c = 0; c < n; c++)
                dfs(board, r, c, root, result);
        return result;
    }

    private static void dfs(char[][] board, int r, int c, TrieNode node, List<String> result) {
        if (r < 0 || r >= board.length || c < 0 || c >= board[0].length || board[r][c] == '#') return;
        char ch = board[r][c];
        TrieNode next = node.children[ch - 'a'];
        if (next == null) return; // no word with this prefix → prune
        if (next.word != null) {
            result.add(next.word);
            next.word = null; // avoid duplicates
        }
        board[r][c] = '#'; // mark visited
        for (int[] d : DIRS) dfs(board, r + d[0], c + d[1], next, result);
        board[r][c] = ch; // restore
    }

    private static TrieNode buildTrie(String[] words) {
        TrieNode root = new TrieNode();
        for (String word : words) {
            TrieNode curr = root;
            for (char c : word.toCharArray()) {
                int idx = c - 'a';
                if (curr.children[idx] == null) curr.children[idx] = new TrieNode();
                curr = curr.children[idx];
            }
            curr.word = word;
        }
        return root;
    }
}
