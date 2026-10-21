package com.masters.socratesai.execution.infrastructure.judge0;

import com.masters.socratesai.execution.domain.*;
import com.masters.socratesai.execution.infrastructure.judge0.dto.*;
import com.masters.socratesai.execution.model.ExecutionStatus;
import com.masters.socratesai.execution.model.ExecutionVerdict;
import com.masters.socratesai.task.model.OutputComparison;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.*;
import static com.masters.socratesai.execution.infrastructure.judge0.SandboxFixtures.*;

class Judge0ResultMapperTest {
    private final Judge0ResultMapper mapper = new Judge0ResultMapper(new OutputComparator());

    @ParameterizedTest
    @CsvSource({"3,ACCEPTED", "4,WRONG_ANSWER", "5,TIME_LIMIT_EXCEEDED", "6,COMPILATION_ERROR", "7,RUNTIME_ERROR",
            "8,OUTPUT_LIMIT_EXCEEDED", "9,RUNTIME_ERROR", "10,RUNTIME_ERROR", "11,RUNTIME_ERROR", "12,RUNTIME_ERROR", "14,RUNTIME_ERROR"})
    void mapsAllTerminalProviderStatuses(int status, ExecutionVerdict verdict) {
        var result = map(response(status).build());
        assertThat(result.getStatus()).isEqualTo(ExecutionStatus.FINISHED);
        assertThat(result.getVerdict()).isEqualTo(verdict);
        assertThat(result.getTimeConsumedMs()).isEqualTo(2);
        assertThat(result.getMemoryConsumedBytes()).isEqualTo(2048L);
    }

    @Test
    void mapsPendingAndProviderErrorsWithoutTrustingDiagnostics() {
        assertThat(map(response(1).stdout("invalid-base64").build()).getStatus()).isEqualTo(ExecutionStatus.QUEUED);
        assertThat(map(response(2).build()).getStatus()).isEqualTo(ExecutionStatus.RUNNING);
        assertThat(map(response(13).build()).getFailureCode()).isEqualTo(SandboxFailureCode.PROVIDER_INTERNAL_ERROR);
        assertThat(map(response(99).build()).getFailureCode()).isEqualTo(SandboxFailureCode.INVALID_RESPONSE);
        assertThat(map(response(0).build()).getFailureCode()).isEqualTo(SandboxFailureCode.INVALID_RESPONSE);
        assertThat(map(Judge0ResultResponse.builder().build()).getFailureCode()).isEqualTo(SandboxFailureCode.INVALID_RESPONSE);
        assertThat(map(Judge0ResultResponse.builder().status(Judge0StatusResponse.builder().build()).build()).getFailureCode()).isEqualTo(SandboxFailureCode.INVALID_RESPONSE);
    }

    @Test
    void comparesLocallyAndDropsHiddenDiagnosticsBeforeReturningInternalResult() {
        assertThat(map(response(3).stdout(encoded("wrong")).build()).getVerdict()).isEqualTo(ExecutionVerdict.WRONG_ANSWER);
        assertThat(map(response(3).stdout(encoded("42\r\n")).build()).getVerdict()).isEqualTo(ExecutionVerdict.ACCEPTED);
        var hidden = mapper.map(ticket("token", 1, true), response(3).stderr(encoded(SECRET)).compileOutput(encoded(SECRET)).build(), limits(), OutputComparison.EXACT);
        assertThat(hidden.getVerdict()).isEqualTo(ExecutionVerdict.ACCEPTED);
        assertThat(hidden.getDiagnostics()).isNull();
        assertThat(hidden.toString()).doesNotContain(SECRET, "42");
        assertThat(ticket(SECRET, 1, true).toString()).doesNotContain(SECRET, "42");
    }

    @Test
    void handlesWrappedBase64AndAbsentDiagnosticsAndMetrics() {
        assertThat(map(response(3).stdout("ND\r\nIK\n").build()).getVerdict()).isEqualTo(ExecutionVerdict.ACCEPTED);
        var empty = map(response(6).stdout(null).stderr(null).compileOutput(null).time(null).memory(null).build());
        assertThat(empty.getDiagnostics().getStdout()).isEmpty();
        assertThat(empty.getTimeConsumedMs()).isNull();
        assertThat(empty.getMemoryConsumedBytes()).isNull();
    }

    @Test
    void cannotSpoofMemoryLimitByWritingToStderr() {
        assertThat(map(response(11).stderr(encoded("Memory limit exceeded")).build()).getVerdict()).isEqualTo(ExecutionVerdict.RUNTIME_ERROR);
        assertThat(map(response(7).memory(65537L).build()).getVerdict()).isEqualTo(ExecutionVerdict.MEMORY_LIMIT_EXCEEDED);
        assertThat(map(response(5).memory(65537L).build()).getVerdict()).isEqualTo(ExecutionVerdict.TIME_LIMIT_EXCEEDED);
        assertThat(map(response(3).exitCode(1).build()).getStatus()).isEqualTo(ExecutionStatus.SYSTEM_ERROR);
    }

    @ParameterizedTest
    @ValueSource(strings = {"-0.1", "NaN", "1e9999999", "2147483.648", "12345678901234567890123456789012345", "0.0000000001"})
    void rejectsInvalidOrUnboundedTimeMetrics(String time) {
        assertThat(map(response(3).time(time).build()).getFailureCode()).isEqualTo(SandboxFailureCode.INVALID_RESPONSE);
    }

    @Test
    void rejectsInvalidMemoryAndBase64() {
        assertThat(map(response(3).memory(-1L).build()).getStatus()).isEqualTo(ExecutionStatus.SYSTEM_ERROR);
        assertThat(map(response(3).memory(Long.MAX_VALUE).build()).getStatus()).isEqualTo(ExecutionStatus.SYSTEM_ERROR);
        assertThat(map(response(3).stdout("!bad!").build()).getStatus()).isEqualTo(ExecutionStatus.SYSTEM_ERROR);
    }

    @Test
    void boundsDecodedOutputAndPreservesAuthoritativeFailureVerdicts() {
        assertThat(map(response(3).stdout(encoded("x".repeat(4097))).build()).getVerdict()).isEqualTo(ExecutionVerdict.OUTPUT_LIMIT_EXCEEDED);
        assertThat(map(response(4).stderr(encoded("x".repeat(5000))).build()).getVerdict()).isEqualTo(ExecutionVerdict.OUTPUT_LIMIT_EXCEEDED);
        assertThat(map(response(6).compileOutput(encoded("x".repeat(65537))).build()).getVerdict()).isEqualTo(ExecutionVerdict.COMPILATION_ERROR);
        assertThat(map(response(3).stdout("/w==").build()).getVerdict()).isEqualTo(ExecutionVerdict.WRONG_ANSWER);
        assertThat(map(response(7).stdout("/w==").build()).getVerdict()).isEqualTo(ExecutionVerdict.RUNTIME_ERROR);
    }

    private Judge0ResultResponse.Judge0ResultResponseBuilder response(int status) {
        return Judge0ResultResponse.builder().status(Judge0StatusResponse.builder().id(status).build())
                .stdout(encoded("42\n")).time("0.0011").memory(2L).exitCode(0);
    }

    private CaseExecutionResult map(Judge0ResultResponse response) {
        return mapper.map(ticket("token", 1, false), response, limits(), OutputComparison.EXACT);
    }
}
