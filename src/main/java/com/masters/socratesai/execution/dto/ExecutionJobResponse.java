package com.masters.socratesai.execution.dto;

import com.masters.socratesai.execution.model.ExecutionMode;
import com.masters.socratesai.execution.model.ExecutionStatus;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Builder;

@Builder
public record ExecutionJobResponse(
        UUID id,
        Long problemVersionId,
        UUID submissionId,
        UUID runId,
        ExecutionMode mode,
        ExecutionStatus status,
        int attemptCount,
        int maxAttempts,
        int generation,
        OffsetDateTime nextAttemptAt,
        OffsetDateTime deadlineAt,
        OffsetDateTime finishedAt,
        String lastErrorCode
) {}
