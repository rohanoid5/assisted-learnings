package com.algoforge.datastructures.linear;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class LinkedListTest {

    @Test void singlyAddRemove() {
        SinglyLinkedList<Integer> list = new SinglyLinkedList<>();
        list.addLast(1); list.addLast(2); list.addLast(3);
        assertThat(list.removeFirst()).isEqualTo(1);
        assertThat(list.size()).isEqualTo(2);
    }

    @Test void singlyReverse() {
        SinglyLinkedList<Integer> list = new SinglyLinkedList<>();
        list.addLast(1); list.addLast(2); list.addLast(3);
        list.reverse();
        assertThat(list.get(0)).isEqualTo(3);
        assertThat(list.get(2)).isEqualTo(1);
    }

    @Test void doublyAddBothEnds() {
        DoublyLinkedList<String> list = new DoublyLinkedList<>();
        list.addFirst("b");
        list.addFirst("a");
        list.addLast("c");
        assertThat(list.peekFirst()).isEqualTo("a");
        assertThat(list.peekLast()).isEqualTo("c");
        assertThat(list.size()).isEqualTo(3);
    }

    @Test void lruCacheEvictsCorrectly() {
        LRUCache cache = new LRUCache(2);
        cache.put(1, 1);
        cache.put(2, 2);
        assertThat(cache.get(1)).isEqualTo(1);   // access key 1 — makes 2 the LRU
        cache.put(3, 3);                          // evicts key 2
        assertThat(cache.get(2)).isEqualTo(-1);  // key 2 was evicted
        assertThat(cache.get(1)).isEqualTo(1);
        assertThat(cache.get(3)).isEqualTo(3);
    }

    @Test void minStackGetMin() {
        MinStack ms = new MinStack();
        ms.push(-2); ms.push(0); ms.push(-3);
        assertThat(ms.getMin()).isEqualTo(-3);
        ms.pop();
        assertThat(ms.top()).isEqualTo(0);
        assertThat(ms.getMin()).isEqualTo(-2);
    }
}
