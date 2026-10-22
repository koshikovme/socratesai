package com.masters.socratesai.execution.service;

import com.masters.socratesai.execution.domain.*;
import com.masters.socratesai.execution.model.*;
import com.masters.socratesai.execution.repo.*;
import com.masters.socratesai.submission.model.Submission;
import com.masters.socratesai.submission.repo.*;
import com.masters.socratesai.task.repo.*;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Short transactions only. Provider I/O belongs to JudgingCoordinator. */
@Service @RequiredArgsConstructor
public class JudgingStore {
    private final ExecutionJobRepository jobs;
    private final ExecutionCaseResultRepository results;
    private final SubmissionRepository submissions;
    private final ExecutionRunRepository runs;
    private final StudentProblemProgressRepository progress;
    private final ProblemTestCaseRepository tests;
    private final ProblemVersionRepository versions;
    private final JudgingProperties properties;
    private final Clock clock;
    private final EntityManager entityManager;
    private final JudgingEvents events;
    private static final ExecutionResultAggregator AGGREGATOR = new ExecutionResultAggregator();

    @Transactional
    public List<JudgingLease> claim(String owner, int limit) {
        if (limit <= 0) return List.of();
        var now = now();
        return jobs.lockNextJobs(now, Math.min(Math.max(limit, 1), properties.getConcurrency())).stream().map(job -> {
            job.setStatus(ExecutionStatus.DISPATCHING); job.setFinishedAt(null); job.setRetryAllowed(false);
            job.setLeaseOwner(owner); job.setLeaseToken(UUID.randomUUID()); job.setLeaseExpiresAt(now.plus(properties.getLease()));
            job.setGeneration(job.getGeneration() + 1); job.setAttemptCount(job.getAttemptCount() + 1);
            if (job.getStartedAt() == null) job.setStartedAt(now);
            if (job.getSubmissionId() != null) {
                var submission = submission(job); submission.setStatus(ExecutionStatus.DISPATCHING); submission.setFinishedAt(null);
            }
            return JudgingLease.builder().jobId(job.getId()).generation(job.getGeneration()).token(job.getLeaseToken())
                    .deadline(job.getDeadlineAt().toInstant()).build();
        }).toList();
    }

    @Transactional
    public boolean heartbeat(JudgingLease lease) {
        var job = jobs.lockById(lease.getJobId()).orElse(null);
        if (job == null || !owned(job, lease)) return false;
        job.setLeaseExpiresAt(now().plus(properties.getLease()));
        return true;
    }

    @Transactional
    public void expire() {
        jobs.lockExpiredJobs(now(), 100).forEach(job -> terminal(job, job.getDeadlineAt().isAfter(now()) ? "RETRY_EXHAUSTED" : "DEADLINE_EXCEEDED"));
    }

