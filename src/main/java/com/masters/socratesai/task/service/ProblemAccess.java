package com.masters.socratesai.task.service;

import com.masters.socratesai.common.api.*;
import com.masters.socratesai.common.model.*;
import com.masters.socratesai.security.CurrentActor;
import com.masters.socratesai.task.model.*;
import com.masters.socratesai.task.repo.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.List;

@Component @RequiredArgsConstructor
public class ProblemAccess {
    private final TaskRepository problems;
    private final ProblemVersionRepository versions;
    private final CurrentActor actors;

    public Task owned(Long id, boolean write) {
        var actor = actors.staff();
        Task problem = (write ? problems.lockById(id) : problems.findById(id))
                .orElseThrow(() -> new ApiException(ApiErrorCode.NOT_FOUND));
        actors.owned(actor, problem.getOwnerId());
        return problem;
    }

    public ProblemVersion draft(Task problem) {
        return versions.findFirstByProblem_IdAndStatusOrderByVersionNumberDesc(problem.getId(), ProblemVersionStatus.DRAFT)
                .orElseThrow(() -> new ApiException(ApiErrorCode.CONFLICT));
    }

    public List<ContentVisibility> readableVisibilities() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication == null || !authentication.isAuthenticated() || authentication instanceof AnonymousAuthenticationToken
                ? List.of(ContentVisibility.PUBLIC) : List.of(ContentVisibility.PUBLIC, ContentVisibility.AUTHENTICATED);
    }

    public boolean readable(Task problem) {
        return problem.getStatus() == ContentStatus.PUBLISHED && readableVisibilities().contains(problem.getVisibility())
                && problem.getPublishedVersionId() != null;
    }

    public ProblemVersion published(Long problemId, Long versionId) {
        return published(problemId, versionId, readableVisibilities());
    }

    /** Async callers supply server-established visibility, never a client role. */
    public ProblemVersion publishedAuthenticated(Long problemId, Long versionId) {
        return published(problemId, versionId, List.of(ContentVisibility.PUBLIC, ContentVisibility.AUTHENTICATED));
    }

    private ProblemVersion published(Long problemId, Long versionId, List<ContentVisibility> visibility) {
        Task problem = problems.findById(problemId).orElseThrow(() -> new ApiException(ApiErrorCode.NOT_FOUND));
        if (problem.getStatus() != ContentStatus.PUBLISHED || !visibility.contains(problem.getVisibility()) || problem.getPublishedVersionId() == null)
            throw new ApiException(ApiErrorCode.NOT_FOUND);
        return validVersion(problem, versionId == null ? problem.getPublishedVersionId() : versionId, visibility);
    }

    public ProblemVersion validVersion(Task problem, Long versionId) {
        return validVersion(problem, versionId, readableVisibilities());
    }

    private ProblemVersion validVersion(Task problem, Long versionId, List<ContentVisibility> visibility) {
        var version = versions.findByIdAndProblem_Id(versionId, problem.getId())
                .orElseThrow(() -> new ApiException(ApiErrorCode.NOT_FOUND));
        if (version.getStatus() != ProblemVersionStatus.PUBLISHED || Boolean.TRUE.equals(version.getLegacySnapshot())
                || version.getReferenceVerificationId() == null || !visibility.contains(version.getVisibility())) throw new ApiException(ApiErrorCode.NOT_FOUND);
        return version;
    }
}
