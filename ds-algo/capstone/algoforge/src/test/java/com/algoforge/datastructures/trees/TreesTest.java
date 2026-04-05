package com.algoforge.datastructures.trees;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class TreesTest {

    @Test void bstInsertContainsInorder() {
        BST<Integer> bst = new BST<>();
        bst.insert(5); bst.insert(3); bst.insert(7); bst.insert(1); bst.insert(4);
        assertThat(bst.contains(3)).isTrue();
        assertThat(bst.contains(6)).isFalse();
        assertThat(bst.inorder()).containsExactly(1, 3, 4, 5, 7);
    }

    @Test void bstMinMax() {
        BST<Integer> bst = new BST<>();
        bst.insert(5); bst.insert(2); bst.insert(8);
        assertThat(bst.min()).isEqualTo(2);
        assertThat(bst.max()).isEqualTo(8);
    }

    @Test void bstDelete() {
        BST<Integer> bst = new BST<>();
        bst.insert(5); bst.insert(3); bst.insert(7);
        bst.delete(3);
        assertThat(bst.contains(3)).isFalse();
        assertThat(bst.inorder()).containsExactly(5, 7);
    }

    @Test void minHeapPollOrder() {
        MinHeap heap = new MinHeap(new int[]{5, 3, 1, 4, 2});
        assertThat(heap.poll()).isEqualTo(1);
        assertThat(heap.poll()).isEqualTo(2);
        assertThat(heap.poll()).isEqualTo(3);
    }

    @Test void minHeapOfferAndPeek() {
        MinHeap heap = new MinHeap(10);
        heap.offer(10); heap.offer(3); heap.offer(7);
        assertThat(heap.peek()).isEqualTo(3);
        assertThat(heap.size()).isEqualTo(3);
    }

    @Test void trieInsertSearch() {
        Trie trie = new Trie();
        trie.insert("apple");
        assertThat(trie.search("apple")).isTrue();
        assertThat(trie.search("app")).isFalse();
        assertThat(trie.startsWith("app")).isTrue();
        assertThat(trie.startsWith("xyz")).isFalse();
    }

    @Test void trieAutocomplete() {
        Trie trie = new Trie();
        trie.insert("apple"); trie.insert("app"); trie.insert("application");
        var results = trie.autocomplete("app");
        assertThat(results).containsExactlyInAnyOrder("app", "apple", "application");
    }
}
