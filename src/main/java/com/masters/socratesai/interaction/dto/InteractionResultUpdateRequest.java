package com.masters.socratesai.interaction.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class InteractionResultUpdateRequest {
    @NotNull
    private Boolean resolvedAfterFeedback;
    @PositiveOrZero
    private Integer fixedAfterMs;
    private Boolean feedbackHelpful;
    @Min(1)
    @Max(5)
    private Integer feedbackRating;
    @Size(max = 1000)
    private String studentComment;
    private Boolean repeatedSameErrorAfterFeedback;
}
