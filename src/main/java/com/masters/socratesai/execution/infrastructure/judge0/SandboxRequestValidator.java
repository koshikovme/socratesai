package com.masters.socratesai.execution.infrastructure.judge0;

import com.masters.socratesai.common.validation.ContesterLimits;
import com.masters.socratesai.execution.domain.*;
import lombok.RequiredArgsConstructor;

import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.List;

@RequiredArgsConstructor
public final class SandboxRequestValidator {
    private final Judge0Properties properties;

    public ExecutionBatch snapshot(ExecutionBatch batch) {
        require(batch != null && batch.getDeadline() != null && batch.getComparison() != null);
        limits(batch.getLimits());
        text(batch.getSourceCode(), properties.getMaxSourceBytes());
        require(!batch.getSourceCode().isBlank() && batch.getSourceCode().length() <= ContesterLimits.SOURCE_CHARACTERS);
        require(batch.getTests() != null && !batch.getTests().isEmpty() && batch.getTests().size() <= properties.getMaxBatchSize());
        List<ExecutionTestCase> tests;
        try { tests = List.copyOf(batch.getTests()); }
        catch (NullPointerException ex) { throw new SandboxException(SandboxFailureCode.INVALID_REQUEST); }
        tests.forEach(test -> {
            require(test.getTestCaseId() > 0 && test.getPosition() >= 0);
            text(test.getInput(), properties.getMaxTestBytes());
            text(test.getExpectedOutput(), properties.getMaxTestBytes());
        });
        require(tests.stream().map(ExecutionTestCase::getTestCaseId).distinct().count() == tests.size()
                && tests.stream().map(ExecutionTestCase::getPosition).distinct().count() == tests.size());
        return ExecutionBatch.builder().languageKey(batch.getLanguageKey()).runtimeKey(batch.getRuntimeKey())
                .runtimeVersion(batch.getRuntimeVersion()).sourceCode(batch.getSourceCode()).limits(batch.getLimits())
                .comparison(batch.getComparison()).deadline(batch.getDeadline())
                .tests(tests.stream().sorted(Comparator.comparingInt(ExecutionTestCase::getPosition)).toList()).build();
    }

    public SubmittedExecutionBatch snapshot(SubmittedExecutionBatch batch) {
        require(batch != null && batch.getDeadline() != null && batch.getComparison() != null);
        limits(batch.getLimits());
        require(batch.getTickets() != null && !batch.getTickets().isEmpty() && batch.getTickets().size() <= properties.getMaxBatchSize());
        List<ExecutionTicket> tickets;
        try { tickets = List.copyOf(batch.getTickets()); }
        catch (NullPointerException ex) { throw new SandboxException(SandboxFailureCode.INVALID_REQUEST); }
        tickets.forEach(ticket -> {
            require(ticket.getTestCaseId() > 0 && ticket.getPosition() >= 0 && validToken(ticket.getToken()));
            text(ticket.getExpectedOutput(), properties.getMaxTestBytes());
        });
        require(tickets.stream().map(ExecutionTicket::getToken).distinct().count() == tickets.size()
                && tickets.stream().map(ExecutionTicket::getTestCaseId).distinct().count() == tickets.size()
                && tickets.stream().map(ExecutionTicket::getPosition).distinct().count() == tickets.size());
        return SubmittedExecutionBatch.builder().runtimeKey(batch.getRuntimeKey()).runtimeVersion(batch.getRuntimeVersion())
                .limits(batch.getLimits()).comparison(batch.getComparison()).deadline(batch.getDeadline())
                .tickets(tickets.stream().sorted(Comparator.comparingInt(ExecutionTicket::getPosition)).toList()).build();
    }

    static boolean validToken(String token) {
        return token != null && token.matches("[A-Za-z0-9_-]{1,200}");
    }

    private void limits(ExecutionLimits limits) {
        require(limits != null && limits.getTimeLimitMs() > 0 && limits.getTimeLimitMs() <= ContesterLimits.TIME_LIMIT_MS
                && limits.getWallTimeLimitMs() >= limits.getTimeLimitMs() && limits.getWallTimeLimitMs() <= ContesterLimits.WALL_TIME_LIMIT_MS
                && limits.getMemoryLimitKb() > 0 && limits.getMemoryLimitKb() <= ContesterLimits.MEMORY_LIMIT_KB
                && limits.getOutputLimitBytes() > 0 && limits.getOutputLimitBytes() <= ContesterLimits.OUTPUT_LIMIT_BYTES);
    }

    private void text(String value, int byteLimit) {
        require(value != null && value.length() <= byteLimit && StandardCharsets.UTF_8.newEncoder().canEncode(value)
                && value.getBytes(StandardCharsets.UTF_8).length <= byteLimit);
    }

    private void require(boolean valid) {
        if (!valid) throw new SandboxException(SandboxFailureCode.INVALID_REQUEST);
    }
}
