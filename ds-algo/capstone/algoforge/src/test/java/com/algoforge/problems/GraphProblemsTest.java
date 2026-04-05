package com.algoforge.problems;

import com.algoforge.problems.graphs.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class GraphProblemsTest {

    @Test void numberOfIslands() {
        char[][] grid = {
            {'1','1','0','0','0'},
            {'1','1','0','0','0'},
            {'0','0','1','0','0'},
            {'0','0','0','1','1'}
        };
        assertThat(NumberOfIslands.numIslands(grid)).isEqualTo(3);
    }

    @Test void numberOfIslandsSingleIsland() {
        char[][] grid = {{'1','1','1'},{'0','1','0'},{'1','1','1'}};
        assertThat(NumberOfIslands.numIslands(grid)).isEqualTo(1);
    }

    @Test void courseScheduleCanFinish() {
        assertThat(CourseSchedule.canFinish(2, new int[][]{{1,0}})).isTrue();
    }

    @Test void courseScheduleCannotFinish() {
        assertThat(CourseSchedule.canFinish(2, new int[][]{{1,0},{0,1}})).isFalse();
    }

    @Test void courseScheduleNoDeps() {
        assertThat(CourseSchedule.canFinish(3, new int[][]{})).isTrue();
    }

    @Test void wordLadderFound() {
        int result = WordLadder.ladderLength("hit", "cog",
            List.of("hot","dot","dog","lot","log","cog"));
        assertThat(result).isEqualTo(5);
    }

    @Test void wordLadderNotFound() {
        int result = WordLadder.ladderLength("hit", "cog",
            List.of("hot","dot","dog","lot","log"));
        assertThat(result).isEqualTo(0);
    }

    @Test void networkDelayTime() {
        int result = NetworkDelayTime.networkDelayTime(
            new int[][]{{2,1,1},{2,3,1},{3,4,1}}, 4, 2);
        assertThat(result).isEqualTo(2);
    }

    @Test void networkDelayTimeUnreachable() {
        int result = NetworkDelayTime.networkDelayTime(
            new int[][]{{1,2,1}}, 2, 2);
        assertThat(result).isEqualTo(-1);
    }

    @Test void minCostConnectPointsSmall() {
        int result = MinCostConnectPoints.minCostConnectPoints(
            new int[][]{{0,0},{2,2},{3,10},{5,2},{7,0}});
        assertThat(result).isEqualTo(20);
    }

    @Test void pacificAtlanticBasic() {
        int[][] heights = {
            {1,2,2,3,5},
            {3,2,3,4,4},
            {2,4,5,3,1},
            {6,7,1,4,5},
            {5,1,1,2,4}
        };
        List<List<Integer>> result = PacificAtlanticWaterFlow.pacificAtlantic(heights);
        assertThat(result).isNotEmpty();
    }
}
