package com.masters.socratesai.execution.domain;

import com.masters.socratesai.execution.model.ExecutionStatus;
import com.masters.socratesai.execution.model.ExecutionVerdict;
import java.util.List;

public final class ExecutionResultAggregator {

    public ExecutionBatchResult aggregate(List<CaseExecutionResult> cases) {
        if (cases.isEmpty()) return ExecutionBatchResult.builder().status(ExecutionStatus.SYSTEM_ERROR)
                .failureCode(SandboxFailureCode.INVALID_RESPONSE).cases(List.of()).build();
        var failure = cases.stream().filter(item -> item.getStatus() == ExecutionStatus.SYSTEM_ERROR).findFirst();
        if (failure.isPresent()) return ExecutionBatchResult.builder().status(ExecutionStatus.SYSTEM_ERROR)
                .failureCode(failure.get().getFailureCode()).cases(List.copyOf(cases)).build();
        boolean unfinished = cases.stream().anyMatch(item -> item.getStatus() != ExecutionStatus.FINISHED);
        if (unfinished) {
            boolean running = cases.stream().anyMatch(item -> item.getStatus() != ExecutionStatus.QUEUED);
            return ExecutionBatchResult.builder().status(running ? ExecutionStatus.RUNNING : ExecutionStatus.QUEUED)
                    .cases(List.copyOf(cases)).build();
        }
        if (cases.stream().anyMatch(item -> item.getVerdict() == null)) return ExecutionBatchResult.builder()
                .status(ExecutionStatus.SYSTEM_ERROR).failureCode(SandboxFailureCode.INVALID_RESPONSE).cases(List.copyOf(cases)).build();
        ExecutionVerdict verdict = cases.stream().map(CaseExecutionResult::getVerdict)
                .filter(value -> value != ExecutionVerdict.ACCEPTED).findFirst().orElse(ExecutionVerdict.ACCEPTED);
        return ExecutionBatchResult.builder().status(ExecutionStatus.FINISHED).verdict(verdict).cases(List.copyOf(cases)).build();
    }
}
