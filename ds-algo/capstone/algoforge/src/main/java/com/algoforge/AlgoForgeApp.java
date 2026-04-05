package com.algoforge;

/**
 * AlgoForgeApp — Module 01 capstone entry point.
 *
 * Demonstrates the library at a glance and acts as the main runnable
 * that ties together both parts of AlgoForge:
 *   Part A — custom data structure implementations
 *   Part B — curated interview problem solutions
 *
 * Run with: mvn exec:java -Dexec.mainClass="com.algoforge.AlgoForgeApp"
 */
public class AlgoForgeApp {

    public static void main(String[] args) {
        System.out.println("╔══════════════════════════════════════════════════╗");
        System.out.println("║           AlgoForge — DS&A Capstone             ║");
        System.out.println("╠══════════════════════════════════════════════════╣");
        System.out.println("║  Part A: Custom Data Structures                 ║");
        System.out.println("║    DynamicArray, SinglyLinkedList, Stack, Queue ║");
        System.out.println("║    HashMap, BST, AVLTree, MinHeap, MaxHeap      ║");
        System.out.println("║    Graph, Dijkstra, UnionFind, Trie             ║");
        System.out.println("║    SegmentTree, FenwickTree, SkipList           ║");
        System.out.println("╠══════════════════════════════════════════════════╣");
        System.out.println("║  Part B: 50+ Curated Problem Solutions          ║");
        System.out.println("║    Arrays, LinkedLists, Stacks/Queues           ║");
        System.out.println("║    HashTables, Backtracking, Sorting            ║");
        System.out.println("║    Trees, Graphs, Advanced DS, DP, Patterns     ║");
        System.out.println("╚══════════════════════════════════════════════════╝");
        System.out.println();
        System.out.println("Run 'mvn test' to validate all implementations.");
        System.out.println("Run ComplexityBenchmark for empirical Big-O measurement.");
    }
}
