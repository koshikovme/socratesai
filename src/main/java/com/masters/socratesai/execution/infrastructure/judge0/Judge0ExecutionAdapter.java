package com.masters.socratesai.execution.infrastructure.judge0;

import com.masters.socratesai.execution.domain.*;
import com.masters.socratesai.execution.domain.port.CodeExecutionPort;
import com.masters.socratesai.execution.infrastructure.judge0.dto.*;
import com.masters.socratesai.execution.model.ExecutionStatus;
import lombok.RequiredArgsConstructor;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@RequiredArgsConstructor
public final class Judge0ExecutionAdapter implements CodeExecutionPort {
    private final Judge0Properties properties;
    private final Judge0HttpClient client;
    private final Judge0RuntimeRegistry registry;
    private final SandboxRequestValidator validator;
    private final Judge0ResultMapper mapper;
    private final ExecutionResultAggregator aggregator;
    private final SandboxAsyncRuntime runtime;
    private final BackoffPolicy backoff;

    @Override
    public CompletableFuture<SubmittedExecutionBatch> submitBatch(ExecutionBatch request) {
        try {
            enabled();
            ExecutionBatch batch = validator.snapshot(request);
            var configured = registry.configuredRuntime(batch);
            var dispatched = new AtomicBoolean();
            var operation = runtime.<SubmittedExecutionBatch>start(batch.getDeadline(), code -> {
                throw new SandboxException(code, dispatched.get());
            });
            if (!operation.isDone()) {
                if (registry.hasFreshCatalog()) dispatch(operation, batch, configured, dispatched);
                else operation.track(client.get("languages", operation.getDeadline(), Judge0LanguageResponse[].class), (catalog, error) -> {
                    if (error != null) { operation.fail(failureCode(error)); return; }
                    registry.acceptCatalog(catalog);
                    dispatch(operation, batch, configured, dispatched);
                });
            }
            return operation.getResult();
        } catch (SandboxException ex) {
            return CompletableFuture.failedFuture(ex);
        } catch (RuntimeException ex) {
            return CompletableFuture.failedFuture(new SandboxException(SandboxFailureCode.INVALID_REQUEST));
        }
    }

    @Override
    public CompletableFuture<ExecutionBatchResult> pollBatch(SubmittedExecutionBatch request) {
        try {
            enabled();
            SubmittedExecutionBatch batch = validator.snapshot(request);
            Map<Long, CaseExecutionResult> results = new ConcurrentHashMap<>();
            var operation = runtime.start(batch.getDeadline(), code -> ExecutionBatchResult.builder()
                    .status(ExecutionStatus.SYSTEM_ERROR).failureCode(code)
                    .cases(batch.getTickets().stream()
                            .map(ticket -> terminal(results.get(ticket.getTestCaseId())) ? results.get(ticket.getTestCaseId()) : mapper.failure(ticket, code))
                            .toList()).build());
            poll(operation, batch, results, 0);
            return operation.getResult();
        } catch (SandboxException ex) {
            return CompletableFuture.failedFuture(ex);
        } catch (RuntimeException ex) {
            return CompletableFuture.failedFuture(new SandboxException(SandboxFailureCode.INVALID_REQUEST));
        }
    }

    private void dispatch(SandboxAsyncRuntime.Operation<SubmittedExecutionBatch> operation, ExecutionBatch batch,
                          Judge0Properties.RuntimeSpec configured, AtomicBoolean dispatched) {
        if (operation.isDone()) return;
        try { registry.verify(configured); }
        catch (SandboxException ex) { operation.fail(ex.getCode()); return; }
        Judge0BatchRequest payload = createPayload(batch, configured.getLanguageId());
        dispatched.set(true);
        operation.track(client.post("submissions/batch?base64_encoded=true", payload, operation.getDeadline(), Judge0TokenResponse[].class), (response, error) -> {
            if (error != null) {
                RuntimeException safe = Judge0HttpClient.safeFailure(error);
                if (safe instanceof Judge0HttpException http && http.getStatus() >= 400 && http.getStatus() < 500 && http.getStatus() != 408) {
                    dispatched.set(false);
                    operation.fail(SandboxFailureCode.PROVIDER_REJECTED);
                } else operation.fail(SandboxFailureCode.SUBMISSION_UNKNOWN);
                return;
            }
            if (response == null || response.length != batch.getTests().size()
                    || Arrays.stream(response).anyMatch(item -> item == null || !SandboxRequestValidator.validToken(item.getToken()))
                    || Arrays.stream(response).map(Judge0TokenResponse::getToken).distinct().count() != response.length) {
                operation.fail(SandboxFailureCode.SUBMISSION_UNKNOWN);
                return;
            }
            List<ExecutionTicket> tickets = IntStream.range(0, response.length).mapToObj(index -> {
                var test = batch.getTests().get(index);
                return ExecutionTicket.builder().testCaseId(test.getTestCaseId()).position(test.getPosition()).hidden(test.isHidden())
                        .expectedOutput(test.getExpectedOutput()).token(response[index].getToken()).build();
            }).toList();
            operation.complete(SubmittedExecutionBatch.builder().runtimeKey(batch.getRuntimeKey()).runtimeVersion(batch.getRuntimeVersion())
                    .limits(batch.getLimits()).comparison(batch.getComparison()).deadline(operation.getDeadline()).tickets(tickets).build());
        });
    }

