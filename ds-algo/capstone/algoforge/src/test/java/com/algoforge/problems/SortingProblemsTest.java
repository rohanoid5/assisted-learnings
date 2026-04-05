package com.algoforge.problems;

import com.algoforge.problems.sorting.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class SortingProblemsTest {

    @Test void searchRotatedFound() {
        assertThat(SearchRotatedArray.search(new int[]{4,5,6,7,0,1,2}, 0)).isEqualTo(4);
        assertThat(SearchRotatedArray.search(new int[]{4,5,6,7,0,1,2}, 3)).isEqualTo(-1);
        assertThat(SearchRotatedArray.search(new int[]{1}, 0)).isEqualTo(-1);
    }

    @Test void findMinInRotated() {
        assertThat(FindMinInRotatedArray.findMin(new int[]{3,4,5,1,2})).isEqualTo(1);
        assertThat(FindMinInRotatedArray.findMin(new int[]{4,5,6,7,0,1,2})).isEqualTo(0);
        assertThat(FindMinInRotatedArray.findMin(new int[]{11,13,15,17})).isEqualTo(11);
    }

    @Test void search2DMatrixFound() {
        int[][] matrix = {{1,3,5,7},{10,11,16,20},{23,30,34,60}};
        assertThat(Search2DMatrix.searchMatrix(matrix, 3)).isTrue();
        assertThat(Search2DMatrix.searchMatrix(matrix, 13)).isFalse();
    }

    @Test void findPeakElement() {
        int idx = FindPeakElement.findPeakElement(new int[]{1,2,3,1});
        assertThat(idx).isEqualTo(2);
    }

    @Test void findPeakElementEdge() {
        int idx = FindPeakElement.findPeakElement(new int[]{1,2,1,3,5,6,4});
        // valid peaks are at index 1 or 5
        assertThat(idx).isIn(1, 5);
    }

    @Test void medianOfTwoSortedArraysEven() {
        assertThat(MedianOfTwoSortedArrays.findMedianSortedArrays(
            new int[]{1,3}, new int[]{2}))
            .isEqualTo(2.0);
    }

    @Test void medianOfTwoSortedArraysOdd() {
        assertThat(MedianOfTwoSortedArrays.findMedianSortedArrays(
            new int[]{1,2}, new int[]{3,4}))
            .isEqualTo(2.5);
    }
}
