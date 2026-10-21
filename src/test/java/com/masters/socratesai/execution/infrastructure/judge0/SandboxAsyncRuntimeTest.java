package com.masters.socratesai.execution.infrastructure.judge0;

import com.masters.socratesai.execution.domain.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.*;

class SandboxAsyncRuntimeTest {
    private final ScheduledThreadPoolExecutor scheduler = new ScheduledThreadPoolExecutor(1);
    private final Semaphore permits = new Semaphore(1);
    private final SandboxAsyncRuntime runtime = new SandboxAsyncRuntime(new Judge0Properties(), scheduler, Clock.systemUTC(), permits);

    SandboxAsyncRuntimeTest() { scheduler.setRemoveOnCancelPolicy(true); }

    @AfterEach
    void close() { runtime.close(); }

    @Test
    void deadlineCancelsHungHttpAndReleasesCapacityAndTimers() throws Exception {
        var operation = runtime.<SandboxFailureCode>start(Instant.now().plusMillis(120), code -> code);
        var http = new CompletableFuture<String>();
        operation.track(http, (value, error) -> fail("Hung request should not complete before deadline"));
        assertThat(operation.getResult().get(2, TimeUnit.SECONDS)).isEqualTo(SandboxFailureCode.DEADLINE_EXCEEDED);
        awaitCleanup();
        assertThat(http.isCancelled()).isTrue();
        assertThat(permits.availablePermits()).isEqualTo(1);
        assertThat(scheduler.getQueue()).isEmpty();
    }

    @Test
    void cancellationStopsPendingRetryAndBusyRejectionDoesNotLeakPermits() {
        var operation = runtime.<String>start(Instant.now().plusSeconds(10), null);
        var busy = runtime.<SandboxFailureCode>start(Instant.now().plusSeconds(10), code -> code);
        assertThat(busy.getResult().join()).isEqualTo(SandboxFailureCode.BUSY);
        var retried = new AtomicBoolean();
        operation.schedule(() -> retried.set(true), Duration.ofSeconds(5));
        operation.getResult().cancel(true);
        assertThat(retried).isFalse();
        assertThat(permits.availablePermits()).isEqualTo(1);
        assertThat(scheduler.getQueue()).isEmpty();
        var next = runtime.<String>start(Instant.now().plusSeconds(10), null);
        next.complete("done");
        assertThat(next.getResult()).isCompletedWithValue("done");
        assertThat(permits.availablePermits()).isEqualTo(1);
    }

    @Test
    void expiredAndClosedOperationsFailImmediatelyAndSafely() {
        var expired = runtime.<SandboxFailureCode>start(Instant.MIN, code -> code);
        assertThat(expired.getResult().join()).isEqualTo(SandboxFailureCode.DEADLINE_EXCEEDED);
        var active = runtime.<SandboxFailureCode>start(Instant.MAX, code -> code);
        assertThat(active.getDeadline()).isBefore(Instant.now().plusSeconds(31));
        runtime.close();
        assertThat(active.getResult().join()).isEqualTo(SandboxFailureCode.CANCELLED);
        assertThat(runtime.<SandboxFailureCode>start(Instant.MAX, code -> code).getResult().join()).isEqualTo(SandboxFailureCode.CANCELLED);
        assertThat(permits.availablePermits()).isEqualTo(1);
    }

    @Test
    void callbackErrorsNeverEscapeWithSourceOrSecretCauses() {
        var operation = runtime.<String>start(Instant.now().plusSeconds(10), null);
        operation.track(CompletableFuture.completedFuture("response"), (value, error) -> { throw new IllegalStateException(SandboxFixtures.SECRET); });
        assertThatThrownBy(() -> operation.getResult().join()).hasCauseInstanceOf(SandboxException.class);
        var failure = catchThrowable(() -> operation.getResult().join()).getCause();
        assertThat(failure).hasNoCause().hasMessageNotContaining(SandboxFixtures.SECRET);
        assertThat(permits.availablePermits()).isEqualTo(1);
    }

    private void awaitCleanup() throws InterruptedException {
        // CompletableFuture wakes its waiter before running dependent cleanup callbacks.
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(1);
        while (permits.availablePermits() == 0 && System.nanoTime() < deadline) Thread.sleep(1);
    }
}
