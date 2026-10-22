package com.masters.socratesai.execution.service;

import com.masters.socratesai.execution.model.*;
import java.util.UUID;

/** Internal event; listeners must use AFTER_COMMIT. HTTP is the durable recovery path. */
public record SubmissionJudged(UUID submissionId, Long studentId, ExecutionStatus status, ExecutionVerdict verdict) {}
