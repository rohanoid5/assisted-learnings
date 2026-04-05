package com.algoforge.problems.hashtables;

import java.util.HashMap;
import java.util.Map;

/**
 * LC #205 — Isomorphic Strings
 *
 * <p>Two strings s and t are isomorphic if the characters in s can be replaced
 * to get t. No two different characters may map to the same character.
 * Preserving order is required.</p>
 *
 * <b>Pattern:</b> Two HashMaps for bidirectional character mapping.
 *
 * <pre>
 * Trace: s="egg", t="add"
 *   i=0: 'e'→? not mapped. 'a'←? not mapped. map: e→a, a→e ✓
 *   i=1: 'g'→? not mapped. 'd'←? not mapped. map: g→d, d→g ✓
 *   i=2: 'g'→'d'. t[2]='d'? yes ✓
 *   return true
 *
 * Trace: s="foo", t="bar"
 *   i=0: 'f'→'b', 'b'→'f' ✓
 *   i=1: 'o'→'a', 'a'→'o' ✓
 *   i=2: 'o'→'a'? but 'r'≠'a' → return false
 * </pre>
 *
 * Time: O(n)  Space: O(1) — at most 256 ASCII entries
 */
public class IsomorphicStrings {

    public static boolean isIsomorphic(String s, String t) {
        Map<Character, Character> sToT = new HashMap<>();
        Map<Character, Character> tToS = new HashMap<>();

        for (int i = 0; i < s.length(); i++) {
            char sc = s.charAt(i), tc = t.charAt(i);
            if (sToT.containsKey(sc)) {
                if (sToT.get(sc) != tc) return false;
            } else {
                if (tToS.containsKey(tc)) return false; // tc already mapped to a different char
                sToT.put(sc, tc);
                tToS.put(tc, sc);
            }
        }
        return true;
    }
}
