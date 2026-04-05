package com.algoforge.problems;

import com.algoforge.problems.stacksqueues.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class StacksQueuesProblemsTest {

    @Test void validParenthesesBalanced() {
        assertThat(ValidParentheses.isValid("()[]{}")).isTrue();
        assertThat(ValidParentheses.isValid("({[]})")).isTrue();
    }

    @Test void validParenthesesUnbalanced() {
        assertThat(ValidParentheses.isValid("(]")).isFalse();
        assertThat(ValidParentheses.isValid("([)]")).isFalse();
        assertThat(ValidParentheses.isValid("{")).isFalse();
    }

    @Test void dailyTemperaturesBasic() {
        assertThat(DailyTemperatures.dailyTemperatures(new int[]{73,74,75,71,69,72,76,73}))
            .containsExactly(1,1,4,2,1,1,0,0);
    }

    @Test void dailyTemperaturesNoWarmerDay() {
        assertThat(DailyTemperatures.dailyTemperatures(new int[]{30,40,50,60}))
            .containsExactly(1,1,1,0);
    }

    @Test void evaluateRPNBasic() {
        assertThat(EvaluateRPN.evalRPN(new String[]{"2","1","+","3","*"})).isEqualTo(9);
        assertThat(EvaluateRPN.evalRPN(new String[]{"4","13","5","/","+"})).isEqualTo(6);
    }

    @Test void slidingWindowMaximumBasic() {
        assertThat(SlidingWindowMaximum.maxSlidingWindow(new int[]{1,3,-1,-3,5,3,6,7}, 3))
            .containsExactly(3,3,5,5,6,7);
    }

    @Test void slidingWindowMaximumSingleElement() {
        assertThat(SlidingWindowMaximum.maxSlidingWindow(new int[]{1}, 1))
            .containsExactly(1);
    }

    @Test void largestRectangleHistogram() {
        assertThat(LargestRectangleHistogram.largestRectangleArea(new int[]{2,1,5,6,2,3}))
            .isEqualTo(10);
        assertThat(LargestRectangleHistogram.largestRectangleArea(new int[]{2,4}))
            .isEqualTo(4);
    }
}
