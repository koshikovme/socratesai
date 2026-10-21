package com.masters.socratesai.collection.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Builder;

@Builder
public record CollectionProblemRequest(
        @NotNull @Positive Long problemId,
        @NotNull @Positive Long problemVersionId
) {}
