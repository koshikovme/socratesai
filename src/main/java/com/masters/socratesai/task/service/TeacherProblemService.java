package com.masters.socratesai.task.service;

import com.masters.socratesai.common.api.*;
import com.masters.socratesai.common.model.ContentStatus;
import com.masters.socratesai.security.CurrentActor;
import com.masters.socratesai.task.dto.*;
import com.masters.socratesai.task.model.*;
import com.masters.socratesai.task.repo.*;
import com.masters.socratesai.task.repo.projection.ProblemSummary;
import com.masters.socratesai.collection.repo.CollectionProblemRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import static com.masters.socratesai.execution.model.ExecutionStatus.*;

@Service @Validated @RequiredArgsConstructor
public class TeacherProblemService {
    private final TaskRepository problems;
    private final ProblemVersionRepository versions;
    private final ProblemLanguageConfigRepository languages;
    private final ReferenceVerificationRepository verifications;
    private final CollectionProblemRepository memberships;
    private final CurrentActor actors;
    private final ProblemAccess access;
    private final ProblemDtoMapper mapper;
    private final DraftContentWriter writer;
    private final PublicationGate publication;

    @Transactional(readOnly = true)
    public PageResponse<ProblemSummary> list(int page, int size) {
        var actor = actors.staff();
        var request = PageResponse.request(page, size);
        return PageResponse.from(actor.admin() ? problems.authorCatalog(request) : problems.findAllByOwnerId(actor.id(), request));
    }

    @Transactional(readOnly = true)
    public ProblemAuthorResponse get(Long id) {
        var problem = access.owned(id, false);
        var version = versions.findFirstByProblem_IdAndStatusOrderByVersionNumberDesc(id, ProblemVersionStatus.DRAFT)
                .orElseGet(() -> versions.findByIdAndProblem_Id(problem.getPublishedVersionId(), id)
                        .orElseThrow(() -> new ApiException(ApiErrorCode.NOT_FOUND)));
        return mapper.author(problem, version);
    }

    @Transactional
    public ProblemAuthorResponse create(@Valid ProblemDraftRequest request) {
        var actor = actors.staff();
        AuthoringValidation.revision(0L, request.lockVersion());
        AuthoringValidation.draft(request);
        var problem = problems.saveAndFlush(Task.builder().ownerId(actor.id()).title(request.title()).topic(request.topic())
                .difficulty(request.difficulty()).description(request.statement()).language(request.languages().isEmpty() ? "unspecified" : request.languages().getFirst().languageKey())
                .published(false).status(ContentStatus.DRAFT).visibility(request.visibility()).createdAt(LocalDateTime.now()).build());
        var version = versions.saveAndFlush(newVersion(problem, 1, request));
        writer.write(version, request);
        versions.flush();
        return mapper.author(problem, version);
    }

    @Transactional
    public ProblemAuthorResponse update(Long id, @Valid ProblemDraftRequest request) {
        var problem = access.owned(id, true);
        AuthoringValidation.revision(problem.getLockVersion(), request.lockVersion());
        AuthoringValidation.draft(request);
        var version = access.draft(problem);
        writer.write(version, request);
        touch(problem);
        problems.flush(); versions.flush();
        return mapper.author(problem, version);
    }

    @Transactional
    public ProblemAuthorResponse draft(Long id, Long lockVersion) {
        var problem = access.owned(id, true);
        AuthoringValidation.revision(problem.getLockVersion(), lockVersion);
        var existing = versions.findFirstByProblem_IdAndStatusOrderByVersionNumberDesc(id, ProblemVersionStatus.DRAFT);
        if (existing.isPresent()) return mapper.author(problem, existing.get());
        if (problem.getPublishedVersionId() == null) throw new ApiException(ApiErrorCode.CONFLICT);
        var source = versions.findByIdAndProblem_Id(problem.getPublishedVersionId(), id).orElseThrow(() -> new ApiException(ApiErrorCode.CONFLICT));
        int number = versions.findFirstByProblem_IdOrderByVersionNumberDesc(id).orElseThrow().getVersionNumber() + 1;
        var version = versions.saveAndFlush(ProblemVersion.builder().problem(problem).versionNumber(number).title(source.getTitle())
                .topic(source.getTopic()).difficulty(source.getDifficulty()).statement(source.getStatement()).visibility(source.getVisibility()).build());
        writer.copy(source, version);
        touch(problem); problems.flush(); versions.flush();
        return mapper.author(problem, version);
    }

