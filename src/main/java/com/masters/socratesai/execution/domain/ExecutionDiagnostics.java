package com.masters.socratesai.execution.domain;

import lombok.Builder;
import lombok.ToString;
import lombok.Value;

/** Internal execution contract; never return this type from student endpoints. */
@Value
@Builder
public class ExecutionDiagnostics {
    @ToString.Exclude
    String stdout;

    @ToString.Exclude
    String stderr;

    @ToString.Exclude
    String compileOutput;
}
