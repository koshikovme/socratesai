package com.masters.socratesai.task.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.masters.socratesai.common.model.ContentVisibility;
import com.masters.socratesai.task.model.OutputComparison;
import com.masters.socratesai.task.model.TaskDifficulty;
import java.util.List;
import java.util.Objects;
import static com.masters.socratesai.common.validation.ContesterLimits.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Builder;

@Builder
public record ProblemDraftRequest(
        @NotNull @PositiveOrZero Long lockVersion,
        @NotBlank @Size(max = 180) String title,
        @NotBlank @Size(max = 120) String topic,
        @NotNull TaskDifficulty difficulty,
        @NotBlank @Size(max = STATEMENT_CHARACTERS) String statement,
        @NotNull @Size(max = STATEMENT_CHARACTERS) String inputFormat,
        @NotNull @Size(max = STATEMENT_CHARACTERS) String outputFormat,
        @NotNull @Size(max = STATEMENT_CHARACTERS) String constraintsText,
        @NotNull OutputComparison comparisonMode,
        @NotNull ContentVisibility visibility,
        @NotNull @Size(max = LANGUAGES) List<@NotNull @Valid ProblemLanguageRequest> languages,
        @NotNull @Size(max = TEST_CASES) List<@NotNull @Valid ProblemTestCaseRequest> tests
) {
    @JsonIgnore
    @AssertTrue(message = "language keys must be unique")
    public boolean isLanguageSelectionUnique() {
        return languages == null || languages.stream().filter(Objects::nonNull)
                .map(ProblemLanguageRequest::languageKey).distinct().count()
                == languages.stream().filter(Objects::nonNull).count();
    }

    @JsonIgnore
    @AssertTrue(message = "test positions must be unique")
    public boolean isTestOrderUnique() {
        return tests == null || tests.stream().filter(Objects::nonNull)
                .map(ProblemTestCaseRequest::position).distinct().count()
                == tests.stream().filter(Objects::nonNull).count();
    }
}
