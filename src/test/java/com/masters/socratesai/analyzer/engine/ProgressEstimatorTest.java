package com.masters.socratesai.analyzer.engine;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProgressEstimatorTest {

    private final ProgressEstimator estimator = new ProgressEstimator();

    @Test
    void shouldEstimatePartialProgressForIncompleteCode() {
        assertThat(estimator.estimateTestsPassed("int x = 1;", 1L)).isEqualTo(1);
        assertThat(estimator.estimateTestsFailed("int x = 1;", 1L)).isEqualTo(2);
    }

    @Test
    void shouldEstimateNoRemainingFailuresForLocallyCompleteCode() {
        assertThat(estimator.estimateTestsPassed("return value;", 1L)).isEqualTo(3);
        assertThat(estimator.estimateTestsFailed("return value;", 1L)).isZero();
    }

    @Test
    void shouldNotTreatSuspiciousBinarySearchAsComplete() {
        String code = "int mid = (left + right) / 2; while (left < right) { return mid; }";

        assertThat(estimator.estimateTestsPassed(code, 1L)).isEqualTo(1);
        assertThat(estimator.estimateTestsFailed(code, 1L)).isEqualTo(2);
    }

    @Test
    void shouldTreatTwoPointerCompletionAsLocallyComplete() {
        String code = "while (left < right) { left++; right--; } return true;";

        assertThat(estimator.estimateTestsPassed(code, 1L)).isEqualTo(3);
        assertThat(estimator.estimateTestsFailed(code, 1L)).isZero();
    }

    @Test
    void shouldEstimateNoProgressForUnfinishedCode() {
        assertThat(estimator.estimateTestsPassed("// TODO\nint x = 0;", 1L)).isZero();
        assertThat(estimator.estimateTestsFailed("// TODO\nint x = 0;", 1L)).isEqualTo(3);
    }
}