    @Transactional
    public JudgingPlan next(JudgingLease lease) {
        var job = require(lease);
        if (job.getMode() == ExecutionMode.REFERENCE_VERIFICATION) { terminal(job, "UNSUPPORTED_JOB_MODE"); return JudgingPlan.done(); }
        var checkpoints = checkpoints(job);
        var required = job.getMode() == ExecutionMode.SAMPLE_RUN
                ? tests.findAllByProblemVersion_IdAndHiddenFalseOrderByPosition(job.getProblemVersionId())
                : tests.findAllByProblemVersion_IdOrderByPosition(job.getProblemVersionId());
        var requiredIds = required.stream().map(test -> test.getId()).collect(Collectors.toSet());
        if (required.isEmpty() || checkpoints.size() != required.size() || !checkpoints.stream().map(item -> item.getTestCase().getId())
                .collect(Collectors.toSet()).equals(requiredIds)) { terminal(job, "INCOMPLETE_SUITE"); return JudgingPlan.done(); }
        if (checkpoints.stream().anyMatch(item -> item.getDispatchState() == DispatchState.SENDING)) {
            terminal(job, "SUBMISSION_UNKNOWN"); return JudgingPlan.done();
        }
        var snapshot = job.getSubmissionId() == null ? runs.findById(job.getRunId()).orElseThrow(StaleJudgingLease::new).getSnapshot()
                : submission(job).getSnapshot();
        var version = versions.findById(job.getProblemVersionId()).orElseThrow(StaleJudgingLease::new);
        var ticketed = checkpoints.stream().filter(item -> item.getDispatchState() == DispatchState.TICKETED).toList();
        if (!ticketed.isEmpty()) {
            var deadline = ticketed.stream().map(ExecutionCaseResult::getProviderDeadlineAt).filter(Objects::nonNull)
                    .min(Comparator.naturalOrder()).orElse(job.getDeadlineAt());
            if (!deadline.isAfter(now())) { terminal(job, "DEADLINE_EXCEEDED"); return JudgingPlan.done(); }
            running(job);
            var batch = SubmittedExecutionBatch.builder().runtimeKey(snapshot.getRuntimeKey()).runtimeVersion(snapshot.getRuntimeVersion())
                    .limits(ExecutionSnapshots.limits(snapshot)).comparison(version.getComparisonMode()).deadline(deadline.toInstant())
                    .tickets(ticketed.stream().map(item -> ExecutionTicket.builder().testCaseId(item.getTestCase().getId())
                            .position(item.getTestCase().getPosition()).hidden(item.getTestCase().getHidden())
                            .expectedOutput(item.getTestCase().getExpectedOutput()).token(item.getProviderToken()).build()).toList()).build();
            return new JudgingPlan(JudgingPlan.Action.POLL, null, batch);
        }
        var pending = checkpoints.stream().filter(item -> item.getDispatchState() == DispatchState.PENDING).limit(properties.getBatchSize()).toList();
        if (!pending.isEmpty()) {
            pending.forEach(item -> item.setDispatchState(DispatchState.SENDING));
            var batch = ExecutionBatch.builder().languageKey(snapshot.getLanguageKey()).runtimeKey(snapshot.getRuntimeKey()).runtimeVersion(snapshot.getRuntimeVersion())
                    .sourceCode(snapshot.getSourceCode()).limits(ExecutionSnapshots.limits(snapshot)).comparison(version.getComparisonMode()).deadline(job.getDeadlineAt().toInstant())
                    .tests(pending.stream().map(item -> ExecutionTestCase.builder().testCaseId(item.getTestCase().getId()).position(item.getTestCase().getPosition())
                            .hidden(item.getTestCase().getHidden()).input(item.getTestCase().getInputData()).expectedOutput(item.getTestCase().getExpectedOutput()).build()).toList()).build();
            return new JudgingPlan(JudgingPlan.Action.CREATE, batch, null);
        }
        complete(job, checkpoints);
        return JudgingPlan.done();
    }

    @Transactional
    public void tickets(JudgingLease lease, SubmittedExecutionBatch batch) {
        var job = require(lease);
        var sending = checkpoints(job).stream().filter(item -> item.getDispatchState() == DispatchState.SENDING).toList();
        if (batch == null || batch.getTickets() == null || batch.getDeadline() == null || batch.getDeadline().isAfter(job.getDeadlineAt().toInstant())
                || sending.isEmpty() || batch.getTickets().size() != sending.size()
                || batch.getTickets().stream().anyMatch(ticket -> ticket == null || ticket.getToken() == null || !ticket.getToken().matches("[A-Za-z0-9._:-]{1,200}"))
                || batch.getTickets().stream().map(ExecutionTicket::getToken).distinct().count() != sending.size()
                || !batch.getTickets().stream().map(ExecutionTicket::getTestCaseId).collect(Collectors.toSet())
                    .equals(sending.stream().map(item -> item.getTestCase().getId()).collect(Collectors.toSet()))) {
            terminal(job, "SUBMISSION_UNKNOWN"); return;
        }
        var byId = batch.getTickets().stream().collect(Collectors.toMap(ExecutionTicket::getTestCaseId, Function.identity()));
        sending.forEach(item -> { item.setProviderToken(byId.get(item.getTestCase().getId()).getToken());
            item.setProviderDeadlineAt(batch.getDeadline().atOffset(ZoneOffset.UTC)); item.setDispatchState(DispatchState.TICKETED); item.setStatus(CaseExecutionStatus.RUNNING); });
        running(job);
    }

