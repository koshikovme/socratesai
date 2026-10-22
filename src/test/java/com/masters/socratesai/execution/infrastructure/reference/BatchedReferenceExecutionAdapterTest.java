package com.masters.socratesai.execution.infrastructure.reference;

import com.masters.socratesai.execution.domain.*;
import com.masters.socratesai.execution.domain.port.CodeExecutionPort;
import com.masters.socratesai.execution.infrastructure.judge0.*;
import com.masters.socratesai.execution.model.*;
import com.masters.socratesai.task.model.OutputComparison;
import org.junit.jupiter.api.*;

import java.time.*;
import java.util.List;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class BatchedReferenceExecutionAdapterTest {
    private final CodeExecutionPort execution = mock(CodeExecutionPort.class);
    private final ScheduledThreadPoolExecutor scheduler = new ScheduledThreadPoolExecutor(1);
    private final Semaphore permits = new Semaphore(1);
    private final SandboxAsyncRuntime runtime = new SandboxAsyncRuntime(new Judge0Properties(), scheduler, Clock.systemUTC(), permits);
    private final BatchedReferenceExecutionAdapter adapter = new BatchedReferenceExecutionAdapter(execution, runtime, new ExecutionResultAggregator());

    @BeforeEach void setup() {
        scheduler.setRemoveOnCancelPolicy(true);
        when(execution.submitBatch(any())).thenReturn(CompletableFuture.completedFuture(SubmittedExecutionBatch.builder().build()));
    }
    @AfterEach void close() { runtime.close(); }

    @Test void aggregatesAllBatchesWithoutRetainingAnyDiagnostics() throws Exception {
        when(execution.pollBatch(any())).thenReturn(CompletableFuture.completedFuture(result(ExecutionVerdict.ACCEPTED)));
        var result = adapter.verify(List.of(batch(), batch()), deadline()).get(2, TimeUnit.SECONDS);
        assertThat(result.getStatus()).isEqualTo(ExecutionStatus.FINISHED);
        assertThat(result.getVerdict()).isEqualTo(ExecutionVerdict.ACCEPTED);
        assertThat(result.getCases()).hasSize(2).allMatch(item -> item.getDiagnostics() == null);
        verify(execution, times(2)).submitBatch(any());
    }

    @Test void neverTrustsAnAcceptedAggregateWithoutCompleteMatchingCaseEvidence() {
        when(execution.pollBatch(any())).thenReturn(CompletableFuture.completedFuture(ExecutionBatchResult.builder()
                .status(ExecutionStatus.FINISHED).verdict(ExecutionVerdict.ACCEPTED).cases(List.of()).build()));
        assertFailure(adapter.verify(List.of(batch()), deadline()), SandboxFailureCode.INVALID_RESPONSE);
    }

    @Test void propagatesInfrastructureFailureAndSynchronousPortFailureSafely() {
        when(execution.pollBatch(any())).thenReturn(CompletableFuture.completedFuture(ExecutionBatchResult.builder()
                .status(ExecutionStatus.SYSTEM_ERROR).failureCode(SandboxFailureCode.TRANSPORT_ERROR).build()));
        assertFailure(adapter.verify(List.of(batch()), deadline()), SandboxFailureCode.TRANSPORT_ERROR);
        when(execution.submitBatch(any())).thenThrow(new IllegalStateException("private-reference-source"));
        assertFailure(adapter.verify(List.of(batch()), deadline()), SandboxFailureCode.INVALID_RESPONSE);
    }

    @Test void deadlineAndCancellationStopInflightPollingAndReleaseAdmission() throws Exception {
        var pending = new CompletableFuture<ExecutionBatchResult>();
        when(execution.pollBatch(any())).thenReturn(pending);
        assertFailure(adapter.verify(List.of(batch()), Instant.now().plusMillis(120)), SandboxFailureCode.DEADLINE_EXCEEDED);
        org.awaitility.Awaitility.await().atMost(Duration.ofSeconds(1)).untilAsserted(() -> {
            assertThat(pending.isCancelled()).isTrue(); assertThat(permits.availablePermits()).isEqualTo(1);
        });
        var next = new CompletableFuture<ExecutionBatchResult>();
        when(execution.pollBatch(any())).thenReturn(next);
        var operation = adapter.verify(List.of(batch()), deadline());
        operation.cancel(true);
        assertThat(next.isCancelled()).isTrue();
    }

    @Test void emptyWorkAndIncompleteRunningEvidenceCannotPass() {
        assertFailure(adapter.verify(List.of(), deadline()), SandboxFailureCode.INVALID_REQUEST);
        when(execution.pollBatch(any())).thenReturn(CompletableFuture.completedFuture(ExecutionBatchResult.builder().status(ExecutionStatus.RUNNING).build()));
        assertFailure(adapter.verify(List.of(batch()), deadline()), SandboxFailureCode.INVALID_RESPONSE);
    }

    @Test void wrongReferenceRemainsWrongEvenWhenProviderAggregateClaimsAccepted() throws Exception {
        var response = result(ExecutionVerdict.WRONG_ANSWER);
        when(execution.pollBatch(any())).thenReturn(CompletableFuture.completedFuture(ExecutionBatchResult.builder().status(ExecutionStatus.FINISHED)
                .verdict(ExecutionVerdict.ACCEPTED).cases(response.getCases()).build()));
        assertThat(adapter.verify(List.of(batch()), deadline()).get(2, TimeUnit.SECONDS).getVerdict()).isEqualTo(ExecutionVerdict.WRONG_ANSWER);
    }

    private Instant deadline() { return Instant.now().plusSeconds(10); }
    private ExecutionBatch batch() {
        return ExecutionBatch.builder().languageKey("fixture").runtimeKey("fixture").runtimeVersion("fixture").sourceCode("private-reference-source")
                .comparison(OutputComparison.EXACT).deadline(deadline()).tests(List.of(ExecutionTestCase.builder().testCaseId(1).position(0).hidden(true)
                        .input("secret").expectedOutput("expected").build())).build();
    }
    private ExecutionBatchResult result(ExecutionVerdict verdict) {
        return new ExecutionResultAggregator().aggregate(List.of(CaseExecutionResult.builder().testCaseId(1).position(0).hidden(true)
                .status(ExecutionStatus.FINISHED).verdict(verdict).diagnostics(ExecutionDiagnostics.builder().stdout("private-echo").build()).build()));
    }
    private void assertFailure(CompletableFuture<?> future, SandboxFailureCode code) {
        var error = catchThrowable(() -> future.get(2, TimeUnit.SECONDS));
        assertThat(error).hasCauseInstanceOf(SandboxException.class);
        assertThat(((SandboxException) error.getCause()).getCode()).isEqualTo(code);
        assertThat(error.getCause()).hasNoCause().hasMessageNotContaining("private-reference-source");
    }
}
