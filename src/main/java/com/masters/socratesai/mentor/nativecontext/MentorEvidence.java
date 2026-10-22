package com.masters.socratesai.mentor.nativecontext;

import com.masters.socratesai.common.model.EvidenceOrigin;
import com.masters.socratesai.execution.model.ExecutionVerdict;
import com.masters.socratesai.analyzer.dto.AnalyzerResult;
import lombok.*;
import java.util.*;

@Value @Builder
public class MentorEvidence {
    boolean executionKnown;
    EvidenceOrigin origin;
    UUID jobId;
    UUID submissionId;
    Boolean compileSuccess;
    Integer testsPassed;
    Integer testsFailed;
    ExecutionVerdict verdict;

    public static MentorEvidence unknown() { return builder().origin(EvidenceOrigin.STATIC_HEURISTIC).build(); }

    /** Unknown public/log values stay null; primitive legacy features use false/zero sentinels. */
    public AnalyzerResult features(String code) {
        var result = new AnalyzerResult();
        result.setErrorType(errorType()); result.setSeverity(executionKnown ? (verdict == ExecutionVerdict.ACCEPTED ? "NONE" : "MEDIUM") : "UNKNOWN");
        result.setCompileSuccess(Boolean.TRUE.equals(compileSuccess)); result.setTestsPassed(testsPassed == null ? 0 : testsPassed);
        result.setTestsFailed(testsFailed == null ? 0 : testsFailed); result.setCodeLines((int) code.lines().count());
        result.setSignals(Map.of("executionKnown", executionKnown, "evidenceOrigin", origin.name()));
        return result;
    }
    private String errorType() {
        if (!executionKnown) return "EXECUTION_UNKNOWN";
        return switch (verdict) {
            case ACCEPTED -> origin == EvidenceOrigin.JUDGED_SUBMISSION ? "SUCCESS" : "SAMPLE_PASSED";
            case COMPILATION_ERROR -> "SYNTAX_ERROR";
            case WRONG_ANSWER -> "WRONG_CONDITION";
            case RUNTIME_ERROR -> "RUNTIME_ERROR";
            case TIME_LIMIT_EXCEEDED -> "TIME_LIMIT_EXCEEDED";
            case MEMORY_LIMIT_EXCEEDED -> "MEMORY_LIMIT_EXCEEDED";
            case OUTPUT_LIMIT_EXCEEDED -> "OUTPUT_LIMIT_EXCEEDED";
        };
    }
}
