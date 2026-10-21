package com.masters.socratesai.execution.domain;

import lombok.Builder;
import lombok.Value;

/** Internal execution contract; never return this type from student endpoints. */
@Value
@Builder
public class ExecutionLimits {
    int timeLimitMs;

    int wallTimeLimitMs;

    int memoryLimitKb;

    int outputLimitBytes;
}
