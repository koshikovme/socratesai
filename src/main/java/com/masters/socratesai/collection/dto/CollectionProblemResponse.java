package com.masters.socratesai.collection.dto;

import lombok.Builder;

@Builder
public record CollectionProblemResponse(
        Long problemId,
        Long problemVersionId,
        int position,
        String title
) {}
