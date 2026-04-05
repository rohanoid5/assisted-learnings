package com.algoforge.datastructures.linear;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class DynamicArrayTest {

    @Test void addAndGet() {
        DynamicArray<Integer> arr = new DynamicArray<>();
        arr.add(10); arr.add(20); arr.add(30);
        assertThat(arr.get(0)).isEqualTo(10);
        assertThat(arr.get(2)).isEqualTo(30);
        assertThat(arr.size()).isEqualTo(3);
    }

    @Test void autoResizes() {
        DynamicArray<Integer> arr = new DynamicArray<>(2);
        for (int i = 0; i < 20; i++) arr.add(i);
        assertThat(arr.size()).isEqualTo(20);
        assertThat(arr.get(19)).isEqualTo(19);
    }

    @Test void removeShiftsElements() {
        DynamicArray<String> arr = new DynamicArray<>();
        arr.add("a"); arr.add("b"); arr.add("c");
        arr.remove(1);
        assertThat(arr.size()).isEqualTo(2);
        assertThat(arr.get(1)).isEqualTo("c");
    }

    @Test void insertAtIndex() {
        DynamicArray<Integer> arr = new DynamicArray<>();
        arr.add(1); arr.add(3);
        arr.add(1, 2);   // insert 2 at index 1
        assertThat(arr.get(0)).isEqualTo(1);
        assertThat(arr.get(1)).isEqualTo(2);
        assertThat(arr.get(2)).isEqualTo(3);
    }

    @Test void containsWorks() {
        DynamicArray<String> arr = new DynamicArray<>();
        arr.add("hello"); arr.add("world");
        assertThat(arr.contains("hello")).isTrue();
        assertThat(arr.contains("foo")).isFalse();
    }

    @Test void throwsOnOutOfBounds() {
        DynamicArray<Integer> arr = new DynamicArray<>();
        assertThatThrownBy(() -> arr.get(0))
            .isInstanceOf(IndexOutOfBoundsException.class);
    }
}
