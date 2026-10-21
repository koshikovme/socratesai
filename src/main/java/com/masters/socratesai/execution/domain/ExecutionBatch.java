package com.masters.socratesai.execution.domain;

import com.masters.socratesai.task.model.OutputComparison;
import java.time.Instant;
import java.util.List;
import lombok.Builder;
import lombok.ToString;
import lombok.Value;

/** Internal execution contract; never return this type from student endpoints. */
@Value
@Builder
public class ExecutionBatch {
    String languageKey;

    String runtimeKey;

    String runtimeVersion;

    @ToString.Exclude
    String sourceCode;

    ExecutionLimits limits;

    OutputComparison comparison;

    List<ExecutionTestCase> tests;

    Instant deadline;
}