    @Transactional
    public void polled(JudgingLease lease, ExecutionBatchResult batch) {
        var job = require(lease);
        var ticketed = checkpoints(job).stream().filter(item -> item.getDispatchState() == DispatchState.TICKETED).toList();
        if (batch == null || batch.getCases() == null || batch.getCases().size() != ticketed.size()
                || batch.getCases().stream().anyMatch(Objects::isNull)
                || !batch.getCases().stream().map(CaseExecutionResult::getTestCaseId).collect(Collectors.toSet())
                    .equals(ticketed.stream().map(item -> item.getTestCase().getId()).collect(Collectors.toSet()))
                || batch.getCases().stream().anyMatch(item -> (item.getStatus() == ExecutionStatus.FINISHED) != (item.getVerdict() != null)
                    || (item.getTimeConsumedMs() != null && item.getTimeConsumedMs() < 0) || (item.getMemoryConsumedBytes() != null && item.getMemoryConsumedBytes() < 0))) {
            failure(job, "INVALID_RESPONSE", true); return;
        }
        var byId = batch.getCases().stream().collect(Collectors.toMap(CaseExecutionResult::getTestCaseId, Function.identity()));
        boolean finished = batch.getCases().stream().allMatch(item -> item.getStatus() == ExecutionStatus.FINISHED);
        if ((batch.getStatus() == ExecutionStatus.FINISHED) != finished) { failure(job, "INVALID_RESPONSE", true); return; }
        ticketed.forEach(item -> {
            var result = byId.get(item.getTestCase().getId());
            if (result.getStatus() == ExecutionStatus.FINISHED) {
                item.setStatus(CaseExecutionStatus.FINISHED); item.setVerdict(result.getVerdict()); item.setDispatchState(DispatchState.COMPLETE);
                item.setTimeConsumedMs(result.getTimeConsumedMs()); item.setMemoryConsumedBytes(result.getMemoryConsumedBytes());
                // Diagnostics are intentionally not retained, even when the provider returns hidden echoes.
            }
        });
        if (batch.getStatus() != ExecutionStatus.FINISHED || batch.getCases().stream().anyMatch(item -> item.getStatus() != ExecutionStatus.FINISHED))
            failure(job, batch.getFailureCode() == null ? "INVALID_RESPONSE" : batch.getFailureCode().name(), true);
    }

    @Transactional
    public void failed(JudgingLease lease, String code, boolean safeToRetry, boolean resetUnsubmitted) {
        var job = require(lease);
        if (resetUnsubmitted) checkpoints(job).stream().filter(item -> item.getDispatchState() == DispatchState.SENDING)
                .forEach(item -> item.setDispatchState(DispatchState.PENDING));
        failure(job, code, safeToRetry);
    }

