package com.masters.socratesai.collection.service;

import com.masters.socratesai.collection.dto.CollectionProgressResponse;
import com.masters.socratesai.collection.repo.*;
import com.masters.socratesai.common.api.*;
import com.masters.socratesai.security.CurrentActor;
import com.masters.socratesai.submission.repo.SubmissionRepository;
import com.masters.socratesai.task.service.ProblemAccess;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service @RequiredArgsConstructor
public class CollectionProgressService {
    private final CurrentActor actors;
    private final ProblemAccess access;
    private final ProblemCollectionRepository collections;
    private final CollectionProblemRepository members;
    private final SubmissionRepository submissions;

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public CollectionProgressResponse get(Long id) {
        Long student = actors.require().id();
        if (!collections.readable(id, access.readableVisibilities())) throw new ApiException(ApiErrorCode.NOT_FOUND);
        var items = members.findDetails(id);
        var accepted = submissions.acceptedVersions(student, items.stream().map(item -> item.problemVersionId()).toList());
        var problems = items.stream().map(item -> new CollectionProgressResponse.ProblemProgress(item.problemId(), item.problemVersionId(),
                accepted.contains(item.problemVersionId()))).toList();
        return new CollectionProgressResponse(id, problems.size(), (int) problems.stream().filter(CollectionProgressResponse.ProblemProgress::solved).count(), problems);
    }
}
