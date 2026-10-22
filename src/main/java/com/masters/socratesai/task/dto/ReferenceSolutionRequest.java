package com.masters.socratesai.task.dto;

import jakarta.validation.constraints.*;
import static com.masters.socratesai.common.validation.ContesterLimits.*;

public record ReferenceSolutionRequest(@NotBlank @Pattern(regexp = LANGUAGE_KEY) String languageKey,
                                       @NotBlank @Size(max = SOURCE_CHARACTERS) String sourceCode) {
    @Override public String toString() { return "ReferenceSolutionRequest[languageKey=" + languageKey + "]"; }
}
