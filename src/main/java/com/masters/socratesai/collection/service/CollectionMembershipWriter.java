package com.masters.socratesai.collection.service;

import com.masters.socratesai.collection.dto.*;
import com.masters.socratesai.collection.model.*;
import com.masters.socratesai.collection.repo.CollectionProblemRepository;
import com.masters.socratesai.common.api.*;
import com.masters.socratesai.common.model.*;
import com.masters.socratesai.task.model.ProblemVersion;
import com.masters.socratesai.task.repo.TaskRepository;
import com.masters.socratesai.task.service.ProblemAccess;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.IntStream;

@Component @RequiredArgsConstructor
public class CollectionMembershipWriter {
    private final TaskRepository problems;
    private final ProblemAccess access;
    private final CollectionProblemRepository memberships;

    public List<ProblemVersion> validate(List<CollectionProblemRequest> selection, ContentVisibility visibility, boolean published) {
        if (published && selection.isEmpty()) throw new ApiException(ApiErrorCode.CONFLICT);
        if (selection.stream().map(CollectionProblemRequest::problemId).distinct().count() != selection.size()) throw new ApiException(ApiErrorCode.INVALID_REQUEST);
        Map<Long, ProblemVersion> selected = new HashMap<>();
        // Stable lock order prevents opposite reorders from locking shared problems in opposite order.
        selection.stream().sorted(Comparator.comparing(CollectionProblemRequest::problemId)).forEach(item -> {
            var problem = problems.lockById(item.problemId()).orElseThrow(() -> new ApiException(ApiErrorCode.INVALID_REQUEST));
            if (problem.getStatus() != ContentStatus.PUBLISHED || problem.getPublishedVersionId() == null) throw new ApiException(ApiErrorCode.INVALID_REQUEST);
            ProblemVersion version;
            try { version = access.validVersion(problem, item.problemVersionId()); }
            catch (ApiException ex) { throw new ApiException(ApiErrorCode.INVALID_REQUEST); }
            if (visibility == ContentVisibility.PUBLIC && (problem.getVisibility() != ContentVisibility.PUBLIC || version.getVisibility() != ContentVisibility.PUBLIC)) {
                throw new ApiException(ApiErrorCode.CONFLICT);
            }
            selected.put(item.problemId(), version);
        });
        return selection.stream().map(item -> selected.get(item.problemId())).toList();
    }

    public void replace(ProblemCollection collection, List<ProblemVersion> versions) {
        memberships.deleteAllByCollection_Id(collection.getId());
        memberships.flush();
        memberships.saveAll(IntStream.range(0, versions.size()).mapToObj(index -> CollectionProblem.builder().collection(collection)
                .problemId(versions.get(index).getProblem().getId()).problemVersion(versions.get(index)).position(index).build()).toList());
        memberships.flush();
    }

    public List<CollectionProblemResponse> details(Long id) {
        return memberships.findDetails(id);
    }
}
