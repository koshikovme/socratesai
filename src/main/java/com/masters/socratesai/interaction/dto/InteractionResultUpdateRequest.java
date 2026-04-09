package com.masters.socratesai.interaction.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

@Data
public class InteractionResultUpdateRequest {
    @NotNull
    private Boolean resolvedAfterFeedback;
    @PositiveOrZero
    private Integer fixedAfterMs;
}