    private void complete(ExecutionJob job, List<ExecutionCaseResult> cases) {
        if (cases.stream().anyMatch(item -> item.getDispatchState() != DispatchState.COMPLETE)) { terminal(job, "INCOMPLETE_SUITE"); return; }
        var aggregate = AGGREGATOR.aggregate(cases.stream().map(item -> CaseExecutionResult.builder().testCaseId(item.getTestCase().getId())
                .position(item.getTestCase().getPosition()).hidden(item.getTestCase().getHidden())
                .status(item.getStatus() == CaseExecutionStatus.FINISHED ? ExecutionStatus.FINISHED : ExecutionStatus.SYSTEM_ERROR).verdict(item.getVerdict()).build()).toList());
        if (aggregate.getStatus() != ExecutionStatus.FINISHED) { terminal(job, "INCOMPLETE_SUITE"); return; }
        if (job.getMode() == ExecutionMode.SAMPLE_RUN) { finishJob(job, ExecutionStatus.FINISHED, null); return; }
        var submission = submission(job);
        submission.setStatus(ExecutionStatus.FINISHED); submission.setVerdict(aggregate.getVerdict()); submission.setTestsTotal(cases.size());
        submission.setTestsPassed((int) cases.stream().filter(item -> item.getVerdict() == ExecutionVerdict.ACCEPTED).count());
        submission.setTestsFailed(cases.size() - submission.getTestsPassed()); submission.setFinishedAt(now());
        submission.setTimeConsumedMs(cases.stream().map(ExecutionCaseResult::getTimeConsumedMs).filter(Objects::nonNull).max(Integer::compareTo).orElse(null));
        submission.setMemoryConsumedBytes(cases.stream().map(ExecutionCaseResult::getMemoryConsumedBytes).filter(Objects::nonNull).max(Long::compareTo).orElse(null));
        finishJob(job, ExecutionStatus.FINISHED, null);
        // Flush accepted evidence before the progress trigger verifies the FK target.
        entityManager.flush();
        var profile = progress.lockProgress(submission.getStudentId(), submission.getSnapshot().getProblemId()).orElseThrow(StaleJudgingLease::new);
        profile.setBestTestsPassed(Math.max(profile.getBestTestsPassed(), submission.getTestsPassed())); profile.setLastTestsFailed(submission.getTestsFailed());
        profile.setLastActivityAt(now());
        if (aggregate.getVerdict() == ExecutionVerdict.ACCEPTED && profile.getAcceptedSubmissionId() == null) {
            profile.setAcceptedSubmissionId(submission.getId()); profile.setAcceptedAt(submission.getFinishedAt());
        }
        events.afterCommit(new SubmissionJudged(submission.getId(), submission.getStudentId(), submission.getStatus(), submission.getVerdict()));
    }

    private void failure(ExecutionJob job, String code, boolean safeToRetry) {
        terminal(job, code);
        boolean retry = safeToRetry && job.getAttemptCount() < job.getMaxAttempts() && job.getDeadlineAt().isAfter(now());
        job.setRetryAllowed(retry);
        job.setNextAttemptAt(now().plus(new ExponentialBackoff(properties.getInitialBackoff(), properties.getMaxBackoff()).delay(job.getAttemptCount() - 1)));
    }

    private void terminal(ExecutionJob job, String code) {
        finishJob(job, ExecutionStatus.SYSTEM_ERROR, code);
        if (job.getSubmissionId() != null) {
            var submission = submission(job); submission.setStatus(ExecutionStatus.SYSTEM_ERROR); submission.setVerdict(null); submission.setFinishedAt(now());
        }
    }

    private void running(ExecutionJob job) {
        job.setStatus(ExecutionStatus.RUNNING);
        if (job.getSubmissionId() != null) submission(job).setStatus(ExecutionStatus.RUNNING);
    }

    private void finishJob(ExecutionJob job, ExecutionStatus status, String code) {
        job.setStatus(status); job.setFinishedAt(now()); job.setRetryAllowed(false); job.setLastErrorCode(code);
        job.setLeaseOwner(null); job.setLeaseToken(null); job.setLeaseExpiresAt(null);
    }
    private Submission submission(ExecutionJob job) { return submissions.findById(job.getSubmissionId()).orElseThrow(StaleJudgingLease::new); }
    private List<ExecutionCaseResult> checkpoints(ExecutionJob job) { return results.findAllByJob_IdAndGenerationOrderByTestCase_Position(job.getId(), 0); }
    private OffsetDateTime now() { return OffsetDateTime.now(clock); }
    private ExecutionJob require(JudgingLease lease) {
        var job = jobs.lockById(lease.getJobId()).orElseThrow(StaleJudgingLease::new);
        if (!owned(job, lease)) throw new StaleJudgingLease();
        return job;
    }
    private boolean owned(ExecutionJob job, JudgingLease lease) {
        return job.getGeneration() == lease.getGeneration() && Objects.equals(job.getLeaseToken(), lease.getToken())
                && (job.getStatus() == ExecutionStatus.DISPATCHING || job.getStatus() == ExecutionStatus.RUNNING)
                && job.getLeaseExpiresAt() != null && job.getLeaseExpiresAt().isAfter(now()) && job.getDeadlineAt().isAfter(now());
    }
}
