package com.masters.socratesai.execution.infrastructure.reference;

import com.masters.socratesai.execution.domain.*;
import com.masters.socratesai.execution.domain.port.*;
import com.masters.socratesai.execution.infrastructure.judge0.SandboxAsyncRuntime;
import com.masters.socratesai.execution.model.ExecutionStatus;
import lombok.RequiredArgsConstructor;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@RequiredArgsConstructor
public class BatchedReferenceExecutionAdapter implements ReferenceExecutionPort {
    private final CodeExecutionPort execution;
    private final SandboxAsyncRuntime runtime;
    private final ExecutionResultAggregator aggregator;

    @Override
    public CompletableFuture<ExecutionBatchResult> verify(List<ExecutionBatch> batches, Instant deadline) {
        var operation = runtime.<ExecutionBatchResult>start(deadline, null);
        if (batches == null || batches.isEmpty() || batches.size() > 1000) operation.fail(SandboxFailureCode.INVALID_REQUEST);
        else {
            try { next(operation, List.copyOf(batches), new ArrayList<>(), 0); }
            catch (RuntimeException ex) { operation.fail(SandboxFailureCode.INVALID_RESPONSE); }
        }
        return operation.getResult();
    }

    private void next(SandboxAsyncRuntime.Operation<ExecutionBatchResult> operation, List<ExecutionBatch> batches,
                      List<CaseExecutionResult> results, int index) {
        if (operation.isDone()) return;
        if (index == batches.size()) { operation.complete(aggregator.aggregate(results)); return; }
        var batch = batches.get(index);
        operation.track(execution.submitBatch(batch), (submitted, error) -> {
            if (error != null) { operation.fail(code(error)); return; }
            operation.track(execution.pollBatch(submitted), (result, pollError) -> {
                if (pollError != null) { operation.fail(code(pollError)); return; }
                if (!complete(batch, result)) { operation.fail(SandboxFailureCode.INVALID_RESPONSE); return; }
                if (result.getStatus() == ExecutionStatus.SYSTEM_ERROR) {
                    operation.fail(result.getFailureCode() == null ? SandboxFailureCode.PROVIDER_INTERNAL_ERROR : result.getFailureCode());
                    return;
                }
                // Reference diagnostics are deliberately not retained, even for public sample cases.
                result.getCases().forEach(item -> results.add(CaseExecutionResult.builder().testCaseId(item.getTestCaseId())
                        .position(item.getPosition()).hidden(item.isHidden()).status(item.getStatus()).verdict(item.getVerdict()).build()));
                operation.schedule(() -> next(operation, batches, results, index + 1), java.time.Duration.ZERO);
            });
        });
    }

    private boolean complete(ExecutionBatch batch, ExecutionBatchResult result) {
        if (result == null) return false;
        if (result.getStatus() == ExecutionStatus.SYSTEM_ERROR) return true;
        if (result.getStatus() != ExecutionStatus.FINISHED || result.getCases() == null || result.getCases().size() != batch.getTests().size()) return false;
        var expected = batch.getTests().stream().collect(Collectors.toMap(ExecutionTestCase::getTestCaseId, test -> test));
        return result.getCases().stream().map(CaseExecutionResult::getTestCaseId).distinct().count() == expected.size()
                && result.getCases().stream().allMatch(item -> {
                    var test = expected.get(item.getTestCaseId());
                    return test != null && item.getStatus() == ExecutionStatus.FINISHED && item.getVerdict() != null
                            && test.getPosition() == item.getPosition() && test.isHidden() == item.isHidden();
                });
    }

    private SandboxFailureCode code(Throwable failure) {
        Throwable current = failure;
        for (int depth = 0; depth < 8 && current != null; depth++, current = current.getCause()) {
            if (current instanceof SandboxException exception) return exception.getCode();
        }
        return SandboxFailureCode.PROVIDER_INTERNAL_ERROR;
    }
}
