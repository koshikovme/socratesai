package com.masters.socratesai.submission.dto;

import java.util.UUID;
import static com.masters.socratesai.common.validation.ContesterLimits.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Builder;

@Builder
public record CreateSubmissionRequest(
        @NotNull @Positive Long problemVersionId,
        @Positive Long collectionId,
        @NotBlank @Pattern(regexp = LANGUAGE_KEY) String languageKey,
        @NotBlank @Size(max = SOURCE_CHARACTERS) String sourceCode,
        @NotNull UUID idempotencyKey
) {}
