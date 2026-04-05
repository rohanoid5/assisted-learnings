package com.algoforge.datastructures.graphs;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class GraphsTest {

    @Test void undirectedBFS() {
        Graph<Integer> g = new Graph<>(false);
        g.addEdge(1, 2); g.addEdge(1, 3); g.addEdge(2, 4);
        List<Integer> order = g.bfs(1);
        assertThat(order.get(0)).isEqualTo(1);
        assertThat(order).containsExactlyInAnyOrder(1, 2, 3, 4);
        // BFS must visit 1 before 2 and 3 before 4 (level ordering)
        assertThat(order.indexOf(1)).isLessThan(order.indexOf(4));
    }

    @Test void directedDFS() {
        Graph<String> g = new Graph<>(true);
        g.addEdge("A", "B"); g.addEdge("A", "C"); g.addEdge("B", "D");
        List<String> order = g.dfs("A");
        assertThat(order).contains("A", "B", "C", "D");
        assertThat(order.get(0)).isEqualTo("A");
    }

    @Test void topologicalSort() {
        Graph<Integer> g = new Graph<>(true);
        g.addEdge(0, 1); g.addEdge(0, 2); g.addEdge(1, 3); g.addEdge(2, 3);
        List<Integer> topo = g.topologicalSort();
        // 0 must come before 1, 2; 1 and 2 must come before 3
        assertThat(topo.indexOf(0)).isLessThan(topo.indexOf(1));
        assertThat(topo.indexOf(0)).isLessThan(topo.indexOf(2));
        assertThat(topo.indexOf(1)).isLessThan(topo.indexOf(3));
        assertThat(topo.indexOf(2)).isLessThan(topo.indexOf(3));
    }

    @Test void unionFindConnectsAndCounts() {
        UnionFind uf = new UnionFind(5);
        assertThat(uf.componentCount()).isEqualTo(5);
        uf.union(0, 1);
        uf.union(2, 3);
        assertThat(uf.connected(0, 1)).isTrue();
        assertThat(uf.connected(0, 2)).isFalse();
        assertThat(uf.componentCount()).isEqualTo(3);
        uf.union(1, 2);
        assertThat(uf.connected(0, 3)).isTrue();
        assertThat(uf.componentCount()).isEqualTo(2);
    }
}
