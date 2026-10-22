package com.masters.socratesai.execution.dto;

import com.masters.socratesai.execution.model.*;
import com.masters.socratesai.submission.dto.SubmissionStatusResponse.CaseSummary;
import java.time.OffsetDateTime;
import java.util.*;

public record SampleRunStatusResponse(UUID id, Long problemVersionId, ExecutionStatus status, ExecutionVerdict verdict,
                                      OffsetDateTime expiresAt, boolean retryScheduled, OffsetDateTime nextAttemptAt, List<CaseSummary> cases) {}
