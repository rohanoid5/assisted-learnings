package com.algoforge.datastructures.advanced;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class AdvancedDSTest {

    @Test void segmentTreeRangeQuery() {
        SegmentTree st = new SegmentTree(new int[]{1, 3, 5, 7, 9, 11});
        assertThat(st.query(0, 5)).isEqualTo(36);   // sum of all
        assertThat(st.query(1, 3)).isEqualTo(15);   // 3+5+7
        assertThat(st.query(2, 2)).isEqualTo(5);    // single element
    }

    @Test void segmentTreePointUpdate() {
        SegmentTree st = new SegmentTree(new int[]{1, 3, 5, 7, 9, 11});
        st.update(1, 10);    // change 3 → 10
        assertThat(st.query(0, 2)).isEqualTo(16);   // 1+10+5
        assertThat(st.query(1, 1)).isEqualTo(10);
    }

    @Test void fenwickTreePrefixSum() {
        FenwickTree ft = new FenwickTree(new int[]{1, 3, 5, 7, 9, 11});
        assertThat(ft.prefixSum(0)).isEqualTo(1);
        assertThat(ft.prefixSum(2)).isEqualTo(9);   // 1+3+5
        assertThat(ft.prefixSum(5)).isEqualTo(36);  // all
    }

    @Test void fenwickTreeRangeSum() {
        FenwickTree ft = new FenwickTree(new int[]{1, 3, 5, 7, 9, 11});
        assertThat(ft.rangeSum(1, 3)).isEqualTo(15);   // 3+5+7
    }

    @Test void fenwickTreeUpdate() {
        FenwickTree ft = new FenwickTree(new int[]{1, 3, 5, 7, 9, 11});
        ft.update(1, 7);     // add 7 to index 1 (3 becomes 10)
        assertThat(ft.prefixSum(2)).isEqualTo(16);   // 1+10+5
    }
}
