package com.masters.socratesai.submission.service;

import com.masters.socratesai.collection.repo.*;
import com.masters.socratesai.common.api.*;
import com.masters.socratesai.execution.model.*;
import com.masters.socratesai.execution.repo.*;
import com.masters.socratesai.execution.service.*;
import com.masters.socratesai.security.CurrentActor;
import com.masters.socratesai.submission.dto.*;
import com.masters.socratesai.submission.model.*;
import com.masters.socratesai.submission.repo.*;
import com.masters.socratesai.task.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;

@Service @RequiredArgsConstructor
public class SubmissionService {
    private final CurrentActor actors;
    private final ProblemAccess access;
    private final ExecutionInputResolver inputs;
    private final ExecutionAdmission admission;
    private final ProblemCollectionRepository collections;
    private final CollectionProblemRepository memberships;
    private final SubmissionRepository submissions;
    private final StudentProblemProgressRepository progress;
    private final ExecutionJobRepository jobs;
    private final ExecutionCaseResultRepository results;
    private final JudgingProperties properties;
    private final Clock clock;

    @Transactional
    public SubmissionResponse submit(Long problemId, CreateSubmissionRequest request) {
        Long studentId = actors.require().id();
        AuthoringValidation.text(request.sourceCode(), com.masters.socratesai.common.validation.ContesterLimits.SOURCE_BYTES);
        admission.lock();
        var existing = submissions.findByStudentIdAndIdempotencyKey(studentId, request.idempotencyKey());
        if (existing.isPresent()) {
            var submission = existing.get(); var snapshot = submission.getSnapshot();
            if (!Objects.equals(snapshot.getProblemId(), problemId) || !Objects.equals(snapshot.getProblemVersionId(), request.problemVersionId())
                    || !Objects.equals(snapshot.getLanguageKey(), request.languageKey()) || !Objects.equals(submission.getCollectionId(), request.collectionId())
                    || !snapshot.getSourceHash().equals(ExecutionSnapshots.hash(request.sourceCode()))) throw new ApiException(ApiErrorCode.CONFLICT);
            return SubmissionProjection.summary(submission);
        }
        var input = inputs.resolve(problemId, request.problemVersionId(), request.languageKey(), request.sourceCode());
        Long versionId = input.snapshot().getProblemVersionId();
        if (request.collectionId() != null && (!collections.readable(request.collectionId(), access.readableVisibilities())
                || !memberships.existsByCollection_IdAndProblemIdAndProblemVersion_Id(request.collectionId(), problemId, versionId)))
            throw new ApiException(ApiErrorCode.NOT_FOUND);
        admission.capacity(studentId);
        var cases = input.cases();
        var now = OffsetDateTime.now(clock);
        var profile = progress.lockProgress(studentId, problemId).orElseGet(() -> progress.save(StudentProblemProgress.builder()
                .studentId(studentId).problemId(problemId).lastActivityAt(now).build()));
        profile.setFormalAttempts(Math.addExact(profile.getFormalAttempts(), 1)); profile.setLastActivityAt(now);
        var submission = submissions.saveAndFlush(Submission.builder().studentId(studentId).collectionId(request.collectionId())
                .snapshot(input.snapshot()).attemptNumber(profile.getFormalAttempts())
                .idempotencyKey(request.idempotencyKey()).testsTotal(cases.size()).build());
        var job = jobs.saveAndFlush(ExecutionJob.builder().problemVersionId(versionId).submissionId(submission.getId())
                .mode(ExecutionMode.SUBMISSION).maxAttempts(properties.getMaxAttempts()).deadlineAt(now.plus(properties.getDeadline())).build());
        results.saveAll(cases.stream().map(test -> ExecutionCaseResult.builder().job(job).problemVersionId(versionId).testCase(test).build()).toList());
        return SubmissionProjection.summary(submission);
    }

    @Transactional(readOnly = true)
    public SubmissionStatusResponse status(UUID id) {
        var submission = submissions.findByIdAndStudentId(id, actors.require().id()).orElseThrow(() -> new ApiException(ApiErrorCode.NOT_FOUND));
        var job = jobs.findBySubmissionId(id).orElseThrow(() -> new ApiException(ApiErrorCode.NOT_FOUND));
        return new SubmissionStatusResponse(SubmissionProjection.summary(submission), job.getRetryAllowed(), job.getRetryAllowed() ? job.getNextAttemptAt() : null,
                results.findAllByJob_IdAndGenerationOrderByTestCase_Position(job.getId(), 0)
                .stream().map(item -> new SubmissionStatusResponse.CaseSummary(item.getTestCase().getPosition(), item.getTestCase().getHidden(),
                        item.getStatus(), item.getVerdict(), item.getTimeConsumedMs(), item.getMemoryConsumedBytes())).toList());
    }

    @Transactional(readOnly = true)
    public PageResponse<SubmissionResponse> history(int page, int size) {
        return PageResponse.from(submissions.findSummariesByStudentId(actors.require().id(), PageResponse.request(page, size)));
    }
}
