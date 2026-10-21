package com.masters.socratesai.execution.domain;

import com.masters.socratesai.execution.application.StudentExecutionResultMapper;
import com.masters.socratesai.execution.model.ExecutionStatus;
import com.masters.socratesai.execution.model.ExecutionVerdict;
import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;

import static com.masters.socratesai.execution.model.ExecutionStatus.*;
import static com.masters.socratesai.execution.model.ExecutionVerdict.*;
import static org.assertj.core.api.Assertions.*;

class ExecutionResultAggregatorTest {
    private final ExecutionResultAggregator aggregator = new ExecutionResultAggregator();

    @Test
    void choosesFirstFailureInTrustedTestOrderAndRequiresAllTestsToFinish() {
        assertThat(aggregate(result(FINISHED, ACCEPTED), result(FINISHED, WRONG_ANSWER), result(FINISHED, RUNTIME_ERROR)).getVerdict())
                .isEqualTo(WRONG_ANSWER);
        assertThat(aggregate(result(FINISHED, ACCEPTED)).getVerdict()).isEqualTo(ACCEPTED);
        assertThat(aggregate(result(QUEUED, null)).getStatus()).isEqualTo(QUEUED);
        var running = aggregate(result(FINISHED, WRONG_ANSWER), result(RUNNING, null));
        assertThat(running.getStatus()).isEqualTo(RUNNING);
        assertThat(running.getVerdict()).isNull();
    }

    @Test
    void infrastructureFailuresNeverBecomeStudentVerdicts() {
        assertThat(aggregate().getFailureCode()).isEqualTo(SandboxFailureCode.INVALID_RESPONSE);
        assertThat(aggregate(result(FINISHED, null)).getStatus()).isEqualTo(SYSTEM_ERROR);
        var failure = CaseExecutionResult.builder().status(SYSTEM_ERROR).failureCode(SandboxFailureCode.DEADLINE_EXCEEDED).build();
        assertThat(aggregate(result(FINISHED, ACCEPTED), failure).getFailureCode()).isEqualTo(SandboxFailureCode.DEADLINE_EXCEEDED);
    }

    @Test
    void studentDtoOmitsHiddenCaseEvenIfInternalCallerSuppliesDiagnostics() throws Exception {
        var secret = ExecutionDiagnostics.builder().stdout("hidden-input-echo").stderr("hidden-stderr")
                .compileOutput("hidden-compiler-detail").build();
        var hidden = CaseExecutionResult.builder().hidden(true).testCaseId(900).position(4).status(FINISHED)
                .verdict(WRONG_ANSWER).diagnostics(secret).timeConsumedMs(998).memoryConsumedBytes(991L).build();
        var sample = CaseExecutionResult.builder().position(0).status(FINISHED).verdict(ACCEPTED)
                .diagnostics(ExecutionDiagnostics.builder().stdout("sample-output").build()).build();
        var dto = new StudentExecutionResultMapper().toResponse(aggregate(sample, hidden));
        assertThat(dto.samples()).hasSize(1);
        String json = new ObjectMapper().writeValueAsString(dto);
        assertThat(json).contains("sample-output", "WRONG_ANSWER").doesNotContain("hidden", "998", "991", "900", "expectedOutput", "token");
        assertThat(new StudentExecutionResultMapper().toResponse(aggregate(result(SYSTEM_ERROR, null))).samples().getFirst().stdout()).isNull();
    }

    private CaseExecutionResult result(ExecutionStatus status, ExecutionVerdict verdict) {
        return CaseExecutionResult.builder().status(status).verdict(verdict).build();
    }

    private ExecutionBatchResult aggregate(CaseExecutionResult... cases) {
        return aggregator.aggregate(List.of(cases));
    }
}
