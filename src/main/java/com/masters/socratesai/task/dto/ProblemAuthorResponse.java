package com.masters.socratesai.task.dto;

import com.masters.socratesai.common.model.ContentStatus;
import com.masters.socratesai.common.model.ContentVisibility;
import lombok.Builder;

@Builder
public record ProblemAuthorResponse(
        Long id,
        Long ownerId,
        ContentStatus status,
        ContentVisibility visibility,
        Long publishedVersionId,
        long lockVersion,
        ProblemAuthorVersionResponse version
) {}
