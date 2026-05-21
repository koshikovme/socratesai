package com.masters.socratesai.analyzer.engine;

import org.springframework.stereotype.Component;

@Component
public class ProgressEstimator {
    public Integer estimateTestsPassed(String code, Long taskId) {
        if (isBlankOrUnfinished(code)) {
            return 0;
        }
        return looksLocallyComplete(code) ? 3 : 1;
    }

    public Integer estimateTestsFailed(String code, Long taskId) {
        if (isBlankOrUnfinished(code)) {
            return 3;
        }
        return looksLocallyComplete(code) ? 0 : 2;
    }

    private boolean isBlankOrUnfinished(String code) {
        if (code == null || code.isBlank()) {
            return true;
        }
        String normalized = code.toLowerCase();
        return normalized.contains("todo") || normalized.contains("unsupportedoperationexception");
    }

    private boolean looksLocallyComplete(String code) {
        String normalized = code.toLowerCase();
        String compact = normalized.replaceAll("\\s+", "");
        boolean hasCompletionSignal = normalized.contains("return")
                || normalized.contains("system.out.print")
                || normalized.contains("arrays.")
                || normalized.contains("collections.");
        boolean hasSuspiciousLoopBoundary = normalized.contains("for") && normalized.contains("<=");
        boolean hasBinarySearchContext = normalized.contains("mid") || normalized.contains("binary search");
        boolean hasSuspiciousBinaryBoundary = hasBinarySearchContext
                && (normalized.contains("left < right") || normalized.contains("low < high"));
        boolean hasSuspiciousCondition = compact.contains("while(true)");
        return hasCompletionSignal && !hasSuspiciousLoopBoundary && !hasSuspiciousBinaryBoundary && !hasSuspiciousCondition;
    }
}
