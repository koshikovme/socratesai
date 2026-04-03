package com.masters.socratesai.mentor.policy;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.masters.socratesai.analyzer.dto.AnalyzerResult;
import com.masters.socratesai.mentor.dto.StudentContextDto;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record PolicyFeatures(
        String errorType,
        String severity,
        boolean compileSuccess,
        int testsPassed,
        int testsFailed,
        int sameErrorCount,
        int totalErrorsSeen,
        int attemptNo,
        String lastFeedbackAction,
        Boolean lastFeedbackSuccess,
        boolean hasSuspiciousRegion,
        int codeLines,
        int totalFeedbackCountInSession
) {

    public static PolicyFeatures from(AnalyzerResult analyzer, StudentContextDto context, Integer attemptNo) {
        return new PolicyFeatures(
                analyzer.getErrorType(),
                analyzer.getSeverity(),
                analyzer.isCompileSuccess(),
                analyzer.getTestsPassed(),
                analyzer.getTestsFailed(),
                context.getSameErrorCount(),
                context.getTotalErrorsSeen(),
                attemptNo == null ? 0 : attemptNo,
                context.getLastFeedbackAction(),
                context.getLastFeedbackSuccess(),
                hasText(analyzer.getSuspiciousRegion()),
                analyzer.getCodeLines(),
                context.getTotalFeedbackCountInSession()
        );
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
