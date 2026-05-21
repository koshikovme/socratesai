package com.masters.socratesai.interaction.dto;

import com.masters.socratesai.mentor.model.FeedbackAction;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ExpertLabelRequest {
    @NotNull
    private Long reviewerId;
    @NotNull
    private FeedbackAction targetFeedbackAction;
    @Min(1)
    @Max(5)
    private Integer confidence;
    @Size(max = 1000)
    private String rationale;
}
