package com.algoforge.problems.dp;

/**
 * LC #121 — Best Time to Buy and Sell Stock
 *
 * <p>Given an array prices where prices[i] is the price on day i,
 * find the maximum profit from one buy-sell transaction. Return 0 if none.</p>
 *
 * <b>Pattern:</b> 1-pass DP / Greedy — track min price seen so far.
 *
 * <pre>
 * maxProfit = max(maxProfit, prices[i] - minPrice)
 *
 * Trace: [7,1,5,3,6,4]
 *   i=0: minPrice=7, profit=0
 *   i=1: minPrice=1, profit=0
 *   i=2: profit=5-1=4, maxProfit=4
 *   i=3: profit=3-1=2
 *   i=4: profit=6-1=5, maxProfit=5
 *   i=5: profit=4-1=3
 *   Answer: 5
 * </pre>
 *
 * Time: O(n)  Space: O(1)
 */
public class BestTimeToBuyAndSellStock {
    public static int maxProfit(int[] prices) {
        int minPrice = Integer.MAX_VALUE, maxProfit = 0;
        for (int price : prices) {
            minPrice = Math.min(minPrice, price);
            maxProfit = Math.max(maxProfit, price - minPrice);
        }
        return maxProfit;
    }
}
