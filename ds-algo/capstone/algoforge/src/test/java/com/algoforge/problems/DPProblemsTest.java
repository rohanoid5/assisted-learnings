package com.algoforge.problems;

import com.algoforge.problems.dp.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class DPProblemsTest {

    @Test void climbingStairs() {
        assertThat(ClimbingStairs.climbStairs(1)).isEqualTo(1);
        assertThat(ClimbingStairs.climbStairs(2)).isEqualTo(2);
        assertThat(ClimbingStairs.climbStairs(5)).isEqualTo(8);
    }

    @Test void houseRobber() {
        assertThat(HouseRobber.rob(new int[]{1,2,3,1})).isEqualTo(4);
        assertThat(HouseRobber.rob(new int[]{2,7,9,3,1})).isEqualTo(12);
    }

    @Test void coinChange() {
        assertThat(CoinChange.coinChange(new int[]{1,5,11}, 15)).isEqualTo(3); // 5+5+5
        assertThat(CoinChange.coinChange(new int[]{2}, 3)).isEqualTo(-1);
    }

    @Test void longestIncreasingSubsequence() {
        assertThat(LongestIncreasingSubsequence.lengthOfLIS(new int[]{10,9,2,5,3,7,101,18})).isEqualTo(4);
        assertThat(LongestIncreasingSubsequence.lengthOfLISDp(new int[]{0,1,0,3,2,3})).isEqualTo(4);
    }

    @Test void longestCommonSubsequence() {
        assertThat(LongestCommonSubsequence.longestCommonSubsequence("abcde", "ace")).isEqualTo(3);
        assertThat(LongestCommonSubsequence.longestCommonSubsequence("abc", "abc")).isEqualTo(3);
        assertThat(LongestCommonSubsequence.longestCommonSubsequence("abc", "def")).isEqualTo(0);
    }

    @Test void editDistance() {
        assertThat(EditDistance.minDistance("horse", "ros")).isEqualTo(3);
        assertThat(EditDistance.minDistance("intention", "execution")).isEqualTo(5);
    }

    @Test void partitionEqualSubsetSum() {
        assertThat(PartitionEqualSubsetSum.canPartition(new int[]{1,5,11,5})).isTrue();
        assertThat(PartitionEqualSubsetSum.canPartition(new int[]{1,2,3,5})).isFalse();
    }

    @Test void uniquePaths() {
        assertThat(UniquePaths.uniquePaths(3, 7)).isEqualTo(28);
        int[][] grid = {{0,0,0},{0,1,0},{0,0,0}};
        assertThat(UniquePaths.uniquePathsWithObstacles(grid)).isEqualTo(2);
    }

    @Test void wordBreak() {
        assertThat(WordBreak.wordBreak("leetcode", List.of("leet","code"))).isTrue();
        assertThat(WordBreak.wordBreak("applepenapple", List.of("apple","pen"))).isTrue();
        assertThat(WordBreak.wordBreak("catsandog", List.of("cats","dog","sand","and","cat"))).isFalse();
    }

    @Test void decodeWays() {
        assertThat(DecodeWays.numDecodings("12")).isEqualTo(2);
        assertThat(DecodeWays.numDecodings("226")).isEqualTo(3);
        assertThat(DecodeWays.numDecodings("06")).isEqualTo(0);
    }

    @Test void jumpGame() {
        assertThat(JumpGame.canJump(new int[]{2,3,1,1,4})).isTrue();
        assertThat(JumpGame.canJump(new int[]{3,2,1,0,4})).isFalse();
    }

    @Test void longestPalindromicSubstring() {
        String result = LongestPalindromicSubstring.longestPalindrome("babad");
        assertThat(result).isIn("bab", "aba");
        assertThat(LongestPalindromicSubstring.longestPalindrome("cbbd")).isEqualTo("bb");
    }

    @Test void longestPalindromicSubsequence() {
        assertThat(LongestPalindromicSubsequence.longestPalindromeSubseq("bbbab")).isEqualTo(4);
        assertThat(LongestPalindromicSubsequence.longestPalindromeSubseq("cbbd")).isEqualTo(2);
    }

    @Test void maximumProductSubarray() {
        assertThat(MaximumProductSubarray.maxProduct(new int[]{2,3,-2,4})).isEqualTo(6);
        assertThat(MaximumProductSubarray.maxProduct(new int[]{-2,0,-1})).isEqualTo(0);
    }

    @Test void bestTimeToBuyAndSellStock() {
        assertThat(BestTimeToBuyAndSellStock.maxProfit(new int[]{7,1,5,3,6,4})).isEqualTo(5);
        assertThat(BestTimeToBuyAndSellStock.maxProfit(new int[]{7,6,4,3,1})).isEqualTo(0);
    }

    @Test void maximalSquare() {
        char[][] matrix = {
            {'1','0','1','0','0'},
            {'1','0','1','1','1'},
            {'1','1','1','1','1'},
            {'1','0','0','1','0'}
        };
        assertThat(MaximalSquare.maximalSquare(matrix)).isEqualTo(4);
    }

    @Test void coinChangeII() {
        assertThat(CoinChangeII.change(5, new int[]{1,2,5})).isEqualTo(4);
        assertThat(CoinChangeII.change(3, new int[]{2})).isEqualTo(0);
    }

    @Test void combinationSumIV() {
        assertThat(CombinationSumIV.combinationSum4(new int[]{1,2,3}, 4)).isEqualTo(7);
    }

    @Test void palindromicSubstringsCount() {
        assertThat(PalindromicSubstringsCount.countSubstrings("abc")).isEqualTo(3);
        assertThat(PalindromicSubstringsCount.countSubstrings("aaa")).isEqualTo(6);
    }

    @Test void zeroOneKnapsack() {
        assertThat(ZeroOneKnapsack.knapsack(
            new int[]{1,3,4,5}, new int[]{1,4,5,7}, 7)).isEqualTo(9);
    }

    @Test void trappingRainWater() {
        assertThat(TrappingRainWater.trap(new int[]{0,1,0,2,1,0,1,3,2,1,2,1})).isEqualTo(6);
        assertThat(TrappingRainWater.trapTwoPointer(new int[]{4,2,0,3,2,5})).isEqualTo(9);
    }

    @Test void regularExpressionMatching() {
        assertThat(RegularExpressionMatching.isMatch("aa", "a")).isFalse();
        assertThat(RegularExpressionMatching.isMatch("aa", "a*")).isTrue();
        assertThat(RegularExpressionMatching.isMatch("ab", ".*")).isTrue();
    }

    @Test void burstBalloons() {
        assertThat(BurstBalloons.maxCoins(new int[]{3,1,5,8})).isEqualTo(167);
        assertThat(BurstBalloons.maxCoins(new int[]{1,5})).isEqualTo(10);
    }
}
