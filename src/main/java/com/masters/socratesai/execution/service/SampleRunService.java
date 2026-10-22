package com.masters.socratesai.execution.service;

import com.masters.socratesai.common.api.*;
import com.masters.socratesai.execution.domain.*;
import com.masters.socratesai.execution.dto.*;
import com.masters.socratesai.execution.model.*;
import com.masters.socratesai.execution.repo.*;
import com.masters.socratesai.security.CurrentActor;
import com.masters.socratesai.submission.dto.SubmissionStatusResponse.CaseSummary;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;

@Service @RequiredArgsConstructor
public class SampleRunService {
    private final CurrentActor actors;
    private final ExecutionAdmission admission;
    private final ExecutionInputResolver inputs;
    private final ExecutionRunRepository runs;
    private final ExecutionJobRepository jobs;
    private final ExecutionCaseResultRepository results;
    private final JudgingProperties properties;
    private final Clock clock;

    @Transactional
    public SampleRunStatusResponse create(Long problemId, CreateExecutionRunRequest request) {
        Long student = actors.require().id();
        com.masters.socratesai.task.service.AuthoringValidation.text(request.sourceCode(), com.masters.socratesai.common.validation.ContesterLimits.SOURCE_BYTES);
        admission.lock();
        var existing = runs.findByRequestedByAndIdempotencyKey(student, request.idempotencyKey());
        if (existing.isPresent()) {
            var run = existing.get(); var snapshot = run.getSnapshot();
            if (!Objects.equals(snapshot.getProblemId(), problemId) || !Objects.equals(snapshot.getProblemVersionId(), request.problemVersionId())
                    || !Objects.equals(snapshot.getLanguageKey(), request.languageKey()) || !snapshot.getSourceHash().equals(ExecutionSnapshots.hash(request.sourceCode())))
                throw new ApiException(ApiErrorCode.CONFLICT);
            return response(run);
        }
        var input = inputs.resolve(problemId, request.problemVersionId(), request.languageKey(), request.sourceCode()); admission.capacity(student);
        var now = OffsetDateTime.now(clock);
        var run = runs.saveAndFlush(ExecutionRun.builder().requestedBy(student).idempotencyKey(request.idempotencyKey()).snapshot(input.snapshot())
                .mode(ExecutionMode.SAMPLE_RUN).expiresAt(now.plus(properties.getSampleTtl())).build());
        var job = jobs.saveAndFlush(ExecutionJob.builder().problemVersionId(request.problemVersionId()).runId(run.getId()).mode(ExecutionMode.SAMPLE_RUN)
                .maxAttempts(properties.getMaxAttempts()).deadlineAt(now.plus(properties.getDeadline())).build());
        results.saveAll(input.cases().stream().filter(test -> !Boolean.TRUE.equals(test.getHidden())).map(test -> ExecutionCaseResult.builder()
                .job(job).problemVersionId(request.problemVersionId()).testCase(test).build()).toList());
        return response(run);
    }

    @Transactional(readOnly = true)
    public SampleRunStatusResponse get(UUID id) {
        return response(runs.findByIdAndRequestedBy(id, actors.require().id()).orElseThrow(() -> new ApiException(ApiErrorCode.NOT_FOUND)));
    }

    private SampleRunStatusResponse response(ExecutionRun run) {
        if (!run.getExpiresAt().isAfter(OffsetDateTime.now(clock))) throw new ApiException(ApiErrorCode.NOT_FOUND);
        var job = jobs.findByRunId(run.getId()).orElseThrow(() -> new ApiException(ApiErrorCode.NOT_FOUND));
        var cases = results.findAllByJob_IdAndGenerationOrderByTestCase_Position(job.getId(), 0);
        var verdict = job.getStatus() == ExecutionStatus.FINISHED ? new ExecutionResultAggregator().aggregate(cases.stream().map(item -> CaseExecutionResult.builder()
                .status(ExecutionStatus.FINISHED).verdict(item.getVerdict()).build()).toList()).getVerdict() : null;
        return new SampleRunStatusResponse(run.getId(), run.getSnapshot().getProblemVersionId(), job.getStatus(), verdict, run.getExpiresAt(),
                job.getRetryAllowed(), job.getRetryAllowed() ? job.getNextAttemptAt() : null,
                java.util.stream.IntStream.range(0, cases.size()).mapToObj(index -> {
                    var item = cases.get(index);
                    return new CaseSummary(index, false, item.getStatus(), item.getVerdict(), item.getTimeConsumedMs(), item.getMemoryConsumedBytes());
                }).toList());
    }
}
