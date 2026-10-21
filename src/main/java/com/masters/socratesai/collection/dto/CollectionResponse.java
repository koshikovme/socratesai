package com.masters.socratesai.collection.dto;

import com.masters.socratesai.common.model.ContentStatus;
import com.masters.socratesai.common.model.ContentVisibility;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.Builder;

@Builder
public record CollectionResponse(
        Long id,
        String title,
        String description,
        ContentStatus status,
        ContentVisibility visibility,
        long lockVersion,
        OffsetDateTime updatedAt,
        List<CollectionProblemResponse> problems
) {}
