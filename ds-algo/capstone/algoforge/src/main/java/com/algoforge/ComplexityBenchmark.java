package com.algoforge;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * ComplexityBenchmark — Module 01 capstone deliverable.
 *
 * Measures the actual wall-clock time of common operations to make
 * Big-O analysis concrete and empirically verifiable.
 *
 * Run with: mvn exec:java -Dexec.mainClass="com.algoforge.ComplexityBenchmark"
 */
public class ComplexityBenchmark {

    public static void main(String[] args) {
        System.out.println("=== AlgoForge — Complexity Benchmark ===\n");

        int[] sizes = {1_000, 10_000, 100_000, 1_000_000};

        System.out.println("--- O(1): HashMap get ---");
        benchmarkHashMapGet(sizes);

        System.out.println("\n--- O(log n): Binary Search ---");
        benchmarkBinarySearch(sizes);

        System.out.println("\n--- O(n): Linear Scan ---");
        benchmarkLinearScan(sizes);

        System.out.println("\n--- O(n log n): Arrays.sort ---");
        benchmarkSort(sizes);

        System.out.println("\n--- O(n²): Bubble Sort (small n only) ---");
        benchmarkBubbleSort(new int[]{1_000, 5_000, 10_000});
    }

    // ---------------------------------------------------------------

    private static void benchmarkHashMapGet(int[] sizes) {
        for (int n : sizes) {
            Map<Integer, Integer> map = new HashMap<>(n);
            for (int i = 0; i < n; i++) map.put(i, i);

            long start = System.nanoTime();
            for (int i = 0; i < n; i++) map.get(i);          // n gets, each O(1)
            long elapsed = System.nanoTime() - start;

            System.out.printf("  n=%-9d  %,d ns  (~%.0f ns/op)\n",
                    n, elapsed, (double) elapsed / n);
        }
    }

    private static void benchmarkBinarySearch(int[] sizes) {
        for (int n : sizes) {
            int[] arr = new int[n];
            for (int i = 0; i < n; i++) arr[i] = i * 2;      // sorted, even numbers

            long start = System.nanoTime();
            for (int i = 0; i < n; i++) Arrays.binarySearch(arr, i * 2);
            long elapsed = System.nanoTime() - start;

            System.out.printf("  n=%-9d  %,d ns  (~%.0f ns/op)\n",
                    n, elapsed, (double) elapsed / n);
        }
    }

    private static void benchmarkLinearScan(int[] sizes) {
        for (int n : sizes) {
            List<Integer> list = new ArrayList<>(n);
            for (int i = 0; i < n; i++) list.add(i);

            long start = System.nanoTime();
            long sum = 0;
            for (int v : list) sum += v;                      // prevent JIT elimination
            long elapsed = System.nanoTime() - start;

            System.out.printf("  n=%-9d  %,d ns  (sum=%d)\n", n, elapsed, sum);
        }
    }

    private static void benchmarkSort(int[] sizes) {
        Random rng = new Random(42);
        for (int n : sizes) {
            int[] arr = rng.ints(n).toArray();

            long start = System.nanoTime();
            Arrays.sort(arr);
            long elapsed = System.nanoTime() - start;

            System.out.printf("  n=%-9d  %,d ns\n", n, elapsed);
        }
    }

    private static void benchmarkBubbleSort(int[] sizes) {
        Random rng = new Random(42);
        for (int n : sizes) {
            int[] arr = rng.ints(n).toArray();

            long start = System.nanoTime();
            bubbleSort(arr);
            long elapsed = System.nanoTime() - start;

            System.out.printf("  n=%-9d  %,d ns\n", n, elapsed);
        }
    }

    private static void bubbleSort(int[] arr) {
        int n = arr.length;
        for (int i = 0; i < n - 1; i++) {
            for (int j = 0; j < n - i - 1; j++) {
                if (arr[j] > arr[j + 1]) {
                    int tmp = arr[j];
                    arr[j] = arr[j + 1];
                    arr[j + 1] = tmp;
                }
            }
        }
    }
}
