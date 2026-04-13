package com.algoforge.problems.arrays;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class TwoSumTest {

    private final TwoSum solution = new TwoSum();

    @Test
    void brute_findsCorrectIndices() {
        assertThat(solution.twoSumBrute(new int[]{2, 7, 11, 15}, 9)).containsExactly(0, 1);
        assertThat(solution.twoSumBrute(new int[]{3, 2, 4}, 6)).containsExactly(1, 2);
    }

    @Test
    void hash_findsCorrectIndices() {
        assertThat(solution.twoSumHash(new int[]{2, 7, 11, 15}, 9)).containsExactly(0, 1);
        assertThat(solution.twoSumHash(new int[]{3, 3}, 6)).containsExactly(0, 1);
    }
}