    @Transactional
    public ProblemAuthorResponse publish(Long id, @Valid PublishProblemRequest request) {
        var problem = access.owned(id, true);
        AuthoringValidation.revision(problem.getLockVersion(), request.lockVersion());
        var version = access.draft(problem);
        publication.verified(version, request.verificationId());
        var configs = languages.findAllByProblemVersion_IdOrderByLanguageKey(version.getId());
        configs.forEach(language -> language.setRuntimeVerified(true));
        languages.flush(); // Child writes must finish before V9 makes the parent immutable.
        version.setReferenceVerificationId(request.verificationId());
        version.setPublishedAt(OffsetDateTime.now(ZoneOffset.UTC));
        version.setStatus(ProblemVersionStatus.PUBLISHED);
        problem.setTitle(version.getTitle()); problem.setTopic(version.getTopic()); problem.setDifficulty(version.getDifficulty());
        problem.setDescription(version.getStatement()); problem.setLanguage(configs.getFirst().getLanguageKey());
        problem.setStarterCode(configs.getFirst().getStarterCode()); problem.setVisibility(version.getVisibility());
        problem.setPublishedVersionId(version.getId()); problem.setStatus(ContentStatus.PUBLISHED); problem.setPublished(true);
        touch(problem); versions.flush(); problems.flush();
        return mapper.author(problem, version);
    }

    @Transactional
    public ProblemAuthorResponse archive(Long id, Long lockVersion) {
        var problem = access.owned(id, true);
        AuthoringValidation.revision(problem.getLockVersion(), lockVersion);
        problem.setStatus(ContentStatus.ARCHIVED); problem.setPublished(false); touch(problem); problems.flush();
        var version = versions.findFirstByProblem_IdOrderByVersionNumberDesc(id).orElseThrow(() -> new ApiException(ApiErrorCode.CONFLICT));
        return mapper.author(problem, version);
    }

    @Transactional
    public void delete(Long id, Long lockVersion, boolean draftOnly) {
        var problem = access.owned(id, true);
        AuthoringValidation.revision(problem.getLockVersion(), lockVersion);
        var version = access.draft(problem);
        if (!draftOnly && (problem.getPublishedVersionId() != null || memberships.existsByProblemId(id)
                || versions.countByProblem_Id(id) != 1)) throw new ApiException(ApiErrorCode.CONFLICT);
        verifications.expire(version.getId(), OffsetDateTime.now(ZoneOffset.UTC));
        if (verifications.existsByProblemVersionIdAndStatusIn(version.getId(), List.of(QUEUED, RUNNING))) throw new ApiException(ApiErrorCode.CONFLICT);
        verifications.deleteAllByProblemVersionId(version.getId()); verifications.flush();
        writer.deleteChildren(version.getId()); versions.delete(version); versions.flush();
        if (!draftOnly || problem.getPublishedVersionId() == null) problems.delete(problem);
        else { touch(problem); problems.flush(); }
    }

    private ProblemVersion newVersion(Task problem, int number, ProblemDraftRequest request) {
        return ProblemVersion.builder().problem(problem).versionNumber(number).title(request.title()).topic(request.topic())
                .difficulty(request.difficulty()).statement(request.statement()).inputFormat(request.inputFormat()).outputFormat(request.outputFormat())
                .constraintsText(request.constraintsText()).comparisonMode(request.comparisonMode()).visibility(request.visibility()).build();
    }

    private void touch(Task problem) { problem.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC)); }
}
