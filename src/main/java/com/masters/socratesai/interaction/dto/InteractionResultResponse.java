package com.masters.socratesai.interaction.dto;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class InteractionResultResponse {
    private UUID interactionId;
    private Boolean resolvedAfterFeedback;
    private Integer fixedAfterMs;
    private String message;
}