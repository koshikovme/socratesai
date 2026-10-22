package com.masters.socratesai.collection.service;

import com.masters.socratesai.collection.model.ProblemCollection;
import com.masters.socratesai.collection.repo.ProblemCollectionRepository;
import com.masters.socratesai.common.api.*;
import com.masters.socratesai.security.CurrentActor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component @RequiredArgsConstructor
public class CollectionAccess {
    private final ProblemCollectionRepository collections;
    private final CurrentActor actors;

    public ProblemCollection owned(Long id, boolean write) {
        var actor = actors.staff();
        var collection = (write ? collections.lockById(id) : collections.findById(id))
                .orElseThrow(() -> new ApiException(ApiErrorCode.NOT_FOUND));
        actors.owned(actor, collection.getOwnerId());
        return collection;
    }
}
