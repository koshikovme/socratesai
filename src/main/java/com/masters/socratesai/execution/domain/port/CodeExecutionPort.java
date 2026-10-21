package com.masters.socratesai.execution.domain.port;

import com.masters.socratesai.execution.domain.ExecutionBatch;
import com.masters.socratesai.execution.domain.ExecutionBatchResult;
import com.masters.socratesai.execution.domain.SubmittedExecutionBatch;
import java.util.concurrent.CompletableFuture;

/** No HTTP, Spring or JPA dependencies. Persist tickets before starting polling. */
public interface CodeExecutionPort {
    CompletableFuture<SubmittedExecutionBatch> submitBatch(ExecutionBatch batch);

    /** Resumes known tickets without ever resubmitting source. Cancellation stops local I/O. */
    CompletableFuture<ExecutionBatchResult> pollBatch(SubmittedExecutionBatch batch);
}
