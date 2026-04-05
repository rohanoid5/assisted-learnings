package com.algoforge.problems.arrays;

/**
 * LC #238 — Product of Array Except Self
 *
 * <p>For each index i, compute the product of all elements except nums[i].
 * Cannot use division. Must run in O(n).</p>
 *
 * <b>Pattern:</b> Prefix & Suffix Products
 *
 * <pre>
 * nums = [1, 2, 3, 4]
 *
 * prefix[i] = product of all elements to the LEFT of i:
 *   prefix = [1, 1, 2, 6]   (prefix[0]=1 by convention)
 *
 * suffix[i] = product of all elements to the RIGHT of i:
 *   suffix = [24, 12, 4, 1]  (suffix[n-1]=1 by convention)
 *
 * result[i] = prefix[i] * suffix[i]:
 *   result = [24, 12, 8, 6]
 *
 * Space optimisation: compute prefix into result[], then multiply suffix on the fly.
 * </pre>
 *
 * Time: O(n)  Space: O(1) extra (result array not counted)
 */
public class ProductExceptSelf {

    public static int[] productExceptSelf(int[] nums) {
        int n = nums.length;
        int[] result = new int[n];

        // Pass 1: fill result[i] with the product of all elements to the LEFT of i
        result[0] = 1;
        for (int i = 1; i < n; i++) {
            result[i] = result[i - 1] * nums[i - 1];
        }

        // Pass 2: multiply each result[i] by the product of all elements to the RIGHT
        int suffixProduct = 1;
        for (int i = n - 1; i >= 0; i--) {
            result[i] *= suffixProduct;
            suffixProduct *= nums[i];
        }
        return result;
    }
}
