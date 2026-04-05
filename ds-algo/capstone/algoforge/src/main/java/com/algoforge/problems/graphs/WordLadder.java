package com.algoforge.problems.graphs;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

/**
 * LC #127 — Word Ladder
 *
 * <p>Given beginWord, endWord, and a wordList, return the length of the shortest
 * transformation sequence from beginWord to endWord where each pair of adjacent
 * words in the sequence differs by exactly one letter.
 * Return 0 if no such sequence exists.</p>
 *
 * <b>Pattern:</b> BFS on implicit graph — each word is a node; adjacent = 1 letter different.
 *
 * <pre>
 * Key insight: use wildcards to precompute adjacency.
 *   "hit" → "*it", "h*t", "hi*"  map each wildcard to all matching words
 *   BFS guarantees shortest path.
 *
 * Trace: begin="hit", end="cog", wordList=["hot","dot","dog","lot","log","cog"]
 *   Level 1: hit
 *   Level 2: hot (h*t matches)
 *   Level 3: dot, lot
 *   Level 4: dog, log
 *   Level 5: cog → found! → return 5
 * </pre>
 *
 * Time: O(M^2 * N) where M=word length, N=word count   Space: O(M^2 * N)
 */
public class WordLadder {

    public static int ladderLength(String beginWord, String endWord, List<String> wordList) {
        Set<String> wordSet = new HashSet<>(wordList);
        if (!wordSet.contains(endWord)) return 0;

        // Build wildcard map: "h*t" → [hot, hit]
        int L = beginWord.length();
        Map<String, List<String>> pattern = new HashMap<>();
        for (String word : wordSet) {
            for (int i = 0; i < L; i++) {
                String key = word.substring(0, i) + '*' + word.substring(i + 1);
                pattern.computeIfAbsent(key, k -> new java.util.ArrayList<>()).add(word);
            }
        }

        Queue<String> queue = new ArrayDeque<>();
        Set<String> visited = new HashSet<>();
        queue.offer(beginWord);
        visited.add(beginWord);
        int level = 1;

        while (!queue.isEmpty()) {
            int size = queue.size();
            level++;
            while (size-- > 0) {
                String word = queue.poll();
                for (int i = 0; i < L; i++) {
                    String key = word.substring(0, i) + '*' + word.substring(i + 1);
                    for (String next : pattern.getOrDefault(key, List.of())) {
                        if (next.equals(endWord)) return level;
                        if (!visited.contains(next)) {
                            visited.add(next);
                            queue.offer(next);
                        }
                    }
                }
            }
        }
        return 0;
    }
}
