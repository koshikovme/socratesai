package com.masters.socratesai.task.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import static com.masters.socratesai.common.validation.ContesterLimits.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Builder;

@Builder
public record ProblemLanguageRequest(
        @NotBlank @Pattern(regexp = LANGUAGE_KEY) String languageKey,
        @Size(max = SOURCE_CHARACTERS) String starterCode,
        @NotNull @Min(1) @Max(TIME_LIMIT_MS) Integer timeLimitMs,
        @NotNull @Min(1) @Max(WALL_TIME_LIMIT_MS) Integer wallTimeLimitMs,
        @NotNull @Min(1) @Max(MEMORY_LIMIT_KB) Integer memoryLimitKb,
        @NotNull @Min(1) @Max(OUTPUT_LIMIT_BYTES) Integer outputLimitBytes
) {
    @JsonIgnore
    @AssertTrue(message = "wallTimeLimitMs must be at least timeLimitMs")
    public boolean isWallTimeValid() {
        return timeLimitMs == null || wallTimeLimitMs == null || wallTimeLimitMs >= timeLimitMs;
    }
}
