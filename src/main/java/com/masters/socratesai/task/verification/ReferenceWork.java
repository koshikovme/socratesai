package com.masters.socratesai.task.verification;

import com.masters.socratesai.execution.domain.ExecutionBatch;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Process-local immutable execution snapshot. It is never serialized to students or logs. */
public record ReferenceWork(UUID id, List<ExecutionBatch> batches, Instant deadline) {
    @Override public String toString() { return "ReferenceWork[id=" + id + "]"; }
}
