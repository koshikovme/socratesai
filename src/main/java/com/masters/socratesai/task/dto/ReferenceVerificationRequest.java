package com.masters.socratesai.task.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import java.util.Objects;
import static com.masters.socratesai.common.validation.ContesterLimits.*;

public record ReferenceVerificationRequest(@NotNull @PositiveOrZero Long lockVersion,
        @NotNull @Size(min = 1, max = LANGUAGES) List<@NotNull @Valid ReferenceSolutionRequest> solutions) {
    @JsonIgnore @AssertTrue(message = "reference languages must be unique")
    public boolean isUnique() {
        return solutions == null || solutions.stream().filter(Objects::nonNull).map(ReferenceSolutionRequest::languageKey).distinct().count()
                == solutions.stream().filter(Objects::nonNull).count();
    }
}
