package com.masters.socratesai.execution.domain.port;

import com.masters.socratesai.execution.domain.*;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/** Executes every bounded batch under one admission slot and deadline. No persistent orchestration assumptions. */
public interface ReferenceExecutionPort {
    CompletableFuture<ExecutionBatchResult> verify(List<ExecutionBatch> batches, Instant deadline);
}
