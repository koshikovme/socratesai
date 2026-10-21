package com.masters.socratesai.execution.dto;

import com.masters.socratesai.execution.domain.SandboxFailureCode;
import com.masters.socratesai.execution.model.ExecutionStatus;
import com.masters.socratesai.execution.model.ExecutionVerdict;
import java.util.List;

public record StudentExecutionResultResponse(ExecutionStatus status, ExecutionVerdict verdict,
        List<SampleExecutionResultResponse> samples, SandboxFailureCode failureCode) {}
