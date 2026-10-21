package com.masters.socratesai.execution.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.masters.socratesai.execution.model.ExecutionMode;
import java.time.OffsetDateTime;
import java.util.UUID;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Builder;

@Builder
public record CreateExecutionJobRequest(
        @NotNull @Positive Long problemVersionId,
        UUID submissionId,
        UUID runId,
        @NotNull ExecutionMode mode,
        @NotNull @Future OffsetDateTime deadlineAt,
        @NotNull @Min(1) @Max(10) Integer maxAttempts
) {
    @JsonIgnore
    @AssertTrue(message = "a job must target exactly one submission or run matching its mode")
    public boolean isTargetValid() {
        if (mode == null) return true;
        return mode == ExecutionMode.SUBMISSION
                ? submissionId != null && runId == null
                : runId != null && submissionId == null;
    }
}
