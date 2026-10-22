package com.masters.socratesai.task.dto;

import com.masters.socratesai.execution.model.*;
import java.time.OffsetDateTime;
import java.util.UUID;

public record ReferenceVerificationResponse(UUID id, Long problemVersionId, ExecutionStatus status, ExecutionVerdict verdict,
        int testsTotal, int testsPassed, String failureCode, OffsetDateTime validUntil) {}
