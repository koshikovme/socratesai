package com.masters.socratesai.interaction.dto;

import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
public class ExpertLabelResponse {
    private UUID labelId;
    private UUID interactionId;
    private Long reviewerId;
    private String targetFeedbackAction;
    private Integer confidence;
    private String rationale;
    private OffsetDateTime createdAt;
}