    private Judge0BatchRequest createPayload(ExecutionBatch batch, int languageId) {
        var limits = batch.getLimits();
        String source = encode(batch.getSourceCode());
        return Judge0BatchRequest.builder().submissions(batch.getTests().stream().map(test -> Judge0SubmissionRequest.builder()
                .sourceCode(source).languageId(languageId).stdin(encode(test.getInput()))
                .cpuTimeLimit(BigDecimal.valueOf(limits.getTimeLimitMs(), 3)).cpuExtraTime(BigDecimal.ZERO)
                .wallTimeLimit(BigDecimal.valueOf(limits.getWallTimeLimitMs(), 3)).memoryLimit(limits.getMemoryLimitKb())
                .stackLimit(limits.getMemoryLimitKb()).maxProcessesAndOrThreads(properties.getMaxProcesses())
                .maxFileSize(Math.max(1, (limits.getOutputLimitBytes() + 1023) / 1024))
                .enableNetwork(false).enablePerProcessAndThreadMemoryLimit(false).enablePerProcessAndThreadTimeLimit(false)
                .redirectStderrToStdout(false).numberOfRuns(1).build()).toList()).build();
    }

    private void poll(SandboxAsyncRuntime.Operation<ExecutionBatchResult> operation, SubmittedExecutionBatch batch,
                      Map<Long, CaseExecutionResult> results, int attempt) {
        if (operation.isDone()) return;
        if (attempt >= properties.getMaxPollAttempts()) { operation.fail(SandboxFailureCode.POLL_LIMIT_EXCEEDED); return; }
        List<ExecutionTicket> pending = batch.getTickets().stream().filter(ticket -> !terminal(results.get(ticket.getTestCaseId()))).toList();
        var poll = Judge0PollRequest.builder().tokens(pending.stream().map(ExecutionTicket::getToken).toList()).build();
        String path = "submissions/batch?base64_encoded=true&tokens=" + String.join(",", poll.getTokens())
                + "&fields=token,status,stdout,stderr,compile_output,time,memory,exit_code";
        operation.track(client.get(path, operation.getDeadline(), Judge0BatchResponse.class), (response, error) -> {
            if (error != null) {
                if (retryable(error)) operation.schedule(() -> poll(operation, batch, results, attempt + 1), backoff.delay(attempt));
                else operation.fail(failureCode(error));
                return;
            }
            if (response == null || response.getSubmissions() == null || response.getSubmissions().size() != pending.size()) {
                operation.fail(SandboxFailureCode.INVALID_RESPONSE);
                return;
            }
            Set<String> expected = Set.copyOf(poll.getTokens());
            if (response.getSubmissions().stream().anyMatch(item -> item == null || item.getToken() == null || !expected.contains(item.getToken()))) {
                operation.fail(SandboxFailureCode.INVALID_RESPONSE);
                return;
            }
            Map<String, Judge0ResultResponse> byToken = response.getSubmissions().stream()
                    .collect(Collectors.toMap(Judge0ResultResponse::getToken, item -> item));
            pending.forEach(ticket -> results.put(ticket.getTestCaseId(), mapper.map(ticket, byToken.get(ticket.getToken()), batch.getLimits(), batch.getComparison())));
            var infrastructureError = results.values().stream().filter(item -> item.getStatus() == ExecutionStatus.SYSTEM_ERROR).findFirst();
            if (infrastructureError.isPresent()) { operation.fail(infrastructureError.get().getFailureCode()); return; }
            if (results.size() == batch.getTickets().size() && results.values().stream().allMatch(this::terminal)) {
                operation.complete(aggregator.aggregate(batch.getTickets().stream().map(ticket -> results.get(ticket.getTestCaseId())).toList()));
            } else operation.schedule(() -> poll(operation, batch, results, attempt + 1), backoff.delay(attempt));
        });
    }

    private boolean terminal(CaseExecutionResult result) {
        return result != null && (result.getStatus() == ExecutionStatus.FINISHED || result.getStatus() == ExecutionStatus.SYSTEM_ERROR);
    }

    private boolean retryable(Throwable error) {
        RuntimeException safe = Judge0HttpClient.safeFailure(error);
        if (safe instanceof Judge0HttpException http) return http.getStatus() == 429 || http.getStatus() >= 500;
        return safe instanceof SandboxException exception && exception.getCode() == SandboxFailureCode.TRANSPORT_ERROR;
    }

    private SandboxFailureCode failureCode(Throwable error) {
        RuntimeException safe = Judge0HttpClient.safeFailure(error);
        return safe instanceof SandboxException exception ? exception.getCode() : SandboxFailureCode.PROVIDER_REJECTED;
    }

    private String encode(String value) {
        return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private void enabled() {
        if (!properties.isEnabled()) throw new SandboxException(SandboxFailureCode.DISABLED);
    }
}
