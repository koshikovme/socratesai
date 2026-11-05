package com.masters.socratesai.submission.dto;

import com.masters.socratesai.execution.model.ExecutionStatus;
import com.masters.socratesai.execution.model.ExecutionVerdict;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Builder;

/** List projection deliberately excludes source, test data, tickets and diagnostics. */
@Builder
public record StaffSubmissionResponse(
        UUID id, Long studentId, String studentName, String studentEmail, String groupName,
        Long problemId, Long problemVersionId, Integer versionNumber, String problemTitle,
        Long collectionId, String languageKey, int attemptNumber, ExecutionStatus status, ExecutionVerdict verdict,
        int testsTotal, int testsPassed, int testsFailed, Integer timeConsumedMs, Long memoryConsumedBytes,
        OffsetDateTime createdAt, OffsetDateTime finishedAt
) {
    @Override public String toString() { return "StaffSubmissionResponse[id=" + id + "]"; }
}
