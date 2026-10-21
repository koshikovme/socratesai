package com.masters.socratesai.execution.domain;

import com.masters.socratesai.execution.model.ExecutionStatus;
import com.masters.socratesai.execution.model.ExecutionVerdict;
import lombok.Builder;
import lombok.ToString;
import lombok.Value;

/** Internal execution contract; never return this type from student endpoints. */
@Value
@Builder
public class CaseExecutionResult {
    long testCaseId;

    int position;

    boolean hidden;

    ExecutionStatus status;

    ExecutionVerdict verdict;

    Integer timeConsumedMs;

    Long memoryConsumedBytes;

    @ToString.Exclude
    ExecutionDiagnostics diagnostics;

    SandboxFailureCode failureCode;
}
