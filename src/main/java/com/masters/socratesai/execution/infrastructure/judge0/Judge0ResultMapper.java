package com.masters.socratesai.execution.infrastructure.judge0;

import com.masters.socratesai.execution.domain.*;
import com.masters.socratesai.execution.infrastructure.judge0.dto.Judge0ResultResponse;
import com.masters.socratesai.execution.model.ExecutionStatus;
import com.masters.socratesai.execution.model.ExecutionVerdict;
import com.masters.socratesai.task.model.OutputComparison;
import lombok.RequiredArgsConstructor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

@RequiredArgsConstructor
public final class Judge0ResultMapper {
    private final OutputComparator comparator;

    public CaseExecutionResult map(ExecutionTicket ticket, Judge0ResultResponse response,
                                   ExecutionLimits limits, OutputComparison comparison) {
        if (response.getStatus() == null || response.getStatus().getId() == null) return failure(ticket, SandboxFailureCode.INVALID_RESPONSE);
        int status = response.getStatus().getId();
        if (status == 1 || status == 2) return identity(ticket).status(status == 1 ? ExecutionStatus.QUEUED : ExecutionStatus.RUNNING).build();
        if (status == 13) return failure(ticket, SandboxFailureCode.PROVIDER_INTERNAL_ERROR);
        if (status < 3 || status > 14) return failure(ticket, SandboxFailureCode.INVALID_RESPONSE);
        try {
            Integer time = milliseconds(response.getTime());
            Long memory = bytes(response.getMemory());
            String stdout = decode(response.getStdout(), limits.getOutputLimitBytes());
            String stderr = decode(response.getStderr(), limits.getOutputLimitBytes());
            String compileOutput = decode(response.getCompileOutput(), 65536);
            ExecutionVerdict verdict = verdict(status);
            if (status == 3) {
                if (response.getExitCode() != null && response.getExitCode() != 0) return failure(ticket, SandboxFailureCode.INVALID_RESPONSE);
                verdict = comparator.matches(ticket.getExpectedOutput(), stdout, comparison)
                        ? ExecutionVerdict.ACCEPTED : ExecutionVerdict.WRONG_ANSWER;
            }
            // Judge0 CE has no distinct memory-limit status. Do not infer it from
            // student-controlled stderr/messages; only measured over-limit usage qualifies.
            if (status != 5 && status != 6 && status != 8 && memory != null && memory > limits.getMemoryLimitKb() * 1024L) {
                verdict = ExecutionVerdict.MEMORY_LIMIT_EXCEEDED;
            }
            ExecutionDiagnostics diagnostics = ticket.isHidden() ? null : ExecutionDiagnostics.builder()
                    .stdout(stdout).stderr(stderr).compileOutput(compileOutput).build();
            return identity(ticket).status(ExecutionStatus.FINISHED).verdict(verdict)
                    .timeConsumedMs(time).memoryConsumedBytes(memory).diagnostics(diagnostics).build();
        } catch (OutputOverflow ex) {
            return identity(ticket).status(ExecutionStatus.FINISHED)
                    .verdict(status == 3 || status == 4 ? ExecutionVerdict.OUTPUT_LIMIT_EXCEEDED : verdict(status)).build();
        } catch (InvalidTextOutput ex) {
            return identity(ticket).status(ExecutionStatus.FINISHED)
                    .verdict(status == 3 ? ExecutionVerdict.WRONG_ANSWER : verdict(status)).build();
        } catch (IllegalArgumentException | ArithmeticException ex) {
            return failure(ticket, SandboxFailureCode.INVALID_RESPONSE);
        }
    }

    public CaseExecutionResult failure(ExecutionTicket ticket, SandboxFailureCode code) {
        return identity(ticket).status(ExecutionStatus.SYSTEM_ERROR).failureCode(code).build();
    }

    private CaseExecutionResult.CaseExecutionResultBuilder identity(ExecutionTicket ticket) {
        return CaseExecutionResult.builder().testCaseId(ticket.getTestCaseId()).position(ticket.getPosition()).hidden(ticket.isHidden());
    }

    private ExecutionVerdict verdict(int status) {
        return switch (status) {
            case 3 -> ExecutionVerdict.ACCEPTED;
            case 4 -> ExecutionVerdict.WRONG_ANSWER;
            case 5 -> ExecutionVerdict.TIME_LIMIT_EXCEEDED;
            case 6 -> ExecutionVerdict.COMPILATION_ERROR;
            case 8 -> ExecutionVerdict.OUTPUT_LIMIT_EXCEEDED;
            default -> ExecutionVerdict.RUNTIME_ERROR;
        };
    }

    private Integer milliseconds(String value) {
        if (value == null) return null;
        if (value.length() > 32) throw new IllegalArgumentException();
        BigDecimal seconds = new BigDecimal(value);
        if (Math.abs((long) seconds.scale()) > 9 || seconds.precision() > 16) throw new IllegalArgumentException();
        if (seconds.signum() < 0) throw new IllegalArgumentException();
        return seconds.movePointRight(3).setScale(0, RoundingMode.CEILING).intValueExact();
    }

    private Long bytes(Long kilobytes) {
        if (kilobytes == null) return null;
        if (kilobytes < 0) throw new IllegalArgumentException();
        return Math.multiplyExact(kilobytes, 1024L);
    }

    private String decode(String encoded, int limit) {
        if (encoded == null) return "";
        String compact = encoded.replace("\r", "").replace("\n", "");
        if (compact.length() > 4L * ((limit + 2L) / 3L)) throw new OutputOverflow();
        byte[] decoded = Base64.getDecoder().decode(compact);
        if (decoded.length > limit) throw new OutputOverflow();
        try {
            return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(decoded)).toString();
        } catch (CharacterCodingException ex) {
            throw new InvalidTextOutput();
        }
    }

    private static final class OutputOverflow extends RuntimeException {}
    private static final class InvalidTextOutput extends RuntimeException {}
}
