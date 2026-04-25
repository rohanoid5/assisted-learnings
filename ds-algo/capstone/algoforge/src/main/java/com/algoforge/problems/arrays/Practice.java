package com.algoforge.problems.arrays;

import java.util.ArrayList;
import java.util.HashMap;

class Practice {
    public static void main(String[] args) {
        System.out.println("Practice Array Problems");
        int[] nums = {1,2,3,4,5,6,7};
        int k = 3;
        Practice p = new Practice();
        p.rotate(nums, k);
        for(int num : nums) {
            System.out.print(num + " ");
        }
        System.out.println();

        System.out.println("Find Duplicates");
        int[] nums2 = {1,2,2,3,3,3,4,4,5,6,7};
        findDupicates(nums2);
    }

    public void rotate(int[] nums, int k) {
        int n = nums.length;
        k = k % n;
        reverse(nums, 0, n - 1);
        reverse(nums, 0, k - 1);
        reverse(nums, k, n - 1);
    }

    public static void reverse(int[] nums, int lo, int hi) {
        while(lo < hi) {
            int temp = nums[lo];
            nums[lo] = nums[hi];
            nums[hi] = temp;
            lo++;
            hi--;
        }
    }

    public static void findDupicates(int[] nums) {
        HashMap<Integer, Boolean> map = new HashMap<>();
        ArrayList<Integer> list = new ArrayList<>();

        for(int num: nums) {
            if(!map.containsKey(num)) {
                map.put(num, true);
                list.add(num);
            }
        }

        for(int num: list) {
            System.out.print(num + " ");
        }
    }
}