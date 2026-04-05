package com.algoforge;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Smoke test — verifies the project compiles and JUnit 5 is wired correctly.
 * Run with: mvn test
 */
class AlgoForgeTest {

    @Test
    void projectBuildsAndTestsRun() {
        // This test exists only to validate the project setup.
        // The AlgoForge benchmark is a main-class utility, not a JUnit test.
        assertThat(true).isTrue();
    }
}
