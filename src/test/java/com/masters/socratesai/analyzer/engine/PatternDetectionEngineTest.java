package com.masters.socratesai.analyzer.engine;

import com.masters.socratesai.analyzer.model.ErrorType;
import com.masters.socratesai.analyzer.model.PatternDetectionResult;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PatternDetectionEngineTest {

    private final PatternDetectionEngine engine = new PatternDetectionEngine();

    @Test
    void shouldTreatBlankCodeAsStuckNoProgress() {
        PatternDetectionResult result = engine.detect(" ", 1L);

        assertThat(result.getErrorType()).isEqualTo(ErrorType.STUCK_NO_PROGRESS);
        assertThat(result.getSeverity()).isEqualTo("MEDIUM");
        assertThat(result.getSuspiciousRegion()).isEqualTo("empty editor");
        assertThat(result.getSignals()).isEmpty();
    }

    @Test
    void shouldDetectOffByOneLoopBoundary() {
        PatternDetectionResult result = engine.detect("for (int i = 0; i <= n; i++) {}", 1L);

        assertThat(result.getErrorType()).isEqualTo(ErrorType.OFF_BY_ONE);
        assertThat(result.getSeverity()).isEqualTo("MEDIUM");
        assertThat(result.getSuspiciousRegion()).isEqualTo("for loop condition");
        assertThat(result.getSignals()).containsEntry("loopBoundarySuspicious", true);
    }

    @Test
    void shouldDetectWrongConditionForInfiniteWhileLoop() {
        PatternDetectionResult result = engine.detect("while (true) { work(); }", 1L);

        assertThat(result.getErrorType()).isEqualTo(ErrorType.WRONG_CONDITION);
        assertThat(result.getSeverity()).isEqualTo("MEDIUM");
        assertThat(result.getSuspiciousRegion()).isEqualTo("while condition");
        assertThat(result.getSignals()).containsEntry("possibleInfiniteLoop", true);
    }

    @Test
    void shouldDetectBinarySearchBoundaryMistake() {
        PatternDetectionResult result = engine.detect(
                "int mid = (left + right) / 2; while (left < right) { return mid; }",
                1L
        );

        assertThat(result.getErrorType()).isEqualTo(ErrorType.WRONG_LOOP_BOUNDARY);
        assertThat(result.getSeverity()).isEqualTo("MEDIUM");
        assertThat(result.getSuspiciousRegion()).isEqualTo("binary search loop boundary");
        assertThat(result.getSignals()).containsEntry("binarySearchBoundarySuspicious", true);
    }

    @Test
    void shouldDetectMissingNullOrEmptyGuard() {
        PatternDetectionResult result = engine.detect("return value.equals(target);", 1L);

        assertThat(result.getErrorType()).isEqualTo(ErrorType.POSSIBLE_NULL_ACCESS);
        assertThat(result.getSeverity()).isEqualTo("MEDIUM");
        assertThat(result.getSuspiciousRegion()).isEqualTo("missing null or empty guard");
        assertThat(result.getSignals()).containsEntry("missingGuard", true);
    }

    @Test
    void shouldDetectUnfinishedImplementationAsStuckNoProgress() {
        PatternDetectionResult result = engine.detect("// TODO\nint x = 0;", 1L);

        assertThat(result.getErrorType()).isEqualTo(ErrorType.STUCK_NO_PROGRESS);
        assertThat(result.getSeverity()).isEqualTo("MEDIUM");
        assertThat(result.getSuspiciousRegion()).isEqualTo("unfinished implementation");
        assertThat(result.getSignals()).containsEntry("unfinishedImplementation", true);
    }

    @Test
    void shouldReturnUnknownWhenNoPatternMatches() {
        PatternDetectionResult result = engine.detect("return value;", 1L);

        assertThat(result.getErrorType()).isEqualTo(ErrorType.UNKNOWN);
        assertThat(result.getSeverity()).isEqualTo("LOW");
        assertThat(result.getSuspiciousRegion()).isEqualTo("logic block");
        assertThat(result.getSignals()).isEmpty();
    }
}
