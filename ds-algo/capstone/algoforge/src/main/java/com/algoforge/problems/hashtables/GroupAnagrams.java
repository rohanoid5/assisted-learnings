package com.algoforge.problems.hashtables;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * LC #49 — Group Anagrams
 *
 * <p>Given an array of strings, group the anagrams together.
 * Order of groups and elements within each group does not matter.</p>
 *
 * <b>Pattern:</b> HashMap with sorted string as canonical key.
 *
 * <pre>
 * Key insight: two strings are anagrams iff their sorted characters are identical.
 *
 * Trace: ["eat","tea","tan","ate","nat","bat"]
 *   "eat" → sort → "aet" → group: {"aet": ["eat"]}
 *   "tea" → sort → "aet" → group: {"aet": ["eat","tea"]}
 *   "tan" → sort → "ant" → group: {..., "ant": ["tan"]}
 *   "ate" → sort → "aet" → group: {"aet": ["eat","tea","ate"]}
 *   ...
 *   Output: [["eat","tea","ate"],["tan","nat"],["bat"]]
 * </pre>
 *
 * Time: O(n * k log k) where k = max string length   Space: O(nk)
 */
public class GroupAnagrams {

    public static List<List<String>> groupAnagrams(String[] strs) {
        Map<String, List<String>> map = new HashMap<>();
        for (String s : strs) {
            char[] chars = s.toCharArray();
            Arrays.sort(chars);
            String key = new String(chars);
            map.computeIfAbsent(key, k -> new ArrayList<>()).add(s);
        }
        return new ArrayList<>(map.values());
    }
}
