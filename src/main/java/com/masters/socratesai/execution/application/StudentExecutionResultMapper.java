package com.masters.socratesai.execution.application;

import com.masters.socratesai.execution.domain.ExecutionBatchResult;
import com.masters.socratesai.execution.dto.SampleExecutionResultResponse;
import com.masters.socratesai.execution.dto.StudentExecutionResultResponse;
import org.springframework.stereotype.Component;

@Component
public class StudentExecutionResultMapper {
    public StudentExecutionResultResponse toResponse(ExecutionBatchResult result) {
        var samples = result.getCases().stream().filter(item -> !item.isHidden()).map(item -> {
            var diagnostics = item.getDiagnostics();
            return new SampleExecutionResultResponse(item.getPosition(), item.getStatus(), item.getVerdict(),
                    item.getTimeConsumedMs(), item.getMemoryConsumedBytes(),
                    diagnostics == null ? null : diagnostics.getStdout(),
                    diagnostics == null ? null : diagnostics.getStderr(),
                    diagnostics == null ? null : diagnostics.getCompileOutput());
        }).toList();
        return new StudentExecutionResultResponse(result.getStatus(), result.getVerdict(), samples, result.getFailureCode());
    }
}
