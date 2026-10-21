package com.masters.socratesai.collection.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.masters.socratesai.common.model.ContentVisibility;
import java.util.List;
import java.util.Objects;
import static com.masters.socratesai.common.validation.ContesterLimits.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Builder;

@Builder
public record CollectionDraftRequest(
        @NotNull @PositiveOrZero Long lockVersion,
        @NotBlank @Size(max = 180) String title,
        @NotNull @Size(max = STATEMENT_CHARACTERS) String description,
        @NotNull ContentVisibility visibility,
        @NotNull @Size(max = COLLECTION_PROBLEMS) List<@NotNull @Valid CollectionProblemRequest> problems
) {
    @JsonIgnore
    @AssertTrue(message = "a problem may appear only once in a collection")
    public boolean isProblemSelectionUnique() {
        return problems == null || problems.stream().filter(Objects::nonNull)
                .map(CollectionProblemRequest::problemId).distinct().count()
                == problems.stream().filter(Objects::nonNull).count();
    }
}
