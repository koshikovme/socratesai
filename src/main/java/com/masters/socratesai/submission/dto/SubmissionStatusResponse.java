package com.masters.socratesai.submission.dto;

import com.masters.socratesai.execution.model.*;
import java.util.List;

/** Deliberately has no source, test IDs, tokens, inputs, expected output or diagnostics. */
public record SubmissionStatusResponse(SubmissionResponse submission, boolean retryScheduled, java.time.OffsetDateTime nextAttemptAt, List<CaseSummary> cases) {
    public record CaseSummary(int position, boolean hidden, CaseExecutionStatus status, ExecutionVerdict verdict,
                              Integer timeConsumedMs, Long memoryConsumedBytes) {}
}
