package com.masters.socratesai.execution.service;

import com.masters.socratesai.execution.domain.*;
import com.masters.socratesai.execution.domain.port.CodeExecutionPort;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;

/** Async provider calls execute after each store transaction has committed. */
@Component @RequiredArgsConstructor @Slf4j
public class JudgingCoordinator {
    private final JudgingStore store;
    private final CodeExecutionPort execution;
    private final JudgingProperties properties;
    private final SampleRunCleanup cleanup;
    private final Clock clock;
    @Qualifier("judgingExecutor") private final ExecutorService judgingExecutor;
    private final String owner = UUID.randomUUID().toString();
    private final ConcurrentMap<UUID, ActiveJob> active = new ConcurrentHashMap<>();
    private volatile boolean stopping;

    @Scheduled(fixedDelayString = "${app.execution.worker.cleanup-ms:60000}")
    public void cleanup() {
        if (properties.isEnabled() && !stopping) {
            try { cleanup.removeExpired(); }
            catch (RuntimeException ex) { log.warn("Sample cleanup failed"); }
        }
    }

    @Scheduled(fixedDelayString = "${app.execution.worker.tick-ms:1000}")
    public synchronized void tick() {
        if (!properties.isEnabled() || stopping) return;
        try {
            active.values().forEach(item -> {
                if (!item.lease().getDeadline().isAfter(clock.instant()) || !store.heartbeat(item.lease())) item.future().cancel(true);
            });
            store.expire();
            int capacity = properties.getConcurrency() - active.size();
            if (capacity > 0) store.claim(owner, capacity).forEach(this::start);
        } catch (RuntimeException ex) { log.warn("Judging coordinator tick failed"); }
    }

    /** Public for deterministic local integration tests; does not bypass lease fencing. */
    public synchronized void start(JudgingLease lease) {
        if (stopping) return;
        var previous = active.get(lease.getJobId());
        if (previous != null) {
            if (previous.lease().getGeneration() >= lease.getGeneration()) return;
            active.remove(lease.getJobId(), previous); previous.future().cancel(true);
        }
        var completion = new CompletableFuture<Void>();
        var item = new ActiveJob(lease, completion);
        if (active.putIfAbsent(lease.getJobId(), item) != null) return;
        completion.whenComplete((ignored, error) -> active.remove(lease.getJobId(), item));
        advance(lease).whenComplete((ignored, error) -> {
            if (error != null && !(unwrap(error) instanceof StaleJudgingLease)) {
                log.warn("Judging job interrupted jobId={}", lease.getJobId());
                // A DB/callback failure leaves the durable intent/tickets for lease recovery.
            }
            completion.complete(null);
        });
    }

    private CompletableFuture<Void> advance(JudgingLease lease) {
        if (stopping || !active.containsKey(lease.getJobId())) return CompletableFuture.completedFuture(null);
        return CompletableFuture.supplyAsync(() -> store.next(lease), judgingExecutor).thenCompose(plan -> switch (plan.action()) {
            case DONE -> CompletableFuture.completedFuture(null);
            case CREATE -> create(lease, plan.create());
            case POLL -> poll(lease, plan.poll());
        });
    }

    private CompletableFuture<Void> create(JudgingLease lease, ExecutionBatch batch) {
        CompletableFuture<SubmittedExecutionBatch> submitted;
        try { submitted = execution.submitBatch(batch); }
        catch (RuntimeException ex) { submitted = CompletableFuture.failedFuture(ex); }
        return submitted.handleAsync((tickets, error) -> {
            if (error == null) { store.tickets(lease, tickets); return true; }
            var cause = unwrap(error);
            boolean definitelyNotSubmitted = cause instanceof SandboxException sandbox && !sandbox.isMayHaveBeenSubmitted();
            String code = definitelyNotSubmitted ? ((SandboxException) cause).getCode().name() : "SUBMISSION_UNKNOWN";
            store.failed(lease, code, definitelyNotSubmitted, definitelyNotSubmitted);
            return false;
        }, judgingExecutor).thenCompose(continueJob -> continueJob ? advance(lease) : CompletableFuture.completedFuture(null));
    }

    private CompletableFuture<Void> poll(JudgingLease lease, SubmittedExecutionBatch batch) {
        CompletableFuture<ExecutionBatchResult> polled;
        try { polled = execution.pollBatch(batch); }
        catch (RuntimeException ex) { polled = CompletableFuture.failedFuture(ex); }
        return polled.handleAsync((result, error) -> {
            if (error == null) { store.polled(lease, result); return true; }
            var cause = unwrap(error);
            store.failed(lease, cause instanceof SandboxException sandbox ? sandbox.getCode().name() : "TRANSPORT_ERROR", true, false);
            return false;
        }, judgingExecutor).thenCompose(continueJob -> continueJob ? advance(lease) : CompletableFuture.completedFuture(null));
    }

    private Throwable unwrap(Throwable error) {
        return (error instanceof CompletionException || error instanceof ExecutionException) && error.getCause() != null ? unwrap(error.getCause()) : error;
    }

    @PreDestroy public void stop() { stopping = true; active.values().forEach(item -> item.future().cancel(true)); }
    private record ActiveJob(JudgingLease lease, CompletableFuture<Void> future) {}
}
