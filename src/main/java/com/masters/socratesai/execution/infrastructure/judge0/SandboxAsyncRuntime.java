package com.masters.socratesai.execution.infrastructure.judge0;

import com.masters.socratesai.execution.domain.SandboxException;
import com.masters.socratesai.execution.domain.SandboxFailureCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Function;

@RequiredArgsConstructor
public final class SandboxAsyncRuntime implements AutoCloseable {
    private final Judge0Properties properties;
    private final ScheduledExecutorService scheduler;
    private final Clock clock;
    private final Semaphore permits;
    private final Set<Operation<?>> active = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean closed = new AtomicBoolean();

    public <T> Operation<T> start(Instant requestedDeadline, Function<SandboxFailureCode, T> failureResult) {
        Instant cap = clock.instant().plus(properties.getOperationTimeout());
        Instant deadline = requestedDeadline.isBefore(cap) ? requestedDeadline : cap;
        if (closed.get() || !permits.tryAcquire()) {
            var rejected = new Operation<T>(deadline, scheduler, clock, () -> {}, failureResult);
            rejected.fail(closed.get() ? SandboxFailureCode.CANCELLED : SandboxFailureCode.BUSY);
            return rejected;
        }
        AtomicReference<Operation<T>> reference = new AtomicReference<>();
        var operation = new Operation<T>(deadline, scheduler, clock, () -> {
            permits.release();
            active.remove(reference.get());
        }, failureResult);
        reference.set(operation);
        active.add(operation);
        operation.initialize();
        if (closed.get()) operation.fail(SandboxFailureCode.CANCELLED);
        return operation;
    }

    @Override
    public void close() {
        closed.set(true);
        active.forEach(operation -> operation.fail(SandboxFailureCode.CANCELLED));
        scheduler.shutdownNow();
    }

    @RequiredArgsConstructor
    public static final class Operation<T> {
        @Getter private final Instant deadline;
        private final ScheduledExecutorService scheduler;
        private final Clock clock;
        private final Runnable release;
        private final Function<SandboxFailureCode, T> failureResult;
        @Getter private final CompletableFuture<T> result = new CompletableFuture<>();
        private final AtomicReference<CompletableFuture<?>> request = new AtomicReference<>();
        private final AtomicReference<ScheduledFuture<?>> next = new AtomicReference<>();
        private final AtomicReference<ScheduledFuture<?>> timeout = new AtomicReference<>();
        private final AtomicBoolean cleaned = new AtomicBoolean();

        private void initialize() {
            result.whenComplete((value, error) -> cleanup());
            if (!clock.instant().isBefore(deadline)) { fail(SandboxFailureCode.DEADLINE_EXCEEDED); return; }
            long remaining = Duration.between(clock.instant(), deadline).toNanos();
            if (remaining <= 0) { fail(SandboxFailureCode.DEADLINE_EXCEEDED); return; }
            try {
                ScheduledFuture<?> timer = scheduler.schedule(() -> fail(SandboxFailureCode.DEADLINE_EXCEEDED), remaining, TimeUnit.NANOSECONDS);
                timeout.set(timer);
                if (result.isDone()) timer.cancel(false);
            } catch (RejectedExecutionException ex) { fail(SandboxFailureCode.CANCELLED); }
        }

        public boolean isDone() {
            return result.isDone();
        }

        public void complete(T value) {
            result.complete(value);
        }

        public void fail(SandboxFailureCode code) {
            if (result.isDone()) return;
            if (failureResult == null) result.completeExceptionally(new SandboxException(code));
            else {
                try { result.complete(failureResult.apply(code)); }
                catch (SandboxException ex) { result.completeExceptionally(ex); }
                catch (RuntimeException ex) { result.completeExceptionally(new SandboxException(SandboxFailureCode.INVALID_RESPONSE)); }
            }
        }

        public <R> void track(CompletableFuture<R> future, BiConsumer<R, Throwable> completion) {
            request.set(future);
            if (result.isDone()) { future.cancel(true); return; }
            future.whenComplete((value, error) -> {
                request.compareAndSet(future, null);
                if (result.isDone()) return;
                try { completion.accept(value, error); }
                catch (SandboxException ex) { fail(ex.getCode()); }
                catch (RuntimeException ex) { fail(SandboxFailureCode.INVALID_RESPONSE); }
            });
        }

        public void schedule(Runnable action, Duration delay) {
            if (result.isDone()) return;
            if (!clock.instant().isBefore(deadline)) { fail(SandboxFailureCode.DEADLINE_EXCEEDED); return; }
            long remaining = Duration.between(clock.instant(), deadline).toNanos();
            if (remaining <= 0) { fail(SandboxFailureCode.DEADLINE_EXCEEDED); return; }
            try {
                ScheduledFuture<?> scheduled = scheduler.schedule(() -> {
                    if (result.isDone()) return;
                    if (!clock.instant().isBefore(deadline)) { fail(SandboxFailureCode.DEADLINE_EXCEEDED); return; }
                    try { action.run(); }
                    catch (RuntimeException ex) { fail(SandboxFailureCode.INVALID_RESPONSE); }
                }, Math.min(delay.toNanos(), remaining), TimeUnit.NANOSECONDS);
                next.set(scheduled);
                if (result.isDone()) scheduled.cancel(false);
            } catch (RejectedExecutionException ex) { fail(SandboxFailureCode.CANCELLED); }
        }

        private void cleanup() {
            if (!cleaned.compareAndSet(false, true)) return;
            var io = request.getAndSet(null);
            if (io != null) io.cancel(true);
            var poll = next.getAndSet(null);
            if (poll != null) poll.cancel(false);
            var timer = timeout.getAndSet(null);
            if (timer != null) timer.cancel(false);
            release.run();
        }
    }
}
