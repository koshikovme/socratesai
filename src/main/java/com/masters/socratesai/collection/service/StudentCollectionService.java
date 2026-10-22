package com.masters.socratesai.collection.service;

import com.masters.socratesai.collection.dto.*;
import com.masters.socratesai.collection.repo.*;
import com.masters.socratesai.common.api.*;
import com.masters.socratesai.task.service.ProblemAccess;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

@Service @RequiredArgsConstructor @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class StudentCollectionService {
    private final ProblemCollectionRepository collections;
    private final ProblemAccess problems;
    private final CollectionMembershipWriter writer;

    public PageResponse<StudentCollectionSummaryResponse> list(int page, int size) {
        return PageResponse.from(collections.studentCatalog(problems.readableVisibilities(), PageResponse.request(page, size))
                .map(item -> new StudentCollectionSummaryResponse(item.getId(), item.getTitle())));
    }

    public StudentCollectionResponse get(Long id) {
        var collection = collections.findById(id).orElseThrow(() -> new ApiException(ApiErrorCode.NOT_FOUND));
        if (!collections.readable(id, problems.readableVisibilities())) {
            throw new ApiException(ApiErrorCode.NOT_FOUND);
        }
        return new StudentCollectionResponse(id, collection.getTitle(), collection.getDescription(), writer.details(id));
    }
}
