package com.masters.socratesai.submission.dto;

import com.masters.socratesai.execution.model.ExecutionStatus;
import com.masters.socratesai.execution.model.ExecutionVerdict;
import jakarta.validation.constraints.*;

public record SubmissionResultsFilter(
        @Positive Long problemId,
        @Positive Long problemVersionId,
        @Positive Long studentId,
        @Size(max = 120) String query,
        @Pattern(regexp = "[a-z][a-z0-9]{0,39}") String languageKey,
        ExecutionStatus status,
        ExecutionVerdict verdict
) {}
