package com.masters.socratesai.analyzer.engine;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProgressEstimatorTest {

    private final ProgressEstimator estimator = new ProgressEstimator();

    @Test
    void shouldReturnStableTestPassEstimate() {
        assertThat(estimator.estimateTestsPassed("code", 1L)).isEqualTo(1);
    }

    @Test
    void shouldReturnStableTestFailEstimate() {
        assertThat(estimator.estimateTestsFailed("code", 1L)).isEqualTo(2);
    }
}
