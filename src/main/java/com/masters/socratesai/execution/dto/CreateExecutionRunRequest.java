package com.masters.socratesai.execution.dto;

import static com.masters.socratesai.common.validation.ContesterLimits.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Builder;

@Builder
public record CreateExecutionRunRequest(
        @NotNull @Positive Long problemVersionId,
        @NotBlank @Pattern(regexp = LANGUAGE_KEY) String languageKey,
        @NotBlank @Size(max = SOURCE_CHARACTERS) String sourceCode,
        @NotNull java.util.UUID idempotencyKey
) {
    @Override public String toString() { return "CreateExecutionRunRequest[redacted]"; }
}
