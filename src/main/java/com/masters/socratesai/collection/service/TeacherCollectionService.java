package com.masters.socratesai.collection.service;

import com.masters.socratesai.collection.dto.*;
import com.masters.socratesai.collection.model.ProblemCollection;
import com.masters.socratesai.collection.repo.*;
import com.masters.socratesai.collection.repo.projection.CollectionSummary;
import com.masters.socratesai.common.api.*;
import com.masters.socratesai.common.model.ContentStatus;
import com.masters.socratesai.security.CurrentActor;
import com.masters.socratesai.task.service.AuthoringValidation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Service @Validated @RequiredArgsConstructor
public class TeacherCollectionService {
    private final ProblemCollectionRepository collections;
    private final CollectionProblemRepository memberships;
    private final CollectionAccess access;
    private final CollectionMembershipWriter writer;
    private final CurrentActor actors;

    @Transactional(readOnly = true)
    public PageResponse<CollectionSummary> list(int page, int size) {
        var actor = actors.staff();
        var request = PageResponse.request(page, size);
        return PageResponse.from(actor.admin() ? collections.authorCatalog(request) : collections.findAllByOwnerId(actor.id(), request));
    }

    @Transactional(readOnly = true)
    public CollectionResponse get(Long id) { return response(access.owned(id, false)); }

    @Transactional
    public CollectionResponse create(@Valid CollectionDraftRequest request) {
        var actor = actors.staff();
        AuthoringValidation.revision(0L, request.lockVersion());
        AuthoringValidation.text(request.description(), 200000);
        var versions = writer.validate(request.problems(), request.visibility(), false);
        var collection = collections.saveAndFlush(ProblemCollection.builder().ownerId(actor.id()).title(request.title())
                .description(request.description()).visibility(request.visibility()).build());
        writer.replace(collection, versions);
        return response(collection);
    }

    @Transactional
    public CollectionResponse update(Long id, @Valid CollectionDraftRequest request) {
        var collection = edit(id, request.lockVersion());
        AuthoringValidation.text(request.description(), 200000);
        var versions = writer.validate(request.problems(), request.visibility(), collection.getStatus() == ContentStatus.PUBLISHED);
        collection.setTitle(request.title()); collection.setDescription(request.description()); collection.setVisibility(request.visibility());
        writer.replace(collection, versions); touch(collection); collections.flush();
        return response(collection);
    }

    @Transactional
    public CollectionResponse replace(Long id, @Valid CollectionMembershipRequest request) {
        var collection = edit(id, request.lockVersion());
        var versions = writer.validate(request.problems(), collection.getVisibility(), collection.getStatus() == ContentStatus.PUBLISHED);
        writer.replace(collection, versions); touch(collection); collections.flush();
        return response(collection);
    }

    @Transactional
    public CollectionResponse publish(Long id, Long lockVersion) {
        var collection = edit(id, lockVersion);
        var members = writer.details(id).stream()
                .map(member -> new CollectionProblemRequest(member.problemId(), member.problemVersionId())).toList();
        writer.validate(members, collection.getVisibility(), true);
        collection.setStatus(ContentStatus.PUBLISHED); touch(collection); collections.flush();
        return response(collection);
    }

    @Transactional
    public CollectionResponse archive(Long id, Long lockVersion) {
        var collection = access.owned(id, true);
        AuthoringValidation.revision(collection.getLockVersion(), lockVersion);
        collection.setStatus(ContentStatus.ARCHIVED); touch(collection); collections.flush();
        return response(collection);
    }

    @Transactional
    public void delete(Long id, Long lockVersion) {
        var collection = access.owned(id, true);
        AuthoringValidation.revision(collection.getLockVersion(), lockVersion);
        if (collection.getStatus() != ContentStatus.DRAFT) throw new ApiException(ApiErrorCode.CONFLICT);
        memberships.deleteAllByCollection_Id(id); memberships.flush(); collections.delete(collection); collections.flush();
    }

    private ProblemCollection edit(Long id, Long lockVersion) {
        var collection = access.owned(id, true);
        AuthoringValidation.revision(collection.getLockVersion(), lockVersion);
        if (collection.getStatus() == ContentStatus.ARCHIVED) throw new ApiException(ApiErrorCode.CONFLICT);
        return collection;
    }

    private CollectionResponse response(ProblemCollection collection) {
        return new CollectionResponse(collection.getId(), collection.getTitle(), collection.getDescription(), collection.getStatus(),
                collection.getVisibility(), collection.getLockVersion(), collection.getUpdatedAt(), writer.details(collection.getId()));
    }
    private void touch(ProblemCollection collection) { collection.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC)); }
}
