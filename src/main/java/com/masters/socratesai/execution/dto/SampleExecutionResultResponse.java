package com.masters.socratesai.execution.dto;

import com.masters.socratesai.execution.model.ExecutionStatus;
import com.masters.socratesai.execution.model.ExecutionVerdict;

/** Samples only. Hidden cases have no detailed student projection. */
public record SampleExecutionResultResponse(int position, ExecutionStatus status, ExecutionVerdict verdict,
        Integer timeConsumedMs, Long memoryConsumedBytes, String stdout, String stderr, String compileOutput) {}
