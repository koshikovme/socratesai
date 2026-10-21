package com.masters.socratesai.execution.domain;

import lombok.Builder;
import lombok.ToString;
import lombok.Value;

/** Internal execution contract; never return this type from student endpoints. */
@Value
@Builder
public class ExecutionTicket {
    long testCaseId;

    int position;

    boolean hidden;

    @ToString.Exclude
    String expectedOutput;

    @ToString.Exclude
    String token;
}
