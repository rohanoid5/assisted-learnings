package com.algoforge.problems;

import com.algoforge.problems.advanced.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class AdvancedProblemsTest {

    @Test void wordSearchIIFindsWords() {
        char[][] board = {
            {'o','a','a','n'},
            {'e','t','a','e'},
            {'i','h','k','r'},
            {'i','f','l','v'}
        };
        List<String> result = WordSearchII.findWords(board, new String[]{"oath","pea","eat","rain"});
        assertThat(result).containsExactlyInAnyOrder("oath","eat");
    }

    @Test void wordSearchIINotFound() {
        char[][] board = {{'a','b'},{'c','d'}};
        List<String> result = WordSearchII.findWords(board, new String[]{"abcd"});
        assertThat(result).isEmpty();
    }

    @Test void rangeSumQueryMutable() {
        RangeSumQueryMutable rs = new RangeSumQueryMutable(new int[]{1,3,5});
        assertThat(rs.sumRange(0, 2)).isEqualTo(9);
        rs.update(1, 2);
        assertThat(rs.sumRange(0, 2)).isEqualTo(8);
    }

    @Test void countSmallerNumbersAfterSelf() {
        CountSmallerNumbersAfterSelf c = new CountSmallerNumbersAfterSelf();
        List<Integer> result = c.countSmaller(new int[]{5,2,6,1});
        assertThat(result).containsExactly(2,1,1,0);
    }

    @Test void countSmallerSingleElement() {
        CountSmallerNumbersAfterSelf c = new CountSmallerNumbersAfterSelf();
        assertThat(c.countSmaller(new int[]{1})).containsExactly(0);
    }

    @Test void trieProblemsWildcard() {
        TrieProblems trie = new TrieProblems();
        trie.addWord("bad"); trie.addWord("dad"); trie.addWord("mad");
        assertThat(trie.searchWithWildcard("pad")).isFalse();
        assertThat(trie.searchWithWildcard("bad")).isTrue();
        assertThat(trie.searchWithWildcard(".ad")).isTrue();
        assertThat(trie.searchWithWildcard("b..")).isTrue();
    }
}
