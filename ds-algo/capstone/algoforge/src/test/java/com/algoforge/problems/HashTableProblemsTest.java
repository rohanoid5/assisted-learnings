package com.algoforge.problems;

import com.algoforge.problems.hashtables.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class HashTableProblemsTest {

    @Test void groupAnagramsBasic() {
        var result = GroupAnagrams.groupAnagrams(new String[]{"eat","tea","tan","ate","nat","bat"});
        assertThat(result).hasSize(3);
        // Verify each group has correct size
        result.forEach(g -> assertThat(g.size()).isIn(1, 2, 3));
    }

    @Test void longestConsecutiveSequenceBasic() {
        assertThat(LongestConsecutiveSequence.longestConsecutive(new int[]{100,4,200,1,3,2}))
            .isEqualTo(4);
    }

    @Test void longestConsecutiveEmpty() {
        assertThat(LongestConsecutiveSequence.longestConsecutive(new int[]{}))
            .isEqualTo(0);
    }

    @Test void topKFrequentBasic() {
        int[] result = TopKFrequent.topKFrequent(new int[]{1,1,1,2,2,3}, 2);
        assertThat(result).contains(1, 2);
        assertThat(result).hasSize(2);
    }

    @Test void topKFrequentHeapVariant() {
        int[] result = TopKFrequent.topKFrequentHeap(new int[]{1,1,1,2,2,3}, 2);
        assertThat(result).contains(1, 2);
    }

    @Test void subarraySumEqualsK() {
        assertThat(SubarraySumEqualsK.subarraySum(new int[]{1,1,1}, 2)).isEqualTo(2);
        assertThat(SubarraySumEqualsK.subarraySum(new int[]{1,2,3}, 3)).isEqualTo(2);
    }

    @Test void isomorphicStringsTrue() {
        assertThat(IsomorphicStrings.isIsomorphic("egg", "add")).isTrue();
        assertThat(IsomorphicStrings.isIsomorphic("paper", "title")).isTrue();
    }

    @Test void isomorphicStringsFalse() {
        assertThat(IsomorphicStrings.isIsomorphic("foo", "bar")).isFalse();
        assertThat(IsomorphicStrings.isIsomorphic("badc", "baba")).isFalse();
    }
}
