package com.masters.socratesai.collection.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import java.util.Objects;
import static com.masters.socratesai.common.validation.ContesterLimits.*;

public record CollectionMembershipRequest(@NotNull @PositiveOrZero Long lockVersion,
        @NotNull @Size(max = COLLECTION_PROBLEMS) List<@NotNull @Valid CollectionProblemRequest> problems) {
    @JsonIgnore @AssertTrue(message = "a problem may appear only once in a collection")
    public boolean isUnique() {
        return problems == null || problems.stream().filter(Objects::nonNull).map(CollectionProblemRequest::problemId).distinct().count()
                == problems.stream().filter(Objects::nonNull).count();
    }
}
