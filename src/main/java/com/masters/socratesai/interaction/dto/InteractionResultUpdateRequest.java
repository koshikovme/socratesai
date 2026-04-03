package com.masters.socratesai.interaction.dto;

import lombok.Data;

@Data
public class InteractionResultUpdateRequest {
    private Boolean resolvedAfterFeedback;
    private Integer fixedAfterMs;
}