package com.masters.socratesai.execution.domain;

import com.masters.socratesai.task.model.OutputComparison;
import java.time.Instant;
import java.util.List;
import lombok.Builder;
import lombok.Value;

/** Internal execution contract; never return this type from student endpoints. */
@Value
@Builder
public class SubmittedExecutionBatch {
    String runtimeKey;

    String runtimeVersion;

    ExecutionLimits limits;

    OutputComparison comparison;

    List<ExecutionTicket> tickets;

    Instant deadline;
}
