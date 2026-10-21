package com.masters.socratesai.execution.domain;

import lombok.Builder;
import lombok.ToString;
import lombok.Value;

/** Internal execution contract; never return this type from student endpoints. */
@Value
@Builder
public class ExecutionTestCase {
    long testCaseId;

    int position;

    boolean hidden;

    @ToString.Exclude
    String input;

    @ToString.Exclude
    String expectedOutput;
}
