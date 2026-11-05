package com.masters.socratesai.submission.service;

import com.masters.socratesai.collection.repo.ProblemCollectionRepository;
import com.masters.socratesai.collection.service.CollectionAccess;
import com.masters.socratesai.common.api.*;
import com.masters.socratesai.execution.repo.*;
import com.masters.socratesai.security.CurrentActor;
import com.masters.socratesai.submission.dto.*;
import com.masters.socratesai.submission.repo.*;
import com.masters.socratesai.task.repo.*;
import com.masters.socratesai.task.service.ProblemAccess;
import com.masters.socratesai.user.repo.UserRepository;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service @RequiredArgsConstructor
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class SubmissionReviewService {
    private final ProblemAccess problemAccess;
    private final CollectionAccess collectionAccess;
    private final CurrentActor actors;
    private final SubmissionResultsReader reader;
    private final SubmissionRepository submissions;
    private final TaskRepository problems;
    private final ProblemVersionRepository versions;
    private final ProblemCollectionRepository collections;
    private final UserRepository users;
    private final ExecutionJobRepository jobs;
    private final ExecutionCaseResultRepository results;

    public SubmissionResultsResponse problem(Long id, SubmissionResultsFilter filter, int page, int size) {
        problemAccess.owned(id, false);
        if (filter.problemId() != null && !Objects.equals(id, filter.problemId())) throw new ApiException(ApiErrorCode.INVALID_REQUEST);
        return reader.read(id, null, filter, page, size);
    }

    /** Only explicit collection-context submissions: copying a public task never exposes its other students. */
    public SubmissionResultsResponse collection(Long id, SubmissionResultsFilter filter, int page, int size) {
        collectionAccess.owned(id, false);
        return reader.read(filter.problemId(), id, filter, page, size);
    }

    public StaffSubmissionDetailResponse review(UUID id) {
        var actor = actors.staff();
        var submission = submissions.findById(id).orElseThrow(() -> new ApiException(ApiErrorCode.NOT_FOUND));
        var problem = problems.findById(submission.getSnapshot().getProblemId()).orElseThrow(() -> new ApiException(ApiErrorCode.NOT_FOUND));
        boolean ownsCollection = submission.getCollectionId() != null && collections.existsByIdAndOwnerId(submission.getCollectionId(), actor.id());
        if (!actor.admin() && !Objects.equals(problem.getOwnerId(), actor.id()) && !ownsCollection) throw new ApiException(ApiErrorCode.NOT_FOUND);
        var student = users.findById(submission.getStudentId()).orElseThrow(() -> new ApiException(ApiErrorCode.NOT_FOUND));
        var version = versions.findById(submission.getSnapshot().getProblemVersionId()).orElseThrow(() -> new ApiException(ApiErrorCode.NOT_FOUND));
        var job = jobs.findBySubmissionId(id).orElseThrow(() -> new ApiException(ApiErrorCode.NOT_FOUND));
        var cases = results.findAllByJob_IdAndGenerationOrderByTestCase_Position(job.getId(), 0).stream()
                .map(item -> new SubmissionStatusResponse.CaseSummary(item.getTestCase().getPosition(), item.getTestCase().getHidden(),
                        item.getStatus(), item.getVerdict(), item.getTimeConsumedMs(), item.getMemoryConsumedBytes())).toList();
        return StaffSubmissionDetailResponse.builder().submission(SubmissionProjection.summary(submission))
                .sourceCode(submission.getSnapshot().getSourceCode()).problemTitle(version.getTitle()).collectionId(submission.getCollectionId())
                .student(new StaffSubmissionDetailResponse.Student(student.getId(), student.getFullName(), student.getEmail(), student.getGroupName()))
                .cases(cases).build();
    }
}
