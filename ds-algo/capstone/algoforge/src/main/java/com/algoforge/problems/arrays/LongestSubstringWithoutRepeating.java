package com.algoforge.problems.arrays;

import java.util.HashMap;
import java.util.Map;

/**
 * LC #3 — Longest Substring Without Repeating Characters
 *
 * <b>Pattern:</b> Sliding Window + HashMap
 *
 * <pre>
 * s = "abcabcbb"
 *
 * Window expands right. When a duplicate is found, the left pointer
 * jumps past the previous occurrence of the duplicate character.
 *
 *  r=0 'a': window=[a],      map={a:0}, max=1
 *  r=1 'b': window=[ab],     map={a:0,b:1}, max=2
 *  r=2 'c': window=[abc],    map={a:0,b:1,c:2}, max=3
 *  r=3 'a': 'a' seen at 0, l=max(0, 0+1)=1
 *           window=[bca],    map={a:3,b:1,c:2}, max=3
 *  r=4 'b': 'b' seen at 1, l=max(1, 1+1)=2
 *           window=[cab],    map={...,b:4}, max=3
 *  ...
 *  Answer: 3 ("abc")
 * </pre>
 *
 * Time: O(n)  Space: O(min(n, charset_size))
 */
public class LongestSubstringWithoutRepeating {

    public static int lengthOfLongestSubstring(String s) {
        Map<Character, Integer> lastSeen = new HashMap<>(); // char → last index seen
        int max = 0;
        int left = 0; // left boundary of the window

        for (int right = 0; right < s.length(); right++) {
            char c = s.charAt(right);
            // If c was seen inside the current window, shrink from the left
            if (lastSeen.containsKey(c) && lastSeen.get(c) >= left) {
                left = lastSeen.get(c) + 1;
            }
            lastSeen.put(c, right);
            max = Math.max(max, right - left + 1);
        }
        return max;
    }
}
