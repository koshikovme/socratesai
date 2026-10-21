package com.masters.socratesai.execution.domain;

import com.masters.socratesai.execution.model.ExecutionStatus;
import com.masters.socratesai.execution.model.ExecutionVerdict;
import java.util.List;
import lombok.Builder;
import lombok.Value;

/** Internal execution contract; never return this type from student endpoints. */
@Value
@Builder
public class ExecutionBatchResult {
    ExecutionStatus status;

    ExecutionVerdict verdict;

    List<CaseExecutionResult> cases;

    SandboxFailureCode failureCode;
}
