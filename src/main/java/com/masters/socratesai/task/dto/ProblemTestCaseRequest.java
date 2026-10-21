package com.masters.socratesai.task.dto;

import static com.masters.socratesai.common.validation.ContesterLimits.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Builder;

@Builder
public record ProblemTestCaseRequest(
        @NotNull @PositiveOrZero Integer position,
        @NotNull @Size(max = TEST_DATA_CHARACTERS) String inputData,
        @NotNull @Size(max = TEST_DATA_CHARACTERS) String expectedOutput,
        @NotNull Boolean hidden
) {}
