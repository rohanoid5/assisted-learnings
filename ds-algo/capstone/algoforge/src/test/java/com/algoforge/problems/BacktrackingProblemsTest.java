package com.algoforge.problems;

import com.algoforge.problems.backtracking.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class BacktrackingProblemsTest {

    @Test void subsetsCorrectCount() {
        List<List<Integer>> result = Subsets.subsets(new int[]{1,2,3});
        assertThat(result).hasSize(8); // 2^3
    }

    @Test void subsetsContainsEmptyAndFull() {
        List<List<Integer>> result = Subsets.subsets(new int[]{1,2,3});
        assertThat(result).anySatisfy(s -> assertThat(s).isEmpty());
        assertThat(result).anySatisfy(s -> assertThat(s).containsExactlyInAnyOrder(1,2,3));
    }

    @Test void permutationsCorrectCount() {
        List<List<Integer>> result = Permutations.permute(new int[]{1,2,3});
        assertThat(result).hasSize(6); // 3!
    }

    @Test void permutationsAllDistinct() {
        List<List<Integer>> result = Permutations.permute(new int[]{1,2,3});
        // Each permutation has 3 elements
        result.forEach(p -> assertThat(p).hasSize(3));
    }

    @Test void combinationSumBasic() {
        List<List<Integer>> result = CombinationSum.combinationSum(new int[]{2,3,6,7}, 7);
        assertThat(result).hasSize(2);
        assertThat(result).anySatisfy(c -> assertThat(c).containsExactlyInAnyOrder(2,2,3));
        assertThat(result).anySatisfy(c -> assertThat(c).containsExactly(7));
    }

    @Test void nQueensBasic() {
        List<List<String>> result = NQueens.solveNQueens(4);
        assertThat(result).hasSize(2);
        result.forEach(board -> assertThat(board).hasSize(4));
    }

    @Test void nQueensOneSolution() {
        // n=1: only one solution
        assertThat(NQueens.solveNQueens(1)).hasSize(1);
    }

    @Test void wordSearchFound() {
        char[][] board = {
            {'A','B','C','E'},
            {'S','F','C','S'},
            {'A','D','E','E'}
        };
        assertThat(WordSearch.exist(board, "ABCCED")).isTrue();
        assertThat(WordSearch.exist(board, "SEE")).isTrue();
    }

    @Test void wordSearchNotFound() {
        char[][] board = {
            {'A','B','C','E'},
            {'S','F','C','S'},
            {'A','D','E','E'}
        };
        assertThat(WordSearch.exist(board, "ABCB")).isFalse();
    }
}
