package com.masters.socratesai.submission.dto;

import com.masters.socratesai.execution.model.ExecutionStatus;
import com.masters.socratesai.execution.model.ExecutionVerdict;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Builder;

@Builder
public record SubmissionResponse(
        UUID id,
        Long problemId,
        Long problemVersionId,
        String languageKey,
        String runtimeKey,
        String runtimeVersion,
        int attemptNumber,
        ExecutionStatus status,
        ExecutionVerdict verdict,
        int testsTotal,
        int testsPassed,
        int testsFailed,
        Integer timeConsumedMs,
        Long memoryConsumedBytes,
        OffsetDateTime createdAt,
        OffsetDateTime finishedAt
) {}
